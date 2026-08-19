package com.zhijin.app.controller

import com.zhijin.app.dto.ModelConfigRequest
import com.zhijin.app.service.ModelConfigService
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 应用模型配置端点（/api/apps/{id}/model-config）。 */
@RestController
@RequestMapping("/api/apps")
class ModelConfigController(private val modelConfigService: ModelConfigService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PutMapping("/{id}/model-config")
    fun save(
        @PathVariable id: Long,
        @Valid @RequestBody req: ModelConfigRequest,
    ): Result<Unit> {
        modelConfigService.saveConfig(tenantId, id, req.provider, req.modelName, req.providerKeyId)
        return Result.success()
    }
}
