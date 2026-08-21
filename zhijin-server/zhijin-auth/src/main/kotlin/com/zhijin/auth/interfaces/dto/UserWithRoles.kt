package com.zhijin.auth.interfaces.dto

/** 管理端用户列表项：用户基本信息 + 已绑定的角色 ID（供 /api/rbac/users 返回）。 */
data class UserWithRoles(
    val id: Long,
    val username: String,
    val nickname: String,
    val status: Int,
    val orgId: Long?,
    val roleIds: List<Long>,
)
