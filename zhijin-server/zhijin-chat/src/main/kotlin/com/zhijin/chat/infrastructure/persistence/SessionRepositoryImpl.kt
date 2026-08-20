package com.zhijin.chat.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.chat.domain.session.ChatMessage
import com.zhijin.chat.domain.session.ChatSession
import com.zhijin.chat.domain.session.SessionRepository
import com.zhijin.chat.mapper.ChatMessageMapper
import com.zhijin.chat.mapper.ChatSessionMapper
import org.springframework.stereotype.Repository

/**
 * 会话仓储实现：基于 MyBatis-Plus Mapper，持久化记录与领域实体互转。
 * Mapper 保留在 com.zhijin.chat.mapper（@MapperScan 硬编码该路径，不移动）。
 *
 * 由原 service/SessionService 迁移而来，职责不变：创建/追加消息/取历史。
 */
@Repository
class SessionRepositoryImpl(
    private val sessionMapper: ChatSessionMapper,
    private val messageMapper: ChatMessageMapper,
) : SessionRepository {

    /**
     * 创建会话：insert 后由 MyBatis-Plus 回填自增 id 到记录，再转领域实体返回，
     * 保证调用方拿到携带 id 的会话（后续 appendMessage 需要 sessionId）。
     */
    override fun create(session: ChatSession): ChatSession {
        val record = ChatSessionRecord.from(session)
        sessionMapper.insert(record)
        return record.toDomain()
    }

    /** 追加消息：记录租户字段留空，由租户拦截器从上下文自动填充。 */
    override fun appendMessage(msg: ChatMessage) {
        messageMapper.insert(ChatMessageRecord.from(msg))
    }

    /** 取历史：显式 tenantId + 租户拦截器双重隔离，按 id 升序保证对话顺序。 */
    override fun history(tenantId: Long, sessionId: Long): List<ChatMessage> =
        messageMapper.selectList(
            QueryWrapper<ChatMessageRecord>()
                .eq("session_id", sessionId)
                .eq("tenant_id", tenantId)
                .orderByAsc("id")
        ).map { it.toDomain() }
}
