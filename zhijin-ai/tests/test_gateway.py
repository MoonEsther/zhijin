"""LangChain 模型网关单测（mock 模型对象，不真实调用供应商）。"""
import pytest
from langchain_anthropic import ChatAnthropic
from langchain_openai import ChatOpenAI

from app.gateway.adapters.registry import DEFAULT_BASE_URLS, create_chat_model, extract_usage


def test_create_chat_model_openai_compatible(monkeypatch):
    """qwen/openai/deepseek → ChatOpenAI（LangChain 统一接口）。"""
    monkeypatch.setenv("API_KEY", "env-key")
    model = create_chat_model("qwen", model="qwen-max")
    assert isinstance(model, ChatOpenAI)
    assert model.model_name == "qwen-max"
    # 未配置 BASE_URL 时回退官方默认地址（config 层不拆供应商变量）
    assert DEFAULT_BASE_URLS["qwen"] == "https://dashscope.aliyuncs.com/compatible-mode/v1"
    assert DEFAULT_BASE_URLS["openai"] == "https://api.openai.com/v1"


def test_create_chat_model_claude(monkeypatch):
    """claude → ChatAnthropic。"""
    monkeypatch.setenv("API_KEY", "env-key")
    model = create_chat_model("claude", model="claude-sonnet-4-5")
    assert isinstance(model, ChatAnthropic)


def test_create_chat_model_unknown_provider(monkeypatch):
    """未知供应商报错。"""
    monkeypatch.setenv("API_KEY", "env-key")
    with pytest.raises(ValueError):
        create_chat_model("unknown")


def test_create_chat_model_missing_key(monkeypatch):
    """未提供 Key（请求 api_key 空 + 环境变量 API_KEY 未设）→ 报错，避免静默打到无 Key 网关。"""
    monkeypatch.delenv("API_KEY", raising=False)
    with pytest.raises(ValueError):
        create_chat_model("qwen")


def test_extract_usage_openai_shape():
    """OpenAI 风格 response_metadata.token_usage。"""

    class FakeAI:
        response_metadata = {"token_usage": {"prompt_tokens": 5, "completion_tokens": 10, "total_tokens": 15}}

    assert extract_usage(FakeAI()) == {"prompt_tokens": 5, "completion_tokens": 10, "total_tokens": 15}


def test_extract_usage_anthropic_shape():
    """Anthropic 风格 response_metadata.usage（input/output tokens）。"""

    class FakeAI:
        response_metadata = {"usage": {"input_tokens": 5, "output_tokens": 10}}

    assert extract_usage(FakeAI()) == {"prompt_tokens": 5, "completion_tokens": 10, "total_tokens": 15}


@pytest.mark.asyncio
async def test_router_maps_langchain_response(monkeypatch):
    """router 把 LangChain 模型返回映射为 OpenAI 兼容响应（mock 模型对象，验证响应结构契约不变）。"""
    import app.gateway.router as r

    class FakeAI:
        content = "hello"
        response_metadata = {"token_usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3}}

    class FakeChat:
        async def ainvoke(self, messages):
            return FakeAI()

    monkeypatch.setattr(r, "create_chat_model", lambda *a, **kw: FakeChat())

    resp = await r.chat_completions(
        r.ChatRequest(
            provider="qwen", model="qwen-max", api_key="k",
            messages=[r.ChatMessage(role="user", content="hi")],
        )
    )
    assert resp["choices"][0]["message"]["content"] == "hello"
    assert resp["usage"]["total_tokens"] == 3
    assert resp["model"] == "qwen-max"


@pytest.mark.asyncio
async def test_router_agent_mode_binds_tools(monkeypatch):
    """agent 模式：request 带 tools 时走 create_agent 并绑定工具，返回最终 AI 消息。"""
    import app.gateway.router as r

    class FakeAI:
        content = "tool-answer"
        response_metadata = {"token_usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3}}

    class FakeAgent:
        async def ainvoke(self, inputs):
            assert "messages" in inputs
            return {"messages": [FakeAI()]}

    captured = {}

    def fake_create_agent(chat, tools=None, **kw):
        # 记录实际绑定的工具，证明 create_agent 收到的是已解析的 LangChain 工具
        captured["tools"] = tools
        return FakeAgent()

    monkeypatch.setattr(r, "create_agent", fake_create_agent)

    resp = await r.chat_completions(
        r.ChatRequest(
            provider="qwen", model="qwen-max", api_key="k",
            messages=[r.ChatMessage(role="user", content="hi")],
            tools=["get_current_time"], system_prompt="你是助手",
        )
    )
    assert resp["choices"][0]["message"]["content"] == "tool-answer"
    assert captured["tools"][0].name == "get_current_time"
