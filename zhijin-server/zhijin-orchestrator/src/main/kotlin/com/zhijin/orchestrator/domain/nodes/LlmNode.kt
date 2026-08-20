package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.executor.NodeExecutor
import com.zhijin.orchestrator.domain.executor.NodeResult
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.NodeSchema

/** LLM 节点：解析 prompt 输入 → 调 ModelComponent → 输出写回。 */
class LlmNode(private val model: ModelComponent) : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val prompt = node.inputs.firstOrNull { it.key == "prompt" }?.let { ctx.variableStore.resolveRef(it.source) }?.toString() ?: ""
        val modelName = node.configs["model"]?.toString() ?: "default"
        val reply = model.complete(prompt, modelName)
        val outKey = node.outputs.firstOrNull()?.key ?: "output"
        return NodeResult(mapOf(outKey to reply))
    }
}
