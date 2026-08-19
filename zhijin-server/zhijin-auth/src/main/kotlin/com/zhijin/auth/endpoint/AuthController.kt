package com.zhijin.auth.endpoint

import com.zhijin.auth.dto.LoginRequest
import com.zhijin.auth.dto.RefreshRequest
import com.zhijin.auth.dto.TokenResponse
import com.zhijin.auth.service.AuthService
import com.zhijin.common.web.Result
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 认证契约端点：login / refresh。 */
@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): Result<TokenResponse> =
        Result.success(authService.login(req))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest): Result<TokenResponse> =
        Result.success(authService.refresh(req.refreshToken))
}
