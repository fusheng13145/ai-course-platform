#!/usr/bin/env python
"""B6-2：IREM 五类关系构建（规则召回 → LLM 判类 → 可选写库）。

链路：
    1. 从 MySQL 读课程知识点（id / name / description）；
    2. 用课程语料（scripts/rag_corpus.json）做共现与包含召回（irem.infer_implicit_relations）；
    3. 用本地 Ollama 把候选对判成论文五类（irem.classify_pairs）；
    4. `--apply` 单事务写 knowledge_point_relations（跳过已存在的同向对）。

纪律：
- 输出仍是**候选**，未经人工标注评估，不得表述为"已实现且有效的 IREM 能力"；
- 默认 dry-run，只落 JSON；`--apply` 才写库；
- 写库前先查已存在关系，不覆盖历史 PREREQUISITE/RELATED 数据。
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KG_DIR = ROOT / "python-services" / "kg_service"
sys.path.insert(0, str(KG_DIR))
import irem  # noqa: E402

OLLAMA_BASE = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "deepseek-r1:8b")
MYSQL_CLI = os.environ.get("MYSQL_CLI") or r"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"


def load_env() -> dict:
    result: dict[str, str] = {}
    path = ROOT / ".env"
    if not path.exists():
        return result
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        result[key.strip()] = value.strip().strip('"').strip("'")
    return result


def mysql(sql: str, env: dict, database: str = "ai_course_platform") -> str:
    """执行 SQL（密码走 MYSQL_PWD 环境变量，不进命令行）。"""
    password = env.get("MYSQL_ROOT_PASSWORD", "")
    if not password:
        raise SystemExit("缺少 MYSQL_ROOT_PASSWORD（请检查 .env）")
    command = [MYSQL_CLI, "-u", "root", "-h", "127.0.0.1", "-P", "3306",
               "--ssl-mode=REQUIRED", "--default-character-set=utf8mb4",
               "-D", database, "-N", "-B", "-e", sql]
    child_env = dict(os.environ, MYSQL_PWD=password)
    completed = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                               env=child_env)
    if completed.returncode != 0:
        raise SystemExit(f"MySQL 执行失败：{completed.stderr.strip()[:400]}")
    return completed.stdout


def load_nodes(course_id: int, env: dict) -> list[dict]:
    sql = ("SELECT id, name, COALESCE(description,'') FROM knowledge_points "
           f"WHERE course_id={int(course_id)} ORDER BY id")
    nodes = []
    for line in mysql(sql, env).splitlines():
        parts = line.split("\t")
        if len(parts) < 2 or not parts[0].strip().isdigit():
            continue
        nodes.append({"id": int(parts[0]), "name": parts[1].strip(),
                      "description": parts[2].strip() if len(parts) > 2 else ""})
    return nodes


def load_texts(course_id: int) -> list[str]:
    corpus = json.loads((ROOT / "scripts" / "rag_corpus.json").read_text(encoding="utf-8"))
    documents = corpus.get("documents") or []
    return [doc.get("content", "") for doc in documents if int(doc.get("course_id") or 0) == int(course_id)]


def load_existing_pairs(env: dict) -> set[tuple[int, int]]:
    sql = "SELECT source_id, target_id FROM knowledge_point_relations"
    pairs = set()
    for line in mysql(sql, env).splitlines():
        parts = line.split("\t")
        if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
            pairs.add((int(parts[0]), int(parts[1])))
    return pairs


def load_chapter_pairs(course_id: int, env: dict, max_distance: int = 1) -> list[dict]:
    """按"同一章节内、教材顺序相邻"构造候选对。

    为什么需要这一路：语料只有 10 篇，而 IPA 产出的多是**规范名**（如「CDN内容分发原理」），
    原文里往往只写简称（CDN），纯共现召回实测只有 2 对。但"同章相邻"是教材本身的强先验
    （同章同节的知识点更可能构成包含/并列/前驱关系），用它补候选是合理的，不是凑数。
    """
    sql = ("SELECT ckp.chapter_id, kp.id FROM chapter_knowledge_points ckp "
           "JOIN knowledge_points kp ON kp.id = ckp.knowledge_point_id "
           f"WHERE kp.course_id={int(course_id)} ORDER BY ckp.chapter_id, kp.id")
    by_chapter: dict[int, list[int]] = {}
    for line in mysql(sql, env).splitlines():
        parts = line.split("\t")
        if len(parts) >= 2 and parts[0].strip().isdigit() and parts[1].strip().isdigit():
            by_chapter.setdefault(int(parts[0]), []).append(int(parts[1]))
    pairs: list[dict] = []
    for ids in by_chapter.values():
        for index in range(len(ids)):
            for offset in range(1, max(1, max_distance) + 1):
                other = index + offset
                if other >= len(ids):
                    break
                pairs.append({
                    "source_id": ids[index], "target_id": ids[other],
                    "relation_type": "RELATED", "score": 0.5,
                    "evidence": "同一章节内相邻知识点",
                })
    return pairs


def ollama_call(prompt: str, system: str | None = None) -> str:
    body = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "system": system or "",
        "stream": False,
        "think": False,
        "format": "json",
        "options": {"temperature": 0.2, "num_predict": 1600, "top_p": 0.9},
    }
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(f"{OLLAMA_BASE}/api/generate", data=data,
                                     headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=240) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return payload.get("response", "") or ""


def sql_escape(value: str) -> str:
    text = str(value or "").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    return text.replace("\\", "\\\\").replace("'", "\\'")


def apply_relations(items: list[dict], env: dict) -> dict:
    """单事务写入关系；跳过已存在的同向对。"""
    if not items:
        return {"inserted": 0, "skipped": 0}
    existing = load_existing_pairs(env)
    statements = ["START TRANSACTION;"]
    inserted = skipped = 0
    for item in items:
        key = (int(item["source_id"]), int(item["target_id"]))
        if key in existing:
            skipped += 1
            continue
        relation_type = item["relation_type"]
        strength = round(float(item.get("confidence") or 0.5), 2)
        description = sql_escape(item.get("reason") or item.get("evidence") or "")
        statements.append(
            "INSERT INTO knowledge_point_relations "
            "(source_id, target_id, relation_type, strength, description) VALUES "
            f"({key[0]}, {key[1]}, '{sql_escape(relation_type)}', {strength}, '{description}');")
        existing.add(key)
        inserted += 1
    statements.append("COMMIT;")
    mysql("\n".join(statements), env)
    return {"inserted": inserted, "skipped": skipped}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("course_id", type=int)
    parser.add_argument("--out", default="", help="候选 JSON 落盘路径")
    parser.add_argument("--max-pairs", type=int, default=120, help="最多判多少对（0=不限）")
    parser.add_argument("--batch-size", type=int, default=5)
    parser.add_argument("--chapter-distance", type=int, default=1,
                        help="同章内取相距几步的知识点对（1=相邻）")
    parser.add_argument("--apply", action="store_true", help="写 MySQL（默认只产出候选）")
    args = parser.parse_args()

    env = load_env()
    nodes = load_nodes(args.course_id, env)
    texts = load_texts(args.course_id)
    if len(nodes) < 2 or not texts:
        raise SystemExit(f"课程 {args.course_id} 知识点={len(nodes)} 语料篇数={len(texts)}，不足以推断关系")
    print(f"[irem] course={args.course_id} nodes={len(nodes)} texts={len(texts)} model={OLLAMA_MODEL}")

    rule_pairs = irem.infer_implicit_relations(nodes, texts, top_n=args.max_pairs or None)
    chapter_pairs = load_chapter_pairs(args.course_id, env, args.chapter_distance)
    print(f"[irem] rule candidates={len(rule_pairs)} chapter candidates={len(chapter_pairs)}")

    merged: list[dict] = []
    seen: set[tuple[int, int]] = set()
    for pair in chapter_pairs + rule_pairs:   # 结构先验在前：同章相邻优先被判
        key = (int(pair["source_id"]), int(pair["target_id"]))
        if key in seen:
            continue
        seen.add(key)
        merged.append(pair)
    print(f"[irem] merged candidates={len(merged)}")

    classified = irem.classify_pairs(nodes, merged, ollama_call,
                                     batch_size=args.batch_size,
                                     max_pairs=args.max_pairs or None)
    distribution: dict[str, int] = {}
    for item in classified:
        distribution[item["relation_type"]] = distribution.get(item["relation_type"], 0) + 1
    print(f"[irem] classified={len(classified)} distribution={distribution}")

    if args.out:
        Path(args.out).write_text(json.dumps(classified, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[out] {args.out}")

    if args.apply:
        result = apply_relations(classified, env)
        print(f"[apply] {result}")
        print(f"[next] 同步 Neo4j：python scripts\\sync-kg-to-python.py {args.course_id}")
    else:
        print("[dry-run] 未写库；确认候选后加 --apply")


if __name__ == "__main__":
    main()
