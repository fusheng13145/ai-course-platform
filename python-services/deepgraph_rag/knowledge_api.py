"""
知识库检索端点（原 8002 契约）· 挂在合并服务 8501 上
====================================================

**来源**：`python-services/rag_service/rag_service.py` 的 9 个契约端点，按 Q-03
「双引擎收敛」迁入 8501。路径、方法、响应模型、降级语义**逐项保持原样**，Java 侧
只需把 `RAG_BASE_URL` 指到 8501 即可，不改任何客户端代码。

端点（与原 8002 完全同名同路径）：
  GET    /api/v1/rag/health
  POST   /api/v1/rag/ask            {question, course_id?, top_k}        -> {answer, sources[], mode, gen_mode, code}
  POST   /api/v1/rag/documents      {course_id, document_name, content}  -> 文档元信息
  GET    /api/v1/rag/documents?course_id=
  PUT    /api/v1/rag/documents/{id} {content}
  DELETE /api/v1/rag/documents/{id}
  POST   /api/v1/rag/retrieve       {query, course_id?, top_k}           -> {results[]}
  POST   /api/v1/rag/index/build/{courseId}
  DELETE /api/v1/rag/index/{courseId}

为什么不直接把 8002 的 FastAPI app 挂进来：8002 的中间件（内部认证）与 8501 的
`_request_guard` 语义重叠，两套中间件叠加会改变鉴权/限流口径。收敛后**只保留 8501
一套网关语义**（`X-Internal-Key` 校验 + trace 透传），端点实现放在本模块。

降级口径（与 Java `ContractPaths.MSG_RAG_UNAVAILABLE` 严格一致）：
- 无引擎依赖 / 课程无索引 → HTTP 503 + body{code:503, mode:"UNAVAILABLE", error}；
- 引擎依赖可 import 但运行期失败 → HTTP 200 + body{code:503, mode:"UNAVAILABLE", error}
  （与迁入前逐字一致，测试对此有显式断言，不要“顺手修正”成 503）。
"""
from __future__ import annotations

import os
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

import knowledge_engine

router = APIRouter()

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
    # 检索增强链字段：engine.search() 会写 retrieval=HYDE|QUERY 与 rerank_score，
    # 模型不声明会被 Pydantic 静默裁掉（2026-11-15 B5 实测，Java 侧因此读不到重排分）。
    retrieval: Optional[str] = None
    rerank_score: Optional[float] = None


class RagQueryResult(BaseModel):
    answer: Optional[str] = None
    sources: List[RagSource] = []
    mode: str = "LOCAL"
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


@router.get("/api/v1/rag/health")
def health():
    """status 只反映检索服务自身；engine 如实上报引擎能力（沿用 8002 的 7 个键，勿加字段）。

    8002 契约测试断言 `set(engine) == {7 keys}`，加字段会让护栏红——故数据目录等
    运维信息放在顶层 `data_dir`，不塞进 engine。
    """
    return {
        "status": "ok",
        "llm": knowledge_engine.llm_health(),
        "engine": {
            "available": knowledge_engine.engine_available(),
            "functional": knowledge_engine.engine_functional(),
            "dep_errors": knowledge_engine.dep_errors(),
            "embed_model": knowledge_engine.embed_model_name(),
            "version": knowledge_engine.ENGINE_VERSION,
            "hyde": knowledge_engine.hyde_status(),
            "rerank": knowledge_engine.rerank_status(),
        },
        # 合并后运维必须能一眼看出语料落盘位置（迁移期默认沿用 8002 旧目录）
        "data_dir": str(knowledge_engine.data_dir()),
    }


@router.post("/api/v1/rag/documents", response_model=RagDocument)
def add_document(doc: DocumentIn):
    saved = knowledge_engine.add_document(doc.course_id, doc.document_name, doc.content)
    return RagDocument(id=saved["id"], document_name=saved["document_name"],
                       course_id=saved["course_id"], content=saved["content"])


@router.get("/api/v1/rag/documents", response_model=List[RagDocument])
def list_documents(course_id: Optional[int] = Query(None)):
    return [RagDocument(**d) for d in knowledge_engine.list_documents(course_id)]


@router.put("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def update_document(document_id: int, upd: DocumentUpdate):
    updated = knowledge_engine.update_document(document_id, upd.content)
    if updated is None:
        raise HTTPException(status_code=404, detail="document not found")
    return RagDocument(**updated)


@router.delete("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def delete_document(document_id: int):
    removed = knowledge_engine.delete_document(document_id)
    if removed is None:
        raise HTTPException(status_code=404, detail="document not found")
    return RagDocument(**removed)


def _engine_ready_for(course_id: Optional[int]) -> bool:
    """真实引擎可用且该课程已有有效索引。"""
    return (course_id is not None
            and knowledge_engine.engine_available()
            and knowledge_engine.index_status(course_id) is not None)


def _degrade_error(course_id: Optional[int]) -> str:
    # 课程有文档但未建索引时给出可操作提示；其余情况保持契约降级文案
    if course_id is not None and knowledge_engine.list_documents(course_id) \
            and knowledge_engine.index_status(course_id) is None:
        return (f"课程 {course_id} 已有文档但尚未构建索引，"
                f"请先调用 POST /api/v1/rag/index/build/{course_id}")
    return MSG_RAG_UNAVAILABLE


# 契约测试/CI 用的确定性占位响应（引擎依赖未安装或课程无索引时，
# RAG_ENGINE_READY=1 走此路径，保证契约测试无需下载模型即可断言 200 成功结构）。
# ⚠ 这条路径返回硬编码文本，因此 mode/gen_mode 一律写 PLACEHOLDER：自称 ENGINE
# 就等于把「没接引擎」对外宣称为「引擎已应答」。
_PLACEHOLDER_SOURCES = [
    RagSource(document_id=1, document_name="TCP 三次握手",
              relevance_score=0.92, snippet="SYN/ACK 建立连接"),
    RagSource(document_id=2, document_name="OSI 七层模型",
              relevance_score=0.81, snippet="应用层到物理层"),
]


@router.post("/api/v1/rag/retrieve", response_model=RagRetrievalResult)
def retrieve(req: RetrieveRequest):
    try:
        if _engine_ready_for(req.course_id):
            results = knowledge_engine.search(req.query, req.course_id, req.top_k) or []
            return RagRetrievalResult(
                results=[RagSource(**r) for r in results],
                mode="ENGINE",
                code=200,
            )
    except knowledge_engine.StoreCorruptedError as exc:
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
    # 引擎未接入或该课程无索引：按手册 §34 降级语义，HTTP 503 即契约（I-1 契约化）。
    return JSONResponse(status_code=503, content=RagRetrievalResult(
        results=[],
        mode="UNAVAILABLE",
        error=_degrade_error(req.course_id),
        code=503,
    ).model_dump())


@router.post("/api/v1/rag/ask", response_model=RagQueryResult)
def ask(req: AskRequest):
    try:
        if _engine_ready_for(req.course_id):
            results = knowledge_engine.search(req.question, req.course_id, req.top_k) or []
            # R3：主路径图谱增强——图证据与文档检索同形拼进上下文；图不可用则为空列表。
            results = results + knowledge_engine.graph_sources(req.course_id, req.question)
            contexts = [RagSource(**r) for r in results]
            answer, gen_mode = knowledge_engine.generate_answer(req.question, results)
            if gen_mode == "UNAVAILABLE":
                # 既无可用资料、LLM 又不可用：如实降级，别让空回答冒充 ENGINE/200 成功
                return RagQueryResult(
                    answer=None, sources=contexts, mode="UNAVAILABLE",
                    gen_mode=gen_mode,
                    error="引擎无可用资料且生成模型不可用（未配密钥／Ollama 不可达或调用失败）",
                    code=503)
            return RagQueryResult(answer=answer, sources=contexts,
                                  mode="ENGINE", gen_mode=gen_mode, code=200)
    except knowledge_engine.StoreCorruptedError as exc:
        return RagQueryResult(answer=None, sources=[], mode="UNAVAILABLE",
                              error=f"引擎存储损坏：{exc}", code=503)
    except Exception as exc:
        return RagQueryResult(answer=None, sources=[], mode="UNAVAILABLE",
                              error=f"引擎检索失败：{type(exc).__name__}: {exc}", code=503)
    if os.getenv("RAG_ENGINE_READY", "0") == "1":
        # CI 占位桩：形状与成功一致，但绝不自称 ENGINE（否则就是对外虚报能力）
        return RagQueryResult(
            answer="TCP 是面向连接的传输层协议，通过三次握手建立可靠连接。",
            sources=list(_PLACEHOLDER_SOURCES),
            mode="PLACEHOLDER",
            gen_mode="PLACEHOLDER",
            code=200,
        )
    return JSONResponse(status_code=503, content=RagQueryResult(
        answer=None,
        sources=[],
        mode="UNAVAILABLE",
        error=_degrade_error(req.course_id),
        code=503,
    ).model_dump())


@router.post("/api/v1/rag/index/build/{course_id}", response_model=IndexResult)
def build_index(course_id: int):
    if not knowledge_engine.engine_available():
        # 依赖缺失时必须如实失败：绝不能把文档条数当 chunk 数返回 success=True
        deps = "；".join(knowledge_engine.dep_errors()) or "依赖未安装"
        return IndexResult(
            success=False,
            indexed_chunks=0,
            error=f"引擎不可用，未构建索引（缺依赖：{deps}）",
            code=503,
        )
    try:
        out = knowledge_engine.build_course_index(course_id)
    except Exception as exc:
        # 引擎运行时故障：按契约降级返回 success=False 而非 500，避免运维盲区
        return IndexResult(
            success=False,
            indexed_chunks=0,
            error=f"引擎建索引失败：{type(exc).__name__}: {exc}",
        )
    return IndexResult(success=out["success"],
                       indexed_chunks=out["indexed_chunks"],
                       error=out.get("error"))


@router.delete("/api/v1/rag/index/{course_id}", response_model=IndexResult)
def clear_index(course_id: int):
    knowledge_engine.clear_course_index(course_id)
    return IndexResult(success=True, indexed_chunks=0)
