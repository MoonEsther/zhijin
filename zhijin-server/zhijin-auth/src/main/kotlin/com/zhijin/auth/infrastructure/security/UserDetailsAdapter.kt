package com.zhijin.auth.infrastructure.security

import com.zhijin.auth.domain.user.User
import com.zhijin.auth.entity.ZhijinUserDetails
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 领域 User → Spring Security UserDetails 适配器（解耦 domain 与框架）。
 *
 * 无状态单例，直接以 object 暴露静态工厂；ZhijinUserDetails 保持原样（仍实现
 * Spring Security UserDetails），由本适配器完成字段装配。
 * [perms] 为登录时已解析的权限点（用户角色 ∪ 组织角色），默认空列表保持向后兼容。
 */
object UserDetailsAdapter {

    fun toUserDetails(user: User, perms: List<String> = emptyList()): ZhijinUserDetails = ZhijinUserDetails(
        id = user.id ?: throw IllegalStateException("用户 ID 缺失: username=${user.username}"),
        tenantId = user.tenantId,
        username = user.username,
        password = user.password,
        authorities = listOf(SimpleGrantedAuthority("ROLE_USER")),
        perms = perms,
    )
}
