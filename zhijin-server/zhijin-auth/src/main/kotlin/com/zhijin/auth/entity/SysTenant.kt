package com.zhijin.auth.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 租户实体（对应 sys_tenant 表，平台级，无 tenant_id）。 */
@TableName("sys_tenant")
data class SysTenant(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantCode: String = "",
    var tenantName: String = "",
    var status: Int = 1,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
)
