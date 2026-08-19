package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema

/** 结束节点：聚合最终输出（约定输出 finalOutput）。 */
class EndNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val content = node.inputs.firstOrNull { it.key == "content" }?.let { ctx.variableStore.resolveRef(it.source) }
        return NodeResult(mapOf("finalOutput" to content))
    }
}
