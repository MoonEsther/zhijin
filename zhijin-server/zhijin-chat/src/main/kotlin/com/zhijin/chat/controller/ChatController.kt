package com.zhijin.chat.controller

import com.zhijin.chat.dto.ChatRequest
import com.zhijin.chat.service.ChatService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 开放聊天 API（/v1/chat），API Key 鉴权。 */
@RestController
@RequestMapping("/v1")
class ChatController(private val chatService: ChatService) {

    @PostMapping(value = ["/chat"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody req: ChatRequest): SseEmitter {
        val emitter = SseEmitter()
        chatService.chatAsync(req, emitter)
        return emitter
    }
}
