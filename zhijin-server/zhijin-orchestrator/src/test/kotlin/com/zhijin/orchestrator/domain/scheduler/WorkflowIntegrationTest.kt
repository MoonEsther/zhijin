package com.zhijin.orchestrator.domain.scheduler

import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.infrastructure.WorkflowParser
import com.zhijin.orchestrator.domain.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.nodes.EndNode
import com.zhijin.orchestrator.domain.nodes.IfNode
import com.zhijin.orchestrator.domain.nodes.LlmNode
import com.zhijin.orchestrator.domain.nodes.StartNode
import com.zhijin.orchestrator.domain.nodes.StubModelComponent
import com.zhijin.orchestrator.domain.nodes.VariableNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 端到端集成测试：验证多类型节点（Start/Variable/If/LLM/End）通过 DSL 组合成 DAG 后，
 * 由 WorkflowRunner 按拓扑序执行，数据（字面量→变量节点→引用绑定→LLM→End 聚合）能完整流转。
 */
class WorkflowIntegrationTest {

    @Test
    fun `多节点组合执行`() = runTest {
        val registry = NodeExecutorRegistry().apply {
            register(NodeType.START) { StartNode() }
            register(NodeType.VARIABLE) { VariableNode() }
            register(NodeType.IF) { IfNode() }
            register(NodeType.LLM) { LlmNode(StubModelComponent("AI回复")) }
            register(NodeType.END) { EndNode() }
        }
        val json = """
        { "id": "wf", "start": "start",
          "nodes": [
            { "id": "start", "type": "start" },
            { "id": "v1", "type": "variable",
              "inputs": { "value": { "source": "literal", "value": "你好" } },
              "outputs": [ { "key": "out", "type": "string" } ] },
            { "id": "if1", "type": "if",
              "inputs": { "condition": { "source": "literal", "value": true } },
              "outputs": [ { "key": "branch", "type": "string" } ] },
            { "id": "llm", "type": "llm",
              "inputs": { "prompt": { "source": "ref", "node_id": "v1", "output": "out" } },
              "outputs": [ { "key": "output", "type": "string" } ] },
            { "id": "end", "type": "end",
              "inputs": { "content": { "source": "ref", "node_id": "llm", "output": "output" } } }
          ],
          "edges": [
            { "from": "start", "to": "v1" },
            { "from": "v1", "to": "if1" },
            { "from": "if1", "to": "llm" },
            { "from": "llm", "to": "end" }
          ] }
        """.trimIndent()
        val schema = WorkflowParser().parse(json)
        val result = WorkflowRunner(registry).execute(schema, VariableStore())
        assertEquals("AI回复", result.finalOutput)
    }
}
