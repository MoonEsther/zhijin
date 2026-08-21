package com.zhijin.auth.application

import com.zhijin.auth.interfaces.dto.ValidateResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * 认证应用服务：validate / logout 用例。
 *
 * 说明：登录/签发令牌已由 Spring Security OAuth2 授权服务器（框架层）承担，
 * 本服务保持薄；validate 从已认证 JWT 的 claims 组装身份响应，logout 无状态（无会话可销毁）。
 */
@Service
class AuthApplicationService {

    fun validate(authentication: Authentication): ValidateResponse {
        val jwt = authentication as JwtAuthenticationToken
        val claims = jwt.token.claims
        return ValidateResponse(
            username = claims["sub"] as? String ?: "",
            userId = (claims["uid"] as? Number)?.toLong(),
            tenantId = (claims["tenant_id"] as? Number)?.toLong(),
            roles = (claims["roles"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            // 权限点来自 tokenCustomizer 签发 JWT 时写入的 perms claim（见 SecurityConfig），
            // 解析失败/缺失时兜底空列表，保证老 token 仍可完成 validate
            perms = (claims["perms"] as? List<*>)?.map { it.toString() } ?: emptyList(),
        )
    }

    fun logout() {
        // 无状态 JWT：资源服务器不维护会话，logout 无需任何副作用
    }
}
