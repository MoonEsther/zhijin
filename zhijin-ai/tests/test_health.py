from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_returns_up() -> None:
    """健康检查端点必须返回 UP。"""
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "UP"
