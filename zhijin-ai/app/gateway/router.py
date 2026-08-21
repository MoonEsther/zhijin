"""模型网关路由（LangChain 统一调用真实供应商，返回 OpenAI 兼容响应）。"""
import os

from fastapi import APIRouter, HTTPException
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from .adapters.registry import create_chat_model, extract_usage

router = APIRouter(prefix="/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = Field(default="default")
    # 供应商；空则用环境变量 PROVIDER（默认 qwen）。统一配置面：PROVIDER + BASE_URL + API_KEY
    provider: str = Field(default="")
    api_key: str = Field(default="")             # Kotlin 下发 Key；为空回退环境变量 API_KEY
    messages: list[ChatMessage] = Field(default_factory=list)


@router.post("/chat/completions")
async def chat_completions(req: ChatRequest):
    """经 LangChain 统一模型接口调用真实供应商（ChatOpenAI / ChatAnthropic）。"""
    try:
        provider = req.provider or os.getenv("PROVIDER", "qwen")
        chat = create_chat_model(provider, api_key=req.api_key, model=req.model)
        lc_messages = [
            SystemMessage(content=m.content) if m.role == "system" else HumanMessage(content=m.content)
            for m in req.messages
        ]
        ai = await chat.ainvoke(lc_messages)
        content = ai.content if isinstance(ai.content, str) else str(ai.content)
        usage = extract_usage(ai)
        return {
            "id": "chatcmpl-langchain",
            "object": "chat.completion",
            "model": req.model,
            "choices": [{"index": 0, "message": {"role": "assistant", "content": content}}],
            "usage": usage,
        }
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"供应商调用失败: {e}")
