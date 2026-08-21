import { useEffect, useState } from 'react';
import { Button, Card, Checkbox, Form, Modal, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { rbacApi, type RbacUser } from '../api/rbac';
import { Perm } from '../auth/Perm';
import { userStore } from '../auth/userStore';

// 分配角色弹窗的表单模型（roleIds 可能未勾选任何项，运行期为 undefined）
interface AssignFormValues {
  roleIds?: number[];
}

/** 用户管理页：用户列表 + 分配角色。整体由 <Perm perm="user:manage"> 包裹，无权限不渲染。 */
export function UserManagePage() {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AssignFormValues>();
  // assigning 非空 = 正在给该用户分配角色（弹窗打开）
  const [assigning, setAssigning] = useState<RbacUser | null>(null);

  // 无权限时不发请求（enabled 兜底，页面本身也会整体不渲染）
  const hasPerm = userStore.hasPerm('user:manage');
  const { data: users = [], isLoading } = useQuery({
    queryKey: ['rbac-users'],
    queryFn: rbacApi.users,
    enabled: hasPerm,
  });
  // 角色列表作为分配弹窗的选项（与角色管理页共用 ['roles'] 缓存；
  // 后端 GET /api/rbac/roles 已放宽为 user:manage 或 role:manage 可读）
  const { data: roles = [], isError: rolesError } = useQuery({
    queryKey: ['roles'],
    queryFn: rbacApi.roles,
    enabled: hasPerm,
  });

  // roles 查询失败（网络/后端异常）时提示，避免分配弹窗选项静默为空
  useEffect(() => {
    if (rolesError) message.error('角色列表加载失败，分配角色弹窗选项可能为空');
  }, [rolesError]);

  // 分配角色：全量覆盖用户绑定的角色
  const assignMutation = useMutation({
    mutationFn: ({ userId, roleIds }: { userId: number; roleIds: number[] }) =>
      rbacApi.assignRoles(userId, roleIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rbac-users'] });
      message.success('角色分配成功');
      setAssigning(null);
    },
    onError: (e: Error) => message.error(e.message || '分配失败'),
  });

  const openAssign = (record: RbacUser) => {
    // 回显现有角色：Checkbox.Group 按 roleIds 预勾选
    form.setFieldsValue({ roleIds: record.roleIds });
    setAssigning(record);
  };

  // 用户当前角色列：直接渲染后端内嵌的 roleNames（不再依赖独立 roles 查询，
  // 使仅有 user:manage 无 role:manage 的管理员也能看到用户角色）
  const columns: ColumnsType<RbacUser> = [
    { title: '用户名', dataIndex: 'username', width: 160 },
    { title: '昵称', dataIndex: 'nickname', width: 160 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      // 与后端 User.status 语义一致：1=启用，其余视为禁用
      render: (status: number) => (status === 1 ? <Tag color="success">启用</Tag> : <Tag>禁用</Tag>),
    },
    { title: '当前角色', dataIndex: 'roleNames', render: (roleNames: string[]) => roleNames.join('、') || '-' },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => openAssign(record)}>
          分配角色
        </Button>
      ),
    },
  ];

  return (
    <Perm perm="user:manage">
      <Card>
        <Typography.Title level={4} style={{ marginBottom: 16 }}>
          用户管理
        </Typography.Title>
        <Table rowKey="id" columns={columns} dataSource={users} loading={isLoading} />

        {/* 分配角色弹窗：Checkbox.Group 全量覆盖用户角色 */}
        <Modal
          title={`分配角色：${assigning?.nickname || assigning?.username || ''}`}
          open={assigning != null}
          onOk={() => form.submit()}
          confirmLoading={assignMutation.isPending}
          onCancel={() => setAssigning(null)}
          destroyOnClose
        >
          <Form
            form={form}
            layout="vertical"
            onFinish={(v) => {
              if (assigning) assignMutation.mutate({ userId: assigning.id, roleIds: v.roleIds ?? [] });
            }}
          >
            <Form.Item name="roleIds" label="角色">
              <Checkbox.Group options={roles.map((r) => ({ label: r.roleName, value: r.id }))} />
            </Form.Item>
          </Form>
        </Modal>
      </Card>
    </Perm>
  );
}
