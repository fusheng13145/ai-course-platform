"""
`scripts/start-engines.py` 的守卫测试（Neo4j / KG / RAG 一键启动的决策逻辑）。

为什么测的是决策而不是子进程：一键脚本真正会静默坏事的是**判定**——
把 `.env` 里的占位密钥当有效密钥导出（DR-38 同型假绿）、缓存缺失时仍设
`HF_HUB_OFFLINE=1`（首轮换机即挂死）、已经在跑的引擎被重复拉起（端口冲突、
双实例分叉限流与日志，DR-36 实测）、`.env` 显式写 `KG_ENABLED=false` 却被忽略。
起进程与等端口这些 IO 由真跑取证（见手册 §18.5），不在这里打桩假装验证。

运行：`python -m pytest scripts/tests -q`（必须在仓库根执行；本仓没有
`python-services/` 之外的聚合 pytest 运行，CI 的两个 python 作业只扫各自服务目录，
所以本文件不会被 CI 收录——已作为缺口上报项目主，不擅自改共享的 ci.yml）。
"""

import importlib.util
import os
import re
import socket
import sys
import threading
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "start-engines.py"


def _load_module():
    assert MODULE_PATH.exists(), f"一键启动脚本不存在：{MODULE_PATH}"
    spec = importlib.util.spec_from_file_location("start_engines", MODULE_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


eng = _load_module()


# ------------------------------------------------------------------ .env 解析


def test_parse_env_strips_comments_blank_and_quotes():
    parsed = eng.parse_env(
        "\n".join(
            [
                "# comment line",
                "",
                "JWT_SECRET=abc123",
                "  KG_ENABLED = true  ",
                'NEO4J_HOME="D:\\neo4j"',
                "NO_VALUE_KEY=",
            ]
        )
    )
    assert parsed["JWT_SECRET"] == "abc123"
    assert parsed["KG_ENABLED"] == "true"
    assert parsed["NEO4J_HOME"] == "D:\\neo4j"
    assert parsed["NO_VALUE_KEY"] == ""
    assert "# comment line" not in " ".join(parsed)


def test_effective_api_key_rejects_placeholder_but_accepts_real_shape():
    # .env 里的占位串（前导空格 + 尾注释）在 DR-38 曾被报成「已配置」
    assert eng.effective_api_key("  # put your key here") is None
    assert eng.effective_api_key("") is None
    assert eng.effective_api_key(None) is None
    assert eng.effective_api_key("sk-abcdef") == "sk-abcdef"
    assert eng.effective_api_key("  sk-abcdef  ") == "sk-abcdef"


# ---------------------------------------------------------------- Neo4j 定位


def test_discover_neo4j_home_prefers_env_then_defaults_and_reports_none():
    seen = eng.discover_neo4j_home({"NEO4J_HOME": r"E:\neo4j-custom"}, exists=lambda p: True)
    assert seen == r"E:\neo4j-custom"

    # 只有默认安装位存在时才用它；都不存在必须返回 None（缺依赖上报项目主，不自行安装）
    seen = eng.discover_neo4j_home({}, exists=lambda p: p == eng.DEFAULT_NEO4J_HOMES[0])
    assert seen == eng.DEFAULT_NEO4J_HOMES[0]

    assert eng.discover_neo4j_home({"NEO4J_HOME": "D:\\not-here"}, exists=lambda p: False) is None


def test_env_home_is_trusted_only_when_it_exists():
    # 写了 NEO4J_HOME 但目录不存在：不能回落到默认位而把图库起在别处（会静默换库）
    assert eng.discover_neo4j_home({"NEO4J_HOME": "D:\\gone"}, exists=lambda p: False) is None


# -------------------------------------------------- RAG 离线开关（挂死根因）


def test_offline_flags_set_only_when_model_is_cached():
    # HF_HOME 未设时走真实的 ~/.cache/huggingface/hub，所以这里显式给 HF_HOME 才能注入假 exists
    hub = r"C:\hf\hub"
    snap = hub + "\\models--BAAI--bge-small-zh-v1.5"
    env = {"HF_HOME": r"C:\hf"}
    assert eng.offline_flags(env, exists=lambda p: p == snap) == {
        "HF_HUB_OFFLINE": "1",
        "TRANSFORMERS_OFFLINE": "1",
    }

    # 缓存缺失时绝不设离线位，否则新机器首轮嵌入直接报错而不是去下载
    assert eng.offline_flags(env, exists=lambda p: False) == {}


def test_offline_flags_honour_hf_home_override():
    snap = r"E:\models\hub\models--BAAI--bge-small-zh-v1.5"
    flags = eng.offline_flags({"HF_HOME": r"E:\models"}, exists=lambda p: p == snap)
    assert "HF_HUB_OFFLINE" in flags


def test_offline_flags_follow_a_custom_embed_model():
    env = {"HF_HOME": r"C:\hf", "RAG_EMBED_MODEL": "BAAI/bge-m3"}
    snap = r"C:\hf\hub\models--BAAI--bge-m3"
    assert eng.offline_flags(env, exists=lambda p: p == snap) != {}
    # 阴性对照：只认自定义模型的快照位；认到默认模型目录上不该放行
    assert eng.offline_flags(env, exists=lambda p: p.endswith("bge-small-zh-v1.5")) == {}


# ------------------------------------------------------------------ 起不起


def test_should_start_respects_explicit_disabled_switch():
    # .env 显式写 false 一律优先（与 start-backend.bat 第 5 步同口径）
    assert eng.should_start("false") is False
    assert eng.should_start(" FALSE ") is False
    assert eng.should_start("0") is False
    assert eng.should_start("true") is True
    assert eng.should_start(None) is True


def test_plan_starts_only_what_is_down_and_in_dependency_order():
    # 2026-11-24（批次 3B-2）：8002 退役，ORDER 收敛为四项（知识库并入 deepgraph）
    plan = eng.plan(
        status={"neo4j": False, "kg": False, "deepgraph": False, "ft": False},
        want={"neo4j": True, "kg": True, "deepgraph": True, "ft": True},
    )
    assert plan == ["neo4j", "kg", "deepgraph", "ft"]

    # 已在跑的绝不重启：双实例共用一套库却各自计限流（DR-36）
    plan = eng.plan(
        status={"neo4j": True, "kg": True, "deepgraph": False, "ft": False},
        want={"neo4j": True, "kg": True, "deepgraph": True, "ft": True},
    )
    assert plan == ["deepgraph", "ft"]

    # 图库起不来时 KG 照起（回落内存图），知识库（并进 8501）与图库无关
    plan = eng.plan(
        status={"neo4j": False, "kg": False, "deepgraph": False, "ft": False},
        want={"neo4j": False, "kg": True, "deepgraph": True, "ft": False},
    )
    assert plan == ["kg", "deepgraph"]

    # ft 关掉（.env 写 FT_ENABLED=false）时不得被默认拉起来
    plan = eng.plan(
        status={"neo4j": True, "kg": True, "deepgraph": True, "ft": False},
        want={"neo4j": True, "kg": True, "deepgraph": True, "ft": False},
    )
    assert plan == []


def test_plan_is_empty_when_everything_is_already_up():
    assert (
        eng.plan(
            status={"neo4j": True, "kg": True, "deepgraph": True, "ft": True},
            want={"neo4j": True, "kg": True, "deepgraph": True, "ft": True},
        )
        == []
    )


def test_retired_8002_service_no_longer_has_a_port_or_a_launch_entry():
    """3B-2 退役守卫：8002 不得再出现在端口表/服务目录/模块表里，否则启动器会拉起第二套知识库。

    同时钉住「退役不是能力丢失」：`rag` 仍能解析，只是解析到 8501 的那一个进程。
    """
    assert "rag" not in eng.PORTS and "rag" not in eng.ORDER
    assert "rag" not in eng.SERVICE_DIRS and "rag" not in eng.APP_MODULES
    assert eng.RETIRED_SERVICES == {"rag": "deepgraph"}
    # 知识库旋钮不许随退役消失：它必须在 deepgraph 的导出名单里（少一个＝8501 里静默降级）
    exported = set(eng.ENGINE_EXPORTS["deepgraph"])
    for knob in ("RAG_EMBED_MODEL", "RAG_DATA_DIR", "RAG_CHUNK_SIZE", "RAG_RERANK_ENABLED", "RAG_HYDE_ENABLED"):
        assert knob in exported
    assert "rag" not in eng.ENGINE_EXPORTS


# --------------------------------------------------------------- 子进程环境


def test_engine_env_exports_uri_and_never_a_placeholder_key():
    base = {"DEEPSEEK_API_KEY": "  # placeholder", "JWT_SECRET": "keepme"}

    kg_env = eng.engine_env(base, service="kg", neo4j_uri="bolt://127.0.0.1:7687", api_key=None)
    assert kg_env["NEO4J_URI"] == "bolt://127.0.0.1:7687"
    # 占位值不得转给引擎：引擎拿它会去调 LLM 再失败，问答比导出为空更慢更难判
    assert "DEEPSEEK_API_KEY" not in kg_env
    assert kg_env["JWT_SECRET"] == "keepme"

    rag_env = eng.engine_env(base, service="rag", neo4j_uri=None, api_key="sk-real")
    assert rag_env["DEEPSEEK_API_KEY"] == "sk-real"
    assert "NEO4J_URI" not in rag_env
    # `rag` 是能力别名：导出名单实际就是 deepgraph 那一套（同一进程两套路由）
    assert set(eng.ENGINE_EXPORTS[eng.RETIRED_SERVICES["rag"]]) == set(eng.ENGINE_EXPORTS["deepgraph"])

    rag_env = eng.engine_env(base, service="rag", neo4j_uri=None, api_key=None)
    assert "DEEPSEEK_API_KEY" not in rag_env

    # 原 dict 不能被改坏（父进程环境还要给后端用）
    assert base["DEEPSEEK_API_KEY"] == "  # placeholder"


def test_engine_env_merges_offline_flags_for_the_knowledge_hosting_service():
    """离线位必须跟着知识库走：它现在跑在 8501 进程里（含 sentence-transformers），
    缺 HF_HUB_OFFLINE 时无外网会挂死（§18.5 同型）。`rag` 别名同样要拿到。"""
    base = {"PATH": "x"}
    offline = {"HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1"}
    rag_env = eng.engine_env(base, service="rag", neo4j_uri=None, api_key=None, offline=offline)
    assert rag_env["HF_HUB_OFFLINE"] == "1"

    kg_env = eng.engine_env(base, service="kg", neo4j_uri="bolt://x", api_key=None, offline=offline)
    assert "HF_HUB_OFFLINE" not in kg_env


def test_engines_never_read_dotenv_so_the_helper_must_export_both_keys():
    # DR-39：两个 FastAPI 服务只调 os.getenv，没有 dotenv。
    # 因此导出责任必须在启动器里显式完成——用源码守卫钉住「脚本自己导出 NEO4J_URI/HF 位」，
    # 一旦有人把它改成「让引擎读 .env」就会点红。
    src = MODULE_PATH.read_text(encoding="utf-8")
    assert "NEO4J_URI" in src and "HF_HUB_OFFLINE" in src
    # 阴性对照：缺键名的片段确实不会命中（证明守卫非恒真）
    assert not ("TRANSFORMERS_NEVER_SET" in src)


def test_engine_env_forwards_the_dotenv_knobs_the_engines_actually_read():
    # DR-63：`.env` 里 advertised 的调优旋钮与 Neo4j 认证键，此前只喂给启动器自己的判定，
    # 传给子进程的 base 却是 os.environ -> 配了也不生效，引擎静默用内置默认值。
    dotenv = {
        "RAG_EMBED_MODEL": "BAAI/bge-m3",
        "RAG_DATA_DIR": r"D:\rag-data",
        "RAG_CHUNK_SIZE": "500",
        "RAG_CHUNK_OVERLAP": "80",
        "DEEPSEEK_MODEL": "deepseek-reasoner",
        "NEO4J_USERNAME": "kg_reader",
        "NEO4J_PASSWORD": "not-a-real-secret",
        "KG_BACKEND": "neo4j",
        "NEO4J_URI": "bolt://127.0.0.1:7687",
    }

    rag_env = eng.engine_env({}, service="rag", api_key=None, dotenv=dotenv)
    assert rag_env["RAG_EMBED_MODEL"] == "BAAI/bge-m3"
    assert rag_env["RAG_CHUNK_SIZE"] == "500"
    assert rag_env["DEEPSEEK_MODEL"] == "deepseek-reasoner"
    # 8002 退役后知识库跑在 8501 进程里，该进程本来就要 Neo4j 认证（图谱能力），
    # 所以 NEO4J_PASSWORD 出现在这里属于**同期同进程**的正当导出，不再是「串服务」。
    assert rag_env["NEO4J_PASSWORD"] == "not-a-real-secret"

    kg_env = eng.engine_env(
        {}, service="kg", neo4j_uri="bolt://127.0.0.1:7687", api_key=None, dotenv=dotenv
    )
    assert kg_env["NEO4J_USERNAME"] == "kg_reader"
    assert kg_env["NEO4J_PASSWORD"] == "not-a-real-secret"
    assert kg_env["KG_BACKEND"] == "neo4j"
    assert "RAG_CHUNK_SIZE" not in kg_env

    # 空串/纯空白不导出：否则会把引擎自身的默认值盖成空（int() 直接抛 ValueError）
    blank = eng.engine_env({}, service="rag", dotenv={"RAG_CHUNK_SIZE": "   ", "RAG_DATA_DIR": ""})
    assert "RAG_CHUNK_SIZE" not in blank and "RAG_DATA_DIR" not in blank
    # 不传 dotenv 仍是旧行为（base 有什么就是什么），保持调用方可用
    assert "RAG_CHUNK_SIZE" not in eng.engine_env({}, service="rag", api_key=None)
    # 父进程 dict 不能被改坏
    assert dotenv["RAG_CHUNK_SIZE"] == "500"


def test_engine_export_list_covers_every_var_the_engines_read():
    # 漂移护栏（DR-63 的复发防止）：引擎新增一个 os.getenv 读取，而启动器名单没跟上，
    # 就又回到「.env 配了不生效」——这类缺口没有报错，只能靠集合包含关系钉住。
    read = set()
    for rel in (
        "python-services/kg_service/kg_store.py",
        # 8002 退役后知识库的实读点就在 8501 的这两个模块里；退役不动磁盘目录，
        # 因此原 rag_service 的读取点继续留在扫描清单（防止「以退役为由」漏导出）
        "python-services/rag_service/rag_engine.py",
        "python-services/rag_service/rag_service.py",
        "python-services/deepgraph_rag/knowledge_engine.py",
        "python-services/deepgraph_rag/knowledge_api.py",
        "python-services/fine_tune_service/ft_llm.py",
        "python-services/fine_tune_service/lora_engine.py",
        "python-services/fine_tune_service/lora_service.py",
    ):
        src = (ROOT / rel).read_text(encoding="utf-8", errors="replace")
        read |= set(re.findall(r"""os\.(?:getenv|environ\.get)\(\s*["']([A-Z0-9_]+)""", src))
    assert read, "引擎源码里一个 os.getenv 都没匹配到——守卫的正则已失效"

    # 这几个由专用参数处理（api_key / neo4j_uri），RAG_ENGINE_READY 只在契约测试里置位；
    # FT_ENGINE_READY 是 CI 用的“不启动训练线程”开关，故意不导出（误配会让 /start 静默不训练）。
    handled_elsewhere = {"DEEPSEEK_API_KEY", "NEO4J_URI", "KG_NEO4J_URI", "RAG_ENGINE_READY", "FT_ENGINE_READY"}
    exported = {key for keys in eng.ENGINE_EXPORTS.values() for key in keys}
    assert sorted(read - handled_elsewhere - exported) == []
    # 阴性对照：名单不是「全导出」，随便一个没读过的键确实不在里面
    assert "NOT_A_REAL_KNOB" not in exported


# ------------------------------------------------------------------ 命令构造


def test_uvicorn_command_uses_current_interpreter_and_loopback_only():
    cmd = eng.uvicorn_command("kg_service:app", 8001, python_exe=r"C:\py\python.exe")
    assert cmd[:3] == [r"C:\py\python.exe", "-m", "uvicorn"]
    assert "kg_service:app" in cmd
    # 两个引擎都无鉴权，绑 0.0.0.0 等于把图谱写接口开放到局域网
    assert "127.0.0.1" in cmd and "--host" in cmd
    assert "0.0.0.0" not in cmd


def test_venv_python_points_at_the_service_local_venv():
    """重依赖只装在服务自己的 `.venv` 里；用系统解释器起 8501 会让知识库静默降级
    （端口在听、检索恒空）。

    因此解释器选择必须是「优先服务 venv」，且 venv 缺失时**显式返回 None** 让调用方上报，
    不允许悄悄换回 `sys.executable` 而仍宣称引擎真实可用。
    """
    path = eng.venv_python("deepgraph", exists=lambda p: True)
    assert path is not None
    assert str(ROOT / "python-services" / "deepgraph_rag") in path
    if os.name == "nt":
        assert path.endswith(r".venv\Scripts\python.exe")
    else:  # pragma: no cover - 本机为 Windows，这里只是不炸
        assert path.endswith(".venv/bin/python")

    # `rag` 别名解析到同一服务目录：退役后不另建第二份 venv
    assert eng.venv_python("rag", exists=lambda p: True) == path

    # 阴性对照：venv 缺失必须返回 None（KG 本机就没有 .venv，这是被允许的降级）
    assert eng.venv_python("deepgraph", exists=lambda p: False) is None


def test_venv_python_checks_the_file_system_with_a_file_test(tmp_path):
    """回归：`default_exists` 是 `is_dir()`（给 Neo4j 目录/模型快照用的），
    拿它判 `python.exe` 会恒为 False —— 启动器就会一直静默回落到系统解释器（2026-11-13 实测踩中）。
    """
    sub = ("Scripts", "python.exe") if os.name == "nt" else ("bin", "python")
    exe = tmp_path.joinpath("python-services", "deepgraph_rag", ".venv", *sub)
    exe.parent.mkdir(parents=True)
    exe.write_text("", encoding="utf-8")
    assert eng.venv_python("deepgraph", root=tmp_path) == str(exe)
    # 只有目录、没有解释器文件时仍然返回 None
    assert eng.venv_python("kg", root=tmp_path) is None


def test_engine_python_is_the_shared_decision_both_launchers_use():
    """`start-all.py` 也构造 uvicorn 命令；解释器的裁决只准有一处（否则两条路径会分叉）。"""
    assert eng.engine_python("kg") == (eng.venv_python("kg") or sys.executable)
    assert eng.engine_python("deepgraph") == (eng.venv_python("deepgraph") or sys.executable)


def test_wait_until_succeeds_on_later_attempt_and_times_out_otherwise():
    slept = []
    stamps = iter([0.0, 1.0, 2.0])
    calls = {"n": 0}

    def third_time():
        calls["n"] += 1
        return calls["n"] == 3

    ok = eng.wait_until(
        third_time, timeout=10.0, interval=1.0, sleep=slept.append, now=lambda: next(stamps)
    )
    assert ok is True
    assert slept == [1.0, 1.0]  # 成功后不再睡

    # 超时分支用「会前进的时钟」，否则 now() 恒定会让 elapsed 永远不到期而空转
    over = iter([0.0, 1.0, 2.0, 3.0])
    never = eng.wait_until(
        lambda: False, timeout=2.0, interval=1.0, sleep=lambda s: None, now=lambda: next(over)
    )
    assert never is False


def test_is_port_listening_sees_a_real_listener(tmp_path):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.bind(("127.0.0.1", 0))
    srv.listen(1)
    port = srv.getsockname()[1]

    try:
        assert eng.is_port_listening(port) is True
        # 反向对照：同一 host 上没人监听的口必须判 False，否则「已在跑」会被误判而漏起服务
        probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        probe.bind(("127.0.0.1", 0))
        free_port = probe.getsockname()[1]
        probe.close()
        assert eng.is_port_listening(free_port) is False
    finally:
        srv.close()


def test_service_urls_are_the_documented_probe_paths():
    # 8002 已退役：知识库探针改指 8501 的 /api/v1/rag/documents
    # （该路由从来没有 /health，拿 /health 探会把活引擎判成死的）
    assert eng.probe_url("kg") == "http://127.0.0.1:8001/api/v1/graph/health"
    assert eng.probe_url("rag") == "http://127.0.0.1:8501/api/v1/rag/documents"
    assert eng.probe_url("deepgraph") == "http://127.0.0.1:8501/api/v1/deepgraph/health"
    # 阴性对照：退役端口不得再被任何探针引用
    assert all("8002" not in eng.probe_url(name) for name in ("kg", "rag", "deepgraph", "ft"))


@pytest.mark.parametrize("bad", ["", None])
def test_effective_api_key_handles_missing_env_value(bad):
    assert eng.effective_api_key(bad) is None


def test_neo4j_is_launched_in_console_mode_not_start():
    """`neo4j start` 要求**已安装为服务**（需管理员权限），zip 单机版直接报
    "Neo4j service is not installed / Unable to start"（2026-10-10 真跑实测）。
    一键路径只能用 `console`，在前台跑，关窗即停。

    启动脚本名随平台：Windows 是 `bin\\neo4j.bat`，Linux/macOS 是无扩展名的 `bin/neo4j`。
    写死 `.bat` 会让 Linux 启动器在 spawn 前就找不到文件（E-01 残项，2026-11-16 修）——
    本用例同时跑在 windows-latest 与 ubuntu-latest 上，故只钉语义与后缀，不钉反斜杠形态。"""
    home = r"D:\neo4j-community-5.26.12"
    cmd = eng.neo4j_launch_command(home)
    expected_name = "neo4j.bat" if os.name == "nt" else "neo4j"
    assert cmd[-1] == "console", "一键路径必须 console；start 在 zip 单机版必报服务未安装"
    assert cmd[0].endswith(expected_name), f"平台 {os.name} 的启动脚本名不对：{cmd[0]}"
    assert "bin" in cmd[0]
    assert "start" not in cmd


def test_engine_ready_needs_the_port_and_an_answering_probe():
    # uvicorn 先绑端口再装载路由；只看端口会把「还没初始化完」判成就绪
    assert eng.engine_ready(port_up=True, health={"status": "ok", "backend": "memory"}) is True
    assert eng.engine_ready(port_up=False, health={"status": "ok"}) is False
    assert eng.engine_ready(port_up=True, health=None) is False


def test_graph_backend_reads_the_real_payload_and_labels_memory_honestly():
    assert eng.graph_backend({"status": "ok", "backend": "neo4j"}) == "neo4j"
    assert eng.graph_backend({"status": "ok", "backend": "memory"}) == "memory"
    # 拿不到响应只能说 unknown，不能把「内存图」误报成 Neo4j
    assert eng.graph_backend(None) == "unknown"
    assert eng.graph_backend({}) == "unknown"

def test_effective_api_key_rejects_non_ascii_value():
    """DR-82：Key 值里粘了中文备注（实测形态：`sk-xxx已移除泄露密钥；问答需由成员轮换后重新填入`）。

    这种值能过 `sk-` 前缀检查，却会在引擎写 Authorization 头时抛 UnicodeEncodeError；
    旧代码把该异常整段吞掉，故障表现为"AI 恒为抽取式"且无从定位。形状门必须提前判它不可用。
    """
    assert eng.effective_api_key("sk-abcdef已移除清露") is None
    assert eng.effective_api_key("sk-0123456789abcdef") == "sk-0123456789abcdef"
