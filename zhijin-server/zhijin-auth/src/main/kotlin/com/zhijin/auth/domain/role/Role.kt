package com.zhijin.auth.domain.role

/**
 * 角色领域实体（富血模型，纯 Kotlin）。
 *
 * 对应 sys_role 表，但额外携带解析后的权限点编码集合 [perms]：
 * sys_role_permission + sys_permission 关联得到，供 getPerms 直接扁平化合并。
 * 领域层以"权限点编码"表达权限，不暴露持久化关联细节。
 */
data class Role(
    val id: Long?,
    val tenantId: Long,
    val roleCode: String,
    val roleName: String,
    val perms: List<String> = emptyList(),
)
