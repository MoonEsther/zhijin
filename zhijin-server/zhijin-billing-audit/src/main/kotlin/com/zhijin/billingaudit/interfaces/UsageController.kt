package com.zhijin.billingaudit.interfaces

import com.zhijin.billingaudit.application.UsageApplicationService
import com.zhijin.billingaudit.interfaces.dto.UsageSummaryResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/** 用量查询端点（管理端，JWT 保护，租户来自 JWT claim）。 */
@RestController
@RequestMapping("/api/usage")
class UsageController(private val usageService: UsageApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('usage:view')")
    fun summary(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: LocalDateTime?,
    ): Result<List<UsageSummaryResponse>> =
        Result.success(usageService.summarize(tenantId, start, end).map { UsageSummaryResponse.toResponse(it) })
}
