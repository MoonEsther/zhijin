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

    /**
     * 按租户 + 用户 ID 查找：同样绕过租户拦截器、显式传租户号，
     * 因为权限合并（getPerms 取 orgId）可能在 token 签发等无租户上下文场景触发。
     */
    override fun findById(tenantId: Long, userId: Long): User? =
        userMapper.findById(tenantId, userId)?.toDomain()

    /** 租户下全部用户：管理端用户列表（RbacController /api/rbac/users）。 */
    override fun listByTenant(tenantId: Long): List<User> =
        userMapper.selectByTenant(tenantId).map { it.toDomain() }

    /** 是否存在归属某组织的用户（删除组织防护用）：显式租户号绕过拦截器，与其它查询同策略。 */
    override fun existsByOrgId(tenantId: Long, orgId: Long): Boolean =
        userMapper.countByOrgId(tenantId, orgId) > 0
}
