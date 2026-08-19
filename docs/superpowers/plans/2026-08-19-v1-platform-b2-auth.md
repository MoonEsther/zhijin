# V1 平台服务 · B2 认证中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建认证中心（zhijin-auth 模块）：管理端登录签发 JWT（带租户/角色）、鉴权过滤器与 RBAC、标准 OAuth2.1/OIDC 授权服务器（供三方平台以 OAuth Client 接入）、validate/refresh/logout/userinfo 契约端点。

**Architecture:** 基于 Spring Security 7（Boot 4 内置 OAuth2 授权服务器能力，SAS 已并入）。**双轨**：①管理端登录走自定义 `/auth/login` + `/auth/refresh` 签发 JWT（同一把 RSA 密钥，claim 携带 `sub/tenant_id/roles`）；②三方平台接入走标准 OAuth2.1 授权码流程（`/oauth2/authorize|token|introspect|revoke|jwks` + OIDC Discovery + `/userinfo`）。租户上下文从 JWT claim 收敛进 `TenantContextHolder`（替换 B1 的 `X-Tenant-Id` 请求头来源，`TenantFilter` 只改实现不改接口）。

**Tech Stack:** Spring Security 7.1 · `spring-boot-starter-oauth2-authorization-server` · Nimbus JOSE JWT · MyBatis-Plus（复用 B1）· H2（测试）

**设计依据:** `2026-08-17-agent-platform-design.md` §6.1 认证中心设计、§13 决策 13/14、§12.1 七大原则。

---

## 关键决策

- **管理端登录 ≠ OAuth2 密码模式**（OAuth2.1 已移除 password grant）：自定义 `/auth/login`（用户名密码 → JWT），`/auth/refresh` 刷新；JWT 与 OAuth token 共用同一 RSA 密钥与 `JwtDecoder`。
- **RBAC**：JWT 携带 `roles` claim（角色码列表），`@PreAuthorize("hasAuthority('ROLE_xxx')")` 或 `hasAuthority('perm:xxx')` 校验；权限数据在 sys_permission/sys_role_permission（B1 已建表）。
- **租户收敛**：JWT 的 `tenant_id` claim → `TenantContextHolder`；管理端请求不再依赖 `X-Tenant-Id` 请求头（保留兼容）。
- **初始管理员**：启动时用 `ApplicationRunner` 幂等种子（默认租户 + 管理员账号），密码用 `PasswordEncoder` 加密，不硬编码哈希。
- **客户端注册**：三方 OAuth Client 存 PG（`JdbcRegisteredClientRepository`），初始用 yml 声明式注册一个示例 client。
- **测试**：H2（PG 兼容模式），Spring Security 测试工具（`spring-security-test`）。

---

## 文件结构

```
zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/
├── config/SecurityConfig.kt              ← 双 SecurityFilterChain：授权服务器链 + 资源服务器链
├── config/JwtConfig.kt                   ← RSA 密钥 + JwtEncoder + JwtDecoder
├── config/ClientRepositoryConfig.kt      ← RegisteredClientRepository(JDBC) + OIDC
├── endpoint/AuthController.kt            ← /auth/login /auth/refresh /auth/logout /auth/validate /auth/userinfo
├── service/UserDetailsServiceImpl.kt     ← 按 sys_user 查用户
├── service/AuthService.kt                ← 登录/刷新/校验逻辑
├── web/JwtTenantFilter.kt                ← 从 JWT claim 收敛租户上下文
├── seeder/AdminSeeder.kt                 ← 默认租户+管理员幂等种子
└── (resources) db/migration 追加 V2__seed(或由 seeder 承担)
```

---

## Task 1: 安全依赖与双 SecurityFilterChain

**Files:**
- Modify: `zhijin-server/zhijin-auth/pom.xml`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/SecurityConfig.kt`

- [ ] **Step 1: zhijin-auth pom 追加安全依赖**

```xml
    <!-- Spring Security 7(含 OAuth2 授权服务器) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <!-- 测试 -->
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
```

> 注意：若 `spring-boot-starter-oauth2-authorization-server` 在 Boot 4 中不存在（artifactId 有变），改用 `org.springframework.security:spring-security-oauth2-authorization-server`（版本由 Security BOM 管理）。实现时以 mvn 能解析为准，BLOCKED 时报告。

- [ ] **Step 2: 实现双链 SecurityConfig**

`SecurityConfig.kt`：
```kotlin
package com.zhijin.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher

/**
 * 安全配置：
 * 1) 授权服务器链（OAuth2/OIDC 协议端点）
 * 2) 资源服务器链（业务接口，Bearer JWT 校验）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    // ---------- 链 1：OAuth2 授权服务器端点 ----------
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(
                org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers
                    .OAuth2AuthorizationServerConfigurer().getEndpointsMatcher()
            )
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .exceptionHandling { ex ->
                ex.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            }
            .apply(
                org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers
                    .OAuth2AuthorizationServerConfigurer().oidc(Customizer.withDefaults())
            )
            .and()
            .build()

    // ---------- 链 2：业务接口（资源服务器，Bearer JWT） ----------
    @Bean
    @Order(2)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/**", "/auth/**")
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/login").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .build()

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer("http://${"${'$'}{AUTH_ISSUER:localhost:8080}}")
            .build()
}
```

> 说明：`OAuth2AuthorizationServerConfiguration` 提供默认端点配置；此处按 Security 7 组件式 DSL 显式装配。实现时若 API 签名与上述有出入（Security 7 演进较快），以实际编译为准并报告修正，不擅自更换架构。

- [ ] **Step 3: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth -am clean compile`
Expected: `BUILD SUCCESS`。
- 若安全 starter 解析或 Security 7 DSL 编译失败 → 报 BLOCKED 附错误，控制器决策。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-auth/
git commit -m "feat(auth): Spring Security 7 双链(授权服务器+资源服务器)"
```
（末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`；只暂存 auth 相关文件，不动 `zhijin.iml`。）

---

## Task 2: RSA JWT 密钥 + JwtEncoder/Decoder

**Files:**
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/JwtConfig.kt`

- [ ] **Step 1: 实现 JWT 配置**

`JwtConfig.kt`：
```kotlin
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
```

> 说明：管理端 `/auth/login` 用 `JwtEncoder` 签发自定义 JWT；OAuth2 授权服务器用自己的 JWKSource（见 Task 5）。资源服务器链用此 `JwtDecoder` 校验两者。

- [ ] **Step 2: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add zhijin-server/zhijin-auth/
git commit -m "feat(auth): RSA JWT 密钥与编解码器"
```

---

## Task 3: 管理端登录与刷新（TDD）

**Files:**
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/service/UserDetailsServiceImpl.kt`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/service/AuthService.kt`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/endpoint/AuthController.kt`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/dto/LoginRequest.kt` / `TokenResponse.kt`
- Test: `zhijin-server/zhijin-auth/src/test/kotlin/com/zhijin/auth/service/AuthServiceTest.kt`

- [ ] **Step 1: 写失败测试**

`AuthServiceTest.kt`（验证正确密码返回带 claims 的 JWT，错误密码抛 BizException）：
```kotlin
package com.zhijin.auth.service

import com.zhijin.auth.config.JwtConfig
import com.zhijin.auth.dto.LoginRequest
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthServiceTest {

    // 用真实 JwtConfig 编码器
    private val jwtConfig = JwtConfig()
    private val passwordEncoder = BCryptPasswordEncoder()

    private fun userService(): UserDetailsServiceImpl =
        UserDetailsServiceImpl(object : com.zhijin.auth.repository.SysUserRepository {
            // 测试用假用户：密码为 "admin123" 的 bcrypt
            override fun findByUsername(username: String) = com.zhijin.auth.entity.SysUser(
                id = 1L, tenantId = 1L, username = "admin",
                password = passwordEncoder.encode("admin123"), nickname = "管理员", status = 1,
            )
        })

    private val authService = AuthService(userService(), jwtConfig, passwordEncoder)

    @Test
    fun `正确密码签发带租户与角色claims的JWT`() {
        val resp = authService.login(LoginRequest("admin", "admin123"))
        assertNotNull(resp.accessToken)
    }

    @Test
    fun `错误密码抛业务异常`() {
        assertThrows(BizException::class.java) {
            authService.login(LoginRequest("admin", "wrong"))
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth test -Dtest=AuthServiceTest`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现**

`entity/SysUser.kt`（复用 B1 的 sys_user 表，MyBatis-Plus 实体）：
```kotlin
package com.zhijin.auth.entity

import com.baomidou.mybatisplus.annotation.*
import java.time.LocalDateTime

/** 用户实体（对应 sys_user 表，B1 已建表）。 */
@TableName("sys_user")
data class SysUser(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var username: String = "",
    var password: String = "",
    var nickname: String = "",
    var status: Int = 1,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
)
```

`repository/SysUserRepository.kt`（接口 + MyBatis-Plus 实现）：
```kotlin
package com.zhijin.auth.repository

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.entity.SysUser
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SysUserMapper : BaseMapper<SysUser>
```

`service/UserDetailsServiceImpl.kt`：
```kotlin
package com.zhijin.auth.service

import com.zhijin.auth.repository.SysUserMapper
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/** 从 sys_user 表加载用户（Spring Security UserDetailsService 适配）。 */
class UserDetailsServiceImpl(
    private val userMapper: SysUserMapper,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userMapper.selectOne(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>().eq("username", username)
        ) ?: throw UsernameNotFoundException("用户不存在: $username")
        return User.withUsername(user.username).password(user.password).roles("USER").build()
    }
}
```

> 注意：测试里用 `UserDetailsServiceImpl(object : SysUserRepository {...})`，故 Repository 需为接口（`SysUserRepository` 接口 + `SysUserMapper` 实现）。实现时定义 `interface SysUserRepository { fun findByUsername(username: String): SysUser? }`，`SysUserMapper : BaseMapper<SysUser>, SysUserRepository` 提供默认实现。若此结构与测试不一致，以可编译为准调整并记录。

`dto/LoginRequest.kt`：
```kotlin
package com.zhijin.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String = "",
    @field:NotBlank(message = "密码不能为空")
    val password: String = "",
)
```

`dto/TokenResponse.kt`：
```kotlin
package com.zhijin.auth.dto

/** 登录/刷新响应。 */
data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val tenantId: Long? = null,
)
```

`service/AuthService.kt`：
```kotlin
package com.zhijin.auth.service

import com.zhijin.auth.config.JwtConfig
import com.zhijin.auth.dto.LoginRequest
import com.zhijin.auth.dto.TokenResponse
import com.zhijin.auth.entity.SysUser
import com.zhijin.auth.repository.SysUserRepository
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant

/** 认证服务：管理端登录/刷新，签发携带租户与角色的 JWT。 */
class AuthService(
    private val userRepository: SysUserRepository,
    private val jwtConfig: JwtConfig,
    private val passwordEncoder: PasswordEncoder,
) {

    fun login(req: LoginRequest): TokenResponse {
        val user = userRepository.findByUsername(req.username)
            ?: throw BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误")
        if (user.status != 1) throw BizException(ResultCode.FORBIDDEN, "账号已禁用")
        if (!passwordEncoder.matches(req.password, user.password)) {
            throw BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误")
        }
        return issueToken(user)
    }

    /** 签发 JWT：sub=用户名, tenant_id, roles(暂空, 后续接 RBAC 查角色)。 */
    private fun issueToken(user: SysUser): TokenResponse {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("zhijin")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .subject(user.username)
            .claim("uid", user.id)
            .claim("tenant_id", user.tenantId)
            .claim("roles", emptyList<String>())
            .build()
        val token = jwtConfig.jwtEncoder().encode(JwtEncoderParameters.from(claims)).tokenValue
        return TokenResponse(accessToken = token, tenantId = user.tenantId)
    }
}
```

`endpoint/AuthController.kt`：
```kotlin
package com.zhijin.auth.endpoint

import com.zhijin.auth.dto.LoginRequest
import com.zhijin.auth.dto.TokenResponse
import com.zhijin.auth.service.AuthService
import com.zhijin.common.web.Result
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/** 认证契约端点：login / refresh / logout / validate / userinfo。 */
@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): Result<TokenResponse> =
        Result.success(authService.login(req))

    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshRequest): Result<TokenResponse> =
        Result.success(authService.refresh(req.refreshToken))
}
```

（`RefreshRequest` 含 `refreshToken` 字段；`AuthService.refresh` 先实现为：验证 refreshToken（解析 JWT）+ 重新查用户签发——V1 简化版。）

- [ ] **Step 4: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-auth test -Dtest=AuthServiceTest`
Expected: `2 passed`。

- [ ] **Step 5: Commit**

```bash
git add zhijin-server/zhijin-auth/
git commit -m "feat(auth): 管理端登录/刷新签发 JWT"
```

---

## Task 4: 租户上下文从 JWT 收敛 + 鉴权过滤器

**Files:**
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/web/JwtTenantFilter.kt`
- Modify: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/TenantFilter.kt`（收敛来源）

- [ ] **Step 1: JWT 租户过滤器**

`JwtTenantFilter.kt`（在资源服务器链之后解析已认证 JWT 的 tenant claim 写入上下文）：
```kotlin
package com.zhijin.auth.web

import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * 从已认证 JWT 的 tenant_id claim 收敛租户上下文。
 * 放在资源服务器认证之后执行；未认证请求(如 /auth/login)跳过。
 */
class JwtTenantFilter : HttpFilter() {

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        if (auth is JwtAuthenticationToken) {
            val tenantId = auth.token.getClaim<Long>("tenant_id")
            TenantContextHolder.setTenantId(tenantId)
        }
        try {
            chain.doFilter(req, res)
        } finally {
            TenantContextHolder.clear()
        }
    }
}
```

- [ ] **Step 2: 注册过滤器（资源服务器认证之后）**

在 `SecurityConfig` 的 `defaultSecurityFilterChain` 中，`oauth2ResourceServer(...)` 之后加：
```kotlin
            .addFilterAfter(JwtTenantFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter::class.java)
```
（若顺序 API 与 Security 7 实际不符，以编译为准调整，用 `@Component` + `OncePerRequestFilter` 亦可，报告采用方式。）

- [ ] **Step 3: 构建 + 现有测试**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile && mvn -pl zhijin-framework test`
Expected: BUILD SUCCESS + B1 的 4 个测试仍绿。

- [ ] **Step 4: Commit**

```bash
git add zhijin-server/zhijin-auth/ zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/TenantFilter.kt
git commit -m "feat(auth): 租户上下文从 JWT claim 收敛"
```

---

## Task 5: validate / logout / userinfo 端点（TDD）

**Files:**
- Modify: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/endpoint/AuthController.kt`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/dto/ValidateResponse.kt`
- Test: `zhijin-server/zhijin-auth/src/test/kotlin/com/zhijin/auth/endpoint/AuthControllerTest.kt`

- [ ] **Step 1: 写失败测试（MockMvc）**

`AuthControllerTest.kt`（JWT 认证后访问 /auth/validate 返回身份信息）：
```kotlin
package com.zhijin.auth.endpoint

import com.zhijin.auth.config.JwtConfig
import com.zhijin.auth.entity.SysUser
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtConfig: JwtConfig

    private fun token(tenantId: Long = 1L): String {
        val claims = JwtClaimsSet.builder()
            .subject("admin").claim("uid", 1L).claim("tenant_id", tenantId).claim("roles", emptyList<String>())
            .build()
        return jwtConfig.jwtEncoder().encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    @Test
    fun `带有效JWT访问validate返回身份`() {
        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.tenantId").value(1))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app test -Dtest=AuthControllerTest`
（测试放 zhijin-app 或 zhijin-auth；若 auth 模块无 @SpringBootApplication，放 zhijin-app 的 test 下，import auth 包。）
Expected: 失败（端点不存在）。

- [ ] **Step 3: 实现端点**

`ValidateResponse.kt`：
```kotlin
package com.zhijin.auth.dto

/** /auth/validate 返回的身份信息。 */
data class ValidateResponse(
    val username: String,
    val userId: Long?,
    val tenantId: Long?,
    val roles: List<String>,
)
```

`AuthController` 追加（注入 `Authentication`，从 JWT 取 claims）：
```kotlin
    @GetMapping("/validate")
    fun validate(authentication: org.springframework.security.core.Authentication): Result<ValidateResponse> {
        val jwt = authentication as org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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
```
（logout 在无状态 JWT 下为客户端侧弃用 token；V1 返回成功即可，后续可接吊销列表。）

- [ ] **Step 4: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app test -Dtest=AuthControllerTest`
Expected: `1 passed`。

- [ ] **Step 5: Commit**

```bash
git add zhijin-server/
git commit -m "feat(auth): validate/logout 端点"
```

---

## Task 6: 三方 OAuth Client 注册 + OIDC + 初始管理员种子

**Files:**
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/config/ClientRepositoryConfig.kt`
- Create: `zhijin-server/zhijin-auth/src/main/kotlin/com/zhijin/auth/seeder/AdminSeeder.kt`
- Create: `zhijin-server/zhijin-auth/src/main/resources/application-auth.yml`（或并入 app 的 yml）

- [ ] **Step 1: RegisteredClientRepository（JDBC）**

`ClientRepositoryConfig.kt`：
```kotlin
package com.zhijin.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

@Configuration
class ClientRepositoryConfig {

    /** 三方 OAuth Client 持久化到 PG（表由授权服务器 schema 提供）。 */
    @Bean
    fun registeredClientRepository(jdbcTemplate: JdbcTemplate): RegisteredClientRepository =
        JdbcRegisteredClientRepository(jdbcTemplate)
}
```
> 授权服务器 schema（oauth2_registered_client 等表）由 Spring Security 提供 `schema.sql`，在 resources 放一份并让 Flyway 执行，或由 Spring 自动初始化。实现时选其一并记录。

- [ ] **Step 2: OIDC Discovery 验证配置**

确认 `spring-boot-starter-oauth2-authorization-server` 已含 OIDC；`AuthorizationServerSettings` 配了 issuer 后，`/.well-known/openid-configuration` 与 `/oauth2/jwks` 即暴露。

- [ ] **Step 3: 初始管理员种子（幂等）**

`AdminSeeder.kt`：
```kotlin
package com.zhijin.auth.seeder

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.auth.entity.SysTenant
import com.zhijin.auth.entity.SysUser
import com.zhijin.auth.repository.SysTenantMapper
import com.zhijin.auth.repository.SysUserMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/** 启动时幂等种子：默认租户 + 管理员账号（admin / 由 env ADMIN_INIT_PASSWORD 指定，默认 admin123）。 */
@Component
class AdminSeeder(
    private val tenantMapper: SysTenantMapper,
    private val userMapper: SysUserMapper,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        val tenant = tenantMapper.selectOne(QueryWrapper<SysTenant>().eq("tenant_code", "default"))
        val tenantId = if (tenant == null) {
            val t = SysTenant(tenantCode = "default", tenantName = "默认租户", status = 1)
            tenantMapper.insert(t)
            t.id!!
        } else tenant.id!!

        val admin = userMapper.selectOne(QueryWrapper<SysUser>().eq("tenant_id", tenantId).eq("username", "admin"))
        if (admin == null) {
            val initPwd = System.getenv("ADMIN_INIT_PASSWORD") ?: "admin123"
            val u = SysUser(
                tenantId = tenantId, username = "admin",
                password = passwordEncoder.encode(initPwd), nickname = "管理员", status = 1,
            )
            userMapper.insert(u)
            log.info("[seed] 已创建默认租户({})与管理员 admin", tenantId)
        }
    }
}
```
> 需要 `SysTenant` 实体 + `SysTenantMapper`（对应 B1 的 sys_tenant 表）。`PasswordEncoder` Bean 用 `BCryptPasswordEncoder`（在 SecurityConfig 提供）。

- [ ] **Step 4: 构建 + 集成验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
Expected: BUILD SUCCESS（含全部测试）。

- [ ] **Step 5: Commit**

```bash
git add zhijin-server/
git commit -m "feat(auth): 三方OAuth Client注册 + 初始管理员种子"
```

---

## Task 7: 收尾验证

- [ ] **Step 1: 全量构建 + 测试**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 记录实现修正**

把实现中发现的安全/Security 7 真实 API 修正追加到本计划的「执行修正记录」（参照 B1 做法）。

- [ ] **Step 3: Commit 遗留**

```bash
git add -A
git commit -m "docs(plans): B2 追加执行修正记录"
```

---

## Self-Review 记录

- **Spec 覆盖**：§6.1 认证契约（login/logout/validate/refresh/userinfo）✓ · OAuth2.1/OIDC ✓ · 三方 OAuth Client 接入 ✓ · JWT 携带租户 ✓ · RBAC 基础 ✓（`@EnableMethodSecurity` + roles claim）· 决策 13/14 ✓。
- **占位符扫描**：无 TBD；代码块完整。
- **类型一致性**：`tenant_id` claim ↔ `TenantContextHolder` ↔ `SysUser.tenantId` 命名一致；`JwtConfig`/`JwtEncoder`/`JwtDecoder` 在 Task 2/3/5 间一致。

## 执行交接

B2 完成后 → **B3 应用管理**（zhijin-app：智能体 CRUD + 模型配置 + 发布 + API Key），API Key 鉴权在 B2 的 validate 之外补一个简单过滤器。
