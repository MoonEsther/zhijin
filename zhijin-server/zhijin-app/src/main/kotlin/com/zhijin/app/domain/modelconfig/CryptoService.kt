package com.zhijin.app.domain.modelconfig

/** 模型供应商 Key 加解密端口（依赖倒置：实现放 infrastructure/crypto，域内仅依赖抽象）。 */
interface CryptoService {

    /** 加密明文，返回可安全落库的密文串（Base64(iv + ciphertext + GCM tag)）。 */
    fun encrypt(plain: String): String

    /** 解密还原明文。 */
    fun decrypt(encrypted: String): String
}
