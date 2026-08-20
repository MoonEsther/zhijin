package com.zhijin.app.interfaces

import com.zhijin.app.application.ModelConfigApplicationService
import com.zhijin.app.interfaces.dto.ProviderKeyRequest
import com.zhijin.app.interfaces.dto.ProviderKeyResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 模型供应商 Key 管理端点（/api/model-keys）。 */
@RestController
@RequestMapping("/api/model-keys")
class ProviderKeyController(private val modelConfigService: ModelConfigApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping
    fun add(@Valid @RequestBody req: ProviderKeyRequest): Result<ProviderKeyResponse> {
        val key = modelConfigService.addProviderKey(tenantId, req.provider, req.name, req.apiKey)
        // 返回响应 DTO，不直接暴露领域实体（S1：接口层不泄露领域对象）
        return Result.success(
            ProviderKeyResponse(
                id = key.id ?: throw IllegalStateException("供应商 Key ID 缺失"),
                provider = key.provider, name = key.name,
            )
        )
    }
}
