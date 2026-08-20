package com.zhijin.chat.web

import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 开放 API 鉴权：读取 X-API-Key，校验并设置租户上下文 + appId 请求属性。
 * 仅作用于 /v1 开放接口，管理端仍走 JWT（B2 资源服务器链）。
 */
class ApiKeyAuthFilter(private val resolver: ApiKeyResolver) : HttpFilter() {

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = res as HttpServletResponse
        val apiKey = request.getHeader("X-API-Key") ?: return unauthorized(response)
        val resolved = resolver.findByPlainKey(apiKey) ?: return unauthorized(response)
        // 租户由 Key 解析而来，写入上下文供后续业务（会话/消息表写入）走租户隔离
        TenantContextHolder.setTenantId(resolved.first)
        request.setAttribute("zhijin.appId", resolved.second)
        try {
            chain.doFilter(request, response)
        } finally {
            // 请求结束清理租户上下文，避免线程复用导致串租
            TenantContextHolder.clear()
        }
    }

    private fun unauthorized(response: HttpServletResponse) {
        response.status = 401
        response.writer.write("""{"code":3000,"message":"无效 API Key"}""")
    }
}
