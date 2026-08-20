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

class IfNodeTest {

    private val node = IfNode()

    private fun ifSchema(condition: Boolean): NodeSchema = NodeSchema(
        key = "if", type = NodeType.IF,
        inputs = listOf(FieldInfo("condition", FieldSource.Literal(condition))),
        outputs = listOf(OutputField("branch", "string")),
    )

    @Test
    fun `条件为真输出then分支`() = runTest {
        val result = node.invoke(NodeContext(VariableStore()), ifSchema(true))
        assertEquals("then", result.outputs["branch"])
    }

    @Test
    fun `条件为假输出else分支`() = runTest {
        val result = node.invoke(NodeContext(VariableStore()), ifSchema(false))
        assertEquals("else", result.outputs["branch"])
    }
}
