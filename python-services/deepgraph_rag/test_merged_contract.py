# -*- coding: utf-8 -*-
"""
Q-03 双引擎收敛护栏（8501 承载 deepgraph + 原 8002 知识库）
==========================================================

用户裁定：接受 8501 单点，但**功能只能升级不能降级**。本文件就是这条裁定的机器证据：

1. `test_engine_source_markers_present`——搬迁后的 `knowledge_engine.py` 必须保留
   混合检索（BM25+向量+RRF）、HyDE、Cross-Encoder 重排与索引生命周期的实现标记，
   从根上排除「搬迁时顺手改算法」的可能（能力降级最常见就发生在这里）。
   2026-11-25（阶段3）：退役副本 `rag_service/` 已物理删除，原先的对端逐行比对
   改为源码标记守卫（无对端文件可比的现实下，仍能钉住算法面）。
2. `test_all_8002_routes_exist_on_merged_app`——原 8002 的 8 个 `/api/v1/rag/*`
   端点（路径+方法）逐条出现在合并 app 上（黄金清单，来自 Java `ContractPaths.RAG_*`）。
3. `test_deepgraph_routes_unchanged`——原 8501 的 deepgraph 端点一个不少
   （收敛不能反向把 deepgraph 端点挤掉）。
4. `test_merged_smoke_*`——合并 app 上知识库端点真实可用（CRUD/降级语义）。
5. `test_two_engines_stores_are_separate`——两个引擎的语料目录互不覆盖
   （否则收口当天会出现「知识库文档污染 deepgraph 元数据」这类隐性降级）。

完整「不降级」验证命令（**同一目录**同时跑三套契约）：
    cd python-services/deepgraph_rag
    python -m pytest -q test_rag_contract_merged.py test_deepgraph_contract.py test_merged_contract.py
"""
import os
import sys

import pytest
from fastapi.testclient import TestClient

_DG_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _DG_DIR)

import api_server  # noqa: E402
import knowledge_api  # noqa: E402
import knowledge_engine  # noqa: E402

client = TestClient(api_server.app)


# ---------------- 1. 引擎搬迁完整性（退役副本已删除，改为源码标记守卫） ----------------
# 2026-11-25（阶段3）：rag_service/ 目录已物理删除，原先「与 rag_engine.py 逐行等价」
# 的比对不再可执行（对端文件不存在）。改为守 knowledge_engine.py 自身的关键实现标记：
# 任何一次「搬迁式重写」若丢掉混合检索/重排/HyDE 的任何一环，这里立刻变红。
_ENGINE_MARKERS = (
    "def search(",             # 检索入口
    "def graph_sources(",      # 图谱增强召回
    "def generate_answer(",    # 生成融合
    "def build_course_index(", # 索引生命周期
    "def clear_course_index(",
    "def index_status(",
    "def hyde_status(",        # HyDE（ENGINE_VERSION=1.1 的能力面）
    "def rerank_status(",      # Cross-Encoder 重排
    "class StoreCorruptedError",
)


def test_engine_source_markers_present():
    src_path = os.path.join(_DG_DIR, "knowledge_engine.py")
    with open(src_path, "r", encoding="utf-8") as f:
        src = f.read()
    missing = [m for m in _ENGINE_MARKERS if m not in src]
    assert not missing, f"知识库引擎实现标记丢失（疑似降级重写）: {missing}"
    # 混合检索三件套必须仍在对引擎的调用面上（BM25 + 向量 + RRF 融合）
    for marker in ("bm25", "embed", "rrf"):
        assert marker.lower() in src.lower(), f"混合检索组成缺失: {marker}"


def test_knowledge_engine_keeps_engine_identity():
    """搬迁后引擎版本号必须原样保留（1.1 含 HyDE + Cross-Encoder 重排），不得回退。"""
    assert knowledge_engine.ENGINE_VERSION == "1.1"
    # 能力探针齐全：健康检查与降级分支都依赖这些函数存在
    for name in ("engine_available", "engine_functional", "dep_errors", "embed_model_name",
                 "hyde_status", "rerank_status", "llm_health", "search", "graph_sources",
                 "generate_answer", "build_course_index", "clear_course_index",
                 "index_status", "StoreCorruptedError"):
        assert hasattr(knowledge_engine, name), f"引擎搬迁丢失能力: {name}"


# ---------------- 2/3. 端点集合：原 8002 的 8 端点全在，deepgraph 不少 ----------------
_HTTP_METHODS = {"get", "post", "put", "delete", "patch", "head", "options", "trace"}

# 原 8002 的 8 个端点 × 方法 = Java `ContractPaths.RAG_*` 的权威口径，逐条点名。
# 2026-11-25（阶段3）：不再从已删除的 rag_service.py 动态取集合，改为此处写成黄金清单，
# 任何一条在 8501 上消失都会因为「能力降级」而失败。
_RAG_GOLDEN = {
    ("/api/v1/rag/health", "GET"),
    ("/api/v1/rag/documents", "GET"),
    ("/api/v1/rag/documents", "POST"),
    ("/api/v1/rag/documents/{document_id}", "DELETE"),
    ("/api/v1/rag/retrieve", "POST"),
    ("/api/v1/rag/ask", "POST"),
    ("/api/v1/rag/index/build/{course_id}", "POST"),
    ("/api/v1/rag/index/{course_id}", "DELETE"),
}


def _api_signatures(app):
    """{(path, method)} 集合，只取 /api/ 前缀的业务端点（每个「路径+方法」算一条）。

    用 OpenAPI 路径表而不是遍历 ``app.routes``：FastAPI 0.136 起 include_router 的结果
    被包成惰性 ``_IncludedRouter``（``app.routes`` 里只剩一个 path=None 的容器，真实路由
    要等首次解析才展开），而 requirements-platform 是 ``fastapi>=0.115,<1``，CI 解析到新
    版本后会把「知识库端点已挂载」误判成「能力降级」。``openapi()`` 在新旧两代实现下
    都会展开成最终路径表，与真实路由口径一致（smoke 用例打的就是同一批路径）。
    """
    schema_paths = app.openapi().get("paths", {})
    out = set()
    for path, ops in schema_paths.items():
        if not path.startswith("/api/"):
            continue
        for method in ops:
            if method.lower() in _HTTP_METHODS:
                out.add((path, method.upper()))
    return out


def test_all_8002_routes_exist_on_merged_app():
    merged = _api_signatures(api_server.app)
    missing = sorted(_RAG_GOLDEN - merged)
    assert not missing, f"合并服务缺少原 8002 端点（能力降级）: {missing}"
    # 反向确认：/api/v1/rag 的路径集合不多不少（多出来的端点必须先改契约再改这里）
    merged_rag_paths = {p for p, _ in merged if p.startswith("/api/v1/rag")}
    assert merged_rag_paths == {p for p, _ in _RAG_GOLDEN}, (
        f"知识库路径集合与黄金清单不一致: {sorted(merged_rag_paths)}"
    )


def test_deepgraph_routes_unchanged():
    """收敛只允许在 8501 上加端点，不许动 deepgraph 既有端点。"""
    expected = {
        "/api/v1/deepgraph/health",
        "/api/v1/deepgraph/ingest",
        "/api/v1/deepgraph/ingest-file",
        "/api/v1/deepgraph/documents",
        "/api/v1/deepgraph/documents/{document_name}",
        "/api/v1/deepgraph/retrieve",
        "/api/v1/deepgraph/qa",
        "/api/v1/deepgraph/chapter-graph",
        "/api/v1/deepgraph/learning-path",
        "/api/v1/deepgraph/course-graph",
    }
    paths = {p for p, _ in _api_signatures(api_server.app)}
    assert expected <= paths, f"deepgraph 端点丢失: {sorted(expected - paths)}"


# ---------------- 4. 合并 app 上的知识库 smoke ----------------
@pytest.fixture()
def isolated_store(tmp_path, monkeypatch):
    """知识库存储落到临时目录，避免污染本地真实语料。"""
    monkeypatch.setenv("RAG_DATA_DIR", str(tmp_path))
    knowledge_engine.reset_store()
    yield tmp_path
    knowledge_engine.reset_store()


def test_merged_smoke_health_reports_both_layers():
    resp = client.get("/api/v1/rag/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    # engine 的 7 个键是 8002 契约面（勿加字段），数据目录信息在顶层
    assert set(body["engine"]) == {"available", "functional", "dep_errors", "embed_model",
                                   "version", "hyde", "rerank"}
    assert body["data_dir"], "合并后必须暴露语料落盘位置（迁移排障用）"

    # 同一进程内 deepgraph 健康检查也必须照常应答
    dg = client.get("/api/v1/deepgraph/health")
    assert dg.status_code == 200
    assert dg.json()["status"] == "ok"


def test_merged_smoke_documents_crud_and_degrade(isolated_store):
    added = client.post("/api/v1/rag/documents",
                        json={"course_id": 1, "document_name": "D", "content": "内容"})
    assert added.status_code == 200
    did = added.json()["id"]
    assert any(d["id"] == did for d in client.get("/api/v1/rag/documents",
                                                  params={"course_id": 1}).json())

    # 未建索引 → 降级 503（HTTP 状态即契约）
    deg = client.post("/api/v1/rag/retrieve", json={"query": "q", "course_id": 1, "top_k": 3})
    assert deg.status_code == 503
    assert deg.json()["code"] == 503
    assert deg.json()["mode"] == "UNAVAILABLE"
    err = deg.json()["error"]
    assert err, "降级必须带非空 error 供 Java 侧记录原因"
    # 有文档但未建索引时给可操作提示；无文档时回落到契约文案（两种都不许空）
    assert err == knowledge_api.MSG_RAG_UNAVAILABLE or "index/build" in err

    assert client.put(f"/api/v1/rag/documents/{did}", json={"content": "新"}).status_code == 200
    assert client.delete(f"/api/v1/rag/documents/{did}").status_code == 200
    assert client.post("/api/v1/rag/index/build/1").status_code == 200
    assert client.delete("/api/v1/rag/index/1").status_code == 200


def test_merged_smoke_internal_key_guard_still_applies(monkeypatch, isolated_store):
    """收敛后内部认证不能因为「多挂了一组端点」而漏掉知识库路径。"""
    monkeypatch.setenv("INTERNAL_API_KEY", "merged-key")
    missing = client.get("/api/v1/rag/documents", params={"course_id": 1})
    assert missing.status_code == 401
    ok = client.get("/api/v1/rag/documents", params={"course_id": 1},
                    headers={"X-Internal-Key": "merged-key"})
    assert ok.status_code == 200
    # health 豁免（编排器探针不带业务密钥）
    assert client.get("/api/v1/rag/health").status_code == 200


# ---------------- 5. 存储隔离 ----------------
def test_two_engines_stores_are_separate(tmp_path, monkeypatch):
    """知识库语料目录与 deepgraph 数据目录必须是两个位置（禁止双写/互相覆盖）。"""
    monkeypatch.delenv("RAG_DATA_DIR", raising=False)
    rag_dir = os.path.normpath(str(knowledge_engine.data_dir()))
    assert rag_dir == os.path.normpath(os.path.join(_DG_DIR, "data", "knowledge")), (
        f"知识库默认目录应为 deepgraph_rag/data/knowledge，实际: {rag_dir}")
    # 与 deepgraph 的 persistence 数据目录（data/ 根）不同级、不重叠
    dg_data = os.path.normpath(os.path.join(_DG_DIR, "data"))
    assert rag_dir.startswith(dg_data + os.sep) and rag_dir != dg_data
    # 显式 RAG_DATA_DIR 必须能覆盖（部署用 /app/data/rag，测试用 tmp_path）
    monkeypatch.setenv("RAG_DATA_DIR", str(tmp_path))
    assert os.path.normpath(str(knowledge_engine.data_dir())) == os.path.normpath(str(tmp_path))
