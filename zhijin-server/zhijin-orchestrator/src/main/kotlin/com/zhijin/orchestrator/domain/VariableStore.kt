package com.zhijin.orchestrator.domain

import com.zhijin.orchestrator.domain.FieldSource

/**
 * 变量区：以 nodeId.output 为 key 存节点输出；解析 FieldSource。
 * 引用约定 {{node_id.output_key}}（决策 17）。$var 会话变量 V1 从 inputs 注入。
 */
class VariableStore {

    private val outputs = mutableMapOf<String, Any?>()
    private val sessionVars = mutableMapOf<String, Any?>()

    fun writeNodeOutput(nodeId: String, outputKey: String, value: Any?) {
        outputs["$nodeId.$outputKey"] = value
    }

    fun writeSessionVar(name: String, value: Any?) {
        sessionVars[name] = value
    }

    fun readNodeOutput(nodeId: String, outputKey: String): Any? = outputs["$nodeId.$outputKey"]

    /** 解析输入来源：ref → 变量区取值；literal → 直接返回。 */
    fun resolveRef(source: FieldSource): Any? = when (source) {
        is FieldSource.Literal -> source.value
        is FieldSource.Ref -> outputs["${source.nodeId}.${source.output}"]
            ?: throw IllegalStateException("引用未写入: ${source.nodeId}.${source.output}")
    }
}
