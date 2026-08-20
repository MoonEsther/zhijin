package com.zhijin.orchestrator.nodes

import com.zhijin.aiclient.AiClient
import com.zhijin.orchestrator.model.ModelComponent

/**
 * 真实模型组件：经 zhijin-ai-client 调 Python 模型网关。
 * 替换 StubModelComponent；计划 C 完善 Python 供应商后无需改此处。
 */
class HttpModelComponent(private val aiClient: AiClient) : ModelComponent {
    override suspend fun complete(prompt: String, modelName: String): String =
        aiClient.complete(prompt, modelName)
}
