package com.zhijin.auth.repository

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.SysUserRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 用户 Mapper（持久化层）：只处理 [SysUserRecord]，返回记录后由
 * UserRepositoryImpl 转领域实体。保持 @Mapper 与包路径不变（@MapperScan 硬编码扫描）。
 */
@Mapper
interface SysUserMapper : BaseMapper<SysUserRecord> {

    /**
     * 登录查找必须在租户确定之前进行（租户来自用户本身），故绕过租户拦截器。
     * 假设管理端用户名全局唯一；若未来跨租户用户名重复，需扩展登录带租户选择。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    fun findByUsername(username: String): SysUserRecord?

    /**
     * 种子器专用：按租户 + 用户名查询管理员。
     * 启动时无租户上下文（租户拦截器会把 tenant_id 拼成 0），必须绕过租户拦截器，否则重启时会因查不到而重复插入。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} LIMIT 1")
    fun findByTenantIdAndUsername(@Param("tenantId") tenantId: Long, @Param("username") username: String): SysUserRecord?

    /**
     * 按租户 + 用户 ID 查找：显式传租户号并绕过租户拦截器。
     * 调用场景覆盖无租户上下文路径（token 签发时 getPerms 取用户 orgId），
     * 若依赖租户拦截器自动拼 tenant_id=0 将查不到用户。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND id = #{userId} LIMIT 1")
    fun findById(@Param("tenantId") tenantId: Long, @Param("userId") userId: Long): SysUserRecord?

    /** 租户下全部用户（管理端用户列表）：显式传租户号，避免依赖租户拦截器上下文。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} ORDER BY id")
    fun selectByTenant(@Param("tenantId") tenantId: Long): List<SysUserRecord>

    /** 更新用户归属组织（V6 组织模型）：种子器为 admin 挂根组织时使用，启动无租户上下文须绕过拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_user SET org_id = #{orgId}, update_time = now() WHERE tenant_id = #{tenantId} AND id = #{userId}")
    fun updateOrgId(@Param("tenantId") tenantId: Long, @Param("userId") userId: Long, @Param("orgId") orgId: Long)
}
