package com.zhijin.chat.application

import com.zhijin.chat.domain.session.ChatMessage
import com.zhijin.chat.domain.session.ChatSession
import com.zhijin.chat.domain.session.SessionRepository
import com.zhijin.chat.interfaces.dto.ChatRequest
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.nodes.EndNode
import com.zhijin.orchestrator.nodes.LlmNode
import com.zhijin.orchestrator.nodes.StartNode
import com.zhijin.orchestrator.nodes.StubModelComponent
import com.zhijin.orchestrator.scheduler.WorkflowRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * ChatApplicationService 测试：默认工作流路径 + chat 用例装配。
 *
 * 用例装配测试用 StubModelComponent 桩返回固定文本 + Mockito 模拟 SessionRepository，
 * 验证 chatAsync 在后台线程中：建会话 → 追加用户消息 → 引擎产出回复 → 追加助手回复。
 */
class ChatApplicationServiceTest {

    @AfterEach
    fun tearDown() {
        // 测试线程租户上下文在用例间清理，避免串租
        TenantContextHolder.clear()
    }

    @Test
    fun `默认工作流返回模型回复`() = runTest {
        val registry = NodeExecutorRegistry().apply {
            register(NodeType.START) { StartNode() }
            register(NodeType.LLM) { LlmNode(StubModelComponent("AI回复")) }
            register(NodeType.END) { EndNode() }
        }
        val runner = WorkflowRunner(registry)
        val schema = DefaultWorkflow.build("你好")
        val result = runner.execute(schema, VariableStore())
        assertEquals("AI回复", result.finalOutput)
    }

    @Test
    fun `聊天用例驱动引擎并持久化助手回复`() {
        // 请求线程需有租户上下文（chatAsync 在派生子线程前捕获）
        TenantContextHolder.setTenantId(1L)
        val repo = mock(SessionRepository::class.java)
        // 仓储 create 回填自增 id=1，与真实 MyBatis-Plus 行为一致。
        // 注意：不可用 any() 匹配 Kotlin 非空参数（any() 返回 null 会触发 NPE），用具体值精确匹配。
        val created = ChatSession(id = null, tenantId = 1L, appId = 1L, title = "")
        `when`(repo.create(created))
            .thenReturn(ChatSession(id = 1L, tenantId = 1L, appId = 1L, title = ""))

        val service = ChatApplicationService(repo, StubModelComponent("AI回复"))
        service.chatAsync(ChatRequest(appId = 1L, message = "你好"), SseEmitter())

        // chatAsync 在后台线程异步执行，用 Mockito timeout 轮询等待断言
        verify(repo, timeout(2000)).create(created)
        verify(repo, timeout(2000)).appendMessage(
            ChatMessage(id = null, sessionId = 1L, role = "user", content = "你好")
        )
        verify(repo, timeout(2000)).appendMessage(
            ChatMessage(id = null, sessionId = 1L, role = "assistant", content = "AI回复")
        )
    }
}
