package com.zhijin.auth.application

import com.zhijin.auth.domain.organization.Organization
import com.zhijin.auth.domain.organization.OrganizationRepository
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.auth.interfaces.dto.OrgNode
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service

/**
 * 组织应用服务（V6 组织模型）：组织树构建、组织 CRUD、组织级角色分配。
 *
 * 组织继承语义：给组织绑定角色（sys_org_role）后，该组织下用户的权限 = 用户角色 ∪ 组织角色，
 * 合并逻辑在 [RbacApplicationService.getPerms]，本服务只负责组织本身及其角色关联的维护。
 */
@Service
class OrgApplicationService(
    private val orgRepository: OrganizationRepository,
    private val roleRepository: RoleRepository,
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
    fun create(tenantId: Long, parentId: Long, orgName: String, sort: Int): Organization {
        if (orgName.isBlank()) throw BizException(ResultCode.BAD_REQUEST, "组织名称不能为空")
        if (parentId != 0L && orgRepository.findById(tenantId, parentId) == null) {
            throw BizException(ResultCode.BAD_REQUEST, "父组织不存在: id=$parentId")
        }
        return orgRepository.save(
            tenantId, Organization(id = null, tenantId = tenantId, parentId = parentId, orgName = orgName, sort = sort, status = 1)
        )
    }

    /** 更新组织：校验存在 + 父组织存在（含移动归属），可更新状态启停用。 */
    fun update(tenantId: Long, orgId: Long, parentId: Long, orgName: String, sort: Int, status: Int): Organization {
        if (orgName.isBlank()) throw BizException(ResultCode.BAD_REQUEST, "组织名称不能为空")
        val existing = orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        if (parentId != 0L && orgRepository.findById(tenantId, parentId) == null) {
            throw BizException(ResultCode.BAD_REQUEST, "父组织不存在: id=$parentId")
        }
        return orgRepository.save(
            tenantId, existing.copy(parentId = parentId, orgName = orgName, sort = sort, status = status)
        )
    }

    /** 删除组织（不级联删除子组织/用户归属，调用方需自行处理边界；本任务仅提供基础删除）。 */
    fun delete(tenantId: Long, orgId: Long) {
        orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        orgRepository.deleteById(tenantId, orgId)
    }

    /** 重设组织绑定的角色（组织级角色授权，先删后插）。 */
    fun assignRoles(tenantId: Long, orgId: Long, roleIds: List<Long>) {
        orgRepository.findById(tenantId, orgId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "组织不存在: id=$orgId")
        roleRepository.assignOrgRoles(tenantId, orgId, roleIds)
    }
}
