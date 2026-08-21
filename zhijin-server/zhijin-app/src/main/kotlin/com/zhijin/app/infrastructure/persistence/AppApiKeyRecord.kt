package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.app.domain.apikey.AppApiKey
import java.time.LocalDateTime

/** 应用 API Key 持久化记录（贫血，仅 infrastructure 用；keyHash 为 SHA-256 哈希）。 */
@TableName("app_api_key")
data class AppApiKeyRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appId: Long? = null,
    var keyHash: String = "",
    var name: String = "",
    var status: Int = 1,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    var expireTime: LocalDateTime? = null,
) {
    fun toDomain(): AppApiKey = AppApiKey(
        id = id, tenantId = tenantId!!, appId = appId!!, keyHash = keyHash,
        name = name, status = status, createTime = createTime,
    )

    companion object {
        fun from(key: AppApiKey): AppApiKeyRecord = AppApiKeyRecord(
            id = key.id, tenantId = key.tenantId, appId = key.appId, keyHash = key.keyHash,
            name = key.name, status = key.status, createTime = key.createTime,
        )
    }
}
