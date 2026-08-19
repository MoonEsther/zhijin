package com.zhijin.app

import com.zhijin.app.service.CryptoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CryptoServiceTest {

    private val crypto = CryptoService()

    @Test
    fun `AES加密后能解密还原`() {
        val plain = "sk-abcdef123456"
        val encrypted = crypto.encrypt(plain)
        assertEquals(plain, crypto.decrypt(encrypted))
    }

    @Test
    fun `密文不含明文且两次加密不同`() {
        val plain = "sk-secret"
        val e1 = crypto.encrypt(plain)
        val e2 = crypto.encrypt(plain)
        assert(!e1.contains(plain))
        assertNotEquals(e1, e2) // 随机 IV
    }
}
