package com.zhijin.billingaudit.interfaces.dto

/** 分页结果响应 DTO（管理端查询返回结构，对应 domain 层 PageResult）。 */
data class PageResultResponse<T>(
    val items: List<T>,
    val total: Long,
)
