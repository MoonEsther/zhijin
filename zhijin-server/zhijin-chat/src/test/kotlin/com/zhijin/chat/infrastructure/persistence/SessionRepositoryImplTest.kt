package com.zhijin.chat.infrastructure.persistence

import com.zhijin.chat.domain.session.ChatMessage
import com.zhijin.chat.domain.session.ChatSession
import com.zhijin.chat.mapper.ChatMessageMapper
import com.zhijin.chat.mapper.ChatSessionMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * SessionRepositoryImpl 测试：基于 MyBatis-Plus Mapper 的会话/消息持久化。
 *
 * 由原 SessionServiceTest 迁移而来：验证创建会话回填 id、追加消息、取历史三条路径，
 * Mapper 均以 Mockito 模拟。
 */
class SessionRepositoryImplTest {

    private val sessionMapper = mock(ChatSessionMapper::class.java)
    private val messageMapper = mock(ChatMessageMapper::class.java)
    private val repo = SessionRepositoryImpl(sessionMapper, messageMapper)

    /** 模拟 MyBatis-Plus 自增主键回填：insert 时给记录 id 赋值。 */
    private fun backfillSessionId() = Answer<Int> { inv: InvocationOnMock ->
        inv.getArgument<ChatSessionRecord>(0).id = 1L
        1
    }

    @Test
    fun `创建会话返回带id会话`() {
        `when`(sessionMapper.insert(any(ChatSessionRecord::class.java))).thenAnswer(backfillSessionId())
        val s = repo.create(ChatSession(id = null, tenantId = 1L, appId = 1L, title = "售前"))
        assertNotNull(s.id)
        assertEquals("售前", s.title)
    }

    @Test
    fun `追加消息后可取回`() {
        repo.appendMessage(ChatMessage(id = null, sessionId = 1L, role = "user", content = "你好"))
        repo.appendMessage(ChatMessage(id = null, sessionId = 1L, role = "assistant", content = "AI回复"))
        `when`(messageMapper.selectList(any())).thenReturn(
            listOf(
                ChatMessageRecord(id = 1L, sessionId = 1L, role = "user", content = "你好"),
                ChatMessageRecord(id = 2L, sessionId = 1L, role = "assistant", content = "AI回复"),
            )
        )
        val history = repo.history(1L, 1L)
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("assistant", history[1].role)
    }
}
