package com.zhijin.auth.domain.permission

/**
 * 权限仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 * 权限字典为平台级（sys_permission 无 tenant_id），查询不依赖租户上下文。
 */
interface PermissionRepository {
    /** 全部权限点（管理端 /api/rbac/permissions 数据源）。 */
    fun findAll(): List<Permission>

    /** 按权限点编码精确查找（角色保存时把 perms 编码解析为 sys_permission.id）。 */
    fun findByCode(permCode: String): Permission?
}
