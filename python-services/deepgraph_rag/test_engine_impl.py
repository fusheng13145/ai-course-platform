"""自有引擎实现测试（离线，不依赖 Ollama / Neo4j）

覆盖「第三方内容清零」重写后的实现面：

* 落盘存储的读写与清理（`utils/persistence`）
* 层次大纲解析、规则三元组、章节图谱兜底（`utils/build_graph`）
* 图检索召回（`retrieve_from_graph`）
* 图谱存储的降级行为与本地兜底（`neo4j_handler`）
* 混合检索的开关与去重/重排（`utils/retriever_pipeline`）
"""

from __future__ import annotations

import pytest

from utils import persistence
from utils.build_graph import (
    _rule_triples,
    build_chapter_knowledge_graph,
    build_hierarchical_graph,
    retrieve_from_graph,
)
from utils.retriever_pipeline import retrieve_documents

SAMPLE = """第1章 内存管理
1.1 分页机制
虚拟内存是内存管理的一种技术，它把物理内存划分成固定大小的页框。
分页机制依赖于页表，页表用于记录虚拟页到物理页框的映射。
缺页中断导致页面调度，页面置换算法用于降低缺页率。

第2章 进程调度
2.1 调度算法
时间片轮转属于抢占式调度，优先级调度依赖于进程优先级。
"""


@pytest.fixture()
def sandbox(tmp_path, monkeypatch):
    monkeypatch.setattr(persistence, "DATA_DIR", str(tmp_path))
    monkeypatch.setattr(persistence, "DOCUMENTS_DIR", str(tmp_path / "documents"))
    monkeypatch.setattr(persistence, "FAISS_DIR", str(tmp_path / "faiss_index"))
    monkeypatch.setattr(persistence, "CHAPTERS_DIR", str(tmp_path / "chapters"))
    monkeypatch.setattr(persistence, "GLOBAL_GRAPH_DIR", str(tmp_path / "global_graphs"))
    monkeypatch.setattr(persistence, "METADATA_PATH", str(tmp_path / "metadata.json"))
    persistence.ensure_dirs()
    return tmp_path


# ---------------------------------------------------------------- persistence
def test_persistence_roundtrip_and_clear(sandbox):
    name = "course1_教材.txt"
    path = persistence.save_document_text(name, "正文")
    assert path.endswith(".txt") and persistence.get_saved_document_path(name) == path
    assert persistence.load_document_text(name) == "正文"
    assert persistence.list_documents() == [name]

    persistence.save_chapters(name, {"第1章": "章节正文"})
    assert persistence.load_chapters(name) == {"第1章": "章节正文"}

    import networkx as nx

    graph = nx.Graph()
    graph.add_edge("虚拟内存", "页表", label="依赖于")
    persistence.save_global_graph(name, graph)
    loaded = persistence.load_global_graph(name)
    assert loaded is not None and loaded.has_edge("虚拟内存", "页表")

    persistence.clear_all_data(name)
    assert persistence.get_saved_document_path(name) is None
    assert persistence.load_chapters(name) == {}
    assert persistence.list_documents() == []


def test_safe_hash_name_is_stable_and_path_safe():
    assert persistence.safe_hash_name("含 空格/斜杠.txt") == persistence.safe_hash_name("含 空格/斜杠.txt")
    assert len(persistence.safe_hash_name("x")) == 16
    assert "/" not in persistence.safe_file_stem("a/b")


# ---------------------------------------------------------------- 层次大纲
def test_hierarchical_tree_has_chapters_and_sections():
    tree = build_hierarchical_graph(SAMPLE, "操作系统.pdf")
    assert tree["name"] == "操作系统"
    chapters = [c["name"] for c in tree["children"]]
    assert chapters[0].startswith("第1章"), chapters
    assert any("第2章" in c for c in chapters)
    first = tree["children"][0]
    assert first["children"], "章下必须解析出节"


def test_hierarchical_tree_falls_back_to_single_chapter():
    tree = build_hierarchical_graph("一段没有任何章节标题的正文内容。", "朴素资料")
    assert len(tree["children"]) == 1 and tree["children"][0]["type"] == "chapter"


# ---------------------------------------------------------------- 三元组
def test_rule_triples_extract_relations():
    triples = _rule_triples(SAMPLE, limit=20)
    assert triples, "规则抽取不得返回空"
    assert any("分页机制" in s or "分页机制" in t for s, _r, t in triples), triples


def test_chapter_graph_never_empty_when_llm_down(monkeypatch):
    import backend

    monkeypatch.setattr(backend, "call_llm", lambda *a, **k: (_ for _ in ()).throw(RuntimeError("llm down")))
    kg = build_chapter_knowledge_graph("内存管理", SAMPLE)
    assert kg.number_of_edges() > 0
    assert all((data or {}).get("label") for _u, _v, data in kg.edges(data=True))


# ---------------------------------------------------------------- 图检索
def test_retrieve_from_graph_matches_query_and_carries_evidence():
    kg = build_chapter_knowledge_graph("内存管理", SAMPLE)
    docs = retrieve_from_graph("分页机制依赖什么", kg, top_k=5)
    assert docs, "查询词与边端点重合时必须召回"
    assert any("分页机制" in d.page_content for d in docs)
    assert getattr(docs[0], "page_content", "")


def test_retrieve_from_graph_returns_empty_on_blank_query():
    import networkx as nx

    graph = nx.Graph()
    graph.add_edge("甲", "乙", label="包含")
    assert retrieve_from_graph("", graph) == []
    assert retrieve_from_graph("无关词", None) == []


# ---------------------------------------------------------------- Neo4j 降级
def test_neo4j_driver_raises_when_not_configured(monkeypatch):
    import config
    from neo4j_handler import Neo4jHandler

    monkeypatch.setattr(config, "NEO4J_PASSWORD", "")
    handler = Neo4jHandler()
    with pytest.raises(RuntimeError):
        _ = handler.driver


def test_learning_path_falls_back_to_local_graph(sandbox):
    import networkx as nx

    from neo4j_handler import Neo4jHandler

    name = "course2_教材.txt"
    graph = nx.Graph()
    graph.add_edge("页表", "分页机制", label="实现")
    graph.add_edge("分页机制", "虚拟内存", label="属于")
    persistence.save_global_graph(name, graph)

    text = Neo4jHandler().query_learning_path("虚拟内存", source_doc=name, max_depth=3)
    assert "学习路径" in text and "页表" in text, text


def test_graph_context_empty_when_neo4j_unavailable(monkeypatch):
    import config
    from neo4j_handler import Neo4jHandler

    monkeypatch.setattr(config, "NEO4J_PASSWORD", "")
    assert Neo4jHandler().query_graph_context(["虚拟内存"], "course1_x.txt") == ""


# ---------------------------------------------------------------- 检索流水线
class _FakeDoc:
    def __init__(self, content):
        self.page_content = content
        self.metadata = {}


class _FakeEnsemble:
    def __init__(self, docs):
        self._docs = docs
        self.calls = []

    def invoke(self, query):
        self.calls.append(query)
        return list(self._docs)


def test_retrieve_documents_uses_pipeline_and_dedupes():
    pipeline = {
        "ensemble": _FakeEnsemble([_FakeDoc("片段A"), _FakeDoc("片段A"), _FakeDoc("片段B")]),
        "knowledge_graph": None,
        "reranker": None,
    }
    flags = {"enable_hyde": False, "enable_graph_rag": True, "enable_reranking": True, "max_contexts": 3}
    docs = retrieve_documents("分页机制", "", "", "", flags=flags, pipeline=pipeline)
    assert [d.page_content for d in docs] == ["片段A", "片段B"]


def test_retrieve_documents_returns_empty_without_pipeline():
    assert retrieve_documents("问题", "", "", "", flags={}, pipeline=None) == []


def test_retrieve_documents_applies_reranker_order():
    class _Ranker:
        def predict(self, pairs):
            return [float(len(text)) for _q, text in pairs]

    pipeline = {
        "ensemble": _FakeEnsemble([_FakeDoc("短"), _FakeDoc("更长的片段内容")]),
        "knowledge_graph": None,
        "reranker": _Ranker(),
    }
    flags = {"enable_hyde": False, "enable_graph_rag": False, "enable_reranking": True, "max_contexts": 3}
    docs = retrieve_documents("问题", "", "", "", flags=flags, pipeline=pipeline)
    assert [d.page_content for d in docs] == ["更长的片段内容", "短"]
