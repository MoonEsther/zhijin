package com.zhijin.auth.entity

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 携带租户 ID 的自定义 UserDetails，供 token customizer 写入 tenant_id claim。
 *
 * 实现说明：username/password 属性若保留默认 getter 名，会与显式覆写的 getUsername()/getPassword()
 * 产生 JVM 平台声明冲突（data class 自动生成同名 getter），故用 @get:JvmName 把属性访问器改名，
 * 由下方 override 方法承担 UserDetails 接口实现。id/tenantId 供 token customizer 直接读取。
 *
 * perms 为登录时解析的用户权限点（用户角色 ∪ 组织角色），主要供 token customizer 兜底读取；
 * 签发 JWT 时 tokenCustomizer 会经 RbacApplicationService 重新查询最新权限点写入 perms claim，
 * 以保证角色变更在令牌签发时刻即时生效（登录会话与签发令牌之间可能有时间差）。
 */
data class ZhijinUserDetails(
    val id: Long,
    val tenantId: Long,
    @get:JvmName("getUsernameValue") val username: String,
    @get:JvmName("getPasswordValue") val password: String,
    val authorities: List<GrantedAuthority>,
    val perms: List<String> = emptyList(),
) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = authorities.toMutableList()
    override fun getPassword(): String = password
    override fun getUsername(): String = username
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
