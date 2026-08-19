package com.zhijin.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import com.zhijin.auth.web.JwtTenantFilter

/**
 * 安全配置：
 * 1) 授权服务器链（OAuth2/OIDC 协议端点）
 * 2) 资源服务器链（业务接口，Bearer JWT 校验）
 *
 * 说明：Spring Security 7 已把 OAuth2 授权服务器核心集成进 spring-security-config，
 *       OAuth2AuthorizationServerConfigurer 的包路径与 6.x 不同，此处使用 7.x 新路径。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    // ---------- 密码编码器：AuthService 校验登录密码使用 ----------
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // ---------- 链 1：OAuth2 授权服务器端点 ----------
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val configurer = OAuth2AuthorizationServerConfigurer()
        http
            .securityMatcher(configurer.endpointsMatcher)
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .exceptionHandling { ex ->
                ex.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            }
            .apply(configurer.oidc(Customizer.withDefaults()))
        return http.build()
    }

    // ---------- 链 2：业务接口（资源服务器，Bearer JWT） ----------
    @Bean
    @Order(2)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/**", "/auth/**")
            // 无状态 JWT API：禁用 CSRF（POST 登录等不需要表单 CSRF token）
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/login").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .addFilterAfter(JwtTenantFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .build()

    // ---------- 授权服务器设置：issuer 从 AUTH_ISSUER 环境变量解析（默认 localhost:8080） ----------
    // 注意：@Value 里的 "${AUTH_ISSUER:localhost:8080}" 由 Spring 占位符机制在运行时解析，
    //       不要直接在 Kotlin 字符串里用 "$" 插值，否则会变成字面量占位符而非真实环境值。
    @Bean
    fun authorizationServerSettings(
        @Value("\${AUTH_ISSUER:localhost:8080}") issuerHostPort: String
    ): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer("http://$issuerHostPort")
            .build()
}
