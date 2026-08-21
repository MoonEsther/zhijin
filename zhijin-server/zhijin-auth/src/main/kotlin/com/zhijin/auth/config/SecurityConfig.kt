package com.zhijin.auth.config

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.zhijin.auth.application.RbacApplicationService
import com.zhijin.auth.entity.ZhijinUserDetails
import com.zhijin.auth.web.JwtTenantFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher

/**
 * 安全配置：三条过滤链（Spring Security 7 / Boot 4）。
 * 1) 授权服务器链（OAuth2/OIDC 协议端点：/oauth2/authorize、/oauth2/token 等）
 * 2) 资源服务器链（业务接口，Bearer JWT 校验；开放 API 由 ApiKeyAuthFilter 鉴权，放行）
 * 3) 表单登录链（/login 登录页，DaoAuthenticationProvider 自动装配 UserDetailsServiceImpl + BCrypt）
 *
 * 说明：Spring Security 7 已把 OAuth2 授权服务器核心集成进 spring-security-config，
 *       OAuth2AuthorizationServerConfigurer 与 OAuth2AuthorizationServerConfiguration 均使用 7.x 新包路径。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    // ---------- 密码编码器：表单登录用户密码校验 + 授权服务器 client secret 校验共用 ----------
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // ---------- 链 1：OAuth2 授权服务器端点 ----------
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val configurer = OAuth2AuthorizationServerConfigurer()
        http
            .securityMatcher(configurer.endpointsMatcher)
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
            .exceptionHandling { ex ->
                // HTML 请求（浏览器访问授权端点）重定向到 /login 走表单登录；非 HTML（机器请求）返回 401
                ex.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            }
            .apply(configurer.oidc(Customizer.withDefaults()))
        return http.build()
    }

    // ---------- 链 2：资源服务器（Bearer JWT；/v1/** 开放 API 用 API Key，放行） ----------
    @Bean
    @Order(2)
    fun resourceServerSecurityFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
    ): SecurityFilterChain =
        http
            .securityMatcher("/api/**", "/auth/**", "/v1/**")
            // 无状态 JWT API：禁用 CSRF
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/validate").authenticated()
                    .requestMatchers("/auth/logout").authenticated()
                    // 开放 API 走 X-API-Key 鉴权（ApiKeyAuthFilter），不由 JWT 资源服务器链路拦截
                    .requestMatchers("/v1/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    // 用与授权服务器同一 JWKSource 构造 JWT 解码器，保证签名校验一致
                    jwt.decoder(OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource))
                    // 挂载自定义转换器：@PreAuthorize 依赖它把 perms claim 解析为无前缀 authority
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .exceptionHandling { ex ->
                // 统一 JSON 异常响应：未登录 401 invalid_token / 无权限 403 insufficient_scope
                ex.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint())
                ex.accessDeniedHandler(BearerTokenAccessDeniedHandler())
            }
            // 从已认证 JWT 的 tenant_id claim 收敛租户上下文（沿用 B1 多租户能力）
            .addFilterAfter(JwtTenantFilter(), BearerTokenAuthenticationFilter::class.java)
            .build()

    // ---------- 链 3：表单登录（浏览器 OAuth2 授权码流程的用户登录页 /login） ----------
    @Bean
    @Order(3)
    fun formLoginSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .formLogin(Customizer.withDefaults())
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
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

    // ---------- JWT 权限解析器：把 perms claim 转成 Spring Security authorities ----------
    // 关键：Spring Security 默认的 JwtAuthenticationConverter 只从 scope claim 解析且带 SCOPE_ 前缀，
    // 无法满足 @PreAuthorize("hasAuthority('app:create')") 的无前缀权限点匹配。
    // 这里把解析源改为 perms claim 且前缀置空，权限点编码（如 app:create）直接成为 authority。
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(
                JwtGrantedAuthoritiesConverter().apply {
                    setAuthoritiesClaimName("perms")
                    setAuthorityPrefix("")
                }
            )
        }

    // ---------- OAuth2 Token 定制器：把租户 ID + 角色 + 权限点写入 JWT claims ----------
    // 仅对授权码流程（principal 为 UsernamePasswordAuthenticationToken）生效；
    // client_credentials M2M 的 principal 是客户端认证，被 if 分支过滤，不写入租户/角色/权限。
    // perms 在签发令牌时经 RbacApplicationService 实时查询（用户角色 ∪ 组织角色），
    // 保证角色变更在令牌签发时刻即时生效；底层仓储显式传租户号并绕过租户拦截器，
    // 因为 OAuth2 签发链路不经过资源服务器链，TenantContextHolder 无租户上下文。
    @Bean
    fun tokenCustomizer(rbacService: RbacApplicationService): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            val principal = context.getPrincipal<Authentication>()
            if (principal is UsernamePasswordAuthenticationToken && principal.principal is ZhijinUserDetails) {
                val user = principal.principal as ZhijinUserDetails
                val perms = rbacService.getPerms(user.tenantId, user.id)
                context.claims.claims { claims ->
                    claims["tenant_id"] = user.tenantId
                    claims["roles"] = user.authorities.map { it.authority }
                    claims["perms"] = perms
                }
            }
        }
}
