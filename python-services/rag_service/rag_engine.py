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
  AI_PROVIDER         生成后端：deepseek（默认，等同旧行为）/ ollama（本地 Ollama）
  OLLAMA_BASE_URL     Ollama 地址（默认 http://localhost:11434）
  OLLAMA_MODEL        Ollama 模型名（默认 deepseek-r1:8b）
  OLLAMA_THINK        留空=模型模板决定；true/false=显式开关；思考内容不进正式答案
"""
from __future__ import annotations

import json
import logging
import os
import re
import threading
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

ENGINE_VERSION = "1.0"

_LOCK = threading.RLock()


class StoreCorruptedError(RuntimeError):
    """文档存储文件损坏且无法解析时抛出。

    保留"引擎故障"的显式语义：调用方（检索/健康检查）不应把它当作"无结果"静默处理，
    而应向上报错或按契约降级，避免损坏后检索悄悄返回空。
    """

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


def embed_model_cached(model_name: Optional[str] = None) -> bool:
    """嵌入模型是否已本地缓存（**不触发联网**）。

    语义：仅检查本地 HF 缓存是否已有该模型的实质文件，绝不发起网络请求。
    用途：
      1) `_get_model` 命中缓存时以 `local_files_only=True` 离线加载——否则
         `SentenceTransformer` 即便文件已缓存也会向 HF 发连通性探测请求，
         离线 / CI 环境会因此长时间挂起；
      2) 真实引擎测试门禁：模型未缓存则跳过而非联网挂起。
    """
    if not engine_available():
        return False
    model_name = model_name or os.getenv("RAG_EMBED_MODEL", "BAAI/bge-small-zh-v1.5")
    try:
        from huggingface_hub import try_to_load_from_cache
        # config_sentence_transformers.json 是 sentence-transformers 加载任一模型
        # 都会落盘的标记文件，命中即代表该模型已被完整缓存。
        return try_to_load_from_cache(
            model_name, "config_sentence_transformers.json") is not None
    except Exception:
        # huggingface_hub 不可用 / 探测异常：回退到文件系统检查（同样免联网）。
        norm = model_name.replace("/", "--")
        for base in (
            os.path.join(os.getenv("HF_HUB_CACHE", ""), "hub") if os.getenv("HF_HUB_CACHE") else "",
            os.path.join(os.getenv("HF_HOME", os.path.expanduser("~/.cache/huggingface")), "hub"),
        ):
            cache = Path(base) / f"models--{norm}" / "snapshots"
            if cache.exists() and any(p.suffix in {".safetensors", ".bin", ".json"}
                                      for p in cache.rglob("*") if p.is_file()):
                return True
        return False


def engine_functional() -> bool:
    """引擎是否**真实可用**：重依赖齐备 **且** 嵌入模型已本地缓存（可离线加载）。

    区别于 `engine_available()`（仅检查 import 层依赖齐备）：本函数 additionally
    要求嵌入模型已缓存，使真实引擎用例既能在无网环境安全运行（`local_files_only`
    加载），也能在模型未缓存时被 `requires_engine` 收敛跳过，而非联网挂起。
    """
    return engine_available() and embed_model_cached()


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
    """载入全局文档表。

    损坏恢复语义：文件存在但解析失败时，**不静默清空**——先把损坏文件另存为
    `.corrupt` 以便排查，再抛出 `StoreCorruptedError`，让上层（检索/健康检查）显式
    感知引擎故障，而不是返回空结果伪装成功。正常载入后才置 `_loaded`。
    """
    global _NEXT_ID, _loaded
    if _loaded:
        return
    path = _docs_path()
    if path.exists():
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, ValueError, OSError) as exc:
            quarantined = _quarantine_corrupt(path)
            raise StoreCorruptedError(
                f"文档存储损坏（{exc}），已隔离至 {quarantined.name}"
            ) from exc
        _DOCS.clear()
        _DOCS.update({int(k): v for k, v in payload.get("documents", {}).items()})
        _INDEX_META.clear()
        _INDEX_META.update({int(k): v for k, v in payload.get("indexes", {}).items()})
        _NEXT_ID = payload.get("next_id", max(_DOCS, default=0) + 1)
    _loaded = True


def _quarantine_corrupt(path: Path) -> Path:
    """把损坏的存储文件改名保留（加时间戳），供事后排查而非直接删除。"""
    from datetime import datetime

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    target = path.with_name(f"{path.name}.corrupt.{stamp}")
    try:
        os.replace(path, target)
    except OSError:
        # 改名失败（例如同秒冲突）时退回带随机后缀，确保原文件不被后续写入覆盖
        target = path.with_name(f"{path.name}.corrupt.{stamp}.{os.getpid()}")
        try:
            os.replace(path, target)
        except OSError:
            pass
    return target


def _atomic_write_text(path: Path, text: str) -> None:
    """先写临时文件再 os.replace 原子替换，避免进程崩溃留下半截 JSON。"""
    _atomic_write_bytes(path, text.encode("utf-8"))


def _atomic_write_bytes(path: Path, payload: bytes) -> None:
    """字节级原子替换落盘（先写临时文件、flush/fsync 再 os.replace）。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.tmp")
    try:
        with open(tmp, "wb") as fh:
            fh.write(payload)
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(tmp, path)
    finally:
        if tmp.exists():
            try:
                tmp.unlink()
            except OSError:
                pass


def _save_store() -> None:
    _atomic_write_text(
        _docs_path(),
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
    """文档变更后该课程索引视为过期（检索时回退降级语义，需重新 build）。

    同时删除 `index.*` 与 `chunks.json`，避免旧分块残留被检索复用。
    """
    meta = _INDEX_META.pop(course_id, None)
    if meta:
        cdir = _course_dir(course_id)
        for f in list(cdir.glob("index.*")) + [cdir / "chunks.json"]:
            try:
                f.unlink(missing_ok=True)
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
        load_kwargs: dict = {}
        # 模型已本地缓存时强制 offline 加载：SentenceTransformer 默认即便文件已缓存
        # 也会向 HF 发连通性探测请求，离线 / CI 环境会因此长时间挂起；local_files_only=True
        # 直接读缓存规避该问题。未缓存时保留在线行为，以支持首次部署下载模型。
        if embed_model_cached(model_name):
            load_kwargs["local_files_only"] = True
        _model_holder["model"] = _SentenceTransformer(model_name, **load_kwargs)
    return _model_holder["model"]


def _as_unit_vectors(vecs):
    """把向量矩阵按行 L2 归一化（模型已 normalize，此处兜底并保证幂等）。

    输入可为 1D 或 2D；返回 float32 的 2D 数组。零向量保持为全 0（除零保护）。
    维度不匹配的输入直接抛 ValueError，交由上层按引擎故障处理。
    """
    arr = _np.asarray(vecs, dtype="float32")
    if arr.ndim == 1:
        arr = arr.reshape(1, -1)
    if arr.ndim != 2:
        raise ValueError(f"向量维度非法：期望 1D/2D，实际 ndim={arr.ndim}")
    norms = _np.linalg.norm(arr, axis=1, keepdims=True)
    norms = _np.where(norms == 0, 1.0, norms)
    return arr / norms


def _embed(texts: list[str]):
    model = _get_model()
    vecs = model.encode(
        texts,
        normalize_embeddings=True,
        show_progress_bar=False,
        batch_size=16,
    )
    return _as_unit_vectors(vecs)


# ---------------------------------------------------------------- 索引构建 / 检索

# ---------------------------------------------------------------- 召回与融合（可独立测试）

# RRF 融合常数：与历史实现严格保持一致（原代码 k0=60.0），改此值会影响排名语义。
RRF_K = 60.0


def bm25_rank(corpus_tokens: list[list[str]], query_tokens: list[str],
              pool: int) -> list[tuple[int, float]]:
    """BM25 召回：返回按分数降序、仅保留正分、截断到 pool 的 (chunk_idx, score)。

    空查询 / 纯标点（分词后为空） / 全未知词都返回 []，不抛异常。
    """
    if _BM25Okapi is None or not query_tokens or not corpus_tokens:
        return []
    if not any(corpus_tokens):
        return []
    bm25 = _BM25Okapi(corpus_tokens)
    scores = bm25.get_scores(query_tokens)
    ranked = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)
    return [(i, float(scores[i])) for i in ranked[:pool] if scores[i] > 0]


def vector_rank_from_index(index, query_vec, pool: int) -> list[tuple[int, float]]:
    """在 FAISS IndexFlatIP 上取回 top-`pool`，过滤 -1（无命中）。

    空索引（ntotal==0）返回 []；查询向量维度与索引维度不符抛 ValueError，
    交由调用方按引擎故障处理，而不是静默返回空。
    """
    qv = _np.asarray(query_vec, dtype="float32")
    if qv.ndim == 1:
        qv = qv.reshape(1, -1)
    if index.ntotal == 0:
        return []
    if qv.shape[1] != index.d:
        raise ValueError(f"查询向量维度({qv.shape[1]})与索引维度({index.d})不匹配")
    D, I = index.search(_np.ascontiguousarray(qv), min(pool, index.ntotal))
    return [(int(i), float(d)) for d, i in zip(D[0], I[0]) if i >= 0]


def rrf_fuse(*rank_lists, top_k: int, k: float = RRF_K) -> list[tuple[int, float]]:
    """Reciprocal Rank Fusion：score(d) = Σ_各路 1/(k + rank + 1)，rank 为 0 基排名。

    每个入参是一路的有序命中（元素为 (idx, score) 元组或纯 idx）。返回按融合分降序、
    截断到 top_k 的 (idx, fused_score) 列表。同一元素在多路命中会累加。
    """
    fused: dict[int, float] = {}
    for hits in rank_lists:
        for rank, hit in enumerate(hits):
            idx = hit[0] if isinstance(hit, tuple) else hit
            fused[idx] = fused.get(idx, 0.0) + 1.0 / (k + rank + 1)
    return sorted(fused.items(), key=lambda kv: kv[1], reverse=True)[:top_k]


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
        # 故用内存序列化自行落盘（同样走原子替换，避免崩溃留半截索引）。
        _atomic_write_bytes(
            cdir / "index.faiss",
            _np.ascontiguousarray(_faiss.serialize_index(index)).tobytes(),
        )
        _atomic_write_text(cdir / "chunks.json", json.dumps(chunks, ensure_ascii=False))
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
    corpus_tokens = [tokenize(c["text"]) for c in chunks]
    bm25_scores = bm25_rank(corpus_tokens, tokenize(query), pool)

    # --- 向量支路 ---
    vec_scores: list[tuple[int, float]] = []
    index_path = _course_dir(course_id) / "index.faiss"
    if index_path.exists():
        raw = _np.frombuffer(index_path.read_bytes(), dtype=_np.uint8)
        index = _faiss.deserialize_index(raw)
        qv = _embed([query])
        vec_scores = vector_rank_from_index(index, qv, pool)

    if not bm25_scores and not vec_scores:
        return []

    # --- RRF 融合 ---
    ordered = rrf_fuse(bm25_scores, vec_scores, top_k=top_k)
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

    mode_tag（第三态语义，供调用方区分成功/降级/失败）：
      - "LLM"         DeepSeek 生成成功；
      - "EXTRACTIVE"  无密钥或调用失败，但有检索资料——抽取式兜底；
      - "UNAVAILABLE" LLM 不可用且无检索资料——**可识别的失败**，answer 置空，
                      调用方（rag_service）据此不把它当作成功回答。
    """
    joined = "\n\n".join(
        f"[资料{i + 1}] {r['document_name']}：{r['snippet']}"
        for i, r in enumerate(contexts)
    ) or "（无检索资料）"

    provider = llm_provider()
    if provider == "ollama":
        try:
            answer = _call_ollama(question, joined)
            if answer:
                return answer, "LLM"
            logger.warning("Ollama 未产出最终答案，降级为抽取式回答（model=%s）",
                           os.getenv("OLLAMA_MODEL", "deepseek-r1:8b"))
        except Exception as exc:
            # 与 DeepSeek 同一条降级语义：失败只留痕，不阻断契约响应。
            logger.warning("Ollama 调用失败，降级为抽取式回答：%s: %s",
                           type(exc).__name__, exc)
    else:
        api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
        if api_key:
            try:
                answer = _call_deepseek(api_key, question, joined)
                if answer:
                    return answer, "LLM"
                # 200 但空内容同样是不成功的生成，留痕后走抽取式
                logger.warning("LLM 返回空内容，降级为抽取式回答（model=%s）",
                               os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))
            except Exception as exc:
                # 必须留痕：401（密钥失效）/超时/配额用尽/DNS 失败的处置完全不同，
                # 若一律 `pass`，「真实 LLM 从未成功」这类故障在日志里无从定位。
                # 降级本身不阻断契约响应（抽取式兜底照常返回）。
                logger.warning("LLM 调用失败，降级为抽取式回答：%s: %s",
                               type(exc).__name__, exc)
        else:
            # 未配密钥是预期内的部署形态（Java 侧 RAG_ENABLED=false 亦回退纯 LLM），
            # 记 info 而非 warning，避免与真实故障混成一类。
            logger.info("未配置 DEEPSEEK_API_KEY，直接使用抽取式回答")

    # 抽取式兜底：取最相关块的首句拼接
    picks = [r["snippet"].strip() for r in contexts[:3] if r.get("snippet", "").strip()]
    if picks:
        return "根据课程资料：" + "；".join(p[:120] for p in picks), "EXTRACTIVE"
    # LLM 不可用且无检索资料：返回可识别的失败（空 answer + UNAVAILABLE 标记），
    # 绝不返回看起来像成功的占位字符串。
    return "", "UNAVAILABLE"


def llm_chat_url() -> str:
    """LLM 聊天端点（DR-84）：默认 DeepSeek 官方，可经 `DEEPSEEK_BASE_URL` 指向任意 OpenAI 兼容网关。

    与 Java 侧 `ai.deepseek.base-url` 同一口径、同一默认值。此前这里把 URL 写死，
    于是"网关 Key"在引擎侧必然 401，且现象与"密钥无效"同形——修一半等于没修。
    """
    base = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1").strip().rstrip("/")
    return base + "/chat/completions"


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
        llm_chat_url(),
        headers={"Authorization": f"Bearer {api_key}"},
        json=payload,
        timeout=60.0,
    )
    resp.raise_for_status()
    data = resp.json()
    return data["choices"][0]["message"]["content"].strip() or None


# ---------------------------------------------------------------- Ollama 生成

def llm_provider() -> str:
    """生成后端开关：`AI_PROVIDER` 缺省为 deepseek（与旧行为逐字一致）。

    取值与 Java 侧 `RoutingLLMProvider` 同一口径：ollama／local 都走本地 Ollama，
    其余（含未配置、拼错）一律回落 DeepSeek，避免因一个拼写错误让引擎静默停摆。
    """
    provider = os.getenv("AI_PROVIDER", "deepseek").strip().lower()
    return "ollama" if provider in ("ollama", "local") else "deepseek"


def ollama_chat_url() -> str:
    """Ollama 原生聊天端点（非 OpenAI 兼容路径）。"""
    base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip().rstrip("/")
    return base + "/api/chat"


def _trust_env_for(base_url: str) -> bool:
    """本机回环地址必须绕过系统代理。

    Windows 的系统代理（注册表 ProxyServer）会被 httpx 的 trust_env 读到；若代理不代理
    localhost，发往 127.0.0.1:11434 的请求会得到 502 而不是模型响应（本机实测如此）。
    远程服务（内网 KG／远程 Ollama）仍保留代理支持，交由部署方按 NO_PROXY 处理。
    """
    from urllib.parse import urlparse

    host = (urlparse(base_url.strip()).hostname or "").lower()
    return host not in ("localhost", "127.0.0.1", "::1")


def ollama_trust_env() -> bool:
    return _trust_env_for(os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"))


def _call_ollama(question: str, contexts: str) -> Optional[str]:
    """调用 Ollama `/api/chat`，只取最终答案。

    deepseek-r1 等思考模型会在 `message.thinking` 里返回推理过程；它**不进入正式答案**，
    也不参与空值判定以外的任何拼装——与 Java 侧 `OllamaProvider` 的分离口径一致。
    """
    import httpx

    model = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
    payload = {
        "model": model,
        "stream": False,
        "messages": [
            {"role": "system", "content": (
                "你是一个智能教学助手。请严格依据给定课程资料回答学生问题；"
                "资料不足时明确说明，不要编造。回答使用简体中文，简明、有条理。"
            )},
            {"role": "user", "content": f"课程资料：\n{contexts}\n\n问题：{question}"},
        ],
        "options": {"temperature": 0.3},
    }
    think = os.getenv("OLLAMA_THINK", "").strip().lower()
    if think in ("true", "false"):
        payload["think"] = think == "true"
    resp = httpx.post(ollama_chat_url(), json=payload, timeout=60.0,
                      trust_env=ollama_trust_env())
    resp.raise_for_status()
    message = resp.json().get("message") or {}
    content = (message.get("content") or "").strip()
    if not content:
        thinking = message.get("thinking") or ""
        logger.warning("Ollama 返回空答案（model=%s, thinking=%d 字符）", model, len(thinking))
        return None
    return content


# ---------------------------------------------------------------- GraphRAG 增强

def graph_enabled() -> bool:
    """主路径图谱开关：缺省开启；显式 false/0/no/off 才关闭（与启动器同口径）。"""
    flag = os.getenv("RAG_GRAPH_ENABLED", "true").strip().lower()
    return flag not in ("false", "0", "no", "off")


def graph_sources(course_id: int, question: str, limit: int = 5) -> list:
    """从 KG 取该课程的图证据，返回与检索结果同形的字典列表。

    - 图不可达／超时／无命中：返回空列表且**不抛异常**（检索与生成仍按原路径继续）。
    - 只在问题与节点名／内容有实际命中时才取，避免把整门课的知识点硬塞进上下文。
    - 每个命中节点带出 1 跳关系，构成多跳（节点→关系→邻居）证据，snippet 里显式写出边。
    """
    if not graph_enabled():
        return []
    import httpx

    base = os.getenv("KG_BASE_URL", "http://localhost:8001").strip().rstrip("/")
    try:
        resp = httpx.get(f"{base}/api/v1/graph/course/{course_id}", timeout=2.0,
                         trust_env=_trust_env_for(base))
        resp.raise_for_status()
        nodes = resp.json().get("nodes") or []
        edges = resp.json().get("edges") or []
    except Exception as exc:
        logger.warning("知识图谱不可用，按纯检索继续：%s: %s", type(exc).__name__, exc)
        return []
    if not nodes:
        return []

    tokens = _keywords(question)
    if not tokens:
        return []
    name_by_id = {n.get("id"): n.get("name") or "" for n in nodes}
    scored = []
    for node in nodes:
        haystack = f"{node.get('name') or ''} {node.get('content') or ''}".lower()
        hits = sum(1 for t in tokens if t in haystack)
        if hits:
            scored.append((hits, node))
    scored.sort(key=lambda x: (-x[0], str(x[1].get("name") or "")))

    selected = [n for _, n in scored[:limit]]
    selected_ids = {n.get("id") for n in selected}
    out = []
    for node in selected:
        relations = []
        for edge in edges:
            if edge.get("source_id") == node.get("id") and edge.get("target_id") in name_by_id:
                target = edge.get("target_id")
                relations.append(f"{node.get('name')} -[{edge.get('relation_type')}]-> {name_by_id.get(target)}")
                if target in selected_ids:
                    # 1 跳邻居也命中时补一句反向说明，保证 snippet 自身可读
                    relations.append(f"{name_by_id.get(target)} ← {node.get('name')}")
            elif edge.get("target_id") == node.get("id") and edge.get("source_id") in name_by_id:
                relations.append(f"{name_by_id.get(edge.get('source_id'))} -[{edge.get('relation_type')}]-> {node.get('name')}")
        snippet = (node.get("content") or "").strip()
        if relations:
            snippet = (snippet + "；" if snippet else "") + "图谱关系：" + "；".join(dict.fromkeys(relations))
        if not snippet:
            continue
        out.append({
            "document_id": None,
            "document_name": f"知识图谱：{node.get('name') or ''}",
            "relevance_score": None,
            "snippet": snippet[:500],
        })
    return out


def _keywords(text: str) -> set:
    """极简关键词提取：ASCII 词 + 中文二元组；不引入分词依赖。"""
    import re

    lower = (text or "").lower()
    tokens = {w for w in re.findall(r"[a-z0-9_]+", lower) if len(w) >= 2}
    cjk = re.sub(r"[^\u4e00-\u9fff]", "", lower)
    tokens.update(cjk[i:i + 2] for i in range(max(0, len(cjk) - 1)))
    return tokens


def llm_health() -> dict:
    """健康检查用的生成后端状态：**不改变服务自身 status**。

    Ollama 不可达时只把 `available` 置 false 并给出原因，调用方（rag_service / Java 探针）
    仍看到服务 200、status=ok——生成层降级是既有语义，不等于检索服务下线。
    DeepSeek 侧不发起网络请求：未配密钥是预期部署形态，`available` 表示"能否真生成"。
    """
    provider = llm_provider()
    if provider == "ollama":
        import httpx

        base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip().rstrip("/")
        model = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
        try:
            resp = httpx.get(base + "/api/tags", timeout=1.0,
                             trust_env=ollama_trust_env())
            resp.raise_for_status()
            names = [m.get("name", "") for m in (resp.json().get("models") or [])]
            installed = model in names or f"{model}:latest" in names
            return {
                "provider": "ollama",
                "model": model,
                "available": installed,
                "detail": "ok" if installed else f"model {model} not installed (ollama pull {model})",
            }
        except Exception as exc:
            return {"provider": "ollama", "model": model, "available": False,
                    "detail": f"{type(exc).__name__}: {exc}"}
    return {
        "provider": "deepseek",
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        "available": bool(os.getenv("DEEPSEEK_API_KEY", "").strip()),
        "detail": "ok" if os.getenv("DEEPSEEK_API_KEY", "").strip() else "DEEPSEEK_API_KEY 未配置",
    }
