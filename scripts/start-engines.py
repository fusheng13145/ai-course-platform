#!/usr/bin/env python3
"""
一键拉起后端全链：Neo4j(7687) -> KG(8001) -> DeepGraph+知识库(8501) -> LoRA 微调(8003)，再交给 start.bat backend 起 Java。
==========================================================================================================
为什么需要它：手册 §18.5 把引擎启动写成五条手工 cmd 步骤，三个坑各自都能把演示判成"AI 没接上"：
  1) 两个 FastAPI 服务**只读进程环境、不读 `.env`**（DR-39，全文无 dotenv），所以
     `NEO4J_URI` / `DEEPSEEK_API_KEY` / `HF_HUB_OFFLINE` 必须由启动器导出；
  2) 后端只在**启动那一刻**探测 8001/8501/7687，引擎后起则要重启后端才切 `mode=ENGINE`；
  3) 缺 `HF_HUB_OFFLINE=1` 时 sentence-transformers 会联网向 HF 校验版本，本机无外网通道时
     **不报错而是挂死**，表现为 Java 侧 60s 后返"AI 服务暂时不可用"且 sources 为空。

**8002（rag_service）已于批次 3B-2 退役**：知识库 9 个端点与 `rag_engine` 逐字并进 8501 的
`deepgraph_rag/knowledge_api.py` + `knowledge_engine.py`，本启动器不再拉起第二个进程。
`rag` 在本文件里只剩「能力视图」语义（探针指向 8501 的 `/api/v1/rag/documents`），
因此知识库的 `RAG_*` / 嵌入 / 重排 / HyDE / 离线位一律并入 `deepgraph` 的导出名单——
少导出一个键，8501 里的知识库就会静默回落引擎默认值（等同能力降级）。

本脚本刻意**只补没在跑的东西**：已经在监听的端口一律不重启（双实例共用一套 MySQL/Redis
却各自计限流、日志分叉，DR-36 实测），也不会杀任何进程。缺依赖（如 Neo4j 安装位找不到）
只上报、不自行安装——本阶段 Docker 搁置，缺件一律回项目主（§17）。

用法（仓库根执行）：
    python scripts\\start-engines.py            # 拉起缺的，打印每个服务的真实判定
    python scripts\\start-engines.py --status   # 只探不看、不起任何进程
    python scripts\\start-engines.py --only kg deepgraph rag   # rag 是 8501 的别名
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
# ft(8003) 于 2026-11-16 入编排（原 D5「存在但未入编排」）：LoRA 引擎懒加载重依赖，
# 端口起来即代表服务活；引擎是否真可用由 /fine-tune/health 的 engine_available 如实上报，
# 缺依赖时该服务仍能起（降级为 STRUCTURE），不阻断 KG 主链。
# deepgraph(8501) 于 2026-11-21 入编排：已验收 DeepSeek-RAG-Chatbot 原样复制
# + api_server.py 契约适配器；**2026-11-24（批次 3B-2）起同时承载知识库 9 端点**（原 8002），
# 因此它的导出名单同时包含 deepgraph 与知识库两套键。
ORDER = ("neo4j", "kg", "deepgraph", "ft")
# 8002 已退役：`rag` 仍是可用的逻辑名，但解析到 8501 的那一个进程（不再单起）。
RETIRED_SERVICES = {"rag": "deepgraph"}
PORTS = {"neo4j": 7687, "kg": 8001, "deepgraph": 8501, "ft": 8003}

# 【2026-11-24 端口统一】三套引擎可合并为一个进程（engine-hub :8600）：
#   ENGINE_LAYOUT=hub（默认）= 只起 hub：一个窗口、一个端口承载 KG / DeepGraph+知识库 / LoRA；
#   ENGINE_LAYOUT=separate   = 起原来的 kg/deepgraph/ft 三个进程（回滚口，端口不变）。
# 合并发生在「进程与端口」这一层：hub 按原始 path 前缀原样转发，契约路径一字不变，
# Java 侧只需把 base-url 指到 8600（由 start-all.py 按 hub 在听与否自动注入）。
HUB_PORT = 8600
HUB_SERVICE = "hub"
ORDER_HUB = ("neo4j", HUB_SERVICE)
# hub 需要三套重依赖同装：按此顺序找现成 venv（engine_hub 一般没有，落 deepgraph_rag）
# 注意：rag_service 已随 8002 退役删除，不再作为 venv 候选（否则会误用一个没有本次依赖的旧环境）
HUB_VENV_CANDIDATES = ("engine_hub", "deepgraph_rag", "kg_service", "fine_tune_service")
PORTS[HUB_SERVICE] = HUB_PORT


def engine_layout(env: dict) -> str:
    """`ENGINE_LAYOUT` 的生效口径：只有显式 separate/split/legacy/multi 才拆回三进程。

    默认 hub：合并的意义就是「少记端口」，默认值必须体现该决定；拆回是少数回滚场景，
    用显式开关表达，且拆回时端口与导出名单都保持原样。
    """
    value = (env.get("ENGINE_LAYOUT") or "").strip().lower()
    return "separate" if value in ("separate", "split", "legacy", "multi") else "hub"


def order_for(layout: str) -> tuple:
    """按布局给出启动顺序：hub 布局只有 neo4j + hub；separate 布局保持原四段。"""
    return ORDER_HUB if layout == "hub" else ORDER

# Neo4j 是仓库外的本机单机安装（§18.5）。首选 .env 的 NEO4J_HOME，其次本机已知路径；
# 都不存在则只上报，不猜路径、不装东西。
# 候选按平台排列：Windows 是本机 zip 单机版；Linux 只列常见安装位。
# 生产/容器编排不经过这里（compose 的 neo4j 用镜像自带进程，见 docker-compose.yml）；
# 都找不到时只上报，不猜路径、不装东西。
DEFAULT_NEO4J_HOMES = (
    r"D:\neo4j-community-5.26.12",  # Windows 本机（zip 单机版，必须 neo4j.bat console）
    "/opt/neo4j",                   # Linux 官方 tarball 常见位
    "/usr/local/neo4j",
    "/var/lib/neo4j",
)

DEFAULT_EMBED_MODEL = "BAAI/bge-small-zh-v1.5"
SERVICE_DIRS = {"kg": "kg_service", "deepgraph": "deepgraph_rag", "ft": "fine_tune_service",
                HUB_SERVICE: "engine_hub"}
# deepgraph 的入口是契约适配器 api_server.py（复制的 app.py 是 Streamlit UI，不进 uvicorn）；
# 该进程同时挂载 deepgraph 与知识库（原 8002）两套路由。
APP_MODULES = {"kg": "kg_service:app", "deepgraph": "api_server:app", "ft": "lora_service:app",
               HUB_SERVICE: "hub:app"}

# DR-63：引擎进程没有 dotenv，只调 os.getenv（kg_store.py:36/312-323、rag_engine.py:101-634）。
# 因此「写在 .env 里」不等于「会生效」——只有在下面这份名单里的键才会被导出到子进程。
# 名单按服务分列，与各服务实际读取的键一一对应；未配置（缺失或空串）的键不导出，让引擎用自身默认。
ENGINE_EXPORTS = {
    # 服务间内部认证（2026-11-10 V8 批）：kg/rag 的中间件在 INTERNAL_API_KEY 非空时校验
    # X-Internal-Key——启动器必须透传，否则 .env 配了钥匙、引擎却收不到（DR-63 同型盲区，
    # scripts/tests/test_start_engines.py 的导出名单护栏已钉住该漂移）。
    "kg": ("INTERNAL_API_KEY", "NEO4J_USERNAME", "NEO4J_PASSWORD", "KG_BACKEND"),
    # LoRA 微调（8003，2026-11-16 入编排）。键名依据 lora_service.py / lora_engine.py / ft_llm.py
    # 的 os.getenv 实读点；DR-63 同型盲区——不导出则 .env 里配了也不生效。
    # **故意不导出 FT_ENGINE_READY**：它是 CI 用的「不启动训练线程」开关，生产误配成 1
    # 会让 /start 静默不训练，属于最不该被 .env 顺带打开的一类键。
    "ft": (
        "FT_DATA_DIR",
        "FT_DEFAULT_BASE_MODEL",
        "FT_MODEL_MAP",
        "FT_DEVICE",
        "FT_TRAIN_STEPS",
        "FT_ALLOW_DOWNLOAD",
        "FT_PUBLIC_BASE",
        "FT_CORPUS_DIR",
        "FT_CORPUS_JSON",
        "FT_LLM_PROVIDER",
        "INTERNAL_API_KEY",
        "AI_PROVIDER",
        "OLLAMA_BASE_URL",
        "OLLAMA_MODEL",
        "OLLAMA_THINK",
        "FT_OLLAMA_BASE_URL",
        "FT_OLLAMA_MODEL",
        "FT_OLLAMA_TIMEOUT",
        "HF_HOME",
        "HF_HUB_CACHE",
    ),
    # `rag` 不再是独立服务项（8002 已退役）：它的旋钮已并入下面的 "deepgraph"，
    # 保留这个名字在 RETIRED_SERVICES 里只用于把 `--only rag` 解析到 8501。

    # deepgraph_rag（8501，2026-11-21 接入）。键名依据复制内容 config.py / llm_client.py /
    # neo4j_handler.py 的 os.getenv 实读点（DR-63 同型：不导出则 .env 配了也不生效）。
    # DEEPSEEK_API_KEY 走 api_key 通道（见 engine_env），占位值一律不转；
    # CROSS_ENCODER_MODEL 允许用平台缓存的中文重排模型覆盖默认英文模型。
    "deepgraph": (
        "LLM_PROVIDER",
        "MODEL",
        "DEEPSEEK_MODEL",
        "OLLAMA_API_URL",
        "OLLAMA_BASE_URL",
        "NEO4J_URI",
        "NEO4J_USERNAME",
        "NEO4J_PASSWORD",
        # 课程知识图谱桥接目标（2026-11-22）：抽取三元组/层次树并入 kg_service 课程图谱
        "KG_BASE_URL",
        "DG_KG_SYNC",
        # 大文档嵌入批处理垫片的批大小（api_server 运行期兼容层）
        "DG_EMBED_BATCH",
        # 知识库（原 8002 rag_service）的旋钮：3B-2 收敛后这些键由**同一进程**读取，
        # 因此必须并进本名单。少一个键＝8501 里的知识库静默回落引擎默认值（能力降级）。
        "DEEPSEEK_BASE_URL",
        "RAG_EMBED_MODEL",
        "RAG_DATA_DIR",
        "RAG_CHUNK_SIZE",
        "RAG_CHUNK_OVERLAP",
        "RAG_GRAPH_ENABLED",
        "RAG_HYDE_ENABLED",
        "RAG_HYDE_TIMEOUT",
        "RAG_HYDE_ALLOW_OLLAMA",
        "RAG_RERANK_ENABLED",
        "RAG_RERANK_MODEL",
        "RAG_RERANK_POOL",
        "RAG_RERANK_MAX_LENGTH",
        # 适配器身份/限流/上传（阶段4）：不导出则 .env 配置对 8501 不生效
        "DG_INTERNAL_AUTH_MODE",
        "DG_RATE_LIMIT_PER_MINUTE",
        "DG_MAX_UPLOAD_BYTES",
        "CROSS_ENCODER_MODEL",
        "HF_ENDPOINT",
        "HF_HOME",
        "HF_HUB_CACHE",
        "INTERNAL_API_KEY",
    ),
}

# hub 是三个引擎同进程：导出名单必须是**三家并集**。少导出一个键，该能力就会静默回落到
# 引擎默认值（例：INTERNAL_API_KEY 漏了 = 内部认证形同关闭；HF_HUB_OFFLINE 漏了 = 无外网挂死），
# 与批次 3B-2「8002 并入 8501 时 15 个 RAG_* 键必须并入导出名单」同一条教训。
ENGINE_EXPORTS[HUB_SERVICE] = tuple(dict.fromkeys(
    [key for service in ("kg", "deepgraph", "ft") for key in ENGINE_EXPORTS.get(service, ())]
))

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


def default_file_exists(path: str) -> bool:
    """解释器是**文件**，不能用上面的 `is_dir()` 判（那样 venv 永远判成不存在）。"""
    return Path(path).is_file()


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


def plan(status: dict, want: dict, order: tuple = None) -> list:
    """按依赖顺序返回"该起但还没在跑"的服务；已在跑的绝不重启。

    `order` 必须由调用方按布局传入：hub 布局是 (neo4j, hub)，separate 才是四段。
    写死全局 ORDER 会让 hub 布局把 kg/deepgraph/ft 一起拉起来（want 里没有的名字
    会落到 `want.get(name, True)` 的 True 分支），表现为"单口启动却开了三个旧端口"。
    """
    order = ORDER if order is None else order
    return [name for name in order if want.get(name, True) and not status.get(name)]


def engine_env(base: dict, *, service: str, neo4j_uri=None, api_key=None, offline=None, dotenv=None) -> dict:
    """引擎不读 .env，所以导出必须在这里完成。
    DEEPSEEK_API_KEY 只给 RAG（KG 侧无生成逻辑），且占位值一律不转。
    dotenv 是启动器解析出的 .env 读数（DR-63）：名单内的键由它显式导出，
    否则 .env 里配了也只会静默回落引擎默认值。"""
    env = dict(base)
    env.pop("DEEPSEEK_API_KEY", None)
    env.pop("NEO4J_URI", None)
    # 退役别名解析：`rag` 的导出名单就是 8501 那一套（两套路由同一进程）
    service = RETIRED_SERVICES.get(service, service)
    source = dotenv or {}
    for key in ENGINE_EXPORTS.get(service, ()):
        value = source.get(key)
        if value is not None and str(value).strip():
            env[key] = str(value).strip()
    if service == "deepgraph":
        # 已验收复制内容 llm_client.call_llm 需要 DeepSeek 密钥（LLM_PROVIDER=deepseek 时）；
        # 占位值不转（effective_api_key 已过滤）。
        # 离线位同样必须转：本进程内含知识库的 sentence-transformers / Cross-Encoder，
        # 缺 HF_HUB_OFFLINE 时无外网会挂死（§18.5 同型，2026-11-24 合并后同进程生效）。
        if api_key:
            env["DEEPSEEK_API_KEY"] = api_key
        env.update(offline or {})
    elif service == "kg" and neo4j_uri:
        env["NEO4J_URI"] = neo4j_uri
    if service == HUB_SERVICE:
        # hub 一个进程同时读三家的键：密钥/离线位/kg 图库地址都要给到（否则只有一部分能力正常）
        if api_key:
            env["DEEPSEEK_API_KEY"] = api_key
        env.update(offline or {})
        if neo4j_uri:
            env["NEO4J_URI"] = neo4j_uri
        # 能力开关下发：显式 false 的引擎在 hub 内不 import（省 torch 与模型内存），
        # 并在 /health 里标 skipped=true——「跳过」可见，不伪装成健康。
        skip = [
            capability
            for capability, flag in (
                ("kg", "KG_ENABLED"),
                ("deepgraph", "DG_RAG_ENABLED"),
                ("ft", "FT_ENABLED"),
            )
            if not should_start(source.get(flag))
        ]
        if skip:
            env["ENGINE_HUB_SKIP"] = ",".join(skip)
    return env


def venv_python(service: str, *, root: Path = ROOT, exists=default_file_exists) -> str | None:
    """服务自己的 venv 解释器；不存在返回 None，由调用方回落当前解释器。

    为什么不能直接用 `sys.executable`：重依赖（sentence-transformers / torch / faiss）
    只装在 `python-services/<service>/.venv` 里，而启动器通常由系统 Python 跑。
    用系统解释器起 8501，知识库会因缺包恒走降级分支——端口在听、健康检查也 200，
    但检索永远是空/占位，这类失败没有任何报错，只能靠解释器路径本身钉住。
    venv 缺失是**如实上报**的降级（KG 本机就没有 .venv），不是静默换解释器。
    """
    parts = ("Scripts", "python.exe") if os.name == "nt" else ("bin", "python")
    service = RETIRED_SERVICES.get(service, service)
    if service == HUB_SERVICE:
        # hub 没有自己的 venv 时，必须落到「三套重依赖都装齐的那个」：
        # 用系统解释器起 hub 会让 torch/faiss/jieba 全部 import 失败 -> 三能力齐降级且不报错。
        for candidate_dir in HUB_VENV_CANDIDATES:
            candidate = root / "python-services" / candidate_dir / ".venv" / parts[0] / parts[1]
            if exists(str(candidate)):
                return str(candidate)
        return None
    candidate = root / "python-services" / SERVICE_DIRS[service] / ".venv" / parts[0] / parts[1]
    return str(candidate) if exists(str(candidate)) else None


def engine_python(service: str) -> str:
    """两个启动器共用的解释器选择：优先服务自己的 venv，缺失才回落当前解释器。

    放在这里而不是各启动器各写一份：`start-engines.py` 与 `start-all.py` 都构造 uvicorn
    命令，两处一旦分叉就会出现「一键启动用系统解释器、单起引擎用 venv」这种只在一半
    路径上成立的假绿（本仓已因同类分叉吃过 DR-36 的教训）。
    """
    return venv_python(service) or sys.executable


def uvicorn_command(module: str, port: int, python_exe=None) -> list:
    """用 `python_exe` 起 uvicorn（缺省为当前解释器），只绑 127.0.0.1——两个引擎都无鉴权，
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
    """存活探针用真实存在的端点：8002 已于 3B-2 退役，知识库探针同样指向 8501。

    8501 上是两个路由：deepgraph 的 `/api/v1/deepgraph/health` 与知识库（原 8002）的
    `/api/v1/rag/documents`——后者从来就没有 `/health`（返 404），拿错路径会把活引擎判成死的。
    """
    if service == "kg":
        return "http://127.0.0.1:8001/api/v1/graph/health"
    if service == "rag":
        return "http://127.0.0.1:8501/api/v1/rag/documents"
    if service == "deepgraph":
        return "http://127.0.0.1:8501/api/v1/deepgraph/health"
    if service == "ft":
        return "http://127.0.0.1:8003/api/v1/fine-tune/health"
    if service == HUB_SERVICE:
        # 聚合健康端点：三能力状态都在 body.services[] 里，一次探针判全部
        return f"http://127.0.0.1:{HUB_PORT}/health"
    return "http://127.0.0.1:7474"


def neo4j_bin(home: str) -> Path:
    """Neo4j 启动脚本：Windows 是 `bin\\neo4j.bat`，Linux/macOS 是无扩展名的 `bin/neo4j`。
    写死 `.bat` 的旧版本在非 Windows 上必然找不到文件，Linux 启动器因此不可用（E-01 残项）。"""
    return Path(home) / "bin" / ("neo4j.bat" if os.name == "nt" else "neo4j")


def neo4j_launch_command(home: str) -> list:
    """只能用 console：zip 单机版没装成服务，`neo4j start` 会直接报
    "Neo4j service is not installed / Unable to start"（2026-10-10 真跑实测）。
    console 在前台跑，关窗即停——与引擎窗口同一姿态。子命令两平台同名。"""
    return [str(neo4j_bin(home)), "console"]


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
    launcher = neo4j_bin(home)
    if not launcher.exists():
        print(f"[FAIL] 找不到 {launcher}（平台 {os.name}）")
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
    if not workdir.is_dir():
        print(f"[FAIL] 服务目录不存在：{workdir}")
        return False
    own_python = venv_python(service)
    if own_python is None:
        print(f"[WARN] {service} 没有自己的 .venv，回落当前解释器 {sys.executable}"
              "（重依赖缺失时会静默降级，请在服务目录建 venv 后重跑）")
    python_exe = engine_python(service)
    print(f"[INFO] {service} 解释器：{python_exe}")
    cmd = uvicorn_command(APP_MODULES[service], PORTS[service], python_exe)
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
    if service == "ft":
        body = probe("ft")
        if not isinstance(body, dict):
            return "UP (端口在监听，/api/v1/fine-tune/health 未响应)"
        # engine_available=false 是**如实读数**（重依赖缺失或懒加载失败），不粉饰成可用
        ready = "true" if body.get("engine_available") else "false"
        missing = body.get("deps_missing") or []
        tail = f"，缺 {','.join(map(str, missing))}" if missing else ""
        device = body.get("device") or "?"
        return f"UP (engine_available={ready}，device={device}{tail})"
    if service == HUB_SERVICE:
        body = probe(HUB_SERVICE)
        if not isinstance(body, dict):
            return "UP (端口在监听，/health 未响应)"
        parts = []
        for item in body.get("services") or []:
            capability = item.get("capability")
            if item.get("skipped"):
                parts.append(f"{capability}:跳过")
            else:
                parts.append(f"{capability}:{'ok' if item.get('ready') else '降级'}")
        return f"UP ({'，'.join(parts)})"
    if service == "deepgraph":
        body = probe("deepgraph")
        if not isinstance(body, dict):
            return "UP (端口在监听，/api/v1/deepgraph/health 未响应)"
        engine = body.get("engine") or {}
        provider = engine.get("provider") or "?"
        neo4j = (body.get("neo4j") or {}).get("reachable")
        courses = body.get("courses_indexed") or []
        return f"UP (provider={provider}，neo4j={'up' if neo4j else 'down'}，已建索引课程={courses or '无'})"
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
    # None = 未显式指定 --only，稍后按布局补齐；显式才收窄
    only = None
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
    # 布局：默认 hub（单端口 8600）；ENGINE_LAYOUT=separate 拆回三进程
    layout = engine_layout(env)
    order = order_for(layout)
    status = status_snapshot()
    # 退役别名解析：`--only rag` 与 `--only deepgraph` 等价（同一个进程）
    only = [RETIRED_SERVICES.get(name, name) for name in (only or order)]
    only = [name for name in only if name in order] or list(order)

    if status_only:
        print(f"[布局] {layout}（ENGINE_LAYOUT 决定；separate = 三个进程三个端口）")
        for name in order:
            print(f"{name:6s} port {PORTS[name]}: {describe(name, status[name])}")
        if layout == "hub":
            # hub 内知识库与 deepgraph 同前缀，能力视图由 /health 的 services[] 给出
            print(f"{'rag':6s} port {PORTS[HUB_SERVICE]}（hub 内嵌，8002 已退役）: "
                  f"{describe(HUB_SERVICE, status[HUB_SERVICE])}")
        else:
            # 知识库（原 8002）不再是独立端口：它的存活就是 8501 在听
            print(f"{'rag':6s} port {PORTS['deepgraph']}（内嵌 deepgraph，8002 已退役）: "
                  f"{describe('rag', status['deepgraph'])}")
        return 0

    neo4j_home = discover_neo4j_home(env)
    if layout == "hub":
        # hub 只需一个进程：三能力任一开启即拉起（具体加载哪些由 ENGINE_HUB_SKIP 下发）
        capabilities = (
            should_start(env.get("KG_ENABLED")),
            should_start(env.get("DG_RAG_ENABLED")),
            should_start(env.get("FT_ENABLED")),
        )
        want = {
            "neo4j": should_start(env.get("NEO4J_ENABLED")) and bool(neo4j_home),
            HUB_SERVICE: any(capabilities),
        }
    else:
        want = {
            "neo4j": should_start(env.get("NEO4J_ENABLED")) and bool(neo4j_home),
            "kg": should_start(env.get("KG_ENABLED")),
            # 8002 退役后知识库随 deepgraph 进程一起起，没有独立开关；
            # `.env` 的 RAG_ENABLED 只剩「点不点这个能力」的历史语义，不再决定进程
            "deepgraph": should_start(env.get("DG_RAG_ENABLED")),
            # 默认也起（四引擎全编排）；不想要 LoRA 就在 .env 写 FT_ENABLED=false
            "ft": should_start(env.get("FT_ENABLED")),
        }
    want = {name: want.get(name, False) and name in only for name in order}
    todo = plan(status, want, order)

    api_key = effective_api_key(env.get("DEEPSEEK_API_KEY"))
    offline = offline_flags(env)
    uri = (env.get("KG_NEO4J_URI") or env.get("NEO4J_URI") or "bolt://127.0.0.1:7687").strip()

    print("=" * 60)
    print(" AI Course Platform - engines "
          + ("(Neo4j / Engine Hub :8600 承载 KG+DeepGraph+知识库+LoRA)" if layout == "hub"
             else "(Neo4j / KG / DeepGraph+知识库 / LoRA · separate 回滚布局)"))
    print("=" * 60)
    if not todo:
        print("[OK] 引擎端口都已在监听，本次不重复拉起任何进程。"
              + ("（hub :8600 单口）" if layout == "hub" else ""))
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
            label = "ENGINE HUB" if name == HUB_SERVICE else name.upper()
            print(f"[..] 启动 {label} 引擎（:{PORTS[name]}，新窗口，日志在窗口内）")
            ok = start_engine(name, exported)
        print(f"[{'OK' if ok else 'FAIL'}] {name} {'已监听' if ok else '未在预期时间内监听'}")

    final = status_snapshot()
    if want["neo4j"] and not neo4j_home and ({"kg", HUB_SERVICE} & set(only)):
        print(
            "[WARN] 找不到 Neo4j 安装目录（.env 的 NEO4J_HOME 或已知位："
            f"{' / '.join(DEFAULT_NEO4J_HOMES)}）。KG 会回落内存图，重启即丢。"
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
    for name in order:
        print(f"{name:6s} :{PORTS[name]}  {describe(name, final[name])}")
    if layout == "hub":
        # 知识库（原 8002，已退役）：能力视图挂在 hub 内，与 deepgraph 同生同死
        print(f"{'rag':6s} :{PORTS[HUB_SERVICE]}  {describe(HUB_SERVICE, final[HUB_SERVICE])}")
    else:
        # 知识库（原 8002，已退役）：能力视图挂在 8501 上，与 deepgraph 同生同死
        print(f"{'rag':6s} :{PORTS['deepgraph']}  {describe('rag', final['deepgraph'])}")
    print("-" * 60)

    if is_port_listening(8080) and todo:
        print(
            "[WARN] 后端已在跑而引擎刚起：Java 只在启动那一刻探测引擎，"
            "需重启 start.bat backend 才会切到 mode=ENGINE。"
        )
    if layout == "separate" and final["kg"] and not final["neo4j"]:
        print("[INFO] Neo4j 端口未起 -> KG 只能是内存图或尚未初始化，见上一行 backend 判定")
    kg_probe = "kg" if layout == "separate" else HUB_SERVICE
    kg_backend = graph_backend(probe(kg_probe)) if final[kg_probe] else "down"
    if kg_backend == "memory":
        hint = "（Neo4j 本次刚起，KG 是更早起的进程，需关掉它的窗口重跑 start-engines.py 才会接上图库）" if "neo4j" in todo else ""
        print(f"[WARN] KG 走的是内存图{hint} -> 图谱重启即丢，接上后重跑 python scripts\\sync-kg-to-python.py 1 4007")
    if reseed:
        run_reseed(courses or ["1", "4007"])
    elif final.get("deepgraph") or final.get(HUB_SERVICE):
        docs = probe("rag")
        items = docs.get("documents") if isinstance(docs, dict) else docs
        if isinstance(items, list) and not items:
            print("[INFO] RAG 语料为空 -> 问答会返「AI 服务暂时不可用」。重放：python scripts\\seed-rag-corpus.py")

    wanted = [name for name in order if name != "neo4j" and want.get(name)]
    missing = [name for name in wanted if not final.get(name)]
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
