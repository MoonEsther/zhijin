package com.zhijin.orchestrator.domain.executor

import com.zhijin.orchestrator.domain.NodeType

/** 执行器注册表：新增节点类型 = 注册，不动调度器（开闭原则）。 */
class NodeExecutorRegistry {
    private val map = mutableMapOf<NodeType, () -> NodeExecutor>()

    fun register(type: NodeType, factory: () -> NodeExecutor) {
        map[type] = factory
    }

    fun get(type: NodeType): NodeExecutor =
        map[type]?.invoke() ?: throw IllegalStateException("未注册节点类型: $type")
}
