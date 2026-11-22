# -*- coding: utf-8 -*-
"""DeepGraph RAG 集成端到端验证脚本（2026-11-21 接入验收）。

用法：
  python scripts/verify-deepgraph-e2e.py            # 直连 8501（引擎级验证）
  python scripts/verify-deepgraph-e2e.py --via-api  # 走 Java 后端 /api/rag/deepgraph/*（集成级验证，需登录）

验证内容（全部真实链路，无模拟）：
  1. /health 引擎自检（provider / Neo4j / Ollama / 重排器）
  2. /ingest 把 scripts/rag_corpus.json 课程 4178 的 10 章真实课程文本
     经原实现 process_documents 全链路入库（Ollama nomic-embed-text 嵌入 → FAISS+BM25）
  3. /retrieve 双路混合检索 + Neo4j GraphRAG 关系上下文
  4. /qa 真实生成（LLM_PROVIDER=deepseek → DeepSeek API）
  5. /chapter-graph LLM 三元组抽取 + Neo4j 同步
  6. /learning-path 图谱反向追溯前置知识
"""
import io
import json
import sys
import urllib.request
import urllib.parse

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8501"
API_BASE = "http://127.0.0.1:8080/api"
COURSE_ID = 4178
CORPUS = r"C:\Users\ASUS\Desktop\大创赛\ai-course-platform\scripts\rag_corpus.json"

VIA_API = "--via-api" in sys.argv


def load_env(path):
    env = {}
    with io.open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            env[key.strip()] = value.strip()
    return env


ENV = load_env(r"C:\Users\ASUS\Desktop\大创赛\ai-course-platform\.env")


class CookieJar:
    def __init__(self):
        self.cookies = {}

    def header(self):
        return "; ".join(f"{k}={v}" for k, v in self.cookies.items())

    def absorb(self, resp):
        for value in resp.headers.get_all("Set-Cookie") or []:
            first = value.split(";")[0]
            if "=" in first:
                k, _, v = first.partition("=")
                self.cookies[k.strip()] = v.strip()


JAR = CookieJar()
CSRF_TOKEN = {}


def request(method, url, body=None, json_body=None, headers=None, timeout=300):
    data = None
    final_headers = dict(headers or {})
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        final_headers.setdefault("Content-Type", "application/json")
    elif body is not None:
        data = body.encode("utf-8") if isinstance(body, str) else body
        final_headers.setdefault("Content-Type", "text/plain")
    if JAR.cookies:
        final_headers.setdefault("Cookie", JAR.header())
    if CSRF_TOKEN.get("token") and method in ("POST", "PUT", "DELETE", "PATCH"):
        final_headers.setdefault("X-XSRF-TOKEN", CSRF_TOKEN["token"])
    req = urllib.request.Request(url, data=data, headers=final_headers, method=method)
    resp = urllib.request.urlopen(req, timeout=timeout)
    JAR.absorb(resp)
    for value in resp.headers.get_all("Set-Cookie") or []:
        if header.lower() == "xsrf-token" or "XSRF-TOKEN" in value:
            first = value.split(";")[0]
            if "=" in first:
                CSRF_TOKEN["token"] = first.partition("=")[2]
    raw = resp.read().decode("utf-8", errors="replace")
    return resp.status, raw


def http_json(method, url, **kwargs):
    status, raw = request(method, url, **kwargs)
    try:
        return status, json.loads(raw)
    except Exception:
        return status, {"_raw": raw[:400]}


def step(name, ok, detail=""):
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {name}" + (f" | {detail}" if detail else ""))
    return ok


def main():
    results = []

    if VIA_API:
        # 登录拿 cookie（admin / 123456 演示种子口径）
        status, body = http_json("POST", f"{API_BASE}/auth/login",
                                 json_body={"username": "admin", "password": "123456"})
        results.append(step("登录 admin", status == 200 and body.get("code") == 200,
                            f"http={status} code={body.get('code')}"))
        ingest_base = API_BASE + "/rag/deepgraph"
        retrieve_url = ingest_base + "/retrieve"
        health_url = ingest_base + "/health"
    else:
        ingest_base = BASE + "/api/v1/deepgraph"
        retrieve_url = ingest_base + "/retrieve"
        health_url = ingest_base + "/health"

    # 1) 健康
    status, health = http_json("GET", health_url)
    engine = (health.get("engine") or {}) if isinstance(health, dict) else {}
    results.append(step("健康检查 8501 引擎可达", status == 200,
                        f"provider={engine.get('provider')} reranker_loaded={engine.get('reranker_loaded')}"))
    neo4j = (health.get("neo4j") or {})
    print(f"       engine.available={engine.get('available')} provider_detail={engine.get('provider_detail')}")
    print(f"       neo4j.reachable={neo4j.get('reachable')} embeddings={engine.get('embeddings_model')}")
    print(f"       cross_encoder={engine.get('cross_encoder')}")

    # 2) 入库：课程 4178 十章真实语料（经原实现 process_documents 全链路）
    corpus = json.load(io.open(CORPUS, encoding="utf-8"))
    docs = corpus if isinstance(corpus, list) else corpus.get("documents", [])
    chapters = [d for d in docs if d.get("course_id") == COURSE_ID]
    ok_all = True
    chunk_total = 0
    for d in chapters:
        status, body = http_json("POST", f"{ingest_base}/ingest",
                                 json_body={"course_id": COURSE_ID,
                                            "document_name": d["document_name"],
                                            "content": d["content"]},
                                 timeout=600)
        ok = status == 200 and body.get("success") is True
        if not ok:
            ok_all = False
            print(f"       [WARN] {d['document_name']} -> http={status} body={str(body)[:200]}")
        else:
            chunk_total += body.get("chunk_count") or 0
    results.append(step(f"文档入库（{len(chapters)} 章经 process_documents 全链路）", ok_all,
                        f"累计 chunk={chunk_total}"))

    # 3) 检索：双路混合 + 图谱上下文
    status, body = http_json("POST", retrieve_url,
                             json_body={"query": "TCP 三次握手的目的是什么？", "course_id": COURSE_ID, "top_k": 3},
                             timeout=600)
    results_data = body.get("results") or []
    graph_ctx = body.get("graph_context") or ""
    results.append(step("混合检索（FAISS+BM25+HyDE+重排）返回 ENGINE 证据", status == 200 and body.get("mode") == "ENGINE" and len(results_data) > 0,
                        f"http={status} 命中={len(results_data)} 首条得分={results_data[0].get('relevance_score') if results_data else None}"))
    if results_data:
        print(f"       首条来源：{results_data[0].get('document_name')} | snippet={str(results_data[0].get('snippet'))[:80]}...")
    print(f"       图谱上下文行数={len([l for l in graph_ctx.split(chr(10)) if l.strip()])} 示例={graph_ctx[:80]}")

    # 4) 问答（真实生成）
    status, body = http_json("POST", f"{ingest_base}/qa",
                             json_body={"question": "TCP 和 UDP 的核心区别是什么？请依据课程资料回答。",
                                        "course_id": COURSE_ID,
                                        "answer_mode": "向量检索 + 知识图谱"},
                             timeout=600)
    answer = body.get("answer") or ""
    results.append(step("真实问答（DeepSeek/Ollama 生成，带检索上下文）", status == 200 and body.get("mode") == "ENGINE" and len(answer) > 30,
                        f"http={status} 答案长度={len(answer)} 引用={len(body.get('sources') or [])} 条"))
    print(f"       答案摘录：{answer[:160]}...")

    # 5) 章节图谱（LLM 三元组抽取 + Neo4j 同步）
    status, body = http_json("POST", f"{ingest_base}/chapter-graph",
                             json_body={"course_id": COURSE_ID,
                                        "document_name": "计算机网络-第6章-传输层TCP与UDP",
                                        "chapter_name": "传输层TCP与UDP",
                                        "chapter_text": [d for d in chapters if "第6章" in d["document_name"]][0]["content"]},
                             timeout=600)
    neo4j_synced = body.get("neo4j_synced")
    results.append(step("章节图谱构建（LLM 三元组抽取）", status == 200 and body.get("success") is True,
                        f"http={status} 节点={body.get('nodes')} 边={body.get('edges')} Neo4j同步={neo4j_synced}"))

    # 6) 学习路径
    status, body = http_json("POST", f"{ingest_base}/learning-path",
                             json_body={"target": "TCP", "course_id": COURSE_ID, "max_depth": 3,
                                        "search_all_sources": True},
                             timeout=600)
    path_text = body.get("path_text") or ""
    results.append(step("学习路径规划（Neo4j 反向追溯）", status == 200 and body.get("mode") == "ENGINE" and len(path_text) > 10,
                        f"http={status} 路径文本长度={len(path_text)}"))
    print(f"       路径摘录：{path_text[:140]}...")

    passed = sum(1 for ok in results if ok)
    print(f"\n===== 结论：{passed}/{len(results)} 项通过 =====")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
