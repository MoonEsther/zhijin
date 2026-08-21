package com.zhijin.app.interfaces

import com.zhijin.app.application.AppApplicationService
import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.app.interfaces.dto.AppRequest
import com.zhijin.app.interfaces.dto.AppResponse
import com.zhijin.app.interfaces.dto.AppVersionResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 应用管理端点（/api/apps，JWT 保护，租户来自 JWT claim）：CRUD + 发布。 */
@RestController
@RequestMapping("/api/apps")
class AppController(private val appService: AppApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    /** 列表：返回租户下全部应用（前端列表页数据源）。 */
    @GetMapping
    @PreAuthorize("hasAuthority('app:view')")
    fun list(): Result<List<AppResponse>> =
        Result.success(appService.list(tenantId).map { it.toResponse() })

    @PostMapping
    @PreAuthorize("hasAuthority('app:create')")
    fun create(@Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(appService.create(tenantId, req.name, req.description, req.iconUri).toResponse())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('app:view')")
    fun get(@PathVariable id: Long): Result<AppResponse> =
        Result.success(appService.get(tenantId, id).toResponse())

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('app:update')")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(appService.update(tenantId, id, req.name, req.description, req.iconUri).toResponse())

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('app:delete')")
    fun delete(@PathVariable id: Long): Result<Unit> {
        appService.delete(tenantId, id)
        return Result.success()
    }

    /** 发布：返回新版本快照（版本号自增）。 */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('app:publish')")
    fun publish(@PathVariable id: Long): Result<AppVersionResponse> =
        Result.success(appService.publish(tenantId, id).toVersionResponse())

    // ---- 领域实体 → 响应 DTO 的薄映射（不直接暴露领域对象） ----

    private fun App.toResponse() = AppResponse(
        id = id ?: throw IllegalStateException("应用 ID 缺失"),
        appKey = appKey, name = name, description = description, iconUri = iconUri,
        status = status.ordinal,
    )

    private fun AppVersion.toVersionResponse() = AppVersionResponse(
        id = id ?: throw IllegalStateException("版本 ID 缺失"),
        versionNo = versionNo, status = status,
    )
}
