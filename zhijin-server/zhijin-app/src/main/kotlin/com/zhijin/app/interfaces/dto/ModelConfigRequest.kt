package com.zhijin.app.interfaces.dto

import jakarta.validation.constraints.NotBlank

data class ModelConfigRequest(
    @field:NotBlank(message = "供应商不能为空") val provider: String = "",
    @field:NotBlank(message = "模型名不能为空") val modelName: String = "",
    val providerKeyId: Long? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)
