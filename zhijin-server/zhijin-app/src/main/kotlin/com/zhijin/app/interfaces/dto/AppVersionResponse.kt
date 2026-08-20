package com.zhijin.app.interfaces.dto

/** 应用版本发布响应。 */
data class AppVersionResponse(
    val id: Long,
    val versionNo: Int,
    val status: Int,
)
