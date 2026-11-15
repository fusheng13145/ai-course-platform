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
        body = resp.json()
        # 只断言 200 等于把「端口活着但检索永远降级」当成健康。引擎侧能力必须可见。
        assert body["status"] == "ok"
        engine = body["engine"]
        assert set(engine) == {"available", "functional", "dep_errors", "embed_model"}
        assert isinstance(engine["available"], bool)
        assert isinstance(engine["functional"], bool)
        assert isinstance(engine["dep_errors"], list)
        assert engine["embed_model"]
        # functional 蕴含 available（模型缓存检查以依赖可 import 为前提）
        assert not engine["functional"] or engine["available"]


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
    # 端到端断言补全：RAG_ENGINE_READY=1 时验证 Java HttpRagProvider 期望的 200 成功结构。
    # ⚠ mode 必须是 PLACEHOLDER 而非 ENGINE：该开关返回的是硬编码样例文本，
    # 一旦自称 ENGINE，就等于把「未接引擎」对外宣称为「引擎已应答」（DR 登记）。
    monkeypatch.setenv("RAG_ENGINE_READY", "1")
    with TestClient(app) as c:
        resp = c.post(
            "/api/v1/rag/retrieve",
            json={"query": "OSI", "course_id": 2, "top_k": 5},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 200
        assert body["mode"] == "PLACEHOLDER"
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
        assert body["mode"] == "PLACEHOLDER"
        assert body["gen_mode"] == "PLACEHOLDER"
        assert body["answer"]
        sources = body["sources"]
        assert len(sources) >= 1
        for s in sources:
            assert s["document_id"] is not None
            assert s["document_name"] is not None
            assert s["relevance_score"] is not None
            assert s["snippet"] is not None


def _fake_engine_ready(monkeypatch, gen_mode: str, answer: str):
    """把「引擎已就绪」这条分支钉成可控桩：只桩引擎函数，不桩 rag_service 的装配逻辑。"""
    import rag_service

    monkeypatch.setattr(rag_service, "_engine_ready_for", lambda cid: True)
    monkeypatch.setattr(
        rag_engine, "search",
        lambda q, cid, k: [{"document_id": 11, "document_name": "进程与线程",
                            "relevance_score": 0.87,
                            "snippet": "进程是资源分配的基本单位。"}],
    )
    monkeypatch.setattr(rag_engine, "generate_answer", lambda q, ctx: (answer, gen_mode))
    return rag_service


@pytest.mark.parametrize("gen_mode", ["LLM", "EXTRACTIVE"])
def test_ask_surfaces_generation_source_in_contract(monkeypatch, gen_mode):
    """真实 LLM 回答与抽取式兜底必须在契约上可区分（D6-2/D13-1 取证的前提）。

    原实现里 rag_service 把 generate_answer 的 mode_tag 丢弃（`answer, _gen_mode = ...`），
    两者对外都长成 mode=ENGINE/code=200——密钥 401 时平台照样报「引擎已回答」，
    「真实 LLM 链路是否真的通」无法从响应判断，只能读代码猜。
    """
    _fake_engine_ready(monkeypatch, gen_mode, "进程是资源分配的基本单位。")
    with TestClient(app) as c:
        body = c.post("/api/v1/rag/ask",
                      json={"question": "进程和线程的区别", "course_id": 8, "top_k": 3}).json()
    assert body["code"] == 200
    assert body["mode"] == "ENGINE"          # 检索层确实走了引擎
    assert body["gen_mode"] == gen_mode      # 生成层来源单独如实上报


def test_ask_gen_mode_extractive_when_llm_key_missing(monkeypatch):
    """配了索引但没配密钥：抽取式兜底不得冒充 LLM 生成。"""
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.setattr(rag_engine, "engine_available", lambda: True)
    monkeypatch.setattr(rag_engine, "index_status",
                        lambda cid: {"indexed_chunks": 1, "document_count": 1})
    monkeypatch.setattr(
        rag_engine, "search",
        lambda q, cid, k: [{"document_id": 1, "document_name": "D",
                            "relevance_score": 0.5, "snippet": "资料首句。"}],
    )
    with TestClient(app) as c:
        body = c.post("/api/v1/rag/ask",
                      json={"question": "问题", "course_id": 88, "top_k": 2}).json()
    assert body["mode"] == "ENGINE"
    assert body["gen_mode"] == "EXTRACTIVE"
    assert body["answer"], "抽取式兜底仍应有内容"


def test_ask_degrades_when_generation_unavailable(monkeypatch):
    """引擎检索层就绪但生成层如实报 UNAVAILABLE（无资料且 LLM 不可用）：不是 200 成功。

    原实现丢弃 mode_tag，这种「什么都没生成出来」的回答照样对外是 mode=ENGINE/code=200，
    Java 侧因此不会回退纯 LLM——用户看到空回答却无任何降级信号。
    """
    _fake_engine_ready(monkeypatch, "UNAVAILABLE", "")
    with TestClient(app) as c:
        body = c.post("/api/v1/rag/ask",
                      json={"question": "问题", "course_id": 8, "top_k": 3}).json()
    assert body["code"] == 503
    assert body["mode"] == "UNAVAILABLE"
    assert body["gen_mode"] == "UNAVAILABLE"
    assert body["answer"] in (None, "")



def test_index_build():
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents",
               json={"course_id": 3, "document_name": "X", "content": "x"})
        resp = c.post("/api/v1/rag/index/build/3")
        assert resp.status_code == 200
        body = resp.json()
        if rag_engine.engine_available():
            assert body["success"] is True
        else:
            # CI 轻量环境：依赖缺失时**不得**谎报成功（见 test_index_build_without_engine_is_honest）
            assert body["success"] is False
            assert body["indexed_chunks"] == 0
            assert body["error"]


def test_index_build_without_engine_is_honest(monkeypatch):
    """依赖缺失时旧实现把文档条数当 chunk 数返回 success=True，Java 侧据此对教师报「构建成功」，
    而检索永远 UNAVAILABLE。这里用桩钉住「不可用就明确失败」，与真实环境是否装了重依赖无关。"""
    monkeypatch.setattr(rag_engine, "engine_available", lambda: False)
    monkeypatch.setattr(rag_engine, "dep_errors", lambda: ["sentence-transformers: ImportError: pyarrow"])
    with TestClient(app) as c:
        c.post("/api/v1/rag/documents",
               json={"course_id": 31, "document_name": "D", "content": "内容"})
        body = c.post("/api/v1/rag/index/build/31").json()
    assert body["success"] is False
    assert body["indexed_chunks"] == 0
    assert body["code"] == 503
    assert "引擎不可用" in body["error"] and "pyarrow" in body["error"]


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
    not rag_engine.engine_functional(),
    reason="引擎重依赖未安装或嵌入模型未本地缓存（requirements-engine.txt），跳过真实检索验证",
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
        # 生成来源如实上报：有可用密钥才是 LLM，否则抽取式兜底——两者都必须能在响应里看出来
        assert body["gen_mode"] in {"LLM", "EXTRACTIVE"}, body
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


# ------------------------------------------------------------------
# 引擎"依赖可 import 但运行时失败"的中间态降级（不依赖联网下载模型，确定性）
# 复现并锁定 A 排查发现的缺陷：依赖齐备（engine_available=True）但模型缺失/
# 离线部署/版本不兼容导致 _embed 抛错时，端点必须优雅降级（200+success=False
# 或 503/UNAVAILABLE），绝不能 500。
# ------------------------------------------------------------------
def test_engine_runtime_failure_degrades_gracefully(monkeypatch):
    # 模拟"依赖可 import 但引擎运行时失败"的环境（本沙箱正是此类中间态）
    monkeypatch.setattr(rag_engine, "engine_available", lambda: True)

    def _boom():
        raise RuntimeError("embedding model failed to load (offline / missing / version mismatch)")

    def _boom_search(*args, **kwargs):
        raise RuntimeError("embedding failed at runtime (offline / missing / version mismatch)")

    # _get_model 抛错：覆盖建索引路径（build_course_index -> _embed -> _get_model）
    monkeypatch.setattr(rag_engine, "_get_model", _boom)
    # search 抛错：覆盖检索路径（_engine_ready_for 为真后即调用 search）
    monkeypatch.setattr(rag_engine, "search", _boom_search)

    with TestClient(app) as c:
        c.post("/api/v1/rag/documents",
               json={"course_id": 77, "document_name": "X", "content": "x"})

        # 1) 建索引：不应 500，应返回 success=False + 可操作 error
        built = c.post("/api/v1/rag/index/build/77")
        assert built.status_code == 200
        bb = built.json()
        assert bb["success"] is False
        assert "失败" in (bb.get("error") or "")

        # 2) 让引擎"看似已有索引"以进入检索路径，验证 retrieve/ask 亦降级而非 500
        monkeypatch.setattr(rag_engine, "index_status", lambda cid: {"indexed_chunks": 1})

        retrieved = c.post("/api/v1/rag/retrieve",
                           json={"query": "x", "course_id": 77, "top_k": 2})
        assert retrieved.status_code == 200
        rb = retrieved.json()
        assert rb["code"] == 503
        assert rb["mode"] == "UNAVAILABLE"
        assert "失败" in (rb.get("error") or "")

        asked = c.post("/api/v1/rag/ask",
                       json={"question": "x", "course_id": 77, "top_k": 2})
        assert asked.status_code == 200
        ab = asked.json()
        assert ab["code"] == 503
        assert ab["mode"] == "UNAVAILABLE"
        assert "失败" in (ab.get("error") or "")


# ---------------- 服务间内部认证（X-Internal-Key，2026-11-07 验收整改批） ----------------

def test_internal_api_key_guard(monkeypatch):
    """INTERNAL_API_KEY 非空时：业务端点缺头/错头 401，对头放行，health 豁免。"""
    monkeypatch.setenv("INTERNAL_API_KEY", "guard-key-1")
    with TestClient(app) as c:
        # 业务端点：缺头 401
        missing = c.post("/api/v1/rag/documents",
                         json={"course_id": 1, "document_name": "d", "content": "x"})
        assert missing.status_code == 401
        # 业务端点：错头 401
        wrong = c.get("/api/v1/rag/documents", params={"course_id": 1},
                      headers={"X-Internal-Key": "not-it"})
        assert wrong.status_code == 401
        # 业务端点：对头放行
        ok = c.get("/api/v1/rag/documents", params={"course_id": 1},
                   headers={"X-Internal-Key": "guard-key-1"})
        assert ok.status_code == 200
        # health 豁免（编排器探针不携带业务密钥）
        health = c.get("/api/v1/rag/health")
        assert health.status_code == 200


def test_internal_api_key_disabled_by_default(monkeypatch):
    """INTERNAL_API_KEY 为空（默认）：不校验，直连行为不变。"""
    monkeypatch.delenv("INTERNAL_API_KEY", raising=False)
    with TestClient(app) as c:
        resp = c.get("/api/v1/rag/documents", params={"course_id": 1})
        assert resp.status_code == 200
