package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TenantContextHolderTest {

    /** 每个用例结束后清理 ThreadLocal，避免线程复用导致租户上下文泄漏。 */
    @AfterEach
    fun `清理租户上下文`() {
        TenantContext.clear()
    }

    @Test
    fun `设置与读取租户`() {
        TenantContextHolder.setTenantId(100L)
        assertEquals(100L, TenantContextHolder.getRequiredTenantId())
    }

    @Test
    fun `未设置时获取必填租户抛异常`() {
        TenantContext.clear()
        assertThrows(BizException::class.java) { TenantContextHolder.getRequiredTenantId() }
    }
}
