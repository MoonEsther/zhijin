package com.zhijin.chat.domain.session

/**
 * 会话消息领域实体（纯 Kotlin，无框架依赖）。
 *
 * tenant_id 不进入领域实体——领域消息仅关注会话内语义（归属哪个会话、什么角色、什么内容），
 * 租户归属属于基础设施关注点，落库时由 MyBatis-Plus 租户拦截器自动填充（见 ChatMessageRecord）。
 */
data class ChatMessage(
    val id: Long?,
    val sessionId: Long?,
    val role: String,
    val content: String,
)
