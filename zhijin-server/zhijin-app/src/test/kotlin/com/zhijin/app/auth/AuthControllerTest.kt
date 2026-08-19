package com.zhijin.app.auth

import com.zhijin.auth.config.JwtConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
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

    @Autowired
    lateinit var jwtConfig: JwtConfig

    private fun token(tenantId: Long = 1L): String {
        val claims = JwtClaimsSet.builder()
            .subject("admin")
            .claim("uid", 1L)
            .claim("tenant_id", tenantId)
            .claim("roles", emptyList<String>())
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
