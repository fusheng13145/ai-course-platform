"""IREM 五类判定的确定性单测：LLM 全部用假实现，不联网。"""
from __future__ import annotations

import json

import irem

NODES = [
    {"id": 1, "name": "网络层", "description": "负责寻址与路由。"},
    {"id": 2, "name": "IP 协议", "description": "网络层核心协议，负责编址与转发。"},
    {"id": 3, "name": "传输层", "description": "负责端到端传输。"},
    {"id": 4, "name": "TCP", "description": "传输层的可靠传输协议。"},
]
PAIRS = [
    {"source_id": 1, "target_id": 2, "relation_type": "INCLUDES", "score": 0.8, "evidence": "网络层包含 IP。"},
    {"source_id": 1, "target_id": 3, "relation_type": "RELATED", "score": 0.4, "evidence": "两层相邻。"},
    {"source_id": 2, "target_id": 4, "relation_type": "RELATED", "score": 0.3, "evidence": "协议族。"},
]


def test_five_classes_are_declared():
    assert set(irem.FIVE_CLASSES) == {"INCLUDES", "PREREQUISITE", "SAME", "SIBLING", "OTHER"}
    assert irem.RELATION_SAME == "SAME" and irem.RELATION_SIBLING == "SIBLING"


def test_classify_pairs_maps_llm_verdicts():
    verdicts = [
        {"source_id": 1, "target_id": 2, "type": "INCLUDES", "confidence": 0.9, "reason": "IP 属网络层"},
        {"source_id": 1, "target_id": 3, "type": "SIBLING", "confidence": 0.7, "reason": "并列层"},
        {"source_id": 2, "target_id": 4, "type": "OTHER", "confidence": 0.5, "reason": "不同层次"},
    ]
    llm = lambda prompt, system=None: json.dumps(verdicts, ensure_ascii=False)
    out = irem.classify_pairs(NODES, PAIRS, llm, batch_size=5)
    assert [item["relation_type"] for item in out] == ["INCLUDES", "SIBLING", "OTHER"]
    assert out[0]["method"] == "IREM-LLM"
    assert out[0]["confidence"] == 0.9
    assert out[1]["source_name"] == "网络层" and out[1]["target_name"] == "传输层"


def test_chinese_class_names_are_normalized():
    verdicts = [{"source_id": 1, "target_id": 3, "type": "兄弟", "confidence": 80}]
    llm = lambda prompt, system=None: json.dumps(verdicts, ensure_ascii=False)
    out = irem.classify_pairs(NODES, [PAIRS[1]], llm)
    assert out[0]["relation_type"] == "SIBLING"
    assert out[0]["confidence"] == 0.8   # 百分数被归一


def test_missing_verdict_falls_back_to_rule_class():
    # 模型只答了 1 对，另外 2 对必须回退为规则类别（不能凭空消失）
    verdicts = [{"source_id": 1, "target_id": 2, "type": "INCLUDES", "confidence": 0.9}]
    llm = lambda prompt, system=None: json.dumps(verdicts, ensure_ascii=False)
    out = irem.classify_pairs(NODES, PAIRS, llm)
    assert len(out) == 3
    assert out[1]["relation_type"] == "OTHER"      # 规则的 RELATED 归 OTHER
    assert out[1]["method"] == "IREM-RULE"
    assert out[2]["method"] == "IREM-RULE"


def test_llm_exception_degrades_to_rule():
    def boom(prompt, system=None):
        raise RuntimeError("model down")
    out = irem.classify_pairs(NODES, PAIRS, boom)
    assert len(out) == 3
    assert all(item["method"] == "IREM-RULE" for item in out)


def test_invalid_inputs_return_empty():
    assert irem.classify_pairs([], PAIRS, lambda p, s=None: "[]") == []
    assert irem.classify_pairs(NODES, [], lambda p, s=None: "[]") == []
    assert irem.classify_pairs(NODES, PAIRS, None) == []
    # 自环与未知 id 被过滤
    bad = [{"source_id": 1, "target_id": 1}, {"source_id": 1, "target_id": 99}]
    assert irem.classify_pairs(NODES, bad, lambda p, s=None: "[]") == []


def test_duplicate_pairs_and_max_pairs():
    dup = [PAIRS[0], dict(PAIRS[0]), PAIRS[1]]
    llm = lambda prompt, system=None: "not-json"
    out = irem.classify_pairs(NODES, dup, llm)
    assert len(out) == 2      # 同一对只保留一条
    assert len(irem.classify_pairs(NODES, dup, llm, max_pairs=1)) == 1
