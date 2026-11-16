"""
IPA：知识点抽取（Instructional Point Analysis / 教学要点分析）
==============================================================

对应论文《基于知识图谱的课程知识抽取》中的 IPA 两阶段法：

    阶段一「模拟学习者阅读理解」——逐段精读课程正文，找出**值得单独成条**的术语，
    并为每个术语摘出正文原句作为证据（不编造、不改写原文）。
    阶段二「模拟专家归纳」——把学生摘出的术语交给专家角色整理：合并同义词、识别
    上下位、给出规范名称与教材式说明。

与 IREM 的分工：**IPA 负责"有哪些点"，IREM 负责"点与点是什么关系"**。
本模块只产出候选，不读写数据库、不发网络请求；LLM 由调用方以 ``llm_call``
可调用对象注入（脚本侧用 Ollama / DeepSeek 实现，测试用假实现），因此本模块
是纯函数、可离线单测、无随机、无时间依赖。

能力声明（与 IREM 同一纪律）
----------------------------
输出是**候选知识点**，是否入库须经人工抽检。当前本项目**没有人工标注的抽取评测集**，
因此抽取的准确率/召回率**未经量化验证**；写文档与汇报时只能说"已实现 IPA 抽取流程
并产出候选"，不得声称"抽取准确率 N%"。

输入约定
--------
paragraphs: list[str]，课程正文段落（通常来自 RAG 语料 `scripts/rag_corpus.json`）。
chapters:   可选 list[dict]，每项 ``{"id", "title"}``；用于把候选点标注到章节，
            不传则 chapter_id 为 None。

LLM 约定
--------
``llm_call(prompt: str, system: str | None) -> str``，返回模型原始文本；本模块负责
从文本里容错提取 JSON（允许 ```json 围栏、前后废话、单引号等常见退化）。

失败语义（不抛异常）
--------------------
LLM 返回不可解析、段落为空、术语为空等，一律跳过该段/该条并继续；
全部失败时返回 ``[]``。调用方可据 ``extract_knowledge_points(...)` 的返回值是否为空
判定"抽取未产出"，不得静默当作"课程没有知识点"。
"""

from __future__ import annotations

import ast
import json
import re

# 句子分隔：中英文句末标点与分号、换行（与 irem.py 保持同一口径）
_SENTENCE_SEP = re.compile(r"[。！？!?；;\n\r]+")

# JSON 围栏：模型常把 JSON 包在 ```json ... ``` 里
_JSON_FENCE = re.compile(r"```(?:json|JSON)?\s*(.*?)```", re.S)

# 单引号键/值的常见退化（不是合法 JSON，但模型常写）
_SINGLE_QUOTE_KEY = re.compile(r"([{,]\s*)'([^']*)'(\s*:)")

DEFAULT_MAX_TERMS_PER_PARAGRAPH = 8
DEFAULT_MAX_POINTS = 200
DEFAULT_BATCH_SIZE = 8

_SYSTEM_STAGE1 = (
    "你是正在精读课程教材的学生。只依据给定正文作答，不补充正文以外的知识。"
)
_SYSTEM_STAGE2 = (
    "你是该课程的教学专家，负责把学生摘出的术语整理成规范的课程知识点条目。"
)

_STAGE1_PROMPT = (
    "下面是一段课程正文。请找出其中**值得作为独立知识点**的术语或概念"
    "（通常 2-6 个，宁少勿滥），并为每个术语摘出正文中的**原句**作为证据。\n"
    "要求：\n"
    "1. 术语必须是正文里真实出现或可直接概括正文内容的，不得引入正文没有的概念；\n"
    "2. 证据必须是正文原句的连续片段，不得改写；\n"
    "3. **不要把下面格式示例里的占位文字当作答案**（示例只是告诉你字段怎么写）；\n"
    "4. 只输出 JSON 数组，不要解释、不要 Markdown 标题。\n"
    "格式示例（内容必须是正文中的真实术语与原句）：\n"
    '[{"term":"填写正文中出现的术语","evidence":"填写正文中的原句"}]\n\n'
    "正文：\n{paragraph}"
)

# 模型容易把提示词/格式示例里的占位文字原样复读出来：这些一律不是术语
_PLACEHOLDER_TERMS = {
    "术语", "术语或概念", "概念", "知识点", "名称", "示例", "例子", "填写正文中出现的术语",
    "正文中出现的术语", "正文中的原句", "以下", "等等", "无", "none", "term", "name",
}

_STAGE2_PROMPT = (
    "下面是学生从同一段/同一章正文里摘出的术语与证据。请把它们整理成课程知识点：\n"
    "1. **输入有几个术语，输出就必须有几条**，一一对应，不得合并条目；\n"
    "2. 只有**完全同义/互为缩写**才合并为一条并把另一种写法放进 aliases；\n"
    "   上位与下位概念、并列概念（如网络层与传输层这类并列层）**必须分开保留**；\n"
    "3. 每个知识点给出规范名称（2-20 字）与 40-120 字的教材式说明（讲清定义/作用/要点，"
    "不得虚构正文没有的事实）；\n"
    "4. 只输出 JSON 数组，不要解释。\n"
    '格式示例：[{"name":"规范名称","description":"说明","aliases":["别名"],"evidence":"证据原句"}]\n\n'
    "待整理：\n{terms}"
)


def split_sentences(text: str) -> list[str]:
    """按句切分，保留非空片段（与 IREM 的句子口径一致）。"""
    if not isinstance(text, str):
        return []
    return [part.strip() for part in _SENTENCE_SEP.split(text) if part.strip()]


def split_paragraphs(paragraphs) -> list[str]:
    """清洗输入段落：去空、去纯标点、去重（保序）。"""
    if not isinstance(paragraphs, (list, tuple)):
        return []
    cleaned: list[str] = []
    seen: set[str] = set()
    for item in paragraphs:
        if not isinstance(item, str):
            continue
        text = item.strip()
        if len(text) < 20:  # 太短的片段没有抽取价值，且会显著拉低 LLM 产出质量
            continue
        if text in seen:
            continue
        seen.add(text)
        cleaned.append(text)
    return cleaned


def _strip_fence(text: str) -> str:
    match = _JSON_FENCE.search(text)
    if match:
        return match.group(1).strip()
    return text.strip()


def _repair_single_quotes(text: str) -> str:
    return _SINGLE_QUOTE_KEY.sub(r'\1"\2"\3', text)


def _loads_lenient(text: str):
    """容错解析模型输出：去围栏 → 直接解析 → 修单引号 → 截取首个 JSON 片段。"""
    if not isinstance(text, str) or not text.strip():
        return None
    body = _strip_fence(text)
    for candidate in (body, _repair_single_quotes(body)):
        try:
            return json.loads(candidate)
        except Exception:
            pass
    # 模型爱把 JSON 写成 Python 字面量（键和值都用单引号）：literal_eval 只解析字面量，不执行代码
    for candidate in (body, _repair_single_quotes(body)):
        try:
            value = ast.literal_eval(candidate)
        except Exception:
            value = None
        if isinstance(value, (list, dict)):
            return value
    # 退一步：从第一个 [ 或 { 截到最后一个 ] 或 }（模型常在 JSON 前后写解释）
    starts = [i for i in (body.find("["), body.find("{")) if i >= 0]
    if not starts:
        return None
    start = min(starts)
    end_bracket = max(body.rfind("]"), body.rfind("}"))
    if end_bracket <= start:
        return None
    snippet = body[start:end_bracket + 1]
    for candidate in (snippet, _repair_single_quotes(snippet)):
        try:
            return json.loads(candidate)
        except Exception:
            pass
    return None


def _as_text(value, limit: int = 500) -> str:
    if not isinstance(value, str):
        return ""
    return value.strip()[:limit]


def stage1_identify(paragraph: str, llm_call, *, max_terms: int = DEFAULT_MAX_TERMS_PER_PARAGRAPH,
                    system: str = _SYSTEM_STAGE1) -> list[dict]:
    """阶段一：模拟学习者，找出该段中的候选术语与证据原句。

    返回 ``[{"term","evidence"}]``；无法解析时返回 ``[]``（不抛异常）。
    """
    if not isinstance(paragraph, str) or not paragraph.strip() or not callable(llm_call):
        return []
    prompt = _STAGE1_PROMPT.replace("{paragraph}", paragraph)
    try:
        raw = llm_call(prompt, system)
    except Exception:
        return []
    data = _loads_lenient(raw)
    if isinstance(data, dict):
        data = data.get("terms") or data.get("items") or []
    if not isinstance(data, list):
        return []
    out: list[dict] = []
    seen: set[str] = set()
    for item in data:
        if isinstance(item, str):
            term, evidence = item, ""
        elif isinstance(item, dict):
            term = _as_text(item.get("term") or item.get("name"), 60)
            evidence = _as_text(item.get("evidence") or item.get("sentence"), 300)
        else:
            continue
        if not term or term in seen:
            continue
        # 模型复读提示词占位词 / 术语短到没有信息量时，直接丢弃
        if term.lower() in _PLACEHOLDER_TERMS or len(term) < 2:
            continue
        # 证据必须能在原文中找到，否则视为模型编造，丢弃该条
        if evidence and evidence not in paragraph:
            evidence = ""
        seen.add(term)
        out.append({"term": term, "evidence": evidence})
        if len(out) >= max(1, max_terms):
            break
    return out


def stage2_normalize(terms: list[dict], llm_call, *, system: str = _SYSTEM_STAGE2) -> list[dict]:
    """阶段二：模拟专家，把一批术语整理为规范知识点（合并同义、给说明）。"""
    if not isinstance(terms, list) or not terms or not callable(llm_call):
        return []
    payload = json.dumps(terms, ensure_ascii=False)
    prompt = _STAGE2_PROMPT.replace("{terms}", payload)
    try:
        raw = llm_call(prompt, system)
    except Exception:
        return []
    data = _loads_lenient(raw)
    if isinstance(data, dict):
        data = data.get("points") or data.get("items") or [data]
    if not isinstance(data, list):
        return []
    out: list[dict] = []
    seen: set[str] = set()
    for item in data:
        if not isinstance(item, dict):
            continue
        name = _as_text(item.get("name") or item.get("term"), 60)
        if not name or name in seen:
            continue
        description = _as_text(item.get("description") or item.get("desc"), 400)
        aliases = item.get("aliases") or []
        if isinstance(aliases, str):
            aliases = [aliases]
        aliases = [a.strip() for a in aliases if isinstance(a, str) and a.strip()][:5]
        evidence = _as_text(item.get("evidence"), 300)
        seen.add(name)
        out.append({
            "name": name,
            "description": description,
            "aliases": aliases,
            "evidence": evidence,
        })
    return out


def extract_knowledge_points(paragraphs, llm_call, *, chapters=None,
                             max_points: int = DEFAULT_MAX_POINTS,
                             batch_size: int = DEFAULT_BATCH_SIZE,
                             max_terms_per_paragraph: int = DEFAULT_MAX_TERMS_PER_PARAGRAPH) -> list[dict]:
    """两阶段抽取主入口，返回知识点候选列表。

    返回项字段：
        name            规范名称（阶段二产出；阶段二失败时回退为阶段一的术语）
        description     教材式说明（可能为空串，表示只抽到名字）
        aliases         别名列表
        evidence        证据原句（能在原文中定位到的那条）
        occurrences     该术语在全部段落中被摘出的次数
        source_paragraph 首次出现的段落序号（0 基）
        chapter_id      章节 id（chapters 为空时为 None）
        method          "IPA"（固定，便于入库时标注来源）
        confidence      0~1 的经验置信度：有说明 + 有证据 + 多次出现 越高
    """
    valid = split_paragraphs(paragraphs)
    if not valid or not callable(llm_call):
        return []

    chapter_ids = _chapter_ids(chapters, len(valid))

    # 阶段一：逐段摘术语（保留首次证据与出现次数）
    collected: dict[str, dict] = {}
    order: list[str] = []
    for index, paragraph in enumerate(valid):
        for hit in stage1_identify(paragraph, llm_call,
                                   **{"max_terms": max_terms_per_paragraph}):
            term = hit["term"]
            if term not in collected:
                collected[term] = {
                    "term": term,
                    "evidence": hit["evidence"],
                    "paragraph": index,
                    "occurrences": 0,
                }
                order.append(term)
            collected[term]["occurrences"] += 1
            if not collected[term]["evidence"] and hit["evidence"]:
                collected[term]["evidence"] = hit["evidence"]

    if not collected:
        return []

    # 阶段二：分批规范化（每批同一章，便于专家在章内做上下位判断）
    points: list[dict] = []
    batch: list[dict] = []
    batch_chapter = None

    def flush() -> None:
        nonlocal batch, batch_chapter
        if not batch:
            return
        normalized = stage2_normalize(batch, llm_call)
        for item in _reconcile(batch, normalized, batch_chapter):
            points.append(item)
        batch = []

    for term in order:
        item = collected[term]
        chapter = chapter_ids[item["paragraph"]] if chapter_ids else None
        if batch and chapter != batch_chapter:
            flush()
        batch_chapter = chapter
        batch.append(item)
        if len(batch) >= max(1, batch_size):
            flush()
    flush()

    points = [p for p in points if p.get("name")]
    # 同名去重（阶段二可能把两个术语归成同名条目）
    deduped: list[dict] = []
    seen_names: set[str] = set()
    for point in points:
        if point["name"] in seen_names:
            continue
        seen_names.add(point["name"])
        deduped.append(point)

    # 排序：出现次数降序 → 名称升序（确定性）
    deduped.sort(key=lambda p: (-int(p.get("occurrences") or 0), p["name"]))
    return deduped[:max(1, max_points)]


def _reconcile(batch: list[dict], normalized: list[dict], chapter_id) -> list[dict]:
    """把阶段二的产物对齐回阶段一的术语清单。

    纪律：**条目数不得因专家"合并"而减少**。专家阶段的价值是"给规范名称和说明"，
    不是"替抽取环节决定哪些术语不值得留"——实测 deepseek-r1 会把 8 个术语压成 1 条
    （把网络层/传输层/OSI 都并进"TCP/IP 体系"），这里做兜底：
      1. 每个阶段一术语按 name / aliases 找对应条目，找到就用专家版（带说明）；
      2. 找不到就用术语名回退（description 空、confidence 0.5），保留证据与出现次数；
      3. 专家额外新增的条目（未匹配到任何术语）也保留，标注 method=IPA。
    """
    used: set[int] = set()
    result: list[dict] = []
    for term_item in batch:
        matched = None
        for item in normalized:
            if id(item) in used:
                continue
            if item.get("name") == term_item["term"] or term_item["term"] in (item.get("aliases") or []):
                matched = item
                break
        if matched is not None:
            used.add(id(matched))
            matched["chapter_id"] = chapter_id
            matched["method"] = "IPA"
            if not matched.get("evidence"):
                matched["evidence"] = term_item.get("evidence") or ""
            _attach_meta(matched, batch)
            result.append(matched)
        else:
            result.append({
                "name": term_item["term"],
                "description": "",
                "aliases": [],
                "evidence": term_item.get("evidence") or "",
                "chapter_id": chapter_id,
                "method": "IPA",
                "confidence": 0.5,
                "occurrences": term_item.get("occurrences") or 1,
                "source_paragraph": term_item.get("paragraph"),
            })
    for item in normalized:
        if id(item) in used:
            continue
        item["chapter_id"] = chapter_id
        item["method"] = "IPA"
        _attach_meta(item, batch)
        result.append(item)
    return result


def _attach_meta(item: dict, batch: list[dict]) -> None:
    """给阶段二的产物补上阶段一的统计字段（按名称/别名匹配回批次里的术语）。"""
    names = [item["name"]] + list(item.get("aliases") or [])
    meta = None
    for candidate in batch:
        if candidate["term"] in names:
            meta = candidate
            break
    if meta is None and batch:
        meta = batch[0]
    occurrences = int(meta.get("occurrences") or 1) if meta else 1
    evidence = item.get("evidence") or (meta or {}).get("evidence") or ""
    has_desc = bool(item.get("description"))
    confidence = 0.5
    if has_desc:
        confidence += 0.2
    if evidence:
        confidence += 0.15
    if occurrences >= 2:
        confidence += 0.1
    item["confidence"] = round(min(1.0, confidence), 2)
    item["occurrences"] = occurrences
    item["source_paragraph"] = (meta or {}).get("paragraph")
    item["evidence"] = evidence


def _chapter_ids(chapters, paragraph_count: int) -> list:
    """把章节均分到段落上，返回每个段落对应的 chapter_id（长度对齐 paragraphs）。

    章节与段落不是一一对应时，按"段落在全部段落中的相对位置"线性映射，
    这是**位置近似**而非真实切分——真实切分应由调用方在拆分段落时一并给出，
    这里只是让候选点带上可追溯的章节线索。
    """
    if not isinstance(chapters, (list, tuple)) or not chapters:
        return []
    ids = []
    for chapter in chapters:
        if isinstance(chapter, dict):
            cid = chapter.get("id")
        else:
            cid = chapter
        ids.append(cid)
    if len(ids) == paragraph_count:
        return ids
    if paragraph_count <= 0:
        return []
    mapped = []
    for index in range(paragraph_count):
        pos = int(index * len(ids) / paragraph_count)
        mapped.append(ids[min(pos, len(ids) - 1)])
    return mapped
