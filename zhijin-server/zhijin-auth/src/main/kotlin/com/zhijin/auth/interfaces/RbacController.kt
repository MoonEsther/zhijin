package com.zhijin.auth.interfaces

import com.zhijin.auth.application.RbacApplicationService
import com.zhijin.auth.interfaces.dto.AssignRolesRequest
import com.zhijin.auth.interfaces.dto.PermissionResponse
import com.zhijin.auth.interfaces.dto.RoleRequest
import com.zhijin.auth.interfaces.dto.RoleResponse
import com.zhijin.auth.interfaces.dto.UserWithRoles
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * RBAC 管理端点（/api/rbac，JWT 保护，租户来自 JWT claim）：权限点/角色/用户角色分配。
 *
 * 方法级鉴权（@PreAuthorize）依赖 JwtAuthenticationConverter 从 JWT perms claim
 * 无前缀解析 authority（见 SecurityConfig），权限字符串与 domain/permission/Permissions.kt 常量对齐。
 */
@RestController
@RequestMapping("/api/rbac")
class RbacController(private val rbacService: RbacApplicationService) {

    // 租户号统一取自上下文过滤器，防止跨租户越权
    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    /** 权限点列表：role:manage 或 user:manage 任一即可查看（字典供角色配置下拉使用）。 */
    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('role:manage', 'user:manage')")
    fun permissions(): Result<List<PermissionResponse>> =
        Result.success(rbacService.listPermissions().map { PermissionResponse.toResponse(it) })

    /** 角色列表（含权限点）。 */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    fun roles(): Result<List<RoleResponse>> =
        Result.success(rbacService.listRoles(tenantId).map { RoleResponse.toResponse(it) })

    /** 新建角色。 */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    fun createRole(@RequestBody req: RoleRequest): Result<RoleResponse> =
        Result.success(RoleResponse.toResponse(rbacService.createRole(tenantId, req.roleCode, req.roleName, req.perms)))

    /** 更新角色（含权限点重设）。 */
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    fun updateRole(@PathVariable id: Long, @RequestBody req: RoleRequest): Result<RoleResponse> =
        Result.success(RoleResponse.toResponse(rbacService.updateRole(tenantId, id, req.roleCode, req.roleName, req.perms)))

    /** 删除角色（级联清理关联）。 */
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    fun deleteRole(@PathVariable id: Long): Result<Unit> {
        rbacService.deleteRole(tenantId, id)
        return Result.success()
    }

    /** 用户列表（含已绑角色 ID）。 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    fun users(): Result<List<UserWithRoles>> =
        Result.success(rbacService.listUsers(tenantId))

    /** 重设用户绑定的角色。 */
    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    fun assignUserRoles(@PathVariable id: Long, @RequestBody req: AssignRolesRequest): Result<Unit> {
        rbacService.assignUserRoles(tenantId, id, req.roleIds)
        return Result.success()
    }
}
