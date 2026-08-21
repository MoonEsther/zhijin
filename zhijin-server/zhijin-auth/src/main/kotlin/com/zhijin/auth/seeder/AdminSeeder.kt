package com.zhijin.auth.seeder

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.auth.domain.permission.Permissions
import com.zhijin.auth.entity.SysTenant
import com.zhijin.auth.infrastructure.persistence.OrganizationRecord
import com.zhijin.auth.infrastructure.persistence.PermissionRecord
import com.zhijin.auth.infrastructure.persistence.RolePermissionRecord
import com.zhijin.auth.infrastructure.persistence.RoleRecord
import com.zhijin.auth.infrastructure.persistence.SysUserRecord
import com.zhijin.auth.infrastructure.persistence.UserRoleRecord
import com.zhijin.auth.repository.AuthOrganizationMapper
import com.zhijin.auth.repository.AuthPermissionMapper
import com.zhijin.auth.repository.AuthRoleMapper
import com.zhijin.auth.repository.AuthRolePermissionMapper
import com.zhijin.auth.repository.AuthUserRoleMapper
import com.zhijin.auth.repository.SysTenantMapper
import com.zhijin.auth.repository.SysUserMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * 启动时幂等种子：默认租户 + 管理员账号 + 根组织 + admin 角色（全权限）+ 角色分配。
 *
 * RBAC 幂等策略（方案 C）：
 *   * 权限字典（sys_permission）为平台级，按 perm_code 查重后补齐 10 个权限点；
 *   * admin 角色按 租户+role_code 查重，创建后每次启动重设其权限为"全权限"，
 *     保证管理员始终拥有全部能力（该角色为引导角色，不允许被降权成普通角色）；
 *   * 管理员绑定 admin 角色（sys_user_role）+ 归属根组织（sys_user.org_id）。
 *
 * 关键：启动时无租户上下文（TenantContextHolder.getTenantId() 为 null，租户拦截器会拼 0），
 * 所有查询/写入必须显式传租户号；写入时先手工赋值 tenantId，避免 MyMetaObjectHandler
 * 在无租户上下文时把 tenant_id 回填成 0。
 */
@Component
class AdminSeeder(
    private val tenantMapper: SysTenantMapper,
    private val userMapper: SysUserMapper,
    private val permissionMapper: AuthPermissionMapper,
    private val roleMapper: AuthRoleMapper,
    private val userRoleMapper: AuthUserRoleMapper,
    private val rolePermissionMapper: AuthRolePermissionMapper,
    private val organizationMapper: AuthOrganizationMapper,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

    /** 种子权限点：编码 → 名称（与 Permissions.kt 常量对齐，保证字典与注解字面量一致）。 */
    private val seedPerms = listOf(
        Permissions.APP_VIEW to "查看应用",
        Permissions.APP_CREATE to "创建应用",
        Permissions.APP_UPDATE to "更新应用",
        Permissions.APP_DELETE to "删除应用",
        Permissions.APP_PUBLISH to "发布应用",
        Permissions.APIKEY_MANAGE to "管理API Key",
        Permissions.USAGE_VIEW to "查看用量",
        Permissions.AUDIT_VIEW to "查看审计",
        Permissions.USER_MANAGE to "管理用户",
        Permissions.ROLE_MANAGE to "管理角色",
    )

    override fun run(args: ApplicationArguments) {
        // 1. 幂等创建默认租户：sys_tenant 在租户拦截器 IGNORE_TABLES 中，查询不会被强制拼 tenant_id。
        val tenant = tenantMapper.selectOne(QueryWrapper<SysTenant>().eq("tenant_code", "default"))
        val tenantId = if (tenant == null) {
            val t = SysTenant(tenantCode = "default", tenantName = "默认租户", status = 1)
            tenantMapper.insert(t)
            log.info("[seed] 已创建默认租户 id={}, code=default", t.id)
            t.id!!
        } else {
            log.info("[seed] 默认租户已存在 id={}", tenant.id)
            tenant.id!!
        }

        // 2. 幂等创建管理员：启动时无租户上下文，走 findByTenantIdAndUsername 绕过租户拦截器，
        //    否则租户拦截器会把 tenant_id 拼成 0，导致重启时查不到已有 admin 而重复插入。
        val admin = userMapper.findByTenantIdAndUsername(tenantId, "admin")
        val adminId: Long
        if (admin == null) {
            val initPwd = System.getenv("ADMIN_INIT_PASSWORD") ?: "admin123"
            val u = SysUserRecord(
                tenantId = tenantId, username = "admin",
                password = passwordEncoder.encode(initPwd)!!, nickname = "管理员", status = 1,
            )
            userMapper.insert(u)
            adminId = u.id ?: throw IllegalStateException("管理员 ID 回填失败")
            log.info("[seed] 已创建默认租户({})与管理员 admin", tenantId)
        } else {
            adminId = admin.id ?: throw IllegalStateException("管理员 ID 缺失")
            log.info("[seed] 管理员 admin 已存在 tenantId={}", tenantId)
        }

        // 3. 幂等创建根组织（parent_id=0）：组织树根节点，管理员挂到该组织下。
        //    注意：selectList 走租户拦截器会拼 tenant_id=0 导致查不到，故改用 @InterceptorIgnore 的
        //    selectByTenant 后按业务字段过滤（启动无租户上下文，这是既有 SysUserMapper 的既有模式）。
        val rootOrg = organizationMapper.selectByTenant(tenantId)
            .firstOrNull { it.parentId == 0L && it.orgName == "根组织" }
        val rootOrgId: Long
        if (rootOrg == null) {
            // 显式赋值 tenantId，避免无租户上下文时 MyMetaObjectHandler 回填 0
            val org = OrganizationRecord(tenantId = tenantId, parentId = 0, orgName = "根组织", sort = 0, status = 1)
            organizationMapper.insert(org)
            rootOrgId = org.id ?: throw IllegalStateException("根组织 ID 回填失败")
            log.info("[seed] 已创建根组织 id={}, tenantId={}", rootOrgId, tenantId)
        } else {
            rootOrgId = rootOrg.id ?: throw IllegalStateException("根组织 ID 缺失")
            log.info("[seed] 根组织已存在 id={}", rootOrgId)
        }

        // 4. 幂等补齐权限字典：sys_permission 平台级（无 tenant_id），按 perm_code 查重后插入。
        val allPermIds = seedPerms.map { (code, name) ->
            permissionMapper.selectByCode(code)?.let { it.id!! }
                ?: run {
                    val p = PermissionRecord(permCode = code, permName = name, parentId = 0)
                    permissionMapper.insert(p)
                    log.info("[seed] 已创建权限点 {} ({})", code, name)
                    p.id ?: throw IllegalStateException("权限点 ID 回填失败: $code")
                }
        }

        // 5. 幂等创建 admin 角色（全权限）：按租户+role_code 查重。
        val adminRole = roleMapper.selectByCode(tenantId, "admin")
        val adminRoleId: Long
        if (adminRole == null) {
            val r = RoleRecord(tenantId = tenantId, roleCode = "admin", roleName = "管理员")
            roleMapper.insert(r)
            adminRoleId = r.id ?: throw IllegalStateException("admin 角色 ID 回填失败")
            log.info("[seed] 已创建 admin 角色 id={}, tenantId={}", adminRoleId, tenantId)
        } else {
            adminRoleId = adminRole.id ?: throw IllegalStateException("admin 角色 ID 缺失")
            log.info("[seed] admin 角色已存在 id={}", adminRoleId)
        }
        // 重设 admin 角色为全权限：先删后插，保证引导角色权限不被意外降级（幂等）
        rolePermissionMapper.deleteByRoleId(tenantId, adminRoleId)
        allPermIds.forEach { permId ->
            rolePermissionMapper.insert(RolePermissionRecord(tenantId = tenantId, roleId = adminRoleId, permId = permId))
        }
        log.info("[seed] admin 角色已同步全权限，共 {} 项", allPermIds.size)

        // 6. 幂等绑定 admin 用户 → admin 角色（sys_user_role，唯一约束 uk_sys_user_role 兜底）
        val hasUserRole = userRoleMapper.selectRoleIdsByUserId(tenantId, adminId).contains(adminRoleId)
        if (!hasUserRole) {
            userRoleMapper.insert(UserRoleRecord(tenantId = tenantId, userId = adminId, roleId = adminRoleId))
            log.info("[seed] 已绑定管理员(uid={})到 admin 角色(roleId={})", adminId, adminRoleId)
        }

        // 7. 幂等设置管理员归属根组织（sys_user.org_id）
        if (admin?.orgId != rootOrgId) {
            userMapper.updateOrgId(tenantId, adminId, rootOrgId)
            log.info("[seed] 已设置管理员(uid={})归属根组织(orgId={})", adminId, rootOrgId)
        }
    }
}
