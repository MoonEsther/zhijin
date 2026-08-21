package com.zhijin.auth.infrastructure.persistence

import com.zhijin.auth.domain.organization.Organization
import com.zhijin.auth.domain.organization.OrganizationRepository
import com.zhijin.auth.repository.AuthOrganizationMapper
import org.springframework.stereotype.Repository

/**
 * 组织仓储实现：sys_organization 带 tenant_id，写入显式传租户号（策略同角色仓储）。
 */
@Repository
class OrganizationRepositoryImpl(
    private val orgMapper: AuthOrganizationMapper,
) : OrganizationRepository {

    override fun listByTenant(tenantId: Long): List<Organization> =
        orgMapper.selectByTenant(tenantId).map { it.toDomain() }

    override fun findById(tenantId: Long, orgId: Long): Organization? =
        orgMapper.selectByIdAndTenant(tenantId, orgId)?.toDomain()

    override fun save(tenantId: Long, org: Organization): Organization {
        val record = OrganizationRecord.from(org).apply { this.tenantId = tenantId }
        if (record.id == null) {
            orgMapper.insert(record)
        } else {
            orgMapper.updateById(record)
        }
        val orgId = record.id ?: throw IllegalStateException("组织 ID 回填失败: orgName=${org.orgName}")
        return Organization(
            id = orgId, tenantId = tenantId, parentId = record.parentId,
            orgName = record.orgName, sort = record.sort, status = record.status,
        )
    }

    override fun deleteById(tenantId: Long, orgId: Long) {
        orgMapper.deleteByIdAndTenant(tenantId, orgId)
    }
}
