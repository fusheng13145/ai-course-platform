#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""换机/发布前的自动验收清单（B-10）。

用法：
    python scripts/acceptance_check.py
    python scripts/acceptance_check.py --require backend,frontend,kg,rag
    python scripts/acceptance_check.py --format json

判据：
  - 端口：TCP 可连接；
  - 后端：GET /api/auth/me 返回 401/200 均视为「服务已起」（401 是未登录，不是故障）；
  - 前端：GET / 返回 200 且包含 Vue 挂载点；
  - KG / RAG / 微调：GET 各自 /health 返回 200；
  - 环境：.env 存在且 JWT_SECRET 非空（只报存在性，不回显值）。
"""
from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

CHECKS = {
    "backend": {"port": 8080, "url": "http://127.0.0.1:8080/api/auth/me",
                "ok_status": {200, 401}, "name": "后端 API"},
    "frontend": {"port": 5173, "host": "localhost", "url": "http://localhost:5173/",
                 "ok_status": {200}, "contains": "<div id=\"app\">", "name": "前端 Vite"},
    "kg": {"port": 8001, "url": "http://127.0.0.1:8001/api/v1/graph/health",
           "ok_status": {200}, "name": "知识图谱服务"},
    "rag": {"port": 8501, "url": "http://127.0.0.1:8501/api/v1/rag/health",
            "ok_status": {200}, "name": "RAG 服务（知识库，已并入 8501）"},
    "fine_tune": {"port": 8003, "url": "http://127.0.0.1:8003/api/v1/fine-tune/health",
                  "ok_status": {200}, "name": "微调服务（可选）"},
}
DEFAULT_REQUIRED = ["backend", "frontend", "kg", "rag"]


def port_open(port: int, host: str = "127.0.0.1", timeout: float = 1.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def http_get(url: str, timeout: float = 5.0) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, r.read(65536).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(65536).decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def env_check() -> list[dict]:
    rows = []
    env = ROOT / ".env"
    rows.append({"item": ".env 存在", "ok": env.exists(), "detail": str(env)})
    if env.exists():
        text = env.read_text(encoding="utf-8", errors="replace")
        jwt = any(line.strip().startswith("JWT_SECRET=") and line.split("=", 1)[1].strip()
                  for line in text.splitlines())
        rows.append({"item": "JWT_SECRET 非空", "ok": jwt, "detail": "只检查存在性，不回显值"})
    else:
        rows.append({"item": "JWT_SECRET 非空", "ok": False, "detail": ".env 缺失"})
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require", default=",".join(DEFAULT_REQUIRED),
                        help="要求必须通过的检查，逗号分隔：backend,frontend,kg,rag,fine_tune")
    parser.add_argument("--format", choices=["table", "json"], default="table")
    args = parser.parse_args()
    required = {x.strip() for x in args.require.split(",") if x.strip()}

    rows = []
    for key, spec in CHECKS.items():
        required_flag = key in required
        port_ok = port_open(spec["port"], spec.get("host", "127.0.0.1"))
        status, body = http_get(spec["url"]) if port_ok else (0, "端口不可连")
        status_ok = status in spec["ok_status"]
        contains_ok = ("contains" not in spec) or (spec["contains"] in body)
        ok = port_ok and status_ok and contains_ok
        rows.append({
            "key": key,
            "item": spec["name"],
            "required": required_flag,
            "port": spec["port"],
            "port_open": port_ok,
            "http_status": status,
            "ok": ok,
            "detail": "" if ok else f"port={port_ok}, status={status}, contains={contains_ok}",
        })
    rows.extend(env_check())

    failed_required = [r for r in rows if r.get("required") and not r["ok"]]
    failed_optional = [r for r in rows if not r.get("required") and not r["ok"]]

    if args.format == "json":
        print(json.dumps({"ok": not failed_required, "checks": rows}, ensure_ascii=False, indent=2))
    else:
        print(f"{'结果':<6}{'检查项':<20}{'端口':>6}{'HTTP':>8} 说明")
        for r in rows:
            flag = "PASS" if r["ok"] else ("FAIL" if r.get("required") else "SKIP")
            print(f"{flag:<6}{r['item']:<20}{r.get('port', '-'):>6}{r.get('http_status', '-'):>8} {r['detail']}")
        if failed_required:
            print(f"\n[FAIL] 必须通过项失败 {len(failed_required)} 个")
        else:
            print("\n[PASS] 所有必须通过项已通过")
        if failed_optional:
            print(f"[INFO] 可选服务未起：{', '.join(r['key'] for r in failed_optional)}")

    return 1 if failed_required else 0


if __name__ == "__main__":
    sys.exit(main())
