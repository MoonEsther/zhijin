package com.zhijin.auth.web

import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * 从已认证 JWT 的 tenant_id claim 收敛租户上下文。
 * 放在资源服务器认证之后执行；未认证请求(如 /auth/login)跳过。
 */
class JwtTenantFilter : HttpFilter() {

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        if (auth is JwtAuthenticationToken) {
            val tenantId = auth.token.getClaim<Long>("tenant_id")
            TenantContextHolder.setTenantId(tenantId)
        }
        try {
            chain.doFilter(req, res)
        } finally {
            TenantContextHolder.clear()
        }
    }
}
