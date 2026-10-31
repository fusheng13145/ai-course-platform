"""历史题目知识点回填：对 knowledge_points 为空的题目，按课程知识点表做内容匹配后回填。

规则与边界：
- 只填空、不覆盖：已有标签的题目一律跳过。
- 标签源是**课程知识点表**；知识点为空的课程必须用 `--map <课程>=<源课程>` 显式映射，
  不做同名课程的自动猜测。
- 匹配按知识点名的分段（与/和/、/空格/括号/斜杠切分）在「题干＋选项＋解析」里找，
  取总分最高者；最高分并列视为歧义、跳过并报告。
- 默认 dry-run；`--apply` 才写库，写前把原值落备份 JSON（默认落到系统临时目录），
  `--revert <backup.json>` 可按备份还原。
- **限制**：原值为空的行无法还原——`knowledge_points` 是 JSON 列，空串会被 MySQL 拒绝（400），
  而 null 会被 MyBatis-Plus 的 updateById 跳过；脚本会逐条如实打印「无法还原为空」。

口令不写死：读环境变量 BACKFILL_ADMIN_USER / BACKFILL_ADMIN_PASSWORD。

用法：
    python scripts/backfill_question_kp.py --map 4047=4007 --map 4060=4007
    python scripts/backfill_question_kp.py --map 4047=4007 --map 4060=4007 --apply
    python scripts/backfill_question_kp.py --revert /tmp/backfill-kp-....json
"""

import argparse
import datetime
import json
import os
import re
import tempfile
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api"
SPLIT = re.compile(r"[与和、/\\\s()（）·\-—]+")


def request(method, path, body=None, cookie=None, retries=1):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    last = (None, "未执行")
    for _ in range(retries + 1):
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8", "replace")
                return resp.status, (json.loads(raw) if raw else None), resp.headers
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            try:
                return exc.code, json.loads(raw or "null"), exc.headers
            except ValueError:
                return exc.code, raw[:200], exc.headers
        except Exception as exc:  # noqa: BLE001
            last = (None, repr(exc))
    return last[0], last[1], None


def unwrap(payload):
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def login():
    user = os.environ.get("BACKFILL_ADMIN_USER", "admin")
    pwd = os.environ.get("BACKFILL_ADMIN_PASSWORD")
    if not pwd:
        raise SystemExit("缺口令：BACKFILL_ADMIN_PASSWORD（演示种子口令见 V5 种子账号注释）")
    status, _, headers = request("POST", "/auth/login", {"username": user, "password": pwd})
    cookies = (headers.get_all("Set-Cookie") if headers else None) or []
    if status != 200 or not cookies:
        raise SystemExit("登录失败：HTTP %s（不回显任何凭据）" % status)
    return "; ".join(c.split(";")[0] for c in cookies)


def fetch_question(qid, cookie):
    status, payload, _ = request("GET", "/questions/%s" % qid, cookie=cookie)
    if status != 200:
        return None
    return unwrap(payload)


def build_update_payload(question, kp_text):
    """PUT /questions/{id} 带 @Valid（题干不能为空），必须整行回写而不是只发标签。"""
    return {
        "type": question.get("type"),
        "content": question.get("content"),
        "difficulty": question.get("difficulty"),
        "courseId": question.get("courseId"),
        "options": question.get("options"),
        "correctAnswers": question.get("correctAnswers"),
        "explanation": question.get("explanation"),
        "knowledgePoints": kp_text,
    }


def list_courses(cookie):
    status, payload, _ = request("GET", "/courses?page=1&size=200", cookie=cookie)
    if status != 200:
        raise SystemExit("课程列表失败：HTTP %s" % status)
    data = unwrap(payload) or {}
    return data.get("list") or []


def list_questions(course_id, cookie):
    rows = []
    for page in range(1, 30):
        status, payload, _ = request(
            "GET", "/questions?courseId=%s&page=%d&size=200" % (course_id, page), cookie=cookie
        )
        if status != 200:
            raise SystemExit("题目列表失败：HTTP %s" % status)
        data = unwrap(payload) or {}
        rows += data.get("list") or []
        if not data.get("hasNext"):
            break
    return rows


def list_points(course_id, cookie):
    status, payload, _ = request("GET", "/knowledge/points?courseId=%s" % course_id, cookie=cookie)
    if status != 200:
        return []
    data = unwrap(payload)
    if isinstance(data, list):
        return data
    return (data or {}).get("list") or []


def is_blank_kp(raw):
    if raw is None:
        return True
    text = str(raw).strip()
    return text == "" or text == "[]"


def normalize(text):
    return re.sub(r"[\s，。、；：？！“”‘’（）()【】\[\]《》,.;:?!\"'/\\|]+", "", str(text or "")).lower()


def segments(name):
    parts = [p for p in SPLIT.split(str(name)) if p and len(p) >= 2]
    return parts or [str(name)]


def score_kp(kp_name, haystack):
    total = 0
    hit_parts = []
    for part in segments(kp_name):
        if normalize(part) and normalize(part) in haystack:
            total += len(part)
            hit_parts.append(part)
    return total, hit_parts


def match_question(question, kp_names):
    haystack = normalize(
        " ".join(
            [
                str(question.get("content") or ""),
                str(question.get("options") or ""),
                str(question.get("explanation") or ""),
            ]
        )
    )
    scored = []
    for name in kp_names:
        score, hits = score_kp(name, haystack)
        if score > 0:
            scored.append((score, name, hits))
    scored.sort(key=lambda item: item[0], reverse=True)
    if not scored:
        return None, "no-match", []
    if len(scored) > 1 and scored[1][0] == scored[0][0]:
        return None, "ambiguous", [s[1] for s in scored[:3]]
    best = scored[0]
    if best[0] < 2:
        return None, "weak-match", [best[1]]
    return best[1], "matched", best[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--map", action="append", default=[], help="课程ID=知识点源课程ID")
    parser.add_argument("--course", type=int, action="append", default=[], help="只处理这些课程")
    parser.add_argument("--assign", action="append", default=[], help="人工裁定：题目ID=知识点名")
    parser.add_argument("--apply", action="store_true", help="真正写库（默认 dry-run）")
    parser.add_argument("--revert", help="按备份 JSON 还原")
    parser.add_argument("--out", help="dry-run/apply 报告输出路径")
    args = parser.parse_args()

    cookie = login()

    if args.revert:
        backup = json.load(open(args.revert, encoding="utf-8"))
        restored = 0
        for row in backup.get("rows", []):
            if not str(row.get("before") or "").strip():
                print("revert", row["id"], "原始为空：JSON 列无法写空，跳过（后端限制）")
                continue
            current = fetch_question(row["id"], cookie)
            if not current:
                print("revert", row["id"], "题目不存在，跳过")
                continue
            status, payload, _ = request(
                "PUT",
                "/questions/%s" % row["id"],
                build_update_payload(current, row.get("before") or ""),
                cookie=cookie,
            )
            ok = status == 200 and isinstance(payload, dict) and payload.get("code") == 200
            restored += 1 if ok else 0
            print("revert", row["id"], "HTTP", status, "ok" if ok else "FAILED")
        print("还原完成：%d/%d" % (restored, len(backup.get("rows", []))))
        return

    assigned = {}
    for item in args.assign:
        left, _, right = item.partition("=")
        assigned[int(left)] = right.strip()
    source_map = {}
    for item in args.map:
        left, _, right = item.partition("=")
        source_map[int(left)] = int(right)

    courses = list_courses(cookie)
    if args.course:
        courses = [c for c in courses if int(c["id"]) in set(args.course)]

    report = {"generatedAt": datetime.datetime.now().isoformat(timespec="seconds"), "rows": [], "skipped": []}
    backup = {"rows": [], "generatedAt": report["generatedAt"]}

    for course in courses:
        cid = int(course["id"])
        questions = list_questions(cid, cookie)
        blanks = [q for q in questions if is_blank_kp(q.get("knowledgePoints"))]
        if not blanks:
            continue
        source_id = source_map.get(cid, cid)
        points = list_points(source_id, cookie)
        names = [str(p.get("name") or "").strip() for p in points if str(p.get("name") or "").strip()]
        if not names:
            report["skipped"].append(
                {"courseId": cid, "course": course.get("name"), "reason": "知识点源为空", "questions": len(blanks)}
            )
            continue
        for q in blanks:
            if int(q.get("id")) in assigned:
                picked = assigned[int(q["id"])]
                if picked not in names:
                    report["skipped"].append(
                        {
                            "courseId": cid,
                            "id": q.get("id"),
                            "reason": "assign-not-in-source",
                            "content": str(q.get("content") or "")[:48],
                        }
                    )
                    continue
                kp, reason, hits = picked, "assigned", [picked]
            else:
                kp, reason, hits = match_question(q, names)
            row = {
                "courseId": cid,
                "course": course.get("name"),
                "id": q.get("id"),
                "content": str(q.get("content") or "")[:48],
                "sourceCourse": source_id,
                "kp": kp,
                "reason": reason,
                "hits": hits,
            }
            if kp:
                report["rows"].append(row)
                backup["rows"].append({"id": q.get("id"), "before": q.get("knowledgePoints") or "", "after": kp})
            else:
                report["skipped"].append(row)

    print("=" * 72)
    for row in report["rows"]:
        print("MATCH %-8s %-14s %-26s -> %s" % (row["courseId"], row["id"], row["kp"], "、".join(row["hits"])))
    for row in report["skipped"]:
        cand = (" candidates=" + " | ".join(row.get("hits") or [])) if row.get("hits") else ""
        print(
            "SKIP  %-8s %-14s reason=%-10s %s%s"
            % (row.get("courseId"), row.get("id", "-"), row.get("reason"), row.get("content", ""), cand)
        )
    print("=" * 72)
    print("待回填 %d 条，跳过 %d 条" % (len(report["rows"]), len(report["skipped"])))

    if not args.apply:
        print("dry-run：未写库。加 --apply 执行（会先落备份）。")
        return

    backup_path = args.out or os.path.join(tempfile.gettempdir(), "backfill-kp-%s.json" % int(datetime.datetime.now().timestamp()))
    json.dump(backup, open(backup_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("备份已写入：", backup_path)

    written = 0
    for row in report["rows"]:
        current = fetch_question(row["id"], cookie)
        if not current:
            print("apply", row["id"], "题目不存在，跳过")
            continue
        status, payload, _ = request(
            "PUT",
            "/questions/%s" % row["id"],
            build_update_payload(current, json.dumps([row["kp"]], ensure_ascii=False)),
            cookie=cookie,
        )
        ok = status == 200 and isinstance(payload, dict) and payload.get("code") == 200
        written += 1 if ok else 0
        print("apply", row["id"], "HTTP", status, "ok" if ok else "FAILED")
    print("写库完成：%d/%d" % (written, len(report["rows"])))


if __name__ == "__main__":
    main()
