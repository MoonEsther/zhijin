package com.zhijin.auth.infrastructure.security

import com.zhijin.auth.application.RbacApplicationService
import com.zhijin.auth.domain.user.UserRepository
import com.zhijin.auth.entity.ZhijinUserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * 从 sys_user 表加载用户并包装为携带租户 ID 的 UserDetails（Spring Security 表单登录链3用）。
 *
 * 由原 service/UserDetailsServiceImpl 迁移而来：改依赖 domain 仓储 [UserRepository]
 * （而非直接依赖 SysUserMapper），加载领域 User 后经 [UserDetailsAdapter] 适配为
 * Spring Security UserDetails；密码比对由 DaoAuthenticationProvider 在框架内完成。
 * 登录时经 [RbacApplicationService.getPerms] 预解析权限点（用户角色 ∪ 组织角色），
 * 存入 ZhijinUserDetails.perms —— 令牌签发侧 tokenCustomizer 会重新查询最新权限点，两者职责分离。
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
    private val rbacService: RbacApplicationService,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): ZhijinUserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("用户不存在: $username")
        // 登录时解析权限点：底层仓储显式传租户号并绕过租户拦截器（登录无租户上下文）
        val userId = user.id ?: throw IllegalStateException("用户 ID 缺失: username=${user.username}")
        val perms = rbacService.getPerms(user.tenantId, userId)
        return UserDetailsAdapter.toUserDetails(user, perms)
    }
}
