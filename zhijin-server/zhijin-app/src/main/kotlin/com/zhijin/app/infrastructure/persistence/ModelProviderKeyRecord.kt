package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.app.domain.modelconfig.ModelProviderKey
import java.time.LocalDateTime

/** 模型供应商 Key 持久化记录（贫血，仅 infrastructure 用；encryptedKey 已加密落库）。 */
@TableName("model_provider_key")
data class ModelProviderKeyRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var provider: String = "",
    var name: String = "",
    var encryptedKey: String = "",
    var status: Int = 1,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    fun toDomain(): ModelProviderKey = ModelProviderKey(
        id = id, tenantId = tenantId!!, provider = provider, name = name,
        encryptedKey = encryptedKey, status = status,
    )

    companion object {
        fun from(key: ModelProviderKey): ModelProviderKeyRecord = ModelProviderKeyRecord(
            id = key.id, tenantId = key.tenantId, provider = key.provider, name = key.name,
            encryptedKey = key.encryptedKey, status = key.status,
        )
    }
}
