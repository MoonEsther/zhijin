package com.zhijin.chat.service

import com.zhijin.chat.entity.ChatMessage
import com.zhijin.chat.entity.ChatSession
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

class SessionServiceTest {

    private val sessionMapper = mock(ChatSessionMapper::class.java)
    private val messageMapper = mock(ChatMessageMapper::class.java)
    private val service = SessionService(sessionMapper, messageMapper)

    private fun backfillSessionId() = Answer<Int> { inv: InvocationOnMock ->
        inv.getArgument<ChatSession>(0).id = 1L
        1
    }

    @Test
    fun `创建会话返回带id会话`() {
        `when`(sessionMapper.insert(any(ChatSession::class.java))).thenAnswer(backfillSessionId())
        val s = service.createSession(1L, 1L, "售前")
        assertNotNull(s.id)
        assertEquals("售前", s.title)
    }

    @Test
    fun `追加消息后可取回`() {
        val session = ChatSession(id = 1L, tenantId = 1L, appId = 1L, title = "x")
        service.appendMessage(1L, session, "user", "你好")
        service.appendMessage(1L, session, "assistant", "AI回复")
        `when`(messageMapper.selectList(any())).thenReturn(
            listOf(ChatMessage(role = "user", content = "你好"), ChatMessage(role = "assistant", content = "AI回复"))
        )
        val history = service.getHistory(1L, session)
        assertEquals(2, history.size)
    }
}
