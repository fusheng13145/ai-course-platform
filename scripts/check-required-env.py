#!/usr/bin/env python3
# =============================================================================
# AI Course Platform - 上线必填键检查（A 路径 #5）
#
# 检查 docker-compose.yml 中所有 ${VAR:?...} 强制变量是否已在 .env 中给出
# 真实值；并明确打印「generate-secrets.py 不生成、必须人工填写」的键。
#
# 设计约束：
#   1. 必填键集合从 docker-compose.yml 解析，不在脚本里维护第二份清单。
#   2. 自动生成范围从 scripts/generate-secrets.py 的 SPEC / POLICY_KEYS 解析，
#      避免“文档说会生成、实际没生成”的漂移。
#   3. 只读，不写 .env、不生成密钥；不做任何破坏性操作。
#   4. 有 docker 时额外跑 `docker compose config -q`，验证 compose 插值闭环；
#      没有 docker 时该项记为 SKIP（不伪装通过）。
#
# 用法：
#   python scripts/check-required-env.py
#   python scripts/check-required-env.py --env-file /opt/aicourse/.env
#   python scripts/check-required-env.py --json
#
# 退出码：0 全部通过；1 存在缺失/空值/占位值/格式不符或 compose 插值失败。
#
# 状态：2026-11-05 本机无真实 .env 与 Docker，脚本以临时夹具做过逻辑自测；
#      真实部署环境的通过记录须由运维在真机执行后留存。
# =============================================================================
import argparse
import json
import pathlib
import re
import shutil
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]

FALLBACK_REQUIRED = [
    "MYSQL_ROOT_PASSWORD",
    "REDIS_PASSWORD",
    "JWT_SECRET",
    "APP_ADMIN_INITIAL_PASSWORD",
    "NEO4J_PASSWORD",
    "GRAFANA_ADMIN_PASSWORD",
]

# 与 backend/src/main/java/com/aicourse/util/PasswordPolicy.java 一致的镜像正则。
PASSWORD_POLICY = re.compile(
    r"""^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}\[\]:;"'<>,.?/\\|`]).{8,32}$"""
)

PLACEHOLDER_RE = re.compile(
    r"(change[-_]?me|changeme|your[-_]|placeholder|todo|xxxx|ci-build-only|<[^>]+>)",
    re.IGNORECASE,
)

# 不是 ${:?} 必填键、但属于人工凭证的键：生成脚本不处理，建议显式设置。
OPTIONAL_MANUAL_KEYS = ["MYSQL_APP_PASSWORD"]


def parse_env(path: pathlib.Path) -> dict:
    values = {}
    if not path.exists():
        return values
    # utf-8-sig：Windows PowerShell 5 的 `Set-Content -Encoding UTF8` 会写 BOM，
    # 不剥掉会把首行的 KEY 读成 "\ufeffKEY" 而误报缺失。
    for raw in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key.startswith("export "):
            key = key[len("export "):].strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values


def parse_required_keys(compose_path: pathlib.Path) -> list:
    if not compose_path.exists():
        return list(FALLBACK_REQUIRED)
    text = compose_path.read_text(encoding="utf-8", errors="replace")
    # 只取 `${VAR:?...}` 强制变量。若写成 `${VAR:?` 会把 `${VAR:-默认值}` 也吞进来
    # （曾实测把 80 个带默认值的键误判为必填，2026-11-05 修正）。
    keys = sorted(set(re.findall(r"\$\{([A-Z][A-Z0-9_]*):\?", text)))
    return keys or list(FALLBACK_REQUIRED)


def parse_generated_keys(generator_path: pathlib.Path) -> set:
    if not generator_path.exists():
        return set()
    text = generator_path.read_text(encoding="utf-8", errors="replace")
    blocks = []
    for name in ("SPEC", "POLICY_KEYS"):
        match = re.search(name + r"\s*=\s*\{([^}]*)\}", text, re.S)
        if match:
            blocks.append(match.group(1))
    return set(re.findall(r'"([A-Z][A-Z0-9_]*)"', "\n".join(blocks)))


def validate(name: str, value: str) -> str | None:
    """返回错误原因；通过时返回 None。"""
    if value is None or value == "":
        return "缺失或为空"
    if PLACEHOLDER_RE.search(value):
        return "仍是占位值"
    if name == "JWT_SECRET" and len(value.encode("utf-8")) < 32:
        return "UTF-8 字节数 < 32（JwtUtils 下限）"
    if name == "APP_ADMIN_INITIAL_PASSWORD" and not PASSWORD_POLICY.match(value):
        return "不满足 PasswordPolicy（8-32 位，含大小写/数字/特殊字符）"
    return None


def compose_config_check(compose_path: pathlib.Path, env_path: pathlib.Path) -> tuple:
    """返回 (状态, 说明)。状态：PASS / SKIP / FAIL。"""
    if shutil.which("docker") is None:
        return "SKIP", "docker CLI 不可用，未执行 docker compose config -q"
    cmd = ["docker", "compose"]
    if env_path.exists():
        cmd += ["--env-file", str(env_path)]
    cmd += ["-f", str(compose_path), "config", "-q"]
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(compose_path.parent),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=60,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return "FAIL", f"执行失败：{exc}"
    if proc.returncode == 0:
        return "PASS", "docker compose config -q 通过"
    detail = (proc.stderr or proc.stdout or "").strip().splitlines()
    return "FAIL", detail[-1] if detail else f"退出码 {proc.returncode}"


def main() -> int:
    parser = argparse.ArgumentParser(description="检查上线必填环境变量（A 路径 #5）")
    parser.add_argument("--env-file", default=str(REPO / ".env"))
    parser.add_argument("--compose", default=str(REPO / "docker-compose.yml"))
    parser.add_argument("--generator", default=str(REPO / "scripts" / "generate-secrets.py"))
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    args = parser.parse_args()

    env_path = pathlib.Path(args.env_file)
    compose_path = pathlib.Path(args.compose)
    generator_path = pathlib.Path(args.generator)

    required = parse_required_keys(compose_path)
    generated = parse_generated_keys(generator_path)
    values = parse_env(env_path)

    rows = []
    failed = False
    for name in required:
        reason = validate(name, values.get(name))
        ok = reason is None
        failed = failed or not ok
        rows.append({
            "key": name,
            "status": "PASS" if ok else "FAIL",
            "reason": reason or "",
            "auto_generated_by_script": name in generated,
        })

    compose_status, compose_detail = compose_config_check(compose_path, env_path)
    if compose_status == "FAIL":
        failed = True

    manual_required = sorted(set(required) - generated)
    result = {
        "env_file": str(env_path),
        "env_file_exists": env_path.exists(),
        "compose_file": str(compose_path),
        "required_keys": rows,
        "compose_config": {"status": compose_status, "detail": compose_detail},
        "generated_keys": sorted(generated),
        "manual_required_keys": manual_required,
        "optional_manual_keys": OPTIONAL_MANUAL_KEYS,
        "failed": failed,
    }

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if failed else 0

    print(f"[A#5] 必填键检查：{env_path}")
    if not env_path.exists():
        print("  [WARN] .env 不存在；请先 cp .env.example .env 并填写。")
    print(f"  必填键来源：{compose_path}（共 {len(required)} 个 ${{\u2026:?}}）")
    print("")
    for row in rows:
        mark = "OK  " if row["status"] == "PASS" else "FAIL"
        extra = "" if row["auto_generated_by_script"] else "（脚本不自动生成）"
        print(f"  [{mark}] {row['key']}{extra}" + (f"：{row['reason']}" if row["reason"] else ""))

    print("")
    print(f"  [compose config] {compose_status}：{compose_detail}")
    print("")
    if manual_required:
        print("  [需人工填] 必填键（数量取自 compose，勿在此硬编码）中 generate-secrets.py 不生成：")
        for key in manual_required:
            print(f"    - {key}")
    else:
        print("  [需人工填] 必填键均已由 generate-secrets.py 覆盖。")
    print("  [另需人工填] 非 ${:?} 必填，但建议显式设置（否则可能回落到 root）：")
    for key in OPTIONAL_MANUAL_KEYS:
        print(f"    - {key}")

    print("")
    if failed:
        print("[RESULT] FAIL：请补齐/替换上述条目后重跑。")
        return 1
    print("[RESULT] PASS：必填键齐全且非占位值" +
          "（compose 插值已由 docker compose config -q 验证）。" if compose_status == "PASS"
          else "[RESULT] PASS（静态检查）；compose 插值未取证（docker 不可用）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
