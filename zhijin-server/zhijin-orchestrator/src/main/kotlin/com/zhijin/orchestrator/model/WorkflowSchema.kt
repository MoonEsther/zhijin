package com.zhijin.orchestrator.model

/** 工作流运行时定义（对齐 §7.3 WorkflowSchema）。 */
data class WorkflowSchema(
    val id: String,
    val start: String,
    val nodes: List<NodeSchema>,
    val connections: List<Connection>,
) {
    /** 静态校验：start 存在、边两端节点存在、引用存在。返回错误列表。 */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val keys = nodes.map { it.key }.toSet()
        if (start !in keys) errors += "start 节点不存在: $start"
        connections.forEach { c ->
            if (c.fromNode !in keys) errors += "边起点不存在: ${c.fromNode}"
            if (c.toNode !in keys) errors += "边终点不存在: ${c.toNode}"
        }
        nodes.forEach { n ->
            n.inputs.forEach { f ->
                if (f.source is FieldSource.Ref && f.source.nodeId !in keys) {
                    errors += "节点 ${n.key} 引用不存在的节点: ${f.source.nodeId}"
                }
            }
        }
        return errors
    }
}
