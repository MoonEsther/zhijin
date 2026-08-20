package com.zhijin.auth.service

import com.zhijin.auth.entity.ZhijinUserDetails
import com.zhijin.auth.repository.SysUserMapper
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/** 从 sys_user 表加载用户并包装为携带租户 ID 的 UserDetails。 */
@Service
class UserDetailsServiceImpl(
    private val userMapper: SysUserMapper,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): ZhijinUserDetails {
        val user = userMapper.findByUsername(username)
            ?: throw UsernameNotFoundException("用户不存在: $username")
        return ZhijinUserDetails(
            id = user.id!!,
            tenantId = user.tenantId!!,
            username = user.username,
            password = user.password,
            authorities = listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    }
}
