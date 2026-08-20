package com.zhijin.orchestrator.infrastructure

import com.zhijin.orchestrator.domain.Connection
import com.zhijin.orchestrator.domain.FieldInfo
import com.zhijin.orchestrator.domain.FieldSource
import com.zhijin.orchestrator.domain.NodeExecConfig
import com.zhijin.orchestrator.domain.NodeSchema
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.OutputField
import com.zhijin.orchestrator.domain.WorkflowSchema
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/** JSON DSL → WorkflowSchema（V1 折叠 canvas→schema 适配层）。 */
class WorkflowParser {

    private val mapper = ObjectMapper()

    /** 解析 JSON DSL 为 WorkflowSchema；节点/边/引用缺省均有默认值兜底。 */
    fun parse(json: String): WorkflowSchema {
        val root = mapper.readTree(json)
        val nodes = root.path("nodes").map { parseNode(it) }
        val connections = root.path("edges").map { e ->
            Connection(e.path("from").asText(), e.path("to").asText())
        }
        return WorkflowSchema(
            id = root.path("id").asText("wf"),
            start = root.path("start").asText(),
            nodes = nodes,
            connections = connections,
        )
    }

    /** 解析单个节点：输入字段来源(字面量/引用)、输出字段、执行配置与节点特定配置。 */
    private fun parseNode(n: JsonNode): NodeSchema {
        val inputs = n.path("inputs").properties().asSequence().map { (k, v) ->
            val sourceNode = v.path("source").asText("literal")
            val source = if (sourceNode == "ref") {
                FieldSource.Ref(v.path("node_id").asText(), v.path("output").asText())
            } else {
                FieldSource.Literal(readValue(v.path("value")))
            }
            FieldInfo(k, source)
        }.toList()
        val outputs = n.path("outputs").map { o -> OutputField(o.path("key").asText(), o.path("type").asText("string")) }
        return NodeSchema(
            key = n.path("id").asText(),
            name = n.path("name").asText(""),
            type = NodeType.from(n.path("type").asText()),
            inputs = inputs,
            outputs = outputs,
            execConfig = NodeExecConfig.fromJson(n.path("exec")),
            configs = readConfigs(n),
        )
    }

    /** 读取节点 config 对象为 Map；非对象返回空 Map。 */
    private fun readConfigs(n: JsonNode): Map<String, Any?> {
        val cfg = n.path("config")
        if (!cfg.isObject) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        cfg.properties().forEach { (k, v) -> map[k] = readValue(v) }
        return map
    }

    /** 将 JsonNode 标量转换为 Kotlin 基本类型；复合结构降级为 JSON 字符串。 */
    private fun readValue(node: JsonNode): Any? = when {
        node.isTextual -> node.asText()
        node.isIntegralNumber -> node.asLong()
        node.isFloatingPointNumber -> node.asDouble()
        node.isBoolean -> node.asBoolean()
        node.isNull -> null
        else -> node.toString()
    }
}
