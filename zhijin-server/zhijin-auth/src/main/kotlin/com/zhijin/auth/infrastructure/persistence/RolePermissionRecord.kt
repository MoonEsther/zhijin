package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 角色-权限关联记录（对应 sys_role_permission 表）。
 * 纯关联表，无领域实体，仅供仓储读写使用。
 */
@TableName("sys_role_permission")
data class RolePermissionRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var roleId: Long? = null,
    var permId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
)
