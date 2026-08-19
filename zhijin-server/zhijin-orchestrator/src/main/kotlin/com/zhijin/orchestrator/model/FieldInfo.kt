package com.zhijin.orchestrator.model

/** 输入来源：常量或引用。 */
sealed interface FieldSource {
    /** 常量输入：直接内联值。 */
    data class Literal(val value: Any?) : FieldSource

    /** 引用输入：取上游某节点某输出字段的值。 */
    data class Ref(val nodeId: String, val output: String) : FieldSource
}

/** 节点输入字段绑定：字段名 + 来源。 */
data class FieldInfo(val key: String, val source: FieldSource)

/** 输出字段定义：字段名 + 数据类型。 */
data class OutputField(val key: String, val type: String)
