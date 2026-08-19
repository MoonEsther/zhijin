"""AI 服务配置：优先从 Nacos 拉取，失败回退环境变量。

说明：当前可安装的 nacos-sdk-python（3.x / v2 SDK）为异步客户端，会创建后台
gRPC 线程，在短生命周期进程（测试、空壳启动）中会导致进程无法正常退出。
因此 V1 空壳改用 Nacos Open API（HTTP）拉取配置；sdk 依赖按计划保留，供
计划 C 正式接入时使用。
"""
import os

import httpx

_NACOS_SERVER = os.getenv("NACOS_ADDR", "127.0.0.1:8848")
_SERVICE_NAME = "zhijin-ai"
_DATA_ID = "zhijin-ai.yml"
_GROUP = "DEFAULT_GROUP"
# 请求超时上限：空壳阶段拉取配置属尽力而为，超时即回退本地默认配置。
_NACOS_TIMEOUT_SECONDS = 2.0


def load_config() -> dict:
    """返回服务配置字典。Nacos 不可用时使用本地默认值，保证空壳可独立启动。"""
    default = {
        "app.name": _SERVICE_NAME,
        "log.level": os.getenv("LOG_LEVEL", "INFO"),
    }
    try:
        # 通过 Nacos Open API 拉取配置：GET /nacos/v1/cs/configs?dataId=X&group=Y
        resp = httpx.get(
            f"http://{_NACOS_SERVER}/nacos/v1/cs/configs",
            params={"dataId": _DATA_ID, "group": _GROUP},
            timeout=_NACOS_TIMEOUT_SECONDS,
        )
        if resp.status_code == 200 and resp.text:
            # V1 空壳仅打日志证明连通；完整解析留给计划 C。
            print(f"[config] 已从 Nacos 拉取 {_DATA_ID}: {len(resp.text)} chars")
        else:
            print(f"[config] Nacos 返回状态 {resp.status_code}，使用本地默认配置")
    except Exception as exc:  # noqa: BLE001 - 空壳阶段 Nacos 不可用不致命
        print(f"[config] Nacos 不可用，使用本地默认配置: {exc}")
    return default
