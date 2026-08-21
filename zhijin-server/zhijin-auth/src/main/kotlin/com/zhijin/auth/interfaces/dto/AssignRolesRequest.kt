package com.zhijin.auth.interfaces.dto

/** 角色分配请求 DTO：为用户/组织重设绑定的角色（roleIds 为角色 ID 列表）。 */
data class AssignRolesRequest(
    val roleIds: List<Long>,
)
