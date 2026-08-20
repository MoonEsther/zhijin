package com.zhijin.billingaudit.interfaces.dto

import com.zhijin.billingaudit.domain.usage.UsageSummary

/** 用量汇总响应 DTO（管理端查询返回，不直接暴露领域对象）。 */
data class UsageSummaryResponse(
    val appId: Long,
    val totalCalls: Int,
    val totalTokens: Int,
) {
    companion object {
        /** 领域汇总 → 响应 DTO 的薄映射（映射逻辑收敛在 DTO 伴生对象）。 */
        fun toResponse(summary: UsageSummary): UsageSummaryResponse = UsageSummaryResponse(
            appId = summary.appId,
            totalCalls = summary.totalCalls,
            totalTokens = summary.totalTokens,
        )
    }
}
