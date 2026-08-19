package com.zhijin.orchestrator.executor

import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.nodes.EchoNode
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NodeExecutorRegistryTest {

    @Test
    fun `注册后按类型获取`() {
        val registry = NodeExecutorRegistry()
        val node = EchoNode()
        registry.register(NodeType.VARIABLE) { node }
        assertSame(node, registry.get(NodeType.VARIABLE))
    }

    @Test
    fun `未注册类型抛异常`() {
        val registry = NodeExecutorRegistry()
        assertThrows(IllegalStateException::class.java) { registry.get(NodeType.IF) }
    }
}
