package com.zhijin.orchestrator.domain.tool

/** 工具抽象：可执行单元。 */
interface Tool {
    suspend fun execute(args: Map<String, Any?>): Any?
}

/** 工具注册表：V1 提供 EchoTool（测试），HttpToolExecutor（B4 后续/工具节点扩展）。 */
class ToolRegistry {
    private val map = mutableMapOf<String, Tool>()

    /** 注册工具：按名称映射到具体实现。 */
    fun register(name: String, tool: Tool) {
        map[name] = tool
    }

    /** 按名称取工具，未注册抛出异常以提示配置错误。 */
    fun get(name: String): Tool = map[name] ?: throw IllegalStateException("未注册工具: $name")
}

/** 测试用 Echo 工具：返回 echo:{msg}。 */
class EchoTool : Tool {
    override suspend fun execute(args: Map<String, Any?>): Any? = "echo:${args["msg"]}"
}
