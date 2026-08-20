package com.zhijin.billingaudit.domain.usage

import java.time.LocalDateTime

/** 用量记录（每次模型调用一行）。 */
data class UsageRecord(
    val id: Long? = null,
    val tenantId: Long,
    val appId: Long,
    val sessionId: Long? = null,
    val model: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val latencyMs: Int = 0,
    val createTime: LocalDateTime? = null,
)

/** 用量汇总（管理端查询结果）。 */
data class UsageSummary(
    val appId: Long,
    val totalCalls: Int,
    val totalTokens: Int,
)
