package com.zhijin.aiclient

import com.fasterxml.jackson.annotation.JsonProperty  // 注解类在 Jackson 3.0.x 仍保留 2.x 包名（3.x 注解仍留在 com.fasterxml.jackson.core:jackson-annotations）
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/** Token 使用量（snake_case → camelCase 映射，解决 C7）。 */
data class Usage(
    @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
    @JsonProperty("completion_tokens") val completionTokens: Int = 0,
    @JsonProperty("total_tokens") val totalTokens: Int = 0,
)

/** 模型调用结果。 */
data class ChatCompletionResult(
    val content: String,
    val usage: Usage? = null,
)

/** OpenAI 兼容响应结构（usage 为新增字段，Python 网关返回真实 token 计数）。 */
data class CompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
)

data class Choice(val index: Int? = null, val message: Message? = null)
data class Message(val role: String? = null, val content: String? = null)

/**
 * AI 服务（Python）客户端：调 OpenAI 兼容 /v1/chat/completions。
 * baseUrl 来自环境变量 AI_SERVICE_URL（默认 http://127.0.0.1:8001）。
 *
 * 类与方法声明为 open：便于 HttpModelComponent 测试用 Mockito 对其打桩。
 * 解决 N4/N4'：必须配置 JsonMapper.builder().addModule(KotlinModule.Builder().build())
 * 并通过 configureMessageConverters 装配，否则默认 JsonMapper 无法构造 Kotlin data class
 * （无默认构造器），反序列化会抛异常（B5 执行时踩过的坑）。
 */
open class AiClient(
    private val baseUrl: String = System.getenv("AI_SERVICE_URL") ?: "http://127.0.0.1:8001",
) {

    /** 自定义 JsonMapper：注册 KotlinModule，使 Jackson 能通过主构造函数反序列化 Kotlin data class。 */
    private val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .configureMessageConverters { clientBuilder ->
            // 用注册了 KotlinModule 的 JsonMapper 重建 JSON 转换器；
            // 否则默认 JsonMapper 无法构造 Kotlin data class（无默认构造器），反序列化会抛异常。
            // 注意：必须先 registerDefaults() 启用默认转换器装配，再以 withJsonConverter 替换 JSON 转换器。
            clientBuilder.registerDefaults()
            clientBuilder.withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
        }
        .build()

    /** 调用模型，返回 assistant 内容（向后兼容，无内容时返回空串）。 */
    open fun complete(prompt: String, model: String = "default"): String =
        completeWithUsage(prompt, model, "qwen", "").content

    /**
     * 调用模型，返回内容 + usage（解决 C7/N3/N4'）。
     * provider 选择 Python 侧适配器（V1 默认 qwen，后续从 AppModelConfig 取，解决 C6）；
     * apiKey 由 Kotlin 侧解密后明文下发（解决 C3：不落盘 Python 侧）。
     */
    open fun completeWithUsage(
        prompt: String,
        model: String = "default",
        provider: String = "qwen",
        apiKey: String = "",
    ): ChatCompletionResult {
        val body = mapOf(
            "model" to model,
            "provider" to provider,       // 解决 C6：供应商（V1 默认 qwen）
            "api_key" to apiKey,          // 解决 C3：传明文，不落盘 Python
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
        )
        val resp = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(CompletionResponse::class.java)
        val content = resp?.choices?.firstOrNull()?.message?.content ?: ""
        return ChatCompletionResult(content = content, usage = resp?.usage)
    }
}
