"""LangChain 工具注册表：create_agent 可绑定的工具集。

工具在 AI 服务（Python）侧实现并注册；后续平台真实工具（HTTP/代码等）在此扩展。
"""
from datetime import datetime, timezone

from langchain_core.tools import BaseTool, tool


@tool
def get_current_time() -> str:
    """返回当前 UTC 时间（ISO 8601），用于演示 create_agent 的工具调用。"""
    return datetime.now(timezone.utc).isoformat()


# 工具名 → 工具对象（create_agent 按 tools=[...] 绑定）
TOOLS: dict[str, BaseTool] = {
    get_current_time.name: get_current_time,
}


def build_tools(names: list[str]) -> list[BaseTool]:
    """按名称解析要绑定的工具；未知工具名报错（避免静默忽略）。"""
    unknown = [n for n in names if n not in TOOLS]
    if unknown:
        raise ValueError(f"未知工具: {', '.join(unknown)}（可用: {', '.join(TOOLS)}）")
    return [TOOLS[n] for n in names]
