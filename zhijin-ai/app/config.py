"""AI 服务配置：.env 文件 → 环境变量 → Nacos 拉取（后者失败回退）。

说明：
- 启动最先加载 .env（仓库 deploy/.env 或 cwd 下 .env，均可选），使模型供应商
  （qwen/openai/deepseek/claude）的 API Key 与 Base URL 可由配置文件集中管理，
  不依赖 shell 环境变量；已有环境变量优先级更高（override=False 不覆盖）。
- 使用 Nacos 3.x OpenAPI v3 客户端接口拉取配置（GET /nacos/v3/client/cs/config）。
- 认证：先 POST /nacos/v1/auth/login 拿 accessToken（3.x 认证接口仍为 v1 路径）。
- Nacos 不可用或配置不存在时不致命，超时即回退本地默认配置，保证空壳可独立启动。
"""
import os
from pathlib import Path

import httpx
from dotenv import load_dotenv


def _load_env_files(env_file: str | None = None) -> None:
    """加载 .env 文件（可选）。必须在任何 os.getenv 之前执行。

    关键：gateway/adapters 在模块 import 时读取 base_url 环境变量（见 openai_adapter 顶部），
    而 main.py 先 import config 再 import router，因此本函数在 config 模块顶部调用，
    能保证 .env 的值先于适配器读取生效。
    """
    if env_file:
        # 显式指定配置文件（ZHIJIN_ENV_FILE）
        load_dotenv(env_file)
        return
    # 仓库 deploy/.env（gitignore 忽略，含中间件 + 模型供应商 Key/地址）；
    # 解析路径：app/config.py 的 parents[2] 即仓库根（app → zhijin-ai → 根）。
    load_dotenv(Path(__file__).resolve().parents[2] / "deploy" / ".env")
    # cwd 下 .env 兜底（本地开发覆盖，如 zhijin-ai/.env）
    load_dotenv(Path.cwd() / ".env")


_load_env_files(os.getenv("ZHIJIN_ENV_FILE"))

_NACOS_SERVER = os.getenv("NACOS_ADDR", "127.0.0.1:8848")
_NACOS_USERNAME = os.getenv("NACOS_USERNAME", "")
_NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "")
_SERVICE_NAME = "zhijin-ai"
_DATA_ID = "zhijin-ai.yml"
_GROUP = "DEFAULT_GROUP"
_NAMESPACE = "public"
# 请求超时上限：空壳阶段拉取配置属尽力而为，超时即回退本地默认配置。
_NACOS_TIMEOUT_SECONDS = 2.0


def _login_token(client: httpx.Client) -> str:
    """登录 Nacos 获取 accessToken（v1 认证接口，3.x 仍可用）。"""
    resp = client.post(
        f"http://{_NACOS_SERVER}/nacos/v1/auth/login",
        data={"username": _NACOS_USERNAME, "password": _NACOS_PASSWORD},
        timeout=_NACOS_TIMEOUT_SECONDS,
    )
    data = resp.json()
    return data.get("accessToken", "")


def load_config() -> dict:
    """返回服务配置字典。Nacos 不可用时使用本地默认值，保证空壳可独立启动。"""
    default = {
        "app.name": _SERVICE_NAME,
        "log.level": os.getenv("LOG_LEVEL", "INFO"),
    }
    try:
        with httpx.Client() as client:
            token = _login_token(client)
            # Nacos 3.x 客户端接口：GET /nacos/v3/client/cs/config?dataId&groupName&namespaceId
            resp = client.get(
                f"http://{_NACOS_SERVER}/nacos/v3/client/cs/config",
                params={
                    "dataId": _DATA_ID,
                    "groupName": _GROUP,
                    "namespaceId": _NAMESPACE,
                    "accessToken": token,
                },
                timeout=_NACOS_TIMEOUT_SECONDS,
            )
            body = resp.json()
            if body.get("code") == 0 and body.get("data") is not None:
                # V1 空壳仅打日志证明连通；完整解析留给计划 C。
                content = body["data"].get("content", "")
                print(f"[config] 已从 Nacos 拉取 {_DATA_ID}: {len(content)} chars")
            else:
                print(f"[config] Nacos 返回 {body.get('code')}: {body.get('message')}，使用本地默认配置")
    except Exception as exc:  # noqa: BLE001 - 空壳阶段 Nacos 不可用不致命
        print(f"[config] Nacos 不可用，使用本地默认配置: {exc}")
    return default
