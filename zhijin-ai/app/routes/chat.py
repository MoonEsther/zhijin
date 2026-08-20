"""模型网关最小桩（OpenAI 兼容）。计划 C 用真实供应商替换。"""
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field


router = APIRouter(prefix="/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="")


class ChatRequest(BaseModel):
    model: str = Field(default="default")
    messages: list[ChatMessage] = Field(default_factory=list)


@router.post("/chat/completions")
async def chat_completions(req: ChatRequest) -> JSONResponse:
    """OpenAI 兼容 /v1/chat/completions：V1 桩返回固定文本，计划 C 接真实模型。"""
    last = req.messages[-1].content if req.messages else ""
    return JSONResponse({
        "id": "chatcmpl-stub",
        "object": "chat.completion",
        "model": req.model,
        "choices": [{"index": 0, "message": {"role": "assistant", "content": f"echo: {last}"}}],
    })
