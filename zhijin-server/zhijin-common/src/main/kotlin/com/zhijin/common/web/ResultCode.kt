package com.zhijin.common.web

/** 统一响应码：0 成功，1xxx 通用，2xxx 租户，3xxx 认证。 */
enum class ResultCode(val code: Int, val message: String) {
    SUCCESS(0, "成功"),
    BAD_REQUEST(1000, "请求参数错误"),
    INTERNAL_ERROR(1001, "系统内部错误"),
    TENANT_MISSING(2000, "缺少租户上下文"),
    TENANT_NOT_FOUND(2001, "租户不存在"),
    UNAUTHORIZED(3000, "未认证"),
    FORBIDDEN(3001, "无权限"),
}
