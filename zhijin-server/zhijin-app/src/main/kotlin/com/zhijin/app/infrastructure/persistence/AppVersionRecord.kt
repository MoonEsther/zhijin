package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.app.domain.app.AppVersion
import java.time.LocalDateTime

/** 版本快照持久化记录（贫血，仅 infrastructure 用；由原 AppVersion 实体改造，保留 publish_by 列）。 */
@TableName("app_version")
data class AppVersionRecord(
    // 注意：id 必须 var —— MyBatis-Plus IdType.AUTO 靠反射 setter 回填自增主键，val 无 setter 回填失败
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appId: Long? = null,
    var versionNo: Int = 1,
    var workflowDsl: String? = null,
    var modelSnapshot: String? = null,
    var status: Int = 1,
    var publishBy: Long? = null,
    var publishTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
) {
    fun toDomain(): AppVersion = AppVersion(
        id = id, tenantId = tenantId!!, appId = appId!!, versionNo = versionNo,
        workflowDsl = workflowDsl, modelSnapshot = modelSnapshot,
        status = status, publishBy = publishBy, publishTime = publishTime,
    )

    companion object {
        fun from(version: AppVersion): AppVersionRecord = AppVersionRecord(
            id = version.id, tenantId = version.tenantId, appId = version.appId, versionNo = version.versionNo,
            workflowDsl = version.workflowDsl, modelSnapshot = version.modelSnapshot,
            status = version.status, publishBy = version.publishBy, publishTime = version.publishTime,
        )
    }
}
