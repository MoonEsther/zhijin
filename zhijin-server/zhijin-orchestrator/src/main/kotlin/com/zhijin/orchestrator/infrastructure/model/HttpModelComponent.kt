package com.zhijin.orchestrator.infrastructure.model

import com.zhijin.aiclient.AiClient
import com.zhijin.orchestrator.domain.ChatCompletionResult
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.ModelKeyResolver
import com.zhijin.orchestrator.domain.Usage

/**
 * 真实模型组件：通过 ModelKeyResolver 解密 Key，传 api_key 明文给 Python（解决 C1/C3/N1）。
 * 请求体：{model, provider, api_key, messages}
 * 响应体：{choices[0].message.content, usage: {prompt_tokens, completion_tokens, total_tokens}}
 *
 * 说明：ai-client 的 ChatCompletionResult/Usage（带 JSON 映射注解）与 orchestrator 领域层的同名类型
 * 各自独立定义（模块依赖方向：orchestrator → ai-client，领域层不反向依赖基础设施），
 * 这里在基础设施层做一次适配转换，把 ai-client 结果映射为领域类型。
 */
class HttpModelComponent(
    private val aiClient: AiClient,
    private val keyResolver: ModelKeyResolver,  // 解决 C1：端口模式
) : ModelComponent {

    override suspend fun complete(prompt: String, modelName: String, providerKeyId: Long?): ChatCompletionResult {
        // 解密 Key（解决 C1/N1：签名无 tenantId，适配 Bean 内部从 TenantContextHolder 取）
        val plainKey = providerKeyId?.let { keyResolver.resolvePlainKey(it) } ?: ""
        // 传 api_key 明文给 Python（解决 C3：不落盘 Python 侧）
        val aiResult = aiClient.completeWithUsage(prompt, modelName, "qwen", plainKey)
        // ai-client 结果 → 领域类型（usage 可能为 null，保持透传）
        return ChatCompletionResult(
            content = aiResult.content,
            usage = aiResult.usage?.let { u ->
                Usage(promptTokens = u.promptTokens, completionTokens = u.completionTokens, totalTokens = u.totalTokens)
            },
        )
    }
}
