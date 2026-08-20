package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.FieldInfo
import com.zhijin.orchestrator.domain.FieldSource
import com.zhijin.orchestrator.domain.NodeSchema
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.OutputField
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariableNodeTest {

    private val node = VariableNode()

    @Test
    fun `字面量赋值到输出`() = runTest {
        val store = VariableStore()
        val schema = NodeSchema(
            key = "v", type = NodeType.VARIABLE,
            inputs = listOf(FieldInfo("value", FieldSource.Literal(42L))),
            outputs = listOf(OutputField("out", "long")),
        )
        val result = node.invoke(NodeContext(store), schema)
        assertEquals(42L, result.outputs["out"])
    }

    @Test
    fun `引用上游节点输出`() = runTest {
        val store = VariableStore().also { it.writeNodeOutput("n1", "out", "hi") }
        val schema = NodeSchema(
            key = "v", type = NodeType.VARIABLE,
            inputs = listOf(FieldInfo("value", FieldSource.Ref("n1", "out"))),
            outputs = listOf(OutputField("out", "string")),
        )
        val result = node.invoke(NodeContext(store), schema)
        assertEquals("hi", result.outputs["out"])
    }
}
