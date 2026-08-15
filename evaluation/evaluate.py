#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 助教系统 · RAG 幻觉率 / 准确率评测脚本（零第三方依赖，标准库即可运行）

用法：
  1) 单次评测（先启动后端，确保 knowledge_points 已录入对应课程的知识库）：
     python evaluate.py --base http://localhost:8080/api \
         --username admin --password '你的密码' \
         --course 1 --topk 5 --tag hybrid --out results/run-hybrid.json
     * --tag 仅为结果标签（如 hybrid / bm25 / vector），便于多模式合并对比；
     * 后端通过 RAG_RETRIEVER_MODE 环境变量切换实际检索模式后重启，再分别跑一次。

  2) 合并多模式结果，生成 Markdown 对比报告：
     python evaluate.py --merge results/run-bm25.json results/run-hybrid.json results/run-vector.json \
         --out results/report.md

  3) 可选 LLM-as-judge（语义级判定幻觉/正确性，OpenAI 兼容端点，如 DeepSeek）：
     设置环境变量 LLM_JUDGE_URL / LLM_JUDGE_KEY / LLM_JUDGE_MODEL 后自动启用。

指标说明：
  - recall@k      ：参考答案关键词在检索命中片段（name+description）中的覆盖率 → 检索召回质量
  - coverage      ：参考答案关键词在生成答案中的覆盖率 → 答案要点完整度（客观近似准确率）
  - refused_ratio ：答案中诚实声明「知识库未涵盖」等拒答的比例 → 低幻觉倾向信号
  - judge_correct / judge_hallucination：启用 LLM-as-judge 时的语义判定（0/1 均值）
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.request
import urllib.parse
import http.cookiejar
from datetime import datetime

QA_SET = os.path.join(os.path.dirname(os.path.abspath(__file__)), "qa-set.json")
DEFAULT_BASE = "http://localhost:8080/api"

# ---------- 网络层：Cookie 支持 + JSON 请求 ----------

class ApiClient:
    def __init__(self, base, username=None, password=None, timeout=90):
        self.base = base.rstrip("/")
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf_token = None
        if username and password:
            self.login(username, password)

    def _headers(self, extra=None):
        h = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.csrf_token:
            h["X-XSRF-TOKEN"] = self.csrf_token
        if extra:
            h.update(extra)
        return h

    def _parse_csrf(self):
        for c in self.cookies:
            if c.name.lower() in ("xsrf-token", "x-xsrf-token"):
                self.csrf_token = c.value

    def login(self, username, password):
        body = json.dumps({"username": username, "password": password}).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base}/auth/login", data=body, headers=self._headers(), method="POST")
        try:
            with self.opener.open(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                self._parse_csrf()
                return raw
        except urllib.error.HTTPError as e:
            msg = e.read().decode("utf-8", errors="replace")
            sys.exit(f"[登录失败] HTTP {e.code}: {msg[:300]}\n"
                     "请检查 --username/--password 与 CSRF_ENABLED 配置（若已启用 CSRF，脚本会自动携带 XSRF Cookie）。")

    def post(self, path, params=None, body=None):
        url = f"{self.base}{path}"
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode("utf-8") if body is not None else b"{}"
        req = urllib.request.Request(url, data=data, headers=self._headers(), method="POST")
        with self.opener.open(req, timeout=self.timeout) as resp:
            return json.loads(resp.read().decode("utf-8", errors="replace"))

# ---------- 评测核心 ----------

def load_qa():
    with open(QA_SET, "r", encoding="utf-8") as f:
        return json.load(f)

def norm(s):
    return re.sub(r"\s+", "", s or "")

def keyword_hits(text, keywords):
    t = norm(text)
    return [k for k in keywords if norm(k) in t]

def recall_at_k(retrieve_results, keywords):
    """参考答案关键词在 topK 检索片段中的覆盖率。"""
    corpus = " ".join(
        (r.get("name", "") + " " + r.get("description", "")) for r in retrieve_results)
    hits = keyword_hits(corpus, keywords)
    return len(hits) / max(len(keywords), 1), hits

def answer_coverage(answer, keywords):
    hits = keyword_hits(answer, keywords)
    return len(hits) / max(len(keywords), 1), hits

REFUSED_MARKS = ["知识库未涵盖", "未收录", "未涵盖", "无法回答", "没有找到",
                 "暂未收录", "没有收录", "不在知识库", "未在知识库"]

def refused_signal(answer):
    return any(m in (answer or "") for m in REFUSED_MARKS)

def llm_judge(question, answer, reference):
    """LLM-as-judge：语义判定答案正确性（0/1）与幻觉（0/1）。"""
    url = os.environ.get("LLM_JUDGE_URL", "").strip()
    key = os.environ.get("LLM_JUDGE_KEY", "").strip()
    model = os.environ.get("LLM_JUDGE_MODEL", "deepseek-chat")
    if not url:
        return None
    prompt = (
        "你是严谨的 QA 判卷员。判断模型回答是否依据参考要点正确作答。\n"
        f"问题：{question}\n模型回答：{answer}\n参考要点：{reference}\n"
        "只输出 JSON：{\"correct\":0或1,\"hallucination\":0或1,\"reason\":\"一句话\"}\n"
        "correct=1 表示回答正确/基本正确；hallucination=1 表示回答包含参考要点之外且与知识相悖的捏造事实。")
    body = json.dumps({"model": model, "messages": [
        {"role": "system", "content": "你只输出 JSON。"},
        {"role": "user", "content": prompt}], "temperature": 0}).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {key}" if key else ""})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode("utf-8", errors="replace"))
        content = data["choices"][0]["message"]["content"]
        m = re.search(r"\{.*\}", content, re.S)
        if not m:
            return None
        j = json.loads(m.group(0))
        return {"correct": int(j.get("correct", 0)),
                "hallucination": int(j.get("hallucination", 0)),
                "reason": j.get("reason", "")[:80]}
    except Exception as e:
        print(f"  [judge 调用失败] {e}")
        return None

def evaluate_question(client, q, course_id, topk, use_ask):
    """单题评测：检索召回 +（可选）生成答案。"""
    result = {
        "id": q["id"], "course": q["course"], "domain": q["domain"],
        "difficulty": q["difficulty"], "question": q["question"],
        "keywords": q["keywords"],
    }
    # 阶段一：检索召回
    try:
        r = client.post("/rag/retrieve", params={"query": q["question"],
                                                 "courseId": course_id, "topK": topk})
        results = (r.get("data") or {}).get("results", []) or []
    except Exception as e:
        results = []
        result["retrieve_error"] = str(e)[:200]
    recall, recalled = recall_at_k(results, q["keywords"])
    result["recall@k"] = round(recall, 3)
    result["recalled_keywords"] = recalled
    result["retrieve_count"] = len(results)
    result["retrieve_mode"] = (r.get("data") or {}).get("mode", "")

    if not use_ask:
        return result
    # 阶段二：生成答案
    try:
        a = client.post("/rag/ask", params={"question": q["question"],
                                            "courseId": course_id, "topK": topk})
        data = a.get("data") or {}
        answer = data.get("answer", "")
        result["answer"] = answer[:500]
        result["ask_mode"] = data.get("model", "")
    except Exception as e:
        answer = ""
        result["ask_error"] = str(e)[:200]
    cov, cov_hits = answer_coverage(answer, q["keywords"])
    result["coverage"] = round(cov, 3)
    result["covered_keywords"] = cov_hits
    result["refused"] = refused_signal(answer)
    j = llm_judge(q["question"], answer, q["reference_answer"])
    if j:
        result["judge"] = j
    return result

def run_once(args):
    qa = load_qa()
    client = ApiClient(args.base, args.username, args.password, timeout=args.timeout)
    qs = qa["questions"]
    if args.filter:
        qs = [q for q in qs if q["id"] in set(args.filter.split(","))]
        if not qs:
            sys.exit("--filter 未匹配任何题目 ID")
    print(f"开始评测：{len(qs)} 题 | 课程 courseId={args.course} | topK={args.topk} "
          f"| 标签={args.tag} | ask={'开' if args.ask else '关'} | judge={'开' if os.environ.get('LLM_JUDGE_URL') else '关'}")
    rows, t0 = [], time.time()
    for i, q in enumerate(qs, 1):
        row = evaluate_question(client, q, args.course, args.topk, args.ask)
        rows.append(row)
        print(f"  [{i}/{len(qs)}] {q['id']} recall@{args.topk}={row.get('recall@k')}"
              f" coverage={row.get('coverage')} refused={row.get('refused')}")
        time.sleep(args.sleep)
    elapsed = round(time.time() - t0, 1)

    def avg(key, pred=lambda r: True):
        vals = [r[key] for r in rows if key in r and pred(r)]
        return round(sum(vals) / len(vals), 3) if vals else None

    summary = {
        "tag": args.tag,
        "course_id": args.course,
        "topk": args.topk,
        "questions": len(rows),
        "elapsed_sec": elapsed,
        "recall@k": avg("recall@k"),
        "coverage": avg("coverage"),
        "refused_ratio": avg("refused"),
        "judge_correct": avg("judge", lambda r: "judge" in r),
        "judge_hallucination": avg("judge", lambda r: "judge" in r),
    }
    out = {"meta": qa["meta"], "summary": summary, "results": rows,
           "generated_at": datetime.now().isoformat(timespec="seconds")}
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("\n===== 汇总 =====")
    for k, v in summary.items():
        print(f"  {k}: {v}")
    print(f"结果已写入 {args.out}")

def merge(args):
    agg = []
    for p in args.merge:
        with open(p, "r", encoding="utf-8") as f:
            d = json.load(f)
        agg.append(d)
    lines = [
        "# AI 助教系统 · RAG 评测对比报告",
        "",
        f"> 生成时间：{datetime.now().isoformat(timespec='seconds')} ｜ 评测集：{agg[0]['meta']['title'] if agg else ''}",
        "",
        "## 汇总对比",
        "",
        "| 模式 | 题数 | Recall@k | 答案覆盖率 | 诚实拒答率 | Judge正确率 | Judge幻觉率 |",
        "|------|------|-----------|------------|------------|-------------|-------------|",
    ]
    for d in agg:
        s = d["summary"]
        lines.append(f"| {s.get('tag','-')} | {s['questions']} | {s.get('recall@k','-')} "
                     f"| {s.get('coverage','-')} | {s.get('refused_ratio','-')} "
                     f"| {s.get('judge_correct','-')} | {s.get('judge_hallucination','-')} |")
    lines += [
        "",
        "## 指标说明",
        "",
        "- **Recall@k**：参考答案关键词在检索命中片段中的覆盖率，衡量检索召回质量；",
        "- **答案覆盖率**：参考答案关键词在生成答案中的覆盖率，客观近似准确率；",
        "- **诚实拒答率**：答案中声明「知识库未涵盖」的比例，为低幻觉倾向的正向信号；",
        "- **Judge 正确率 / 幻觉率**：LLM-as-judge 语义判定（启用时才有值）。",
        "",
        "## 结论模板",
        "",
        "> 待填入：混合检索相对纯词法/裸 LLM 的准确率提升幅度、幻觉率下降幅度、"
        "示例问答截图等答辩证据。",
    ]
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"对比报告已写入 {args.out}")

def main():
    ap = argparse.ArgumentParser(description="AI 助教 RAG 评测")
    ap.add_argument("--base", default=DEFAULT_BASE, help="后端 API 基地址（含 /api）")
    ap.add_argument("--username", default=None)
    ap.add_argument("--password", default=None)
    ap.add_argument("--course", type=int, default=1, help="课程 courseId（知识库归属）")
    ap.add_argument("--topk", type=int, default=5)
    ap.add_argument("--tag", default="run", help="本次运行标签（如 hybrid/bm25/vector）")
    ap.add_argument("--ask", action="store_true", help="同时评测生成答案（默认只测检索召回）")
    ap.add_argument("--filter", default=None, help="按题目ID过滤，逗号分隔")
    ap.add_argument("--sleep", type=float, default=0.2, help="题间间隔秒（防限流）")
    ap.add_argument("--timeout", type=int, default=90)
    ap.add_argument("--out", default=None)
    ap.add_argument("--merge", nargs="+", default=None, help="合并多个结果 JSON 生成报告")
    args = ap.parse_args()

    if args.merge:
        if not args.out:
            args.out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                    "results", "report.md")
        merge(args)
        return
    if not args.out:
        args.out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                "results", f"run-{args.tag}.json")
    run_once(args)

if __name__ == "__main__":
    main()
