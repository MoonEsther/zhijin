package com.zhijin.orchestrator.model

/** 运行时节点定义（对齐 §7.3 NodeSchema）。 */
data class NodeSchema(
    val key: String,
    val name: String = "",
    val type: NodeType,
    val inputs: List<FieldInfo> = emptyList(),
    val outputs: List<OutputField> = emptyList(),
    val execConfig: NodeExecConfig = NodeExecConfig(),
    val configs: Map<String, Any?> = emptyMap(), // 节点特定配置(如 llm.prompt, tool.id, if.branches)
)
