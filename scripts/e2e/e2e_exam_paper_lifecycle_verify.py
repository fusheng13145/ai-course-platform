"""考试试卷生命周期探针：核对「发布态试卷能否删除」与演示数据在窗情况。

口令来源：--password 或环境变量 PROBE_ADMIN_PASSWORD（不写死在文件里）。
演示种子口令见 backend/src/main/resources/db/seed-demo/V5__rich_seed_data.sql 的账号注释。

用法：
    PROBE_ADMIN_PASSWORD=... python scripts/e2e/e2e_exam_paper_lifecycle_verify.py
    python scripts/e2e/e2e_exam_paper_lifecycle_verify.py --course 4001 --password ...
"""

import argparse
import datetime
import json
import os
import time
import urllib.error
import urllib.request

# A 路径断链 #10：端点可经 E2E_BASE_URL 指向实际入口，默认保持原本机行为。
BASE = os.environ.get("E2E_BASE_URL", "http://127.0.0.1:8080/api").rstrip("/")
E2E_PREFIX = "E2E-"
PROBE_PREFIX = "PROBE-生命周期探针-"


def request(method, path, body=None, cookie=None, retries=2):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    last = (None, "未执行", None)
    for _ in range(retries + 1):
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.status, json.loads(resp.read().decode("utf-8", "replace") or "null"), resp.headers
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            try:
                return exc.code, json.loads(raw or "null"), exc.headers
            except ValueError:
                return exc.code, raw[:200], exc.headers
        except Exception as exc:  # noqa: BLE001
            last = (None, "TRANSPORT-ERROR %r" % (exc,), None)
            time.sleep(1)
    return last


def unwrap(payload):
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def parse_time(value):
    if not value:
        return None
    try:
        return datetime.datetime.fromisoformat(str(value).replace(" ", "T")[:19])
    except ValueError:
        return None


def list_all_papers(cookie):
    papers = []
    for page in range(1, 30):
        status, payload, _ = request("GET", "/exam-papers?page=%d&size=200" % page, cookie=cookie)
        if status != 200:
            raise SystemExit("exam-papers 列表读数失败：HTTP %s" % status)
        data = unwrap(payload) or {}
        papers += data.get("list") or []
        if not data.get("hasNext"):
            break
    return papers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--course", type=int, default=4001, help="探针建卷使用的课程 id")
    parser.add_argument("--username", default=os.environ.get("PROBE_ADMIN_USER", "admin"))
    parser.add_argument("--password", default=os.environ.get("PROBE_ADMIN_PASSWORD"))
    parser.add_argument("--keep", action="store_true", help="保留探针试卷（默认删除并复核）")
    args = parser.parse_args()
    if not args.password:
        raise SystemExit("缺口令：--password 或 PROBE_ADMIN_PASSWORD（见 V5 种子账号注释）")

    status, payload, headers = request(
        "POST", "/auth/login", {"username": args.username, "password": args.password}
    )
    if status != 200 or not headers.get_all("Set-Cookie"):
        raise SystemExit("登录失败：HTTP %s（不回显任何凭据）" % status)
    cookie = "; ".join(c.split(";")[0] for c in headers.get_all("Set-Cookie"))
    print("[1] 登录成功（口令未打印）")

    papers = list_all_papers(cookie)
    by_status = {}
    for paper in papers:
        by_status[str(paper.get("status"))] = by_status.get(str(paper.get("status")), 0) + 1
    residue = [p for p in papers if str(p.get("title", "")).startswith(E2E_PREFIX)]
    print("[2] 试卷总数=%d 按状态=%s" % (len(papers), json.dumps(by_status, ensure_ascii=False)))
    print("[3] e2e 残留卷（标题前缀 %s）=%d id=%s"
          % (E2E_PREFIX, len(residue), sorted(int(p["id"]) for p in residue)))

    now = datetime.datetime.now()
    in_window = [
        p for p in papers
        if str(p.get("status")) == "PUBLISHED"
        and parse_time(p.get("startTime")) and parse_time(p.get("endTime"))
        and parse_time(p.get("startTime")) <= now <= parse_time(p.get("endTime"))
    ]
    print("[4] 已发布且在时间窗内的卷=%d id=%s" % (len(in_window), [int(p["id"]) for p in in_window]))
    for paper in in_window[:5]:
        _, qpayload, _ = request("GET", "/exam-papers/%s/questions" % paper["id"], cookie=cookie)
        print("     卷 %s 题数=%d" % (paper["id"], len(unwrap(qpayload) or [])))

    _, qpayload, _ = request("GET", "/questions?courseId=%d&page=1&size=2" % args.course, cookie=cookie)
    qdata = unwrap(qpayload) or {}
    questions = qdata.get("list") if isinstance(qdata, dict) else qdata
    if not questions:
        raise SystemExit("课程 %d 题库为空，跳过生命周期段" % args.course)

    title = "%s%d" % (PROBE_PREFIX, int(time.time()))
    _, created, _ = request("POST", "/exam-papers", {
        "courseId": args.course, "title": title, "duration": 30, "totalScore": 100,
        "description": "生命周期探针自动卷，运行结束即删除",
    }, cookie=cookie)
    paper_id = (unwrap(created) or {}).get("id")
    if not paper_id:
        raise SystemExit("建卷失败：%s" % json.dumps(created, ensure_ascii=False)[:200])
    request("POST", "/exam-papers/%s/questions" % paper_id, {
        "questions": [
            {"questionId": int(row["id"]), "points": 50, "orderIndex": idx}
            for idx, row in enumerate(questions[:2])
        ]
    }, cookie=cookie)
    pub_status, pub_payload, _ = request("POST", "/exam-papers/%s/publish" % paper_id, None, cookie=cookie)
    _, detail, _ = request("GET", "/exam-papers/%s" % paper_id, cookie=cookie)
    print("[5] 建卷+发布：HTTP %s 卷 id=%s 状态=%s"
          % (pub_status, paper_id, (unwrap(detail) or {}).get("status")))

    if args.keep:
        print("[6] --keep：未执行删除，卷 %s 留在库里（需手工清理）" % paper_id)
        return

    del_status, del_payload, _ = request("DELETE", "/exam-papers/%s" % paper_id, None, cookie=cookie)
    after_status, after_get, _ = request("GET", "/exam-papers/%s" % paper_id, cookie=cookie)
    detail_status = request("GET", "/exam-papers/%s/detail" % paper_id, cookie=cookie)[0]
    if not isinstance(after_get, dict):
        print("[7] 删后 GET 未取到 JSON 信封，无法判定：HTTP %s body=%r" % (after_status, after_get))
        return
    gone_from_list = not any(p["id"] == paper_id for p in list_all_papers(cookie))
    print("[6] 删除发布态卷：HTTP %s code=%s" % (del_status, (del_payload or {}).get("code")))
    print("[7] 删后复核：GET /{id} HTTP %s data=%s / GET /{id}/detail HTTP %s / 列表仍含该卷=%s"
          % (after_status, "null" if after_get.get("data") is None else "非空",
             detail_status, "否" if gone_from_list else "是"))
    verdict = (del_status == 200 and after_get.get("data") is None and gone_from_list)
    print("[8] 判定：发布态试卷「可删除」%s" % ("成立（端点存在且真删）" if verdict else "不成立，见上方读数"))


if __name__ == "__main__":
    main()
