package com.zhijin.auth.interfaces

import com.zhijin.auth.application.AuthApplicationService
import com.zhijin.auth.interfaces.dto.ValidateResponse
import com.zhijin.common.web.Result
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 认证契约端点（薄：只做 DTO 传递 + 调应用服务）：validate / logout。
 * login/refresh 已由 OAuth2 授权服务器 /oauth2/token 取代，AuthService 随 B2 重构移除。
 */
@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthApplicationService) {

    @GetMapping("/validate")
    fun validate(authentication: Authentication): Result<ValidateResponse> =
        Result.success(authService.validate(authentication))

    @PostMapping("/logout")
    fun logout(): Result<Unit> = Result.success()
}
