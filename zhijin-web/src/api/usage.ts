import { request } from './client';

// 用量汇总项（与后端 UsageSummaryResponse 字段一一对应）
export interface UsageSummary {
  appId: number;
  totalCalls: number;
  totalTokens: number;
}

/** 用量资源 API（/api/usage，request 已解包 Result<T> 拿到 data）。 */
export const usageApi = {
  /** 用量汇总：按应用维度统计调用量与 Token 消耗（时间范围可选，默认全量）。 */
  summary: () => request<UsageSummary[]>('/usage/summary'),
};
