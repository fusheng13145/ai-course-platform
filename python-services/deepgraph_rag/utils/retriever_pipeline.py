"""混合检索流水线（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

`retrieve_documents(query, ollama_api_url, model, chat_history="", flags=None, pipeline=None)`

四段式召回，对应申报书「向量化 → 语义召回 → 图查询 → 生成融合」的前半段：

1. **HyDE（可选）**：让生成模型先写一段假设答案，用它的向量去检索，缓解
   "提问短、文档长"的语义错配；模型不可用时直接用原问题，绝不吞异常。
2. **混合召回**：BM25（关键词）+ FAISS（向量）双路，权重 0.4 / 0.6。
3. **图召回**：`retrieve_from_graph` 按实体重合度补关系文本。
4. **重排（可选）**：CrossEncoder 打分后重排；未加载模型时保持召回顺序。

开关与上下文条数由调用方以 `flags` 传入（来自 `runtime_state.STATE`），
本模块不读任何全局会话状态，也不 import Streamlit。
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

import requests

from utils.build_graph import retrieve_from_graph

_HYDE_PROMPT = (
    "请针对下面的问题写一段 120 字以内的专业性回答草稿，用于检索相似资料，"
    "只输出草稿正文。\n\n问题：{query}"
)


def _state(flags: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if flags is not None:
        return flags
    import runtime_state

    return runtime_state.STATE


def _hyde(query: str, ollama_api_url: str, model: str) -> str:
    try:
        response = requests.post(
            ollama_api_url,
            json={
                "model": model,
                "prompt": _HYDE_PROMPT.format(query=query),
                "stream": False,
                "options": {"temperature": 0.3},
            },
            timeout=20,
        )
        response.raise_for_status()
        draft = str((response.json() or {}).get("response") or "").strip()
        return draft or query
    except Exception as exc:
        print(f"[deepgraph] HyDE 生成失败，改用原问题: {type(exc).__name__}: {exc}")
        return query


def _dedupe(docs: List[Any]) -> List[Any]:
    seen: List[str] = []
    out: List[Any] = []
    for doc in docs:
        content = (getattr(doc, "page_content", "") or "").strip()
        if not content or content in seen:
            continue
        seen.append(content)
        out.append(doc)
    return out


def _rerank(query: str, docs: List[Any], reranker: Any) -> List[Any]:
    if not docs or reranker is None:
        return docs
    try:
        pairs = [[query, getattr(doc, "page_content", "") or ""] for doc in docs]
        scores = reranker.predict(pairs)
        ranked = sorted(zip(docs, scores), key=lambda item: -float(item[1]))
        return [doc for doc, _score in ranked]
    except Exception as exc:
        print(f"[deepgraph] 重排失败，保持召回顺序: {type(exc).__name__}: {exc}")
        return docs


def retrieve_documents(
    query: str,
    ollama_api_url: str = "",
    model: str = "",
    chat_history: str = "",
    flags: Optional[Dict[str, Any]] = None,
    pipeline: Optional[Dict[str, Any]] = None,
) -> List[Any]:
    state = _state(flags)
    active = pipeline if pipeline is not None else state.get("retrieval_pipeline")
    if not isinstance(active, dict):
        return []
    try:
        top_k = max(1, min(int(state.get("max_contexts", 3) or 3), 10))
    except Exception:
        top_k = 3

    search_query = query
    if state.get("enable_hyde") and ollama_api_url:
        search_query = _hyde(query, ollama_api_url, model)

    docs: List[Any] = []
    ensemble = active.get("ensemble")
    if ensemble is not None:
        try:
            docs.extend(list(ensemble.invoke(search_query)))
        except Exception as exc:
            print(f"[deepgraph] 混合召回失败: {type(exc).__name__}: {exc}")

    if state.get("enable_graph_rag"):
        graph_docs = retrieve_from_graph(query, active.get("knowledge_graph"), top_k=top_k)
        docs.extend(graph_docs)

    docs = _dedupe(docs)
    if state.get("enable_reranking") and active.get("reranker") is not None:
        docs = _rerank(query, docs, active.get("reranker"))
    return docs[: max(top_k, 3)]
