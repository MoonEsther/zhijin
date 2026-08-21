package com.zhijin.auth.infrastructure.persistence

import com.zhijin.auth.domain.permission.Permission
import com.zhijin.auth.domain.permission.PermissionRepository
import com.zhijin.auth.repository.AuthPermissionMapper
import org.springframework.stereotype.Repository

/**
 * 权限点仓储实现：sys_permission 为平台级字典（无 tenant_id，租户拦截器 IGNORE_TABLES），
 * selectList 不携带租户过滤，任何上下文均可安全查询。
 */
@Repository
class PermissionRepositoryImpl(
    private val permissionMapper: AuthPermissionMapper,
) : PermissionRepository {

    override fun findAll(): List<Permission> =
        permissionMapper.selectList(null).map { it.toDomain() }

    override fun findByCode(permCode: String): Permission? =
        permissionMapper.selectByCode(permCode)?.toDomain()
}
