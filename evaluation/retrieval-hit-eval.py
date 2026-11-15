"""B7 · 检索层自动评测（R-K2 的可自动测部分）。

口径（写入读数时必须一并写清）：
- **召回率**：`/api/v1/rag/retrieve` 对某题返回的 sources 非空 → 记为 1 次召回。
- **关键词命中**：返回的前 top_k 条正文里出现该题 `keywords` 中任一项 → 记为命中。
  （这是**规则化近似**，不等于人工判定的"答案正确"；R-K2 的最终结论仍以人工/标注集为准。）

用法：
    python evaluation/retrieval-hit-eval.py --course-id 4178 --course-name 计算机网络 --top-k 5

输出：控制台读数 + `calibration/retrieval-hit-eval-<courseId>.json`
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QA_SET = os.path.join(ROOT, "evaluation", "qa-set.json")
RAG_BASE = os.environ.get("RAG_BASE_URL", "http://127.0.0.1:8002").rstrip("/")


def post_json(url, payload, timeout=60):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def extract_sources(body):
    """兼容三种响应形态：{"results":[...]}（8002 实测）/ {"data":{"sources":[...]}} / {"sources":[...]}。"""
    if not isinstance(body, dict):
        return []
    for path in (("results",), ("data", "sources"), ("sources",)):
        node = body
        for key in path:
            if not isinstance(node, dict) or key not in node:
                node = None
                break
            node = node[key]
        if isinstance(node, list):
            return node
    return []


def source_text(src):
    """8002 实测字段为 document_name / snippet；其余为兼容旧形态。"""
    if not isinstance(src, dict):
        return ""
    return " ".join(str(src.get(k) or "") for k in
                    ("snippet", "document_name", "content", "text", "title", "file_name"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--course-id", type=int, required=True)
    ap.add_argument("--course-name", default="计算机网络")
    ap.add_argument("--top-k", type=int, default=5)
    ap.add_argument("--limit", type=int, default=0, help="0=全部")
    args = ap.parse_args()

    with open(QA_SET, encoding="utf-8") as f:
        qa = json.load(f)
    questions = [q for q in qa.get("questions", []) if q.get("course") == args.course_name]
    if not questions:
        print(f"[FATAL] qa-set 中没有 course={args.course_name} 的题目")
        return 2
    if args.limit:
        questions = questions[:args.limit]

    rows = []
    recalled = 0
    keyword_hit = 0
    errors = 0

    for q in questions:
        qid = q.get("id")
        text = q.get("question", "")
        kws = [str(k) for k in (q.get("keywords") or [])]
        try:
            body = post_json(f"{RAG_BASE}/api/v1/rag/retrieve", {
                "course_id": args.course_id,
                "query": text,
                "top_k": args.top_k,
            })
        except urllib.error.HTTPError as e:
            errors += 1
            rows.append({"id": qid, "status": f"HTTP {e.code}", "recalled": False, "keyword_hit": False})
            continue
        except Exception as e:  # noqa: BLE001
            errors += 1
            rows.append({"id": qid, "status": f"ERR {type(e).__name__}", "recalled": False, "keyword_hit": False})
            continue

        sources = extract_sources(body)
        has_recall = len(sources) > 0
        recalled += 1 if has_recall else 0
        blob = " ".join(source_text(s) for s in sources[:args.top_k])
        hit = any(k and k in blob for k in kws)
        keyword_hit += 1 if hit else 0
        rows.append({
            "id": qid,
            "status": "ok",
            "recalled": has_recall,
            "keyword_hit": hit,
            "source_count": len(sources),
            "top1_preview": source_text(sources[0])[:120] if sources else "",
        })

    total = len(questions)
    rep = {
        "generated_at": __import__("datetime").datetime.now().isoformat(timespec="seconds"),
        "course_id": args.course_id,
        "course_name": args.course_name,
        "top_k": args.top_k,
        "total": total,
        "recalled": recalled,
        "recall_rate": round(recalled / total, 4) if total else 0.0,
        "keyword_hit": keyword_hit,
        "keyword_hit_rate": round(keyword_hit / total, 4) if total else 0.0,
        "errors": errors,
        "caveat": "规则化近似；R-K2 的最终结论需人工或标注集判定，不得用本表替代",
        "rows": rows,
    }

    out_dir = os.path.join(ROOT, "calibration")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"retrieval-hit-eval-{args.course_id}.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(rep, f, ensure_ascii=False, indent=2)

    print(f"题目数      : {total}")
    print(f"召回(非空)  : {recalled}  召回率 {rep['recall_rate']*100:.1f}%")
    print(f"关键词命中  : {keyword_hit}  命中率 {rep['keyword_hit_rate']*100:.1f}%")
    print(f"调用失败    : {errors}")
    print(f"结果文件    : {out}")
    if errors:
        print("[注意] 有调用失败，命中率分母含失败项，读数不可当结论")
    return 0 if errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
