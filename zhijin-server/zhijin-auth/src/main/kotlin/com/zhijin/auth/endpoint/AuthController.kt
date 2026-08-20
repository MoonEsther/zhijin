package com.zhijin.auth.endpoint

import com.zhijin.auth.dto.ValidateResponse
import com.zhijin.common.web.Result
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 认证契约端点：validate / logout（login/refresh 已由 OAuth2 授权服务器 /oauth2/token 取代，AuthService 随 B2 重构移除）。 */
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
