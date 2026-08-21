package com.zhijin.app.domain.apikey

import java.time.LocalDateTime

/** 应用 API Key 领域实体（纯 Kotlin，不含 Spring/MyBatis；keyHash 为 SHA-256 哈希，明文不可恢复）。 */
data class AppApiKey(
    val id: Long?,
    val tenantId: Long,
    val appId: Long,
    val keyHash: String,
    val name: String,
    val status: Int = 1,
    /** 创建时间（DB 自动填充，仅列表展示用，领域逻辑不依赖）。 */
    val createTime: LocalDateTime? = null,
) {
    /** 是否启用（status=1，校验/反查均要求启用态）。 */
    fun isActive(): Boolean = status == 1

    /** 吊销：置 status=0（幂等，重复吊销仍为 0）。 */
    fun revoked(): AppApiKey = copy(status = 0)
}
