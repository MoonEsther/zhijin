package com.zhijin.app.auth

import com.jayway.jsonpath.JsonPath
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.zhijin.auth.infrastructure.persistence.SysUserRecord
import com.zhijin.auth.repository.SysUserMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
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
 * OAuth2 登录流集成测试（B2 重构）：
 * 1) 表单登录：验证 Spring Security 表单登录 + UserDetailsServiceImpl + BCrypt 用户校验（链3）
 * 2) 客户端凭证模式：验证授权服务器 /oauth2/token 签发 JWT，且 iss 与 AUTH_ISSUER 默认值一致
 * 3) 完整授权码流程（PKCE）：表单登录 → /oauth2/authorize 签发 code → /oauth2/token 换取 access_token
 *
 * 说明：admin/admin123 正常由 AdminSeeder 在启动时幂等创建，这里用 SysUserMapper 兜底确保存在。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginFlowTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userMapper: SysUserMapper

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    // 与授权服务器共用同一 JWKSource，可解码校验其签发的 JWT
    @Autowired
    lateinit var jwkSource: ImmutableJWKSet<SecurityContext>

    private val jwtDecoder: JwtDecoder by lazy {
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)
    }

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

    /** 复用：表单登录并返回已认证会话。 */
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

    @Test
    fun `表单登录成功返回302`() {
        // 链3 表单登录：UserDetailsServiceImpl 按用户名查库 + BCrypt 校验密码
        mockMvc.perform(
            post("/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin123")
        )
            .andExpect(status().is3xxRedirection())
    }

    @Test
    fun `客户端凭证模式签发JWT且issuer匹配`() {
        val result = mockMvc.perform(
            post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", "openid")
                .with(httpBasic("zhijin-server", "zhijin-secret"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty)
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn()

        val accessToken = JsonPath.read<String>(result.response.contentAsString, "$.access_token")
        val jwt = jwtDecoder.decode(accessToken)
        // AUTH_ISSUER 默认 localhost:8080 → AuthorizationServerSettings.issuer = http://localhost:8080
        assertEquals("http://localhost:8080", jwt.issuer.toString())
    }

    @Test
    fun `授权码流程表单登录后签发JWT`() {
        // 1) 表单登录建立认证会话（PKCE 前置的用户登录步骤）
        val session = loginSession()

        // 2) 请求授权端点：console 客户端 requireProofKey=true，必须携带 PKCE code_challenge。
        //    注意：OAuth2 授权端点按原始 query string 解析参数（OAuth2EndpointUtils.getQueryParameters），
        //    MockMvc 的 .param() 不会写入 query string，因此必须把参数直接拼进 URL。
        val verifier = "test-code-verifier-01234567890123456789012345678901234567890"
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val authorizeUrl = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("client_id", "zhijin-console")
            .queryParam("response_type", "code")
            .queryParam("scope", "openid")
            .queryParam("redirect_uri", "http://localhost:5173/callback")
            .queryParam("state", "test-state")
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256")
            .build()
            .encode()
            .toUriString()
        val authorizeResult = mockMvc.perform(
            get(authorizeUrl).session(session)
        )
            .andExpect(status().is3xxRedirection())
            .andReturn()
        val location = authorizeResult.response.getHeader("Location")
        assertNotNull(location)
        val code = UriComponentsBuilder.fromUriString(location!!).build().queryParams.getFirst("code")
        assertNotNull(code)

        // 3) 用授权码 + code_verifier 换取 access_token
        val tokenResult = mockMvc.perform(
            post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code!!)
                .param("redirect_uri", "http://localhost:5173/callback")
                .param("client_id", "zhijin-console")
                .param("code_verifier", verifier)
                .with(httpBasic("zhijin-console", "console-secret"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty)
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn()

        val accessToken = JsonPath.read<String>(tokenResult.response.contentAsString, "$.access_token")
        val jwt = jwtDecoder.decode(accessToken)
        assertEquals("http://localhost:8080", jwt.issuer.toString())
        assertEquals("admin", jwt.subject)
    }
}
