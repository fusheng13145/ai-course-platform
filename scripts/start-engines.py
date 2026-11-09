#!/usr/bin/env python3
"""
一键拉起后端全链：Neo4j(7687) -> KG(8001) -> RAG(8002)，再交给 start.bat backend 起 Java。
=========================================================================================
为什么需要它：手册 §18.5 把引擎启动写成五条手工 cmd 步骤，三个坑各自都能把演示判成"AI 没接上"：
  1) 两个 FastAPI 服务**只读进程环境、不读 `.env`**（DR-39，全文无 dotenv），所以
     `NEO4J_URI` / `DEEPSEEK_API_KEY` / `HF_HUB_OFFLINE` 必须由启动器导出；
  2) 后端只在**启动那一刻**探测 8001/8002/7687，引擎后起则要重启后端才切 `mode=ENGINE`；
  3) 缺 `HF_HUB_OFFLINE=1` 时 sentence-transformers 会联网向 HF 校验版本，本机无外网通道时
     **不报错而是挂起**，表现为 Java 侧 60s 后返"AI 服务暂时不可用"且 sources 为空。

本脚本刻意**只补没在跑的东西**：已经在监听的端口一律不重启（双实例共用一套 MySQL/Redis
却各自计限流、日志分叉，DR-36 实测），也不会杀任何进程。缺依赖（如 Neo4j 安装位找不到）
只上报、不自行安装——本阶段 Docker 搁置，缺件一律回项目主（§17）。

用法（仓库根执行）：
    python scripts\\start-engines.py            # 拉起缺的，打印每个服务的真实判定
    python scripts\\start-engines.py --status   # 只探不看、不起任何进程
    python scripts\\start-engines.py --only kg rag
    python scripts\\start-engines.py --reseed 1 4007   # 起完再灌 KG 图与 RAG 语料（幂等）

守卫测试：`python -m pytest scripts/tests -q`（决策逻辑；起进程与等端口由真跑取证）。
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # pragma: no cover - 极老的运行时，不影响判定
        pass

ROOT = Path(__file__).resolve().parent.parent
ORDER = ("neo4j", "kg", "rag")
PORTS = {"neo4j": 7687, "kg": 8001, "rag": 8002}

# Neo4j 是仓库外的本机单机安装（§18.5）。首选 .env 的 NEO4J_HOME，其次本机已知路径；
# 都不存在则只上报，不猜路径、不装东西。
DEFAULT_NEO4J_HOMES = (r"D:\neo4j-community-5.26.12",)

DEFAULT_EMBED_MODEL = "BAAI/bge-small-zh-v1.5"
SERVICE_DIRS = {"kg": "kg_service", "rag": "rag_service"}
APP_MODULES = {"kg": "kg_service:app", "rag": "rag_service:app"}

# DR-63：引擎进程没有 dotenv，只调 os.getenv（kg_store.py:36/312-323、rag_engine.py:101-634）。
# 因此「写在 .env 里」不等于「会生效」——只有在下面这份名单里的键才会被导出到子进程。
# 名单按服务分列，与各服务实际读取的键一一对应；未配置（缺失或空串）的键不导出，让引擎用自身默认。
ENGINE_EXPORTS = {
    # 服务间内部认证（2026-11-10 V8 批）：kg/rag 的中间件在 INTERNAL_API_KEY 非空时校验
    # X-Internal-Key——启动器必须透传，否则 .env 配了钥匙、引擎却收不到（DR-63 同型盲区，
    # scripts/tests/test_start_engines.py 的导出名单护栏已钉住该漂移）。
    "kg": ("INTERNAL_API_KEY", "NEO4J_USERNAME", "NEO4J_PASSWORD", "KG_BACKEND"),
    "rag": (
        # DR-84：网关端点必须一起导出，否则 .env 里配了 base-url 也到不了引擎进程（DR-63 同型）
        "DEEPSEEK_BASE_URL",
        "DEEPSEEK_MODEL",
        # 生成后端开关与本地 Ollama（与 Java 侧同名同义；OLLAMA_THINK 留空=模型模板决定）
        "AI_PROVIDER",
        "OLLAMA_BASE_URL",
        "OLLAMA_MODEL",
        "OLLAMA_THINK",
        # R3：RAG 问答主路径的图谱增强（图在 8001；不可达时静默退化为纯检索）
        "KG_BASE_URL",
        "RAG_GRAPH_ENABLED",
        # 服务间内部认证（与 kg 同口径；为空=不校验）
        "INTERNAL_API_KEY",
        "RAG_EMBED_MODEL",
        "RAG_DATA_DIR",
        "RAG_CHUNK_SIZE",
        "RAG_CHUNK_OVERLAP",
        "HF_HOME",
        "HF_HUB_CACHE",
    ),
}

NEO4J_START_TIMEOUT = 120.0
ENGINE_START_TIMEOUT = 60.0


# ------------------------------------------------------------------ 纯判定


def parse_env(text: str) -> dict:
    """.env 文本 -> dict（跳过注释与空行，去首尾空白与成对引号）。"""
    parsed = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        parsed[key.strip()] = value
    return parsed


def effective_api_key(value) -> str | None:
    """只有形如 `sk-…` **且全 ASCII** 的值才算真密钥；占位串/含备注的值一律按未配置处理。

    DR-38 管"看着像占位串"，DR-82 管"粘了中文备注"：后者能过前缀检查，却会在引擎写
    Authorization 头时抛 UnicodeEncodeError，而旧代码整段吞掉该异常，表现为"AI 恒为抽取式"
    且无从定位。在启动器这一层就拒绝，比让请求在运行时静默失败更可诊断。
    """
    if not value:
        return None
    stripped = str(value).strip()
    if not stripped.startswith("sk-"):
        return None
    return stripped if stripped.isascii() else None


def default_exists(path: str) -> bool:
    return Path(path).is_dir()


def discover_neo4j_home(env: dict, exists=default_exists) -> str | None:
    """定位 Neo4j 安装目录。写了 NEO4J_HOME 但目录不存在时**不回落到默认位**——
    静默换库会让图谱对不上，必须显式失败并上报。"""
    configured = (env.get("NEO4J_HOME") or "").strip()
    if configured:
        return configured if exists(configured) else None
    for candidate in DEFAULT_NEO4J_HOMES:
        if exists(candidate):
            return candidate
    return None


def hf_hub_dir(env: dict) -> str:
    hf_home = (env.get("HF_HOME") or "").strip()
    if hf_home:
        return str(Path(hf_home) / "hub")
    return str(Path.home() / ".cache" / "huggingface" / "hub")


def model_snapshot_dir(hub: str, model_name: str) -> str:
    """HF 缓存的目录名规则：models--<org>--<repo>（本机实测 models--BAAI--bge-small-zh-v1.5）。"""
    return str(Path(hub) / f"models--{model_name.replace('/', '--')}")


def offline_flags(env: dict, exists=default_exists) -> dict:
    """模型已在本地缓存才设离线位；缓存缺失时保持在线，否则新机器首轮嵌入直接报错而不去下载。"""
    model = (env.get("RAG_EMBED_MODEL") or DEFAULT_EMBED_MODEL).strip()
    snapshot = model_snapshot_dir(hf_hub_dir(env), model)
    if not exists(snapshot):
        return {}
    return {"HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1"}


def should_start(enabled_flag) -> bool:
    """.env 里显式写 false/0/no 一律优先（与 start-all.py 的 backend_env() 同口径）。"""
    if enabled_flag is None:
        return True
    return str(enabled_flag).strip().lower() not in ("false", "0", "no", "off")


def plan(status: dict, want: dict) -> list:
    """按依赖顺序返回"该起但还没在跑"的服务；已在跑的绝不重启。"""
    return [name for name in ORDER if want.get(name, True) and not status.get(name)]


def engine_env(base: dict, *, service: str, neo4j_uri=None, api_key=None, offline=None, dotenv=None) -> dict:
    """引擎不读 .env，所以导出必须在这里完成。
    DEEPSEEK_API_KEY 只给 RAG（KG 侧无生成逻辑），且占位值一律不转。
    dotenv 是启动器解析出的 .env 读数（DR-63）：名单内的键由它显式导出，
    否则 .env 里配了也只会静默回落引擎默认值。"""
    env = dict(base)
    env.pop("DEEPSEEK_API_KEY", None)
    env.pop("NEO4J_URI", None)
    source = dotenv or {}
    for key in ENGINE_EXPORTS.get(service, ()):
        value = source.get(key)
        if value is not None and str(value).strip():
            env[key] = str(value).strip()
    if service == "rag":
        if api_key:
            env["DEEPSEEK_API_KEY"] = api_key
        env.update(offline or {})
    elif service == "kg" and neo4j_uri:
        env["NEO4J_URI"] = neo4j_uri
    return env


def uvicorn_command(module: str, port: int, python_exe=None) -> list:
    """用当前解释器（不是 PATH 上的 python），只绑 127.0.0.1——两个引擎都无鉴权，
    绑 0.0.0.0 等于把图谱写接口开放到局域网。"""
    return [
        python_exe or sys.executable,
        "-m",
        "uvicorn",
        module,
        "--host",
        "127.0.0.1",
        "--port",
        str(port),
    ]


def probe_url(service: str) -> str:
    """存活探针用真实存在的端点：8002 的 /health 返 404，拿它探测会把活引擎判成死的。"""
    if service == "kg":
        return "http://127.0.0.1:8001/api/v1/graph/health"
    if service == "rag":
        return "http://127.0.0.1:8002/api/v1/rag/documents"
    return "http://127.0.0.1:7474"


def neo4j_launch_command(home: str) -> list:
    """只能用 console：zip 单机版没装成 Windows 服务，`neo4j.bat start` 会直接报
    "Neo4j service is not installed / Unable to start"（2026-10-10 真跑实测）。
    console 在新窗口里前台跑，关窗即停——与引擎窗口同一姿态。"""
    return [str(Path(home) / "bin" / "neo4j.bat"), "console"]


def engine_ready(port_up: bool, health) -> bool:
    """端口在监听不足以判就绪：uvicorn 先绑口、后装载路由与模型，此时间探针拿不到响应。"""
    return bool(port_up) and health is not None


def graph_backend(health) -> str:
    """把 /graph/health 的 backend 读成可展示的事实；拿不到响应只能说 unknown，不粉饰成 neo4j。"""
    if isinstance(health, dict) and health.get("backend"):
        return str(health["backend"])
    return "unknown"


# ------------------------------------------------------------------- IO 层


def is_port_listening(port: int, host: str = "127.0.0.1", timeout: float = 0.35) -> bool:
    import socket

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(timeout)
        return sock.connect_ex((host, port)) == 0


def wait_until(check, *, timeout: float, interval: float = 1.0, sleep=time.sleep, now=time.monotonic):
    start = now()
    while True:
        if check():
            return True
        if now() - start >= timeout:
            return False
        sleep(interval)


def http_json(url: str, timeout: float = 2.0):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            if resp.status != 200:
                return None
            return json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


def spawn_detached(cmd: list, cwd: Path, env: dict, log_path: Path | None = None) -> subprocess.Popen:
    """新窗口里跑 uvicorn，日志留在窗口内可滚；父脚本退出后进程继续存活。"""
    flags = 0
    if os.name == "nt":
        flags = getattr(subprocess, "CREATE_NEW_CONSOLE", 0)
        popen = subprocess.Popen(cmd, cwd=str(cwd), env=env, creationflags=flags)
    else:  # pragma: no cover - 本机为 Windows，这里只是不炸
        handle = open(log_path, "ab") if log_path else subprocess.DEVNULL
        popen = subprocess.Popen(cmd, cwd=str(cwd), env=env, stdout=handle, stderr=handle)
    time.sleep(1.0)  # 让窗口起来；真正的判定等端口
    return popen


def start_neo4j(home: str, env: dict) -> bool:
    bat = Path(home) / "bin" / "neo4j.bat"
    if not bat.exists():
        print(f"[FAIL] 找不到 {bat}")
        return False
    spawn_detached(neo4j_launch_command(home), Path(home), env)
    return wait_until(lambda: is_port_listening(PORTS["neo4j"]), timeout=NEO4J_START_TIMEOUT)


def probe(service: str):
    return http_json(probe_url(service), timeout=3.0)


def wait_for_engine(service: str, *, timeout: float = ENGINE_START_TIMEOUT) -> bool:
    return wait_until(lambda: engine_ready(is_port_listening(PORTS[service]), probe(service)),
                      timeout=timeout)


def start_engine(service: str, env: dict) -> bool:
    workdir = ROOT / "python-services" / SERVICE_DIRS[service]
    cmd = uvicorn_command(APP_MODULES[service], PORTS[service])
    if not workdir.is_dir():
        print(f"[FAIL] 服务目录不存在：{workdir}")
        return False
    spawn_detached(cmd, workdir, env, workdir / f"{service}-engine.log")
    return wait_for_engine(service)


# ------------------------------------------------------------------ 状态与主流程


def merged_env() -> dict:
    env = dict(os.environ)
    dotenv = ROOT / ".env"
    if dotenv.exists():
        env.update(parse_env(dotenv.read_text(encoding="utf-8", errors="replace")))
    return env


def status_snapshot() -> dict:
    return {name: is_port_listening(port) for name, port in PORTS.items()}


def describe(service: str, up: bool) -> str:
    if not up:
        return "DOWN"
    if service == "kg":
        return f"UP (backend={graph_backend(probe('kg'))})"
    if service == "rag":
        docs = probe("rag")
        if docs is None:
            return "UP (端口在监听，/api/v1/rag/documents 未响应)"
        items = docs.get("documents") if isinstance(docs, dict) else docs
        count = len(items) if isinstance(items, list) else "?"
        return f"UP (语料文档数={count})"
    return "UP"


def run_reseed(courses: list) -> None:
    args = [sys.executable, str(ROOT / "scripts" / "sync-kg-to-python.py")] + courses
    print(f"$ {' '.join(args)}")
    subprocess.run(args, cwd=str(ROOT), check=False)
    args = [sys.executable, str(ROOT / "scripts" / "seed-rag-corpus.py")] + courses
    print(f"$ {' '.join(args)}")
    subprocess.run(args, cwd=str(ROOT), check=False)


def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    only = list(ORDER)
    courses: list[str] = []
    reseed = False
    status_only = False

    idx = 0
    while idx < len(argv):
        token = argv[idx]
        if token == "--status":
            status_only = True
        elif token == "--reseed":
            reseed = True
        elif token == "--only":
            idx += 1
            only = []
            while idx < len(argv) and not argv[idx].startswith("--"):
                only.append(argv[idx])
                idx += 1
            idx -= 1
        elif token.lstrip("-").isdigit():
            courses.append(token)
        idx += 1

    env = merged_env()
    status = status_snapshot()

    if status_only:
        for name in ORDER:
            print(f"{name:6s} port {PORTS[name]}: {describe(name, status[name])}")
        return 0

    neo4j_home = discover_neo4j_home(env)
    want = {
        "neo4j": should_start(env.get("NEO4J_ENABLED")) and bool(neo4j_home),
        "kg": should_start(env.get("KG_ENABLED")),
        "rag": should_start(env.get("RAG_ENABLED")),
    }
    want = {name: want[name] and name in only for name in ORDER}
    todo = plan(status, want)

    api_key = effective_api_key(env.get("DEEPSEEK_API_KEY"))
    offline = offline_flags(env)
    uri = (env.get("KG_NEO4J_URI") or env.get("NEO4J_URI") or "bolt://127.0.0.1:7687").strip()

    print("=" * 60)
    print(" AI Course Platform - engines (Neo4j / KG / RAG)")
    print("=" * 60)
    if not todo:
        print("[OK] 三个引擎端口都已在监听，本次不重复拉起任何进程。")
    for name in todo:
        if name == "neo4j":
            print(f"[..] 启动 Neo4j：{neo4j_home}")
            ok = start_neo4j(neo4j_home, env)
        else:
            exported = engine_env(
                os.environ,
                service=name,
                neo4j_uri=uri,
                api_key=api_key,
                offline=offline,
                dotenv=env,
            )
            print(f"[..] 启动 {name.upper()} 引擎（:{PORTS[name]}，新窗口，日志在窗口内）")
            ok = start_engine(name, exported)
        print(f"[{'OK' if ok else 'FAIL'}] {name} {'已监听' if ok else '未在预期时间内监听'}")

    final = status_snapshot()
    if want["neo4j"] and not neo4j_home and "kg" in only:
        print(
            "[WARN] 找不到 Neo4j 安装目录（.env 的 NEO4J_HOME 或 "
            f"{DEFAULT_NEO4J_HOMES[0]}）。KG 会回落内存图，重启即丢。"
            " 请把 NEO4J_HOME=<安装目录> 写进 .env；本机没有图库请回项目主，不要拉容器（§17）。"
        )
    provider = env.get("AI_PROVIDER") or os.getenv("AI_PROVIDER", "deepseek")
    if provider.strip().lower() in ("ollama", "local"):
        print(f"[INFO] 生成后端为本地 Ollama（{env.get('OLLAMA_MODEL') or 'deepseek-r1:8b'}），"
              "无需 DEEPSEEK_API_KEY；Ollama 不可用时 RAG 仍按抽取式降级。")
    elif api_key is None:
        print("[WARN] DEEPSEEK_API_KEY 未配置或形如占位串 -> RAG 恒为抽取式回答（§40），不阻塞启动。")
    if offline:
        print("[OK]   嵌入模型已在本机缓存 -> 导出 HF_HUB_OFFLINE=1（缺这一步会让检索挂死，§18.5）")
    else:
        print("[INFO] 嵌入模型未缓存 -> 不设离线位，首次检索会尝试下载模型（需外网）。")

    print("-" * 60)
    for name in ORDER:
        print(f"{name:6s} :{PORTS[name]}  {describe(name, final[name])}")
    print("-" * 60)

    if is_port_listening(8080) and todo:
        print(
            "[WARN] 后端已在跑而引擎刚起：Java 只在启动那一刻探测引擎，"
            "需重启 start.bat backend 才会切到 mode=ENGINE。"
        )
    if final["kg"] and not final["neo4j"]:
        print("[INFO] Neo4j 端口未起 -> KG 只能是内存图或尚未初始化，见上一行 backend 判定")
    kg_backend = graph_backend(probe("kg")) if final["kg"] else "down"
    if kg_backend == "memory":
        hint = "（Neo4j 本次刚起，KG 是更早起的进程，需关掉它的窗口重跑 start-engines.py 才会接上图库）" if "neo4j" in todo else ""
        print(f"[WARN] KG 走的是内存图{hint} -> 图谱重启即丢，接上后重跑 python scripts\\sync-kg-to-python.py 1 4007")
    if reseed:
        run_reseed(courses or ["1", "4007"])
    elif final["rag"]:
        docs = probe("rag")
        items = docs.get("documents") if isinstance(docs, dict) else docs
        if isinstance(items, list) and not items:
            print("[INFO] RAG 语料为空 -> 问答会返「AI 服务暂时不可用」。重放：python scripts\\seed-rag-corpus.py")

    wanted = [name for name in ("kg", "rag") if want.get(name)]
    missing = [name for name in wanted if not final.get(name)]
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
