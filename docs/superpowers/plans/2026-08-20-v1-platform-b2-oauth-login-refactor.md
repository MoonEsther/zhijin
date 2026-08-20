# V1 平台服务 · B2-重构：走 Spring Security OAuth2 登录流 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 auth 模块——移除自定义 `/auth/login`（手写密码比对 + 手签 JWT），改为完整 **Spring Security OAuth2 登录流**：控制台(前端)作为 OAuth2 Client 走授权码 + PKCE → `/oauth2/authorize` → Spring 表单登录页 → `/oauth2/token` 发 token。认证完全由 Spring Security 承担。

**Architecture:** 基于已建的 Spring Security 7 OAuth2 授权服务器（B2）。改动三处核心：①JWT 密钥改为 `JWKSource` Bean（授权服务器签名 + 资源服务器校验共用同一密钥）；②SecurityConfig 改三链（授权服务器 / 资源服务器 / formLogin）；③删除自定义 AuthService.login/refresh。租户/角色通过 `OAuth2TokenCustomizer` 写入 token claim，`JwtTenantFilter` 不变。

**Tech Stack:** Spring Security 7 · Spring Boot 4 · Nimbus JOSE JWT · H2（测试）

**设计依据:** `2026-08-17-agent-platform-design.md` §6.1（认证中心）、§13 决策 13/14；用户指示「走 spring security 的登录不要自己写」。

---

## 关键决策

- **登录 = OAuth2 授权码流**：控制台 client（`zhijin-console`，authorization_code + PKCE）→ `/oauth2/authorize` → Spring 表单登录页（`UserDetailsServiceImpl` + `BCryptPasswordEncoder`）→ 授权码 → `/oauth2/token` 签发 JWT。
- **JWT 密钥**：`JwtConfig` 从「自定义 encoder/decoder」改为提供 **`JWKSource<SecurityContext>` Bean**（RSA 2048 KeyPair）。授权服务器用它签 token，资源服务器用 `NimbusJwtDecoder.withJwkSource(jwkSource)` 校验——同源，天然匹配。
- **token claims**：`OAuth2TokenCustomizer<JwtEncodingContext>` 从已认证用户（自定义 `ZhijinUserDetails` 携带 tenantId）写入 `tenant_id` + `roles` claim，`JwtTenantFilter` 无改动。
- **移除**：`AuthController.login/refresh`、`AuthService`（login/refresh/issueToken）、`LoginRequest/TokenResponse/RefreshRequest` DTO。
- **保留**：`/auth/validate`（从 Spring Security `JwtAuthenticationToken` 读身份）、`/auth/logout`、`JwtTenantFilter`、`UserDetailsServiceImpl`（改造返回带 tenant 的 UserDetails）。
- **第三方 OAuth Client**：`zhijin-server`（client_credentials）保留；新增 `zhijin-console`（authorization_code + PKCE，redirect 到控制台）。

---

## 文件结构

```
zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/
├── config/SecurityConfig.kt       ← 三链重构 + console client + token customizer 装配
├── config/JwtConfig.kt            ← 改为 JWKSource Bean
├── config/TokenCustomizerConfig.kt ← OAuth2TokenCustomizer 写 tenant_id/roles（或并入 SecurityConfig）
├── entity/ZhijinUserDetails.kt    ← 自定义 UserDetails（含 tenantId）
├── service/UserDetailsServiceImpl.kt ← 改造返回 ZhijinUserDetails
├── service/AuthService.kt         ← 删除
├── dto/LoginRequest.kt / TokenResponse.kt / RefreshRequest.kt ← 删除
└── endpoint/AuthController.kt     ← 删 login/refresh，留 validate/logout
```

---

## Task 1: JwtConfig 改为 JWKSource Bean

**Files:**
- Modify: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/JwtConfig.kt`

- [ ] **Step 1: 重写 JwtConfig**

```kotlin
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
 * V1 启动生成 RSA 密钥对（重启后旧 token 失效，可接受）；生产化后从密钥库加载。
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
```

- [ ] **Step 2: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth -am clean compile`
Expected: `BUILD SUCCESS`（注意：`NimbusJwtDecoder`/`NimbusJwtEncoder` 的引用在 SecurityConfig 等其它文件中，若编译报 Unresolved 属预期，Task 3 会更新引用）。

- [ ] **Step 3: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/JwtConfig.kt
git commit -m "refactor(auth): JwtConfig 改为 JWKSource Bean"
```
（末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`；不动 `zhijin.iml`。）

---

## Task 2: 自定义 UserDetails（携带 tenantId）

**Files:**
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/entity/ZhijinUserDetails.kt`
- Modify: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/service/UserDetailsServiceImpl.kt`

- [ ] **Step 1: 创建 ZhijinUserDetails**

```kotlin
package com.zhijin.auth.entity

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/** 携带租户 ID 的自定义 UserDetails，供 token customizer 写入 tenant_id claim。 */
data class ZhijinUserDetails(
    val id: Long,
    val tenantId: Long,
    val username: String,
    val password: String,
    val authorities: List<GrantedAuthority>,
) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = authorities.toMutableList()
    override fun getPassword(): String = password
    override fun getUsername(): String = username
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
```

- [ ] **Step 2: 改造 UserDetailsServiceImpl**（返回 ZhijinUserDetails，携带 tenantId）

```kotlin
package com.zhijin.auth.service

import com.zhijin.auth.entity.ZhijinUserDetails
import com.zhijin.auth.repository.SysUserMapper
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/** 从 sys_user 表加载用户并包装为携带租户 ID 的 UserDetails。 */
class UserDetailsServiceImpl(
    private val userMapper: SysUserMapper,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): ZhijinUserDetails {
        val user = userMapper.findByUsername(username)
            ?: throw UsernameNotFoundException("用户不存在: $username")
        return ZhijinUserDetails(
            id = user.id!!,
            tenantId = user.tenantId!!,
            username = user.username,
            password = user.password,
            authorities = listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    }
}
```
> 注意：`findByUsername` 已带 `@InterceptorIgnore`（B2 决策），登录查找不受租户拦截。

- [ ] **Step 3: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-auth/
git commit -m "feat(auth): 自定义 UserDetails 携带租户ID"
```

---

## Task 3: SecurityConfig 三链重构 + console client + token customizer

**Files:**
- Modify: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/SecurityConfig.kt`

- [ ] **Step 1: 重写 SecurityConfig 为三链**

```kotlin
package com.zhijin.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher

/**
 * 安全配置（三链，走 Spring Security OAuth2 登录流）：
 * 1) 授权服务器端点（/oauth2/authorize 等）
 * 2) 资源服务器（/api/** /auth/** /v1/**，Bearer JWT）
 * 3) 表单登录（Spring Security 登录页 /login）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    // ---------- 链 1：OAuth2 授权服务器端点 ----------
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val configurer = OAuth2AuthorizationServerConfigurer()
        http
            .securityMatcher(configurer.endpointsMatcher)
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
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
    fun resourceServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/**", "/auth/**", "/v1/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/validate").authenticated()
                    .requestMatchers("/auth/logout").authenticated()
                    .requestMatchers("/v1/**").permitAll()  // 开放 API 走 X-API-Key
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    // 用同一 JWKSource 校验授权服务器签发的 token
                    jwt.decoder(org.springframework.security.oauth2.jwt.NimbusJwtDecoder
                        .withJwkSource(jwtSource()).build())
                }
            }
            .build()

    // ---------- 链 3：Spring Security 表单登录（/login） ----------
    @Bean
    @Order(3)
    fun formLoginSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .formLogin(Customizer.withDefaults())
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
            .build()
}
```
> 说明：`jwtSource()` 引用 Task 1 的 `jwkSource` Bean（注入 `ImmutableJWKSet<SecurityContext>`）。若 NimbusJwtDecoder.withJwkSource 签名与 Security 7 实际不符，以编译为准调整并报告。

- [ ] **Step 2: 注册控制台 OAuth Client + 保留 M2M client**

```kotlin
    @Bean
    fun registeredClientRepository(): RegisteredClientRepository {
        // 控制台 client（授权码 + PKCE）
        val console = RegisteredClient.withId("zhijin-console")
            .clientId("zhijin-console")
            .clientSecret("{noop}console-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build()) // PKCE
            .build()
        // M2M client（平台服务/三方系统 client_credentials）
        val server = RegisteredClient.withId("zhijin-server")
            .clientId("zhijin-server")
            .clientSecret("{noop}zhijin-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope("openid")
            .build()
        return InMemoryRegisteredClientRepository(console, server)
    }
```

- [ ] **Step 3: 加 token customizer（写 tenant_id + roles claim）**

```kotlin
    @Bean
    fun tokenCustomizer(): org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer<
        org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext> =
        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer { context ->
            val principal = context.getPrincipal<org.springframework.security.core.Authentication>()
            if (principal is org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                && principal.principal is com.zhijin.auth.entity.ZhijinUserDetails) {
                val user = principal.principal as com.zhijin.auth.entity.ZhijinUserDetails
                context.claims.claims { claims ->
                    claims["tenant_id"] = user.tenantId
                    claims["roles"] = user.authorities.map { it.authority }
                }
            }
        }
```
> 若泛型签名的 Kotlin 写法繁琐，可用类型别名或简化；核心是给 JWT 加 tenant_id + roles claim（`JwtTenantFilter` 依赖 tenant_id）。

- [ ] **Step 4: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth -am clean compile`
Expected: `BUILD SUCCESS`。（若资源服务器链引用旧 `JwtConfig.jwtDecoder()` 报错，已在 Task 1 删除，此处用新 jwkSource；AuthService 尚在导致 `AuthController` 引用它——Task 4 删除，编译可能暂红，可先删 AuthController 的 login/refresh 引用。）

- [ ] **Step 5: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/SecurityConfig.kt
git commit -m "refactor(auth): SecurityConfig 三链 + console client + token customizer"
```

---

## Task 4: 移除自定义登录

**Files:**
- Modify: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/endpoint/AuthController.kt`
- Delete: `service/AuthService.kt`、`dto/LoginRequest.kt`、`dto/TokenResponse.kt`、`dto/RefreshRequest.kt`

- [ ] **Step 1: 重写 AuthController（只留 validate + logout）**

```kotlin
package com.zhijin.auth.endpoint

import com.zhijin.auth.dto.ValidateResponse
import com.zhijin.common.web.Result
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 认证契约端点：validate / logout（登录走 OAuth2 授权码流，见 SecurityConfig）。 */
@RestController
@RequestMapping("/auth")
class AuthController {

    @GetMapping("/validate")
    fun validate(authentication: Authentication): Result<ValidateResponse> {
        val jwt = authentication as JwtAuthenticationToken
        val claims = jwt.token.claims
        return Result.success(
            ValidateResponse(
                username = claims["sub"] as? String ?: "",
                userId = (claims["uid"] as? Number)?.toLong(),
                tenantId = (claims["tenant_id"] as? Number)?.toLong(),
                roles = (claims["roles"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            )
        )
    }

    @PostMapping("/logout")
    fun logout(): Result<Unit> = Result.success()
}
```
> 注意：OAuth2 授权码流签发的 token 默认没有 `uid` claim，`userId` 可能为 null（可后续在 customizer 补 `uid`，非阻塞）。

- [ ] **Step 2: 删除 AuthService + 三个 DTO**

用 `git rm` 删除 `service/AuthService.kt`、`dto/LoginRequest.kt`、`dto/TokenResponse.kt`、`dto/RefreshRequest.kt`。

- [ ] **Step 3: 全量编译**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`（若 AuthServiceTest 仍引用已删类，先删测试——Task 5）。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A zhijin-server/zhijin-auth/
git commit -m "refactor(auth): 移除自定义登录, 认证完全走 Spring Security OAuth2"
```

---

## Task 5: 测试重构

**Files:**
- Delete: `zhijin-server/zhijin-auth/src/test/kotlin/com/zhijin/auth/service/AuthServiceTest.kt`
- Modify: `zhijin-server/zhijin-app/src/test/kotlin/com/zhijin/app/auth/AuthControllerTest.kt`
- Create: `zhijin-server/zhijin-app/src/test/kotlin/com/zhijin/app/auth/OAuth2LoginFlowTest.kt`

- [ ] **Step 1: 删 AuthServiceTest**

`git rm zhijin-server/zhijin-auth/src/test/kotlin/com/zhijin/auth/service/AuthServiceTest.kt`

- [ ] **Step 2: 重写 AuthControllerTest（用授权服务器签发的 token）**

用 `@SpringBootTest` + 授权服务器真实签发：注册测试 client（authorization_code），通过 token 端点拿 token，或用 `JwtEncoder` 直接签（与 JWKSource 同源）。推荐直接构造 JWT（用 `JwtConfig.jwkSource()` + `NimbusJwtEncoder`）：
```kotlin
val jwtEncoder = NimbusJwtEncoder(jwtConfig.jwkSource())
val claims = JwtClaimsSet.builder()
    .subject("admin").claim("uid", 1L).claim("tenant_id", 1L).claim("roles", emptyList<String>())
    .build()
val token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
```
（`jwkSource` 从 `JwtConfig` 注入。）

- [ ] **Step 3: 新增 OAuth2 登录流集成测试**

`OAuth2LoginFlowTest.kt`（MockMvc 验证：未认证访问 `/oauth2/authorize` → 重定向 /login；POST 表单登录 → 拿到授权码 → token 端点发 token）：
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginFlowTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userMapper: SysUserMapper
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach fun seed() {
        // 幂等插入 admin（tenant 1）
    }

    @Test
    fun `授权码流登录签发token`() {
        // GET /oauth2/authorize?client_id=zhijin-console&... (PKCE code_challenge)
        // → 302 到 /login
        // POST /login?username=admin&password=admin123 → 302
        // → 用授权码 POST /oauth2/token → 200 带 access_token
    }
}
```
> 授权码流 + PKCE 的 MockMvc 集成较复杂；若耗时，退化为「验证 /login 表单认证 + token 端点 client_credentials 发 token」两个独立验证，report 采用方式。

- [ ] **Step 4: 全量测试**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app test`
Expected: 全绿（B1 集成测试、B2 重构测试、B3-B5 测试）。

- [ ] **Step 5: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A zhijin-server/
git commit -m "refactor(auth): 测试重构为 OAuth2 登录流"
```

---

## Task 6: 端到端联调 + 收尾

- [ ] **Step 1: 全量构建**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 真实联调（OAuth2 登录流）**

启动平台服务（真 PG/Nacos）。用 curl 模拟授权码流：
1. `GET /oauth2/authorize?client_id=zhijin-console&response_type=code&redirect_uri=...&code_challenge=...` → 302 到 `/login`
2. `GET /login` → 登录页
3. `POST /login?username=admin&password=admin123`（带 JSESSIONID cookie）→ 302 回 authorize → 拿 code
4. `POST /oauth2/token`（code + code_verifier + client credentials）→ 返回 `access_token`（JWT，含 tenant_id/roles claim）
5. 用 token 调 `/auth/validate` → 返回身份
> curl 模拟 PKCE/授权码较繁琐；若步骤 1-4 复杂，可改用脚本或用 client_credentials 演示 token 签发 + validate（report 说明）。

- [ ] **Step 3: 记录实现修正**，追加到本计划「执行修正记录」。

- [ ] **Step 4: Commit 遗留**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A
git commit -m "docs(plans): B2重构 追加执行修正记录"
```

---

## Self-Review 记录

- **Spec 覆盖**：§6.1 认证中心（login 走 OAuth2 授权码）✓ · §13 决策 13/14 ✓ · 用户指示「走 spring security 的登录」✓。
- **移除**：自定义 /auth/login、AuthService、LoginRequest/TokenResponse/RefreshRequest、AuthServiceTest。
- **保留**：/auth/validate、/auth/logout、JwtTenantFilter、UserDetailsServiceImpl（改造）、三方 M2M client。
- **类型一致性**：`tenant_id` claim ↔ JwtTenantFilter ↔ ZhijinUserDetails.tenantId；`jwkSource` Bean 贯穿授权服务器签名 + 资源服务器校验。

## 执行交接

重构完成后 → **计划 C**（AI 服务真实供应商）或 **B6**（用量统计 + 审计），可继续 V1 剩余交付。

---

## 博客研读补充（2026-08-20，已通读掘金 SAS 系列全部 27 篇）

来源：掘金专栏 `7239953874950684732`（SAS 1.x / Boot 3 时代，API 以 Security 7 / Boot 4 实际编译为准）。对 B2 重构的采纳与注意点：

| 主题 | 采纳/注意 |
|---|---|
| `applyDefaultSecurity(http)` | **已过时**，用组件式 `OAuth2AuthorizationServerConfigurer`（B2 已如此）✓ |
| `OAuth2TokenCustomizer` 写自定义 claim | 采纳（tenant_id/roles）✓；按 `context.getPrincipal().getPrincipal() is UserDetails` 判断 |
| `JwtGrantedAuthoritiesConverter` 默认加 `SCOPE_` 前缀 | 注意：RBAC 鉴权 `hasAuthority('SCOPE_xxx')`；自定义前缀/claim 名时对应调整 |
| issuer 一致性 | token `iss` 必须与 `AUTH_ISSUER` 一致，否则资源服务器 401；联调确认 |
| 统一异常响应 | 采纳：`AuthenticationEntryPoint`/`AccessDeniedHandler` 返回 JSON（401 invalid_token / 403 insufficient_scope） |
| 登出三层 | `/logout`（session）+ `/connect/logout`（OIDC，`post_logout_redirect_uri` 需预注册）+ `/oauth2/revoke`（撤销 token）；V1 保留 `/auth/logout` 简化 |
| JWKSource 持久化 | V1 启动生成（重启失效可接受）；生产化用 Redis 存 JWKSet（`JWKSet.parse/toString`） |
| 前后端分离 console | Plan D 时采纳 spring-session-data-redis 共享 session + JSON 登录/确认处理器 |
| token 端点参数 | SAS 1.2.1+ 只能用 **form-data**（url-params 失败）；联调注意 |
| InMemory 存储 | 禁止生产，用 JDBC（B2 已用）✓ |

---

## 执行修正记录（2026-08-20 实现期间的真实发现，均已落地并验证）

| # | 修正 | 原因 |
|---|---|---|
| 1 | `OAuth2AuthorizationServerConfiguration` 包路径：`org.springframework.security.config.annotation.web.configuration` | Security 7 并入 spring-security-config，包迁移 |
| 2 | `jwkSource()` 无法在 SecurityConfig 内调用（Bean 在 JwtConfig）→ 方法参数注入 | Bean 方法跨类不可直接调 |
| 3 | `{noop}` 明文 client secret 与 `BCryptPasswordEncoder` 冲突 → 用 `passwordEncoder.encode(...)` 存储 | AS 的 ClientSecretAuthenticationProvider 用唯一 PasswordEncoder Bean 校验，BCrypt 无法验证 `{noop}` |
| 4 | `UserDetailsServiceImpl` 加 `@Service` | 表单登录链 DaoAuthenticationProvider 自动拾取 |
| 5 | `ClientRepositoryConfig` 重写为 InMemory（console + server），旧 JDBC repo 移除 | 避免 duplicate `registeredClientRepository` Bean；JDBC 表留作后续 |
| 6 | `JwtTenantFilter` 保留在链 2 | 多租户隔离不能丢 |
| 7 | `ZhijinUserDetails` `val username/password` 与 override 冲突 → `@get:JvmName` | Kotlin 平台声明冲突 |
| 8 | tokenCustomizer 对 access + ID token 都生效（UsernamePasswordAuthenticationToken 守卫保证 client_credentials 干净） | JwtGenerator 统一处理 |
| 9 | AuthControllerTest issuer = `http://localhost:8080` | 对齐 AUTH_ISSUER 默认值 |
| 10 | MockMvc 测 authorize 需显式 query-string（`.param()` 被丢弃） | `OAuth2EndpointUtils.getQueryParameters` 读原始 query string |
| 11 | `spring-security-test` test 依赖加入 app pom | `.with(csrf())` / `.with(httpBasic())` |
| 12 | console client 为机密客户端 + PKCE（`CLIENT_SECRET_BASIC`） | 计划如此；SPA 更常见公共客户端 + `NONE`，Plan D 前端时再定 |

> **端到端验收**：真实环境 `POST /oauth2/token`（client_credentials）签发 JWT（`iss=http://localhost:8080`）✅、OIDC Discovery 正常 ✅；完整授权码流（表单登录 → PKCE → code → token，`sub=admin`）由集成测试 `OAuth2LoginFlowTest` 验证（3 场景全绿）。自定义 `/auth/login` 已彻底移除，认证完全走 Spring Security。
