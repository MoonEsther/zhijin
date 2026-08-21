package com.zhijin.app.auth

import com.jayway.jsonpath.JsonPath
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.zhijin.auth.infrastructure.persistence.SysUserRecord
import com.zhijin.auth.repository.SysUserMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.util.Base64

/**
 * 方法级安全集成测试（Task 4 RBAC 方案 C）：
 * 1) 授权码全流程签发的 JWT 应携带非空 perms claim（验证 tokenCustomizer + getPerms 在无租户上下文下取到值）；
 * 2) 带 usage:view 权限点的 JWT 可访问 @PreAuthorize("hasAuthority('usage:view')") 的 /api/usage/summary；
 * 3) 缺 usage:view 权限点的 JWT 访问同一端点应返回 403（验证 JwtAuthenticationConverter 从 perms claim 解析 authority）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MethodSecurityIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userMapper: SysUserMapper

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    // 与授权服务器共用同一 JWKSource，可解码校验其签发的 JWT
    @Autowired
    lateinit var jwkSource: ImmutableJWKSet<SecurityContext>

    private val jwtDecoder by lazy { OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource) }

    @BeforeEach
    fun seedAdmin() {
        // 幂等：AdminSeeder 启动时已创建 tenant 1 的 admin；此处兜底，避免依赖启动顺序
        if (userMapper.findByTenantIdAndUsername(1L, "admin") == null) {
            userMapper.insert(
                SysUserRecord(
                    tenantId = 1L,
                    username = "admin",
                    password = passwordEncoder.encode("admin123")!!,
                    nickname = "管理员",
                    status = 1,
                )
            )
        }
    }

    /** 手动签发 JWT：issuer 必须与 AuthorizationServerSettings 一致（http://localhost:8080）。 */
    private fun token(perms: List<String>): String {
        val encoder = NimbusJwtEncoder(jwkSource)
        val claims = JwtClaimsSet.builder()
            .issuer("http://localhost:8080")
            .subject("admin")
            .claim("uid", 1L)
            .claim("tenant_id", 1L)
            .claim("perms", perms)
            .build()
        return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    /** 表单登录并返回已认证会话（复用 OAuth2LoginFlowTest 流程）。 */
    private fun loginSession(): MockHttpSession {
        val loginResult = mockMvc.perform(
            post("/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin123")
        )
            .andExpect(status().is3xxRedirection())
            .andReturn()
        return loginResult.request.session as MockHttpSession
    }

    /** 完整授权码流程（PKCE）换取 access_token。 */
    private fun authCodeToken(): String {
        val session = loginSession()
        val verifier = "method-security-verifier-0123456789012345678901234567890"
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val authorizeUrl = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("client_id", "zhijin-console")
            .queryParam("response_type", "code")
            .queryParam("scope", "openid")
            .queryParam("redirect_uri", "http://localhost:5173/callback")
            .queryParam("state", "method-security")
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256")
            .build().encode().toUriString()
        val authorizeResult = mockMvc.perform(get(authorizeUrl).session(session))
            .andExpect(status().is3xxRedirection())
            .andReturn()
        val location = authorizeResult.response.getHeader("Location")
        val code = UriComponentsBuilder.fromUriString(location!!).build().queryParams.getFirst("code")
        val tokenResult = mockMvc.perform(
            post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code!!)
                .param("redirect_uri", "http://localhost:5173/callback")
                .param("client_id", "zhijin-console")
                .param("code_verifier", verifier)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty)
            .andReturn()
        return JsonPath.read(tokenResult.response.contentAsString, "$.access_token")
    }

    @Test
    fun `授权码流程签发JWT携带非空perms`() {
        val accessToken = authCodeToken()
        val jwt = jwtDecoder.decode(accessToken)
        // 关键断言：tokenCustomizer 在无租户上下文（授权服务器链路）下经 getPerms 查询
        // 用户角色∪组织角色权限点，admin 角色被 Seed 授予全 10 权限，perms 不应为空
        val perms = jwt.getClaim<List<String>>("perms")
        assertTrue(perms.isNullOrEmpty().not(), "JWT perms claim 不应为空")
        assertTrue(perms!!.contains("app:create"), "admin 应具备 app:create 权限")
        assertEquals(10, perms.size, "admin 应具备全部 10 个权限点")
    }

    @Test
    fun `带usageView权限可访问用量汇总`() {
        // @PreAuthorize("hasAuthority('usage:view')")：JwtAuthenticationConverter 从 perms claim 无前缀解析
        mockMvc.perform(get("/api/usage/summary").header("Authorization", "Bearer ${token(listOf("usage:view"))}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
    }

    @Test
    fun `缺usageView权限访问用量汇总返回403`() {
        // 仅 app:create 而无 usage:view：方法级鉴权应拒绝（403）
        mockMvc.perform(get("/api/usage/summary").header("Authorization", "Bearer ${token(listOf("app:create"))}"))
            .andExpect(status().isForbidden())
    }

    @Test
    fun `带appView权限可访问应用列表`() {
        // @PreAuthorize("hasAuthority('app:view')")：AppController.list 需 app:view
        mockMvc.perform(get("/api/apps").header("Authorization", "Bearer ${token(listOf("app:view"))}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
    }

    @Test
    fun `缺appView权限访问应用列表返回403`() {
        // 仅 usage:view 而无 app:view：访问 /api/apps 应被拒（403）
        mockMvc.perform(get("/api/apps").header("Authorization", "Bearer ${token(listOf("usage:view"))}"))
            .andExpect(status().isForbidden())
    }
}
