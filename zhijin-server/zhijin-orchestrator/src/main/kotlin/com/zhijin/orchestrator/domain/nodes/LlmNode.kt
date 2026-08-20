package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.NodeSchema
import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.executor.NodeExecutor
import com.zhijin.orchestrator.domain.executor.NodeResult

/** LLM 节点：解析 prompt 输入 → 调 ModelComponent → 内容写回输出键，usage 透传（解决 C2）。 */
class LlmNode(private val model: ModelComponent) : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val prompt = node.inputs.firstOrNull { it.key == "prompt" }?.let { ctx.variableStore.resolveRef(it.source) }?.toString() ?: ""
        val modelName = node.configs["model"]?.toString() ?: "default"
        val providerKeyId = (node.configs["providerKeyId"] as? Number)?.toLong()
        val result = model.complete(prompt, modelName, providerKeyId)
        val outKey = node.outputs.firstOrNull()?.key ?: "output"
        return NodeResult(
            outputs = mapOf(
                outKey to result.content,
                "usage" to result.usage,  // 把 usage 透传给 WorkflowRunner → VariableStore，供上层回填 usage_record
            )
        )
    }
}
