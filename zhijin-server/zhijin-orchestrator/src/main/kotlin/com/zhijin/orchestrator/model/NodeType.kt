package com.zhijin.orchestrator.model

/** 节点类型（V1 对齐决策 20）。 */
enum class NodeType(val code: String) {
    START("start"), END("end"), LLM("llm"), TOOL("tool"),
    IF("if"), VARIABLE("variable");

    companion object {
        /** 根据 DSL 中的字符串 code 解析节点类型，未知类型抛出异常。 */
        fun from(code: String): NodeType = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("未知节点类型: $code")
    }
}
