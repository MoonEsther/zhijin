package com.zhijin.auth.domain.user

/**
 * 用户领域实体（富血模型，纯 Kotlin）。
 *
 * 设计说明：
 * - 不依赖任何 Spring / MyBatis 注解，仅承载领域状态与领域规则；
 * - 密码校验（BCrypt 比对）留在基础设施层——登录链路由 Spring Security 的
 *   DaoAuthenticationProvider 用 UserDetails 里的加密密码完成比对，领域实体不引入
 *   Spring Security 的 PasswordEncoder，保持 domain 零框架依赖；
 * - 持久化列 create_time/update_time 属基础设施关注点，不进入领域实体（见 SysUserRecord）。
 */
data class User(
    val id: Long?,
    val tenantId: Long,
    val username: String,
    val password: String,
    val nickname: String,
    val status: Int,
) {
    /**
     * 账户是否被禁用：sys_user.status 语义 1=正常/启用，其余值一律视为禁用。
     * 领域规则校验统一走 BizException（见领域约定），此处仅暴露状态判断供上层决策。
     */
    fun isDisabled(): Boolean = status != 1
}
