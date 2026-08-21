package com.zhijin.billingaudit.interfaces

import com.zhijin.billingaudit.application.AuditApplicationService
import com.zhijin.billingaudit.interfaces.dto.AuditLogResponse
import com.zhijin.billingaudit.interfaces.dto.PageResultResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 审计日志查询端点（管理端，JWT 保护，租户来自 JWT claim）。 */
@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController(private val auditService: AuditApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    fun page(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Result<PageResultResponse<AuditLogResponse>> {
        val result = auditService.page(tenantId, page, size)
        return Result.success(
            PageResultResponse(
                items = result.items.map { AuditLogResponse.toResponse(it) },
                total = result.total,
            )
        )
    }
}
