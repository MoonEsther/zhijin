package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.RolePermissionRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 角色-权限关联 Mapper（sys_role_permission 表）。
 * 显式租户号绕过租户拦截器，与角色 Mapper 同一策略（适配无租户上下文场景）。
 */
@Mapper
interface AuthRolePermissionMapper : BaseMapper<RolePermissionRecord> {

    /** 角色拥有的权限点编码列表（JOIN sys_permission 解析编码）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select(
        "SELECT DISTINCT p.perm_code FROM sys_permission p JOIN sys_role_permission rp ON rp.perm_id = p.id " +
            "WHERE rp.tenant_id = #{tenantId} AND rp.role_id = #{roleId}"
    )
    fun selectPermCodesByRoleId(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long): List<String>

    /** 删除角色全部权限关联（重设权限前先删后插）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_role_permission WHERE tenant_id = #{tenantId} AND role_id = #{roleId}")
    fun deleteByRoleId(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long)
}
