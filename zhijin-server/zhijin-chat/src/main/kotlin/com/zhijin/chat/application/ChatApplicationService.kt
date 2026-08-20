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
import com.zhijin.orchestrator.domain.Usage
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

                // 模型配置（解决 C6）：V1 从环境变量取（默认 qwen/qwen-max），providerKeyId 后续从 AppModelConfig 取
                val provider = System.getenv("MODEL_PROVIDER") ?: "qwen"
                val model = System.getenv("MODEL_NAME") ?: "qwen-max"
                val providerKeyId: Long? = null  // 后续从 AppModelConfig 取

                // 执行工作流：VariableStore 作为局部变量传入（解决 N2'），
                // LlmNode 调 ModelComponent 后把 usage 透传到 store（key=llm.usage），供执行后取回
                val store = VariableStore()
                val startedAt = System.currentTimeMillis()
                val result = runBlocking {
                    runner.execute(DefaultWorkflow.build(req.message, provider, model, providerKeyId), store)
                }
                val reply = result.finalOutput?.toString() ?: ""
                sessionRepository.appendMessage(session.appendMessage("assistant", reply))

                // 记录用量（解决 C2/N2'）：WorkflowResult 无 outputs 字段，
                // 从 store 取 LlmNode 写入的 usage 回填真实 token 计数；取不到时按 0 处理
                val usage = store.readNodeOutput("llm", "usage") as? Usage
                usageRecorder.record(
                    UsageRecord(
                        tenantId = tenantId,
                        appId = appId,
                        sessionId = session.id,
                        model = model,
                        promptTokens = usage?.promptTokens ?: 0,
                        completionTokens = usage?.completionTokens ?: 0,
                        totalTokens = usage?.totalTokens ?: 0,
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
