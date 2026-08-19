package com.zhijin.orchestrator.nodes

import com.zhijin.orchestrator.executor.NodeContext
import com.zhijin.orchestrator.executor.NodeExecutor
import com.zhijin.orchestrator.executor.NodeResult
import com.zhijin.orchestrator.model.NodeSchema
import com.zhijin.orchestrator.tool.ToolRegistry

/** 工具节点：按 configs.tool 取工具，输入字段作参数，输出写回。 */
class ToolNode(private val toolRegistry: ToolRegistry) : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        // 校验并解析工具名（缺失时视为配置错误，直接抛异常）
        val toolName = node.configs["tool"]?.toString()
            ?: throw IllegalStateException("工具节点缺少 tool 配置")
        // 输入字段 → 工具参数：ref 从变量区解析，literal 直接取值
        val args = node.inputs.associate { f -> f.key to ctx.variableStore.resolveRef(f.source) }
        // 执行工具并取回结果
        val result = toolRegistry.get(toolName).execute(args)
        // 输出字段：取第一个输出定义，缺省用 "result"
        val outKey = node.outputs.firstOrNull()?.key ?: "result"
        return NodeResult(mapOf(outKey to result))
    }
}
