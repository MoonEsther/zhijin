package com.zhijin.app.domain.modelconfig

/** 应用模型配置领域实体（纯 Kotlin，不含 Spring/MyBatis；每个应用一条）。 */
data class AppModelConfig(
    val id: Long?,
    val tenantId: Long,
    val appId: Long,
    val provider: String,
    val modelName: String,
    val providerKeyId: Long?,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
)
