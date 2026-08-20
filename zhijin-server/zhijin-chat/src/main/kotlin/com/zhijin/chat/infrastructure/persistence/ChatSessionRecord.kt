package com.zhijin.chat.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.chat.domain.session.ChatSession
import java.time.LocalDateTime

/**
 * 会话持久化记录（对应 chat_session 表，贫血模型，仅 infrastructure 使用）。
 *
 * 由原 entity/ChatSession 迁移而来：id 保持 var —— MyBatis-Plus IdType.AUTO 靠反射
 * setter 回填自增主键，val 无 setter 会回填失败。create_time/update_time 为
 * 基础设施审计字段，不进入领域实体 [ChatSession]。
 */
@TableName("chat_session")
data class ChatSessionRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appId: Long? = null,
    var title: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体（tenant_id/app_id 在 chat_session 表必填，缺失属脏数据，抛异常暴露）。 */
    fun toDomain(): ChatSession = ChatSession(
        id = id,
        tenantId = tenantId ?: throw IllegalStateException("会话租户缺失: id=$id"),
        appId = appId ?: throw IllegalStateException("会话应用缺失: id=$id"),
        title = title,
    )

    companion object {
        /** 领域实体 → 持久化记录。 */
        fun from(session: ChatSession): ChatSessionRecord = ChatSessionRecord(
            id = session.id,
            tenantId = session.tenantId,
            appId = session.appId,
            title = session.title,
        )
    }
}
