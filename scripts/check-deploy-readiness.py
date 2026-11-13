#!/usr/bin/env python3
# =============================================================================
# AI Course Platform - 上线补件静态自检入口（挂账清单汇总）
#
# 目的：把 A#5/#6/#7/#8/#12、CI 部署、镜像构建这批「脚本已备但未真机验证」的
#   件，收敛成一个可重复执行的静态自检入口，让「有脚本」至少有可重放的证据。
#
# 边界（必须遵守）：
#   - 只做静态检查：文件存在、Python 语法、Shell 语法、脚本对**夹具**的行为、
#     YAML/模板占位检查。**不接触真实主机、不调用 docker、不产生任何验收结论**。
#   - 没有真实 .env / 域名 / 证书时，相关项记为 SKIP 或 PENDING，绝不记为 PASS。
#   - 默认退出码：0 = 无 FAIL（存在 SKIP/PENDING 仍可 0）；1 = 有 FAIL。
#     --strict 时 SKIP/PENDING 也返回 1，供真实主机上线前使用。
#
# 用法：
#   python scripts/check-deploy-readiness.py
#   python scripts/check-deploy-readiness.py --json
#   python scripts/check-deploy-readiness.py --env-file /opt/aicourse/.env
#   python scripts/check-deploy-readiness.py --strict      # 真机上线前
#
# 状态：2026-11-05 新增。本机为 Windows，无真实域名/证书；默认路径下的
#   SKIP/PENDING 属预期，不代表任何上线项已验收。
# =============================================================================
import argparse
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

REPO = pathlib.Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "scripts/check-required-env.py",
    "scripts/check-https-ws-config.py",
    "scripts/fix-volume-owner.sh",
    "scripts/restore-mysql.sh",
    "scripts/verify-uploads-sha256.sh",
    "scripts/build_images.sh",
    "deploy/env.production.template",
    "deploy/docker-compose.ghcr.yml",
    ".github/workflows/deploy.yml",
    "docs/恢复演练记录模板.md",
    "docs/异地备份与TLS件方案骨架.md",
    "docs/B-12-P-08-拆包与排期方案.md",
    "docs/组织映射清单模板.md",
]

PY_SCRIPTS = [
    "scripts/check-required-env.py",
    "scripts/check-https-ws-config.py",
    "scripts/check-deploy-readiness.py",
]

SH_SCRIPTS = [
    "scripts/fix-volume-owner.sh",
    "scripts/restore-mysql.sh",
    "scripts/verify-uploads-sha256.sh",
    "scripts/build_images.sh",
]

results = []


def record(name, status, detail):
    results.append({"name": name, "status": status, "detail": detail})


def check_files():
    missing = [p for p in REQUIRED_FILES if not (REPO / p).is_file()]
    if missing:
        record("补件文件存在", "FAIL", "缺失：" + ", ".join(missing))
    else:
        record("补件文件存在", "PASS", f"{len(REQUIRED_FILES)} 个文件均存在")


def check_python_syntax():
    bad = []
    for rel in PY_SCRIPTS:
        path = REPO / rel
        try:
            compile(path.read_text(encoding="utf-8"), str(path), "exec")
        except SyntaxError as exc:
            bad.append(f"{rel}:{exc.lineno} {exc.msg}")
    if bad:
        record("Python 语法", "FAIL", "; ".join(bad))
    else:
        record("Python 语法", "PASS", f"{len(PY_SCRIPTS)} 个脚本可编译")


def find_bash():
    """Windows 原生 PATH 上的 bash 多为 WSL，无法直接吃 C:\\ 路径；优先用 Git Bash。"""
    if sys.platform == "win32":
        for candidate in (
            r"C:\Program Files\Git\bin\bash.exe",
            r"C:\Program Files (x86)\Git\bin\bash.exe",
        ):
            if pathlib.Path(candidate).is_file():
                return candidate
        return None
    return shutil.which("bash")


def check_shell_syntax():
    bash = find_bash()
    if not bash:
        record("Shell 语法", "SKIP", "本机无可用 bash（Windows 原生 cmd/PowerShell 不能直接 -n）")
        return
    bad = []
    for rel in SH_SCRIPTS:
        proc = subprocess.run(
            [bash, "-n", str(REPO / rel)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if proc.returncode != 0:
            bad.append(f"{rel}: {(proc.stderr or '').strip()[:200]}")
    if bad:
        record("Shell 语法", "FAIL", "; ".join(bad))
    else:
        record("Shell 语法", "PASS", f"{len(SH_SCRIPTS)} 个脚本 bash -n 通过")


def check_https_static_fixture():
    """用临时夹具跑 HTTPS/WS 静态分支，验证脚本本身可执行。

    夹具全部是示例值，不是验收；脚本预期退出码 2（静态通过、运行期 PENDING）。
    """
    fixture = "\n".join(
        [
            "REQUIRE_HTTPS=true",
            "COOKIE_SECURE=true",
            "CSRF_ENABLED=true",
            "TRUST_PROXY_HEADER=true",
            "CORS_ALLOWED_ORIGINS=https://course.example.com",
            "APP_CORS_DEV_FALLBACK=false",
            "",
        ]
    )
    with tempfile.TemporaryDirectory() as tmp:
        env_file = pathlib.Path(tmp) / "fixture.env"
        env_file.write_text(fixture, encoding="utf-8")
        proc = subprocess.run(
            [
                sys.executable,
                str(REPO / "scripts/check-https-ws-config.py"),
                "--env-file",
                str(env_file),
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    if proc.returncode == 2:
        record("A#7/#8 静态夹具", "PENDING", "静态分支通过；运行期需真实域名/证书（退出码 2）")
    elif proc.returncode == 0:
        record("A#7/#8 静态夹具", "PASS", "夹具静态检查通过（仍非真实验收）")
    else:
        record(
            "A#7/#8 静态夹具",
            "FAIL",
            (proc.stdout + proc.stderr).strip()[-400:] or f"退出码 {proc.returncode}",
        )


def check_required_env(env_file):
    if not env_file:
        record("A#5 必填键", "SKIP", "未指定 --env-file（避免把开发 .env 当生产环境检查）")
        return
    env_path = pathlib.Path(env_file)
    if not env_path.is_file():
        record("A#5 必填键", "FAIL", f"指定的 env 文件不存在：{env_path}")
        return
    proc = subprocess.run(
        [sys.executable, str(REPO / "scripts/check-required-env.py"), "--env-file", str(env_path)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode == 0:
        record("A#5 必填键", "PASS", "目标 env 通过必填键检查（仍需真机 compose 复验）")
    else:
        record("A#5 必填键", "FAIL", (proc.stdout + proc.stderr).strip()[-400:])


def check_docker_compose(env_file):
    docker = shutil.which("docker")
    if not docker:
        record("compose 插值", "SKIP", "本机无 docker CLI")
        return
    if not env_file or not pathlib.Path(env_file).is_file():
        record("compose 插值", "SKIP", "未指定 --env-file，compose 插值无法成立")
        return
    proc = subprocess.run(
        [docker, "compose", "--env-file", str(env_file), "-f", "docker-compose.yml", "config", "-q"],
        cwd=str(REPO),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode == 0:
        record("compose 插值", "PASS", "docker compose config -q 通过")
    else:
        record("compose 插值", "FAIL", (proc.stderr or "").strip()[-400:])


def check_template_placeholders():
    text = (REPO / "deploy/env.production.template").read_text(encoding="utf-8")
    if "<真实域名>" in text:
        record("生产模板占位", "PENDING", "模板含 <真实域名> 等占位，填写后才可上线")
    else:
        record("生产模板占位", "PASS", "真实域名占位未见（确认未被真实值覆盖）")


def main():
    parser = argparse.ArgumentParser(description="上线补件静态自检（不含真实验收）")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    parser.add_argument("--strict", action="store_true", help="SKIP/PENDING 也视为失败")
    parser.add_argument(
        "--env-file",
        default=None,
        help="目标部署环境的 env 文件；只有显式给出才做 A#5 与 compose 插值检查",
    )
    args = parser.parse_args()

    check_files()
    check_python_syntax()
    check_shell_syntax()
    check_https_static_fixture()
    check_required_env(args.env_file)
    check_docker_compose(args.env_file)
    check_template_placeholders()

    failures = [r for r in results if r["status"] == "FAIL"]
    pending = [r for r in results if r["status"] in ("SKIP", "PENDING")]

    if args.json:
        print(json.dumps({"results": results, "failures": len(failures), "pending": len(pending)}, ensure_ascii=False, indent=2))
    else:
        print("上线补件静态自检（PASS≠真机验收）")
        print("-" * 52)
        for r in results:
            print(f"[{r['status']:<7}] {r['name']}: {r['detail']}")
        print("-" * 52)
        print(f"FAIL {len(failures)} / SKIP+PENDING {len(pending)} / 共 {len(results)}")

    if failures:
        return 1
    if args.strict and pending:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
