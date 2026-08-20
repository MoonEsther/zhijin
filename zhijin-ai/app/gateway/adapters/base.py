"""供应商适配器抽象。"""
from dataclasses import dataclass
from typing import Protocol


@dataclass
class CompletionResult:
    """供应商返回结果。"""
    content: str
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ProviderAdapter(Protocol):
    """供应商适配器协议。"""

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        """调用供应商返回结果。"""
        ...
