package com.zhijin.auth.domain.user

/**
 * 用户仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 *
 * S2 复用：由原 auth/repository/SysUserRepository 仓储雏形重命名/迁移而来，
 * 避免 domain 与基础设施各维护一套仓储接口。返回领域实体 [User] 而非持久化记录。
 */
interface UserRepository {

    /** 按用户名查找（登录，租户来自用户本身，绕过租户拦截器）。 */
    fun findByUsername(username: String): User?

    /** 按租户 + 用户 ID 查找（权限合并取 orgId、管理端用户详情）。 */
    fun findById(tenantId: Long, userId: Long): User?

    /** 租户下全部用户（管理端用户列表）。 */
    fun listByTenant(tenantId: Long): List<User>
}
