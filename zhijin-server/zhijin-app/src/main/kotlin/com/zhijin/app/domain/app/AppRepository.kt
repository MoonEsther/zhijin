package com.zhijin.app.domain.app

/** 应用仓储接口（依赖倒置：实现放 infrastructure）。 */
interface AppRepository {
    fun findById(tenantId: Long, id: Long): App?

    /** 查询租户下全部应用（按 tenant_id 过滤，用于列表页）。 */
    fun findAll(tenantId: Long): List<App>

    fun save(app: App): App
    fun delete(tenantId: Long, id: Long)
}
