package com.zhijin.common.context

/** 租户上下文：基于 ThreadLocal 保存当前请求的租户 ID。 */
object TenantContext {
    private val holder = ThreadLocal<Long?>()

    fun set(tenantId: Long?) {
        holder.set(tenantId)
    }

    fun get(): Long? = holder.get()

    fun clear() {
        holder.remove()
    }
}
