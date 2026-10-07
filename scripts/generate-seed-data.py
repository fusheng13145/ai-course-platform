# -*- coding: utf-8 -*-
"""大创·AI 智能课程平台 V5 种子数据生成引擎。

用法：python scripts/generate-seed-data.py
输出：backend/src/main/resources/db/seed-demo/V5__rich_seed_data.sql

约束：
  * 确定性随机（固定种子），重复生成结果逐字节一致；
  * 新 ID 全部使用独立号段（>=1000），不与 V1 种子或本地手工数据冲突；
  * 状态域遵循 V2-V4 规范化：courses ACTIVE/INACTIVE，tasks DRAFT/PUBLISHED/CLOSED，
    exam_papers DRAFT/PUBLISHED/ARCHIVED，questions.type 大写枚举；
  * grade_policies 受 UNIQUE(course_id) 结构性限制（每课 1 条，共 30 条封顶）；
  * 冗余列由计算保证一致：三维成绩/总评、试卷总分、监考 risk_score、讨论 reply_count 等。
"""

import datetime as dt
import json
import os
import random
from collections import defaultdict

from seed_data_templates import TEMPLATES

# 模板兼容：ESSAY 题省略 options 槽位（5 元组），统一补齐为 6 元组
for _t in TEMPLATES:
    _t["questions"] = [
        (q[0], q[1], None, q[2], q[3], q[4]) if len(q) == 5 else q
        for q in _t["questions"]
    ]

R = random.Random(20261005)
PWD = "$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq"  # 123456
NOW = dt.datetime(2026, 10, 5, 10, 0, 0)
SPRING_START = dt.datetime(2026, 2, 23, 8, 0, 0)   # 2026春 第1周周一
FALL_START = dt.datetime(2026, 9, 7, 8, 0, 0)      # 2026秋 第1周周一
SEM_S, SEM_F = "2026春", "2026秋"

class Band:
    def __init__(self, base, limit):
        self.cur, self.base, self.limit = base, base, limit
    def nxt(self):
        self.cur += 1
        if self.cur >= self.base + self.limit:
            raise RuntimeError(f"号段 {self.base} 溢出，请调大 limit")
        return self.cur

B = {
    "teacher": Band(1000, 900), "student": Band(2000, 900),
    "classroom": Band(3000, 900), "course": Band(4000, 900),
    "chapter": Band(5000, 14000), "kp": Band(20000, 9000),
    "kprel": Band(30000, 9000), "ckp": Band(40000, 9000),
    "resource": Band(50000, 9000), "resver": Band(60000, 19000),
    "task": Band(80000, 9000), "taskcompl": Band(90000, 19000),
    "submission": Band(110000, 9000), "plagchk": Band(120000, 9000),
    "plagmatch": Band(130000, 9000), "reminder": Band(140000, 9000),
    "paper": Band(150000, 9000), "question": Band(160000, 9000),
    "examq": Band(170000, 29000), "session": Band(200000, 19000),
    "wrong": Band(220000, 24000), "lp": Band(250000, 9000),
    "lpn": Band(260000, 19000), "lr": Band(280000, 19000),
    "note": Band(300000, 9000), "vlr": Band(310000, 19000),
    "vws": Band(330000, 24000), "notif": Band(360000, 9000),
    "rp": Band(370000, 9000), "sc": Band(380000, 9000),
    "fus": Band(390000, 9000), "oplog": Band(400000, 9000),
    "aic": Band(410000, 9000), "aim": Band(420000, 19000),
    "sign": Band(440000, 9000), "signrec": Band(450000, 24000),
    "vote": Band(480000, 9000), "voterec": Band(490000, 19000),
    "dt": Band(510000, 9000), "dp": Band(520000, 19000),
    "dl": Band(540000, 19000), "ps": Band(560000, 9000),
    "pe": Band(570000, 9000), "gp": Band(580000, 9000),
    "gi": Band(590000, 19000), "us": Band(610000, 9000),
    "ll": Band(620000, 9000), "vc": Band(630000, 9000),
    "enroll": Band(650000, 19000),
}

def J(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))

def esc(s):
    return s.replace("\\", "\\\\").replace("'", "''")

def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, dt.datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, (int, float)):
        return repr(v)
    return "'" + esc(str(v)) + "'"

def week_dt(sem, week, day=0, hh=8, mm=0):
    base = SPRING_START if sem == SEM_S else FALL_START
    return base + dt.timedelta(days=(week - 1) * 7 + day, hours=hh - 8, minutes=mm)

def r2(x):
    return round(x, 2)

# ─────────────────────────── 词库 ───────────────────────────

SUR = list("王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐冯于董萧程曹袁邓许傅沈曾彭吕苏卢蒋蔡贾丁魏薛")
GIVEN = ["伟","芳","娜","敏","静","磊","军","洋","勇","艳","杰","娟","涛","明","超","秀兰","霞","平","刚","桂英",
         "文","辉","雪","飞","鑫","宇","辰","浩然","子轩","欣怡","佳","晨曦","睿","嘉","琳","泽","航","瑶","可","乐"]
TS_NAMES = ["孙立群","钱木兰","周慕云","郑明远","冯唐律","陈景润之","褚晨光","卫玠文","蒋听雨","沈括刀","韩非声","杨观澜"]
MAJORS = [("CS","计算机科学","计算机学院"),("SE","软件工程","计算机学院"),("AI","人工智能","计算机学院"),
          ("DS","数据科学与大数据技术","计算机学院"),("NE","网络工程","电子信息学院"),
          ("IS","信息安全","电子信息学院"),("MATH","数学与应用数学","数学学院"),("EE","电子信息工程","电子信息学院")]
TITLES = ["教授","副教授","讲师","讲师","副教授","教授","讲师","副教授","教授","讲师","副教授","讲师"]
BIO = ["博士生导师，主讲程序设计类课程","系副主任，主讲软件工程课程","教研室主任，主讲数学基础课程","青年骨干教师，主讲计算机类课程"]
DEVICES = [("Windows PC","Chrome 138","Windows 11"),("MacBook Air","Safari 18","macOS 15"),
           ("ThinkPad","Edge 138","Windows 11"),("iPad","Safari 18","iPadOS 18"),
           ("Xiaomi Phone","Chrome Mobile","Android 15"),("Dell Desktop","Firefox 141","Ubuntu 24.04")]
UA = {"Chrome 138":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/138.0 Safari/537.36",
      "Safari 18":"Mozilla/5.0 (Macintosh; Intel Mac OS X 15) AppleWebKit/605.1.15 Safari/605.1.15",
      "Edge 138":"Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/126 Safari/537.36 Edg/138.0",
      "Chrome Mobile":"Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome Mobile Safari/537.36",
      "Firefox 141":"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:141.0) Gecko Firefox/141.0"}
QSCORE = {"SINGLE_CHOICE": 5.0, "MULTIPLE_CHOICE": 6.0, "TRUE_FALSE": 3.0, "FILL_BLANK": 4.0, "ESSAY": 10.0}

def ip_of(i):
    return f"172.18.{(i // 250) % 200 + 2}.{i % 250 + 2}"

def student_name(i):
    return SUR[i % 40] + GIVEN[(i * 7 + i // 40) % 40]

# ─────────────────────────── 1. 用户与班级 ───────────────────────────

teachers = []
for i in range(12):
    teachers.append(dict(id=B["teacher"].nxt(), username=f"ts{i+1:02d}", email=f"ts{i+1:02d}@aicourse.com",
                         name=TS_NAMES[i], dept=MAJORS[i % 8][2], title=TITLES[i],
                         phone=f"13817{i:04d}1", ip=ip_of(500 + i)))

STU_COUNT = 150
students = []
for i in range(STU_COUNT):
    cls_idx = i * 20 // STU_COUNT
    m = MAJORS[cls_idx % 8]
    grade_lvl = "大一" if cls_idx < 12 else "大二"
    year = "2025" if cls_idx < 12 else "2024"
    students.append(dict(id=B["student"].nxt(), username=f"st{i+1:03d}", email=f"st{i+1:03d}@stu.aicourse.com",
                         name=student_name(i), cls_idx=cls_idx, student_id=f"{year}{cls_idx+1:02d}{i%8+1:03d}",
                         major=m[1], grade=grade_lvl, phone=f"13958{i:04d}2", ip=ip_of(i),
                         dev=DEVICES[i % 6], abl=min(0.97, max(0.42, 0.55 + R.gauss(0.08, 0.14)))))

classrooms = []
for k in range(20):
    m = MAJORS[k % 8]
    lvl = "大一" if k < 12 else "大二"
    yr = "2025" if k < 12 else "2024"
    classrooms.append(dict(id=B["classroom"].nxt(), idx=k, name=f"{m[1]}{yr}级{k+1}班",
                           code=f"{m[0]}{yr[2:]}-{k+1:02d}", major=m[1], lvl=lvl,
                           advisor=teachers[k % 12]["id"],
                           cnt=sum(1 for s in students if s["cls_idx"] == k)))

# ─────────────────────────── 2. 课程班次 ───────────────────────────

courses = []
for ti in range(10):                      # 春学期：模板 0-9 × A/B 平行班（已结束）
    for sec, sfx, off in (("A", "A班", 0), ("B", "B班", 2)):
        tpl = TEMPLATES[ti]
        courses.append(dict(tpl=tpl, ti=ti, sem=SEM_S, status="INACTIVE", sec=sfx,
                            code=f"{tpl['code']}-2026S{sec}", teacher=teachers[ti % 12],
                            classes=[classrooms[(ti * 2 + off) % 20], classrooms[(ti * 2 + off + 1) % 20]],
                            enroll_type="APPROVAL" if ti % 3 == 1 else "FREE"))
for ti in range(10, 20):                  # 秋学期：模板 10-19 单班（进行中）
    tpl = TEMPLATES[ti]
    courses.append(dict(tpl=tpl, ti=ti, sem=SEM_F, status="ACTIVE", sec="1班",
                        code=f"{tpl['code']}-2026F", teacher=teachers[ti % 12],
                        classes=[classrooms[(ti * 2) % 20], classrooms[(ti * 2 + 1) % 20]],
                        enroll_type="APPROVAL" if ti % 3 == 0 else "FREE"))
for c in courses:
    c["id"] = B["course"].nxt()
    c["disp"] = f"{c['tpl']['name']}（{c['sec']}）"

# ─────────────────────────── 3. 章节 / 知识点 / 资源 ───────────────────────────

chapters, kps, kp_rels, ckps, resources, resvers = [], [], [], [], [], []
for c in courses:
    tpl, slug = c["tpl"], c["code"].lower().replace("-", "_")
    c["chapters"] = []
    for i, title in enumerate(tpl["chapters"]):
        ch = dict(id=B["chapter"].nxt(), course=c, title=title, idx=i,
                  dur=24 + i * 4, published=(c["sem"] == SEM_S or i < 3),
                  week=(1 + i * 3 if c["sem"] == SEM_S else 1 + i))
        c["chapters"].append(ch)
        chapters.append(ch)
    c["kps"] = []
    for i, (name, ptype, diff, desc) in enumerate(tpl["kps"]):
        kp = dict(id=B["kp"].nxt(), name=name, ptype=ptype, diff=diff, desc=desc,
                  course=c, idx=i, imp=R.randint(5, 10), est=R.randint(20, 60))
        c["kps"].append(kp)
        kps.append(kp)
    for i in range(len(c["kps"]) - 1):
        kp_rels.append(dict(id=B["kprel"].nxt(), s=c["kps"][i], t=c["kps"][i + 1],
                            rtype="prerequisite", st=r2(R.uniform(0.6, 1.0))))
        if i % 3 == 0:
            kp_rels.append(dict(id=B["kprel"].nxt(), s=c["kps"][i], t=c["kps"][-1],
                                rtype="related", st=r2(R.uniform(0.3, 0.7))))
    for i, kp in enumerate(c["kps"]):
        ckps.append(dict(id=B["ckp"].nxt(), ch=c["chapters"][i % 6], kp=kp))
    c["resources"] = []
    for i, ch in enumerate(c["chapters"]):
        up_t = week_dt(c["sem"], ch["week"], 1, 9)
        vid = dict(id=B["resource"].nxt(), course=c, ch=ch, name=f"{ch['title']}·课堂录像.mp4",
                   mime="video/mp4", kind="video", uploader=c["teacher"]["id"], vis=1,
                   url=f"/uploads/{slug}/ch{i+1}/lesson-{i+1}.mp4", t=up_t,
                   desc=f"第{i+1}章课堂录像：{ch['title']}", dur_s=(24 + i * 4) * 60)
        doc = dict(id=B["resource"].nxt(), course=c, ch=ch, name=f"{ch['title']}讲义.pdf",
                   mime="application/pdf", kind="doc", uploader=c["teacher"]["id"], vis=0 if i == 4 else 1,
                   url=f"/uploads/{slug}/ch{i+1}/slides-{i+1}.pdf", t=up_t,
                   desc=f"第{i+1}章讲义与例题", dur_s=None)
        c["resources"] += [vid, doc]
        resources += [vid, doc]
        for r in (vid, doc):
            nv = 1 + (r["id"] % 3)
            for v in range(1, nv + 1):
                resvers.append(dict(id=B["resver"].nxt(), res=r, num=v, cur=(v == nv),
                                    t=r["t"] + dt.timedelta(days=v * 3),
                                    size=(r["dur_s"] or 25_000_000) // 20 + v * 812_345))

# ─────────────────────────── 4. 题库 / 试卷 ───────────────────────────

questions = []
for c in courses:
    c["questions"] = []
    for i, (qt, content, opts, answers, diff, expl) in enumerate(c["tpl"]["questions"]):
        q = dict(id=B["question"].nxt(), course=c, idx=i, qt=qt, content=content, opts=opts,
                 answers=answers, diff=diff, expl=expl, score=QSCORE[qt], used=0,
                 crate=r2(min(0.95, max(0.15, 0.82 - {"easy": 0.0, "medium": 0.18, "hard": 0.34}[diff] + R.uniform(-0.06, 0.06)))))
        c["questions"].append(q)
        questions.append(q)

KIND_NAME = {"quiz1": "单元测验一", "quiz2": "单元测验二", "mid": "期中考试", "final": "期末考试", "makeup": "补考试卷"}
QIDX = {"quiz1": [0, 1, 2, 8, 9, 10], "quiz2": [3, 4, 5, 7, 10, 11],
        "mid": [0, 1, 2, 3, 5, 6, 8, 9, 10, 11], "final": list(range(12))}

papers, examqs = [], []
for c in courses:
    spring = c["sem"] == SEM_S
    c["papers"] = {}
    plan = ([("quiz1", 4), ("quiz2", 8), ("mid", 9), ("final", 17)] if spring
            else [("quiz1", 3), ("quiz2", 4), ("mid", 9), ("final", 18)])
    for kind, week in plan:
        qs = [c["questions"][i] for i in QIDX[kind]]
        start = week_dt(c["sem"], week, 5, 9)
        dur = 30 if kind.startswith("quiz") else (90 if kind == "mid" else 120)
        end = start + dt.timedelta(minutes=dur)
        st = "DRAFT" if (not spring and kind == "final") else "PUBLISHED"
        if not spring and kind == "quiz2":
            start, end = dt.datetime(2026, 10, 3, 9, 0), dt.datetime(2026, 10, 8, 23, 59)
        p = dict(id=B["paper"].nxt(), course=c, kind=kind, st=st, qs=qs,
                 title=f"{c['disp']}{KIND_NAME[kind]}", total=sum(q["score"] for q in qs),
                 dur=dur, start=start, end=end, creator=c["teacher"]["id"],
                 shuffle=(kind in ("mid", "final")))
        c["papers"][kind] = p
        papers.append(p)
        for oi, q in enumerate(qs):
            q["used"] += 1
            examqs.append(dict(id=B["examq"].nxt(), p=p, q=q, oi=oi, pts=q["score"]))
    if spring:
        qs = c["questions"][:8]
        start = week_dt(SEM_S, 18, 0, 14)
        p = dict(id=B["paper"].nxt(), course=c, kind="makeup", st="ARCHIVED", qs=qs,
                 title=f"{c['disp']}补考试卷", total=sum(q["score"] for q in qs), dur=90,
                 start=start, end=start + dt.timedelta(minutes=90), creator=c["teacher"]["id"], shuffle=True)
        c["papers"]["makeup"] = p
        papers.append(p)
        for oi, q in enumerate(qs):
            q["used"] += 1
            examqs.append(dict(id=B["examq"].nxt(), p=p, q=q, oi=oi, pts=q["score"]))

# ─────────────────────────── 5. 选课 ───────────────────────────

enrollments = []
student_courses = defaultdict(list)
for c in courses:
    c["enrolled"] = []
    for cls in c["classes"]:
        for s in students:
            if s["cls_idx"] != cls["idx"]:
                continue
            pend = rej = False
            if c["enroll_type"] == "APPROVAL":
                r_ = len(c["enrolled"]) % 13
                pend, rej = (r_ == 11), (r_ == 12)
            at = (dt.datetime(2026, 2, 16, 20, 0) if c["sem"] == SEM_S else dt.datetime(2026, 9, 3, 12, 0))
            e = dict(id=B["enroll"].nxt(), s=s, c=c,
                     st="PENDING" if pend else ("REJECTED" if rej else "APPROVED"),
                     at=at, final=None, att=None, hw=None, exam=None, prog=0.0, study=0)
            if e["st"] == "APPROVED":
                c["enrolled"].append(e)
                student_courses[s["id"]].append(e)
            enrollments.append(e)

spring_enr = defaultdict(list)
for e in enrollments:
    if e["st"] == "APPROVED" and e["c"]["sem"] == SEM_S:
        spring_enr[e["s"]["id"]].append(e)

# ─────────────────────────── 6. 签到 ───────────────────────────

sign_ins, sign_recs = [], []
SIGN_TYPES = ["NORMAL", "CODE", "GESTURE", "QR", "LOCATION"]
for c in courses:
    spring = c["sem"] == SEM_S
    weeks = [1, 5, 10, 15] if spring else [1, 2, 3, 4]
    c["signs"] = []
    items = list(zip(c["tpl"]["signs"], weeks))
    if not spring and c["id"] % 3 == 0:
        items.append(("第6周课堂签到", 6))
    for i, (title, w) in enumerate(items):
        typ = SIGN_TYPES[(c["id"] + i) % 5]
        if spring or w <= 3:
            st = "CLOSED"
        elif w == 4:
            st = "ONGOING"
        else:
            st = "PENDING"
        started = week_dt(c["sem"], w, 2, 10, 5)
        if st == "ONGOING":
            started = NOW - dt.timedelta(minutes=5)
        code = "%04d" % ((c["id"] * 7 + i) % 10000)
        sgn = dict(id=B["sign"].nxt(), course=c, ch=c["chapters"][min(i, 5)], title=title,
                   typ=typ, st=st, start=started, dur=15, code=code,
                   gesture="右上左下", closed=started + dt.timedelta(minutes=15) if st == "CLOSED" else None)
        c["signs"].append(sgn)
        sign_ins.append(sgn)
        if st == "PENDING":
            continue
        for e in c["enrolled"]:
            roll = R.random()
            if roll < 0.80:
                r_st, t_off = "SIGNED", R.randint(0, 9)
            elif roll < 0.90:
                r_st, t_off = "LATE", R.randint(16, 21)
            elif roll < 0.96:
                r_st, t_off = "ABSENT", None
            else:
                r_st, t_off = "LEAVE", None
            rec = dict(id=B["signrec"].nxt(), sgn=sgn, e=e, st=r_st,
                       t=(started + dt.timedelta(minutes=t_off)) if t_off is not None else None,
                       ip=ip_of(e["s"]["id"] + e["c"]["id"]), sub_code=None, sub_ges=None, lat=None, lng=None)
            if st in ("CLOSED", "ONGOING") and r_st in ("SIGNED", "LATE"):
                if typ in ("CODE", "QR"):
                    rec["sub_code"] = sgn["code"] if R.random() < 0.9 else f"{R.randint(0, 9999):04d}"
                if typ == "GESTURE":
                    rec["sub_ges"] = sgn["gesture"] if R.random() < 0.9 else "下左上右"
                if typ == "LOCATION":
                    rec["lat"] = r2(30.2660 + R.uniform(-0.002, 0.002))
                    rec["lng"] = r2(120.1210 + R.uniform(-0.002, 0.002))
            sign_recs.append(rec)
            e.setdefault("att_list", []).append({"SIGNED": 100.0, "LATE": 80.0, "LEAVE": 60.0, "ABSENT": 0.0}[r_st])

# ─────────────────────────── 7. 任务 / 提交 / 查重 / 催交 ───────────────────────────

tasks, submissions, task_compls = [], [], []
plag_checks, plag_matches, reminders = [], [], []
for c in courses:
    spring = c["sem"] == SEM_S
    tweeks = [3, 5, 8, 11, 14] if spring else [2, 4, 6, 8, 10]
    c["tasks"] = []
    for i, title in enumerate(c["tpl"]["tasks"]):
        ttype = "quiz" if i in (1, 3) else "assignment"
        due = week_dt(c["sem"], tweeks[i], 4, 23, 59)
        if spring or due <= NOW:
            st = "CLOSED"
        elif i == 4:
            st = "DRAFT"
        else:
            st = "PUBLISHED"
        ch = c["chapters"][min(i, 5)]
        doc = next((r for r in c["resources"] if r["ch"] is ch and r["kind"] == "doc"), None)
        tsk = dict(id=B["task"].nxt(), course=c, ch=ch, title=title, ttype=ttype, st=st,
                   pub=1 if st != "DRAFT" else 0, due=due, start=due - dt.timedelta(days=14),
                   att=doc["url"] if doc else None, maxs=100.0,
                   late_p=[None, 5.0, 15.0, None, 10.0][i], maxsub=[0, 3, 0, 2, 0][i])
        c["tasks"].append(tsk)
        tasks.append(tsk)
        if st == "DRAFT":
            continue
        for e in c["enrolled"]:
            if st == "PUBLISHED":
                submitted = R.random() < 0.30
            else:
                submitted = R.random() < (0.83 if ttype == "assignment" else 0.90)
            if not submitted:
                continue
            late = R.random() < 0.07
            if late:
                sub_t = due + dt.timedelta(hours=R.randint(2, 30))
            else:
                sub_t = due - dt.timedelta(days=R.randint(1, 6), hours=R.randint(1, 20))
            if sub_t > NOW:
                sub_t = NOW - dt.timedelta(hours=R.randint(1, 40))
            g_roll = R.random()
            if spring:
                sst = ("GRADED" if g_roll < 0.80 else "SUBMITTED" if g_roll < 0.93
                       else "RETURNED" if g_roll < 0.97 else "RESUBMITTED")
            else:
                sst = "GRADED" if (st == "CLOSED" and g_roll < 0.7) else "SUBMITTED"
            score = None
            if sst in ("GRADED", "RESUBMITTED"):
                score = r2(min(100.0, max(40.0, e["s"]["abl"] * 100 + R.gauss(4, 7))) - (6 if late else 0))
            sub = dict(id=B["submission"].nxt(), tsk=tsk, e=e, st=sst, tsub=sub_t, late=late,
                       pen=r2((2 + R.randint(0, 5)) * (tsk["late_p"] or 10.0) / 100 * 10) if late else 0.0,
                       score=score,
                       grader=c["teacher"]["id"] if sst in ("GRADED", "RETURNED", "RESUBMITTED") else None,
                       tgraded=sub_t + dt.timedelta(days=R.randint(1, 4)) if sst in ("GRADED", "RESUBMITTED") else None,
                       tret=sub_t + dt.timedelta(days=2) if sst == "RETURNED" else None)
            submissions.append(sub)
            if score is not None:
                e.setdefault("hw_list", []).append(score)
            if ttype == "quiz":
                tc_score = r2(max(0.0, min(100.0, e["s"]["abl"] * 100 + R.gauss(0, 8))))
                task_compls.append(dict(id=B["taskcompl"].nxt(), e=e, tsk=tsk, ch=ch, ctype="QUIZ",
                                        score=tc_score, completed=1, wd=None, lp=None, td=None, cat=sub_t))
                e.setdefault("hw_q", []).append(tc_score)
            else:
                task_compls.append(dict(id=B["taskcompl"].nxt(), e=e, tsk=tsk, ch=ch, ctype="ASSIGNMENT",
                                        score=score, completed=1 if sst in ("GRADED", "RESUBMITTED") else 0,
                                        wd=None, lp=None, td=None, cat=sub_t))
            if sst == "RETURNED":
                reminders.append(dict(id=B["reminder"].nxt(), typ="ASSIGNMENT_DUE", e=e, tsk=tsk, st="SENT",
                    at=sub_t, title=f"作业退回提醒：{tsk['title']}",
                    msg=f"你的作业《{tsk['title']}》已被退回，请修改后重新提交。", due=tsk["due"], trig=None,
                    sch=sub_t, sent=sub_t + dt.timedelta(minutes=3)))
            elif late:
                reminders.append(dict(id=B["reminder"].nxt(), typ="ASSIGNMENT_DUE", e=e, tsk=tsk, st="SENT",
                    at=sub_t, title=f"作业逾期提醒：{tsk['title']}",
                    msg=f"《{tsk['title']}》已逾期提交，按规定扣分 {sub['pen']} 分。", due=tsk["due"], trig=None,
                    sch=sub_t, sent=sub_t + dt.timedelta(minutes=1)))

for c in courses:
    for tsk in c["tasks"]:
        if tsk["st"] != "PUBLISHED":
            continue
        for e in c["enrolled"][:9]:
            if R.random() >= 0.55:
                continue
            sched = min(tsk["due"] - dt.timedelta(days=1), NOW + dt.timedelta(days=R.randint(1, 9)))
            if sched < NOW:
                sched = NOW + dt.timedelta(hours=R.randint(2, 48))
            reminders.append(dict(id=B["reminder"].nxt(), typ="ASSIGNMENT_DUE", e=e, tsk=tsk, st="PENDING",
                at=sched, title=f"作业即将截止：{tsk['title']}",
                msg=f"《{tsk['title']}》将于 {tsk['due']:%m月%d日 %H:%M} 截止，请尽快提交。",
                due=tsk["due"], trig=1, sch=sched, sent=None))

for sub in submissions:
    if sub["tsk"]["ttype"] != "assignment" or sub["st"] not in ("GRADED", "RESUBMITTED"):
        continue
    if sub["id"] % 5 != 2:
        continue
    sim = r2(4 + R.random() * 20) if sub["id"] % 3 else r2(38 + R.random() * 40)
    hi = 0 if sim < 30 else R.randint(1, 4)
    at = sub["tgraded"] + dt.timedelta(minutes=R.randint(5, 60))
    chk = dict(id=B["plagchk"].nxt(), sub=sub, sim=sim, hi=hi, at=at)
    plag_checks.append(chk)
    if sim >= 30:
        peers = [x for x in submissions if x["tsk"] is sub["tsk"] and x["id"] != sub["id"]]
        for _ in range(min(hi, len(peers))):
            p = R.choice(peers)
            plag_matches.append(dict(id=B["plagmatch"].nxt(), chk=chk, ms=p,
                                     sim=r2(min(95.0, sim + R.uniform(-4, 12))),
                                     ot="核心实现段：for i in range(n): total += a[i] * w[i]",
                                     mt="核心实现段：for j in range(n): s += arr[j] * wt[j]（变量名替换）"))

# ─────────────────────────── 8. 考试会话 / 错题 / 监考 ───────────────────────────

sessions, wrongs, proctor_sess, proctor_ev = [], [], [], []
wrong_pairs = set()
for c in courses:
    spring = c["sem"] == SEM_S
    for kind, paper in c["papers"].items():
        if kind == "makeup" or paper["st"] == "DRAFT":
            continue
        ongoing = (not spring and kind == "quiz2" and paper["start"] <= NOW <= paper["end"])
        for e in c["enrolled"]:
            s = e["s"]
            if ongoing:
                took = (s["id"] % 5 == 1) and (s["id"] % 3 != 0)
            else:
                took = R.random() < (0.94 if spring else 0.80)
            if not took:
                continue
            start = paper["start"] + dt.timedelta(minutes=R.randint(0, 25))
            spent = int(paper["dur"] * 60 * R.uniform(0.55, 0.95))
            ans, qscores, tot, wrongs_this = {}, {}, 0.0, []
            for q in paper["qs"]:
                if ongoing and q["idx"] > 2:
                    continue
                p_ok = min(0.97, max(0.05, s["abl"] + {"easy": 0.12, "medium": 0.0, "hard": -0.18}[q["diff"]]))
                ok = R.random() < p_ok
                if q["qt"] in ("SINGLE_CHOICE", "TRUE_FALSE"):
                    correct = q["answers"][0]
                    given = correct if ok else R.choice([o for o in "ABCD"[:len(q["opts"])] if o != correct])
                elif q["qt"] == "MULTIPLE_CHOICE":
                    given = q["answers"] if ok else sorted(R.sample("ABCD"[:len(q["opts"])], 2))
                elif q["qt"] == "FILL_BLANK":
                    given = q["answers"][0] if ok else "（记不清了）"
                else:
                    given = (f"要点：{q['answers'][0]}；其余展开见答题纸。" if ok
                             else "时间不够，只写了第一条要点。")
                qid = str(q["id"])
                ans[qid] = given
                earned = q["score"] if ok else (r2(q["score"] * 0.4) if q["qt"] == "ESSAY" and R.random() < 0.35 else 0.0)
                qscores[qid] = r2(earned)
                tot += earned
                if earned == 0:
                    wrongs_this.append(q)
            if ongoing:
                st, sub_t, tst, tot_v, pct = "STARTED", None, None, None, None
                qs_v = None
            else:
                roll = R.random()
                st = ("GRADED" if roll < 0.86 else "SUBMITTED" if roll < 0.93
                      else "AUTO_SUBMITTED" if roll < 0.98 else "FORCE_SUBMITTED")
                sub_t = start + dt.timedelta(seconds=spent)
                tst = sub_t + dt.timedelta(minutes=R.randint(3, 40)) if st == "GRADED" else None
                tot_v = r2(tot)
                graded = st in ("GRADED", "AUTO_SUBMITTED", "FORCE_SUBMITTED")
                pct = r2(tot / paper["total"] * 100) if graded else None
                qs_v = J(qscores) if graded else None
                if pct is not None:
                    e.setdefault("exam_list", []).append(pct)
            ses = dict(id=B["session"].nxt(), paper=paper, e=e, st=st, start=start, sub=sub_t,
                       spent=None if ongoing else spent, tot=tot_v, pct=pct, ans=ans,
                       qs=qs_v, graded=(st in ("GRADED", "AUTO_SUBMITTED", "FORCE_SUBMITTED")),
                       tst=tst, flags=None, qorder=None, oorders=None,
                       ip=s["ip"], ua=UA[s["dev"][1]])
            if paper["shuffle"] and not ongoing:
                order = [q["id"] for q in paper["qs"]]
                R.shuffle(order)
                ses["qorder"] = J(order)
                ses["oorders"] = J({str(q["id"]): sorted(range(len(q["opts"])), key=lambda _: R.random())
                                    for q in paper["qs"] if q["opts"]})
            sessions.append(ses)
            if ses["graded"]:
                for q in wrongs_this[:3]:
                    key = (s["id"], q["id"])
                    if key in wrong_pairs:
                        continue
                    wrong_pairs.add(key)
                    mastered = R.random() < 0.35
                    wrongs.append(dict(id=B["wrong"].nxt(), s=s, q=q, c=c,
                        wa=str(ans.get(str(q["id"]), "略")),
                        mastered=1 if mastered else 0,
                        pcnt=R.randint(1, 3) if mastered else R.randint(0, 1),
                        lat=(sub_t + dt.timedelta(days=R.randint(1, 5))) if mastered else None,
                        src="exam" if kind in ("mid", "final") else "quiz",
                        t=sub_t + dt.timedelta(hours=1)))

for ses in sessions:
    c = ses["e"]["c"]
    if c["sem"] != SEM_S or ses["e"]["s"]["id"] % 9 != 3 or ses["st"] == "STARTED":
        continue
    evts = []
    for _ in range(R.randint(1, 4)):
        typ = R.choice(["TAB_SWITCH", "FACE_MISSING", "MULTIPLE_FACE", "MOBILE_DETECTED", "COPY_PASTE", "FULLSCREEN_EXIT"])
        sev = (R.choice(["LOW", "MEDIUM", "HIGH"]) if typ in ("MULTIPLE_FACE", "COPY_PASTE", "MOBILE_DETECTED")
               else R.choice(["LOW", "LOW", "MEDIUM"]))
        evts.append(dict(id=B["pe"].nxt(), typ=typ, sev=sev,
                         at=ses["start"] + dt.timedelta(minutes=R.randint(2, max(3, (ses["spent"] or 900) // 60))),
                         reviewed=R.random() < 0.6,
                         note=R.choice(["经复核为正常切屏查资料，予以豁免。", "确认违规，已通报。", None])))
    risk = sum({"LOW": 1, "MEDIUM": 2, "HIGH": 3}[x["sev"]] for x in evts)
    lvl = "HIGH" if risk >= 6 else ("MEDIUM" if risk >= 3 else "LOW")
    proctor_sess.append(dict(id=B["ps"].nxt(), ses=ses, evs=evts, risk=risk, lvl=lvl,
                             start=ses["start"], end=ses["sub"]))
    ses["flags"] = {"detectedAt": ses["start"].strftime("%Y-%m-%dT%H:%M:%S"), "flagCount": len(evts),
                    "riskLevel": lvl,
                    "details": [{"type": x["typ"], "severity": x["sev"],
                                 "at": x["at"].strftime("%Y-%m-%dT%H:%M:%S")} for x in evts]}

# ─────────────────────────── 9. 学习行为记录 ───────────────────────────

learning_recs, vlr, vws_l, notes = [], [], [], []
for c in courses:
    spring = c["sem"] == SEM_S
    vids = [r for r in c["resources"] if r["kind"] == "video"]
    docs = [r for r in c["resources"] if r["kind"] == "doc"]
    n_watch = 6 if spring else 3
    for e in c["enrolled"]:
        s = e["s"]
        for r in vids[:n_watch]:
            prog = r2(R.uniform(55, 100) if spring else R.uniform(20, 90))
            watch = int(r["dur_s"] * prog / 100)
            e["study"] += watch
            last = min(r["t"] + dt.timedelta(days=R.randint(3, 30)), NOW - dt.timedelta(hours=R.randint(1, 20)))
            vlr.append(dict(id=B["vlr"].nxt(), s=s, r=r, c=c, dur=watch, prog=prog,
                            lp=int(r["dur_s"] * prog / 100), cc=1 if prog >= 99 else 0, last=last))
            for k in range(2 if spring else 1):
                st0 = last - dt.timedelta(days=k, hours=R.randint(1, 8))
                d0 = R.randint(300, max(310, watch // 2))
                vws_l.append(dict(id=B["vws"].nxt(), s=s, r=r, sp=k * 400, ep=k * 400 + d0, dur=d0,
                                  st0=st0, en0=st0 + dt.timedelta(seconds=d0 + R.randint(0, 120))))
        for r in docs[:4]:
            act = "download" if R.random() < 0.5 else "view"
            learning_recs.append(dict(id=B["lr"].nxt(), s=s, r=r, ch=r["ch"], c=c, act=act,
                                      dur=R.randint(60, 900), prog=r2(R.uniform(10, 100)),
                                      t=min(r["t"] + dt.timedelta(days=R.randint(1, 40)), NOW)))
        for ch in c["chapters"][:3 if spring else 2]:
            learning_recs.append(dict(id=B["lr"].nxt(), s=s, r=None, ch=ch, c=c, act="view",
                                      dur=R.randint(120, 1800), prog=r2(R.uniform(30, 100)),
                                      t=min(week_dt(c["sem"], ch["week"], R.randint(0, 4), R.randint(8, 22)), NOW)))
        if R.random() < 0.5:
            ch = R.choice(c["chapters"][:3 if spring else 2])
            kp1, kp2 = R.sample(c["kps"], 2)
            notes.append(dict(id=B["note"].nxt(), s=s, c=c, ch=ch, kp=kp1,
                title=f"{ch['title']}复习笔记",
                body=(f"## {ch['title']}\n- 核心：{kp1['name']}——{kp1['desc']}\n"
                      f"- 待巩固：{kp2['name']}\n- 例题见讲义第 {ch['idx']+1} 节。"),
                fav=R.random() < 0.25,
                t=min(week_dt(c["sem"], ch["week"] + 1, 3, 21), NOW)))

# ─────────────────────────── 10. 学习路径 / 投票 / 讨论 ───────────────────────────

lps, lpns = [], []
for s in students[:120]:
    elist = spring_enr.get(s["id"]) or []
    if not elist:
        continue
    c = elist[0]["c"]
    stt = "COMPLETED" if s["id"] % 3 == 0 else ("IN_PROGRESS" if s["id"] % 4 else "DRAFT")
    cur = {"COMPLETED": 5, "IN_PROGRESS": R.randint(1, 4), "DRAFT": 0}[stt]
    rate = 100.0 if stt == "COMPLETED" else (min(95.0, r2(cur / 5 * 100 * R.uniform(0.85, 1.1))) if cur else 0.0)
    kps_c = c["kps"][:5]
    lp = dict(id=B["lp"].nxt(), s=s, c=c, name=f"{c['tpl']['name']}个性化学习路径", st=stt,
              cur=cur, total=5, rate=rate, goal=f"四周内掌握{c['tpl']['name']}核心考点",
              stages=[{"name": f"阶段{i+1}：{kp['name']}", "estimatedDays": 7} for i, kp in enumerate(kps_c)],
              start=week_dt(c["sem"], 2, 0, 20),
              acd=week_dt(c["sem"], 14, 0, 20) if stt == "COMPLETED" else None)
    lps.append(lp)
    for i, kp in enumerate(kps_c):
        node_st = "COMPLETED" if i < cur else ("IN_PROGRESS" if i == cur else "PENDING")
        lpns.append(dict(id=B["lpn"].nxt(), lp=lp, kp=kp, ch=c["chapters"][i % 6],
                         title=kp["name"], ntype="study" if i < 3 else "practice",
                         st=node_st, oi=i, est=kp["est"],
                         act=kp["est"] + R.randint(-8, 20) if node_st == "COMPLETED" else 0,
                         cat=week_dt(c["sem"], 3 + i, 5, 18) if node_st == "COMPLETED" else None))

votes_l, vote_recs = [], []
for c in courses:
    spring = c["sem"] == SEM_S
    items = list(c["tpl"]["votes"])
    if c["id"] % 2 == 0:
        items.append(("教学反馈问卷", ["内容充实", "节奏合适", "希望讲慢一点", "希望增加实战"]))
    for i, (title, opts) in enumerate(items):
        mode = "SINGLE_CHOICE" if i != 2 else "MULTIPLE_CHOICE"
        st = "CLOSED" if (spring or i < 2) else ("ONGOING" if i == 2 else "PENDING")
        start = week_dt(c["sem"], 2 + i * 4, 2, 10, 10)
        if st == "ONGOING":
            start = NOW - dt.timedelta(minutes=R.randint(30, 600))
        v = dict(id=B["vote"].nxt(), course=c, ch=c["chapters"][min(i, 5)], title=title, mode=mode,
                 st=st, opts=[{"id": chr(ord("a") + j), "text": o} for j, o in enumerate(opts)],
                 start=start, closed=start + dt.timedelta(minutes=10) if st == "CLOSED" else None)
        votes_l.append(v)
        if st == "PENDING":
            continue
        for e in c["enrolled"]:
            if R.random() > 0.66:
                continue
            pick = R.sample([o["id"] for o in v["opts"]], 2 if mode == "MULTIPLE_CHOICE" else 1)
            vote_recs.append(dict(id=B["voterec"].nxt(), v=v, e=e,
                                  choice=pick[0] if mode == "SINGLE_CHOICE" else pick,
                                  t=min(v["start"] + dt.timedelta(minutes=R.randint(1, 8)), NOW),
                                  ip=ip_of(e["s"]["id"])))

disc_topics, disc_posts, disc_likes = [], [], []
POST_BODY = ["我也有同样的疑问，蹲一个解答。", "建议先跑通例题再回看讲义，效果会好很多。",
             "老师课上讲过的，第{n}章讲义里有完整推导。", "补充一点：边界情况容易忽略，测试要覆盖到。",
             "楼主的问题我也遇到过，后来发现是环境配置问题。", "已解决，谢谢大家！结帖留档。",
             "这个坑经典了，考试大概率会考，标记一下。", "同意楼上，实践一遍胜过看十遍。"]
REPLY_BODY = ["正解，受教了。", "第二小问还有疑问，能再展开吗？", "+1，同问。",
              "这里其实还要考虑时间复杂度。", "老师已在上面楼层回复。"]
for c in courses:
    for i, (title, body) in enumerate(c["tpl"]["topics"]):
        by_teacher = (i == 3)
        creator = c["teacher"] if by_teacher else (R.choice(c["enrolled"])["s"] if c["enrolled"] else c["teacher"])
        role = "TEACHER" if by_teacher else "STUDENT"
        start = week_dt(c["sem"], 2 + i * 3, R.randint(0, 4), R.randint(9, 22))
        t = dict(id=B["dt"].nxt(), course=c, ch=c["chapters"][i % 6] if i % 2 == 0 else None,
                 title=title, body=body, typ="QA" if (by_teacher or i == 2) else "NORMAL",
                 st="OPEN" if (c["id"] + i) % 8 else "CLOSED",
                 creator=creator, role=role, pin=1 if by_teacher else 0,
                 ess=1 if (i == 0 and not by_teacher) else 0,
                 start=min(start, NOW - dt.timedelta(hours=R.randint(1, 48))))
        posts, kids, floor = [], [], 0
        for j in range(R.randint(3, 6)):
            floor += 1
            pe = R.choice(c["enrolled"])["s"] if c["enrolled"] else creator
            author = c["teacher"] if (by_teacher and j == 2) else pe
            p = dict(id=B["dp"].nxt(), topic=t, parent=None, author=author,
                     arole=("TEACHER" if author is c["teacher"] else "STUDENT"),
                     body=R.choice(POST_BODY).format(n=i + 1), likes=R.randint(0, 6), floor=floor,
                     at=min(t["start"] + dt.timedelta(hours=R.randint(1, 96)), NOW),
                     mentions=[str(creator["id"])] if j == 3 else None)
            posts.append(p)
        for p in posts[:2]:
            for _ in range(R.randint(1, 2)):
                floor += 1
                pe = R.choice(c["enrolled"])["s"] if c["enrolled"] else creator
                kids.append(dict(id=B["dp"].nxt(), topic=t, parent=p, author=pe, arole="STUDENT",
                                 body=R.choice(REPLY_BODY), likes=R.randint(0, 3), floor=floor,
                                 at=min(p["at"] + dt.timedelta(hours=R.randint(2, 40)), NOW), mentions=None))
        allt = posts + kids
        t["rc"] = len(allt)
        t["lra"] = max(p["at"] for p in allt)
        disc_topics.append(t)
        disc_posts += allt
        for p in allt:
            if not c["enrolled"]:
                continue
            likers = R.sample([e["s"] for e in c["enrolled"]], min(p["likes"], len(c["enrolled"])))
            for u in likers:
                disc_likes.append(dict(id=B["dl"].nxt(), p=p, u=u, t=min(p["at"] + dt.timedelta(minutes=R.randint(5, 800)), NOW)))

# ─────────────────────────── 11. 通知 / AI / 系统表 ───────────────────────────

notifications = []
for s in students:
    notifications.append(dict(u=s, title="欢迎加入AI智能课程平台",
        content=f"{s['name']}同学，选课、作业、考试与AI学习功能已就绪，祝你学习顺利！",
        cat="system", typ="SYSTEM", read=1, pr="normal", url="/student/dashboard",
        t=week_dt(SEM_S, 1, R.randint(0, 3), R.randint(9, 20))))
for c in courses:
    if c["sem"] == SEM_S:
        for e in c["enrolled"][:12]:
            notifications.append(dict(u=e["s"], title=f"成绩已发布：{c['disp']}",
                content=f"{c['disp']}本学期总评成绩已发布，请前往成绩页查看。",
                cat="course", typ="GRADE", read=1 if e["s"]["id"] % 3 == 0 else 0, pr="normal",
                url=f"/student/courses/{c['id']}/grade", t=week_dt(SEM_S, 17, 3, 16)))
    else:
        for e in c["enrolled"][:8]:
            notifications.append(dict(u=e["s"], title=f"考试提醒：{c['disp']}单元测验二",
                content="测验窗口为 10月3日 09:00 - 10月8日 23:59，请及时参加。",
                cat="course", typ="EXAM", read=0, pr="high",
                url=f"/student/courses/{c['id']}/exams", t=dt.datetime(2026, 10, 2, 18, 0)))

ai_sess, ai_msgs = [], []
AI_ANS = "结合本课程讲义，可以这样理解：先掌握概念定义，再看一个最小可运行示例，最后做 2-3 道对应习题巩固。若仍有疑问，欢迎在讨论区发帖，同学和老师都会来解答。"
for s in students:
    elist = spring_enr.get(s["id"]) or []
    c = elist[0]["c"] if elist else courses[s["cls_idx"] % 30]
    for k in range(R.randint(1, 2)):
        sid = f"seed-{s['id']}-{c['id']}-{k}"
        msgs = []
        n = R.randint(3, 5)
        for m in range(n):
            kp = R.choice(c["kps"])
            if m % 2 == 0:
                msgs.append(dict(role="user", content=f"{c['tpl']['name']}里的「{kp['name']}」怎么学？（{kp['desc']}）",
                                 t=week_dt(c["sem"], 2 + m, m % 5, R.randint(8, 22))))
            else:
                msgs.append(dict(role="assistant", content=AI_ANS, t=msgs[-1]["t"] + dt.timedelta(seconds=42)))
        ai_sess.append(dict(id=B["aic"].nxt(), sid=sid, s=s, title=f"{c['tpl']['name']}学习问答",
                            n=len(msgs), t=msgs[0]["t"]))
        for m in msgs:
            ai_msgs.append(dict(id=B["aim"].nxt(), sid=sid, role=m["role"], body=m["content"], t=m["t"]))

sys_new_configs = [
    ("site_footer", "© 2026 AI智能课程平台 · 大创项目", "basic", "STRING", "页脚版权文案", 1),
    ("site_icp", "教学演示，暂无备案号", "basic", "STRING", "备案信息", 1),
    ("ui.theme", "light", "ui", "STRING", "默认主题", 1),
    ("ui.sidebar_collapsed", "false", "ui", "BOOLEAN", "侧边栏默认是否折叠", 1),
    ("ui.page_size", "20", "ui", "NUMBER", "列表默认分页大小", 1),
    ("ui.home_carousel", "6", "ui", "NUMBER", "首页轮播课程数", 1),
    ("feature.rag_enabled", "true", "ai", "BOOLEAN", "是否启用 RAG 契约服务（8002）", 0),
    ("feature.kg_enabled", "true", "ai", "BOOLEAN", "是否启用知识图谱服务（8001）", 0),
    ("rag.top_k", "5", "ai", "NUMBER", "RAG 检索片段数", 0),
    ("rag.chunk_size", "512", "ai", "NUMBER", "文档切片长度", 0),
    ("kg.layout.iterations", "300", "ai", "NUMBER", "力导向布局迭代数", 0),
    ("deepseek.model", "deepseek-chat", "ai", "STRING", "DeepSeek 对话模型", 0),
    ("deepseek.temperature", "0.3", "ai", "NUMBER", "采样温度", 0),
    ("deepseek.max_tokens", "2048", "ai", "NUMBER", "最大输出 token", 0),
    ("exam.auto_save_interval", "30", "exam", "NUMBER", "答卷自动保存间隔（秒）", 0),
    ("exam.anti_cheat_enabled", "true", "exam", "BOOLEAN", "是否开启在线监考", 0),
    ("exam.auto_submit_on_timeout", "true", "exam", "BOOLEAN", "超时自动交卷", 0),
    ("exam.min_duration_seconds", "300", "exam", "NUMBER", "最短作答时长限制", 0),
    ("grading.default_weights", "10,30,60", "grading", "STRING", "默认 考勤/作业/考试 权重", 0),
    ("grading.late_penalty_default", "10", "grading", "NUMBER", "默认逾期扣分(%)", 0),
    ("grading.unscored_exclude_denominator", "true", "grading", "BOOLEAN", "未评分项剔除总评分母", 0),
    ("notification.email_enabled", "false", "notification", "BOOLEAN", "邮件通道开关", 0),
    ("notification.daily_digest", "true", "notification", "BOOLEAN", "每日学习摘要", 1),
    ("notification.exam_remind_days", "1", "notification", "NUMBER", "考试提前提醒天数", 0),
    ("storage.allowed_video_ext", "mp4,mov,webm", "storage", "STRING", "允许的视频扩展名", 0),
    ("storage.allowed_doc_ext", "pdf,docx,pptx,xlsx,md", "storage", "STRING", "允许的文档扩展名", 0),
    ("storage.chunk_size_mb", "8", "storage", "NUMBER", "分片上传大小(MB)", 0),
    ("storage.session_expire_minutes", "60", "storage", "NUMBER", "上传会话有效期", 0),
    ("security.password_min_len", "8", "security", "NUMBER", "密码最小长度", 0),
    ("security.session_ttl_hours", "12", "security", "NUMBER", "登录会话有效期（小时）", 0),
    ("security.login_rate_limit", "10/60s", "security", "STRING", "登录速率限制", 0),
    ("security.account_lock_minutes", "15", "security", "NUMBER", "账号锁定时长", 0),
    ("sentinel.dashboard_enabled", "true", "system", "BOOLEAN", "Sentinel 控制台开关", 0),
    ("scheduler.data_maint_cron", "0 3 * * *", "system", "STRING", "数据维护定时任务", 0),
    ("scheduler.exam_timeout_cron", "*/5 * * * *", "system", "STRING", "考试超时扫描", 0),
]
for gi in range(24):
    sys_new_configs.append((f"ui.widget_{gi+1}_visible", "true", "ui", "BOOLEAN", f"工作台组件 {gi+1} 显隐", 1))
for gi in range(18):
    sys_new_configs.append((f"ai.prompt.template_{gi+1}", f"提示词模板 {gi+1} 占位", "ai", "STRING", f"AI 提示词模板 {gi+1}", 0))
for c in courses:
    sys_new_configs.append((f"kg.course_style.{c['code'].lower()}",
                            J({"color": f"hsl({c['id'] % 360},65%,55%)", "layout": "force"}),
                            "ai", "JSON", f"{c['disp']} 图谱配色", 0))

RP_KEYS = [
    ("course:create", "创建课程", "/courses/create"), ("course:edit", "编辑课程", None),
    ("course:delete", "删除课程", None), ("course:publish", "发布课程章节", None),
    ("task:create", "创建任务", None), ("task:edit", "编辑任务", None),
    ("task:delete", "删除任务", None), ("task:publish", "发布任务", None),
    ("submission:export", "导出提交", None), ("exam:create", "创建试卷", None),
    ("exam:edit", "编辑试卷", None), ("exam:publish", "发布试卷", None),
    ("exam:force_submit", "强制交卷", None), ("question:create", "新增题目", None),
    ("question:import", "批量导入题目", None), ("question:delete", "删除题目", None),
    ("grade:entry", "录入成绩", None), ("grade:export", "导出成绩", None),
    ("grade:policy:edit", "维护成绩方案", None), ("kg:manage", "知识图谱管理", "/admin/kg"),
    ("rag:manage", "RAG 知识库管理", "/admin/rag"), ("user:import", "批量导入用户", None),
    ("user:disable", "禁用账号", None), ("role:assign", "分配角色", None),
    ("config:edit", "修改系统配置", None), ("log:export", "导出操作日志", None),
    ("enroll:approve", "审批选课", None), ("enroll:reject", "驳回选课", None),
    ("proctor:review", "监考复核", None), ("plagiarism:run", "发起查重", None),
    ("discussion:pin", "置顶话题", None), ("discussion:delete", "删除讨论", None),
    ("sign:launch", "发起签到", None), ("vote:launch", "发起投票", None),
    ("notification:send", "群发通知", None), ("backup:run", "执行备份", None),
    ("ai:chat", "AI 对话", None), ("learning:path:generate", "生成学习路径", None),
    ("wrong:practice", "错题练习", None), ("note:manage", "笔记管理", None),
    ("course:clone", "复制课程", None), ("task:clone", "复制任务", None),
    ("question:export", "导出题库", None), ("question:rate:view", "题目正确率查看", None),
    ("resource:delete", "删除资源", None), ("resource:rollback", "回滚资源版本", None),
    ("discussion:essence", "标记精华帖", None), ("discussion:close", "关闭话题", None),
    ("sign:close", "结束签到", None), ("vote:close", "结束投票", None),
    ("vote:export", "导出投票结果", None), ("proctor:export", "导出监考报告", None),
    ("ai:feedback:regen", "重新生成 AI 反馈", None), ("note:export", "导出笔记", None),
    ("enroll:export", "导出选课名单", None), ("analytics:export", "导出数据分析", None),
    ("exam:review:config", "考后查卷配置", None), ("submission:return", "退回提交", None),
    ("kg:export", "导出图谱", None), ("rag:reindex", "重建索引", None),
]
ADMIN_KEYS = [k for k, _, _ in RP_KEYS]
TEACHER_KEYS = ["course:create", "course:edit", "course:publish", "course:clone",
                "task:create", "task:edit", "task:publish", "task:clone",
                "submission:export", "submission:return", "exam:create", "exam:edit", "exam:publish",
                "exam:force_submit", "exam:review:config",
                "question:create", "question:import", "question:export", "question:rate:view",
                "grade:entry", "grade:export", "grade:policy:edit",
                "kg:manage", "kg:export", "resource:delete", "resource:rollback",
                "proctor:review", "proctor:export", "plagiarism:run",
                "discussion:pin", "discussion:essence", "discussion:close",
                "sign:launch", "sign:close", "vote:launch", "vote:close", "vote:export",
                "enroll:approve", "enroll:export", "analytics:export",
                "notification:send", "ai:chat", "ai:feedback:regen",
                "learning:path:generate", "note:manage"]
STUDENT_KEYS = ["ai:chat", "learning:path:generate", "wrong:practice", "note:manage", "note:export",
                "question:rate:view", "discussion:close", "vote:export"]
role_perm_rows = []
for role, keys in (("ADMIN", ADMIN_KEYS), ("TEACHER", TEACHER_KEYS), ("STUDENT", STUDENT_KEYS)):
    for so, k in enumerate(keys, start=1):
        nm, path = next((n, p) for kk, n, p in RP_KEYS if kk == k)
        role_perm_rows.append(dict(id=B["rp"].nxt(), role=role, key=k, nm=nm, path=path, so=so))

op_actions = ["CREATE", "UPDATE", "DELETE", "STATE_CHANGE", "IMPORT", "EXPORT", "EXECUTE", "SEND"]
op_modules = ["课程管理", "作业管理", "考试管理", "题库管理", "成绩管理", "用户管理", "审批管理", "系统治理", "知识图谱", "资源管理"]
op_targets = {
    "课程管理": ["发布课程", "更新课程信息", "停用课程", "审批开课申请"],
    "作业管理": ["发布作业", "批改提交", "退回作业", "导出作业名单"],
    "考试管理": ["发布试卷", "监考复核", "强制交卷", "导出成绩单"],
    "题库管理": ["导入题目", "编辑题目", "下架题目"],
    "成绩管理": ["维护成绩方案", "录入成绩明细", "发布总评"],
    "用户管理": ["重置密码", "禁用账号", "批量导入用户"],
    "审批管理": ["审批选课", "审批开课"],
    "系统治理": ["修改系统配置", "查看操作日志", "清理过期数据"],
    "知识图谱": ["重建图谱索引", "同步知识点"],
    "资源管理": ["上传资源", "回滚资源版本"],
}
op_logs = []
all_staff = list(teachers) + [dict(id=1, username="admin", name="系统管理员", ip=ip_of(1))]
for i in range(300):
    u = R.choice(all_staff)
    mod = R.choice(op_modules)
    fail = R.random() < 0.06
    op_logs.append(dict(id=B["oplog"].nxt(), u=u, mod=mod, act=R.choice(op_actions),
        tgt=R.choice(op_targets[mod]), tid=str(4000 + (i * 37) % 30),
        ip=u["ip"], ua=UA[R.choice(list(UA))], m=R.choice(["POST", "PUT", "DELETE", "GET"]),
        dur=R.randint(5, 850), st="FAILED" if fail else "SUCCESS",
        err=R.choice(["参数校验失败: dueDate 不能为空", "目标对象不存在", "CSRF 校验失败", "权限不足"]) if fail else None,
        t=NOW - dt.timedelta(days=R.randint(0, 210), hours=R.randint(0, 12), minutes=R.randint(0, 59))))

fus = []
FNAME = ["第一章课件.pdf", "实验指导书.docx", "课堂录像-ch3.mp4", "期中试卷扫描件.pdf", "成绩导入表.xlsx", "课程封面.png"]
for i in range(120):
    uploader = R.choice(list(teachers) + students[:40])
    total = R.randint(3, 240)
    if i == 7:
        st, up_n = "UPLOADING", total // 2
    elif i % 9 in (3, 8):
        st, up_n = "FAILED", 0
    else:
        st, up_n = "COMPLETED", total
    created = (NOW - dt.timedelta(minutes=25) if st == "UPLOADING"
               else NOW - dt.timedelta(days=R.randint(0, 90), hours=R.randint(0, 20)))
    fus.append(dict(id=B["fus"].nxt(), fn=f"{created:%m%d}-{i:03d}-{R.choice(FNAME)}",
                    size=R.randint(200_000, 400_000_000), total=total, up=up_n, st=st,
                    uid=uploader["id"], path=f"/tmp/upload/{i:03d}",
                    exp=created + dt.timedelta(minutes=60), t=created))

user_sessions = []
for u in list(teachers) + students[:140]:
    login_t = NOW - dt.timedelta(days=R.randint(1, 120), hours=R.randint(0, 23))
    dev = DEVICES[u["id"] % 6]
    user_sessions.append(dict(id=B["us"].nxt(), uid=u["id"],
        tok="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.seed." + "%032x" % ((u["id"] * 2654435761) % (1 << 128)),
        rf="rt-" + "%016x" % ((u["id"] * 40503) % (1 << 64)), dev=dev[0], br=dev[1], osn=dev[2],
        ip=ip_of(u["id"]), login=login_t, last=login_t + dt.timedelta(minutes=R.randint(20, 300)),
        exp=login_t + dt.timedelta(hours=12)))

login_logs = []
pool = list(students) + list(teachers)
for i in range(250):
    u = R.choice(pool)
    ok = R.random() < 0.9
    dev = DEVICES[u["id"] % 6]
    login_logs.append(dict(id=B["ll"].nxt(), uid=u["id"], un=u["username"],
        st="SUCCESS" if ok else "FAILURE", ip=ip_of(u["id"] + i),
        dev=dev[0], br=dev[1], osn=dev[2],
        reason=None if ok else R.choice(["密码错误", "验证码错误", "账号已锁定", "用户不存在"]),
        t=NOW - dt.timedelta(days=R.randint(0, 180), hours=R.randint(0, 23), minutes=R.randint(0, 59))))

v_codes = []
for i in range(120):
    u = R.choice(students)
    t = NOW - dt.timedelta(days=R.randint(0, 200))
    v_codes.append(dict(id=B["vc"].nxt(), em=u["email"], ph=u["phone"] if i % 4 == 0 else None,
        code=f"{(u['id'] * 977 + i * 31) % 1000000:06d}",
        typ=R.choice(["REGISTER", "LOGIN", "RESET_PASSWORD"]),
        used=0 if i % 5 == 4 else 1, exp=t + dt.timedelta(minutes=10), ip=ip_of(u["id"]), t=t))

# ─────────────────────────── 12. 成绩方案与三维成绩回填 ───────────────────────────

COMPS = [{"key": "SIGN_IN", "name": "出勤", "weight": 10, "source": "sign_in"},
         {"key": "HOMEWORK", "name": "作业", "weight": 30, "source": "submission"},
         {"key": "EXAM", "name": "考试", "weight": 60, "source": "exam"}]
grade_policies, grade_items = [], []
for c in courses:
    c["policy"] = B["gp"].nxt()
    grade_policies.append(dict(id=c["policy"], c=c,
        name=f"{c['disp']}成绩方案（考勤10/作业30/考试60）", t=week_dt(c["sem"], 1, 0, 9)))
for e in enrollments:
    if e["st"] != "APPROVED":
        continue
    c = e["c"]
    spring = c["sem"] == SEM_S
    att = r2(sum(e.get("att_list", [])) / len(e["att_list"])) if e.get("att_list") else (r2(R.uniform(60, 100)) if spring else 100.0)
    hw_src = list(e.get("hw_list", [])) + list(e.get("hw_q", []))
    hw = r2(sum(hw_src) / len(hw_src)) if hw_src else (r2(R.uniform(55, 95)) if spring else None)
    exam = r2(sum(e["exam_list"]) / len(e["exam_list"])) if e.get("exam_list") else None
    e["att"], e["hw"], e["exam"] = att, hw, exam
    if spring and exam is not None:
        e["final"] = r2(0.1 * att + 0.3 * hw + 0.6 * exam)
    n_att, n_hw, n_ex = len(e.get("att_list", [])), len(e.get("hw_list", [])), len(e.get("exam_list", []))
    for key, val, remark in (("SIGN_IN", att, f"{n_att} 次考勤均分"),
                             ("HOMEWORK", hw, f"{n_hw} 次作业/测验均分"),
                             ("EXAM", exam, f"{n_ex} 场测验考试均分")):
        if val is None:
            continue
        grade_items.append(dict(id=B["gi"].nxt(), pol=c["policy"], e=e, key=key, score=val,
                                raw=None, remark=remark, grader=c["teacher"]["id"],
                                t=NOW - dt.timedelta(days=R.randint(1, 40))))

for e in enrollments:
    if e["st"] != "APPROVED":
        e["prog"] = 0.0
        continue
    c = e["c"]
    if e["final"] is not None:
        e["prog"] = 100.0
    else:
        e["prog"] = r2(R.uniform(35, 75))

# ─────────────────────────── SQL 输出 ───────────────────────────

HEADER = """-- ====================================================================
-- V5：富种子数据（大创演示 · 2026 春/秋两学期全链路业务数据）
-- ====================================================================
-- 生成：python scripts/generate-seed-data.py（确定性随机，可整文件重生成）
-- 规模：12 教师 / 150 学生 / 20 班级 / 30 课程班次（2026春 20 个已结束班 +
--       2026秋 10 个进行中班），覆盖全部 48 张表；除下列结构性上限外，
--       每张表新增 >=100 行：
--   * grade_policies：UNIQUE(course_id) 限制，每课 1 套（30 条封顶）；
--   * classrooms / courses：行政班与排课颗粒上限（20 / 30）。
-- 号段：全部新 ID >=1000，各表独立万级号段，不与 V1 种子及本地手工数据冲突。
-- 状态域：遵循 V2-V4 规范化——courses ACTIVE/INACTIVE、tasks DRAFT/PUBLISHED/CLOSED、
--       exam_papers DRAFT/PUBLISHED/ARCHIVED、questions.type 大写枚举。
-- 脏数据修复：脚本开头将历史遗留的小写 questions.type / exam_sessions.status 归一。
-- 业务一致性（由生成器计算保证）：
--   * enrollment 考勤/作业/考试成绩 = 各明细均分，总评 = 10/30/60 加权（春学期）；
--   * grade_items 三维明细与 enrollment 冗余列同源；
--   * 试卷总分 = 题目分值和；考试会话得分 = 逐题判分和；错题均出自实际答错题目；
--   * 监考 risk_score = 事件权重和，risk_level 与 proctor.threshold.* 配置一致；
--   * 讨论 reply_count/last_reply_at、资源版本 is_active、查重相似度等同步计算；
--   * 秋学期进行中班保留：开放测验窗口（进行中会话）、即将截止作业、
--     进行中签到/投票、PENDING 催交与待审批选课，便于演示动态功能。
-- 演示账号：密码与 V1 相同（123456），学生 st001..st150@stu.aicourse.com，
--       教师 ts01..ts12@aicourse.com。仅限开发环境。
-- ====================================================================

SET NAMES utf8mb4;

-- ── 0. 脏数据归一（历史遗留小写枚举） ──
UPDATE questions SET type = CASE LOWER(type)
    WHEN 'single' THEN 'SINGLE_CHOICE' WHEN 'multiple' THEN 'MULTIPLE_CHOICE'
    WHEN 'true_false' THEN 'TRUE_FALSE' WHEN 'fill_blank' THEN 'FILL_BLANK'
    WHEN 'essay' THEN 'ESSAY' ELSE UPPER(type) END
WHERE BINARY type = LOWER(type);

UPDATE exam_sessions SET status = UPPER(status) WHERE BINARY status = LOWER(status);

UPDATE submissions SET status = UPPER(status) WHERE BINARY status = LOWER(status);

UPDATE course_enrollments SET approval_status = UPPER(approval_status) WHERE BINARY approval_status = LOWER(approval_status);

"""

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
               "backend", "src", "main", "resources", "db", "seed-demo", "V5__rich_seed_data.sql"))
lines = [HEADER]
summary = []

def dump(table, cols, rows, comment):
    summary.append((table, len(rows)))
    if not rows:
        return
    lines.append(f"\n-- ── {comment}（{len(rows)} 行） ──")
    head = f"INSERT INTO {table} {cols} VALUES"
    for i in range(0, len(rows), 100):
        body = ",\n".join("(" + ", ".join(lit(v) for v in r) + ")" for r in rows[i:i + 100])
        lines.append(head + "\n" + body + ";")

UCOLS = ("(id,email,username,password,full_name,role,is_active,is_verified,phone,student_id,major,grade,"
         "class_id,teacher_id,department,title,bio,register_ip,last_login,created_at)")

dump("users", UCOLS,
     [(t["id"], t["email"], t["username"], PWD, t["name"], "TEACHER", 1, 1, t["phone"], None, None, None,
       None, f"T2025{i+1:03d}", t["dept"], t["title"], BIO[i % 4], t["ip"],
       NOW - dt.timedelta(days=R.randint(0, 6)), SPRING_START - dt.timedelta(days=30))
      for i, t in enumerate(teachers)],
     "1a. 教师（ts01-ts12）")

dump("classrooms", "(id,name,code,description,grade_level,major,advisor_id,student_count,max_students,is_active,created_at)",
     [(cl["id"], cl["name"], cl["code"], f"{cl['major']}专业{cl['lvl']}行政班，班主任办公地点：教三机房楼",
       cl["lvl"], cl["major"], cl["advisor"], cl["cnt"], 50, 1, SPRING_START - dt.timedelta(days=45))
      for cl in classrooms],
     "2. 班级（course_id 置空：行政班不绑定单一课程）")

dump("users", UCOLS,
     [(s["id"], s["email"], s["username"], PWD, s["name"], "STUDENT", 1, 1, s["phone"], s["student_id"],
       s["major"], s["grade"], classrooms[s["cls_idx"]]["id"], None, None, None, None, s["ip"],
       NOW - dt.timedelta(days=R.randint(0, 4), hours=R.randint(0, 12)),
       SPRING_START - dt.timedelta(days=25, hours=s["cls_idx"]))
      for s in students],
     "1b. 学生（st001-st150，按班挂载 class_id）")

dump("courses",
     "(id,name,code,description,credits,teacher_id,semester,year,status,approval_status,approval_time,approved_by,"
     "approval_comment,enrollment_type,max_students,is_active,category,difficulty,price,tags,start_date,end_date)",
     [(c["id"], c["disp"], c["code"],
       (f"{c['tpl']['intro']}。16 教学周，{len(c['chapters'])} 章，含任务点/测验/考试与成绩方案（已结束）。"
        if c["sem"] == SEM_S else f"{c['tpl']['intro']}。当前第 5 教学周，进行中。"),
       c["tpl"]["credits"], c["teacher"]["id"], c["sem"], 2026, c["status"], "APPROVED",
       week_dt(c["sem"], 1, -3, 15), 1, "开课申请已批准，按计划运行", c["enroll_type"], 60, 1,
       c["tpl"]["cat"], c["tpl"]["diff"], 0,
       J([c["tpl"]["cat"], c["tpl"]["name"], "大创示范课"]),
       (SPRING_START if c["sem"] == SEM_S else FALL_START),
       (SPRING_START + dt.timedelta(days=18 * 7 - 3) if c["sem"] == SEM_S else FALL_START + dt.timedelta(days=19 * 7 - 3)))
      for c in courses],
     "3. 课程班次")

dump("course_chapters", "(id,course_id,parent_id,title,content,order_index,duration,is_published,created_at)",
     [(ch["id"], ch["course"]["id"], None, f"第{ch['idx']+1}章 {ch['title']}",
       f"本章围绕{ch['title']}展开：概念导入 → 原理讲解 → 例题演练 → 课后小结；配套讲义与课堂录像见资源列表。",
       ch["idx"], ch["dur"], 1 if ch["published"] else 0, week_dt(ch["course"]["sem"], ch["week"], 0, 8))
      for ch in chapters],
     "4. 章节")

dump("course_enrollments",
     "(id,student_id,course_id,approval_status,approval_time,approved_by,approval_comment,is_active,final_score,"
     "attendance_score,assignment_score,exam_score,progress,total_study_time,enrolled_at)",
     [(e["id"], e["s"]["id"], e["c"]["id"], e["st"],
       (e["at"] + dt.timedelta(hours=18)) if e["st"] != "PENDING" else None,
       1 if e["st"] != "PENDING" else None,
       {"APPROVED": "已选入课程", "REJECTED": "名额已满，建议参加补选"}.get(e["st"]),
       0 if e["st"] == "REJECTED" else 1,
       e["final"], e["att"], e["hw"], e["exam"], e["prog"], e["study"], e["at"])
      for e in enrollments],
     "5. 选课（含 PENDING/REJECTED 审批演示，冗余成绩列与明细同源）")

dump("knowledge_points",
     "(id,name,description,course_id,parent_id,difficulty_level,importance,estimated_time,point_type,order_index,created_at)",
     [(k["id"], k["name"], k["desc"], k["course"]["id"], None, k["diff"], k["imp"], k["est"],
       k["ptype"], k["idx"], week_dt(k["course"]["sem"], 1, -5, 9)) for k in kps],
     "6. 知识点")

dump("knowledge_point_relations", "(id,source_id,target_id,relation_type,strength,description)",
     [(r["id"], r["s"]["id"], r["t"]["id"], r["rtype"], r["st"],
       (f"掌握「{r['s']['name']}」是学习「{r['t']['name']}」的前提" if r["rtype"] == "prerequisite"
        else f"「{r['s']['name']}」与「{r['t']['name']}」常结合出题")) for r in kp_rels],
     "7. 知识点关系（链式前置 + 跨章相关）")

dump("chapter_knowledge_points", "(id,chapter_id,knowledge_point_id)",
     [(x["id"], x["ch"]["id"], x["kp"]["id"]) for x in ckps], "8. 章节-知识点挂载")

dump("resources", "(id,file_name,file_type,url,course_id,chapter_id,uploader_id,upload_time,description,visible_to_all)",
     [(r["id"], r["name"], r["mime"], r["url"], r["course"]["id"], r["ch"]["id"], r["uploader"], r["t"],
       r["desc"], r["vis"]) for r in resources],
     "9. 教学资源（file_type 存 MIME，与 V2 注释口径一致）")

dump("resource_versions", "(id,resource_id,version_number,file_name,file_type,url,description,file_size,uploader_id,is_active,created_at)",
     [(v["id"], v["res"]["id"], v["num"], v["res"]["name"], v["res"]["mime"],
       v["res"]["url"].rsplit("/", 1)[0] + f"/v{v['num']}-" + v["res"]["url"].rsplit("/", 1)[1],
       "初版上传" if v["num"] == 1 else f"第{v['num']}轮修订：补充例题、修正笔误",
       v["size"], v["res"]["uploader"], 1 if v["cur"] else 0, v["t"]) for v in resvers],
     "10. 资源版本")

dump("tasks",
     "(id,title,description,task_type,course_id,creator_id,chapter_id,due_date,max_score,status,is_published,"
     "submission_format,requirements,attachment_url,start_date,late_penalty_per_day,max_submissions,created_at)",
     [(t["id"], f"{t['course']['tpl']['name']}·{t['title']}", f"第{t['ch']['idx']+1}章任务点：{t['title']}",
       t["ttype"], t["course"]["id"], t["course"]["teacher"]["id"], t["ch"]["id"], t["due"], t["maxs"],
       t["st"], t["pub"], "both" if t["ttype"] == "assignment" else "text",
       ("要求独立完成、提交前自测运行；严禁抄袭，系统自动查重。" if t["ttype"] == "assignment"
        else "限时线上测验，超时自动交卷。"),
       t["att"], t["start"], t["late_p"], t["maxsub"], t["start"] - dt.timedelta(days=1)) for t in tasks],
     "11. 任务点/作业（秋学期含 PUBLISHED 未截止 + DRAFT）")

dump("submissions",
     "(id,task_id,student_id,content,file_urls,status,submitted_at,graded_at,returned_at,score,feedback,ai_feedback,"
     "grader_id,attempt_number,is_late,late_penalty,time_spent,view_count,created_at)",
     [(su["id"], su["tsk"]["id"], su["e"]["s"]["id"],
       f"完成情况：核心功能已实现并自测通过；主要思路见代码注释。（{su['tsk']['title']}）",
       J([f"/uploads/submissions/{su['id']}-answer.zip"]), su["st"], su["tsub"], su["tgraded"], su["tret"],
       su["score"],
       ("思路正确，注意边界输入。" if su["st"] in ("GRADED", "RESUBMITTED")
        else "格式不符合要求（缺运行截图），请修改后重交。" if su["st"] == "RETURNED" else None),
       ("AI 预评：结构清晰，建议补充异常处理用例；预计得分区间 82-90。" if su["score"] is not None else None),
       su["grader"], 2 if su["st"] == "RESUBMITTED" else 1, 1 if su["late"] else 0, su["pen"],
       R.randint(1800, 7200), R.randint(0, 3), su["tsub"]) for su in submissions],
     "12. 作业提交（GRADED/SUBMITTED/RETURNED/RESUBMITTED 四态）")

dump("task_completions",
     "(id,student_id,task_id,course_id,chapter_id,task_type,completion_type,watched_duration,last_position,"
     "total_duration,score,completed,completed_at,created_at)",
     [(tc["id"], tc["e"]["s"]["id"], tc["tsk"]["id"], tc["tsk"]["course"]["id"], tc["ch"]["id"],
       tc["tsk"]["ttype"], tc["ctype"], tc["wd"], tc["lp"], tc["td"], tc["score"], tc["completed"],
       tc["cat"] if tc["completed"] else None, tc["cat"] - dt.timedelta(minutes=30)) for tc in task_compls],
     "13. 任务点完成")

dump("plagiarism_checks",
     "(id,task_id,submission_id,student_id,check_type,status,similarity_score,high_similarity_count,result_summary,check_detail,checked_at,created_at)",
     [(pc["id"], pc["sub"]["tsk"]["id"], pc["sub"]["id"], pc["sub"]["e"]["s"]["id"], "INTERNAL", "COMPLETED",
       pc["sim"], pc["hi"],
       f"相似度 {pc['sim']}%，高相似片段 {pc['hi']} 处" + ("，已转人工复核。" if pc["sim"] >= 30 else "，判定原创。"),
       J({"engine": "shingle-5", "fragments": pc["hi"]}), pc["at"], pc["at"]) for pc in plag_checks],
     "14. 查重记录")

dump("plagiarism_matches", "(id,plagiarism_check_id,matched_submission_id,similarity,original_text,matched_text,match_type)",
     [(pm["id"], pm["chk"]["id"], pm["ms"]["id"], pm["sim"], pm["ot"], pm["mt"], "INTERNAL") for pm in plag_matches],
     "15. 查重匹配片段（内部库）")

dump("reminders",
     "(id,reminder_type,status,user_id,task_id,course_id,title,message,due_date,trigger_day,scheduled_at,sent_at,created_at)",
     [(rm["id"], rm["typ"], rm["st"], rm["e"]["s"]["id"], rm["tsk"]["id"], rm["tsk"]["course"]["id"],
       rm["title"], rm["msg"], rm["due"], rm["trig"], rm["sch"], rm["sent"], rm["at"]) for rm in reminders],
     "16. 催交提醒（SENT 历史 + PENDING 待发）")

dump("questions",
     "(id,type,content,options,correct_answers,difficulty,score,knowledge_points,explanation,usage_count,correct_rate,course_id,created_by,is_active,created_at)",
     [(q["id"], q["qt"], q["content"], J(q["opts"]) if q["opts"] else None, J(q["answers"]), q["diff"],
       q["score"], J([q["course"]["kps"][q["idx"] % 8]["name"]]), q["expl"], q["used"], q["crate"],
       q["course"]["id"], q["course"]["teacher"]["id"], 1, week_dt(q["course"]["sem"], 2, 1, 10))
      for q in questions],
     "17. 题库（type 大写枚举，与运行时一致）")

dump("exam_papers",
     "(id,title,description,course_id,creator_id,status,instructions,start_time,end_time,total_score,duration,"
     "question_count,shuffle_questions,shuffle_options,show_score,show_correct_answer,max_attempts,time_limit,"
     "allow_review,is_active,created_at)",
     [(p["id"], p["title"], f"{p['course']['disp']}·{KIND_NAME[p['kind']]}，共 {len(p['qs'])} 题 / {p['total']} 分。",
       p["course"]["id"], p["creator"], p["st"],
       "闭卷，独立完成；允许使用课程讲义，禁止交流。" if p["kind"] in ("mid", "final") else "开卷小测，限时提交。",
       p["start"], p["end"], p["total"], p["dur"], len(p["qs"]),
       1 if p["shuffle"] else 0, 1 if p["shuffle"] else 0,
       1 if p["kind"] in ("quiz1", "quiz2") else 0, 1 if p["kind"] == "quiz1" else 0,
       1, p["dur"], 1, 1, week_dt(p["course"]["sem"], 1, 1, 10)) for p in papers],
     "18. 试卷（春 PUBLISHED/ARCHIVED + 秋含 DRAFT/开放窗口）")

dump("exam_questions", "(id,exam_paper_id,question_id,order_index,points)",
     [(x["id"], x["p"]["id"], x["q"]["id"], x["oi"], x["pts"]) for x in examqs], "19. 试卷题目")

dump("exam_sessions",
     "(id,exam_paper_id,student_id,status,attempt_number,start_time,submit_time,time_spent,total_score,percentage,"
     "answers,question_scores,ip_address,user_agent,cheating_flags,question_order,option_orders,created_at)",
     [(x["id"], x["paper"]["id"], x["e"]["s"]["id"], x["st"], 1, x["start"], x["sub"], x["spent"],
       x["tot"], x["pct"], J(x["ans"]), x["qs"], x["ip"], x["ua"],
       J(x["flags"]) if x["flags"] else None, x["qorder"], x["oorders"], x["start"])
      for x in sessions],
     "20. 考试会话（含秋学期 STARTED 进行中作答）")

dump("wrong_questions",
     "(id,student_id,question_id,course_id,wrong_answer,reason,ai_analysis,mastered,source_type,practice_count,"
     "last_practiced_at,created_at,updated_at)",
     [(w["id"], w["s"]["id"], w["q"]["id"], w["c"]["id"], f"我的作答：{w['wa']}",
       f"未掌握「{w['q']['course']['kps'][w['q']['idx'] % 8]['name']}」相关要点",
       f"错因分析：概念混淆/要点遗漏。建议重看第 {w['q']['idx']//2+1} 节讲义，并完成 2 道同类练习。（AI 自动生成）",
       w["mastered"], w["src"], w["pcnt"], w["lat"], w["t"], w["t"]) for w in wrongs],
     "21. 错题（均出自考试会话实际答错的题）")

dump("learning_paths",
     "(id,student_id,course_id,path_name,description,stages,learning_goal,current_stage,total_stages,"
     "completion_rate,status,estimated_completion_date,actual_completion_date,created_at)",
     [(lp["id"], lp["s"]["id"], lp["c"]["id"], lp["name"],
       f"基于 {lp['c']['disp']} 知识点图谱生成的个性化路径", J(lp["stages"]), lp["goal"],
       lp["cur"], lp["total"], lp["rate"], lp["st"],
       lp["start"] + dt.timedelta(days=28), lp["acd"], lp["start"]) for lp in lps],
     "22. 学习路径")

dump("learning_path_nodes",
     "(id,learning_path_id,knowledge_point_id,chapter_id,title,description,node_type,status,order_index,"
     "estimated_time,actual_time,completed_at)",
     [(n["id"], n["lp"]["id"], n["kp"]["id"], n["ch"]["id"], n["title"], n["kp"]["desc"], n["ntype"],
       n["st"], n["oi"], n["est"], n["act"], n["cat"]) for n in lpns],
     "23. 学习路径节点")

dump("learning_records", "(id,user_id,resource_id,chapter_id,course_id,action_type,duration,progress,metadata,created_at)",
     [(x["id"], x["s"]["id"], x["r"]["id"] if x["r"] else None, x["ch"]["id"], x["c"]["id"], x["act"],
       x["dur"], x["prog"], J({"source": "web"}), x["t"]) for x in learning_recs],
     "24. 学习行为记录")

dump("notes", "(id,user_id,course_id,chapter_id,knowledge_point_id,title,content,is_favorite,created_at,updated_at)",
     [(n["id"], n["s"]["id"], n["c"]["id"], n["ch"]["id"], n["kp"]["id"], n["title"], n["body"],
       n["fav"], n["t"], n["t"]) for n in notes], "25. 学生笔记/收藏")

dump("video_learning_records",
     "(id,user_id,resource_id,course_id,watch_duration,progress,last_position,complete_count,last_watched_at,created_at)",
     [(v["id"], v["s"]["id"], v["r"]["id"], v["c"]["id"], v["dur"], v["prog"], v["lp"], v["cc"], v["last"],
       v["last"]) for v in vlr], "26. 视频学习记录")

dump("video_watch_sessions", "(id,user_id,resource_id,start_position,end_position,duration,started_at,ended_at)",
     [(x["id"], x["s"]["id"], x["r"]["id"], x["sp"], x["ep"], x["dur"], x["st0"], x["en0"]) for x in vws_l],
     "27. 视频观看会话")

notif_rows = []
for i, n in enumerate(notifications):
    nt = min(n["t"], NOW)
    notif_rows.append((n["id"] if "id" in n else None, n["u"]["id"], n["title"], n["content"], n["cat"],
                       n["typ"], n["read"], n["pr"], n["url"], None,
                       nt, nt + dt.timedelta(days=R.randint(0, 3)) if n["read"] else None))
dump("notifications",
     "(id,user_id,title,content,category,type,is_read,priority,action_url,created_at,read_at)",
     [(B["notif"].nxt(), r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[10], r[11]) for r in notif_rows],
     "28. 通知（欢迎/成绩发布/考试提醒）")

dump("role_permissions",
     "(id,role,permission_key,permission_name,permission_type,resource_path,sort_order,is_active,description)",
     [(x["id"], x["role"], x["key"], x["nm"], "BUTTON", x["path"], x["so"], 1, f"{x['nm']}（按钮级权限）")
      for x in role_perm_rows],
     "29. 角色权限扩展（BUTTON 级，补齐 V1 仅 MENU 粒度的缺口）")

dump("system_configs",
     "(id,config_key,config_value,config_group,config_type,description,is_public,sort_order)",
     [(B["sc"].nxt(), k, v, g, t, d, pub, so) for so, (k, v, g, t, d, pub) in enumerate(sys_new_configs, start=1)],
     "30. 系统配置扩展")

dump("file_upload_sessions",
     "(id,filename,file_size,total_chunks,uploaded_chunks,upload_path,status,uploader_id,expires_at,created_at)",
     [(x["id"], x["fn"], x["size"], x["total"], x["up"], x["path"], x["st"], x["uid"], x["exp"], x["t"])
      for x in fus], "31. 分片上传会话")

dump("operation_logs",
     "(id,user_id,username,module,action,target,target_id,ip,user_agent,method,params,duration_ms,status,error_msg,created_at)",
     [(x["id"], x["u"]["id"], x["u"]["username"], x["mod"], x["act"], x["tgt"], x["tid"], x["ip"], x["ua"],
       x["m"], J({"id": x["tid"], "note": x["tgt"]}), x["dur"], x["st"], x["err"], x["t"]) for x in op_logs],
     "32. 操作日志")

dump("ai_chat_sessions", "(id,session_id,user_id,title,message_count,deleted,created_at)",
     [(x["id"], x["sid"], x["s"]["id"], x["title"], x["n"], 0, x["t"]) for x in ai_sess],
     "33. AI 对话会话")

dump("ai_chat_messages", "(id,session_id,role,content,deleted,created_at)",
     [(x["id"], x["sid"], x["role"], x["body"], 0, x["t"]) for x in ai_msgs], "34. AI 对话消息")

dump("sign_ins",
     "(id,course_id,chapter_id,title,type,status,duration_minutes,gesture,sign_code,creator_id,started_at,closed_at,created_at)",
     [(x["id"], x["course"]["id"], x["ch"]["id"], f"{x['course']['disp']}·{x['title']}", x["typ"], x["st"],
       x["dur"], x["gesture"] if x["typ"] == "GESTURE" else None,
       x["code"] if x["typ"] in ("CODE", "QR") else None, x["course"]["teacher"]["id"],
       x["start"] if x["st"] != "PENDING" else None, x["closed"], x["start"] - dt.timedelta(days=1))
      for x in sign_ins],
     "35. 课堂签到（NORMAL/CODE/GESTURE/QR/LOCATION 五类 + ONGOING/PENDING 演示态）")

dump("sign_in_records",
     "(id,sign_in_id,student_id,status,sign_time,ip,user_agent,latitude,longitude,submitted_code,submitted_gesture)",
     [(x["id"], x["sgn"]["id"], x["e"]["s"]["id"], x["st"], x["t"], x["ip"],
       UA.get(x["e"]["s"]["dev"][1], "")[:200] if x["t"] else None,
       x["lat"], x["lng"], x["sub_code"], x["sub_ges"]) for x in sign_recs],
     "36. 签到记录")

dump("votes",
     "(id,course_id,chapter_id,title,description,vote_mode,options,status,creator_id,started_at,closed_at)",
     [(x["id"], x["course"]["id"], x["ch"]["id"], f"{x['course']['disp']}·{x['title']}",
       f"随堂投票：{x['title']}，单选限时 10 分钟。" if x["mode"] == "SINGLE_CHOICE" else f"随堂多选：{x['title']}。",
       x["mode"], J(x["opts"]), x["st"], x["course"]["teacher"]["id"],
       x["start"] if x["st"] != "PENDING" else None, x["closed"]) for x in votes_l],
     "37. 课堂投票")

dump("vote_records", "(id,vote_id,student_id,choice,ip,created_at)",
     [(x["id"], x["v"]["id"], x["e"]["s"]["id"], J(x["choice"]) if isinstance(x["choice"], list) else J(x["choice"]),
       x["ip"], x["t"]) for x in vote_recs],
     "38. 投票记录（SINGLE 存字符串 JSON，MULTI 存数组 JSON）")

dump("discussion_topics",
     "(id,course_id,chapter_id,title,content,type,status,creator_id,creator_role,is_pinned,is_essence,"
     "reply_count,last_reply_at,created_at)",
     [(x["id"], x["course"]["id"], x["ch"]["id"] if x["ch"] else None, x["title"], x["body"], x["typ"], x["st"],
       x["creator"]["id"], x["role"], x["pin"], x["ess"], x["rc"], x["lra"], x["start"])
      for x in disc_topics],
     "39. 讨论话题（reply_count/last_reply_at 与回复表一致）")

dump("discussion_posts",
     "(id,topic_id,parent_id,author_id,author_role,content,like_count,floor,mentions,created_at)",
     [(x["id"], x["topic"]["id"], x["parent"]["id"] if x["parent"] else None, x["author"]["id"], x["arole"],
       x["body"], x["likes"], x["floor"], J(x["mentions"]) if x["mentions"] else None, x["at"])
      for x in disc_posts],
     "40. 讨论回复（顶层在前、楼中楼在后，保证同批插入的 FK 顺序）")

dump("discussion_post_likes", "(id,post_id,user_id,created_at)",
     [(x["id"], x["p"]["id"], x["u"]["id"], x["t"]) for x in disc_likes], "41. 回复点赞")

dump("proctor_sessions", "(id,exam_session_id,student_id,status,started_at,ended_at,risk_score,risk_level,created_at)",
     [(x["id"], x["ses"]["id"], x["ses"]["e"]["s"]["id"], "ENDED", x["start"], x["end"], x["risk"], x["lvl"],
       x["start"]) for x in proctor_sess],
     "42. 监考会话（risk_score=事件权重和，risk_level 与 proctor.threshold.* 一致）")

dump("proctor_events",
     "(id,proctor_session_id,type,severity,detail,evidence_url,captured_at,status,review_note,reviewed_at,created_at)",
     [(x["id"], p["id"], x["typ"], x["sev"], f"检测到 {x['typ']}，严重度 {x['sev']}",
       f"/evidence/proctor/{p['id']}/{x['id']}.jpg", x["at"],
       "REVIEWED" if x["reviewed"] else "PENDING", x["note"] if x["reviewed"] else None,
       x["at"] + dt.timedelta(days=2) if x["reviewed"] else None, x["at"])
      for p in proctor_sess for x in p["evs"]],
     "43. 监考风险事件")

dump("grade_policies", "(id,course_id,name,components,creator_id,is_default,created_at)",
     [(x["id"], x["c"]["id"], x["name"], J(COMPS), x["c"]["teacher"]["id"], 1, x["t"])
      for x in grade_policies],
     "44. 成绩方案（结构性上限：UNIQUE(course_id)，每课 1 套，权重合计=100）")

dump("grade_items", "(id,policy_id,student_id,component_key,score,raw_score,remark,grader_id,created_at)",
     [(x["id"], x["pol"], x["e"]["s"]["id"], x["key"], x["score"], x["raw"], x["remark"], x["grader"], x["t"])
      for x in grade_items],
     "45. 成绩明细（与 enrollment 冗余列同源）")

dump("user_sessions",
     "(id,user_id,session_token,refresh_token,device_name,browser,operating_system,ip_address,location,is_active,is_current,login_time,last_activity,expires_at)",
     [(x["id"], x["uid"], x["tok"], x["rf"], x["dev"], x["br"], x["osn"], x["ip"], "浙江省杭州市",
       0, 0, x["login"], x["last"], x["exp"]) for x in user_sessions],
     "46. 历史登录会话（全部已过期失效，不与当前登录态冲突）")

dump("login_logs",
     "(id,user_id,username,login_status,ip_address,location,device_name,browser,operating_system,failure_reason,created_at)",
     [(x["id"], x["uid"], x["un"], x["st"], x["ip"], "浙江省杭州市", x["dev"], x["br"], x["osn"], x["reason"], x["t"])
      for x in login_logs],
     "47. 登录日志（含 FAILURE 样本供安全页演示）")

dump("verification_codes", "(id,email,phone,code,type,used,expires_at,ip_address,created_at)",
     [(x["id"], x["em"], x["ph"], x["code"], x["typ"], x["used"], x["exp"], x["ip"], x["t"])
      for x in v_codes],
     "48. 验证码（均已过期，used 混合）")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")

print(f"已写出 {OUT}")
merged = {}
for t, n in summary:
    merged[t] = merged.get(t, 0) + n
print(f"{'表名':28s} 行数")
total = 0
below = []
for t, n in merged.items():
    total += n
    print(f"{t:28s} {n}")
    if n < 100 and t not in ("grade_policies", "classrooms", "courses"):
        below.append((t, n))
print(f"{'合计':28s} {total}")
if below:
    print("!! 以下表新增不足 100 行：", below)
else:
    print("OK：除结构性上限表（grade_policies/classrooms/courses）外，各表新增均 >=100 行")
sys_exit_code = 0
if below:
    sys_exit_code = 1
raise SystemExit(sys_exit_code)
