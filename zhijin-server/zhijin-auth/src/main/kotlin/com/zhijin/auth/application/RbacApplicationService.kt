package com.zhijin.auth.application

import com.zhijin.auth.domain.permission.Permission
import com.zhijin.auth.domain.permission.PermissionRepository
import com.zhijin.auth.domain.role.Role
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.auth.domain.user.User
import com.zhijin.auth.domain.user.UserRepository
import com.zhijin.auth.interfaces.dto.UserWithRoles
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * RBAC 应用服务（方案 C）：权限点查询、角色 CRUD、用户角色分配、权限合并。
 *
 * 核心用例 [getPerms]：计算某用户在某一租户下的全部权限点。
 *   权限来源 = 用户直接绑定的角色 ∪ 用户所属组织绑定的角色（组织继承），
 *   两路角色去重后扁平化权限点并去重。
 * 该方法被两处调用：
 *   1) SecurityConfig.tokenCustomizer 在 OAuth2 签发 JWT 时写入 perms claim；
 *   2) 登录链路 UserDetailsServiceImpl 构造 UserDetails 时预解析 perms。
 * 两处都可能在无租户上下文的环境执行，因此底层仓储全部采用"显式 tenantId + 绕过租户拦截器"。
 */
@Service
class RbacApplicationService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
) {

    /**
     * 合并用户权限点：用户角色 ∪ 组织角色（组织继承）。
     * 用户无组织（orgId 为空）时组织角色为空列表，不参与合并。
     */
    fun getPerms(tenantId: Long, userId: Long): List<String> {
        // 用户直接绑定的角色
        val userRoles = roleRepository.findRolesByUserId(tenantId, userId)
        // 用户所属组织绑定的角色（组织继承：组织内的角色对组织内用户生效）
        val user = userRepository.findById(tenantId, userId)
        val orgRoles = user?.orgId?.let { roleRepository.findRolesByOrgId(tenantId, it) } ?: emptyList()
        // 合并去重：同一角色可能同时被用户与组织绑定
        val allRoles = (userRoles + orgRoles).distinctBy { it.id }
        return allRoles.flatMap { it.perms }.distinct()
    }

    /** 平台全部权限点（管理端 /api/rbac/permissions 数据源）。 */
    fun listPermissions(): List<Permission> = permissionRepository.findAll()

    /** 租户下全部角色（含各自权限点）。 */
    fun listRoles(tenantId: Long): List<Role> = roleRepository.listByTenant(tenantId)

    /** 新建角色：校验编码必填 + 租户内唯一 + 权限点合法，随后落库并绑定权限点。 */
    @Transactional
    fun createRole(tenantId: Long, roleCode: String, roleName: String, perms: List<String>): Role {
        if (roleCode.isBlank() || roleName.isBlank()) {
            throw BizException(ResultCode.BAD_REQUEST, "角色编码与名称不能为空")
        }
        if (roleRepository.findByCode(tenantId, roleCode) != null) {
            throw BizException(ResultCode.BAD_REQUEST, "角色编码已存在: $roleCode")
        }
        // 权限点合法性提前到写库之前：避免角色行已写入、旧权限已删后才因非法 permCode 抛错
        validatePerms(perms)
        return roleRepository.save(
            tenantId, Role(id = null, tenantId = tenantId, roleCode = roleCode, roleName = roleName, perms = perms)
        )
    }

    /** 更新角色：校验存在 + 编码唯一（排除自身）+ 权限点合法，重设权限点。 */
    @Transactional
    fun updateRole(tenantId: Long, roleId: Long, roleCode: String, roleName: String, perms: List<String>): Role {
        if (roleCode.isBlank() || roleName.isBlank()) {
            throw BizException(ResultCode.BAD_REQUEST, "角色编码与名称不能为空")
        }
        val existing = roleRepository.findById(tenantId, roleId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "角色不存在: id=$roleId")
        roleRepository.findByCode(tenantId, roleCode)?.let {
            if (it.id != roleId) throw BizException(ResultCode.BAD_REQUEST, "角色编码已存在: $roleCode")
        }
        // 权限点合法性提前到写库之前（@Transactional 兜底回滚，但前置校验更早失败、语义更清晰）
        validatePerms(perms)
        return roleRepository.save(
            tenantId, existing.copy(roleCode = roleCode, roleName = roleName, perms = perms)
        )
    }

    /** 删除角色（级联清理权限/用户/组织关联）。 */
    @Transactional
    fun deleteRole(tenantId: Long, roleId: Long) {
        roleRepository.findById(tenantId, roleId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "角色不存在: id=$roleId")
        roleRepository.deleteById(tenantId, roleId)
    }

    /** 用户列表（含各自角色 ID，供管理端分配角色）。 */
    fun listUsers(tenantId: Long): List<UserWithRoles> =
        userRepository.listByTenant(tenantId).map { user ->
            val userId = user.id ?: throw IllegalStateException("用户 ID 缺失: username=${user.username}")
            UserWithRoles(
                id = userId,
                username = user.username,
                nickname = user.nickname,
                status = user.status,
                orgId = user.orgId,
                roleIds = roleRepository.findRoleIdsByUserId(tenantId, userId),
            )
        }

    /** 重设用户绑定的角色。 */
    @Transactional
    fun assignUserRoles(tenantId: Long, userId: Long, roleIds: List<Long>) {
        // 校验用户存在，避免对不存在的用户写入关联
        userRepository.findById(tenantId, userId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "用户不存在: id=$userId")
        roleRepository.assignUserRoles(tenantId, userId, roleIds)
    }

    /**
     * 校验权限点编码全部合法（存在于平台权限字典）。
     * 在写库前调用，保证"角色行更新 + 旧权限删除 + 新权限绑定"不会因非法 permCode 处于半完成状态。
     */
    private fun validatePerms(perms: List<String>) {
        if (perms.isEmpty()) return
        val existing = permissionRepository.findAll().map { it.permCode }.toSet()
        perms.forEach { code ->
            if (code !in existing) throw BizException(ResultCode.BAD_REQUEST, "权限点不存在: $code")
        }
    }
}
