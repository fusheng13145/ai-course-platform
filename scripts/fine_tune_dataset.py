"""讲义 → LoRA 训练样本的种子脚本（可重复运行，取证与冷启动共用）。

为什么需要它：`scripts/rag_corpus.json` 是 RAG 的**文档**语料，粒度是「一篇文档 = 一段
浓缩讲义」。直接交给 `lora_engine.build_samples()` 派生，实测只有 course 4007 得 12 条、
course 1 得 2 条（<20），跑不满一次有意义 loss 序列的真实训练。本脚本把**同一份权威讲义**
按句子窗口再切细，因此：

- 每条 `output` 都是讲义原文片段——不改写、不补全、不虚构；
- `instruction` 只由「小节标题 + 窗口序号」构成，是提问脚手架，不含答案；
- 只调用 `lora_engine` 的公开入口（`corpus_source` / `validate_samples` / `add_dataset`），
  不碰私有符号，也不改 `corpus_source()` 的回落链——回落链的职责是「找不到语料时诚实失败」，
  把数据切细是脚本的职责。

用法：
  python scripts/fine_tune_dataset.py                      # 默认 course 4007
  python scripts/fine_tune_dataset.py --course 1           # 语料单薄的课会如实报数
  python scripts/fine_tune_dataset.py --sentences 3        # 调窗口粒度
  python scripts/fine_tune_dataset.py --dry-run            # 只打印不入库

Java 侧 `POST /api/v1/fine-tune/datasets` 接上（批 2）后，本脚本退役为纯种子工具。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "python-services" / "fine_tune_service"))
import lora_engine as le  # noqa: E402

_SENT = re.compile(r"(?<=[。！？；!?;])")


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
    ap = argparse.ArgumentParser(description="从仓库权威讲义派生 LoRA 训练样本并登记数据集")
    ap.add_argument("--course", type=int, default=4007)
    ap.add_argument("--sentences", type=int, default=2, help="每条样本拼几句原文")
    ap.add_argument("--min-chars", type=int, default=40)
    ap.add_argument("--dry-run", action="store_true", help="只打印计数与样例，不入库")
    args = ap.parse_args()

    kind, text = le.corpus_source(args.course)
    if kind == "none":
        print(f"课程 {args.course} 无可派生讲义：请设置 FT_CORPUS_DIR 或 FT_CORPUS_JSON", file=sys.stderr)
        return 1
    rows = samples(text, args.sentences, args.min_chars)
    print(f"course_id={args.course} 语料={kind} 字符={len(text)} 派生样本={len(rows)}")
    for r in rows[:3]:
        print(f"  - {r['instruction']}\n    → {r['output'][:80]}")
    if not rows:
        print("派生为空：调小 --min-chars 或 --sentences 后重试", file=sys.stderr)
        return 1
    if args.dry_run:
        return 0
    saved = le.add_dataset(args.course, le.validate_samples(rows), source="种子脚本讲义窗口")
    print(f"已登记 dataset_id={saved['dataset_id']} sample_count={saved['sample_count']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
