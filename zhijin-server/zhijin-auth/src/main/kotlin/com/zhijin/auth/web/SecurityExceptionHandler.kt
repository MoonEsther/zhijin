package com.zhijin.auth.web

import com.zhijin.common.web.Result
import com.zhijin.common.web.ResultCode
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 安全异常处理器：把方法级鉴权（@PreAuthorize）抛出的 AccessDeniedException 转成 403，
 * AuthenticationException 转成 401。
 *
 * 为什么必须单独处理：zhijin-common 的 GlobalExceptionHandler 有兜底 @ExceptionHandler(Exception)，
 * 会把 @PreAuthorize 抛出的 AccessDeniedException 吞成 500 —— 越权本应是 403，却返回内部错误。
 * 两个 @RestControllerAdvice 同优先级时，Spring 按注册顺序取"第一个能处理该异常的 advice"，
 * 若 GlobalExceptionHandler 在前，其兜底 Exception 处理器会抢先命中；故本 advice 用
 * @Order(HIGHEST_PRECEDENCE) 提升优先级，让更具体的安全异常处理器先接管。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<Result<Any>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(ResultCode.FORBIDDEN, "无权限"))

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(e: AuthenticationException): ResponseEntity<Result<Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(ResultCode.UNAUTHORIZED, "未认证"))
}
