package com.zhijin.app.interfaces.dto

import java.time.LocalDateTime

/**
 * API Key 响应：plainKey 仅在生成响应中返回一次（DB 只存哈希，不可恢复明文）；
 * 列表查询时 plainKey 置空、仅返回 id/name/createTime，避免把明文或哈希暴露给前端。
 */
data class ApiKeyResponse(
    val id: Long,
    val plainKey: String,
    val name: String,
    /** 创建时间：列表查询返回，生成响应为 null（前端仅列表展示用）。 */
    val createTime: LocalDateTime? = null,
)
