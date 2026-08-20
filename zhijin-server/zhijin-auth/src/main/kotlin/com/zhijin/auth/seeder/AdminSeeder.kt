package com.zhijin.auth.seeder

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.auth.entity.SysTenant
import com.zhijin.auth.infrastructure.persistence.SysUserRecord
import com.zhijin.auth.repository.SysTenantMapper
import com.zhijin.auth.repository.SysUserMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/** 启动时幂等种子：默认租户 + 管理员账号（admin / 由 env ADMIN_INIT_PASSWORD 指定，默认 admin123）。 */
@Component
class AdminSeeder(
    private val tenantMapper: SysTenantMapper,
    private val userMapper: SysUserMapper,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

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
        if (admin == null) {
            val initPwd = System.getenv("ADMIN_INIT_PASSWORD") ?: "admin123"
            val u = SysUserRecord(
                tenantId = tenantId, username = "admin",
                password = passwordEncoder.encode(initPwd)!!, nickname = "管理员", status = 1,
            )
            userMapper.insert(u)
            log.info("[seed] 已创建默认租户({})与管理员 admin", tenantId)
        } else {
            log.info("[seed] 管理员 admin 已存在 tenantId={}", tenantId)
        }
    }
}
