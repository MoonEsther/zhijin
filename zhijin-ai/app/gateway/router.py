"""模型网关路由（真实供应商调用）。"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from .adapters.registry import get_adapter


router = APIRouter(prefix="/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = Field(default="default")
    provider: str = Field(default="qwen")        # 新增：供应商
    api_key: str = Field(default="")             # 新增：Kotlin 下发的 Key
    messages: list[ChatMessage] = Field(default_factory=list)


@router.post("/chat/completions")
async def chat_completions(req: ChatRequest):
    """调用真实供应商（适配器内 api_key 为空时回退环境变量）。"""
    try:
        adapter = get_adapter(req.provider)
        result = await adapter.complete(
            api_key=req.api_key,
            model=req.model,
            messages=[{"role": m.role, "content": m.content} for m in req.messages],
        )
        return {
            "id": "chatcmpl-real",
            "object": "chat.completion",
            "model": req.model,
            "choices": [{"index": 0, "message": {"role": "assistant", "content": result.content}}],
            "usage": {
                "prompt_tokens": result.prompt_tokens,
                "completion_tokens": result.completion_tokens,
                "total_tokens": result.total_tokens,
            },
        }
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"供应商调用失败: {e}")
