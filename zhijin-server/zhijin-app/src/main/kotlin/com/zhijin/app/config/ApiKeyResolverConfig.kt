package com.zhijin.app.config

import com.zhijin.app.service.AppApiKeyService
import com.zhijin.chat.web.ApiKeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 开放 API 鉴权解析器适配：把 zhijin-chat 的 ApiKeyResolver 函数式接口
 * 适配到 zhijin-app 的 AppApiKeyService（按明文 Key 反查租户+应用）。
 * 因 zhijin-app 已依赖 zhijin-chat，chat 不能再反向依赖 app，故在此提供适配 Bean。
 */
@Configuration
class ApiKeyResolverConfig(private val apiKeyService: AppApiKeyService) {

    @Bean
    fun apiKeyResolver(): ApiKeyResolver = ApiKeyResolver { plainKey ->
        apiKeyService.findByPlainKey(plainKey)
    }
}
