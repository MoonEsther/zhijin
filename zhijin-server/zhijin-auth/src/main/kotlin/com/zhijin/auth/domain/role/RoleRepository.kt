package com.zhijin.auth.domain.role

/**
 * 角色仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 *
 * 关键约束：角色相关表（sys_role / sys_user_role / sys_role_permission / sys_org_role）
 * 均带 tenant_id。以下方法统一采用"显式 tenantId 参数 + @InterceptorIgnore 绕过租户拦截器"
 * 的实现策略，因为部分调用场景（OAuth2 token 签发、AdminSeeder 启动种子）运行时没有
 * 租户上下文（getTenantId 返回 0 会查不到），显式传租户号是最稳妥的隔离手段。
 */
interface RoleRepository {

    /** 用户直接绑定的角色（含权限点解析）。 */
    fun findRolesByUserId(tenantId: Long, userId: Long): List<Role>

    /** 组织绑定的角色（含权限点解析），供组织继承合并。 */
    fun findRolesByOrgId(tenantId: Long, orgId: Long): List<Role>

    /** 租户下全部角色（管理端角色列表）。 */
    fun listByTenant(tenantId: Long): List<Role>

    /** 按角色编码精确查找（唯一性校验）。 */
    fun findByCode(tenantId: Long, roleCode: String): Role?

    /** 按角色 ID 查找（编辑/删除前校验归属）。 */
    fun findById(tenantId: Long, roleId: Long): Role?

    /** 新增或更新角色，并重设其权限点关联（先删后插，幂等）。 */
    fun save(tenantId: Long, role: Role): Role

    /** 删除角色及其全部关联（角色-权限 / 用户-角色 / 组织-角色）。 */
    fun deleteById(tenantId: Long, roleId: Long)

    /** 重设用户绑定的角色（先删后插）。 */
    fun assignUserRoles(tenantId: Long, userId: Long, roleIds: List<Long>)

    /** 重设组织绑定的角色（先删后插）。 */
    fun assignOrgRoles(tenantId: Long, orgId: Long, roleIds: List<Long>)

    /** 用户绑定的角色 ID 列表（管理端用户列表展示）。 */
    fun findRoleIdsByUserId(tenantId: Long, userId: Long): List<Long>
}
