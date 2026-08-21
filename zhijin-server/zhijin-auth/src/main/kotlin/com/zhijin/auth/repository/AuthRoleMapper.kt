package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.RoleRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 角色 Mapper（持久化层）：只处理 [RoleRecord]，返回记录后由 RoleRepositoryImpl 转领域实体。
 * 包路径固定在 com.zhijin.auth.repository（@MapperScan 硬编码扫描）。
 * 类名 AuthRoleMapper：与 framework 模块已有 SysRoleMapper 区分，避免 Bean 名冲突。
 *
 * 租户策略：角色表带 tenant_id，自定义查询一律 @InterceptorIgnore + 显式传租户号，
 * 覆盖无租户上下文场景（token 签发 getPerms、AdminSeeder 启动种子）；单表 CRUD 走 BaseMapper，
 * 由租户拦截器按上下文自动隔离。
 */
@Mapper
interface AuthRoleMapper : BaseMapper<RoleRecord> {

    /** 用户直接绑定的角色（JOIN sys_user_role）。显式租户号绕过拦截器，供 token 签发/Seeder 等无租户上下文场景。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select(
        "SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE r.tenant_id = #{tenantId} AND ur.tenant_id = #{tenantId} AND ur.user_id = #{userId}"
    )
    fun selectRolesByUserId(@Param("tenantId") tenantId: Long, @Param("userId") userId: Long): List<RoleRecord>

    /** 组织绑定的角色（JOIN sys_org_role），供组织继承合并。显式租户号绕过拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select(
        "SELECT r.* FROM sys_role r JOIN sys_org_role og ON og.role_id = r.id " +
            "WHERE r.tenant_id = #{tenantId} AND og.tenant_id = #{tenantId} AND og.org_id = #{orgId}"
    )
    fun selectRolesByOrgId(@Param("tenantId") tenantId: Long, @Param("orgId") orgId: Long): List<RoleRecord>

    /** 按租户 + 角色编码精确查找（唯一性校验），显式租户号绕过拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_role WHERE tenant_id = #{tenantId} AND role_code = #{roleCode} LIMIT 1")
    fun selectByCode(@Param("tenantId") tenantId: Long, @Param("roleCode") roleCode: String): RoleRecord?

    /** 按租户 + 角色 ID 查找（编辑/删除前归属校验），显式租户号绕过拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_role WHERE tenant_id = #{tenantId} AND id = #{roleId} LIMIT 1")
    fun selectByIdAndTenant(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long): RoleRecord?

    /** 租户下全部角色（管理端角色列表），显式租户号绕过拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_role WHERE tenant_id = #{tenantId} ORDER BY id")
    fun selectByTenant(@Param("tenantId") tenantId: Long): List<RoleRecord>

    /** 按租户 + 角色 ID 删除（显式租户号，避免依赖租户拦截器上下文）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_role WHERE tenant_id = #{tenantId} AND id = #{roleId}")
    fun deleteByIdAndTenant(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long)
}
