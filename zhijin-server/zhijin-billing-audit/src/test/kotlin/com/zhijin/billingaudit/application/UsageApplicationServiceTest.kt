package com.zhijin.billingaudit.application

import com.zhijin.billingaudit.domain.usage.UsageRecord
import com.zhijin.billingaudit.domain.usage.UsageRepository
import com.zhijin.billingaudit.domain.usage.UsageSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * UsageApplicationService 单元测试：模拟 UsageRepository，
 * 验证 record 委托仓储保存、summarize 透传汇总结果。
 */
class UsageApplicationServiceTest {

    private val repository = mock(UsageRepository::class.java)
    private val service = UsageApplicationService(repository)

    @Test
    fun `record委托仓储保存`() {
        val record = UsageRecord(tenantId = 1L, appId = 1L, model = "default")
        `when`(repository.save(record)).thenReturn(record)

        service.record(record)

        verify(repository).save(record)
    }

    @Test
    fun `summarize返回汇总列表`() {
        `when`(repository.summarizeByApp(1L, null, null))
            .thenReturn(listOf(UsageSummary(appId = 1L, totalCalls = 2, totalTokens = 10)))

        val result = service.summarize(1L, null, null)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].appId)
        assertEquals(2, result[0].totalCalls)
        assertEquals(10, result[0].totalTokens)
    }
}
