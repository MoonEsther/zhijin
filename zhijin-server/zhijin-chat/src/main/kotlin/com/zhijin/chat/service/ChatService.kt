package com.zhijin.chat.service

import com.zhijin.chat.dto.ChatRequest
import com.zhijin.chat.workflow.DefaultWorkflow
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.ModelComponent
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.nodes.EndNode
import com.zhijin.orchestrator.nodes.LlmNode
import com.zhijin.orchestrator.nodes.StartNode
import com.zhijin.orchestrator.scheduler.WorkflowRunner
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 聊天服务：会话/消息持久化 + 默认工作流执行 + SSE 流式返回。
 *
 * 执行流程：
 * 1) 在请求线程解析租户（由 API Key 鉴权过滤器写入上下文）与应用（请求体或过滤器属性）；
 * 2) 派生工作线程执行：因租户上下文是 ThreadLocal，需在子线程手动重放租户；
 * 3) 创建/复用会话、持久化用户消息 → 跑默认工作流(start→llm→end) → 持久化助手回复 → SSE 推送。
 */
@Service
class ChatService(
    private val sessionService: SessionService,
    private val modelComponent: ModelComponent,
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    /** 请求属性 key：由 ApiKeyAuthFilter 写入解析出的应用 ID。 */
    private companion object {
        const val APP_ID_ATTR = "zhijin.appId"
    }

    /** 懒加载工作流执行器：注册默认 LLM-only 工作流所需节点。 */
    private val runner: WorkflowRunner by lazy {
        WorkflowRunner(
            NodeExecutorRegistry().apply {
                register(NodeType.START) { StartNode() }
                register(NodeType.LLM) { LlmNode(modelComponent) }
                register(NodeType.END) { EndNode() }
            }
        )
    }

    /** 异步聊天：立即返回 SSE 流，后台线程执行工作流。 */
    fun chatAsync(req: ChatRequest, emitter: SseEmitter) {
        // 请求线程解析租户与应用（必须在派生子线程前捕获）
        val tenantId = TenantContextHolder.getRequiredTenantId()
        val appId = req.appId ?: resolveAppId()
        Thread {
            try {
                // 子线程无租户上下文，手动重放以保证 DB 写入走租户隔离
                TenantContextHolder.setTenantId(tenantId)
                val session = sessionService.createSession(tenantId, appId)
                sessionService.appendMessage(tenantId, session, "user", req.message)
                log.info("对话会话创建: tenantId={}, appId={}, sessionId={}", tenantId, appId, session.id)

                val schema = DefaultWorkflow.build(req.message)
                val result = runBlocking { runner.execute(schema, VariableStore()) }
                val reply = result.finalOutput?.toString() ?: ""
                sessionService.appendMessage(tenantId, session, "assistant", reply)

                emitter.send(SseEmitter.event().name("message").data(reply))
                emitter.complete()
            } catch (e: Exception) {
                log.error("聊天处理失败: tenantId={}, appId={}", tenantId, appId, e)
                emitter.completeWithError(e)
            } finally {
                TenantContextHolder.clear()
            }
        }.start()
    }

    /** 应用 ID 回退：优先请求体显式传 appId，否则取 API Key 鉴权过滤器写入的请求属性。 */
    private fun resolveAppId(): Long {
        val attrs = RequestContextHolder.getRequestAttributes()
        return (attrs as? ServletRequestAttributes)?.request?.getAttribute(APP_ID_ATTR) as? Long ?: 0L
    }
}
