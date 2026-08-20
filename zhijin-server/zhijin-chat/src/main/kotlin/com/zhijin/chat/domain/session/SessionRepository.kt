package com.zhijin.chat.domain.session

/**
 * 会话仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 *
 * 返回领域实体 [ChatSession]/[ChatMessage] 而非持久化记录，调用方不感知 MyBatis-Plus。
 */
interface SessionRepository {

    /** 创建会话，返回携带自增 id 的领域实体（id 由持久化层回填）。 */
    fun create(session: ChatSession): ChatSession

    /** 追加一条消息（tenant_id 由租户拦截器自动填充，领域消息不带租户字段）。 */
    fun appendMessage(msg: ChatMessage)

    /** 按会话取历史消息（按 id 升序）；tenantId 显式传参 + 租户拦截器双重保证隔离。 */
    fun history(tenantId: Long, sessionId: Long): List<ChatMessage>
}
