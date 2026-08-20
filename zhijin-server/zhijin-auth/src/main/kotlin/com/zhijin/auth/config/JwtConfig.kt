package com.zhijin.auth.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

/**
 * JWT 签名密钥源：授权服务器用它签发 access token，资源服务器用同一 JWKSource 校验。
 * V1 启动生成 RSA 密钥对（重启后旧 token 失效，可接受）；生产化后从密钥库/Redis 加载。
 */
@Configuration
class JwtConfig {

    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    @Bean
    fun jwkSource(): ImmutableJWKSet<SecurityContext> {
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private)
            .keyID("zhijin-key")
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }
}
