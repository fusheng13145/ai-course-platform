"""生成层（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

本模块只提供 8501 适配层真正调用的能力，不再承载任何 UI 逻辑：

* `generate_llm_response(prompt, context)`  —— 组装系统提示并返回 `(答案, 提示词)`
* `extract_entities_from_prompt(prompt)`    —— 从提问里抽实体（供图谱上下文检索）
* `build_chapter_graph_backend(...)`        —— 章节三元组抽取（转 `utils/build_graph`）
* `get_provider()` / `get_provider_name()`  —— 供 `/health` 透出当前真实供应商

设计取舍：

1. **全链路超时**：Ollama / OpenAI 兼容接口都显式带 `timeout`，避免一次挂死拖垮
   整个检索链路（阶段2 缺陷清单里的超时缺失问题）。
2. **双通道**：`deepseek|openai` 走 OpenAI 兼容 HTTP（各用各自 base/model/key），
   `ollama` 走本机 `/api/generate`。缺密钥时回落 Ollama，而不是直接报错。
3. **确定性兜底**：实体抽取在模型不可用时改用 jieba/正则规则，图谱上下文检索
   因此不会因为一次模型抖动整段失效。
"""

from __future__ import annotations

import json
import re
from typing import Any, Callable, List, Optional, Tuple

import requests

import config

_HAS_JIEBA = False
try:  # jieba 缺失只损失中文分词质量，不影响可用性
    import jieba

    _HAS_JIEBA = True
except Exception:  # pragma: no cover - 依赖缺失路径
    jieba = None  # type: ignore[assignment]

_ENTITY_NOISE = re.compile(r"[，。！？；：、（）()\[\]【】《》“”\"'`~ \n\r\t]+")
_QUESTION_WORDS = (
    "什么", "怎么", "如何", "为什么", "是否", "哪些", "哪个", "请问", "请", "介绍",
    "解释", "说明", "一下", "可以", "能否", "有没有", "多少", "区别", "关系",
)
_STOP_ENTITIES = {"问题", "内容", "方法", "系统", "时候", "东西", "我们", "你们", "它们"}


# ---------------------------------------------------------------- 供应商
def get_provider() -> str:
    return config.ai_provider()


def get_provider_name() -> str:
    return config.describe_provider()


def _openai_credentials() -> Tuple[str, str, str]:
    """按供应商取 (base_url, model, api_key)：deepseek 与 openai 不共用配置项。"""
    if config.ai_provider() == "deepseek":
        return config.DEEPSEEK_BASE_URL, config.DEEPSEEK_MODEL, config.DEEPSEEK_API_KEY
    return config.OPENAI_BASE_URL, config.OPENAI_MODEL, config.OPENAI_API_KEY


# ---------------------------------------------------------------- 调用
def _ollama_chat(prompt: str, system_prompt: Optional[str], temperature: float) -> str:
    payload: dict = {
        "model": config.MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": temperature},
    }
    if system_prompt:
        payload["system"] = system_prompt
    response = requests.post(
        config.OLLAMA_API_URL,
        json=payload,
        timeout=config.LLM_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return str((response.json() or {}).get("response") or "").strip()


def _openai_chat(prompt: str, system_prompt: Optional[str], temperature: float) -> str:
    base_url, model, api_key = _openai_credentials()
    if not (base_url and model and api_key):
        raise RuntimeError(
            "OpenAI 兼容通道配置不完整（需 base_url + model + api_key，"
            "deepseek 用 DEEPSEEK_*，openai 用 OPENAI_*）"
        )
    messages: List[dict] = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    response = requests.post(
        f"{base_url.rstrip('/')}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={"model": model, "messages": messages, "temperature": temperature, "stream": False},
        timeout=config.LLM_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    choices = (response.json() or {}).get("choices") or []
    if not choices:
        return ""
    return str((choices[0].get("message") or {}).get("content") or "").strip()


def call_llm(prompt: str, system_prompt: Optional[str] = None, temperature: float = 0.2) -> str:
    """统一生成入口：按 `config.ai_provider()` 选通道，异常原样上抛由调用方兜底。"""
    if config.ai_provider() == "ollama":
        return _ollama_chat(prompt, system_prompt, temperature)
    return _openai_chat(prompt, system_prompt, temperature)


# ---------------------------------------------------------------- 问答
_SYSTEM_BASE = (
    "你是课程知识助教，只依据给定资料回答问题；资料没有覆盖的内容要明确说明"
    "“课程资料中未涉及”，不得编造。回答使用中文，条理清晰，必要时分点。"
)
_SYSTEM_WITH_CONTEXT = _SYSTEM_BASE + "\n\n以下是本次问答可用的上下文信息：\n{context}"


def generate_llm_response(prompt: str, context: str) -> Tuple[str, str]:
    """返回 `(答案, 实际使用的系统提示词)`；`context` 为空时不伪造上下文段落。"""
    context = (context or "").strip()
    if context:
        system_prompt = _SYSTEM_WITH_CONTEXT.format(context=context)
    else:
        system_prompt = _SYSTEM_BASE
    answer = call_llm(prompt, system_prompt=system_prompt, temperature=0.2)
    return answer, system_prompt


# ---------------------------------------------------------------- 实体抽取
def _strip_decorations(text: str) -> str:
    return re.sub(r"^\s*(?:问题|提问|请问)\s*[:：]\s*", "", text or "").strip()


def _fallback_entities(prompt: str, limit: int) -> List[str]:
    """规则抽取：中文分词 + 长度过滤 + 疑问词剔除，结果确定可复现。"""
    text = _strip_decorations(prompt)
    if not text:
        return []
    tokens = [t.strip() for t in _ENTITY_NOISE.split(text) if t.strip()]
    if _HAS_JIEBA:
        words: List[str] = []
        for token in tokens:
            words.extend([w for w in jieba.cut(token) if w.strip()])
        tokens = words
    seen: List[str] = []
    for token in tokens:
        if len(token) < 2 or token.isdigit() or token in _QUESTION_WORDS or token in _STOP_ENTITIES:
            continue
        if token not in seen:
            seen.append(token)
    return seen[:limit]


_ENTITY_PROMPT = (
    "从下面这段提问中抽取用于知识图谱检索的实体名词，只输出 JSON 数组，"
    "不要输出任何解释。最多 {limit} 个，保留专业术语与课程概念。\n\n提问：{prompt}"
)


def _parse_entity_json(raw: str) -> List[str]:
    if not raw:
        return []
    match = re.search(r"\[[\s\S]*?\]", raw)
    if not match:
        return []
    try:
        data = json.loads(match.group(0))
    except Exception:
        return []
    if not isinstance(data, list):
        return []
    out: List[str] = []
    for item in data:
        if isinstance(item, str):
            value = item.strip()
        elif isinstance(item, dict):
            value = str(item.get("name") or item.get("entity") or "").strip()
        else:
            value = ""
        if value and value not in out:
            out.append(value)
    return out


def extract_entities_from_prompt(prompt: str, limit: int = 8) -> List[str]:
    if not (prompt or "").strip():
        return []
    try:
        raw = call_llm(
            _ENTITY_PROMPT.format(limit=limit, prompt=prompt.strip()),
            system_prompt="你只输出 JSON 数组，不输出其它任何字符。",
            temperature=0.0,
        )
    except Exception as exc:  # 模型不可用：按既定口径退回规则抽取
        print(f"[deepgraph] 实体抽取调用模型失败，改用规则抽取: {type(exc).__name__}: {exc}")
        return _fallback_entities(prompt, limit)
    candidates = _parse_entity_json(raw)
    if not candidates:
        return _fallback_entities(prompt, limit)
    return candidates[:limit]


# ---------------------------------------------------------------- 章节图谱
def build_chapter_graph_backend(
    chapter_name: str,
    chapter_text: str,
    progress_callback: Optional[Callable[[str, str, str], None]] = None,
) -> Any:
    """章节三元组抽取：实现位于 `utils/build_graph.py`，此处只做稳定入口。"""
    from utils.build_graph import build_chapter_knowledge_graph

    return build_chapter_knowledge_graph(chapter_name, chapter_text, progress_callback)
