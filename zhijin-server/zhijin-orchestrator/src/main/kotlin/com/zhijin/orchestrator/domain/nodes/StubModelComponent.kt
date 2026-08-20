package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.ModelComponent

/** V1 测试用桩：返回固定文本。B5/计划 C 用真实 HTTP 实现替换。 */
class StubModelComponent(private val reply: String = "模型返回") : ModelComponent {
    override suspend fun complete(prompt: String, modelName: String): String = reply
}
