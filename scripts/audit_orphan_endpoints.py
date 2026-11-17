#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描后端 Controller 端点，并在前端/ e2e 目录里找调用痕迹。

用法：
    python scripts/audit_orphan_endpoints.py
    python scripts/audit_orphan_endpoints.py --format json

判据：端点路径（去掉路径变量后）在前端 `src/` 或 `e2e/`、`scripts/e2e/` 中出现即视为有调用方。
注意：这是**线索级**审计，不是运行时调用图；动态拼接路径会漏，需人工复核。
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTROLLER_DIR = ROOT / "backend" / "src" / "main" / "java" / "com" / "aicourse" / "controller"
SEARCH_DIRS = [ROOT / "frontend" / "src", ROOT / "frontend" / "e2e", ROOT / "scripts" / "e2e"]

MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"|$)')
CLASS_RE = re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"')
SKIP_FILES = {"HealthController.java"}  # 健康检查由运维/监控直接访问，不算孤儿


def controller_endpoints() -> list[dict]:
    rows = []
    for path in sorted(CONTROLLER_DIR.glob("*.java")):
        if path.name in SKIP_FILES:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        class_match = CLASS_RE.search(text)
        base = class_match.group(1) if class_match else ""
        for line_no, line in enumerate(text.splitlines(), 1):
            m = MAPPING_RE.search(line)
            if not m:
                continue
            method = m.group(1).upper()
            sub = m.group(2) if m.group(2) is not None else ""
            full = (base + sub).replace("//", "/")
            if not full:
                full = "/"
            rows.append({
                "controller": path.stem,
                "line": line_no,
                "method": method,
                "path": full,
            })
    return rows


def search_corpus() -> list[tuple[str, str]]:
    corpus = []
    for root in SEARCH_DIRS:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".ts", ".vue", ".js", ".py", ".json", ".md"}:
                continue
            if "node_modules" in p.parts:
                continue
            try:
                corpus.append((str(p.relative_to(ROOT)), p.read_text(encoding="utf-8", errors="replace")))
            except OSError:
                continue
    return corpus


def normalize_for_search(path: str) -> list[re.Pattern]:
    """把 Controller 路径转成可匹配前端模板字符串的正则（路径变量 → .*）。"""
    pattern = re.sub(r"\{[^}]+\}", ".*", path)
    pattern = re.sub(r"//+", "/", pattern).rstrip("/")
    if not pattern:
        return []
    return [re.compile(pattern)]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--format", choices=["table", "json"], default="table")
    parser.add_argument("--all", action="store_true", help="同时输出有调用方的端点")
    args = parser.parse_args()

    endpoints = controller_endpoints()
    corpus = search_corpus()
    for ep in endpoints:
        hits = []
        for cand in normalize_for_search(ep["path"]):
            for rel, text in corpus:
                if cand.search(text):
                    hits.append(rel)
                    break
            if hits:
                break
        ep["called_by"] = sorted(set(hits))
        ep["orphan"] = not hits

    orphans = [e for e in endpoints if e["orphan"]]
    if args.format == "json":
        print(json.dumps({"total": len(endpoints), "orphan": len(orphans),
                          "endpoints": endpoints}, ensure_ascii=False, indent=2))
    else:
        print(f"端点总数={len(endpoints)} 未找到调用方={len(orphans)}")
        for e in (endpoints if args.all else orphans):
            flag = "ORPHAN" if e["orphan"] else "USED"
            print(f"[{flag}] {e['method']:<6} {e['path']:<62} {e['controller']}:{e['line']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
