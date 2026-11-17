#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""解析 MyBatis 慢 SQL 日志，输出 Top-N。

日志来源：`MyBatisSlowSqlInterceptor` 的 WARN 行，格式为
    [slow-sql] {cost} ms 超过阈值 {threshold} ms | {msId} | {sql}

用法：
    python scripts/analyze_slow_sql.py
    python scripts/analyze_slow_sql.py --log logs/api.log --top 10
    python scripts/analyze_slow_sql.py --log .workbuddy/tmp/backend_e2e.log --format json
"""
from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LOG = ROOT / "logs" / "api.log"
LINE_RE = re.compile(
    r"\[slow-sql\]\s+(?P<cost>\d+)\s+ms\s+超过阈值\s+(?P<threshold>\d+)\s+ms\s*\|\s*"
    r"(?P<msid>[^|]+)\|\s*(?P<sql>.+)$"
)


def parse_log(path: Path) -> tuple[int, list[dict]]:
    records = []
    bad = 0
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if "[slow-sql]" not in line:
                continue
            m = LINE_RE.search(line.rstrip("\n"))
            if not m:
                bad += 1
                continue
            records.append({
                "cost_ms": int(m.group("cost")),
                "threshold_ms": int(m.group("threshold")),
                "msid": m.group("msid").strip(),
                "sql": m.group("sql").strip(),
            })
    return bad, records


def aggregate(records: list[dict]) -> list[dict]:
    groups = defaultdict(lambda: {"count": 0, "total_ms": 0, "max_ms": 0,
                                  "threshold_ms": 0, "sql": ""})
    for r in records:
        # 同一条 MappedStatement 且 SQL 前 120 字符相同才归并，避免把不同参数混在一起
        key = (r["msid"], r["sql"][:120])
        g = groups[key]
        g["count"] += 1
        g["total_ms"] += r["cost_ms"]
        g["max_ms"] = max(g["max_ms"], r["cost_ms"])
        g["threshold_ms"] = r["threshold_ms"]
        g["sql"] = r["sql"][:200]
    rows = []
    for (msid, _), g in groups.items():
        rows.append({
            "msid": msid,
            "count": g["count"],
            "avg_ms": round(g["total_ms"] / g["count"], 1),
            "max_ms": g["max_ms"],
            "threshold_ms": g["threshold_ms"],
            "sql": g["sql"],
        })
    rows.sort(key=lambda x: (x["max_ms"], x["count"]), reverse=True)
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", action="append", default=None,
                        help="日志路径，可重复；默认 logs/api.log")
    parser.add_argument("--top", type=int, default=10)
    parser.add_argument("--format", choices=["table", "json"], default="table")
    args = parser.parse_args()

    logs = [Path(p) for p in args.log] if args.log else [DEFAULT_LOG]
    total_records = 0
    all_records = []
    malformed = 0
    for log in logs:
        if not log.exists():
            print(f"[skip] 日志不存在: {log}")
            continue
        bad, records = parse_log(log)
        malformed += bad
        total_records += len(records)
        all_records.extend(records)
        print(f"[read] {log}: slow-sql={len(records)}, malformed={bad}")

    rows = aggregate(all_records)[: args.top]
    if args.format == "json":
        print(json.dumps({"total": total_records, "malformed": malformed,
                          "top": rows}, ensure_ascii=False, indent=2))
    else:
        print(f"\n总计 {total_records} 条慢 SQL（阈值触发行）; 解析失败 {malformed} 条")
        if not rows:
            print("Top-N 为空：当前日志内没有超过 500ms 阈值的 SQL。")
            return 0
        print(f"{'#':>2} {'次数':>4} {'平均ms':>7} {'最大ms':>7} {'阈值':>5}  MappedStatement / SQL")
        for i, r in enumerate(rows, 1):
            print(f"{i:>2} {r['count']:>4} {r['avg_ms']:>7} {r['max_ms']:>7} "
                  f"{r['threshold_ms']:>5}  {r['msid']} | {r['sql'][:120]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
