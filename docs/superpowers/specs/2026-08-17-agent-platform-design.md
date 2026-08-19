# 企业级智能体平台（zhijin）设计文档

> 状态：**草稿待审阅**　|　日期：2026-08-17　|　作者：Esther / Devin
>
> 本文档汇总前期讨论确认的设计决策，供评审后进入实现计划阶段。标注「✅已确认」为已敲定项，「❓待确认」为建议项，评审时请特别关注。

---

## 1. 项目定位与背景 ✅已确认

- **定位**：端到端一体化智能体平台——覆盖智能体「开发 → 编排 → 运行 → 评测 → 治理」全生命周期。
- **建设背景**：商业产品对外售卖（公有云 SaaS + 私有化交付双形态）。
- **对标/参考**：Dify（编排形态）、Coze Studio（开源编排引擎实现，Apache 2.0，已实际研读）、LangSmith（评测可观测）、企业 AI 门户（治理运营）作为**理念参考**；评测与可观测能力**全部自研**，不集成 LangSmith 等外部产品。
- **架构基线**：平台整体（模块划分、工作流引擎内部结构、节点模型、会话运行模型）**全面参考开源 Coze Studio**（字节，Apache 2.0），**仅实现语言 Go → Kotlin**；模型/向量等重计算按我方两服务架构放 Python。
- **术语约定**：本文档及后续讨论中「LangChain」统一指代 LangChain 生态体系（含 LangChain / LangGraph / LangSmith）；引用到具体库时按实际名称（如编排用 LangGraph）。
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
      ┌──────────────────┐          ┌──────────────────────┐
      │ PostgreSQL        │          │ Elasticsearch         │
      │ + Redis(会话/缓存) │          │ (向量+全文, AI服务直连) │
      └──────────────────┘          └──────────────────────┘
            │
            ▼
      Redis Streams（异步任务：文档解析、评测跑批）

      ┌──────────────────┐
      │ MinIO(对象存储)   │
      │ 文档/附件/S3 兼容 │
      └──────────────────┘
```

- **编排引擎放平台服务（Kotlin）**，Python 只承担 AI 重计算（模型网关、向量化、文档解析、评测跑批）。
- 服务间通过 Nacos 服务发现 + 内网 HTTP 调用。
- **对外访问原则**：前端 / 客户系统的所有请求**只走平台服务（Kotlin，唯一对外入口）**；Python 服务不对外、不暴露公网。Kotlin 在业务编排中**按需调用** Python（经 `zhijin-ai-client`），仅流式聊天（SSE）场景由 Kotlin 将 Python 的流式响应透传回前端。统一入口带来统一鉴权 / 租户上下文 / 限流 / 审计，Python 保持无状态。

### 3.1 架构基线：全面参考 Coze Studio

本平台以字节开源 **Coze Studio**（`coze-dev/coze-studio`，Apache 2.0）为**全面参考基线**：除实现语言（Go → Kotlin）与「模型/向量等重计算放 Python」外，**平台模块划分、工作流引擎内部结构、节点模型、会话运行模型完整对齐**，不自造轮子式的拍脑袋设计。

**分层对照**（Coze 用 DDD 四层 + 跨域层 + 基础设施层）：

| 层 | Coze Studio（Go） | zhijin（Kotlin 平台服务） |
|---|---|---|
| 接口层 | `api/model`（admin/app/conversation/workflow/...） | 各模块 controller + DTO |
| 应用层 | `application` | 各模块 service |
| 领域层 | `domain`：agent/app/conversation/knowledge/memory/plugin/prompt/template/user/workflow | 各领域模块 |
| 跨域层 | `crossdomain`：agentrun/message/variables/database/... | 跨模块协同服务 |
| 基础设施 | `infra`：orm/es/cache/eventbus/idgen/coderunner/sqlparser/storage/sse/checkpoint | zhijin-framework + 中间件 |
| 图运行时 | CloudWeGo **Eino** compose（Graph/State/流） | **自研等价图运行时**（Kotlin，见 §7.3） |
| AI 重计算 | Go 内联（embedding/es/document/modelmgr） | **移入 Python AI 服务**（模型网关/向量化/文档解析/检索/评测） |

> **关键原则（两服务为何不破坏对齐）**：Coze 引擎的模型/检索/文档解析均为**组件抽象**（Eino model component），引擎只依赖抽象。我方把这三类组件默认实现为「经 `zhijin-ai-client` 调 Python」，引擎结构因此与 Coze 完全对齐，仅组件实现走远程。该抽象同时保留「个别节点 Kotlin 内联实现」的替换弹性。唯一代价是 SSE 流式透传多一跳，由 OpenAI 兼容格式 + SSE 事实标准摊薄，V1 需重点保证该链路正确。

**领域模块映射**：

| Coze Studio 域 | 职责 | zhijin 模块 |
|---|---|---|
| workflow | 工作流定义/画布/执行 | zhijin-orchestrator |
| app + agent | 应用(Agent)管理、发布、版本 | zhijin-app |
| conversation + message + memory | 会话、消息、记忆 | zhijin-chat |
| plugin | 工具插件开发/接入 | zhijin-tool |
| knowledge + search | 知识库、检索 | 知识库域（数据/算法在 Python） |
| prompt | Prompt 管理 | zhijin-app（Prompt 库） |
| template | 应用/工作流模板 | 模板市场（V3） |
| user + permission + openauth + passport | 用户、权限、开放鉴权、登录 | zhijin-auth + zhijin-tenant |
| upload + resource | 上传、资源 | zhijin-resource（MinIO/S3） |
| admin/config | 平台配置 | 平台运营 |
| marketplace | 模板市场 | 模板市场（V3） |
| connector | 渠道接入（企微/钉钉/Slack） | 多渠道（V3） |
| —（Coze 未开源） | 多租户隔离、计费配额、评测、可观测大盘 | zhijin-tenant/billing + Python eval + 可观测 |

## 4. 技术选型 ✅已确认

| 组件 | 选型 | 备注 |
|---|---|---|
| 平台服务 | **Kotlin** + Spring Boot 3.x + Spring Cloud Alibaba | 用户指定 Kotlin；Nacos 生态成熟 |
| AI 服务 | Python 3.11 + FastAPI（依赖管理 **uv**） | 以 LangChain 生态为参考/工具库（LangChain / LangGraph 按实际场景选用），不强依赖框架 |
| 注册/配置中心 | **Nacos** | 服务发现 + 配置中心 |
| 主库 | PostgreSQL 16 | SaaS/私有化均友好 |
| 检索存储（向量+全文） | **Elasticsearch（ES 8.x）** | 参考 RAGFlow 选型：同时支持向量检索（dense knn）与全文检索（BM25），混合检索天然一体；企业运维熟悉、私有化友好；检索访问接口抽象，保留切换弹性 |
| 缓存/会话 | Redis 7 | 会话、限流、热点缓存 |
| 对象存储 | **MinIO**（S3 兼容接口） | 代码统一走 AWS S3 SDK（S3 协议），不依赖 MinIO 私有 API；私有化默认 MinIO，公有云可平滑切换阿里 OSS / 腾讯 COS / AWS S3，无需改代码 |
| 异步任务 | 先 Redis Streams | 支撑 V1；量大了再上 RocketMQ/RabbitMQ |
| 前端 | React + **TypeScript** + **antd**（控制台） | 全程 TS 严格模式；Widget 已砍掉，V1 聊天只做开放 API |
| 部署形态 | **Docker Compose 部署（先行）** | 全栈中间件 + 两个服务用 docker-compose 一键编排；私有化交付同套方案；后续按需扩展 K8s（大规模私有化 / 公有云 SaaS） |

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
- **技术实现**：基于 **Spring Authorization Server（OAuth 2.1 / OIDC 1.0）**；用户身份、角色、权限（RBAC）使用自有用户体系，授权协议层由 SAS 提供。
- **对外接口契约**（业务模块只能通过这些接口与认证中心交互，不触碰内部）：

  `login` / `logout` / `validate` / `refresh` / `userinfo`

- **三方平台接入（OAuth Client）**：第三方平台在平台注册 client（`client_id` / `client_secret`），通过授权码（Authorization Code + PKCE）、客户端凭证（Client Credentials，M2M 场景）、刷新令牌等方式获取 access token，调用平台开放 API；提供 OIDC Discovery（`/.well-known/openid-configuration`）与 JWKS 端点。
- **开放 API 两种鉴权方式**：
  - 简单场景：静态 **API Key**（绑定租户），由过滤器校验；
  - 三方平台正式对接：**OAuth 2.0 access token**（可吊销、可审计、可区分来源）。
- **调用关系**：
  - 管理端登录 → `login` 签发 access token（Token 内携带租户信息）；
  - 各业务模块权限校验 → 调 `validate` 或读取已解析的身份上下文；
  - 平台服务 → Python 内部调用时透传身份上下文（`X-Tenant-Id` / `X-User-Id` header），Python 不重新鉴权。

## 7. 编排引擎设计（工作流驱动）✅已确认

### 7.1 核心概念

- **产品形态**：Dify / Coze 式**可视化、无代码画布工作流**——开发者在画布上拖拽节点、连线、配置参数，工作流定义以 JSON（DSL）保存到后端，引擎按图执行。Dify/Coze 的引擎均为自研，无现成框架可用，故本平台自研引擎（DSL + 执行器 + 画布），仅参考其产品形态。
- **工作流定义（DSL）**：JSON 描述的一张 DAG 图（节点 + 边）。开发者在画布上画，后端存定义、执行。
- **显式边（edges）**：DSL 顶层**显式**列出边（`from → to`，条件分支边带 `port`），边决定**执行拓扑**；节点输入引用（`{{node_id.output_key}}`）只负责**数据绑定**，两者分离（参照 Coze Studio）。
- **节点类型**（对齐 Coze Studio 节点集，V1 从简、V2/V3 补齐）：

| 节点 | 说明 | 对应 Coze 节点 | 版本 |
|---|---|---|---|
| 开始 / 结束节点 | 工作流入口/出口，定义输入输出参数 | Start / End | V1 |
| LLM 节点 | 调用模型生成回复；内置节点内自循环（canContinue） | LLM | V1 |
| 工具节点 | 执行已注册工具（HTTP/代码/插件） | Api(Plugin) | V1 |
| 分支节点（if/switch） | 按变量条件路由，分支条件支持多条件与或组合 | If | V1 |
| 变量节点 | 变量赋值 / JSON 序列化 / 反序列化 | Variable / AssignVariable / JsonSerialization / JsonDeserialization | V1 |
| 数据库节点 | 执行 SQL（读写/查库，经 SQL 解析与权限控制） | Database | V1/V2 |
| 代码节点 | 执行用户代码片段（沙箱执行） | Code | V2 |
| 知识检索节点 | 数据集检索，片段注入上下文 | Dataset | V2 |
| Agent 节点 | 模型自主循环（ReAct），最大步数兜底 | LLM canContinue + 工具（Coze 用 Eino react 编排） | V2 |
| 迭代节点 | 对数组逐项执行子流程 | Batch / Loop | V2 |
| 意图识别节点 | LLM 判断输入路由到分支 | Intent | V2 |
| 消息节点 | 对话流中生成消息、多轮交互 | Message / Question / CreateMessage | V3 |
| 图片生成节点 | 文生图 | ImageGenerate | V3 |

- **图规则**：工作流图无环（DAG）。循环不画在图上，用「迭代节点」或「Agent 节点内部循环」实现。
- **节点接口**：每个节点（无论内置/自定义）统一带 **输入接口 + 输出接口**，这是节点与外界唯一的交互契约：
  - **输入（inputs）**：声明输入参数 `{ key, 类型, 必填, 默认值 }`；参数值来源三选一——常量、上游节点输出引用 `{{node_id.output_key}}`、会话/全局变量 `$var`。执行前做引用解析 + 必填/类型校验。
  - **输出（outputs）**：声明输出字段 `{ key, 类型 }`；节点执行结果按 `{{node_id.output_key}}` 写入变量区，供下游节点引用。
  - 接口的作用：画布据此渲染节点**端口**与连线、DSL 据此做**静态校验**、自定义节点据此定义参数 schema（与 §7.5 一致）、调试/观测据此记录每节点的**输入输出快照**。

### 7.2 DSL 示例（售前咨询助手）

```json
{
  "id": "wf-sales-assistant",
  "start": "intent-classify",
  "nodes": [
    { "id": "intent-classify", "type": "llm",
      "inputs": { "model": "gpt-4o",
                  "prompt": "判断用户意图：询价/售后/其他",
                  "history": "$session.history" },
      "outputs": [ { "key": "intent", "type": "string" } ] },

    { "id": "route", "type": "switch",
      "inputs": { "condition": "{{intent-classify.intent}}" },
      "branches": [
        { "when": "== '询价'", "goto": "query-price" },
        { "when": "== '售后'", "goto": "query-order" },
        { "default": "create-ticket" } ] },

    { "id": "query-price", "type": "tool",
      "inputs": { "tool": "query_product_price",
                  "params": { "sku": "$session.sku" } },
      "outputs": [ { "key": "price", "type": "number" },
                   { "key": "stock", "type": "number" } ] },

    { "id": "query-order", "type": "tool",
      "inputs": { "tool": "query_order_status",
                  "params": { "orderNo": "$session.orderNo" } },
      "outputs": [ { "key": "status", "type": "string" } ] },

    { "id": "create-ticket", "type": "tool",
      "inputs": { "tool": "create_ticket" },
      "outputs": [ { "key": "ticketId", "type": "string" } ] },

    { "id": "final", "type": "llm",
      "inputs": { "model": "gpt-4o",
                  "prompt": "根据工具结果组织回答",
                  "toolResult": "{{query-price.price}}" },
      "outputs": [ { "key": "reply", "type": "string" } ] }
  ],
  "edges": [
    { "from": "intent-classify", "to": "route" },
    { "from": "query-price", "to": "final" },
    { "from": "query-order", "to": "final" },
    { "from": "create-ticket", "to": "final" }
  ]
}
```

> 变量引用约定：`{{node_id.output_key}}` = 引用上游节点某个输出字段；`$var` = 会话/全局变量（如 `$session.history`、`$session.sku`）。所有引用在节点执行前由引擎统一解析、校验。边（edges）**显式存储**于 DSL 顶层：线性连线用 `edges`，条件分支用 switch 节点的 `branches[].goto`（对应 Coze 的 If 分支 `next_node_id`）；边管拓扑、输入引用管数据绑定，二者分离。

### 7.3 引擎内部结构（Kotlin 包结构）

```
zhijin-orchestrator/
├── canvas/       ← 画布层(等价 Coze vo.Node)：节点/边/layout/nodeMeta 画布模型，DSL 解析与序列化
├── adaptor/      ← 适配器层(等价 Coze NodeAdaptor)：画布节点 → 运行时 NodeSchema，含静态校验
├── schema/       ← 运行时定义(等价 Coze NodeSchema/WorkflowSchema)：Configs、InputTypes/InputSources、
│                  OutputTypes、ExceptionConfigs、StreamConfigs、Branches
├── execute/      ← 执行引擎(等价 Coze Eino compose)：图构建、拓扑执行、字段填充、状态流转、流式
├── nodes/        ← 各节点执行器(等价 Coze nodes/)：llm / tool / code / dataset / if / loop / batch /
│                  variable / database / intent / json / ...
└── context/      ← 变量区({{node_id.output_key}} 引用解析 + $会话/全局变量读写)、会话历史、记忆注入
```

**关键设计：执行器注册表 + 节点能力接口。** 每种节点类型 = 一个实现能力接口的 Executor 类，注册进注册表（参照 Coze Studio 的 `RegisterNodeAdaptor`）：

```kotlin
// 节点执行器能力接口（多态）：普通节点实现 invoke，流式节点实现 stream，按需实现
interface NodeExecutor {
    suspend fun invoke(ctx: NodeContext, schema: NodeSchema): NodeResult         // 非流 → 非流
    suspend fun stream(ctx: NodeContext, schema: NodeSchema): Flow<NodeEvent>    // 非流 → 流式(LLM/Agent)
    suspend fun transform(ctx: NodeContext, schema: NodeSchema): Flow<NodeEvent> // 流式 → 流式(迭代/聚合)
}

// 节点级执行配置：超时/重试/出错降级（参照 Coze 的 settingOnError）
data class NodeExecConfig(
    val timeoutMs: Long = 60_000,               // 单节点超时，0=不设
    val maxRetry: Int = 0,                       // 最大重试次数
    val onError: ErrorProcessType = THROW,       // THROW / RETURN_DEFAULT
    val dataOnErr: Any? = null,                  // 出错时返回的降级值(与 RETURN_DEFAULT 搭配)
)

// 流式能力声明：是否真正产生流式输出 / 是否要求流式输入
data class StreamCapability(val canGenerateStream: Boolean, val requireStreamInput: Boolean)
```

调度器只做一件事：找到当前节点的 Executor → 按能力接口执行 → 根据结果决定下一步 → 继续。新增节点类型 = 新增一个 Executor 注册，不动调度器。

### 7.4 运行机制

1. 收到用户消息 → 创建上下文（变量区 + 会话历史 + 记忆）
2. 从 `start` 节点开始，按 DAG 拓扑逐节点执行
3. 每个节点执行前：按**输入接口**解析入参（常量直用、`{{node_id.output_key}}` 从变量区取值、`$var` 取会话/全局变量），做必填 + 类型校验
4. 节点执行：LLM/检索节点 → 调 AI 服务；工具节点 → 本地执行；结果按**输出接口**写入变量区，供下游引用
5. 走到终点节点 → 聚合输出为最终回复，流式返回

### 7.5 节点类型分级与自定义节点（权限控制）

节点类型参考 Dify / Coze 画布节点面板，分两级：

| 级别 | 来源 | 维护 | 使用范围 | 权限 |
|---|---|---|---|---|
| **内置节点** | 系统提供 | 平台管理员 | 所有租户 | 只读，平台统一维护（升级/下架） |
| **自定义节点** | 用户/租户创建 | 租户管理员 | 本租户（跨租户共享 V3） | 按角色授权：创建 / 编辑 / 发布 / 停用 |

- **自定义节点** = 可复用的节点类型包：定义输入/输出参数 schema + 实现方式，实现方式三选一：
  1. 封装为调用某个已注册的 HTTP 工具；
  2. 封装为执行一段代码；
  3. 封装为一段子工作流（内部由基础节点组合，即「模板节点」，也是 Skill 的底层形态）。
- **引擎侧实现**：执行器注册表内，内置节点 = 代码注册的 Executor；自定义节点 = 统一 `CustomNodeExecutor`，按节点类型注册表数据分发执行。
- **节点类型注册表（数据表）**：存储内置 + 自定义节点的定义、参数 schema、权限、版本、启用状态；工作流 DSL 仅引用节点类型 id，不区分内置/自定义。

### 7.6 全面参考基线：Coze Studio 引擎内部对照

已研读字节开源版 Coze Studio（`coze-dev/coze-studio`，Apache 2.0，Go 单体 + React，底层图运行时用 CloudWeGo **Eino**）。Eino 是 Go 库不可直接复用，故自研等价图运行时；引擎内部**逐层对齐**其结构：

| Coze Studio 做法 | 采纳结论 |
|---|---|
| 画布节点 `vo.Node` → `NodeAdaptor` 适配器 → 运行时 `NodeSchema` 三层分离 | ✅ 完全对齐（见 §7.3 包结构） |
| 节点接口多态：`Invoke` / `Stream` / `Transform`（+带选项 WOpt） | ✅ 执行器拆为 invoke/stream/transform 多态能力（见 §7.3） |
| 每节点类型 `RegisterNodeAdaptor(NodeType, factory)` 注册表 | ✅ 与执行器注册表一致 |
| 显式 `edges`（sourceNodeID → targetNodeID，分支带 port） | ✅ 边显式存储管拓扑；输入引用只管数据绑定 |
| 参数 `{ name, type, value: literal \| ref }`，ref 指向 `{blockID, name}` | ✅ 即 `{{node_id.output_key}}`，已一致 |
| `TypeInfo` 递归类型（对象 properties / 数组 elem_type / 文件 file_type） | ✅ 输入输出类型用递归 TypeInfo |
| 每节点 `settingOnError { timeoutMs, retryTimes, processType }` | ✅ 节点级超时/重试/出错降级（见 §7.3） |
| `StreamConfig { can_generates_stream, require_streaming_input }` | ✅ 流式能力元数据 |
| `meta.position {x,y}` + `nodeMeta` 画布元信息随 schema 持久化 | ✅ DSL 带 layout / 画布元信息字段 |
| 复合节点（CompositeNode 父子结构）：Batch/Loop 内嵌子节点 | ✅ 迭代节点内部为子图 |
| 运行模型（`conversation/run.thrift`）：conversation_id + query + 富内容（text/image/audio/video/file/mix）+ 事件（message/done/error） | ✅ 会话运行与 SSE 事件对齐 |
| LLM 节点内置 `canContinue` + `loopPrompt*`（节点内自循环）；`Batch/Loop` 节点做数组迭代 | ✅ 验证决策16：循环在节点内实现 |
| `SchemaType`: DAG（废弃）→ **FDL**（现行，Eino 流描述语言） | ✅ V1 DSL 直接按 FDL 思想设计，不走 DAG 老路 |
| 版本模型：`workflow_version` SemVer + draft/submit/publish 三阶段 commit | 📋 记入 V2/V3 版本管理 |

## 8. AI 服务模块划分（Python）✅已确认

```
zhijin-ai/
├── app/
│   ├── main.py              ← FastAPI 入口、中间件(鉴权/日志/异常)
│   ├── config.py            ← 配置（从 Nacos 拉取）
│   ├── gateway/             ← ★ 模型网关
│   │   ├── routes.py        ← OpenAI 兼容: /v1/chat/completions、/v1/embeddings
│   │   ├── providers/       ← 供应商适配器: openai / anthropic / qwen / deepseek（本地 Ollama-vLLM 留 V2）
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
| AI 数据（向量 / 全文 / 知识库索引） | **Elasticsearch** + PG `ai_kb` schema（知识库元数据） | **AI 服务直连** | 平台服务（调 AI API） |
| 缓存/流/限流 | Redis | 各自用各自 key 前缀 | — |

- 模型供应商 Key 由平台服务**持有并在数据库加密存储**（AES-256-GCM 静态加密），**每次调用时直接从数据库查询**、解密后透传给 AI 服务；AI 服务不落盘、不缓存 Key（V1 直接查库，不引入独立 Key 管理服务/KMS）。

### 9.1 多租户设计

- **隔离模型**：共享库 + `tenant_id` 行级隔离（逻辑隔离）。所有业务表带 `tenant_id`，查询由 ORM 拦截器**自动注入 `WHERE tenant_id = ?`**。
- **租户识别**：
  - 管理端：登录用户的 JWT 内携带租户信息；
  - 客户系统：调用开放 API 用 API Key，Key 与租户绑定。
- **隔离覆盖范围**：PostgreSQL `platform` schema 全部业务表；Redis key 前缀 `tenant:{id}:`；Elasticsearch 索引数据带 `tenant_id` 字段（按租户过滤），`ai_kb` 元数据同样带 `tenant_id`。
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
- 工作流编排：**最小可视化画布** + 开始/结束/LLM/工具/分支/变量节点（参考 Coze 开源画布实现）
- 模型网关：多供应商、SSE 流式
- 会话 + 开放 API（聊天）
- 基础用量统计 + 审计
- 可观测：结构化日志 + traceId 透传
- 控制台：应用管理 + 最小画布编排页

### V2 —— 核心竞争力

- 画布完善 + 完整节点：Agent / 迭代 / 知识检索 / 代码 / 数据库 / 意图
- 知识库 RAG：文档解析、分块、向量化、混合检索
- 评测与质量：数据集 + **首发指标集**（正确率/准确率、相关性、忠实度、幻觉检测、延迟）+ A/B、回归
- 可观测增强：链路追踪、成本归集、监控大盘
- 工具生态：MCP 协议接入 + Skill 能力包管理

### V3 —— 商业化完整

- 模板市场
- 计费 / 配额 / 结算
- 多渠道：企微 / 钉钉 / Slack
- 完整安全合规：脱敏、内容审核、Prompt 注入防护
- 私有化一键部署（Docker Compose 先行，规模化后按需上 K8s）
- 企业 SSO 对接

## 12. 测试、可观测性与错误处理 ❓建议项（请确认）

- **测试**：平台服务 JUnit5 + Testcontainers；AI 服务 pytest；端到端接口测试。
- **错误处理**：统一异常模型；网关/编排层超时、重试、熔断；异步任务幂等。
- **可观测**：结构化日志（含 traceId，跨 Kotlin/Python 透传）；指标采集 Prometheus；链路追踪先靠 traceId + 日志关联，后续按需上 OpenTelemetry / SkyWalking。

### 12.1 七大编程原则与可扩展性（工程红线）

平台开发**绝对遵守七大编程原则**，可扩展性为设计红线。所有扩展点收敛为「**注册表 + 适配器 + 组件抽象**」模式：

| 原则 | 本项目落地 |
|---|---|
| 单一职责 SRP | 每个模块/类只做一件事；模块划分见 §6 |
| 开闭原则 OCP | 新增节点类型 = 注册 Executor（不动调度器）；新增供应商 = 新增适配器；对扩展开放、对修改关闭 |
| 里氏替换 LSP | 组件抽象可被任意实现替换：模型/检索/文档组件默认走 Python，可换 Kotlin 内联实现 |
| 接口隔离 ISP | 执行器拆 invoke/stream/transform 多态接口，按需实现，不强迫实现无用方法 |
| 依赖倒置 DIP | 引擎依赖抽象接口（`NodeExecutor` / `ModelComponent`），不依赖具体实现 |
| 迪米特法则 LoD | 模块间只通过接口契约交互（如认证中心仅暴露 login/logout/validate/refresh/userinfo） |
| 合成复用 CRP | 优先组合而非继承：节点组成工作流、Skill = Prompt + 工具组合 |

- **扩展点收敛模式**：节点类型（注册表）、模型供应商（适配器）、存储（接口抽象）、AI 能力（组件抽象）——新增能力一律新增实现 + 注册，禁止改动既有调度/执行主链路。

## 13. 已确认决策清单 ✅

| # | 决策 |
|---|---|
| 1 | 定位：端到端一体化智能体平台，商业产品对外售卖 |
| 2 | 8 大能力域全部纳入 |
| 3 | 编排形态：纯工作流驱动（Dify 风格），DAG，Agent 是节点类型 |
| 4 | 服务数：2 个（平台服务 Kotlin + AI 服务 Python），不拆微服务网格 |
| 5 | 后端语言：**Kotlin** + Spring Boot 3 + Spring Cloud Alibaba（Nacos） |
| 6 | AI 服务：Python 3.11 + FastAPI |
| 7 | 存储：PostgreSQL 16、Elasticsearch（向量+全文检索）、Redis 7、MinIO、Redis Streams |
| 8 | 前端：React + antd（控制台）；Widget 不做，V1 只做开放 API |
| 9 | 编排引擎在平台服务内，执行器注册表 + 调度器设计 |
| 10 | 数据访问：谁拥有 schema 谁直连库，platform→Kotlin、ai_kb→Python |
| 11 | 仓库：monorepo，`zhijin-server` / `zhijin-ai` / `zhijin-web` |
| 12 | 评测与可观测全部自研，外部产品（LangSmith 等）仅作理念参考，不引入依赖 |
| 13 | 认证授权中心：平台服务内独立模块 `zhijin-auth`，逻辑独立成中心，后期可搬成独立服务零重构 |
| 14 | 认证/授权基于 **Spring Authorization Server**（OAuth 2.1 / OIDC 1.0），支持第三方平台以 OAuth Client 接入 |
| 15 | 节点类型分级：内置节点（系统维护）+ 自定义节点（租户维护、按角色授权）；自定义节点实现方式 = HTTP 工具 / 代码 / 子工作流模板 |
| 16 | 图规则：工作流图为 DAG（无环）；循环不画在图上，用「迭代节点」或「Agent 节点内部循环」在节点内实现 |
| 17 | 节点接口：每个节点统一带输入/输出接口定义（参数 schema + 输出字段）；输入来源 = 常量 / `{{node_id.output_key}}` 上游输出引用 / `$var` 会话全局变量，执行前解析校验；画布端口、DSL 静态校验、自定义节点 schema、调试快照均以此为统一契约 |
| 18 | 显式边：DSL 顶层显式存储 `edges`（线性连线）+ switch 分支 `goto`（条件路由），边管**执行拓扑**，输入引用只管**数据绑定**，二者分离（参照 Coze Studio） |
| 19 | 全面参考 Coze Studio 架构基线：平台模块划分、引擎内部结构、节点模型、会话运行模型完整对齐（仅 Go → Kotlin）；自研等价 Eino 的图运行时，不引入 Go 库 |
| 20 | 节点集对齐 Coze Studio：开始/结束/LLM/工具/分支/变量/数据库/代码/知识检索/Agent/迭代/意图/消息/图片生成；「循环在节点内」即 Coze 的 LLM canContinue + Batch/Loop 节点 |
| 21 | 模型供应商 Key：平台服务持有并在数据库加密存储（AES-256-GCM），每次调用直接从数据库查询、解密后透传给 AI 服务；AI 服务不落盘、不缓存 Key，V1 不引入独立 Key 管理服务/KMS |
| 22 | 首发模型供应商：**qwen / claude / openai / deepseek**；本地 Ollama-vLLM 留待 V2 |
| 23 | 编排 V1 直接上**最小可视化画布**（开始/结束/LLM/工具/分支/变量 6 种节点），参考 Coze 开源画布实现；表单式配置不做 |
| 24 | 评测 V2 首发指标集：正确率/准确率、相关性、忠实度、幻觉检测、延迟（前三类含 LLM 裁判打分） |
| 25 | 工程红线：平台开发**绝对遵守七大编程原则**（单一职责/开闭/里氏替换/接口隔离/依赖倒置/迪米特/合成复用）；所有扩展点收敛为「注册表 + 适配器 + 组件抽象」，保证可扩展性（见 §12.1） |
| 26 | 前端使用 **TypeScript**（全程 strict 模式），React + TS + antd；不使用 JS |
| 27 | Python 依赖管理使用 **uv**（pyproject.toml + uv.lock + `uv sync` / `uv run`） |

## 14. 开放问题 ✅ 已全部确认

- 首发模型供应商：qwen / claude / openai / deepseek（决策 22）
- 编排 V1 直接上最小画布（决策 23）
- 模型 Key 管理：平台服务直接查库、加密落库、调用时透传（决策 21）
- 评测 V2 首发指标：正确率/准确率、相关性、忠实度、幻觉检测、延迟（决策 24）
- 目标客户行业：不影响模板市场设计

**设计决策已全部锁定，可进入 V1 实现计划阶段。**
