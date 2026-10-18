#!/usr/bin/env python3
"""
RAG 演示语料重放：scripts/rag_corpus.json -> Python RAG 服务（8002）
====================================================================
`python-services/rag_service/data/` 被 .gitignore 排除，语料只存在于运行态；
本脚本把版本控制里的 `rag_corpus.json` 重放到引擎，使换机器/清目录后
AI 问答仍能命中真实索引（否则按契约降级为"AI 服务暂时不可用"）。

用法：
    python scripts/seed-rag-corpus.py [course_id ...]   # 缺省重放全部课程
前置：RAG 服务已启动（地址用 RAG_BASE_URL 覆盖，缺省 http://localhost:8002）。
幂等：同名文档内容一致则跳过，不一致则 PUT 覆盖；范围内的每门课程都会重建向量索引。
"""
import json
import os
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
RAG_BASE = os.getenv("RAG_BASE_URL", "http://localhost:8002").rstrip("/")
CORPUS = ROOT / "scripts" / "rag_corpus.json"


def call(method: str, path: str, payload: dict | None = None) -> dict | list:
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{RAG_BASE}{path}", data=data,
                                 headers={"Content-Type": "application/json"},
                                 method=method)
    return json.loads(urllib.request.urlopen(req, timeout=120).read().decode("utf-8"))


def main() -> None:
    if not CORPUS.exists():
        sys.exit(f"缺少语料文件：{CORPUS}")
    documents = json.loads(CORPUS.read_text(encoding="utf-8"))["documents"]
    wanted = [int(a) for a in sys.argv[1:]]
    if wanted:
        documents = [d for d in documents if d["course_id"] in wanted]
    if not documents:
        sys.exit(f"rag_corpus.json 内没有课程 {wanted} 的语料")

    live = {(d["course_id"], d["document_name"]): d
            for d in call("GET", "/api/v1/rag/documents")}
    created = updated = skipped = 0
    for doc in documents:
        key = (doc["course_id"], doc["document_name"])
        current = live.get(key)
        if current is None:
            call("POST", "/api/v1/rag/documents", doc)
            created += 1
        elif current["content"] != doc["content"]:
            call("PUT", f"/api/v1/rag/documents/{current['id']}", {"content": doc["content"]})
            updated += 1
        else:
            skipped += 1

    # 文档写入会让该课程索引过期（rag_engine._invalidate_course_index），一律重建；
    # 未写入的课程也 rebuild 一次，使"清过索引"的库回到可用状态。
    for course_id in sorted({d["course_id"] for d in documents}):
        result = call("POST", f"/api/v1/rag/index/build/{course_id}")
        print(f"[course {course_id}] indexed_chunks={result.get('indexed_chunks')} "
              f"success={result.get('success')}"
              + (f" error={result.get('error')}" if result.get("error") else ""))

    print(f"[rag] 语料 {len(documents)} 篇：新建 {created}、更新 {updated}、一致跳过 {skipped}"
          f"（{RAG_BASE}）")


if __name__ == "__main__":
    main()
