package com.zhijin.billingaudit.application

import com.zhijin.billingaudit.domain.audit.AuditLog
import com.zhijin.billingaudit.domain.audit.AuditRepository
import com.zhijin.billingaudit.domain.audit.PageResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * AuditApplicationService 单元测试：模拟 AuditRepository，
 * 验证 record 委托仓储保存、page 透传分页结果。
 */
class AuditApplicationServiceTest {

    private val repository = mock(AuditRepository::class.java)
    private val service = AuditApplicationService(repository)

    @Test
    fun `record委托仓储保存`() {
        val log = AuditLog(tenantId = 1L, action = "APP_CREATE", targetType = "app")
        `when`(repository.save(log)).thenReturn(log)

        service.record(log)

        verify(repository).save(log)
    }

    @Test
    fun `page返回分页结果`() {
        val logs = listOf(AuditLog(tenantId = 1L, action = "APP_CREATE", targetType = "app"))
        `when`(repository.page(1L, 1, 20)).thenReturn(PageResult(items = logs, total = 1))

        val result = service.page(1L, 1, 20)

        assertEquals(1, result.total)
        assertEquals(1, result.items.size)
        assertEquals("APP_CREATE", result.items[0].action)
    }
}
