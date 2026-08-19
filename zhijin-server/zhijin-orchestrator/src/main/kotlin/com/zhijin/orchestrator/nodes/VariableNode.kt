package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema

/** 变量节点：把输入 value（字面量或引用）原样写到输出。 */
class VariableNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val value = node.inputs.firstOrNull { it.key == "value" }?.let { ctx.variableStore.resolveRef(it.source) }
        val outKey = node.outputs.firstOrNull()?.key ?: "out"
        return NodeResult(mapOf(outKey to value))
    }
}
