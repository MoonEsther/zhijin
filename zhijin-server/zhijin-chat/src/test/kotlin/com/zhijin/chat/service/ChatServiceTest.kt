package com.zhijin.chat.service

import com.zhijin.chat.workflow.DefaultWorkflow
import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.nodes.EndNode
import com.zhijin.orchestrator.nodes.LlmNode
import com.zhijin.orchestrator.nodes.StartNode
import com.zhijin.orchestrator.nodes.StubModelComponent
import com.zhijin.orchestrator.scheduler.WorkflowRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ChatService 默认工作流路径测试。
 *
 * 验证 ChatService.chatAsync 实际使用的默认 LLM-only 工作流（start→llm→end）
 * 能通过真实编排引擎产生模型回复。使用 StubModelComponent 桩返回固定文本，
 * 避免依赖外部模型网关。
 */
class ChatServiceTest {

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
}
