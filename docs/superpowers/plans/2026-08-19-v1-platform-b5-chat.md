# V1 平台服务 · B5 会话运行时 + AI-client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现会话运行时与开放聊天 API：`zhijin-ai` 最小模型网关桩、`zhijin-ai-client`（调 Python 封装）、`HttpModelComponent`（真实模型组件）、会话/消息数据模型、API Key 鉴权 + `/v1/chat` SSE 端点、ChatService 驱动 B4 编排引擎。

**Architecture:** 对齐 §10 数据流 B：客户系统带 API Key 调 `/v1/chat` → API Key 过滤器识别租户+应用 → 取/建会话 → 加载应用（V1 用默认 LLM-only 工作流，B4 WorkflowRunner 执行）→ LLM 节点经 `HttpModelComponent` → `zhijin-ai-client` → Python `/v1/chat/completions` → 结果存消息 → SSE 推给客户。`zhijin-ai-client` 用 Spring `RestClient`（阻塞）+ `text/event-stream` 解析（V1 一次事件返回完整回复）。

**Tech Stack:** Spring Boot 4 · RestClient（`spring-web`）· MyBatis-Plus（复用）· Flyway V4 · Python FastAPI（复用 zhijin-ai）· H2/MockWebServer（测试）

**设计依据:** `2026-08-17-agent-platform-design.md` §6（chat/ai-client）、§10 数据流 B、§13 决策 21（Key 加密）、§12.1 七大原则。

---

## 关键决策

- **模型网关桩先行**：`zhijin-ai` 先加最小 `POST /v1/chat/completions`（OpenAI 兼容格式 + SSE），返回固定文本（`hello`）。让 Kotlin→Python 链路真实跑通；计划 C 用真实供应商替换。
- **AiClient**：Kotlin `RestClient` 调 Python，`POST /v1/chat/completions`，body 为 `{model, messages:[{role, content}]}`，解析 `choices[0].message.content`。
- **HttpModelComponent**：实现 B4 `ModelComponent` 接口（`complete(prompt, modelName)` → AiClient 调用）。
- **会话/消息**：`chat_session` + `chat_message` 表（Flyway V4），会话服务 CRUD + 追加消息。
- **开放 API 鉴权**：`ApiKeyAuthFilter` 读请求头 `X-API-Key`（或 `Authorization: Bearer ak_...`），用 B3 `AppApiKeyService.verify(tenantId, appId, key)` 校验 → 设置租户上下文 + 请求属性 appId。注册到 `/v1/**`。
- **V1 聊天流程**：无画布 → 用程序化构建的默认 LLM-only 工作流（start → llm(prompt=用户消息) → end）跑 `WorkflowRunner`；应用已发布版本若带 workflow_dsl 则解析运行（预留，B4+画布后启用）。
- **SSE**：`/v1/chat` 返回 `text/event-stream`，V1 单事件 `data: {reply}`（引擎非流式；真 token 流式 V2 用 stream 能力执行器）。
- **无状态**：聊天进程内不存状态，会话历史存 `chat_message` 表（每次执行注入 prompt 上下文）。

---

## 文件结构

```
zhijin-ai/app/              ← 新增 routes/chat.py（模型网关桩）
zhijin-server/
├── zhijin-ai-client/
│   ├── pom.xml             ← spring-web(RestClient) 依赖
│   └── src/main/kotlin/com/zhijin/aiclient/AiClient.kt
├── zhijin-chat/
│   ├── src/main/kotlin/com/zhijin/chat/
│   │   ├── entity/ChatSession.kt / ChatMessage.kt
│   │   ├── mapper/ChatSessionMapper.kt / ChatMessageMapper.kt
│   │   ├── service/ChatService.kt / SessionService.kt
│   │   ├── web/ApiKeyAuthFilter.kt / ChatController.kt
│   │   └── workflow/DefaultWorkflow.kt   ← 程序化构建 LLM-only 工作流
│   └── src/main/resources/db/migration/V4__chat_schema.sql
└── zhijin-app/
    └── src/main/kotlin/com/zhijin/app/config/ModelComponentConfig.kt  ← HttpModelComponent Bean
```

---

## Task 1: Python 模型网关桩（zhijin-ai）

**Files:**
- Create: `zhijin-ai/app/routes/chat.py`
- Modify: `zhijin-ai/app/main.py`（挂载路由）

- [ ] **Step 1: 实现桩**

`zhijin-ai/app/routes/chat.py`：
```python
"""模型网关最小桩（OpenAI 兼容）。计划 C 用真实供应商替换。"""
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field


router = APIRouter(prefix="/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = Field(default="default")
    messages: list[ChatMessage] = Field(default_factory=list)


@router.post("/chat/completions")
async def chat_completions(req: ChatRequest) -> JSONResponse:
    """OpenAI 兼容 /v1/chat/completions：V1 桩返回固定文本，计划 C 接真实模型。"""
    last = req.messages[-1].content if req.messages else ""
    return JSONResponse({
        "id": "chatcmpl-stub",
        "object": "chat.completion",
        "model": req.model,
        "choices": [{"index": 0, "message": {"role": "assistant", "content": f"echo: {last}"}}],
    })
```

- [ ] **Step 2: 挂载到 main.py**

在 `zhijin-ai/app/main.py` 加：
```python
from .routes import chat as chat_route

app.include_router(chat_route.router)
```

- [ ] **Step 3: 测试 + 运行验证**

Run（在 `zhijin-ai`）：
```bash
uv run pytest tests/test_health.py -q          # 既有测试仍绿
uv run uvicorn app.main:app --port 8001 &
curl -s -X POST http://localhost:8001/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-max","messages":[{"role":"user","content":"你好"}]}'
```
Expected: 返回 `{"choices":[{"message":{"content":"echo: 你好"}}]}`。测试后杀掉 uvicorn。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-ai/
git commit -m "feat(ai): 模型网关桩(OpenAI兼容 /v1/chat/completions)"
```
（末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`；不动 `zhijin.iml`。）

---

## Task 2: zhijin-ai-client（AiClient，TDD）

**Files:**
- Modify: `zhijin-server/zhijin-ai-client/pom.xml`
- Create: `zhijin-server/zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`
- Test: `zhijin-server/zhijin-ai-client/src/test/kotlin/com/zhijin/aiclient/AiClientTest.kt`

- [ ] **Step 1: pom 加依赖**

`zhijin-ai-client/pom.xml` 追加：
```xml
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 2: 写失败测试（MockWebServer 模拟 Python）**

`AiClientTest.kt`：
```kotlin
package com.zhijin.aiclient

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiClient

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        client = AiClient(server.url("/").toString().trimEnd('/'))
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `调用chat completions返回内容`() {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"1","object":"chat.completion","model":"qwen-max",
                   "choices":[{"index":0,"message":{"role":"assistant","content":"AI你好"}}]}"""
            ).addHeader("Content-Type", "application/json")
        )
        val content = client.complete("你好", "qwen-max")
        assertEquals("AI你好", content)
    }
}
```
> `okhttp3.mockwebserver` 测试依赖加 pom（`com.squareup.okhttp3:mockwebserver`，版本 `4.12.0`）。

- [ ] **Step 3: 运行确认失败**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-ai-client test -Dtest=AiClientTest`
Expected: 编译失败。

- [ ] **Step 4: 实现**

`zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`：
```kotlin
package com.zhijin.aiclient

import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * AI 服务（Python）客户端：调 OpenAI 兼容 /v1/chat/completions。
 * baseUrl 来自环境变量 AI_SERVICE_URL（默认 http://127.0.0.1:8001）。
 */
class AiClient(private val baseUrl: String = System.getenv("AI_SERVICE_URL") ?: "http://127.0.0.1:8001") {

    private val restClient = RestClient.builder().baseUrl(baseUrl).build()

    /** 调用模型，返回 assistant 内容。 */
    fun complete(prompt: String, model: String = "default"): String {
        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
        )
        val resp = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(CompletionResponse::class.java)
        return resp?.choices?.firstOrNull()?.message?.content ?: ""
    }

    /** OpenAI 兼容响应结构。 */
    data class CompletionResponse(val id: String?, val choices: List<Choice>?)
    data class Choice(val index: Int?, val message: Message?)
    data class Message(val role: String?, val content: String?)
}
```

- [ ] **Step 5: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-ai-client test -Dtest=AiClientTest`
Expected: `1 passed`。

- [ ] **Step 6: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-ai-client/
git commit -m "feat(ai-client): AiClient 调 Python OpenAI兼容接口"
```

---

## Task 3: HttpModelComponent（真实 ModelComponent，TDD）

**Files:**
- Create: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/nodes/HttpModelComponent.kt`
- Modify: `zhijin-server/zhijin-orchestrator/pom.xml`（依赖 zhijin-ai-client）
- Test: `zhijin-server/zhijin-orchestrator/src/test/kotlin/com/zhijin/orchestrator/nodes/HttpModelComponentTest.kt`

- [ ] **Step 1: pom 加 ai-client 依赖（test 可见）**

`zhijin-orchestrator/pom.xml` 追加：
```xml
    <dependency>
      <groupId>com.zhijin</groupId>
      <artifactId>zhijin-ai-client</artifactId>
    </dependency>
```

- [ ] **Step 2: 写失败测试**

`HttpModelComponentTest.kt`（用 mock AiClient——需 AiClient 可 mock；若 AiClient 是 final class，用 `open` 或接口化）：
```kotlin
package com.zhijin.orchestrator.nodes

import com.zhijin.aiclient.AiClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class HttpModelComponentTest {

    @Test
    fun `HttpModelComponent通过AiClient调用`() = runTest {
        val client = mock(AiClient::class.java)
        `when`(client.complete("prompt-x", "qwen-max")).thenReturn("AI回复")
        val component = HttpModelComponent(client)
        assertEquals("AI回复", component.complete("prompt-x", "qwen-max"))
    }
}
```
> 若 `AiClient` 为 final 无法 mock：把 `AiClient.complete` 改为 `open fun`，或将 AiClient 接口化。报告采用方式。

- [ ] **Step 3: 运行确认失败**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-orchestrator test -Dtest=HttpModelComponentTest`
Expected: 编译失败。

- [ ] **Step 4: 实现**

`zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/nodes/HttpModelComponent.kt`：
```kotlin
package com.zhijin.orchestrator.nodes

import com.zhijin.aiclient.AiClient
import com.zhijin.orchestrator.model.ModelComponent

/**
 * 真实模型组件：经 zhijin-ai-client 调 Python 模型网关。
 * 替换 StubModelComponent；计划 C 完善 Python 供应商后无需改此处。
 */
class HttpModelComponent(private val aiClient: AiClient) : ModelComponent {
    override suspend fun complete(prompt: String, modelName: String): String =
        aiClient.complete(prompt, modelName)
}
```

- [ ] **Step 5: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-orchestrator test -Dtest=HttpModelComponentTest`
Expected: `1 passed`。

- [ ] **Step 6: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-orchestrator/
git commit -m "feat(orchestrator): HttpModelComponent 真实模型组件"
```

---

## Task 4: 会话/消息数据模型（Flyway V4）

**Files:**
- Create: `zhijin-server/zhijin-chat/src/main/resources/db/migration/V4__chat_schema.sql`
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/entity/ChatSession.kt` / `ChatMessage.kt`
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/mapper/ChatSessionMapper.kt` / `ChatMessageMapper.kt`

- [ ] **Step 1: 建表 SQL**

`V4__chat_schema.sql`：
```sql
-- 会话与消息
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    app_id      BIGINT       NOT NULL,
    title       VARCHAR(128) NOT NULL DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_session_app ON chat_session (tenant_id, app_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,      -- user / assistant
    content     TEXT         NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message (tenant_id, session_id);
```

- [ ] **Step 2: 实体 + Mapper**

`entity/ChatSession.kt`：
```kotlin
package com.zhijin.chat.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 会话实体（对应 chat_session 表）。 */
@TableName("chat_session")
data class ChatSession(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var appId: Long? = null,
    var title: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
)
```

`entity/ChatMessage.kt`：
```kotlin
package com.zhijin.chat.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 聊天消息（对应 chat_message 表）。 */
@TableName("chat_message")
data class ChatMessage(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var tenantId: Long? = null,
    var sessionId: Long? = null,
    var role: String = "",
    var content: String = "",
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
)
```

`mapper/ChatSessionMapper.kt` / `ChatMessageMapper.kt`（标准 BaseMapper + @Mapper）。
> 需把 `com.zhijin.chat.mapper` 加进 `MybatisPlusConfig` 的 `@MapperScan`（必要修改）。

- [ ] **Step 3: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-chat/ zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/MybatisPlusConfig.kt
git commit -m "feat(chat): 会话/消息数据模型(Flyway V4)"
```

---

## Task 5: 会话服务（TDD）

**Files:**
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/service/SessionService.kt`
- Test: `zhijin-server/zhijin-chat/src/test/kotlin/com/zhijin/chat/service/SessionServiceTest.kt`

- [ ] **Step 1: 写失败测试**

`SessionServiceTest.kt`（mock mapper 验证创建/追加消息/取历史）：
```kotlin
package com.zhijin.chat.service

import com.zhijin.chat.entity.ChatMessage
import com.zhijin.chat.entity.ChatSession
import com.zhijin.chat.mapper.ChatMessageMapper
import com.zhijin.chat.mapper.ChatSessionMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class SessionServiceTest {

    private val sessionMapper = mock(ChatSessionMapper::class.java)
    private val messageMapper = mock(ChatMessageMapper::class.java)
    private val service = SessionService(sessionMapper, messageMapper)

    private fun backfillId() = Answer<Int> { inv: InvocationOnMock ->
        inv.getArgument<ChatSession>(0).id = 1L
        1
    }

    @Test
    fun `创建会话返回带id会话`() {
        `when`(sessionMapper.insert(any(ChatSession::class.java))).thenAnswer(backfillId())
        val s = service.createSession(1L, 1L, "售前")
        assertNotNull(s.id)
        assertEquals("售前", s.title)
    }

    @Test
    fun `追加消息后可取回`() {
        val session = ChatSession(id = 1L, tenantId = 1L, appId = 1L, title = "x")
        service.appendMessage(1L, session, "user", "你好")
        service.appendMessage(1L, session, "assistant", "AI回复")
        `when`(messageMapper.selectList(any())).thenReturn(
            listOf(ChatMessage(role = "user", content = "你好"), ChatMessage(role = "assistant", content = "AI回复"))
        )
        val history = service.getHistory(1L, session)
        assertEquals(2, history.size)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat test -Dtest=SessionServiceTest`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`service/SessionService.kt`：
```kotlin
package com.zhijin.chat.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.chat.entity.ChatMessage
import com.zhijin.chat.entity.ChatSession
import com.zhijin.chat.mapper.ChatMessageMapper
import com.zhijin.chat.mapper.ChatSessionMapper
import org.springframework.stereotype.Service

/** 会话服务：创建/追加消息/取历史。租户隔离由拦截器自动保证。 */
@Service
class SessionService(
    private val sessionMapper: ChatSessionMapper,
    private val messageMapper: ChatMessageMapper,
) {

    fun createSession(tenantId: Long, appId: Long, title: String = ""): ChatSession {
        val session = ChatSession(tenantId = tenantId, appId = appId, title = title)
        sessionMapper.insert(session)
        return session
    }

    fun appendMessage(tenantId: Long, session: ChatSession, role: String, content: String) {
        val msg = ChatMessage(tenantId = tenantId, sessionId = session.id, role = role, content = content)
        messageMapper.insert(msg)
    }

    fun getHistory(tenantId: Long, session: ChatSession): List<ChatMessage> =
        messageMapper.selectList(
            QueryWrapper<ChatMessage>().eq("session_id", session.id).eq("tenant_id", tenantId).orderByAsc("id")
        )
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat test -Dtest=SessionServiceTest`
Expected: `2 passed`。

- [ ] **Step 5: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-chat/
git commit -m "feat(chat): 会话服务(创建/消息/历史)"
```

---

## Task 6: API Key 鉴权过滤器 + /v1/chat SSE 端点

**Files:**
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/web/ApiKeyAuthFilter.kt`
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/dto/ChatRequest.kt`
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/controller/ChatController.kt`
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/workflow/DefaultWorkflow.kt`

- [ ] **Step 1: API Key 鉴权过滤器**

`web/ApiKeyAuthFilter.kt`：
```kotlin
package com.zhijin.chat.web

import com.zhijin.app.service.AppApiKeyService
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.util.WebUtils

/**
 * 开放 API 鉴权：读取 X-API-Key，用 AppApiKeyService.verify 校验并设置租户上下文 + appId 请求属性。
 * 仅作用于 /v1/**（开放 API），管理端仍走 JWT（B2 资源服务器链）。
 */
class ApiKeyAuthFilter(private val apiKeyService: AppApiKeyService) : HttpFilter() {

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = res as HttpServletResponse
        val apiKey = request.getHeader("X-API-Key") ?: return unauthorized(response)
        // 校验 Key：需要租户+应用。V1 约定：Key 格式 ak_xxx，通过查询 DB 匹配哈希找到租户/应用。
        val resolved = resolveTenantAndApp(apiKey) ?: return unauthorized(response)
        TenantContextHolder.setTenantId(resolved.first)
        request.setAttribute("zhijin.appId", resolved.second)
        try {
            chain.doFilter(request, response)
        } finally {
            TenantContextHolder.clear()
        }
    }

    /** 通过哈希反查租户+应用（V1 简化：AppApiKeyService 增加 findByHash）。 */
    private fun resolveTenantAndApp(apiKey: String): Pair<Long, Long>? = apiKeyService.findByPlainKey(apiKey)

    private fun unauthorized(response: HttpServletResponse) {
        response.status = 401
        response.writer.write("""{"code":3000,"message":"无效 API Key"}""")
    }
}
```
> 说明：`AppApiKeyService.findByPlainKey`（V1 新增）——用 B3 的 sha256 反查 `app_api_key` 表得 tenant_id + app_id（需在 B3 的 AppApiKeyService/Mapper 加此方法，必要修改，报告）。

- [ ] **Step 2: 注册过滤器 + /v1/** 放行（绕过 JWT 链）**

- 把 `ApiKeyAuthFilter` 注册为 servlet filter（`@Component` + `FilterRegistrationBean` 或 `@WebFilter`），匹配 `/v1/*`，order 在 TenantFilter 之后。
- SecurityConfig 的链 2 需把 `/v1/**` 加入 `permitAll`（开放 API 用 API Key，不走 JWT）；同时 `/v1/**` 命中链 2（`securityMatcher` 需含 `/v1/**`）。这是必要修改。

- [ ] **Step 3: /v1/chat 端点 + 默认工作流**

`dto/ChatRequest.kt`：
```kotlin
package com.zhijin.chat.dto

data class ChatRequest(
    val appId: Long? = null,
    val sessionId: Long? = null,
    val message: String = "",
)
```

`workflow/DefaultWorkflow.kt`（程序化构建 LLM-only 工作流）：
```kotlin
package com.zhijin.chat.workflow

import com.zhijin.orchestrator.model.Connection
import com.zhijin.orchestrator.model.FieldInfo
import com.zhijin.orchestrator.model.FieldSource
import com.zhijin.orchestrator.model.NodeSchema
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.model.OutputField
import com.zhijin.orchestrator.model.WorkflowSchema

/** 默认 LLM-only 工作流：start → llm(prompt=用户消息) → end。 */
object DefaultWorkflow {
    fun build(prompt: String): WorkflowSchema = WorkflowSchema(
        id = "wf-default",
        start = "start",
        nodes = listOf(
            NodeSchema(key = "start", type = NodeType.START),
            NodeSchema(
                key = "llm", type = NodeType.LLM,
                inputs = listOf(FieldInfo("prompt", FieldSource.Literal(prompt))),
                outputs = listOf(OutputField("output", "string")),
            ),
            NodeSchema(
                key = "end", type = NodeType.END,
                inputs = listOf(FieldInfo("content", FieldSource.Ref("llm", "output"))),
            ),
        ),
        connections = listOf(
            Connection("start", "llm"),
            Connection("llm", "end"),
        ),
    )
}
```

`controller/ChatController.kt`：
```kotlin
package com.zhijin.chat.controller

import com.zhijin.chat.dto.ChatRequest
import com.zhijin.chat.service.ChatService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 开放聊天 API（/v1/chat），API Key 鉴权。 */
@RestController
@RequestMapping("/v1")
class ChatController(private val chatService: ChatService) {

    @PostMapping(value = ["/chat"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody req: ChatRequest): SseEmitter {
        val emitter = SseEmitter()
        chatService.chatAsync(req, emitter)
        return emitter
    }
}
```

- [ ] **Step 4: 构建验证**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat -am clean compile`
Expected: `BUILD SUCCESS`。（ChatService 在 Task 7 实现，先建空壳或一并实现。）

- [ ] **Step 5: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-chat/ zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/MybatisPlusConfig.kt zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/service/AppApiKeyService.kt
git commit -m "feat(chat): API Key鉴权 + /v1/chat SSE端点 + 默认工作流"
```

---

## Task 7: ChatService 接编排引擎（TDD）

**Files:**
- Create: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/service/ChatService.kt`
- Test: `zhijin-server/zhijin-chat/src/test/kotlin/com/zhijin/chat/service/ChatServiceTest.kt`

- [ ] **Step 1: 写失败测试**（ChatService 跑默认工作流，用 stub ModelComponent，返回最终输出）

`ChatServiceTest.kt`：
```kotlin
package com.zhijin.chat.service

import com.zhijin.chat.dto.ChatRequest
import com.zhijin.chat.entity.ChatSession
import com.zhijin.chat.workflow.DefaultWorkflow
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.nodes.EndNode
import com.zhijin.orchestrator.nodes.LlmNode
import com.zhijin.orchestrator.nodes.StartNode
import com.zhijin.orchestrator.nodes.StubModelComponent
import com.zhijin.orchestrator.scheduler.WorkflowRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatServiceTest {

    @Test
    fun `默认工作流返回模型回复`() = runTest {
        val registry = NodeExecutorRegistry().apply {
            register(NodeType.START) { StartNode() }
            register(NodeType.LLM) { LlmNode(StubModelComponent("AI回复")) }
            register(NodeType.END) { EndNode() }
        }
        val runner = WorkflowRunner(registry)
        val schema = DefaultWorkflow.build("你好")
        val result = runner.execute(schema, com.zhijin.orchestrator.context.VariableStore())
        assertEquals("AI回复", result.finalOutput)
    }
}
```
> 说明：本测试先验证「默认工作流 + 引擎」核心路径（不依赖会话/HTTP）。ChatService 的 HTTP/SSE 部分在 Task 6/8 端到端验证。

- [ ] **Step 2: 运行确认失败/通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat test -Dtest=ChatServiceTest`
Expected: 首次可能直接通过（依赖已存在）；若 ChatService 被引用则补实现。

- [ ] **Step 3: 实现 ChatService**

`service/ChatService.kt`：
```kotlin
package com.zhijin.chat.service

import com.zhijin.chat.dto.ChatRequest
import com.zhijin.chat.entity.ChatSession
import com.zhijin.chat.workflow.DefaultWorkflow
import com.zhijin.orchestrator.context.VariableStore
import com.zhijin.orchestrator.executor.NodeExecutorRegistry
import com.zhijin.orchestrator.model.NodeType
import com.zhijin.orchestrator.model.ModelComponent
import com.zhijin.orchestrator.nodes.EndNode
import com.zhijin.orchestrator.nodes.LlmNode
import com.zhijin.orchestrator.nodes.StartNode
import com.zhijin.orchestrator.scheduler.WorkflowRunner
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

/** 聊天服务：驱动编排引擎执行默认工作流，结果 SSE 返回 + 落消息。 */
@Service
class ChatService(
    private val model: ModelComponent,
    private val sessionService: SessionService,
) {

    private val runner: WorkflowRunner by lazy {
        WorkflowRunner(
            NodeExecutorRegistry().apply {
                register(NodeType.START) { StartNode() }
                register(NodeType.LLM) { LlmNode(model) }
                register(NodeType.END) { EndNode() }
            }
        )
    }

    /** 异步聊天：默认工作流 → SSE。 */
    fun chatAsync(req: ChatRequest, emitter: SseEmitter) {
        val appId = req.appId ?: 0L
        val session = req.sessionId?.let { findSession(it) } ?: sessionService.createSession(0L, appId, "会话")
        // 追加用户消息
        sessionService.appendMessage(session.tenantId ?: 0L, session, "user", req.message)
        // 执行默认工作流（V1；有已发布工作流 DSL 时改走解析路径，B4+画布后启用）
        val schema = DefaultWorkflow.build(req.message)
        val result = runner.execute(schema, VariableStore())
        val reply = result.finalOutput?.toString() ?: ""
        sessionService.appendMessage(session.tenantId ?: 0L, session, "assistant", reply)
        try {
            emitter.send(SseEmitter.event().data(reply))
            emitter.complete()
        } catch (e: IOException) {
            emitter.completeWithError(e)
        }
    }

    private fun findSession(sessionId: Long): ChatSession? = null // V1 简化：会话查找后续完善
}
```
> 说明：`chatAsync` 里 `runner.execute` 是 suspend，ChatController 用 SseEmitter 异步；V1 用 `kotlinx.coroutines` 包一层或直接同步执行（SSE 一次返回）。实现时若 suspend 与 SseEmitter 冲突，用 `GlobalScope.launch` 或 `runBlocking` 包执行（报告采用方式）。findSession 简化返回 null（每次新建），会话复用留 V1 后续。

- [ ] **Step 4: 运行确认通过**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-chat test`
Expected: 全部通过（含 ChatServiceTest + SessionServiceTest）。

- [ ] **Step 5: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/zhijin-chat/
git commit -m "feat(chat): ChatService 驱动编排引擎 + SSE"
```

---

## Task 8: 端到端联调 + 收尾

- [ ] **Step 1: 全量构建**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 真实端到端联调**

启动 Python 桩（8001）+ 平台服务（8080，真 PG/Nacos）。流程：
1. 管理端登录拿 token
2. 创建应用 + 生成 API Key（B3 端点）
3. 用 API Key 调 `POST /v1/chat`（带 `X-API-Key`）
4. 验证 SSE 返回 `echo: {message}`
> 联调细节参照 B2/B3 已建立的方式（ASCII body、UTF-8 文件避免 GBK 问题）。

- [ ] **Step 3: 记录实现修正**

追加到本计划「执行修正记录」。

- [ ] **Step 4: Commit 遗留**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A
git commit -m "docs(plans): B5 追加执行修正记录"
```

---

## Self-Review 记录

- **Spec 覆盖**：§6 chat/ai-client ✓ · §10 数据流 B（鉴权→会话→引擎→SSE→落账）✓ · §13 决策 21（Key 加密复用）✓ · §12.1 七大原则（ApiKeyAuthFilter 单一职责、ModelComponent 依赖倒置、SessionService SRP）✓。
- **测试覆盖**：AiClient（MockWebServer）、HttpModelComponent、SessionService、ChatService 默认工作流——TDD。
- **占位符扫描**：无 TBD；每步含完整代码。
- **类型一致性**：`ModelComponent.complete(prompt, modelName)` 在 Stub/Http 实现间一致；`/v1/chat/completions` 在 Python 桩与 AiClient 间一致；`X-API-Key` 头在 Filter 与联调间一致。

## 执行交接

B5 完成后 → **B6 用量统计 + 审计**（zhijin-billing-audit：token 用量落账 + 审计日志），然后 **计划 C（AI 服务模型网关真实供应商）** 替换 Python 桩。

---

## 执行修正记录（2026-08-20 实现期间的真实发现，均已落地并验证）

| # | 修正 | 原因 |
|---|---|---|
| 1 | **循环依赖**：zhijin-chat→zhijin-app 会成环，用 `ApiKeyResolver` 函数式接口 + app 侧适配 Bean 解耦 | zhijin-app 聚合依赖 chat，chat 再依赖 app 即 Maven 循环 |
| 2 | `findByPlainKey` 反查需 `@InterceptorIgnore(tenantLine="true")` + `findByHash` Mapper 方法 | 认证时租户未知（Key 即租户来源），租户拦截器会注入 `tenant_id=0` |
| 3 | `AiClient` RestClient 需注册 Jackson 3 `KotlinModule`（`KotlinModule.Builder()`）+ `registerDefaults()` 先于 `withJsonConverter` | Boot 4 用 Jackson 3；`KotlinModule()` 直接调用解析到 private 构造器编译失败；转换器注册顺序错则列表为空 |
| 4 | `kotlinx-coroutines-test` 需在 zhijin-chat 单独声明（test-scope 非传递） | orchestrator 的 test 依赖不传给 chat |
| 5 | Kotlin 嵌套注释：KDoc 里 `/v1/**` 的 `/**` 触发未闭合注释 | 注释措辞避开 `/**` 序列 |
| 6 | `FilterRegistrationBean.filter` 需 `setFilter(...)` | Kotlin 2.2 + Boot 4 下视为只读 val |
| 7 | Windows 控制台 GBK 导致 curl 中文 body `Invalid UTF-8`（联调环境问题） | 非应用缺陷；联调用 UTF-8 文件传 body |

> **端到端验收**：`POST /v1/chat`（X-API-Key）→ API Key 鉴权 → 会话 → 默认工作流 → HttpModelComponent → AiClient → Python 桩 → **SSE `data: echo: hello world`**，chat_session + chat_message 双表落库（user + assistant）验证通过。
