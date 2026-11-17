import importlib.util
import json
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "migrate-uploads.py"
spec = importlib.util.spec_from_file_location("migrate_uploads", MODULE_PATH)
migrate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate)


def _seed(source: Path) -> None:
    (source / "2026" / "10").mkdir(parents=True)
    (source / "a.pdf").write_bytes(b"%PDF-1.4")
    (source / "2026" / "10" / "b.txt").write_text("hello", encoding="utf-8")


def test_dry_run_does_not_copy(tmp_path):
    source = tmp_path / "backend" / "uploads"
    target = tmp_path / "storage"
    _seed(source)

    assert migrate.main(["--source", str(source), "--target", str(target)]) == 0
    assert not (target / "a.pdf").exists()
    assert not target.exists()


def test_apply_copies_and_writes_manifest(tmp_path):
    source = tmp_path / "uploads"
    target = tmp_path / "storage"
    _seed(source)

    assert migrate.main(["--source", str(source), "--target", str(target), "--apply"]) == 0

    assert (target / "a.pdf").read_bytes() == b"%PDF-1.4"
    manifest = json.loads((target / "migration-manifest.json").read_text(encoding="utf-8"))
    assert manifest["file_count"] == 2
    assert manifest["purged"] is False
    assert {entry["relative"] for entry in manifest["files"]} == {"a.pdf", "2026/10/b.txt"}


def test_second_run_is_idempotent(tmp_path):
    source = tmp_path / "uploads"
    target = tmp_path / "storage"
    _seed(source)

    assert migrate.main(["--source", str(source), "--target", str(target), "--apply"]) == 0
    assert migrate.main(["--source", str(source), "--target", str(target), "--apply"]) == 0
    manifest = json.loads((target / "migration-manifest.json").read_text(encoding="utf-8"))
    assert all(entry["status"] == "identical" for entry in manifest["files"])


def test_conflict_aborts_without_overwriting(tmp_path):
    source = tmp_path / "uploads"
    target = tmp_path / "storage"
    _seed(source)
    target.mkdir()
    (target / "a.pdf").write_bytes(b"different")

    assert migrate.main(["--source", str(source), "--target", str(target), "--apply"]) == 2
    assert (target / "a.pdf").read_bytes() == b"different"


def test_purge_requires_apply(tmp_path):
    source = tmp_path / "uploads"
    target = tmp_path / "storage"
    _seed(source)
    assert migrate.main(["--source", str(source), "--target", str(target), "--purge-old"]) == 2
    assert (source / "a.pdf").exists()


def test_purge_backs_up_then_clears_source(tmp_path):
    source = tmp_path / "uploads"
    target = tmp_path / "storage"
    backup = tmp_path / "backup"
    _seed(source)

    assert migrate.main([
        "--source", str(source), "--target", str(target),
        "--apply", "--purge-old", "--backup-dir", str(backup),
    ]) == 0

    assert (target / "a.pdf").read_bytes() == b"%PDF-1.4"
    assert not (source / "a.pdf").exists()
    assert source.is_dir()
    manifest = json.loads((target / "migration-manifest.json").read_text(encoding="utf-8"))
    assert manifest["purged"] is True
    archive = Path(manifest["backup"]["path"])
    assert archive.is_file()
    with zipfile.ZipFile(archive) as zf:
        assert zf.testzip() is None
        assert zf.read("a.pdf") == b"%PDF-1.4"
