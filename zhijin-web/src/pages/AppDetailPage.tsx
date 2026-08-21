import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { appsApi, type ApiKeyItem, type AppVersion } from '../api/apps';
import { Perm } from '../auth/Perm';
import { FlowCanvas, type FlowCanvasHandle } from '../canvas/FlowCanvas';
import { createCanvasNode, NodePalette, type PaletteItem } from '../canvas/palette';
import { fromDsl, toDsl } from '../canvas/dsl';

// ---- V1 前端内部草稿：画布 DSL 与模型配置均存 localStorage，不上送后端（后端 DSL 对接留 V2 D4） ----
const dslKey = (id: number) => `zhijin_dsl_${id}`;
const modelKey = (id: number) => `zhijin_model_${id}`;

// 模型配置 V1：provider → 可选 model 映射（前端写死的简化选项，后续对接后端模型注册表）
const PROVIDER_MODELS: Record<string, string[]> = {
  qwen: ['qwen-max', 'qwen-plus'],
  claude: ['claude-sonnet-4-5', 'claude-opus-4-1'],
  openai: ['gpt-4o', 'gpt-4o-mini'],
};
const PROVIDER_OPTIONS = Object.keys(PROVIDER_MODELS).map(p => ({ value: p, label: p }));

// 状态 → 标签映射（与 AppListPage 一致：0=草稿 1=已发布 2=已下线，AppStatus.ordinal）
const STATUS_MAP: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'success', text: '已发布' },
  2: { color: 'error', text: '已下线' },
};

// 时间展示格式化：后端 ISO 串 "2026-08-21T10:30:00" → "2026-08-21 10:30:00"；非字符串（异常数据）兜底显示 '-'
const formatTime = (t?: string) => (typeof t === 'string' ? t.replace('T', ' ').slice(0, 19) : '-');

/**
 * 应用详情页：四个 tab（画布编排 / 模型配置 / API Key / 发布）。
 * 画布与模型配置为 V1 本地草稿（localStorage），API Key 与发布走后端真实接口。
 */
export function AppDetailPage() {
  const { id: idParam } = useParams<{ id: string }>();
  const appId = Number(idParam); // 路由 /apps/:id，id 必填；Number 兜底 NaN 不会命中接口
  const queryClient = useQueryClient();
  const canvasRef = useRef<FlowCanvasHandle>(null);
  // 陈旧闭包守卫：始终指向最近一次渲染的 appId，供 useMutation 的 onSuccess 判断请求返回时是否已切走应用
  const appIdRef = useRef(appId);
  appIdRef.current = appId;

  // 应用基本信息（头部展示名称/状态；发布成功后 invalidate 刷新状态标签）
  const { data: app } = useQuery({
    queryKey: ['app', appId],
    queryFn: () => appsApi.get(appId),
    enabled: Number.isFinite(appId) && appId > 0,
  });

  // ---- 画布 tab：读 localStorage DSL → fromDsl 得到初始节点/边（useMemo 随 appId 重新计算） ----
  const initialCanvas = useMemo(() => {
    const raw = localStorage.getItem(dslKey(appId));
    if (raw) {
      try {
        return fromDsl(JSON.parse(raw));
      } catch {
        // 本地草稿 JSON 损坏：静默回退空画布，避免详情页白屏
      }
    }
    // 无草稿：放一个「开始」节点占位，便于用户从入口开始连线
    return { nodes: [createCanvasNode('start', 0)], edges: [] };
  }, [appId]);

  /** 保存画布：读取画布内部节点/边快照 → toDsl 序列化 → 写回 localStorage */
  const handleSaveCanvas = () => {
    const snapshot = canvasRef.current?.getSnapshot();
    if (!snapshot) return;
    localStorage.setItem(dslKey(appId), JSON.stringify(toDsl(snapshot.nodes, snapshot.edges)));
    message.success('画布已保存（V1 本地草稿）');
  };

  /** 左侧面板点击 → 让画布追加一个新节点 */
  const handleAddNode = (type: PaletteItem['type']) => canvasRef.current?.addNode(type);

  // ---- 模型配置 tab：读写 localStorage；切换应用时重置为对应草稿 ----
  const [provider, setProvider] = useState<string>();
  const [model, setModel] = useState<string>();

  useEffect(() => {
    const raw = localStorage.getItem(modelKey(appId));
    if (raw) {
      try {
        const cfg = JSON.parse(raw) as { provider: string; model: string };
        setProvider(cfg.provider);
        setModel(cfg.model);
        return;
      } catch {
        // 草稿损坏：按无草稿处理
      }
    }
    setProvider(undefined);
    setModel(undefined);
  }, [appId]);

  const handleSaveModel = () => {
    if (!provider || !model) {
      message.warning('请选择 provider 与 model');
      return;
    }
    localStorage.setItem(modelKey(appId), JSON.stringify({ provider, model }));
    message.success('模型配置已保存（V1 本地草稿）');
  };

  // ---- API Key tab：生成（明文仅显示一次）+ 列表 + 吊销 ----
  const [genOpen, setGenOpen] = useState(false);
  const [genForm] = Form.useForm<{ name: string }>();
  const [plainKey, setPlainKey] = useState<string | null>(null);

  const { data: apiKeys = [], isLoading: keysLoading } = useQuery({
    queryKey: ['api-keys', appId],
    queryFn: () => appsApi.listApiKeys(appId),
    enabled: Number.isFinite(appId) && appId > 0,
  });

  const generateMutation = useMutation({
    mutationFn: (name: string) => appsApi.generateApiKey(appId, name),
    onSuccess: (key) => {
      queryClient.invalidateQueries({ queryKey: ['api-keys', appId] });
      // 陈旧闭包守卫：请求发出后若已切到其他应用（/apps/:id 复用组件实例），丢弃展示态，
      // 避免 app1 的明文 Key 泄漏显示在 app2 页面；invalidate 保留（按 queryKey 精确刷新，无副作用）
      if (appIdRef.current !== appId) return;
      setPlainKey(key.plainKey); // 明文仅本次生成响应返回，故只在页面上显示一次
      setGenOpen(false);
      genForm.resetFields();
      message.success('API Key 已生成');
    },
    onError: (e: Error) => message.error(e.message || '生成失败'),
  });

  const revokeMutation = useMutation({
    mutationFn: (keyId: number) => appsApi.revokeApiKey(appId, keyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['api-keys', appId] });
      message.success('已吊销');
    },
    onError: (e: Error) => message.error(e.message || '吊销失败'),
  });

  const keyColumns: ColumnsType<ApiKeyItem> = [
    { title: '名称', dataIndex: 'name' },
    { title: '创建时间', dataIndex: 'createTime', render: (t?: string) => formatTime(t) },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        // 吊销按钮按 apikey:manage 权限过滤
        <Perm perm="apikey:manage">
          <Popconfirm
            title="确认吊销该 API Key？"
            description="吊销后立即失效且不可恢复"
            onConfirm={() => revokeMutation.mutate(record.id)}
          >
            <Button type="link" size="small" danger>
              吊销
            </Button>
          </Popconfirm>
        </Perm>
      ),
    },
  ];

  // ---- 发布 tab：调用发布接口，展示返回版本号 ----
  const [publishResult, setPublishResult] = useState<AppVersion | null>(null);

  // 切换应用（/apps/:id 复用同一组件实例）时重置本次会话态：明文 Alert、发布结果、生成弹窗。
  // 画布用 key={appId} 强制重挂载解决同类问题，这些页面级 state 需手动复位，避免残留上一应用的展示。
  useEffect(() => {
    setPlainKey(null);
    setPublishResult(null);
    setGenOpen(false);
    genForm.resetFields();
  }, [appId]);

  const publishMutation = useMutation({
    mutationFn: () => appsApi.publish(appId),
    onSuccess: (version) => {
      queryClient.invalidateQueries({ queryKey: ['app', appId] }); // 刷新头部状态标签
      // 陈旧闭包守卫：已切走应用则不展示发布结果，避免 app1 的发布成功提示残留到 app2 页面
      if (appIdRef.current !== appId) return;
      setPublishResult(version);
      message.success(`发布成功，版本号：v${version.versionNo}`);
    },
    onError: (e: Error) => message.error(e.message || '发布失败'),
  });

  return (
    <Card>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {app ? app.name : `应用详情 #${appId}`}
        </Typography.Title>
        {app && <Tag color={STATUS_MAP[app.status]?.color}>{STATUS_MAP[app.status]?.text ?? '未知'}</Tag>}
      </div>

      <Tabs
        items={[
          {
            key: 'canvas',
            label: '画布',
            children: (
              <div style={{ display: 'flex', gap: 16 }}>
                <NodePalette onAdd={handleAddNode} />
                <div style={{ flex: 1 }}>
                  <div style={{ marginBottom: 8, textAlign: 'right' }}>
                    <Button type="primary" onClick={handleSaveCanvas}>
                      保存画布
                    </Button>
                  </div>
                  {/* key=appId：useNodesState 只在首次挂载读 initialNodes，切换应用必须强制重挂载画布 */}
                  <FlowCanvas
                    key={appId}
                    ref={canvasRef}
                    initialNodes={initialCanvas.nodes}
                    initialEdges={initialCanvas.edges}
                  />
                </div>
              </div>
            ),
          },
          {
            key: 'model',
            label: '模型配置',
            children: (
              <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }} size="middle">
                <Typography.Paragraph type="secondary">
                  V1 简化配置：仅选 provider 与 model，保存为本地草稿（zhijin_model_{appId}），暂不上送后端。
                </Typography.Paragraph>
                <Space>
                  <Select
                    placeholder="Provider"
                    style={{ width: 160 }}
                    value={provider}
                    onChange={(v) => {
                      setProvider(v);
                      setModel(undefined); // 切换 provider 后旧 model 不再适用，重置待选
                    }}
                    options={PROVIDER_OPTIONS}
                  />
                  <Select
                    placeholder="Model"
                    style={{ width: 200 }}
                    value={model}
                    onChange={setModel}
                    disabled={!provider}
                    options={(provider ? PROVIDER_MODELS[provider] : []).map(m => ({ value: m, label: m }))}
                  />
                  <Button type="primary" onClick={handleSaveModel}>
                    保存
                  </Button>
                </Space>
              </Space>
            ),
          },
          {
            key: 'apikey',
            label: 'API Key',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography.Text type="secondary">
                    用于开放 API /v1 鉴权；明文仅在生成时显示一次，DB 只存哈希。
                  </Typography.Text>
                  {/* 生成 API Key 按钮按 apikey:manage 权限过滤 */}
                  <Perm perm="apikey:manage">
                    <Button type="primary" onClick={() => setGenOpen(true)}>
                      生成 API Key
                    </Button>
                  </Perm>
                </div>
                {plainKey && (
                  <Alert
                    type="warning"
                    showIcon
                    closable
                    onClose={() => setPlainKey(null)}
                    message="请立即保存该 API Key"
                    description={
                      <>
                        <Typography.Text code>{plainKey}</Typography.Text> —— 明文仅显示一次，关闭后无法再次查看。
                      </>
                    }
                  />
                )}
                <Table rowKey="id" columns={keyColumns} dataSource={apiKeys} loading={keysLoading} pagination={false} />
              </Space>
            ),
          },
          {
            key: 'publish',
            label: '发布',
            children: (
              <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }} size="middle">
                <Typography.Paragraph type="secondary">
                  发布将固化当前版本快照（版本号自增），并把应用状态更新为「已发布」；V1 画布 DSL 仍为前端本地草稿。
                </Typography.Paragraph>
                <div>
                  {/* 发布按钮按 app:publish 权限过滤 */}
                  <Perm perm="app:publish">
                    <Button type="primary" loading={publishMutation.isPending} onClick={() => publishMutation.mutate()}>
                      发布
                    </Button>
                  </Perm>
                </div>
                {publishResult && (
                  <Alert
                    type="success"
                    showIcon
                    message={`发布成功，版本号：v${publishResult.versionNo}`}
                    description={`版本 ID：${publishResult.id}`}
                  />
                )}
              </Space>
            ),
          },
        ]}
      />

      {/* 生成 API Key 弹窗：输入名称 → 调用生成接口 → 明文在页面上方 Alert 一次性展示 */}
      <Modal
        title="生成 API Key"
        open={genOpen}
        onOk={() => genForm.submit()}
        confirmLoading={generateMutation.isPending}
        onCancel={() => setGenOpen(false)}
        destroyOnClose
      >
        <Form form={genForm} layout="vertical" onFinish={(v) => generateMutation.mutate(v.name)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入 Key 名称' }]}>
            <Input placeholder="如：生产环境" maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
