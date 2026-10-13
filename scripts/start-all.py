#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
单窗口统一启动器：一个控制台拉起完整演示栈。
=================================================================
为什么要它：`start-backend.bat` + `start-frontend.bat` 的结构本身就是 **5 个 cmd 窗口**：
  ① start-backend.bat 自己（第 6 步 `mvnw.cmd spring-boot:run` 前台占住控制台）
  ②③④ scripts/start-engines.py 的 `spawn_detached()` 用 `CREATE_NEW_CONSOLE` 给
     Neo4j console / KG / RAG 各开一个新窗口
  ⑤ start-frontend.bat 自己（Vite 前台）
三个引擎窗口的唯一价值是"日志能滚"（两个 FastAPI 服务在 Windows 下没有任何日志文件，
`python-services/*/*.log` 实测为空），所以收窗的正确做法不是砍日志，而是**把五路输出
用管道接进同一个控制台**，逐行加 `[neo4j] [kg] [rag] [api] [web]` 前缀，同时各自落一份
`logs/<name>.log`（仓库根 `.gitignore` 已忽略 `*.log`）。

与既有两份启动器的关系：**本文件只做加法**，`start-backend.bat`、`start-frontend.bat`、
`scripts/start-engines.py` 一行未改，手工分步姿态与无 python 环境的兜底继续可用。
判定层直接复用 `scripts/start-engines.py`（文件名带连字符，不能用 import 语句，故用
importlib 显式加载），`.env` 解析、`sk-` 形状门、离线位门、"只补没在跑的"这些已经过
真跑取证的规则不复制第二份——复制就会分叉。

语义口径（与手册 §17/§18.5/§25、DR-36/DR-37/DR-38/DR-39/DR-50 对齐）：
  - **接管不重启**：端口已在监听的服务一律不拉起、不杀（双实例共用一套 MySQL/Redis
    却各自计限流、日志分叉，DR-36 实测）；收尸清单只含本启动器真正起的子进程。
  - **子树收尸**：`mvnw.cmd` 下面挂着 java，只杀父会让 java 被重生并抢不到 8080（本机实测），
    因此 Ctrl+C 走 `taskkill /T /F`。用鼠标点 X 关窗时，子进程与本进程共用同一控制台，
    Windows 会给它们投递 CTRL_CLOSE_EVENT，仍然会断——与"关窗即断演示"的既有口径一致。
  - **依赖缺失只上报**：MySQL(3306)/Redis(6379) 是手工本机服务，缺了就 fail-fast 并回项目主，
    绝不拉容器（本阶段 Docker 搁置，§17）。缺 `sk-` 形态的 AI Key 只是降级，不阻塞。
  - **就绪判据**：引擎=端口在听**且**探针有响应（uvicorn 先绑口后装载）；后端=`/probe` 返 200
    （复合 `/health` 无 Key 恒 503，拿它判存活会把健康后端判成死，DR-28）；
    前端=5173/5174/5175 双栈探测（Vite 实测只绑 `[::1]`，单栈探测是假 DOWN）。

用法（仓库根双击 `start-all.bat`，或手动）：
    python scripts\\start-all.py               # 拉起缺的，接管在跑的，一个窗口看全部日志
    python scripts\\start-all.py --status      # 只报五进程状态，不起任何进程、不注入环境
    python scripts\\start-all.py --no-color     # 控制台不支持 ANSI 时用纯文本前缀

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

# 依赖序即启动序：KG 要接图库，Java 只在启动那一刻探测引擎，前端最后（代理要能打到 8080）
SERVICES = ("neo4j", "kg", "rag", "api", "web")
PORTS = {"neo4j": 7687, "kg": 8001, "rag": 8002, "api": 8080}
WEB_PORTS = (5173, 5174, 5175)
DB_PORT, CACHE_PORT = 3306, 6379

API_READY_TIMEOUT = 240.0
WEB_READY_TIMEOUT = 120.0
MAX_PENDING = 8192

# 后端启动必备的两把钥匙：缺任何一把都必须 fail-fast，且报错文本只准出现变量名
REQUIRED_KEYS = ("MYSQL_ROOT_PASSWORD", "JWT_SECRET")


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

    五路流各自一个 buffers 槽，所以一个服务的残段不会影响另一个服务的行。
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
    `start-all.bat`（cmd 窗口天然带 System32），而不是让启动器替用户修 PATH 藏住问题。
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
    """
    return {"kg": bool(in_use(ports["kg"])), "rag": bool(in_use(ports["rag"]))}


def backend_env(base: dict, dotenv: dict, *, redis_open: bool, kg_up: bool, rag_up: bool) -> dict:
    """复刻 start-backend.bat 第 1/5 步的注入口径，让"一键"与"分步"两份姿态行为一致。

    刻意与 .bat 同口径：整份 `.env` 都进进程环境（非只读那两把钥匙），否则同一台机器
    上分步起和一键起拿到的配置不同，故障就没法复现了。
    """
    for key in REQUIRED_KEYS:
        if not (dotenv.get(key) or "").strip():
            raise MissingCredential(
                f"{key} 未配置（.env 与环境变量都没有）。它是后端启动的必需项，"
                f"请 copy .env.example .env 后填入强随机值；本脚本从不打印它的值。"
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
    env["CSRF_ENABLED"] = "false"
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


COLORS = {"neo4j": "36", "kg": "32", "rag": "33", "api": "34", "web": "35"}


def format_line(name, line: str, color_on: bool) -> str:
    """name=None 是启动器自己的系统行，不着色也不加前缀。"""
    if name is None:
        return line
    if not color_on or name not in COLORS:
        return prefixed(name, line)
    return f"\x1b[{COLORS[name]}m{prefixed(name, '')}\x1b[0m{line}"


class Sink:
    """单写者队列：五个读管道线程 + 主线程都往这里投，只有一个线程真正 print。

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


def wait_for_api(sink: Sink, proc, timeout: float = API_READY_TIMEOUT) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc is not None and proc.poll() is not None:
            sink.info(f"[FAIL] 后端进程提前退出（exit {proc.returncode}），见 logs/api.log 末尾")
            return False
        if health_ok(http_probe_status("http://127.0.0.1:8080/api/actuator/health/probe")):
            return True
        time.sleep(2.0)
    return False


def wait_for_web(port: int, timeout: float = WEB_READY_TIMEOUT) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if port_in_use(port):
            return True
        time.sleep(1.0)
    return False


# ------------------------------------------------------------------ 编排


def build_want(env: dict, sink: Sink) -> dict:
    """want 里带 `.env` 显式开关与"环境不具备"的判定，缺 Neo4j 安装位只降级不报错。"""
    neo4j_home = eng.discover_neo4j_home(env)
    want = {
        "neo4j": bool(neo4j_home) and eng.should_start(env.get("NEO4J_ENABLED")),
        "kg": eng.should_start(env.get("KG_ENABLED")),
        "rag": eng.should_start(env.get("RAG_ENABLED")),
        "api": True,
        "web": True,
    }
    if not neo4j_home and want["kg"]:
        sink.info(
            "[WARN] 找不到 Neo4j 安装目录（.env 的 NEO4J_HOME 或本机默认位）。"
            "KG 会回落内存图，重启即丢；把 NEO4J_HOME=<安装目录> 写进 .env。"
        )
    return want


def snapshot() -> dict:
    return {
        "neo4j": port_in_use(PORTS["neo4j"]),
        "kg": port_in_use(PORTS["kg"]),
        "rag": port_in_use(PORTS["rag"]),
        "api": port_in_use(PORTS["api"]),
        "web": any(port_in_use(p) for p in WEB_PORTS),
    }


def print_status(sink: Sink, status: dict, env: dict, app_title: str) -> None:
    sink.info("-" * 62)
    for name in ("neo4j", "kg", "rag"):
        sink.info(f"{name:6s} :{PORTS[name]}  {eng.describe(name, status[name])}")
    if status["api"]:
        code = http_probe_status("http://127.0.0.1:8080/api/actuator/health/probe")
        sink.info(f"{'api':6s} :8080  {'UP (probe HTTP %s)' % code if health_ok(code) else '端口在听但 /probe 未返 200（HTTP %s）' % code}")
    else:
        sink.info("api    :8080  DOWN")
    own_port = prefer_own_dev_port(port_in_use, http_page_title, app_title)
    sink.info(
        f"web    :{WEB_PORTS[0]}-{WEB_PORTS[-1]}  "
        + (f"UP (http://localhost:{own_port}/)" if own_port else "DOWN")
        + ("" if not own_port or app_title else "  [!] 该口未认出本项目标题，可能属于别的开发服务")
    )
    key = eng.effective_api_key(env.get("DEEPSEEK_API_KEY"))
    sink.info(
        "[OK]   DEEPSEEK_API_KEY 已配置且形如真实 Key（值从不打印）"
        if key
        else "[WARN] DEEPSEEK_API_KEY 未配置或形如占位串 -> AI 走抽取式回答（§40），不阻塞启动"
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
    )
    workdir = ROOT / "python-services" / eng.SERVICE_DIRS[name]
    sink.put(name, f"启动 {name.upper()} 引擎（:{PORTS[name]}，本窗口内）")
    proc = spawn_captured(eng.uvicorn_command(eng.APP_MODULES[name], PORTS[name]), workdir, exported, name, sink)
    return wait_for_engine(name), proc


def start_api(env: dict, java_home, sink: Sink):
    backend = ROOT / "backend"
    run_env = dict(env)
    if java_home:
        run_env = apply_java_home(run_env, java_home)
    gap = shell_path_gap(run_env)
    if gap:
        sink.put("api", f"[WARN] PATH 里没有 {gap}（多半是从 Git Bash 之类的壳里启动的）。"
                        f"mvnw.cmd 会报 'powershell 不是内部或外部命令' 之类看不懂的错——"
                        f"请改用 start-all.bat 在 cmd 窗口里启动。")
    cmd = [
        "cmd",
        "/c",
        "mvnw.cmd",
        "spring-boot:run",
        "-Dspring-boot.run.profiles=local",
        "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8",
    ]
    sink.put("api", "启动 Spring Boot（mvnw spring-boot:run -Plocal，本窗口内）")
    return spawn_captured(cmd, backend, run_env, "api", sink)


def start_web(node, npm_cli, port: int, env: dict, sink: Sink):
    frontend = ROOT / "frontend"
    if not (frontend / "node_modules").is_dir():
        sink.put("web", "node_modules 不存在，先装依赖（首次 2-5 分钟，日志照常在下面滚）")
        install = spawn_captured([node, npm_cli, "install"], frontend, env, "web", sink)
        install.wait()
        if install.returncode:
            sink.info("[FAIL] npm install 失败，前端未启动；后端与引擎不受影响")
            return None
    sink.put("web", f"启动 Vite（--port {port}，本窗口内）")
    return spawn_captured([node, npm_cli, "run", "dev", "--", "--port", str(port)], frontend, env, "web", sink)


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
    sink = Sink(enable_vt_colors()).start()
    LOG_DIR.mkdir(exist_ok=True)

    env = eng.merged_env()
    status = snapshot()
    app_title = read_app_title()

    sink.info("=" * 62)
    sink.info(" AI Course Platform - 单窗口统一启动器（neo4j/kg/rag/api/web）")
    sink.info("=" * 62)

    if "--status" in argv:
        print_status(sink, status, env, app_title)
        sink.close()
        return 0

    # 依赖门：MySQL/Redis 是手工本机服务，缺了就只能上报（本阶段 Docker 搁置，§17）
    if not port_in_use(DB_PORT):
        sink.info("[ERROR] 本机 3306 无 MySQL 监听。启动：net start MySQL84；"
                  "服务名不确定用 sc query type=service | findstr /i mysql。"
                  "本机没有 MySQL 请回项目主，不要起容器（Docker 已搁置，§17）。")
        sink.close()
        return 1
    if not port_in_use(CACHE_PORT):
        sink.info("[ERROR] 本机 6379 无 Redis 监听。Redis 是必需依赖（缓存/限流/AI 配额/验证码），"
                  "后端会 fail-fast。启动：net start Redis。缺件请回项目主（§17）。")
        sink.close()
        return 1

    java_home = discover_java_home(env, lambda p: Path(p).exists())
    if not java_home:
        sink.info("[ERROR] 未找到 JDK 21（JAVA_HOME 指向的目录不存在，且默认位无 java.exe）。"
                  "请设 JAVA_HOME 或安装 Temurin 21；本脚本不会静默回落。")
        sink.close()
        return 1

    node, npm_cli = locate_node(env, shutil.which)
    if not node:
        sink.info("[WARN] 未找到 Node.js（PATH 与 WorkBuddy 托管位都没有）。前端不会启动；"
                  "后端与引擎不受影响。请装 Node 20+ 后重跑。")

    try:
        # 这一次只为 fail-fast 校验密钥并给前端备环境；后端的开关在下面按"当下在册引擎"重算
        run_env = backend_env(
            dict(os.environ),
            env,
            redis_open=not redis_needs_auth(),
            kg_up=status["kg"],
            rag_up=status["rag"],
        )
    except MissingCredential as exc:
        sink.info(f"[ERROR] {exc}")
        sink.close()
        return 1
    sink.info("[OK] 依赖门与环境变量就绪（Redis 免密已按端口探明；密钥值从不打印）")

    want = build_want(env, sink)
    todo, adopted = todo_and_adopt(status, want)
    children = dict.fromkeys(SERVICES, None)
    if adopted:
        sink.info("[OK] 已在监听，本次接管不重启、也不终止：" + ", ".join(adopted))

    for name in todo:
        if name not in ("neo4j", "kg", "rag"):
            continue
        ok, proc = start_engine(name, env, sink)
        children[name] = proc
        sink.info(f"[{'OK' if ok else 'FAIL'}] {name} {'已就绪' if ok else '未在预期时间内就绪'}")

    if "api" in todo:
        if port_in_use(PORTS["api"]):
            # 起引擎的十几到几十秒里别人可能已经把后端起了；此刻再起就是双实例（DR-36）
            sink.info("[OK] 起引擎期间 8080 已被占用 -> 改判接管，不起第二个后端")
        else:
            # 引擎若是本次拉起来的，开场快照里仍是 False；必须在起后端的前一行现探重算，
            # 否则一键启动出来的后端永远停在 native，mode=ENGINE 还得靠人手重启一次。
            flags = engine_flags(port_in_use)
            run_env = backend_env(
                dict(os.environ),
                env,
                redis_open=not redis_needs_auth(),
                kg_up=flags["kg"],
                rag_up=flags["rag"],
            )
            children["api"] = start_api(run_env, java_home, sink)
            ok = wait_for_api(sink, children["api"])
            sink.info(
                "[OK]   后端就绪 http://localhost:8080/api（Swagger: /api/swagger-ui.html）"
                if ok
                else "[FAIL] 后端未在预期时间内就绪，见上方日志与 logs/api.log"
            )

    web_port = None
    if "web" in todo and node:
        web_port = pick_dev_port(port_in_use)
        if web_port is None:
            sink.info("[ERROR] 5173/5174/5175 全被占用（双栈探测）。不要改用 5176+，"
                      "后端 CORS 白名单只放这三个口，登录会被 403 拒掉。")
        else:
            children["web"] = start_web(node, npm_cli, web_port, run_env, sink)
            if wait_for_web(web_port):
                sink.info(f"[OK]   前端就绪 http://localhost:{web_port}/（Vite 实测只绑 IPv6 回环，"
                          f"别用 127.0.0.1:{web_port} 判活）")
            else:
                sink.info(f"[FAIL] 前端未在预期时间内监听 {web_port}，见上方日志与 logs/web.log")

    final = snapshot()
    print_status(sink, final, env, app_title)

    engines_just_up = any(children[name] is not None for name in ("neo4j", "kg", "rag"))
    if engines_just_up and children["api"] is None and final["api"]:
        sink.info("[WARN] 后端是本次之前就在跑的，而引擎刚起：Java 只在启动那一刻探测引擎端口，"
                  "需重启本启动器（或 start-backend.bat）才会切到 mode=ENGINE。")
    if final["kg"] and not final["neo4j"]:
        sink.info("[INFO] Neo4j 未起 -> KG 只能走内存图，图谱重启即丢。")
    if final["web"] and final["api"]:
        entry = web_port or prefer_own_dev_port(port_in_use, http_page_title, app_title)
        sink.info("[INFO] 演示入口：http://localhost:%s/ ；Ctrl+C 只终止本启动器起的进程。" % entry)

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
