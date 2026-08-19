package com.zhijin.auth.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

/**
 * JWT 签名密钥与编解码器。
 * V1 启动时生成 RSA 密钥对（重启后旧 token 失效，可接受）；
 * 生产化后改为从配置/密钥库加载持久化密钥。
 */
@Configuration
class JwtConfig {

    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private fun rsaKey(): RSAKey =
        RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private)
            .keyID("zhijin-key")
            .build()

    private fun jwkSource() = ImmutableJWKSet<SecurityContext>(JWKSet(rsaKey()))

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(jwkSource())

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()
}
