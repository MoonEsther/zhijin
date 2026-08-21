import { tokenStore } from '../auth/tokenStore';

// API 基础路径：全部业务接口挂在 /api 下（vite 代理到 8080）
const BASE = '/api';

/**
 * 统一请求封装：自动携带 Bearer token，401 清除登录态跳登录页，
 * 并按后端 Result<T> 结构（{ code, message, data }）解包，code !== 0 抛错。
 */
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStore.get();
  const resp = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (resp.status === 401) {
    // token 失效：清空本地登录态并回到登录页（全站唯一处理点）
    tokenStore.clear();
    window.location.href = '/login';
    throw new Error('未认证');
  }
  const body = await resp.json();
  // Result<T> 解包：{ code, message, data }
  if (body.code !== 0) throw new Error(body.message || '请求失败');
  return body.data as T;
}
