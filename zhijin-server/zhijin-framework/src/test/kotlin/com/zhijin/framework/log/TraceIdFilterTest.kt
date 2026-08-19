package com.zhijin.framework.log

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TraceIdFilterTest {

    private val filter = TraceIdFilter()

    @Test
    fun `无上游traceId时自动生成并写入MDC`() {
        val req = MockHttpServletRequest()
        // 断言必须在链路执行期间进行：过滤器在处理完成后会清理 MDC，避免线程复用污染
        val chain = FilterChain { _, _ ->
            assertNotNull(MDC.get(TraceIdFilter.TRACE_ID_KEY))
        }
        filter.doFilter(req, MockHttpServletResponse(), chain)
    }

    @Test
    fun `透传上游traceId`() {
        val req = MockHttpServletRequest()
        req.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-abc")
        val chain = FilterChain { _, _ ->
            assertEquals("trace-abc", MDC.get(TraceIdFilter.TRACE_ID_KEY))
        }
        filter.doFilter(req, MockHttpServletResponse(), chain)
    }
}
