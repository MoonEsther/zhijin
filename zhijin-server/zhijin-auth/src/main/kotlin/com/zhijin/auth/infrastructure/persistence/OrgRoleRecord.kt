package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 组织-角色关联记录（对应 sys_org_role 表，V6 组织级角色授权）。
 * 纯关联表，无领域实体，仅供仓储读写使用。
 */
@TableName("sys_org_role")
data class OrgRoleRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var orgId: Long? = null,
    var roleId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
)
