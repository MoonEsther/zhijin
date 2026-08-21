package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.OrganizationRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 组织 Mapper（持久化层）：sys_organization 带 tenant_id。
 * 显式租户号绕过租户拦截器，与角色 Mapper 同一策略（覆盖无租户上下文场景，如 AdminSeeder）。
 */
@Mapper
interface AuthOrganizationMapper : BaseMapper<OrganizationRecord> {

    /** 租户下全部组织（管理端组织树数据源）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_organization WHERE tenant_id = #{tenantId} ORDER BY sort, id")
    fun selectByTenant(@Param("tenantId") tenantId: Long): List<OrganizationRecord>

    /** 按租户 + 组织 ID 查找（编辑/删除前归属校验）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_organization WHERE tenant_id = #{tenantId} AND id = #{orgId} LIMIT 1")
    fun selectByIdAndTenant(@Param("tenantId") tenantId: Long, @Param("orgId") orgId: Long): OrganizationRecord?

    /** 按租户 + 组织 ID 删除（显式租户号，避免依赖租户拦截器上下文）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_organization WHERE tenant_id = #{tenantId} AND id = #{orgId}")
    fun deleteByIdAndTenant(@Param("tenantId") tenantId: Long, @Param("orgId") orgId: Long)
}
