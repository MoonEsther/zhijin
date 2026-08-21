import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppItem, appsApi } from '../api/apps';
import { Perm } from '../auth/Perm';

// 状态 → 标签映射：0=草稿 1=已发布 2=已下线（与后端 AppStatus.ordinal 一致）
const STATUS_MAP: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'success', text: '已发布' },
  2: { color: 'error', text: '已下线' },
};

// 新建/编辑表单模型（iconUri 后端允许空，仅 name 必填）
interface AppFormValues {
  name: string;
  description?: string;
  iconUri?: string;
}

/** 应用列表页：查询 + 新建/编辑 Modal + 删除 Popconfirm，TanStack Query 管理服务端状态。 */
export function AppListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AppFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  // editingId 非空 = 编辑模式（复用同一 Modal）
  const [editingId, setEditingId] = useState<number | null>(null);

  // 应用列表（queryKey: ['apps']，增删改成功后 invalidate 自动重新拉取）
  const { data: apps = [], isLoading } = useQuery({ queryKey: ['apps'], queryFn: appsApi.list });

  // 新建应用
  const createMutation = useMutation({
    mutationFn: appsApi.create,
    onSuccess: () => {
      // D3：TanStack Query v5 必须用对象形式 invalidateQueries
      queryClient.invalidateQueries({ queryKey: ['apps'] });
      message.success('创建成功');
      closeModal();
    },
    onError: (e: Error) => message.error(e.message || '创建失败'),
  });

  // 编辑应用（data 与 appsApi.update 一致：三个字段均为必填字符串）
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { name: string; description: string; iconUri: string } }) =>
      appsApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['apps'] });
      message.success('保存成功');
      closeModal();
    },
    onError: (e: Error) => message.error(e.message || '保存失败'),
  });

  // 删除应用
  const deleteMutation = useMutation({
    mutationFn: appsApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['apps'] });
      message.success('删除成功');
    },
    onError: (e: Error) => message.error(e.message || '删除失败'),
  });

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: AppItem) => {
    setEditingId(record.id);
    form.setFieldsValue({ name: record.name, description: record.description, iconUri: record.iconUri });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    form.resetFields();
  };

  // 提交：编辑模式走 update，否则走 create
  const onSubmit = (values: AppFormValues) => {
    const data = { name: values.name, description: values.description ?? '', iconUri: values.iconUri ?? '' };
    if (editingId != null) updateMutation.mutate({ id: editingId, data });
    else createMutation.mutate(data);
  };

  const columns: ColumnsType<AppItem> = [
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: number) => {
        const tag = STATUS_MAP[status];
        return <Tag color={tag?.color}>{tag?.text ?? '未知'}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => navigate(`/apps/${record.id}`)}>
            详情
          </Button>
          {/* 编辑/删除按钮按权限点过滤（无权限不渲染） */}
          <Perm perm="app:update">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          </Perm>
          <Perm perm="app:delete">
            <Popconfirm title="确认删除该应用？" description="删除后不可恢复" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Perm>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          应用列表
        </Typography.Title>
        {/* 新建应用按钮按 app:create 权限过滤 */}
        <Perm perm="app:create">
          <Button type="primary" onClick={openCreate}>
            新建应用
          </Button>
        </Perm>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={apps}
        loading={isLoading}
        // 点击行跳转应用详情
        onRow={(record) => ({ onClick: () => navigate(`/apps/${record.id}`), style: { cursor: 'pointer' } })}
      />

      <Modal
        title={editingId != null ? '编辑应用' : '新建应用'}
        open={modalOpen}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        onCancel={closeModal}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入应用名称' }]}>
            <Input placeholder="应用名称" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="应用描述" rows={3} />
          </Form.Item>
          <Form.Item name="iconUri" label="图标 URL">
            <Input placeholder="https://..." />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
