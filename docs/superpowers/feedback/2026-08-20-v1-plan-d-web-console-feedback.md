# Plan D：前端控制台 审核反馈

> **关联计划:** [`docs/superpowers/plans/2026-08-20-v1-plan-d-web-console.md`](../plans/2026-08-20-v1-plan-d-web-console.md)
>
> **审核方式:** 计划与当前代码/配置逐条对照——B2 重构后的 `ClientRepositoryConfig.kt`（zhijin-console 注册）、`AdminSeeder.kt`（默认账号）、`vite.config.ts`（代理）、orchestrator `NodeType.kt`/`Connection.kt`/`WorkflowParser`（DSL 结构）、B3/B6 后端接口契约。
>
> **结论:** 整体设计合理（React Flow 画布、TanStack Query、PKCE 流程、页面划分、TDD DSL 转换），但 **OAuth2 登录链路有 2 个阻塞级问题**（token 交换必失败、dev 代理缺失），不修登录流程走不通；另有 2 个中等问题。修完即可执行。

---

## 1. 总体判定

| 维度 | 判定 | 说明 |
|---|---|---|
| 技术选型 | ✅ 合理 | React Flow 12（Coze/Dify 同型）、TanStack Query v5、antd 5、vitest TDD |
| 页面/路由划分 | ✅ 合理 | 登录/回调/列表/详情(Tabs)/用量/审计，覆盖 V1 全流程 |
| 后端契约对齐 | ⚠️ 大部分正确 | scope openid/profile ✅、redirect_uri ✅、/api 路径 ✅、admin/admin123 ✅（AdminSeeder 默认）；但 token 认证方式不匹配（D1） |
| OAuth2 登录链路 | ❌ **需先修订** | D1（client_secret 认证矛盾）+ D2（vite 代理缺失）→ 登录走不通 |
| 可执行性 | ❌ **需先修订** | 修 D1/D2 后可执行 |

---

## 2. 阻塞级问题（必须改，否则登录流程必失败）

### D1. token 交换必失败：前端不带 client_secret，但服务器端 zhijin-console 是 CLIENT_SECRET_BASIC
- **计划位置:** 关键决策 L17「client_secret 不放前端（公共客户端 + PKCE）」+ Task 1 Step 2 `exchangeCode`（L125~139，只发 client_id/code/code_verifier，无 secret）。
- **现状（已核实）:** `ClientRepositoryConfig.kt` L30~31：zhijin-console 注册了 `clientSecret(BCrypt("console-secret"))` + `ClientAuthenticationMethod.CLIENT_SECRET_BASIC`——**不是公共客户端**。token 端点（/oauth2/token）按 BASIC 认证校验 → 前端不带 secret → **401 invalid_client，token 交换必失败**。
- **修订（二选一，推荐①）:**
  - ① 服务器端改：zhijin-console 去掉 `clientSecret`，认证方式改 `ClientAuthenticationMethod.NONE`（`requireProofKey(true)` 已设置 ✅ 保持）——真正的 PKCE 公共客户端，与计划「不放 secret」决策一致。**本计划 Task 需加一条后端改动**（`ClientRepositoryConfig.kt`），否则登录无法工作；
  - ② 前端带 `client_secret: 'console-secret'`——与「不放前端」决策相悖且 secret 形同虚设（不推荐）。

### D2. vite 代理缺失：`/oauth2` 与 `/login` 未代理，dev 下登录 404
- **计划位置:** Task 1 Step 1「vite.config.ts（加 react plugin 已有）」——未提代理。
- **现状（已核实）:** `vite.config.ts` 只代理 `/api`（L10）。而计划前端跳转 `window.location.href = '/oauth2/authorize?...'`（L113）与 `fetch('/oauth2/token')`（L126）都是 **5173 同源路径**——vite 无 `/oauth2` 代理 → **404**。
- **连带:** Spring 表单登录页是 `/login`（302 跳转 + POST 提交），同样需要代理；登录失败错误页 `/error` 也建议代理。
- **修订:** vite.config.ts 的 proxy 增加 `'/oauth2'`、`'/login'`、`'/error'`（均 target 8080）。注意：不能只把 authorize 跳转改成绝对地址 8080——token 交换 fetch 跨端口会触发 CORS（OAuth2 token 端点默认无 CORS），且表单登录 session cookie 依赖同源代理最稳妥。
- **验收点:** dev 下浏览器访问 `http://localhost:5173/oauth2/authorize?...` 应 302 到 Spring 表单登录页。

---

## 3. 中等问题

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| D3 | Task 2 Step 3（L361） | TanStack Query **v5** 中 `invalidateQueries(['apps'])` 数组形式已废弃，应为 `invalidateQueries({ queryKey: ['apps'] })`（v5 只接受对象过滤条件） | 改对象形式 |
| D4 | Task 1 Step 7 `toDsl`/`fromDsl` + Self-Review L516 | 前端 DSL 结构与后端 §7.2 格式**并不一致**：后端 `Connection(fromNode, toNode)`（`Connection.kt` L4，JSON 字段 fromNode/toNode），前端输出 `{from, to}`；且 nodes 只有 `{id, type, config}`，**无 inputs/outputs**（后端 LLM 节点必须有 prompt 输入才能运行）。Self-Review「toDsl 输出 ↔ §7.2 DSL 格式一致」不成立 | V1 画布 DSL 只存 localStorage 不落后端——可接受，但计划应**明确标注「V1 为前端内部草稿格式，后端 DSL 对接留 V2」**，并修正 Self-Review 表述；或补 inputs 结构（LLM 节点带 `{prompt: {source: literal}}`） |

---

## 4. 轻微项（可不改）

| # | 说明 |
|---|---|
| D5 | Task 2 Step 5 commit 范围含 `zhijin-server/zhijin-orchestrator/`（L376）——列表端点改动不涉及 orchestrator，疑为笔误，可去掉 |
| D6 | `redirect_uri = ${window.location.origin}/callback` 与服务器端注册的 `http://localhost:5173/callback` 在 dev 一致 ✅；部署到其他 origin 时需同步改 `ClientRepositoryConfig` 注册——注明即可 |
| D7 | PKCE challenge 生成代码（generateChallenge）存在但 `redirectToAuthorize` 的跳转 URL **未带 `code_challenge`/`code_challenge_method` 参数**（L113~121）——服务器端 `requireProofKey(true)` 会拒绝无 challenge 的授权请求！**此点应并入 D1 一起修**：跳转 URL 需补 `code_challenge` 与 `code_challenge_method=S256`（计划 L142 注释说改 async 并 await challenge，但示例代码没体现——需真正把 challenge 拼进 URL） |
| D8 | `exchangeCode` 的 `redirect_uri` 参数必须与 authorize 请求一致 ✅（同一常量） |
| D9 | token 存 localStorage 的 XSS 风险 V1 接受 ✅ 已注明 |
| D10 | vitest 纯函数测试无需 jsdom 配置 ✅（dsl.test.ts 无 DOM 依赖），`npx vitest run` 可直接跑 |

---

## 5. 修订清单（按 Task 组织）

| 位置 | 修订动作 |
|---|---|
| 全局/关键决策 | ① 明确 zhijin-console 为公共客户端（NONE 认证）或前端带 secret，二者取一（D1） |
| Task 1 Step 1 | ② vite proxy 增加 `/oauth2`、`/login`、`/error`（D2） |
| Task 1 Step 2 | ③ `redirectToAuthorize` 真正生成并把 `code_challenge` + `code_challenge_method=S256` 拼入 URL（D7）；④ `exchangeCode` 按 D1 决策补/不补 secret |
| Task 1 Step 7 | ⑤ 标注 V1 DSL 为前端内部草稿格式（D4）；⑥ Self-Review 表述修正 |
| Task 2 Step 3 | ⑦ `invalidateQueries` 改对象形式（D3） |
| Task 2 Step 5 | ⑧ commit 范围去掉 orchestrator（D5，可选） |

---

## 6. 验证记录（本轮）

- **ClientRepositoryConfig.kt:** L30~31 zhijin-console 有 secret + CLIENT_SECRET_BASIC；L37~42 requireProofKey(true) ✅ + redirectUri L34 + scope L35~36 → D1 成立、D7 的 challenge 缺失会触发 requireProofKey 拒绝。
- **vite.config.ts:** 仅 `/api` 代理 → D2 成立。
- **AdminSeeder.kt:** admin / ADMIN_INIT_PASSWORD（默认 admin123）→ 联调账号 admin/admin123 ✅ 正确。
- **NodeType.kt:** code 值 start/end/llm/tool/if/variable → 前端 NODE_TYPE_MAP ✅ 一致。
- **Connection.kt:** `Connection(fromNode, toNode)` → 前端 `{from, to}` 字段不一致 → D4 成立。
- **后端契约:** `/api/apps` CRUD+publish、`/api/apps/{id}/api-keys`、`/api/usage/summary`、`/api/audit-logs` 均与前端 api 封装对齐 ✅；`GET /api/apps` 列表缺失已被计划 Task 2 Step 4 识别并补后端 ✅。

---

## 7. 执行交接

修订 D1/D2（+D7）后按 Task 1→4 执行；完成后 V1 全栈可交付，后续 V2：画布增强/RAG/评测/MCP/模板市场。
