package com.zhijin.auth.interfaces.dto

import com.zhijin.auth.domain.permission.Permission

/** 权限点响应 DTO：管理端 /api/rbac/permissions 数据源。 */
data class PermissionResponse(
    val id: Long,
    val permCode: String,
    val permName: String,
) {
    companion object {
        /** 领域权限点 → 响应 DTO 的薄映射。 */
        fun toResponse(p: Permission): PermissionResponse = PermissionResponse(
            id = p.id ?: throw IllegalStateException("权限点 ID 缺失: permCode=${p.permCode}"),
            permCode = p.permCode,
            permName = p.permName,
        )
    }
}
