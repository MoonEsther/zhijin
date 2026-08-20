package com.zhijin.app.domain.app

import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import java.time.LocalDateTime

/** 应用（智能体）领域实体，富血模型：状态/发布规则即领域行为。 */
data class App(
    val id: Long?,
    val tenantId: Long,
    val appKey: String,
    val name: String,
    val description: String,
    val iconUri: String,
    val status: AppStatus,
    val createBy: Long?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
) {
    /** 校验归属（对外抛业务异常，非 check()）。 */
    fun ensureOwnedBy(tenantId: Long) {
        if (this.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权操作")
    }

    /** 发布规则：DRAFT 或 PUBLISHED 均可发布（每次生成新版本快照）。 */
    fun ensurePublishable() {
        if (status !in setOf(AppStatus.DRAFT, AppStatus.PUBLISHED)) {
            throw BizException(ResultCode.BAD_REQUEST, "当前状态不可发布: $status")
        }
    }

    fun published(): App = copy(status = AppStatus.PUBLISHED)
}

enum class AppStatus { DRAFT, PUBLISHED, OFFLINE }
