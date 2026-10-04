"""
RAG 真实检索引擎单元测试（rag_engine.py）
========================================
覆盖 BM25+向量混合召回、RRF 融合、分块、索引生命周期、多课程隔离、
存储持久化/损坏恢复、生成降级等核心算法路径——这些在 test_rag_contract.py 里
只做到端点形状级别，本文件把引擎内部逻辑逐项钉死。

运行（本环境引擎重依赖齐备，全部实跑；缺依赖时相应用例自动 skip）：
    cd rag_service && python -m pytest -q test_rag_engine.py

设计要点：
- 纯算法用例直接喂 token 列表 / 构造向量，不触发模型下载，确定且快；
- 索引生命周期/隔离用例走真实 build+search（懒加载 sentence-transformers），
  用自定义 fixture **保留已加载模型**（不像 reset_store 那样清空 model_holder），
  避免每个用例重复加载嵌入模型；
- 缺陷1（存储非原子写/损坏恢复）在 store 层断言：读损坏文件显式抛错并隔离，
  下次载入恢复正常，绝不静默返回空。
"""
from __future__ import annotations

import json
import os

import pytest

import rag_engine


# ------------------------------------------------------------------ 依赖可用性标记
_np = rag_engine._np
requires_np = pytest.mark.skipif(_np is None, reason="numpy 未安装")
requires_bm25 = pytest.mark.skipif(
    not (rag_engine._jieba and rag_engine._BM25Okapi and _np),
    reason="BM25 依赖（jieba/rank_bm25/numpy）未安装",
)
requires_faiss = pytest.mark.skipif(
    rag_engine._faiss is None or _np is None, reason="faiss/numpy 未安装"
)
requires_engine = pytest.mark.skipif(
    not rag_engine.engine_available(),
    reason="引擎重依赖未安装（requirements-engine.txt），跳过真实索引用例",
)


def _clear_store() -> None:
    """就地清空进程内文档表/索引元数据，但**保留已加载的嵌入模型**（区别于 reset_store）。"""
    rag_engine._DOCS.clear()
    rag_engine._INDEX_META.clear()
    rag_engine._loaded = False
    rag_engine._NEXT_ID = 1


@pytest.fixture(autouse=True)
def isolated(tmp_path, monkeypatch):
    """每用例独立数据目录；清除生成密钥，保证走本地降级分支而非真实外部调用。"""
    monkeypatch.setenv("RAG_DATA_DIR", str(tmp_path))
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    _clear_store()
    yield tmp_path
    _clear_store()


# ================================================================== chunk_text 分块

def test_chunk_hard_cut_overlap_equals_param():
    """单句超长走硬切：相邻块重叠字符数应恰为 overlap（用互异汉字，避免退化匹配）。"""
    text = "".join(chr(0x4E00 + i) for i in range(120))  # 120 个不同汉字，无句读
    size, overlap = 50, 10
    chunks = rag_engine.chunk_text(text, size, overlap)
    assert len(chunks) >= 2

    def tail_head_overlap(a, b):
        m = 0
        for k in range(1, min(len(a), len(b)) + 1):
            if a[-k:] == b[:k]:
                m = k
        return m

    for i in range(len(chunks) - 1):
        assert tail_head_overlap(chunks[i], chunks[i + 1]) == overlap
    # 不吞字：原字符集合应被完整覆盖
    assert set(text) <= set("".join(chunks))


def test_chunk_size_and_overlap_clamped():
    """chunk_size 下限 50、overlap 收敛到 [0, size-10]：越界入参不得报错且行为可控。"""
    text = "".join(chr(0x4E00 + i) for i in range(200))
    # size<50 被抬到 50；overlap 负数归零
    chunks = rag_engine.chunk_text(text, 10, -5)
    assert chunks and all(len(c) <= max(50, 50) + len(text) for c in chunks)
    for c in chunks:
        assert len(c) >= 1
    # 极大 overlap 收敛到 size-10，仍不超过 size
    big = rag_engine.chunk_text(text, 60, 999)
    assert all(len(c) <= 60 for c in big)


def test_chunk_empty_and_whitespace_return_empty():
    assert rag_engine.chunk_text("", 300, 50) == []
    assert rag_engine.chunk_text("   \n\t  ", 300, 50) == []


def test_chunk_short_single_sentence_passthrough():
    """能容纳的整句原样返回，不被二次切分。"""
    text = "TCP 使用三次握手。"
    chunks = rag_engine.chunk_text(text, 300, 50)
    assert chunks == [text]


def test_chunk_chinese_not_split_into_invalid_chars():
    """中文按码点切分，每个块都是合法字符串，拼接不产生代理残片。"""
    text = "知识图谱将实体与关系显式建模，便于多跳推理与路径规划。" * 8
    for c in rag_engine.chunk_text(text, 40, 10):
        assert isinstance(c, str) and c
        c.encode("utf-8")  # 合法 UTF-8，不抛异常
        assert "\ufffd" not in c  # 无替换字符


# ================================================================== BM25 打分
@requires_bm25
def test_bm25_empty_punctuation_and_unknown_query_return_empty():
    corpus = [["机器学习", "基础"], ["深度学习", "神经网络"], ["强化学习"]]
    assert rag_engine.bm25_rank(corpus, [], pool=10) == []
    # 纯标点 / 全未知词：语料无匹配 -> 正分过滤后为空，且不抛异常
    assert rag_engine.bm25_rank(corpus, ["。！？，"], pool=10) == []
    assert rag_engine.bm25_rank(corpus, ["完全不在语料里的词"], pool=10) == []


@requires_bm25
def test_bm25_short_doc_scores_higher_than_long_doc():
    """同一词在短文档得分高于长文档（TF/长度归一化 b 的性质）。"""
    # 令 df=1、N=3，保证 IDF 为正，避免 rank_bm25 常见词负分被过滤
    short = ["机器学习", "入门"]
    long = ["机器学习"] + ["填充", "词", "其", "他", "内容"] * 12
    other = ["深度学习", "视觉"]
    corpus = [short, long, other]
    ranked = rag_engine.bm25_rank(corpus, ["机器学习"], pool=10)
    idx = {i: s for i, s in ranked}
    assert 0 in idx and 1 in idx
    assert idx[0] > idx[1]  # 短文档 > 长文档


@requires_bm25
def test_bm25_hit_count_and_monotonic_ordering():
    """命中数=含查询词的文档数；返回分数随排名单调不增。"""
    d0 = ["数据", "数据", "数据", "库"]  # 3 次
    d1 = ["数据", "结构"]               # 1 次
    d2 = ["网络", "协议"]               # 0 次
    corpus = [d0, d1, d2]
    ranked = rag_engine.bm25_rank(corpus, ["数据"], pool=10)
    hits = [i for i, _ in ranked]
    assert set(hits) == {0, 1}                       # 只命中含词的文档
    scores = [s for _, s in ranked]
    assert scores == sorted(scores, reverse=True)    # 降序
    assert ranked[0][0] == 0                          # 高 TF 排在前


# ================================================================== 向量检索
@requires_np
def test_as_unit_vectors_normalizes():
    """L2 归一化：非零行单位化，零行保持全 0，1D 输入升为 2D。"""
    assert _np is not None
    out = rag_engine._as_unit_vectors([[3.0, 4.0], [0.0, 0.0]])
    assert _np.isclose(_np.linalg.norm(out[0]), 1.0)
    assert _np.allclose(out[1], 0.0)                 # 除零保护
    single = rag_engine._as_unit_vectors([1.0, 2.0, 2.0])
    assert single.shape == (1, 3)
    assert _np.isclose(_np.linalg.norm(single[0]), 1.0)
    # 二次归一化幂等
    again = rag_engine._as_unit_vectors(out)
    assert _np.allclose(again[0], out[0], atol=1e-6)


@requires_faiss
def test_vector_rank_empty_index_returns_empty():
    index = rag_engine._faiss.IndexFlatIP(8)
    qv = _np.ones((1, 8), dtype="float32")
    assert rag_engine.vector_rank_from_index(index, qv, pool=5) == []


@requires_faiss
def test_vector_rank_dim_mismatch_raises():
    index = rag_engine._faiss.IndexFlatIP(8)
    index.add(_np.eye(4, 8, dtype="float32"))
    wrong = _np.ones((1, 4), dtype="float32")
    with pytest.raises(ValueError):
        rag_engine.vector_rank_from_index(index, wrong, pool=5)


@requires_faiss
def test_vector_rank_returns_sorted_hits_and_filters_neg1():
    """命中按相似度降序；pool 大于文档数时不会返回 -1 占位。"""
    index = rag_engine._faiss.IndexFlatIP(4)
    docs = _np.array([
        [1.0, 0.0, 0.0, 0.0],
        [0.0, 1.0, 0.0, 0.0],
        [0.6, 0.8, 0.0, 0.0],
    ], dtype="float32")
    index.add(_np.ascontiguousarray(docs))
    q = _np.array([[1.0, 0.0, 0.0, 0.0]], dtype="float32")
    ranked = rag_engine.vector_rank_from_index(index, q, pool=10)
    idx_order = [i for i, _ in ranked]
    assert idx_order[0] == 0
    assert idx_order == [0, 2, 1]                    # 相似度 1.0 > 0.6 > 0.0
    scores = [s for _, s in ranked]
    assert scores == sorted(scores, reverse=True)
    assert all(i >= 0 for i, _ in ranked)            # 无 -1


# ================================================================== RRF 融合手算
def test_rrf_fuse_matches_reciprocal_rank_formula():
    """融合分 = Σ_各路 1/(k + rank + 1)，k 取代码常量 RRF_K（=60）。"""
    k = rag_engine.RRF_K
    assert k == 60.0
    # 单路：每个命中 rank 从 0 起
    fused = dict(rag_engine.rrf_fuse([(0, 5.0), (1, 3.0)], top_k=5))
    assert fused[0] == pytest.approx(1.0 / (k + 1))
    assert fused[1] == pytest.approx(1.0 / (k + 2))


def test_rrf_fuse_both_paths_outrank_single_path():
    """两路都命中的块必须排在只被一路命中的块之前。"""
    k = rag_engine.RRF_K
    bm25 = [(2, 9.0), (0, 5.0)]   # doc2 rank0, doc0 rank1
    vec = [(0, 0.9), (1, 0.7)]    # doc0 rank0, doc1 rank1
    result = rag_engine.rrf_fuse(bm25, vec, top_k=5)
    fused = dict(result)
    # doc0 两路命中 = 1/(k+2) + 1/(k+1)
    assert fused[0] == pytest.approx(1.0 / (k + 2) + 1.0 / (k + 1))
    assert fused[2] == pytest.approx(1.0 / (k + 1))   # 仅 BM25 rank0
    assert fused[1] == pytest.approx(1.0 / (k + 2))   # 仅向量 rank1
    assert result[0][0] == 0                          # 双命中排首位


def test_rrf_fuse_top_k_truncation():
    result = rag_engine.rrf_fuse([(0, 1.0), (1, 1.0), (2, 1.0)], top_k=2)
    assert len(result) == 2
    assert [i for i, _ in result] == [0, 1]


def test_rrf_fuse_accepts_plain_indices():
    """入参支持纯 idx 列表（无需 score），便于组合两路排名。"""
    k = rag_engine.RRF_K
    fused = dict(rag_engine.rrf_fuse([0, 1], [1, 0], top_k=5))
    assert fused[0] == pytest.approx(1.0 / (k + 1) + 1.0 / (k + 2))
    assert fused[1] == pytest.approx(1.0 / (k + 1) + 1.0 / (k + 2))


# ================================================================== 存储损坏恢复（缺陷1）
def test_load_corrupt_store_raises_and_quarantines(tmp_path):
    """写入半截/损坏 JSON 后读取：显式抛 StoreCorruptedError 并隔离坏文件，不静默返回空。"""
    docs = rag_engine._docs_path()
    docs.parent.mkdir(parents=True, exist_ok=True)
    docs.write_text('{"documents": { "incomplete', encoding="utf-8")  # 截断的非法 JSON
    _clear_store()

    with pytest.raises(rag_engine.StoreCorruptedError):
        rag_engine.list_documents()

    # 原损坏文件被改名保留（可排查），且带 .corrupt 标记
    quarantined = list(docs.parent.glob("documents.json.corrupt*"))
    assert quarantined, "应保留损坏文件副本以便排查"
    assert not docs.exists()


@requires_engine
def test_search_recovers_after_corrupt_quarantine(tmp_path):
    """隔离坏文件后，下一次载入恢复正常（空存储），不会永久中毒或抛错。

    依赖标记（2026-10-04 修正）：`search()` 第一行就是 `if not engine_available(): return None`，
    引擎重依赖缺失时它在触达存储之前就返回，断言里的 StoreCorruptedError 不可能发生。
    原先没有 @requires_engine，本地（依赖齐）绿、CI（轻依赖）红——补上标记后
    CI 会显式 skip 而不是假失败。
    """
    docs = rag_engine._docs_path()
    docs.parent.mkdir(parents=True, exist_ok=True)
    docs.write_text("not-json-at-all", encoding="utf-8")
    _clear_store()

    with pytest.raises(rag_engine.StoreCorruptedError):
        rag_engine.search("q", 1, 5)

    # 第二次调用：坏文件已隔离 -> 干净起步，返回 None（无索引）而非抛错
    assert rag_engine.search("q", 1, 5) is None


def test_save_store_atomic_no_partial_file(tmp_path, monkeypatch):
    """_save_store 走临时文件 + os.replace：中途异常不得留下半截 documents.json。"""
    rag_engine.add_document(5, "D", "内容内容")
    docs = rag_engine._docs_path()
    good = docs.read_text(encoding="utf-8")

    # 模拟序列化阶段崩溃（os.replace 之前），原文件应保持完好且无 .tmp 残留
    real_dumps = json.dumps

    def boom(*a, **k):
        raise RuntimeError("模拟写盘崩溃")

    monkeypatch.setattr(json, "dumps", boom)
    with pytest.raises(RuntimeError):
        rag_engine._save_store()
    monkeypatch.setattr(json, "dumps", real_dumps)

    assert docs.read_text(encoding="utf-8") == good
    assert not list(docs.parent.glob(".*tmp"))       # 临时文件已清理


def test_round_trip_persistence(tmp_path):
    """save/load 往返一致：模拟进程重启后文档与内容完整恢复。"""
    d1 = rag_engine.add_document(21, "进程", "进程是资源分配单位")
    d2 = rag_engine.add_document(21, "线程", "线程是调度单位")

    _clear_store()  # 模拟重启：清空内存但保留磁盘
    docs = rag_engine.list_documents(21)
    assert {d["id"] for d in docs} == {d1["id"], d2["id"]}
    assert rag_engine.get_document(d1["id"])["content"] == "进程是资源分配单位"
    # next_id 连续，新文档不撞 id
    d3 = rag_engine.add_document(21, "调度", "CPU 调度")
    assert d3["id"] not in {d1["id"], d2["id"]}


# ================================================================== 索引生命周期（真实引擎）
@requires_engine
def test_search_before_build_returns_none():
    rag_engine.add_document(31, "D", "TCP 三次 握手")
    assert rag_engine.search("三次握手", 31, 5) is None


@requires_engine
def test_build_then_search_top_is_normalized_to_one():
    rag_engine.add_document(32, "三次握手",
                             "TCP 通过 三次 握手 建立 连接，客户端 发送 SYN。")
    rag_engine.add_document(32, "OSI 模型",
                            "OSI 参考 模型 包含 应用层、传输层、物理层。")
    out = rag_engine.build_course_index(32)
    assert out["success"] is True and out["indexed_chunks"] >= 2

    res = rag_engine.search("三次握手 建立 连接", 32, 5)
    assert res and len(res) <= 5
    assert res[0]["relevance_score"] == pytest.approx(1.0)  # 归一化最高分为 1.0
    for r in res:
        assert 0 < r["relevance_score"] <= 1.0


@requires_engine
def test_delete_document_invalidates_and_leaves_no_chunks(tmp_path):
    d1 = rag_engine.add_document(33, "三次握手", "TCP 三次 握手 建立 连接。")
    rag_engine.add_document(33, "OSI", "OSI 七层 模型。")
    assert rag_engine.build_course_index(33)["success"] is True

    cdir = tmp_path / "course_33"
    assert (cdir / "chunks.json").exists()

    removed = rag_engine.delete_document(d1["id"])
    assert removed is not None
    # 缺陷回归：删除文档后索引过期，chunks.json 不应残留（此前只删 index.*）
    assert not (cdir / "chunks.json").exists()
    assert not list(cdir.glob("index.*"))
    assert rag_engine.index_status(33) is None
    assert rag_engine.search("三次握手", 33, 5) is None


@requires_engine
def test_multi_course_isolation():
    """course A 建索引后，未建索引的 course B 检索不得返回 A 的片段。"""
    a = rag_engine.add_document(41, "A-进程", "进程 是 资源 分配 单位。")
    assert rag_engine.build_course_index(41)["success"] is True
    # B 无索引 -> None，绝不串到 A
    assert rag_engine.search("进程", 42, 5) is None
    # B 建自己的索引后，结果只含 B 的文档 id
    b = rag_engine.add_document(42, "B-英语", "现在 进行 时 表示 正在 发生 的 动作。")
    assert rag_engine.build_course_index(42)["success"] is True
    res_b = rag_engine.search("现在 进行 时", 42, 5)
    assert res_b
    assert all(r["document_id"] == b["id"] for r in res_b)
    assert a["id"] not in {r["document_id"] for r in res_b}


@requires_engine
def test_search_top_k_boundary_clamped():
    for _ in range(3):
        rag_engine.add_document(51, "D", "数据 结构 与 算法 基础")
    assert rag_engine.build_course_index(51)["success"] is True
    # top_k=0 -> 兜底 5；负数 -> 夹到 1；超大 -> 夹到 50；均不抛异常
    assert len(rag_engine.search("数据", 51, 0)) >= 1
    one = rag_engine.search("数据", 51, -1)
    assert one is not None and len(one) <= 1
    big = rag_engine.search("数据", 51, 1000)
    assert big is not None and len(big) <= 50


# ================================================================== generate_answer 降级
def test_generate_answer_extractive_when_contexts_present():
    contexts = [
        {"document_name": "进程", "snippet": "进程是资源分配的基本单位。"},
        {"document_name": "线程", "snippet": "线程是CPU调度的基本单位。"},
    ]
    answer, mode = rag_engine.generate_answer("进程和线程的区别？", contexts)
    assert mode == "EXTRACTIVE"
    assert answer and "进程" in answer


def test_generate_answer_unavailable_is_recognizable_failure():
    """LLM 客户端不可用且无检索资料：返回可识别失败（空 answer + UNAVAILABLE），非伪成功串。"""
    answer, mode = rag_engine.generate_answer("无关问题", [])
    assert mode == "UNAVAILABLE"
    assert answer == ""


def test_generate_answer_llm_success_via_stub(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "sk-test-not-real")
    monkeypatch.setattr(
        rag_engine, "_call_deepseek",
        lambda key, q, ctx: "这是由 LLM 生成的回答。",
    )
    answer, mode = rag_engine.generate_answer("问题", [{"document_name": "D", "snippet": "内容"}])
    assert mode == "LLM"
    assert answer == "这是由 LLM 生成的回答。"


def test_generate_answer_llm_error_falls_back(monkeypatch):
    """LLM 调用抛错时降级：有资料 -> EXTRACTIVE，无资料 -> UNAVAILABLE（绝不冒充 LLM）。"""
    monkeypatch.setenv("DEEPSEEK_API_KEY", "sk-test-not-real")

    def raise_err(*a, **k):
        raise RuntimeError("网络不可达")

    monkeypatch.setattr(rag_engine, "_call_deepseek", raise_err)
    with_ctx = rag_engine.generate_answer("q", [{"document_name": "D", "snippet": "S"}])
    assert with_ctx[1] == "EXTRACTIVE"
    without_ctx = rag_engine.generate_answer("q", [])
    assert without_ctx[1] == "UNAVAILABLE"
    assert without_ctx[0] == ""
