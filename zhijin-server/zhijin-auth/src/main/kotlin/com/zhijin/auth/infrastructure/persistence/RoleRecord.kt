package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.auth.domain.role.Role
import java.time.LocalDateTime

/**
 * 角色持久化记录（对应 sys_role 表，贫血模型，仅 infrastructure 使用）。
 *
 * perms 不在本记录内（角色-权限多对多存于 sys_role_permission），
 * 由 RoleRepositoryImpl 解析后装配进领域实体 [Role]。
 */
@TableName("sys_role")
data class RoleRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var roleCode: String = "",
    var roleName: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体（perms 由仓储另行解析后填充）。 */
    fun toDomain(perms: List<String>): Role = Role(
        id = id,
        tenantId = tenantId ?: throw IllegalStateException("角色租户缺失: id=$id"),
        roleCode = roleCode,
        roleName = roleName,
        perms = perms,
    )

    companion object {
        /** 领域实体 → 持久化记录（perms 不落库，仅存关联表）。 */
        fun from(role: Role): RoleRecord = RoleRecord(
            id = role.id,
            tenantId = role.tenantId,
            roleCode = role.roleCode,
            roleName = role.roleName,
        )
    }
}
