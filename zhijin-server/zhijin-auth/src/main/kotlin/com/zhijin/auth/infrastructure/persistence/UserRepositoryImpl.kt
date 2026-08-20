package com.zhijin.auth.infrastructure.persistence

import com.zhijin.auth.domain.user.User
import com.zhijin.auth.domain.user.UserRepository
import com.zhijin.auth.repository.SysUserMapper
import org.springframework.stereotype.Repository

/**
 * 用户仓储实现：基于 MyBatis-Plus Mapper，持久化记录与领域实体互转。
 * Mapper 保留在 com.zhijin.auth.repository（@MapperScan 硬编码该路径，不移动）。
 */
@Repository
class UserRepositoryImpl(private val userMapper: SysUserMapper) : UserRepository {

    /**
     * 关键：必须委托给带 @InterceptorIgnore(tenantLine="true") 的 Mapper 方法，
     * 登录发生在租户确定之前（租户来自用户本身），若被租户拦截器拼上 tenant_id=0
     * 将永远查不到用户 —— 这是表单登录 / OAuth2 授权码流程的命脉，不得回归。
     */
    override fun findByUsername(username: String): User? =
        userMapper.findByUsername(username)?.toDomain()
}
