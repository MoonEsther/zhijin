"""Anthropic Claude Messages API 适配器。"""
import os
import anthropic
from .base import CompletionResult


class ClaudeAdapter:
    """Claude Messages API 适配器。"""

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        # 解决 N5：api_key 为空时回退环境变量
        key = api_key or os.getenv("CLAUDE_API_KEY", "")
        if not key:
            raise ValueError("未提供 API Key 且环境变量 CLAUDE_API_KEY 未设置")

        client = anthropic.AsyncAnthropic(api_key=key)
        # OpenAI 格式转 Claude 格式
        system = ""
        claude_messages = []
        for m in messages:
            if m["role"] == "system":
                system = m["content"]
            else:
                claude_messages.append({"role": m["role"], "content": m["content"]})
        resp = await client.messages.create(
            model=model,
            max_tokens=4096,
            system=system,
            messages=claude_messages,
        )
        # 兼容 thinking 块：部分模型/代理的 content[0] 为 ThinkingBlock（无 text 属性），取首个文本块
        content = next((b.text for b in resp.content if getattr(b, "type", "") == "text"), "")
        return CompletionResult(
            content=content,
            prompt_tokens=resp.usage.input_tokens,
            completion_tokens=resp.usage.output_tokens,
            total_tokens=resp.usage.input_tokens + resp.usage.output_tokens,
        )


CLAUDE_ADAPTER = ClaudeAdapter()
