package com.zhijin.app.controller

import com.zhijin.app.dto.AppRequest
import com.zhijin.app.dto.AppResponse
import com.zhijin.app.service.AppService
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 应用管理端点（/api/apps，JWT 保护，租户来自 JWT claim）。 */
@RestController
@RequestMapping("/api/apps")
class AppController(private val appService: AppService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping
    fun create(@Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(appService.create(tenantId, req))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): Result<AppResponse> =
        Result.success(appService.get(tenantId, id))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(appService.update(tenantId, id, req))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Result<Unit> {
        appService.delete(tenantId, id)
        return Result.success()
    }
}
