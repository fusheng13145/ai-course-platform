#!/usr/bin/env python3
"""把仓库内 uploads 目录迁移到外部存储根（历史迁移脚本，保留可重放）。

迁移已完成，仓库内 uploads/ 目录已移除；本脚本仅作留档：源目录不存在时
直接以退出码 2 报「源目录不存在」，不会创建或回写仓库目录。

默认只做 dry-run，不复制、不删除任何文件。分两步执行：

    python scripts/legacy/migrate-uploads.py                  # 打印计划
    python scripts/legacy/migrate-uploads.py --apply          # 复制 + 校验 + 写 manifest
    python scripts/legacy/migrate-uploads.py --apply --purge-old  # 备份旧目录后再删除

安全约束：
- 只复制，不移动；目标已有同哈希文件时跳过，不同哈希时整批中止；
- manifest 记录每个文件的 relative/size/sha256，写盘后逐文件复核；
- --purge-old 强制先打 zip 备份并校验 CRC，备份失败绝不删除；
- 不删除目标根、不删除仓库外路径；所有删除都限定在解析后的 --source 内。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = REPO_ROOT / "backend" / "uploads"
DEFAULT_BACKUP_DIR = REPO_ROOT / "data" / "backups" / "uploads-migration"


def default_target() -> Path:
    """与 StorageLayout 的优先顺序一致：FILE_UPLOAD_DIR > AICOURSE_DATA_DIR/storage > ~/.aicourse-platform/storage。"""
    file_upload_dir = os.environ.get("FILE_UPLOAD_DIR", "").strip()
    if file_upload_dir:
        return Path(file_upload_dir).expanduser().resolve()
    data_dir = os.environ.get("AICOURSE_DATA_DIR", "").strip()
    if data_dir:
        return (Path(data_dir).expanduser() / "storage").resolve()
    return (REPO_ROOT / "data" / "aicourse-platform" / "storage").resolve()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def iter_source_files(source: Path) -> list[Path]:
    files: list[Path] = []
    for root, dirs, names in os.walk(source, followlinks=False):
        dirs[:] = sorted(d for d in dirs if not (Path(root) / d).is_symlink())
        for name in sorted(names):
            path = Path(root) / name
            if path.is_symlink():
                print(f"[warn] 跳过符号链接: {path}", file=sys.stderr)
                continue
            files.append(path)
    return files


def build_plan(source: Path, target: Path) -> tuple[list[dict], list[dict]]:
    entries: list[dict] = []
    conflicts: list[dict] = []
    for path in iter_source_files(source):
        relative = path.relative_to(source).as_posix()
        digest = sha256_file(path)
        size = path.stat().st_size
        target_path = target / relative
        if target_path.exists():
            target_digest = sha256_file(target_path)
            status = "identical" if target_digest == digest else "conflict"
        else:
            status = "copy"
        entry = {
            "relative": relative,
            "size": size,
            "sha256": digest,
            "status": status,
            "target": str(target_path),
        }
        entries.append(entry)
        if status == "conflict":
            conflicts.append(entry)
    return entries, conflicts


def copy_one(source_file: Path, target_file: Path) -> None:
    target_file.parent.mkdir(parents=True, exist_ok=True)
    temp = target_file.with_name(target_file.name + ".migrating")
    shutil.copy2(source_file, temp)
    os.replace(temp, target_file)


def verify_target(plan: list[dict]) -> list[str]:
    failures: list[str] = []
    for entry in plan:
        target = Path(entry["target"])
        if not target.is_file():
            failures.append(f"缺失: {entry['relative']}")
            continue
        if target.stat().st_size != entry["size"]:
            failures.append(f"大小不符: {entry['relative']}")
            continue
        if sha256_file(target) != entry["sha256"]:
            failures.append(f"哈希不符: {entry['relative']}")
    return failures


def write_manifest(target: Path, source: Path, plan: list[dict], backup: dict | None, purged: bool) -> Path:
    target.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": 1,
        "generated_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "source": str(source),
        "target": str(target),
        "file_count": len(plan),
        "total_bytes": sum(entry["size"] for entry in plan),
        "backup": backup,
        "purged": purged,
        "files": [{k: entry[k] for k in ("relative", "size", "sha256", "status")} for entry in plan],
    }
    stamped = target / f"migration-manifest-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    latest = target / "migration-manifest.json"
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    stamped.write_text(text, encoding="utf-8")
    latest.write_text(text, encoding="utf-8")
    return latest


def create_backup(source: Path, backup_dir: Path) -> dict:
    backup_dir.mkdir(parents=True, exist_ok=True)
    suffix = datetime.now().strftime("%Y%m%d-%H%M%S")
    archive = backup_dir / f"backend-uploads-{suffix}.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in iter_source_files(source):
            zf.write(path, arcname=path.relative_to(source).as_posix())
    with zipfile.ZipFile(archive, "r") as zf:
        bad = zf.testzip()
        if bad is not None:
            raise RuntimeError(f"备份 CRC 校验失败: {bad}")
    return {
        "path": str(archive),
        "size": archive.stat().st_size,
        "sha256": sha256_file(archive),
    }


def purge_source(source: Path) -> None:
    for path in sorted(iter_source_files(source), key=lambda p: len(p.parts), reverse=True):
        path.unlink()
    for root, dirs, _names in os.walk(source, topdown=False):
        for name in dirs:
            directory = Path(root) / name
            try:
                directory.rmdir()
            except OSError:
                # 非空或权限不足时保留；文件已全部删除，残留目录不构成数据风险。
                pass
    # 源根本身保留为空目录，避免运行期启动器因目录缺失报错；不要删除 --source 目录。
    source.mkdir(parents=True, exist_ok=True)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="迁移 backend/uploads 到外部存储根（默认 dry-run）")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE,
                        help=f"旧上传目录，默认 {DEFAULT_SOURCE}")
    parser.add_argument("--target", type=Path, default=default_target(),
                        help=f"外部存储根，默认 {default_target()}")
    parser.add_argument("--apply", action="store_true", help="真正复制并写 manifest；缺省只打印计划")
    parser.add_argument("--purge-old", action="store_true",
                        help="复制+校验+备份后删除旧目录中的文件（必须同时 --apply）")
    parser.add_argument("--backup-dir", type=Path, default=DEFAULT_BACKUP_DIR,
                        help=f"旧目录 zip 备份位置，默认 {DEFAULT_BACKUP_DIR}")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    source = args.source.expanduser().resolve()
    target = args.target.expanduser().resolve()
    backup_dir = args.backup_dir.expanduser().resolve()

    if args.purge_old and not args.apply:
        print("[error] --purge-old 必须与 --apply 同时使用", file=sys.stderr)
        return 2
    if not source.is_dir():
        print(f"[error] 源目录不存在: {source}", file=sys.stderr)
        return 2
    if source == target or source.is_relative_to(target):
        print("[error] 源目录不能位于目标存储根内", file=sys.stderr)
        return 2

    plan, conflicts = build_plan(source, target)
    total_bytes = sum(entry["size"] for entry in plan)
    print(f"source={source}")
    print(f"target={target}")
    print(f"files={len(plan)} total_bytes={total_bytes}")
    print(f"copy={sum(1 for e in plan if e['status'] == 'copy')} "
          f"identical={sum(1 for e in plan if e['status'] == 'identical')} "
          f"conflict={len(conflicts)}")
    if conflicts:
        for entry in conflicts[:5]:
            print(f"[conflict] {entry['relative']}", file=sys.stderr)
        print("[error] 目标已存在不同内容文件，整批中止；请先人工核对", file=sys.stderr)
        return 2
    if not args.apply:
        print("[dry-run] 未复制、未删除；加 --apply 执行")
        return 0

    for entry in plan:
        if entry["status"] == "copy":
            copy_one(source / entry["relative"], Path(entry["target"]))
    failures = verify_target(plan)
    if failures:
        for failure in failures[:10]:
            print(f"[verify-failed] {failure}", file=sys.stderr)
        return 3

    backup = None
    purged = False
    if args.purge_old:
        backup = create_backup(source, backup_dir)
        print(f"backup={backup['path']} sha256={backup['sha256']}")
        purge_source(source)
        purged = True
        print(f"purged_old_files={len(plan)} source_kept_empty={source}")

    manifest = write_manifest(target, source, plan, backup, purged)
    print(f"manifest={manifest}")
    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
