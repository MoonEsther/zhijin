package com.zhijin.auth.interfaces.dto

/** 组织请求 DTO：新建/更新组织时提交。status 语义 1=启用，0=停用。 */
data class OrgRequest(
    val parentId: Long = 0,
    val orgName: String,
    val sort: Int = 0,
    val status: Int = 1,
)
