"""LangChain 模型供应商注册：统一 base_url + api_key + model 构造（抽象由 LangChain 承担）。

设计：不按供应商拆配置变量，整个 AI 服务只有一个统一的 `BASE_URL` + `API_KEY`
（加上选择供应商的 `PROVIDER`）。provider 决定用哪个 LangChain 集成：
OpenAI 兼容三家（qwen/openai/deepseek）→ ChatOpenAI，claude → ChatAnthropic。
后续 RAG / Agent 等能力直接复用 LangChain 生态。
"""
import os

from langchain_anthropic import ChatAnthropic
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI

# 各供应商官方默认 base_url（仅当未配置 BASE_URL 时兜底，避免每家一个变量）
DEFAULT_BASE_URLS = {
    "qwen": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "openai": "https://api.openai.com/v1",
    "deepseek": "https://api.deepseek.com/v1",
    "claude": "https://api.anthropic.com",
}

_OPENAI_COMPATIBLE = ("qwen", "openai", "deepseek")


def create_chat_model(
    provider: str,
    *,
    base_url: str = "",
    api_key: str = "",
    model: str = "default",
) -> BaseChatModel:
    """按供应商构造 LangChain 聊天模型（统一入参：base_url / api_key / model）。

    配置优先级：显式参数 > 环境变量（BASE_URL / API_KEY）> 供应商官方默认地址。
    api_key 为空（请求未下发且环境变量未设）时报错，避免静默打到无 Key 的网关。
    """
    key = api_key or os.getenv("API_KEY", "")
    if not key:
        raise ValueError("未提供 API Key（请求 api_key 或环境变量 API_KEY 未设置）")
    url = base_url or os.getenv("BASE_URL", "") or DEFAULT_BASE_URLS.get(provider, "")
    if provider in _OPENAI_COMPATIBLE:
        return ChatOpenAI(model=model, base_url=url, api_key=key)
    if provider == "claude":
        return ChatAnthropic(model=model, base_url=url, api_key=key, max_tokens=4096)
    raise ValueError(f"不支持的供应商: {provider}")


def extract_usage(ai) -> dict:
    """从 LangChain 模型返回提取 token 用量，兼容 OpenAI / Anthropic 两套 response_metadata 结构。"""
    meta = (ai.response_metadata or {})
    usage = meta.get("token_usage") or meta.get("usage") or {}
    prompt = usage.get("prompt_tokens") or usage.get("input_tokens") or 0
    completion = usage.get("completion_tokens") or usage.get("output_tokens") or 0
    return {
        "prompt_tokens": prompt,
        "completion_tokens": completion,
        "total_tokens": prompt + completion,
    }
