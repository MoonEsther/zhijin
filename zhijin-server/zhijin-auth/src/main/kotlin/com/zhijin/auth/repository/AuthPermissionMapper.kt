package com.zhijin.auth.repository

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.infrastructure.persistence.PermissionRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 权限点 Mapper（持久化层）：sys_permission 为平台级字典（无 tenant_id，租户拦截器 IGNORE_TABLES），
 * 查询不受租户上下文影响，无需 @InterceptorIgnore。类名 AuthPermissionMapper 与框架区分。
 */
@Mapper
interface AuthPermissionMapper : BaseMapper<PermissionRecord> {

    /** 按权限点编码精确查找（角色保存时把 perms 编码解析为权限点 ID）。 */
    @Select("SELECT * FROM sys_permission WHERE perm_code = #{permCode} LIMIT 1")
    fun selectByCode(@Param("permCode") permCode: String): PermissionRecord?
}
