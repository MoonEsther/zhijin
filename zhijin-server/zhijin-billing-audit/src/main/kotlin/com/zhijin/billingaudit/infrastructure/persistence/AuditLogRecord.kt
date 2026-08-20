package com.zhijin.billingaudit.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.billingaudit.domain.audit.AuditLog
import java.time.LocalDateTime

/** 审计日志持久化记录（贫血，仅 infrastructure 用）。 */
@TableName("audit_log")
data class AuditLogRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,          // var：MyBatis-Plus 自增主键靠 setter 回填
    var tenantId: Long? = null,
    var userId: Long? = null,
    var username: String = "",
    var action: String = "",
    var targetType: String = "",
    var targetId: Long? = null,
    var detail: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
) {
    fun toDomain(): AuditLog = AuditLog(
        id = id, tenantId = tenantId!!, userId = userId, username = username,
        action = action, targetType = targetType, targetId = targetId,
        detail = detail, createTime = createTime,
    )

    companion object {
        fun from(log: AuditLog): AuditLogRecord = AuditLogRecord(
            id = log.id, tenantId = log.tenantId, userId = log.userId, username = log.username,
            action = log.action, targetType = log.targetType, targetId = log.targetId,
            detail = log.detail, createTime = log.createTime,
        )
    }
}
