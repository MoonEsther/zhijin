# Plan D：前端控制台 审核反馈（v2 复审）

> **关联计划:** [`docs/superpowers/plans/2026-08-20-v1-plan-d-web-console.md`](../plans/2026-08-20-v1-plan-d-web-console.md)（v2 版，含方案 C 完整 RBAC）
>
> **审核方式:** v2 计划与当前代码逐条对照——`ClientRepositoryConfig.kt`（公共客户端改造目标）、`vite.config.ts`、`AuthController.kt`（/auth/validate 实际路径）、`ValidateResponse.kt`（当前字段）、V1 迁移 SQL（RBAC 表）、`SecurityConfig.kt`（@EnableMethodSecurity）、`AdminSeeder.kt`（所在模块）。
>
> **结论:** 上轮 D1/D2/D3/D4/D7 已全部修复，且与代码吻合；新增 RBAC（方案 C）整体设计正确（表已存在、方法安全已启用、JWT converter 方案可行）。**剩 1 个中等问题**（R1：/auth/validate 路径与代理不匹配，perms 获取会 404）和 2 处小笔误（R2/R3）。修完即可执行。

---

## 1. 上轮反馈（D1~D10）闭环复核

| 项 | v2 处理 | 复核 |
|---|---|---|
| D1 token 认证不匹配 | ✅ zhijin-console 改公共客户端（去 secret + NONE，L18、Task 1 Step 2）；exchangeCode 无 secret（L171~185） | 通过（保留 requireProofKey(true) + redirectUri + scopes） |
| D2 vite 代理缺失 | ✅ 加 `/oauth2`、`/login`、`/error`（L116~123） | 通过 |
| D3 query v5 对象形式 | ✅ `invalidateQueries({ queryKey: ['apps'] })`（L307） | 通过 |
| D4 DSL 格式不一致 | ✅ 明确「V1 前端内部草稿格式」，Self-Review 不再声称与 §7.2 一致（L27、L225、L588） | 通过 |
| D7 challenge 缺失 | ✅ 跳转 URL 带 `code_challenge` + `code_challenge_method=S256`（L154~166） | 通过 |
| D5/D6/D8~D10 | ⚠️ D5（commit 含 orchestrator）未修，见 R3；其余轻微项未处理（可接受） | — |

---

## 2. 新增 RBAC（方案 C）核实结论

| 检查点 | 结果 |
|---|---|
| RBAC 表（sys_role / sys_user_role / sys_permission / sys_role_permission） | ✅ **V1 迁移已建齐**（`V1__base_schema.sql`），无需新迁移 |
| @EnableMethodSecurity | ✅ `SecurityConfig.kt` 已启用 |
| JwtAuthenticationConverter（perms claim → authorities，空前缀） | ✅ 方案正确（`setAuthoritiesClaimName("perms")` + `setAuthorityPrefix("")`；注意：permissions 接管后 scope 权限不再解析，V1 预期行为） |
| 权限点字符串 ↔ 前端 hasPerm ↔ @PreAuthorize | ✅ 一致（10 个权限点） |
| RbacController 依赖方向 | ✅ 全在 zhijin-auth 模块内，无跨模块循环 |
| /auth/validate 加 perms | ✅ `ValidateResponse.kt` 当前为 username/userId/tenantId/roles（L4~9），加 perms 字段吻合 |
| AdminSeeder 增强 | ⚠️ **文件路径写错**（见 R2） |

---

## 3. 剩余问题

### R1（中）：`/auth/validate` 路径不在 `/api` 下，前端经 `request()` 封装会拼错 + vite 代理缺 `/auth`
- **现状（已核实）:** `AuthController` 是 `@RequestMapping("/auth")` + `@GetMapping("/validate")`（`interfaces/AuthController.kt` L17~21）——**路径是 `/auth/validate`，不是 `/api/auth/validate`**。
- **计划:** Task 5 Step 1 `api/auth.ts` 写「GET /auth/validate」（L522），且 `api/client.ts` 的 `BASE = '/api'`（L251）——若用 `request('/auth/validate')` 会请求 `/api/auth/validate` → **404**；若直接 fetch('/auth/validate')，vite 代理（L116~123）**没有 `/auth`** → 同样 404。
- **修订（二选一）:**
  - ① 前端 `api/auth.ts` 直接 `fetch('/auth/validate')`（带 Bearer，不走 /api BASE），**vite 代理增加 `'/auth'`**；
  - ② 后端 AuthController 加 `/api` 前缀（改动后端契约，影响既有测试）。
  - 推荐①，并把 fetch 写法写进计划（当前只有一句描述，无代码）。

### R2（轻-中）：Task 4 文件清单里 AdminSeeder 路径写错
- **计划位置:** L422「Modify: `zhijin-server/zhijin-app/.../seeder/AdminSeeder.kt`」。
- **现状（已核实）:** `AdminSeeder.kt` 在 **zhijin-auth** 模块（`zhijin-auth/src/main/kotlin/com/zhijin/auth/seeder/AdminSeeder.kt`）。
- **修订:** 改为 `zhijin-server/zhijin-auth/.../seeder/AdminSeeder.kt`。

### R3（轻）：Task 2 Step 5 commit 范围仍含 orchestrator（上轮 D5 未修）
- **计划位置:** L322 `git add zhijin-web/ zhijin-server/zhijin-app/ zhijin-server/zhijin-orchestrator/`。
- **说明:** 列表端点改动不涉及 orchestrator，去掉即可。

### R4（轻，实现提示）：userStore 的 perms 过滤不触发 React 重渲染
- 菜单/按钮权限过滤若直接同步读 localStorage（`userStore.hasPerm`），登录回调写入后**不会自动刷新 UI**（需刷新页面）。建议用 React Context/state 承载用户信息（或登录后 `window.location.reload()` 兜底）。非阻塞，执行时注意。

---

## 4. 修订清单（按 Task 组织）

| 位置 | 修订动作 |
|---|---|
| Task 5 Step 1 | ① `api/auth.ts` 明确直接 fetch `/auth/validate`（带 Bearer），不走 /api BASE；vite 代理加 `/auth`（R1） |
| Task 4 文件清单 | ② AdminSeeder 路径改 zhijin-auth（R2） |
| Task 2 Step 5 | ③ commit 范围去掉 orchestrator（R3，可选） |
| 全局（可选） | ④ userStore 用 Context/state 承载，登录后刷新菜单（R4） |

---

## 5. 验证记录（本轮新增）

- **AuthController:** `@RequestMapping("/auth")` + `GET /validate`（`interfaces/AuthController.kt` L17~21）→ R1 成立。
- **ValidateResponse:** 当前字段 username/userId/tenantId/roles（L4~9）→ 加 perms 与计划吻合 ✅。
- **V1 迁移:** sys_role / sys_user_role / sys_permission / sys_role_permission 均已建 → RBAC 无需新迁移 ✅。
- **SecurityConfig:** `@EnableMethodSecurity` 已启用 → @PreAuthorize 可用 ✅。
- **AdminSeeder:** 位于 `zhijin-auth/.../seeder/` → R2 成立。

---

## 6. 执行交接

修订 R1 后按 Task 1→5 执行；完成后 **V1 全栈可交付**。后续 V2：画布增强、RAG、评测、MCP、模板市场。
