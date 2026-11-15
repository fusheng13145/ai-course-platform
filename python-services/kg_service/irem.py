"""
IREM：隐式关系抽取（Implicit Relation Extraction Module）——仅产出候选
=====================================================================

本模块从知识点元数据与课程文本中推断**未被显式标注**的关系候选，用于人工复核与
后续离线评估。它是纯函数、离线、确定性的：只用标准库，不读写数据库，不发网络请求，
不注册任何 HTTP 端点，也不依赖任何外部服务。

能力声明（必须原样保留）
------------------------
本模块输出的是**候选（candidate）**，不是已验证的关系抽取能力。
当前项目**没有标注语料**，因此其**抽取效果与准确率完全未知、未经任何验证**。
不得在文档、演示或汇报中将其表述为“已实现的、有效的 IREM 能力”，
也不得用其输出直接覆盖或替代人工/显式标注的关系。

三条规则（可解释、无随机、无时间依赖）
--------------------------------------
1. ``INCLUDES``：知识点 B 的名称出现在知识点 A 的 ``content`` 中（A 的说明里提到了 B），
   输出 (A, B, INCLUDES)，score ≥ 0.6。
2. ``PREREQUISITE``：A 的名称出现在 B 的 ``content`` 中，且语料中 A 首次出现早于 B
   （先修内容通常先讲、并被后续内容引用），输出 (A, B, PREREQUISITE)。
   若同一有序对同时满足 INCLUDES（互相包含），只保留分数更高的那条，避免重复。
3. ``RELATED``：A、B 在同一句的共现窗口内出现，且两者之间不存在上述包含关系，
   输出一条无向候选（按 source_id < target_id 归一），score 落在 [0.3, 0.6]。
4. 任意有序对 (source_id, target_id) 只输出一次，取最高分；``existing_pairs`` 中的
   有序对不再输出（其反向对仍可作为候选）。

共现窗口
--------
按句切分（``。！？!?；;`` 与换行）后，句内各知识点名称按**首次出现位置**排序得到序号；
两个知识点的序号差 ≤ ``window`` 即视为共现。``window=1``（默认）表示名称在句中相邻
出现——课程短句通常只含两个知识点，此时等价于“同句内均出现即共现”；增大 ``window``
则允许同句内相隔更多知识点也算共现。

评分（线性、可复现）
--------------------
- INCLUDES:      ``min(1.0, 0.6 + 0.1 * overlap)``，overlap = |B 名| / |A 的 content|
- PREREQUISITE:  ``min(1.0, 0.6 + 0.2 * overlap)``，overlap = |A 名| / |B 的 content|
- RELATED:       ``min(0.6, 0.3 + 0.1 * 共现次数 + 0.2 * 名称字符 Jaccard 相似度)``

排序：score 降序 → source_id 升序 → target_id 升序；``top_n`` 非空时截断。
输入非法（nodes 为空、texts 为空、有效知识点不足 2 个等）一律返回 ``[]``，不抛异常。
"""

from __future__ import annotations

import re

# 句子分隔符：中英文句末标点与分号、换行
_SENTENCE_SEP = re.compile(r"[。！？!?；;\n\r]+")

RELATION_INCLUDES = "INCLUDES"
RELATION_PREREQUISITE = "PREREQUISITE"
RELATION_RELATED = "RELATED"

EVIDENCE_MAX_CHARS = 80

_SCORE_DIGITS = 3


def _is_int(value) -> bool:
    """排除 bool（Python 中 bool 是 int 的子类），只接受真正的整数 id。"""
    return isinstance(value, int) and not isinstance(value, bool)


def _valid_nodes(nodes) -> list[dict]:
    """清洗输入节点：保留 id/name 合法的条目，按 id 去重并升序，保证遍历顺序确定。"""
    cleaned: list[dict] = []
    seen: set[int] = set()
    if not isinstance(nodes, (list, tuple)):
        return cleaned
    for item in nodes:
        if not isinstance(item, dict):
            continue
        node_id = item.get("id")
        name = item.get("name")
        if not _is_int(node_id) or not isinstance(name, str):
            continue
        name = name.strip()
        if not name or node_id in seen:
            continue
        seen.add(node_id)
        content = item.get("content")
        cleaned.append({
            "id": node_id,
            "name": name,
            "content": content.strip() if isinstance(content, str) else "",
        })
    cleaned.sort(key=lambda node: node["id"])
    return cleaned


def _valid_texts(texts) -> list[str]:
    if isinstance(texts, str) or not isinstance(texts, (list, tuple)):
        return []
    return [text for text in texts if isinstance(text, str) and text.strip()]


def _split_sentences(text: str) -> list[str]:
    return [part.strip() for part in _SENTENCE_SEP.split(text) if part.strip()]


def _normalize_pairs(existing_pairs) -> set:
    """容错地把 existing_pairs 归一为 {(source_id, target_id)}；非法条目直接忽略。"""
    pairs: set = set()
    if not existing_pairs:
        return pairs
    try:
        items = list(existing_pairs)
    except TypeError:
        return pairs
    for item in items:
        if not isinstance(item, (tuple, list)) or len(item) != 2:
            continue
        source_id, target_id = item[0], item[1]
        if _is_int(source_id) and _is_int(target_id):
            pairs.add((source_id, target_id))
    return pairs


def _coerce_threshold(value, default: float = 0.3) -> float:
    try:
        threshold = float(value)
    except (TypeError, ValueError):
        return default
    if threshold != threshold:  # NaN
        return default
    return threshold


def _coerce_window(value) -> int:
    try:
        window = int(value)
    except (TypeError, ValueError):
        return 1
    return window if window >= 1 else 1


def _build_catalog(nodes: list[dict]) -> list[tuple[int, str]]:
    """(id, name) 按名称长度降序：扫描时优先匹配更长的名称，避免“函数”抢占“函数与模块”。"""
    return sorted(((node["id"], node["name"]) for node in nodes),
                  key=lambda item: (-len(item[1]), item[0]))


def _mentions(sentence: str, catalog: list[tuple[int, str]]) -> list[tuple[int, int]]:
    """返回句内 (字符偏移, node_id) 列表；同名重叠时取最长匹配并跳过已消费的字符。"""
    found: list[tuple[int, int]] = []
    index = 0
    length = len(sentence)
    while index < length:
        hit = None
        for node_id, name in catalog:
            if sentence.startswith(name, index):
                hit = (node_id, name)
                break
        if hit is None:
            index += 1
            continue
        found.append((index, hit[0]))
        index += len(hit[1])
    return found


def _cooccurring_pairs(sentence: str, catalog: list[tuple[int, str]],
                       window: int) -> list[tuple[int, int]]:
    """句内按首次出现顺序编号，返回序号差 ≤ window 的 (node_id, node_id) 有序对。"""
    order: list[int] = []
    seen: set[int] = set()
    for _, node_id in _mentions(sentence, catalog):
        if node_id not in seen:
            seen.add(node_id)
            order.append(node_id)
    pairs: list[tuple[int, int]] = []
    for i in range(len(order)):
        for j in range(i + 1, len(order)):
            if j - i <= window:
                pairs.append((order[i], order[j]))
    return pairs


def _char_jaccard(a: str, b: str) -> float:
    """名称的字符集合 Jaccard 相似度：用于在没有包含关系时衡量两个术语的用词重叠。"""
    set_a, set_b = set(a), set(b)
    if not set_a or not set_b:
        return 0.0
    return len(set_a & set_b) / len(set_a | set_b)


def _clip(text: str, limit: int = EVIDENCE_MAX_CHARS) -> str:
    return text.strip()[:limit]


def _first_positions(sentences: list[str], catalog: list[tuple[int, str]]) -> dict:
    """每个知识点在语料中的首次出现位置 (句序号, 句内偏移)，用于判定“先修在前”。"""
    positions: dict = {}
    for sentence_index, sentence in enumerate(sentences):
        for offset, node_id in _mentions(sentence, catalog):
            if node_id not in positions:
                positions[node_id] = (sentence_index, offset)
    return positions


def infer_implicit_relations(nodes, texts, *, min_score=0.3, top_n=None,
                             existing_pairs=None, window=1) -> list[dict]:
    """推断隐式关系的**候选**，返回按分数降序排列的候选列表。

    参数：
        nodes:          list[dict]，每项至少含 ``id``(int) 与 ``name``(str)，可选 ``content``(str)。
        texts:          list[str]，课程语料段落。
        min_score:      分数下限（闭区间），默认 0.3。
        top_n:          非空时只返回前 N 条。
        existing_pairs: 既有显式关系的有序对集合，这些有序对不再作为候选返回。
        window:         句内共现窗口（序号差阈值），< 1 按 1 处理。

    返回：
        [{"source_id", "source_name", "target_id", "target_name",
          "relation_type", "score", "evidence"}]

    说明：本函数是候选推断器，**效果与准确率未经标注语料验证（未知）**，
    输出不得直接当作既成事实写入图谱。
    """
    valid_nodes = _valid_nodes(nodes)
    valid_texts = _valid_texts(texts)
    if len(valid_nodes) < 2 or not valid_texts:
        return []

    existing = _normalize_pairs(existing_pairs)
    threshold = _coerce_threshold(min_score)
    radius = _coerce_window(window)

    catalog = _build_catalog(valid_nodes)
    by_id = {node["id"]: node for node in valid_nodes}
    sentences = [sentence for text in valid_texts for sentence in _split_sentences(text)]

    best: dict[tuple[int, int], dict] = {}
    # 已存在包含关系的无向对：不再为其产出 RELATED，避免同一对知识点重复表达
    contained: set[frozenset] = set()

    def offer(source_id: int, target_id: int, relation_type: str,
              score: float, evidence: str) -> None:
        if source_id == target_id or (source_id, target_id) in existing:
            return
        rounded = round(min(1.0, max(0.0, score)), _SCORE_DIGITS)
        candidate = {
            "source_id": source_id,
            "source_name": by_id[source_id]["name"],
            "target_id": target_id,
            "target_name": by_id[target_id]["name"],
            "relation_type": relation_type,
            "score": rounded,
            "evidence": _clip(evidence),
        }
        current = best.get((source_id, target_id))
        if current is None:
            best[(source_id, target_id)] = candidate
        elif rounded > current["score"] or (rounded == current["score"]
                                            and relation_type < current["relation_type"]):
            best[(source_id, target_id)] = candidate

    # 规则 1：INCLUDES —— B 的名称出现在 A 的 content 中
    for source in valid_nodes:
        if not source["content"]:
            continue
        for target in valid_nodes:
            if target["id"] == source["id"]:
                continue
            if target["name"] not in source["content"]:
                continue
            overlap = len(target["name"]) / len(source["content"])
            contained.add(frozenset((source["id"], target["id"])))
            offer(source["id"], target["id"], RELATION_INCLUDES,
                  min(1.0, 0.6 + 0.1 * overlap), source["content"])

    # 规则 2：PREREQUISITE —— A 的名称出现在 B 的 content 中，且语料中 A 先出现
    first_positions = _first_positions(sentences, catalog)
    for source in valid_nodes:
        for target in valid_nodes:
            if target["id"] == source["id"] or not target["content"]:
                continue
            if source["name"] not in target["content"]:
                continue
            source_pos = first_positions.get(source["id"])
            target_pos = first_positions.get(target["id"])
            if source_pos is None or target_pos is None or not source_pos < target_pos:
                continue
            contained.add(frozenset((source["id"], target["id"])))
            evidence = target["content"]
            for sentence in sentences:
                if source["name"] in sentence and target["name"] in sentence:
                    evidence = sentence
                    break
            overlap = len(source["name"]) / len(target["content"])
            offer(source["id"], target["id"], RELATION_PREREQUISITE,
                  min(1.0, 0.6 + 0.2 * overlap), evidence)

    # 规则 3：RELATED —— 同句共现且无包含关系，按 source_id < target_id 归一为一条
    cooccurrence: dict[tuple[int, int], list] = {}
    for sentence in sentences:
        for left, right in _cooccurring_pairs(sentence, catalog, radius):
            pair = (left, right) if left < right else (right, left)
            if frozenset(pair) in contained:
                continue
            entry = cooccurrence.setdefault(pair, [0, sentence])
            entry[0] += 1
    for (source_id, target_id), (count, sentence) in cooccurrence.items():
        overlap = _char_jaccard(by_id[source_id]["name"], by_id[target_id]["name"])
        offer(source_id, target_id, RELATION_RELATED,
              min(0.6, 0.3 + 0.1 * count + 0.2 * overlap), sentence)

    ranked = sorted(best.values(),
                    key=lambda item: (-item["score"], item["source_id"], item["target_id"]))
    ranked = [item for item in ranked if item["score"] >= threshold]
    if _is_int(top_n) and top_n >= 0:
        ranked = ranked[:top_n]
    return ranked
