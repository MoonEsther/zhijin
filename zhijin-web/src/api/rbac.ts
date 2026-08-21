import { request } from './client';

// ---- RBAC 响应体（与后端 zhijin-auth interfaces/dto 字段一一对应）----
/** 权限点（/api/rbac/permissions，字典项，供角色配置勾选）。 */
export interface PermissionItem {
  id: number;
  permCode: string;
  permName: string;
}
/** 角色（含已绑定的权限点编码）。 */
export interface RoleItem {
  id: number;
  roleCode: string;
  roleName: string;
  perms: string[];
}
/** 管理端用户列表项：用户基本信息 + 已绑定的角色 ID 与角色名（分配角色弹窗回显/当前角色列用）。 */
export interface RbacUser {
  id: number;
  username: string;
  nickname: string;
  status: number; // 1=启用，其余为禁用（与后端 User.status 语义一致）
  orgId: number | null;
  roleIds: number[];
  /** 用户当前角色名（后端 /api/rbac/users 内嵌返回，供仅有 user:manage 的管理员展示角色）。 */
  roleNames: string[];
}

/** 角色请求体：新建/更新角色时提交（perms 为权限点编码列表）。 */
export interface RolePayload {
  roleCode: string;
  roleName: string;
  perms: string[];
}

/** RBAC 管理 API（/api/rbac，request 已解包 Result<T> 拿到 data；端点均有 @PreAuthorize 鉴权）。 */
export const rbacApi = {
  /** 权限点字典：role:manage 或 user:manage 任一即可查看。 */
  permissions: () => request<PermissionItem[]>('/rbac/permissions'),
  /** 角色列表（含权限点），需 role:manage。 */
  roles: () => request<RoleItem[]>('/rbac/roles'),
  /** 新建角色（覆盖式设置权限点）。 */
  createRole: (data: RolePayload) =>
    request<RoleItem>('/rbac/roles', { method: 'POST', body: JSON.stringify(data) }),
  /** 更新角色（含权限点重设）。 */
  updateRole: (id: number, data: RolePayload) =>
    request<RoleItem>(`/rbac/roles/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  /** 删除角色（后端级联清理角色-权限/用户-角色关联）。 */
  deleteRole: (id: number) => request<void>(`/rbac/roles/${id}`, { method: 'DELETE' }),
  /** 用户列表（含已绑角色 ID），需 user:manage。 */
  users: () => request<RbacUser[]>('/rbac/users'),
  /** 重设用户绑定的角色（全量覆盖）。 */
  assignRoles: (userId: number, roleIds: number[]) =>
    request<void>(`/rbac/users/${userId}/roles`, { method: 'PUT', body: JSON.stringify({ roleIds }) }),
};
