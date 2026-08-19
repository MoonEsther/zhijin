package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.model.FieldInfo
import com.zhijin.orchestrator.model.FieldSource
import com.zhijin.orchestrator.model.NodeSchema
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.model.OutputField
import com.zhijin.orchestrator.tool.EchoTool
import com.zhijin.orchestrator.tool.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolNodeTest {

    private val registry = ToolRegistry().also { it.register("echo", EchoTool()) }

    @Test
    fun `调用工具并输出结果`() = runTest {
        val store = VariableStore().also { it.writeNodeOutput("n1", "out", "pong") }
        val schema = NodeSchema(
            key = "t", type = NodeType.TOOL,
            inputs = listOf(FieldInfo("msg", FieldSource.Ref("n1", "out"))),
            outputs = listOf(OutputField("result", "string")),
            configs = mapOf("tool" to "echo"),
        )
        val node = ToolNode(registry)
        val result = node.invoke(NodeContext(store), schema)
        assertEquals("echo:pong", result.outputs["result"])
    }

    @Test
    fun `未注册工具抛异常`() = runTest {
        val schema = NodeSchema(
            key = "t", type = NodeType.TOOL,
            inputs = emptyList(), outputs = emptyList(),
            configs = mapOf("tool" to "missing"),
        )
        val node = ToolNode(registry)
        // invoke 是挂起函数，不能放进 JUnit assertThrows 的非挂起 lambda，改用 try/catch 断言
        val error = try {
            node.invoke(NodeContext(VariableStore()), schema)
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(error is IllegalStateException, "期望抛出 IllegalStateException，实际: $error")
    }
}
