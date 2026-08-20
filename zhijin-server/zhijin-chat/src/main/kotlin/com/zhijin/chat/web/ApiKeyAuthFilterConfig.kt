package com.zhijin.chat.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 注册开放 API 鉴权过滤器（/v1 路径），在租户上下文过滤器之后。 */
@Configuration
class ApiKeyAuthFilterConfig(private val resolver: ApiKeyResolver) {

    @Bean
    fun apiKeyAuthFilter(): FilterRegistrationBean<ApiKeyAuthFilter> =
        FilterRegistrationBean<ApiKeyAuthFilter>().apply {
            // 注意：FilterRegistrationBean#getFilter 返回 @Nullable，而 setFilter 参数非空，
            // Kotlin 不会为这种可空性不匹配合成 var，须显式调用 setter（与 LogConfig 一致）。
            setFilter(ApiKeyAuthFilter(resolver))
            addUrlPatterns("/v1/*")
            order = 1 // 在 B1 的 TenantFilter(order MIN_VALUE+1) 之后
        }
}
