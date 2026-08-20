"""模型网关适配器单测（mock 供应商，C8）。"""
import pytest
from app.gateway.adapters.registry import get_adapter


def test_get_adapter_known_provider():
    assert get_adapter("qwen") is not None
    assert get_adapter("claude") is not None


def test_get_adapter_unknown_provider():
    with pytest.raises(ValueError):
        get_adapter("unknown")


@pytest.mark.asyncio
async def test_openai_adapter_env_fallback(monkeypatch):
    """N5：api_key 为空时回退环境变量（用 mock AsyncOpenAI 验证）。"""
    from app.gateway.adapters.openai_adapter import OpenAICompatibleAdapter

    monkeypatch.setenv("QWEN_API_KEY", "env-key")
    adapter = OpenAICompatibleAdapter("http://mock", "QWEN_API_KEY")

    # mock OpenAI 客户端
    import app.gateway.adapters.openai_adapter as mod
    called = {}

    class FakeUsage:
        prompt_tokens = 5
        completion_tokens = 10
        total_tokens = 15

    class FakeMessage:
        content = "hello"

    class FakeChoice:
        message = FakeMessage()

    class FakeResp:
        usage = FakeUsage()
        choices = [FakeChoice()]

    class FakeCompletions:
        """模拟 client.chat.completions.create（真实 SDK 为属性链，需逐级模拟）。"""

        @staticmethod
        async def create(**kwargs):
            return FakeResp()

    class FakeChat:
        """模拟 client.chat 属性对象。"""

        def __init__(self):
            self.completions = FakeCompletions()

    class FakeAsyncClient:
        def __init__(self, **kwargs):
            called["api_key"] = kwargs.get("api_key")
            self.chat = FakeChat()

    monkeypatch.setattr(mod, "AsyncOpenAI", lambda **kw: FakeAsyncClient(**kw))

    result = await adapter.complete("", "qwen-max", [{"role": "user", "content": "hi"}])
    assert called["api_key"] == "env-key"
    assert result.content == "hello"
    assert result.total_tokens == 15


@pytest.mark.asyncio
async def test_claude_adapter_thinking_block(monkeypatch):
    """Claude 响应首个 content 块为 ThinkingBlock 时仍能取到文本（兼容 thinking 块）。"""
    import app.gateway.adapters.claude_adapter as mod

    monkeypatch.setenv("CLAUDE_API_KEY", "env-key")

    class FakeTextBlock:
        type = "text"
        text = "Hi!"

    class FakeThinkingBlock:
        type = "thinking"
        # 无 text 属性，模拟 ThinkingBlock

    class FakeUsage:
        input_tokens = 10
        output_tokens = 5

    class FakeResp:
        content = [FakeThinkingBlock(), FakeTextBlock()]
        usage = FakeUsage()

    class FakeMessages:
        @staticmethod
        async def create(**kwargs):
            return FakeResp()

    class FakeAsyncClient:
        def __init__(self, **kwargs):
            self.messages = FakeMessages()

    monkeypatch.setattr(mod.anthropic, "AsyncAnthropic", lambda **kw: FakeAsyncClient(**kw))

    adapter = mod.ClaudeAdapter()
    result = await adapter.complete("", "claude-sonnet-4-5", [{"role": "user", "content": "hi"}])
    assert result.content == "Hi!"
    assert result.total_tokens == 15
