package com.zhijin.auth.interfaces.dto

/** /auth/validate 返回的身份信息（由原 dto/ValidateResponse 迁移到 interfaces/dto）。 */
data class ValidateResponse(
    val username: String,
    val userId: Long?,
    val tenantId: Long?,
    val roles: List<String>,
    /** 用户权限点编码列表（用户角色 ∪ 组织角色），供前端菜单/按钮按 perms 过滤渲染。 */
    val perms: List<String>,
)
