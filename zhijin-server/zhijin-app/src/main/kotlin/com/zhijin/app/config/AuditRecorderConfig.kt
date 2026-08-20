package com.zhijin.app.config

import com.zhijin.billingaudit.application.AuditApplicationService
import com.zhijin.billingaudit.domain.audit.AuditRecorder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 审计记录器适配：把管理操作（zhijin-app 应用服务）的 AuditRecorder 函数式端口
 * 适配到 zhijin-billing-audit 的 AuditApplicationService（入库）。
 * 适配 Bean 放在 zhijin-app（已依赖 billing-audit），避免模块反向依赖与循环依赖。
 */
@Configuration
class AuditRecorderConfig(private val auditService: AuditApplicationService) {

    @Bean
    fun auditRecorder(): AuditRecorder = AuditRecorder { auditService.record(it) }
}
