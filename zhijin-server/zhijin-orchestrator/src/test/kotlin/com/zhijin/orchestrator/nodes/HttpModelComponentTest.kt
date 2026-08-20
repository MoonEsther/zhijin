package com.zhijin.orchestrator.nodes

import com.zhijin.aiclient.AiClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class HttpModelComponentTest {

    @Test
    fun `HttpModelComponent通过AiClient调用`() = runTest {
        val client = mock(AiClient::class.java)
        `when`(client.complete("prompt-x", "qwen-max")).thenReturn("AI回复")
        val component = HttpModelComponent(client)
        assertEquals("AI回复", component.complete("prompt-x", "qwen-max"))
    }
}
