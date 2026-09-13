"""
RAG 检索引擎（8002）· 集成契约占位骨架
================================================
⚠️ 重要职责边界：RAG 检索引擎（BM25 / 向量检索 / embedding / 融合）由**团队另一成员**
以 Python 微服务（端口 8002）实现，本文件**不是引擎实现**，仅为：

1. 与 Java 端 `RAGService` / `RAGServiceImpl` / `HttpRagProvider` 的**对接契约占位**；
2. CI 护栏 `python-rag-contract` 的可运行载体（确保契约端点路径/JSON 形状不被破坏）；
3. 队友接入真实引擎前的**最小可启动骨架**——队友应在本文件基础上替换检索/生成逻辑，
   保持以下端点与 `docs/项目手册.md`（第八部分）及 `com.aicourse.integration.rag.RagContract`
   一致。

契约端点（详见 RagContract.java 注释）：
  GET  /api/v1/rag/health
  POST /api/v1/rag/ask            {question, course_id?, top_k}        -> {answer, sources[], mode, code}
  POST /api/v1/rag/documents      {course_id, document_name, content}  -> 文档元信息
  GET  /api/v1/rag/documents?course_id=
  PUT  /api/v1/rag/documents/{id} {content}                            -> 更新
  DELETE /api/v1/rag/documents/{id}
  POST /api/v1/rag/retrieve       {query, course_id?, top_k}           -> {results[]}
  POST /api/v1/rag/index/build/{courseId}                             -> {success, indexed_chunks}

本占位使用进程内内存字典存储文档，仅用于契约验证；真实引擎应接入持久化/向量库。
"""
from __future__ import annotations

import os
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

app = FastAPI(title="AI Course RAG Service (contract placeholder)", version="0.1.0")

# 与 Java 端 com.aicourse.integration.ContractPaths.MSG_RAG_UNAVAILABLE 严格一致（降级语义文案）
MSG_RAG_UNAVAILABLE = "RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用"

# ---- 进程内文档存储（占位，仅契约验证用）----
_DOCS: dict[int, dict] = {}
_NEXT_ID = 1


class AskRequest(BaseModel):
    question: str
    course_id: Optional[int] = None
    top_k: int = 5


class RetrieveRequest(BaseModel):
    query: str
    course_id: Optional[int] = None
    top_k: int = 5


class DocumentIn(BaseModel):
    course_id: int
    document_name: str
    content: str


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
    return {"status": "ok"}


@app.post("/api/v1/rag/documents", response_model=RagDocument)
def add_document(doc: DocumentIn):
    global _NEXT_ID
    doc_id = _NEXT_ID
    _NEXT_ID += 1
    _DOCS[doc_id] = {
        "id": doc_id,
        "course_id": doc.course_id,
        "document_name": doc.document_name,
        "content": doc.content,
    }
    return RagDocument(id=doc_id, document_name=doc.document_name,
                       course_id=doc.course_id, content=doc.content)


@app.get("/api/v1/rag/documents", response_model=List[RagDocument])
def list_documents(course_id: Optional[int] = Query(None)):
    docs = [RagDocument(**d) for d in _DOCS.values()]
    if course_id is not None:
        docs = [d for d in docs if d.course_id == course_id]
    return docs


@app.put("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def update_document(document_id: int, upd: DocumentUpdate):
    if document_id not in _DOCS:
        raise HTTPException(status_code=404, detail="document not found")
    _DOCS[document_id]["content"] = upd.content
    return RagDocument(**_DOCS[document_id])


@app.delete("/api/v1/rag/documents/{document_id}", response_model=RagDocument)
def delete_document(document_id: int):
    if document_id not in _DOCS:
        raise HTTPException(status_code=404, detail="document not found")
    removed = _DOCS.pop(document_id)
    return RagDocument(**removed)


@app.post("/api/v1/rag/retrieve", response_model=RagRetrievalResult)
def retrieve(req: RetrieveRequest):
    # 端到端契约成功路径（确定性占位）：用于 python-rag-contract 断言补全。
    # 仅当 RAG_ENGINE_READY=1 时返回，验证 Java HttpRagProvider 期望的 200 成功结构；
    # 默认（未置位）仍返回 503 降级，保持与 Java 端 RAG_ENABLED=true 时引擎不可达回退纯 LLM 一致。
    if os.getenv("RAG_ENGINE_READY", "0") == "1":
        return RagRetrievalResult(
            results=[
                RagSource(document_id=1, document_name="TCP 三次握手",
                          relevance_score=0.92, snippet="SYN/ACK 建立连接"),
                RagSource(document_id=2, document_name="OSI 七层模型",
                          relevance_score=0.81, snippet="应用层到物理层"),
            ],
            mode="ENGINE",
            code=200,
        )
    # 占位骨架：真实 RAG 引擎（BM25/向量/embedding/融合）未接入，按手册 §34 降级语义
    # 返回 code=503 / mode=UNAVAILABLE；待 Python 端接入真实引擎后替换此处逻辑。
    return RagRetrievalResult(
        results=[],
        mode="UNAVAILABLE",
        error=MSG_RAG_UNAVAILABLE,
        code=503,
    )


@app.post("/api/v1/rag/ask", response_model=RagQueryResult)
def ask(req: AskRequest):
    # 端到端契约成功路径（确定性占位）：用于 python-rag-contract 断言补全。
    # 仅当 RAG_ENGINE_READY=1 时返回，验证 Java HttpRagProvider 期望的 200 成功结构；
    # 默认（未置位）仍返回 503 降级，保持与 Java 端 RAG_ENABLED=false / 引擎不可达时回退纯 LLM 一致。
    if os.getenv("RAG_ENGINE_READY", "0") == "1":
        return RagQueryResult(
            answer="TCP 是面向连接的传输层协议，通过三次握手建立可靠连接。",
            sources=[
                RagSource(document_id=1, document_name="TCP 三次握手",
                          relevance_score=0.92, snippet="SYN/ACK 建立连接"),
                RagSource(document_id=2, document_name="OSI 七层模型",
                          relevance_score=0.81, snippet="应用层到物理层"),
            ],
            mode="ENGINE",
            code=200,
        )
    # 占位骨架：真实 RAG 引擎未接入，按手册 §34 降级语义返回 code=503 / mode=UNAVAILABLE；
    # 与 Java 端 RAG_ENABLED=false 时整体返回 503 一致。待 Python 端接入真实引擎后替换此处逻辑。
    return RagQueryResult(
        answer=None,
        sources=[],
        mode="UNAVAILABLE",
        error=MSG_RAG_UNAVAILABLE,
        code=503,
    )


@app.post("/api/v1/rag/index/build/{course_id}", response_model=IndexResult)
def build_index(course_id: int):
    chunks = sum(1 for d in _DOCS.values() if d["course_id"] == course_id)
    return IndexResult(success=True, indexed_chunks=chunks)


@app.delete("/api/v1/rag/index/{course_id}", response_model=IndexResult)
def clear_index(course_id: int):
    # 占位：契约补全（手册 §32 列出 DELETE /api/v1/rag/index/{courseId} 清理索引）。
    # 真实引擎应清理该课程的向量索引；占位仅返回契约形状，不报错。
    return IndexResult(success=True, indexed_chunks=0)
