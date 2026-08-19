package com.zhijin.orchestrator.model

import tools.jackson.databind.JsonNode

/** 节点级执行配置：超时/重试/出错降级（对齐 §7.3，Coze settingOnError）。 */
data class NodeExecConfig(
    val timeoutMs: Long = 60_000,
    val maxRetry: Int = 0,
    val onError: String = "THROW", // THROW / RETURN_DEFAULT
    val dataOnErr: Any? = null,
) {
    companion object {
        /** 从 DSL 的 exec 节点解析；缺省或非对象时返回默认配置。 */
        fun fromJson(n: JsonNode): NodeExecConfig {
            if (!n.isObject) return NodeExecConfig()
            return NodeExecConfig(
                timeoutMs = n.path("timeoutMs").asLong(60_000),
                maxRetry = n.path("maxRetry").asInt(0),
                onError = n.path("onError").asText("THROW"),
                dataOnErr = if (n.path("dataOnErr").isNull) null else n.path("dataOnErr").asText(),
            )
        }
    }
}
