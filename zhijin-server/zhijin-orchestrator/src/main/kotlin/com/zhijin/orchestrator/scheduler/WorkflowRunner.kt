package com.zhijin.orchestrator.scheduler

import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.model.WorkflowSchema

/** 工作流执行结果。 */
data class WorkflowResult(val finalOutput: Any?)

/**
 * 图调度器：按 DAG 拓扑执行（显式边驱动顺序），输入引用负责数据绑定。
 * V1 顺序执行（无并行）；分支节点（if）自行决定 goto（后续扩展）。
 */
class WorkflowRunner(private val registry: NodeExecutorRegistry) {

    suspend fun execute(schema: WorkflowSchema, store: VariableStore): WorkflowResult {
        val errors = schema.validate()
        if (errors.isNotEmpty()) throw IllegalStateException("工作流校验失败: $errors")

        val order = topologicalOrder(schema)
        var finalOutput: Any? = null

        for (nodeKey in order) {
            val node = schema.nodes.first { it.key == nodeKey }
            val executor = registry.get(node.type)
            val ctx = NodeContext(store)
            val result = executor.invoke(ctx, node)
            // 写节点输出到变量区
            node.outputs.forEach { out ->
                store.writeNodeOutput(node.key, out.key, result.outputs[out.key])
            }
            // END 节点聚合最终输出（约定 END 输出 finalOutput）
            if (node.type == NodeType.END) {
                finalOutput = result.outputs["finalOutput"]
            }
        }
        return WorkflowResult(finalOutput)
    }

    /** Kahn 拓扑排序：入度为 0 优先。 */
    private fun topologicalOrder(schema: WorkflowSchema): List<String> {
        val indegree = mutableMapOf<String, Int>()
        val adj = mutableMapOf<String, MutableList<String>>()
        schema.nodes.forEach { indegree[it.key] = 0; adj[it.key] = mutableListOf() }
        schema.connections.forEach { c ->
            adj[c.fromNode]!!.add(c.toNode)
            indegree[c.toNode] = indegree[c.toNode]!! + 1
        }
        val queue = ArrayDeque(schema.nodes.filter { indegree[it.key] == 0 }.map { it.key })
        val order = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            order.add(u)
            adj[u]!!.forEach { v ->
                indegree[v] = indegree[v]!! - 1
                if (indegree[v] == 0) queue.addLast(v)
            }
        }
        if (order.size != schema.nodes.size) throw IllegalStateException("存在环（DAG 要求无环）")
        return order
    }
}
