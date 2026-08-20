package com.zhijin.orchestrator.infrastructure

import com.zhijin.orchestrator.domain.NodeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkflowParserTest {

    private val parser = WorkflowParser()

    @Test
    fun `解析DSL生成workflowSchema与显式边`() {
        val json = """
        {
          "id": "wf-demo", "start": "n1",
          "nodes": [
            { "id": "n1", "type": "start", "outputs": [ { "key": "out", "type": "string" } ] },
            { "id": "n2", "type": "variable",
              "inputs": { "value": { "source": "literal", "value": "hello" } },
              "outputs": [ { "key": "out", "type": "string" } ] }
          ],
          "edges": [ { "from": "n1", "to": "n2" } ]
        }
        """.trimIndent()
        val schema = parser.parse(json)
        assertEquals("wf-demo", schema.id)
        assertEquals(2, schema.nodes.size)
        assertEquals(1, schema.connections.size)
        assertEquals("n1", schema.connections[0].fromNode)
        assertEquals(NodeType.START, schema.nodes.first { it.key == "n1" }.type)
        assertTrue(schema.validate().isEmpty())
    }

    @Test
    fun `引用不存在的上游节点校验失败`() {
        val json = """
        { "id": "wf", "start": "n1",
          "nodes": [ { "id": "n1", "type": "start",
            "inputs": { "x": { "source": "ref", "node_id": "ghost", "output": "y" } } } ],
          "edges": [] }
        """.trimIndent()
        val errors = parser.parse(json).validate()
        assertTrue(errors.isNotEmpty())
    }
}
