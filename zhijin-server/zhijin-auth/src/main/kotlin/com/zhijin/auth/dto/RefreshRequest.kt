package com.zhijin.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank(message = "refreshToken 不能为空")
    val refreshToken: String = "",
)
