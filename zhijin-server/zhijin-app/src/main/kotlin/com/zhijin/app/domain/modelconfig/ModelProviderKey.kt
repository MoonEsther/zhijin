package com.zhijin.app.domain.modelconfig

/** 模型供应商 Key 领域实体（纯 Kotlin，不含 Spring/MyBatis；encryptedKey 已加密，明文不落库）。 */
data class ModelProviderKey(
    val id: Long?,
    val tenantId: Long,
    val provider: String,
    val name: String,
    val encryptedKey: String,
    val status: Int = 1,
) {
    /** 是否启用（status=1）。 */
    fun isActive(): Boolean = status == 1
}
