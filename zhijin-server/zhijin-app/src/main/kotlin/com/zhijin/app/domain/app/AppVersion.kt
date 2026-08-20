package com.zhijin.app.domain.app

import java.time.LocalDateTime

/** 版本快照（不可变，发布时生成）。 */
data class AppVersion(
    val id: Long?,
    val tenantId: Long,
    val appId: Long,
    val versionNo: Int,
    val workflowDsl: String?,
    val modelSnapshot: String?,
    val status: Int = 1,           // 可改用 AppStatus/专门枚举替代裸 Int（可选优化）
    val publishBy: Long?,          // 对应 app_version.publish_by 列
    val publishTime: LocalDateTime?,
)
