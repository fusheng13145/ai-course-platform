"""CI 机械守卫：`docker` 作业必须覆盖 docker-compose.yml 的全部强制变量。

背景：`docker-compose.yml` 里用 `${VAR:?...}` 声明了 6 个「必须在场」的变量。
compose 在 `config` 阶段就对**整份文件**求值，任何一个缺失都会让 `docker compose build`
直接失败——哪怕缺变量的那个服务根本不参与 build。

2026-11-02 主干 CI #213 的 `Docker Images Build` 正是停在这条：
`required variable NEO4J_PASSWORD is missing a value`。
该作业 `if: github.ref == 'refs/heads/main'`，PR 里被 skipped，所以缺口从接入起一直没暴露。

判据（可机械复跑）：
1. compose 里每个 `${VAR:?...}` 的 VAR，都出现在 ci.yml `docker` 作业 build 步骤的 `env:` 中；
2. 反向不做断言——作业里多给的变量不算缺陷（允许预留）；
3. 阴性对照：给定一段刻意缺项的环境块，检测函数必须报出来，否则守卫本身是哑的。
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "docker-compose.yml"
CI = ROOT / ".github" / "workflows" / "ci.yml"

_REQUIRED_RE = re.compile(r"\$\{([A-Z][A-Z0-9_]*):\?")
_JOB_START_RE = re.compile(r"^  ([a-z0-9][a-z0-9-]*):\s*$")
_ENV_KEY_RE = re.compile(r"^\s+([A-Z][A-Z0-9_]*):\s*\S")


def required_vars(compose_text: str) -> set:
    """compose 文本里所有 `${VAR:?...}` 的变量名。"""
    return set(_REQUIRED_RE.findall(compose_text))


def job_block(ci_text: str, job_id: str) -> str:
    """取出 ci.yml 中某个顶层作业的原文块（到下一个顶层作业键为止）。"""
    lines = ci_text.splitlines()
    start = None
    for idx, line in enumerate(lines):
        if _JOB_START_RE.match(line) and _JOB_START_RE.match(line).group(1) == job_id:
            start = idx
            break
    if start is None:
        raise AssertionError(f"ci.yml 中找不到作业 `{job_id}`")

    end = len(lines)
    for idx in range(start + 1, len(lines)):
        if _JOB_START_RE.match(lines[idx]):
            end = idx
            break
    return "\n".join(lines[start:end])


def env_keys(block: str) -> set:
    """作业块里 `env:` 段声明的键（含缩进下的 `KEY: value` 行）。"""
    keys = set()
    in_env = False
    env_indent = None
    for line in block.splitlines():
        stripped = line.strip()
        if stripped == "env:":
            in_env = True
            env_indent = len(line) - len(line.lstrip())
            continue
        if not in_env:
            continue
        indent = len(line) - len(line.lstrip())
        if stripped and indent <= env_indent:
            # 回到 env 的同级或更高层，env 段结束
            in_env = False
            continue
        match = _ENV_KEY_RE.match(line)
        if match:
            keys.add(match.group(1))
    return keys


def missing_required_vars(compose_text: str, job_text: str) -> list:
    """compose 要求但作业 env 未声明的变量，升序返回。"""
    return sorted(required_vars(compose_text) - env_keys(job_text))


def test_docker_job_declares_every_required_compose_var():
    compose_text = COMPOSE.read_text(encoding="utf-8")
    block = job_block(CI.read_text(encoding="utf-8"), "docker")

    assert required_vars(compose_text), "compose 里没解析到任何 ${VAR:?}，守卫可能读错了文件"

    missing = missing_required_vars(compose_text, block)
    assert not missing, (
        "ci.yml 的 `docker` 作业缺少 compose 强制变量 "
        f"{missing}；compose 在 config 阶段即整体求值，缺一个就会让 build 失败"
    )


def test_env_parser_reads_only_the_job_env_section():
    """`with:`/`run:` 里以大写开头的行不能被误当成 env 键。"""
    block = """  docker:
    runs-on: ubuntu-latest
    steps:
      - name: x
        with:
          UPPER_ARG: 1
        env:
          ONLY_REAL_ONE: value
        run:
          SOME_COMMAND: 2
"""
    assert env_keys(block) == {"ONLY_REAL_ONE"}


def test_detector_catches_missing_var_negative_control():
    """阴性对照：故意缺 NEO4J_PASSWORD，检测函数必须报出它。"""
    compose_text = """
    environment:
      - NEO4J_AUTH=neo4j/${NEO4J_PASSWORD:?NEO4J_PASSWORD 必须设置}
      - JWT_SECRET=${JWT_SECRET:?JWT_SECRET 必须设置}
    """
    block = """  docker:
    steps:
      - name: Build
        env:
          JWT_SECRET: dummy
        run: docker compose build
"""
    assert missing_required_vars(compose_text, block) == ["NEO4J_PASSWORD"]
