package com.zhijin.aiclient

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiClient

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        client = AiClient(server.url("/").toString().trimEnd('/'))
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `调用chat completions返回内容`() {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"1","object":"chat.completion","model":"qwen-max",
                   "choices":[{"index":0,"message":{"role":"assistant","content":"AI你好"}}]}"""
            ).addHeader("Content-Type", "application/json")
        )
        val content = client.complete("你好", "qwen-max")
        assertEquals("AI你好", content)
    }

    @Test
    fun `解析usage的snake_case字段为camelCase`() {
        // 解决 C7：Python 网关返回 snake_case 的 token 计数，需映射到 camelCase 字段
        server.enqueue(
            MockResponse().setBody(
                """{"id":"1","object":"chat.completion","model":"qwen-max",
                   "choices":[{"index":0,"message":{"role":"assistant","content":"AI你好"}}],
                   "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}"""
            ).addHeader("Content-Type", "application/json")
        )
        val result = client.completeWithUsage("你好", "qwen-max")
        assertEquals("AI你好", result.content)
        assertEquals(10, result.usage?.promptTokens)
        assertEquals(20, result.usage?.completionTokens)
        assertEquals(30, result.usage?.totalTokens)
    }

    @Test
    fun `请求体携带provider与api_key明文`() {
        // 解决 C3/C6：api_key 明文随请求下发（不落盘 Python），provider 供网关路由供应商适配器
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"ok"}}],"usage":null}"""
            ).addHeader("Content-Type", "application/json")
        )
        client.completeWithUsage("你好", "qwen-max", "qwen", "sk-123")

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assert(body.contains("\"provider\":\"qwen\"")) { "请求体应携带 provider" }
        assert(body.contains("\"api_key\":\"sk-123\"")) { "请求体应携带 api_key 明文" }
    }
}
