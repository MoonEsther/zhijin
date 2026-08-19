# 企业级智能体平台（zhijin）设计文档

> 状态：**草稿待审阅**　|　日期：2026-08-17　|　作者：Esther / Devin
>
> 本文档汇总前期讨论确认的设计决策，供评审后进入实现计划阶段。标注「✅已确认」为已敲定项，「❓待确认」为建议项，评审时请特别关注。

---

## 1. 项目定位与背景 ✅已确认

- **定位**：端到端一体化智能体平台——覆盖智能体「开发 → 编排 → 运行 → 评测 → 治理」全生命周期。
- **建设背景**：商业产品对外售卖（公有云 SaaS + 私有化交付双形态）。
- **对标/参考**：Dify（编排开发）、LangSmith（评测可观测）、企业 AI 门户（治理运营）作为**理念参考**；评测与可观测能力**全部自研**，不集成 LangSmith 等外部产品。
- **核心形态**：编排采用**纯工作流驱动**（与 Dify 编排形态一致），工作流图是无环有向图（DAG）；「Agent 自主循环」不作为独立模式，而是工作流里的一种节点类型。

## 2. 功能全景（8 大能力域）✅已确认

用户已确认 8 大能力域**全部纳入**：

| # | 能力域 | 核心内容 |
|---|---|---|
| 1 | 开发与编排 | 工作流编排、节点类型、Prompt 管理、工具注册（MCP/HTTP/代码） |
| 2 | 知识库 / RAG | 多数据源接入、文档解析分块、向量化、混合检索 |
| 3 | 模型网关 | 多供应商统一接入、路由、Key 管理、用量归属 |
| 4 | 运行与会话 | 会话管理、记忆、流式输出、异步长任务、并发限流 |
| 5 | 评测与质量 | 评测数据集、指标、A/B、回归测试、bad case 回放 |
| 6 | 可观测性 | 链路追踪、成本追踪、监控大盘 |
| 7 | 治理与安全 | 多租户隔离、RBAC、审计、脱敏、安全防护 |
| 8 | 平台运营 | 用户/SSO、模板市场、配额计费、部署形态 |

### 2.1 Skill / Tool / MCP 概念与层次

三者不是并列的三个功能，而是**能力的三层结构**：

| 概念 | 定位 | 平台落地 |
|---|---|---|
| **Tool（工具）** | 可执行的**最小单元**：调用 HTTP 接口 / 执行代码 / 查数据库 | `zhijin-tool` 模块，**V1 提供**（HTTP 工具 + 代码工具） |
| **MCP（协议）** | 标准化**接入通道**：连上一个 MCP Server，自动获得其暴露的全部工具 | 平台作为 MCP Client 接入，工具自动入库（**V2**） |
| **Skill（能力包）** | 可**复用**的能力封装：Prompt + 若干工具 + 可选子流程，供多个智能体共享 | Skill 管理 + 可被工作流引用（**V2/V3**） |

层次关系：

```
Skill（能力包 = Prompt + 工具组合 + 可选子流程）
   └─ 内部组合使用 ▼
Tool（可执行单元）
   ├─ 平台自定义工具（HTTP / 代码）
   └─ 来自 MCP Server 暴露的工具
MCP（接入协议，连接外部工具生态）
```

- 工具是底座；MCP 是「接入更多工具」的通道；Skill 是「把工具+提示词打包成可复用能力」。
- 编排引擎的**工具节点**既可调用单个 Tool，也可调用一个 Skill。

## 3. 总体架构 ✅已确认

两个可独立部署的服务 + 前端控制台，**不拆分微服务网格**（后续业务扩大再扩）。

```
                         ┌──────────────┐
   外部用户/客户端  ───▶ │    Nginx     │  (TLS / 静态资源 / 反向代理)
                         └──────┬───────┘
                                │
                 ┌──────────────┼──────────────┐
                 ▼                             ▼
      ┌───────────────────┐         ┌───────────────────┐
      │ 🟩 平台服务(Kotlin) │         │ 🟨 AI 服务(Python)  │
      │  (Spring Boot 3)  │         │  (FastAPI)        │
      │                    │ ◀────▶  │                   │
      │ 平台业务 + 编排引擎 │   Nacos │ 模型网关代理        │
      │ + 会话 + 工具执行   │  发现    │ + embedding/向量化 │
      │ + 计费 + 审计      │  HTTP   │ + 文档解析分块     │
      └───────────────────┘         │ + 评测跑批         │
                                    └───────────────────┘
                │                              │
                ▼                              ▼
      ┌──────────────────┐          ┌──────────────────┐
      │ PostgreSQL +      │          │ MinIO(对象存储)   │
      │ pgvector(向量)    │          │ 文档/附件/S3 兼容  │
      │ + Redis(会话/缓存) │          └──────────────────┘
      └──────────────────┘
            │
            ▼
      Redis Streams（异步任务：文档解析、评测跑批）
```

- **编排引擎放平台服务（Kotlin）**，Python 只承担 AI 重计算（模型网关、向量化、文档解析、评测跑批）。
- 服务间通过 Nacos 服务发现 + 内网 HTTP 调用。

## 4. 技术选型 ✅已确认

| 组件 | 选型 | 备注 |
|---|---|---|
| 平台服务 | **Kotlin** + Spring Boot 3.x + Spring Cloud Alibaba | 用户指定 Kotlin；Nacos 生态成熟 |
| AI 服务 | Python 3.11 + FastAPI | LangChain 仅作工具库，不强依赖框架 |
| 注册/配置中心 | **Nacos** | 服务发现 + 配置中心 |
| 主库 | PostgreSQL 16 | SaaS/私有化均友好 |
| 向量库 | **pgvector**（内嵌 PG） | 私有化交付最省事；向量接口抽象，数据量大再换 Milvus |
| 缓存/会话 | Redis 7 | 会话、限流、热点缓存 |
| 对象存储 | MinIO（S3 兼容） | 私有化自托管；公有云可切厂商 OSS/S3 |
| 异步任务 | 先 Redis Streams | 支撑 V1；量大了再上 RocketMQ/RabbitMQ |
| 前端 | React + **antd**（控制台） | Widget 已砍掉，V1 聊天只做开放 API |
| 部署形态 | 公有云 SaaS + 私有化（Docker Compose 起步） | ❓具体优先级待确认 |

## 5. 仓库结构（monorepo）✅已确认

```
zhijin/                      ← 总目录（monorepo，后续会承载多个项目）
├── zhijin-server/           ← 平台服务（Kotlin + Spring Boot）
├── zhijin-ai/               ← AI 服务（Python + FastAPI）
├── zhijin-web/              ← 前端控制台（React + antd）
└── docs/                    ← 文档
```

## 6. 平台服务模块划分（Kotlin）✅已确认

单个 Spring Boot 工程，内部按模块边界分。V1 部分模块合并（见表），业务扩大后再拆。

| 模块 | 职责 | V1 是否独立 |
|---|---|---|
| `zhijin-common` | 统一响应、异常、日志、工具类 | 是（基础） |
| `zhijin-framework` | Nacos 接入、数据库、Redis、安全框架、限流 | 是（基础） |
| `zhijin-tenant` | 多租户：租户上下文、数据隔离、租户管理 | 与 auth 合并 |
| `zhijin-auth` | ★ 认证中心（独立边界模块）：用户/角色/权限/Token，对外只暴露认证接口契约（login/logout/validate/refresh/userinfo），业务代码不触碰内部 | 与 tenant 合并 |
| `zhijin-app` | 应用管理：智能体 CRUD、模型配置、版本发布/灰度 | 是 |
| `zhijin-orchestrator` | ★ 编排引擎（见 §7） | 是（核心） |
| `zhijin-chat` | 会话运行时：会话管理、SSE 流式、长任务、记忆 | 是 |
| `zhijin-tool` | 工具系统：注册、MCP 接入、HTTP/代码工具 | 与 orchestrator 协同 |
| `zhijin-billing` | 计费配额：用量统计、配额控制 | 与 audit 合并 |
| `zhijin-audit` | 审计：操作留痕、安全事件 | 与 billing 合并 |
| `zhijin-ai-client` | 调用 AI 服务（Python）的封装层 | 是 |

### 6.1 认证中心设计（独立边界模块）

- **形态**：平台服务内 `zhijin-auth` 模块，逻辑上独立成「中心」。后期如需拆成独立服务，搬模块 + 加网络层即可，业务代码零改动。
- **职责**：用户、角色、权限（RBAC）、Token 签发/校验/刷新。
- **对外接口契约**（业务模块只能通过这些接口与认证中心交互，不触碰内部）：

  `login` / `logout` / `validate` / `refresh` / `userinfo`

- **调用关系**：
  - 管理端登录 → `login` 签发 JWT（Token 内携带租户信息）；
  - 客户系统调用开放 API → API Key 校验（Key 绑定租户），由过滤器完成；
  - 各业务模块权限校验 → 调 `validate` 或读取已解析的身份上下文；
  - 平台服务 → Python 内部调用时透传身份上下文（`X-Tenant-Id` / `X-User-Id` header），Python 不重新鉴权。

## 7. 编排引擎设计（工作流驱动）✅已确认

### 7.1 核心概念

- **工作流定义（DSL）**：JSON 描述的一张 DAG 图（节点 + 边）。开发者在画布上画，后端存定义、执行。
- **节点类型**（V1 从简，V2 扩充）：

| 节点 | 说明 | 版本 |
|---|---|---|
| LLM 节点 | 调用模型，输出写变量 | V1 |
| 工具节点 | 执行已注册工具（HTTP/代码） | V1 |
| 分支节点（switch） | 按变量条件路由 | V1 |
| 变量节点 | 变量赋值/转换 | V1 |
| 知识检索节点 | 调 AI 服务检索，片段注入上下文 | V2 |
| Agent 节点 | 模型自主循环（ReAct），最大步数兜底 | V2 |
| 迭代节点 | 对数组逐项执行子流程 | V2 |
| 代码节点 | 执行用户代码片段 | V2 |

- **图规则**：工作流图无环（DAG）。循环不画在图上，用「迭代节点」或「Agent 节点内部循环」实现。

### 7.2 DSL 示例（售前咨询助手）

```json
{
  "id": "wf-sales-assistant",
  "start": "intent-classify",
  "nodes": [
    { "id": "intent-classify", "type": "llm",
      "prompt": "判断用户意图：询价/售后/其他", "output": "$intent" },
    { "id": "route", "type": "switch",
      "branches": [
        { "when": "$intent == '询价'", "goto": "query-price" },
        { "when": "$intent == '售后'", "goto": "query-order" },
        { "default": "create-ticket" } ] },
    { "id": "query-price", "type": "tool", "tool": "query_product_price",
      "args": { "sku": "$sku" } },
    { "id": "query-order", "type": "tool", "tool": "query_order_status",
      "args": { "orderNo": "$orderNo" } },
    { "id": "create-ticket", "type": "tool", "tool": "create_ticket" },
    { "id": "final", "type": "llm", "prompt": "根据工具结果组织回答" }
  ]
}
```

### 7.3 引擎内部结构（Kotlin 包结构）

```
zhijin-orchestrator/
├── workflow/     ← 工作流定义模型：节点(Node) + 边(Edge)、DAG 校验、DSL 解析器
├── executor/     ← 节点执行器注册表：LlmExecutor / ToolExecutor / SwitchExecutor
│                  AgentExecutor / KnowledgeExecutor / IteratorExecutor / CodeExecutor
├── scheduler/    ← 工作流调度：按 DAG 拓扑执行、分支路由、并行、重试、超时
└── context/      ← 变量区($变量读写)、会话历史、记忆注入
```

**关键设计：执行器注册表。** 每种节点类型 = 一个实现同一接口的 Executor 类：

```kotlin
interface NodeExecutor {
    fun execute(ctx: NodeContext, node: NodeDef): NodeResult
}
```

调度器只做一件事：找到当前节点的 Executor → 执行 → 根据结果决定下一步 → 继续。新增节点类型 = 新增一个 Executor 注册，不动调度器。

### 7.4 运行机制

1. 收到用户消息 → 创建上下文（变量区 + 会话历史 + 记忆）
2. 从 `start` 节点开始，按 DAG 拓扑逐节点执行
3. 每个节点输出写入变量区（`$变量`），供后续节点读取
4. LLM/检索节点 → 调 AI 服务；工具节点 → 本地执行
5. 走到终点节点 → 生成最终回复，流式返回

## 8. AI 服务模块划分（Python）✅已确认

```
zhijin-ai/
├── app/
│   ├── main.py              ← FastAPI 入口、中间件(鉴权/日志/异常)
│   ├── config.py            ← 配置（从 Nacos 拉取）
│   ├── gateway/             ← ★ 模型网关
│   │   ├── routes.py        ← OpenAI 兼容: /v1/chat/completions、/v1/embeddings
│   │   ├── providers/       ← 供应商适配器: openai / anthropic / qwen / local
│   │   └── routing.py       ← 模型路由、切换、超时/重试策略
│   ├── rag/                 ← RAG: parse.py(解析) / chunk.py(分块)
│   │                        ←      embed.py(向量化) / retrieve.py(混合检索+重排)
│   ├── eval/                ← 评测: runner.py(批量) / metrics.py(指标) / judge.py(LLM裁判)
│   ├── common/              ← 日志、鉴权、限流、错误处理
│   └── api/                 ← 暴露给平台服务的接口
```

### 暴露给平台服务的核心接口

| 接口 | 用途 |
|---|---|
| `POST /v1/chat/completions` | LLM 调用（OpenAI 兼容格式，SSE 流式） |
| `POST /v1/embeddings` | 文本向量化 |
| `POST /rag/documents/parse` | 文档解析 + 分块（异步，回调结果） |
| `POST /rag/retrieve` | 混合检索（query → 相关片段） |
| `POST /eval/run` | 提交批量评测任务 |
| `GET /eval/tasks/{id}` | 查询评测进度/结果 |

## 9. 数据访问规则 ✅已确认

> **谁拥有 schema，谁直接连库；别人一律通过 API 访问。**

| 数据 | 所在库 | 谁直接连 | 谁通过 API 访问 |
|---|---|---|---|
| 平台业务数据（租户/应用/会话/账单/审计） | PG `platform` schema | 平台服务(Kotlin) | AI 服务（不直连） |
| AI 数据（向量表/知识库索引） | PG `ai_kb` schema | **AI 服务直连** | 平台服务（调 AI API） |
| 缓存/流/限流 | Redis | 各自用各自 key 前缀 | — |

- 模型供应商 Key 由平台服务持有并加密存储，调用时下发给 AI 服务（AI 服务不落盘 Key）。❓Key 管理方式待最终确认。

### 9.1 多租户设计

- **隔离模型**：共享库 + `tenant_id` 行级隔离（逻辑隔离）。所有业务表带 `tenant_id`，查询由 ORM 拦截器**自动注入 `WHERE tenant_id = ?`**。
- **租户识别**：
  - 管理端：登录用户的 JWT 内携带租户信息；
  - 客户系统：调用开放 API 用 API Key，Key 与租户绑定。
- **隔离覆盖范围**：PostgreSQL `platform` schema 全部业务表；Redis key 前缀 `tenant:{id}:`；Python 侧 `ai_kb` 向量数据同样带 `tenant_id`。
- **增强项（V3）**：对高安全/合规要求的租户，提供独立 schema 或独立实例（物理隔离）作为可选能力。

## 10. 关键数据流 ✅已确认

### 数据流 A：管理端——智能体「被创建」

```
管理员登录控制台(React+antd)
  → 新建应用 → 配置模型(供应商/Key/路由)
  → 画布画工作流(节点+连线) → 保存草稿
  → 试运行调试 → 发布
  → 生成【正式版本】+【API Key】→ 交付客户
```

### 数据流 B：运行端——用户对话（核心链路）

```
① 客户系统调 平台服务 API: POST /v1/chat {appId, sessionId, message}
② 平台服务鉴权(租户+API Key) → 取/建会话(Redis)
   → 加载该应用发布版本的工作流定义 + 模型配置
③ 编排引擎启动工作流：
     上下文区写入: 用户消息 + 会话历史 + 记忆
     ┌─── 按 DAG 逐节点执行 ───┐
     │ LLM节点  → AI服务 /v1/chat/completions(SSE流式)  输出写 $变量
     │ 工具节点 → 平台服务执行(HTTP/代码/MCP)            结果写 $变量
     │ 检索节点 → AI服务 /rag/retrieve → 片段注入 LLM 上下文
     │ 分支节点 → 读 $变量决定走向
     └─────────────────────────┘
     直到终点节点 → 生成最终回复
④ 平台服务 SSE 流式把回复推给客户系统 → 客户页面实时展示
⑤ 全程落账：token用量/工具调用/延迟 → 用量表 + 审计表 + trace日志
```

## 11. 分期落地计划 ❓建议项（请确认）

### V1 —— 最小可交付（可卖给第一批客户）

- 多租户骨架 + 账号 + RBAC（基础）
- 应用管理：创建、模型配置、发布、API Key
- 工作流编排：LLM / 工具 / 分支 / 变量节点（线性 + 分支，**无画布**，前端先做表单式配置）❓
- 模型网关：多供应商、SSE 流式
- 会话 + 开放 API（聊天）
- 基础用量统计 + 审计
- 可观测：结构化日志 + traceId 透传
- 控制台：应用管理 + 简单配置页

### V2 —— 核心竞争力

- 可视化编排画布（前端拖拽 ↔ 后端 DSL 双向）
- 完整节点：Agent / 迭代 / 知识检索 / 代码
- 知识库 RAG：文档解析、分块、向量化、混合检索
- 评测与质量：数据集、指标、A/B、回归
- 可观测增强：链路追踪、成本归集、监控大盘
- 工具生态：MCP 协议接入 + Skill 能力包管理

### V3 —— 商业化完整

- 模板市场
- 计费 / 配额 / 结算
- 多渠道：企微 / 钉钉 / Slack
- 完整安全合规：脱敏、内容审核、Prompt 注入防护
- 私有化一键部署（Docker Compose / K8s）
- 企业 SSO 对接

## 12. 测试、可观测性与错误处理 ❓建议项（请确认）

- **测试**：平台服务 JUnit5 + Testcontainers；AI 服务 pytest；端到端接口测试。
- **错误处理**：统一异常模型；网关/编排层超时、重试、熔断；异步任务幂等。
- **可观测**：结构化日志（含 traceId，跨 Kotlin/Python 透传）；指标采集 Prometheus；链路追踪先靠 traceId + 日志关联，后续按需上 OpenTelemetry / SkyWalking。

## 13. 已确认决策清单 ✅

| # | 决策 |
|---|---|
| 1 | 定位：端到端一体化智能体平台，商业产品对外售卖 |
| 2 | 8 大能力域全部纳入 |
| 3 | 编排形态：纯工作流驱动（Dify 风格），DAG，Agent 是节点类型 |
| 4 | 服务数：2 个（平台服务 Kotlin + AI 服务 Python），不拆微服务网格 |
| 5 | 后端语言：**Kotlin** + Spring Boot 3 + Spring Cloud Alibaba（Nacos） |
| 6 | AI 服务：Python 3.11 + FastAPI |
| 7 | 存储：PostgreSQL 16 + pgvector、Redis 7、MinIO、Redis Streams |
| 8 | 前端：React + antd（控制台）；Widget 不做，V1 只做开放 API |
| 9 | 编排引擎在平台服务内，执行器注册表 + 调度器设计 |
| 10 | 数据访问：谁拥有 schema 谁直连库，platform→Kotlin、ai_kb→Python |
| 11 | 仓库：monorepo，`zhijin-server` / `zhijin-ai` / `zhijin-web` |
| 12 | 评测与可观测全部自研，外部产品（LangSmith 等）仅作理念参考，不引入依赖 |
| 13 | 认证授权中心：平台服务内独立模块 `zhijin-auth`，逻辑独立成中心，后期可搬成独立服务零重构 |

## 14. 开放问题 ❓待你确认

1. 部署形态优先级：先 SaaS 还是先私有化？（影响 Nacos/MinIO/部署脚本优先级）
2. 模型供应商清单：首发支持哪几家？（OpenAI / Claude / 通义 / DeepSeek / 本地 Ollama-vLLM …）
3. 编排 V1 是否真的不要画布？（表单式配置 vs 最小画布）
4. 模型 Key 管理：确认「平台服务持有加密、调用时下发，AI 服务不落盘」。
5. 评测 V2 首发指标集：先做哪些？（相关性 / 准确率 / 幻觉 / 延迟…）
6. 目标客户行业是否会影响模板市场设计？
