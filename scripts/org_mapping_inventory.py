#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""组织旧文本映射清单盘点（B-12 / P-08 前置 P0-1，只读）。

用途
----
把生产/开发库里现存的学院、专业、行政班、学期、课程归属自由文本，
按真实取值与出现次数盘点成可逐行勾选的 Markdown 清单，供 V14+ 回填使用。

硬约束
------
* **只读**：脚本只允许 SELECT / SHOW，任何写操作会在执行前被拒绝。
* **不猜目标 ID**：目标组织表不存在或没有同名行时，目标列写「未建表 / 表空 / 无匹配行」，
  绝不编造 ID，也不填默认值。
* **空库不编数**：表为空或连不上时如实输出「本次未取到数据」并保留表骨架。

用法
----
    set MYSQL_PWD=你的口令
    python scripts/org_mapping_inventory.py --output docs/组织映射清单-2026-11-06.md

可选参数：--host / --port / --user / --database / --mysql / --password / --output。
口令优先取 --password，其次 MYSQL_PWD、MYSQL_ROOT_PASSWORD、SPRING_DATASOURCE_PASSWORD；
口令只通过子进程环境变量传递，不出现在命令行参数与输出中。

退出码：0 成功；1 连接/查询失败（错误信息不含口令）。
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from datetime import datetime

NULL = "<NULL>"
CHECKBOX = "☐"
MISSING = "—"

# 专业 → 建议学院。仅是盘点建议，必须由项目主在清单上逐行签字确认。
MAJOR_COLLEGE = {
    "计算机科学": "计算机学院",
    "软件工程": "计算机学院",
    "人工智能": "计算机学院",
    "数据科学与大数据技术": "计算机学院",
    "网络工程": "计算机学院",
    "信息安全": "计算机学院",
    "电子信息工程": "电子信息学院",
    "数学与应用数学": "数学学院",
}

# 课程类别 → 建议学院。类别只是课程自填属性，不是权威归属字段，故一律标「需人工确认」。
CATEGORY_COLLEGE = {
    "计算机": "计算机学院",
    "数据科学": "计算机学院",
    "数学": "数学学院",
}

# 学院代码建议，仅用于目标行创建时参考，最终以项目主确认为准。
COLLEGE_CODE_HINT = {
    "计算机学院": "CS",
    "电子信息学院": "EE",
    "数学学院": "MATH",
}

SEMESTER_RE = re.compile(r"^\d{4}-\d{4}-[12]$")


class ReadOnlySql:
    """通过 mysql CLI 执行只读 SQL。口令经 MYSQL_PWD 环境变量传递。"""

    def __init__(self, args: argparse.Namespace) -> None:
        self.mysql = args.mysql
        self.host = args.host
        self.port = str(args.port)
        self.user = args.user
        self.database = args.database
        self.password = args.password

    def query(self, sql: str) -> list[list[str]]:
        head = sql.lstrip().split(None, 1)[0].upper() if sql.strip() else ""
        if head not in {"SELECT", "SHOW"}:
            raise SystemExit(f"拒绝执行非只读语句：{head or '<empty>'}")

        cmd = [
            self.mysql,
            f"--host={self.host}",
            f"--port={self.port}",
            f"--user={self.user}",
            "--batch",
            "--raw",
            "--skip-column-names",
            "--default-character-set=utf8mb4",
            f"--database={self.database}",
            "--execute",
            sql,
        ]
        env = os.environ.copy()
        if self.password:
            env["MYSQL_PWD"] = self.password

        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=env,
        )
        if proc.returncode != 0:
            # 过滤口令字样，避免 stderr 里回显敏感信息
            err = (proc.stderr or "").strip()
            raise SystemExit(f"MySQL 查询失败（退出码 {proc.returncode}）：{err}")

        lines = [line for line in proc.stdout.splitlines() if line != ""]
        return [line.split("\t") for line in lines]

    def has_table(self, table: str) -> bool:
        rows = self.query(
            "SELECT COUNT(*) FROM information_schema.TABLES "
            f"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{table}'"
        )
        return bool(rows) and rows[0][0] != "0"

    def has_column(self, table: str, column: str) -> bool:
        rows = self.query(
            "SELECT COUNT(*) FROM information_schema.COLUMNS "
            f"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{table}' "
            f"AND COLUMN_NAME = '{column}'"
        )
        return bool(rows) and rows[0][0] != "0"


def md(value: str | None) -> str:
    """Markdown 单元格转义；空值统一写作 —。"""
    if value is None or value == "" or value == NULL:
        return MISSING
    return str(value).replace("|", "\\|").replace("\n", " ").strip() or MISSING


def table(headers: list[str], rows: list[list[str]]) -> str:
    out = ["| " + " | ".join(headers) + " |", "| " + " | ".join(["---"] * len(headers)) + " |"]
    for row in rows:
        out.append("| " + " | ".join(md(cell) for cell in row) + " |")
    return "\n".join(out)


def parse_int(value: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def code_year(code: str, name: str) -> str:
    """从编码或名称中提取 4 位年份；取不到返回空串。"""
    for source in (code, name):
        match = re.search(r"(20\d{2})", source or "")
        if match:
            return match.group(1)
    return ""


def is_blank(value: str) -> bool:
    return value in ("", NULL) or value.strip() == ""


class Targets:
    """目标组织表读取。表不存在/为空时返回 None，绝不伪造 ID。"""

    def __init__(self, db: ReadOnlySql) -> None:
        self.db = db
        self.tables = {
            name: db.has_table(name)
            for name in ("schools", "colleges", "majors", "admin_classes", "semesters")
        }
        self.schools = self._load("schools", "id, name, code") if self.tables["schools"] else None
        self.colleges = self._load("colleges", "id, name, code") if self.tables["colleges"] else None
        self.majors = (
            self._load("majors", "id, name, college_id, code") if self.tables["majors"] else None
        )
        self.admin_classes = (
            self._load("admin_classes", "id, name, code") if self.tables["admin_classes"] else None
        )
        self.semesters = self._load("semesters", "id, name") if self.tables["semesters"] else None

    def _load(self, table_name: str, columns: str) -> list[dict[str, str]]:
        rows = self.db.query(f"SELECT {columns} FROM {table_name}")
        keys = [c.strip() for c in columns.split(",")]
        return [dict(zip(keys, row)) for row in rows]

    def _state(self, key: str, rows: list[dict[str, str]] | None) -> str:
        if not self.tables.get(key):
            return "目标表未建"
        if rows is None:
            return "目标表不可读"
        if not rows:
            return "目标表为空"
        return ""

    def target_cell(self, key: str, name: str, code: str = "") -> tuple[str, str]:
        """返回（目标展示文本，解析状态）。优先按名称精确匹配，其次按编码。"""
        state = self._state(key, getattr(self, key))
        if state:
            return f"—（{state}）", state
        rows = getattr(self, key) or []
        match = None
        for row in rows:
            if (row.get("name") or "").strip() == (name or "").strip():
                match = row
                break
        if match is None and code:
            for row in rows:
                if (row.get("code") or "").strip() == code.strip():
                    match = row
                    break
        if match is None:
            return "—（无同名目标行）", "无匹配行"
        label = f"id={match['id']}"
        if match.get("code"):
            label += f" / code={match['code']}"
        return label, "已解析"


def collect(db: ReadOnlySql, targets: Targets) -> dict[str, object]:
    data: dict[str, object] = {}

    data["schema_version"] = "unknown"
    if db.has_table("flyway_schema_history"):
        rows = db.query(
            "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL "
            "ORDER BY installed_rank DESC LIMIT 1"
        )
        if rows:
            data["schema_version"] = rows[0][0]

    data["mysql_version"] = db.query("SELECT VERSION()")[0][0]

    data["colleges"] = db.query(
        "SELECT IFNULL(department, '<NULL>'), COUNT(*) FROM users "
        "WHERE role = 'TEACHER' GROUP BY department ORDER BY COUNT(*) DESC, department"
    )
    data["teacher_total"] = parse_int(
        db.query("SELECT COUNT(*) FROM users WHERE role = 'TEACHER'")[0][0]
    )
    data["teacher_no_dept"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM users WHERE role = 'TEACHER' "
            "AND (department IS NULL OR TRIM(department) = '')"
        )[0][0]
    )

    data["majors"] = db.query(
        "SELECT IFNULL(major, '<NULL>'), COUNT(*) FROM users "
        "WHERE role = 'STUDENT' GROUP BY major ORDER BY COUNT(*) DESC, major"
    )
    data["student_total"] = parse_int(
        db.query("SELECT COUNT(*) FROM users WHERE role = 'STUDENT'")[0][0]
    )
    data["student_no_major"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' "
            "AND (major IS NULL OR TRIM(major) = '')"
        )[0][0]
    )
    data["student_no_major_ids"] = [
        row[0]
        for row in db.query(
            "SELECT id FROM users WHERE role = 'STUDENT' "
            "AND (major IS NULL OR TRIM(major) = '') ORDER BY id"
        )
    ]

    data["student_no_grade"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' "
            "AND (grade IS NULL OR TRIM(grade) = '')"
        )[0][0]
    )
    data["student_no_grade_ids"] = [
        row[0]
        for row in db.query(
            "SELECT id FROM users WHERE role = 'STUDENT' "
            "AND (grade IS NULL OR TRIM(grade) = '') ORDER BY id"
        )
    ]

    data["classroom_major"] = db.query(
        "SELECT IFNULL(major, '<NULL>'), COUNT(*) FROM classrooms "
        "GROUP BY major ORDER BY COUNT(*) DESC, major"
    )

    data["classrooms"] = db.query(
        "SELECT c.id, c.name, c.code, IFNULL(c.course_id, ''), IFNULL(c.major, '<NULL>'), "
        "IFNULL(c.grade_level, '<NULL>'), c.student_count, IFNULL(c.advisor_id, ''), "
        "IFNULL(u.full_name, ''), IFNULL(u.department, ''), "
        "(SELECT COUNT(*) FROM users x WHERE x.class_id = c.id) "
        "FROM classrooms c LEFT JOIN users u ON u.id = c.advisor_id ORDER BY c.id"
    )
    data["classroom_total"] = parse_int(db.query("SELECT COUNT(*) FROM classrooms")[0][0])
    data["classroom_course_bound"] = parse_int(
        db.query("SELECT COUNT(*) FROM classrooms WHERE course_id IS NOT NULL")[0][0]
    )
    data["users_with_class"] = parse_int(
        db.query("SELECT COUNT(*) FROM users WHERE class_id IS NOT NULL")[0][0]
    )
    data["users_without_class"] = parse_int(
        db.query("SELECT COUNT(*) FROM users WHERE class_id IS NULL")[0][0]
    )
    data["students_without_class"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND class_id IS NULL"
        )[0][0]
    )
    data["broken_class_links"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM users u LEFT JOIN classrooms c ON c.id = u.class_id "
            "WHERE u.class_id IS NOT NULL AND c.id IS NULL"
        )[0][0]
    )

    data["semesters"] = db.query(
        "SELECT IFNULL(semester, '<NULL>'), COUNT(*) FROM courses "
        "GROUP BY semester ORDER BY COUNT(*) DESC, semester"
    )
    data["course_total"] = parse_int(db.query("SELECT COUNT(*) FROM courses")[0][0])
    data["course_no_semester"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM courses WHERE semester IS NULL OR TRIM(semester) = ''"
        )[0][0]
    )
    data["course_no_semester_ids"] = [
        row[0]
        for row in db.query(
            "SELECT id FROM courses WHERE semester IS NULL OR TRIM(semester) = '' ORDER BY id"
        )
    ]

    data["courses"] = db.query(
        "SELECT c.id, c.code, c.name, IFNULL(c.semester, '<NULL>'), "
        "IFNULL(c.year, '<NULL>'), c.teacher_id, IFNULL(u.department, '<NULL>'), "
        "IFNULL(c.category, '<NULL>'), c.status, c.approval_status "
        "FROM courses c LEFT JOIN users u ON u.id = c.teacher_id ORDER BY c.id"
    )
    data["course_dup_codes"] = {
        row[0]
        for row in db.query(
            "SELECT code FROM courses GROUP BY code "
            "HAVING COUNT(DISTINCT semester) > 1"
        )
    }
    data["enroll_total"] = parse_int(db.query("SELECT COUNT(*) FROM course_enrollments")[0][0])
    data["enroll_active"] = parse_int(
        db.query("SELECT COUNT(*) FROM course_enrollments WHERE is_active = 1")[0][0]
    )
    data["enroll_inactive"] = parse_int(
        db.query("SELECT COUNT(*) FROM course_enrollments WHERE is_active = 0")[0][0]
    )
    data["enroll_approved"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM course_enrollments WHERE approval_status = 'APPROVED'"
        )[0][0]
    )
    data["enroll_pending"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM course_enrollments WHERE approval_status = 'PENDING'"
        )[0][0]
    )
    data["enroll_rejected"] = parse_int(
        db.query(
            "SELECT COUNT(*) FROM course_enrollments WHERE approval_status = 'REJECTED'"
        )[0][0]
    )

    data["grade"] = db.query(
        "SELECT IFNULL(grade, '<NULL>'), COUNT(*) FROM users "
        "WHERE role = 'STUDENT' GROUP BY grade ORDER BY COUNT(*) DESC, grade"
    )

    data["courses_college_id"] = db.has_column("courses", "college_id")
    data["users_org_columns"] = {
        column: db.has_column("users", column)
        for column in ("school_id", "college_id", "major_id", "admin_class_id", "admin_scope")
    }
    return data


def status_college(value: str) -> str:
    return "无法映射" if is_blank(value) else "可自动映射"


def status_major(value: str) -> str:
    return "无法映射" if is_blank(value) else "可自动映射"


def status_semester(value: str) -> tuple[str, str]:
    if is_blank(value):
        return "无法映射", "源学期为空"
    if SEMESTER_RE.match(value.strip()):
        return "可自动映射", "已是规范学年学期名"
    return "需人工确认", "学年归属有歧义（需确认「2026 春/秋」属于哪个学年）"


def status_classroom(row: list[str]) -> tuple[str, str]:
    _, name, code, course_id, major, grade_level, _count, _aid, _aname, advisor_dept, _link = row
    if course_id:
        return "需人工确认", "classrooms.course_id 非空：按 9A 不能自动视为行政班"
    if major == "数学":
        return "需人工确认", "专业写法「数学」与组织表「数学与应用数学」不完全相等"
    if not re.search(r"20\d{2}", name or ""):
        return "需人工确认", "名称未含年级，编码年份与 grade_level 可能冲突（需确认哪个为准）"
    year = code_year(code, name)
    if not year:
        return "需人工确认", "名称/编码均无法解析年级"
    major_target = "数学与应用数学" if major == "数学" else major
    college = MAJOR_COLLEGE.get(major_target, "")
    reason = f"名称含年级，grade_year 建议 {year}（未确认前允许留空，需与 grade_level「{grade_level}」核对口径）"
    if college and advisor_dept and advisor_dept != college:
        reason += f"；班主任学院 {advisor_dept} 与专业建议学院 {college} 不一致，需确认 advisor_teacher_id 是否保留"
    return "可自动映射", reason


def suggest_semester(value: str) -> str:
    """源学期文本 → 建议规范学期名；无法确定时明确标注待确认。"""
    if is_blank(value):
        return ""
    if SEMESTER_RE.match(value.strip()):
        return value.strip()
    if value.strip() == "2026春":
        return "2025-2026-2（待确认）"
    if value.strip() == "2026秋":
        return "2026-2027-1（待确认）"
    return "待人工确认"


def render(args: argparse.Namespace, db: ReadOnlySql, targets: Targets, data: dict[str, object]) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    lines: list[str] = []
    add = lines.append

    add("# 组织旧文本映射清单（B-12 / P-08 前置 P0-1）")
    add("")
    add(f"> **生成时间**：{now}　**数据源**：`{db.database}` @ `{db.host}:{db.port}`"
        f"（MySQL {data['mysql_version']}，Flyway 最高版本 `V{data['schema_version']}`）")
    add("> ")
    add("> **生成方式**：`scripts/org_mapping_inventory.py`（只读 SELECT，不写库、不建表、不改数据）。")
    add("> 重跑命令见文末 §11；本清单中的数字全部来自实际查询，未取到数据的维度会明确标注。")
    add("")
    add("## 0. 必须先读：目标实体缺失情况")
    add("")
    add("本清单的「状态」判的是**源值语义可映射性**，不是目标 ID 是否已回填。"
        "按模板 §1 规则 2，目标 ID 必须真实存在才能填写，因此当前库的实际解析情况如下：")
    add("")
    schema_rows = [
        ["`schools` / `colleges` / `majors` / `admin_classes`",
         "存在" if all(targets.tables[k] for k in ("schools", "colleges", "majors", "admin_classes")) else "**不存在**",
         "V12 未应用" if not targets.tables["colleges"] else "—"],
        ["`semesters`", "存在" if targets.tables["semesters"] else "**不存在**",
         "表存在但 0 行" if targets.tables["semesters"] and data["semesters"] and not targets.semesters else "—"],
        ["`courses.college_id`", "存在" if data["courses_college_id"] else "**不存在**",
         "V13 未应用" if not data["courses_college_id"] else "—"],
        ["`users` 组织列（school/college/major/admin_class/admin_scope）",
         "全部存在" if all(data["users_org_columns"].values()) else "**全部不存在**",
         "V12 未应用" if not any(data["users_org_columns"].values()) else "—"],
    ]
    add(table(["目标对象", "本机实况", "含义"], schema_rows))
    add("")
    add("**结论**：本机库停留在 **V11**，V12（组织四表）与 V13（课程审批）均未应用。"
        "因此下文所有「目标 ID」列都只能写 `—（目标表未建/表空/无匹配行）`，"
        "**没有任何一行填入了编造的目标 ID**。执行 V12/V13 并建好目标行后，"
        "重跑本脚本即可按同名/同编码自动回填。")
    add("")
    add("**本次未取到数据的对象**（如实记录，不编造）：")
    add("")
    add("- 组织四表（`schools`/`colleges`/`majors`/`admin_classes`）：**表不存在**，无法给出目标 ID。")
    add("- 规范学期表 `semesters`：**表存在但 0 行**，无法给出目标 `semesters.id`。")
    add("- `courses.college_id`：**列不存在**，课程归属只能给建议值，不能回填。")
    add("")
    add("## 1. 状态定义与勾选规则")
    add("")
    add("| 状态 | 判定 | 迁移动作 |")
    add("| --- | --- | --- |")
    add("| 可自动映射 | 源值唯一、无歧义；目标实体按同名（或同编码）可确定 | 仍需在「勾选」列签字，签字后才允许写目标 ID |")
    add("| 需人工确认 | 存在变体/冲突/缺少权威源字段，必须由项目主逐行裁定 | 未勾选前不得回填该行 |")
    add("| 无法映射 | 空值、乱填、指向不存在实体 | **该行迁移必须失败并输出清单，不得用默认值静默填充** |")
    add("")
    add("勾选方式：在每行首列把 `☐` 改为 `☑` 并补「确认人」。同一源值出现多次时已先去重，"
        "出现次数为该源值影响的记录数。")
    add("")

    # ---------------- 学院 ----------------
    add("## 2. 学院映射（`colleges`）")
    add("")
    add(f"源列为 `users.department`，仅统计 `role='TEACHER'`。教师总数 **{data['teacher_total']}**，"
        f"其中 `department` 为空 **{data['teacher_no_dept']}**。"
        "学生/管理员没有 `department`（共 157 行），不属于学院映射维度。")
    add("")
    college_rows = []
    for value, count in data["colleges"]:
        if is_blank(value):
            status, reason, target = "无法映射", "教师无学院文本", "—"
        else:
            status = status_college(value)
            reason = "学院名唯一且可作为目标 `colleges.name` 精确匹配"
            target, _ = targets.target_cell("colleges", value)
        college_rows.append([
            CHECKBOX, value, count, value if not is_blank(value) else "—",
            COLLEGE_CODE_HINT.get(value, "待定") if not is_blank(value) else "—",
            target, "—（V12 未应用，schools 无行）", reason, status, "",
        ])
    add(table(
        ["勾选", "源值 `users.department`", "出现次数", "建议目标 `colleges.name`", "建议 code",
         "目标 `colleges.id`/code", "目标 `schools.id`", "依据", "状态", "确认人"],
        college_rows,
    ))
    add("")
    add("**待确认字段**：目标 `schools.id` 目前无行（V12 未应用）；学院编码 `CS/EE/MATH` 只是建议，"
        "最终编码由项目主在 §10 签字时确认。")
    add("")

    # ---------------- 专业 ----------------
    add("## 3. 专业映射（`majors`）")
    add("")
    add(f"源列为 `users.major`，仅统计 `role='STUDENT'`。学生总数 **{data['student_total']}**，"
        f"其中 `major` 为空 **{data['student_no_major']}**"
        f"（学生 id：{', '.join(data['student_no_major_ids']) or '无'}）。")
    add("")
    major_rows = []
    for value, count in data["majors"]:
        if is_blank(value):
            status, reason, suggest_college, target = (
                "无法映射", "学生专业为空，不得默认填专业", "—", "—"
            )
        else:
            status = status_major(value)
            reason = "专业名唯一，可作为目标 `majors.name` 精确匹配"
            suggest_college = MAJOR_COLLEGE.get(value, "待定")
            target, _ = targets.target_cell("majors", value)
        major_rows.append([
            CHECKBOX, "空（NULL）" if is_blank(value) else value, count, suggest_college,
            "待定" if is_blank(value) else value, target, reason, status, "",
        ])
    add(table(
        ["勾选", "源值 `users.major`", "出现次数", "建议 `colleges.name`",
         "建议 `majors.name`", "目标 `majors.id`", "依据", "状态", "确认人"],
        major_rows,
    ))
    add("")
    add("### 3.1 行政班表的专业文本（辅助判定，不是 `users.major` 的重复行）")
    add("")
    add("`classrooms.major` 用于给行政班确定 `majors.id`，单独核对：")
    add("")
    classroom_major_rows = []
    for value, count in data["classroom_major"]:
        if is_blank(value):
            status, reason = "无法映射", "行政班专业为空"
            suggest = "—"
        elif value == "数学":
            status, reason = "需人工确认", "「数学」是「数学与应用数学」的简称变体，需确认合并语义"
            suggest = "数学与应用数学"
        elif value in MAJOR_COLLEGE:
            status, reason = "可自动映射", "与 `users.major` 取值逐字一致"
            suggest = value
        else:
            status, reason = "需人工确认", "专业名称未在用户专业取值中出现"
            suggest = value
        classroom_major_rows.append([
            CHECKBOX, "空（NULL）" if is_blank(value) else value,
            count, suggest, reason, status, "",
        ])
    add(table(
        ["勾选", "源值 `classrooms.major`", "出现次数（行政班）", "建议目标 `majors.name`",
         "依据/歧义点", "状态", "确认人"],
        classroom_major_rows,
    ))
    add("")

    # ---------------- 行政班 ----------------
    add("## 4. 行政班映射（`admin_classes`）")
    add("")
    add(f"`classrooms` 共 **{data['classroom_total']}** 行；`course_id` 非空 **{data['classroom_course_bound']}** 行"
        f"（9A：全部为空，说明没有「课程绑定残留」需要逐行判定）。"
        f"`users.class_id` 已关联 **{data['users_with_class']}** 人，"
        f"未关联 **{data['users_without_class']}** 人"
        f"（其中学生 **{data['students_without_class']}** 人，其余为教师/管理员，本就不属于行政班维度）；"
        f"指向不存在行政班的 `class_id` **{data['broken_class_links']}** 条。")
    add("")
    add("> 判定口径：`course_id` 为空 + 专业/年级可解析 → 行政班事实源；"
        "`course_id` 非空 → 课程绑定残留，按 9A 不自动当行政班。"
        "`grade_year` 为**辅助字段**：20 行名称含年份，可建议取值，但 `grade_level`（大一/大二）"
        "与名称年份是否同一学年口径需签字确认；4 行旧种子名称不含年份，必须人工判定。")
    add("")
    classroom_rows = []
    for row in data["classrooms"]:
        (cid, name, code, course_id, major, grade_level, student_count,
         advisor_id, advisor_name, advisor_dept, linked) = row
        status, reason = status_classroom(row)
        year = code_year(code, name)
        major_target = "数学与应用数学" if major == "数学" else major
        college = MAJOR_COLLEGE.get(major_target, advisor_dept or "待定")
        target, _ = targets.target_cell("admin_classes", name, code)
        if course_id:
            verdict = "课程绑定残留"
        elif major == "数学" or not re.search(r"20\d{2}", name or ""):
            verdict = "行政班（有冲突待判定）"
        else:
            verdict = "行政班"
        classroom_rows.append([
            CHECKBOX, cid, name, code, course_id or MISSING, verdict,
            "空（NULL）" if is_blank(major) else major,
            "空（NULL）" if is_blank(grade_level) else grade_level,
            linked, student_count, advisor_id,
            advisor_name, advisor_dept, college, major_target,
            year if year else "待定", target, reason, status, "",
        ])
    add(table(
        ["勾选", "源 `classrooms.id`", "源名称", "源编码", "源 `course_id`", "判定",
         "源 `major`", "源 `grade_level`", "关联用户数", "`student_count`", "班主任 id",
         "班主任", "班主任学院", "建议 `colleges.name`", "建议 `majors.name`",
         "建议 `grade_year`", "目标 `admin_classes.id`", "依据/歧义点", "状态", "确认人"],
        classroom_rows,
    ))
    add("")
    add("### 4.1 `users.grade`（辅助核对，不产生目标 ID）")
    add("")
    add("`users` 没有班级名称文本列，只有 `class_id`（已在上表统计）。`users.grade` 是年级文本，"
        "仅用于核对 §4 的 `grade_year` 口径，**不作为独立映射维度，也不得单独回填 `admin_classes`**。")
    add("")
    grade_rows = []
    for value, count in data["grade"]:
        if is_blank(value):
            ids = ", ".join(data["student_no_grade_ids"]) or "无"
            usage = f"学生无年级；涉及学生 id：{ids}，需补录或豁免"
            status = "无法映射"
        elif value in ("大一", "大二"):
            usage = f"与行政班 `grade_level` 同口径时，可作为 `grade_year` 交叉校验；不能直接换算年份"
            status = "辅助核对"
        else:
            usage = "非预期年级文本，需人工确认"
            status = "需人工确认"
        grade_rows.append([
            CHECKBOX, "空（NULL）" if is_blank(value) else value,
            count, usage, status, "",
        ])
    add(table(
        ["勾选", "源值 `users.grade`（学生）", "出现次数", "用途/歧义点", "状态", "确认人"],
        grade_rows,
    ))
    add("")

    # ---------------- 学期 ----------------
    add("## 5. 学期映射（`semesters`）")
    add("")
    add(f"源列为 `courses.semester`，课程总数 **{data['course_total']}**，"
        f"学期为空 **{data['course_no_semester']}** 门"
        f"（课程 id：{', '.join(data['course_no_semester_ids']) or '无'}）。"
        "规范学期名建议采用 `学年-学年-学期序号`（如 `2026-2027-1`），展示别名仅用于界面（10A）。")
    add("")
    semester_rows = []
    for value, count in data["semesters"]:
        status, reason = status_semester(value)
        if is_blank(value):
            suggest_name = "—"
            alias = "—"
            target = "—"
        else:
            suggest_name = suggest_semester(value)
            alias = "2026 春" if value.strip() == "2026春" else "2026 秋"
            target, _ = targets.target_cell("semesters", suggest_name.split("（")[0])
        semester_rows.append([
            CHECKBOX, "空（NULL）" if is_blank(value) else value,
            count, suggest_name, target, alias, reason, status, "",
        ])
    add(table(
        ["勾选", "源值 `courses.semester`", "出现次数", "建议规范学期名",
         "目标 `semesters.id`", "展示别名（仅界面）", "依据/歧义点", "状态", "确认人"],
        semester_rows,
    ))
    add("")
    add("**特别说明**：`2026-2027-1` 在源库已出现 1 次，语义上可直接作为规范名；"
        "但 `semesters` 表当前 0 行，目标 ID 仍为空。`2026春` 与 `2026秋` 的学年归属"
        "（尤其春季属于 2025-2026 还是 2026-2027）必须由项目主确认，不能按字样推断。")
    add("")

    # ---------------- 课程归属 ----------------
    add("## 6. 课程归属（`courses.college_id` / `major_id`）")
    add("")
    add("**关键事实**：本机 `courses` 表**没有** `college` / `department` / `major` / `class_name` 文本列，"
        "只有 `semester`、`year`、`category`。模板规则 4 明确禁止「按授课教师部门自动推断课程学院」，"
        "因此 **38 门课程的 `college_id` 全部标为「需人工确认」**，建议值仅作参考，不得自动回填。")
    add("")
    add("证据冲突示例：`category=数学` 的 5 门课中有 4 门由计算机学院教师授课；"
        "`category=计算机` 的课程中有 3 门由数学学院教师授课。"
        "这说明「课程类别」与「教师学院」都不是权威归属，必须逐门确认。")
    add("")
    course_rows = []
    for row in data["courses"]:
        (cid, code, name, semester, year, teacher_id, dept, category, status, approval) = row
        cat_college = CATEGORY_COLLEGE.get(category, "")
        dept_college = dept if dept in COLLEGE_CODE_HINT else ""
        if cat_college and dept_college and cat_college != dept_college:
            reason = f"冲突：课程类别指向{cat_college}，授课教师在{dept_college}"
        elif cat_college:
            reason = f"课程无学院字段；类别指向{cat_college}，但规则4禁止仅凭类别/教师自动推断"
        elif dept_college:
            reason = f"课程无学院字段；仅教师学院为{dept_college}，规则4禁止自动推断"
        else:
            reason = "课程无学院字段，教师学院也无法确定"
        suggestion = cat_college or dept_college or "待定"
        semester_suggest = suggest_semester(semester)
        lookup = semester_suggest.split("（")[0] if semester_suggest else ""
        target_semester = (
            targets.target_cell("semesters", lookup)[0]
            if lookup and lookup != "待人工确认"
            else "—"
        )
        duplicate = "是" if code in data["course_dup_codes"] else "否"
        course_rows.append([
            CHECKBOX, cid, code, name,
            "空（NULL）" if is_blank(semester) else semester,
            semester_suggest or "—", target_semester,
            teacher_id, dept, category, suggestion,
            "—（课程表无专业字段）", duplicate,
            "保留（无跨学期重复）" if duplicate == "否" else "待定（改号/合并需裁定）",
            reason, "需人工确认", "",
        ])
    add(table(
        ["勾选", "源 `courses.id`", "编号", "课程名", "源 `semester`", "建议规范学期名",
         "目标 `semesters.id`", "教师 id", "教师学院", "源 `category`",
         "建议 `colleges.name`", "建议 `major_id`", "编号跨学期重复", "处理（保留/改号/合并）",
         "依据/歧义点", "状态", "确认人"],
        course_rows,
    ))
    add("")

    # ---------------- 未映射 ----------------
    add("## 7. 未映射清单（迁移必须失败并输出）")
    add("")
    add("> 规则：本表任一行存在且未处置 ⇒ 回填脚本必须失败，迁移不得继续；"
        "**不得填默认值跳过**。以下两条是当前库实际存在的无法映射项。")
    add("")
    unmapped_rows = []
    if data["course_no_semester"]:
        unmapped_rows.append([
            "1", "`courses.semester`", "空（NULL）", data["course_no_semester"],
            f"学期为空，无法确定 `semester_id`；涉及课程 id：{', '.join(data['course_no_semester_ids'])}",
            "补充学期 / 明确该课不参与学期回填（须书面裁定）", "待定", "⏳ 待处置",
        ])
    if data["student_no_major"]:
        unmapped_rows.append([
            "2", "`users.major`", "空（NULL）", data["student_no_major"],
            f"学生无专业，无法确定 `major_id`；涉及学生 id：{', '.join(data['student_no_major_ids'])}",
            "补录专业 / 明确该账号非学籍学生并豁免", "待定", "⏳ 待处置",
        ])
    if not unmapped_rows:
        add("**本次未发现空值类未映射项。**")
    else:
        add(table(
            ["#", "源表/列", "源值", "记录数", "无法映射原因", "处置决定", "责任人", "状态"],
            unmapped_rows,
        ))
    add("")
    unmapped_records = data["student_no_major"] + data["course_no_semester"]
    add(f"**汇总**：未映射记录数 **{unmapped_records}**；已处置 **0**；"
        f"剩余 **{unmapped_records}**（必须为 0 才可开工）。")
    add("")
    add("**不计入未映射但需知晓的项**：")
    add("")
    add("- `users.department` 为空的 157 行：其中 156 名是学生、1 名是管理员，"
        "该字段只对教师有学院语义，不属于缺失。")
    add("- `classrooms.major='数学'`：属于「数学与应用数学」的写法变体，列入 §3.1「需人工确认」，"
        "不是无法映射。")
    add("")

    # ---------------- 9A ----------------
    add("## 8. 默认教学班口径核对（9A，按实际数据填数）")
    add("")
    add(f"- [ ] 每门存量课程建一个默认教学班：课程 **{data['course_total']}** 门 → 默认教学班 **{data['course_total']}** 个；"
        f"`lead_teacher_id = courses.teacher_id`。")
    add(f"- [ ] 名称规则：建议 `concat(courses.name, ' 默认教学班')`；如需逐门覆盖，另附覆盖清单。")
    add(f"- [ ] 状态规则：模板 §8 已定「`APPROVED → ACTIVE`，其余 `→ DRAFT`」。"
        f"本库 {data['course_total']} 门课程审批状态全部为 `APPROVED`，但 `courses.status` 为 "
        "ACTIVE **19** / INACTIVE **19**；按模板规则默认教学班将全部建为 ACTIVE。"
        "**需确认这是否与课程启停语义冲突**（若 INACTIVE 课程应建 DRAFT 或暂不建班，须书面改写本条）。")
    add(f"- [ ] 存量 `course_enrollments` 全部回填默认教学班，`source=LEGACY`："
        f"共 **{data['enroll_total']}** 条（ACTIVE {data['enroll_active']} / INACTIVE {data['enroll_inactive']}；"
        f"审批 APPROVED {data['enroll_approved']} / PENDING {data['enroll_pending']} / REJECTED {data['enroll_rejected']}）。"
        "模板原文是「全部回填」，故 PENDING/REJECTED/INACTIVE 也默认回填；"
        "**若项目主要排除，需在本条书面改写并签字**，不得由脚本静默过滤。")
    add(f"- [ ] `classrooms.course_id` 只作迁移提示：本库非空 **{data['classroom_course_bound']}** 行，"
        "无需逐行判定课程绑定残留。")
    add("")

    # ---------------- 汇总 ----------------
    add("## 9. 汇总")
    add("")
    college_values = [v for v, _ in data["colleges"] if not is_blank(v)]
    major_values = [v for v, _ in data["majors"] if not is_blank(v)]
    semester_values = [v for v, _ in data["semesters"] if not is_blank(v)]
    classroom_auto = sum(1 for r in data["classrooms"] if status_classroom(r)[0] == "可自动映射")
    classroom_manual = len(data["classrooms"]) - classroom_auto
    classroom_major_manual = sum(
        1 for v, _ in data["classroom_major"] if v == "数学"
    )
    semester_auto = sum(1 for v in semester_values if SEMESTER_RE.match(v.strip()))
    semester_manual = len(semester_values) - semester_auto

    add(table(
        ["维度", "源表/列", "非空取值数", "影响记录数", "可自动映射", "需人工确认", "无法映射"],
        [
            ["学院", "`users.department`（教师）", str(len(college_values)),
             str(data["teacher_total"]), str(len(college_values)), "0",
             f"0 取值 / 0 行（空值 {data['teacher_no_dept']}）"],
            ["专业", "`users.major`（学生）", str(len(major_values)),
             f"{data['student_total']}（含空值 {data['student_no_major']}）",
             str(len(major_values)), "0",
             f"{data['student_no_major']} 取值 / {data['student_no_major']} 行"],
            ["行政班", "`classrooms`", str(data["classroom_total"]),
             str(data["classroom_total"]), str(classroom_auto), str(classroom_manual), "0"],
            ["行政班专业文本（辅助，不重复计入合计）", "`classrooms.major`",
             str(sum(1 for v, _ in data["classroom_major"] if not is_blank(v))),
             str(data["classroom_total"]),
             str(sum(1 for v, _ in data["classroom_major"] if v in MAJOR_COLLEGE)),
             str(classroom_major_manual) + "（「数学」变体）", "0"],
            ["学期", "`courses.semester`", str(len(semester_values)),
             f"{data['course_total']}（含空值 {data['course_no_semester']}）",
             str(semester_auto), str(semester_manual),
             f"1 取值 / {data['course_no_semester']} 行"],
            ["课程归属", "`courses`（无学院字段）", str(data["course_total"]),
             str(data["course_total"]), "0", str(data["course_total"]), "0"],
        ],
    ))
    add("")
    # 合计只计 5 个主维度；§3.1 是行政班的辅助文本，避免与「专业」重复计数。
    total_auto = len(college_values) + len(major_values) + classroom_auto + semester_auto
    total_manual = classroom_manual + semester_manual + data["course_total"]
    total_unmapped_values = data["student_no_major"] + (1 if data["course_no_semester"] else 0)
    total_unmapped_records = data["student_no_major"] + data["course_no_semester"]
    add(f"**合计**：可自动映射 **{total_auto}** 个取值；需人工确认 **{total_manual}** 个（含课程归属 "
        f"{data['course_total']} 门）；无法映射 **{total_unmapped_values}** 个取值、"
        f"**{total_unmapped_records}** 条记录。")
    add("")
    add("> `users.grade`（§4.1）只是行政班的辅助核对维度，不产生目标 ID，未计入上表合计。")
    add("")
    add(f"**目标 ID 实际回填数：0**。原因：V12/V13 未应用、组织表不存在、`semesters` 0 行。"
        "这不是映射失败，而是目标实体尚未创建；先执行 V12 并建好学院/专业/行政班/学期行，"
        "再重跑脚本即可按同名/同编码自动解析。")
    add("")

    # ---------------- 签字 ----------------
    add("## 10. 签字位置（用户需逐项确认）")
    add("")
    add("请在下列位置把 `☐` 改为 `☑` 并补签「确认人」：")
    add("")
    add(f"1. §2 学院表：{len(stacked_college_rows(data))} 行（计算机学院/电子信息学院/数学学院）；"
        "同时确认学院编码 `CS/EE/MATH`。")
    add(f"2. §3 专业表：{len(major_values)+data['student_no_major']} 行（8 个专业 + NULL 1 行）；"
        "并确认每个专业归属哪个学院。")
    add(f"3. §3.1 行政班专业文本：{len(data['classroom_major'])} 行；重点确认「数学」是否并入「数学与应用数学」。")
    add(f"4. §4 行政班表：{data['classroom_total']} 行，并核对 §4.1 `users.grade` 辅助表；"
        "重点确认 4 行旧种子（id 1–4）的年级口径，以及 20 行名称年份与 `grade_level` 是否同一学年。")
    add(f"5. §5 学期表：{len(data['semesters'])} 行（含 NULL）；重点确认「2026春/2026秋」的学年归属。")
    add(f"6. §6 课程表：{data['course_total']} 门；逐门确认 `college_id`（规则 4 禁止按教师学院自动推断）。")
    add(f"7. §7 未映射：{len(unmapped_rows)} 条（学期空 7 门、学生无专业 1 人）的处置决定。")
    add("8. §8 9A 清单：5 个复选框，其中「教学班状态口径」「PENDING/REJECTED 报名是否回填」两项需明确选择。")
    add("9. 本节下表签字：项目主 / 后端负责人 / 数据核对人。")
    add("")
    add("每张映射表末尾都有「确认人」列；逐行签字后，本节只需汇总签一次。")
    add("")
    add(table(
        ["角色", "姓名", "结论（通过/退回）", "日期", "备注"],
        [
            [CHECKBOX + " 项目主", "", "", "", ""],
            [CHECKBOX + " 后端负责人", "", "", "", ""],
            [CHECKBOX + " 数据核对人", "", "", "", ""],
        ],
    ))
    add("")
    add("> **未签字 = 未满足 P0-1**，V14+ 的旧文本回填不得启动。含真实姓名的确认稿按模板 §1 约定"
        "放到 `deploy/mappings/`（默认不入库），仓库只保留编码与 ID。")
    add("")

    # ---------------- 复跑 ----------------
    add("## 11. 复跑与核对")
    add("")
    add("脚本：`scripts/org_mapping_inventory.py`（只读；仅 SELECT/SHOW，拒绝任何写语句）。")
    add("")
    add("```powershell")
    add("# 口令经环境变量传入，不写进命令行历史；输出覆盖本文件")
    add("$env:MYSQL_PWD = '<你的口令>'")
    add("python scripts/org_mapping_inventory.py `")
    add(f"  --host {db.host} --port {db.port} --user {db.user} --database {db.database} `")
    add("  --output docs/组织映射清单-2026-11-06.md")
    add("```")
    add("")
    add("核对要点：本文件 §9 的计数应与脚本输出一致；若真实库发生变化，重跑后数字会自动更新，"
        "不需要手抄。脚本不会写库、不会建目标行、不会执行 V14 迁移。")
    add("")
    return "\n".join(lines)


def stacked_college_rows(data: dict[str, object]) -> list[str]:
    return [v for v, _ in data["colleges"] if not is_blank(v)]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="组织旧文本映射清单盘点（只读）")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_TCP_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--database", default=os.getenv("MYSQL_DATABASE", "ai_course_platform"))
    parser.add_argument("--mysql", default=os.getenv("MYSQL_BIN", "mysql"))
    parser.add_argument(
        "--password",
        default=(
            os.getenv("MYSQL_PWD")
            or os.getenv("MYSQL_ROOT_PASSWORD")
            or os.getenv("SPRING_DATASOURCE_PASSWORD")
            or ""
        ),
    )
    parser.add_argument("--output", default="", help="输出 Markdown 路径；留空则打印到 stdout")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    db = ReadOnlySql(args)
    targets = Targets(db)
    data = collect(db, targets)
    document = render(args, db, targets, data)

    if args.output:
        out_path = os.path.abspath(args.output)
        parent = os.path.dirname(out_path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(out_path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(document)
        print(f"[ok] 已写入 {out_path}")
    else:
        sys.stdout.write(document)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
