package com.zhijin.app.controller

import com.zhijin.app.dto.AppVersionResponse
import com.zhijin.app.service.PublishService
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 应用发布端点（/api/apps/{id}/publish）。 */
@RestController
@RequestMapping("/api/apps")
class PublishController(private val publishService: PublishService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long): Result<AppVersionResponse> =
        Result.success(publishService.publish(tenantId, id))
}
