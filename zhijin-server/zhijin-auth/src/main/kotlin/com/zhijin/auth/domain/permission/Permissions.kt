package com.zhijin.auth.domain.permission

/**
 * 权限点常量（方案 C RBAC）：集中声明平台全部权限点，供 @PreAuthorize 注解字面量与
 * 权限字典种子（sys_permission 平台级表）统一引用，避免散落的魔法字符串漂移。
 *
 * 命名约定：{资源}:{动作}，例如 app:create 表示"创建应用"。
 * 校验注解统一写作 hasAuthority('app:create')，依赖 JwtAuthenticationConverter
 * 从 JWT perms claim 无前缀解析（见 SecurityConfig）。
 */
object Permissions {
    const val APP_VIEW = "app:view"
    const val APP_CREATE = "app:create"
    const val APP_UPDATE = "app:update"
    const val APP_DELETE = "app:delete"
    const val APP_PUBLISH = "app:publish"
    const val APIKEY_MANAGE = "apikey:manage"
    const val USAGE_VIEW = "usage:view"
    const val AUDIT_VIEW = "audit:view"
    const val USER_MANAGE = "user:manage"
    const val ROLE_MANAGE = "role:manage"
}
