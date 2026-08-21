package com.zhijin.auth.domain.organization

/**
 * 组织仓储接口（依赖倒置：接口在 domain，实现放 infrastructure.persistence）。
 * 与角色仓储一致：组织表带 tenant_id，统一显式传租户号，适配无租户上下文场景。
 */
interface OrganizationRepository {

    /** 租户下全部组织（管理端构建组织树数据源）。 */
    fun listByTenant(tenantId: Long): List<Organization>

    /** 按组织 ID 查找（编辑/删除前校验归属）。 */
    fun findById(tenantId: Long, orgId: Long): Organization?

    /** 新增或更新组织。 */
    fun save(tenantId: Long, org: Organization): Organization

    /** 删除组织（不级联清理子组织/用户归属，由调用方约束）。 */
    fun deleteById(tenantId: Long, orgId: Long)
}
