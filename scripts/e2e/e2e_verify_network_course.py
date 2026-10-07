# -*- coding: utf-8 -*-
"""
AI 智能课程平台 · 端到端全流程实机验证脚本（以「计算机网络」课程为实例）
覆盖：建课 → 审批 → 章节/资料/视频上传 → 作业布置与发布 → 组卷与发布
      → 学生选课 → 查看资料与视频 → 完成作业 → 参加考试 → 批改与成绩
以及：认证/越权、状态机非法流转、唯一约束、上传边界、时间边界、并发/重复提交等异常与边界。

仅使用 Python 标准库（urllib + http.cookiejar），无第三方依赖。
数据库断言使用 mysql 客户端（本机 3306）。
"""
import json
import os
import subprocess
import sys
import time
import uuid
import urllib.request
import urllib.error
import http.cookiejar
from datetime import datetime, timedelta

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE = "http://localhost:8080/api"
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

RESULTS = []          # (case_id, title, expect, actual, verdict, note)
DEFECTS = []          # 记录与预期不符的发现


def db(sql):
    """执行 SQL，返回多行（制表符分隔）列表"""
    p = subprocess.run(MYSQL + [sql], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        return ["<SQL_ERROR> " + (p.stderr or "").strip()[:200]]
    return [ln for ln in (p.stdout or "").splitlines() if ln.strip() != ""]


def db1(sql, default=None):
    rows = db(sql)
    if not rows or rows[0].startswith("<SQL_ERROR>"):
        return default
    return rows[0].split("\t")[0]


class Session:
    def __init__(self, who):
        self.who = who
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj), NoRedirect()
        )

    def req(self, method, path, body=None, raw=None, content_type="application/json", extra_headers=None):
        url = BASE + path
        data = None
        headers = {"Accept": "application/json"}
        if raw is not None:
            data = raw
            headers["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if extra_headers:
            headers.update(extra_headers)
        r = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            resp = self.opener.open(r, timeout=30)
            status, text = resp.getcode(), resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode("utf-8", "replace")
        except Exception as e:  # noqa
            return 0, {"_transport_error": str(e)}, ""
        try:
            obj = json.loads(text)
        except Exception:
            obj = {"_raw": text[:400]}
        return status, obj, text

    def login(self, username, password="123456"):
        st, obj, _ = self.req("POST", "/auth/login", {"username": username, "password": password})
        ok = st == 200 and obj.get("code") == 200
        return ok, st, obj


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def flush_redis():
    """清空测试用 Redis（db0）。

    必要性：读路径带 @Cacheable 而脚本用 SQL 直删课程清场，绕过了应用层 @CacheEvict；
    若后续新建课程复用同一 id，会读到上一轮陈旧对象（症状：新建的 PENDING 课程被判为已审批）。
    同时顺带清零限流计数，避免连续多轮脚本触及登录 IP 限流。仅用标准库 socket 发 RESP 命令。
    """
    try:
        import socket
        with socket.create_connection(("127.0.0.1", 6379), timeout=3) as s:
            s.sendall(b"*1\r\n$7\r\nFLUSHDB\r\n")
            s.recv(64)
        print("[setup] Redis db0 已清空（缓存与限流计数）")
    except Exception as e:  # noqa
        print("[setup] Redis 清空失败（忽略）：", e)


def record(cid, title, expect, actual, ok, note=""):
    verdict = "PASS" if ok else "FAIL"
    RESULTS.append((cid, title, expect, actual, verdict, note))
    if not ok:
        DEFECTS.append((cid, title, expect, actual, note))
    print(f"[{verdict}] {cid} {title}\n        期望: {expect}\n        实际: {actual}" + (f"\n        备注: {note}" if note else ""))


def multipart(fields, files):
    boundary = "----Boundary" + uuid.uuid4().hex
    out = bytearray()
    for k, v in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode("utf-8")
    for name, filename, ctype, content in files:
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n".encode("utf-8")
        out += f"Content-Type: {ctype}\r\n\r\n".encode("utf-8")
        out += content + b"\r\n"
    out += f"--{boundary}--\r\n".encode("utf-8")
    return bytes(out), f"multipart/form-data; boundary={boundary}"


# ---------- 造真实魔数的样例文件 ----------
FAKE_PDF = b"%PDF-1.4\n" + b"computer networks syllabus\n" + b"0" * 200
FAKE_PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 200
FAKE_MP4 = b"\x00\x00\x00\x18ftypmp42" + b"\x00\x00\x00\x00mp42isom" + b"\x00" * 300   # offset4..7 = 'ftyp'
MISLABELED = b"%PDF-1.4\n" + b"x" * 200        # 内容为 PDF，但声明 image/png


def main():
    print("=" * 100)
    print("AI 智能课程平台 · 端到端全流程实机验证")
    print("课程实例：计算机网络（course code = CS-NET-2026）")
    print("时间：", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    print("=" * 100)

    ADMIN = "admin"
    TEACHER = "teacher"          # id=2 张教授
    TEACHER2 = "teacher2"        # id=3 李副教授
    STUDENT = "student"          # id=4 王同学
    STUDENT2 = "student2"        # id=5
    STUDENT3 = "student3"        # id=6（用于满员边界）
    STUDENT4 = "student4"        # id=7（用于“未选课”越权用例）

    # ---- 前置：清理历史测试数据 + 清空测试用 Redis，保证从确定性基线开始 ----
    db(f"delete from {DB}.submissions where task_id in (select id from {DB}.tasks where course_id in (select id from {DB}.courses where code like 'CS-NET-2026%'))")
    db(f"delete from {DB}.questions where course_id in (select id from {DB}.courses where code like 'CS-NET-2026%')")
    db(f"delete from {DB}.questions where course_id is null")   # 失败运行可能留下的孤儿题目
    db(f"delete from {DB}.courses where code like 'CS-NET-2026%'")
    flush_redis()

    anon = Session("anon")
    admin = Session("admin")
    teacher = Session("teacher")
    teacher2 = Session("teacher2")
    student = Session("student")
    student2 = Session("student2")
    student3 = Session("student3")
    student4 = Session("student4")

    # ============ A. 环境与认证 ============
    print("\n----- A. 环境与认证 -----")
    st, obj, _ = anon.req("GET", "/courses")
    record("A1", "未登录访问受保护接口", "401 未认证", f"HTTP {st} code={obj.get('code')}", st == 401)

    for s, name in ((admin, ADMIN), (teacher, TEACHER), (teacher2, TEACHER2),
                    (student, STUDENT), (student2, STUDENT2), (student3, STUDENT3),
                    (student4, STUDENT4)):
        ok, st, obj = s.login(name)
        record(f"A2-{name}", f"{name} 登录", "HTTP 200 / code=200", f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", ok)

    # ============ B. 环节1 教师创建课程 ============
    print("\n----- B. 环节1：教师创建课程 -----")
    course_body = {
        "name": "计算机网络", "code": "CS-NET-2026",
        "description": "覆盖 OSI/TCP-IP 体系结构、链路层、网络层、传输层与应用层",
        "credits": 3, "semester": "2026-2027-1", "year": 2026,
        "enrollmentType": "APPROVAL", "maxStudents": 2,
        "category": "计算机", "difficulty": "中等",
        # 【重要】LocalDateTime 入参必须 ISO-8601；spring.jackson.date-format 只作用于 java.util.Date
        "startDate": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        "endDate": (datetime.now() + timedelta(days=120)).strftime("%Y-%m-%dT%H:%M:%S"),
    }
    st, obj, _ = teacher.req("POST", "/teacher/courses", course_body)
    cid = (obj.get("data") or {}).get("id")
    d_teacher = (obj.get("data") or {}).get("teacherId")
    d_ap = (obj.get("data") or {}).get("approvalStatus")
    d_st = (obj.get("data") or {}).get("status")
    record("B1", "教师创建课程（计算机网络）", "HTTP 200，绑定 teacher_id=2，且响应回显 approval_status=PENDING/status=active",
           f"HTTP {st} code={obj.get('code')} id={cid} teacherId={d_teacher} approvalStatus={d_ap} status={d_st}",
           st == 200 and obj.get("code") == 200 and cid is not None and d_teacher == 2
           and d_ap == "PENDING" and (d_st or "").lower() == "active",
           "修复后服务层显式落定初始状态，响应体 = 落库值（原实现不回显，前端会读到 null）")
    db_ap = db1(f"select approval_status from {DB}.courses where code='CS-NET-2026';")
    db_st = db1(f"select status from {DB}.courses where code='CS-NET-2026';")
    db_tid = db1(f"select teacher_id from {DB}.courses where code='CS-NET-2026';")
    record("B2", "DB 断言：新建课程落库状态", "approval_status=PENDING / status=ACTIVE / teacher_id=2",
           f"{db_ap} / {db_st} / {db_tid}",
           db_ap == "PENDING" and (db_st or "").upper() == "ACTIVE" and db_tid == "2")

    st, obj, _ = teacher.req("POST", "/teacher/courses", dict(course_body))
    record("B3", "边界：课程编号重复（uk_courses_code）", "409 CONFLICT 语义化提示（原为 500）",
           f"HTTP {st} code={obj.get('code')} msg={str(obj.get('message'))[:60]}", obj.get("code") == 409)

    # R-09 修复验证：接受非 ISO 的 yyyy-MM-dd HH:mm:ss 日期格式
    st, obj, _ = teacher.req("POST", "/teacher/courses", {
        "name": "计算机网络（日期格式兼容用例）", "code": "CS-NET-2026-DT",
        "credits": 1, "enrollmentType": "FREE", "maxStudents": 1,
        "startDate": "2026-09-14 16:00:00", "endDate": "2027-01-10 23:59:59"})
    dt_id = (obj.get("data") or {}).get("id")
    record("B4", "契约：LocalDateTime 接受 yyyy-MM-dd HH:mm:ss（宽松反序列化）",
           "HTTP 200（原为 400 请求格式错误）",
           f"HTTP {st} code={obj.get('code')} id={dt_id} msg={obj.get('message')}", obj.get("code") == 200)
    db(f"delete from {DB}.courses where code='CS-NET-2026-DT'")

    # ============ C. 环节2 管理员审核 ============
    print("\n----- C. 环节2：管理员审核通过 -----")
    # 审批前先探一次“未审批课程能否被选课”
    st_pre, obj_pre, _ = student.req("POST", f"/student/courses/{cid}/enroll")
    pre_ok = obj_pre.get("code") == 200
    record("C1", "边界：未审批(PENDING)课程被学生选课", "应拒绝（课程未审核通过不可选）",
           f"HTTP {st_pre} code={obj_pre.get('code')} msg={obj_pre.get('message')}",
           not pre_ok, "若放行则为越权/状态机漏洞：学生可加入未审核课程")
    if pre_ok:
        db(f"delete from {DB}.course_enrollments where student_id=4 and course_id={cid};")

    st, obj, _ = student.req("POST", f"/courses/{cid}/approve")
    record("C2", "越权：学生调用管理员审批接口", "403 无权限", f"HTTP {st} code={obj.get('code')}", st == 403)

    st, obj, _ = teacher.req("POST", f"/courses/{cid}/approve")
    record("C3", "越权：教师调用管理员审批接口", "403 无权限", f"HTTP {st} code={obj.get('code')}", st == 403)

    st, obj, _ = admin.req("POST", f"/courses/{cid}/approve")
    ap = (obj.get("data") or {}).get("approvalStatus")
    stat = (obj.get("data") or {}).get("status")
    record("C4", "管理员审批通过", "HTTP 200，approval_status=APPROVED 且 status=ACTIVE",
           f"HTTP {st} code={obj.get('code')} approvalStatus={ap} status={stat}",
           st == 200 and ap == "APPROVED" and (stat or "").upper() == "ACTIVE")
    record("C5", "DB 断言：courses 审批态", "APPROVED / ACTIVE",
           f"{db1(f'select approval_status from {DB}.courses where id={cid};')} / {db1(f'select status from {DB}.courses where id={cid};')}",
           db1(f"select approval_status from {DB}.courses where id={cid};") == "APPROVED")

    st, obj, _ = admin.req("POST", f"/courses/{cid}/approve")
    record("C6", "边界：重复审批（幂等性）", "无异常（幂等）或明确提示", f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}",
           obj.get("code") == 200, "approveCourse 未做状态前置校验，重复调用始终成功")

    st, obj, _ = teacher2.req("PUT", f"/teacher/courses/{cid}", {"name": "计算机网络（篡改）"})
    record("C7", "越权：其他教师修改本课程", "403 FORBIDDEN（原为 400）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") == 403)

    # ============ D. 环节3 章节 + 资料/视频上传 ============
    print("\n----- D. 环节3：上传资料与视频 -----")
    st, obj, _ = teacher.req("POST", f"/teacher/courses/{cid}/chapters",
                             {"title": "第1章 计算机网络概述", "content": "体系结构与分层模型",
                              "orderIndex": 1, "duration": 90, "isPublished": True})
    ch1 = (obj.get("data") or {}).get("id")
    record("D1", "新增课程章节", "HTTP 200 返回 chapter.id", f"HTTP {st} code={obj.get('code')} chapterId={ch1}",
           st == 200 and ch1 is not None)

    st, obj, _ = teacher.req("POST", f"/teacher/courses/{cid}/chapters",
                             {"title": "第2章 物理层与数据链路层", "orderIndex": 2, "duration": 120, "isPublished": True})
    ch2 = (obj.get("data") or {}).get("id")
    record("D2", "新增第2章节", "HTTP 200", f"HTTP {st} chapterId={ch2}", st == 200 and ch2 is not None)

    # 上传视频（MP4 魔数）
    body, ctype = multipart({"courseId": str(cid), "description": "第1章 教学视频"},
                            [("file", "网络概述.mp4", "video/mp4", FAKE_MP4)])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    vid = (obj.get("data") or {}).get("id")
    record("D3", "上传教学视频（MP4）", "HTTP 200，resource 落库且 file_type=video/mp4",
           f"HTTP {st} code={obj.get('code')} id={vid} fileType={(obj.get('data') or {}).get('fileType')}",
           st == 200 and vid is not None,
           "修复前恒定 500（Resource 实体 getter 重载导致 MyBatis 反射失败）")

    # 上传文档（PDF）
    body, ctype = multipart({"courseId": str(cid), "description": "第1章 讲义"},
                            [("file", "第1章讲义.pdf", "application/pdf", FAKE_PDF)])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    doc = (obj.get("data") or {}).get("id")
    record("D5", "上传课程资料（PDF）", "HTTP 200",
           f"HTTP {st} code={obj.get('code')} id={doc}", st == 200 and doc is not None)

    # 兜底：若上传接口仍不可用（回归），以 SQL 直插保证后续链路可继续验证
    if vid is None or doc is None:
        db(f"insert into {DB}.resources (file_name,file_type,url,course_id,chapter_id,uploader_id,description,visible_to_all) "
           f"values ('网络概述.mp4','video/mp4','/uploads/e2e-video.mp4',{cid},{ch1},2,'第1章 教学视频',1)")
        vid = db1(f"select id from {DB}.resources where course_id={cid} and file_name='网络概述.mp4' order by id desc limit 1;")
        db(f"insert into {DB}.resources (file_name,file_type,url,course_id,chapter_id,uploader_id,description,visible_to_all) "
           f"values ('第1章讲义.pdf','application/pdf','/uploads/e2e-doc.pdf',{cid},{ch1},2,'第1章 讲义',1)")
        doc = db1(f"select id from {DB}.resources where course_id={cid} and file_name='第1章讲义.pdf' order by id desc limit 1;")
        record("D3b", "兜底数据准备：SQL 直插资源", "两条 resources 记录就绪",
               f"videoId={vid} docId={doc}", vid is not None and doc is not None)

    # 兜底：把真实文件落到 uploads/，使下载鉴权用例可验证到「文件存在」分支
    up = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "backend", "uploads")
    os.makedirs(up, exist_ok=True)
    for url_name, content in (("e2e-video.mp4", FAKE_MP4), ("e2e-doc.pdf", FAKE_PDF)):
        try:
            with open(os.path.join(up, url_name), "wb") as fh:
                fh.write(content)
        except Exception as e:  # noqa
            print("   写入兜底文件失败：", e)

    record("D4", "DB 断言：resources.file_type 实际取值", "video/mp4（MIME 形式，与更新后的列注释一致）",
           f"{db1(f'select file_type from {DB}.resources where id={vid};')}",
           db1(f"select file_type from {DB}.resources where id={vid};") == "video/mp4")

    # 对照组：通用上传接口是否可用（用于定位故障边界）
    body, ctype = multipart({}, [("file", "u.pdf", "application/pdf", FAKE_PDF)])
    st, obj, _ = teacher.req("POST", "/common/upload", raw=body, content_type=ctype)
    record("D5b", "对照组：通用上传接口 /common/upload", "HTTP 200 返回 url（用于界定故障范围）",
           f"HTTP {st} code={obj.get('code')} url={(obj.get('data') or {}).get('url')}",
           st == 200 and (obj.get("data") or {}).get("url") is not None,
           "该接口正常，说明故障局限于 /resources/upload")

    # 边界：空文件
    body, ctype = multipart({"courseId": str(cid)}, [("file", "empty.pdf", "application/pdf", b"")])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    record("D6", "边界：上传空文件", "业务错误（上传文件不能为空）", f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}",
           obj.get("code") != 200)

    # 边界：内容与声明类型不符（伪装）
    body, ctype = multipart({"courseId": str(cid)}, [("file", "fake.png", "image/png", MISLABELED)])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    record("D7", "边界：伪装文件类型（PNG 头声明实为 PDF 内容）", "拒绝（文件内容与声明类型不符）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    # 边界：非法扩展名/类型
    body, ctype = multipart({"courseId": str(cid)}, [("file", "shell.sh", "application/x-sh", b"#!/bin/sh\necho hi")])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    record("D8", "边界：上传非白名单类型（sh 脚本）", "拒绝（不支持该文件格式）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    # 边界：超过 50MB
    big = b"%PDF-1.4\n" + b"0" * (51 * 1024 * 1024)
    body, ctype = multipart({"courseId": str(cid)}, [("file", "big.pdf", "application/pdf", big)])
    st, obj, _ = teacher.req("POST", "/resources/upload", raw=body, content_type=ctype)
    record("D9", "边界：上传超过 50MB 文件", "413 PAYLOAD_TOO_LARGE（原为 500）",
           f"HTTP {st} code={obj.get('code')} msg={str(obj.get('message'))[:60]}",
           obj.get("code") == 413)
    del big

    # 学生可见性
    st, obj, _ = teacher.req("PATCH", f"/resources/{doc}/visibility", {"visibleToAll": False})
    v0 = db1(f"select visible_to_all from {DB}.resources where id={doc};")
    record("D10", "资源可见性切换（仅教师可见）", "HTTP 200 且落库 visible_to_all=0",
           f"HTTP {st} code={obj.get('code')} visible_to_all={v0}", obj.get("code") == 200 and v0 == "0")
    st, obj, _ = teacher.req("PATCH", f"/resources/{doc}/visibility", {"visibleToAll": True})
    v1 = db1(f"select visible_to_all from {DB}.resources where id={doc};")
    record("D11", "资源可见性恢复（对所有学生可见）", "visible_to_all=1", f"visible_to_all={v1}", v1 == "1")

    # ============ E. 环节4 布置作业与发布 ============
    print("\n----- E. 环节4：布置作业与发布 -----")
    due = (datetime.now() + timedelta(days=5)).strftime("%Y-%m-%dT%H:%M:%S")
    st, obj, _ = teacher.req("POST", "/teacher/assignments", {
        "title": "作业1：OSI 七层模型与 TCP/IP 四层模型对照",
        "description": "绘制对照表并说明各层职责", "taskType": "assignment",
        "courseId": cid, "chapterId": ch1, "maxScore": 100, "dueDate": due,
        "submissionFormat": "both", "maxSubmissions": 1, "status": "draft",
    })
    task = (obj.get("data") or {}).get("id")
    record("E1", "教师创建作业（草稿）", "HTTP 200，is_published=false 且响应回显",
           f"HTTP {st} code={obj.get('code')} taskId={task} isPublished={(obj.get('data') or {}).get('isPublished')}",
           st == 200 and task is not None and (obj.get("data") or {}).get("isPublished") is False)

    st, obj, _ = student.req("POST", f"/student/assignments/{task}/submit", {"content": "提前抢占提交"})
    record("E2", "异常：提交未发布作业", "业务错误（该作业尚未发布）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    st, obj, _ = teacher.req("PUT", f"/teacher/assignments/{task}/publish")
    record("E3", "发布作业", "HTTP 200 且 is_published=1",
           f"HTTP {st} code={obj.get('code')} isPublished={db1(f'select is_published from {DB}.tasks where id={task};')}",
           db1(f"select is_published from {DB}.tasks where id={task};") == "1")

    st, obj, _ = teacher.req("PUT", f"/teacher/assignments/{task}/publish")
    record("E4", "边界：重复发布作业", "业务错误（任务已发布）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    st, obj, _ = teacher2.req("PUT", f"/teacher/assignments/{task}/publish")
    record("E5", "越权：其他教师发布本课程作业", "403 FORBIDDEN",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") == 403)

    # ============ F. 环节5 题库与试卷 ============
    print("\n----- F. 环节5：组卷与发布考试 -----")
    qids = []
    q_single_correct = {}
    for i, (qtype, content, options, correct) in enumerate([
        ("SINGLE_CHOICE", "TCP 协议工作在哪一层？", ["网络层", "传输层", "会话层", "表示层"], "\"B\""),
        ("SINGLE_CHOICE", "IPv4 地址长度是多少位？", ["16", "32", "64", "128"], "\"B\""),
        ("TRUE_FALSE", "UDP 提供可靠的面向连接传输服务。", ["正确", "错误"], "\"false\""),
    ]):
        body = {"type": qtype, "content": content,
                "options": json.dumps(options, ensure_ascii=False),
                "correctAnswers": correct, "difficulty": "medium",
                "score": 10 if qtype == "SINGLE_CHOICE" else 5,
                "courseId": cid, "isActive": True}
        st, obj, _ = teacher.req("POST", "/questions/create", body)
        qid = (obj.get("data") or {}).get("id")
        qids.append(qid)
        if i == 0:
            record("F1", "题目入库（单选/判断，共3题）", "HTTP 200 返回 question.id",
                   f"HTTP {st} code={obj.get('code')} 首题id={qid}",
                   st == 200 and qid is not None,
                   "correct_answers 为 JSON 列，单值需按 JSON 字符串（含引号）写入")

    st, obj, _ = teacher.req("POST", "/exam-papers/auto-compose", {
        "courseId": cid, "title": "计算机网络 期中考试（A卷）",
        "description": "覆盖网络概述与传输层", "duration": 45, "timeLimit": 45,
        "maxAttempts": 1, "shuffleQuestions": True, "shuffleOptions": True,
        "showScore": True, "showCorrectAnswer": True, "allowReview": True,
        "typeRules": {"SINGLE_CHOICE": 2, "TRUE_FALSE": 1},
        "scoreRules": {"SINGLE_CHOICE": 10, "TRUE_FALSE": 5},
    })
    paper = (obj.get("data") or {}).get("id")
    qcnt = db1(f"select count(*) from {DB}.exam_questions where exam_paper_id={paper};", "0") if paper else "0"
    record("F2", "自动组卷", "HTTP 200，试卷含 3 题",
           f"HTTP {st} code={obj.get('code')} paperId={paper} 组卷题数={qcnt}",
           st == 200 and paper is not None and qcnt == "3",
           "typeRules 的键必须与 questions.type 存量值（大写）一致，否则选题为空")
    total_score = db1(f"select total_score from {DB}.exam_papers where id={paper};") if paper else None
    record("F2b", "DB 断言：试卷总分自动回填", "exam_papers.total_score = 卷面合计 25.00（原为 NULL）",
           f"total_score={total_score} 卷面合计={db1(f'select sum(points) from {DB}.exam_questions where exam_paper_id={paper};') if paper else None}",
           total_score is not None and abs(float(total_score or 0) - 25.0) < 0.01)

    st, obj, _ = teacher.req("POST", f"/exam-papers/{paper}/publish")
    record("F3", "发布试卷", "HTTP 200 且 status=PUBLISHED",
           f"HTTP {st} code={obj.get('code')} status={db1(f'select status from {DB}.exam_papers where id={paper};')}",
           db1(f"select status from {DB}.exam_papers where id={paper};") == "PUBLISHED")

    st, obj, _ = teacher.req("POST", f"/exam-papers/{paper}/publish")
    record("F4", "边界：重复发布试卷", "业务错误（试卷已发布）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    # 越权发布他人试卷：另组一份草稿卷，由 teacher2 尝试发布
    st, obj, _ = teacher.req("POST", "/exam-papers/auto-compose", {
        "courseId": cid, "title": "计算机网络 备用卷（草稿）", "duration": 30, "timeLimit": 30,
        "maxAttempts": 1, "typeRules": {"SINGLE_CHOICE": 1}})
    paper2 = (obj.get("data") or {}).get("id")
    st, obj, _ = teacher2.req("POST", f"/exam-papers/{paper2}/publish")
    p2stat = db1(f"select status from {DB}.exam_papers where id={paper2};") if paper2 else None
    record("F5", "越权：其他教师发布本课程试卷（草稿卷）", "403 FORBIDDEN（原为 200 发布成功）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')} paper2.status={p2stat}",
           obj.get("code") == 403)

    # ============ G. 环节6 学生加入课堂 ============
    print("\n----- G. 环节6：学生加入课堂（选课） -----")
    st, obj, _ = student.req("POST", f"/student/courses/{cid}/enroll")
    enr_ap = db1(f"select approval_status from {DB}.course_enrollments where student_id=4 and course_id={cid};")
    record("G1", "学生加入课堂", "HTTP 200 且生成选课记录",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')} 落库approval_status={enr_ap}",
           st == 200 and enr_ap is not None)
    record("G2", "选课审批态：课程 enrollment_type=APPROVAL", "落 PENDING（等待教师审批，原为 APPROVED）",
           f"实际={enr_ap}", enr_ap == "PENDING",
           "修复后 enrollStudent() 与 enroll() 共用同一审批态推导：APPROVAL/RESTRICTED → PENDING")

    st, obj, _ = student.req("POST", f"/student/courses/{cid}/enroll")
    record("G3", "边界：重复选课", "业务错误（已加入该课程）", f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}",
           obj.get("code") != 200)

    st, obj, _ = student2.req("POST", f"/student/courses/{cid}/enroll")
    record("G4", "第2名学生加入（上限 maxStudents=2）", "HTTP 200", f"HTTP {st} code={obj.get('code')}", obj.get("code") == 200)

    st, obj, _ = student3.req("POST", f"/student/courses/{cid}/enroll")
    enrolled = db1(f"select count(*) from {DB}.course_enrollments where course_id={cid};")
    record("G5", "边界：选课人数超过 max_students=2", "拒绝（课程选课人数已满，原为 200 入库）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')} 当前选课数={enrolled}",
           obj.get("code") != 200 and enrolled == "2")

    st, obj, _ = teacher.req("POST", f"/student/courses/{cid}/enroll")
    record("G6", "越权：教师调用学生选课接口", "应拒绝（学生专属接口）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}",
           st != 200 or obj.get("code") != 200,
           "/student/** 未纳入 SecurityConfig 角色规则，且控制器无 @PreAuthorize")

    # 教师审批选课：审批型课程在审批通过后才算课程成员（资源下载/开考资格）
    appr = []
    for sid in (4, 5):
        eid = db1(f"select id from {DB}.course_enrollments where student_id={sid} and course_id={cid} and is_active=1;")
        if eid:
            stx, ox, _ = teacher.req("POST", f"/enrollments/{eid}/approve")
            appr.append(f"stu{sid}:{ox.get('code')}/"
                        f"{db1(f'select approval_status from {DB}.course_enrollments where id={eid};')}")
    record("G2b", "教师审批通过选课申请", "两名学生均 APPROVED",
           " ".join(appr),
           all(":200/APPROVED" in x for x in appr) and len(appr) == 2)

    st, obj, _ = student4.req("GET", f"/resources/download/{vid}")
    record("G2c", "非成员（未选课）下载资源被拒", "403（成员资格 = 有效 + 已审批）",
           f"HTTP {st}", st == 403)

    # ============ H. 环节7 查看资料与视频 ============
    print("\n----- H. 环节7：学生查看资料与视频 -----")
    st, obj, _ = student.req("GET", f"/resources/course/{cid}")
    n = len(obj.get("data") or [])
    record("H1", "学生查询课程资源列表", "HTTP 200，返回视频与讲义",
           f"HTTP {st} code={obj.get('code')} 资源数={n}", st == 200 and n >= 2)

    st, obj, _ = student.req("GET", f"/student/courses/{cid}/learning-center")
    record("H2", "学习中心（章节任务点/闯关解锁态）", "HTTP 200 返回章节任务点结构",
           f"HTTP {st} code={obj.get('code')}", st == 200 and obj.get("code") == 200)

    st, obj, _ = student.req("GET", f"/resources/download/{vid}")
    record("H3", "已选课学生下载视频资源", "HTTP 200（放行）", f"HTTP {st}", st == 200)

    st, obj, _ = student4.req("GET", f"/resources/download/{vid}")
    record("H4", "越权：未选课学生下载资源", "HTTP 403（拒绝）", f"HTTP {st}", st == 403)

    st, obj, _ = student.req("POST", f"/student/courses/{cid}/tasks/{task}/complete", {"completionType": "DOC"})
    tc = db1(f"select completed from {DB}.task_completions where student_id=4 and task_id={task};")
    record("H5", "学生完成任务点（视频/文档类）", "HTTP 200 且 task_completions 落库 completed=1",
           f"HTTP {st} code={obj.get('code')} completed={tc}", st == 200 and tc == "1")

    st, obj, _ = student.req("GET", f"/student/courses/{cid}/progress-summary")
    record("H6", "课程任务点完成度统计", "HTTP 200", f"HTTP {st} code={obj.get('code')}", st == 200)

    # ============ I. 环节8 完成作业 ============
    print("\n----- I. 环节8：学生完成作业 -----")
    st, obj, _ = student.req("POST", f"/student/assignments/{task}/submit",
                             {"content": "OSI 七层：物理/数据链路/网络/传输/会话/表示/应用；TCP/IP 四层：网络接口/网际/传输/应用。"})
    sub = (obj.get("data") or {}).get("id")
    sub_status = db1(f"select status from {DB}.submissions where id={sub};") if sub else None
    record("I1", "学生提交作业", "HTTP 200，status=SUBMITTED",
           f"HTTP {st} code={obj.get('code')} submissionId={sub} status={sub_status}",
           st == 200 and sub_status == "SUBMITTED")

    st, obj, _ = student.req("POST", f"/student/assignments/{task}/submit", {"content": "再次提交"})
    record("I2", "边界：超过 max_submissions=1 再次提交", "业务错误（已达最大提交次数）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    st, obj, _ = student.req("POST", f"/submissions/{sub}/grade", {"score": 95, "feedback": "自评"})
    record("I3", "越权：学生自批作业", "403 无权限", f"HTTP {st} code={obj.get('code')}", st == 403)

    st, obj, _ = teacher.req("POST", f"/submissions/{sub}/grade", {"score": 88, "feedback": "结构清晰，补充丢包分析"})
    g_status = db1(f"select status from {DB}.submissions where id={sub};")
    g_score = db1(f"select score from {DB}.submissions where id={sub};")
    record("I4", "教师批改作业", "HTTP 200，status=GRADED 且 score=88",
           f"HTTP {st} code={obj.get('code')} status={g_status} score={g_score}",
           g_status == "GRADED" and g_score == "88.00")

    # 逾期作业
    st, obj, _ = teacher.req("POST", "/teacher/assignments", {
        "title": "作业2：抓包分析报告（逾期用例）", "taskType": "assignment",
        "courseId": cid, "maxScore": 100,
        "dueDate": (datetime.now() - timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S"),
        "submissionFormat": "text", "status": "draft"})
    task2 = (obj.get("data") or {}).get("id")
    teacher.req("PUT", f"/teacher/assignments/{task2}/publish")
    st, obj, _ = student.req("POST", f"/student/assignments/{task2}/submit", {"content": "迟交报告"})
    late = db1(f"select is_late from {DB}.submissions where task_id={task2} and student_id=4;")
    pen = db1(f"select late_penalty from {DB}.submissions where task_id={task2} and student_id=4;")
    record("I5", "边界：截止后提交（逾期判定 + 扣分）", "is_late=1 且 late_penalty=10.00（100×10%×1天）",
           f"HTTP {st} code={obj.get('code')} is_late={late} late_penalty={pen}",
           late == "1" and pen == "10.00")

    # ============ J. 环节9 参加考试 ============
    print("\n----- J. 环节9：学生参加考试 -----")
    st, obj, _ = student4.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    record("J1", "异常：未选课学生开始考试", "403 拒绝（尚未加入该课程，原为 200 考试开始成功）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") == 403)

    st, obj, _ = student.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    sess = (obj.get("data") or {}).get("id")
    s_status = (obj.get("data") or {}).get("status")
    record("J2", "学生开始考试", "HTTP 200，status=STARTED",
           f"HTTP {st} code={obj.get('code')} sessionId={sess} status={s_status}",
           st == 200 and (s_status or "").upper() == "STARTED")

    st, obj, _ = student.req("GET", f"/exam-sessions/{sess}/questions")
    qs = obj.get("data") or []
    leak = False
    try:
        blob = json.dumps(qs, ensure_ascii=False)
        leak = ("correctAnswers" in blob) or ("correct_answers" in blob) or ("\"answer\"" in blob)
    except Exception:
        pass
    record("J3", "获取考试题目", "HTTP 200 且不泄露正确答案",
           f"HTTP {st} 题数={len(qs)} 含答案字段={leak}", st == 200 and len(qs) == 3 and not leak)

    order = [q.get("id") or q.get("questionId") for q in qs]
    # 构造"全部正确答案"的作答：答案取自题库（json_unquote 还原为普通字符串，即学生应填的值）
    sess_qids = [r.split("\t")[0] for r in
                 db(f"select question_id from {DB}.exam_questions where exam_paper_id={paper} order by order_index;")]
    correct_map = {}
    if sess_qids:
        for r in db(f"select id, json_unquote(correct_answers) from {DB}.questions where id in ({','.join(sess_qids)});"):
            p = r.split("\t")
            if len(p) >= 2:
                correct_map[p[0]] = p[1]
    fill = {q: correct_map.get(q, "") for q in sess_qids}
    record("J3b", "构造满分作答（答案取自题库）", "3 题答案均非空",
           f"answers={json.dumps(fill, ensure_ascii=False)}",
           all(v != "" for v in fill.values()) and len(fill) == 3,
           "学生应填值 = json_unquote(correct_answers)，与前端作答语义一致")

    st, obj, _ = student.req("POST", f"/exam-sessions/{sess}/auto-save", {"sessionId": sess, "answers": fill})
    record("J4", "作答自动保存", "HTTP 200", f"HTTP {st} code={obj.get('code')}", st == 200)

    st, obj, _ = student.req("POST", f"/exam-sessions/{sess}/submit", {"sessionId": sess, "answers": fill})
    st_after = db1(f"select status from {DB}.exam_sessions where id={sess};")
    record("J5", "提交试卷", "HTTP 200，status=SUBMITTED",
           f"HTTP {st} code={obj.get('code')} status={st_after}",
           st_after in ("SUBMITTED", "AUTO_SUBMITTED"),
           "若为 AUTO_SUBMITTED 说明被超时调度器抢先收卷（见 J13 缺陷），非提交接口问题" if st_after == "AUTO_SUBMITTED" else "")

    st, obj, _ = student.req("POST", f"/exam-sessions/{sess}/submit", {"sessionId": sess, "answers": fill})
    record("J6", "边界：重复提交试卷", "业务错误（已提交或已结束）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    st, obj, _ = teacher.req("POST", f"/exam-sessions/{sess}/auto-grade")
    gstat = db1(f"select status from {DB}.exam_sessions where id={sess};")
    gscore = db1(f"select total_score from {DB}.exam_sessions where id={sess};")
    pct = db1(f"select percentage from {DB}.exam_sessions where id={sess};")
    full = db1(f"select sum(points) from {DB}.exam_questions where exam_paper_id={paper};") or "0"
    record("J7", "自动判分（作答全部正确，应得满分）", f"HTTP 200，status=GRADED 且 total_score={full}",
           f"HTTP {st} code={obj.get('code')} status={gstat} total_score={gscore} 卷面满分={full} percentage={pct}",
           gstat == "GRADED" and gscore is not None and abs(float(gscore or 0) - float(full)) < 0.01,
           "修复后对 correct_answers 做 JSON 标量解引用，与作答字符串口径一致（原为引号参与比较导致全判错）")

    st, obj, _ = student.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    record("J8", "边界：超过 max_attempts=1 再次开考", "业务错误（已达最大尝试次数）",
           f"HTTP {st} code={obj.get('code')} msg={obj.get('message')}", obj.get("code") != 200)

    st, obj, _ = student.req("GET", f"/exam-sessions/{sess}/result")
    record("J9", "学生查询考试成绩", "HTTP 200 返回成绩明细",
           f"HTTP {st} code={obj.get('code')}", st == 200 and obj.get("code") == 200)

    st, obj, _ = student2.req("GET", f"/exam-sessions/{sess}/questions")
    record("J10", "越权：他人考试会话读取", "403 拒绝", f"HTTP {st} code={obj.get('code')}",
           st in (403, 404))

    st, obj, _ = student.req("GET", "/student/grades")
    record("J11", "学生成绩总览", "HTTP 200", f"HTTP {st} code={obj.get('code')}", st == 200)

    st, obj, _ = teacher.req("GET", f"/teacher/courses/{cid}/dashboard")
    record("J12", "教师课程驾驶舱数据", "HTTP 200", f"HTTP {st} code={obj.get('code')}", st == 200)

    # ---- J13：进行中的考试是否被超时调度器误收卷（考试时长形同虚设）----
    st, obj, _ = student2.req("POST", "/exam-sessions/start", {"examPaperId": paper})
    s2 = (obj.get("data") or {}).get("id")
    s2_0 = db1(f"select status from {DB}.exam_sessions where id={s2};") if s2 else None
    print("   ... 等待 40s 观察超时调度器行为（试卷时长 45 分钟，远未到期）")
    time.sleep(40)
    s2_1 = db1(f"select status from {DB}.exam_sessions where id={s2};") if s2 else None
    record("J13", "进行中的考试是否被超时调度器强制收卷", "40s 后仍应为 STARTED（时长 45 分钟未到）",
           f"开考后 status={s2_0}；40s 后 status={s2_1}",
           s2_0 == "STARTED" and s2_1 == "STARTED",
           "修复前 ExamTimeoutScheduler 无时间比较即对全部 STARTED 会话收卷；修复后按「开始时间+时长 / 试卷结束时间取早」判定")

    # ============ 汇总 ============
    print("\n" + "=" * 100)
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r[4] == "PASS")
    print(f"用例总数 {total}，通过 {passed}，失败 {total - passed}")
    print("=" * 100)
    if DEFECTS:
        print("\n## 与预期不符的发现（需人工确认是否为缺陷）")
        for i, (cid, title, expect, actual, note) in enumerate(DEFECTS, 1):
            print(f"{i}. [{cid}] {title}\n   期望: {expect}\n   实际: {actual}\n   说明: {note}")

    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_result.json"), "w", encoding="utf-8") as f:
        json.dump({"total": total, "passed": passed,
                   "results": [{"case": r[0], "title": r[1], "expect": r[2], "actual": r[3],
                                "verdict": r[4], "note": r[5]} for r in RESULTS]},
                  f, ensure_ascii=False, indent=2)
    print("\n明细已写入 e2e_result.json")


if __name__ == "__main__":
    main()
