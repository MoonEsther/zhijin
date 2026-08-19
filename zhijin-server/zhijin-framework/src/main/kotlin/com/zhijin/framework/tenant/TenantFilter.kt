package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

/**
 * 从请求头 X-Tenant-Id 解析租户 ID 写入上下文。
 * B2 认证接入后，租户来源收敛为 JWT 声明，本过滤器随之调整，接口不变。
 */
class TenantFilter : HttpFilter() {

    companion object {
        const val TENANT_HEADER = "X-Tenant-Id"
    }

    private val log = LoggerFactory.getLogger(TenantFilter::class.java)

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val header = request.getHeader(TENANT_HEADER)
        val tenantId = header?.toLongOrNull()
        if (header != null && tenantId == null) {
            log.warn("非法租户请求头: {}", header)
        }
        TenantContext.set(tenantId)
        try {
            chain.doFilter(request, res)
        } finally {
            TenantContext.clear()
        }
    }
}
