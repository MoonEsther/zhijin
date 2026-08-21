import { request } from './client';

// 审计日志项（与后端 AuditLogResponse 字段一一对应；可选字段后端可能为 null）
export interface AuditItem {
  id?: number;
  userId?: number;
  username: string;
  action: string;
  targetType: string;
  targetId?: number;
  detail: string;
  createTime?: string;
}

// 分页结果（与后端 PageResultResponse<AuditLogResponse> 结构一致）
export interface AuditPage {
  items: AuditItem[];
  total: number;
}

/** 审计日志资源 API（/api/audit-logs，request 已解包 Result<T> 拿到 data）。 */
export const auditApi = {
  /** 分页查询审计日志（page 从 1 起，size 默认 20）。 */
  page: (page: number, size: number) => request<AuditPage>(`/audit-logs?page=${page}&size=${size}`),
};
