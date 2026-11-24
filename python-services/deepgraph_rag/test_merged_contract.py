# -*- coding: utf-8 -*-
"""
Q-03 双引擎收敛护栏（8501 承载 deepgraph + 原 8002 知识库）
==========================================================

用户裁定：接受 8501 单点，但**功能只能升级不能降级**。本文件就是这条裁定的机器证据：

1. `test_engine_code_is_verbatim_copy`——搬迁后的 `knowledge_engine.py` 与原
   `rag_service/rag_engine.py` 除「模块说明」与 `data_dir()` 两处外**逐字相同**，
   从根上排除「搬迁时顺手改算法」的可能（能力降级最常见就发生在这里）。
2. `test_all_8002_routes_exist_on_merged_app`——8002 的每个 `/api/v1/rag/*`
   路径与方法都出现在合并 app 上（结构性集合比对，不靠人工清单）。
3. `test_deepgraph_routes_unchanged`——原 8501 的 9 个 deepgraph 端点一个不少
   （收敛不能反向把 deepgraph 端点挤掉）。
4. `test_merged_smoke_*`——合并 app 上知识库端点真实可用（CRUD/降级语义）。
5. `test_two_engines_stores_are_separate`——两个引擎的语料目录互不覆盖
   （否则收口当天会出现「8002 文档污染 deepgraph 元数据」这类隐性降级）。

完整「不降级」验证命令（**同一进程**同时跑两套契约）：
    set RAG_CONTRACT_APP=merged
    python -m pytest -q python-services/rag_service/test_rag_contract.py \
                       python-services/deepgraph_rag/test_deepgraph_contract.py \
                       python-services/deepgraph_rag/test_merged_contract.py
"""
import importlib.util
import os
import re
import sys

import pytest
from fastapi.testclient import TestClient

_DG_DIR = os.path.dirname(os.path.abspath(__file__))
_RAG_DIR = os.path.join(_DG_DIR, "..", "rag_service")
sys.path.insert(0, _DG_DIR)

import api_server  # noqa: E402
import knowledge_api  # noqa: E402
import knowledge_engine  # noqa: E402

client = TestClient(api_server.app)


# ---------------- 1. 引擎搬迁逐行等价 ----------------
def _normalize_engine_source(path: str) -> str:
    """抹掉两个「允许差异区」：模块 docstring、data_dir() 函数体。"""
    with open(path, "r", encoding="utf-8", newline="\n") as f:
        text = f.read()
    start = text.index('"""')
    end = text.index('"""', start + 3) + 3
    text = text[:start] + '"""<module-docstring>"""' + text[end:]
    text = re.sub(
        r"def data_dir\(\) -> Path:.*?\n\n\n",
        "def data_dir() -> Path:\n    <data-dir-body>\n\n\n",
        text,
        count=1,
        flags=re.S,
    )
    return text


def test_engine_code_is_verbatim_copy():
    legacy = os.path.normpath(os.path.join(_RAG_DIR, "rag_engine.py"))
    assert os.path.exists(legacy), "原 8002 引擎文件不存在，无法做等价校验"
    want = _normalize_engine_source(legacy)
    got = _normalize_engine_source(os.path.join(_DG_DIR, "knowledge_engine.py"))

    # 相等即「除允许差异区外逐字一致」；不等时打印首个差异位置，便于定位
    if want != got:
        for i, (a, b) in enumerate(zip(want, got)):
            if a != b:
                ctx = max(0, i - 120)
                want_ctx = want[ctx:i + 80].replace("\n", "\\n")
                got_ctx = got[ctx:i + 80].replace("\n", "\\n")
                pytest.fail(f"搬迁改动了引擎逻辑（首个差异 @ {i}）\n原: ...{want_ctx}...\n新: ...{got_ctx}...")
    assert len(want) == len(got)


def test_knowledge_engine_keeps_engine_identity():
    """搬迁后引擎版本号必须原样保留（1.1 含 HyDE + Cross-Encoder 重排），不得回退。"""
    assert knowledge_engine.ENGINE_VERSION == "1.1"
    # 能力探针齐全：健康检查与降级分支都依赖这些函数存在
    for name in ("engine_available", "engine_functional", "dep_errors", "embed_model_name",
                 "hyde_status", "rerank_status", "llm_health", "search", "graph_sources",
                 "generate_answer", "build_course_index", "clear_course_index",
                 "index_status", "StoreCorruptedError"):
        assert hasattr(knowledge_engine, name), f"引擎搬迁丢失能力: {name}"


# ---------------- 2/3. 端点集合：8002 全在，deepgraph 不少 ----------------
def _load_legacy_rag_app():
    """按文件路径加载 8002 的 app（绕过 sys.modules，避免与合并实现混淆）。"""
    rag_dir = os.path.normpath(_RAG_DIR)
    spec = importlib.util.spec_from_file_location(
        "legacy_rag_service_for_routes", os.path.join(rag_dir, "rag_service.py"))
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, rag_dir)
    try:
        spec.loader.exec_module(module)
    finally:
        sys.path.remove(rag_dir)
    return module.app


def _api_signatures(app):
    """(path, frozenset(methods)) 集合，只取 /api/ 前缀的业务端点。"""
    out = set()
    for route in app.routes:
        path = getattr(route, "path", None)
        methods = getattr(route, "methods", None)
        if not path or not methods or not path.startswith("/api/"):
            continue
        out.add((path, frozenset(methods)))
    return out


def test_all_8002_routes_exist_on_merged_app():
    legacy = _api_signatures(_load_legacy_rag_app())
    assert len(legacy) >= 9, f"8002 端点数量异常: {sorted(legacy)}"
    merged = _api_signatures(api_server.app)
    missing = sorted(legacy - merged)
    assert not missing, f"合并服务缺少 8002 端点（能力降级）: {missing}"

    # 反向确认：9 个业务端点（health 之外）逐个点名，避免集合比对被「同路径不同方法」蒙混
    expected_paths = {
        "/api/v1/rag/health",
        "/api/v1/rag/retrieve",
        "/api/v1/rag/ask",
        "/api/v1/rag/documents",
        "/api/v1/rag/documents/{document_id}",
        "/api/v1/rag/index/build/{course_id}",
        "/api/v1/rag/index/{course_id}",
    }
    merged_paths = {p for p, _ in merged}
    assert expected_paths <= merged_paths, f"缺少: {sorted(expected_paths - merged_paths)}"


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
