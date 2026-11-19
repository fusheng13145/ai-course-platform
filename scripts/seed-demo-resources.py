#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""给本地开发库的课程补齐真实可下载资源（非 Flyway 迁移）。

与 `seed-demo-data.py` 的分工：
- 本脚本只负责“真实文件”这一层：生成 DOCX、调用教师上传接口写入 MinIO；
- 不直接 INSERT `resources`，不伪造 URL；
- 默认 dry-run，`--apply` 才上传；
- 上传后立即用教师会话下载并比对 SHA-256，再用学生会话验证可读。

前置：
- 后端 8080 在运行；
- 本地开发库可连接（读取仓库根 `.env` 的 MYSQL_ROOT_PASSWORD）；
- 演示教师/学生口令默认 123456，可用 DEMO_PASSWORD 覆盖。

用法：
    python scripts/seed-demo-resources.py
    python scripts/seed-demo-resources.py --apply
    python scripts/seed-demo-resources.py --apply --course 4178
"""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import Any

import pymysql
import requests
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt, RGBColor
from docx.oxml.ns import qn
from pymysql.cursors import DictCursor

ROOT = Path(__file__).resolve().parent.parent
DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
COURSE_IDS = [1, 2, 3, 4, 5, 6, 4178]
MAX_FILE_SIZE = 1024 * 1024


def load_env() -> dict[str, str]:
    vals: dict[str, str] = {}
    env_path = ROOT / ".env"
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            vals[key.strip()] = value.strip().strip('"').strip("'")
    return vals


def connect(env: dict[str, str]) -> pymysql.Connection:
    profile = " ".join(env.get(k, "") for k in
                       ("APP_ENV", "SPRING_PROFILES_ACTIVE", "SPRING_PROFILES_INCLUDE")).lower()
    if "prod" in profile:
        raise SystemExit("拒绝执行：APP_ENV/SPRING_PROFILES_ACTIVE 含 prod")
    host = env.get("MYSQL_HOST", "127.0.0.1")
    if host not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit(f"拒绝执行：只允许本机开发库，当前 MYSQL_HOST={host}")
    password = env.get("MYSQL_ROOT_PASSWORD", "")
    if not password:
        raise SystemExit("缺少 MYSQL_ROOT_PASSWORD，无法连接开发库")
    return pymysql.connect(
        host=host,
        port=int(env.get("MYSQL_PORT", "3306")),
        user="root",
        password=password,
        database=env.get("MYSQL_DATABASE", "ai_course_platform"),
        charset="utf8mb4",
        cursorclass=DictCursor,
    )


def rows(conn: pymysql.Connection, sql: str, params: tuple[Any, ...] = ()) -> list[dict]:
    with conn.cursor() as cur:
        cur.execute(sql, params)
        return list(cur.fetchall())


def safe_filename(name: str) -> str:
    name = re.sub(r'[\\/:*?"<>|]', "_", name).strip()
    return name[:120] or "resource.docx"


def font(run, size: float = 10.5, bold: bool = False, color: str | None = None) -> None:
    run.font.name = "Microsoft YaHei"
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor.from_string(color)


def add_title(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    font(p.add_run(text), 20, True)


def add_subtitle(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    font(p.add_run(text), 10.5, False, "666666")


def add_heading(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(4)
    font(p.add_run(text), 13, True)


def add_body(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0.74)
    p.paragraph_format.line_spacing = 1.5
    font(p.add_run(text), 10.5)


def add_bullet(doc: Document, text: str) -> None:
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.line_spacing = 1.4
    font(p.add_run(text), 10.5)


def add_table(doc: Document, headers: list[str], data: list[list[str]]) -> None:
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        font(cell.paragraphs[0].add_run(h), 10, True)
    for row in data:
        cells = table.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = ""
            font(cells[i].paragraphs[0].add_run(value), 9.5)


def build_syllabus(course: dict, chapters: list[str]) -> bytes:
    doc = Document()
    doc.sections[0].top_margin = Cm(2.2)
    doc.sections[0].bottom_margin = Cm(2.2)
    add_title(doc, f"{course['name']} 课程大纲")
    add_subtitle(doc, f"课程代码：{course.get('code') or '-'} ｜ 授课教师：{course.get('teacher') or '-'}")
    add_heading(doc, "一、课程简介")
    add_body(doc, f"{course['name']}是面向本科生开设的专业课程。课程以课堂讲授、案例分析和上机/实验实践相结合，"
                  "强调概念理解、问题建模和可验证的工程实践，帮助学习者形成完整的知识结构。")
    add_heading(doc, "二、课程目标")
    for item in [
        "掌握课程核心概念、基本方法与常用工具；",
        "能够分析典型问题，选择合适的方法并说明理由；",
        "完成规定实验/项目，提交可复现的过程材料与结果；",
        "形成自主查阅资料、团队协作和规范表达的能力。",
    ]:
        add_bullet(doc, item)
    add_heading(doc, "三、章节安排")
    chapter_rows = [[str(i + 1), title] for i, title in enumerate(chapters[:12])] or [["1", "课程导论"]]
    add_table(doc, ["周次/序号", "章节"], chapter_rows)
    add_heading(doc, "四、考核方式")
    add_body(doc, "总评由平时表现、作业/实验和期末考试构成。具体比例以课程成绩方案为准；"
                  "缺交或逾期提交按课程规则扣分，学术不端按学校规定处理。")
    add_heading(doc, "五、学习建议")
    add_body(doc, "课前完成预习任务，课中记录关键概念与例题，课后在 48 小时内完成练习；"
                  "遇到问题时先独立定位，再通过讨论区或答疑时间求助。")
    return docx_bytes(doc)


def build_chapter_guide(course: dict, chapter: str) -> bytes:
    doc = Document()
    doc.sections[0].top_margin = Cm(2.2)
    doc.sections[0].bottom_margin = Cm(2.2)
    add_title(doc, f"{chapter} 学习指南")
    add_subtitle(doc, f"{course['name']} ｜ 第 1 章配套材料")
    add_heading(doc, "一、学习目标")
    for item in [
        f"能用自己的话解释“{chapter}”的核心概念；",
        "能识别本章知识在后续章节中的位置与作用；",
        "能完成基础练习，并对结果进行简单验证。",
    ]:
        add_bullet(doc, item)
    add_heading(doc, "二、重点与难点")
    add_body(doc, "重点：基本概念、典型方法与适用条件。难点：将实际问题转化为可计算/可实现的形式，"
                  "并解释结果为什么合理。")
    add_heading(doc, "三、课前准备")
    for item in ["阅读教材对应章节，标记不理解的概念；", "完成 3–5 道基础自测题；",
                 "准备一个与本章相关的生活或工程案例。"]:
        add_bullet(doc, item)
    add_heading(doc, "四、课堂任务")
    add_body(doc, "跟随教师完成概念梳理与例题推导，参与课堂讨论；记录至少一条“原来如此”的关键点，"
                  "以及一个仍需课后解决的问题。")
    add_heading(doc, "五、课后练习")
    add_body(doc, "完成本章作业与实验任务；提交前自查：输入/条件是否完整、过程是否可复现、"
                  "结论是否有证据支撑。建议在讨论区回答一位同学的问题，以输出倒逼输入。")
    return docx_bytes(doc)


def build_project(course: dict) -> bytes:
    doc = Document()
    doc.sections[0].top_margin = Cm(2.2)
    doc.sections[0].bottom_margin = Cm(2.2)
    add_title(doc, f"{course['name']} 综合实践任务")
    add_subtitle(doc, "过程材料与结果同等重要")
    add_heading(doc, "一、任务目标")
    add_body(doc, "围绕课程核心知识完成一个可运行/可验证的小型实践作品，经历需求分析、方案设计、实现/求解、"
                  "测试与反思的完整过程。")
    add_heading(doc, "二、提交清单")
    for item in ["任务说明与需求拆解（1 页以内）；", "方案设计与关键步骤说明；",
                 "可复现的代码/数据/实验记录；", "结果截图或测试记录；",
                 "300 字以内的复盘：做了什么、遇到什么、如何解决。"]:
        add_bullet(doc, item)
    add_heading(doc, "三、评分点")
    add_table(doc, ["维度", "分值建议", "观察点"], [
        ["问题理解", "20", "目标明确、范围合理"],
        ["过程完整性", "30", "设计、实现、验证链条完整"],
        ["结果质量", "30", "正确、稳定、可复现"],
        ["表达与反思", "20", "结构清晰、反思具体"],
    ])
    add_heading(doc, "四、提交要求")
    add_body(doc, "在课程任务截止前提交；文件命名建议为“学号-姓名-课程项目”。"
                  "不得抄袭；引用他人代码、数据或结论时须注明来源。")
    return docx_bytes(doc)


def docx_bytes(doc: Document) -> bytes:
    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def build_resources(course: dict, chapters: list[str]) -> list[tuple[str, str, bytes]]:
    first = chapters[0] if chapters else "课程导论"
    specs: list[tuple[str, str, bytes]] = [
        (safe_filename(f"{course['name']}-课程大纲.docx"),
         "课程目标、章节安排、考核方式与学习建议", build_syllabus(course, chapters)),
        (safe_filename(f"第1章-{first}-学习指南.docx"),
         "第 1 章学习目标、重点难点、课前准备与课后练习", build_chapter_guide(course, first)),
        (safe_filename(f"{course['name']}-综合实践任务.docx"),
         "综合实践任务的目标、提交清单、评分点与提交要求", build_project(course)),
    ]
    if course["id"] == 4178:
        # 4178 已有 10 条真实资源，只补一份综合实践任务，避免重复堆量。
        specs = specs[2:]
    return specs


def login(base: str, username: str, password: str) -> requests.Session:
    session = requests.Session()
    session.trust_env = False
    r = session.post(f"{base}/auth/login",
                     json={"username": username, "password": password}, timeout=15)
    if r.status_code != 200:
        raise SystemExit(f"登录失败：{username} HTTP {r.status_code}")
    return session


def list_resources(session: requests.Session, base: str, course_id: int) -> list[dict]:
    r = session.get(f"{base}/resources/course/{course_id}", timeout=15)
    if r.status_code != 200:
        raise SystemExit(f"读取课程 {course_id} 资源失败：HTTP {r.status_code}")
    data = r.json().get("data") or []
    return data if isinstance(data, list) else []


def upload_one(session: requests.Session, base: str, course_id: int,
               filename: str, description: str, content: bytes) -> dict:
    files = {"file": (filename, content, DOCX_MIME)}
    data = {"courseId": str(course_id), "description": description}
    r = session.post(f"{base}/resources/upload", files=files, data=data, timeout=30)
    if r.status_code != 200:
        raise SystemExit(f"上传失败：{filename} HTTP {r.status_code} {r.text[:200]}")
    body = r.json()
    resource = body.get("data") or {}
    if not resource.get("id") or not resource.get("url"):
        raise SystemExit(f"上传返回缺字段：{filename} {body}")
    return resource


def verify_download(session: requests.Session, base: str, resource: dict, content: bytes) -> None:
    r = session.get(f"{base}/resources/download/{resource['id']}", timeout=30)
    if r.status_code != 200:
        raise SystemExit(f"下载失败：{resource['fileName']} HTTP {r.status_code}")
    if hashlib.sha256(r.content).hexdigest() != hashlib.sha256(content).hexdigest():
        raise SystemExit(f"下载字节不一致：{resource['fileName']}")


def main() -> int:
    parser = argparse.ArgumentParser(description="为本地开发库课程补齐真实资源文件")
    parser.add_argument("--apply", action="store_true", help="实际生成并上传；不加只打印计划")
    parser.add_argument("--course", type=int, action="append", help="只处理指定 course id，可重复")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api", help="后端 API 根地址")
    args = parser.parse_args()

    env = load_env()
    conn = connect(env)
    course_ids = [c for c in COURSE_IDS if not args.course or c in args.course]
    course_rows = rows(conn, """
        SELECT c.id,c.code,c.name,c.teacher_id,u.username AS teacher
        FROM courses c LEFT JOIN users u ON u.id=c.teacher_id
        WHERE c.id IN (%s)
        ORDER BY c.id
    """ % ",".join(str(int(x)) for x in course_ids))
    by_id = {int(x["id"]): x for x in course_rows}
    missing = [c for c in course_ids if c not in by_id]
    if missing:
        print(f"[resource] 跳过不存在的课程：{missing}")

    chapters: dict[int, list[str]] = {}
    for cid in course_ids:
        if cid not in by_id:
            continue
        chapter_rows = rows(conn, """
            SELECT title FROM course_chapters
            WHERE course_id=%s AND (parent_id IS NULL OR parent_id=0)
            ORDER BY order_index, id
        """, (cid,))
        chapters[cid] = [str(x["title"]) for x in chapter_rows if x.get("title")]

    password = os.getenv("DEMO_PASSWORD", "123456")
    teacher_usernames = sorted({str(x["teacher"]) for x in by_id.values() if x.get("teacher")})
    sessions: dict[str, requests.Session] = {}
    if args.apply:
        for username in teacher_usernames:
            sessions[username] = login(args.base_url, username, password)
            print(f"[resource] 教师会话就绪：{username}")

    planned: list[dict] = []
    for cid in course_ids:
        if cid not in by_id:
            continue
        course = by_id[cid]
        specs = build_resources(course, chapters.get(cid, []))
        existing = list_resources(sessions.get(str(course["teacher"])), args.base_url, cid) if args.apply else []
        existing_names = {str(x.get("fileName") or "") for x in existing}
        for filename, description, content in specs:
            planned.append({"course": course, "filename": filename,
                            "description": description, "content": content,
                            "exists": filename in existing_names})

    print("[resource] 计划：")
    for item in planned:
        mark = "已存在，跳过" if item["exists"] else "待上传"
        print(f"  - {item['course']['id']} {item['course']['name']} / {item['filename']} [{mark}]")
    if not args.apply:
        print("[resource] dry-run：未上传。加 --apply 执行。")
        conn.close()
        return 0

    uploaded = skipped = 0
    created: list[dict] = []
    with tempfile.TemporaryDirectory(prefix="aicourse-res-") as tmp:
        for item in planned:
            if item["exists"]:
                skipped += 1
                continue
            course = item["course"]
            session = sessions[str(course["teacher"])]
            resource = upload_one(session, args.base_url, int(course["id"]),
                                  item["filename"], item["description"], item["content"])
            verify_download(session, args.base_url, resource, item["content"])
            uploaded += 1
            created.append({"course": course, "resource": resource, "content": item["content"]})
            print(f"[resource] 已上传 {course['id']} {course['name']} / {item['filename']} -> {resource['url']}")

    # 学生会话抽查：每门课至少能列出并下载一条本轮新资源。
    student = login(args.base_url, "student", password)
    checked = 0
    for item in created:
        course = item["course"]
        resource = item["resource"]
        listed = list_resources(student, args.base_url, int(course["id"]))
        if not any(int(x.get("id") or 0) == int(resource["id"]) for x in listed):
            raise SystemExit(f"学生资源列表缺新资源：{course['id']} {resource['fileName']}")
        verify_download(student, args.base_url, resource, item["content"])
        checked += 1

    print(f"[resource] 完成：上传 {uploaded}，跳过 {skipped}，学生抽查 {checked}；未直接写数据库。")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
