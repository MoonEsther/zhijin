package com.zhijin.billingaudit.domain.audit

/** 分页结果值对象（domain 层，不依赖 MyBatis-Plus IPage——P5：domain 零框架依赖）。 */
data class PageResult<T>(val items: List<T>, val total: Long)

/** 审计仓储接口（分页返回 domain 类型，不用 MyBatis-Plus IPage/Page）。 */
interface AuditRepository {
    fun save(log: AuditLog): AuditLog
    fun page(tenantId: Long, page: Int, size: Int): PageResult<AuditLog>
}

/** 审计记录端口（供管理操作依赖倒置注入）。 */
fun interface AuditRecorder {
    fun record(log: AuditLog)
}
