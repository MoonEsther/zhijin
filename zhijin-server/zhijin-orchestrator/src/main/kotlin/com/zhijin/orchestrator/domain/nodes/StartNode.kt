package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.executor.NodeContext
import com.zhijin.orchestrator.domain.executor.NodeExecutor
import com.zhijin.orchestrator.domain.executor.NodeResult
import com.zhijin.orchestrator.domain.NodeSchema

/** 开始节点：无输入，可选输出工作流入参。 */
class StartNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult = NodeResult(emptyMap())
}
