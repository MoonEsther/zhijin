package com.zhijin.auth.infrastructure.persistence

import com.zhijin.auth.domain.permission.PermissionRepository
import com.zhijin.auth.domain.role.Role
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.auth.repository.AuthOrgRoleMapper
import com.zhijin.auth.repository.AuthRoleMapper
import com.zhijin.auth.repository.AuthRolePermissionMapper
import com.zhijin.auth.repository.AuthUserRoleMapper
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Repository

/**
 * 角色仓储实现：基于 MyBatis-Plus Mapper，持久化记录与领域实体互转。
 *
 * 权限点解析：角色领域实体携带 [Role.perms]（权限点编码），落库时把编码解析为
 * sys_permission.id 写入 sys_role_permission；读库时反向 JOIN 解析回编码。
 * 关联表写入统一显式传租户号，避免无租户上下文（token 签发/Seeder）时被回填 0。
 */
@Repository
class RoleRepositoryImpl(
    private val roleMapper: AuthRoleMapper,
    private val rolePermissionMapper: AuthRolePermissionMapper,
    private val userRoleMapper: AuthUserRoleMapper,
    private val orgRoleMapper: AuthOrgRoleMapper,
    private val permissionRepository: PermissionRepository,
) : RoleRepository {

    /** 记录 → 领域实体并解析权限点编码（JOIN sys_permission，一次一角色，角色量小可接受）。 */
    private fun RoleRecord.toDomainWithPerms(): Role {
        val roleId = id ?: throw IllegalStateException("角色 ID 缺失: roleCode=$roleCode")
        val perms = rolePermissionMapper.selectPermCodesByRoleId(tenantId!!, roleId)
        return toDomain(perms)
    }

    override fun findRolesByUserId(tenantId: Long, userId: Long): List<Role> =
        roleMapper.selectRolesByUserId(tenantId, userId).map { it.toDomainWithPerms() }

    override fun findRolesByOrgId(tenantId: Long, orgId: Long): List<Role> =
        roleMapper.selectRolesByOrgId(tenantId, orgId).map { it.toDomainWithPerms() }

    override fun listByTenant(tenantId: Long): List<Role> =
        roleMapper.selectByTenant(tenantId).map { it.toDomainWithPerms() }

    override fun findByCode(tenantId: Long, roleCode: String): Role? =
        roleMapper.selectByCode(tenantId, roleCode)?.toDomainWithPerms()

    override fun findById(tenantId: Long, roleId: Long): Role? =
        roleMapper.selectByIdAndTenant(tenantId, roleId)?.toDomainWithPerms()

    override fun save(tenantId: Long, role: Role): Role {
        // 显式设置租户号：MyMetaObjectHandler 的 strictInsertFill 仅在字段为空时回填，
        // 若依赖它在无租户上下文（Seeder）下回填会得到 0，故必须先手工赋值。
        val record = RoleRecord.from(role).apply { this.tenantId = tenantId }
        if (record.id == null) {
            roleMapper.insert(record)
        } else {
            roleMapper.updateById(record)
        }
        val roleId = record.id ?: throw IllegalStateException("角色 ID 回填失败: roleCode=${role.roleCode}")

        // 重设权限点关联：先删后插，保证保存幂等
        rolePermissionMapper.deleteByRoleId(tenantId, roleId)
        role.perms.distinct().forEach { code ->
            val perm = permissionRepository.findByCode(code)
                ?: throw BizException(ResultCode.BAD_REQUEST, "权限点不存在: $code")
            rolePermissionMapper.insert(
                RolePermissionRecord(tenantId = tenantId, roleId = roleId, permId = perm.id)
            )
        }
        return Role(id = roleId, tenantId = tenantId, roleCode = role.roleCode, roleName = role.roleName, perms = role.perms)
    }

    override fun deleteById(tenantId: Long, roleId: Long) {
        // 级联清理：权限关联 + 用户绑定 + 组织绑定，最后删角色本体
        rolePermissionMapper.deleteByRoleId(tenantId, roleId)
        userRoleMapper.deleteByRoleId(tenantId, roleId)
        orgRoleMapper.deleteByRoleId(tenantId, roleId)
        roleMapper.deleteByIdAndTenant(tenantId, roleId)
    }

    override fun assignUserRoles(tenantId: Long, userId: Long, roleIds: List<Long>) {
        userRoleMapper.deleteByUserId(tenantId, userId)
        roleIds.distinct().forEach { roleId ->
            userRoleMapper.insert(UserRoleRecord(tenantId = tenantId, userId = userId, roleId = roleId))
        }
    }

    override fun assignOrgRoles(tenantId: Long, orgId: Long, roleIds: List<Long>) {
        orgRoleMapper.deleteByOrgId(tenantId, orgId)
        roleIds.distinct().forEach { roleId ->
            orgRoleMapper.insert(OrgRoleRecord(tenantId = tenantId, orgId = orgId, roleId = roleId))
        }
    }

    override fun findRoleIdsByUserId(tenantId: Long, userId: Long): List<Long> =
        userRoleMapper.selectRoleIdsByUserId(tenantId, userId)
}
