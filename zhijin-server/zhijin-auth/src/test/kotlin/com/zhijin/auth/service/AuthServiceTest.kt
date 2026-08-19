package com.zhijin.auth.service

import com.zhijin.auth.config.JwtConfig
import com.zhijin.auth.dto.LoginRequest
import com.zhijin.auth.entity.SysUser
import com.zhijin.auth.repository.SysUserRepository
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthServiceTest {

    private val jwtConfig = JwtConfig()
    private val passwordEncoder = BCryptPasswordEncoder()

    private val repo = object : SysUserRepository {
        override fun findByUsername(username: String): SysUser? =
            if (username == "admin") SysUser(
                id = 1L, tenantId = 1L, username = "admin",
                password = passwordEncoder.encode("admin123")!!, nickname = "管理员", status = 1,
            ) else null
    }

    private val authService = AuthService(repo, jwtConfig, passwordEncoder)

    @Test
    fun `正确密码签发带租户claims的JWT`() {
        val resp = authService.login(LoginRequest("admin", "admin123"))
        assertNotNull(resp.accessToken)
        assert(resp.tenantId == 1L)
    }

    @Test
    fun `错误密码抛业务异常`() {
        assertThrows(BizException::class.java) {
            authService.login(LoginRequest("admin", "wrong"))
        }
    }

    @Test
    fun `refresh用有效token重签`() {
        val first = authService.login(LoginRequest("admin", "admin123"))
        val refreshed = authService.refresh(first.accessToken)
        assertNotNull(refreshed.accessToken)
    }
}
