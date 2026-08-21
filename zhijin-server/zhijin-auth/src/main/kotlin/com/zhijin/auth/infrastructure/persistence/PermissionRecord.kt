package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.auth.domain.permission.Permission
import java.time.LocalDateTime

/**
 * 权限点持久化记录（对应 sys_permission 表，平台级字典，无 tenant_id）。
 * 该表在租户拦截器 IGNORE_TABLES 中，查询不会被强制拼 tenant_id。
 */
@TableName("sys_permission")
data class PermissionRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var permCode: String = "",
    var permName: String = "",
    var parentId: Long = 0,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体。 */
    fun toDomain(): Permission = Permission(
        id = id,
        permCode = permCode,
        permName = permName,
        parentId = parentId,
    )

    companion object {
        /** 领域实体 → 持久化记录（种子器按权限点常量播种用）。 */
        fun from(permission: Permission): PermissionRecord = PermissionRecord(
            id = permission.id,
            permCode = permission.permCode,
            permName = permission.permName,
            parentId = permission.parentId,
        )
    }
}
