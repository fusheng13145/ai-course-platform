"""
RAG 真实检索引擎（混合召回：BM25 + 向量，RRF 融合）
====================================================
为 rag_service.py 的契约端点提供真实实现。依赖策略：

- 重依赖（jieba / rank_bm25 / sentence-transformers / faiss / numpy）**全部懒加载**，
  缺失时 `engine_available()` 返回 False，rag_service 仍按契约返回降级/占位响应——
  这样 CI（只装 fastapi/pytest）无需下载模型即可保持全绿。
- 存储为文件持久化（JSON + numpy + faiss），目录由 `RAG_DATA_DIR` 指定，
  默认 `python-services/rag_service/data/`（已 gitignore）。测试可用该变量隔离。

环境变量：
  RAG_DATA_DIR        数据目录（默认 ./data）
  RAG_EMBED_MODEL     向量模型（默认 BAAI/bge-small-zh-v1.5，走 HF_ENDPOINT 镜像）
  RAG_CHUNK_SIZE      分块字符数（默认 300）
  RAG_CHUNK_OVERLAP   块重叠字符（默认 50）
  DEEPSEEK_API_KEY    生成模型密钥（缺省时 /ask 返回抽取式回答，不调外部 API）
  DEEPSEEK_MODEL      生成模型名（默认 deepseek-chat）
"""
from __future__ import annotations

import json
import os
import re
import threading
from pathlib import Path
from typing import Optional

ENGINE_VERSION = "1.0"

_LOCK = threading.RLock()

# 可选依赖探测（懒加载 + 可用性标记）
_DEP_ERRORS: list[str] = []


def _record_dep_error(name: str, exc: Exception) -> None:
    _DEP_ERRORS.append(f"{name}: {exc}")


try:
    import numpy as _np
except Exception as e:  # pragma: no cover - 环境相关
    _np = None
    _record_dep_error("numpy", e)

try:
    import jieba as _jieba
except Exception as e:  # pragma: no cover
    _jieba = None
    _record_dep_error("jieba", e)

try:
    from rank_bm25 import BM25Okapi as _BM25Okapi
except Exception as e:  # pragma: no cover
    _BM25Okapi = None
    _record_dep_error("rank_bm25", e)

try:
    import faiss as _faiss
except Exception as e:  # pragma: no cover
    _faiss = None
    _record_dep_error("faiss", e)

try:
    from sentence_transformers import SentenceTransformer as _SentenceTransformer
except Exception as e:  # pragma: no cover
    _SentenceTransformer = None
    _record_dep_error("sentence-transformers", e)


def engine_available() -> bool:
    """混合检索所需的最低依赖集合是否齐备（不含网络/模型下载）。"""
    return all([_np, _jieba, _BM25Okapi, _faiss, _SentenceTransformer])


def dep_errors() -> list[str]:
    return list(_DEP_ERRORS)


# ---------------------------------------------------------------- 存储目录

def data_dir() -> Path:
    default = Path(__file__).resolve().parent / "data"
    return Path(os.getenv("RAG_DATA_DIR") or default)


def _course_dir(course_id: int) -> Path:
    return data_dir() / f"course_{course_id}"


def _docs_path() -> Path:
    return data_dir() / "documents.json"


# ---------------------------------------------------------------- 文档存储

# 全局文档表： {id: {id, course_id, document_name, content}}
_DOCS: dict[int, dict] = {}
_NEXT_ID = 1
_loaded = False

# 已建索引标记： {course_id: {"indexed_chunks": n, "doc_ids": [...], "built_at": ts}}
_INDEX_META: dict[int, dict] = {}


def _load_store() -> None:
    global _NEXT_ID, _loaded
    if _loaded:
        return
    path = _docs_path()
    if path.exists():
        payload = json.loads(path.read_text(encoding="utf-8"))
        _DOCS.clear()
        _DOCS.update({int(k): v for k, v in payload.get("documents", {}).items()})
        _INDEX_META.clear()
        _INDEX_META.update({int(k): v for k, v in payload.get("indexes", {}).items()})
        _NEXT_ID = payload.get("next_id", max(_DOCS, default=0) + 1)
    _loaded = True


def _save_store() -> None:
    path = _docs_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "version": ENGINE_VERSION,
                "next_id": _NEXT_ID,
                "documents": {str(k): v for k, v in _DOCS.items()},
                "indexes": {str(k): v for k, v in _INDEX_META.items()},
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def reset_store() -> None:
    """清空进程内存储并重建（契约测试隔离用）。"""
    global _NEXT_ID, _loaded
    with _LOCK:
        _DOCS.clear()
        _INDEX_META.clear()
        _model_holder["model"] = None
        _NEXT_ID = 1
        _loaded = False


def add_document(course_id: int, document_name: str, content: str) -> dict:
    global _NEXT_ID
    with _LOCK:
        _load_store()
        doc_id = _NEXT_ID
        _NEXT_ID += 1
        doc = {
            "id": doc_id,
            "course_id": course_id,
            "document_name": document_name,
            "content": content,
        }
        _DOCS[doc_id] = doc
        _save_store()
        return dict(doc)


def get_document(document_id: int) -> Optional[dict]:
    with _LOCK:
        _load_store()
        doc = _DOCS.get(document_id)
        return dict(doc) if doc else None


def update_document(document_id: int, content: str) -> Optional[dict]:
    with _LOCK:
        _load_store()
        doc = _DOCS.get(document_id)
        if not doc:
            return None
        doc["content"] = content
        _save_store()
        return dict(doc)


def delete_document(document_id: int) -> Optional[dict]:
    with _LOCK:
        _load_store()
        doc = _DOCS.pop(document_id, None)
        if not doc:
            return None
        _invalidate_course_index(doc["course_id"])
        _save_store()
        return dict(doc)


def list_documents(course_id: Optional[int] = None) -> list[dict]:
    with _LOCK:
        _load_store()
        docs = [dict(d) for d in _DOCS.values()]
    if course_id is not None:
        docs = [d for d in docs if d["course_id"] == course_id]
    return sorted(docs, key=lambda d: d["id"])


def index_status(course_id: int) -> Optional[dict]:
    with _LOCK:
        _load_store()
        meta = _INDEX_META.get(course_id)
        return dict(meta) if meta else None


def _invalidate_course_index(course_id: int) -> None:
    """文档变更后该课程索引视为过期（检索时回退降级语义，需重新 build）。"""
    meta = _INDEX_META.pop(course_id, None)
    if meta:
        cdir = _course_dir(course_id)
        for f in cdir.glob("index.*"):
            try:
                f.unlink()
            except OSError:
                pass


# ---------------------------------------------------------------- 分块与分词

_SENT_SPLIT = re.compile(r"(?<=[。！？；!?;\n])")


def chunk_text(text: str, chunk_size: int, overlap: int) -> list[str]:
    """按句子边界聚合的定长滑窗分块，兼容中英文标点。"""
    chunk_size = max(50, chunk_size)
    overlap = max(0, min(overlap, chunk_size - 10))
    sentences = [s for s in _SENT_SPLIT.split(text) if s and s.strip()]
    if not sentences:
        return [text.strip()] if text.strip() else []

    chunks: list[str] = []
    buf = ""
    for sent in sentences:
        if len(buf) + len(sent) <= chunk_size:
            buf += sent
        else:
            if buf:
                chunks.append(buf.strip())
            # 单句超长时硬切
            while len(sent) > chunk_size:
                chunks.append(sent[:chunk_size].strip())
                sent = sent[chunk_size - overlap:]
            buf = buf[overlap:] + sent if buf and overlap else sent
    if buf.strip():
        chunks.append(buf.strip())
    return [c for c in chunks if c]


def tokenize(text: str) -> list[str]:
    if _jieba is None:  # pragma: no cover - 依赖缺失时不参与 BM25
        return text.lower().split()
    return [t.strip().lower() for t in _jieba.cut_for_search(text) if t.strip()]


# ---------------------------------------------------------------- 向量模型

_model_holder: dict = {"model": None}


def _get_model():
    if _model_holder["model"] is None:
        model_name = os.getenv("RAG_EMBED_MODEL", "BAAI/bge-small-zh-v1.5")
        _model_holder["model"] = _SentenceTransformer(model_name)
    return _model_holder["model"]


def _embed(texts: list[str]):
    model = _get_model()
    vecs = model.encode(
        texts,
        normalize_embeddings=True,
        show_progress_bar=False,
        batch_size=16,
    )
    return _np.asarray(vecs, dtype="float32")


# ---------------------------------------------------------------- 索引构建 / 检索

def build_course_index(course_id: int) -> dict:
    """为该课程全部文档建混合索引（BM25 + FAISS 向量），落盘持久化。

    返回 {success, indexed_chunks, error?}。依赖缺失时 success=False，
    调用方（rag_service）按契约降级，不抛异常。
    """
    if not engine_available():
        return {
            "success": False,
            "indexed_chunks": 0,
            "error": "引擎依赖未安装：" + "; ".join(dep_errors())
            + "。请 pip install -r requirements-engine.txt 后重启服务。",
        }
    docs = list_documents(course_id)
    if not docs:
        return {"success": False, "indexed_chunks": 0,
                "error": f"课程 {course_id} 尚无文档，无法建索引"}

    chunk_size = int(os.getenv("RAG_CHUNK_SIZE", "300"))
    overlap = int(os.getenv("RAG_CHUNK_OVERLAP", "50"))

    chunks: list[dict] = []
    for doc in docs:
        for i, piece in enumerate(chunk_text(doc["content"], chunk_size, overlap)):
            chunks.append({
                "doc_id": doc["id"],
                "doc_name": doc["document_name"],
                "chunk_no": i,
                "text": piece,
            })
    if not chunks:
        return {"success": False, "indexed_chunks": 0, "error": "文档内容为空，无有效分块"}

    with _LOCK:
        vectorized = _embed([c["text"] for c in chunks])  # 一次批量编码

        index = _faiss.IndexFlatIP(vectorized.shape[1])
        index.add(vectorized)

        cdir = _course_dir(course_id)
        cdir.mkdir(parents=True, exist_ok=True)
        # faiss 的 C++ 文件 API 在 Windows 非 ASCII 路径下不可用（本仓库目录含中文），
        # 故用内存序列化自行落盘。
        (cdir / "index.faiss").write_bytes(
            _np.ascontiguousarray(_faiss.serialize_index(index)).tobytes()
        )
        (cdir / "chunks.json").write_text(
            json.dumps(chunks, ensure_ascii=False), encoding="utf-8"
        )
        _INDEX_META[course_id] = {
            "indexed_chunks": len(chunks),
            "doc_ids": [d["id"] for d in docs],
            "embed_model": os.getenv("RAG_EMBED_MODEL", "BAAI/bge-small-zh-v1.5"),
        }
        _save_store()
    return {"success": True, "indexed_chunks": len(chunks)}


def _load_course_chunks(course_id: int) -> Optional[list[dict]]:
    path = _course_dir(course_id) / "chunks.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def clear_course_index(course_id: int) -> None:
    with _LOCK:
        _load_store()
        _INDEX_META.pop(course_id, None)
        cdir = _course_dir(course_id)
        if cdir.exists():
            for f in cdir.glob("index.*"):
                f.unlink(missing_ok=True)
            (cdir / "chunks.json").unlink(missing_ok=True)
        _save_store()


def search(query: str, course_id: Optional[int], top_k: int) -> Optional[list[dict]]:
    """混合检索。课程无有效索引时返回 None（调用方决定降级语义）。

    返回 [{"document_id","document_name","relevance_score","snippet"}...]，
    分数为 RRF 融合后的归一化相对分（0~1）。
    """
    if not engine_available() or course_id is None:
        return None
    with _LOCK:
        _load_store()
        meta = _INDEX_META.get(course_id)
    if not meta:
        return None
    chunks = _load_course_chunks(course_id)
    if not chunks:
        return None

    top_k = max(1, min(top_k or 5, 50))
    pool = max(top_k * 4, 20)

    # --- BM25 支路 ---
    bm25_scores: list[tuple[int, float]] = []
    corpus_tokens = [tokenize(c["text"]) for c in chunks]
    if any(corpus_tokens):
        bm25 = _BM25Okapi(corpus_tokens)
        scores = bm25.get_scores(tokenize(query))
        ranked = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)[:pool]
        bm25_scores = [(i, float(scores[i])) for i in ranked if scores[i] > 0]

    # --- 向量支路 ---
    vec_scores: list[tuple[int, float]] = []
    index_path = _course_dir(course_id) / "index.faiss"
    if index_path.exists():
        raw = _np.frombuffer(index_path.read_bytes(), dtype=_np.uint8)
        index = _faiss.deserialize_index(raw)
        qv = _embed([query])
        D, I = index.search(qv, min(pool, index.ntotal))
        vec_scores = [(int(i), float(d)) for d, i in zip(D[0], I[0]) if i >= 0]

    if not bm25_scores and not vec_scores:
        return []

    # --- RRF 融合 ---
    k0 = 60.0
    fused: dict[int, float] = {}
    for rank, (i, _) in enumerate(bm25_scores):
        fused[i] = fused.get(i, 0.0) + 1.0 / (k0 + rank + 1)
    for rank, (i, _) in enumerate(vec_scores):
        fused[i] = fused.get(i, 0.0) + 1.0 / (k0 + rank + 1)

    ordered = sorted(fused.items(), key=lambda kv: kv[1], reverse=True)[:top_k]
    if not ordered:
        return []
    max_rrf = ordered[0][1] or 1.0

    results = []
    for i, rrf in ordered:
        c = chunks[i]
        results.append({
            "document_id": c["doc_id"],
            "document_name": c["doc_name"],
            "relevance_score": round(min(1.0, rrf / max_rrf), 4),
            "snippet": c["text"][:500],
        })
    return results


# ---------------------------------------------------------------- LLM 生成

def generate_answer(question: str, contexts: list[dict]) -> tuple[str, str]:
    """基于检索上下文生成回答。返回 (answer, mode_tag)。

    mode_tag: "LLM"（DeepSeek 生成）| "EXTRACTIVE"（无密钥/调用失败时抽取式回答）。
    """
    joined = "\n\n".join(
        f"[资料{i + 1}] {r['document_name']}：{r['snippet']}"
        for i, r in enumerate(contexts)
    ) or "（无检索资料）"

    api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
    if api_key:
        try:
            answer = _call_deepseek(api_key, question, joined)
            if answer:
                return answer, "LLM"
        except Exception:
            pass  # 网络/配额失败 -> 抽取式兜底，不阻断契约响应

    # 抽取式兜底：取最相关块的首句拼接
    picks = [r["snippet"].strip() for r in contexts[:3] if r["snippet"].strip()]
    answer = "根据课程资料：" + "；".join(p[:120] for p in picks) if picks else "未检索到相关课程资料。"
    return answer, "EXTRACTIVE"


def _call_deepseek(api_key: str, question: str, contexts: str) -> Optional[str]:
    import httpx

    model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": (
                "你是一个智能教学助手。请严格依据给定课程资料回答学生问题；"
                "资料不足时明确说明，不要编造。回答使用简体中文，简明、有条理。"
            )},
            {"role": "user", "content": f"课程资料：\n{contexts}\n\n问题：{question}"},
        ],
        "temperature": 0.3,
        "max_tokens": 1024,
    }
    resp = httpx.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {api_key}"},
        json=payload,
        timeout=60.0,
    )
    resp.raise_for_status()
    data = resp.json()
    return data["choices"][0]["message"]["content"].strip() or None
