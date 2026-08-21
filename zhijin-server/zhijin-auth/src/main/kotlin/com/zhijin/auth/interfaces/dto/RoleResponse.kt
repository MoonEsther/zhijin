package com.zhijin.auth.interfaces.dto

import com.zhijin.auth.domain.role.Role

/** 角色响应 DTO：返回角色信息及其权限点编码。 */
data class RoleResponse(
    val id: Long,
    val roleCode: String,
    val roleName: String,
    val perms: List<String>,
) {
    companion object {
        /** 领域角色 → 响应 DTO 的薄映射。 */
        fun toResponse(role: Role): RoleResponse = RoleResponse(
            id = role.id ?: throw IllegalStateException("角色 ID 缺失: roleCode=${role.roleCode}"),
            roleCode = role.roleCode,
            roleName = role.roleName,
            perms = role.perms,
        )
    }
}
