# 织锦 · zhijin

> **企业级智能体平台** —— 端到端覆盖智能体「开发 → 编排 → 运行 → 评测 → 治理」全生命周期。

## 名称由来

**织锦天成，错落有致。**

将 Agent 对复杂 API、工作流和工具的编排，比作将缕缕丝线织成华丽锦缎，严丝合缝：

- 每一条 **API**、每一段 **工作流**、每一个 **工具**，都像一根根丝线；
- 编排引擎如同织机，把散落的丝线按图交织、错落有致地组织起来；
- 最终织出的，是一幅严丝合缝、浑然天成的智能体——**织锦（zhijin）**。

## 仓库结构

```
zhijin/                  ← monorepo 总目录
├── zhijin-server/       ← 平台服务（Kotlin + Spring Boot 3 + Nacos）
├── zhijin-ai/           ← AI 服务（Python + FastAPI：模型网关 / RAG / 评测）
├── zhijin-web/          ← 前端控制台（React + antd）
└── docs/
    └── superpowers/specs/
        └── 2026-08-17-agent-platform-design.md  ← 平台设计文档
```

## 文档

- 平台设计文档：[`docs/superpowers/specs/2026-08-17-agent-platform-design.md`](docs/superpowers/specs/2026-08-17-agent-platform-design.md)
