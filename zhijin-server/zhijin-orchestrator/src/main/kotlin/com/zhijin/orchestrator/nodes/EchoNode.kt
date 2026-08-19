package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema

/** 测试用 Echo 节点：把输入 value 原样输出。 */
class EchoNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult =
        NodeResult(mapOf("out" to node.inputs.firstOrNull { it.key == "value" }?.let {
            ctx.variableStore.resolveRef(it.source)
        }))
}
