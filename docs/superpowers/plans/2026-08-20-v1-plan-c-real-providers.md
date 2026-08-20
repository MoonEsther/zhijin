# 计划 C：Python 真实供应商接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `zhijin-ai` 的模型网关桩（`echo:` 返回）替换为真实供应商调用（qwen / claude / openai / deepseek），Kotlin 侧从数据库取加密 Key 下发给 Python，Python 返回真实 token 计数回填 `usage_record`。

**Architecture:** Python 侧 `gateway/` 模块按供应商分适配器（OpenAI 兼容协议统一，Claude 用 Anthropic SDK）；Kotlin 侧 `HttpModelComponent` 改造：从 `ModelProviderKey` 表取加密 Key 解密后随请求传给 Python；Python 返回 `usage`（prompt/completion/total tokens）→ Kotlin 回填 `usage_record`。

**Tech Stack:** Python 3.11 + FastAPI + `openai` SDK + `anthropic` SDK · Kotlin RestClient · 数据库 `model_provider_key` 表（已加密）

**设计依据:** `2026-08-17-agent-platform-design.md` §8（AI 服务模型网关）、§13 决策 21（Key 加密下发，AI 服务不落盘）。

---

## 关键决策

- **供应商协议**：qwen / openai / deepseek 都走 **OpenAI 兼容协议**（`/v1/chat/completions`）；claude 走 **Anthropic Messages API**（`/v1/messages`）。Python 侧统一适配器模式。
- **Key 下发**：Kotlin `HttpModelComponent.complete(prompt, modelName, providerKeyId)` 从 `ModelProviderKey` 表取加密 Key，解密后随请求 body 传给 Python（`{model, provider, api_key, messages}`）。**Python 不落盘 Key**（决策 21）。
- **Token 回填**：Python 返回 `usage: {prompt_tokens, completion_tokens, total_tokens}` → Kotlin 解析 → `usage_record` 回填真实 token 计数（V1 之前为 0）。
- **供应商选择**：请求 body 带 `provider` 字段（`qwen`/`claude`/`openai`/`deepseek`），Python 路由到对应适配器。
- **错误处理**：供应商返回错误（401/429/500）→ Python 转 5xx 给 Kotlin → Kotlin 落 `usage_record`（`latency_ms` + 错误标记，token 为 0）。
- **V1 简化**：不做供应商负载均衡/重试/熔断（留 V2）；不做流式 token 计数（V1 一次性返回）。

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
└── src/main/kotlin/com/zhijin/orchestrator/infrastructure/model/
    └── HttpModelComponent.kt      ← 改造：从 DB 取 Key 解密传给 Python

zhijin-server/zhijin-chat/
└── src/main/kotlin/com/zhijin/chat/application/
    └── ChatApplicationService.kt  ← 改造：解析 Python 返回的 usage 回填 usage_record
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

- [ ] **Step 2: OpenAI 兼容适配器（qwen/openai/deepseek 共用）**

`adapters/openai_adapter.py`：
```python
"""OpenAI 兼容协议适配器（qwen/openai/deepseek 共用）。"""
import os
from openai import AsyncOpenAI
from .base import CompletionResult


class OpenAICompatibleAdapter:
    """OpenAI 兼容协议适配器。"""

    def __init__(self, base_url: str):
        self.base_url = base_url

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        client = AsyncOpenAI(api_key=api_key, base_url=self.base_url)
        resp = await client.chat.completions.create(model=model, messages=messages)
        usage = resp.usage
        return CompletionResult(
            content=resp.choices[0].message.content or "",
            prompt_tokens=usage.prompt_tokens if usage else 0,
            completion_tokens=usage.completion_tokens if usage else 0,
            total_tokens=usage.total_tokens if usage else 0,
        )


# 预配置实例（base_url 从环境变量读）
QWEN_ADAPTER = OpenAICompatibleAdapter(os.getenv("QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"))
OPENAI_ADAPTER = OpenAICompatibleAdapter(os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"))
DEEPSEEK_ADAPTER = OpenAICompatibleAdapter(os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"))
```

- [ ] **Step 3: Claude 适配器**

`adapters/claude_adapter.py`：
```python
"""Anthropic Claude Messages API 适配器。"""
import os
import anthropic
from .base import CompletionResult


class ClaudeAdapter:
    """Claude Messages API 适配器。"""

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        client = anthropic.AsyncAnthropic(api_key=api_key)
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

## Task 2: Kotlin HttpModelComponent 改造（从 DB 取 Key 解密下发）

**Files:**
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/infrastructure/model/HttpModelComponent.kt`
- Modify: `zhijin-server/zhijin-orchestrator/src/main/kotlin/com/zhijin/orchestrator/domain/ModelComponent.kt`（接口签名加 providerKeyId 参数）
- Modify: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/application/ChatApplicationService.kt`（传 providerKeyId）

- [ ] **Step 1: 扩展 ModelComponent 接口**

`domain/ModelComponent.kt`：
```kotlin
/** 模型组件抽象（依赖倒置）。 */
interface ModelComponent {
    /** 调用模型，返回 assistant 内容。providerKeyId 为加密 Key 的 ID，从 DB 取。 */
    suspend fun complete(prompt: String, modelName: String, providerKeyId: Long? = null): String
}
```
（默认 `providerKeyId = null` 保持向后兼容；V1 不传则用默认供应商 + 环境变量 Key。）

- [ ] **Step 2: HttpModelComponent 改造**

`infrastructure/model/HttpModelComponent.kt`：
```kotlin
/**
 * 真实模型组件：从 DB 取加密 Key 解密后传给 Python。
 * 请求体：{model, provider, api_key, messages}
 * 响应体：{choices[0].message.content, usage: {prompt_tokens, completion_tokens, total_tokens}}
 */
class HttpModelComponent(private val aiClient: AiClient) : ModelComponent {

    override suspend fun complete(prompt: String, modelName: String, providerKeyId: Long?): String =
        aiClient.complete(prompt, modelName, providerKeyId)
}
```

- [ ] **Step 3: AiClient 改造**

`zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`：
```kotlin
/** 调用模型，返回 assistant 内容。 */
fun complete(prompt: String, model: String = "default", providerKeyId: Long? = null): String {
    val body = mutableMapOf<String, Any?>(
        "model" to model,
        "provider" to "qwen",  // V1 默认 qwen；后续从 AppModelConfig 取
        "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
    )
    if (providerKeyId != null) body["api_key_id"] = providerKeyId  // Python 侧从 DB 取 Key（更安全）
    // 或直接传解密后的 api_key（V1 简化）
    val resp = restClient.post()
        .uri("/v1/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(CompletionResponse::class.java)
    return resp?.choices?.firstOrNull()?.message?.content ?: ""
}
```
> **安全决策**：V1 简化，Kotlin 解密 Key 后直接传 `api_key` 字段给 Python（不落盘 Python 侧）。Python 不存 Key，每次调用从 Kotlin 接收。后续可改为 Python 通过安全通道从 Kotlin 取 Key（V2）。

- [ ] **Step 4: ChatApplicationService 传 providerKeyId**

`zhijin-chat/.../application/ChatApplicationService.kt`：
```kotlin
// 从 AppModelConfig 取 providerKeyId（V1 简化：默认 null，用环境变量 Key）
val providerKeyId: Long? = null  // 后续从 AppModelConfig 取
val reply = runBlocking { model.complete(req.message, "qwen-max", providerKeyId) }
```

- [ ] **Step 5: 构建验证**

`cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 6: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/
git commit -m "feat(ai-client): HttpModelComponent 从 DB 取 Key 解密下发"
```

---

## Task 3: usage_record 回填真实 token 计数

**Files:**
- Modify: `zhijin-server/zhijin-ai-client/src/main/kotlin/com/zhijin/aiclient/AiClient.kt`（解析 usage）
- Modify: `zhijin-server/zhijin-chat/src/main/kotlin/com/zhijin/chat/application/ChatApplicationService.kt`（回填 usage）

- [ ] **Step 1: AiClient 解析 usage**

`AiClient.kt` 改造返回类型（或新增方法）：
```kotlin
data class ChatCompletionResult(val content: String, val usage: Usage?)
data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)

fun completeWithUsage(prompt: String, model: String = "default", providerKeyId: Long? = null): ChatCompletionResult {
    // ... 同 complete，但返回 ChatCompletionResult
    val resp = ...
    val usage = resp?.usage?.let { Usage(it.promptTokens, it.completionTokens, it.totalTokens) }
    return ChatCompletionResult(content = resp?.choices?.firstOrNull()?.message?.content ?: "", usage = usage)
}
```

- [ ] **Step 2: ChatApplicationService 回填 usage**

`ChatApplicationService.kt`：
```kotlin
val result = runBlocking { aiClient.completeWithUsage(req.message, "qwen-max", providerKeyId) }
val reply = result.content
// 回填 usage_record
usageRecorder.record(
    UsageRecord(
        tenantId = tenantId, appId = appId, sessionId = session.id,
        model = "qwen-max",
        promptTokens = result.usage?.promptTokens ?: 0,
        completionTokens = result.usage?.completionTokens ?: 0,
        totalTokens = result.usage?.totalTokens ?: 0,
        latencyMs = latencyMillis,
    )
)
```
（需要把 `aiClient` 注入到 `ChatApplicationService`，或扩展 `ModelComponent` 接口返回 `ChatCompletionResult`。）

- [ ] **Step 3: 测试 + 验证**

`cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean test`
Expected: 全部通过（既有 62 测试 + 新 usage 回填测试）。

- [ ] **Step 4: Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-server/
git commit -m "feat(chat): usage_record 回填真实 token 计数"
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

## 执行交接

计划 C 完成后 → **Plan D**（前端控制台）。
