package com.zhijin.chat.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.chat.entity.ChatMessage
import com.zhijin.chat.entity.ChatSession
import com.zhijin.chat.mapper.ChatMessageMapper
import com.zhijin.chat.mapper.ChatSessionMapper
import org.springframework.stereotype.Service

/** 会话服务：创建/追加消息/取历史。租户隔离由拦截器自动保证。 */
@Service
class SessionService(
    private val sessionMapper: ChatSessionMapper,
    private val messageMapper: ChatMessageMapper,
) {

    fun createSession(tenantId: Long, appId: Long, title: String = ""): ChatSession {
        val session = ChatSession(tenantId = tenantId, appId = appId, title = title)
        sessionMapper.insert(session)
        return session
    }

    fun appendMessage(tenantId: Long, session: ChatSession, role: String, content: String) {
        val msg = ChatMessage(tenantId = tenantId, sessionId = session.id, role = role, content = content)
        messageMapper.insert(msg)
    }

    fun getHistory(tenantId: Long, session: ChatSession): List<ChatMessage> =
        messageMapper.selectList(
            QueryWrapper<ChatMessage>().eq("session_id", session.id).eq("tenant_id", tenantId).orderByAsc("id")
        )
}
