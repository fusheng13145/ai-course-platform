"""DR-83 量具：现用 .env 密钥是否**等于**曾提交进 git 历史的值。

背景：F-02 长期声称"git 历史擦除 + 密钥轮换已完成"，但本轮实测 `dbafa82:.env` 仍在
`origin/main` 的祖先链上，且其中 `NEO4J_PASSWORD` 与**当前在用的值相同**。
只要"已轮换"没有机械判据，这种论断就能一直成立——所以本文件把它变成可复跑的读数。
判据只输出**键名与布尔**，绝不输出值本身或其任何前缀。
"""
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "audit_stats", ROOT / "scripts" / "audit-stats.py")
audit_stats = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_stats)


def test_keys_equal_to_history_flags_reuse_only():
    current = {
        "NEO4J_PASSWORD": "still-live",     # 历史里出现过且现在还在用
        "MYSQL_APP_PASSWORD": "rotated-ok",  # 历史里出现过旧值，但已换掉
        "JWT_SECRET": "",                    # 空值不参与判定
        "DEEPSEEK_API_KEY": "never-seen",
    }
    history = {
        "NEO4J_PASSWORD": ["still-live"],
        "MYSQL_APP_PASSWORD": ["old-value"],
        "JWT_SECRET": [""],
    }
    assert audit_stats.keys_equal_to_history(current, history) == ["NEO4J_PASSWORD"]


def test_keys_equal_to_history_ignores_empty_and_whitespace_only():
    """历史里的空值不能把"当前也为空"判成泄露复用，否则读数全是噪声。"""
    assert audit_stats.keys_equal_to_history(
        {"A": "", "B": "  "}, {"A": ["", "  "], "B": ["  "]}) == []


def test_keys_equal_to_history_matches_multiple_history_commits():
    current = {"NEO4J_PASSWORD": "ui", "X": "keep"}
    history = {"NEO4J_PASSWORD": ["v1", "ui"]}
    assert audit_stats.keys_equal_to_history(current, history) == ["NEO4J_PASSWORD"]


def test_credential_keys_narrow_to_secrets_only():
    """DR-83 判据只作用于**凭据类**键名。

    第一版把 NEO4J_URI / MODEL / LLM_PROVIDER 这类标识符也算进来，实测一次命中 7 键——
    其中 6 键根本不值轮换，于是"轮换成功后应为无"永远达不到，读数会被当噪声忽略。
    口径收紧后必须只剩口令/密钥类键。
    """
    all_keys = {
        "NEO4J_PASSWORD": "x", "MYSQL_ROOT_PASSWORD": "y", "JWT_SECRET": "z",
        "DEEPSEEK_API_KEY": "k", "GRAFANA_ADMIN_PASSWORD": "g", "APP_A_TOKEN": "t",
        "NEO4J_URI": "bolt://x", "NEO4J_USERNAME": "neo4j", "MODEL": "deepseek-r1:7b",
        "OLLAMA_API_URL": "http://x", "DEEPSEEK_MODEL": "deepseek-chat",
        "BACKUP_RETENTION_DAYS": "7",
    }
    assert audit_stats.credential_keys(all_keys) == [
        "APP_A_TOKEN", "DEEPSEEK_API_KEY", "GRAFANA_ADMIN_PASSWORD", "JWT_SECRET",
        "MYSQL_ROOT_PASSWORD", "NEO4J_PASSWORD",
    ]
