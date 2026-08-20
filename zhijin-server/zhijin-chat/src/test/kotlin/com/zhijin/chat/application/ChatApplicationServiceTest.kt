package com.zhijin.chat.application

import com.zhijin.billingaudit.domain.usage.UsageRecord
import com.zhijin.billingaudit.domain.usage.UsageRecorder
import com.zhijin.chat.domain.session.ChatMessage
import com.zhijin.chat.domain.session.ChatSession
import com.zhijin.chat.domain.session.SessionRepository
import com.zhijin.chat.interfaces.dto.ChatRequest
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.domain.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.nodes.EndNode
import com.zhijin.orchestrator.domain.nodes.LlmNode
import com.zhijin.orchestrator.domain.nodes.StartNode
import com.zhijin.orchestrator.domain.nodes.StubModelComponent
import com.zhijin.orchestrator.domain.scheduler.WorkflowRunner
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

    @Test
    fun `聊天后记录用量`() {
        // 请求线程需有租户上下文（chatAsync 在派生子线程前捕获）
        TenantContextHolder.setTenantId(1L)
        val repo = mock(SessionRepository::class.java)
        val created = ChatSession(id = null, tenantId = 1L, appId = 1L, title = "")
        `when`(repo.create(created))
            .thenReturn(ChatSession(id = 1L, tenantId = 1L, appId = 1L, title = ""))

        // 捕获型 UsageRecorder：验证 chatAsync 完成后确实记录了一条用量
        val captured = java.util.Collections.synchronizedList(mutableListOf<UsageRecord>())
        val service = ChatApplicationService(
            repo,
            StubModelComponent("AI回复"),
            UsageRecorder { captured.add(it) },
        )
        service.chatAsync(ChatRequest(appId = 1L, message = "你好"), SseEmitter())

        // chatAsync 在后台线程异步执行，轮询等待用量记录产生（与仓库断言一致的 2s 上限）
        val deadline = System.currentTimeMillis() + 2000
        while (captured.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assert(captured.isNotEmpty()) { "聊天后应记录一条用量" }
        val record = captured.first()
        assertEquals(1L, record.tenantId)
        assertEquals(1L, record.appId)
        assertEquals(1L, record.sessionId)
        // 解决 C2/N2'：从 VariableStore 回填真实 token 计数（StubModelComponent 固定返回 usage=10/20/30）
        assertEquals("qwen-max", record.model)
        assertEquals(10, record.promptTokens)
        assertEquals(20, record.completionTokens)
        assertEquals(30, record.totalTokens)
    }

    @Test
    fun `默认工作流LLM节点携带模型配置`() {
        // 解决 C6：provider/model/providerKeyId 写入 LLM 节点 configs，供 LlmNode 读取传给 ModelComponent
        val schema = DefaultWorkflow.build("你好", provider = "qwen", model = "qwen-max", providerKeyId = 7L)
        val llm = schema.nodes.first { it.key == "llm" }
        assertEquals("qwen-max", llm.configs["model"])
        assertEquals("qwen", llm.configs["provider"])
        assertEquals(7L, llm.configs["providerKeyId"])
        // 默认参数向后兼容：无参调用仍可构建（既有测试继续可用）
        val defaultSchema = DefaultWorkflow.build("你好")
        assertEquals("qwen-max", defaultSchema.nodes.first { it.key == "llm" }.configs["model"])
    }
}
