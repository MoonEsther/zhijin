package com.zhijin.auth.domain.user

/**
 * 用户仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 *
 * S2 复用：由原 auth/repository/SysUserRepository 仓储雏形重命名/迁移而来，
 * 避免 domain 与基础设施各维护一套仓储接口。返回领域实体 [User] 而非持久化记录。
 */
interface UserRepository {
    fun findByUsername(username: String): User?
}
