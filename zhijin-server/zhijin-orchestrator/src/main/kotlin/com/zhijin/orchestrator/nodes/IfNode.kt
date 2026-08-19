package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema

/**
 * 分支节点：按 condition 布尔值输出 then/else 分支名。
 * 调度器根据分支名选择后续边（V1 用 branch 输出配合 edges 的 port 语义，B4 后续扩展）。
 */
class IfNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val cond = node.inputs.firstOrNull { it.key == "condition" }?.let { ctx.variableStore.resolveRef(it.source) } as? Boolean ?: false
        return NodeResult(mapOf("branch" to if (cond) "then" else "else"))
    }
}
