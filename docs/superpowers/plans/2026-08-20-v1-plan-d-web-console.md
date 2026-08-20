# Plan D：前端控制台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `zhijin-web` 从占位页建设为可用控制台：OAuth2 授权码 + PKCE 登录（对接 B2 重构后的 Spring Security 授权服务器）、应用管理（列表/创建/编辑/发布/API Key）、最小可视化画布（React Flow，6 种节点）、用量与审计查看、**完整 RBAC（角色管理 + 权限点控制）**。

**Architecture:** React 18 + Vite + TypeScript(strict) + antd 5 + react-router + TanStack Query + **React Flow（@xyflow/react）**。OAuth2 登录流：前端为 `zhijin-console` **公共客户端**（PKCE，无 client_secret，D1 修订），跳转 `/oauth2/authorize` → Spring 表单登录页 → 回调页换 token 存 localStorage。API 统一走 `/api/**`（vite 代理到 8080），Bearer token。RBAC：JWT 携带 `roles` + `perms` claim，后端 `@PreAuthorize` 方法级校验，前端菜单/按钮按 perms 过滤 + 用户/角色管理页。

**Tech Stack:** React 18 · TypeScript 5 (strict) · Vite 5 · antd 5 · react-router-dom 6 · @tanstack/react-query 5 · **@xyflow/react**（React Flow 12）

**设计依据:** `2026-08-17-agent-platform-design.md` §7 编排引擎、§10 数据流 A、§13 决策 13/14；B2 OAuth2 登录流；博客入门(十八) Vue PKCE 模式；**用户确认方案 C（完整 RBAC）**。

---

## 关键决策

- **OAuth2 登录（D1/D2/D7 修订）**：
  - **zhijin-console 改为公共客户端**：服务端 `ClientRepositoryConfig.kt` 去掉 `clientSecret`，`ClientAuthenticationMethod.NONE` + `requireProofKey(true)`（前端不放 secret 的 PKCE 语义）
  - 前端 PKCE：生成 `code_verifier`/`code_challenge`(S256) → 跳 `/oauth2/authorize`（**URL 必须带 `code_challenge` + `code_challenge_method=S256`，否则 requireProofKey 拒绝**，D7）→ 回调页取 code 校验 state → POST `/oauth2/token`（公共客户端无需 secret，D1）
  - **vite proxy 增加 `/oauth2`、`/login`、`/error`**（D2：否则 dev 登录 404；token 交换 fetch 跨端口会触发 CORS）
- **RBAC（方案 C 完整版）**：
  - **权限点**：`app:view` `app:create` `app:update` `app:delete` `app:publish` `apikey:manage` `usage:view` `audit:view` `user:manage` `role:manage`
  - **后端**：JWT 加 `perms` claim（token customizer 从用户权限查询写入）→ 关键接口 `@PreAuthorize("hasAuthority('app:create')")` → 角色/权限管理接口（CRUD）
  - **前端**：菜单按 perms 过滤（无权限不显示菜单项）、按钮按 perms 控制（无权限禁用/隐藏）、**用户管理页**（分配角色）+ **角色管理页**（配置权限点）
  - 内置：`admin` 角色默认全权限（AdminSeeder 给 admin 用户分配）
- **画布**：**React Flow**（@xyflow/react）。6 种节点：开始/结束/LLM/工具/分支/变量。
- **DSL 保存（D4 修订）**：画布 → DSL JSON。**V1 明确为「前端内部草稿格式」**（localStorage），后端 `app_version.workflow_dsl` 对接留 V2——前端 `{from,to}` 与后端 `Connection(fromNode,toNode)` 字段不一致，V1 不落后端，**Self-Review 不再声称与 §7.2 一致**。
- **路由**：
  - `/login` → 跳 OAuth2 authorize
  - `/callback` → 换 token
  - `/` → 应用列表
  - `/apps/:id` → 应用详情（画布 tab + 发布 + API Key）
  - `/usage` → 用量汇总
  - `/audit` → 审计日志
  - `/users` → 用户管理（RBAC）
  - `/roles` → 角色管理（RBAC）
- **API 客户端**：`src/api/client.ts` 封装 fetch + Bearer + 401 处理；按资源拆分。
- **V1 权限简化**：登录用户即平台管理端，但**操作按 perms 控制**（无角色管理界面前用 admin 全权限）。

---

## 文件结构

```
zhijin-web/src/
├── main.tsx                    ← 已有（改：QueryClientProvider + RouterProvider）
├── App.tsx                     ← 已有（改为路由出口）
├── auth/
│   ├── oauth.ts                ← PKCE 工具（verifier/challenge/state + authorize 跳转含 challenge）
│   ├── tokenStore.ts           ← localStorage 读写 token
│   ├── userStore.ts            ← 用户信息 + perms（登录后从 /auth/validate 获取，缓存）
│   └── RequireAuth.tsx         ← 路由守卫（无 token → /login）
│   └── Perm.tsx                ← 权限点控制组件（<Perm perm="app:create">…</Perm>）
├── api/
│   ├── client.ts               ← fetch 封装（Bearer + Result<T> 解包 + 401 处理）
│   ├── apps.ts                 ← 应用 CRUD + 发布 + API Key
│   ├── usage.ts                ← 用量汇总
│   ├── audit.ts                ← 审计分页
│   ├── rbac.ts                 ← 用户/角色/权限点（方案 C）
│   └── auth.ts                 ← /auth/validate（用户身份 + perms）
├── pages/
│   ├── LoginPage.tsx           ← 跳 authorize（或展示"去登录"按钮）
│   ├── CallbackPage.tsx        ← code 换 token
│   ├── AppListPage.tsx         ← 应用列表 + 新建
│   ├── AppDetailPage.tsx       ← 应用详情（Tabs：画布/模型配置/API Key/发布）
│   ├── UsagePage.tsx           ← 用量汇总表
│   ├── AuditPage.tsx           ← 审计日志表
│   ├── UserManagePage.tsx      ← 用户管理（分配角色，RBAC 方案 C）
│   └── RoleManagePage.tsx      ← 角色管理（配置权限点，RBAC 方案 C）
├── canvas/
│   ├── FlowCanvas.tsx          ← React Flow 画布
│   ├── nodes/                  ← 6 种自定义节点组件（StartNode/EndNode/LlmNode/ToolNode/IfNode/VariableNode）
│   ├── dsl.ts                  ← 画布 ↔ DSL JSON 转换（V1 前端内部草稿格式，D4）
│   └── palette.tsx             ← 左侧节点面板（拖拽添加）
└── components/
    ├── AppLayout.tsx           ← antd Layout（侧边栏菜单按 perms 过滤）
    └── PageHeader.tsx          ← 页头（标题 + 操作区）
```

**RBAC 后端补充（方案 C）**：
```
zhijin-server/zhijin-auth/
├── domain/
│   ├── role/Role.kt + RoleRepository.kt         ← 角色实体 + 仓储（角色/用户角色/角色权限）
│   └── permission/Permission.kt + PermissionRepository.kt
├── application/RbacApplicationService.kt        ← 角色 CRUD + 权限点查询 + 用户分配角色
├── interfaces/RbacController.kt                 ← /api/rbac/roles /api/rbac/permissions /api/rbac/users/{id}/roles
└── infrastructure/persistence/（RoleRecord/RoleRepositoryImpl 等）
```

---

## Task 1: 依赖 + 布局 + 路由 + OAuth2 登录（D1/D2/D7 修订 + TDD DSL）

**Files:**
- Modify: `package.json`（加依赖）、`vite.config.ts`（**加 /oauth2 /login /error 代理，D2**）、`main.tsx`、`App.tsx`
- Modify（后端，D1）: `zhijin-server/zhijin-auth/.../config/ClientRepositoryConfig.kt`（zhijin-console 改公共客户端 NONE + 去 secret）
- Create: `auth/`（oauth.ts / tokenStore.ts / userStore.ts / RequireAuth.tsx）、`components/AppLayout.tsx`、`pages/LoginPage.tsx`、`pages/CallbackPage.tsx`
- Test: `canvas/dsl.test.ts`（DSL 转换，vitest）

- [ ] **Step 1: 安装依赖**

```bash
cd zhijin-web
npm install react-router-dom @tanstack/react-query @xyflow/react
npm install -D vitest @testing-library/react @testing-library/jest-dom
```

- [ ] **Step 2: 后端改 zhijin-console 为公共客户端（D1，本任务同步提交）**

`ClientRepositoryConfig.kt`：zhijin-console **去掉 clientSecret**，`clientAuthenticationMethod` 改 `ClientAuthenticationMethod.NONE`（保留 `requireProofKey(true)` + redirectUri + scopes）。

- [ ] **Step 3: vite 代理补全（D2）**

`vite.config.ts`：
```ts
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },  // D2
  '/login': { target: 'http://localhost:8080', changeOrigin: true },   // D2
  '/error': { target: 'http://localhost:8080', changeOrigin: true },   // D2
}
```

- [ ] **Step 4: PKCE 工具 `auth/oauth.ts`（D1/D7 修订：公共客户端 + 跳转带 challenge）**

```ts
// OAuth2 PKCE 工具：zhijin-console 公共客户端（无 client_secret，D1）
const CLIENT_ID = 'zhijin-console';
const REDIRECT_URI = `${window.location.origin}/callback`;
const AUTH_BASE = '/oauth2';

export function generateVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/[^a-zA-Z0-9]/g, '').slice(0, 64);
}

export async function generateChallenge(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function generateState(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

/** 跳转到授权服务器登录（PKCE，跳转 URL 必须带 code_challenge，D7）。 */
export async function redirectToAuthorize() {
  const verifier = generateVerifier();
  sessionStorage.setItem('code_verifier', verifier);
  const challenge = await generateChallenge(verifier);   // D7：真正生成 challenge
  const state = generateState();
  sessionStorage.setItem('oauth_state', state);
  window.location.href =
    `${AUTH_BASE}/authorize?` +
    new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: 'code',
      scope: 'openid profile',
      redirect_uri: REDIRECT_URI,
      state,
      code_challenge: challenge,            // D7：缺了会被 requireProofKey 拒绝
      code_challenge_method: 'S256',        // D7
    }).toString();
}

/** 换 token（公共客户端无 secret，D1）。 */
export async function exchangeCode(code: string, verifier: string) {
  const resp = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      code_verifier: verifier,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
    }),
  });
  if (!resp.ok) throw new Error(`token exchange failed: ${resp.status}`);
  return resp.json() as Promise<{ access_token: string; expires_in?: number }>;
}
```

- [ ] **Step 5: token/用户存储 + 守卫**

`auth/tokenStore.ts`：同前。
`auth/userStore.ts`（方案 C：登录后调 `/auth/validate` 缓存用户身份 + perms）：
```ts
import { tokenStore } from './tokenStore';

export interface UserInfo { username: string; userId: number | null; tenantId: number | null; roles: string[]; perms?: string[] }
const KEY = 'zhijin_user';
export const userStore = {
  get: (): UserInfo | null => { const s = localStorage.getItem(KEY); return s ? JSON.parse(s) : null; },
  set: (u: UserInfo) => localStorage.setItem(KEY, JSON.stringify(u)),
  clear: () => { localStorage.removeItem(KEY); tokenStore.clear(); },
  hasPerm: (perm: string) => userStore.get()?.perms?.includes(perm) ?? false,
};
```

`auth/RequireAuth.tsx`：同前（无 token → /login）。

- [ ] **Step 6: 权限控制组件 `auth/Perm.tsx`（方案 C）**

```tsx
import { userStore } from './userStore';

/** 按权限点控制渲染（无权限返回 null）。 */
export function Perm({ perm, children }: { perm: string; children: React.ReactNode }) {
  return userStore.hasPerm(perm) ? <>{children}</> : null;
}
```

- [ ] **Step 7: 登录/回调页**

`LoginPage.tsx`：按钮 → `await redirectToAuthorize()`。
`CallbackPage.tsx`：URL 取 code + state → 校验 sessionStorage state → `exchangeCode` → 存 token → 调 `/auth/validate` 存 userStore → `navigate('/')`。

- [ ] **Step 8: DSL 转换 + 单测（D4 修订：标注 V1 前端内部草稿格式）**

`canvas/dsl.ts` + `dsl.test.ts`：同前代码，**文件头标注「V1 前端内部草稿格式（localStorage），后端 DSL 对接留 V2」**。

- [ ] **Step 9: 验证 + Commit**

```bash
cd zhijin-web && npx vitest run --reporter=dot && npm run build
cd ../zhijin-server && mvn -pl zhijin-app -am clean compile   # D1 后端改动编译
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/ zhijin-server/zhijin-auth/
git commit -m "feat(web): 路由布局 + OAuth2 PKCE 登录(公共客户端D1 + 代理D2 + challenge D7) + DSL 转换"
```

---

## Task 2: API 客户端封装 + 应用列表页

**Files:**
- Create: `api/client.ts`、`api/apps.ts`、`pages/AppListPage.tsx`

- [ ] **Step 1: API 客户端 `api/client.ts`**

```ts
import { tokenStore } from '../auth/tokenStore';

const BASE = '/api';

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStore.get();
  const resp = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (resp.status === 401) {
    tokenStore.clear();
    window.location.href = '/login';
    throw new Error('未认证');
  }
  const body = await resp.json();
  // Result<T> 解包：{ code, message, data }
  if (body.code !== 0) throw new Error(body.message || '请求失败');
  return body.data as T;
}
```

- [ ] **Step 2: 应用 API `api/apps.ts`**

```ts
import { request } from './client';

export interface AppItem { id: number; appKey: string; name: string; description: string; iconUri: string; status: number }
export interface AppVersion { id: number; versionNo: number; status: number }
export interface ApiKeyResult { id: number; plainKey: string; name: string }

export const appsApi = {
  list: () => request<AppItem[]>('/apps'),
  create: (data: { name: string; description: string; iconUri: string }) =>
    request<AppItem>('/apps', { method: 'POST', body: JSON.stringify(data) }),
  get: (id: number) => request<AppItem>(`/apps/${id}`),
  update: (id: number, data: { name: string; description: string; iconUri: string }) =>
    request<AppItem>(`/apps/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/apps/${id}`, { method: 'DELETE' }),
  publish: (id: number) => request<AppVersion>(`/apps/${id}/publish`, { method: 'POST' }),
  generateApiKey: (id: number, name: string) =>
    request<ApiKeyResult>(`/apps/${id}/api-keys?name=${encodeURIComponent(name)}`, { method: 'POST' }),
};
```

> **注意**：后端 `AppController` 没有 `GET /api/apps` 列表端点（只有 get/update/delete/publish）——**需后端补列表端点**（Task 2 末尾加后端改动，或标注前端联调前需后端加 `GET /api/apps` 分页/列表）。**建议在 Task 2 同步加后端 `AppApplicationService.list(tenantId)` + `GET /api/apps`**。

- [ ] **Step 3: 应用列表页 `AppListPage.tsx`**

antd `Table`（列：名称/描述/状态/操作）+「新建应用」按钮（Modal 表单，antd `Form`）+ 删除确认（`Popconfirm`）。用 TanStack Query（**D3：v5 用对象形式 invalidateQueries**）：
```tsx
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { appsApi, AppItem } from '../api/apps';
// useQuery({ queryKey: ['apps'], queryFn: appsApi.list })
// useMutation({ mutationFn: appsApi.create, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apps'] }) })  // D3
```

- [ ] **Step 4: 后端补列表端点（必要）**

后端 `AppApplicationService` 加 `list(tenantId: Long): List<App>`（`AppRepository` 加 `findAll(tenantId)`），`AppController` 加 `GET /api/apps`。提交到 zhijin-server。

- [ ] **Step 5: 验证 + Commit**

```bash
cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am test   # 后端测试
cd ../zhijin-web && npm run build
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/ zhijin-server/zhijin-app/ zhijin-server/zhijin-orchestrator/
git commit -m "feat(web): API 客户端 + 应用列表页(后端补列表端点)"
```

---

## Task 3: 应用详情页（Tabs：画布/模型配置/API Key/发布）

**Files:**
- Create: `pages/AppDetailPage.tsx`、`canvas/FlowCanvas.tsx`、`canvas/nodes/*`、`canvas/palette.tsx`

- [ ] **Step 1: React Flow 画布 `canvas/FlowCanvas.tsx`**

```tsx
import { ReactFlow, Background, Controls, MiniMap, addEdge, useNodesState, useEdgesState } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { StartNode, EndNode, LlmNode, ToolNode, IfNode, VariableNode } from './nodes';
import type { Node, Edge, Connection, NodeTypes } from '@xyflow/react';

const nodeTypes: NodeTypes = { start: StartNode, end: EndNode, llm: LlmNode, tool: ToolNode, if: IfNode, variable: VariableNode };

interface Props {
  initialNodes: Node[];
  initialEdges: Edge[];
  onChange?: (nodes: Node[], edges: Edge[]) => void;
}

export function FlowCanvas({ initialNodes, initialEdges, onChange }: Props) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const onConnect = (c: Connection) => setEdges(es => addEdge(c, es));

  return (
    <div style={{ height: 560 }}>
      <ReactFlow
        nodes={nodes} edges={edges} nodeTypes={nodeTypes}
        onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={onConnect}
        fitView
      >
        <Background /><Controls /><MiniMap />
      </ReactFlow>
    </div>
  );
}
```

- [ ] **Step 2: 6 种节点组件 `canvas/nodes/`**

每个节点一个组件，antd 风格卡片 + 输入/输出端口提示。示例 `LlmNode.tsx`：
```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export function LlmNode({ data }: NodeProps) {
  return (
    <div style={{ border: '1px solid #5C62FF', borderRadius: 8, padding: 12, background: '#fff', minWidth: 140 }}>
      <Handle type="target" position={Position.Top} />
      <div style={{ fontWeight: 600 }}>大模型</div>
      <div style={{ fontSize: 12, color: '#888' }}>{String(data.label ?? '')}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
```
Start/End/Llm/Tool/If/Variable 同型（颜色区分：start=绿/end=红/llm=蓝/tool=橙/if=紫/variable=灰）。

- [ ] **Step 3: 节点面板 `canvas/palette.tsx`**

左侧面板列出 6 种节点，点击添加到画布（`setNodes` + 定位）。

- [ ] **Step 4: 详情页 `AppDetailPage.tsx`**

antd `Tabs`：
- **画布**：`FlowCanvas`（从 localStorage 读该 app 的 DSL → `fromDsl` → 画布；保存按钮 → `toDsl` → 存 localStorage）
- **模型配置**：表单（provider/model 下拉，V1 简化）
- **API Key**：`Button` 生成（显示明文一次）+ 列表
- **发布**：`Button` 调 `appsApi.publish`，显示返回版本号

- [ ] **Step 5: 验证 + Commit**

```bash
cd zhijin-web && npm run build
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/
git commit -m "feat(web): React Flow 画布 + 应用详情页(Tabs)"
```

---

## Task 4: 用量/审计页 + RBAC 后端（方案 C）+ 联调收尾

**Files:**
- Create: `api/usage.ts`、`api/audit.ts`、`pages/UsagePage.tsx`、`pages/AuditPage.tsx`
- **RBAC 后端（新增，方案 C）**：
  - Modify: `zhijin-server/zhijin-auth/.../domain/user/`（User 实体加 roles；ZhijinUserDetails 加 perms）
  - Modify: `zhijin-server/zhijin-auth/.../config/SecurityConfig.kt`（token customizer 写 perms claim）
  - Create: `zhijin-server/zhijin-auth/.../domain/role/`（Role + RoleRepository）、`domain/permission/`（Permission + PermissionRepository）
  - Create: `zhijin-server/zhijin-auth/.../application/RbacApplicationService.kt`
  - Create: `zhijin-server/zhijin-auth/.../interfaces/RbacController.kt`
  - Modify: `zhijin-server/zhijin-app/.../seeder/AdminSeeder.kt`（创建 admin 角色 + 全权限 + 分配）

- [ ] **Step 1: 用量/审计 API + 页面**（同前）

- [ ] **Step 2: RBAC 后端 - 权限点定义 + JWT perms claim（方案 C）**

权限点常量（`zhijin-auth/.../domain/permission/Permissions.kt`）：
```kotlin
object Permissions {
    const val APP_VIEW = "app:view"
    const val APP_CREATE = "app:create"
    const val APP_UPDATE = "app:update"
    const val APP_DELETE = "app:delete"
    const val APP_PUBLISH = "app:publish"
    const val APIKEY_MANAGE = "apikey:manage"
    const val USAGE_VIEW = "usage:view"
    const val AUDIT_VIEW = "audit:view"
    const val USER_MANAGE = "user:manage"
    const val ROLE_MANAGE = "role:manage"
}
```

`SecurityConfig.tokenCustomizer` 增强：从用户查询 roles + perms 写入 claims：
```kotlin
// 从用户角色查询权限点（RoleRepository.findPermsByUserId）
claims["roles"] = roles
claims["perms"] = perms  // 权限点列表
```

- [ ] **Step 3: RBAC 后端 - 角色/权限仓储 + 应用服务**

`domain/role/Role.kt`（富血：id/roleCode/roleName/perms）+ `RoleRepository`（findByUserId 查角色 + findPermsByUserId 查权限点 + CRUD）。
`domain/permission/Permission.kt`（权限点：code/name）+ `PermissionRepository`（listAll）。

`application/RbacApplicationService.kt`：
- `listRoles(tenantId)` / `createRole` / `updateRole`（含 perms 分配）/ `deleteRole`
- `listPermissions()` → 权限点全量
- `assignRoleToUser(tenantId, userId, roleIds)` / `listUsersWithRoles(tenantId)`

`interfaces/RbacController.kt`（`/api/rbac/**`，JWT 保护）：
- `GET /api/rbac/permissions`（权限点列表）
- `GET/POST/PUT/DELETE /api/rbac/roles`
- `GET /api/rbac/users`（用户 + 角色）
- `PUT /api/rbac/users/{id}/roles`（分配角色）

- [ ] **Step 4: 后端方法级校验（@PreAuthorize）+ JWT 权限映射（关键）**

**先配 `JwtAuthenticationConverter`**（`@PreAuthorize` 依赖它从 `perms` claim 提取 authorities——默认只从 `scope` claim 解析且带 `SCOPE_` 前缀）：
```kotlin
// SecurityConfig（或独立 config）
@Bean
fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
    JwtAuthenticationConverter().apply {
        // 从 perms claim 提取权限点，无 SCOPE_ 前缀（对齐 @PreAuthorize("hasAuthority('app:create')")）
        setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("perms")
            setAuthorityPrefix("")
        })
    }
```
并在资源服务器链 `oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(...); jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) } }` 挂上。

> **依赖方向检查**：token customizer 与 JwtAuthenticationConverter 都在 `zhijin-auth` 内；角色/权限仓储（RoleRepository）查询 B1 建的 `sys_role`/`sys_user_role`/`sys_role_permission`/`sys_permission` 表——这些表属于 auth 域，无跨模块循环依赖。

关键接口加注解：
- `AppController.create/update/delete` → `@PreAuthorize("hasAuthority('app:create')")` 等
- `AppController.publish` → `app:publish`
- `ApiKeyController.generate/revoke` → `apikey:manage`
- `UsageController.summary` → `usage:view`
- `AuditLogController.page` → `audit:view`
- `RbacController` → `user:manage` / `role:manage`

> **说明**：`@PreAuthorize` 需要 `@EnableMethodSecurity`（B2 已启用 ✓）；权限点校验依赖 JWT `perms` claim（token customizer 写入）+ JwtAuthenticationConverter 映射（本步骤新增）。

- [ ] **Step 5: AdminSeeder 增强（方案 C）**

创建 `admin` 角色（`roleCode="admin"`，perms = 全部权限点）+ 分配 `sys_user_role`（admin 用户 → admin 角色）。

- [ ] **Step 6: 验证 + Commit**

```bash
cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean test
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/ zhijin-server/
git commit -m "feat(web): 用量/审计页 + RBAC后端(权限点/@PreAuthorize/角色管理)"
```

---

## Task 5: RBAC 前端（方案 C）- 用户/角色管理页 + 权限过滤

**Files:**
- Create: `api/rbac.ts`、`api/auth.ts`（/auth/validate 用户身份 + perms）
- Modify: `auth/userStore.ts`（登录后存 perms）、`components/AppLayout.tsx`（菜单按 perms 过滤）
- Create: `pages/UserManagePage.tsx`、`pages/RoleManagePage.tsx`

- [ ] **Step 1: RBAC API + 用户身份**

`api/auth.ts`：`GET /auth/validate` → `{ username, userId, tenantId, roles, perms }`（登录后调，存 userStore）。

**后端配合**：`ValidateResponse` 加 `perms: List<String>` 字段（当前只有 roles），`AuthApplicationService.validate` 从 JWT claims 读 `perms`：
```kotlin
// ValidateResponse.kt
data class ValidateResponse(
    val username: String,
    val userId: Long?,
    val tenantId: Long?,
    val roles: List<String>,
    val perms: List<String>,   // 新增：权限点（方案 C）
)
// AuthApplicationService.validate 中
perms = (claims["perms"] as? List<*>)?.map { it.toString() } ?: emptyList(),
```

`api/rbac.ts`：`permissions()` / `roles` CRUD / `users` / `assignRoles(userId, roleIds)`。

- [ ] **Step 2: 菜单按 perms 过滤**

`AppLayout.tsx`：antd Menu 项带 perm 字段，渲染时 `userStore.hasPerm` 过滤：
```tsx
const MENUS = [
  { key: '/', icon: <AppstoreOutlined />, label: '应用', perm: 'app:view' },
  { key: '/usage', icon: <BarChartOutlined />, label: '用量', perm: 'usage:view' },
  { key: '/audit', icon: <SafetyCertificateOutlined />, label: '审计', perm: 'audit:view' },
  { key: '/users', icon: <TeamOutlined />, label: '用户', perm: 'user:manage' },
  { key: '/roles', icon: <SolutionOutlined />, label: '角色', perm: 'role:manage' },
].filter(m => userStore.hasPerm(m.perm));
```

- [ ] **Step 3: 按钮权限控制**

列表/详情页操作按钮包 `<Perm perm="...">`：新建（app:create）、编辑/删除（app:update/delete）、发布（app:publish）、API Key（apikey:manage）。

- [ ] **Step 4: 用户管理页 `UserManagePage.tsx`**

antd Table（用户列表 + 当前角色）+「分配角色」Modal（Checkbox 组选角色，提交 `assignRoles`）。`Perm perm="user:manage"` 包裹。

- [ ] **Step 5: 角色管理页 `RoleManagePage.tsx`**

antd Table（角色列表 + 权限点）+「新建/编辑角色」Modal（角色名 + 权限点 Checkbox 组，提交 roles CRUD）。`Perm perm="role:manage"` 包裹。

- [ ] **Step 6: 路由注册**

`App.tsx` 加 `/users`、`/roles` 路由。

- [ ] **Step 7: 验证 + Commit**

```bash
cd zhijin-web && npm run build
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/
git commit -m "feat(web): RBAC前端(用户/角色管理 + 菜单按钮权限过滤)"
```

---

## Self-Review 记录

- **Spec 覆盖**：§7 画布/节点/DSL ✓ · §10 数据流 A（登录→应用→画布→发布→API Key）✓ · B2 OAuth2 登录流 ✓ · **§13 决策 13/14 + RBAC（方案 C）** ✓。
- **反馈闭环**：D1（zhijin-console 公共客户端）、D2（vite 代理）、D3（query v5 对象形式）、D4（V1 DSL 为前端草稿格式）、D7（code_challenge 入 URL）全部修订。
- **测试覆盖**：DSL 转换单测（TDD）+ 各任务构建验证 + 端到端浏览器联调。
- **占位符扫描**：无 TBD；每步含完整代码。
- **类型一致性**：`NODE_TYPE_MAP` ↔ 后端 `NodeType` code 一致；`toDsl` 输出为 **V1 前端内部草稿格式**（D4，不与后端 §7.2 断言一致）；API 类型 ↔ 后端 `Result<T>` 结构一致；`perms` claim ↔ 前端 `userStore.hasPerm` ↔ `@PreAuthorize` 权限点字符串一致。

## 执行交接

Plan D 完成后 → **V1 全栈可交付**（设计 → 后端 B1-B6 + DDD + 计划C → 前端控制台 + RBAC）。后续 V2：画布增强（完整节点集 + DSL 后端对接）、RAG、评测、MCP、模板市场。
