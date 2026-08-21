package com.zhijin.auth.domain.permission

/**
 * 权限领域实体（富血模型，纯 Kotlin）。
 *
 * 对应 sys_permission 表（平台级权限字典，无 tenant_id，租户拦截器忽略该表）。
 * 权限点是整个平台共享的"能力清单"，角色通过 sys_role_permission 关联权限点，
 * 用户/组织通过角色间接获得权限点集合。
 */
data class Permission(
    val id: Long?,
    val permCode: String,
    val permName: String,
    val parentId: Long,
)
