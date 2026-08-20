package com.zhijin.auth.infrastructure.security

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
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): ZhijinUserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("用户不存在: $username")
        return UserDetailsAdapter.toUserDetails(user)
    }
}
