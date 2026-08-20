package com.zhijin.app

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.zhijin.app.application.ApiKeyApplicationService
import com.zhijin.app.domain.apikey.ApiKeyRepository
import com.zhijin.app.domain.apikey.AppApiKey
import com.zhijin.app.mapper.AppApiKeyMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.security.MessageDigest

/**
 * ApiKeyApplicationService 单元测试：模拟 ApiKeyRepository。
 * 覆盖核心安全要求：明文只返回一次、DB 只存 SHA-256 哈希、吊销后校验失败、
 * findByPlainKey 反查租户+应用，以及 @InterceptorIgnore 绕过租户拦截器断言。
 */
class ApiKeyApplicationServiceTest {

    // ---- Mockito 与 Kotlin 非空参数的适配 ----
    // Mockito 的 any(Class) 运行时返回 null，而 Kotlin 会对非空参数在调用点插入编译期非空校验，
    // 导致 mock 未被调用、匹配器残留。这里先调用 any() 注册"任意匹配"语义，
    // 再返回一个非空占位实例，以绕过该校验。
    private fun anyApiKey(): AppApiKey {
        any(AppApiKey::class.java)
        return AppApiKey(id = null, tenantId = 1L, appId = 1L, keyHash = "", name = "", status = 1)
    }

    private val repository = mock(ApiKeyRepository::class.java)
    private val service = ApiKeyApplicationService(repository)

    // 记录最近一次 save 的领域实体，用于断言"DB 存的是哈希而非明文"
    private var lastSaved: AppApiKey? = null

    // 模拟 save 回填自增主键 id（MyBatis-Plus IdType.AUTO 的真实行为）
    private fun backfillId() = Answer<AppApiKey> { invocation: InvocationOnMock ->
        invocation.getArgument<AppApiKey>(0).copy(id = 100L).also { lastSaved = it }
    }

    @Test
    fun `生成key返回明文, 校验通过`() {
        `when`(repository.save(anyApiKey())).thenAnswer(backfillId())
        val resp = service.generate(1L, 1L, "客户A")
        // 明文仅此一次返回：以 ak_ 前缀开头，且与存入库中的哈希完全不同
        assertTrue(resp.plainKey.startsWith("ak_"))
        val stored = lastSaved!!
        assertNotEquals(resp.plainKey, stored.keyHash)
        // SHA-256 十六进制串固定 64 位，验证确为哈希存储而非明文
        assertEquals(64, stored.keyHash.length)
        // 校验通过：findActiveByHash 返回启用态(status=1)的 key
        `when`(repository.findActiveByHash(1L, 1L, stored.keyHash)).thenReturn(stored)
        assertTrue(service.verify(1L, 1L, resp.plainKey))
    }

    @Test
    fun `吊销后校验失败`() {
        `when`(repository.save(anyApiKey())).thenAnswer(backfillId())
        val resp = service.generate(1L, 1L, "客户A")
        // 吊销前：findById 返回启用态 key，revoke 应将其 status 置为 0
        val activeKey = AppApiKey(id = resp.id, tenantId = 1L, appId = 1L, keyHash = "x", name = "客户A", status = 1)
        `when`(repository.findById(1L, resp.id)).thenReturn(activeKey)
        service.revoke(1L, 1L, resp.id)
        // 吊销后：findActiveByHash 返回 null → verify 判定失败
        `when`(repository.findActiveByHash(anyLong(), anyLong(), anyString())).thenReturn(null)
        assertFalse(service.verify(1L, 1L, resp.plainKey))
    }

    @Test
    fun `findByPlainKey反查租户应用`() {
        val plain = "ak_test_plain_key"
        val stored = AppApiKey(id = 1L, tenantId = 7L, appId = 3L, keyHash = sha256(plain), name = "x", status = 1)
        `when`(repository.findByHash(sha256(plain))).thenReturn(stored)
        val resolved = service.findByPlainKey(plain)
        assertEquals(Pair(7L, 3L), resolved)
    }

    @Test
    fun `findByPlainKey无效key返回null`() {
        `when`(repository.findByHash(anyString())).thenReturn(null)
        assertNull(service.findByPlainKey("nope"))
    }

    @Test
    fun `findByPlainKey已吊销返回null`() {
        val plain = "ak_revoked"
        val stored = AppApiKey(id = 1L, tenantId = 7L, appId = 3L, keyHash = sha256(plain), name = "x", status = 0)
        `when`(repository.findByHash(sha256(plain))).thenReturn(stored)
        assertNull(service.findByPlainKey(plain))
    }

    @Test
    fun `findByHash绕过租户拦截器`() {
        // 反射断言 AppApiKeyMapper.findByHash 带 @InterceptorIgnore(tenantLine=true)：
        // 保证开放 API /v1 鉴权在租户上下文建立前不被租户拦截器拼上 tenant_id=0。
        val method = AppApiKeyMapper::class.java.getMethod("findByHash", String::class.java)
        val annotation = method.getAnnotation(InterceptorIgnore::class.java)
        assertNotNull(annotation)
        assertEquals("true", annotation.tenantLine)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
