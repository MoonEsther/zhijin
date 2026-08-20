package com.zhijin.billingaudit.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.billingaudit.domain.usage.UsageRecord
import com.zhijin.billingaudit.domain.usage.UsageRepository
import com.zhijin.billingaudit.domain.usage.UsageSummary
import com.zhijin.billingaudit.mapper.UsageRecordMapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** 用量仓储实现：基于 MyBatis-Plus Mapper，记录与领域实体互转。 */
@Repository
class UsageRepositoryImpl(private val mapper: UsageRecordMapper) : UsageRepository {

    override fun save(record: UsageRecord): UsageRecord {
        val rec = UsageRecordRecord.from(record)
        mapper.insert(rec)
        return rec.toDomain()
    }

    override fun summarizeByApp(tenantId: Long, start: LocalDateTime?, end: LocalDateTime?): List<UsageSummary> {
        // 简化：按 app 分组统计调用次数与 token 总量；V1 量小可接受，量大后换 SQL group by
        val qw = QueryWrapper<UsageRecordRecord>().eq("tenant_id", tenantId)
        if (start != null) qw.ge("create_time", start)
        if (end != null) qw.le("create_time", end)
        return mapper.selectList(qw).groupBy { it.appId }.map { (appId, rows) ->
            UsageSummary(appId = appId!!, totalCalls = rows.size, totalTokens = rows.sumOf { it.totalTokens })
        }
    }
}
