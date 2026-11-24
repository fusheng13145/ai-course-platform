#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
单窗口统一启动器：一个控制台拉起完整演示栈（或按子命令只起后端 / 只起前端）。
=================================================================
为什么要它：旧的两份分步启动器（`start-backend.bat` + `start-frontend.bat`，已合并进本文件）
的结构本身就是 **多个 cmd 窗口**：
  ① start-backend.bat 自己（第 6 步 `mvnw.cmd spring-boot:run` 前台占住控制台）
  ②③④ scripts/start-engines.py 的 `spawn_detached()` 用 `CREATE_NEW_CONSOLE` 给
     Neo4j console / KG / RAG 各开一个新窗口
  ⑤ start-frontend.bat 自己（Vite 前台）
现在把 **Ollama / MinIO / Neo4j / KG / RAG / LoRA 微调 / Spring Boot / Vite** 全部收进
同一控制台，逐行加服务前缀，同时各自落一份 `logs/<name>.log`（仓库根 `.gitignore`
已忽略 `*.log`）。MySQL 与 Redis 仍是本机服务，但脚本会在端口未监听时**尝试启动
Windows 服务/Linux systemd 服务**，不再要求用户先手动起库。

与旧三份启动器的关系：**本文件是唯一实现**。`start-all.bat` / `start-backend.bat` /
`start-frontend.bat` 已于 2026-10-27 合并成一份 `start.bat`（只做 python 定位与子命令透传），
batch 里那份"注入口径 + 择口 + 探活"的复刻随之删除——两套并行逻辑必然分叉。
判定层直接复用 `scripts/start-engines.py`（文件名带连字符，不能用 import 语句，故用
importlib 显式加载），`.env` 解析、`sk-` 形状门、离线位门、"只补没在跑的"这些已经过
真跑取证的规则不复制第二份。

语义口径（与手册 §17/§18.5/§25、DR-36/DR-37/DR-38/DR-39/DR-50 对齐）：
  - **接管不重启**：端口已在监听且探针认主的服务一律不拉起、不杀（双实例共用一套
    MySQL/Redis 却各自计限流、日志分叉，DR-36 实测）；收尸清单只含本启动器真正起的
    子进程。8080 被别的应用占用但 `/probe` 不认主时直接报冲突，不把别人的后端当成本项目。
  - **子树收尸**：`mvnw.cmd` 下面挂着 java，只杀父会让 java 被重生并抢不到 8080（本机实测），
    因此 Ctrl+C 走 `taskkill /T /F`。
  - **依赖可自愈**：MySQL(3306)/Redis(6379) 端口未监听时先尝试启动本机服务；仍失败才
    fail-fast 并回项目主，绝不拉容器（本阶段 Docker 搁置，§17）。
  - **全量服务**：`all/backend` 默认编排 MinIO、Ollama（当 AI/FT 需要时）、Neo4j、KG、
    RAG、LoRA 微调、Spring Boot；`frontend` 只起 Vite。
  - **就绪判据**：引擎=端口在听**且**探针有响应（uvicorn 先绑口后装载）；MinIO=`/minio/health/live`
    200；Ollama=`/api/tags` 200；后端=`/probe` 返 200（复合 `/health` 无 Key 恒 503，
    拿它判存活会把健康后端判成死，DR-28）；前端=5173/5174/5175 双栈探测。
  - **按子命令收窄依赖门**：`frontend` 不需要 JDK/MySQL/Redis/MinIO，只查 Node。

用法（仓库根双击 `start.bat`，或手动）：
    python scripts\\start-all.py               # = all：拉起缺的，接管在跑的，一个窗口看全部日志
    python scripts\\start-all.py backend       # 引擎 + MinIO + 微调 + 后端
    python scripts\\start-all.py frontend      # 只起 Vite（原 start-frontend.bat 的姿态）
    python scripts\\start-all.py status        # 只报全量服务状态，不起任何进程、不注入环境（同 --status）
    python scripts\\start-all.py --no-color     # 控制台不支持 ANSI 时用纯文本前缀
    start.bat backend --no-color               # 薄壳把子命令与开关原样透传
    API_PORT=8081 python scripts\\start-all.py  # 8080 被别的项目占用时的并行口方案

守卫测试：`python -m pytest scripts/tests -q`（决策与形态；起进程与等端口由真跑取证）。
"""

from __future__ import annotations

import ctypes
import importlib.util
import os
import queue
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # pragma: no cover - 极老的运行时，不影响判定
        pass

ROOT = Path(__file__).resolve().parent.parent
LOG_DIR = ROOT / "logs"

# 依赖序即启动序：Ollama（AI/FT 本地生成）→ MinIO（对象存储）→ Neo4j →
# KG → DeepGraph(8501，内嵌原 8002 知识库) → LoRA 微调 → Java → 前端（代理要能打到 API）
ENGINE_SERVICES = ("neo4j", "kg", "deepgraph", "ft", "hub")
SERVICES = ("ollama", "minio", *ENGINE_SERVICES, "api", "web")
# deepgraph(8501)：已验收 DeepSeek-RAG-Chatbot 原样复制 + api_server.py 契约适配器（2026-11-21 接入）；
# 2026-11-24（批次 3B-2）起同一进程同时承载知识库 9 端点（原 8002 已退役，不再有独立端口）。
PORTS = {"ollama": 11434, "minio": 9000, "neo4j": 7687, "kg": 8001, "deepgraph": 8501, "ft": 8003,
         "hub": 8600, "api": 8080}

# 【2026-11-24 端口统一】默认布局是 hub（:8600 单口承载 kg/deepgraph/知识库/ft）；
# 布局口径由 start-engines.py 的 engine_layout() 决定（ENGINE_LAYOUT=separate 才拆回三进程）。
HUB_PORT = PORTS["hub"]
HUB_EMBEDDED = ("kg", "deepgraph", "ft")
WEB_PORTS = (5173, 5174, 5175)
DB_PORT, CACHE_PORT = 3306, 6379

# 可选并行口：8080 被别的课程作业后端占用时，API_PORT=8081 起本项目后端；
# 启动器同时把 VITE_PROXY_TARGET 指到同一个口，避免页面打到别人的 8080。
DEFAULT_API_PORT = 8080
API_READY_TIMEOUT = 240.0
WEB_READY_TIMEOUT = 120.0
MAX_PENDING = 8192

# 后端启动必备的两把钥匙：缺任何一把都必须 fail-fast，且报错文本只准出现变量名
REQUIRED_KEYS = ("MYSQL_ROOT_PASSWORD", "JWT_SECRET")

# MinIO 原生服务与本地平台服务名（可用 .env 覆盖；缺省按本机实测值）
DEFAULT_MINIO_BIN = Path.home() / "minio" / ("minio.exe" if os.name == "nt" else "minio")
DEFAULT_MINIO_DATA = Path.home() / ".aicourse-platform" / "minio-data"
DEFAULT_SERVICE_NAMES = {
    "mysql": "MySQL84" if os.name == "nt" else "mysql",
    "redis": "redis" if os.name == "nt" else "redis",
}
MINIO_HEALTH_URL = "http://127.0.0.1:9000/minio/health/live"
OLLAMA_TAGS_URL = "http://127.0.0.1:11434/api/tags"

# 子命令：三份 .bat 合并后，唯一入口是 start.bat，它把第一个参数原样透传到这里
MODE_ALL, MODE_BACKEND, MODE_FRONTEND, MODE_STATUS = "all", "backend", "frontend", "status"
MODES = (MODE_ALL, MODE_BACKEND, MODE_FRONTEND, MODE_STATUS)


def parse_args(argv: list) -> tuple:
    """拆出 (mode, flags, 非法输入)。非法为 None 表示可用。

    `--status` 是 `status` 子命令的旧写法，继续接受。位置参数只认第一个当子命令，
    出现第二个（例如 `start.bat backend all`）就判非法——与其"默默取第一个"，
    不如让用户看见自己敲了两个互相矛盾的动词。
    """
    flags = [a for a in argv if a.startswith("-")]
    words = [a for a in argv if not a.startswith("-")]
    if "--status" in flags:
        return MODE_STATUS, flags, None
    mode = words[0] if words else MODE_ALL
    if mode not in MODES or len(words) > 1:
        return mode, flags, " ".join(words) or mode
    return mode, flags, None


def wanted_services(mode: str) -> dict:
    """子命令该管哪些服务——依赖门、接管判定与收尸清单都按这张表收窄。

    全量后端链包含 Ollama / MinIO / Neo4j / KG / DeepGraph（内嵌知识库）/ LoRA / Spring Boot；
    `frontend` 只保留 Vite，不能因为 .env 写了 KG_ENABLED=true 就把后端偷拉起来。
    """
    managed = mode in (MODE_ALL, MODE_BACKEND)
    return {
        "ollama": managed,
        "minio": managed,
        "neo4j": managed,
        "kg": managed,
        "deepgraph": managed,
        "ft": managed,
        "api": managed,
        "web": mode in (MODE_ALL, MODE_FRONTEND),
    }


def color_enabled(flags: list, vt_available: bool) -> bool:
    """`--no-color` 的生效口径。合并前这个开关写在 docstring 里但 main 从不读它——
    开关只准在真正被消费的地方声明，否则文档与行为各说一套。"""
    return vt_available and "--no-color" not in flags


def usage(sink: "Sink") -> None:
    sink.info("用法：start.bat [all|backend|frontend|status] [--no-color]")
    sink.info("      all       起缺的、接管在跑的：Ollama/MinIO/Neo4j/EngineHub(:8600 含 KG+DeepGraph+知识库+LoRA)/API/Web")
    sink.info("      backend   起 Ollama/MinIO/引擎/LoRA/API，不起 Vite")
    sink.info("      frontend  只起 Vite（5173/5174/5175 择口）")
    sink.info("      status    只报全量服务状态，不起任何进程")


class MissingCredential(RuntimeError):
    """.env 缺后端启动必备凭证；异常文本刻意只带变量名，不带值。"""


def _load_engines():
    """复用 start-engines.py 的判定层（同目录、文件名带连字符，只能显式加载）。"""
    path = ROOT / "scripts" / "start-engines.py"
    spec = importlib.util.spec_from_file_location("start_engines", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


eng = _load_engines()


# ------------------------------------------------------------------ 纯判定


def take_lines(buffers: dict, name: str, chunk: str) -> list:
    """把管道里任意切分的 chunk 组装成整行；半行留在 buffers 里等下一段。

    八路流各自一个 buffers 槽，所以一个服务的残段不会影响另一个服务的行。
    行尾认 `\\n` 与 `\\r\\n`，行内残留的裸 `\\r` 原样留在正文里（不当行尾：npm/Vite 的进度
    输出只用裸 `\\r` 复位光标，当行尾会把一行刷成几十行）。上限兜底见 MAX_PENDING。
    """
    pending = buffers.get(name, "") + chunk.replace("\r\n", "\n")
    parts = pending.split("\n")
    buffers[name] = parts.pop()
    lines = [part.rstrip("\r") for part in parts]
    if len(buffers[name]) >= MAX_PENDING:
        # 只发裸 `r` 的进度流会永远凑不出换行，攒到上限就整段吐一次，不然残段无限长
        lines.append(buffers[name])
        buffers[name] = ""
    return lines


def flush_lines(buffers: dict, name: str) -> list:
    """子进程退出时吐出残段；崩溃现场常常就写在最后一行、且没有换行符。"""
    pending = buffers.get(name, "")
    buffers[name] = ""
    return [pending.rstrip("\r")] if pending else []


def prefixed(name: str, line: str) -> str:
    """固定 6 字符宽的服务名标签，交错输出也能一眼分辨来源。"""
    return f"[{name:<6}] {line}"


def decode_bytes(raw: bytes) -> str:
    """UTF-8 优先，退回 GBK，再退回替换解码。

    两个 FastAPI 与带 `-Dfile.encoding=UTF-8` 的 java 吐 UTF-8，而 `neo4j.bat console`
    经中文 cmd 可能吐 GBK；中继线程一旦抛异常，这一路日志就永久静默，比乱码更糟。
    """
    for codec in ("utf-8", "gbk"):
        try:
            return raw.decode(codec)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def pick_dev_port(in_use, candidates=WEB_PORTS) -> int:
    """取第一个空闲开发口；探活口径由调用方注入（必须双栈，见 port_in_use）。"""
    for port in candidates:
        if not in_use(port):
            return port
    return None


def own_dev_port(in_use, fetch_title, app_title: str, candidates=WEB_PORTS) -> int:
    """只返回**标题认主**的本项目开发口；不认主一律 None（区别于 prefer_own_dev_port 的兜底）。

    用于“接管不启动”的判定：5174 常被别的项目占着，若只看端口在听就接管，本项目的
    Vite 永远不会起来（一键启动实测踩到：状态报 web UP，点进去却是别人的页面）。
    """
    if not app_title:
        return None
    for port in candidates:
        if in_use(port) and app_title in (fetch_title(port) or ""):
            return port
    return None


def prefer_own_dev_port(in_use, fetch_title, app_title: str, candidates=WEB_PORTS) -> int:
    """在"有人在听"的口里挑本项目的那个；都不认识时退回第一个在听的口。

    端口号在本机不可信：同一台机器上 5173 今天属于本项目、昨天属于另一个前端，
    照着端口号喊入口就会把人带到别的应用去（已发生过一次误判）。认主标记由调用方
    从 `frontend/index.html` 的 `<title>` 取，脚本里不抄第二份字面量（抄了就会漂移）。
    """
    listening = [port for port in candidates if in_use(port)]
    if not listening:
        return None
    if app_title:
        for port in listening:
            if app_title in (fetch_title(port) or ""):
                return port
    return listening[0]


def expected_app_title(html_text: str) -> str:
    """从 `frontend/index.html` 正文里取 `<title>`；取不到就返回空串（调用方据此退回端口号顺序）。"""
    match = re.search(r"<title>(.*?)</title>", html_text, re.IGNORECASE | re.DOTALL)
    return match.group(1).strip() if match else ""


def todo_and_adopt(status: dict, want: dict) -> tuple:
    """按依赖序拆成"该起"与"接管"；want=False 的两侧都不进（用户主动关掉的东西不当就绪报）。"""
    todo = [name for name in SERVICES if want.get(name, True) and not status.get(name)]
    adopted = [name for name in SERVICES if want.get(name, True) and status.get(name)]
    return todo, adopted


def kill_targets(children: dict) -> list:
    """收尸清单：只含本启动器真起起来的 PID。接管来的（值是 None）一律不进。"""
    return [child.pid for name, child in children.items() if child is not None]


def stop_command(pid: int) -> list:
    """子树终止：mvnw.cmd -> java 是两代进程，只杀父会让 java 被重生再抢 8080。"""
    return ["taskkill", "/T", "/F", "/PID", str(pid)]


def health_ok(status) -> bool:
    """只有 200 算就绪：复合 /health 的 503 是"没配 AI Key"的设计形态，不是后端死了。"""
    return status == 200


API_PROBE_PATH = "/api/actuator/health/probe"


def api_port(env: dict) -> int:
    """后端监听口：默认 8080；8080 被别的项目占用时可用 API_PORT=8081 并行。

    前端代理目标会跟着这个口走（start_web 注入 VITE_PROXY_TARGET），否则页面仍会打到
    别人的 8080，表现为“启动器全绿但登录 404”。
    """
    raw = (env.get("API_PORT") or "").strip()
    try:
        port = int(raw) if raw else DEFAULT_API_PORT
    except ValueError:
        return DEFAULT_API_PORT
    return port if 1 <= port <= 65535 else DEFAULT_API_PORT


def api_probe_url(port: int = DEFAULT_API_PORT) -> str:
    return f"http://127.0.0.1:{port}{API_PROBE_PATH}"


def api_is_up(port: int = DEFAULT_API_PORT, timeout: float = 3.0) -> bool:
    """该口上是否有能返 200 的 `/probe`；端口在听不等于本项目后端。"""
    return health_ok(http_probe_status(api_probe_url(port), timeout=timeout))


def minio_enabled(dotenv: dict) -> bool:
    return eng.should_start(dotenv.get("MINIO_ENABLED"))


def minio_credentials(dotenv: dict) -> tuple:
    """MinIO 原生进程/后端共用的凭据；优先 ACCESS/SECRET，回退 ROOT。"""
    access = (dotenv.get("MINIO_ACCESS_KEY") or dotenv.get("MINIO_ROOT_USER") or "").strip()
    secret = (dotenv.get("MINIO_SECRET_KEY") or dotenv.get("MINIO_ROOT_PASSWORD") or "").strip()
    return access, secret


def discover_minio_binary(env: dict, exists=None, which=None) -> str | None:
    """定位原生 minio 可执行文件：显式 MINIO_BIN → MINIO_HOME → 本机默认位 → PATH。"""
    exists = exists or (lambda p: Path(p).is_file())
    which = which or shutil.which
    configured = (env.get("MINIO_BIN") or "").strip()
    if configured:
        return configured if exists(configured) else None
    home = (env.get("MINIO_HOME") or "").strip()
    if home:
        candidate = str(Path(home) / ("minio.exe" if os.name == "nt" else "minio"))
        return candidate if exists(candidate) else None
    if exists(str(DEFAULT_MINIO_BIN)):
        return str(DEFAULT_MINIO_BIN)
    return which("minio")


def minio_data_dir(env: dict) -> Path:
    configured = (env.get("MINIO_DATA_DIR") or "").strip()
    if configured:
        return Path(configured)
    data_root = (env.get("AICOURSE_DATA_DIR") or "").strip()
    if data_root:
        return Path(data_root) / "minio-data"
    return DEFAULT_MINIO_DATA


def ollama_needed(env: dict) -> bool:
    """本地 Ollama 是否被当前配置需要：RAG 生成或 LoRA 的 ollama provider。"""
    ai = (env.get("AI_PROVIDER") or "deepseek").strip().lower()
    ft_provider = (env.get("FT_LLM_PROVIDER") or "engine").strip().lower()
    ft_on = eng.should_start(env.get("FT_ENABLED"))
    return ai in ("ollama", "local") or (ft_on and ft_provider == "ollama")


def discover_ollama_binary(env: dict, exists=None, which=None) -> str | None:
    exists = exists or (lambda p: Path(p).is_file())
    which = which or shutil.which
    configured = (env.get("OLLAMA_BIN") or "").strip()
    if configured:
        return configured if exists(configured) else None
    found = which("ollama")
    if found:
        return found
    if os.name == "nt":
        candidate = Path(env.get("LOCALAPPDATA", "")) / "Programs" / "Ollama" / "ollama.exe"
        if exists(str(candidate)):
            return str(candidate)
    return None


def dependency_service_name(kind: str, env: dict) -> str:
    return (env.get(f"{kind.upper()}_SERVICE_NAME") or DEFAULT_SERVICE_NAMES[kind]).strip()


def apply_java_home(env: dict, java_home: str) -> dict:
    """把 `<JAVA_HOME>\\bin` 前置到**已存在**的 PATH 键上，并顺带收敛历史遗留的键名变体。

    键名拼法不由我们决定：cmd 传 `Path`，sh/Git Bash 传 `PATH`。首轮真跑就是栽在这上面——
    `env["Path"] = ...` 在只有 `PATH` 的环境里造出第二个大小写变体，子进程拿到的 PATH
    丢了 System32，`mvnw.cmd` 报 `'powershell' 不是内部或外部命令` 而起不了后端。
    """
    out = {}
    variants = [k for k in env if k.upper() == "PATH"]
    keep = variants[0] if variants else "Path"
    merged = []
    for key in variants:
        merged.extend(part for part in env[key].split(os.pathsep) if part)
    bin_dir = str(Path(java_home) / "bin")
    parts = [bin_dir] + [p for p in merged if p.lower() != bin_dir.lower()]
    for key, value in env.items():
        if key.upper() != "PATH":
            out[key] = value
    out[keep] = os.pathsep.join(parts)
    out["JAVA_HOME"] = java_home
    return out


def shell_path_gap(env: dict):
    """PATH 里缺 System32 时返回该目录，否则 None。

    只提示不擅自改写：从 sh 类壳里起 java/mvnw 会缺系统目录，那时该做的是改用
    `start.bat`（cmd 窗口天然带 System32），而不是让启动器替用户修 PATH 藏住问题。
    """
    system_root = ""
    for key, value in env.items():
        if key.upper() == "SYSTEMROOT":
            system_root = (value or "").strip()
    if not system_root:
        return None
    needed = str(Path(system_root) / "System32")
    for key, value in env.items():
        if key.upper() == "PATH" and needed.lower() in {p.lower() for p in value.split(os.pathsep) if p}:
            return None
    return needed


def engine_flags(in_use, ports=PORTS) -> dict:
    """起后端那一刻现探引擎在册状态。

    绝不能复用启动器开场时的快照：冷启动时引擎必然还在起来，照旧快照注入
    `KG_ENABLED`/`RAG_ENABLED` 就等于把"要重启后端才切 mode=ENGINE"（§18.5）
    固化进一键脚本——Java 的 `@Value` 只在启动那一刻读端口，之后不会再改。

    8002 退役后知识库与 deepgraph 是同一个端口，"知识库可用"= 8501 在听。
    """
    hub_up = bool(in_use(ports["hub"]))
    capability_ready = {}
    if hub_up:
        body = eng.http_json(eng.probe_url("hub"))
        if isinstance(body, dict):
            for item in body.get("services") or []:
                capability_ready[item.get("capability")] = bool(item.get("ready"))
    return {
        "kg": bool(in_use(ports["kg"])) or (hub_up and capability_ready.get("kg", False)),
        "deepgraph": bool(in_use(ports["deepgraph"])) or (hub_up and capability_ready.get("deepgraph", False)),
        "rag": bool(in_use(ports["deepgraph"])) or (hub_up and capability_ready.get("rag", False)),
        "hub": hub_up,
    }


def backend_env(base: dict, dotenv: dict, *, redis_open: bool, kg_up: bool, rag_up: bool,
                deepgraph_up: bool = False, hub_up: bool = False) -> dict:
    """复刻 start-backend.bat 第 1/5 步的注入口径，让"一键"与"分步"两份姿态行为一致。

    刻意与 .bat 同口径：整份 `.env` 都进进程环境（非只读那两把钥匙），否则同一台机器
    上分步起和一键起拿到的配置不同，故障就没法复现了。

    `rag_up` 现在的含义是「知识库能用」，判据就是 8501 在听（8002 已退役、不再单起）：
    Java 侧 `RAG_BASE_URL` 已指向 8501，若这里漏置位，后端的 RAG 会停在 native 而降级。
    """
    for key in REQUIRED_KEYS:
        if not (dotenv.get(key) or "").strip():
            raise MissingCredential(
                f"{key} 未配置（.env 与环境变量都没有）。它是后端启动的必需项，"
                f"请 copy .env.example .env 后填入强随机值；本脚本从不打印它的值。"
            )
    if minio_enabled(dotenv):
        access, secret = minio_credentials(dotenv)
        if not (access and secret):
            raise MissingCredential(
                "MINIO_ROOT_USER / MINIO_ROOT_PASSWORD（或 MINIO_ACCESS_KEY / MINIO_SECRET_KEY）"
                "未配置；minio.enabled=true 时后端会在启动期 fail-fast。本脚本从不打印凭据值。"
            )

    env = dict(base)
    env.update(dotenv)
    env["SPRING_DATASOURCE_URL"] = (
        "jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false"
        "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
        "&characterEncoding=utf8&useUnicode=true"
    )
    env["SPRING_DATASOURCE_PASSWORD"] = dotenv["MYSQL_ROOT_PASSWORD"]
    # 最小权限：应用号走 aicourse_app，不用 root（本机 MySQL 已建该号，口令复用 MYSQL_ROOT_PASSWORD）
    env["SPRING_DATASOURCE_USERNAME"] = "aicourse_app"
    env["SPRING_DATA_REDIS_HOST"] = "localhost"
    env["SPRING_DATA_REDIS_PORT"] = str(CACHE_PORT)
    env["COOKIE_SECURE"] = "false"
    env["REQUIRE_HTTPS"] = "false"
    # 本地默认开启双提交 CSRF：前端 axios 已自动回填 X-XSRF-TOKEN，不再需要为方便脚本而关防护。
    # 需要裸 HTTP 脚本联调时在 .env 显式写 CSRF_ENABLED=false（仅限受信本机）。
    env["CSRF_ENABLED"] = dotenv.get("CSRF_ENABLED", "true")
    # 并行口：Spring Boot 的 server.port 用环境变量覆盖（默认仍是 8080）
    api_port_value = api_port(dotenv)
    if api_port_value != DEFAULT_API_PORT:
        env["SERVER_PORT"] = str(api_port_value)
    # 无密码的本地 Redis 收到 AUTH 会让 RedisStartupValidator fail-fast、应用根本起不来（已观测）；
    # .env 里的 REDIS_PASSWORD 是给容器 requirepass 写的，端口探明免密时必须摘掉。
    if redis_open:
        env.pop("REDIS_PASSWORD", None)
        env.pop("SPRING_DATA_REDIS_PASSWORD", None)
    elif (dotenv.get("REDIS_PASSWORD") or "").strip():
        env["SPRING_DATA_REDIS_PASSWORD"] = dotenv["REDIS_PASSWORD"]

    # 开关按端口自动置位，仅限本次进程；.env 里显式写过的值（含显式 false）永远优先
    if kg_up and "KG_ENABLED" not in dotenv:
        env["KG_ENABLED"] = "true"
    if rag_up and "RAG_ENABLED" not in dotenv:
        env["RAG_ENABLED"] = "true"
    if deepgraph_up and "DG_RAG_ENABLED" not in dotenv:
        env["DG_RAG_ENABLED"] = "true"
    if hub_up:
        # 单口形态：所有引擎地址指向 hub，路径前缀不变（Java 契约零改动）。
        # 显式注入而非依赖配置默认值：一旦某处漏了，就是「开关 true、地址打空」的静默降级。
        hub_base = f"http://127.0.0.1:{HUB_PORT}"
        env["KG_BASE_URL"] = hub_base
        env["RAG_BASE_URL"] = hub_base
        env["DG_RAG_BASE_URL"] = hub_base
        env["FT_BASE_URL"] = hub_base
    elif eng.engine_layout(env) == "separate":
        # 回滚布局：application.yaml 默认已改成 8600（新形态优先），所以这里必须把旧端口写回去，
        # 否则 separate 起完三进程、后端却打 8600。
        env["KG_BASE_URL"] = f"http://127.0.0.1:{PORTS['kg']}"
        env["RAG_BASE_URL"] = f"http://127.0.0.1:{PORTS['deepgraph']}"
        env["DG_RAG_BASE_URL"] = f"http://127.0.0.1:{PORTS['deepgraph']}"
        env["FT_BASE_URL"] = f"http://127.0.0.1:{PORTS['ft']}"
    return env


def discover_java_home(env: dict, exists) -> str:
    """JAVA_HOME 优先，其次本机已知默认位；写了 JAVA_HOME 但目录不存在**不回落到默认位**
    （静默换 JDK 会让版本判定与实际运行的 JDK 对不上，与 Neo4j 的 NEO4J_HOME 同口径）。"""
    configured = (env.get("JAVA_HOME") or "").strip()
    if configured:
        return configured if exists(str(Path(configured) / "bin" / "java.exe")) else None
    default = r"C:\Program Files\Java\jdk-21"
    return default if exists(str(Path(default) / "bin" / "java.exe")) else None


def locate_node(env: dict, which) -> tuple:
    """node 与 npm-cli.js 的定位口径同 start-frontend.bat：PATH 优先，其次 WorkBuddy 托管位。
    托管位路径随用户目录变，一律从 USERPROFILE 推导，不硬编码用户名。"""
    node = which("node")
    if not node:
        managed = Path(env.get("USERPROFILE", "")) / ".workbuddy" / "binaries" / "node" / "versions" / "22.22.2"
        node = str(managed / "node.exe") if (managed / "node.exe").exists() else None
    if not node:
        return None, None
    npm_cli = str(Path(node).parent / "node_modules" / "npm" / "bin" / "npm-cli.js")
    return node, npm_cli


# --------------------------------------------------------------------- IO 层


def enable_vt_colors() -> bool:
    """cmd 默认不一定开 ANSI；开不了就退回纯文本前缀，不因为颜色把启动器搞崩。"""
    if os.name != "nt":  # pragma: no cover - 本机为 Windows
        return True
    try:
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.GetStdHandle(-11)
        mode = ctypes.c_uint32()
        if not kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
            return False
        return bool(
            kernel32.SetConsoleMode(handle, mode.value | 0x0004)
        )
    except Exception:
        return False


COLORS = {
    "ollama": "35",
    "minio": "36",
    "neo4j": "36",
    "kg": "32",
    "rag": "33",
    "ft": "31",
    "api": "34",
    "web": "35",
}


def format_line(name, line: str, color_on: bool) -> str:
    """name=None 是启动器自己的系统行，不着色也不加前缀。"""
    if name is None:
        return line
    if not color_on or name not in COLORS:
        return prefixed(name, line)
    return f"\x1b[{COLORS[name]}m{prefixed(name, '')}\x1b[0m{line}"


class Sink:
    """单写者队列：八个读管道线程 + 主线程都往这里投，只有一个线程真正 print。

    多线程各自 print 会把两行日志在控制台上咬成一行，那比丢色难查得多。
    """

    def __init__(self, color_on: bool = True):
        self._q: queue.Queue = queue.Queue()
        self._color = color_on
        self._done = object()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> "Sink":
        self._thread.start()
        return self

    def put(self, name, line: str) -> None:
        self._q.put((name, line))

    def info(self, text: str) -> None:
        self._q.put((None, text))

    def close(self) -> None:
        self._q.put((self._done, None))
        self._thread.join(timeout=5)

    def _run(self) -> None:
        while True:
            name, line = self._q.get()
            if name is self._done:
                break
            print(format_line(name, line, self._color), flush=True)


def port_in_use(port: int, timeout: float = 0.35) -> bool:
    """双栈回环探测。Vite 实测只绑 `[::1]:5173`，只试 IPv4 会把"已占用"判成"空闲"，
    随后第二个 Vite 因 `strictPort: true` 直接退出，看起来像启动器没起来。"""
    for family, addr in ((socket.AF_INET, "127.0.0.1"), (socket.AF_INET6, "::1")):
        try:
            with socket.socket(family, socket.SOCK_STREAM) as sock:
                sock.settimeout(timeout)
                if sock.connect_ex((addr, port)) == 0:
                    return True
        except OSError:
            continue
    return False


def redis_needs_auth(timeout: float = 0.6) -> bool:
    """明文 PING 拿不到 PONG 才认为本地 Redis 有密码（同 start-backend.bat 第 1 步的反向探针）。"""
    try:
        with socket.create_connection(("127.0.0.1", CACHE_PORT), timeout=timeout) as sock:
            sock.sendall(b"*1\r\n$4\r\nPING\r\n")
            return not sock.recv(64).decode(errors="replace").strip().startswith("+PONG")
    except OSError:
        return True


def http_probe_status(url: str, timeout: float = 3.0):
    """直连时必须绕开本机拦截代理，否则探活会拿到 502/10061。"""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(url, timeout=timeout) as resp:
            return resp.status
    except urllib.error.HTTPError as exc:
        return exc.code
    except Exception:
        return None


def http_page_title(port: int) -> str:
    """取开发口的 <title> 用于认主；拿不到就当不认识（退回端口号顺序）。"""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(f"http://localhost:{port}/", timeout=2.0) as resp:
            return expected_app_title(decode_bytes(resp.read(4096)))
    except Exception:
        return ""


def read_app_title() -> str:
    """认主标记取自仓库里的 `frontend/index.html`，不在脚本里抄第二份字面量。"""
    html = ROOT / "frontend" / "index.html"
    return expected_app_title(html.read_text(encoding="utf-8", errors="replace")) if html.exists() else ""


def _pump(proc, name: str, sink: Sink, handle, buffers: dict) -> None:
    while True:
        raw = proc.stdout.read(4096)
        if not raw:
            break
        text = decode_bytes(raw)
        for line in take_lines(buffers, name, text):
            sink.put(name, line)
            handle.write((prefixed(name, line) + "\n").encode("utf-8", errors="replace"))
        handle.flush()
    for line in flush_lines(buffers, name):
        sink.put(name, line)
        handle.write((prefixed(name, line) + "\n").encode("utf-8", errors="replace"))
    handle.flush()


def spawn_captured(cmd: list, cwd: Path, env: dict, name: str, sink: Sink):
    """同控制台、管道捕获：既满足"只有一个窗口"，又保住窗口里能滚日志这个唯一价值。"""
    handle = open(LOG_DIR / f"{name}.log", "ab")
    proc = subprocess.Popen(
        cmd,
        cwd=str(cwd),
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        bufsize=0,
    )
    threading.Thread(target=_pump, args=(proc, name, sink, handle, {}), daemon=True).start()
    return proc


def wait_for_engine(name: str) -> bool:
    return eng.wait_for_engine(name)


def wait_for_port(port: int, timeout: float = 30.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if port_in_use(port):
            return True
        time.sleep(1.0)
    return False


def wait_for_api(sink: Sink, proc, port: int = DEFAULT_API_PORT, timeout: float = API_READY_TIMEOUT) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc is not None and proc.poll() is not None:
            sink.info(f"[FAIL] 后端进程提前退出（exit {proc.returncode}），见 logs/api.log 末尾")
            return False
        if api_is_up(port):
            return True
        time.sleep(2.0)
    return False


def wait_for_minio(timeout: float = 60.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if health_ok(http_probe_status(MINIO_HEALTH_URL)):
            return True
        time.sleep(1.0)
    return False


def wait_for_ollama(timeout: float = 60.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if health_ok(http_probe_status(OLLAMA_TAGS_URL)):
            return True
        time.sleep(1.0)
    return False


def wait_for_web(port: int, timeout: float = WEB_READY_TIMEOUT) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if port_in_use(port):
            return True
        time.sleep(1.0)
    return False


def ensure_dependency(kind: str, port: int, env: dict, sink: Sink) -> bool:
    """MySQL/Redis 端口未监听时尝试启动本机服务；仍失败才让主流程 fail-fast。

    不拉容器（本阶段 Docker 搁置，§17）。`sc start` / `systemctl start` 可能需要管理员，
    失败时只报命令退出码与变量名，不把服务输出拼进日志。
    """
    if port_in_use(port):
        return True
    service = dependency_service_name(kind, env)
    sink.info(f"[..] {kind} :{port} 未监听，尝试启动本机服务 `{service}`（不拉容器）")
    if os.name == "nt":
        cmd = ["sc.exe", "start", service]
    else:  # pragma: no cover - 本机为 Windows
        cmd = ["systemctl", "start", service]
    try:
        result = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
            check=False,
        )
    except Exception as exc:
        sink.info(f"[WARN] 启动 {kind} 服务失败：{type(exc).__name__}（命令 `{cmd[0]}`）")
        return False
    if result.returncode != 0:
        sink.info(f"[WARN] `{cmd[0]} {service}` 退出码 {result.returncode}（可能需要管理员权限或服务名不匹配）")
    ok = wait_for_port(port, timeout=30.0)
    if ok:
        sink.info(f"[OK]   {kind} :{port} 已监听")
    return ok


def start_minio(env: dict, sink: Sink):
    """启动原生 MinIO（非 Docker）；返回 (是否就绪, 子进程)。"""
    binary = discover_minio_binary(env)
    if not binary:
        sink.put("minio", "[WARN] 找不到 minio 可执行文件（MINIO_BIN/MINIO_HOME/本机默认位/PATH 都没有）。"
                         "后端的 MinIO 上传将不可用；可用 MINIO_ENABLED=false 临时回退本地。")
        return False, None
    access, secret = minio_credentials(env)
    if not (access and secret):
        sink.put("minio", "[WARN] MINIO_ROOT_USER/PASSWORD 未配置，原生 MinIO 不启动。")
        return False, None
    data = minio_data_dir(env)
    if not data.is_absolute():
        data = ROOT / data
    data.mkdir(parents=True, exist_ok=True)
    run_env = dict(os.environ)
    run_env.update(env)
    run_env["MINIO_ROOT_USER"] = access
    run_env["MINIO_ROOT_PASSWORD"] = secret
    cmd = [
        binary,
        "server",
        str(data),
        "--address",
        f"127.0.0.1:{PORTS['minio']}",
        "--console-address",
        "127.0.0.1:9001",
    ]
    sink.put("minio", f"启动原生 MinIO（:{PORTS['minio']}，数据 {data}，非 Docker）")
    proc = spawn_captured(cmd, Path(binary).parent, run_env, "minio", sink)
    return wait_for_minio(), proc


def start_ollama(env: dict, sink: Sink):
    """启动本机 Ollama（仅当 AI/FT 配置为 ollama 时由 build_want 置位）。"""
    binary = discover_ollama_binary(env)
    if not binary:
        sink.put("ollama", "[WARN] 找不到 ollama 可执行文件；RAG/FT 的 ollama 调用会按契约降级。")
        return False, None
    run_env = dict(os.environ)
    run_env.update(env)
    sink.put("ollama", f"启动 Ollama serve（:{PORTS['ollama']}，本窗口内）")
    proc = spawn_captured([binary, "serve"], Path(binary).parent, run_env, "ollama", sink)
    return wait_for_ollama(), proc


# ------------------------------------------------------------------ 编排


def build_want(env: dict, sink: Sink, mode: str = MODE_ALL) -> dict:
    """want 里带 `.env` 显式开关与"环境不具备"的判定，缺 Neo4j/MinIO/Ollama 只降级不报错。

    再按子命令收窄：`frontend` 模式下 want["api"] 必须是 False，否则"只起前端"会顺手
    把整个后端拉起来——那正是这次合并要消灭的第二套行为。
    """
    scope = wanted_services(mode)
    neo4j_home = eng.discover_neo4j_home(env)
    layout = eng.engine_layout(env)
    hub_caps = (
        eng.should_start(env.get("KG_ENABLED")),
        eng.should_start(env.get("DG_RAG_ENABLED")),
        eng.should_start(env.get("FT_ENABLED")),
    )
    want = {
        "ollama": scope["ollama"] and ollama_needed(env) and eng.should_start(env.get("OLLAMA_AUTOSTART")),
        "minio": scope["minio"] and minio_enabled(env),
        "neo4j": scope["neo4j"] and bool(neo4j_home) and eng.should_start(env.get("NEO4J_ENABLED")),
        # hub 布局：三套引擎由 hub 一个进程承载；显式 false 的能力由 ENGINE_HUB_SKIP 下发（进程内不 import）
        "hub": scope["kg"] and layout == "hub" and any(hub_caps),
        # separate 布局（回滚口）：原三进程各自起，端口与原来一致
        "kg": scope["kg"] and layout == "separate" and eng.should_start(env.get("KG_ENABLED")),
        # 8002 退役：知识库随 deepgraph 进程起，不再有独立开关
        "deepgraph": scope["deepgraph"] and layout == "separate" and eng.should_start(env.get("DG_RAG_ENABLED")),
        "ft": scope["ft"] and layout == "separate" and eng.should_start(env.get("FT_ENABLED")),
        "api": scope["api"],
        "web": scope["web"],
    }
    if not neo4j_home and scope["kg"]:
        sink.info(
            "[WARN] 找不到 Neo4j 安装目录（.env 的 NEO4J_HOME 或本机默认位）。"
            "KG 会回落内存图，重启即丢；把 NEO4J_HOME=<安装目录> 写进 .env。"
        )
    if scope["ollama"] and ollama_needed(env) and not eng.should_start(env.get("OLLAMA_AUTOSTART")):
        sink.info("[INFO] AI/FT 需要 ollama，但 .env 显式 OLLAMA_AUTOSTART=false -> 只接管已有 Ollama，不主动拉起。")
    return want


def snapshot(api_port_value: int = DEFAULT_API_PORT, app_title: str = "") -> dict:
    return {
        "ollama": port_in_use(PORTS["ollama"]),
        "minio": port_in_use(PORTS["minio"]),
        "neo4j": port_in_use(PORTS["neo4j"]),
        "kg": port_in_use(PORTS["kg"]),
        # hub（:8600）：单口承载 kg/deepgraph/知识库/ft；它是否就位决定后端 base-url 注入哪一套
        "hub": port_in_use(PORTS["hub"]),
        # 知识库（原 8002）与 deepgraph 同口：一个 bool 就是两者的存活
        "deepgraph": port_in_use(PORTS["deepgraph"]),
        "ft": port_in_use(PORTS["ft"]),
        # 端口在听不等于本项目后端：只认 /probe 200，避免把 8080 上别人的应用接管成自己的
        "api": api_is_up(api_port_value),
        # 同理，5173-5175 只认标题；只看端口会把隔壁项目的 Vite 当成本项目已就绪
        "web": own_dev_port(port_in_use, http_page_title, app_title) is not None,
    }


def print_status(sink: Sink, status: dict, env: dict, app_title: str, api_port_value: int = DEFAULT_API_PORT) -> None:
    sink.info("-" * 62)
    for dep, port in (("mysql", DB_PORT), ("redis", CACHE_PORT)):
        sink.info(f"{dep:6s} :{port}  {'UP' if port_in_use(port) else 'DOWN'}")
    for name in ("ollama", "minio", "neo4j", "hub", "kg", "deepgraph", "ft"):
        if not status.get(name):
            # hub 在听时，三套引擎端口本来就不在听（合并的必然结果），不能报 DOWN 制造假故障
            if name in HUB_EMBEDDED and status.get("hub"):
                sink.info(f"{name:6s} :{PORTS[name]}  合并在 hub :{HUB_PORT} 内（该端口不再单独监听）")
                continue
            sink.info(f"{name:6s} :{PORTS[name]}  DOWN")
            continue
        if name == "hub":
            detail = eng.describe("hub", status["hub"])
        elif name in ("kg", "ft"):
            detail = eng.describe(name, status[name])
        elif name == "deepgraph":
            detail = eng.describe(name, status[name]) + "｜" + eng.describe("rag", status[name])
        elif name == "minio":
            detail = "UP (health 200)" if health_ok(http_probe_status(MINIO_HEALTH_URL)) else \
                "UP (端口在听，/minio/health/live 未返 200)"
        elif name == "ollama":
            detail = "UP (tags 200)" if health_ok(http_probe_status(OLLAMA_TAGS_URL)) else \
                "UP (端口在听，/api/tags 未返 200)"
        else:
            detail = "UP"
        sink.info(f"{name:6s} :{PORTS[name]}  {detail}")
    if status.get("api"):
        code = http_probe_status(api_probe_url(api_port_value))
        sink.info(f"{'api':6s} :{api_port_value}  {'UP (probe HTTP %s)' % code if health_ok(code) else '端口在听但 /probe 未返 200（HTTP %s）' % code}")
    elif port_in_use(api_port_value):
        sink.info(f"{'api':6s} :{api_port_value}  CONFLICT（端口被占用，但不是本项目 /probe）")
    else:
        sink.info(f"{'api':6s} :{api_port_value}  DOWN")
    own_port = own_dev_port(port_in_use, http_page_title, app_title)
    if own_port:
        sink.info(f"{'web':6s} :{WEB_PORTS[0]}-{WEB_PORTS[-1]}  UP (http://localhost:{own_port}/)")
    elif any(port_in_use(p) for p in WEB_PORTS):
        sink.info(f"{'web':6s} :{WEB_PORTS[0]}-{WEB_PORTS[-1]}  CONFLICT（口被别的开发服务占用，标题未认主）")
    else:
        sink.info(f"{'web':6s} :{WEB_PORTS[0]}-{WEB_PORTS[-1]}  DOWN")
    provider = (env.get("AI_PROVIDER") or "deepseek").strip().lower()
    if provider in ("ollama", "local"):
        ollama_url = (env.get("OLLAMA_BASE_URL") or "http://localhost:11434").strip().rstrip("/")
        if health_ok(http_probe_status(ollama_url + "/api/tags")):
            sink.info(f"[OK]   AI_PROVIDER=ollama，本地服务可达（{ollama_url}，无需 API Key）")
        else:
            sink.info(
                f"[WARN] AI_PROVIDER=ollama 但 {ollama_url} 不可达 -> 大模型调用将按 §40 返回 503；"
                "请先运行 `ollama serve` 并 `ollama pull deepseek-r1:8b`，不阻塞启动"
            )
    else:
        key = eng.effective_api_key(env.get("DEEPSEEK_API_KEY"))
        sink.info(
            "[OK]   DEEPSEEK_API_KEY 已配置且形如真实 Key（值从不打印）"
            if key
            else "[WARN] DEEPSEEK_API_KEY 未配置、形如占位串、或含非 ASCII 字符（备注粘连进键值，DR-82） -> 大模型调用不可用（Java 侧按 §40 如实返回 503，不再走「抽取式冒充成功」），不阻塞启动"
        )
    sink.info("-" * 62)


def start_engine(name: str, env: dict, sink: Sink):
    """返回 (是否就绪, 子进程)。子进程必须交回 main 登记，否则 Ctrl+C 收不到它。"""
    if name == "neo4j":
        home = eng.discover_neo4j_home(env)
        sink.put("neo4j", f"启动 Neo4j console：{home}（本窗口内，不另开窗口）")
        proc = spawn_captured(eng.neo4j_launch_command(home), Path(home), env, name, sink)
        return wait_for_engine(name), proc

    exported = eng.engine_env(
        dict(os.environ),
        service=name,
        neo4j_uri=(env.get("KG_NEO4J_URI") or env.get("NEO4J_URI") or "bolt://127.0.0.1:7687").strip(),
        api_key=eng.effective_api_key(env.get("DEEPSEEK_API_KEY")),
        offline=eng.offline_flags(env),
        dotenv=env,
    )
    workdir = ROOT / "python-services" / eng.SERVICE_DIRS[name]
    python_exe = eng.engine_python(name)
    if eng.venv_python(name) is None:
        sink.put(name, f"[WARN] {name} 没有自己的 .venv，回落当前解释器 {python_exe}"
                       "（重依赖缺失时会静默降级，请在服务目录建 venv 后重跑）")
    sink.put(name, f"启动 {name.upper()} 引擎（:{PORTS[name]}，本窗口内，解释器 {python_exe}）")
    proc = spawn_captured(
        eng.uvicorn_command(eng.APP_MODULES[name], PORTS[name], python_exe),
        workdir, exported, name, sink,
    )
    return wait_for_engine(name), proc


def start_api(env: dict, java_home, sink: Sink, port: int = DEFAULT_API_PORT):
    backend = ROOT / "backend"
    run_env = dict(env)
    if java_home:
        run_env = apply_java_home(run_env, java_home)
    gap = shell_path_gap(run_env)
    if gap:
        sink.put("api", f"[WARN] PATH 里没有 {gap}（多半是从 Git Bash 之类的壳里启动的）。"
                        f"mvnw.cmd 会报 'powershell 不是内部或外部命令' 之类看不懂的错——"
                        f"请改用 start.bat 在 cmd 窗口里启动。")
    jvm_args = "-Dfile.encoding=UTF-8"
    if port != DEFAULT_API_PORT:
        jvm_args += f" -Dserver.port={port}"
    cmd = [
        "cmd",
        "/c",
        "mvnw.cmd",
        "spring-boot:run",
        "-Dspring-boot.run.profiles=local",
        f"-Dspring-boot.run.jvmArguments={jvm_args}",
    ]
    sink.put("api", f"启动 Spring Boot（mvnw spring-boot:run -Plocal，:{port}，本窗口内）")
    return spawn_captured(cmd, backend, run_env, "api", sink)


def start_web(node, npm_cli, port: int, env: dict, sink: Sink, api_port_value: int = DEFAULT_API_PORT):
    frontend = ROOT / "frontend"
    web_env = dict(env)
    # Vite 的 /api 代理必须跟随后端并行口；否则 8080 被别的项目占用时，页面仍会打到
    # 别人的后端（表现为登录 404/403，而启动器本身全绿）。
    web_env["VITE_PROXY_TARGET"] = f"http://127.0.0.1:{api_port_value}/api"
    if not (frontend / "node_modules").is_dir():
        sink.put("web", "node_modules 不存在，先装依赖（首次 2-5 分钟，日志照常在下面滚）")
        install = spawn_captured([node, npm_cli, "install"], frontend, web_env, "web", sink)
        install.wait()
        if install.returncode:
            sink.info("[FAIL] npm install 失败，前端未启动；后端与引擎不受影响")
            return None
    sink.put("web", f"启动 Vite（--port {port}，代理 -> :{api_port_value}，本窗口内）")
    return spawn_captured([node, npm_cli, "run", "dev", "--", "--port", str(port)], frontend, web_env, "web", sink)


def cleanup(children: dict, sink: Sink) -> None:
    """只收本启动器起的进程树；接管的进程一台机器上可能同时是同事的演示，绝不碰。"""
    for name in SERVICES:
        proc = children.get(name)
        if proc is None:
            continue
        if proc.poll() is not None:
            continue
        sink.info(f"[..] 终止本启动器起的 {name}（PID {proc.pid} 及其子树）")
        subprocess.run(stop_command(proc.pid), check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    mode, flags, illegal = parse_args(argv)
    sink = Sink(color_enabled(flags, enable_vt_colors())).start()
    LOG_DIR.mkdir(exist_ok=True)

    env = eng.merged_env()
    api_port_value = api_port(env)
    app_title = read_app_title()
    status = snapshot(api_port_value, app_title)
    scope = wanted_services(mode)

    sink.info("=" * 62)
    if illegal is not None:
        sink.info(f"[ERROR] 无法识别的子命令：{illegal}")
        usage(sink)
        sink.close()
        return 2
    sink.info(f" AI Course Platform - 统一启动器  mode={mode}（ollama/minio/neo4j/kg/deepgraph(含知识库)/ft/api/web）")
    sink.info("=" * 62)

    if mode == MODE_STATUS:
        print_status(sink, status, env, app_title, api_port_value)
        sink.close()
        return 0

    # 依赖门按子命令收窄：MySQL/Redis/JDK 只有真要起后端时才必需（旧 start-frontend.bat 也不查它们）。
    # 未监听时先尝试启动本机服务，仍然失败才 fail-fast；不拉容器。
    if scope["api"]:
        if not ensure_dependency("mysql", DB_PORT, env, sink):
            sink.info("[ERROR] 本机 3306 无 MySQL 监听，且自动启动失败。请检查服务名 "
                      f"`{dependency_service_name('mysql', env)}`（可用 MYSQL_SERVICE_NAME 覆盖）；"
                      "本机没有 MySQL 请回项目主，不要起容器（Docker 已搁置，§17）。")
            sink.close()
            return 1
        if not ensure_dependency("redis", CACHE_PORT, env, sink):
            sink.info("[ERROR] 本机 6379 无 Redis 监听，且自动启动失败。Redis 是必需依赖"
                      "（缓存/限流/AI 配额/验证码），后端会 fail-fast。请检查服务名 "
                      f"`{dependency_service_name('redis', env)}`（可用 REDIS_SERVICE_NAME 覆盖）；"
                      "缺件请回项目主（§17）。")
            sink.close()
            return 1

        java_home = discover_java_home(env, lambda p: Path(p).exists())
        if not java_home:
            sink.info("[ERROR] 未找到 JDK 21（JAVA_HOME 指向的目录不存在，且默认位无 java.exe）。"
                      "请设 JAVA_HOME 或安装 Temurin 21；本脚本不会静默回落。")
            sink.close()
            return 1
        if port_in_use(api_port_value) and not status["api"]:
            sink.info(f"[ERROR] API 口 :{api_port_value} 已被占用，但 `/probe` 不返 200，"
                      "不是本项目后端。请释放该端口，或设置 API_PORT=8081 并行启动；"
                      "不要把别人的后端当成本项目接管。")
            sink.close()
            return 1
    else:
        java_home = None

    if scope["web"]:
        node, npm_cli = locate_node(env, shutil.which)
        if not node:
            if mode == MODE_FRONTEND:
                sink.info("[ERROR] 未找到 Node.js（PATH 与 WorkBuddy 托管位都没有）。"
                          "frontend 模式除了 Vite 不起别的东西，缺 Node 无事可做。请装 Node 20+ 后重跑。")
                sink.close()
                return 1
            sink.info("[WARN] 未找到 Node.js（PATH 与 WorkBuddy 托管位都没有）。前端不会启动；"
                      "后端与引擎不受影响。请装 Node 20+ 后重跑。")
    else:
        node, npm_cli = None, None

    if scope["api"]:
        try:
            # 这一次只为 fail-fast 校验密钥并给前端备环境；后端的开关在下面按"当下在册引擎"重算
            run_env = backend_env(
                dict(os.environ),
                env,
                redis_open=not redis_needs_auth(),
                kg_up=status["kg"],
                rag_up=status["deepgraph"],
                deepgraph_up=status["deepgraph"],
                hub_up=status["hub"],
            )
        except MissingCredential as exc:
            sink.info(f"[ERROR] {exc}")
            sink.close()
            return 1
        sink.info("[OK] 依赖门与环境变量就绪（Redis 免密已按端口探明；密钥值从不打印）")
    else:
        # frontend 模式不碰数据库也不签 JWT：把 .env 并进进程环境就够了，
        # 拿 backend_env 去校验 MYSQL_ROOT_PASSWORD/JWT_SECRET 会让"只起前端"莫名失败。
        run_env = dict(os.environ)
        run_env.update(env)
        sink.info("[OK] frontend 模式：只查 Node 与开发口，不校验数据库/签名密钥")

    want = build_want(env, sink, mode)
    todo, adopted = todo_and_adopt(status, want)
    children = dict.fromkeys(SERVICES, None)
    if adopted:
        sink.info("[OK] 已在监听，本次接管不重启、也不终止：" + ", ".join(adopted))

    for name in todo:
        if name in ENGINE_SERVICES:
            ok, proc = start_engine(name, env, sink)
        elif name == "minio":
            ok, proc = start_minio(env, sink)
        elif name == "ollama":
            ok, proc = start_ollama(env, sink)
        else:
            continue
        children[name] = proc
        sink.info(f"[{'OK' if ok else 'FAIL'}] {name} {'已就绪' if ok else '未在预期时间内就绪'}")

    if "api" in todo:
        if port_in_use(api_port_value):
            # 起引擎的几十秒里别人可能已经把后端起了；此刻再起就是双实例（DR-36）
            if api_is_up(api_port_value):
                sink.info(f"[OK] 起引擎期间 API :{api_port_value} 已被本项目后端占用 -> 改判接管，不起第二个后端")
            else:
                sink.info(f"[FAIL] 起引擎期间 API :{api_port_value} 被非本项目进程占用，不再起第二个后端")
        else:
            # 引擎若是本次拉起来的，开场快照里仍是 False；必须在起后端的前一行现探重算，
            # 否则一键启动出来的后端永远停在 native，mode=ENGINE 还得靠人手重启一次。
            engine_live = engine_flags(port_in_use)
            run_env = backend_env(
                dict(os.environ),
                env,
                redis_open=not redis_needs_auth(),
                kg_up=engine_live["kg"],
                rag_up=engine_live["rag"],
                deepgraph_up=engine_live["deepgraph"],
                hub_up=engine_live["hub"],
            )
            children["api"] = start_api(run_env, java_home, sink, api_port_value)
            ok = wait_for_api(sink, children["api"], api_port_value)
            sink.info(
                f"[OK]   后端就绪 http://localhost:{api_port_value}/api（Swagger: /api/swagger-ui.html）"
                if ok
                else "[FAIL] 后端未在预期时间内就绪，见上方日志与 logs/api.log"
            )

    web_port = None
    if "web" in todo and node:
        if mode == MODE_FRONTEND and not api_is_up(api_port_value):
            # 与旧 start-frontend.bat 同口径：后端不在也照样起 Vite，只把"每个 API 都会失败"说在前面
            sink.info(f"[INFO] API :{api_port_value} 上没有在听的本项目后端（复合 /health 无 AI Key 时返 503 属设计，见 §25）。"
                      "Vite 照常起，但页面里每个 API 请求都会失败。"
                      "要连后端一起起：start.bat（不带子命令）。")
        web_port = pick_dev_port(port_in_use)
        if web_port is None:
            sink.info("[ERROR] 5173/5174/5175 全被占用（双栈探测）。不要改用 5176+，"
                      "后端 CORS 白名单只放这三个口，登录会被 403 拒掉。")
        else:
            children["web"] = start_web(node, npm_cli, web_port, run_env, sink, api_port_value)
            if wait_for_web(web_port):
                sink.info(f"[OK]   前端就绪 http://localhost:{web_port}/（Vite 实测只绑 IPv6 回环，"
                          f"代理 -> :{api_port_value}）")
            else:
                sink.info(f"[FAIL] 前端未在预期时间内监听 {web_port}，见上方日志与 logs/web.log")

    final = snapshot(api_port_value, app_title)
    print_status(sink, final, env, app_title, api_port_value)

    engines_just_up = any(children[name] is not None for name in ENGINE_SERVICES)
    layout = eng.engine_layout(env)
    if scope["api"] and engines_just_up and children["api"] is None and final["api"]:
        sink.info("[WARN] 后端是本次之前就在跑的，而引擎刚起：Java 只在启动那一刻探测引擎端口，"
                  "需重启本启动器（start.bat backend）才会切到 mode=ENGINE。")
    if (final["kg"] or (final["hub"] and layout == "hub")) and not final["neo4j"]:
        sink.info("[INFO] Neo4j 未起 -> KG 只能走内存图，图谱重启即丢。")
    if want.get("ft") and not final["ft"]:
        sink.info("[WARN] LoRA 微调 :8003 未就绪；检查 logs/ft.log 与 python-services/fine_tune_service/.venv。")
    if want.get("hub") and not final["hub"]:
        sink.info("[WARN] 引擎集线器 :8600 未就绪；KG/DeepGraph/知识库/FT 全部不可用。"
                  "检查 logs/hub.log 与 python-services/engine_hub/。")
    if want.get("minio") and not final["minio"]:
        sink.info("[WARN] MinIO :9000 未就绪；上传会不可用。检查 logs/minio.log 与 MINIO_BIN/MINIO_DATA_DIR。")
    if want.get("ollama") and not final["ollama"]:
        sink.info("[WARN] Ollama :11434 未就绪；RAG/FT 的 ollama 调用会按契约降级。检查 logs/ollama.log。")
    if final["web"]:
        entry = web_port or prefer_own_dev_port(port_in_use, http_page_title, app_title)
        if entry:
            sink.info("[INFO] 演示入口：http://localhost:%s/ ；Ctrl+C 只终止本启动器起的进程。" % entry)
            if scope["api"] and not final["api"]:
                sink.info("[INFO] 后端此刻未就绪，页面会报接口失败；日志见 logs/api.log。")

    if not any(child is not None for child in children.values()):
        sink.info("[OK] 没有需要托管的进程（全部接管或全部已退出），本次退出，不杀任何东西。")
        sink.close()
        return 0

    sink.info("... 全部服务在本窗口内运行中，Ctrl+C 退出")
    try:
        while any(child.poll() is None for child in children.values() if child is not None):
            time.sleep(0.5)
    except KeyboardInterrupt:
        sink.info("[..] 收到 Ctrl+C，开始收尸")
    finally:
        cleanup(children, sink)
        for name in SERVICES:
            child = children[name]
            if child is not None and child.poll() is None:
                child.wait(timeout=10)
        sink.close()
    return 130


if __name__ == "__main__":
    sys.exit(main())
