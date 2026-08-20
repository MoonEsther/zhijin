package com.zhijin.orchestrator.infrastructure.model

import com.zhijin.aiclient.AiClient
import com.zhijin.aiclient.ChatCompletionResult as AiChatCompletionResult
import com.zhijin.aiclient.Usage as AiUsage
import com.zhijin.orchestrator.domain.ModelKeyResolver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class HttpModelComponentTest {

    @Test
    fun `HttpModelComponent通过ModelKeyResolver解密Key并返回usage`() = runTest {
        val aiClient = mock(AiClient::class.java)
        val keyResolver = mock(ModelKeyResolver::class.java)
        `when`(keyResolver.resolvePlainKey(1L)).thenReturn("sk-test")  // 解决 N1：签名无 tenantId
        `when`(aiClient.completeWithUsage("prompt", "qwen-max", "qwen", "sk-test"))
            .thenReturn(AiChatCompletionResult("AI回复", AiUsage(10, 20, 30)))  // 解决 C3：api_key 明文随请求下发

        val component = HttpModelComponent(aiClient, keyResolver)
        val result = component.complete("prompt", "qwen-max", "qwen", 1L)

        // 断言领域层结果：内容 + usage（token 计数透传，解决 C2）
        assertEquals("AI回复", result.content)
        assertEquals(10, result.usage?.promptTokens)
        assertEquals(20, result.usage?.completionTokens)
        assertEquals(30, result.usage?.totalTokens)
    }

    @Test
    fun `providerKeyId为空时不解析Key并传空api_key`() = runTest {
        val aiClient = mock(AiClient::class.java)
        val keyResolver = mock(ModelKeyResolver::class.java)
        `when`(aiClient.completeWithUsage("prompt", "qwen-max", "qwen", ""))
            .thenReturn(AiChatCompletionResult("AI回复", null))

        val component = HttpModelComponent(aiClient, keyResolver)
        val result = component.complete("prompt", "qwen-max", "qwen", null)

        // providerKeyId 为空 → 不触发 Key 解析，回退空 Key（Python 侧回退环境变量，解决 C5）
        assertEquals("AI回复", result.content)
        assertNull(result.usage)
    }
}
