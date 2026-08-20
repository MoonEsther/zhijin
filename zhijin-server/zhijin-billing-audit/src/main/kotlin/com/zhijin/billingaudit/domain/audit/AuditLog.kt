package com.zhijin.billingaudit.domain.audit

import java.time.LocalDateTime

/** 审计记录（每次管理操作一行）。 */
data class AuditLog(
    val id: Long? = null,
    val tenantId: Long,
    val userId: Long? = null,
    val username: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: Long? = null,
    val detail: String = "",
    val createTime: LocalDateTime? = null,
)
