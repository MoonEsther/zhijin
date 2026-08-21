package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 用户-角色关联记录（对应 sys_user_role 表）。
 * 纯关联表，无领域实体，仅供仓储读写使用。
 */
@TableName("sys_user_role")
data class UserRoleRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var userId: Long? = null,
    var roleId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
)
