"""知识图谱构建与图检索（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

提供四个能力，全部服务于「知识抽取 → 图谱 → 检索」这条申报书主线：

* `build_hierarchical_graph(full_text, file_name)` —— 章/节层次大纲树（教材骨架）
* `build_global_knowledge_graph(text, ...)`       —— 文档级概念关系图
* `build_chapter_knowledge_graph(name, text, ...)` —— 章节级三元组抽取
* `retrieve_from_graph(query, G, top_k)`          —— 图内关系召回（供混合检索）

设计取舍（为什么这样实现而不是调模型一把梭）：

1. **模型优先、规则兜底、永不空图**：LLM 抽三元组质量最好但不可依赖；规则抽取
   （句式模板 + 名词短语共现）在模型不可用时保证"有图可看、有边可检索"，
   而不是让整条图谱链路停在 0 边上。
2. **边带溯源**：每条边写 `source_chunks=[{"text": 原句}]`，平台侧并入课程图谱时
   可据此给出证据句（申报书要求的"知识抽取可追溯"）。
3. **切块有上限**：一次抽取最多处理 `MAX_EXTRACT_CHUNKS` 块文本，避免 200 页 PDF
   把接口拖成分钟级；剩余内容靠规则抽取补齐，不静默丢整章。
"""

from __future__ import annotations

import json
import os
import re
from typing import Any, Callable, Dict, Iterable, List, Optional, Tuple

import networkx as nx

import backend

MAX_EXTRACT_CHUNKS = int(os.getenv("DG_MAX_EXTRACT_CHUNKS", "6") or 6)
MAX_EDGES = int(os.getenv("DG_MAX_GRAPH_EDGES", "400") or 400)
_CHUNK_SIZE = 900

_CN_NUM = "一二三四五六七八九十百零〇0-9０-９"
CHAPTER_RE = re.compile(rf"^第([{_CN_NUM}]{{1,4}})章\s*([^\n]{{0,60}})$", re.M)
SECTION_RE = re.compile(rf"^第([{_CN_NUM}]{{1,4}})节\s*([^\n]{{0,60}})$", re.M)
NUMBERED_RE = re.compile(r"^(\d+(?:\.\d+){0,2})[、.．]?\s+([^\n]{2,60})$", re.M)

_SENTENCE_SPLIT = re.compile(r"[。！？；\n]+")
_STOP_WORDS = {
    "我们", "你们", "他们", "它们", "本文", "本章", "本节", "内容", "问题", "方法",
    "系统", "过程", "情况", "时候", "方面", "部分", "可以", "需要", "进行", "通过",
    "一个", "一种", "一些", "这个", "那个", "以及", "因此", "所以", "但是", "其中",
}

# 句式模板：(正则, 方向)  direction="forward" 表示 (组1 -> 组2)，"reverse" 表示 (组2 -> 组1)
_TEMPLATES: List[Tuple[re.Pattern[str], str, str]] = [
    (re.compile(r"(.{2,16}?)(?:包括|包含|分为|由.{0,6}?组成)了?(.{2,40}?)$"), "包含", "forward"),
    (re.compile(r"(.{2,16}?)(?:属于|是.{0,8}?的一种|是一类)(.{2,20}?)$"), "属于", "forward"),
    (re.compile(r"(.{2,16}?)(?:用于|应用于|作用于|实现)(.{2,24}?)$"), "应用于", "reverse"),
    (re.compile(r"(.{2,16}?)(?:导致|引起|造成)(.{2,24}?)$"), "导致", "forward"),
    (re.compile(r"(.{2,16}?)(?:依赖于|取决于)(.{2,24}?)$"), "依赖于", "forward"),
    (re.compile(r"(.{2,16}?)(?:提高|提升|优化)(.{2,24}?)$"), "提升", "forward"),
    (re.compile(r"(.{2,16}?)(?:是|即|指的是)(.{2,28}?)$"), "属于", "forward"),
    (re.compile(r"(.{2,16}?)(?:与|和)(.{2,20}?)(?:配合|协同|共同)(.{0,20})$"), "配合", "forward"),
]

_CONCEPT_CHARS = re.compile(r"[^\u4e00-\u9fffA-Za-z0-9]")


def _has_jieba() -> bool:
    try:
        import jieba  # noqa: F401

        return True
    except Exception:
        return False


def _clean_name(value: str, limit: int = 18) -> str:
    text = _CONCEPT_CHARS.sub("", (value or "").strip())
    if len(text) > limit:
        text = text[:limit]
    return text


def _is_valid(name: str) -> bool:
    return bool(name) and len(name) >= 2 and name not in _STOP_WORDS


def _tokens(text: str) -> List[str]:
    """查询/文本的关键词切分：有 jieba 用分词，否则按中文连续片段切。"""
    text = (text or "").strip()
    if not text:
        return []
    if _has_jieba():
        import jieba

        raw = [w.strip() for w in jieba.cut(text)]
    else:
        raw = [m.group(0) for m in re.finditer(r"[\u4e00-\u9fff]{2,}|[A-Za-z]{3,}", text)]
    out: List[str] = []
    for token in raw:
        if len(token) < 2 or token in _STOP_WORDS or token.isdigit():
            continue
        if token not in out:
            out.append(token)
    return out


def _noun_phrases(sentence: str, limit: int = 6) -> List[str]:
    """取句子里的名词性短语（jieba 词性过滤，缺 jieba 时退回连续中文片段）。"""
    if _has_jieba():
        import jieba.posseg as pseg

        phrases = [
            word.strip()
            for word, flag in pseg.cut(sentence)
            if flag.startswith(("n", "v", "vn", "nz", "eng")) and len(word.strip()) >= 2
        ]
    else:
        phrases = [m.group(0) for m in re.finditer(r"[\u4e00-\u9fff]{2,8}", sentence)]
    out: List[str] = []
    for phrase in phrases:
        name = _clean_name(phrase)
        if _is_valid(name) and name not in out:
            out.append(name)
    return out[:limit]


# ---------------------------------------------------------------- 层次大纲
def _doc_label(file_name: str) -> str:
    stem = re.sub(r"\.(pdf|docx|txt)$", "", os.path.basename(file_name or ""), flags=re.IGNORECASE)
    return stem or "课程资料"


def build_hierarchical_graph(full_text: str, file_name: str) -> Dict[str, Any]:
    """把教材正文解析成 `文档 → 章 → 节` 的树（平台按「包含章节」并入课程图谱）。"""
    root: Dict[str, Any] = {
        "name": _doc_label(file_name),
        "type": "document",
        "level": 0,
        "children": [],
    }
    chapter_nodes: List[Dict[str, Any]] = []
    current_chapter: Optional[Dict[str, Any]] = None
    current_section: Optional[Dict[str, Any]] = None

    for raw_line in (full_text or "").splitlines():
        line = raw_line.strip()
        if not line or len(line) > 80:
            continue
        chapter_match = CHAPTER_RE.match(line)
        section_match = SECTION_RE.match(line)
        numbered_match = NUMBERED_RE.match(line)
        if chapter_match:
            title = f"第{chapter_match.group(1)}章 {chapter_match.group(2)}".strip()
            current_chapter = {"name": title, "type": "chapter", "level": 1, "children": []}
            chapter_nodes.append(current_chapter)
            root["children"].append(current_chapter)
            current_section = None
        elif section_match:
            title = f"第{section_match.group(1)}节 {section_match.group(2)}".strip()
            parent = current_chapter or root
            current_section = {"name": title, "type": "section", "level": 2, "children": []}
            parent["children"].append(current_section)
        elif numbered_match and current_chapter is not None:
            title = f"{numbered_match.group(1)} {numbered_match.group(2)}".strip()
            parent = current_section or current_chapter
            if len(parent["children"]) < 40:
                parent["children"].append({"name": title, "type": "section", "level": 3, "children": []})

    if not chapter_nodes:
        # 没有章节标题：退化为单章，保证层次树可用而不是空树
        root["children"].append({"name": _doc_label(file_name), "type": "chapter", "level": 1, "children": []})
    return root


# ---------------------------------------------------------------- 三元组抽取
_TRIPLE_PROMPT = (
    "你是知识图谱抽取器。从下面文本中抽取概念之间的关系三元组，"
    '只输出 JSON 数组，元素形如 {{"source":"概念A","relation":"关系","target":"概念B"}}。'
    "relation 用 2-6 个汉字的动词或名词，如 包含/属于/应用于/导致/依赖于。"
    "最多 {limit} 条，不要重复，不要输出解释。\n\n文本：\n{text}"
)


def _parse_triples(raw: str) -> List[Tuple[str, str, str]]:
    if not raw:
        return []
    match = re.search(r"\[[\s\S]*\]", raw)
    if not match:
        return []
    try:
        payload = json.loads(match.group(0))
    except Exception:
        return []
    triples: List[Tuple[str, str, str]] = []
    if not isinstance(payload, list):
        return []
    for item in payload:
        if not isinstance(item, dict):
            continue
        source = _clean_name(str(item.get("source") or item.get("head") or ""))
        target = _clean_name(str(item.get("target") or item.get("tail") or ""))
        relation = re.sub(r"[^\u4e00-\u9fffA-Za-z]", "", str(item.get("relation") or ""))[:8]
        if _is_valid(source) and _is_valid(target) and source != target and relation:
            triples.append((source, relation, target))
    return triples


def _llm_triples(text: str, limit: int) -> List[Tuple[str, str, str]]:
    try:
        raw = backend.call_llm(
            _TRIPLE_PROMPT.format(limit=limit, text=text[:_CHUNK_SIZE * 2]),
            system_prompt="你只输出 JSON 数组，不输出任何其它字符。",
            temperature=0.0,
        )
    except Exception as exc:
        print(f"[deepgraph] 三元组抽取调用模型失败，改用规则抽取: {type(exc).__name__}: {exc}")
        return []
    return _parse_triples(raw)


def _rule_triples(text: str, limit: int) -> List[Tuple[str, str, str]]:
    """规则抽取：句式模板优先，模板不命中则退化到句内名词共现。"""
    triples: List[Tuple[str, str, str]] = []
    seen: set[Tuple[str, str, str]] = set()

    def add(source: str, relation: str, target: str) -> None:
        key = (source, relation, target)
        if len(triples) >= limit or key in seen or source == target:
            return
        seen.add(key)
        triples.append(key)

    for sentence in _SENTENCE_SPLIT.split(text or ""):
        sentence = sentence.strip()
        if len(sentence) < 6:
            continue
        matched = False
        for pattern, relation, direction in _TEMPLATES:
            match = pattern.search(sentence)
            if not match:
                continue
            left = _clean_name(match.group(1))
            right = _clean_name(match.group(2))
            if not (_is_valid(left) and _is_valid(right)) or left == right:
                continue
            if direction == "forward":
                add(left, relation, right)
            else:
                add(right, relation, left)
            matched = True
            break
        if matched:
            continue
        phrases = _noun_phrases(sentence, limit=4)
        if len(phrases) >= 2:
            for index in range(len(phrases) - 1):
                add(phrases[index], "相关", phrases[index + 1])
        if len(triples) >= limit:
            break
    return triples


def _chunks(text: str, size: int = _CHUNK_SIZE) -> List[str]:
    text = text or ""
    return [text[i : i + size] for i in range(0, len(text), size)] if text else []


def _evidence_for(text: str, source: str, target: str) -> List[Dict[str, str]]:
    for sentence in _SENTENCE_SPLIT.split(text or ""):
        if source in sentence and target in sentence:
            return [{"text": sentence.strip()[:400]}]
    return []


def _add_triples(graph: nx.Graph, triples: Iterable[Tuple[str, str, str]], text: str) -> None:
    for source, relation, target in triples:
        if graph.number_of_edges() >= MAX_EDGES:
            return
        if graph.has_edge(source, target):
            labels = graph[source][target].setdefault("label", relation)
            if labels != relation:
                graph[source][target]["aliases"] = sorted(
                    set(graph[source][target].get("aliases", [])) | {labels, relation}
                )
            continue
        graph.add_edge(
            source,
            target,
            label=relation,
            source_chunks=_evidence_for(text, source, target),
        )


def build_global_knowledge_graph(
    text: str,
    file_name: Optional[str] = None,
    progress_callback: Optional[Callable[[str, str, str], None]] = None,
) -> nx.Graph:
    """文档级概念图：LLM 抽前 N 块，规则抽取补齐全文，两条腿都必须有结果。"""
    graph = nx.Graph(name=_doc_label(file_name or "")) if file_name else nx.Graph()
    total = min(len(_chunks(text)), MAX_EXTRACT_CHUNKS)
    for index, chunk in enumerate(_chunks(text)[:MAX_EXTRACT_CHUNKS], start=1):
        if progress_callback:
            progress_callback("extract", f"抽取第 {index}/{total} 块", chunk[:120])
        _add_triples(graph, _llm_triples(chunk, limit=12), chunk)
    _add_triples(graph, _rule_triples(text, limit=MAX_EDGES), text)
    return graph


def build_chapter_knowledge_graph(
    chapter_name: str,
    chapter_text: str,
    progress_callback: Optional[Callable[[str, str, str], None]] = None,
) -> nx.Graph:
    """章节级三元组：单章文本较短，仍然保留"模型 → 规则 → 标题锚点"三级兜底。"""
    graph = nx.Graph(name=(chapter_name or "chapter"))
    text = chapter_text or ""
    if progress_callback:
        progress_callback("extract", "开始章节三元组抽取", text[:120])
    triples = _llm_triples(text, limit=20) or _rule_triples(text, limit=20)
    _add_triples(graph, triples, text)
    if graph.number_of_edges() == 0 and _is_valid(_clean_name(chapter_name)):
        anchor = _clean_name(chapter_name)
        for phrase in _noun_phrases(text, limit=5):
            if phrase != anchor:
                graph.add_edge(anchor, phrase, label="包含章节要点", source_chunks=[])
        _add_triples(graph, [(anchor, "包含章节要点", p) for p in _noun_phrases(text, limit=5) if p != anchor], text)
    if progress_callback:
        progress_callback(
            "done", "章节图谱构建完成", f"{graph.number_of_nodes()} 节点 / {graph.number_of_edges()} 关系"
        )
    return graph


# ---------------------------------------------------------------- 图检索
class _GraphSnippet:
    """无 langchain 依赖时的最小文档片体（与 langchain `Document` 的字段对齐）。"""

    def __init__(self, content: str, metadata: Optional[Dict[str, Any]] = None) -> None:
        self.page_content = content
        self.metadata = metadata or {}


def _make_snippet(content: str, metadata: Dict[str, Any]) -> Any:
    try:
        from langchain_core.documents import Document

        return Document(page_content=content, metadata=metadata)
    except Exception:
        return _GraphSnippet(content, metadata)


def retrieve_from_graph(query: str, graph: Any, top_k: int = 10) -> List[Any]:
    """按查询关键词与边端点的重合度召回关系文本（图检索分支）。"""
    if graph is None or getattr(graph, "number_of_edges", None) is None:
        return []
    if graph.number_of_edges() == 0:
        return []
    keywords = _tokens(query)
    if not keywords:
        return []
    scored: List[Tuple[float, str, str, Dict[str, Any]]] = []
    for u, v, data in graph.edges(data=True):
        text_u, text_v = str(u), str(v)
        hit = sum(1 for k in keywords if k in text_u or k in text_v)
        if hit == 0:
            continue
        exact = sum(1 for k in keywords if k == text_u or k == text_v)
        scored.append((hit + exact * 2, text_u, text_v, data or {}))
    if not scored:
        return []
    scored.sort(key=lambda item: (-item[0], item[1], item[2]))
    out: List[Any] = []
    for score, u, v, data in scored[: max(1, int(top_k))]:
        label = data.get("label") or "相关"
        content = f"{u} --{label}--> {v}"
        evidence = ""
        for item in data.get("source_chunks") or []:
            if (item or {}).get("text"):
                evidence = item["text"]
                break
        out.append(
            _make_snippet(
                content,
                {"source": "graph", "score": score, "evidence": evidence},
            )
        )
    return out
