package com.zhijin.common.web

import com.zhijin.common.web.ResultCode

/**
 * 统一响应体：所有对外接口返回该结构。
 * code=0 表示成功；code 非 0 表示业务错误（见 ResultCode）。
 */
data class Result<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> success(data: T? = null): Result<T> =
            Result(ResultCode.SUCCESS.code, ResultCode.SUCCESS.message, data)

        fun <T> error(code: ResultCode, message: String? = null): Result<T> =
            Result(code.code, message ?: code.message)
    }
}
