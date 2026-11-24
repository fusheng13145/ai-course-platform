#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""跨语言契约端点对齐校验器（Java 常量 ↔ 四套 Python 引擎路由）。

用途
----
`ContractPaths.java` 是调用约定的唯一事实源，四套 Python 引擎（kg / deepgraph /
知识库(并入 deepgraph) / fine_tune）各自用 FastAPI 声明路由。两侧一旦漂移，
Java 侧调用就是 404，而单侧测试全绿——本工具把两侧放在一起比。

判定口径
--------
- **Java 声明了、Python 没有 → 缺口**（`--strict` 下退出码 1）。这是真缺陷：调用必 404。
- **Python 有、Java 未声明 → 未消费**（只登记，不算缺陷）。Java 可以只用部分端点，
  例如知识库的 `PUT/DELETE /documents/{id}` 由 Python 侧管理，Java 只读。
- 路径参数名归一：`{documentName}` 与 `{document_name}` 视为同一个位置（都是 `{}`），
  因为参数名属于两端各自的命名习惯，不构成契约漂移；**位置数量**不同才是漂移。

用法
----
    python scripts/check-contract-alignment.py            # 报告 + 有缺口返回 1
    python scripts/check-contract-alignment.py --json     # 机器可读

依赖：仅标准库。可在 CI 里当门禁跑（不需要装任何引擎依赖）。
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTRACT_PATHS = os.path.join(
    ROOT, "backend", "src", "main", "java", "com", "aicourse", "integration", "ContractPaths.java"
)
PS_DIR = os.path.join(ROOT, "python-services")

# 四套引擎的路由声明文件（知识库的 9 个端点用 APIRouter 挂在 deepgraph 的 app 上）
PY_ROUTE_GLOBS = (
    os.path.join(PS_DIR, "deepgraph_rag", "api_server.py"),
    os.path.join(PS_DIR, "deepgraph_rag", "knowledge_api.py"),
)
PY_ROUTE_DIR_GLOBS = (
    os.path.join(PS_DIR, "kg_service", "*.py"),
    os.path.join(PS_DIR, "fine_tune_service", "*.py"),
)

JAVA_CONST_RE = re.compile(r'public static final String ([A-Z0-9_]+)\s*=\s*"(/api/[^"]*)"')
PY_ROUTE_RE = re.compile(r'@(?:app|router)\.(?:get|post|put|delete|patch)\("(/[^"]*)"')
PARAM_RE = re.compile(r"\{[^}]+\}")


def normalize(path: str) -> str:
    """参数名归一并去掉尾斜杠：只保留"路径形状"。"""
    return PARAM_RE.sub("{}", path.rstrip("/"))


def read(path: str) -> str:
    return io.open(path, encoding="utf-8").read()


def collect_java() -> dict:
    if not os.path.isfile(CONTRACT_PATHS):
        raise SystemExit("找不到 ContractPaths.java：%s" % CONTRACT_PATHS)
    out = {}
    for name, path in JAVA_CONST_RE.findall(read(CONTRACT_PATHS)):
        out[name] = path
    return out


def python_files() -> list:
    files = [p for p in PY_ROUTE_GLOBS if os.path.isfile(p)]
    for pattern in PY_ROUTE_DIR_GLOBS:
        files.extend(sorted(glob.glob(pattern)))
    # 排除测试文件：契约测试里的桩 app 也会声明路由，若把它们算进"实现侧"，
    # 某个端点只在桩里存在、真实实现缺失时会漏报缺口（假绿）。
    return [f for f in files if not os.path.basename(f).startswith("test_")]


def collect_python() -> dict:
    """返回 {归一化路径: [(原始路径, 文件名), ...]}"""
    out = {}
    for f in python_files():
        name = os.path.basename(f)
        for path in PY_ROUTE_RE.findall(read(f)):
            out.setdefault(normalize(path), []).append((path, name))
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Java 契约常量 ↔ Python 引擎路由对齐校验")
    ap.add_argument("--json", action="store_true", help="输出机器可读结果")
    ap.add_argument("--strict", action="store_true", default=True,
                    help="有缺口时返回 1（默认开启；--no-strict 关闭）")
    ap.add_argument("--no-strict", dest="strict", action="store_false")
    args = ap.parse_args(argv)

    java = collect_java()
    py = collect_python()

    missing = [(n, p) for n, p in sorted(java.items()) if normalize(p) not in py]
    unconsumed = sorted(set(
        (orig, src) for key, items in py.items()
        if key not in {normalize(p) for p in java.values()}
        for orig, src in items
    ))

    if args.json:
        print(json.dumps({
            "java_endpoints": len(java),
            "python_routes": sum(len(v) for v in py.values()),
            "missing": [{"constant": n, "path": p} for n, p in missing],
            "unconsumed": [{"path": p, "declared_in": s} for p, s in unconsumed],
        }, ensure_ascii=False, indent=2))
    else:
        print("Java 契约端点 %d 个 / Python 路由 %d 个（扫描 %d 个文件）"
              % (len(java), sum(len(v) for v in py.values()), len(python_files())))
        print("\n=== Java 声明但 Python 无（缺口）: %d ===" % len(missing))
        for n, p in missing:
            print("  %-24s %s" % (n, p))
        if not missing:
            print("  （无）")
        print("\n=== Python 有但 Java 未声明（未消费，允许）: %d ===" % len(unconsumed))
        for p, s in unconsumed:
            print("  %-46s %s" % (p, s))
        print("\n对齐: %d / %d" % (len(java) - len(missing), len(java)))

    return 1 if (missing and args.strict) else 0


if __name__ == "__main__":
    sys.exit(main())
