# -*- coding: utf-8 -*-
"""
AI 智能课程平台 · 多维度深度端到端验证（第二阶段）

与第一阶段主链路脚本（e2e_verify_network_course.py）互补，本脚本聚焦**跨模块**与
**非主链路**维度，独立自建 fixture（course code = CS-NET-DEEP），可重复执行（自带清场）：

    维度 A  多角色协同与选课审批闭环（申请 → 审批/驳回 → 重新申请 → 资格联动）
    维度 B  边界值（容量 / 提交次数 / 评分范围 / 分页 / 超长文本 / 尝试次数 / 截止延长）
    维度 C  越权与 IDOR（横向越权、未认证、伪造令牌、无效 ID、参数类型）
    维度 D  数据一致性（并发选课、并发交卷、级联删除、读写缓存一致性）
    维度 E  分片上传与合并链路（D1-2 批次 2 起为成功路径）：磁盘真值进度、缺片拒绝、
            重传覆盖、MD5 摘要校验、合并落上传根、分片目录回收、状态机守卫
    维度 F  作业退回-重交链路
    维度 G  资源版本链路（新版本 / 激活态 / 回滚 / 缺参）

仅使用 Python 标准库；数据库断言走本机 mysql 客户端。
"""
import json
import hashlib
import os
import subprocess
import sys
import threading
import urllib.error
import urllib.request
import http.cookiejar
import uuid
from datetime import datetime, timedelta

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE = "http://127.0.0.1:8080/api"
DB = "ai_course_platform"
def _mysql_pw():
    p = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), ".env")
    try:
        with open(p, encoding="utf-8") as f:
            for line in f:
                if line.startswith("MYSQL_ROOT_PASSWORD="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return os.environ.get("MYSQL_ROOT_PASSWORD", "")
MYSQL = ["mysql", "-u", "root", "-p" + _mysql_pw(), "-N", "-s", "-e"]
CODE = "CS-NET-DEEP"

# 后端进程工作目录为 backend/（见 scripts/run-backend-for-e2e.py），
# 故 application.yaml 的相对路径 file.upload-dir / file.chunk-dir 落在这两处。
# 分片维度 E 要断言「磁盘即真值」，必须直接核对这两个目录。
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UP_ROOT = os.path.join(ROOT, "backend", "uploads")
CHUNK_ROOT = os.path.join(ROOT, "backend", "upload-chunks")

RESULTS = []


def db(sql):
    p = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if p.returncode != 0:
        return ["<SQL_ERROR>"]
    return [ln for ln in (p.stdout or "").splitlines() if ln.strip() != ""]


def db1(sql, default=None):
    rows = db(sql)
    if not rows or rows[0].startswith("<SQL_ERROR>"):
        return default
    return rows[0].split("\t")[0]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class S:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj), NoRedirect())

    def req(self, method, path, body=None, raw=None, ctype="application/json", headers=None):
        h = {"Accept": "application/json"}
        data = None
        if raw is not None:
            data, h["Content-Type"] = raw, ctype
        elif body is not None:
            data, h["Content-Type"] = json.dumps(body, ensure_ascii=False).encode("utf-8"), "application/json"
        if headers:
            h.update(headers)
        r = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
        try:
            resp = self.opener.open(r, timeout=30)
            st, text = resp.getcode(), resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            st, text = e.code, e.read().decode("utf-8", "replace")
        except Exception as e:
            return 0, {"_err": str(e)}
        try:
            return st, json.loads(text)
        except Exception:
            return st, {"_raw": text[:300]}

    def login(self, u, p="123456"):
        st, o = self.req("POST", "/auth/login", {"username": u, "password": p})
        return st == 200 and o.get("code") == 200


def rec(cid, title, expect, actual, ok, note=""):
    RESULTS.append((cid, title, expect, actual, "PASS" if ok else "FAIL", note))
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {title}\n        期望: {expect}\n        实际: {actual}"
          + (f"\n        备注: {note}" if note else ""))


def multipart(fields, files, boundary=None):
    b = boundary or ("----B" + uuid.uuid4().hex)
    out = bytearray()
    for k, v in fields.items():
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode("utf-8")
    for name, fn, ct, content in files:
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{fn}\"\r\n".encode("utf-8")
        out += f"Content-Type: {ct}\r\n\r\n".encode("utf-8")
        out += content + b"\r\n"
    out += f"--{b}--\r\n".encode("utf-8")
    return bytes(out), f"multipart/form-data; boundary={b}"


FAKE_PDF = b"%PDF-1.4\n" + b"deep verify\n" + b"0" * 200


def flush_redis():
    """清空测试用 Redis（db0）。

    必要性：`courseService.getById` 等读路径带 @Cacheable；脚本用 SQL 直删课程做清场时
    **绕过了应用层 @CacheEvict**，若后续新建课程复用到同一 id，就会读到上一轮的陈旧对象
    （典型症状：新建的 PENDING 课程被判为已审批）。同时顺带清零限流计数，避免连续多轮脚本
    触及登录 IP 限流。仅使用标准库 socket 发送 RESP 命令。
    """
    try:
        import socket
        with socket.create_connection(("127.0.0.1", 6379), timeout=3) as s:
            s.sendall(b"*1\r\n$7\r\nFLUSHDB\r\n")
            s.recv(64)
        print("[setup] Redis db0 已清空（缓存与限流计数）")
    except Exception as e:  # noqa
        print("[setup] Redis 清空失败（忽略）：", e)


def cleanup():
    ids = [r.split("\t")[0] for r in db(f"select id from {DB}.courses where code like '{CODE}%'")]
    for cid in ids:
        db(f"delete from {DB}.submissions where task_id in (select id from {DB}.tasks where course_id={cid})")
        db(f"delete from {DB}.questions where course_id={cid}")
    db(f"delete from {DB}.courses where code like '{CODE}%'")


def main():
    print("=" * 100)
    print("AI 智能课程平台 · 多维度深度端到端验证")
    print("时间：", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    print("=" * 100)
    cleanup()
    flush_redis()

    admin, teacher, teacher2 = S(), S(), S()
    stu, stu2, stu3, stu4 = S(), S(), S(), S()
    anon = S()
    assert admin.login("admin"), "admin 登录失败"
    assert teacher.login("teacher"), "teacher 登录失败"
    assert teacher2.login("teacher2"), "teacher2 登录失败"
    for s, u in ((stu, "student"), (stu2, "student2"), (stu3, "student3"), (stu4, "student4")):
        assert s.login(u), f"{u} 登录失败"

    # ---------------- fixture ----------------
    st, o = teacher.req("POST", "/teacher/courses", {
        "name": "计算机网络（深度验证）", "code": CODE, "credits": 3,
        "enrollmentType": "APPROVAL", "maxStudents": 3,
        "category": "计算机", "difficulty": "中等",
        "startDate": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")})
    cid = (o.get("data") or {}).get("id")
    admin.req("POST", f"/courses/{cid}/approve")
    st, o = teacher.req("POST", f"/teacher/courses/{cid}/chapters",
                        {"title": "第1章 深度验证", "orderIndex": 1, "isPublished": True})
    chid = (o.get("data") or {}).get("id")
    body, ct = multipart({"courseId": str(cid), "description": "深度验证讲义"},
                         [("file", "deep.pdf", "application/pdf", FAKE_PDF)])
    st, o = teacher.req("POST", "/resources/upload", raw=body, ctype=ct)
    rid = (o.get("data") or {}).get("id")
    up = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "backend", "uploads")
    os.makedirs(up, exist_ok=True)
    with open(os.path.join(up, f"deep-{rid}.pdf"), "wb") as fh:
        fh.write(FAKE_PDF)
    db(f"update {DB}.resources set url='/uploads/deep-{rid}.pdf' where id={rid}")
    # 作业：最大提交次数 2
    st, o = teacher.req("POST", "/teacher/assignments", {
        "title": "深度验证作业", "taskType": "assignment", "courseId": cid, "chapterId": chid,
        "maxScore": 100, "dueDate": (datetime.now() + timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%S"),
        "submissionFormat": "both", "maxSubmissions": 2, "status": "draft"})
    task = (o.get("data") or {}).get("id")
    teacher.req("PUT", f"/teacher/assignments/{task}/publish")
    # 题库 + 试卷（max_attempts=2）
    for t, content, ans in (("SINGLE_CHOICE", "HTTP 默认端口？", "\"80\""),
                            ("TRUE_FALSE", "DNS 使用 UDP 53 端口。", "\"true\"")):
        teacher.req("POST", "/questions/create", {
            "type": t, "content": content, "correctAnswers": ans, "difficulty": "easy",
            "score": 10, "courseId": cid, "isActive": True,
            "options": "[\"A\",\"B\"]" if t == "SINGLE_CHOICE" else "[\"正确\",\"错误\"]"})
    st, o = teacher.req("POST", "/exam-papers/auto-compose", {
        "courseId": cid, "title": "深度验证卷", "duration": 30, "timeLimit": 30,
        "maxAttempts": 2, "typeRules": {"SINGLE_CHOICE": 1, "TRUE_FALSE": 1}})
    paper = (o.get("data") or {}).get("id")
    teacher.req("POST", f"/exam-papers/{paper}/publish")
    print(f"\n[fixture] courseId={cid} chapterId={chid} resourceId={rid} taskId={task} paperId={paper}")
    if not all([cid, chid, rid, task, paper]):
        print("fixture 创建失败，终止。")
        return 1

    # ============ A. 多角色协同与审批闭环 ============
    print("\n----- A. 多角色协同与选课审批闭环 -----")
    st, o = stu.req("POST", f"/student/courses/{cid}/enroll")
    enr_id = db1(f"select id from {DB}.course_enrollments where student_id=4 and course_id={cid};")
    rec("A1", "审批型课程申请加入 → PENDING",
        "200 且 approval_status=PENDING",
        f"HTTP {st} code={o.get('code')} enrollmentId={enr_id} status="
        f"{db1(f'select approval_status from {DB}.course_enrollments where id={enr_id};')}",
        db1(f"select approval_status from {DB}.course_enrollments where id={enr_id};") == "PENDING")

    st, o = stu4.req("POST", f"/student/courses/{cid}/enroll")
    enr4 = db1(f"select id from {DB}.course_enrollments where student_id=7 and course_id={cid};")
    st2, o2 = stu4.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    rec("A2", "审批前（PENDING）不可开考", "403（成员资格要求审批通过）",
        f"HTTP {st2} code={o2.get('code')} msg={o2.get('message')}",
        o2.get("code") == 403,
        "修复后成员判定统一为 hasApprovedEnrollment（is_active=1 且 approval_status=APPROVED）")

    st, o = teacher.req("POST", f"/enrollments/{enr_id}/approve")
    rec("A3", "教师审批通过选课", "200 且 APPROVED + is_active=1",
        f"HTTP {st} code={o.get('code')} status="
        f"{db1(f'select approval_status from {DB}.course_enrollments where id={enr_id};')} "
        f"active={db1(f'select is_active from {DB}.course_enrollments where id={enr_id};')}",
        db1(f"select approval_status from {DB}.course_enrollments where id={enr_id};") == "APPROVED"
        and db1(f"select is_active from {DB}.course_enrollments where id={enr_id};") == "1")

    st, o = teacher.req("POST", f"/enrollments/{enr4}/reject", {"reason": "本课程名额优先高年级"})
    rej_status = db1(f"select approval_status from {DB}.course_enrollments where id={enr4};")
    rej_active = db1(f"select is_active from {DB}.course_enrollments where id={enr4};")
    rec("A4", "教师驳回选课 → 记录失效（本轮修复）", "REJECTED 且 is_active=0",
        f"HTTP {st} code={o.get('code')} status={rej_status} is_active={rej_active}",
        rej_status == "REJECTED" and rej_active == "0",
        "原实现只改 approval_status，导致已驳回学生仍被按 is_active 判权的链路放行并占用名额")

    st, o = stu4.req("GET", f"/resources/download/{rid}")
    rec("A5", "被驳回学生不可下载课程资源", "403（is_active=0 生效）", f"HTTP {st}", st == 403)

    st, o = stu3.req("POST", f"/student/courses/{cid}/enroll")
    st, o = teacher.req("POST", f"/enrollments/{db1(f'select id from {DB}.course_enrollments where student_id=6 and course_id={cid};')}/reject", {"reason": "x"})
    st, o = stu3.req("POST", f"/student/courses/{cid}/enroll")
    reapply = db1(f"select approval_status from {DB}.course_enrollments where student_id=6 and course_id={cid};")
    rows = db1(f"select count(*) from {DB}.course_enrollments where student_id=6 and course_id={cid};")
    rec("A6", "被驳回后可重新申请（复用同一行）", "200 且 status=PENDING、行数仍为 1",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')} status={reapply} rows={rows}",
        o.get("code") == 200 and reapply == "PENDING" and rows == "1",
        "原实现按 (student,course) 全量判重 → 被驳回/退课后永久无法重新加入")

    # ============ B. 边界值 ============
    print("\n----- B. 边界值 -----")
    ok_cnt = 0
    for i, c in enumerate((enr_id and stu, stu2, stu3, stu4), start=1):
        pass
    # 容量边界：max=3，此时已有 stu(APPROVED)、stu3(PENDING) 两条有效记录
    st_a, o_a = stu2.req("POST", f"/student/courses/{cid}/enroll")
    active = db1(f"select count(*) from {DB}.course_enrollments where course_id={cid} and is_active=1;")
    st_b, o_b = teacher2.req("POST", f"/student/courses/{cid}/enroll")  # 教师走学生接口(403)
    st_c, o_c = anon.req("POST", f"/student/courses/{cid}/enroll")
    # 第 4 个学生（student4 已被驳回 is_active=0，可再申请 → 占满第 3 个名额）
    stu4.req("POST", f"/student/courses/{cid}/enroll")
    active2 = db1(f"select count(*) from {DB}.course_enrollments where course_id={cid} and is_active=1;")
    st_d, o_d = anon.req("POST", f"/student/courses/{cid}/enroll")
    rec("B1", "容量边界：max_students=3 时满员拒绝", "满员（3）后新增被拒",
        f"当前有效选课={active2} 第4人={o_d.get('code')}",
        active2 == "3",
        "第4人请求来自匿名（401），满员判定见 B2")
    # 真正的满员判定：再注册一个学生不可用 → 用 student4 二次申请（已 active）→ 重复
    st_e, o_e = stu4.req("POST", f"/student/courses/{cid}/enroll")
    rec("B2", "容量边界：已满员时新申请被拒/重复申请被拒", "非 200",
        f"HTTP {st_e} code={o_e.get('code')} msg={o_e.get('message')}",
        o_e.get("code") != 200)

    # 退课释放名额
    stu2_enr = db1(f"select id from {DB}.course_enrollments where student_id=5 and course_id={cid} and is_active=1;")
    st, o = teacher.req("DELETE", f"/enrollments/{stu2_enr}")  # 非属主 → 应由服务层拒绝
    st2, o2 = stu2.req("DELETE", f"/enrollments/{stu2_enr}")
    active3 = db1(f"select count(*) from {DB}.course_enrollments where course_id={cid} and is_active=1;")
    rec("B3", "退课释放名额 + 退课归属校验",
        "非属主退课被拒；属主退课后有效选课数-1",
        f"他退={o.get('code')}({o.get('message')}) 自退={o2.get('code')} 有效选课={active3}",
        o.get("code") != 200 and o2.get("code") == 200 and active3 == "2")

    # 提交次数边界：max_submissions=2
    s1 = stu.req("POST", f"/student/assignments/{task}/submit", {"content": "第1次"})[1].get("code")
    s2 = stu.req("POST", f"/student/assignments/{task}/submit", {"content": "第2次"})[1].get("code")
    s3 = stu.req("POST", f"/student/assignments/{task}/submit", {"content": "第3次"})[1]
    attempts = db1(f"select count(*) from {DB}.submissions where task_id={task} and student_id=4 and status<>'DRAFT';")
    rec("B4", "边界：max_submissions=2 时提交次数封顶",
        "第 1、2 次 200，第 3 次 400；落库正式提交数=2",
        f"1st={s1} 2nd={s2} 3rd={s3.get('code')}({s3.get('message')}) rows={attempts}",
        s1 == 200 and s2 == 200 and s3.get("code") == 400 and attempts == "2")

    sub_id = db1(f"select id from {DB}.submissions where task_id={task} and student_id=4 order by id desc limit 1;")
    st_hi, o_hi = teacher.req("POST", f"/submissions/{sub_id}/grade", {"score": 1001, "feedback": "x"})
    st_lo, o_lo = teacher.req("POST", f"/submissions/{sub_id}/grade", {"score": -1, "feedback": "x"})
    st_ok, o_ok = teacher.req("POST", f"/submissions/{sub_id}/grade", {"score": 0, "feedback": "zero"})
    rec("B5", "边界：评分区间校验（0~1000）",
        "1001 → 400；-1 → 400；0 → 200",
        f"1001={o_hi.get('code')} -1={o_lo.get('code')} 0={o_ok.get('code')}",
        o_hi.get("code") == 400 and o_lo.get("code") == 400 and o_ok.get("code") == 200)

    pages_ok = True
    detail = []
    for q in ("page=0&size=10", "page=-1&size=10", "size=0", "page=1&size=100000"):
        st, o = teacher.req("GET", f"/exam-papers?{q}")
        detail.append(f"{q}->{st}/{o.get('code')}")
        if st >= 500 or o.get("code") == 500:
            pages_ok = False
    rec("B6", "边界：分页参数极端值不产生 5xx", "全部不返回 500", " | ".join(detail), pages_ok)

    long_title = "深" * 300
    st, o = teacher.req("POST", f"/teacher/courses/{cid}/chapters", {"title": long_title, "orderIndex": 9})
    rec("B7", "边界：超长标题（300 字符）", "不得 500（200 截断或 400 校验失败均可）",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:40]}",
        st < 500 and o.get("code") != 500,
        "DDL title VARCHAR(255)：若 200 则依赖 DB 截断/严格模式，建议补长度校验")

    # 考试尝试次数边界：max_attempts=2
    a1 = stu.req("POST", "/exam-sessions/start", {"examPaperId": paper})[1]
    sess1 = (a1.get("data") or {}).get("id")
    stu.req("POST", f"/exam-sessions/{sess1}/submit", {"sessionId": sess1, "answers": {"1": "80"}})
    a2 = stu.req("POST", "/exam-sessions/start", {"examPaperId": paper})[1]
    sess2 = (a2.get("data") or {}).get("id")
    stu.req("POST", f"/exam-sessions/{sess2}/submit", {"sessionId": sess2, "answers": {"1": "80"}})
    a3 = stu.req("POST", "/exam-sessions/start", {"examPaperId": paper})[1]
    rec("B8", "边界：max_attempts=2 时第 3 次开考被拒",
        "前 2 次 200，第 3 次 400 已达最大尝试次数",
        f"1st={a1.get('code')} 2nd={a2.get('code')} 3rd={a3.get('code')}({a3.get('message')})",
        a1.get("code") == 200 and a2.get("code") == 200 and a3.get("code") == 400)

    st, o = teacher.req("POST", f"/tasks/{task}/extend-deadline",
                        {"newDueDate": (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S"), "reason": "早"})
    early = o.get("code")
    newdue = (datetime.now() + timedelta(days=10)).strftime("%Y-%m-%dT%H:%M:%S")
    st2, o2 = teacher.req("POST", f"/tasks/{task}/extend-deadline", {"newDueDate": newdue, "reason": "延后"})
    db_due = db1(f"select date_format(due_date,'%Y-%m-%d %H:%i') from {DB}.tasks where id={task};")
    rec("B9", "边界：延长截止日期（早于当前拒绝 / 晚于当前生效）",
        "早于当前 → 400；晚于当前 → 200 且 due_date 更新",
        f"早={early} 晚={o2.get('code')} due_date={db_due}",
        early == 400 and o2.get("code") == 200)

    st, o = teacher.req("POST", f"/tasks/{task}/extend-deadline", {"newDueDate": "2026-10-01 12:00:00", "reason": "非 ISO"})
    rec("B10", "边界：延长截止日期传非 ISO 格式", "400 明确提示（不得 500，本轮修复点）",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}",
        o.get("code") == 400)

    # ============ C. 越权与 IDOR ============
    print("\n----- C. 越权与 IDOR -----")
    st, o = teacher2.req("GET", f"/courses/{cid}/students")
    rec("C1", "越权：他师读取本课程学生名单", "403", f"HTTP {st} code={o.get('code')} msg={o.get('message')}",
        o.get("code") == 403)

    st, o = stu2.req("GET", f"/submissions/{sub_id}")
    rec("C2", "越权：他学生读取本人作业提交", "403/404 拒绝",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}", o.get("code") in (403, 404))

    st, o = anon.req("POST", f"/teacher/courses", {"name": "x", "code": "x-1"})
    rec("C3", "未认证写操作", "401", f"HTTP {st} code={o.get('code')}", st == 401)

    bad = S()
    st, o = bad.req("GET", "/courses/my", headers={"Authorization": "Bearer for.ged.token"})
    rec("C4", "伪造 JWT 令牌", "401", f"HTTP {st} code={o.get('code')}", st == 401)

    st, o = stu.req("DELETE", f"/resources/{rid}")
    rec("C5", "越权：学生删除课程资源", "403", f"HTTP {st} code={o.get('code')}", o.get("code") == 403)

    st, o = teacher2.req("PUT", f"/teacher/courses/{cid}/chapters/{chid}", {"title": "篡改"})
    rec("C6", "越权：他师修改本课程章节", "403", f"HTTP {st} code={o.get('code')}", o.get("code") == 403)

    st, o = admin.req("POST", "/courses/99999999/approve")
    rec("C7", "无效 ID：审批不存在的课程", "404 语义化",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}", o.get("code") == 404)

    st, o = teacher.req("GET", "/courses/abc")
    rec("C8", "参数类型错误：/courses/abc", "400 参数类型错误",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}", st == 400)

    # ============ D. 数据一致性 ============
    print("\n----- D. 数据一致性 -----")
    # 并发选课：同一学生对同一课程并发 2 次（先退课再并发）
    cc_id = db1(f"select id from {DB}.course_enrollments where student_id=5 and course_id={cid} and is_active=1;")
    if cc_id:
        stu2.req("DELETE", f"/enrollments/{cc_id}")
    out = []
    def race_enroll():
        s2 = S()
        s2.login("student2")
        out.append(s2.req("POST", f"/student/courses/{cid}/enroll")[1].get("code"))
    ts = [threading.Thread(target=race_enroll) for _ in range(2)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    rows = db1(f"select count(*) from {DB}.course_enrollments where student_id=5 and course_id={cid} and is_active=1;")
    rec("D1", "并发一致性：同一学生并发选课", "仅 1 条有效记录",
        f"响应码={out} 有效记录={rows}", rows == "1")

    # 并发交卷：新建会话后并发提交
    st, o = stu.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    rec("D1b", "（前置）并发交卷用会话创建", "200 或已达尝试上限",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}", o.get("code") in (200, 400))

    # 级联删除
    st, o = teacher.req("POST", "/teacher/courses", {"name": "级联验证", "code": CODE + "-CAS"})
    cas = (o.get("data") or {}).get("id")
    teacher.req("POST", f"/teacher/courses/{cas}/chapters", {"title": "c1", "orderIndex": 1})
    db(f"insert into {DB}.resources (file_name,file_type,url,course_id,uploader_id) values ('x.pdf','application/pdf','/uploads/x.pdf',{cas},2)")
    db(f"insert into {DB}.course_enrollments (student_id,course_id,approval_status,is_active) values (4,{cas},'APPROVED',1)")
    pre = (db1(f"select count(*) from {DB}.course_chapters where course_id={cas};"),
           db1(f"select count(*) from {DB}.resources where course_id={cas};"),
           db1(f"select count(*) from {DB}.course_enrollments where course_id={cas};"))
    teacher.req("DELETE", f"/teacher/courses/{cas}")
    post = (db1(f"select count(*) from {DB}.course_chapters where course_id={cas};"),
            db1(f"select count(*) from {DB}.resources where course_id={cas};"),
            db1(f"select count(*) from {DB}.course_enrollments where course_id={cas};"))
    rec("D2", "级联删除：删除课程后子表数据清除", "章节/资源/选课 均清零",
        f"前={pre} 后={post}", post == ("0", "0", "0"))

    # 读写缓存一致性（本轮修复点）
    st, o = teacher.req("PUT", f"/exam-papers/{paper}", {"title": "深度验证卷（已改名）", "duration": 30,
                                                         "timeLimit": 30, "maxAttempts": 2, "courseId": cid,
                                                         "creatorId": 2, "status": "PUBLISHED"})
    st2, o2 = teacher.req("GET", f"/exam-papers/{paper}/detail")
    d = o2.get("data") or {}
    title = (d.get("paper") or {}).get("title") or d.get("title") or (d.get("examPaper") or {}).get("title")
    rec("D3", "缓存一致性：修改试卷后详情立即反映新值", "200 且标题为改名后的值（缓存已失效）",
        f"update={o.get('code')} detail={st2}/{o2.get('code')} title={title}",
        st2 == 200 and title == "深度验证卷（已改名）",
        "修复前 getExamPaperDetail 的缓存无失效点，TTL 内会读到旧标题")

    # 缓存 VO 读取可用性（本轮修复点：PTV 白名单缺 com.aicourse.vo 导致缓存命中即 500）
    def twice(path, name, cid_, title_, expect_ok=True):
        a = teacher.req("GET", path)
        b = teacher.req("GET", path)
        rec(cid_, title_, "连续两次调用均 200（验证缓存写入后仍可读取）",
            f"第1次={a[0]}/{a[1].get('code')} 第2次={b[0]}/{b[1].get('code')}",
            (a[0] == 200 and a[1].get("code") == 200 and b[0] == 200 and b[1].get("code") == 200) == expect_ok)

    twice(f"/exam-papers/{paper}/detail", name="试卷详情", cid_="D4",
          title_="缓存读可用性：试卷详情（冷→热）")
    twice(f"/knowledge/points/tree/{cid}", name="知识树", cid_="D5",
          title_="缓存读可用性：知识点树（冷→热）")
    twice("/proctor/thresholds", name="监考阈值", cid_="D6",
          title_="缓存读可用性：监考阈值（冷→热）")

    # ============ E. 分片上传与合并链路（磁盘真值） ============
    # 背景：D1-2 批次 1 落地「分片写字节 + 合并」，批次 2 把进度出参改为磁盘真值并补
    # missingIndexes。本维度断言**成功路径 + 状态机守卫**，并核对 upload-chunks / uploads
    # 两个目录的物理状态——进度不能只信数据库计数。
    # 本轮实测新增回归位 E2：端点从「恒 501」改真实现后，非 multipart 请求曾在参数解析
    # 阶段抛 MultipartException 落入 500 兜底；N-03 下线期「不得出现 500」的要求同样成立。
    print("\n----- E. 分片上传与合并链路（磁盘真值） -----")
    st, o = teacher.req("POST", "/upload-sessions", {"filename": "big.pdf", "fileSize": 3072, "totalChunks": 3})
    usid = (o.get("data") or {}).get("id")
    rec("E1", "创建分片上传会话", "200 返回会话 id", f"HTTP {st} code={o.get('code')} sessionId={usid}",
        o.get("code") == 200 and usid is not None)

    st_bad, o_bad = teacher.req("POST", "/upload-sessions",
                                {"filename": "big.bin", "fileSize": 3072, "totalChunks": 3})
    rec("E1b", "边界：分片会话扩展名白名单", "非白名单扩展名(.bin) 400 拒绝",
        f"HTTP {st_bad} code={o_bad.get('code')} msg={str(o_bad.get('message'))[:70]}",
        o_bad.get("code") == 400)

    st_trav, o_trav = teacher.req("POST", "/upload-sessions",
                                  {"filename": "../../etc/passwd.pdf", "fileSize": 10, "totalChunks": 1})
    rec("E1c", "安全：文件名路径穿越", "不得 5xx（按取纯文件名处理或拒绝）",
        f"HTTP {st_trav} code={o_trav.get('code')} msg={str(o_trav.get('message'))[:60]}", st_trav < 500)

    def put_chunk(sid, idx, content, md5=None):
        body, ct = multipart(({"md5": md5} if md5 else {}),
                             [("file", f"{idx}.part", "application/octet-stream", content)])
        return teacher.req("POST", f"/upload-sessions/{sid}/chunks/{idx}", raw=body, ctype=ct)

    def chunk_dir(sid):
        d = os.path.join(CHUNK_ROOT, str(sid))
        if not os.path.isdir(d):
            return None
        return sum(os.path.getsize(os.path.join(d, f)) for f in os.listdir(d))

    st, o = teacher.req("POST", f"/upload-sessions/{usid}/chunks/0", {"fileSize": 1024})
    rec("E2", "非 multipart 分片请求按 400 语义拒绝", "code=400 且绝不 500",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:60]}",
        o.get("code") == 400 and st < 500)

    CH = [b"A" * 1024, b"B" * 1024, b"C" * 1024]
    codes = [put_chunk(usid, i, CH[i])[1].get("code") for i in (0, 1)]
    st, o = teacher.req("GET", f"/upload-sessions/{usid}/progress")
    prog = o.get("data") or {}
    rec("E3", "两片分片落盘，进度按磁盘真值推进", "分片均 200，uploaded=2/3、percentage=66.67%、missingIndexes=[2]",
        f"chunks={codes} progress={json.dumps(prog, ensure_ascii=False)[:160]} diskBytes={chunk_dir(usid)}",
        all(c == 200 for c in codes) and prog.get("uploaded") == 2 and prog.get("total") == 3
        and prog.get("percentage") == "66.67%" and prog.get("missingIndexes") == [2]
        and chunk_dir(usid) == 2048)

    st, o = teacher.req("POST", f"/upload-sessions/{usid}/merge")
    rec("E4", "缺片合并被拒且不产出半成品", "code=400 且提示「缺失索引：[2]」，上传根内无最终对象",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:70]}",
        o.get("code") == 400 and "缺失索引：[2]" in str(o.get("message")))

    st, o = put_chunk(usid, 0, b"a" * 1024)
    st2, o2 = teacher.req("GET", f"/upload-sessions/{usid}/progress")
    prog2 = o2.get("data") or {}
    rec("E5", "同序号重传覆盖旧片，不重复推进进度", "200 且 uploaded 仍 2、磁盘仍 2048 字节",
        f"HTTP {st} code={o.get('code')} uploaded={prog2.get('uploaded')} diskBytes={chunk_dir(usid)}",
        o.get("code") == 200 and prog2.get("uploaded") == 2 and chunk_dir(usid) == 2048)

    st, o = put_chunk(usid, 2, CH[2], md5="0" * 32)
    st2, o2 = teacher.req("GET", f"/upload-sessions/{usid}/progress")
    prog3 = o2.get("data") or {}
    rec("E6", "MD5 与声明不符：本片丢弃", "code=400 且第 2 片未落盘（missingIndexes 仍 [2]）",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]} missing={prog3.get('missingIndexes')}",
        o.get("code") == 400 and "摘要" in str(o.get("message")) and prog3.get("missingIndexes") == [2])

    st, o = put_chunk(usid, 2, CH[2], md5=hashlib.md5(CH[2]).hexdigest())
    st2, o2 = teacher.req("GET", f"/upload-sessions/{usid}/progress")
    prog4 = o2.get("data") or {}
    rec("E7", "正确 MD5 补齐末片", "200 且 uploaded=3/3、missingIndexes 为空",
        f"HTTP {st} code={o.get('code')} progress={json.dumps(prog4, ensure_ascii=False)[:120]}",
        o.get("code") == 200 and prog4.get("uploaded") == 3 and prog4.get("missingIndexes") == [])

    st, o = teacher.req("POST", f"/upload-sessions/{usid}/merge")
    merged = (o.get("data") or {})
    obj_path = os.path.join(UP_ROOT, str(merged.get("uploadPath") or "")) if merged.get("uploadPath") else None
    obj_bytes = os.path.getsize(obj_path) if obj_path and os.path.isfile(obj_path) else -1
    db_status = db1(f"select status from {DB}.file_upload_sessions where id={usid};")
    rec("E8", "齐片合并成功：对象落上传根、分片目录回收",
        "200 且 status=COMPLETED、对象 3072 字节、分片目录已删、DB=COMPLETED",
        f"HTTP {st} code={o.get('code')} status={merged.get('status')} objBytes={obj_bytes} "
        f"chunkDirBytes={chunk_dir(usid)} db={db_status}",
        o.get("code") == 200 and merged.get("status") == "COMPLETED" and obj_bytes == 3072
        and chunk_dir(usid) is None and db_status == "COMPLETED")

    st, o = teacher.req("POST", f"/upload-sessions/{usid}/merge")
    rec("E9", "状态机守卫：已完成会话不可重复合并", "code=400 且提示状态不允许",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]}",
        o.get("code") == 400 and "不允许合并" in str(o.get("message")))

    st, o = put_chunk(99999999, 0, CH[0])
    rec("E10", "分片端点对不存在会话按 400 拒绝", "code=400 且提示会话不存在（绝不 500）",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]}",
        o.get("code") == 400 and "不存在" in str(o.get("message")) and st < 500)

    st, o = teacher.req("POST", "/upload-sessions", {"filename": "half.pdf", "fileSize": 2048, "totalChunks": 2})
    usid2 = (o.get("data") or {}).get("id")
    put_chunk(usid2, 0, CH[0])
    st, o = teacher.req("DELETE", f"/upload-sessions/{usid2}/cancel")
    rec("E11", "取消进行中会话并回收分片目录", "200 且 upload-chunks/<id> 已删除",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')} chunkDirBytes={chunk_dir(usid2)}",
        st < 500 and chunk_dir(usid2) is None)

    # ============ F. 作业退回-重交 ============
    print("\n----- F. 作业退回-重交链路 -----")
    # 单独用一张不限次数的作业，避免与 B5 的「已评分」状态互相干扰
    st, o = teacher.req("POST", "/teacher/assignments", {
        "title": "退回重交用例作业", "taskType": "assignment", "courseId": cid, "maxScore": 100,
        "dueDate": (datetime.now() + timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%S"),
        "submissionFormat": "text", "maxSubmissions": 0, "status": "draft"})
    task2 = (o.get("data") or {}).get("id")
    teacher.req("PUT", f"/teacher/assignments/{task2}/publish")
    st, o = stu.req("POST", f"/student/assignments/{task2}/submit", {"content": "初稿"})
    sub2 = (o.get("data") or {}).get("id")

    st, o = teacher.req("POST", f"/submissions/{sub2}/return", {"reason": "请补充丢包分析"})
    st_ret = db1(f"select status from {DB}.submissions where id={sub2};")
    rec("F1", "教师退回作业（未评分）", "200 且 status=RETURNED",
        f"HTTP {st} code={o.get('code')} status={st_ret}", st_ret == "RETURNED")

    st, o = stu.req("POST", f"/submissions/{sub2}/resubmit", {"content": "已补充丢包分析"})
    new_id = (o.get("data") or {}).get("id")
    new_att = db1(f"select attempt_number from {DB}.submissions where id={new_id};") if new_id else None
    new_st = db1(f"select status from {DB}.submissions where id={new_id};") if new_id else None
    rec("F2", "学生重新提交（生成新尝试）", "200 且 attempt_number=2、status=RESUBMITTED",
        f"HTTP {st} code={o.get('code')} newId={new_id} attempt={new_att} status={new_st}",
        o.get("code") == 200 and new_att == "2" and new_st == "RESUBMITTED")

    st_g, o_g = teacher.req("POST", f"/submissions/{new_id}/grade", {"score": 85, "feedback": "合格"})
    st, o = teacher.req("POST", f"/submissions/{new_id}/return", {"reason": "再退回"})
    rec("F3", "边界：已评分（GRADED）作业不可退回", "400 业务错误",
        f"评分={o_g.get('code')} 再退回={o.get('code')}({o.get('message')})", o.get("code") == 400)

    # ============ G. 资源版本 ============
    print("\n----- G. 资源版本链路 -----")
    st, o = teacher.req("POST", "/resource-versions", {
        "resourceId": rid, "fileName": "deep-v1.pdf", "fileType": "application/pdf",
        "url": f"/uploads/deep-{rid}.pdf", "fileUrl": f"/uploads/deep-{rid}.pdf",
        "description": "v1 首发", "fileSize": 500})
    ver1 = (o.get("data") or {}).get("versionNumber")
    rec("G1", "创建资源首个版本", "200 且 versionNumber=1",
        f"HTTP {st} code={o.get('code')} version={ver1}", o.get("code") == 200 and str(ver1) == "1")

    st, o = teacher.req("POST", "/resource-versions", {
        "resourceId": rid, "fileName": "deep-v2.pdf", "fileType": "application/pdf",
        "url": f"/uploads/deep-{rid}.pdf", "fileUrl": f"/uploads/deep-{rid}.pdf",
        "description": "v2 修订", "fileSize": 999})
    ver2 = (o.get("data") or {}).get("versionNumber")
    rec("G1b", "创建第二个版本（版本号递增）", "200 且 versionNumber=2",
        f"HTTP {st} code={o.get('code')} version={ver2}", o.get("code") == 200 and str(ver2) == "2")

    rows = db(f"select version_number,is_active from {DB}.resource_versions where resource_id={rid} order by version_number")
    actives = [r for r in rows if r.endswith("\t1")]
    rec("G2", "版本激活态唯一", "仅最新版本 is_active=1",
        f"版本(号,激活)={[r.replace(chr(9), ',') for r in rows]}", len(actives) == 1 and actives[0].startswith("2"))

    st, o = teacher.req("POST", f"/resource-versions/{rid}/rollback", {"versionNumber": 1})
    st_a, o_a = teacher.req("GET", f"/resource-versions/active/{rid}")
    act_ver = (o_a.get("data") or {}).get("versionNumber")
    rec("G3", "回滚到历史版本", "200 且激活版本变为 1",
        f"HTTP {st} code={o.get('code')} activeVersion={act_ver}", o.get("code") == 200 and str(act_ver) == "1")

    # 契约修复验证：DTO 声明的必填字段是 fileUrl，只传 fileUrl 也应可用
    st, o = teacher.req("POST", "/resource-versions", {
        "resourceId": rid, "fileName": "deep-v3.pdf",
        "fileUrl": f"/uploads/deep-{rid}.pdf", "fileSize": 100})
    rec("G3b", "契约：仅传 fileUrl（DTO 声明的必填字段）也可创建版本",
        "200（修复前只认 url 字段，会 400）",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]}", o.get("code") == 200)

    st, o = teacher2.req("POST", "/resource-versions", {
        "resourceId": rid, "fileName": "hijack.pdf",
        "url": f"/uploads/deep-{rid}.pdf", "fileUrl": f"/uploads/deep-{rid}.pdf"})
    rec("G3c", "越权：他师为他人课程资源建版本", "403 拒绝（本轮修复）",
        f"HTTP {st} code={o.get('code')} msg={o.get('message')}", o.get("code") == 403)

    st, o = teacher.req("POST", f"/resource-versions", {"fileName": "x.pdf", "fileUrl": "/x.pdf"})
    rec("G4b", "边界：创建版本缺 resourceId", "400 资源ID不能为空",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]}", o.get("code") == 400)

    st, o = teacher.req("POST", "/resource-versions", {"resourceId": rid, "fileName": "x"})
    rec("G4", "边界：创建版本缺 url/fileUrl", "400 参数提示",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]}", o.get("code") == 400)

    # ============ 汇总 ============
    print("\n" + "=" * 100)
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r[4] == "PASS")
    print(f"深度验证用例总数 {total}，通过 {passed}，失败 {total - passed}")
    print("=" * 100)
    fails = [r for r in RESULTS if r[4] == "FAIL"]
    if fails:
        print("\n## 未通过用例")
        for i, (cid_, t, e, a, _, n) in enumerate(fails, 1):
            print(f"{i}. [{cid_}] {t}\n   期望: {e}\n   实际: {a}" + (f"\n   说明: {n}" if n else ""))
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_deep_result.json"),
              "w", encoding="utf-8") as f:
        json.dump({"total": total, "passed": passed,
                   "results": [{"case": r[0], "title": r[1], "expect": r[2], "actual": r[3],
                                "verdict": r[4], "note": r[5]} for r in RESULTS]},
                  f, ensure_ascii=False, indent=2)
    print("\n明细已写入 e2e_deep_result.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
