package com.zhijin.billingaudit.application

import com.zhijin.billingaudit.domain.usage.UsageRecord
import com.zhijin.billingaudit.domain.usage.UsageRepository
import com.zhijin.billingaudit.domain.usage.UsageSummary
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 用量应用服务：对外暴露用量记录写入与汇总查询（仅编排 domain 仓储，不含业务规则）。 */
@Service
class UsageApplicationService(private val usageRepository: UsageRepository) {

    /** 记录一次模型调用用量。 */
    fun record(record: UsageRecord) {
        usageRepository.save(record)
    }

    /** 按应用汇总指定租户在时间窗口内的调用次数与 token 总量。 */
    fun summarize(tenantId: Long, start: LocalDateTime?, end: LocalDateTime?): List<UsageSummary> =
        usageRepository.summarizeByApp(tenantId, start, end)
}
