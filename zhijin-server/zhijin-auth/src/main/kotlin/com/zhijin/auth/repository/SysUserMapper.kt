package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.entity.SysUser
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface SysUserMapper : BaseMapper<SysUser>, SysUserRepository {

    /**
     * 登录查找必须在租户确定之前进行（租户来自用户本身），故绕过租户拦截器。
     * 假设管理端用户名全局唯一；若未来跨租户用户名重复，需扩展登录带租户选择。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    override fun findByUsername(username: String): SysUser?

    /**
     * 种子器专用：按租户 + 用户名查询管理员。
     * 启动时无租户上下文（租户拦截器会把 tenant_id 拼成 0），必须绕过租户拦截器，否则重启时会因查不到而重复插入。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} LIMIT 1")
    fun findByTenantIdAndUsername(@Param("tenantId") tenantId: Long, @Param("username") username: String): SysUser?
}
