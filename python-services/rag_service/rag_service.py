"""
RAG 检索服务（8002）· 集成契约 + 真实混合检索引擎
================================================
端点与 JSON 形状严格对齐 `docs/项目手册.md`（第八部分）、Java 端
`RAGService` / `RAGServiceImpl` / `HttpRagProvider` 与 `RagContract`。
CI 护栏 job `python-rag-contract`（`pytest -q`，含 `test_rag_contract.py`）守护该契约，
替换/演进引擎逻辑时不得破坏端点路径与响应结构。

引擎实现在 `rag_engine.py`（BM25 + 向量混合召回、RRF 融合、DeepSeek 生成）：
- 重依赖（torch/faiss/sentence-transformers/jieba/rank_bm25）**懒加载**；
  依赖齐备且课程已建索引时走真实引擎（mode=ENGINE / code=200）；
- 依赖缺失（如 CI 环境）时行为回退为占位语义，契约测试仍全绿；
- 引擎对该课程无数据时按手册 §34 降级：code=503 / mode=UNAVAILABLE，
  Java 端据此回退纯 LLM 回答。

契约端点（详见 RagContract.java 注释）：
  GET  /api/v1/rag/health
  POST /api/v1/rag/ask            {question, course_id?, top_k}        -> {answer, sources[], mode, code}
  POST /api/v1/rag/documents      {course_id, document_name, content}  -> 文档元信息
  GET  /api/v1/rag/documents?course_id=
  PUT  /api/v1/rag/documents/{id} {content}                            -> 更新
  DELETE /api/v1/rag/documents/{id}
  POST /api/v1/rag/retrieve       {query, course_id?, top_k}           -> {results[]}
  POST /api/v1/rag/index/build/{courseId}                             -> {success, indexed_chunks}
  DELETE /api/v1/rag/index/{courseId}                                 -> {success}

文档与索引用持久化存储（`rag_engine.py`，数据目录 RAG_DATA_DIR，默认 ./data，已 gitignore）。
语料正文的权威副本在仓库根的 `scripts/rag_corpus.json`，用 `scripts/seed-rag-corpus.py` 重放。
"""
from __future__ import annotations

import os
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

try:
    import rag_engine
except ImportError:  # 以 python-services 为工作目录、按子包方式启动时（uvicorn rag_service.rag_service:app）
    from . import rag_engine

app = FastAPI(title="AI Course RAG Service", version="0.2.0")

# 与 Java 端 com.aicourse.integration.ContractPaths.MSG_RAG_UNAVAILABLE 严格一致（降级语义文案）
MSG_RAG_UNAVAILABLE = "RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用"


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    course_id: Optional[int] = None
    top_k: int = Field(5, ge=1, le=50)


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    course_id: Optional[int] = None
    top_k: int = Field(5, ge=1, le=50)


class DocumentIn(BaseModel):
    course_id: int = Field(ge=1)
    document_name: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1)


class DocumentUpdate(BaseModel):
    content: str


class RagSource(BaseModel):
    document_id: Optional[int] = None
    document_name: Optional[str] = None
    relevance_score: Optional[float] = None
    snippet: Optional[str] = None


class RagQueryResult(BaseModel):
    answer: Optional[str] = None
    sources: List[RagSource] = []
    mode: str = "LOCAL"
    # 生成层来源（与 mode 正交）：mode 说明「检索走了哪条路」，gen_mode 说明「这段回答是谁写的」。
    # LLM=真实大模型；EXTRACTIVE=抽取式兜底；UNAVAILABLE=两者都不可用；PLACEHOLDER=CI 占位桩。
    # 旧实现把它丢弃在 `answer, _gen_mode = ...` 里，于是密钥 401 的降级回答对外与真生成完全同形。
    gen_mode: Optional[str] = None
    error: Optional[str] = None
    code: int = 200


class RagRetrievalResult(BaseModel):
    results: List[RagSource] = []
    mode: str = "LOCAL"
    error: Optional[str] = None
    code: int = 200


class RagDocument(BaseModel):
    id: Optional[int] = None
    document_name: Optional[str] = None
    course_id: Optional[int] = None
    content: Optional[str] = None
    mode: str = "LOCAL"
    error: Optional[str] = None
    code: int = 200


class IndexResult(BaseModel):
    success: Optional[bool] = None
    indexed_chunks: Optional[int] = None
    error: Optional[str] = None
    code: int = 200


@app.get("/api/v1/rag/health")
def health():
    # status 只反映检索服务自身；生成后端不可用时只降级 llm.available，不把服务判为下线。
    return {"status": "ok", "llm": rag_engine.llm_health()}


@app.post("/api/v1/rag/documents", response_model=RagDocument)
def add_document(doc: DocumentIn):
    saved = rag_engine.add_document(doc.course_id, doc.document_name, doc.content)
    return RagDocument(id=saved["id"], document_name=saved["document_name"],
                       course_id=saved["course_id"], content=saved["content"])


@app.get("/api/v1/rag/documents", response_model=List[RagDocument])
def list_documents(course_id: Optional[int] = Query(None)):
    return [RagDocument(**d) for d in rag_engine.list_documents(course_id)]


@app.put("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def update_document(document_id: int, upd: DocumentUpdate):
    updated = rag_engine.update_document(document_id, upd.content)
    if updated is None:
        raise HTTPException(status_code=404, detail="document not found")
    return RagDocument(**updated)


@app.delete("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def delete_document(document_id: int):
    removed = rag_engine.delete_document(document_id)
    if removed is None:
        raise HTTPException(status_code=404, detail="document not found")
    return RagDocument(**removed)


def _engine_ready_for(course_id: Optional[int]) -> bool:
    """真实引擎可用且该课程已有有效索引。"""
    return (course_id is not None
            and rag_engine.engine_available()
            and rag_engine.index_status(course_id) is not None)


def _degrade_error(course_id: Optional[int]) -> str:
    # 课程有文档但未建索引时给出可操作提示；其余情况保持契约降级文案
    if course_id is not None and rag_engine.list_documents(course_id) \
            and rag_engine.index_status(course_id) is None:
        return (f"课程 {course_id} 已有文档但尚未构建索引，"
                f"请先调用 POST /api/v1/rag/index/build/{course_id}")
    return MSG_RAG_UNAVAILABLE


# 契约测试/CI 用的确定性占位响应（引擎依赖未安装或课程无索引时，
# RAG_ENGINE_READY=1 走此路径，保证 python-rag-contract 无需下载模型即可断言 200 成功结构）。
# ⚠ 这条路径返回的是硬编码文本，因此 mode/gen_mode 一律写 PLACEHOLDER：自称 ENGINE
# 就等于把「没接引擎」对外宣称为「引擎已应答」，取证与告警都会被误导。
_PLACEHOLDER_SOURCES = [
    RagSource(document_id=1, document_name="TCP 三次握手",
              relevance_score=0.92, snippet="SYN/ACK 建立连接"),
    RagSource(document_id=2, document_name="OSI 七层模型",
              relevance_score=0.81, snippet="应用层到物理层"),
]


@app.post("/api/v1/rag/retrieve", response_model=RagRetrievalResult)
def retrieve(req: RetrieveRequest):
    try:
        if _engine_ready_for(req.course_id):
            results = rag_engine.search(req.query, req.course_id, req.top_k) or []
            return RagRetrievalResult(
                results=[RagSource(**r) for r in results],
                mode="ENGINE",
                code=200,
            )
    except rag_engine.StoreCorruptedError as exc:
        # 存储损坏：显式报错并降级（非静默返回空），error 携带可操作信息
        return RagRetrievalResult(results=[], mode="UNAVAILABLE",
                                  error=f"引擎存储损坏：{exc}", code=503)
    except Exception as exc:
        # 引擎运行时故障（依赖可 import 但模型加载/向量化失败）：降级而非 500
        return RagRetrievalResult(results=[], mode="UNAVAILABLE",
                                  error=f"引擎检索失败：{type(exc).__name__}: {exc}",
                                  code=503)
    if os.getenv("RAG_ENGINE_READY", "0") == "1":
        # CI 占位桩（硬编码样例），自称 ENGINE 即虚报引擎已应答
        return RagRetrievalResult(
            results=list(_PLACEHOLDER_SOURCES),
            mode="PLACEHOLDER",
            code=200,
        )
    # 引擎未接入或该课程无索引：按手册 §34 降级语义 code=503 / mode=UNAVAILABLE
    return RagRetrievalResult(
        results=[],
        mode="UNAVAILABLE",
        error=_degrade_error(req.course_id),
        code=503,
    )


@app.post("/api/v1/rag/ask", response_model=RagQueryResult)
def ask(req: AskRequest):
    try:
        if _engine_ready_for(req.course_id):
            results = rag_engine.search(req.question, req.course_id, req.top_k) or []
            # R3：主路径图谱增强——图证据与文档检索同形拼进上下文；图不可用则为空列表，
            # 不影响原检索/生成/降级路径（异常已在 graph_sources 内部捕获并留痕）。
            results = results + rag_engine.graph_sources(req.course_id, req.question)
            contexts = [RagSource(**r) for r in results]
            answer, gen_mode = rag_engine.generate_answer(req.question, results)
            if gen_mode == "UNAVAILABLE":
                # 既无可用资料、LLM 又不可用：如实降级，别让空回答冒充 ENGINE/200 成功
                # （Java 侧见 503/UNAVAILABLE 才会回退纯 LLM 作答）
                return RagQueryResult(
                    answer=None, sources=contexts, mode="UNAVAILABLE",
                    gen_mode=gen_mode,
                    error="引擎无可用资料且生成模型不可用（未配密钥／Ollama 不可达或调用失败）",
                    code=503)
            return RagQueryResult(answer=answer, sources=contexts,
                                  mode="ENGINE", gen_mode=gen_mode, code=200)
    except rag_engine.StoreCorruptedError as exc:
        return RagQueryResult(answer=None, sources=[], mode="UNAVAILABLE",
                              error=f"引擎存储损坏：{exc}", code=503)
    except Exception as exc:
        # 引擎运行时故障（依赖可 import 但模型加载/向量化失败）：降级而非 500
        return RagQueryResult(answer=None, sources=[], mode="UNAVAILABLE",
                              error=f"引擎检索失败：{type(exc).__name__}: {exc}", code=503)
    if os.getenv("RAG_ENGINE_READY", "0") == "1":
        # CI 占位桩：形状与成功一致，但绝不自称 ENGINE——返回的是硬编码样例文本，
        # 若被当成引擎应答，就是对外虚报能力（该开关只应在契约测试里置位）。
        return RagQueryResult(
            answer="TCP 是面向连接的传输层协议，通过三次握手建立可靠连接。",
            sources=list(_PLACEHOLDER_SOURCES),
            mode="PLACEHOLDER",
            gen_mode="PLACEHOLDER",
            code=200,
        )
    # 引擎未接入：按手册 §34 降级语义 code=503 / mode=UNAVAILABLE；
    # Java 端 RAG_ENABLED=false 或收到降级时回退纯 LLM。
    return RagQueryResult(
        answer=None,
        sources=[],
        mode="UNAVAILABLE",
        error=_degrade_error(req.course_id),
        code=503,
    )


@app.post("/api/v1/rag/index/build/{course_id}", response_model=IndexResult)
def build_index(course_id: int):
    if not rag_engine.engine_available():
        # CI/轻量环境保持占位契约行为（不断言真实索引语义）
        chunks = len(rag_engine.list_documents(course_id))
        return IndexResult(success=True, indexed_chunks=chunks)
    try:
        out = rag_engine.build_course_index(course_id)
    except Exception as exc:
        # 引擎运行时故障（依赖可 import 但模型缺失/离线部署/版本不兼容）：
        # 按契约降级返回 success=False 而非 500，避免运维盲区。
        return IndexResult(
            success=False,
            indexed_chunks=0,
            error=f"引擎建索引失败：{type(exc).__name__}: {exc}",
        )
    return IndexResult(success=out["success"],
                       indexed_chunks=out["indexed_chunks"],
                       error=out.get("error"))


@app.delete("/api/v1/rag/index/{course_id}", response_model=IndexResult)
def clear_index(course_id: int):
    # 契约：DELETE /api/v1/rag/index/{courseId} 清理该课程向量索引。
    rag_engine.clear_course_index(course_id)
    return IndexResult(success=True, indexed_chunks=0)
