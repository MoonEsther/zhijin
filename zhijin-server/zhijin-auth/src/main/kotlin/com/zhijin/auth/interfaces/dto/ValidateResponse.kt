package com.zhijin.auth.interfaces.dto

/** /auth/validate 返回的身份信息（由原 dto/ValidateResponse 迁移到 interfaces/dto）。 */
data class ValidateResponse(
    val username: String,
    val userId: Long?,
    val tenantId: Long?,
    val roles: List<String>,
)
