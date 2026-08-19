package com.zhijin.common.exception

import com.zhijin.common.web.Result
import com.zhijin.common.web.ResultCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 全局异常处理：业务异常、参数校验、兜底异常统一转 Result 返回。 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BizException::class)
    fun handleBiz(e: BizException): ResponseEntity<Result<Any>> {
        val status = when (e.code) {
            ResultCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ResultCode.FORBIDDEN -> HttpStatus.FORBIDDEN
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(Result.error(e.code, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<Result<Any>> {
        val first = e.bindingResult.fieldErrors.firstOrNull()
        val msg = first?.let { "${it.field}: ${it.defaultMessage}" } ?: "参数校验失败"
        return ResponseEntity.badRequest().body(Result.error(ResultCode.BAD_REQUEST, msg))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ResponseEntity<Result<Any>> {
        log.error("未捕获异常", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(ResultCode.INTERNAL_ERROR))
    }
}
