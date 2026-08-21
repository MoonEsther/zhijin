import { userStore } from './userStore';

/**
 * 权限点控制组件：拥有 perm 权限时才渲染 children，否则返回 null（隐藏而非禁用）。
 * 依赖 userStore.hasPerm 同步读取 localStorage；父组件重渲染（路由切换等）时自然刷新。
 */
export function Perm({ perm, children }: { perm: string; children: React.ReactNode }) {
  return userStore.hasPerm(perm) ? <>{children}</> : null;
}
