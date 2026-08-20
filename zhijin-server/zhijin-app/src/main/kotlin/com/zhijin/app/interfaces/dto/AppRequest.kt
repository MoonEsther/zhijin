package com.zhijin.app.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 应用创建/更新请求体。 */
data class AppRequest(
    @field:NotBlank(message = "应用名不能为空")
    @field:Size(max = 128)
    val name: String = "",
    val description: String = "",
    val iconUri: String = "",
)
