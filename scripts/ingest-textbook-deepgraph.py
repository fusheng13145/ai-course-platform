# -*- coding: utf-8 -*-
"""把《计算机网络》（谢希仁第八版）全文经平台 deepgraph 适配器入库并逐章构建知识图谱。

流程（全部真实链路）：
  1. POST /api/v1/deepgraph/ingest（build_hierarchical=true, build_chapter=true）
     —— 原实现 process_documents 全链路：加载→分块→Ollama 嵌入→FAISS+BM25→
        层次大纲（LLM）→章节切分→持久化；层次树桥接进 kg_service 课程 4178（章→节=包含章节）
  2. GET /api/v1/deepgraph/documents?course_id=4178 取章节清单
  3. 逐章 POST /api/v1/deepgraph/chapter-graph
     —— 原实现 build_chapter_knowledge_graph：LLM 三元组抽取（带溯源）→
        Neo4j 同步 + 课程知识图谱桥接（实体按名幂等、三元组判重）
"""
import io
import json
import sys
import time
import urllib.request
import urllib.error

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8501/api/v1/deepgraph"
COURSE_ID = 4178
TEXTBOOK = r"C:\Users\ASUS\Desktop\大创赛\DeepSeek-RAG-Chatbot-main\data\documents\计算机网络第八版谢希仁-第八版（1）.docx"
DOC_NAME = "计算机网络第八版谢希仁-第八版（1）"


def http_json(method, url, body=None, timeout=1800):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"} if data else {})
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        return resp.status, json.loads(resp.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw[:300]}


def main():
    t0 = time.time()
    # 1) 教材全文入库
    with io.open(TEXTBOOK, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()
    print(f"[1] 教材全文 {len(content)} 字符，开始入库（层次+章节+向量建库）……")
    status, body = http_json("POST", f"{BASE}/ingest", body={
        "course_id": COURSE_ID, "document_name": DOC_NAME, "content": content,
        "build_hierarchical": True, "build_chapter": True, "build_global": False,
    })
    sync = body.get("course_graph_sync") or {}
    print(f"    http={status} success={body.get('success')} stored={body.get('storedAs') or body.get('stored_as')}")
    print(f"    chunk_count={body.get('chunkCount') or body.get('chunk_count')} "
          f"chapters={body.get('chapters')} 层次树桥接={sync}")
    if not body.get("success"):
        print("    入库失败，终止")
        return 1

    # 2) 章节清单
    status, docs = http_json("GET", f"{BASE}/documents?course_id={COURSE_ID}")
    chapters = []
    for d in docs.get("documents", []):
        if DOC_NAME in (d.get("document_name") or ""):
            chapters = d.get("chapter_names") or []
    print(f"[2] 章节数 {len(chapters)}：{chapters[:6]}……")

    # 3) 逐章构建知识图谱（LLM 抽取 + Neo4j 同步 + 课程图谱桥接）
    ok = 0
    for i, name in enumerate(chapters, 1):
        status, body = http_json("POST", f"{BASE}/chapter-graph", body={
            "course_id": COURSE_ID, "document_name": DOC_NAME, "chapter_name": name,
        }, timeout=1800)
        sync = body.get("course_graph_sync") or {}
        if status == 200 and body.get("success"):
            ok += 1
            print(f"    [{i}/{len(chapters)}] ✅ {name}：{body.get('nodes')}节点/"
                  f"{body.get('edges')}边，Neo4j={body.get('neo4j_synced')}，"
                  f"课程图谱桥接(+{sync.get('added_nodes')}点/+{sync.get('added_relations')}边)")
        else:
            print(f"    [{i}/{len(chapters)}] ⚠️ {name}: http={status} {str(body)[:150]}")

    print(f"\n===== 完成：章节图谱 {ok}/{len(chapters)}，总耗时 {time.time()-t0:.0f}s =====")
    return 0 if ok == len(chapters) else 1


if __name__ == "__main__":
    sys.exit(main())
