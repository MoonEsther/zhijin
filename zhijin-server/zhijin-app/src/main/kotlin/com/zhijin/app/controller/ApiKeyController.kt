package com.zhijin.app.controller

import com.zhijin.app.dto.ApiKeyResponse
import com.zhijin.app.service.AppApiKeyService
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 应用 API Key 端点（/api/apps/{id}/api-keys）：生成与吊销。 */
@RestController
@RequestMapping("/api/apps")
class ApiKeyController(private val apiKeyService: AppApiKeyService) {

    // 租户号统一取自上下文过滤器，防止跨租户越权
    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    /** 生成 API Key：明文仅在此响应中返回一次。 */
    @PostMapping("/{id}/api-keys")
    fun generate(@PathVariable id: Long, @RequestParam(name = "name", defaultValue = "") name: String): Result<ApiKeyResponse> =
        Result.success(apiKeyService.generate(tenantId, id, name))

    /** 吊销 API Key：幂等，keyId 不存在或非本租户时静默成功。 */
    @DeleteMapping("/{id}/api-keys/{keyId}")
    fun revoke(@PathVariable id: Long, @PathVariable keyId: Long): Result<Unit> {
        apiKeyService.revoke(tenantId, id, keyId)
        return Result.success()
    }
}
