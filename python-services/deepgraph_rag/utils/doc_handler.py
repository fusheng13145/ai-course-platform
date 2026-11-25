"""文档接入流水线（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

`process_documents(files, reranker, embeddings_model, ollama_base_url,
 build_hierarchical, build_global, build_chapter)` 是平台入库入口的唯一实现，
语义与平台契约一致：

1. 解析（PDF / DOCX / TXT）→ 2. 切块 → 3. 向量化 + FAISS 落盘 →
4. 章节切分 → 5. 层次大纲 → 6. 全局图谱 → 7. 混合检索器装配

返回 `(pipeline, chapters_by_file)`：

* `pipeline`：`{ensemble, knowledge_graph, reranker, doc_names, chunks}`
* `chapters_by_file`：`{文档名: {章节名: 章节正文}}`

设计取舍：

* **单文件失败不掩盖**：解析失败直接抛出带文件名的异常，由接口转成明确的
  降级信息；不做"跳过并假装成功"。
* **写入顺序先数据后索引**：原文/章节落盘成功后才登记 metadata，避免出现
  "列表里有、磁盘上没有"的幽灵文档。
"""

from __future__ import annotations

import io
import json
import os
import re
from typing import Any, Dict, List, Optional, Tuple

import networkx as nx

import config
from utils import persistence
from utils.build_graph import CHAPTER_RE, build_global_knowledge_graph, build_hierarchical_graph

_GRAPH_DATA_DIR = os.getenv("DG_GRAPH_DIR", "graph_data")
_CHUNK_SIZE = int(os.getenv("DG_CHUNK_SIZE", "1000") or 1000)
_CHUNK_OVERLAP = int(os.getenv("DG_CHUNK_OVERLAP", "150") or 150)


def _decode(raw: bytes) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gb18030", "latin-1"):
        try:
            return raw.decode(encoding)
        except Exception:
            continue
    return raw.decode("utf-8", errors="ignore")


def _load_text(file_name: str, data: bytes) -> str:
    suffix = os.path.splitext(file_name or "")[1].lower()
    if suffix == ".pdf":
        try:
            from pypdf import PdfReader
        except Exception as exc:  # pragma: no cover - 依赖缺失路径
            raise RuntimeError(f"缺少 PDF 解析依赖 pypdf，无法处理「{file_name}」: {exc}") from exc
        reader = PdfReader(io.BytesIO(data))
        pages = [(page.extract_text() or "") for page in reader.pages]
        return "\n\n".join(p for p in pages if p.strip())
    if suffix == ".docx":
        try:
            import docx2txt
        except Exception as exc:  # pragma: no cover
            raise RuntimeError(f"缺少 DOCX 解析依赖 docx2txt，无法处理「{file_name}」: {exc}") from exc
        return docx2txt.process(io.BytesIO(data)) or ""
    return _decode(data)


def _split_chunks(text: str, file_name: str) -> List[Any]:
    try:
        from langchain_text_splitters import RecursiveCharacterTextSplitter
    except Exception:
        from langchain.text_splitter import RecursiveCharacterTextSplitter  # type: ignore[no-redef]

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=_CHUNK_SIZE,
        chunk_overlap=_CHUNK_OVERLAP,
        separators=["\n\n", "\n", "。", "；", " ", ""],
    )
    pieces = splitter.split_text(text or "")
    try:
        from langchain_core.documents import Document

        return [Document(page_content=piece, metadata={"source": file_name}) for piece in pieces if piece.strip()]
    except Exception:
        return [piece for piece in pieces if piece.strip()]


def _split_chapters(text: str) -> Dict[str, str]:
    """按「第X章」切章；没有章标题时退化为单章「全文」，保证章节端点有数据。"""
    text = text or ""
    matches = list(CHAPTER_RE.finditer(text))
    if not matches:
        return {"全文": text} if text.strip() else {}
    chapters: Dict[str, str] = {}
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        title = f"第{match.group(1)}章 {match.group(2)}".strip()
        body = text[start:end].strip()
        if len(body) < 20:
            continue
        chapters.setdefault(title, body)
    return chapters or ({"全文": text} if text.strip() else {})


def _embeddings(embeddings_model: str, ollama_base_url: str) -> Any:
    try:
        from langchain_ollama import OllamaEmbeddings
    except Exception as exc:  # pragma: no cover
        raise RuntimeError(f"缺少嵌入依赖 langchain-ollama: {exc}") from exc
    return OllamaEmbeddings(model=embeddings_model, base_url=ollama_base_url)


def _save_hierarchy(tree: Dict[str, Any], doc_name: str) -> Optional[str]:
    try:
        os.makedirs(_GRAPH_DATA_DIR, exist_ok=True)
        path = os.path.join(_GRAPH_DATA_DIR, f"{doc_name}_hierarchical.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(tree, f, ensure_ascii=False)
        return path
    except Exception as exc:
        print(f"[deepgraph] 层次大纲存档失败（继续）: {exc}")
        return None


def _sync_neo4j(graph: Any, doc_name: str) -> None:
    from neo4j_handler import neo4j_db

    try:
        if not config.neo4j_configured():
            return
        neo4j_db.clear_source_data(doc_name)
        neo4j_db.insert_triplets_from_graph(graph, doc_name, graph_type="global")
    except Exception as exc:
        print(f"[deepgraph] Neo4j 同步失败（不影响本地图谱）: {exc}")


def process_documents(
    files: List[Any],
    reranker: Any = None,
    embeddings_model: str = "",
    ollama_base_url: str = "",
    build_hierarchical: bool = False,
    build_global: bool = False,
    build_chapter: bool = True,
) -> Tuple[Optional[Dict[str, Any]], Dict[str, Dict[str, str]]]:
    from langchain_community.retrievers import BM25Retriever
    from langchain_community.vectorstores import FAISS
    from langchain_classic.retrievers import EnsembleRetriever

    embedder = _embeddings(embeddings_model, ollama_base_url)
    merged_faiss: Optional[Any] = None
    merged_kg = nx.Graph()
    all_chunks: List[Any] = []
    doc_names: List[str] = []
    chapters_by_file: Dict[str, Dict[str, str]] = {}

    for uploaded in files or []:
        file_name = getattr(uploaded, "name", "") or "document.txt"
        raw = uploaded.getvalue() if hasattr(uploaded, "getvalue") else b""
        if not raw:
            raise RuntimeError(f"文件「{file_name}」内容为空")
        text = _load_text(file_name, raw)
        if not text.strip():
            raise RuntimeError(f"文件「{file_name}」未解析出任何文本（扫描版 PDF 需先做 OCR）")

        chunks = _split_chunks(text, file_name)
        if not chunks:
            raise RuntimeError(f"文件「{file_name}」切块失败（正文过短或编码异常）")

        try:
            faiss_db = FAISS.from_documents(chunks, embedder)
        except Exception as exc:
            raise RuntimeError(
                f"文件「{file_name}」向量化失败（请确认 Ollama 已启动并已拉取 "
                f"{embeddings_model}）: {type(exc).__name__}: {exc}"
            ) from exc

        persistence.save_document_text(file_name, text)
        persistence.save_faiss_index(file_name, faiss_db)
        all_chunks.extend(chunks)
        doc_names.append(file_name)
        merged_faiss = faiss_db if merged_faiss is None else merged_faiss

        chapters: Dict[str, str] = _split_chapters(text) if build_chapter else {}
        if chapters:
            persistence.save_chapters(file_name, chapters)
        chapters_by_file[file_name] = chapters

        if build_hierarchical:
            _save_hierarchy(build_hierarchical_graph(text, file_name), file_name)

        if build_global:
            kg = build_global_knowledge_graph(text, file_name)
            if kg.number_of_edges() > 0:
                persistence.save_global_graph(file_name, kg)
                merged_kg = nx.compose(merged_kg, kg)
                _sync_neo4j(kg, file_name)

    if merged_faiss is None or not all_chunks:
        return None, chapters_by_file

    bm25 = BM25Retriever.from_documents(all_chunks)
    bm25.k = 5
    ensemble = EnsembleRetriever(
        retrievers=[bm25, merged_faiss.as_retriever(search_kwargs={"k": 5})],
        weights=[0.4, 0.6],
    )
    pipeline: Dict[str, Any] = {
        "ensemble": ensemble,
        "knowledge_graph": merged_kg if merged_kg.number_of_edges() > 0 else None,
        "reranker": reranker,
        "doc_names": doc_names,
        "chunks": all_chunks,
    }
    return pipeline, chapters_by_file
