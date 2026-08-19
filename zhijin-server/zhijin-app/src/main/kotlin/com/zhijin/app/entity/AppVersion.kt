package com.zhijin.app.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 应用版本快照（对应 app_version 表）。 */
@TableName("app_version")
data class AppVersion(
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
)
