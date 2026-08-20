package com.zhijin.aiclient

import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * AI 服务（Python）客户端：调 OpenAI 兼容 /v1/chat/completions。
 * baseUrl 来自环境变量 AI_SERVICE_URL（默认 http://127.0.0.1:8001）。
 *
 * 类与方法声明为 open：便于 T3 阶段（HttpModelComponent 测试）用 Mockito 对其打桩。
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

    /** 调用模型，返回 assistant 内容（无内容时返回空串）。 */
    open fun complete(prompt: String, model: String = "default"): String {
        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
        )
        val resp = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(CompletionResponse::class.java)
        return resp?.choices?.firstOrNull()?.message?.content ?: ""
    }

    /** OpenAI 兼容响应结构（仅声明用到的字段）。 */
    data class CompletionResponse(val id: String?, val choices: List<Choice>?)
    data class Choice(val index: Int?, val message: Message?)
    data class Message(val role: String?, val content: String?)
}
