package com.zhijin.orchestrator.domain.scheduler

import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.infrastructure.WorkflowParser
import com.zhijin.orchestrator.domain.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.nodes.EchoNode
import com.zhijin.orchestrator.domain.nodes.EndNode
import com.zhijin.orchestrator.domain.nodes.StartNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkflowRunnerTest {

    private fun registry(): NodeExecutorRegistry = NodeExecutorRegistry().apply {
        register(NodeType.START) { StartNode() }
        register(NodeType.VARIABLE) { EchoNode() }
        register(NodeType.END) { EndNode() }
    }

    @Test
    fun `线性图执行并输出到变量区`() = runTest {
        val json = """
        { "id": "wf", "start": "start",
          "nodes": [
            { "id": "start", "type": "start" },
            { "id": "var", "type": "variable",
              "inputs": { "value": { "source": "literal", "value": "hello" } },
              "outputs": [ { "key": "out", "type": "string" } ] },
            { "id": "end", "type": "end",
              "inputs": { "content": { "source": "ref", "node_id": "var", "output": "out" } } }
          ],
          "edges": [
            { "from": "start", "to": "var" },
            { "from": "var", "to": "end" }
          ] }
        """.trimIndent()
        val schema = WorkflowParser().parse(json)
        val runner = WorkflowRunner(registry())
        val result = runner.execute(schema, VariableStore())
        assertEquals("hello", result.finalOutput)
    }
}
