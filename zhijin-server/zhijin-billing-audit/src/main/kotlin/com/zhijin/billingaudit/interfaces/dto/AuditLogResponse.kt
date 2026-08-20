package com.zhijin.billingaudit.interfaces.dto

import com.zhijin.billingaudit.domain.audit.AuditLog
import java.time.LocalDateTime

/** 审计日志响应 DTO（管理端查询返回，不直接暴露领域对象）。 */
data class AuditLogResponse(
    val id: Long?,
    val userId: Long?,
    val username: String,
    val action: String,
    val targetType: String,
    val targetId: Long?,
    val detail: String,
    val createTime: LocalDateTime?,
) {
    companion object {
        /** 领域审计日志 → 响应 DTO 的薄映射（映射逻辑收敛在 DTO 伴生对象）。 */
        fun toResponse(log: AuditLog): AuditLogResponse = AuditLogResponse(
            id = log.id,
            userId = log.userId,
            username = log.username,
            action = log.action,
            targetType = log.targetType,
            targetId = log.targetId,
            detail = log.detail,
            createTime = log.createTime,
        )
    }
}
