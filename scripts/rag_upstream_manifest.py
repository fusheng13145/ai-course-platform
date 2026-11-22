#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DeepGraph RAG 上游快照校验器（仅标准库，不依赖 RAG 运行依赖）。

用途：
- `--generate --source <DeepSeek-RAG-Chatbot-main>` 生成
  `python-services/deepgraph_rag/UPSTREAM_MANIFEST.json`；
- `--check` 校验平台副本与清单的原始 SHA-256 是否一致；
- `--check --source <源目录>` 额外证明源仓库未被写入（同一哈希）。

判定口径：
- `sha256`：原始字节哈希，必须一致，这是“原样复制、零修改”的硬证据；
- `normalized_sha256`：仅把 CRLF 归一为 LF 后计算，用于机器/编辑器行尾漂移诊断；
  归一化一致但原始哈希不一致时，校验器仍然失败，不允许降级为“内容等价”。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


MANIFEST_NAME = "UPSTREAM_MANIFEST.json"
TARGET_DIR = Path("python-services") / "deepgraph_rag"

# 上游快照允许的 16 个运行/文档文件 + 9 个归档脚本；.env 含环境差异，不纳入。
UPSTREAM_FILES = [
    "app.py",
    "backend.py",
    "config.py",
    "neo4j_handler.py",
    "README.md",
    "requirements.txt",
    "Dockerfile",
    "docker-compose.yml",
    "install.sh",
    "使用说明.md",
    "LICENSE",
    "utils/build_graph.py",
    "utils/doc_handler.py",
    "utils/llm_client.py",
    "utils/persistence.py",
    "utils/retriever_pipeline.py",
    "_archive_scripts/append_content.py",
    "_archive_scripts/append_content2.py",
    "_archive_scripts/append_functions.py",
    "_archive_scripts/create_append.py",
    "_archive_scripts/create_append2.py",
    "_archive_scripts/fix_build_graph.py",
    "_archive_scripts/fix_preclean.py",
    "_archive_scripts/restore_build_graph.py",
    "_archive_scripts/test1.py",
]

# 平台自有适配器/契约测试，明确不属于上游快照。
ADAPTER_FILES = {
    "api_server.py",
    "test_deepgraph_contract.py",
    "UPSTREAM_MANIFEST.json",
}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def normalized_bytes(data: bytes) -> bytes:
    # 只处理 CRLF -> LF；不做编码转换、不 strip、不补换行。
    return data.replace(b"\r\n", b"\n")


def file_entry(path: Path) -> dict:
    data = path.read_bytes()
    return {
        "bytes": len(data),
        "sha256": sha256_bytes(data),
        "normalized_sha256": sha256_bytes(normalized_bytes(data)),
        "line_endings": "CRLF" if b"\r\n" in data else "LF",
    }


def source_relative(rel: str) -> str:
    """平台归档目录相对源仓库根目录的映射：_archive_scripts/x.py -> x.py。"""
    if rel.startswith("_archive_scripts/"):
        return rel.split("/", 1)[1]
    return rel


def build_manifest(source: Path) -> dict:
    files = {}
    for rel in UPSTREAM_FILES:
        source_file = source / source_relative(rel)
        if not source_file.is_file():
            raise FileNotFoundError(f"源文件缺失: {source_file}")
        files[rel] = file_entry(source_file)
    return {
        "schema": 1,
        "policy": "byte-identical",
        "source": source.name,
        "description": "DeepSeek-RAG-Chatbot-main 原样复制清单；sha256 为原始字节哈希，不得以 LF 归一化替代",
        "files": files,
    }


def check_manifest(repo_root: Path, source: Path | None) -> tuple[int, dict]:
    target_root = repo_root / TARGET_DIR
    manifest_path = target_root / MANIFEST_NAME
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors = []
    warnings = []
    checked = 0

    declared = manifest.get("files") or {}
    for rel in UPSTREAM_FILES:
        target_file = target_root / rel
        if not target_file.is_file():
            errors.append({"path": rel, "error": "target_missing"})
            continue
        expected = declared.get(rel)
        if not expected:
            errors.append({"path": rel, "error": "manifest_entry_missing"})
            continue
        actual = file_entry(target_file)
        checked += 1
        if actual["sha256"] != expected.get("sha256"):
            item = {
                "path": rel,
                "error": "raw_sha256_mismatch",
                "expected": expected.get("sha256"),
                "actual": actual["sha256"],
                "normalized_match": actual["normalized_sha256"] == expected.get("normalized_sha256"),
            }
            errors.append(item)
        elif actual["bytes"] != expected.get("bytes"):
            errors.append({
                "path": rel,
                "error": "byte_length_mismatch",
                "expected": expected.get("bytes"),
                "actual": actual["bytes"],
            })
        if source is not None:
            source_file = source / source_relative(rel)
            if not source_file.is_file():
                errors.append({"path": rel, "error": "source_missing"})
            elif sha256_bytes(source_file.read_bytes()) != expected.get("sha256"):
                errors.append({"path": rel, "error": "source_changed_since_manifest"})

    # 只允许已声明的适配器文件出现在快照根目录；其余 .py 视为意外混入。
    allowed = set(UPSTREAM_FILES) | ADAPTER_FILES
    for path in target_root.rglob("*.py"):
        rel = path.relative_to(target_root).as_posix()
        if rel.startswith("utils/") or rel.startswith("_archive_scripts/"):
            continue
        if rel not in allowed and rel not in UPSTREAM_FILES:
            warnings.append({"path": rel, "warning": "unexpected_python_file"})

    summary = {
        "checked": checked,
        "expected": len(UPSTREAM_FILES),
        "errors": errors,
        "warnings": warnings,
        "source_checked": str(source) if source else None,
        "policy": manifest.get("policy"),
    }
    return (0 if not errors else 1), summary


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--source", default=None, help="DeepSeek-RAG-Chatbot-main 目录")
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    source = Path(args.source).resolve() if args.source else None

    if args.generate:
        if source is None:
            parser.error("--generate 需要 --source")
        manifest = build_manifest(source)
        target = repo_root / TARGET_DIR / MANIFEST_NAME
        target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"generated": str(target), "files": len(manifest["files"])}, ensure_ascii=False))
        return 0

    if not args.check:
        parser.error("需要 --check 或 --generate")

    code, summary = check_manifest(repo_root, source)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
