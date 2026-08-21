"""模型网关路由（LangChain 统一调用真实供应商，返回 OpenAI 兼容响应）。"""
import os

from fastapi import APIRouter, HTTPException
from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from .adapters.registry import create_chat_model, extract_usage
from .tools import build_tools

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
    # agent 模式：提供 tools 时经 create_agent 绑定工具（模型可调用工具完成任务）；
    # 空列表则走纯聊天模型调用（向后兼容 Kotlin 现有调用）
    tools: list[str] = Field(default_factory=list)
    system_prompt: str = Field(default="")


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
        if req.tools:
            # agent 模式：create_agent 绑定工具，模型可自主调用；返回最终 AIMessage
            agent = create_agent(chat, tools=build_tools(req.tools), system_prompt=req.system_prompt or None)
            result = await agent.ainvoke({"messages": lc_messages})
            ai = result["messages"][-1]
        else:
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
