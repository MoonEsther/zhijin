package com.zhijin.billingaudit.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zhijin.billingaudit.domain.usage.UsageRecord
import java.time.LocalDateTime

/** 用量记录持久化记录（贫血，仅 infrastructure 用）。 */
@TableName("usage_record")
data class UsageRecordRecord(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,          // var：MyBatis-Plus 自增主键靠 setter 回填
    var tenantId: Long? = null,
    var appId: Long? = null,
    var sessionId: Long? = null,
    var model: String = "",
    var promptTokens: Int = 0,
    var completionTokens: Int = 0,
    var totalTokens: Int = 0,
    var latencyMs: Int = 0,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
) {
    fun toDomain(): UsageRecord = UsageRecord(
        id = id, tenantId = tenantId!!, appId = appId!!, sessionId = sessionId,
        model = model, promptTokens = promptTokens, completionTokens = completionTokens,
        totalTokens = totalTokens, latencyMs = latencyMs, createTime = createTime,
    )

    companion object {
        fun from(r: UsageRecord): UsageRecordRecord = UsageRecordRecord(
            id = r.id, tenantId = r.tenantId, appId = r.appId, sessionId = r.sessionId,
            model = r.model, promptTokens = r.promptTokens, completionTokens = r.completionTokens,
            totalTokens = r.totalTokens, latencyMs = r.latencyMs, createTime = r.createTime,
        )
    }
}
