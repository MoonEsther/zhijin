package com.zhijin.framework.log

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 注册 traceId 过滤器，作用于所有请求。 */
@Configuration
class LogConfig {

    @Bean
    fun traceIdFilter(): FilterRegistrationBean<TraceIdFilter> =
        FilterRegistrationBean<TraceIdFilter>().apply {
            setFilter(TraceIdFilter())
            addUrlPatterns("/*")
            order = Int.MIN_VALUE // 最早执行，保证下游拿到 traceId
        }
}
