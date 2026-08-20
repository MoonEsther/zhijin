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
}
