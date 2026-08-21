package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.OrgRoleRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/**
 * 组织-角色关联 Mapper（sys_org_role 表，V6 组织级角色授权）。
 * 显式租户号绕过租户拦截器，与角色 Mapper 同一策略。
 */
@Mapper
interface AuthOrgRoleMapper : BaseMapper<OrgRoleRecord> {

    /** 删除组织全部角色绑定（重设组织角色前先删后插）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_org_role WHERE tenant_id = #{tenantId} AND org_id = #{orgId}")
    fun deleteByOrgId(@Param("tenantId") tenantId: Long, @Param("orgId") orgId: Long)

    /** 删除角色全部组织绑定（删除角色时级联清理）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_org_role WHERE tenant_id = #{tenantId} AND role_id = #{roleId}")
    fun deleteByRoleId(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long)
}
