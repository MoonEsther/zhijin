package com.zhijin.auth.dto

/** /auth/validate 返回的身份信息。 */
data class ValidateResponse(
    val username: String,
    val userId: Long?,
    val tenantId: Long?,
    val roles: List<String>,
)
