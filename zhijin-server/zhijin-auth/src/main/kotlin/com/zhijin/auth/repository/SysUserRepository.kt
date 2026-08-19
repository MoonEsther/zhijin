package com.zhijin.auth.repository

import com.zhijin.auth.entity.SysUser

/** 用户仓储接口（隔离 MyBatis-Plus 细节，便于测试 mock）。 */
interface SysUserRepository {
    fun findByUsername(username: String): SysUser?
}
