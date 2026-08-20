package com.zhijin.app.interfaces.dto

/** 应用响应体。 */
data class AppResponse(
    val id: Long,
    val appKey: String,
    val name: String,
    val description: String,
    val iconUri: String,
    val status: Int,
)
