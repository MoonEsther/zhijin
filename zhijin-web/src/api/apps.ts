import { request } from './client';

// 应用/版本/API Key 响应体（与后端 DTO 字段一一对应）
export interface AppItem {
  id: number;
  appKey: string;
  name: string;
  description: string;
  iconUri: string;
  status: number; // 0=草稿 1=已发布 2=已下线（AppStatus.ordinal）
}
export interface AppVersion {
  id: number;
  versionNo: number;
  status: number;
}
export interface ApiKeyResult {
  id: number;
  plainKey: string;
  name: string;
}
// API Key 列表项：不含明文（明文仅生成时返回一次，DB 只存哈希，不可恢复）
export interface ApiKeyItem {
  id: number;
  name: string;
  createTime?: string;
}

/** 应用资源 API（/api/apps，request 已解包 Result<T> 拿到 data）。 */
export const appsApi = {
  list: () => request<AppItem[]>('/apps'),
  create: (data: { name: string; description: string; iconUri: string }) =>
    request<AppItem>('/apps', { method: 'POST', body: JSON.stringify(data) }),
  get: (id: number) => request<AppItem>(`/apps/${id}`),
  update: (id: number, data: { name: string; description: string; iconUri: string }) =>
    request<AppItem>(`/apps/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/apps/${id}`, { method: 'DELETE' }),
  publish: (id: number) => request<AppVersion>(`/apps/${id}/publish`, { method: 'POST' }),
  generateApiKey: (id: number, name: string) =>
    request<ApiKeyResult>(`/apps/${id}/api-keys?name=${encodeURIComponent(name)}`, { method: 'POST' }),
  /** 列出应用下全部 API Key（T3 后端新增 GET /api/apps/{id}/api-keys，不含明文）。 */
  listApiKeys: (id: number) => request<ApiKeyItem[]>(`/apps/${id}/api-keys`),
  revokeApiKey: (id: number, keyId: number) =>
    request<void>(`/apps/${id}/api-keys/${keyId}`, { method: 'DELETE' }),
};
