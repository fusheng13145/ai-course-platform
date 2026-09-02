"""
RAG 检索引擎（8002）契约冒烟测试
==================================
验证 docs/项目手册.md（第八部分）与 Java 端 `RAGServiceImpl` 下游调用对齐的契约端点。
使用 FastAPI TestClient 内嵌运行，不依赖外部端口，CI 中稳定快速。

⚠️ 本测试守卫的是**契约（端点路径 + JSON 形状）**，而非 RAG 引擎语义——
引擎（BM25/向量/embedding/融合）由团队另一成员实现；rag_service.py 仅为占位骨架。
队友替换引擎逻辑时，必须保持以下端点与响应结构与本测试一致。

本地运行：
    pip install -r requirements.txt pytest
    pytest -q contract_test.py
"""

from fastapi.testclient import TestClient

from rag_service import app


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


def test_retrieve_returns_added_document():
    with TestClient(app) as c:
        c.post(
            "/api/v1/rag/documents",
            json={"course_id": 2, "document_name": "OSI 模型", "content": "七层结构"},
        )
        resp = c.post(
            "/api/v1/rag/retrieve",
            json={"query": "OSI", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        assert len(resp.json()["results"]) >= 1


def test_ask_returns_contract_shape():
    with TestClient(app) as c:
        resp = c.post(
            "/api/v1/rag/ask",
            json={"question": "什么是 TCP？", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert "answer" in body
        assert body["code"] == 200


def test_index_build():
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents",
               json={"course_id": 3, "document_name": "X", "content": "x"})
        resp = c.post("/api/v1/rag/index/build/3")
        assert resp.status_code == 200
        assert resp.json()["success"] is True
