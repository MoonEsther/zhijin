package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.domain.Usage
import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.FieldInfo
import com.zhijin.orchestrator.domain.FieldSource
import com.zhijin.orchestrator.domain.NodeSchema
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.OutputField
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LlmNodeTest {

    private val component = StubModelComponent("模型返回")

    @Test
    fun `LLM节点调用组件并输出结果`() = runTest {
        val store = VariableStore().also { it.writeNodeOutput("n1", "out", "你好") }
        val schema = NodeSchema(
            key = "llm", type = NodeType.LLM,
            inputs = listOf(FieldInfo("prompt", FieldSource.Ref("n1", "out"))),
            outputs = listOf(OutputField("output", "string")),
        )
        val node = LlmNode(component)
        val result = node.invoke(NodeContext(store), schema)
        assertEquals("模型返回", result.outputs["output"])
    }

    @Test
    fun `LlmNode把usage写入outputs`() = runTest {
        val store = VariableStore()
        val schema = NodeSchema(
            key = "llm", type = NodeType.LLM,
            inputs = listOf(FieldInfo("prompt", FieldSource.Literal("你好"))),
            outputs = listOf(OutputField("output", "string")),
        )
        val node = LlmNode(component)
        val result = node.invoke(NodeContext(store), schema)

        // 解决 C2：usage 写入 outputs["usage"]，透传给 WorkflowRunner → VariableStore
        assertEquals("模型返回", result.outputs["output"])
        assertNotNull(result.outputs["usage"])
        val usage = result.outputs["usage"] as Usage
        assertEquals(10, usage.promptTokens)
        assertEquals(20, usage.completionTokens)
        assertEquals(30, usage.totalTokens)
    }
}
