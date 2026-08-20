package com.zhijin.orchestrator.domain

/** 模型调用结果（含 token 使用量）。 */
data class ChatCompletionResult(
    val content: String,
    val usage: Usage? = null,
)

/** Token 使用量。 */
data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

/** 模型组件抽象（依赖倒置，§3.1 关键原则）：LLM 节点依赖抽象，不依赖具体实现。 */
interface ModelComponent {
    /**
     * 调用模型，返回 assistant 内容 + token 使用量。
     * providerKeyId 为加密 Key 的 ID，通过 ModelKeyResolver 解密。
     */
    suspend fun complete(prompt: String, modelName: String, providerKeyId: Long? = null): ChatCompletionResult
}
