package com.zhijin.orchestrator.domain.executor

import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.domain.NodeSchema

/** 节点执行上下文：变量区 + 会话上下文。 */
class NodeContext(
    val variableStore: VariableStore,
    val sessionVars: Map<String, Any?> = emptyMap(),
)

/** 节点执行结果：输出字段映射（key → value）。 */
data class NodeResult(val outputs: Map<String, Any?>)

/** 节点执行器能力接口（多态，对齐决策 19）：普通节点实现 invoke。 */
interface NodeExecutor {
    /** 非流 → 非流（V1 所有节点实现此方法）。 */
    suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult
}
