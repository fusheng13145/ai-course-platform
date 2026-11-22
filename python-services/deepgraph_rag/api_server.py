"""
DeepGraph RAG 文档图谱服务（8501）· FastAPI 契约适配器
======================================================

本文件是 ai-course-platform 集成层的新增适配器，**不属于已验收的复制内容**：
`app.py` / `backend.py` / `config.py` / `neo4j_handler.py` / `utils/*` 与
`requirements.txt` 等自 DeepSeek-RAG-Chatbot-main（已通过验收）原样复制，
一字未改；本适配器只做"协议翻译"，把已验收的 Streamlit 应用以
FastAPI 契约服务形式接入平台（对齐 8001/8002/8003 的既有契约风格）。

为什么可以直接 headless 调用原实现（2026-11-21 实测）：
- Streamlit 1.56 在无 `streamlit run` 环境下 `st.session_state` 读写可用、
  `st.write/st.error/...` 仅打印告警不抛错；
- 因此本适配器直接调用真实 `process_documents` / `retrieve_documents` /
  `generate_llm_response` / `build_chapter_knowledge_graph` / `neo4j_db`，
  不重写、不复制任何算法逻辑。

契约端点（对齐 Java 端 `com.aicourse.integration` 风格；降级语义与 8002 一致：
HTTP 503 + body{code:503, mode:"UNAVAILABLE", error}，绝不自称 ENGINE）：
  GET    /api/v1/deepgraph/health
  POST   /api/v1/deepgraph/ingest        {course_id, document_name, content, build_*?}
  GET    /api/v1/deepgraph/documents?course_id=
  DELETE /api/v1/deepgraph/documents/{document_name}
  POST   /api/v1/deepgraph/retrieve      {query, course_id?, top_k, 开关?}
  POST   /api/v1/deepgraph/qa            {question, course_id?, chat_history?, answer_mode?}
  POST   /api/v1/deepgraph/chapter-graph {course_id, document_name, chapter_name?, chapter_text?}
  POST   /api/v1/deepgraph/learning-path {target, course_id?, max_depth?, search_all_sources?}

运行依赖：Ollama（nomic-embed-text 嵌入 + HyDE/实体抽取辅助）、Neo4j（图谱，
缺失时相关增强自动跳过）、DeepSeek API 或 Ollama 生成模型（LLM_PROVIDER 切换）。
"""
from __future__ import annotations

import os
import re
import json
import threading
import traceback
from typing import Dict, List, Optional

import requests

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

import streamlit as st

# ---- 已验收复制内容（原样调用，禁止修改） ----
try:
    import backend as chatbot_backend
    import config as chatbot_config
    from neo4j_handler import neo4j_db
    from utils import persistence
    from utils.build_graph import retrieve_from_graph
    from utils.doc_handler import process_documents
    from utils.retriever_pipeline import retrieve_documents
    from utils.llm_client import get_provider, get_provider_name

    HEAVY_IMPORT_ERRORS: List[str] = []
except Exception as _import_exc:  # 重依赖缺失（CI/瘦环境）：契约端点按降级语义应答
    HEAVY_IMPORT_ERRORS = [f"{type(_import_exc).__name__}: {_import_exc}"]

app = FastAPI(title="AI Course DeepGraph RAG Service", version="1.0.0")


# ---------------- 数据目录 ASCII 重定位（适配器侧运行期配置，不改复制文件） ----------------
def _relocate_data_dir_if_non_ascii() -> None:
    """FAISS 的 C++ 文件 API 在含中文的绝对路径上必然读写失败（实测：本仓库位于
    “大创赛”目录；平台 rag_engine 同样因此改用内存序列化写字节）。
    已验收复制内容的 persistence.py 以模块常量计算数据目录——这里仅在**非 ASCII
    安装路径**下把这些模块常量重定位到用户目录下的 ASCII 路径（等价于把服务
    安装到纯 ASCII 目录，属部署配置而非实现修改）；ASCII 路径下保持原样。
    """
    try:
        persistence.ensure_dirs()
        base = persistence.DATA_DIR
        try:
            base.encode("ascii")
            return
        except UnicodeEncodeError:
            pass
        new_base = os.path.join(os.path.expanduser("~"), ".aicourse-platform", "deepgraph_rag")
        persistence.DATA_DIR = new_base
        persistence.DOCUMENTS_DIR = os.path.join(new_base, "documents")
        persistence.FAISS_DIR = os.path.join(new_base, "faiss_index")
        persistence.CHAPTERS_DIR = os.path.join(new_base, "chapters")
        persistence.GLOBAL_GRAPH_DIR = os.path.join(new_base, "global_graphs")
        persistence.METADATA_PATH = os.path.join(new_base, "metadata.json")
        persistence.ensure_dirs()
        print(f"[deepgraph] 检测到非 ASCII 安装路径，数据目录重定位: {new_base}")
    except Exception as exc:  # pragma: no cover - 重定位失败时按原路径继续（FAISS 将如实降级）
        print(f"[deepgraph] 数据目录重定位失败: {exc}")


_relocate_data_dir_if_non_ascii()


# ---------------- 大文档嵌入批处理垫片（适配器侧运行期兼容，复制代码不动） ----------------
# 现象：langchain_ollama 的 OllamaEmbeddings.embed_documents 把**全部**分块塞进一次
# /api/embed 请求（整本教材≈1300 块），Ollama 0.40 的内嵌 runner 在 tokenize 巨批时
# 直接崩溃（ResponseError: Post http://127.0.0.1:<runner>/tokenize ... refused），
# 教材级文档因此无法入库（小块语料 140 块可过）。
# 处置：运行期包装 embed_documents 按 DG_EMBED_BATCH（默认 32）分批调用原实现——
# 复制代码 doc_handler 的调用方式不变，仅传输层分批。
_EMBED_BATCH = max(1, int(os.getenv("DG_EMBED_BATCH", "32") or "32"))


def _install_embed_batching_shim() -> None:
    try:
        from langchain_ollama import OllamaEmbeddings

        if getattr(OllamaEmbeddings, "_dg_batch_patched", False):
            return
        original = OllamaEmbeddings.embed_documents

        def batched(self, texts):  # noqa: ANN001 - 与被包装方法同签名
            texts = list(texts)
            if len(texts) <= _EMBED_BATCH:
                return original(self, texts)
            out = []
            for i in range(0, len(texts), _EMBED_BATCH):
                out.extend(original(self, texts[i:i + _EMBED_BATCH]))
            return out

        OllamaEmbeddings.embed_documents = batched
        OllamaEmbeddings._dg_batch_patched = True
        print(f"[deepgraph] 嵌入批处理垫片已启用（每批 {_EMBED_BATCH} 块）")
    except Exception as exc:  # pragma: no cover - langchain 未装时垫片无意义
        print(f"[deepgraph] 嵌入垫片安装失败（不影响其余功能）: {exc}")


_install_embed_batching_shim()


# ---------------- 课程知识图谱桥接（deepgraph 抽取结果 → kg_service 8001） ----------------
# 为什么要桥接：平台「知识图谱」页读的是 kg_service（:Kp 节点，course_id 隔离），
# 而本引擎的三元组此前只落在本服务自有的 Neo4j :Entity 子图里——平台侧看不见。
# 本桥把已验收复制代码真实抽取出的实体/关系（build_chapter_knowledge_graph）
# 与层次大纲（build_hierarchical_graph）以**增量**方式并入课程图谱：
# 实体按名去重（kg_service create_node 不查重，须调用方自行幂等），
# 关系依赖 kg_service Cypher MERGE 的三元组幂等。任何失败只降级本桥，不影响主链。

_KG_BASE_URL = (os.getenv("KG_BASE_URL") or "http://localhost:8001").rstrip("/")


def _kg_sync_enabled() -> bool:
    """调用时读取（可测试性）：DG_KG_SYNC=0 可整体关闭桥接。"""
    return os.getenv("DG_KG_SYNC", "1") != "0"


def _kg_course_state(course_id: int) -> tuple:
    """读取课程图谱现状：返回 (name->id 映射, 已存在三元组集合)。失败返回 ({}, set())。"""
    try:
        resp = requests.get(f"{_KG_BASE_URL}/api/v1/graph/course/{course_id}", timeout=10)
        resp.raise_for_status()
        body = resp.json()
        name2id = {n["name"]: n["id"] for n in body.get("nodes", []) if n.get("name")}
        existing = {
            (e["source_id"], e["target_id"], e["relation_type"])
            for e in body.get("edges", [])
            if e.get("source_id") is not None and e.get("target_id") is not None
        }
        return name2id, existing
    except Exception as exc:
        print(f"[deepgraph] 读取 kg_service 课程图谱失败（桥接跳过）: {exc}")
        return {}, set()


def _kg_ensure_node(course_id: int, name: str, name2id: dict, content: str = "",
                    type_: str = "CONCEPT") -> Optional[int]:
    """按名幂等创建节点，返回节点 id；创建失败返回 None。"""
    if name in name2id:
        return name2id[name]
    try:
        resp = requests.post(
            f"{_KG_BASE_URL}/api/v1/graph/nodes",
            json={"course_id": course_id, "name": name[:120],
                  "content": (content or "")[:500], "type": type_},
            timeout=10,
        )
        resp.raise_for_status()
        node_id = resp.json().get("id")
        if node_id is not None:
            name2id[name] = node_id
        return node_id
    except Exception as exc:
        print(f"[deepgraph] kg_service 建节点失败（name={name[:40]}）: {exc}")
        return None


def _kg_add_relation(course_id: int, source_id: int, target_id: int,
                     relation_type: str, existing: set) -> bool:
    """追加关系（kg_service 按 (源,目标,类型) MERGE 幂等；此处再做一层本地判重）。"""
    triple = (source_id, target_id, relation_type)
    if triple in existing:
        return False
    try:
        resp = requests.post(
            f"{_KG_BASE_URL}/api/v1/graph/relations",
            json={"source_id": source_id, "target_id": target_id,
                  "relation_type": relation_type[:60]},
            timeout=10,
        )
        resp.raise_for_status()
        existing.add(triple)
        return True
    except Exception as exc:
        print(f"[deepgraph] kg_service 建关系失败（{source_id}->{target_id}:{relation_type}）: {exc}")
        return False


def _edge_evidence(data: dict) -> str:
    """取关系溯源文本的首段（复制代码 source_chunks 溯源结构），无则空。"""
    chunks = (data or {}).get("source_chunks") or []
    for item in chunks:
        text = (item or {}).get("text") or ""
        if text.strip():
            return text.strip()
    return ""


def sync_chapter_graph_to_course_kg(course_id: int, kg, source_doc: str) -> dict:
    """把章节/全局图谱的实体关系并入课程知识图谱（kg_service 8001）。

    kg 为 NetworkX 图（复制代码 build_chapter_knowledge_graph 产物，边带 label
    与 source_chunks 溯源）。返回 {added_nodes, added_relations, skipped} 供契约断言。
    """
    if not _kg_sync_enabled() or course_id is None or kg is None or kg.number_of_edges() == 0:
        return {"added_nodes": 0, "added_relations": 0, "skipped": "disabled_or_empty"}
    name2id, existing = _kg_course_state(course_id)
    if not name2id and not existing:
        # 课程图谱读取失败时不再盲目建点，避免半同步
        return {"added_nodes": 0, "added_relations": 0, "skipped": "kg_unreachable"}
    known_before = set(name2id.keys())
    added_relations = 0
    for u, v, data in kg.edges(data=True):
        relation = (data or {}).get("label") or "相关"
        evidence = _edge_evidence(data)
        sid = _kg_ensure_node(course_id, str(u), name2id, content=evidence)
        tid = _kg_ensure_node(course_id, str(v), name2id, content=evidence)
        if sid is None or tid is None or sid == tid:
            continue
        if _kg_add_relation(course_id, sid, tid, str(relation), existing):
            added_relations += 1
    added_nodes = len(set(name2id.keys()) - known_before)
    return {"added_nodes": added_nodes, "added_relations": added_relations,
            "source_doc": source_doc}


def sync_hierarchical_tree_to_course_kg(course_id: int, tree: dict, source_doc: str) -> dict:
    """把层次大纲树（章→节，复制代码 build_hierarchical_graph 产物）并入课程图谱。

    树边统一以 relation_type=「包含章节」落库，节点 type=CHAPTER，供平台知识图谱
    页呈现教材的章级骨架（与已验收 UI 的「层次知识图谱」同一数据来源语义）。
    """
    if not _kg_sync_enabled() or course_id is None or not tree:
        return {"added_nodes": 0, "added_relations": 0, "skipped": "disabled_or_empty"}
    name2id, existing = _kg_course_state(course_id)
    if not name2id and not existing:
        return {"added_nodes": 0, "added_relations": 0, "skipped": "kg_unreachable"}

    known_before = set(name2id.keys())
    added_relations = 0

    def walk(node: dict, parent_id: Optional[int]) -> None:
        nonlocal added_relations
        name = (node or {}).get("name")
        if not name:
            return
        node_id = _kg_ensure_node(course_id, str(name), name2id, type_="CHAPTER")
        if node_id is None:
            return
        if parent_id is not None and parent_id != node_id:
            if _kg_add_relation(course_id, node_id, parent_id, "包含章节", existing):
                added_relations += 1
        for child in (node or {}).get("children") or []:
            walk(child, node_id)

    walk(tree, None)
    return {"added_nodes": len(set(name2id.keys()) - known_before),
            "added_relations": added_relations, "source_doc": source_doc}

# ---------------- 服务间内部认证（与 rag_service 同款，2026-11-07 验收整改批约定） ----------------
INTERNAL_KEY_HEADER = "X-Internal-Key"


@app.middleware("http")
async def _internal_auth_guard(request: Request, call_next):
    expected = (os.getenv("INTERNAL_API_KEY") or "").strip()
    if expected and not request.url.path.endswith("/health"):
        if request.headers.get(INTERNAL_KEY_HEADER) != expected:
            return JSONResponse(
                status_code=401,
                content={"code": 401, "message": "缺少或错误的 X-Internal-Key（服务间内部认证）"},
            )
    return await call_next(request)


# ---------------- 引擎状态 ----------------
# 课程 -> 检索管道（真实组件组装：EnsembleRetriever(BM25+FAISS) + NetworkX 图 + CrossEncoder）
_PIPELINES: Dict[int, dict] = {}
_PIPELINE_LOCK = threading.Lock()
_RERANKER = None
_RERANKER_LOCK = threading.Lock()

# 与 8002 一致的降级文案（Java 端读取 body.error 透出到前端）
MSG_DEEPGRAPH_UNAVAILABLE = "文档图谱 RAG 引擎（deepgraph_rag）未接入或不可用"


def engine_available() -> bool:
    return not HEAVY_IMPORT_ERRORS


def engine_dep_errors() -> List[str]:
    return list(HEAVY_IMPORT_ERRORS)


def _doc_prefix(course_id: int) -> str:
    return f"course{course_id}_"


def _sanitize_doc_name(name: str) -> str:
    cleaned = re.sub(r'[\\/:*?"<>|\r\n]+', "_", (name or "").strip())
    return cleaned[:180] if cleaned else "document"


def _course_doc_name(course_id: int, document_name: str) -> str:
    base = _sanitize_doc_name(document_name)
    if base.endswith(".txt") or base.endswith(".pdf") or base.endswith(".docx"):
        stem = base
    else:
        stem = f"{base}.txt"
    return f"{_doc_prefix(course_id)}{stem}"


def _reranker():
    """惰性加载 CrossEncoder（与 app.py load_cross_encoder_with_retry 同款镜像回退）。

    加载失败返回 None——retriever_pipeline 对 reranker=None 自动跳过重排（原实现内的宽恕路径）。
    可用 DG_CROSS_ENCODER_MODEL 覆盖默认模型（config.py 已预留该环境变量）。
    """
    global _RERANKER
    if _RERANKER is not None:
        return _RERANKER
    with _RERANKER_LOCK:
        if _RERANKER is not None:
            return _RERANKER
        try:
            from sentence_transformers import CrossEncoder

            _RERANKER = CrossEncoder(chatbot_config.CROSS_ENCODER_MODEL)
            # 适配器侧把重排器接进管道 dict：retrieve_documents 读取
            # retrieval_pipeline["reranker"]（原实现预留的接线点），
            # 使「启用 Neural Reranking」开关真正生效；复制内容本身未改动。
        except Exception as exc:
            print(f"[deepgraph] CrossEncoder 加载失败（重排将自动跳过）: {exc}")
            _RERANKER = None
    return _RERANKER


def _pipeline_for(course_id: Optional[int]) -> Optional[dict]:
    if course_id is None:
        return None
    with _PIPELINE_LOCK:
        pipeline = _PIPELINES.get(course_id)
        if pipeline is None:
            pipeline = _rebuild_pipeline_from_disk(course_id)
            if pipeline is not None:
                _PIPELINES[course_id] = pipeline
        return pipeline


def _course_doc_names(course_id: int) -> List[str]:
    try:
        metadata = persistence.load_metadata()
        names = [n for n in metadata.get("doc_names", []) if n.startswith(_doc_prefix(course_id))]
        # 只保留磁盘上真实存在的文档（与 app.py auto_load_existing_data 同判据）
        return [n for n in names if persistence.get_saved_document_path(n)]
    except Exception:
        return []


def _chunks_from_faiss(faiss_db) -> list:
    """从已验收项目持久化的 FAISS 索引中取回原文块（真实 docstore，仅适配器侧读回）。"""
    try:
        docs = []
        for internal_id in list(faiss_db.index_to_docstore_id.values()):
            doc = faiss_db.docstore.search(internal_id)
            if doc is not None and getattr(doc, "page_content", ""):
                docs.append(doc)
        return docs
    except Exception as exc:
        print(f"[deepgraph] FAISS docstore 读回失败: {exc}")
        return []


def _rebuild_pipeline_from_disk(course_id: int) -> Optional[dict]:
    """服务重启后按持久化数据重建课程管道（等价 app.py load_document_data 的多文档聚合版）。"""
    if not engine_available():
        return None
    doc_names = _course_doc_names(course_id)
    if not doc_names:
        return None
    try:
        from langchain_ollama import OllamaEmbeddings
        from langchain_community.retrievers import BM25Retriever
        from langchain_community.vectorstores import FAISS
        from langchain_classic.retrievers import EnsembleRetriever
        import networkx as nx

        embeddings = OllamaEmbeddings(
            model=chatbot_config.EMBEDDINGS_MODEL, base_url=chatbot_config.OLLAMA_BASE_URL
        )
        merged_faiss: Optional[FAISS] = None
        chunk_docs: list = []
        combined_kg = nx.Graph()
        for name in doc_names:
            faiss_db = persistence.load_faiss_index(name, embeddings)
            if faiss_db is not None:
                chunk_docs.extend(_chunks_from_faiss(faiss_db))
                if merged_faiss is None:
                    merged_faiss = faiss_db
                else:
                    merged_faiss.merge_from(faiss_db)
            kg = persistence.load_global_graph(name)
            if kg is not None and kg.number_of_edges() > 0:
                combined_kg = nx.compose(combined_kg, kg)
        if merged_faiss is None or not chunk_docs:
            return None
        bm25 = BM25Retriever.from_documents(chunk_docs)
        bm25.k = 5
        ensemble = EnsembleRetriever(
            retrievers=[bm25, merged_faiss.as_retriever(search_kwargs={"k": 5})],
            weights=[0.4, 0.6],
        )
        pipeline = {
            "ensemble": ensemble,
            "knowledge_graph": combined_kg if combined_kg.number_of_edges() > 0 else None,
            "reranker": _reranker(),
            "doc_names": doc_names,
            "chunks": chunk_docs,
        }
        print(
            f"[deepgraph] 课程 {course_id} 管道重建完成: {len(doc_names)} 文档 / "
            f"{len(chunk_docs)} 块 / 图 {combined_kg.number_of_edges()} 边"
        )
        return pipeline
    except Exception as exc:
        print(f"[deepgraph] 课程 {course_id} 管道重建失败: {exc}")
        traceback.print_exc()
        return None


class _UploadedShim:
    """模拟 Streamlit UploadedFile 的最小接口（.name / .getvalue()）。

    仅适配器侧输入适配，使真实 load_single_file / process_documents 原样运行；
    不修改复制内容。
    """

    def __init__(self, name: str, data: bytes):
        self.name = name
        self._data = data

    def getvalue(self) -> bytes:
        return self._data


def _sync_session_state(course_id: Optional[int], pipeline: Optional[dict]) -> None:
    """把适配器维护的管道/开关同步进原实现读取的 session_state（headless 可用，已实测）。"""
    try:
        if pipeline is not None:
            st.session_state["retrieval_pipeline"] = pipeline
            st.session_state["documents_loaded"] = True
            doc_names = pipeline.get("doc_names") or []
            if doc_names:
                st.session_state["doc_names"] = doc_names
                st.session_state.setdefault("current_source", doc_names[-1])
        # 开关默认值与已验收 app.py 的侧边栏初始值一致（HyDE 默认关、GraphRAG/重排默认开）
        st.session_state.setdefault("enable_hyde", os.getenv("DG_ENABLE_HYDE", "0") == "1")
        st.session_state.setdefault("enable_graph_rag", True)
        st.session_state.setdefault("enable_reranking", True)
        st.session_state.setdefault("max_contexts", 3)
        st.session_state.setdefault("answer_mode", "向量检索 + 知识图谱")
    except Exception as exc:
        print(f"[deepgraph] session_state 同步告警（不影响检索）: {exc}")


def _apply_flags(req_flags: dict) -> None:
    mapping = {
        "enable_hyde": "enable_hyde",
        "enable_graph_rag": "enable_graph_rag",
        "enable_reranking": "enable_reranking",
    }
    for req_key, state_key in mapping.items():
        value = req_flags.get(req_key)
        if value is not None:
            st.session_state[state_key] = bool(value)
    if req_flags.get("max_contexts") is not None:
        st.session_state["max_contexts"] = int(req_flags["max_contexts"])


def _degrade(error: str, status: int = 503):
    return JSONResponse(
        status_code=status,
        content={"mode": "UNAVAILABLE", "error": error, "code": 503, "results": [], "sources": [], "answer": None},
    )


# ---------------- 请求/响应模型 ----------------
class IngestRequest(BaseModel):
    course_id: int = Field(ge=1)
    document_name: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1)
    build_hierarchical: bool = False
    build_global: bool = False
    build_chapter: bool = True


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    course_id: Optional[int] = None
    top_k: int = Field(3, ge=1, le=10)
    enable_hyde: Optional[bool] = None
    enable_graph_rag: Optional[bool] = None
    enable_reranking: Optional[bool] = None


class QaRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    course_id: Optional[int] = None
    document_name: Optional[str] = None
    chat_history: List[dict] = []
    answer_mode: str = Field("向量检索 + 知识图谱", max_length=32)
    enable_hyde: Optional[bool] = None
    enable_graph_rag: Optional[bool] = None
    enable_reranking: Optional[bool] = None


class ChapterGraphRequest(BaseModel):
    course_id: int = Field(ge=1)
    document_name: str = Field(min_length=1, max_length=255)
    chapter_name: Optional[str] = None
    chapter_text: Optional[str] = None


class LearningPathRequest(BaseModel):
    target: str = Field(min_length=1, max_length=120)
    course_id: Optional[int] = None
    max_depth: int = Field(3, ge=1, le=4)
    search_all_sources: bool = True


ANSWER_MODES = {"向量检索 + 知识图谱", "仅知识图谱", "仅向量检索", "仅大模型自身知识"}


# ---------------- 健康检查 ----------------
@app.get("/api/v1/deepgraph/health")
def health():
    provider = ""
    model = ""
    try:
        provider = get_provider() if engine_available() else ""
        model = chatbot_config.MODEL if engine_available() else ""
    except Exception:
        pass
    neo4j_ok = False
    try:
        with neo4j_db.driver.session() as session:
            session.run("RETURN 1")
        neo4j_ok = True
    except Exception:
        neo4j_ok = False
    return {
        "status": "ok",
        "engine": {
            "available": engine_available(),
            "dep_errors": engine_dep_errors(),
            "provider": provider,
            "provider_detail": (get_provider_name() if engine_available() else ""),
            "model": model,
            "embeddings_model": (chatbot_config.EMBEDDINGS_MODEL if engine_available() else ""),
            "cross_encoder": (chatbot_config.CROSS_ENCODER_MODEL if engine_available() else ""),
            "reranker_loaded": _RERANKER is not None,
        },
        "neo4j": {"reachable": neo4j_ok, "uri": chatbot_config.NEO4J_URI},
        "ollama": {"base_url": chatbot_config.OLLAMA_BASE_URL},
        "courses_indexed": sorted(_PIPELINES.keys()),
    }


# ---------------- 文档接入（真实 process_documents 全链路） ----------------
@app.post("/api/v1/deepgraph/ingest")
def ingest(req: IngestRequest):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}），未处理文档")
    doc_name = _course_doc_name(req.course_id, req.document_name)
    if not req.content.strip():
        return _degrade("文档内容为空，未处理")
    try:
        shim = _UploadedShim(doc_name, req.content.encode("utf-8"))
        reranker = _reranker()
        pipeline, chapters = process_documents(
            [shim],
            reranker,
            chatbot_config.EMBEDDINGS_MODEL,
            chatbot_config.OLLAMA_BASE_URL,
            req.build_hierarchical,
            req.build_global,
            req.build_chapter,
        )
        if pipeline is None:
            return _degrade(
                f"文档「{req.document_name}」未能构建检索管道（请确认 Ollama 已启动且已拉取 "
                f"{chatbot_config.EMBEDDINGS_MODEL} 嵌入模型）"
            )
        # 统一从磁盘重建课程级管道（FAISS 刚由真实 process_single_file 持久化；
        # 与服务重启后的恢复路径共用同一段真实组件装配代码）
        course_pipeline = _rebuild_pipeline_from_disk(req.course_id)
        if course_pipeline is None:
            course_pipeline = pipeline
            course_pipeline["doc_names"] = [doc_name]
            course_pipeline["reranker"] = reranker
        with _PIPELINE_LOCK:
            _PIPELINES[req.course_id] = course_pipeline
        try:
            st.session_state["current_source"] = doc_name
        except Exception:
            pass
        _sync_session_state(req.course_id, course_pipeline)
        kg = course_pipeline.get("knowledge_graph")
        # 层次大纲桥接：build_hierarchical 产出的树（章→节）并入课程图谱（type=CHAPTER，关系=包含章节）
        hierarchical_sync = {"skipped": "not_built"}
        if req.build_hierarchical:
            tree_path = os.path.join("graph_data", f"{doc_name}_hierarchical.json")
            if os.path.exists(tree_path):
                try:
                    with open(tree_path, "r", encoding="utf-8") as f:
                        tree = json.load(f)
                    hierarchical_sync = sync_hierarchical_tree_to_course_kg(
                        req.course_id, tree, doc_name)
                except Exception as exc:
                    print(f"[deepgraph] 层次树读取失败（桥接跳过）: {exc}")
                    hierarchical_sync = {"skipped": f"tree_read_failed: {exc}"}
        if kg and kg.number_of_edges() > 0:
            hierarchical_sync["global_graph"] = sync_chapter_graph_to_course_kg(
                req.course_id, kg, doc_name)
        return {
            "success": True,
            "course_id": req.course_id,
            "document_name": req.document_name,
            "stored_as": doc_name,
            "chapters": len(chapters),
            "chapter_names": list(chapters.keys())[:20],
            "chunk_count": len(course_pipeline.get("chunks") or []),
            "has_graph": bool(kg and kg.number_of_edges() > 0),
            "graph_edges": (kg.number_of_edges() if kg else 0),
            "course_graph_sync": hierarchical_sync,
            "course_documents": course_pipeline.get("doc_names") or [doc_name],
            "mode": "ENGINE",
            "code": 200,
        }
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"文档处理失败：{type(exc).__name__}: {exc}")


# ---------------- 文档列表 / 删除 ----------------
@app.get("/api/v1/deepgraph/documents")
def list_documents(course_id: Optional[int] = Query(None)):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    try:
        metadata = persistence.load_metadata()
        names = metadata.get("doc_names", [])
        if course_id is not None:
            names = [n for n in names if n.startswith(_doc_prefix(course_id))]
        items = []
        for name in names:
            chapters = persistence.load_chapters(name) or {}
            graph_path = os.path.join(
                persistence.GLOBAL_GRAPH_DIR,
                f"{persistence.safe_hash_name(name)}_global_graph.json",
            )
            items.append({
                "document_name": name,
                "course_id": course_id,
                "chapters_count": len(chapters),
                "chapter_names": list(chapters.keys())[:20],
                "has_global_graph": os.path.exists(graph_path),
                "has_faiss_index": persistence.get_saved_document_path(name) is not None,
            })
        return {"documents": items, "mode": "ENGINE", "code": 200}
    except Exception as exc:
        return _degrade(f"文档列表读取失败：{type(exc).__name__}: {exc}")


@app.delete("/api/v1/deepgraph/documents/{document_name}")
def delete_document(document_name: str, course_id: Optional[int] = Query(None)):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    saved = persistence.get_saved_document_path(document_name)
    if saved is None:
        raise HTTPException(status_code=404, detail="document not found")
    persistence.clear_all_data(document_name)
    try:  # 图谱数据同步清理（neo4j_handler 提供的真实清理方法；best-effort）
        neo4j_db.clear_source_data(document_name)
    except Exception as exc:
        print(f"[deepgraph] Neo4j 清理失败（继续）: {exc}")
    if course_id is not None:
        with _PIPELINE_LOCK:
            _PIPELINES.pop(course_id, None)  # 触发下次检索时按磁盘重建
    return {"success": True, "document_name": document_name, "mode": "ENGINE", "code": 200}


# ---------------- 检索（真实 retrieve_documents：HyDE + BM25/FAISS + 图谱 + 重排） ----------------
@app.post("/api/v1/deepgraph/retrieve")
def retrieve(req: RetrieveRequest):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    pipeline = _pipeline_for(req.course_id)
    if pipeline is None:
        return _degrade(
            f"课程 {req.course_id} 尚未构建文档检索索引，请先调用 POST /api/v1/deepgraph/ingest"
        )
    try:
        _sync_session_state(req.course_id, pipeline)
        _apply_flags({
            "enable_hyde": req.enable_hyde,
            "enable_graph_rag": req.enable_graph_rag,
            "enable_reranking": req.enable_reranking,
            "max_contexts": req.top_k,
        })
        docs = retrieve_documents(req.query, chatbot_config.OLLAMA_API_URL, chatbot_config.MODEL, "")
        results = []
        for i, doc in enumerate(docs):
            page = getattr(doc, "page_content", "") or ""
            if not page:
                continue
            results.append({
                "document_id": None,
                "document_name": (pipeline.get("doc_names") or ["deepgraph"])[0],
                "relevance_score": round(max(0.0, 1.0 - 0.05 * i), 4),
                "snippet": page[:800],
                "retrieval": "VECTOR",
            })
        # Neo4j GraphRAG 关系上下文（原 backend.get_retrieval_context 的图谱分支，多文档聚合）
        graph_lines: List[str] = []
        if st.session_state.get("enable_graph_rag", True):
            try:
                entities = chatbot_backend.extract_entities_from_prompt(req.query)
                if entities:
                    for name in (pipeline.get("doc_names") or []):
                        ctx = neo4j_db.query_graph_context(entities, name)
                        if ctx:
                            graph_lines.extend([ln for ln in ctx.split("\n") if ln.strip()])
            except Exception as exc:
                print(f"[deepgraph] 图谱上下文获取失败（跳过）: {exc}")
        return {
            "results": results,
            "graph_context": "\n".join(graph_lines[:40]),
            "mode": "ENGINE",
            "code": 200,
        }
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"检索失败：{type(exc).__name__}: {exc}")


# ---------------- 问答（真实 generate_llm_response，上下文按已验收格式组装） ----------------
@app.post("/api/v1/deepgraph/qa")
def qa(req: QaRequest):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    if req.answer_mode not in ANSWER_MODES:
        raise HTTPException(status_code=422, detail=f"answer_mode 须为 {sorted(ANSWER_MODES)} 之一")
    pipeline = _pipeline_for(req.course_id)
    if pipeline is None:
        return _degrade(
            f"课程 {req.course_id} 尚未构建文档检索索引，请先调用 POST /api/v1/deepgraph/ingest"
        )
    try:
        _sync_session_state(req.course_id, pipeline)
        _apply_flags({
            "enable_hyde": req.enable_hyde,
            "enable_graph_rag": req.enable_graph_rag,
            "enable_reranking": req.enable_reranking,
        })
        source_doc = ""
        if req.document_name:
            source_doc = _course_doc_name(req.course_id, req.document_name)
        else:
            doc_names = pipeline.get("doc_names") or []
            source_doc = doc_names[-1] if doc_names else ""
        st.session_state["current_source"] = source_doc

        vector_docs: list = []
        graph_lines: List[str] = []
        if req.answer_mode in ("向量检索 + 知识图谱", "仅向量检索"):
            vector_docs = retrieve_documents(
                req.question, chatbot_config.OLLAMA_API_URL, chatbot_config.MODEL,
                "\n".join(str(m.get("content", "")) for m in req.chat_history[-5:]),
            )
        if req.answer_mode in ("向量检索 + 知识图谱", "仅知识图谱"):
            try:
                entities = chatbot_backend.extract_entities_from_prompt(req.question)
                if entities:
                    for name in (pipeline.get("doc_names") or []):
                        ctx = neo4j_db.query_graph_context(entities, name)
                        if ctx:
                            graph_lines.extend([ln for ln in ctx.split("\n") if ln.strip()])
            except Exception as exc:
                print(f"[deepgraph] 图谱上下文获取失败（跳过）: {exc}")

        vector_context = "\n".join(
            f"> 来源 {i + 1}: {getattr(d, 'page_content', '')}" for i, d in enumerate(vector_docs)
        )
        parts = []
        if vector_context:
            parts.append(f"【向量检索上下文】\n{vector_context}")
        if graph_lines:
            parts.append(f"【图谱关系上下文】\n" + "\n".join(graph_lines[:40]))
        context = "\n\n".join(parts)

        answer, llm_prompt = chatbot_backend.generate_llm_response(req.question, context)
        sources = []
        for i, doc in enumerate(vector_docs):
            page = getattr(doc, "page_content", "") or ""
            if page:
                sources.append({
                    "document_id": None,
                    "document_name": source_doc,
                    "relevance_score": round(max(0.0, 1.0 - 0.05 * i), 4),
                    "snippet": page[:800],
                })
        if not answer:
            return _degrade("生成模型返回空回答（请检查 LLM_PROVIDER / DEEPSEEK_API_KEY / Ollama）")
        return {
            "answer": answer,
            "llm_prompt": llm_prompt,
            "sources": sources,
            "source_doc": source_doc,
            "answer_mode": req.answer_mode,
            "mode": "ENGINE",
            "code": 200,
        }
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"问答失败：{type(exc).__name__}: {exc}")


# ---------------- 章节知识图谱（真实 build_chapter_knowledge_graph + Neo4j 同步） ----------------
@app.post("/api/v1/deepgraph/chapter-graph")
def chapter_graph(req: ChapterGraphRequest):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    doc_name = _course_doc_name(req.course_id, req.document_name)
    chapter_text = req.chapter_text
    chapter_name = req.chapter_name or ""
    if not chapter_text:
        chapters = persistence.load_chapters(doc_name)
        if not chapters:
            return _degrade(
                f"文档「{req.document_name}」没有章节切分数据（入库时请 build_chapter=true，或直接提供 chapter_text）"
            )
        if not chapter_name:
            raise HTTPException(status_code=422, detail="chapter_name 不能为空（或直接提供 chapter_text）")
        if chapter_name not in chapters:
            raise HTTPException(status_code=404, detail=f"章节「{chapter_name}」不存在")
        chapter_text = chapters[chapter_name]
    safe_chapter = (chapter_name or "custom").replace("/", "_").replace("\\", "_").replace(" ", "")
    chapter_source = f"{doc_name}_chapter_{safe_chapter}"
    try:
        logs: List[dict] = []

        def progress_callback(stage, message, detail):
            logs.append({"stage": stage, "message": message, "detail": (detail or "")[:400]})

        kg = chatbot_backend.build_chapter_graph_backend(chapter_name or req.document_name, chapter_text, progress_callback)
        if kg is None or kg.number_of_edges() == 0:
            return _degrade("未能从该章提取出有效三元组（请检查 LLM 服务或文本内容）")
        import networkx as nx

        os.makedirs("graph_data", exist_ok=True)
        graph_save_path = os.path.join("graph_data", f"{doc_name}_kg_{safe_chapter}.json")
        try:
            with open(graph_save_path, "w", encoding="utf-8") as f:
                json.dump(nx.node_link_data(kg), f, ensure_ascii=False)
        except Exception as exc:
            print(f"[deepgraph] 章节图谱本地存档失败（继续）: {exc}")
        neo4j_synced = False
        try:
            neo4j_db.clear_source_data(chapter_source)
            neo4j_db.insert_triplets_from_graph(kg, chapter_source, graph_type="chapter")
            neo4j_db.insert_triplets_from_graph(kg, doc_name, graph_type="chapter")
            neo4j_synced = True
        except Exception as exc:
            print(f"[deepgraph] Neo4j 存储失败（请确保 Neo4j 已启动）: {exc}")
        # 课程知识图谱桥接：真实抽取的三元组并入 kg_service 课程图谱（平台「知识图谱」页数据源）
        kg_sync = sync_chapter_graph_to_course_kg(req.course_id, kg, doc_name)
        relations = [
            {"source": str(u), "relation": (data or {}).get("label", "相关"), "target": str(v)}
            for u, v, data in list(kg.edges(data=True))[:50]
        ]
        return {
            "success": True,
            "course_id": req.course_id,
            "document_name": doc_name,
            "chapter": chapter_name,
            "nodes": kg.number_of_nodes(),
            "edges": kg.number_of_edges(),
            "relations": relations,
            "neo4j_synced": neo4j_synced,
            "course_graph_sync": kg_sync,
            "logs": logs[:30],
            "mode": "ENGINE",
            "code": 200,
        }
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"章节图谱构建失败：{type(exc).__name__}: {exc}")


# ---------------- 学习路径（真实 query_learning_path：Neo4j 反向追溯 + 本地图回退） ----------------
@app.post("/api/v1/deepgraph/learning-path")
def learning_path(req: LearningPathRequest):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    pipeline = _pipeline_for(req.course_id) if req.course_id is not None else None
    source_doc = ""
    if pipeline is not None:
        _sync_session_state(req.course_id, pipeline)
        doc_names = pipeline.get("doc_names") or []
        source_doc = doc_names[-1] if doc_names else ""
    try:
        result = neo4j_db.query_learning_path(
            target_entity=req.target.strip(),
            source_doc=source_doc or None,
            max_depth=req.max_depth,
            search_all_sources=req.search_all_sources,
        )
        return {
            "target": req.target,
            "path_text": result,
            "source_doc": source_doc,
            "mode": "ENGINE",
            "code": 200,
        }
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"学习路径生成失败：{type(exc).__name__}: {exc}")


# ---------------- 课程图谱聚合（供 Java 同步进 MySQL knowledge_points/relations） ----------------
# 平台「知识图谱」页渲染 MySQL 业务表（/knowledge/points/graph）——本端点把某文档
# 已归档的全部章节图谱（graph_data/{doc}_kg_*.json，LLM 真实抽取产物）与层次大纲树
# （{doc}_hierarchical.json）聚合成 nodes/edges，供 Java 门面幂等落库。

@app.get("/api/v1/deepgraph/course-graph")
def course_graph(course_id: int = Query(...), document_name: str = Query(...)):
    if not engine_available():
        return _degrade(f"引擎依赖不可用（缺：{'；'.join(engine_dep_errors())}）")
    doc_name = _course_doc_name(course_id, document_name)
    try:
        import networkx as nx

        edges = []
        node_meta: Dict[str, dict] = {}
        chapters: List[str] = []
        os.makedirs("graph_data", exist_ok=True)
        prefix = f"{doc_name}_kg_"
        for file_name in sorted(os.listdir("graph_data")):
            if not file_name.startswith(prefix) or not file_name.endswith(".json"):
                continue
            chapter_name = file_name[len(prefix):-len(".json")]
            chapters.append(chapter_name)
            try:
                with open(os.path.join("graph_data", file_name), "r", encoding="utf-8") as f:
                    graph = nx.node_link_graph(json.load(f))
            except Exception as exc:
                print(f"[deepgraph] 章节图谱归档读取失败（跳过 {chapter_name[:40]}）: {exc}")
                continue
            for u, v, data in graph.edges(data=True):
                relation = (data or {}).get("label") or "相关"
                evidence = _edge_evidence(data)
                for name in (str(u), str(v)):
                    meta = node_meta.setdefault(name, {"type": "CONCEPT", "evidence": ""})
                    if not meta["evidence"] and evidence:
                        meta["evidence"] = evidence[:400]
                edges.append({"source": str(u), "target": str(v),
                              "relation": str(relation)[:60],
                              "evidence": evidence[:400], "chapter": chapter_name[:120]})

        tree_path = os.path.join("graph_data", f"{doc_name}_hierarchical.json")
        if os.path.exists(tree_path):
            try:
                with open(tree_path, "r", encoding="utf-8") as f:
                    tree = json.load(f)
            except Exception as exc:
                print(f"[deepgraph] 层次树读取失败（骨架跳过）: {exc}")
                tree = None
            if tree:
                def walk(node: dict, parent_name: Optional[str]) -> None:
                    name = (node or {}).get("name")
                    if not name:
                        return
                    name = str(name)
                    node_meta.setdefault(name, {"type": "CONCEPT", "evidence": ""})["type"] = "CHAPTER"
                    if parent_name:
                        node_meta[name]["type"] = "CHAPTER"
                        edges.append({"source": name, "target": parent_name,
                                      "relation": "包含章节", "evidence": "",
                                      "chapter": (tree.get("name") or "")[:120]})
                    for child in (node or {}).get("children") or []:
                        walk(child, name)

                walk(tree, None)

        # 三元组判重（跨章重复抽取同一关系只保留一条，章节字段保留首见）
        seen = set()
        deduped_edges = []
        for e in edges:
            key = (e["source"], e["target"], e["relation"])
            if key in seen:
                continue
            seen.add(key)
            deduped_edges.append(e)

        nodes = [{"name": name, "type": meta["type"], "evidence": meta["evidence"]}
                 for name, meta in sorted(node_meta.items())]
        return {"course_id": course_id, "document_name": doc_name,
                "chapters": chapters, "nodes": nodes, "edges": deduped_edges,
                "mode": "ENGINE", "code": 200}
    except Exception as exc:
        traceback.print_exc()
        return _degrade(f"课程图谱聚合失败：{type(exc).__name__}: {exc}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8501)
