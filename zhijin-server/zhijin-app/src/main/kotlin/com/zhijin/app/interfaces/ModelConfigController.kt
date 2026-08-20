package com.zhijin.app.interfaces

import com.zhijin.app.application.ModelConfigApplicationService
import com.zhijin.app.interfaces.dto.ModelConfigRequest
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
class ModelConfigController(private val modelConfigService: ModelConfigApplicationService) {

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
