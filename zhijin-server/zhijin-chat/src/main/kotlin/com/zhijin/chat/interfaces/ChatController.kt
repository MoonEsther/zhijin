package com.zhijin.chat.interfaces

import com.zhijin.chat.application.ChatApplicationService
import com.zhijin.chat.interfaces.dto.ChatRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 开放聊天 API（/v1/chat），API Key 鉴权（薄：只做 DTO 传递 + 调应用服务）。 */
@RestController
@RequestMapping("/v1")
class ChatController(private val chatService: ChatApplicationService) {

    @PostMapping(value = ["/chat"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody req: ChatRequest): SseEmitter {
        val emitter = SseEmitter()
        chatService.chatAsync(req, emitter)
        return emitter
    }
}
