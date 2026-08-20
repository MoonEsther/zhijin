package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.ChatCompletionResult
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.Usage

/** 测试用桩：返回固定文本 + 模拟 usage。V1/计划 C 用真实 HTTP 实现替换。 */
class StubModelComponent(private val reply: String = "模型返回") : ModelComponent {
    override suspend fun complete(prompt: String, modelName: String, provider: String, providerKeyId: Long?): ChatCompletionResult =
        ChatCompletionResult(
            content = reply,
            usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30),
        )
}
