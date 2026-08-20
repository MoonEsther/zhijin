package com.zhijin.app.config

import com.zhijin.billingaudit.application.UsageApplicationService
import com.zhijin.billingaudit.domain.usage.UsageRecorder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 用量记录器适配：把 zhijin-chat 的 UsageRecorder 函数式端口
 * 适配到 zhijin-billing-audit 的 UsageApplicationService（入库）。
 * 因 zhijin-chat 已依赖 billing-audit（端口类型），chat 不能再反向依赖 app 的装配，
 * 故在此（zhijin-app，已依赖 chat + billing-audit）提供适配 Bean，避免循环依赖。
 */
@Configuration
class UsageRecorderConfig(private val usageService: UsageApplicationService) {

    @Bean
    fun usageRecorder(): UsageRecorder = UsageRecorder { usageService.record(it) }
}
