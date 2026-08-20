package com.zhijin.chat.dto

/** 开放聊天请求体（/v1/chat）。appId 可省略：未传时回退到 API Key 解析出的应用。 */
data class ChatRequest(
    val appId: Long? = null,
    val sessionId: Long? = null,
    val message: String = "",
)
