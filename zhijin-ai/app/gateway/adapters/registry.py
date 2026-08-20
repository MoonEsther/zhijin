"""供应商适配器注册表。"""
from .openai_adapter import QWEN_ADAPTER, OPENAI_ADAPTER, DEEPSEEK_ADAPTER
from .claude_adapter import CLAUDE_ADAPTER


ADAPTERS = {
    "qwen": QWEN_ADAPTER,
    "openai": OPENAI_ADAPTER,
    "deepseek": DEEPSEEK_ADAPTER,
    "claude": CLAUDE_ADAPTER,
}


def get_adapter(provider: str):
    """按供应商名获取适配器。"""
    adapter = ADAPTERS.get(provider)
    if not adapter:
        raise ValueError(f"不支持的供应商: {provider}")
    return adapter
