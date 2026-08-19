package com.zhijin.app.dto

import jakarta.validation.constraints.NotBlank

data class ProviderKeyRequest(
    @field:NotBlank(message = "供应商不能为空") val provider: String = "",
    @field:NotBlank(message = "名称不能为空") val name: String = "",
    @field:NotBlank(message = "API Key 不能为空") val apiKey: String = "",
)
