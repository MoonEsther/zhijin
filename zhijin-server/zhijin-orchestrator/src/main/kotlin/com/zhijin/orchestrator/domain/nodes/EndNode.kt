package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.executor.NodeExecutor
import com.zhijin.orchestrator.domain.executor.NodeResult
import com.zhijin.orchestrator.domain.NodeSchema

/** 结束节点：聚合最终输出（约定输出 finalOutput）。 */
class EndNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val content = node.inputs.firstOrNull { it.key == "content" }?.let { ctx.variableStore.resolveRef(it.source) }
        return NodeResult(mapOf("finalOutput" to content))
    }
}
