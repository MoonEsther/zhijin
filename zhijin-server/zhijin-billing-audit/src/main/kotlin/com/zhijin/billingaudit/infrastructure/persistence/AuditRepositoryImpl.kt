package com.zhijin.billingaudit.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zhijin.billingaudit.domain.audit.AuditLog
import com.zhijin.billingaudit.domain.audit.AuditRepository
import com.zhijin.billingaudit.domain.audit.PageResult
import com.zhijin.billingaudit.mapper.AuditLogMapper
import org.springframework.stereotype.Repository

/** 审计仓储实现：基于 MyBatis-Plus Mapper，分页细节仅在基础设施层，domain 不感知 IPage。 */
@Repository
class AuditRepositoryImpl(private val mapper: AuditLogMapper) : AuditRepository {

    override fun save(log: AuditLog): AuditLog {
        val rec = AuditLogRecord.from(log)
        mapper.insert(rec)
        return rec.toDomain()
    }

    override fun page(tenantId: Long, page: Int, size: Int): PageResult<AuditLog> {
        val qw = QueryWrapper<AuditLogRecord>().eq("tenant_id", tenantId).orderByDesc("id")
        val p = mapper.selectPage(Page<AuditLogRecord>(page.toLong(), size.toLong()), qw)
        return PageResult(items = p.records.map { it.toDomain() }, total = p.total)
    }
}
