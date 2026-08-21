import { useState } from 'react';
import { Button, Card, Checkbox, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { rbacApi, type RoleItem } from '../api/rbac';
import { Perm } from '../auth/Perm';
import { userStore } from '../auth/userStore';

// 角色表单模型（perms 可能未勾选任何项，运行期为 undefined）
interface RoleFormValues {
  roleCode: string;
  roleName: string;
  perms?: string[];
}

/** 角色管理页：角色列表 + 新建/编辑/删除。整体由 <Perm perm="role:manage"> 包裹，无权限不渲染。 */
export function RoleManagePage() {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<RoleFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  // editingId 非空 = 编辑模式（复用同一 Modal）
  const [editingId, setEditingId] = useState<number | null>(null);

  // 无权限时不发请求（enabled 兜底）
  const hasPerm = userStore.hasPerm('role:manage');
  const { data: roles = [], isLoading } = useQuery({
    queryKey: ['roles'],
    queryFn: rbacApi.roles,
    enabled: hasPerm,
  });
  // 权限点字典：作为角色表单 Checkbox.Group 的选项
  const { data: permissions = [] } = useQuery({
    queryKey: ['rbac-permissions'],
    queryFn: rbacApi.permissions,
    enabled: hasPerm,
  });

  // 新建角色（覆盖式设置权限点）
  const createMutation = useMutation({
    mutationFn: (values: RoleFormValues) =>
      rbacApi.createRole({ roleCode: values.roleCode, roleName: values.roleName, perms: values.perms ?? [] }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      message.success('创建成功');
      closeModal();
    },
    onError: (e: Error) => message.error(e.message || '创建失败'),
  });

  // 编辑角色（含权限点重设）
  const updateMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: RoleFormValues }) =>
      rbacApi.updateRole(id, { roleCode: values.roleCode, roleName: values.roleName, perms: values.perms ?? [] }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      message.success('保存成功');
      closeModal();
    },
    onError: (e: Error) => message.error(e.message || '保存失败'),
  });

  // 删除角色（后端级联清理关联）
  const deleteMutation = useMutation({
    mutationFn: rbacApi.deleteRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      message.success('删除成功');
    },
    onError: (e: Error) => message.error(e.message || '删除失败'),
  });

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: RoleItem) => {
    setEditingId(record.id);
    form.setFieldsValue({ roleCode: record.roleCode, roleName: record.roleName, perms: record.perms });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    form.resetFields();
  };

  // 提交：编辑模式走 update，否则走 create
  const onSubmit = (values: RoleFormValues) => {
    if (editingId != null) updateMutation.mutate({ id: editingId, values });
    else createMutation.mutate(values);
  };

  const columns: ColumnsType<RoleItem> = [
    { title: '角色编码', dataIndex: 'roleCode', width: 160 },
    { title: '角色名称', dataIndex: 'roleName', width: 160 },
    {
      title: '权限点',
      dataIndex: 'perms',
      render: (perms: string[]) => perms.map((p) => <Tag key={p}>{p}</Tag>),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该角色？"
            description="删除后不可恢复，且级联清理角色-权限/用户-角色关联"
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Perm perm="role:manage">
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            角色管理
          </Typography.Title>
          <Button type="primary" onClick={openCreate}>
            新建角色
          </Button>
        </div>
        <Table rowKey="id" columns={columns} dataSource={roles} loading={isLoading} />

        {/* 新建/编辑角色弹窗：编码 + 名称 + 权限点多选 */}
        <Modal
          title={editingId != null ? '编辑角色' : '新建角色'}
          open={modalOpen}
          onOk={() => form.submit()}
          confirmLoading={createMutation.isPending || updateMutation.isPending}
          onCancel={closeModal}
          destroyOnClose
        >
          <Form form={form} layout="vertical" onFinish={onSubmit}>
            <Form.Item name="roleCode" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
              <Input placeholder="如：ops" maxLength={64} />
            </Form.Item>
            <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
              <Input placeholder="如：运维" maxLength={64} />
            </Form.Item>
            <Form.Item name="perms" label="权限点">
              <Checkbox.Group
                options={permissions.map((p) => ({ label: `${p.permName}（${p.permCode}）`, value: p.permCode }))}
              />
            </Form.Item>
          </Form>
        </Modal>
      </Card>
    </Perm>
  );
}
