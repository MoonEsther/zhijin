package com.zhijin.chat.config

import com.zhijin.aiclient.AiClient
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.ModelKeyResolver
import com.zhijin.orchestrator.infrastructure.model.HttpModelComponent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 聊天运行时默认装配：
 * 提供 ModelComponent 抽象的唯一实现（真实 HTTP 模型组件，经 AiClient 调 Python 模型网关）。
 * ModelKeyResolver 由 zhijin-app 提供适配 Bean（app 有 ModelProviderKey 表访问权限，解决 C1 依赖方向）。
 * 各应用如后续需要按 appId 切换模型/供应商，在此处替换为路由实现即可（组件抽象扩展点）。
 */
@Configuration
class ChatRuntimeConfig(private val keyResolver: ModelKeyResolver) {

    @Bean
    fun modelComponent(): ModelComponent = HttpModelComponent(AiClient(), keyResolver)
}
