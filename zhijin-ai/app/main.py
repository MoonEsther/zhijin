"""AI 服务（FastAPI）入口。模型网关走真实供应商适配器（计划 C）。"""
from fastapi import FastAPI

from .config import load_config
from .gateway.router import router as gateway_router

app = FastAPI(title="zhijin-ai", version="0.1.0")

app.include_router(gateway_router)

config = load_config()


@app.get("/health")
async def health() -> dict:
    """健康检查端点，供平台服务与编排探活。"""
    return {"status": "UP", "app": config["app.name"]}
