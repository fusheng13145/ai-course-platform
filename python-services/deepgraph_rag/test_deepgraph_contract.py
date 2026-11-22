# -*- coding: utf-8 -*-
"""DeepGraph RAG（8501）契约测试：守护端点路径、降级语义与内部认证，供 CI 护栏使用。

运行方式：`python -m pytest python-services/deepgraph_rag -q`（依赖与 8002 同款轻量栈，
重依赖缺失时引擎按 UNAVAILABLE 降级，本文件全部用例仍应全绿）。
"""
import os
import sys

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import api_server  # noqa: E402

client = TestClient(api_server.app)


# ---------------- 健康检查 ----------------
def test_health_shape():
    resp = client.get("/api/v1/deepgraph/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert set(body.keys()) >= {"status", "engine", "neo4j", "ollama", "courses_indexed"}
    engine = body["engine"]
    assert set(engine.keys()) >= {
        "available", "dep_errors", "provider", "provider_detail", "model",
        "embeddings_model", "cross_encoder", "reranker_loaded",
    }
    assert isinstance(engine["available"], bool)


# ---------------- 服务间内部认证（与 8001/8002/8003 同款） ----------------
def test_internal_auth_guard(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_KEY", "secret-key")
    try:
        resp = client.post("/api/v1/deepgraph/retrieve", json={"query": "TCP", "course_id": 1})
        assert resp.status_code == 401
        resp = client.post(
            "/api/v1/deepgraph/retrieve",
            json={"query": "TCP", "course_id": 1},
            headers={"X-Internal-Key": "secret-key"},
        )
        assert resp.status_code != 401
    finally:
        monkeypatch.delenv("INTERNAL_API_KEY", raising=False)


def test_health_exempt_from_internal_auth(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_KEY", "secret-key")
    try:
        resp = client.get("/api/v1/deepgraph/health")
        assert resp.status_code == 200
    finally:
        monkeypatch.delenv("INTERNAL_API_KEY", raising=False)


# ---------------- 降级契约（HTTP 503 + code/mode/error，绝不自称 ENGINE） ----------------
def _assert_degraded(resp):
    assert resp.status_code == 503
    body = resp.json()
    assert body["code"] == 503
    assert body["mode"] == "UNAVAILABLE"
    assert body.get("error")
    return body


def test_retrieve_degrades_without_pipeline(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setattr(api_server, "_PIPELINES", {})
    monkeypatch.setattr(api_server, "_rebuild_pipeline_from_disk", lambda cid: None)
    resp = client.post("/api/v1/deepgraph/retrieve", json={"query": "三次握手", "course_id": 4178})
    body = _assert_degraded(resp)
    assert "尚未构建文档检索索引" in body["error"]


def test_qa_degrades_without_pipeline(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setattr(api_server, "_PIPELINES", {})
    monkeypatch.setattr(api_server, "_rebuild_pipeline_from_disk", lambda cid: None)
    resp = client.post("/api/v1/deepgraph/qa", json={"question": "什么是 UDP", "course_id": 4178})
    body = _assert_degraded(resp)
    assert "尚未构建文档检索索引" in body["error"]


def test_ingest_degrades_when_engine_unavailable(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: False)
    monkeypatch.setattr(api_server, "engine_dep_errors", lambda: ["ImportError: demo"])
    resp = client.post(
        "/api/v1/deepgraph/ingest",
        json={"course_id": 1, "document_name": "测试.txt", "content": "TCP 三次握手。"},
    )
    body = _assert_degraded(resp)
    assert "引擎依赖不可用" in body["error"]


def test_learning_path_degrades_when_engine_unavailable(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: False)
    monkeypatch.setattr(api_server, "engine_dep_errors", lambda: ["ImportError: demo"])
    resp = client.post(
        "/api/v1/deepgraph/learning-path",
        json={"target": "TCP协议", "course_id": 1},
    )
    _assert_degraded(resp)


def test_retrieve_degrades_on_engine_runtime_error(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    pipeline = {"ensemble": object(), "knowledge_graph": None, "reranker": None,
                "doc_names": ["course1_x.txt"], "chunks": []}
    monkeypatch.setattr(api_server, "_PIPELINES", {1: pipeline})
    monkeypatch.setattr(api_server, "_sync_session_state", lambda a, b: None)
    monkeypatch.setattr(api_server, "_apply_flags", lambda flags: None)

    def boom(*args, **kwargs):
        raise RuntimeError("embedding down")

    monkeypatch.setattr(api_server, "retrieve_documents", boom)
    resp = client.post("/api/v1/deepgraph/retrieve", json={"query": "TCP", "course_id": 1})
    body = _assert_degraded(resp)
    assert "检索失败" in body["error"]


# ---------------- 输入校验 ----------------
def test_qa_answer_mode_validated(monkeypatch):
    monkeypatch.setattr(api_server, "_PIPELINES", {1: {"doc_names": ["course1_x.txt"]}})
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    resp = client.post(
        "/api/v1/deepgraph/qa",
        json={"question": "q", "course_id": 1, "answer_mode": "瞎写的模式"},
    )
    assert resp.status_code == 422


def test_chapter_graph_requires_chapter_name_when_chapters_exist(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setattr(api_server, "_course_doc_name", lambda cid, name: f"course{cid}_{name}.txt")

    class FakePersistence:
        GLOBAL_GRAPH_DIR = ""

        @staticmethod
        def load_chapters(name):
            return {"第1章 概述": "计算机网络概述正文……"}

    monkeypatch.setattr(api_server, "persistence", FakePersistence)
    resp = client.post(
        "/api/v1/deepgraph/chapter-graph",
        json={"course_id": 1, "document_name": "教材"},
    )
    assert resp.status_code == 422
    resp = client.post(
        "/api/v1/deepgraph/chapter-graph",
        json={"course_id": 1, "document_name": "教材", "chapter_name": "不存在的章"},
    )
    assert resp.status_code == 404


def test_chapter_graph_degrades_when_no_chapter_data(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setattr(api_server, "_course_doc_name", lambda cid, name: f"course{cid}_{name}.txt")

    class FakePersistence:
        GLOBAL_GRAPH_DIR = ""

        @staticmethod
        def load_chapters(name):
            return None

    monkeypatch.setattr(api_server, "persistence", FakePersistence)
    resp = client.post(
        "/api/v1/deepgraph/chapter-graph",
        json={"course_id": 1, "document_name": "教材"},
    )
    body = _assert_degraded(resp)
    assert "没有章节切分数据" in body["error"]


# ---------------- 文档管理 ----------------
def test_delete_missing_document_returns_404(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)

    class FakePersistence:
        @staticmethod
        def get_saved_document_path(name):
            return None

    monkeypatch.setattr(api_server, "persistence", FakePersistence)
    resp = client.delete("/api/v1/deepgraph/documents/nope.txt")
    assert resp.status_code == 404


# ---------------- 课程知识图谱桥接（抽取三元组 → kg_service 8001） ----------------

def test_sync_chapter_graph_to_course_kg_idempotent(monkeypatch):
    """桥接语义：按名幂等建点、三元组判重跳过、kg 不可达时整体跳过不半同步。"""
    import networkx as nx

    created_nodes, created_relations = [], []
    kg_state = {
        "nodes": [{"id": 7000083, "name": "TCP"}],
        "edges": [{"source_id": 7000083, "target_id": 7000083, "relation_type": "包含"}],
    }
    next_id = iter(range(7000084, 7000100))

    class FakeResp:
        def __init__(self, body):
            self._body = body

        def raise_for_status(self):
            pass

        def json(self):
            return self._body

    def fake_get(url, timeout=10):
        return FakeResp(kg_state)

    def fake_post(url, json=None, timeout=10):
        if url.endswith("/nodes"):
            node_id = next(next_id)
            kg_state["nodes"].append({"id": node_id, "name": json["name"]})
            created_nodes.append(json["name"])
            return FakeResp({"id": node_id, **json})
        created_relations.append((json["source_id"], json["target_id"], json["relation_type"]))
        kg_state["edges"].append({"source_id": json["source_id"],
                                  "target_id": json["target_id"],
                                  "relation_type": json["relation_type"]})
        return FakeResp({"id": 1, **json})

    monkeypatch.setattr(api_server.requests, "get", fake_get)
    monkeypatch.setattr(api_server.requests, "post", fake_post)

    g = nx.Graph()
    g.add_edge("TCP", "三次握手", label="包含")
    g.add_edge("UDP", "无连接", label="定义")

    out = api_server.sync_chapter_graph_to_course_kg(4178, g, "course4178_教材.txt")
    assert out["added_nodes"] == 3  # 三次握手、UDP、无连接（TCP 已存在）
    assert out["added_relations"] == 2
    assert set(created_nodes) == {"三次握手", "UDP", "无连接"}

    # 重放同一图：全部命中已存在三元组/节点，零新增（幂等）
    out2 = api_server.sync_chapter_graph_to_course_kg(4178, g, "course4178_教材.txt")
    assert out2["added_nodes"] == 0 and out2["added_relations"] == 0


def test_sync_empty_course_graph_is_reachable_and_bootstraps(monkeypatch):
    """新课程的空图谱是可达状态，不能误判为 kg_unreachable：首次入库必须能建点建边。"""
    import networkx as nx

    kg_state = {"nodes": [], "edges": []}
    created_nodes, created_relations = [], []
    next_id = iter(range(7000100, 7000200))

    class FakeResp:
        def __init__(self, body):
            self._body = body

        def raise_for_status(self):
            pass

        def json(self):
            return self._body

    def fake_get(url, timeout=10):
        return FakeResp(kg_state)

    def fake_post(url, json=None, timeout=10):
        if url.endswith("/nodes"):
            node_id = next(next_id)
            kg_state["nodes"].append({"id": node_id, "name": json["name"]})
            created_nodes.append(json["name"])
            return FakeResp({"id": node_id, **json})
        created_relations.append((json["source_id"], json["target_id"], json["relation_type"]))
        kg_state["edges"].append({"source_id": json["source_id"],
                                  "target_id": json["target_id"],
                                  "relation_type": json["relation_type"]})
        return FakeResp({"id": 1, **json})

    monkeypatch.setattr(api_server.requests, "get", fake_get)
    monkeypatch.setattr(api_server.requests, "post", fake_post)

    g = nx.Graph()
    g.add_edge("TCP", "可靠传输", label="定义")

    out = api_server.sync_chapter_graph_to_course_kg(9905, g, "course9905_stage5.txt")
    assert out["added_nodes"] == 2
    assert out["added_relations"] == 1
    assert set(created_nodes) == {"TCP", "可靠传输"}
    assert created_relations == [(7000100, 7000101, "定义")]


def test_source_name_ignores_loader_temp_path():
    """TextLoader/PyPDFLoader 的临时 source 不能冒充课程文档名。"""
    class Doc:
        metadata = {"source": r"C:\Users\x\AppData\Local\Temp\tmpmj_o5fa3.txt"}

    assert api_server._source_name_for(
        Doc(), {"doc_names": ["course9905_stage5-rag-corpus.txt"]}
    ) == "course9905_stage5-rag-corpus.txt"


def test_sync_skips_when_kg_unreachable(monkeypatch):
    import networkx as nx

    def boom(url, timeout=10):
        raise RuntimeError("kg down")

    monkeypatch.setattr(api_server.requests, "get", boom)
    g = nx.Graph()
    g.add_edge("a", "b", label="相关")
    out = api_server.sync_chapter_graph_to_course_kg(1, g, "doc")
    assert out["skipped"] == "kg_unreachable"


def test_sync_disabled_by_env(monkeypatch):
    monkeypatch.setenv("DG_KG_SYNC", "0")
    try:
        import networkx as nx
        g = nx.Graph()
        g.add_edge("a", "b", label="相关")
        out = api_server.sync_chapter_graph_to_course_kg(1, g, "doc")
        assert out["skipped"] == "disabled_or_empty"
    finally:
        monkeypatch.delenv("DG_KG_SYNC", raising=False)


# ---------------- 阶段 4：身份/trace 透传、限流、分页、上传、真实分数语义 ----------------
def test_trace_and_user_headers_echoed(monkeypatch):
    resp = client.get(
        "/api/v1/deepgraph/health",
        headers={"X-Trace-Id": "trace-stage4", "X-User-Id": "teacher-1"},
    )
    assert resp.status_code == 200
    assert resp.headers.get("X-Trace-Id") == "trace-stage4"
    assert resp.headers.get("X-User-Id") == "teacher-1"


def test_trace_id_is_generated_when_absent():
    resp = client.get("/api/v1/deepgraph/health")
    assert resp.status_code == 200
    assert resp.headers.get("X-Trace-Id")


def test_internal_auth_required_without_key_fails_closed(monkeypatch):
    monkeypatch.setenv("DG_INTERNAL_AUTH_MODE", "required")
    monkeypatch.delenv("INTERNAL_API_KEY", raising=False)
    try:
        resp = client.post("/api/v1/deepgraph/retrieve", json={"query": "TCP", "course_id": 1})
        assert resp.status_code == 503
        body = resp.json()
        assert body["code"] == 503
        assert "INTERNAL_API_KEY" in body["message"]
    finally:
        monkeypatch.delenv("DG_INTERNAL_AUTH_MODE", raising=False)


def test_rate_limit_returns_429(monkeypatch):
    monkeypatch.setenv("DG_RATE_LIMIT_PER_MINUTE", "2")
    api_server._RATE_BUCKETS.clear()
    try:
        headers = {"X-User-Id": "rate-limit-probe"}
        first = client.get("/api/v1/deepgraph/documents", headers=headers)
        second = client.get("/api/v1/deepgraph/documents", headers=headers)
        third = client.get("/api/v1/deepgraph/documents", headers=headers)
        assert first.status_code != 429 and second.status_code != 429
        assert third.status_code == 429
        assert third.json()["code"] == 429
    finally:
        api_server._RATE_BUCKETS.clear()
        monkeypatch.delenv("DG_RATE_LIMIT_PER_MINUTE", raising=False)


def test_documents_pagination_and_course_identity(monkeypatch):
    class FakePersistence:
        GLOBAL_GRAPH_DIR = ""

        @staticmethod
        def load_metadata():
            return {"doc_names": [f"course7_doc{i}.txt" for i in range(5)]}

        @staticmethod
        def load_chapters(_name):
            return {}

        @staticmethod
        def get_saved_document_path(_name):
            return "x"

        @staticmethod
        def safe_hash_name(name):
            return name

    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setattr(api_server, "persistence", FakePersistence)
    body = client.get("/api/v1/deepgraph/documents?page=2&page_size=2").json()
    assert body["total"] == 5
    assert body["page"] == 2 and body["page_size"] == 2
    assert body["has_more"] is True
    assert [d["document_name"] for d in body["documents"]] == ["course7_doc2.txt", "course7_doc3.txt"]
    assert body["documents"][0]["course_id"] == 7


def test_ingest_file_accepts_raw_pdf_body(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    captured = {}

    def fake_ingest(course_id, document_name, content, h, g, c):
        captured.update({
            "course_id": course_id, "document_name": document_name,
            "content": content, "build_chapter": c,
        })
        return {"success": True, "mode": "ENGINE", "code": 200}

    monkeypatch.setattr(api_server, "_ingest_document_serialized", fake_ingest)
    resp = client.post(
        "/api/v1/deepgraph/ingest-file?course_id=7&document_name=教材.pdf",
        content=b"%PDF-1.4 fake",
        headers={"Content-Type": "application/pdf"},
    )
    assert resp.status_code == 200
    assert captured["course_id"] == 7
    assert captured["content"] == b"%PDF-1.4 fake"
    assert captured["build_chapter"] is True


def test_ingest_file_rejects_unsupported_suffix(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    resp = client.post(
        "/api/v1/deepgraph/ingest-file?course_id=7&document_name=教材.exe",
        content=b"binary",
    )
    assert resp.status_code == 415
    assert resp.json()["code"] == 415


def test_ingest_file_rejects_oversized_body(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    monkeypatch.setenv("DG_MAX_UPLOAD_BYTES", "8")
    try:
        resp = client.post(
            "/api/v1/deepgraph/ingest-file?course_id=7&document_name=教材.pdf",
            content=b"%PDF-1.4 way too long",
            headers={"Content-Type": "application/pdf"},
        )
        assert resp.status_code == 413
        assert resp.json()["code"] == 413
    finally:
        monkeypatch.delenv("DG_MAX_UPLOAD_BYTES", raising=False)


def test_retrieve_does_not_fabricate_relevance_score(monkeypatch):
    monkeypatch.setattr(api_server, "engine_available", lambda: True)
    pipeline = {"ensemble": object(), "knowledge_graph": None, "reranker": None,
                "doc_names": ["course1_x.txt"], "chunks": []}
    monkeypatch.setattr(api_server, "_PIPELINES", {1: pipeline})
    monkeypatch.setattr(api_server, "_sync_session_state", lambda a, b: None)
    monkeypatch.setattr(api_server, "_apply_flags", lambda flags: None)

    class FakeDoc:
        page_content = "TCP 三次握手"
        metadata = {"source": "course1_x.txt"}

    monkeypatch.setattr(api_server, "retrieve_documents", lambda *a, **k: [FakeDoc()])
    body = client.post(
        "/api/v1/deepgraph/retrieve", json={"query": "TCP", "course_id": 1}
    ).json()
    assert body["mode"] == "ENGINE"
    assert body["results"][0]["relevance_score"] is None
    assert body["results"][0]["rank"] == 1
    assert body["results"][0]["document_name"] == "course1_x.txt"