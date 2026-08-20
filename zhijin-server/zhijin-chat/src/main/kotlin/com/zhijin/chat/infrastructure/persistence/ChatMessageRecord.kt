package com.zhijin.chat.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.chat.domain.session.ChatMessage
import java.time.LocalDateTime

/**
 * 消息持久化记录（对应 chat_message 表，贫血模型，仅 infrastructure 使用）。
 *
 * 由原 entity/ChatMessage 迁移而来：id 保持 var —— MyBatis-Plus IdType.AUTO 靠反射
 * setter 回填自增主键。tenant_id 不进入领域实体 [ChatMessage]，落库时由租户拦截器
 * 从 TenantContextHolder 自动填充（领域消息仅关注会话内语义）。
 */
@TableName("chat_message")
data class ChatMessageRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var sessionId: Long? = null,
    var role: String = "",
    var content: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体。 */
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
    )

    companion object {
        /** 领域实体 → 持久化记录（tenantId 留空，由租户拦截器填充）。 */
        fun from(msg: ChatMessage): ChatMessageRecord = ChatMessageRecord(
            id = msg.id,
            sessionId = msg.sessionId,
            role = msg.role,
            content = msg.content,
        )
    }
}
