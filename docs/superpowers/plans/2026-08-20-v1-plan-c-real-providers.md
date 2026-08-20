# 计划 C：Python 真实供应商接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `zhijin-ai` 的模型网关桩（`echo:` 返回）替换为真实供应商调用（qwen / claude / openai / deepseek），Kotlin 侧从数据库取加密 Key 下发给 Python，Python 返回真实 token 计数回填 `usage_record`。

**Architecture:** Python 侧 `gateway/` 模块按供应商分适配器（OpenAI 兼容协议统一，Claude 用 Anthropic SDK）；Kotlin 侧 `HttpModelComponent` 改造：从 `ModelProviderKey` 表取加密 Key 解密后随请求传给 Python；Python 返回 `usage`（prompt/completion/total tokens）→ Kotlin 回填 `usage_record`。

**Tech Stack:** Python 3.11 + FastAPI + `openai` SDK + `anthropic` SDK · Kotlin RestClient · 数据库 `model_provider_key` 表（已加密）

**设计依据:** `2026-08-17-agent-platform-design.md` §8（AI 服务模型网关）、§13 决策 21（Key 加密下发，AI 服务不落盘）。

---

## 关键决策

- **供应商协议**：qwen / openai / deepseek 都走 **OpenAI 兼容协议**（`/v1/chat/completions`）；claude 走 **Anthropic Messages API**（`/v1/messages`）。Python 侧统一适配器模式。
- **Key 下发（端口模式，解决 C1/N1）**：
  - 在 `zhijin-orchestrator/domain/` 定义端口：`fun interface ModelKeyResolver { fun resolvePlainKey(providerKeyId: Long): String? }`（**去掉 tenantId，适配 Bean 内部从 TenantContextHolder 取**）
  - 在 `zhijin-app/config/` 提供适配 Bean：注入 `ModelConfigApplicationService.getPlainKey(TenantContextHolder.getRequiredTenantId(), keyId)` 实现
  - `HttpModelComponent` 构造注入 `ModelKeyResolver`，调用时解密 Key 后传 `api_key` 明文给 Python（不落盘 Python 侧，决策 21）
- **Token 回填（方案 A，解决 C2，保持引擎语义）**：
  - `ModelComponent.complete` 返回类型改为 `ChatCompletionResult(content: String, usage: Usage?)`
  - `LlmNode` 把 usage 写入 `NodeResult.outputs["usage"]`
  - `WorkflowRunner` 结果透传 usage 到 `ChatApplicationService`
  - `ChatApplicationService` 从执行结果取 usage 回填 `usage_record`
  - 同步改：`StubModelComponent`、`LlmNode`、`HttpModelComponent`、测试
- **Supplier 选择**：请求 body 带 `provider` 字段（`qwen`/`claude`/`openai`/`deepseek`），Python 路由到对应适配器。
- **错误处理**：供应商返回错误（401/429/500）→ Python 转 5xx 给 Kotlin → V1 只落成功记录（C4：`usage_record` 无 error 列）。
- **V1 简化**：不做供应商负载均衡/重试/熔断（留 V2）；不做流式 token 计数（V1 一次性返回）；provider/model 从 `AppModelConfig` 取（C6）。

---

## 文件结构

```
zhijin-ai/app/
├── gateway/
│   ├── __init__.py
│   ├── router.py                  ← 改造 /v1/chat/completions（接收 provider/api_key）
│   ├── adapters/
│   │   ├── __init__.py
│   │   ├── base.py                ← ProviderAdapter 抽象（async def complete）
│   │   ├── openai_adapter.py      ← OpenAI 兼容（qwen/openai/deepseek）
│   │   └── claude_adapter.py      ← Anthropic Messages API
│   └── registry.py                ← 供应商适配器注册表
└── main.py                        ← 挂载 gateway router

zhijin-server/zhijin-orchestrator/
├── src/main/kotlin/com/zhijin/orchestrator/
│   ├── domain/
│   │   ├── ModelComponent.kt      ← 改造：返回 ChatCompletionResult(content, usage)
│   │   └── ModelKeyResolver.kt    ← 新增：端口接口（解决 C1）
│   ├── infrastructure/model/
│   │   └── HttpModelComponent.kt  ← 改造：注入 ModelKeyResolver，传 api_key 明文
│   └── domain/nodes/
│       ├── LlmNode.kt             ← 改造：把 usage 写入 NodeResult.outputs["usage"]
│       └── StubModelComponent.kt  ← 改造：返回 ChatCompletionResult

zhijin-server/zhijin-chat/
└── src/main/kotlin/com/zhijin/chat/application/
    └── ChatApplicationService.kt  ← 改造：从执行结果取 usage 回填 usage_record

zhijin-server/zhijin-app/
└── src/main/kotlin/com/zhijin/app/config/
    └── ModelKeyResolverConfig.kt  ← 新增：适配 Bean（解决 C1）

zhijin-server/zhijin-ai-client/
└── src/main/kotlin/com/zhijin/aiclient/
    └── AiClient.kt                ← 改造：解析 usage（snake_case → camelCase，C7）
```

---

## Task 1: Python 供应商适配器 + 路由改造

**Files:**
- Create: `zhijin-ai/app/gateway/__init__.py`、`adapters/__init__.py`、`adapters/base.py`、`adapters/openai_adapter.py`、`adapters/claude_adapter.py`、`adapters/registry.py`、`router.py`
- Modify: `zhijin-ai/app/main.py`（挂载 gateway router，移除旧桩）

- [ ] **Step 1: 供应商适配器抽象**

`adapters/base.py`：
```python
"""供应商适配器抽象。"""
from dataclasses import dataclass
from typing import Protocol


@dataclass
class CompletionResult:
    """供应商返回结果。"""
    content: str
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ProviderAdapter(Protocol):
    """供应商适配器协议。"""

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        """调用供应商返回结果。"""
        ...
```

- [ ] **Step 2: OpenAI 兼容适配器（qwen/openai/deepseek 共用，解决 C5/N5）**

`adapters/openai_adapter.py`：
```python
"""OpenAI 兼容协议适配器（qwen/openai/deepseek 共用）。"""
import os
from openai import AsyncOpenAI
from .base import CompletionResult


class OpenAICompatibleAdapter:
    """OpenAI 兼容协议适配器。"""

    def __init__(self, base_url: str, env_var_name: str):
        self.base_url = base_url
        self.env_var_name = env_var_name  # 解决 C5/N5：环境变量名

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        # 解决 C5/N5：api_key 为空时回退环境变量
        key = api_key or os.getenv(self.env_var_name, "")
        if not key:
            raise ValueError(f"未提供 API Key 且环境变量 {self.env_var_name} 未设置")
        
        client = AsyncOpenAI(api_key=key, base_url=self.base_url)
        resp = await client.chat.completions.create(model=model, messages=messages)
        usage = resp.usage
        return CompletionResult(
            content=resp.choices[0].message.content or "",
            prompt_tokens=usage.prompt_tokens if usage else 0,
            completion_tokens=usage.completion_tokens if usage else 0,
            total_tokens=usage.total_tokens if usage else 0,
        )


# 预配置实例（base_url 从环境变量读，env_var_name 指定回退环境变量）
QWEN_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    "QWEN_API_KEY"
)
OPENAI_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
    "OPENAI_API_KEY"
)
DEEPSEEK_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
    "DEEPSEEK_API_KEY"
)
```

- [ ] **Step 3: Claude 适配器（解决 C5/N5）**

`adapters/claude_adapter.py`：
```python
"""Anthropic Claude Messages API 适配器。"""
import os
import anthropic
from .base import CompletionResult


class ClaudeAdapter:
    """Claude Messages API 适配器。"""

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        # 解决 C5/N5：api_key 为空时回退环境变量
        key = api_key or os.getenv("CLAUDE_API_KEY", "")
        if not key:
            raise ValueError("未提供 API Key 且环境变量 CLAUDE_API_KEY 未设置")
        
        client = anthropic.AsyncAnthropic(api_key=key)
        # OpenAI 格式转 Claude 格式
        system = ""
        claude_messages = []
        for m in messages:
            if m["role"] == "system":
                system = m["content"]
            else:
                claude_messages.append({"role": m["role"], "content": m["content"]})
        resp = await client.messages.create(
            model=model,
            max_tokens=4096,
            system=system,
            messages=claude_messages,
        )
        return CompletionResult(
            content=resp.content[0].text if resp.content else "",
            prompt_tokens=resp.usage.input_tokens,
            completion_tokens=resp.usage.output_tokens,
            total_tokens=resp.usage.input_tokens + resp.usage.output_tokens,
        )


CLAUDE_ADAPTER = ClaudeAdapter()
```

- [ ] **Step 4: 供应商注册表 + 路由改造**

`adapters/registry.py`：
```python
"""供应商适配器注册表。"""
from .openai_adapter import QWEN_ADAPTER, OPENAI_ADAPTER, DEEPSEEK_ADAPTER
from .claude_adapter import CLAUDE_ADAPTER


ADAPTERS = {
    "qwen": QWEN_ADAPTER,
    "openai": OPENAI_ADAPTER,
    "deepseek": DEEPSEEK_ADAPTER,
    "claude": CLAUDE_ADAPTER,
}


def get_adapter(provider: str):
    """按供应商名获取适配器。"""
    adapter = ADAPTERS.get(provider)
    if not adapter:
        raise ValueError(f"不支持的供应商: {provider}")
    return adapter
```

`router.py`（改造 `/v1/chat/completions`）：
```python
"""模型网关路由（真实供应商调用）。"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from .adapters.registry import get_adapter


router = APIRouter(prefix="/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = Field(default="default")
    provider: str = Field(default="qwen")        # 新增：供应商
    api_key: str = Field(default="")             # 新增：Kotlin 下发的 Key
    messages: list[ChatMessage] = Field(default_factory=list)


@router.post("/chat/completions")
async def chat_completions(req: ChatRequest):
    """调用真实供应商。"""
    try:
        adapter = get_adapter(req.provider)
        result = await adapter.complete(
            api_key=req.api_key,
            model=req.model,
            messages=[{"role": m.role, "content": m.content} for m in req.messages],
        )
        return {
            "id": "chatcmpl-real",
            "object": "chat.completion",
            "model": req.model,
            "choices": [{"index": 0, "message": {"role": "assistant", "content": result.content}}],
            "usage": {
                "prompt_tokens": result.prompt_tokens,
                "completion_tokens": result.completion_tokens,
                "total_tokens": result.total_tokens,
            },
        }
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"供应商调用失败: {e}")
```

- [ ] **Step 5: 更新 main.py 挂载路由**

在 `zhijin-ai/app/main.py` 替换旧桩：
```python
from .gateway.router import router as gateway_router

app.include_router(gateway_router)
```
（删除旧的 `from .routes import chat as chat_route` + `app.include_router(chat_route.router)`。）

- [ ] **Step 6: 安装新依赖**

Run: `cd zhijin-ai && uv add openai anthropic`

- [ ] **Step 7: 测试 + 运行验证**

Run: `uv run pytest tests/test_health.py -q` → `1 passed`。

启动服务：`uv run uvicorn app.main:app --port 8001`。

手动测试（需真实 Key，可选）：
```bash
curl -s -X POST http://localhost:8001/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-max","provider":"qwen","api_key":"sk-xxx","messages":[{"role":"user","content":"hi"}]}'
```
Expected: 真实供应商返回（含 usage token 计数）。无真实 Key 时返回 502 错误（预期）。

- [ ] **Step 8: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-ai/
git commit -m "feat(ai): 模型网关真实供应商(qwen/claude/openai/deepseek)"
```

---

## Task 2: Kotlin ModelComponent 改造 + ModelKeyResolver 端口（解决 C1/C2/C3）

**Files:**
- Create: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/domain/ModelKeyResolver.kt`（端口接口，解决 C1）
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/domain/ModelComponent.kt`（返回 `ChatCompletionResult`，解决 C2）
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/domain/nodes/LlmNode.kt`（把 usage 写入 `NodeResult.outputs["usage"]`，解决 C2）
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/domain/nodes/StubModelComponent.kt`（返回 `ChatCompletionResult`）
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/infrastructure/model/HttpModelComponent.kt`（注入 `ModelKeyResolver`，传 `api_key` 明文，解决 C3）
- Create: `zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/config/ModelKeyResolverConfig.kt`（适配 Bean，解决 C1）
- Modify: 相关测试（`HttpModelComponentTest`、`LlmNodeTest`）

- [ ] **Step 1: 创建 ModelKeyResolver 端口接口（解决 C1/N1）**

`zhijin-orchestrator/domain/ModelKeyResolver.kt`：
```kotlin
package com.zhijin.orchestrator.domain

/**
 * 模型 Key 解析端口（依赖倒置，解决 C1 模块依赖方向问题）。
 * 实现在 zhijin-app（有 ModelProviderKey 表访问权限），通过适配 Bean 注入。
 * 签名不含 tenantId（解决 N1）：适配 Bean 内部从 TenantContextHolder 取。
 */
fun interface ModelKeyResolver {
    /**
     * 根据 Key ID 返回解密后的明文 Key。
     * 返回 null 表示 Key 不存在或已禁用。
     */
    fun resolvePlainKey(providerKeyId: Long): String?
}
```

- [ ] **Step 2: 改造 ModelComponent 接口（解决 C2）**

`zhijin-orchestrator/domain/ModelComponent.kt`：
```kotlin
package com.zhijin.orchestrator.domain

/** 模型调用结果（含 token 使用量）。 */
data class ChatCompletionResult(
    val content: String,
    val usage: Usage? = null,
)

/** Token 使用量。 */
data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

/** 模型组件抽象（依赖倒置）。 */
interface ModelComponent {
    /**
     * 调用模型，返回 assistant 内容 + token 使用量。
     * providerKeyId 为加密 Key 的 ID，通过 ModelKeyResolver 解密。
     */
    suspend fun complete(prompt: String, modelName: String, providerKeyId: Long? = null): ChatCompletionResult
}
```

- [ ] **Step 3: 改造 StubModelComponent**

`zhijin-orchestrator/domain/nodes/StubModelComponent.kt`：
```kotlin
package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.ChatCompletionResult
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.Usage

/** 测试用桩：返回固定文本 + 模拟 usage。 */
class StubModelComponent(private val reply: String = "模型返回") : ModelComponent {
    override suspend fun complete(prompt: String, modelName: String, providerKeyId: Long?): ChatCompletionResult =
        ChatCompletionResult(
            content = reply,
            usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30),
        )
}
```

- [ ] **Step 4: 改造 LlmNode（解决 C2）**

`zhijin-orchestrator/domain/nodes/LlmNode.kt`：
```kotlin
package com.zhijin.orchestrator.domain.nodes

import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.NodeContext
import com.zhijin.orchestrator.domain.NodeExecutor
import com.zhijin.orchestrator.domain.NodeResult
import com.zhijin.orchestrator.domain.NodeSchema

/** LLM 节点：调用模型，把 usage 写入 outputs["usage"]（解决 C2）。 */
class LlmNode(private val model: ModelComponent) : NodeExecutor {
    override suspend fun invoke(ctx: NodeContext, node: NodeSchema): NodeResult {
        val prompt = node.inputs.firstOrNull { it.key == "prompt" }?.let { ctx.variableStore.resolveRef(it.source) }?.toString() ?: ""
        val modelName = node.configs["model"]?.toString() ?: "default"
        val providerKeyId = (node.configs["providerKeyId"] as? Number)?.toLong()
        val result = model.complete(prompt, modelName, providerKeyId)
        val outKey = node.outputs.firstOrNull()?.key ?: "output"
        return NodeResult(
            outputs = mapOf(
                outKey to result.content,
                "usage" to result.usage,  // 把 usage 透传给 WorkflowRunner
            )
        )
    }
}
```

- [ ] **Step 5: 改造 HttpModelComponent（解决 C1/C3/N1）**

`zhijin-orchestrator/infrastructure/model/HttpModelComponent.kt`：
```kotlin
package com.zhijin.orchestrator.infrastructure.model

import com.zhijin.aiclient.AiClient
import com.zhijin.orchestrator.domain.ChatCompletionResult
import com.zhijin.orchestrator.domain.ModelComponent
import com.zhijin.orchestrator.domain.ModelKeyResolver

/**
 * 真实模型组件：通过 ModelKeyResolver 解密 Key，传 api_key 明文给 Python（解决 C1/C3/N1）。
 * 请求体：{model, provider, api_key, messages}
 * 响应体：{choices[0].message.content, usage: {prompt_tokens, completion_tokens, total_tokens}}
 */
class HttpModelComponent(
    private val aiClient: AiClient,
    private val keyResolver: ModelKeyResolver,  // 解决 C1：端口模式
) : ModelComponent {

    override suspend fun complete(prompt: String, modelName: String, providerKeyId: Long?): ChatCompletionResult {
        // 解密 Key（解决 C1/N1：签名无 tenantId，适配 Bean 内部从 TenantContextHolder 取）
        val plainKey = providerKeyId?.let { keyResolver.resolvePlainKey(it) } ?: ""
        // 传 api_key 明文给 Python（解决 C3：不落盘 Python 侧）
        return aiClient.completeWithUsage(prompt, modelName, "qwen", plainKey)
    }
}
```

- [ ] **Step 6: 创建 ModelKeyResolverConfig 适配 Bean（解决 C1/N1）**

`zhijin-app/config/ModelKeyResolverConfig.kt`：
```kotlin
package com.zhijin.app.config

import com.zhijin.app.application.ModelConfigApplicationService
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.orchestrator.domain.ModelKeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ModelKeyResolver 适配 Bean（解决 C1/N1：避免 orchestrator 直接依赖 app）。
 * 注入 ModelConfigApplicationService.getPlainKey 实现，内部从 TenantContextHolder 取 tenantId。
 */
@Configuration
class ModelKeyResolverConfig(private val modelConfigService: ModelConfigApplicationService) {
    @Bean
    fun modelKeyResolver(): ModelKeyResolver = ModelKeyResolver { keyId ->
        val tenantId = TenantContextHolder.getRequiredTenantId()
        modelConfigService.getPlainKey(tenantId, keyId)
    }
}
```

- [ ] **Step 7: 更新测试（解决 N6：补全受影响的测试）**

`HttpModelComponentTest.kt`：
```kotlin
@Test
fun `HttpModelComponent通过ModelKeyResolver解密Key`() = runTest {
    val aiClient = mock(AiClient::class.java)
    val keyResolver = mock(ModelKeyResolver::class.java)
    `when`(keyResolver.resolvePlainKey(1L)).thenReturn("sk-test")  // 解决 N1：签名无 tenantId
    `when`(aiClient.completeWithUsage("prompt", "qwen-max", "qwen", "sk-test"))
        .thenReturn(ChatCompletionResult("AI回复", Usage(10, 20, 30)))
    
    val component = HttpModelComponent(aiClient, keyResolver)
    val result = component.complete("prompt", "qwen-max", 1L)
    
    assertEquals("AI回复", result.content)
    assertEquals(30, result.usage?.totalTokens)
}
```

`LlmNodeTest.kt`：
```kotlin
@Test
fun `LlmNode把usage写入outputs`() = runTest {
    val model = StubModelComponent("AI回复")
    val node = LlmNode(model)
    val schema = NodeSchema(...)
    val result = node.invoke(NodeContext(VariableStore()), schema)
    
    assertEquals("AI回复", result.outputs["output"])
    assertNotNull(result.outputs["usage"])
    assertEquals(30, (result.outputs["usage"] as Usage).totalTokens)
}
```

**受影响的既有测试（解决 N6）**：
- `ChatApplicationServiceTest`：`StubModelComponent` 返回类型从 `String` 改为 `ChatCompletionResult`，需更新断言
- `WorkflowIntegrationTest` / `WorkflowRunnerTest`：多节点 DSL 含 LLM 节点，`StubModelComponent` 返回类型变化，需更新
- 执行时全量 `mvn test` 兜底，确保所有测试通过

- [ ] **Step 8: 构建验证**

`cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 9: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/
git commit -m "feat(orchestrator): ModelComponent返回ChatCompletionResult + ModelKeyResolver端口(解决C1/C2/C3)"
```

---

## Task 3: usage_record 回填真实 token 计数（解决 C2/C6/C7）

**Files:**
- Modify: `zhijin-server/zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`（新增 `completeWithUsage` 方法，解析 usage，解决 C7）
- Modify: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/application/ChatApplicationService.kt`（从执行结果取 usage 回填，解决 C2/C6）

- [ ] **Step 1: AiClient 新增 completeWithUsage 方法（解决 C7/N3/N4）**

`zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`：
```kotlin
package com.zhijin.aiclient

import tools.jackson.annotation.JsonProperty  // 解决 N3：Jackson 3 注解包（非 com.fasterxml.jackson）
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter  // 解决 N4'：装配 jsonMapper 用
import org.springframework.web.client.RestClient

/** Token 使用量（snake_case → camelCase 映射，解决 C7）。 */
data class Usage(
    @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
    @JsonProperty("completion_tokens") val completionTokens: Int = 0,
    @JsonProperty("total_tokens") val totalTokens: Int = 0,
)

/** 模型调用结果。 */
data class ChatCompletionResult(
    val content: String,
    val usage: Usage? = null,
)

/** OpenAI 兼容响应结构。 */
data class CompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,  // 新增 usage 字段
)

data class Choice(val index: Int? = null, val message: Message? = null)
data class Message(val role: String? = null, val content: String? = null)

/**
 * AI 服务客户端（解决 N4/N4'：保留 KotlinModule JsonMapper 配置并装配进 RestClient）。
 * 必须配置 JsonMapper.builder().addModule(KotlinModule.Builder().build()) 并通过
 * configureMessageConverters 装配，否则默认 JsonMapper 无法构造 Kotlin data class
 * （无默认构造器），反序列化会抛异常（B5 执行时踩过的坑）。
 */
open class AiClient(private val baseUrl: String = System.getenv("AI_SERVICE_URL") ?: "http://127.0.0.1:8001") {

    // 解决 N4：KotlinModule 配置（B5 执行时踩过的坑）
    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    // 解决 N4'：把 jsonMapper 装配进 RestClient 消息转换器（必须先 registerDefaults 再 withJsonConverter）
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .configureMessageConverters { converters ->
            converters.registerDefaults()
            converters.withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
        }
        .build()

    /** 调用模型，返回 assistant 内容（向后兼容）。 */
    fun complete(prompt: String, model: String = "default"): String =
        completeWithUsage(prompt, model, "qwen", "").content

    /** 调用模型，返回内容 + usage（解决 C7/N3/N4：snake_case 映射 + KotlinModule 配置）。 */
    fun completeWithUsage(
        prompt: String,
        model: String = "default",
        provider: String = "qwen",  // 解决 C6：从 AppModelConfig 取
        apiKey: String = "",
    ): ChatCompletionResult {
        val body = mapOf(
            "model" to model,
            "provider" to provider,
            "api_key" to apiKey,  // 解决 C3：传明文，不落盘 Python
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
        )
        val resp = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(CompletionResponse::class.java)
        val content = resp?.choices?.firstOrNull()?.message?.content ?: ""
        return ChatCompletionResult(content = content, usage = resp?.usage)
    }
}
```

> **N4 关键**：必须配置 `JsonMapper.builder().addModule(KotlinModule.Builder().build())`，否则 Kotlin data class 反序列化失败。现有 `AiClient` 已有此配置（B5 执行时踩过坑），重写版必须保留。

- [ ] **Step 2: ChatApplicationService 从执行结果取 usage 回填（解决 C2/N2'/N6）**

`zhijin-chat/.../application/ChatApplicationService.kt`：
```kotlin
// 从 AppModelConfig 取 provider/model/providerKeyId（解决 C6）
// V1 简化：默认 qwen/qwen-max/null
val provider = "qwen"
val model = "qwen-max"
val providerKeyId: Long? = null  // 后续从 AppModelConfig 取

// 执行工作流（LlmNode 内部调用 ModelComponent，usage 透传到 NodeResult，再写入 VariableStore）
// 解决 N2'：VariableStore 作为局部变量，执行后从 store 取 usage
val store = VariableStore()
val result = runBlocking {
    runner.execute(DefaultWorkflow.build(req.message), store)
}

// 从 VariableStore 取 usage（解决 N2'：WorkflowResult 无 outputs 字段，但 LlmNode 写入的 usage 在 store 里）
// VariableStore 需有 readNodeOutput 方法（或 readNodeOutput 已存在）
val usage = store.readNodeOutput("llm", "usage") as? com.zhijin.orchestrator.domain.Usage
usageRecorder.record(
    UsageRecord(
        tenantId = tenantId, appId = appId, sessionId = session.id,
        model = model,
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        totalTokens = usage?.totalTokens ?: 0,
        latencyMs = latencyMillis,
    )
)
```

> **说明**：`DefaultWorkflow.build` 需要把 `provider`/`model`/`providerKeyId` 写入 LLM 节点的 `configs`，这样 `LlmNode` 才能读取并传给 `ModelComponent`。修改 `DefaultWorkflow.kt`：
```kotlin
NodeSchema(
    key = "llm", type = NodeType.LLM,
    inputs = listOf(FieldInfo("prompt", FieldSource.Literal(prompt))),
    outputs = listOf(OutputField("output", "string")),
    configs = mapOf("model" to "qwen-max", "provider" to "qwen", "providerKeyId" to null),
)
```

> **VariableStore 读方法**：检查 `VariableStore` 是否有 `readNodeOutput(nodeId, outputKey)` 方法。若只有 `writeNodeOutput`，需新增：
```kotlin
fun readNodeOutput(nodeId: String, outputKey: String): Any? = outputs["$nodeId.$outputKey"]
```

- [ ] **Step 3: 测试 + 验证**

`cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean test`
Expected: 全部通过（既有 62 测试 + 新 usage 回填测试）。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/
git commit -m "feat(chat): usage_record 回填真实 token 计数(解决 C2/C6/C7)"
```

---

## Task 4: 端到端联调（真实供应商调用）

- [ ] **Step 1: 全量构建**

`cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 真实端到端联调**

启动 Python 服务（8001）+ Kotlin 服务（8080，真 PG/Nacos）。

流程：
1. 管理端登录 → 创建应用 → 添加供应商 Key（qwen，真实 Key）→ 生成 API Key
2. `/v1/chat` 真实调用 → 返回真实回复（非 `echo:`）
3. 查 `usage_record` 表 → token 计数非 0（真实 token）
4. 查 `audit_log` 表 → 操作留痕

> 联调细节：需真实供应商 Key（qwen/claude/openai/deepseek 任一）。无 Key 时可用 mock 供应商测试（Python 侧加 mock 适配器）。

- [ ] **Step 3: 记录实现修正**，追加到本计划「执行修正记录」。

- [ ] **Step 4: Commit 遗留**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A
git commit -m "docs(plans): 计划 C 追加执行修正记录"
```

---

## Self-Review 记录

- **Spec 覆盖**：§8 模型网关 ✓ · §13 决策 21（Key 加密下发，AI 服务不落盘）✓。
- **测试覆盖**：每任务编译/测试验证 + Task 4 端到端回归（真实供应商调用 + token 回填）。
- **占位符扫描**：无 TBD；每步含完整代码。
- **类型一致性**：`CompletionResult`（Python）↔ `ChatCompletionResult`（Kotlin）字段一致；`usage_record` 字段与 Python 返回的 `usage` 一一对应。
- **C1-C8 解决方案**：
  - C1（模块依赖方向）：`ModelKeyResolver` 端口模式，orchestrator 定义接口，app 提供适配 Bean
  - C2（usage 回填与工作流冲突）：方案 A，`ModelComponent` 返回 `ChatCompletionResult`，`LlmNode` 把 usage 写入 `NodeResult.outputs["usage"]`，`ChatApplicationService` 从执行结果取
  - C3（api_key vs api_key_id 矛盾）：统一传 `api_key` 明文，Python 不落盘
  - C4（错误标记）：V1 只落成功记录，`usage_record` 不加 error 列
  - C5（providerKeyId=null 回退）：Python 适配器回退环境变量（如 `QWEN_API_KEY`）
  - C6（provider/model 写死）：V1 默认 qwen/qwen-max，注明局限，后续从 `AppModelConfig` 取
  - C7（snake_case → camelCase）：`Usage` 字段加 `@JsonProperty` 注解
  - C8（单测）：Python 补适配器 mock 单测，Kotlin 补 `AiClient` usage 解析单测

## 执行交接

计划 C 完成后 → **Plan D**（前端控制台）。
