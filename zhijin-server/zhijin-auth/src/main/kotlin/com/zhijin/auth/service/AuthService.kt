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

    /** V1 简化版刷新：校验旧 JWT 有效后按用户名重签。 */
    fun refresh(refreshToken: String): TokenResponse {
        val username = jwtConfig.jwtDecoder().decode(refreshToken).subject
            ?: throw BizException(ResultCode.UNAUTHORIZED, "refreshToken 无效")
        val user = userRepository.findByUsername(username)
            ?: throw BizException(ResultCode.UNAUTHORIZED, "用户不存在")
        return issueToken(user)
    }

    /** 签发 JWT：sub=用户名, uid, tenant_id, roles(暂空, 后续接 RBAC 查角色)。 */
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
