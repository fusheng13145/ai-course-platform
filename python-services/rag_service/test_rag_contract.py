"""
RAG 检索引擎（8002）契约冒烟测试
==================================
验证 docs/项目手册.md（第八部分）与 Java 端 `RAGServiceImpl` 下游调用对齐的契约端点。
使用 FastAPI TestClient 内嵌运行，不依赖外部端口，CI 中稳定快速。

⚠️ 本测试守卫的是**契约（端点路径 + JSON 形状）**：
演进引擎逻辑（rag_engine.py）时，必须保持以下端点与响应结构与本测试一致。

本地运行：
    pip install -r requirements.txt pytest
    pytest -q test_rag_contract.py

引擎说明：真实检索引擎在 rag_engine.py（BM25+向量混合召回），重依赖懒加载；
本测试在 CI（无引擎依赖）下验证占位/降级契约，在本地（依赖齐备）额外验证真实检索。
"""

import pytest
from fastapi.testclient import TestClient

import rag_engine
from rag_service import MSG_RAG_UNAVAILABLE, app


@pytest.fixture(autouse=True)
def isolated_store(tmp_path, monkeypatch):
    """每个用例使用独立临时数据目录，避免测试间/与本地真实数据互相污染。"""
    monkeypatch.setenv("RAG_DATA_DIR", str(tmp_path))
    rag_engine.reset_store()
    yield
    rag_engine.reset_store()


def test_health():
    with TestClient(app) as c:
        resp = c.get("/api/v1/rag/health")
        assert resp.status_code == 200


def test_document_add_and_list():
    with TestClient(app) as c:
        added = c.post(
            "/api/v1/rag/documents",
            json={"course_id": 1, "document_name": "TCP 三次握手", "content": "SYN/ACK 建立连接"},
        )
        assert added.status_code == 200
        did = added.json()["id"]

        listed = c.get("/api/v1/rag/documents", params={"course_id": 1})
        assert listed.status_code == 200
        assert any(d["id"] == did for d in listed.json())


def test_document_update_and_delete():
    with TestClient(app) as c:
        added = c.post(
            "/api/v1/rag/documents",
            json={"course_id": 1, "document_name": "D", "content": "old"},
        ).json()
        did = added["id"]

        upd = c.put(f"/api/v1/rag/documents/{did}", json={"content": "new"})
        assert upd.status_code == 200
        assert upd.json()["content"] == "new"

        deleted = c.delete(f"/api/v1/rag/documents/{did}")
        assert deleted.status_code == 200


def test_retrieve_returns_degraded_503():
    with TestClient(app) as c:
        # 占位骨架：引擎未接入，按手册 §34 返回降级语义（code=503 / mode=UNAVAILABLE）
        resp = c.post(
            "/api/v1/rag/retrieve",
            json={"query": "OSI", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 503
        assert body["mode"] == "UNAVAILABLE"
        # 与 Java 端 ContractPaths.MSG_RAG_UNAVAILABLE 严格一致（契约漂移修复）
        assert body["error"] == MSG_RAG_UNAVAILABLE


def test_ask_returns_degraded_503():
    with TestClient(app) as c:
        resp = c.post(
            "/api/v1/rag/ask",
            json={"question": "什么是 TCP？", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 503
        assert body["mode"] == "UNAVAILABLE"
        # 与 Java 端 ContractPaths.MSG_RAG_UNAVAILABLE 严格一致（契约漂移修复）
        assert body["error"] == MSG_RAG_UNAVAILABLE


# ------------------------------------------------------------------
# 跨语言契约固化（缺陷2 回归）：引擎不可用时，两个检索端点的响应体形状必须同时满足
# code==503、mode=="UNAVAILABLE"、error 非空、结果集为空。
# Java 端 HttpRagProvider 据此把 mode 判为 UNAVAILABLE 并回退纯 LLM；此前它把 mode
# 硬编码为 ENGINE 且忽略 body.code/error，导致降级被当成成功。把形状钉死防再次漂移。
# ------------------------------------------------------------------
@pytest.mark.parametrize("endpoint,payload,result_field", [
    ("/api/v1/rag/retrieve", {"query": "OSI", "course_id": 2, "top_k": 5}, "results"),
    ("/api/v1/rag/ask", {"question": "什么是 TCP？", "course_id": 2, "top_k": 5}, "answer"),
])
def test_unavailable_contract_shape(endpoint, payload, result_field):
    with TestClient(app) as c:
        resp = c.post(endpoint, json=payload)
        assert resp.status_code == 200, "降级仍走 HTTP 200，业务码在 body.code"
        body = resp.json()
        assert body["code"] == 503
        assert body["mode"] == "UNAVAILABLE"
        assert body.get("error"), "error 必须非空，供 Java 端记录降级原因"
        # 结果集必须为空（answer=None 或空串；results=[] / sources=[]）
        assert not body.get(result_field), f"{result_field} 应为空"
        if endpoint.endswith("/ask"):
            assert not body.get("sources"), "sources 应为空"
        else:
            assert body.get("results") == []


def test_top_k_out_of_range_returns_422():
    # 缺陷3：入参边界——top_k 越界（0 / 负 / >50）必须 422，而不是被静默夹取
    with TestClient(app) as c:
        for bad in (0, -1, 51):
            resp = c.post("/api/v1/rag/retrieve",
                          json={"query": "OSI", "course_id": 2, "top_k": bad})
            assert resp.status_code == 422, f"top_k={bad} 应 422"
            resp = c.post("/api/v1/rag/ask",
                          json={"question": "TCP？", "course_id": 2, "top_k": bad})
            assert resp.status_code == 422, f"ask top_k={bad} 应 422"


def test_empty_question_returns_422():
    with TestClient(app) as c:
        resp = c.post("/api/v1/rag/ask",
                      json={"question": "", "course_id": 2, "top_k": 5})
        assert resp.status_code == 422


def test_retrieve_returns_success_schema(monkeypatch):
    # 端到端断言补全：RAG_ENGINE_READY=1 时验证 Java HttpRagProvider 期望的 200 成功结构
    monkeypatch.setenv("RAG_ENGINE_READY", "1")
    with TestClient(app) as c:
        resp = c.post(
            "/api/v1/rag/retrieve",
            json={"query": "OSI", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 200
        assert body["mode"] == "ENGINE"
        results = body["results"]
        assert len(results) >= 1
        for r in results:
            assert r["document_id"] is not None
            assert r["document_name"] is not None
            assert r["relevance_score"] is not None
            assert r["snippet"] is not None


def test_ask_returns_success_schema(monkeypatch):
    # 端到端断言补全：RAG_ENGINE_READY=1 时验证 Java HttpRagProvider 期望的 200 成功结构
    monkeypatch.setenv("RAG_ENGINE_READY", "1")
    with TestClient(app) as c:
        resp = c.post(
            "/api/v1/rag/ask",
            json={"question": "什么是 TCP？", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 200
        assert body["mode"] == "ENGINE"
        assert body["answer"]
        sources = body["sources"]
        assert len(sources) >= 1
        for s in sources:
            assert s["document_id"] is not None
            assert s["document_name"] is not None
            assert s["relevance_score"] is not None
            assert s["snippet"] is not None


def test_index_build():
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents",
               json={"course_id": 3, "document_name": "X", "content": "x"})
        resp = c.post("/api/v1/rag/index/build/3")
        assert resp.status_code == 200
        assert resp.json()["success"] is True


def test_clear_index():
    with TestClient(app) as c:
        # 补全手册 §32 列出的 DELETE /api/v1/rag/index/{courseId} 清理索引端点
        c.post("/api/v1/rag/documents",
               json={"course_id": 4, "document_name": "Y", "content": "y"})
        resp = c.delete("/api/v1/rag/index/4")
        assert resp.status_code == 200
        assert resp.json()["success"] is True


# ------------------------------------------------------------------
# 真实引擎验证（rag_engine.py：BM25+向量混合召回）
# CI 轻量环境（requirements.txt 不含 torch/faiss 等）自动跳过
# ------------------------------------------------------------------
requires_engine = pytest.mark.skipif(
    not rag_engine.engine_available(),
    reason="引擎重依赖未安装（requirements-engine.txt），跳过真实检索验证",
)


@requires_engine
def test_real_engine_build_index_and_hybrid_retrieve():
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents", json={
            "course_id": 7, "document_name": "TCP 三次握手",
            "content": "TCP通过三次握手建立连接：客户端发送SYN，服务端回复SYN/ACK，客户端再发送ACK确认。"
                       "三次握手可以防止已失效的连接请求报文段突然又传送到了服务端。"})
        c.post("/api/v1/rag/documents", json={
            "course_id": 7, "document_name": "OSI 七层模型",
            "content": "OSI参考模型分为应用层、表示层、会话层、传输层、网络层、数据链路层和物理层七层。"})

        built = c.post("/api/v1/rag/index/build/7")
        assert built.status_code == 200
        bb = built.json()
        assert bb["success"] is True, bb.get("error")
        assert bb["indexed_chunks"] >= 2

        got = c.post("/api/v1/rag/retrieve",
                     json={"query": "三次握手是怎么建立的", "course_id": 7, "top_k": 2})
        body = got.json()
        assert body["code"] == 200
        assert body["mode"] == "ENGINE"
        assert len(body["results"]) >= 1
        top = body["results"][0]
        assert top["document_name"] == "TCP 三次握手"
        assert 0 < top["relevance_score"] <= 1.0


@requires_engine
def test_real_engine_ask_returns_grounded_answer():
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents", json={
            "course_id": 8, "document_name": "进程与线程",
            "content": "进程是操作系统资源分配的基本单位，拥有独立地址空间。"
                       "线程是CPU调度的基本单位，同一进程内的线程共享堆和全局变量。"})
        assert c.post("/api/v1/rag/index/build/8").json()["success"] is True

        asked = c.post("/api/v1/rag/ask",
                       json={"question": "进程和线程的区别是什么", "course_id": 8, "top_k": 3})
        body = asked.json()
        assert body["code"] == 200
        assert body["mode"] == "ENGINE"
        assert body["answer"]
        assert all(s["snippet"] for s in body["sources"])


@requires_engine
def test_real_engine_degrades_without_index():
    with TestClient(app) as c:
        # 有文档但未建索引 -> 仍按契约降级（503/UNAVAILABLE），error 给出可操作提示
        c.post("/api/v1/rag/documents",
               json={"course_id": 9, "document_name": "D", "content": "内容"})
        resp = c.post("/api/v1/rag/retrieve",
                      json={"query": "D", "course_id": 9, "top_k": 3})
        body = resp.json()
        assert body["code"] == 503
        assert body["mode"] == "UNAVAILABLE"
        assert "index/build" in body["error"]
