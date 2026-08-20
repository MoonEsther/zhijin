package com.zhijin.chat.domain.session

/**
 * 会话领域实体（富血模型，纯 Kotlin）。
 *
 * 设计说明：
 * - 不依赖任何 Spring / MyBatis 注解，仅承载领域状态与领域规则；
 * - 持久化列 create_time/update_time 属基础设施关注点，不进入领域实体（见 ChatSessionRecord）；
 * - appendMessage 是领域行为：生成一条领域消息对象，落库由应用服务经 SessionRepository 完成。
 */
data class ChatSession(
    val id: Long?,
    val tenantId: Long,
    val appId: Long,
    val title: String,
) {
    /**
     * 追加一条会话消息（领域行为）：按会话语义组装一条领域消息，不直接持久化。
     * 消息的持久化由调用方（应用服务）经 [SessionRepository.appendMessage] 完成。
     */
    fun appendMessage(role: String, content: String): ChatMessage = ChatMessage(
        id = null,
        sessionId = id,
        role = role,
        content = content,
    )
}
