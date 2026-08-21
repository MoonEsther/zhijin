# 织锦 · zhijin

> **智能体平台** —— 端到端覆盖智能体「开发 → 编排 → 运行 → 评测 → 治理」全生命周期。

## 名称由来

**织锦天成，错落有致。**

将 Agent 对复杂 API、工作流和工具的编排，比作将缕缕丝线织成华丽锦缎，严丝合缝：

- 每一条 **API**、每一段 **工作流**、每一个 **工具**，都像一根根丝线；
- 编排引擎如同织机，把散落的丝线按图交织、错落有致地组织起来；
- 最终织出的，是一幅严丝合缝、浑然天成的智能体——**织锦（zhijin）**。

## 特性（V1 已落地）

| 能力域 | 现状 |
|---|---|
| 开发与编排 | 前端 **React Flow** 可视化画布（开始/结束/LLM/工具/分支/变量 6 种节点，DSL 前端草稿） |
| 模型网关 | 多供应商统一接入（qwen / claude / openai / deepseek），真实调用 + 用量归属 |
| 运行与会话 | 会话管理、流式输出（`zhijin-chat`） |
| 治理与安全 | 多租户隔离、**OAuth2 授权码 + PKCE 登录**、**完整 RBAC**（权限点 + 角色 + 组织树） |
| 可观测性 | 用量汇总、操作审计（`zhijin-billing-audit`） |
| 平台运营 | 控制台应用管理（列表 / 创建 / 发布 / API Key） |

> RAG / 评测 / MCP / 模板市场等见文末「路线图」。

## 架构总览

```
┌─────────────┐     ┌──────────────────────────────────┐     ┌──────────────┐
│  zhijin-web │     │          zhijin-server            │     │  zhijin-ai   │
│  React 18   │────▶│  Kotlin + Spring Boot 4 :8080     │────▶│  FastAPI     │
│  antd 5     │HTTP │  9 个 Maven 模块（见下）           │HTTP │  :8001       │
│  React Flow │     └──────────────────────────────────┘     │  模型网关/…  │
└─────────────┘              │                               └──────┬───────┘
                             ▼                                      │
              ┌───────────────────────────────┐                     │
              │  既有基础设施（外部提供，不入库） │◀────────────────────┘
              │  PostgreSQL · Redis · ES ·    │
              │  Nacos · MinIO                │
              └───────────────────────────────┘
```

- **zhijin-server**：平台服务，OAuth2 授权服务器（Spring Security）+ 资源服务 + 多租户数据隔离
- **zhijin-ai**：AI 服务，模型网关适配器 + RAG / 评测
- **zhijin-web**：管理控制台，OAuth2 PKCE 登录 + 应用管理 + 可视化画布 + RBAC

### 服务端模块（Maven，`zhijin-server/`）

| 模块 | 职责 |
|---|---|
| `zhijin-app` | 应用管理 + 启动入口（`spring-boot:run` 主模块） |
| `zhijin-auth` | 认证中心：OAuth2 授权服务器、**RBAC**（角色/权限点）、**组织树** |
| `zhijin-orchestrator` | 编排引擎（工作流驱动） |
| `zhijin-chat` | 会话与对话 |
| `zhijin-tool` | 工具（HTTP / 代码） |
| `zhijin-billing-audit` | 用量汇总 + 审计日志 |
| `zhijin-ai-client` | AI 服务客户端 |
| `zhijin-framework` | 框架：多租户拦截器、MyBatis-Plus 装配、公共实体 |
| `zhijin-common` | 通用：`Result<T>` 统一响应、异常、常量 |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Kotlin 2.2 · Spring Boot 4 · Spring Security（OAuth2 授权服务器 + 资源服务器）· MyBatis-Plus · Flyway · Nacos · PostgreSQL |
| 前端 | React 18 · TypeScript (strict) · Vite · antd 5 · React Flow · TanStack Query · react-router |
| AI 服务 | Python 3.11+ · FastAPI · uv · OpenAI 兼容协议适配（qwen / claude / openai / deepseek） |
| 基础设施 | PostgreSQL · Redis · Elasticsearch · Nacos · MinIO（**外部既有，本仓库不编排**） |

## 仓库结构

```
zhijin/                  ← monorepo 总目录
├── zhijin-server/       ← 平台服务（Kotlin + Spring Boot 4 + Nacos，见上模块表）
├── zhijin-ai/           ← AI 服务（Python + FastAPI：模型网关 / RAG / 评测）
├── zhijin-web/          ← 前端控制台（React + antd + React Flow）
├── deploy/              ← 部署说明（中间件连接配置）
├── scripts/smoke.sh     ← 全栈冒烟脚本
└── docs/
    └── superpowers/
        ├── specs/2026-08-17-agent-platform-design.md   ← 平台设计文档（8 大能力域 / 架构 / 决策）
        └── plans/                                      ← 分期落地计划（B1-B6 / DDD / PlanC / PlanD）
```

## 快速启动

### 前置依赖

- **Java 17+**、**Maven 3.9+**
- **Node.js 18+**、**npm**
- **Python 3.11+**、**uv**（Python 包管理，用于 AI 服务）
- 中间件由**既有基础设施**提供：PostgreSQL / Redis / Elasticsearch / Nacos / MinIO（参考 `deploy/README.md` 填入真实地址）

### 1. 平台服务（zhijin-server，端口 8080）

```bash
cd zhijin-server
mvn -pl zhijin-app spring-boot:run
```

> 首次构建若兄弟模块未安装，先执行 `mvn -pl zhijin-app -am clean install` 安装全部依赖模块。

**环境变量（均有默认值，可省略）：**

| 变量 | 默认 | 说明 |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/zhijin` | 数据库连接 |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `zhijin` / `zhijin_dev_2026` | 数据库账号 |
| `NACOS_ADDR` | `127.0.0.1:8848` | Nacos 服务地址 |
| `AUTH_ISSUER` | `localhost:8080` | JWT issuer（授权服务器） |
| `ADMIN_INIT_PASSWORD` | `admin123` | 启动时幂等创建的管理员初始密码 |

启动后自动执行 Flyway 迁移建表，并幂等种子默认租户 + 管理员账号：

> **默认账号：`admin` / `admin123`**（可用 `ADMIN_INIT_PASSWORD` 覆盖初始密码）

### 2. AI 服务（zhijin-ai，端口 8001）

```bash
cd zhijin-ai
uv run uvicorn app.main:app --port 8001
```

模型供应商经 **LangChain 统一接口**调用（`ChatOpenAI` / `ChatAnthropic`），配置收敛为 `PROVIDER` + `BASE_URL` + `API_KEY` 三个变量（从 `deploy/.env` 读取，已 gitignore；请求也可携带 `provider`/`api_key` 覆盖）。`BASE_URL` 留空走该供应商官方默认地址，可改代理/网关：

| 变量 | 说明 |
|---|---|
| `PROVIDER` | 默认供应商：`qwen` / `openai` / `deepseek` / `claude`（默认 qwen） |
| `BASE_URL` | 模型网关地址（留空走官方默认；qwen/deepseek 等兼容端点可改代理） |
| `API_KEY` | 模型 API Key |
| `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` | 从 Nacos 拉取配置（可选） |
| `LOG_LEVEL` | 日志级别（默认 INFO） |
| `ZHIJIN_ENV_FILE` | 指定 .env 文件路径（默认 `deploy/.env` + cwd 兜底） |

### 3. 前端控制台（zhijin-web，端口 5173）

```bash
cd zhijin-web
npm install
npm run dev
```

开发期 Vite 代理把 `/api`、`/oauth2`、`/login`、`/auth` 等转发到 8080，浏览器访问 `http://localhost:5173` 即可。

### 冒烟验证

```bash
./scripts/smoke.sh          # 校验三个服务健康端点
# 或指定地址：
SERVER_ADDR=127.0.0.1:8080 AI_ADDR=127.0.0.1:8001 WEB_ADDR=127.0.0.1:5173 ./scripts/smoke.sh
```

### 登录流程

前端为 **OAuth2 公共客户端（PKCE）**：访问控制台 → 跳转授权服务器表单登录（默认 `admin`/`admin123`）→ 回调换取 token 存入 localStorage → 进入控制台。菜单/按钮按当前用户的 **RBAC 权限点**动态显隐（`admin` 角色默认全权限）。

## 文档

- 平台设计文档：[`docs/superpowers/specs/2026-08-17-agent-platform-design.md`](docs/superpowers/specs/2026-08-17-agent-platform-design.md)（8 大能力域、总体架构、数据流 A/B、已确认决策）
- 分期落地计划：[`docs/superpowers/plans/`](docs/superpowers/plans/)（B1 租户 → B6 计费审计、DDD 重构、计划 C 真实供应商、计划 D 前端控制台）
- 部署说明：[`deploy/README.md`](deploy/README.md)

## 路线图

- ✅ **V1（当前，全栈可交付）**：多租户 + OAuth2 登录 + 应用管理 + 可视化画布 + 真实模型网关 + 用量/审计 + 完整 RBAC + 组织
- ⏳ **V2**：画布增强（完整节点集 + DSL 后端对接）、RAG 知识库、评测与回归、MCP 工具接入、模板市场
- ⏳ **V3**：SSO、监控大盘、多形态部署
