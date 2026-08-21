package com.zhijin.auth.application

import com.zhijin.auth.domain.organization.Organization
import com.zhijin.auth.domain.organization.OrganizationRepository
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.auth.domain.user.UserRepository
import com.zhijin.auth.interfaces.dto.OrgNode
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 组织应用服务（V6 组织模型）：组织树构建、组织 CRUD、组织级角色分配。
 *
 * 组织继承语义：给组织绑定角色（sys_org_role）后，该组织下用户的权限 = 用户角色 ∪ 组织角色，
 * 合并逻辑在 [RbacApplicationService.getPerms]，本服务只负责组织本身及其角色关联的维护。
 *
 * 写操作统一 @Transactional：create/update 涉及"父组织校验 + 成环校验 + 落库"多步，
 * delete 涉及"子组织/用户归属防护 + 删除"，任一失败应整体回滚，避免部分写造成悬挂引用。
 */
@Service
class OrgApplicationService(
    private val orgRepository: OrganizationRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {

    /** 组织树：从扁平列表按 parentId 组装为树，供管理端组织管理页展示。 */
    fun tree(tenantId: Long): List<OrgNode> {
        val orgs = orgRepository.listByTenant(tenantId)
        val byParent = orgs.groupBy { it.parentId }
        fun build(parentId: Long): List<OrgNode> =
            byParent[parentId].orEmpty().map { org ->
                OrgNode(
                    id = org.id!!,
                    parentId = org.parentId,
                    orgName = org.orgName,
                    sort = org.sort,
                    status = org.status,
                    children = build(org.id!!),
                )
            }
        // 根组织（parent_id=0）作为森林入口
        return build(0L)
    }

    /** 新建组织：parentId 非 0 时校验父组织存在，避免悬挂节点。 */
    @Transactional
    fun create(tenantId: Long, parentId: Long, orgName: String, sort: Int): Organization {
        if (orgName.isBlank()) throw BizException(ResultCode.BAD_REQUEST, "组织名称不能为空")
        if (parentId != 0L && orgRepository.findById(tenantId, parentId) == null) {
            throw BizException(ResultCode.BAD_REQUEST, "父组织不存在: id=$parentId")
        }
        return orgRepository.save(
            tenantId, Organization(id = null, tenantId = tenantId, parentId = parentId, orgName = orgName, sort = sort, status = 1)
        )
    }

    /** 更新组织：校验存在 + 父组织存在（含成环防护）+ 可更新状态启停用。 */
    @Transactional
    fun update(tenantId: Long, orgId: Long, parentId: Long, orgName: String, sort: Int, status: Int): Organization {
        if (orgName.isBlank()) throw BizException(ResultCode.BAD_REQUEST, "组织名称不能为空")
        val existing = orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        if (parentId != 0L) {
            // 防护 1：父组织不能是自身（直接拒绝，给出明确错误）
            if (parentId == orgId) throw BizException(ResultCode.BAD_REQUEST, "父组织不能是自身: id=$orgId")
            orgRepository.findById(tenantId, parentId)
                ?: throw BizException(ResultCode.BAD_REQUEST, "父组织不存在: id=$parentId")
            // 防护 2：新父组织不能在 org 的子树内（沿父链上溯可达 orgId 即成环），
            // 否则 tree() 递归组装会无限递归 StackOverflow
            ensureNoCycle(tenantId, orgId, parentId)
        }
        return orgRepository.save(
            tenantId, existing.copy(parentId = parentId, orgName = orgName, sort = sort, status = status)
        )
    }

    /** 删除组织：有子组织或仍有用户归属时拒绝，避免悬挂引用（子组织 parent_id / 用户 org_id 悬空）。 */
    @Transactional
    fun delete(tenantId: Long, orgId: Long) {
        orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        // 防护 1：存在子组织则拒绝（否则子组织 parent_id 悬空）
        if (orgRepository.listByTenant(tenantId).any { it.parentId == orgId }) {
            throw BizException(ResultCode.BAD_REQUEST, "存在子组织，不能删除: id=$orgId")
        }
        // 防护 2：仍有用户归属则拒绝（否则用户 org_id 悬空）
        if (userRepository.existsByOrgId(tenantId, orgId)) {
            throw BizException(ResultCode.BAD_REQUEST, "仍有用户归属该组织，不能删除: id=$orgId")
        }
        orgRepository.deleteById(tenantId, orgId)
    }

    /** 重设组织绑定的角色（组织级角色授权，先删后插）。 */
    @Transactional
    fun assignRoles(tenantId: Long, orgId: Long, roleIds: List<Long>) {
        orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        roleRepository.assignOrgRoles(tenantId, orgId, roleIds)
    }

    /**
     * 成环校验：判断把 orgId 的父组织改为 parentId 是否成环。
     * 做法：先取租户全部组织构建"子→父"映射，从 parentId 沿父链上溯；
     * 若途中到达 orgId，说明 parentId 是 orgId 的后代（新父组织在自身子树内），会形成环。
     */
    private fun ensureNoCycle(tenantId: Long, orgId: Long, parentId: Long) {
        val parentMap = orgRepository.listByTenant(tenantId).associate { it.id!! to it.parentId }
        var cur = parentId
        while (cur != 0L) {
            if (cur == orgId) {
                throw BizException(ResultCode.BAD_REQUEST, "父组织不能是自身或其子组织（会形成环）: id=$orgId")
            }
            cur = parentMap[cur] ?: 0L
        }
    }
}
