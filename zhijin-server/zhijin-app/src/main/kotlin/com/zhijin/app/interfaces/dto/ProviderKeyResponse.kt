package com.zhijin.app.interfaces.dto

/** 模型供应商 Key 新增响应（仅暴露 id/供应商/名称，不暴露加密串等内部字段）。 */
data class ProviderKeyResponse(
    val id: Long,
    val provider: String,
    val name: String,
)
