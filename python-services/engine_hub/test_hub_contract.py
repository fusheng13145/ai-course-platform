"""Engine Hub 契约测试：验证"单端口合并"的三条硬约束。

1. **前缀不剥离**：`/api/v1/graph/health` 必须命中原 kg 服务的全路径路由；
   若实现改成 Starlette `Mount`（剥前缀），该路径会变成 `/health` 而 404 —— 这条钉死它。
2. **能力一个不少**：kg / deepgraph / rag / ft 四个能力视图在 `/health` 里都 ready，
   缺依赖时如实 degraded（不静默当成 ok）。
3. **未知前缀 fail-closed**：不在名单里的路径返回 404 且列出可用前缀，
   而不是把请求吞掉或回落到某个子 app（后者会让错路由"看起来成功"）。

运行：`python -m pytest python-services/engine_hub/test_hub_contract.py -q`
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

hub = pytest.importorskip("hub", reason="engine_hub 依赖 fastapi/httpx")

from fastapi.testclient import TestClient  # noqa: E402


@pytest.fixture(scope="module")
def client():
    return TestClient(hub.app)


CAPABILITIES = ("kg", "deepgraph", "rag", "ft")


def _ready(capability: str) -> bool:
    body = hub._hub_health()
    return next(s["ready"] for s in body["services"] if s["capability"] == capability)


def test_hub_health_lists_all_capabilities(client):
    body = client.get("/health").json()
    assert body["code"] == 200
    assert body["mode"] == "ENGINE_HUB"
    assert {s["capability"] for s in body["services"]} == set(CAPABILITIES)
    assert body["status"] in ("ok", "degraded")


def test_hub_health_degraded_is_honest():
    """加载失败的引擎必须 status=degraded 且带 error，不能伪装成 ok。"""
    body = hub._hub_health()
    if not hub._LOAD_ERRORS:
        assert body["status"] == "ok"
        return
    assert body["status"] == "degraded"
    assert any(s["error"] for s in body["services"])


@pytest.mark.parametrize("path", [
    "/api/v1/graph/health",
    "/api/v1/deepgraph/health",
    "/api/v1/rag/health",
    "/api/v1/fine-tune/health",
])
def test_child_health_reachable_under_original_prefix(client, path):
    """子服务 health 端点在各自原始前缀下可达（证明前缀未被剥离）。"""
    capability = "kg" if "/graph/" in path else "deepgraph" if "/deepgraph/" in path \
        else "rag" if "/rag/" in path else "ft"
    if not _ready(capability):
        pytest.skip(f"{capability} 引擎未加载（依赖缺失），跳过该前缀")
    response = client.get(path)
    assert response.status_code == 200, f"{path} -> {response.status_code} {response.text[:120]}"
    assert response.json().get("status") == "ok", response.text[:160]


def test_unknown_prefix_is_404_with_prefix_list(client):
    response = client.get("/api/v1/unknown/health")
    assert response.status_code == 404
    body = response.json()
    assert body["code"] == 404
    assert set(body["prefixes"]) == {
        "/api/v1/graph", "/api/v1/deepgraph", "/api/v1/rag", "/api/v1/fine-tune",
    }


def test_route_table_has_no_crossing_prefixes():
    """前缀之间不能互为前缀（否则分发顺序决定语义，属隐式 bug）。"""
    prefixes = [p for p, _, _ in hub._ROUTES]
    for left in prefixes:
        for right in prefixes:
            if left != right:
                assert not right.startswith(left + "/"), f"{left} 与 {right} 前缀交叉"


def test_all_services_share_one_port():
    """合并的意义：所有能力都在同一端口暴露（回滚口 separate 布局才有各自的端口）。"""
    assert hub.DEFAULT_PORT == 8600
    body = TestClient(hub.app).get("/health").json()
    assert body["port"] == 8600
