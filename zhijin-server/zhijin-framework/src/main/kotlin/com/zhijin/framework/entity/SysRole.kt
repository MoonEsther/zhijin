package com.zhijin.framework.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 角色实体：tenant_id 由 MyBatis-Plus 自动填充（FieldFill.INSERT）。 */
@TableName("sys_role")
data class SysRole(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var roleCode: String = "",
    var roleName: String = "",
    @TableField(fill = FieldFill.INSERT)
    var tenantId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
)
