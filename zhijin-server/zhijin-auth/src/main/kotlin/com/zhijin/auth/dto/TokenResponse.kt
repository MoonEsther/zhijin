package com.zhijin.auth.dto

/** 登录/刷新响应。 */
data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val tenantId: Long? = null,
)
