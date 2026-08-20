package com.zhijin.orchestrator.domain

/** 模型组件抽象（§3.1 关键原则）：LLM 节点依赖抽象，不依赖具体实现。 */
interface ModelComponent {
    suspend fun complete(prompt: String, modelName: String = "default"): String
}
