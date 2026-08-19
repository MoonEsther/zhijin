"""AI 服务（FastAPI）入口。V1 空壳：仅健康检查，模型网关在计划 C 实现。"""
from fastapi import FastAPI

from .config import load_config

app = FastAPI(title="zhijin-ai", version="0.1.0")

config = load_config()


@app.get("/health")
async def health() -> dict:
    """健康检查端点，供平台服务与编排探活。"""
    return {"status": "UP", "app": config["app.name"]}
