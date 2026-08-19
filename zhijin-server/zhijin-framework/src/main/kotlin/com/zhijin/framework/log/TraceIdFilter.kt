package com.zhijin.framework.log

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import java.util.UUID

/**
 * 全链路 traceId：优先透传请求头中的 traceId（跨 Kotlin/Python 链路），
 * 无则生成。写入 MDC 供日志输出，响应头回传便于前端/客户对账。
 */
class TraceIdFilter : HttpFilter() {

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = res as HttpServletResponse
        val traceId = request.getHeader(TRACE_ID_HEADER) ?: UUID.randomUUID().toString().replace("-", "")
        MDC.put(TRACE_ID_KEY, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
