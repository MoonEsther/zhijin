package com.zhijin.app.domain.app

/** 版本快照仓储接口。 */
interface AppVersionRepository {
    fun nextVersionNo(tenantId: Long, appId: Long): Int   // 现有版本数 + 1
    fun save(version: AppVersion): AppVersion
}
