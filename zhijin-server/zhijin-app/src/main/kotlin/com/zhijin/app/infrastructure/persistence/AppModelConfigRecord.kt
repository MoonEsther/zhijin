package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.app.domain.modelconfig.AppModelConfig
import java.time.LocalDateTime

/** 应用模型配置持久化记录（贫血，仅 infrastructure 用；每个应用一条）。 */
@TableName("app_model_config")
data class AppModelConfigRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appId: Long? = null,
    var provider: String = "",
    var modelName: String = "",
    var providerKeyId: Long? = null,
    var temperature: Double = 0.7,
    var maxTokens: Int = 2048,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    fun toDomain(): AppModelConfig = AppModelConfig(
        id = id, tenantId = tenantId!!, appId = appId!!, provider = provider,
        modelName = modelName, providerKeyId = providerKeyId,
        temperature = temperature, maxTokens = maxTokens,
    )

    companion object {
        fun from(config: AppModelConfig): AppModelConfigRecord = AppModelConfigRecord(
            id = config.id, tenantId = config.tenantId, appId = config.appId, provider = config.provider,
            modelName = config.modelName, providerKeyId = config.providerKeyId,
            temperature = config.temperature, maxTokens = config.maxTokens,
        )
    }
}
