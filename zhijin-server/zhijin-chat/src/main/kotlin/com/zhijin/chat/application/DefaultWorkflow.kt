package com.zhijin.chat.application

import com.zhijin.orchestrator.domain.Connection
import com.zhijin.orchestrator.domain.FieldInfo
import com.zhijin.orchestrator.domain.FieldSource
import com.zhijin.orchestrator.domain.NodeSchema
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.OutputField
import com.zhijin.orchestrator.domain.WorkflowSchema

/**
 * 默认 LLM-only 工作流：start → llm(prompt=用户消息) → end。
 *
 * 归属说明：工作流构建属于 chat 用例的编排辅助，放在 application 层
 * （产出的是 orchestrator 领域模型 WorkflowSchema，作为应用服务驱动引擎的输入）。
 */
object DefaultWorkflow {
    /**
     * 构建默认工作流。
     * @param provider 供应商（V1 写死 qwen，后续从 AppModelConfig 取，解决 C6）
     * @param model 模型名（V1 写死 qwen-max，后续从 AppModelConfig 取，解决 C6）
     * @param providerKeyId 加密 Key 的 ID（V1 为 null，后续从 AppModelConfig 取，解决 C6）
     */
    fun build(
        prompt: String,
        provider: String = "qwen",
        model: String = "qwen-max",
        providerKeyId: Long? = null,
    ): WorkflowSchema = WorkflowSchema(
        id = "wf-default",
        start = "start",
        nodes = listOf(
            NodeSchema(key = "start", type = NodeType.START),
            NodeSchema(
                key = "llm", type = NodeType.LLM,
                inputs = listOf(FieldInfo("prompt", FieldSource.Literal(prompt))),
                outputs = listOf(OutputField("output", "string")),
                // 模型配置写入 LLM 节点 configs（解决 C6）：LlmNode 读取 model/providerKeyId 传给 ModelComponent；
                // provider 供后续扩展（当前 HttpModelComponent V1 写死 qwen）。
                configs = mapOf(
                    "model" to model,
                    "provider" to provider,
                    "providerKeyId" to providerKeyId,
                ),
            ),
            NodeSchema(
                key = "end", type = NodeType.END,
                inputs = listOf(FieldInfo("content", FieldSource.Ref("llm", "output"))),
            ),
        ),
        connections = listOf(
            Connection("start", "llm"),
            Connection("llm", "end"),
        ),
    )
}
