package com.zhijin.billingaudit.domain.usage

import java.time.LocalDateTime

/** 用量仓储接口。 */
interface UsageRepository {
    fun save(record: UsageRecord): UsageRecord
    fun summarizeByApp(tenantId: Long, start: LocalDateTime?, end: LocalDateTime?): List<UsageSummary>
}

/** 用量记录端口（供 chat 流程依赖倒置注入，避免模块循环依赖）。 */
fun interface UsageRecorder {
    fun record(record: UsageRecord)
}
