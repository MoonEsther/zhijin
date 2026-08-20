package com.zhijin.app.infrastructure.crypto

import com.zhijin.app.domain.modelconfig.CryptoService
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 模型供应商 Key 的 AES-256-GCM 加解密实现。
 * 加密密钥从环境变量 MODEL_KEY_SECRET 派生（SHA-256 → 32 字节），符合决策 21：平台持有加密。
 */
@Service
class CryptoServiceImpl : CryptoService {

    private val secret: ByteArray = MessageDigest.getInstance("SHA-256")
        .digest((System.getenv("MODEL_KEY_SECRET") ?: "zhijin-dev-secret").toByteArray())

    /** 加密：输出 Base64(iv(12B) + ciphertext(含 GCM tag))。 */
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    /** 解密：还原 Base64 输入。 */
    override fun decrypt(encrypted: String): String {
        val raw = Base64.getDecoder().decode(encrypted)
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct))
    }
}
