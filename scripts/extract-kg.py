#!/usr/bin/env python3
"""
KG 抽取驱动：课程语料 -> IPA 知识点候选 ->（可选）写 MySQL 并同步 Neo4j
======================================================================

本脚本是 B6 的"真实抽取"入口，把 `python-services/kg_service/ipa.py`（IPA 两阶段
抽取器）接到真实数据上：

    数据来源：`scripts/rag_corpus.json`（RAG 语料的版本控制权威副本，课程 4178 十篇）
    章节来源：MySQL `course_chapters`（章节 id 用于候选点归属）
    模型调用：Ollama `/api/generate`（默认 deepseek-r1:8b）；DeepSeek 需自付额度，
              402 期间不要用它跑抽取

用法（仓库根执行）：
    python scripts\\extract-kg.py 4178                       # 只抽取，打印候选到 stdout
    python scripts\\extract-kg.py 4178 --limit 1             # 只抽第一篇（冒烟/调提示词）
    python scripts\\extract-kg.py 4178 --out tmp.json        # 候选落盘（人工抽检用）
    python scripts\\extract-kg.py 4178 --apply               # 写 MySQL + 同步 Neo4j（危险，需人工确认）

`--apply` 的写入纪律（避免破坏已有 43 个知识点）：
    1. 只**新增** name 在库内不存在的点，同名点跳过（不覆盖人工整理的 description）；
    2. 章节关联写 `chapter_knowledge_points`，已存在则不重复插；
    3. 关系一律先不写——关系由 B6-2（IREM 五类）单独产出，避免本脚本越权；
    4. 每步打印真实读数（新增/跳过/失败），任何失败即整体回滚（单事务）。
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

# Windows 管道环境下 Python 会用 GBK 重定向 stdout，统一固定为 UTF-8
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "python-services" / "kg_service"))

import ipa  # noqa: E402  （必须在 sys.path 注入之后）

DB = os.getenv("DB_NAME", "ai_course_platform")
OLLAMA_BASE = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")


def load_env() -> dict:
    env = {}
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                env[key.strip()] = value.strip()
    return env


def mysql(sql: str, *, env: dict) -> str:
    """以 root 执行 SQL（口令走环境变量，不落命令行：MYSQL_PWD 在 8.0+ 客户端仍受支持）。"""
    exe = os.getenv("MYSQL_CLI") or "mysql"
    password = env.get("MYSQL_ROOT_PASSWORD", "")
    child_env = dict(os.environ)
    child_env["MYSQL_PWD"] = password
    cmd = [exe, "-u", "root", "-h", "127.0.0.1", "-P", "3306", "-D", DB,
           "--ssl-mode=REQUIRED", "--default-character-set=utf8mb4",
           "--batch", "--raw", "-e", sql]
    completed = subprocess.run(cmd, capture_output=True, text=True, env=child_env, timeout=120)
    if completed.returncode != 0:
        raise RuntimeError(f"mysql 失败: {completed.stderr.strip()[:300]}")
    return completed.stdout


def load_corpus(course_id: int) -> list[dict]:
    path = ROOT / "scripts" / "rag_corpus.json"
    if not path.exists():
        raise SystemExit(f"缺少语料文件：{path}")
    documents = json.loads(path.read_text(encoding="utf-8"))["documents"]
    return [d for d in documents if int(d.get("course_id", -1)) == course_id]


def load_chapters(course_id: int, env: dict) -> list[dict]:
    out = mysql(
        f"SELECT id,title FROM course_chapters WHERE course_id={course_id} "
        "AND is_published=1 ORDER BY order_index", env=env)
    chapters = []
    for line in out.splitlines()[1:]:     # 第一行是表头
        parts = line.split("\t")
        if len(parts) >= 2 and parts[0].strip().isdigit():
            chapters.append({"id": int(parts[0]), "title": parts[1]})
    return chapters


def ollama_call(prompt: str, system: str | None = None) -> str:
    """调 Ollama 生成。

    三个实测要点（2026-11-15 B6 冒烟，deepseek-r1:8b + Ollama 0.40.14）：
    1. deepseek-r1 是思维链模型，思考过程会吃掉 `num_predict`——给 800 时最终 JSON 根本没机会输出；
       实测 2500 才够（思考 ~1500 + 答案 ~600）。
    2. `think=false` 在该版本不生效，思考仍写在 `response` 里；真正的解法是 `format="json"`，
       由 Ollama 的语法约束把最终输出锁成 JSON。
    3. 模型会把提示词里的格式示例原样复读（曾抽出 name="术语"）——ipa.py 侧已按占位词清单过滤。
    """
    body = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "system": system or "",
        "stream": False,
        "think": False,
        "format": "json",
        "options": {"temperature": 0.2, "num_predict": 2500, "top_p": 0.9},
    }
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(f"{OLLAMA_BASE}/api/generate", data=data,
                                     headers={"Content-Type": "application/json"},
                                     method="POST")
    with urllib.request.urlopen(request, timeout=240) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return payload.get("response", "") or ""


def build_paragraphs(documents: list[dict]) -> tuple[list[str], list[dict]]:
    """把每篇文档的正文切成 180-280 字的小段（按句子边界聚合），并记录每段来源文档。

    为什么不能整篇当一段：整套语料是"一篇讲义 400-700 字"的形态，整篇塞给模型时
    8B 模型倾向于只挑 1-2 个最显眼的术语（实测 B6 冒烟：整篇 1 段只出 1 个候选）。
    按句子打包后每段聚焦一个主题，抽取密度显著提高。
    """
    import re
    sentence_sep = re.compile(r"(?<=[。！？；!?;])")
    paragraphs: list[str] = []
    meta: list[dict] = []
    for document in documents:
        content = (document.get("content") or "").strip()
        if not content:
            continue
        sentences = [s.strip() for s in sentence_sep.split(content) if s.strip()]
        buffer = ""
        for sentence in sentences:
            buffer += sentence
            if len(buffer) >= 180:
                paragraphs.append(buffer)
                meta.append({"document": document.get("document_name", "")})
                buffer = ""
        if buffer.strip():
            paragraphs.append(buffer.strip())
            meta.append({"document": document.get("document_name", "")})
    return paragraphs, meta


def dedupe_against_existing(points: list[dict], course_id: int, env: dict) -> tuple[list[dict], int]:
    """与库内已有知识点名对齐：同名跳过（不覆盖人工整理）。"""
    out = mysql(f"SELECT name FROM knowledge_points WHERE course_id={course_id}", env=env)
    existing = {line.strip() for line in out.splitlines()[1:] if line.strip()}
    fresh = [p for p in points if p.get("name") and p["name"] not in existing]
    return fresh, len(existing)


def sql_escape(value: str) -> str:
    """SQL 字符串转义：反斜杠与单引号转义，换行替换为空格。

    换行必须替换：MySQL 客户端逐行执行 `-e` 语句，字符串里出现真实换行会被当成语句结束，
    导致"看起来插入了、实际只执行了前半句"的静默截断（本脚本 2026-11-15 首次 --apply 时踩到）。
    """
    text = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    return text.replace("\\", "\\\\").replace("'", "\\'")


def apply_points(points: list[dict], course_id: int, env: dict) -> dict:
    """写 MySQL：单事务插入知识点与章节关联；失败即回滚。"""
    if not points:
        return {"inserted": 0, "linked": 0}
    statements = ["START TRANSACTION;"]
    inserted = linked = 0
    for point in points:
        name = sql_escape(point["name"])
        description = sql_escape(point.get("description") or "")
        point_type = "CONCEPT"
        statements.append(
            "INSERT INTO knowledge_points (name,description,course_id,parent_id,difficulty_level,"
            "importance,estimated_time,point_type,order_index,created_at,updated_at) VALUES "
            f"('{name}','{description}',{course_id},NULL,'中等',5,0,'{point_type}',0,"
            "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);")
        inserted += 1
        chapter_id = point.get("chapter_id")
        if isinstance(chapter_id, int):
            statements.append(
                "INSERT INTO chapter_knowledge_points (chapter_id,knowledge_point_id,created_at) "
                f"SELECT {chapter_id}, LAST_INSERT_ID(), CURRENT_TIMESTAMP "
                "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM chapter_knowledge_points "
                f"WHERE chapter_id={chapter_id} AND knowledge_point_id=LAST_INSERT_ID());")
            linked += 1
    statements.append("COMMIT;")
    mysql("\n".join(statements), env=env)
    return {"inserted": inserted, "linked": linked}


def main() -> None:
    parser = argparse.ArgumentParser(description="IPA 知识点抽取（B6-1）")
    parser.add_argument("course_id", type=int)
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 篇文档（冒烟用）")
    parser.add_argument("--out", default="", help="候选 JSON 落盘路径")
    parser.add_argument("--from-json", default="",
                        help="从已有候选 JSON 写库（跳过 LLM 抽取，用于筛选后重放）")
    parser.add_argument("--min-confidence", type=float, default=0.0,
                        help="只入库置信度不低于该值的候选")
    parser.add_argument("--require-desc", action="store_true",
                        help="只入库有说明的候选（无说明的留在 JSON 里待补）")
    parser.add_argument("--apply", action="store_true", help="写 MySQL（默认只抽取）")
    parser.add_argument("--max-points", type=int, default=200)
    args = parser.parse_args()

    env = load_env()
    documents = load_corpus(args.course_id)
    if args.limit > 0:
        documents = documents[:args.limit]
    if not documents:
        raise SystemExit(f"rag_corpus.json 内没有课程 {args.course_id} 的语料")

    chapters = load_chapters(args.course_id, env)
    paragraphs, meta = build_paragraphs(documents)
    print(f"[corpus] course={args.course_id} docs={len(documents)} paragraphs={len(paragraphs)} "
          f"chapters={len(chapters)} model={OLLAMA_MODEL}")

    if args.from_json:
        points = json.loads(Path(args.from_json).read_text(encoding="utf-8"))
        print(f"[from-json] {args.from_json} candidates={len(points)}")
    else:
        points = ipa.extract_knowledge_points(paragraphs, ollama_call, chapters=chapters,
                                              max_points=args.max_points)
        for point in points:
            index = point.get("source_paragraph")
            if isinstance(index, int) and 0 <= index < len(meta):
                point["source_document"] = meta[index]["document"]
        print(f"[ipa] candidates={len(points)}")

    if args.min_confidence > 0:
        points = [p for p in points if float(p.get("confidence") or 0) >= args.min_confidence]
        print(f"[filter] min_confidence={args.min_confidence} -> {len(points)}")
    if args.require_desc:
        points = [p for p in points if (p.get("description") or "").strip()]
        print(f"[filter] require_desc -> {len(points)}")

    fresh, existing_count = dedupe_against_existing(points, args.course_id, env)
    print(f"[dedupe] existing_in_db={existing_count} fresh={len(fresh)} "
          f"skipped_same_name={len(points) - len(fresh)}")

    if args.out:
        path = Path(args.out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(fresh, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[out] {path}")

    if args.apply:
        result = apply_points(fresh, args.course_id, env)
        print(f"[apply] {result}")
        print("[next] 同步 Neo4j：python scripts\\sync-kg-to-python.py "
              f"{args.course_id}")
    else:
        print("[dry-run] 未写库；确认候选后加 --apply")


if __name__ == "__main__":
    main()
