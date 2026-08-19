package com.zhijin.app.controller

import com.zhijin.app.dto.ProviderKeyRequest
import com.zhijin.app.entity.ModelProviderKey
import com.zhijin.app.service.ModelConfigService
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
class ProviderKeyController(private val modelConfigService: ModelConfigService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping
    fun add(@Valid @RequestBody req: ProviderKeyRequest): Result<Long> {
        val key = modelConfigService.addProviderKey(tenantId, req.provider, req.name, req.apiKey)
        return Result.success(key.id)
    }
}
