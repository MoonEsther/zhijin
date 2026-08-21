package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.auth.domain.organization.Organization
import java.time.LocalDateTime

/**
 * 组织持久化记录（对应 sys_organization 表，贫血模型，仅 infrastructure 使用）。
 * 组织树通过 parent_id 表达；status 语义 1=启用。
 */
@TableName("sys_organization")
data class OrganizationRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var parentId: Long = 0,
    var orgName: String = "",
    var sort: Int = 0,
    var status: Int = 1,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体（tenant_id 必填，缺失属脏数据）。 */
    fun toDomain(): Organization = Organization(
        id = id,
        tenantId = tenantId ?: throw IllegalStateException("组织租户缺失: id=$id"),
        parentId = parentId,
        orgName = orgName,
        sort = sort,
        status = status,
    )

    companion object {
        /** 领域实体 → 持久化记录。 */
        fun from(org: Organization): OrganizationRecord = OrganizationRecord(
            id = org.id,
            tenantId = org.tenantId,
            parentId = org.parentId,
            orgName = org.orgName,
            sort = org.sort,
            status = org.status,
        )
    }
}
