package com.zhijin.app.dto

/** API Key 创建响应：plainKey 仅此一次返回（DB 只存哈希，不再可恢复明文）。 */
data class ApiKeyResponse(
    val id: Long,
    val plainKey: String,
    val name: String,
)
