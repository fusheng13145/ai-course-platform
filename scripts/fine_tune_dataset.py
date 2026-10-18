"""讲义 / 题库 → LoRA 训练样本的种子脚本（可重复运行，取证与冷启动共用）。

为什么需要它：`scripts/rag_corpus.json` 是 RAG 的**文档**语料，粒度是「一篇文档 = 一段
浓缩讲义」。直接交给 `lora_engine.build_samples()` 派生，实测只有 course 4007 得 12 条、
course 1 得 2 条（<20），跑不满一次有意义 loss 序列的真实训练。本脚本把**同一份权威材料**
按两种天然配对再切细：

- `windows`：讲义正文按小节标题 + 句子窗口切，每条 `output` 都是讲义原文片段；
  `instruction` 只由「小节标题 + 窗口序号」构成，是提问脚手架，不含答案。
- `questions`：**只读解析**已版本化的题库 SQL（`backend/.../V6__computer_network_showcase.sql`
  的 `INSERT INTO questions`），题干+选项为 `instruction`、标准答案+解析为 `output`。
  这一来源是真实教学考核材料，instruction/output 语义天然对齐，比窗口派生更贴微调目标。
  ⚠️ 绝不 `import scripts/generate-cn-showcase-seed.py`：那脚本 **import 即执行**并重写
  V6 SQL（本仓库Flyway 种子，实测会污染工作树），所以这里只做文本解析。
- `both`（默认）：两者合并去重后入库。

改写、补全、虚构都不做；只调用 `lora_engine` 的公开入口
（`corpus_source` / `validate_samples` / `add_dataset`），不碰私有符号，也不改
`corpus_source()` 的回落链——回落链的职责是「找不到语料时诚实失败」，把数据切细是脚本的职责。

用法：
  python scripts/fine_tune_dataset.py                      # 默认 course 4007，来源 both
  python scripts/fine_tune_dataset.py --source questions   # 只用题库配对
  python scripts/fine_tune_dataset.py --course 1           # 语料单薄的课会如实报数
  python scripts/fine_tune_dataset.py --sentences 3        # 调窗口粒度
  python scripts/fine_tune_dataset.py --dry-run            # 只打印不入库

Java 侧 `POST /api/v1/fine-tune/datasets` 接上（批 2）后，本脚本退役为纯种子工具。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "python-services" / "fine_tune_service"))
import lora_engine as le  # noqa: E402

_SENT = re.compile(r"(?<=[。！？；!?;])")
LETTERS = "ABCDEFGHIJ"
QUESTIONS_SQL = REPO / "backend" / "src" / "main" / "resources" / "db" / "seed-demo" / "V6__computer_network_showcase.sql"
# questions 表列序（与 V6 的 INSERT 列清单一致）：content=2、options=3、correct_answers=4、
# knowledge_points=7、explanation=8、course_id=11
_Q_COL = {"content": 2, "options": 3, "answers": 4, "kp": 7, "explanation": 8, "course_id": 11}


_MARK = "\x01"          # 标记"这是字符串字面量"，与裸值（数字 / NULL）区分
_ESC = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "0": "\0",
        "\\": "\\", "'": "'", '"': '"'}


def _sql_tuples(text: str, marker: str) -> list[list[str]]:
    """取 `INSERT INTO <表> (...) VALUES` 段里每个 `( ... )` 的顶层字段（只读文本，不执行 SQL）。

    两种转义都必须吃：V6 实测用的是 **MySQL 反斜杠转义**（`\\'` 共 468 处），而 SQL 标准是
    `''`。只按 `''` 解析会让引号状态失步——第一个带 `\\'` 的题干之后整段都读不出行，
    表现就是"题库解析到 0 条"而不是报错。
    """
    start = text.find(marker)
    if start < 0:
        return []
    at = text.find("VALUES", start)
    if at < 0:
        return []
    i, n, rows = at + 6, len(text), []
    while i < n:
        if text[i] != "(":
            if text[i] == ";":          # 语句结束，本表不再往后取
                break
            i += 1
            continue
        i += 1
        fields, buf, in_str = [], [], False
        while i < n:
            ch = text[i]
            if in_str:
                if ch == "\\" and i + 1 < n:
                    buf.append(_ESC.get(text[i + 1], text[i + 1]))
                    i += 2
                    continue
                if ch == "'":
                    if i + 1 < n and text[i + 1] == "'":
                        buf.append("'")
                        i += 2
                        continue
                    in_str = False
                    i += 1
                    continue
                buf.append(ch)
            elif ch == "'":
                in_str = True
                buf.append(_MARK)
            elif ch == ",":
                fields.append("".join(buf).strip())
                buf = []
            elif ch == ")":
                fields.append("".join(buf).strip())
                i += 1
                break
            else:
                buf.append(ch)
            i += 1
        rows.append(fields)
    return rows


def _unquote(field: str) -> str:
    """还原字符串字段正文（清掉 _MARK 标记）；裸值原样返回。"""
    return field.replace(_MARK, "")


def _is_literal(field: str) -> bool:
    return field.startswith(_MARK)


def _json_field(field: str) -> list:
    if not _is_literal(field):
        return []
    try:
        val = json.loads(_unquote(field))
    except (json.JSONDecodeError, ValueError):
        return []
    return val if isinstance(val, list) else []


def question_rows(sql_path: Path) -> list[list[str]]:
    """questions 表的数据行（首列必须是数字 id，借此把列清单那一组排掉）。"""
    if not sql_path.exists():
        return []
    rows = _sql_tuples(sql_path.read_text(encoding="utf-8"), "INSERT INTO questions")
    return [f for f in rows if f and not _is_literal(f[0]) and f[0].isdigit()]


def question_samples(course_id: int, sql_path: Path = QUESTIONS_SQL) -> list[dict]:
    """题库行 → 配对样本。题干+选项提问，标准答案+选项原文+解析作答。"""
    rows = []
    for f in question_rows(sql_path):
        if len(f) <= _Q_COL["course_id"]:
            continue
        if _unquote(f[_Q_COL["course_id"]]).strip() != str(course_id):
            continue
        content = _unquote(f[_Q_COL["content"]]).strip()
        opts = _json_field(f[_Q_COL["options"]])
        answers = _json_field(f[_Q_COL["answers"]])
        kps = _json_field(f[_Q_COL["kp"]])
        expl = _unquote(f[_Q_COL["explanation"]]).strip()
        if not content or not answers:
            continue
        head = f"{content}"
        if opts:
            head += "\n" + "\n".join(f"{LETTERS[n]}. {o}" for n, o in enumerate(opts))
        parts = []
        for a in answers:
            letter = str(a).strip()
            idx = LETTERS.find(letter.upper())
            text = letter if not (opts and 0 <= idx < len(opts)) else f"{letter}. {opts[idx]}"
            parts.append(text)
        out = f"答案：{'、'.join(parts)}。"
        if expl:
            out += f"\n解析：{expl}"
        if kps:
            out += f"\n涉及知识点：{'；'.join(str(k) for k in kps)}"
        rows.append({"instruction": f"请作答并讲解理由。\n{head}", "input": "",
                     "output": out, "derived_from": "题库配对"})
    return rows


def sections(text: str) -> list[tuple[str, str]]:
    """`# 标题\\n正文` 拼接文本 → [(小节标题, 正文块)]。非标题行归入当前小节。"""
    out: list[tuple[str, str]] = []
    topic, buf = "总论", ""
    for line in text.splitlines():
        if line.startswith("#"):
            if buf.strip():
                out.append((topic, buf.strip()))
            buf = ""
            title = line.lstrip("#").strip()
            if title:  # 空标题行不改变当前小节（与引擎 build_samples 同规则）
                topic = title
            continue
        buf += line + "\n"
    if buf.strip():
        out.append((topic, buf.strip()))
    return out


def samples(text: str, per: int, min_chars: int) -> list[dict]:
    rows: list[dict] = []
    for topic, block in sections(text):
        sents = [s.strip() for s in _SENT.split(block) if s.strip()]
        no = 0
        for start in range(0, len(sents), per):
            window = "".join(sents[start:start + per])
            if len(window) < min_chars:
                continue
            no += 1
            rows.append({"instruction": f"请讲解「{topic}」的第 {no} 个要点。",
                         "input": "", "output": window,
                         "derived_from": "讲义窗口派生"})
    return rows


def main() -> int:
    ap = argparse.ArgumentParser(description="从仓库权威讲义/题库派生 LoRA 训练样本并登记数据集")
    ap.add_argument("--course", type=int, default=4007)
    ap.add_argument("--source", choices=("both", "windows", "questions"), default="both")
    ap.add_argument("--sentences", type=int, default=2, help="每条样本拼几句原文")
    ap.add_argument("--min-chars", type=int, default=40)
    ap.add_argument("--dry-run", action="store_true", help="只打印计数与样例，不入库")
    args = ap.parse_args()

    rows, notes = [], []
    if args.source in ("both", "windows"):
        kind, text = le.corpus_source(args.course)
        if kind == "none":
            notes.append(f"讲义来源空（course {args.course} 无正文，可设 FT_CORPUS_DIR / FT_CORPUS_JSON）")
        else:
            win = samples(text, args.sentences, args.min_chars)
            rows += win
            notes.append(f"讲义 {kind} 字符={len(text)} → 窗口样本 {len(win)} 条")
    if args.source in ("both", "questions"):
        qs = question_samples(args.course)
        rows += qs
        notes.append(f"题库 {QUESTIONS_SQL.name} → 配对样本 {len(qs)} 条")

    uniq: list[dict] = []
    seen: set[str] = set()
    for r in rows:
        key = r["output"]
        if key in seen:
            continue
        seen.add(key)
        uniq.append(r)
    print(f"course_id={args.course}")
    for n in notes:
        print(f"  · {n}")
    print(f"  = 合计 {len(uniq)} 条（去重前 {len(rows)}）")
    for r in uniq[:2]:
        print(f"  eg. {r['instruction'][:60]} → {r['output'][:70]}")
    if not uniq:
        print("派生为空：该课既无讲义正文也无题库题目，或参数过严", file=sys.stderr)
        return 1
    if args.dry_run:
        return 0
    saved = le.add_dataset(args.course, le.validate_samples(uniq),
                           source=f"种子脚本 · {args.source}")
    print(f"已登记 dataset_id={saved['dataset_id']} sample_count={saved['sample_count']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
