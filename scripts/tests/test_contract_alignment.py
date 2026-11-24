# -*- coding: utf-8 -*-
"""`scripts/check-contract-alignment.py` 的守卫测试。

为什么需要：那个脚本的正则是**文本解析**，写错就会恒绿（什么都对不上/什么都对上）。
这里用一条真实数据断言 + 两条阴性对照，钉住"能对齐、能抓到缺口、参数名归一不乱判"。
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def _load():
    spec = importlib.util.spec_from_file_location(
        "cca", ROOT / "scripts" / "check-contract-alignment.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


cca = _load()


def test_repository_contracts_are_aligned():
    """当前仓库：Java 声明的端点必须全部能在 Python 路由里找到。"""
    java = cca.collect_java()
    py = cca.collect_python()

    # 阳性对照：两侧都真的解析出了内容，否则"零缺口"可能是空集造成的假绿。
    assert len(java) >= 20, "没解析到 Java 契约常量，正则失效"
    assert len(py) >= 30, "没解析到 Python 路由，正则失效"

    missing = [(n, p) for n, p in java.items() if cca.normalize(p) not in py]
    assert missing == [], f"存在只声明未实现的端点：{missing}"


def test_detector_catches_missing_endpoint():
    """阴性对照：塞一个 Python 里不存在的路径，必须被判成缺口。"""
    java = dict(cca.collect_java())
    java["FAKE_NOT_IMPLEMENTED"] = "/api/v1/rag/not-exist"
    py = cca.collect_python()

    missing = [n for n, p in java.items() if cca.normalize(p) not in py]
    assert missing == ["FAKE_NOT_IMPLEMENTED"]


def test_normalize_treats_parameter_names_as_the_same_position():
    """参数名不同不算漂移（Java 用 courseId、Python 用 course_id），位置数量不同才算。"""
    assert cca.normalize("/api/v1/rag/index/build/{courseId}") == \
        cca.normalize("/api/v1/rag/index/build/{course_id}")
    assert cca.normalize("/api/v1/deepgraph/documents/{documentName}/") == \
        cca.normalize("/api/v1/deepgraph/documents/{document_name}")
    assert cca.normalize("/a/{x}/{y}") != cca.normalize("/a/{x}")


def test_test_stubs_are_excluded_from_python_side():
    """契约测试里的桩 app 也声明路由；若算进实现侧，会掩盖真实缺口。"""
    files = cca.python_files()
    assert files, "没扫到任何 Python 源文件"
    assert all(not Path(f).name.startswith("test_") for f in files), \
        f"测试桩被当成实现侧：{[f for f in files if Path(f).name.startswith('test_')]}"
