package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode

/** 租户上下文读写封装。 */
object TenantContextHolder {

    fun setTenantId(tenantId: Long?) = TenantContext.set(tenantId)

    /** 清理租户上下文：请求结束或异常时调用，避免线程复用导致租户串租。 */
    fun clear() = TenantContext.clear()

    fun getTenantId(): Long? = TenantContext.get()

    /** 取必填租户：缺失时抛业务异常（被全局异常处理转 400）。 */
    fun getRequiredTenantId(): Long =
        TenantContext.get() ?: throw BizException(ResultCode.TENANT_MISSING, "缺少租户上下文")
}
