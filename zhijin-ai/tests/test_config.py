"""config 模块单测：.env 配置文件加载（模型供应商 Key/地址可由配置文件集中管理）。"""
import os

from app.config import _load_env_files


def test_load_env_files_sets_provider_vars(tmp_path, monkeypatch):
    """显式指定 .env：应把 QWEN_API_KEY / QWEN_BASE_URL 读进环境变量。"""
    env_file = tmp_path / "providers.env"
    env_file.write_text(
        "QWEN_API_KEY=sk-test\n"
        "QWEN_BASE_URL=https://proxy.example/v1\n",
        encoding="utf-8",
    )
    # 清掉可能已存在的同名变量，保证断言的是该文件写入的值
    monkeypatch.delenv("QWEN_API_KEY", raising=False)
    monkeypatch.delenv("QWEN_BASE_URL", raising=False)

    _load_env_files(str(env_file))

    assert os.getenv("QWEN_API_KEY") == "sk-test"
    assert os.getenv("QWEN_BASE_URL") == "https://proxy.example/v1"


def test_load_env_files_does_not_override_existing_env(tmp_path, monkeypatch):
    """override=False：shell 已设置的变量优先级高于 .env 文件。"""
    env_file = tmp_path / "providers.env"
    env_file.write_text("QWEN_API_KEY=file-key\n", encoding="utf-8")
    monkeypatch.setenv("QWEN_API_KEY", "shell-key")

    _load_env_files(str(env_file))

    assert os.getenv("QWEN_API_KEY") == "shell-key"


def test_load_env_files_missing_file_noop(tmp_path):
    """配置文件缺失（如 CI 无 deploy/.env）时加载应静默跳过，不抛异常。"""
    _load_env_files(str(tmp_path / "not-exist.env"))
