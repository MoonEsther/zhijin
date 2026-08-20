package com.zhijin.app.auth

import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // JwtConfig 已由 jwtEncoder() 改为 jwkSource() 单一 Bean，测试直接注入 JWKSource 构造 NimbusJwtEncoder 签名
    @Autowired
    lateinit var jwkSource: ImmutableJWKSet<SecurityContext>

    /**
     * 手动签发有效 JWT：issuer 必须与授权服务器 AuthorizationServerSettings 的 issuer 一致
     * （AUTH_ISSUER 默认 localhost:8080 → http://localhost:8080），否则资源服务器解码校验不通过。
     */
    private fun token(tenantId: Long = 1L): String {
        val encoder = NimbusJwtEncoder(jwkSource)
        val claims = JwtClaimsSet.builder()
            .issuer("http://localhost:8080")
            .subject("admin")
            .claim("uid", 1L)
            .claim("tenant_id", tenantId)
            .claim("roles", emptyList<String>())
            .build()
        return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    @Test
    fun `带有效JWT访问validate返回身份`() {
        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.tenantId").value(1))
    }
}
