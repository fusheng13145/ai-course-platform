"""P1-1 量具：审计脚本的「结构源」必须指到现存文件，缺失时**点名失败**而不是抛裸异常。

背景：2026-11-04 前端从 `src/v2` 迁到 `src/ui` 后，`audit-stats.py` 仍硬编码
`frontend/src/router/index.ts`，于是每次跑都以 FileNotFoundError 收场——栈里只有路径，
看不出是「源搬家了」还是「口径坏了」。更危险的是另一种处理方式：静默返回 0，
那样手册的结构计数会无声变假，而没人会去核实一个 0。

本文件把两件事变成可复跑判据：
1. 缺失时抛的是带标签与处理指引的 SystemExit（不是 FileNotFoundError）；
2. 路由统计源在**当前仓库**里真实存在，且 `path:` 字面量计数大于 0（=统计面没空转）。
"""
import importlib.util
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "audit_stats", ROOT / "scripts" / "audit-stats.py")
audit_stats = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_stats)


def test_read_source_fails_loudly_with_label(tmp_path):
    """文件缺失时必须给出「哪个统计项 + 期望路径 + 处理方式」，而不是裸 FileNotFoundError。"""
    missing = tmp_path / "router.ts"
    with pytest.raises(SystemExit) as exc:
        audit_stats.read_source(str(missing), "前端路由表")
    msg = str(exc.value)
    assert "前端路由表" in msg          # 点名是哪个统计项
    assert "router.ts" in msg           # 点名期望路径
    assert "不要用 0" in msg            # 明确禁止静默返回 0


def test_read_source_returns_text_when_present(tmp_path):
    f = tmp_path / "ok.ts"
    f.write_text("path: '/x',", encoding="utf-8")
    assert "path: '/x'" in audit_stats.read_source(str(f), "样例源")


def test_route_source_exists_and_counts_literals():
    """路由源必须落在候选列表里且真的存在；计数为 0 说明正则与写法脱节。"""
    front = ROOT / "frontend"
    existing = [p for p in audit_stats.FRONT_ROUTE_SOURCES if (front / p).exists()]
    assert existing, (
        "前端路由源一个都不存在，说明目录又迁移了："
        f"候选={audit_stats.FRONT_ROUTE_SOURCES}"
    )
    src = audit_stats.read_source(str(front / existing[0]), "前端路由表")
    assert src.count("path:") > 0
    assert len(audit_stats.re.findall(r"\bpath:\s*['\"`]", src)) > 0
