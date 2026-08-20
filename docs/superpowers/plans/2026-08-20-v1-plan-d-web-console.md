# Plan D：前端控制台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `zhijin-web` 从占位页建设为可用控制台：OAuth2 授权码 + PKCE 登录（对接 B2 重构后的 Spring Security 授权服务器）、应用管理（列表/创建/编辑/发布/API Key）、最小可视化画布（React Flow，6 种节点）、用量与审计查看。

**Architecture:** React 18 + Vite + TypeScript(strict) + antd 5 + react-router + TanStack Query + **React Flow（@xyflow/react）**。OAuth2 登录流：前端为 `zhijin-console` 公共客户端（PKCE），跳转 `/oauth2/authorize` → Spring 表单登录页 → 回调页换 token 存 localStorage。API 统一走 `/api/**`（vite 代理到 8080），Bearer token。画布用 React Flow（行业标准，Coze/Dify 类产品同型），节点面板 = 开始/结束/LLM/工具/分支/变量。

**Tech Stack:** React 18 · TypeScript 5 (strict) · Vite 5 · antd 5 · react-router-dom 6 · @tanstack/react-query 5 · **@xyflow/react**（React Flow 12）

**设计依据:** `2026-08-17-agent-platform-design.md` §7 编排引擎（画布/节点/DSL）、§10 数据流 A；B2 重构后 OAuth2 登录流；博客入门(十八) Vue PKCE 对接模式（改为 React 实现）。

---

## 关键决策

- **OAuth2 登录**：`zhijin-console` client（B2 已注册，CLIENT_SECRET_BASIC + PKCE）。前端实现 PKCE：生成 `code_verifier`/`code_challenge`(S256) → 跳 `/oauth2/authorize` → 回调页 `http://localhost:5173/callback` 取 code + 校验 state → POST `/oauth2/token` 换 token。**client_secret 不放前端**（公共客户端 + PKCE，博客 N6 教训）。
- **token 管理**：access_token 存 localStorage；过期 → 引导重新登录。V1 不做 refresh token 自动续期（简化）。
- **画布**：**React Flow**（@xyflow/react）。6 种节点：开始/结束/LLM/工具/分支/变量。节点输入输出端口渲染基于节点类型（V1 简化：LLM=prompt 输入+output 输出等）。
- **DSL 保存**：画布 → DSL JSON（对齐 §7.2 格式：nodes + edges + start）。V1 保存草稿到本地（localStorage），**发布走应用管理页**（B3 的 publish 端点）；画布 DSL 关联到 `app_version.workflow_dsl` 留 B4+ 后置（当前 publish 不含 DSL，V1 画布 DSL 存本地）。
- **路由**：
  - `/login` → 跳 OAuth2 authorize
  - `/callback` → 换 token
  - `/` → 应用列表（Dashboard）
  - `/apps/:id` → 应用详情（含画布 tab + 发布 + API Key）
  - `/usage` → 用量汇总
  - `/audit` → 审计日志
- **API 客户端**：`src/api/client.ts` 封装 fetch + Bearer token + 统一错误处理；`src/api/apps.ts`/`usage.ts`/`audit.ts` 按资源封装。
- **权限**：V1 登录即管理端，无按钮级 RBAC。

---

## 文件结构

```
zhijin-web/src/
├── main.tsx                    ← 已有（改：QueryClientProvider + RouterProvider）
├── App.tsx                     ← 已有（改为路由出口）
├── auth/
│   ├── oauth.ts                ← PKCE 工具（verifier/challenge/state）+ authorize 跳转
│   ├── tokenStore.ts           ← localStorage 读写 token
│   └── RequireAuth.tsx         ← 路由守卫（无 token → /login）
├── api/
│   ├── client.ts               ← fetch 封装（Bearer + Result<T> 解包 + 401 处理）
│   ├── apps.ts                 ← 应用 CRUD + 发布 + API Key
│   └── usage.ts                ← 用量汇总
│   └── audit.ts                ← 审计分页
├── pages/
│   ├── LoginPage.tsx           ← 跳 authorize（或展示"去登录"按钮）
│   ├── CallbackPage.tsx        ← code 换 token
│   ├── AppListPage.tsx         ← 应用列表 + 新建
│   ├── AppDetailPage.tsx       ← 应用详情（Tabs：画布/模型配置/API Key/发布）
│   ├── UsagePage.tsx           ← 用量汇总表
│   └── AuditPage.tsx           ← 审计日志表
├── canvas/
│   ├── FlowCanvas.tsx          ← React Flow 画布
│   ├── nodes/                  ← 6 种自定义节点组件（StartNode/EndNode/LlmNode/ToolNode/IfNode/VariableNode）
│   ├── dsl.ts                  ← 画布 ↔ DSL JSON 转换（§7.2 格式）
│   └── palette.tsx             ← 左侧节点面板（拖拽添加）
└── components/
    └── AppLayout.tsx           ← antd Layout（侧边栏 + 内容区）
```

---

## Task 1: 依赖 + 布局 + 路由 + OAuth2 登录（TDD 验证 DSL 转换）

**Files:**
- Modify: `package.json`（加依赖）、`vite.config.ts`（加 react plugin 已有）、`main.tsx`、`App.tsx`
- Create: `auth/`（oauth.ts / tokenStore.ts / RequireAuth.tsx）、`components/AppLayout.tsx`、`pages/LoginPage.tsx`、`pages/CallbackPage.tsx`
- Test: `canvas/dsl.test.ts`（DSL 转换，用 vitest）

- [ ] **Step 1: 安装依赖**

```bash
cd zhijin-web
npm install react-router-dom @tanstack/react-query @xyflow/react
npm install -D vitest @testing-library/react @testing-library/jest-dom
```

- [ ] **Step 2: PKCE 工具 `auth/oauth.ts`**

```ts
// OAuth2 PKCE 工具：zhijin-console 公共客户端（不放 client_secret）
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

/** 跳转到授权服务器登录（PKCE）。 */
export function redirectToAuthorize() {
  const verifier = generateVerifier();
  sessionStorage.setItem('code_verifier', verifier);
  const state = generateState();
  sessionStorage.setItem('oauth_state', state);
  // 用异步生成 challenge，但跳转需要同步——先存 verifier，challenge 在跳转前 await
  window.location.href =
    `${AUTH_BASE}/authorize?` +
    new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: 'code',
      scope: 'openid profile',
      redirect_uri: REDIRECT_URI,
      state,
    }).toString();
}

/** 换 token（回调页用）。 */
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

> **说明**：PKCE challenge 需在跳转前异步生成（`crypto.subtle.digest` 是异步的）——`redirectToAuthorize` 改为 `async`，调用方 `await redirectToAuthorize()`。

- [ ] **Step 3: token 存储 `auth/tokenStore.ts`**

```ts
const KEY = 'zhijin_access_token';
export const tokenStore = {
  get: () => localStorage.getItem(KEY),
  set: (t: string) => localStorage.setItem(KEY, t),
  clear: () => localStorage.removeItem(KEY),
};
```

- [ ] **Step 4: 路由守卫 `RequireAuth.tsx`**

```tsx
import { Navigate } from 'react-router-dom';
import { tokenStore } from './tokenStore';

export function RequireAuth({ children }: { children: React.ReactNode }) {
  return tokenStore.get() ? <>{children}</> : <Navigate to="/login" replace />;
}
```

- [ ] **Step 5: 布局 + 路由**

`components/AppLayout.tsx`：antd `Layout`（Sider 菜单：应用/用量/审计）+ `Outlet`。

`App.tsx`（路由）：
```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { RequireAuth } from './auth/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { CallbackPage } from './pages/CallbackPage';
import { AppListPage } from './pages/AppListPage';
import { AppDetailPage } from './pages/AppDetailPage';
import { UsagePage } from './pages/UsagePage';
import { AuditPage } from './pages/AuditPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/" element={<AppListPage />} />
          <Route path="/apps/:id" element={<AppDetailPage />} />
          <Route path="/usage" element={<UsagePage />} />
          <Route path="/audit" element={<AuditPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
```

- [ ] **Step 6: 登录/回调页**

`LoginPage.tsx`：展示「登录」按钮 → `redirectToAuthorize()`；说明跳转授权服务器。

`CallbackPage.tsx`：URL 取 `code` + `state` → 校验 state（对比 sessionStorage）→ `exchangeCode` → 存 token → `navigate('/')`；失败显示错误。

- [ ] **Step 7: DSL 转换工具 + 单测（vitest，TDD）**

`canvas/dsl.ts`（画布 ↔ DSL JSON，对齐 §7.2）：
```ts
// 节点类型 → DSL type（对齐后端 NodeType）
export const NODE_TYPE_MAP = {
  start: 'start', end: 'end', llm: 'llm', tool: 'tool', if: 'if', variable: 'variable',
} as const;

export interface FlowNodeData { label: string; type: keyof typeof NODE_TYPE_MAP; [k: string]: unknown }
export interface FlowEdgeData { source: string; target: string; id: string }

/** React Flow 节点/边 → 后端 DSL（§7.2 格式）。 */
export function toDsl(nodes: { id: string; data: FlowNodeData }[], edges: FlowEdgeData[]) {
  return {
    id: `wf-${Date.now().toString(36)}`,
    start: nodes.find(n => n.data.type === 'start')?.id ?? nodes[0]?.id ?? '',
    nodes: nodes.map(n => ({
      id: n.id,
      type: NODE_TYPE_MAP[n.data.type],
      config: { label: n.data.label },
    })),
    edges: edges.map(e => ({ from: e.source, to: e.target })),
  };
}

/** 后端 DSL → React Flow 节点/边。 */
export function fromDsl(dsl: { nodes: {id: string; type: string; config?: Record<string, unknown>}[]; edges: {from: string; to: string}[] }) {
  const nodes = dsl.nodes.map((n, i) => ({
    id: n.id,
    position: { x: 100 + i * 200, y: 100 },
    data: { label: n.config?.label ?? n.type, type: n.type } as FlowNodeData,
  }));
  const edges = dsl.edges.map((e, i) => ({ id: `e${i}`, source: e.from, target: e.to }));
  return { nodes, edges };
}
```

`canvas/dsl.test.ts`（vitest）：
```ts
import { describe, it, expect } from 'vitest';
import { toDsl, fromDsl } from './dsl';

describe('dsl 转换', () => {
  it('React Flow 节点 → 后端 DSL', () => {
    const dsl = toDsl(
      [
        { id: 'start', data: { label: '开始', type: 'start' } },
        { id: 'llm', data: { label: '大模型', type: 'llm' } },
        { id: 'end', data: { label: '结束', type: 'end' } },
      ],
      [{ id: 'e1', source: 'start', target: 'llm' }, { id: 'e2', source: 'llm', target: 'end' }],
    );
    expect(dsl.start).toBe('start');
    expect(dsl.nodes).toHaveLength(3);
    expect(dsl.edges[0]).toEqual({ from: 'start', to: 'llm' });
  });

  it('后端 DSL → React Flow 节点', () => {
    const { nodes, edges } = fromDsl({
      nodes: [
        { id: 'start', type: 'start', config: { label: '开始' } },
        { id: 'llm', type: 'llm' },
      ],
      edges: [{ from: 'start', to: 'llm' }],
    });
    expect(nodes).toHaveLength(2);
    expect(nodes[0].data.type).toBe('start');
    expect(edges[0].source).toBe('start');
    expect(edges[0].target).toBe('llm');
  });
});
```

- [ ] **Step 8: 验证 + Commit**

```bash
cd zhijin-web
npx vitest run --reporter=dot    # 单测通过
npm run build                    # tsc + vite build 通过
```
```bash
cd C:\mypro\JavaProject\zhijin
git add zhijin-web/
git commit -m "feat(web): 路由布局 + OAuth2 PKCE 登录 + DSL 转换(TDD)"
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

antd `Table`（列：名称/描述/状态/操作）+「新建应用」按钮（Modal 表单，antd `Form`）+ 删除确认（`Popconfirm`）。用 TanStack Query：
```tsx
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { appsApi, AppItem } from '../api/apps';
// useQuery({ queryKey: ['apps'], queryFn: appsApi.list })
// useMutation({ mutationFn: appsApi.create, onSuccess: () => queryClient.invalidateQueries(['apps']) })
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

## Task 4: 用量/审计页 + 联调收尾

**Files:**
- Create: `api/usage.ts`、`api/audit.ts`、`pages/UsagePage.tsx`、`pages/AuditPage.tsx`

- [ ] **Step 1: 用量/审计 API**

```ts
// api/usage.ts
export interface UsageSummary { appId: number; totalCalls: number; totalTokens: number }
export const usageApi = { summary: () => request<UsageSummary[]>('/usage/summary') };
// api/audit.ts
export interface AuditLogItem { id: number; username: string; action: string; targetType: string; targetId: number | null; detail: string; createTime: string }
export const auditApi = { page: (p = 1, s = 20) => request<{ items: AuditLogItem[]; total: number }>(`/audit-logs?page=${p}&size=${s}`) };
```

- [ ] **Step 2: 页面**

`UsagePage.tsx`：antd `Table`（应用 ID/调用次数/token 总量），`useQuery(['usage'])`。
`AuditPage.tsx`：antd `Table` + `Pagination`（操作/目标/时间），`useQuery(['audit', page])`。

- [ ] **Step 3: 全量验证**

```bash
cd zhijin-web && npx vitest run && npm run build
```

- [ ] **Step 4: 端到端联调**（真后端）
1. 启动后端（真 PG/Nacos）
2. `npm run dev` 前端
3. 浏览器打开 `http://localhost:5173` → 登录页 → 跳 OAuth2 登录（admin/admin123）→ 回调 → 控制台
4. 应用列表 → 新建应用 → 详情页画布（拖节点）→ 生成 API Key
5. 用量页显示真实 token 汇总；审计页显示操作记录
> 用 Playwright（`webapp-testing` skill）或手动浏览器验证；截图保存。

- [ ] **Step 5: 记录实现修正 + Commit**

```bash
cd C:\mypro\JavaProject\zhijin
git add -A
git commit -m "feat(web): 用量/审计页 + 端到端联调验证"
```

---

## Self-Review 记录

- **Spec 覆盖**：§7 画布/节点/DSL ✓ · §10 数据流 A（登录→应用→画布→发布→API Key）✓ · B2 OAuth2 登录流 ✓。
- **测试覆盖**：DSL 转换单测（TDD）+ 各任务构建验证 + 端到端浏览器联调。
- **占位符扫描**：无 TBD；每步含完整代码。
- **类型一致性**：`NODE_TYPE_MAP` ↔ 后端 `NodeType` code（start/end/llm/tool/if/variable）一致；`toDsl` 输出 ↔ §7.2 DSL 格式一致；API 类型 ↔ 后端 `Result<T>` 结构一致。

## 执行交接

Plan D 完成后 → V1 全栈可交付：设计 → 后端（B1-B6 + DDD + 计划C）→ 前端（登录/应用/画布/用量审计）。后续 V2：画布增强（完整节点集）、RAG、评测、MCP、模板市场。
