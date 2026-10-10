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
    plan = eng.plan(
        status={"neo4j": False, "kg": False, "rag": False},
        want={"neo4j": True, "kg": True, "rag": True},
    )
    assert plan == ["neo4j", "kg", "rag"]

    # 已在跑的绝不重启：双实例共用一套库却各自计限流（DR-36）
    plan = eng.plan(
        status={"neo4j": True, "kg": True, "rag": False},
        want={"neo4j": True, "kg": True, "rag": True},
    )
    assert plan == ["rag"]

    # 图库起不来时 KG 照起（回落内存图），RAG 与图库无关
    plan = eng.plan(
        status={"neo4j": False, "kg": False, "rag": False},
        want={"neo4j": False, "kg": True, "rag": True},
    )
    assert plan == ["kg", "rag"]


def test_plan_is_empty_when_everything_is_already_up():
    assert (
        eng.plan(
            status={"neo4j": True, "kg": True, "rag": True},
            want={"neo4j": True, "kg": True, "rag": True},
        )
        == []
    )


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

    rag_env = eng.engine_env(base, service="rag", neo4j_uri=None, api_key=None)
    assert "DEEPSEEK_API_KEY" not in rag_env

    # 原 dict 不能被改坏（父进程环境还要给后端用）
    assert base["DEEPSEEK_API_KEY"] == "  # placeholder"


def test_engine_env_merges_offline_flags_for_rag_only():
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


# ------------------------------------------------------------------ 命令构造


def test_uvicorn_command_uses_current_interpreter_and_loopback_only():
    cmd = eng.uvicorn_command("kg_service:app", 8001, python_exe=r"C:\py\python.exe")
    assert cmd[:3] == [r"C:\py\python.exe", "-m", "uvicorn"]
    assert "kg_service:app" in cmd
    # 两个引擎都无鉴权，绑 0.0.0.0 等于把图谱写接口开放到局域网
    assert "127.0.0.1" in cmd and "--host" in cmd
    assert "0.0.0.0" not in cmd


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
    # 8002 的 /health 不存在（返 404），用它当探针会把活着的引擎判成死的
    assert eng.probe_url("kg") == "http://127.0.0.1:8001/api/v1/graph/health"
    assert eng.probe_url("rag") == "http://127.0.0.1:8002/api/v1/rag/documents"


@pytest.mark.parametrize("bad", ["", None])
def test_effective_api_key_handles_missing_env_value(bad):
    assert eng.effective_api_key(bad) is None


def test_neo4j_is_launched_in_console_mode_not_start():
    """`neo4j.bat start` 要求**已安装为 Windows 服务**（需管理员），zip 单机版直接报
    "Neo4j service is not installed / Unable to start"（2026-10-10 真跑实测）。
    一键路径只能用 `console`，在新窗口里前台跑，关窗即停。"""
    cmd = eng.neo4j_launch_command(r"D:\neo4j-community-5.26.12")
    assert cmd == [r"D:\neo4j-community-5.26.12\bin\neo4j.bat", "console"]
    assert "start" not in cmd
    assert "console" in cmd


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
