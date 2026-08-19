# 部署（deploy）

全部中间件（PostgreSQL / Redis / Elasticsearch / Nacos / MinIO）**均由既有基础设施提供**，本仓库不编排中间件。平台服务的容器化部署（docker-compose）在服务代码就绪后补充（后续计划）。

## 连接配置

复制 `.env.example` 为 `.env`，填入既有基础设施的真实地址（见 Task 2）。

## 本地开发

三个服务本地运行：

| 服务 | 命令 | 端口 |
|---|---|---|
| 平台服务 | `cd zhijin-server && mvn -pl zhijin-app spring-boot:run` | 8080 |
| AI 服务 | `cd zhijin-ai && uv run uvicorn app.main:app --port 8001` | 8001 |
| 前端 | `cd zhijin-web && npm run dev` | 5173 |

## 冒烟

```bash
./scripts/smoke.sh
```
