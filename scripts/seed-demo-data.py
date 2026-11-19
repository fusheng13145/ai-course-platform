#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在本地开发库补齐可演示的课程业务数据（非 Flyway 迁移）。

设计口径：
- 只面向 127.0.0.1 的开发库，显式拒绝 prod / 远程库；
- `--apply` 才写入，默认只打印计划；
- 所有写入尽量幂等：有唯一键的走 ON DUPLICATE KEY，没有唯一键的先按业务键查重；
- 课程范围：1–6 + 4178；4179 是压测残留，排除且不删除；
- 不伪造资源文件 URL：本脚本不新增 resources，只保留既有真实资源。

用法：
    python scripts/seed-demo-data.py
    python scripts/seed-demo-data.py --apply
    python scripts/seed-demo-data.py --apply --course 4178
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
from datetime import datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pymysql
from pymysql.cursors import DictCursor

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from seed_data_templates import TEMPLATES  # noqa: E402

COURSES = [
    {
        "id": 1,
        "template": "Python程序设计",
        "students": 30,
        "extra_chapters": ["Python 概述与开发环境", "流程控制与循环", "模块、包与标准库", "面向对象与综合项目"],
    },
    {
        "id": 2,
        "template": "数据结构与算法",
        "students": 30,
        "extra_chapters": ["动态规划初步", "课程设计工作坊"],
    },
    {
        "id": 3,
        "template": "机器学习",
        "students": 30,
        "extra_chapters": ["特征工程与模型部署", "综合项目实战"],
    },
    {
        "id": 4,
        "template": "高等数学A",
        "students": 30,
        "extra_chapters": ["多元函数微分学", "数学实验与建模"],
    },
    {
        "id": 5,
        "template": "Web前端开发",
        "students": 30,
        "extra_chapters": ["前端工程化与部署", "综合项目实战"],
    },
    {
        "id": 6,
        "template": "数据库原理与应用",
        "students": 30,
        "extra_chapters": ["数据库运维与备份", "综合项目实战"],
    },
    {
        "id": 4178,
        "template": "计算机网络",
        "students": 60,
        "extra_chapters": [],
    },
]

STUDENT_POOL = [4, 5, 6, 7, 8] + list(range(2002, 2057))
PAPER_TYPES = ("SINGLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "ESSAY")
SEED_SUBMISSION_TEXT = "已完成本次作业，提交内容含解题过程与总结。"


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
    profile = " ".join(
        env.get(k, "") for k in ("APP_ENV", "SPRING_PROFILES_ACTIVE", "SPRING_PROFILES_INCLUDE")
    ).lower()
    if "prod" in profile:
        raise SystemExit("拒绝执行：APP_ENV/SPRING_PROFILES_ACTIVE 含 prod")
    host = env.get("MYSQL_HOST", "127.0.0.1")
    if host not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit(f"拒绝执行：只允许本机开发库，当前 MYSQL_HOST={host}")
    password = env.get("MYSQL_ROOT_PASSWORD", "")
    if not password:
        raise SystemExit("缺少 MYSQL_ROOT_PASSWORD，无法连接开发库")
    db = env.get("MYSQL_DATABASE", "ai_course_platform")
    conn = pymysql.connect(
        host=host,
        port=int(env.get("MYSQL_PORT", "3306")),
        user="root",
        password=password,
        database=db,
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
    )
    with conn.cursor() as cur:
        cur.execute("SELECT DATABASE() AS db, @@hostname AS host")
        row = cur.fetchone()
    print(f"[seed] 已连接开发库：{row['db']} @ {row['host']}")
    return conn


def j(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False)


class Seeder:
    def __init__(self, conn: pymysql.Connection, apply: bool) -> None:
        self.conn = conn
        self.apply = apply
        self.now = datetime.now().replace(microsecond=0)
        self.counts: dict[str, int] = {}

    def q(self, sql: str, params: tuple[Any, ...] = ()) -> list[dict]:
        with self.conn.cursor() as cur:
            if params:
                cur.execute(sql, params)
            else:
                cur.execute(sql)
            return list(cur.fetchall())

    def one(self, sql: str, params: tuple[Any, ...] = ()) -> dict | None:
        rows = self.q(sql, params)
        return rows[0] if rows else None

    def run(self, sql: str, params: tuple[Any, ...] = ()) -> int:
        if not self.apply:
            return 0
        with self.conn.cursor() as cur:
            if params:
                cur.execute(sql, params)
            else:
                cur.execute(sql)
            return int(cur.lastrowid or 0)

    def bump(self, table: str, n: int = 1) -> None:
        self.counts[table] = self.counts.get(table, 0) + n

    def table_counts(self) -> dict[str, int]:
        names = [
            "users", "courses", "course_enrollments", "course_chapters", "tasks",
            "submissions", "questions", "exam_papers", "exam_sessions", "grade_items",
            "discussion_topics", "discussion_posts", "announcements", "learning_records",
            "sign_ins", "sign_in_records", "votes", "vote_records",
        ]
        out: dict[str, int] = {}
        for name in names:
            row = self.one(f"SELECT COUNT(*) AS n FROM `{name}`")
            out[name] = int(row["n"]) if row else 0
        return out

    def ensure_enrollment(self, course_id: int, student_id: int, class_id: int, teacher_id: int) -> None:
        existing = self.one(
            "SELECT id FROM course_enrollments WHERE student_id=%s AND course_id=%s",
            (student_id, course_id),
        )
        if existing:
            if self.apply:
                self.run(
                    """UPDATE course_enrollments
                       SET teaching_class_id=%s, approval_status='APPROVED', is_active=1, source='SEED'
                       WHERE id=%s""",
                    (class_id, existing["id"]),
                )
            return
        self.bump("course_enrollments")
        self.run(
            """INSERT INTO course_enrollments
               (student_id, course_id, teaching_class_id, approval_status, approval_time,
                approved_by, is_active, progress, total_study_time, enrolled_at, source,
                created_at, updated_at)
               VALUES (%s,%s,%s,'APPROVED',%s,%s,1,0,0,%s,'SEED',%s,%s)
               ON DUPLICATE KEY UPDATE teaching_class_id=VALUES(teaching_class_id),
                 approval_status='APPROVED', is_active=1, source='SEED'""",
            (student_id, course_id, class_id, self.now, teacher_id,
             self.now - timedelta(days=20), self.now, self.now),
        )

    def ensure_chapter(self, course_id: int, title: str, order_index: int) -> dict:
        row = self.one(
            "SELECT id,title,order_index FROM course_chapters WHERE course_id=%s AND title=%s",
            (course_id, title),
        )
        if row:
            return row
        if not self.apply:
            return {"id": 0, "title": title, "order_index": order_index}
        self.bump("course_chapters")
        cid = self.run(
            """INSERT INTO course_chapters
               (course_id, parent_id, title, content, order_index, duration, is_published,
                created_at, updated_at)
               VALUES (%s,NULL,%s,%s,%s,45,1,%s,%s)""",
            (course_id, title, f"围绕「{title}」的核心概念、例题与课堂活动。", order_index, self.now, self.now),
        )
        return {"id": cid, "title": title, "order_index": order_index}

    @staticmethod
    def _normalize_question(item: tuple) -> tuple:
        # 模板里 ESSAY 题省略 options 字段，写成 5 元组；这里统一成 6 元组。
        if len(item) == 5:
            qtype, content, answers, difficulty, explanation = item
            return qtype, content, None, answers, difficulty, explanation
        return item

    def ensure_question(self, course_id: int, teacher_id: int, item: tuple, chapter: str, rng: random.Random) -> dict:
        qtype, content, options, answers, difficulty, explanation = self._normalize_question(item)
        row = self.one(
            "SELECT id,type,correct_answers FROM questions WHERE course_id=%s AND content=%s",
            (course_id, content),
        )
        if row:
            return row
        if qtype in ("SINGLE_CHOICE", "MULTIPLE_CHOICE"):
            opts = options or []
        elif qtype == "TRUE_FALSE":
            opts = ["正确", "错误"]
        else:
            opts = []
        if qtype == "SINGLE_CHOICE":
            correct = (answers or ["A"])[0]
        elif qtype == "MULTIPLE_CHOICE":
            correct = answers or []
        elif qtype == "TRUE_FALSE":
            correct = (answers or ["A"])[0] in ("A", "正确", "true", True)
        else:
            correct = answers or []
        if not self.apply:
            return {"id": 0, "type": qtype, "correct_answers": correct}
        self.bump("questions")
        qid = self.run(
            """INSERT INTO questions
               (type, content, options, correct_answers, difficulty, score, knowledge_points,
                explanation, usage_count, correct_rate, course_id, created_by, is_active,
                created_at, updated_at, legacy_unassigned)
               VALUES (%s,%s,%s,%s,%s,10,%s,%s,%s,%s,%s,%s,1,%s,%s,0)""",
            (qtype, content, j(opts), j(correct), difficulty, j([chapter]), explanation,
             rng.randint(3, 20), Decimal(str(rng.randint(55, 90))), course_id, teacher_id,
             self.now, self.now),
        )
        return {"id": qid, "type": qtype, "correct_answers": correct}

    def ensure_task(self, course_id: int, teacher_id: int, chapter_id: int | None,
                    title: str, idx: int, status: str = "PUBLISHED") -> dict:
        row = self.one("SELECT id,title,due_date FROM tasks WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            return row
        due = self.now + timedelta(days=[-12, 3, 10, 17, 24, 31][idx % 6])
        if not self.apply:
            return {"id": 0, "title": title, "due_date": due}
        self.bump("tasks")
        tid = self.run(
            """INSERT INTO tasks
               (title, description, task_type, course_id, creator_id, chapter_id, due_date,
                max_score, status, is_published, submission_format, requirements,
                attachment_url, start_date, late_penalty_per_day, max_submissions,
                created_at, updated_at, visibility)
               VALUES (%s,%s,'ASSIGNMENT',%s,%s,%s,%s,100,%s,%s,%s,%s,NULL,%s,10,3,%s,%s,'COURSE')""",
            (title, f"围绕「{title}」完成课后练习，提交代码、报告或文字说明。", course_id,
             teacher_id, chapter_id, due, status, 1 if status == "PUBLISHED" else 0,
             "文本/代码/报告", "独立完成；引用资料请注明来源；截止前可多次提交，以最后一次为准。",
             self.now - timedelta(days=30), self.now, self.now),
        )
        return {"id": tid, "title": title, "due_date": due}

    def ensure_announcement(self, course_id: int, class_id: int, teacher_id: int,
                            title: str, content: str) -> None:
        row = self.one("SELECT id FROM announcements WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            return
        self.bump("announcements")
        self.run(
            """INSERT INTO announcements
               (course_id, teaching_class_id, admin_class_id, title, content, publisher_id,
                publisher_role, created_at, updated_at)
               VALUES (%s,%s,NULL,%s,%s,%s,'TEACHER',%s,%s)""",
            (course_id, class_id, title, content, teacher_id, self.now, self.now),
        )

    def ensure_topic(self, course_id: int, class_id: int, teacher_id: int, title: str,
                     content: str, idx: int) -> dict:
        row = self.one("SELECT id,reply_count FROM discussion_topics WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            return row
        if not self.apply:
            return {"id": 0, "reply_count": 0}
        self.bump("discussion_topics")
        tid = self.run(
            """INSERT INTO discussion_topics
               (course_id, chapter_id, title, content, type, status, creator_id, creator_role,
                is_pinned, is_essence, reply_count, last_reply_at, created_at, updated_at,
                teaching_class_id)
               VALUES (%s,NULL,%s,%s,'NORMAL','OPEN',%s,'TEACHER',%s,0,0,NULL,%s,%s,%s)""",
            (course_id, title, content, teacher_id, 1 if idx == 0 else 0,
             self.now - timedelta(days=1 + idx), self.now, class_id),
        )
        return {"id": tid, "reply_count": 0}

    def ensure_post(self, topic_id: int, author_id: int, author_role: str, content: str,
                    floor: int, created_at: datetime, rng: random.Random) -> None:
        if not self.apply:
            self.bump("discussion_posts")
            return
        row = self.one(
            "SELECT id FROM discussion_posts WHERE topic_id=%s AND author_id=%s AND content=%s",
            (topic_id, author_id, content),
        )
        if row:
            return
        self.bump("discussion_posts")
        self.run(
            """INSERT INTO discussion_posts
               (topic_id, parent_id, author_id, author_role, content, like_count, floor,
                mentions, moderation_status, moderation_reason, moderated_at, moderated_by,
                created_at, updated_at)
               VALUES (%s,NULL,%s,%s,%s,%s,%s,NULL,'NORMAL',NULL,NULL,NULL,%s,%s)""",
            (topic_id, author_id, author_role, content, rng.randint(0, 5), floor, created_at, created_at),
        )

    def ensure_paper(self, course_id: int, teacher_id: int, title: str, bank: list[dict],
                     duration: int, start: datetime, end: datetime, rng: random.Random) -> dict:
        row = self.one("SELECT id FROM exam_papers WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            qs = self.q(
                """SELECT q.id,q.type,q.correct_answers,eq.points
                   FROM exam_questions eq JOIN questions q ON q.id=eq.question_id
                   WHERE eq.exam_paper_id=%s ORDER BY eq.order_index""",
                (row["id"],),
            )
            paper = self.one("SELECT id,start_time,end_time FROM exam_papers WHERE id=%s", (row["id"],))
            return {"id": row["id"], "title": title, "questions": qs,
                    "total_score": sum(float(x["points"]) for x in qs),
                    "start_time": paper["start_time"], "end_time": paper["end_time"]}
        selected = [x for x in bank if x["type"] in PAPER_TYPES][:10]
        if len(selected) < 10:
            selected += [x for x in bank if x not in selected][: 10 - len(selected)]
        total = len(selected) * 10
        if not self.apply:
            self.bump("exam_papers")
            return {"id": 0, "title": title, "questions": [{**q, "points": 10} for q in selected],
                    "total_score": total, "start_time": start, "end_time": end}
        self.bump("exam_papers")
        pid = self.run(
            """INSERT INTO exam_papers
               (title, description, course_id, creator_id, status, instructions, start_time,
                end_time, total_score, duration, question_count, shuffle_questions,
                shuffle_options, show_score, show_correct_answer, max_attempts, time_limit,
                allow_review, generation_rules, is_active, created_at, updated_at, visibility)
               VALUES (%s,%s,%s,%s,'PUBLISHED',%s,%s,%s,%s,%s,%s,0,0,1,1,1,%s,1,NULL,1,%s,%s,'COURSE')""",
            (title, f"{title}：覆盖本阶段核心概念与典型题型。", course_id, teacher_id,
             "请独立完成；提交后可在成绩页查看得分与解析。", start, end, total, duration,
             len(selected), duration, self.now, self.now),
        )
        for i, q in enumerate(selected, 1):
            self.run(
                """INSERT INTO exam_questions (exam_paper_id, question_id, order_index, points)
                   VALUES (%s,%s,%s,10) ON DUPLICATE KEY UPDATE points=VALUES(points)""",
                (pid, q["id"], i),
            )
        return {"id": pid, "title": title, "questions": [{**q, "points": 10} for q in selected],
                "total_score": total, "start_time": start, "end_time": end}

    def ensure_grade_policy(self, course_id: int, teacher_id: int) -> dict:
        row = self.one("SELECT id,components FROM grade_policies WHERE course_id=%s", (course_id,))
        if row:
            return row
        components = [
            {"key": "usual", "name": "日常", "weight": 30},
            {"key": "assignment", "name": "作业", "weight": 30},
            {"key": "exam", "name": "考试", "weight": 40},
        ]
        if not self.apply:
            return {"id": 0, "components": components}
        self.bump("grade_policies")
        pid = self.run(
            """INSERT INTO grade_policies (course_id, name, components, creator_id, is_default,
               created_at, updated_at) VALUES (%s,%s,%s,%s,1,%s,%s)""",
            (course_id, f"{course_id}-standard", j(components), teacher_id, self.now, self.now),
        )
        return {"id": pid, "components": components}

    def ensure_grade_item(self, policy_id: int, student_id: int, key: str, score: Decimal,
                          teacher_id: int, class_id: int) -> None:
        row = self.one(
            """SELECT id FROM grade_items WHERE policy_id=%s AND student_id=%s
               AND component_key=%s""",
            (policy_id, student_id, key),
        )
        if row:
            return
        if not self.apply:
            self.bump("grade_items")
            return
        self.bump("grade_items")
        self.run(
            """INSERT INTO grade_items
               (policy_id, student_id, component_key, score, raw_score, source_id, remark,
                grader_id, created_at, updated_at, teaching_class_id)
               VALUES (%s,%s,%s,%s,%s,NULL,'演示数据',%s,%s,%s,%s)
               ON DUPLICATE KEY UPDATE score=VALUES(score), raw_score=VALUES(raw_score),
                 grader_id=VALUES(grader_id), remark='演示数据'""",
            (policy_id, student_id, key, score, score, teacher_id, self.now, self.now, class_id),
        )

    def ensure_submission(self, task: dict, student_id: int, teacher_id: int, class_id: int,
                          idx: int, rng: random.Random) -> None:
        if not self.apply:
            self.bump("submissions")
            return
        row = self.one(
            "SELECT id FROM submissions WHERE task_id=%s AND student_id=%s AND attempt_number=1",
            (task["id"], student_id),
        )
        if row:
            return
        graded = (idx + student_id) % 5 != 0
        score = Decimal(str(rng.randint(65, 98))) if graded else None
        due = task.get("due_date")
        if isinstance(due, datetime):
            submitted = min(self.now - timedelta(hours=2), due - timedelta(hours=3))
        else:
            submitted = self.now - timedelta(days=max(0, 10 - idx), hours=rng.randint(1, 8))
        self.bump("submissions")
        self.run(
            """INSERT INTO submissions
               (task_id, student_id, content, file_urls, submission_data, status, submitted_at,
                graded_at, returned_at, score, feedback, ai_feedback, grader_id, attempt_number,
                is_late, late_penalty, time_spent, view_count, created_at, updated_at,
                teaching_class_id)
               VALUES (%s,%s,%s,%s,NULL,%s,%s,%s,NULL,%s,%s,NULL,%s,1,0,0,%s,%s,%s,%s,%s)""",
            (task["id"], student_id, SEED_SUBMISSION_TEXT, j([]),
             "GRADED" if graded else "SUBMITTED", submitted,
             submitted + timedelta(days=1) if graded else None,
             score, "完成度良好，注意补充关键步骤说明。" if graded else None,
             teacher_id if graded else None, rng.randint(600, 1800), rng.randint(1, 3),
             submitted, submitted, class_id),
        )

    def ensure_learning_record(self, course_id: int, student_id: int, chapter_id: int,
                               action: str, progress: int, duration: int) -> None:
        if not self.apply:
            self.bump("learning_records")
            return
        row = self.one(
            """SELECT id FROM learning_records
               WHERE user_id=%s AND course_id=%s AND chapter_id=%s AND action_type=%s""",
            (student_id, course_id, chapter_id, action),
        )
        if row:
            return
        self.bump("learning_records")
        self.run(
            """INSERT INTO learning_records
               (user_id, resource_id, chapter_id, course_id, action_type, duration, progress,
                metadata, created_at)
               VALUES (%s,NULL,%s,%s,%s,%s,%s,%s,%s)""",
            (student_id, chapter_id, course_id, action, duration, progress,
             j({"source": "course"}), self.now - timedelta(days=2)),
        )

    def ensure_sign_in(self, course_id: int, teacher_id: int, title: str, idx: int) -> dict:
        row = self.one("SELECT id FROM sign_ins WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            return row
        if not self.apply:
            self.bump("sign_ins")
            return {"id": 0}
        self.bump("sign_ins")
        status = "CLOSED" if idx < 2 else "ONGOING"
        started = self.now - timedelta(days=7 - idx)
        sid = self.run(
            """INSERT INTO sign_ins
               (course_id, chapter_id, title, type, status, duration_minutes, gesture,
                sign_code, creator_id, started_at, closed_at, created_at, updated_at)
               VALUES (%s,NULL,%s,'NORMAL',%s,10,NULL,NULL,%s,%s,%s,%s,%s)""",
            (course_id, title, status, teacher_id, started,
             started + timedelta(minutes=10) if status == "CLOSED" else None, started, started),
        )
        return {"id": sid}

    def ensure_sign_record(self, sign_in_id: int, student_id: int, status: str) -> None:
        if not self.apply:
            self.bump("sign_in_records")
            return
        row = self.one("SELECT id FROM sign_in_records WHERE sign_in_id=%s AND student_id=%s", (sign_in_id, student_id))
        if row:
            return
        self.bump("sign_in_records")
        self.run(
            """INSERT INTO sign_in_records
               (sign_in_id, student_id, status, sign_time, ip, user_agent, photo_url,
                latitude, longitude, submitted_code, submitted_gesture, created_at, updated_at)
               VALUES (%s,%s,%s,%s,NULL,NULL,NULL,NULL,NULL,NULL,NULL,%s,%s)""",
            (sign_in_id, student_id, status, self.now if status == "PRESENT" else None,
             self.now, self.now),
        )

    def ensure_vote(self, course_id: int, teacher_id: int, title: str, options: list[str], idx: int) -> dict:
        row = self.one("SELECT id FROM votes WHERE course_id=%s AND title=%s", (course_id, title))
        if row:
            return row
        if not self.apply:
            self.bump("votes")
            return {"id": 0}
        self.bump("votes")
        status = "CLOSED" if idx < 2 else "ONGOING"
        started = self.now - timedelta(days=6 - idx)
        vid = self.run(
            """INSERT INTO votes
               (course_id, chapter_id, title, description, vote_mode, options, status,
                creator_id, started_at, closed_at, created_at, updated_at)
               VALUES (%s,NULL,%s,%s,'SINGLE_CHOICE',%s,%s,%s,%s,%s,%s,%s)""",
            (course_id, title, f"请选择你的真实感受：{title}", j(options), status, teacher_id,
             started, started + timedelta(hours=2) if status == "CLOSED" else None, started, started),
        )
        return {"id": vid}

    def ensure_vote_record(self, vote_id: int, student_id: int, choice: str) -> None:
        if not self.apply:
            self.bump("vote_records")
            return
        row = self.one("SELECT id FROM vote_records WHERE vote_id=%s AND student_id=%s", (vote_id, student_id))
        if row:
            return
        self.bump("vote_records")
        self.run(
            """INSERT INTO vote_records (vote_id, student_id, choice, text_answer, ip,
               user_agent, created_at, updated_at)
               VALUES (%s,%s,%s,NULL,NULL,NULL,%s,%s)""",
            (vote_id, student_id, j([choice]), self.now, self.now),
        )

    def seed_course(self, cfg: dict, template: dict, course: dict, rng: random.Random) -> None:
        course_id = int(course["id"])
        teacher_id = int(course["teacher_id"])
        class_row = self.one(
            "SELECT id FROM teaching_classes WHERE course_id=%s ORDER BY id LIMIT 1", (course_id,)
        )
        if not class_row:
            raise RuntimeError(f"课程 {course_id} 缺少教学班")
        class_id = int(class_row["id"])
        students = STUDENT_POOL[: int(cfg["students"])]
        for sid in students:
            self.ensure_enrollment(course_id, sid, class_id, teacher_id)

        existing_chapters = self.q(
            "SELECT id,title,order_index FROM course_chapters WHERE course_id=%s ORDER BY order_index,id",
            (course_id,),
        )
        chapters: list[dict] = list(existing_chapters)
        existing_titles = {x["title"] for x in chapters}
        if course_id == 1:
            wanted = list(cfg["extra_chapters"])
        elif course_id == 4178:
            wanted = []
        else:
            wanted = list(template["chapters"]) + list(cfg["extra_chapters"])
        for title in wanted:
            if title in existing_titles:
                continue
            chapter = self.ensure_chapter(course_id, title, len(chapters) + 1)
            chapters.append(chapter)
            existing_titles.add(title)
        if not chapters:
            raise RuntimeError(f"课程 {course_id} 没有可用章节")

        bank: list[dict] = []
        for i, item in enumerate(template["questions"]):
            chapter = chapters[i % len(chapters)]["title"]
            bank.append(self.ensure_question(course_id, teacher_id, item, chapter, rng))

        tasks: list[dict] = []
        for i, title in enumerate(template["tasks"]):
            chapter_id = chapters[i % len(chapters)]["id"]
            tasks.append(self.ensure_task(course_id, teacher_id, chapter_id, title, i))
        draft = self.ensure_task(
            course_id, teacher_id, chapters[0]["id"], "阶段复习与自测", len(tasks), status="DRAFT"
        )
        tasks.append(draft)

        self.ensure_announcement(
            course_id, class_id, teacher_id, "开课通知",
            f"《{course['name']}》已开课。请按章节顺序学习，关注作业截止时间与课堂活动安排。",
        )
        self.ensure_announcement(
            course_id, class_id, teacher_id, "阶段学习提醒",
            "本周请完成前两章学习与对应作业；有疑问可在讨论区发帖，教师会集中回复。",
        )

        for i, title in enumerate(template["signs"]):
            sign = self.ensure_sign_in(course_id, teacher_id, title, i)
            if sign["id"]:
                for sid in students[: max(8, int(len(students) * 0.6))]:
                    self.ensure_sign_record(sign["id"], sid, "PRESENT")
        for i, (title, options) in enumerate(template["votes"]):
            vote = self.ensure_vote(course_id, teacher_id, title, options, i)
            if vote["id"]:
                for sid in students[: max(8, int(len(students) * 0.5))]:
                    self.ensure_vote_record(vote["id"], sid, options[(sid + i) % len(options)])

        topics: list[dict] = []
        for i, (title, content) in enumerate(template["topics"]):
            topic = self.ensure_topic(course_id, class_id, teacher_id, title, content, i)
            topics.append(topic)
        for ti, topic in enumerate(topics):
            if not topic["id"]:
                continue
            for pi in range(3):
                sid = students[(ti + pi) % len(students)]
                self.ensure_post(
                    topic["id"], sid, "STUDENT",
                    f"关于「{template['topics'][ti][0]}」：我的理解是{topic['id'] % 7 + 1}，"
                    f"目前卡在 {template['chapters'][(ti + pi) % len(template['chapters'])]} 的细节上，求指点。",
                    pi + 1, self.now - timedelta(days=2, hours=pi), rng,
                )
            self.ensure_post(
                topic["id"], teacher_id, "TEACHER",
                f"这个问题很有代表性。先回到「{template['chapters'][ti % len(template['chapters'])]}」的定义，"
                "再结合课堂例题对照；如果还不清楚，下次课集中讲。",
                4, self.now - timedelta(days=1), rng,
            )
            self.run(
                """UPDATE discussion_topics SET reply_count=(SELECT COUNT(*) FROM discussion_posts
                   WHERE topic_id=%s), last_reply_at=(SELECT MAX(created_at) FROM discussion_posts
                   WHERE topic_id=%s) WHERE id=%s""",
                (topic["id"], topic["id"], topic["id"]),
            )

        papers = [
            self.ensure_paper(
                course_id, teacher_id, f"{course['name']} 期中测验", bank, 60,
                self.now - timedelta(days=1), self.now + timedelta(days=14), rng,
            ),
            self.ensure_paper(
                course_id, teacher_id, f"{course['name']} 单元自测", bank, 30,
                self.now - timedelta(days=1), self.now + timedelta(days=14), rng,
            ),
        ]
        # 4178 既有期中卷也纳入会话补数，避免只有 5 名学生有考试记录
        if course_id == 4178:
            existing = self.one(
                "SELECT id FROM exam_papers WHERE course_id=%s AND title='计算机网络期中考试'", (course_id,)
            )
            if existing:
                qs = self.q(
                    """SELECT q.id,q.type,q.correct_answers,eq.points FROM exam_questions eq
                       JOIN questions q ON q.id=eq.question_id
                       WHERE eq.exam_paper_id=%s ORDER BY eq.order_index""",
                    (existing["id"],),
                )
                papers.append({"id": existing["id"], "title": "计算机网络期中考试",
                               "questions": qs, "total_score": sum(float(x["points"]) for x in qs)})

        policy = self.ensure_grade_policy(course_id, teacher_id)
        components = policy["components"]
        if isinstance(components, str):
            components = json.loads(components)
        score_keys = [c["key"] for c in components]
        weights = {c["key"]: float(c["weight"]) for c in components}
        for sid in students:
            for key in score_keys:
                self.ensure_grade_item(
                    int(policy["id"]), sid, key, Decimal(str(rng.randint(70, 98))), teacher_id, class_id
                )
            total = Decimal("0")
            item_rows = self.q(
                "SELECT component_key,score FROM grade_items WHERE policy_id=%s AND student_id=%s",
                (policy["id"], sid),
            )
            scores = {x["component_key"]: Decimal(str(x["score"])) for x in item_rows}
            for key in score_keys:
                total += scores.get(key, Decimal("80")) * Decimal(str(weights.get(key, 0))) / Decimal("100")
            usual = scores.get("usual", scores.get("assignment", Decimal("80")))
            assignment = scores.get("assignment", usual)
            exam = scores.get("exam", scores.get("final", usual))
            self.run(
                """UPDATE course_enrollments SET final_score=%s, attendance_score=%s,
                   assignment_score=%s, exam_score=%s, progress=%s, total_study_time=%s,
                   updated_at=%s WHERE course_id=%s AND student_id=%s""",
                (total, usual, assignment, exam, rng.randint(45, 95), rng.randint(600, 3600),
                 self.now, course_id, sid),
            )

        all_tasks = self.q(
            "SELECT id,title,due_date,status FROM tasks WHERE course_id=%s ORDER BY id",
            (course_id,),
        )
        published_tasks = [x for x in all_tasks if x["status"] == "PUBLISHED"]
        for ti, task in enumerate(published_tasks):
            for si, sid in enumerate(students[: max(8, int(len(students) * 0.65))]):
                self.ensure_submission(task, sid, teacher_id, class_id, ti, rng)
        # 统一把“演示提交”的提交时间压到截止前，避免出现已过截止却 is_late=0 的矛盾。
        self.run(
            """UPDATE submissions s JOIN tasks t ON t.id=s.task_id
               SET s.submitted_at=LEAST(%s, t.due_date - INTERVAL 2 HOUR),
                   s.created_at=LEAST(%s, t.due_date - INTERVAL 2 HOUR),
                   s.graded_at=CASE WHEN s.status='GRADED'
                     THEN LEAST(%s, t.due_date - INTERVAL 2 HOUR) + INTERVAL 1 HOUR ELSE NULL END,
                   s.is_late=0, s.late_penalty=0, s.updated_at=%s
               WHERE t.course_id=%s AND s.content=%s""",
            (self.now, self.now, self.now, self.now, course_id, SEED_SUBMISSION_TEXT),
        )

        for paper in papers:
            if not paper["id"] or not paper["questions"]:
                continue
            total_possible = sum(float(x["points"]) for x in paper["questions"])
            for sid in students[: max(8, int(len(students) * 0.65))]:
                exists = self.one(
                    """SELECT id FROM exam_sessions WHERE exam_paper_id=%s AND student_id=%s
                       AND attempt_number=1""",
                    (paper["id"], sid),
                )
                if exists:
                    continue
                answers: dict[str, Any] = {}
                q_scores: dict[str, float] = {}
                for qi in paper["questions"]:
                    qid = int(qi["id"])
                    raw = qi["correct_answers"]
                    if isinstance(raw, str):
                        try:
                            raw = json.loads(raw)
                        except json.JSONDecodeError:
                            pass
                    correct = raw
                    if qi["type"] == "TRUE_FALSE":
                        correct_answer = bool(correct)
                        answer = correct_answer if rng.random() < 0.72 else not correct_answer
                    elif qi["type"] == "SINGLE_CHOICE":
                        correct_answer = str(correct)
                        answer = correct_answer if rng.random() < 0.72 else rng.choice(
                            [x for x in "ABCD" if x != correct_answer]
                        )
                    elif qi["type"] == "FILL_BLANK":
                        correct_answer = correct[0] if isinstance(correct, list) and correct else str(correct)
                        answer = correct_answer if rng.random() < 0.7 else "未作答"
                    else:
                        correct_answer = correct[0] if isinstance(correct, list) and correct else str(correct)
                        answer = correct_answer if rng.random() < 0.75 else "待补充"
                    answers[str(qid)] = answer
                    ok = (answer == correct_answer) or (qi["type"] == "ESSAY" and answer != "待补充")
                    q_scores[str(qid)] = float(qi["points"]) if ok else 0.0
                score = Decimal(str(round(sum(q_scores.values()), 2)))
                percentage = Decimal(str(round(float(score) / total_possible * 100, 2))) if total_possible else Decimal("0")
                started = paper.get("start_time") if isinstance(paper.get("start_time"), datetime) else self.now - timedelta(days=2)
                started = started + timedelta(minutes=rng.randint(5, 40))
                self.bump("exam_sessions")
                self.run(
                    """INSERT INTO exam_sessions
                       (exam_paper_id, student_id, status, attempt_number, start_time, submit_time,
                        time_spent, total_score, percentage, answers, question_scores,
                        question_comments, ip_address, user_agent, cheating_flags, question_order,
                        option_orders, created_at, updated_at, teaching_class_id)
                       VALUES (%s,%s,'GRADED',1,%s,%s,%s,%s,%s,%s,%s,NULL,NULL,NULL,NULL,NULL,NULL,%s,%s,%s)""",
                    (paper["id"], sid, started, started + timedelta(minutes=rng.randint(30, 80)),
                     rng.randint(1800, 4800), score, percentage, j(answers), j(q_scores),
                     started, started, class_id),
                )
        # 新课试卷的会话统一落在试卷时间窗内，避免“开考前已交卷”的矛盾。
        self.run(
            """UPDATE exam_sessions s JOIN exam_papers p ON p.id=s.exam_paper_id
               SET s.start_time=p.start_time + INTERVAL 20 MINUTE,
                   s.submit_time=p.start_time + INTERVAL 20 MINUTE + INTERVAL s.time_spent SECOND,
                   s.created_at=p.start_time + INTERVAL 20 MINUTE,
                   s.updated_at=p.start_time + INTERVAL 20 MINUTE + INTERVAL s.time_spent SECOND
               WHERE p.course_id=%s AND (p.title LIKE '%%测验%%' OR p.title LIKE '%%自测%%')""",
            (course_id,),
        )

        for chapter_index, chapter in enumerate(chapters[:2]):
            for sid in students:
                self.ensure_learning_record(
                    course_id, sid, int(chapter["id"]),
                    "COMPLETE" if chapter_index == 0 else "VIEW",
                    100 if chapter_index == 0 else 50,
                    rng.randint(600, 1800),
                )

        print(f"[seed] 课程 {course_id} {course['name']}：{len(students)} 学生 / "
              f"{len(chapters)} 章节 / {len(bank)} 题库 / {len(tasks)} 任务 / {len(papers)} 试卷 / "
              f"{len(topics)} 讨论")


def main() -> int:
    parser = argparse.ArgumentParser(description="本地开发库课程演示数据补数")
    parser.add_argument("--apply", action="store_true", help="实际写入；不加只打印计划")
    parser.add_argument("--course", type=int, action="append", help="只处理指定 course id，可重复")
    args = parser.parse_args()

    env = load_env()
    conn = connect(env)
    seeder = Seeder(conn, apply=args.apply)
    t_by_name = {x["name"]: x for x in TEMPLATES}
    courses = [c for c in COURSES if not args.course or c["id"] in args.course]
    course_rows = {int(x["id"]): x for x in seeder.q("SELECT id,name,code,teacher_id,status FROM courses")}
    skipped = [c["id"] for c in courses if c["id"] not in course_rows or "STRESS" in str(course_rows[c["id"]]["code"])]
    courses = [c for c in courses if c["id"] not in skipped]
    if skipped:
        print(f"[seed] 跳过：{skipped}")

    print("[seed] 计划课程：" + "，".join(f"{c['id']}({c['template']})" for c in courses))
    before = seeder.table_counts()
    print("[seed] 改前行数：" + "，".join(f"{k}={v}" for k, v in before.items()))
    if not args.apply:
        print("[seed] dry-run：未写入。加 --apply 执行。")
        return 0

    try:
        for cfg in courses:
            template = t_by_name.get(cfg["template"])
            if not template:
                raise RuntimeError(f"找不到模板：{cfg['template']}")
            seeder.seed_course(cfg, template, course_rows[cfg["id"]], random.Random(20261119 + cfg["id"]))
        conn.commit()
    except Exception:
        conn.rollback()
        raise

    after = seeder.table_counts()
    print("[seed] 本次新增：" + "，".join(f"{k}={v}" for k, v in sorted(seeder.counts.items())))
    print("[seed] 改后行数：" + "，".join(f"{k}={v}" for k, v in after.items()))
    print("[seed] 完成；未新增 resources（避免伪造不可访问文件）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
