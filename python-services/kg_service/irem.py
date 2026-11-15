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

import json
import re

_JSON_FENCE = re.compile(r"```(?:json|JSON)?\s*(.*?)```", re.S)


def _loads_lenient(text):
    """容错解析模型输出：去 ``` 围栏后严格解析，失败再截取首个 JSON 片段。

    本模块刻意**不 import 其他项目模块**：`test_irem` 有一条机械守卫要求 irem 只依赖
    标准库（保证它是可离线、无副作用的纯函数模块），因此解析逻辑在这里自持一份。
    """
    if not isinstance(text, str) or not text.strip():
        return None
    body = text.strip()
    match = _JSON_FENCE.search(body)
    if match:
        body = match.group(1).strip()
    try:
        return json.loads(body)
    except Exception:
        pass
    starts = [i for i in (body.find("["), body.find("{")) if i >= 0]
    if not starts:
        return None
    start = min(starts)
    end = max(body.rfind("]"), body.rfind("}"))
    if end <= start:
        return None
    try:
        return json.loads(body[start:end + 1])
    except Exception:
        return None

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


# ---------------------------------------------------------------- 五类关系判定（LLM 版）
#
# 论文（杜彩丽）把课程知识点关系归纳为五类：包含、前驱、同一、兄弟、其他。
# 上面的规则推断只能给出 3 类（INCLUDES / PREREQUISITE / RELATED），
# 「同一」与「兄弟」必须靠语义判断（规则无法从字面区分并列与同义），因此这里补 LLM 判定。
#
# 能力声明：与规则版同一纪律——输出仍是**候选**，未经人工标注集评估，
# 不得表述为"已实现且有效的 IREM 能力"；入库前须人工抽检。

RELATION_SAME = "SAME"
RELATION_SIBLING = "SIBLING"
RELATION_OTHER = "OTHER"

FIVE_CLASSES = (
    RELATION_INCLUDES,
    RELATION_PREREQUISITE,
    RELATION_SAME,
    RELATION_SIBLING,
    RELATION_OTHER,
)

_CLASS_ALIASES = {
    "INCLUDES": RELATION_INCLUDES, "包含": RELATION_INCLUDES, "上位": RELATION_INCLUDES,
    "PREREQUISITE": RELATION_PREREQUISITE, "前驱": RELATION_PREREQUISITE,
    "先修": RELATION_PREREQUISITE, "前提": RELATION_PREREQUISITE,
    "SAME": RELATION_SAME, "同一": RELATION_SAME, "同义": RELATION_SAME, "等价": RELATION_SAME,
    "SIBLING": RELATION_SIBLING, "兄弟": RELATION_SIBLING, "并列": RELATION_SIBLING,
    "OTHER": RELATION_OTHER, "RELATED": RELATION_OTHER, "其他": RELATION_OTHER,
}

_CLASS_SYSTEM = "你是课程知识图谱专家，只依据给定信息判断两个知识点的关系类别，不补充外部知识。"

_CLASS_PROMPT = (
    "请判断下面每一对课程知识点的关系类别，类别只能是以下五种之一：\n"
    "INCLUDES（包含：A 包含 B，B 是 A 的下位概念或组成部分）\n"
    "PREREQUISITE（前驱：掌握 A 是学习 B 的前提，通常 A 先讲授且被 B 引用）\n"
    "SAME（同一：两者是同一概念的不同叫法/缩写）\n"
    "SIBLING（兄弟：同一上位概念下的并列概念，彼此不包含）\n"
    "OTHER（其他：以上都不是）\n"
    "判定依据的优先顺序（**必须遵守**）：\n"
    "1. evidence 若是原文句子，以该句为准；\n"
    "2. evidence 若写着「同一章节内相邻知识点」，那**只是位置信息、不是语义证据**——"
    "此时只能依据两者的名称与说明来判断，**严禁**因为“相邻”“顺序先后”就判 SIBLING 或 PREREQUISITE；\n"
    "3. **说明不足以判断时必须判 OTHER**。OTHER 是合规答案，不得为了让每题都有“结论”而强行选前四类；\n"
    "4. 只输出 JSON 数组，不得输出推理过程；reason 用一句不超过 30 字的判定依据。\n"
    "   **输入有几对，输出就必须有几条**，一一对应。\n"
    '格式示例：[{"source_id":1,"target_id":2,"type":"INCLUDES","confidence":0.8,"reason":"B 是 A 的组成部分"}]\n\n'
    "待判定：\n{pairs}"
)


def _normalize_class(value) -> str:
    """把模型返回的关系词归一到五类之一；无法识别时返回 OTHER。"""
    if not isinstance(value, str):
        return RELATION_OTHER
    text = value.strip()
    if text in _CLASS_ALIASES:
        return _CLASS_ALIASES[text]
    return _CLASS_ALIASES.get(text.upper(), RELATION_OTHER)


def _coerce_confidence(value, default: float = 0.5) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return default
    if number > 1.0:   # 模型偶尔给百分数
        number = number / 100.0
    return round(max(0.0, min(1.0, number)), 2)


def classify_pairs(nodes, pairs, llm_call, *, batch_size: int = 5,
                   max_pairs=None) -> list[dict]:
    """用 LLM 把关系候选判成论文五类。

    参数：
        nodes:    list[dict]，每项至少 ``id`` 与 ``name``，可选 ``content``/``description``。
        pairs:    list[dict]，通常来自 :func:`infer_implicit_relations`（含 source_id/target_id）。
        llm_call: ``llm_call(prompt, system) -> str``；返回不可解析时该批回退为规则类别。
        batch_size: 每次 LLM 调用判几对（≤5 时模型稳定性最好，实测 B6）。
        max_pairs: 非空时只判前 N 对。

    返回 [{"source_id","source_name","target_id","target_name","relation_type",
            "confidence","reason","evidence","method"}]，relation_type ∈ FIVE_CLASSES。
    无法识别的类别记为 OTHER；同一对只保留一条（按出现顺序）。
    """
    valid_nodes = _valid_nodes(nodes)
    if len(valid_nodes) < 2 or not isinstance(pairs, (list, tuple)) or not callable(llm_call):
        return []
    by_id = {node["id"]: node for node in valid_nodes}

    cleaned: list[dict] = []
    seen: set[tuple[int, int]] = set()
    for item in pairs:
        if not isinstance(item, dict):
            continue
        source_id = item.get("source_id")
        target_id = item.get("target_id")
        if not _is_int(source_id) or not _is_int(target_id):
            continue
        if source_id == target_id or source_id not in by_id or target_id not in by_id:
            continue
        key = (source_id, target_id)
        if key in seen:
            continue
        seen.add(key)
        cleaned.append(item)
    if _is_int(max_pairs) and max_pairs >= 0:
        cleaned = cleaned[:max_pairs]
    if not cleaned:
        return []

    size = max(1, min(8, int(batch_size or 5)))
    out: list[dict] = []
    for start in range(0, len(cleaned), size):
        chunk = cleaned[start:start + size]
        payload = []
        for pair in chunk:
            source = by_id[pair["source_id"]]
            target = by_id[pair["target_id"]]
            payload.append({
                "source_id": pair["source_id"],
                "source_name": source.get("name", ""),
                "source_desc": (source.get("description") or source.get("content") or "")[:200],
                "target_id": pair["target_id"],
                "target_name": target.get("name", ""),
                "target_desc": (target.get("description") or target.get("content") or "")[:200],
                "evidence": (pair.get("evidence") or "")[:80],
            })
        prompt = _CLASS_PROMPT.replace("{pairs}", json.dumps(payload, ensure_ascii=False))
        try:
            raw = llm_call(prompt, _CLASS_SYSTEM)
        except Exception:
            raw = ""
        # 与 ipa.py 同样的容错解析（避免两处各写一份：直接复用其实现）
        verdicts = _loads_lenient(raw)
        if isinstance(verdicts, dict):
            verdicts = verdicts.get("pairs") or verdicts.get("items") or []
        matched: dict[tuple[int, int], dict] = {}
        if isinstance(verdicts, list):
            for verdict in verdicts:
                if not isinstance(verdict, dict):
                    continue
                source_id = verdict.get("source_id")
                target_id = verdict.get("target_id")
                try:
                    source_id = int(source_id)
                    target_id = int(target_id)
                except (TypeError, ValueError):
                    continue
                matched[(source_id, target_id)] = verdict

        for pair in chunk:
            source = by_id[pair["source_id"]]
            target = by_id[pair["target_id"]]
            verdict = matched.get((pair["source_id"], pair["target_id"]))
            if verdict:
                relation_type = _normalize_class(verdict.get("type") or verdict.get("relation"))
                confidence = _coerce_confidence(verdict.get("confidence"))
                reason = str(verdict.get("reason") or "").strip()[:120]
                method = "IREM-LLM"
            else:
                # LLM 未覆盖该对（解析失败/漏答）：回退为规则给出的类别，不让候选凭空消失
                relation_type = _normalize_class(pair.get("relation_type"))
                confidence = _coerce_confidence(pair.get("score"), default=0.5)
                reason = ""
                method = "IREM-RULE"
            out.append({
                "source_id": pair["source_id"],
                "source_name": source.get("name", ""),
                "target_id": pair["target_id"],
                "target_name": target.get("name", ""),
                "relation_type": relation_type,
                "confidence": confidence,
                "reason": reason,
                "evidence": (pair.get("evidence") or "")[:EVIDENCE_MAX_CHARS],
                "method": method,
            })
    return out

