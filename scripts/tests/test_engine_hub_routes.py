# -*- coding: utf-8 -*-
"""Engine Hub 前缀覆盖守卫。

为什么需要：端口统一后 `engine_hub/hub.py` 成为**所有 Python 引擎的唯一入口**，
它按 `/api/v1/<segment>` 前缀分发。Java 契约（ContractPaths.java）声明了哪些 segment，
hub 就必须有对应前缀——否则请求打进来 404，而：
- Java 侧契约测试全绿（它只 mock HTTP 客户端，不知道 hub 的分发表）；
- 各引擎自己的契约测试也全绿（它们直接 import app，绕过了 hub）；
- hub 的契约测试用手写前缀，同样不会发现"Java 声明了但 hub 没接"。

三类测试都覆盖不到这条缝，所以在这里单独钉住。

口径：只比"前缀段"（`/api/v1/<segment>` 的第一段），不比完整路径——完整路径由 §4B 的
`check-contract-alignment.py` 负责（Java 端点 ↔ Python 路由）。
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HUB = ROOT / "python-services" / "engine_hub" / "hub.py"
CONTRACT = ROOT / "backend" / "src" / "main" / "java" / "com" / "aicourse" / "integration" / "ContractPaths.java"

HUB_PREFIX_RE = re.compile(r'\("(/api/v1/[^"]+)",\s*"[^"]+",\s*"[^"]+"\)')
JAVA_PATH_RE = re.compile(r'public static final String [A-Z0-9_]+\s*=\s*"(/api/v1/([^"/]+)[^"]*)"')


def hub_prefixes() -> set:
    text = HUB.read_text(encoding="utf-8")
    found = set(HUB_PREFIX_RE.findall(text))
    assert found, "hub.py 里没解析到 _ROUTES 前缀，正则可能失效"
    return found


def java_segments() -> set:
    text = CONTRACT.read_text(encoding="utf-8")
    found = {m.group(2) for m in JAVA_PATH_RE.finditer(text)}
    assert found, "ContractPaths.java 里没解析到 /api/v1 端点，正则可能失效"
    return found


def missing_prefixes(java_seg: set, hub_pre: set) -> set:
    """返回 Java 用到、但 hub 没有前缀的 segment。"""
    covered = {p.rstrip("/").split("/")[3] for p in hub_pre if len(p.split("/")) > 3}
    return {s for s in java_seg if s not in covered}


def test_every_java_engine_segment_is_served_by_hub():
    """Java 契约里的每个引擎 segment，hub 都必须有分发前缀。"""
    seg, pre = java_segments(), hub_prefixes()

    # 阳性对照：两侧都真的解析出了内容，否则"零缺失"可能是空集假绿。
    assert len(seg) >= 4, f"Java 端点 segment 少于 4 个，解析可能失效：{seg}"
    assert len(pre) >= 4, f"hub 分发前缀少于 4 个，解析可能失效：{pre}"

    miss = missing_prefixes(seg, pre)
    assert miss == set(), (
        f"以下 segment 在 Java 契约里被调用，但 hub 没有对应前缀（请求会 404）：{sorted(miss)}\n"
        f"hub 现有前缀：{sorted(pre)}"
    )


def test_detector_catches_missing_prefix():
    """阴性对照：抽掉一个前缀后，检测器必须报缺失。"""
    seg, pre = java_segments(), hub_prefixes()
    broken = {p for p in pre if p != "/api/v1/graph"}
    assert missing_prefixes(seg, broken) == {"graph"}


def test_detector_catches_unknown_new_segment():
    """阴性对照：Java 新增一个 hub 尚未接入的引擎 segment，必须被抓到。"""
    seg, pre = java_segments(), hub_prefixes()
    assert missing_prefixes(seg | {"vision"}, pre) == {"vision"}
