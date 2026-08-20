package com.zhijin.app.config

import com.zhijin.app.application.ModelConfigApplicationService
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.domain.ModelKeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ModelKeyResolver 适配 Bean（解决 C1/N1：避免 orchestrator 直接依赖 app）。
 * 注入 ModelConfigApplicationService.getPlainKey 实现，内部从 TenantContextHolder 取 tenantId。
 */
@Configuration
class ModelKeyResolverConfig(private val modelConfigService: ModelConfigApplicationService) {

    @Bean
    fun modelKeyResolver(): ModelKeyResolver = ModelKeyResolver { keyId ->
        val tenantId = TenantContextHolder.getRequiredTenantId()
        modelConfigService.getPlainKey(tenantId, keyId)
    }
}
