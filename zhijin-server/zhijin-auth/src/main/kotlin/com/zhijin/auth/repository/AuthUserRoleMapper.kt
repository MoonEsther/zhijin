package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.UserRoleRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 用户-角色关联 Mapper（sys_user_role 表）。
 * 显式租户号绕过租户拦截器，与角色 Mapper 同一策略（适配无租户上下文场景）。
 */
@Mapper
interface AuthUserRoleMapper : BaseMapper<UserRoleRecord> {

    /** 用户绑定的角色 ID 列表（管理端用户列表展示）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT role_id FROM sys_user_role WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    fun selectRoleIdsByUserId(@Param("tenantId") tenantId: Long, @Param("userId") userId: Long): List<Long>

    /** 删除用户全部角色绑定（重设角色前先删后插）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_role WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    fun deleteByUserId(@Param("tenantId") tenantId: Long, @Param("userId") userId: Long)

    /** 删除角色全部用户绑定（删除角色时级联清理）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_role WHERE tenant_id = #{tenantId} AND role_id = #{roleId}")
    fun deleteByRoleId(@Param("tenantId") tenantId: Long, @Param("roleId") roleId: Long)
}
