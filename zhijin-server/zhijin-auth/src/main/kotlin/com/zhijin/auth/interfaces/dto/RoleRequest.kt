package com.zhijin.auth.interfaces.dto

/** 角色请求 DTO：新建/更新角色时提交（perms 为权限点编码列表）。 */
data class RoleRequest(
    val roleCode: String,
    val roleName: String,
    val perms: List<String> = emptyList(),
)
