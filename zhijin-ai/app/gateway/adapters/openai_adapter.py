"""OpenAI 兼容协议适配器（qwen/openai/deepseek 共用）。"""
import os
from openai import AsyncOpenAI
from .base import CompletionResult


class OpenAICompatibleAdapter:
    """OpenAI 兼容协议适配器。"""

    def __init__(self, base_url: str, env_var_name: str):
        self.base_url = base_url
        self.env_var_name = env_var_name  # 解决 N5：api_key 为空时回退的环境变量名

    async def complete(self, api_key: str, model: str, messages: list[dict]) -> CompletionResult:
        # 解决 N5：api_key 为空时回退环境变量（V1 providerKeyId=null 链路靠此工作）
        key = api_key or os.getenv(self.env_var_name, "")
        if not key:
            raise ValueError(f"未提供 API Key 且环境变量 {self.env_var_name} 未设置")

        client = AsyncOpenAI(api_key=key, base_url=self.base_url)
        resp = await client.chat.completions.create(model=model, messages=messages)
        usage = resp.usage
        return CompletionResult(
            content=resp.choices[0].message.content or "",
            prompt_tokens=usage.prompt_tokens if usage else 0,
            completion_tokens=usage.completion_tokens if usage else 0,
            total_tokens=usage.total_tokens if usage else 0,
        )


# 预配置实例（base_url 从环境变量读，env_var_name 指定回退环境变量）
QWEN_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    "QWEN_API_KEY"
)
OPENAI_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
    "OPENAI_API_KEY"
)
DEEPSEEK_ADAPTER = OpenAICompatibleAdapter(
    os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
    "DEEPSEEK_API_KEY"
)
