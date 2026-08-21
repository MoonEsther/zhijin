"""LangChain 工具注册表单测。"""
import pytest

from app.gateway.tools import TOOLS, build_tools


def test_build_tools_known():
    """按名称解析已注册工具。"""
    tools = build_tools(["get_current_time"])
    assert len(tools) == 1
    assert tools[0].name == "get_current_time"


def test_build_tools_unknown_raises():
    """未知工具名报错（避免静默忽略导致 agent 少绑工具）。"""
    with pytest.raises(ValueError):
        build_tools(["not-a-tool"])


def test_tools_registry_nonempty():
    """注册表至少含演示工具。"""
    assert "get_current_time" in TOOLS
