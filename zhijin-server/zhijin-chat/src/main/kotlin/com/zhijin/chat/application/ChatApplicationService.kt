package com.zhijin.chat.application

import com.zhijin.billingaudit.domain.usage.UsageRecord
import com.zhijin.billingaudit.domain.usage.UsageRecorder
import com.zhijin.chat.domain.session.ChatSession
import com.zhijin.chat.domain.session.SessionRepository
import com.zhijin.chat.interfaces.dto.ChatRequest
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.domain.VariableStore
import com.zhijin.orchestrator.domain.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.NodeType
import com.zhijin.orchestrator.domain.nodes.EndNode
import com.zhijin.orchestrator.domain.nodes.LlmNode
import com.zhijin.orchestrator.domain.nodes.StartNode
import com.zhijin.orchestrator.domain.scheduler.WorkflowRunner
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 聊天应用服务：chat 用例（建会话 → 追加用户消息 → 驱动默认工作流 → 追加助手回复 → SSE 流式返回）。
 *
 * 由原 service/ChatService 迁移而来，逻辑保持一致；依赖倒置后只面向两个端口：
 * - 领域仓储 [SessionRepository]：会话/消息持久化（实现见 infrastructure.persistence）；
 * - [ModelComponent]：模型端口（抽象在 orchestrator 的 model 包，LLM 节点依赖它）。
 * 工作流执行引擎（WorkflowRunner + 节点注册）为编排侧领域服务，在此编排组装。
 */
@Service
class ChatApplicationService(
    private val sessionRepository: SessionRepository,
    private val modelComponent: ModelComponent,
    /** 用量记录端口（依赖倒置注入，默认 no-op 保证无适配器时流程不受影响）。 */
    private val usageRecorder: UsageRecorder = UsageRecorder {},
) {
    private val log = LoggerFactory.getLogger(ChatApplicationService::class.java)

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
                val session = sessionRepository.create(
                    ChatSession(id = null, tenantId = tenantId, appId = appId, title = "")
                )
                sessionRepository.appendMessage(session.appendMessage("user", req.message))
                log.info("对话会话创建: tenantId={}, appId={}, sessionId={}", tenantId, appId, session.id)

                val schema = DefaultWorkflow.build(req.message)
                val startedAt = System.currentTimeMillis()
                val result = runBlocking { runner.execute(schema, VariableStore()) }
                val reply = result.finalOutput?.toString() ?: ""
                sessionRepository.appendMessage(session.appendMessage("assistant", reply))

                // 记录用量（V1：模型调用一次 + 工作流执行延迟；token 计数待计划 C 填充）
                usageRecorder.record(
                    UsageRecord(
                        tenantId = tenantId,
                        appId = appId,
                        sessionId = session.id,
                        model = "default",
                        latencyMs = (System.currentTimeMillis() - startedAt).toInt(),
                    )
                )

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
