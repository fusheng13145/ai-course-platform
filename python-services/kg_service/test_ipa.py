"""IPA 抽取器的确定性单测：LLM 全部用假实现，不联网、不依赖模型。"""
from __future__ import annotations

import json

import ipa


def fake_llm_factory(stage1_payload=None, stage2_payload=None, raw_stage2=None):
    """按提示词区分阶段的假 LLM：阶段一返回术语数组，阶段二返回规范化条目。"""
    def _call(prompt: str, system: str | None = None) -> str:
        if "值得作为独立知识点" in prompt:
            if stage1_payload is None:
                return "抱歉，我无法处理。"
            return json.dumps(stage1_payload, ensure_ascii=False)
        if "整理成课程知识点" in prompt:
            if raw_stage2 is not None:
                return raw_stage2
            if stage2_payload is None:
                return "not-json"
            return json.dumps(stage2_payload, ensure_ascii=False)
        return ""
    return _call


PARA = ("协议是通信双方事先约定并共同遵守的通信规则。分层把复杂的通信过程拆成可独立替换的层次。"
        "OSI 参考模型自底向上分为七层，第三层是网络层，负责寻址与路由。")


def test_split_sentences_and_paragraphs():
    assert ipa.split_sentences("甲。乙！丙？") == ["甲", "乙", "丙"]
    assert ipa.split_sentences("") == []
    assert ipa.split_paragraphs(["短", PARA, PARA, "   "]) == [PARA]


def test_stage1_identify_parses_json_and_drops_fabricated_evidence():
    llm = fake_llm_factory([
        {"term": "协议", "evidence": "协议是通信双方事先约定并共同遵守的通信规则"},
        {"term": "会话层", "evidence": "这句话原文里没有"},   # 编造证据 → 证据清空但术语保留
        {"term": "协议", "evidence": "重复术语只留一条"},
    ])
    out = ipa.stage1_identify(PARA, llm)
    assert [x["term"] for x in out] == ["协议", "会话层"]
    assert out[0]["evidence"].startswith("协议是通信双方")
    assert out[1]["evidence"] == ""


def test_stage1_identify_tolerates_fence_and_garbage():
    llm = fake_llm_factory()
    llm2 = lambda prompt, system=None: '说明如下：\n```json\n[{"term":"分层","evidence":"分层把复杂的通信过程拆成可独立替换的层次"}]\n```\n以上。'
    out = ipa.stage1_identify(PARA, llm2)
    assert out and out[0]["term"] == "分层"


def test_stage1_identify_handles_unparseable_output():
    assert ipa.stage1_identify(PARA, fake_llm_factory(None)) == []
    assert ipa.stage1_identify("", fake_llm_factory([{"term": "x"}])) == []
    assert ipa.stage1_identify(PARA, None) == []


def test_stage2_normalize_reads_aliases_and_evidence():
    llm = fake_llm_factory(stage2_payload=[
        {"name": "OSI 参考模型", "description": "七层体系结构，自底向上分层。",
         "aliases": ["开放系统互连参考模型"], "evidence": "OSI 参考模型自底向上分为七层"},
        {"name": "", "description": "空名字应被丢弃"},
    ])
    out = ipa.stage2_normalize([{"term": "OSI", "evidence": ""}], llm)
    assert len(out) == 1
    assert out[0]["aliases"] == ["开放系统互连参考模型"]


def test_extract_two_stages_end_to_end():
    stage1 = [{"term": "协议", "evidence": "协议是通信双方事先约定并共同遵守的通信规则"},
              {"term": "分层", "evidence": "分层把复杂的通信过程拆成可独立替换的层次"}]
    stage2 = [{"name": "协议", "description": "通信双方约定的规则，含语法语义时序。",
               "aliases": [], "evidence": "协议是通信双方事先约定并共同遵守的通信规则"},
              {"name": "分层", "description": "把复杂通信拆成可替换的层次。",
               "aliases": [], "evidence": "分层把复杂的通信过程拆成可独立替换的层次"}]
    llm = fake_llm_factory(stage1, stage2)
    points = ipa.extract_knowledge_points([PARA], llm, max_points=10)
    assert {p["name"] for p in points} == {"协议", "分层"}
    assert all(p["method"] == "IPA" for p in points)
    assert all(0.5 <= p["confidence"] <= 1.0 for p in points)


def test_extract_falls_back_when_stage2_fails():
    stage1 = [{"term": "网络层", "evidence": "第三层是网络层"}]
    llm = fake_llm_factory(stage1, None)   # 阶段二始终返回非 JSON
    points = ipa.extract_knowledge_points([PARA], llm)
    assert points and points[0]["name"] == "网络层"
    assert points[0]["description"] == ""
    assert points[0]["confidence"] == 0.5


def test_extract_returns_empty_on_no_llm_or_no_paragraphs():
    assert ipa.extract_knowledge_points([], lambda p, s=None: "[]") == []
    assert ipa.extract_knowledge_points([PARA], None) == []
    assert ipa.extract_knowledge_points([PARA], fake_llm_factory(None)) == []


def test_chapter_mapping_is_deterministic_and_aligned():
    paragraphs = [PARA, PARA + "补充段落甲。", PARA + "补充段落乙。"]
    chapters = [{"id": 7003079, "title": "计算机网络概述"}, {"id": 7003080, "title": "物理层"}]
    llm = fake_llm_factory(
        [{"term": "协议", "evidence": "协议是通信双方事先约定并共同遵守的通信规则"}],
        [{"name": "协议", "description": "规则。", "aliases": [], "evidence": ""}])
    points = ipa.extract_knowledge_points(paragraphs, llm, chapters=chapters)
    assert points and points[0]["chapter_id"] in (7003079, 7003080)


def test_loads_lenient_repairs_single_quotes():
    raw = "{'name': '协议', 'description': '规则'}"
    assert ipa._loads_lenient(raw) == {"name": "协议", "description": "规则"}
