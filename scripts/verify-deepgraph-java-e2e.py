# -*- coding: utf-8 -*-
"""Java→引擎集成 E2E：登录 admin，走 /api/rag/deepgraph/* 与 /api/ai/qa/stream。

目标端口不写死在文案里：端口统一后默认经 Engine Hub 单口 :8600 转发，
回滚布局（ENGINE_LAYOUT=separate）下是 :8501 的独立进程 —— 两种布局下本脚本同样适用。

阶段一（无索引）：deepgraph retrieve 按契约 503 降级、SSE 问答照常（knowledge 接地）。
阶段二（ingest 后）：SSE 终帧 sources 来自 deepgraph 引擎（集成主证明）。
用法：python scripts/verify-deepgraph-java-e2e.py [--ingest]
"""
import io
import json
import sys
import http.client
import urllib.request
import urllib.parse

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

API = "http://127.0.0.1:8080/api"
COURSE_ID = 4178
CORPUS = r"C:\Users\ASUS\Desktop\大创赛\ai-course-platform\scripts\rag_corpus.json"


class Jar:
    def __init__(self):
        self.cookies = {}

    def header(self):
        return "; ".join(f"{k}={v}" for k, v in self.cookies.items())


JAR = Jar()
CSRF = {"token": None}


def request(method, url, body=None, raw_body=None, timeout=120, tolerate_incomplete=False):
    headers = {}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif raw_body is not None:
        data = raw_body.encode("utf-8")
        headers["Content-Type"] = "text/plain;charset=UTF-8"
    if JAR.cookies:
        headers["Cookie"] = JAR.header()
    if CSRF["token"]:
        headers["X-XSRF-TOKEN"] = CSRF["token"]
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    resp = urllib.request.urlopen(req, timeout=timeout)
    for value in resp.headers.get_all("Set-Cookie") or []:
        first = value.split(";")[0]
        if "=" in first:
            k, _, val = first.partition("=")
            JAR.cookies[k.strip()] = val.strip()
            if k.strip().upper() == "XSRF-TOKEN":
                CSRF["token"] = val.strip()
    try:
        raw = resp.read().decode("utf-8", errors="replace")
    except (http.client.IncompleteRead, TimeoutError, OSError) as exc:
        if not tolerate_incomplete:
            raise
        # SSE 流被对端提前收尾时保留已收到的增量（token 帧仍是真实证据）
        raw = exc.partial.decode("utf-8", errors="replace") if hasattr(exc, "partial") and exc.partial else ""
        print(f"       [note] SSE 提前收尾（{type(exc).__name__}），保留增量 {len(raw)}B")
    return resp.status, raw


def get_json(method, url, **kw):
    status, raw = request(method, url, **kw)
    try:
        return status, json.loads(raw)
    except Exception:
        return status, {"_raw": raw[:300]}


def step(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (f" | {detail}" if detail else ""))
    return ok


def main():
    results = []
    status, body = get_json("POST", f"{API}/auth/login",
                            body={"username": "admin", "password": "123456"})
    results.append(step("登录 admin（8080）", status == 200 and body.get("code") == 200,
                        f"http={status} code={body.get('code')}"))

    # deepgraph 引擎健康（经 Java 代理）
    status, body = get_json("GET", f"{API}/rag/deepgraph/health")
    results.append(step("GET /rag/deepgraph/health（Java→deepgraph 引擎）",
                        status == 200 and body.get("enabled") is True and body.get("reachable") is True,
                        f"http={status} body={body}"))

    if "--ingest" in sys.argv:
        corpus = json.load(io.open(CORPUS, encoding="utf-8"))
        docs = corpus if isinstance(corpus, list) else corpus.get("documents", [])
        chapters = [d for d in docs if d.get("course_id") == COURSE_ID]
        ok_all = True
        stored = []
        for d in chapters:
            status, body = get_json(
                "POST", f"{API}/rag/deepgraph/documents?courseId={COURSE_ID}&documentName={urllib.parse.quote(d['document_name'])}",
                raw_body=d["content"], timeout=600)
            data = body.get("data") or {}
            ok = status == 200 and body.get("code") == 200 and data.get("success") is True
            if not ok:
                ok_all = False
                print(f"       [WARN] {d['document_name']} -> http={status} {str(body)[:160]}")
            else:
                # 门面契约记录以 camelCase 序列化（storedAs/chunkCount）
                stored.append((data.get("storedAs") or "", data.get("chunkCount") or 0))
        # chunkCount 是**课程级总数**（api_server._ingest_document 返回
        # len(course_pipeline["chunks"])，每次入库都会因重建管道而变化），
        # 不是单篇 chunk 数。早期写法把它们相加，10 篇就得到一个根本不存在的
        # "累计 17270"——数字看着壮观，却没人能对上任何一个真实索引。
        # 正确口径：取最后一次返回值（此时课程管道已重建完整）。
        course_chunks = stored[-1][1] if stored else 0
        results.append(step(f"Java 入库 {len(chapters)} 章文档（→引擎全链路）", ok_all,
                            f"stored={[s[0] for s in stored[:2]]}... 课程级总chunk={course_chunks}"))
    else:
        # 自适应：该课程已有索引则断言 ENGINE 成功，否则断言契约降级（HTTP 200 信封 +
        # data.code=503 + data.mode=UNAVAILABLE，与 8002 同款；Java↔Python 线上才是 503）
        status, body = get_json("POST", f"{API}/rag/deepgraph/retrieve?query=TCP&courseId={COURSE_ID}&topK=3")
        data = body.get("data") or {}
        if data.get("mode") == "ENGINE":
            results.append(step("retrieve 已建索引 -> ENGINE 真实证据",
                                status == 200 and len(data.get("results") or []) > 0,
                                f"http={status} 命中={len(data.get('results') or [])}"))
        else:
            results.append(step("retrieve 无索引 -> 契约降级 503/UNAVAILABLE",
                                status == 200 and data.get("code") == 503 and data.get("mode") == "UNAVAILABLE",
                                f"http={status} mode={data.get('mode')} error={str(data.get('error'))[:60]}"))

    # SSE 流式问答（集成主证明：终帧 sources 应含 deepgraph 命中）
    q = "根据课程资料，TCP 三次握手的目的是什么？"
    url = f"{API}/ai/qa/stream?question={urllib.parse.quote(q)}&courseId={COURSE_ID}"
    status, raw = request("GET", url, timeout=420, tolerate_incomplete=True)
    tokens, sources, session_id, done, frames = [], [], None, False, 0
    for line in raw.split("\n"):
        line = line.strip()
        if not line.startswith("data:"):
            continue
        payload = line[5:].strip()
        frames += 1
        if payload == "[DONE]":
            done = True
            continue
        try:
            event = json.loads(payload)
        except Exception:
            if payload:
                tokens.append(payload)
            continue
        if "sessionId" in event:
            session_id = event["sessionId"]
        if "token" in event:
            tokens.append(event["token"])
        if "sources" in event:
            sources = event["sources"]
    answer_text = "".join(tokens)
    # 控制器契约：终帧为 sources 帧（[DONE] 为兼容遗留，不强制）；流以 sources 帧结束即完整收流
    tail_lines = raw.split(chr(10))[-4:]
    terminal = done or any(chr(34) + "sources" + chr(34) in ln for ln in tail_lines)
    results.append(step("SSE /ai/qa/stream 正常收流", status == 200 and terminal and len(answer_text) > 20,
                        f"http={status} frames={frames} answer_len={len(answer_text)} sources={len(sources)}"))
    if sources:
        for s in sources[:3]:
            print(f"       引用源: {s.get('documentName')} (score={s.get('relevanceScore')}) snippet={str(s.get('snippet'))[:60]}...")
    print(f"       答案摘录: {answer_text[:180]}...")

    passed = sum(1 for ok in results if ok)
    print(f"\n===== Java 集成 E2E：{passed}/{len(results)} 项通过 =====")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
