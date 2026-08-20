package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppStatus
import java.time.LocalDateTime

/** 持久化记录（贫血，仅 infrastructure 用；由原 App 实体改造，保留 create_by 列）。 */
@TableName("app")
data class AppRecord(
    // 注意：id 必须 var —— MyBatis-Plus IdType.AUTO 靠反射 setter 回填自增主键，val 无 setter 回填失败
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appKey: String = "",
    var name: String = "",
    var description: String = "",
    var iconUri: String = "",
    var status: Int = 0,
    var createBy: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    fun toDomain(): App = App(
        id = id, tenantId = tenantId!!, appKey = appKey, name = name,
        description = description, iconUri = iconUri,
        // 显式映射：未知状态抛异常，避免脏数据静默回退草稿
        status = when (status) { 0 -> AppStatus.DRAFT; 1 -> AppStatus.PUBLISHED; 2 -> AppStatus.OFFLINE;
                                  else -> throw IllegalStateException("未知应用状态: $status") },
        createBy = createBy, createTime = createTime, updateTime = updateTime,
    )

    companion object {
        fun from(app: App): AppRecord = AppRecord(
            id = app.id, tenantId = app.tenantId, appKey = app.appKey, name = app.name,
            description = app.description, iconUri = app.iconUri,
            status = app.status.ordinal, createBy = app.createBy,
            createTime = app.createTime, updateTime = app.updateTime,
        )
    }
}
