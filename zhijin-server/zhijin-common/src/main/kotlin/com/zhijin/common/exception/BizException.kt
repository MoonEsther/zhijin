package com.zhijin.common.exception

import com.zhijin.common.web.ResultCode

/** 业务异常：携带响应码与可读消息，由全局异常处理统一转成 Result。 */
class BizException(
    val code: ResultCode,
    override val message: String,
) : RuntimeException(message)
