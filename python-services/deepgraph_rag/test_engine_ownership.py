# -*- coding: utf-8 -*-
"""
自研归属守卫（第三方内容清零）
==============================
守三件事，都属于「这个项目必须完全属于我们」的硬约束：

1. `config.py` / `backend.py` 不再依赖 Streamlit、不再内置任何口令回退值。
2. 生成层四个符号由本项目实现提供（`api_server` 依赖这份契约），且全链路带超时。
3. 实体抽取在模型不可用时必须退回规则抽取（不能因为模型掉线让图谱链路整段失效）。
"""

from __future__ import annotations

import inspect
import re
from pathlib import Path

import pytest

import backend
import config

_HERE = Path(__file__).resolve().parent

# 常见「看起来像真实口令」的硬编码形态：键值对里直接写死字符串
_HARDCODED_PASSWORD = re.compile(
    r"NEO4J_PASSWORD[\"']?\s*,\s*[\"'][^\"']{6,}[\"']"
)


@pytest.mark.parametrize("module", ["config.py", "backend.py"])
def test_owned_modules_do_not_import_streamlit(module):
    src = (_HERE / module).read_text(encoding="utf-8")
    # 文档字符串里可以提到 streamlit（说明为什么去掉），但不得出现 import
    code_lines = [
        line for line in src.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    assert not any(re.match(r"\s*(import|from)\s+streamlit", line) for line in code_lines), (
        f"{module} 不得再 import streamlit：8501 是 FastAPI 服务，不需要 UI 依赖"
    )


def test_config_has_no_hardcoded_password():
    src = (_HERE / "config.py").read_text(encoding="utf-8")
    assert not _HARDCODED_PASSWORD.search(src), "config.py 不得内置数据库口令回退值"
    # 默认值必须是空串，缺口令由 neo4j_configured() 报告未配置
    assert "NEO4J_PASSWORD = os.getenv(\"NEO4J_PASSWORD\", \"\")" in src


def test_neo4j_configured_requires_password(monkeypatch):
    monkeypatch.setenv("NEO4J_URI", "bolt://localhost:7687")
    monkeypatch.delenv("NEO4J_PASSWORD", raising=False)
    import importlib

    importlib.reload(config)
    assert config.neo4j_configured() is False, "缺口令必须判定为未配置，而不是用错误口令反复重试"

    monkeypatch.setenv("NEO4J_PASSWORD", "unit-test-password")
    importlib.reload(config)
    assert config.neo4j_configured() is True
    importlib.reload(config)


def test_provider_resolution_prefers_explicit_then_key(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "ollama")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "sk-test")
    import importlib

    importlib.reload(config)
    assert config.ai_provider() == "ollama", "显式 AI_PROVIDER 优先于密钥推断"

    monkeypatch.delenv("AI_PROVIDER", raising=False)
    importlib.reload(config)
    assert config.ai_provider() == "deepseek", "有 DEEPSEEK_API_KEY 时应解析为 deepseek"

    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    importlib.reload(config)
    assert config.ai_provider() == "ollama", "无密钥时回落 ollama"
    importlib.reload(config)


def test_backend_exposes_contract_symbols():
    for name in ("generate_llm_response", "extract_entities_from_prompt", "get_provider", "get_provider_name", "call_llm"):
        assert callable(getattr(backend, name)), f"生成层契约符号缺失: {name}"
    # 全链路必须带 timeout（阶段2 S2-D05 的复发防护）
    src = inspect.getsource(backend)
    assert src.count("timeout=config.LLM_TIMEOUT_SECONDS") >= 2, "生成后端调用必须统一带超时"


def test_generate_llm_response_prompt_shape(monkeypatch):
    captured = {}

    def _fake_call(prompt, system_prompt=None, temperature=0.2):
        captured["prompt"] = prompt
        captured["system"] = system_prompt
        return "答案"

    monkeypatch.setattr(backend, "call_llm", _fake_call)

    answer, sys_prompt = backend.generate_llm_response("什么是虚拟内存", "【资料1】分页与缺页中断")
    assert answer == "答案"
    assert "上下文信息" in sys_prompt and "分页与缺页中断" in sys_prompt
    assert captured["prompt"] == "什么是虚拟内存"

    answer2, sys_prompt2 = backend.generate_llm_response("什么是虚拟内存", "")
    assert answer2 == "答案"
    assert "上下文信息" not in sys_prompt2, "无上下文时不得伪造上下文段落"


def test_extract_entities_falls_back_when_llm_unavailable(monkeypatch):
    def _boom(*_a, **_k):
        raise RuntimeError("ollama down")

    monkeypatch.setattr(backend, "call_llm", _boom)
    entities = backend.extract_entities_from_prompt("虚拟内存和分页机制有什么区别", limit=6)
    assert entities, "模型不可用时必须退回规则抽取，不能返回空列表"
    assert any("分页" in e or "虚拟内存" in e for e in entities), f"规则抽取结果不合预期: {entities}"


def test_extract_entities_rejects_empty_prompt():
    assert backend.extract_entities_from_prompt("") == []
    assert backend.extract_entities_from_prompt("   ") == []
