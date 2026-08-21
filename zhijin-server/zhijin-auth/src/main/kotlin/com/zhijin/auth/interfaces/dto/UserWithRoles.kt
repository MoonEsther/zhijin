package com.zhijin.auth.interfaces.dto

/**
 * 管理端用户列表项：用户基本信息 + 已绑定的角色 ID 与角色名（供 /api/rbac/users 返回）。
 * roleNames 由 listUsers 解析后内嵌，前端「当前角色」列不再依赖独立的 /api/rbac/roles 查询，
 * 从而让仅有 user:manage（无 role:manage）的管理员也能看到用户角色。
 */
data class UserWithRoles(
    val id: Long,
    val username: String,
    val nickname: String,
    val status: Int,
    val orgId: Long?,
    val roleIds: List<Long>,
    val roleNames: List<String>,
)
