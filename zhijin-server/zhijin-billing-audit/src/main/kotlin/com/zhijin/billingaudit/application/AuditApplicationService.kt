package com.zhijin.billingaudit.application

import com.zhijin.billingaudit.domain.audit.AuditLog
import com.zhijin.billingaudit.domain.audit.AuditRepository
import com.zhijin.billingaudit.domain.audit.PageResult
import org.springframework.stereotype.Service

/** 审计应用服务：对外暴露审计记录写入与分页查询（仅编排 domain 仓储，不含业务规则）。 */
@Service
class AuditApplicationService(private val auditRepository: AuditRepository) {

    /** 记录一次管理操作审计。 */
    fun record(log: AuditLog) {
        auditRepository.save(log)
    }

    /** 分页查询指定租户的审计日志（倒序，新记录在前）。 */
    fun page(tenantId: Long, page: Int, size: Int): PageResult<AuditLog> =
        auditRepository.page(tenantId, page, size)
}
