package com.zhijin.app

import com.zhijin.app.entity.AppApiKey
import com.zhijin.app.mapper.AppApiKeyMapper
import com.zhijin.app.service.AppApiKeyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * AppApiKeyService 单元测试：使用 Mockito 模拟 AppApiKeyMapper。
 * 覆盖核心安全要求：明文只返回一次、DB 只存 SHA-256 哈希、吊销后校验失败。
 */
class AppApiKeyServiceTest {

    private val mapper = mock(AppApiKeyMapper::class.java)
    private val service = AppApiKeyService(mapper)

    // 记录最近一次 insert 的实体，用于断言"DB 存的是哈希而非明文"
    private var lastInserted: AppApiKey? = null

    // 模拟 MyBatis-Plus insert 后回填自增主键 id（IdType.AUTO 的真实行为），避免 key.id!! 空指针
    private fun backfillId() = Answer<Int> { invocation: InvocationOnMock ->
        val key = invocation.getArgument<AppApiKey>(0)
        key.id = 100L
        lastInserted = key
        1
    }

    @Test
    fun `生成key返回明文, 校验通过`() {
        `when`(mapper.insert(any(AppApiKey::class.java))).thenAnswer(backfillId())
        val resp = service.generate(1L, 1L, "客户A")
        // 明文仅此一次返回：以 ak_ 前缀开头，且与存入库中的哈希完全不同
        assertTrue(resp.plainKey.startsWith("ak_"))
        val stored = lastInserted!!
        assertNotEquals(resp.plainKey, stored.keyHash)
        // SHA-256 十六进制串固定 64 位，验证确为哈希存储而非明文
        assertEquals(64, stored.keyHash.length)
        // 校验通过：selectList 返回启用状态(status=1)的 key
        `when`(mapper.selectList(any())).thenReturn(listOf(stored))
        assertTrue(service.verify(1L, 1L, resp.plainKey))
    }

    @Test
    fun `吊销后校验失败`() {
        `when`(mapper.insert(any(AppApiKey::class.java))).thenAnswer(backfillId())
        val resp = service.generate(1L, 1L, "客户A")
        // 吊销前：selectById 返回启用状态 key，revoke 应将其 status 置为 0
        val activeKey = AppApiKey(id = resp.id, tenantId = 1L, appId = 1L, keyHash = "x", name = "客户A", status = 1)
        `when`(mapper.selectById(resp.id)).thenReturn(activeKey)
        service.revoke(1L, 1L, resp.id)
        // 吊销后：selectList 返回 status=0 的 key → verify 判定失败
        `when`(mapper.selectList(any())).thenReturn(listOf(activeKey.copy(status = 0)))
        assertFalse(service.verify(1L, 1L, resp.plainKey))
    }
}
