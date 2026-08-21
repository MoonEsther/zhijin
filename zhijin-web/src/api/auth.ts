import { tokenStore } from '../auth/tokenStore';
import type { UserInfo } from '../auth/userStore';

/**
 * 拉取当前用户身份 + 权限点（/auth/validate）。
 * 注意：该端点后端是 @RequestMapping("/auth")，**不走 /api BASE**（R1：路径是 /auth/validate 不是 /api/auth/validate），
 * 因此这里不复用 request<T>，而是直接 fetch 并携带 Bearer；401 统一清登录态跳登录页。
 */
export async function fetchUserInfo(): Promise<UserInfo> {
  const resp = await fetch('/auth/validate', {
    headers: { Authorization: `Bearer ${tokenStore.get()}` },
  });
  if (resp.status === 401) {
    // token 失效：清空本地登录态并回到登录页
    tokenStore.clear();
    window.location.href = '/login';
    throw new Error('未认证');
  }
  // 非 2xx（如 502/503 网关 HTML 错误页）：不满足 Result 结构，直接抛错避免 resp.json() 抛 SyntaxError 被静默吞掉
  if (!resp.ok) throw new Error(`获取用户信息失败：HTTP ${resp.status}`);
  const body = await resp.json();
  // 后端统一 Result<T> 包装：{ code, message, data }，code !== 0 视为失败
  if (body.code !== 0) throw new Error(body.message || '获取用户信息失败');
  return body.data as UserInfo;
}
