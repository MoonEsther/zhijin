package com.zhijin.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings

/**
 * OAuth2 Client 注册（内存版）：
 * 1) zhijin-console —— 前端控制台【公共客户端】（D1：无 client_secret，clientAuthenticationMethod=NONE），
 *    authorization_code + PKCE（requireProofKey=true）授权码流程，登录后 redirect 回
 *    http://localhost:5173/callback，不要求用户手动授权（requireAuthorizationConsent=false）。
 * 2) zhijin-server —— 服务间 M2M，client_credentials 客户端凭证模式（保留 client secret）。
 *
 * 说明：B2 重构由 JDBC 持久化改为内存注册（配合表单登录 + 授权码流程）。
 *       D1 修订：zhijin-console 改为公共客户端后不再注册 secret，前端 PKCE 流程不发 client_secret；
 *       zhijin-server 的 client secret 仍使用 passwordEncoder(BCrypt) 编码存储——授权服务器会用同一个
 *       PasswordEncoder Bean 校验 client secret，{noop} 前缀无法被 BCrypt 校验，故不使用 {noop} 字面量。
 */
@Configuration
class ClientRepositoryConfig {

    @Bean
    fun registeredClientRepository(passwordEncoder: PasswordEncoder): RegisteredClientRepository {
        val console = RegisteredClient.withId("zhijin-console")
            .clientId("zhijin-console")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build()
            )
            .build()

        val server = RegisteredClient.withId("zhijin-server")
            .clientId("zhijin-server")
            .clientSecret(passwordEncoder.encode("zhijin-secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope("openid")
            .build()

        return InMemoryRegisteredClientRepository(console, server)
    }
}
