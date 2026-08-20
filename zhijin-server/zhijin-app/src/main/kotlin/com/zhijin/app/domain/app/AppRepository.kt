package com.zhijin.app.domain.app

/** 应用仓储接口（依赖倒置：实现放 infrastructure）。 */
interface AppRepository {
    fun findById(tenantId: Long, id: Long): App?
    fun save(app: App): App
    fun delete(tenantId: Long, id: Long)
}
