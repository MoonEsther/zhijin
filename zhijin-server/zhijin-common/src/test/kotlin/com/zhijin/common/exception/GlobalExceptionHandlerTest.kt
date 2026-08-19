package com.zhijin.common.exception

import com.zhijin.common.web.Result
import com.zhijin.common.web.ResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `业务异常返回错误码与消息`() {
        val resp: ResponseEntity<Result<Any>> =
            handler.handleBiz(BizException(ResultCode.TENANT_MISSING, "缺少租户"))
        assertEquals(400, resp.statusCode.value())
        assertEquals(ResultCode.TENANT_MISSING.code, resp.body?.code)
        assertEquals("缺少租户", resp.body?.message)
    }
}
