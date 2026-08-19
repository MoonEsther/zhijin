package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema

/** 开始节点：无输入，可选输出工作流入参。 */
class StartNode : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult = NodeResult(emptyMap())
}
