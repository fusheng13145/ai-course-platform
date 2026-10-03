#!/usr/bin/env python3
"""
知识图谱同步：MySQL(ai_course_platform) -> Python KG 服务（8001，Neo4j 后端）
==============================================================================
Java 端知识点 ID 以 MySQL 为权威，本脚本按 ID 对齐推送（kg_service 的
POST /api/v1/graph/nodes 支持显式 id），使 Java 端 `knowledge-graph.enabled=true`
时 path/plan、subgraph 能命中真实图引擎。

用法：
    python scripts/sync-kg-to-python.py [course_id ...]   # 缺省同步全部课程
前置：KG 服务（8001）已启动；本机 mysql CLI 可用（口令从仓库根 .env 的
      MYSQL_ROOT_PASSWORD 读取，不落命令行）。
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

# Windows 管道环境下 Python 会用 GBK 重定向 stdout，统一固定为 UTF-8
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

ROOT = Path(__file__).resolve().parent.parent
KG_BASE = os.getenv("KG_BASE_URL", "http://localhost:8001")
DB = os.getenv("DB_NAME", "ai_course_platform")


def load_env() -> dict:
    env = {}
    p = ROOT / ".env"
    if p.exists():
        for line in p.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    return env


def mysql_query(sql: str, env: dict) -> list[list[str]]:
    e = dict(os.environ)
    e["MYSQL_PWD"] = env.get("MYSQL_ROOT_PASSWORD", "")
    user = env.get("MYSQL_USER", "root")
    out = subprocess.run(
        ["mysql", "-u", user, "--default-character-set=utf8mb4", "-N", "-B", "-e", sql, DB],
        capture_output=True, text=True, encoding="utf-8", errors="replace", env=e)
    if out.returncode != 0:
        sys.exit(f"mysql 查询失败: {out.stderr.strip()[:300]}")
    rows = []
    for line in out.stdout.splitlines():
        rows.append(line.split("\t"))
    return rows


def post(path: str, payload: dict) -> dict:
    req = urllib.request.Request(
        f"{KG_BASE}{path}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))


def main() -> None:
    env = load_env()
    courses = [int(a) for a in sys.argv[1:]]
    where = f"WHERE course_id IN ({','.join(map(str, courses))})" if courses else ""
    points = mysql_query(
        f"SELECT id, course_id, name, IFNULL(description,''), IFNULL(point_type,'CONCEPT') "
        f"FROM knowledge_points {where} ORDER BY id", env)
    rel_sql = ("SELECT id, source_id, target_id, relation_type FROM knowledge_point_relations")
    if courses:
        rel_sql += (" WHERE source_id IN (SELECT id FROM knowledge_points "
                    f"WHERE course_id IN ({','.join(map(str, courses))}))")
    relations = mysql_query(rel_sql, env)

    health = json.loads(
        urllib.request.urlopen(f"{KG_BASE}/api/v1/graph/health", timeout=10).read())
    print(f"[kg] 后端={health.get('backend')}，知识点 {len(points)} 个、关系 {len(relations)} 条")

    for pid, cid, name, desc, ptype in points:
        node = post("/api/v1/graph/nodes", {
            "id": int(pid), "course_id": int(cid), "name": name,
            "content": desc, "type": ptype or "CONCEPT"})
        assert node["id"] == int(pid)
    for rid, src, dst, rtype in relations:
        post("/api/v1/graph/relations", {
            "source_id": int(src), "target_id": int(dst), "relation_type": rtype})
    print("[kg] 同步完成（节点按 MySQL ID 对齐）")


if __name__ == "__main__":
    main()
