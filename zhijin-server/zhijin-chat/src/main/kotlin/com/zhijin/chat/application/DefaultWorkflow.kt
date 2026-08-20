package com.zhijin.chat.application

import com.zhijin.orchestrator.model.Connection
import com.zhijin.orchestrator.model.FieldInfo
import com.zhijin.orchestrator.model.FieldSource
import com.zhijin.orchestrator.model.NodeSchema
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.model.OutputField
import com.zhijin.orchestrator.model.WorkflowSchema

/**
 * 默认 LLM-only 工作流：start → llm(prompt=用户消息) → end。
 *
 * 归属说明：工作流构建属于 chat 用例的编排辅助，放在 application 层
 * （产出的是 orchestrator 领域模型 WorkflowSchema，作为应用服务驱动引擎的输入）。
 */
object DefaultWorkflow {
    fun build(prompt: String): WorkflowSchema = WorkflowSchema(
        id = "wf-default",
        start = "start",
        nodes = listOf(
            NodeSchema(key = "start", type = NodeType.START),
            NodeSchema(
                key = "llm", type = NodeType.LLM,
                inputs = listOf(FieldInfo("prompt", FieldSource.Literal(prompt))),
                outputs = listOf(OutputField("output", "string")),
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
