package com.zhijin.auth.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.auth.domain.user.User
import java.time.LocalDateTime

/**
 * 用户持久化记录（对应 sys_user 表，贫血模型，仅 infrastructure 使用）。
 *
 * 由原 entity/SysUser 迁移而来：id 保持 var —— MyBatis-Plus IdType.AUTO 靠反射
 * setter 回填自增主键，val 无 setter 会回填失败。create_time/update_time 为
 * 基础设施审计字段，不进入领域实体 [User]。
 */
@TableName("sys_user")
data class SysUserRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var username: String = "",
    var password: String = "",
    var nickname: String = "",
    var status: Int = 1,
    // V6 组织模型：用户归属组织（可空，用户可不属于任何组织）
    var orgId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    /** 持久化记录 → 领域实体（tenant_id 在 sys_user 表必填，缺失属脏数据，抛异常暴露）。 */
    fun toDomain(): User = User(
        id = id,
        tenantId = tenantId ?: throw IllegalStateException("用户租户缺失: id=$id"),
        username = username,
        password = password,
        nickname = nickname,
        status = status,
        orgId = orgId,
    )

    companion object {
        /** 领域实体 → 持久化记录。 */
        fun from(user: User): SysUserRecord = SysUserRecord(
            id = user.id,
            tenantId = user.tenantId,
            username = user.username,
            password = user.password,
            nickname = user.nickname,
            status = user.status,
            orgId = user.orgId,
        )
    }
}
