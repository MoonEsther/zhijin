package com.zhijin.auth.interfaces

import com.zhijin.auth.application.OrgApplicationService
import com.zhijin.auth.domain.organization.Organization
import com.zhijin.auth.interfaces.dto.AssignRolesRequest
import com.zhijin.auth.interfaces.dto.OrgNode
import com.zhijin.auth.interfaces.dto.OrgRequest
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
 * 组织管理端点（/api/orgs，JWT 保护，@PreAuthorize role:manage）：组织树 + 组织 CRUD + 组织级角色分配。
 * 组织级角色授权让"组织绑定的角色"对组织内用户生效（权限继承在 RbacApplicationService.getPerms 合并）。
 */
@RestController
@RequestMapping("/api/orgs")
class OrgController(private val orgService: OrgApplicationService) {

    // 租户号统一取自上下文过滤器，防止跨租户越权
    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    /** 组织树。 */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('role:manage')")
    fun tree(): Result<List<OrgNode>> =
        Result.success(orgService.tree(tenantId))

    /** 新建组织。 */
    @PostMapping
    @PreAuthorize("hasAuthority('role:manage')")
    fun create(@RequestBody req: OrgRequest): Result<OrgNode> =
        Result.success(orgService.create(tenantId, req.parentId, req.orgName, req.sort).toNode())

    /** 更新组织（可移动父组织 / 启停用）。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    fun update(@PathVariable id: Long, @RequestBody req: OrgRequest): Result<OrgNode> =
        Result.success(orgService.update(tenantId, id, req.parentId, req.orgName, req.sort, req.status).toNode())

    /** 删除组织。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    fun delete(@PathVariable id: Long): Result<Unit> {
        orgService.delete(tenantId, id)
        return Result.success()
    }

    /** 重设组织绑定的角色（组织级角色授权）。 */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    fun assignRoles(@PathVariable id: Long, @RequestBody req: AssignRolesRequest): Result<Unit> {
        orgService.assignRoles(tenantId, id, req.roleIds)
        return Result.success()
    }

    // ---- 领域实体 → 响应 DTO 的薄映射（新建/更新返回单节点，children 为空） ----

    private fun Organization.toNode(): OrgNode = OrgNode(
        id = id ?: throw IllegalStateException("组织 ID 缺失: orgName=$orgName"),
        parentId = parentId,
        orgName = orgName,
        sort = sort,
        status = status,
        children = emptyList(),
    )
}
