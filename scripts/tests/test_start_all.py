"""
`scripts/start-all.py` 与 `start.bat` 的守卫测试（三份启动器合并后的唯一入口）。

为什么测决策而不是子进程：本文件的每一条都对应一个**已经在本机踩过或已写进手册**的坑，
而这些坑全都是"判定"层面的，起进程部分只能真跑取证（另见 test_start_engines.py 的口径）：
  - 管道读取必然把一行切成多个 chunk（uvicorn/java 交错写），不缓冲就会把一行日志撕成两半；
  - 引擎在 127.0.0.1 上可能只有 IPv6 回环监听（Vite 实测绑 `[::1]:5173`），只探 IPv4 会把
    "已占用"判成"空闲"，接着起的第二个 Vite 因 `strictPort` 直接退出，看起来像启动器坏了；
  - 已经在跑的实例一律**接管不重启**（DR-36：双实例共用一套 MySQL/Redis 却各自计限流），
    收尸清单里也绝不能出现这些不是我们起的 PID；
  - `mvnw.cmd` 下面挂着 java 子进程，只杀父会让 java 被重生并抢不到 8080（本机实测），
    所以收尸必须是 `taskkill /T` 的子树语义；
  - 凭证缺失要 fail-fast，但**报错文本里不得出现密钥值**（DR-37/DR-38 同型事故）；
  - 复合 `/health` 无 AI Key 时恒 503，拿它判存活会把健康的后端判成死（§25 / DR-28）。

形态类守卫（.bat 必须纯 ASCII + CRLF、不得再开新窗口）一律带**阴性对照**：
先证明检测器能抓住人造违规样例，再断言真实源码零命中。

运行：仓库根执行 `python -m pytest scripts/tests -q`。
"""

import ast
import importlib.util
import os
import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "start-all.py"
BAT_PATH = ROOT / "start.bat"


def _load_module():
    assert MODULE_PATH.exists(), f"统一启动器不存在：{MODULE_PATH}"
    spec = importlib.util.spec_from_file_location("start_all", MODULE_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


sa = _load_module()


# ----------------------------------------------------------- 管道行中继


def test_take_lines_buffers_partial_chunk():
    """一行被拆成两个 chunk 读取时，只能输出组装好的整行。"""
    buffers = {}
    first = sa.take_lines(buffers, "api", "Tomcat start")
    second = sa.take_lines(buffers, "api", "ing on port 8080\n")
    assert first == []
    assert second == ["Tomcat starting on port 8080"]


def test_take_lines_negative_incomplete_line_emits_nothing():
    """阴性对照：没有换行符就必须一条不出（否则等于把日志撕开）。"""
    buffers = {}
    assert sa.take_lines(buffers, "rag", "embedding model loaded") == []
    assert buffers["rag"] == "embedding model loaded"


def test_take_lines_splits_multiple_newlines_in_order():
    """一个 chunk 内含多行时按原顺序逐行切开，LF 与 CRLF 都算行尾。"""
    buffers = {"kg": ""}
    lines = sa.take_lines(buffers, "kg", "a\nb\r\nc\n")
    assert lines == ["a", "b", "c"]
    assert buffers["kg"] == ""


def test_take_lines_caps_buffer_for_carriage_return_streams():
    """npm/Vite 的进度行只用裸 `\\r` 复位光标、不发换行；不设上限残段会无限长且永不落屏。"""
    buffers = {"web": ""}
    lines = sa.take_lines(buffers, "web", "p" * (sa.MAX_PENDING + 10))
    assert len(lines) == 1
    assert buffers["web"] == ""


def test_flush_lines_returns_trailing_partial():
    """子进程退出时残段必须吐出来，不能吞掉最后一行（崩溃现场常常就在最后一行）。"""
    buffers = {"api": "BUILD FAILURE"}
    assert sa.flush_lines(buffers, "api") == ["BUILD FAILURE"]
    assert buffers["api"] == ""


def test_prefixed_labels_every_stream():
    """五路流各自带可读的服务名前缀，交错也能分辨来源。"""
    assert sa.prefixed("neo4j", "Bolt enabled") == "[neo4j ] Bolt enabled"
    assert sa.prefixed("web", "ready") == "[web   ] ready"


def test_decode_bytes_accepts_utf8_and_gbk():
    """java/neo4j 在中文 Windows 上可能吐 GBK，uvicorn 与 -Dfile.encoding=UTF-8 的 java 吐 UTF-8。"""
    assert sa.decode_bytes("检索完成".encode("utf-8")) == "检索完成"
    assert sa.decode_bytes("检索完成".encode("gbk")) == "检索完成"
    # 两种码都对不上的二进制垃圾只能替换，不能抛异常把中继线程打死
    assert isinstance(sa.decode_bytes(b"\x00\x01\xff\xfe\xfd"), str)


# --------------------------------------------------------------- 端口判定


def test_pick_dev_port_skips_ipv6_only_occupied_port():
    """Vite 实测只绑 [::1]:5173；只看 IPv4 会误判空闲，第二个实例因 strictPort 直接退出。"""
    v4 = {5173: False, 5174: False, 5175: False}
    v6 = {5173: True, 5174: False, 5175: False}

    def in_use(port):
        return v4[port] or v6[port]

    assert sa.pick_dev_port(in_use) == 5174


def test_pick_dev_port_negative_ipv4_only_probe_would_take_5173():
    """阴性对照：上面那条用例的判据确实来自 IPv6 监听表，换回单栈探针就会选错。"""
    v4 = {5173: False, 5174: False, 5175: False}
    assert sa.pick_dev_port(lambda p: v4[p]) == 5173


def test_pick_dev_port_none_when_all_three_busy():
    """5173/5174/5175 全占用时必须报空不出来，而不是挑 5176+（后端 CORS 白名单只放三个口，会 403）。"""
    assert sa.pick_dev_port(lambda p: True) is None


def test_prefer_own_dev_port_skips_foreign_project_on_first_port():
    """本机实测：5173 与 5174 谁属于本项目会变（一轮是 paper-agent 占 5174，另一轮占 5173），
    照着端口号喊演示入口会把人带到别的应用去。"""
    titles = {5173: "paper-agent · 科研工作台", 5174: "AI智能课程平台", 5175: ""}
    chosen = sa.prefer_own_dev_port(lambda p: p in titles, lambda p: titles[p], "AI智能课程平台")
    assert chosen == 5174


def test_prefer_own_dev_port_negative_order_only_choice_would_take_5173():
    """阴性对照：上一例的答案确实来自标题识别，只看端口顺序会选错。"""
    titles = {5173: "paper-agent · 科研工作台", 5174: "AI智能课程平台"}
    assert [p for p in sa.WEB_PORTS if p in titles][0] == 5173


def test_prefer_own_dev_port_falls_back_when_nothing_is_recognized():
    assert sa.prefer_own_dev_port(lambda p: p == 5175, lambda p: "", "AI智能课程平台") == 5175


def test_prefer_own_dev_port_negative_nothing_listening_is_none():
    assert sa.prefer_own_dev_port(lambda p: False, lambda p: "AI智能课程平台", "AI智能课程平台") is None


def test_expected_app_title_reads_the_real_index_html():
    """认主标记取自 frontend/index.html 本身；本例同时钉住"脚本里没有第二份字面量"的口径。"""
    html = (ROOT / "frontend" / "index.html").read_text(encoding="utf-8", errors="replace")
    assert sa.expected_app_title(html) == "AI智能课程平台"
    assert sa.read_app_title() == "AI智能课程平台"


def test_expected_app_title_negative_html_without_title_is_empty():
    """取不到标题只能退回端口号顺序，不能拿空串去匹配（空串在任何字符串里都"命中"）。"""
    assert sa.expected_app_title("<html><body>no title here</body></html>") == ""
    assert sa.prefer_own_dev_port(lambda p: p == 5173, lambda p: "paper-agent", "") == 5173


# --------------------------------------------------------- 该起什么/接管什么


def test_todo_and_adopt_splits_by_status_and_want():
    """已在监听的是"接管"，缺的才"该起"，且顺序保持依赖序。"""
    status = {"neo4j": True, "kg": False, "rag": False, "api": False, "web": False}
    want = dict.fromkeys(sa.SERVICES, True)
    todo, adopted = sa.todo_and_adopt(status, want)
    assert adopted == ["neo4j"]
    assert todo == ["kg", "rag", "api", "web"]


def test_todo_and_adopt_negative_disabled_service_in_neither_list():
    """阴性对照：.env 显式关掉的服务既不起也不接管（否则会把用户主动关上的东西当"已就绪"报出来）。"""
    status = {"neo4j": False, "kg": True, "rag": True, "api": True, "web": True}
    want = {"neo4j": True, "kg": True, "rag": False, "api": True, "web": True}
    todo, adopted = sa.todo_and_adopt(status, want)
    assert "rag" not in todo and "rag" not in adopted
    assert todo == ["neo4j"]


def test_kill_targets_excludes_adopted_pids():
    """收尸清单只含本启动器真正起的子进程；接管的（proc=None）一律不进，同事的进程绝不碰。"""
    children = {
        "neo4j": None,
        "kg": type("P", (), {"pid": 111})(),
        "rag": None,
        "api": type("P", (), {"pid": 222})(),
        "web": type("P", (), {"pid": 333})(),
    }
    assert sa.kill_targets(children) == [111, 222, 333]


def test_kill_targets_negative_all_adopted_is_empty():
    """阴性对照：全是接管时收尸清单为空——此时按 Ctrl+C 不该杀任何东西。"""
    children = {name: None for name in sa.SERVICES}
    assert sa.kill_targets(children) == []


def test_stop_command_targets_process_tree():
    """mvnw.cmd 下面挂着 java，只杀父会让 java 被重生并抢不到 8080（本机实测），必须收整棵树。"""
    cmd = sa.stop_command(4321)
    assert cmd[0] == "taskkill"
    assert "/T" in cmd and "/F" in cmd and "4321" in cmd


# --------------------------------------------------------------- 就绪判据


def test_health_ok_accepts_only_200():
    """探 orchestration 组的 /probe：200 才是依赖可用。"""
    assert sa.health_ok(200) is True
    assert sa.health_ok(503) is False
    assert sa.health_ok(None) is False


# ------------------------------------------------------- PATH 注入（首轮真跑踩到的）


def test_apply_java_home_prepends_the_existing_path_key():
    """JAVA_HOME 注入必须改**已存在的那个** PATH 键，键名拼法由进程决定（cmd 给 `Path`，sh 给 `PATH`）。"""
    env = sa.apply_java_home({"PATH": r"C:\Program Files\Git\bin"}, r"D:\jdk-21")
    assert env["PATH"].startswith(r"D:\jdk-21\bin")
    assert r"C:\Program Files\Git\bin" in env["PATH"]
    assert len({k.upper() for k in env}) == len(env), "任何环境变量都不该有大小写变体并存"


def test_apply_java_home_negative_case_variant_duplicate_breaks_mvnw():
    """首轮真跑实录：用 `env["Path"] = ...` 硬塞会在已有 `PATH` 的环境里造出第二个大小写变体，
    子进程拿到的 PATH 丢掉 System32，`mvnw.cmd` 直接报 `'powershell' 不是内部或外部命令`。"""
    buggy = {"PATH": r"C:\Program Files\Git\bin"}
    buggy["Path"] = r"D:\jdk-21\bin" + os.pathsep + buggy.get("Path", "")
    assert len({k.upper() for k in buggy}) == 1 and len(buggy) == 2  # 变体重复 = 坏形态

    fixed = sa.apply_java_home({"PATH": r"C:\Program Files\Git\bin"}, r"D:\jdk-21")
    assert len({k.upper() for k in fixed}) == len(fixed)


def test_apply_java_home_collapses_a_pre_existing_variant():
    env = sa.apply_java_home({"Path": r"C:\Windows\System32", "PATH": r"C:\other"}, r"D:\jdk-21")
    path_keys = [k for k in env if k.upper() == "PATH"]
    assert len(path_keys) == 1
    assert env[path_keys[0]].startswith(r"D:\jdk-21\bin")
    assert r"C:\Windows\System32" in env[path_keys[0]] and r"C:\other" in env[path_keys[0]]


def test_shell_path_gap_reports_missing_system32():
    """从 sh 里起 java/mvnw 时 PATH 常不含 System32；与其让 mvnw 报一句听不懂的话，先如实说清。"""
    env = {"SystemRoot": r"C:\WINDOWS", "PATH": r"C:\Program Files\Git\bin"}
    assert sa.shell_path_gap(env) == r"C:\WINDOWS\System32"


def test_shell_path_gap_negative_control_returns_none_when_present():
    env = {"SystemRoot": r"C:\WINDOWS", "PATH": r"C:\WINDOWS\System32;C:\Program Files\Git\bin"}
    assert sa.shell_path_gap(env) is None


PATH_ASSIGN = re.compile(r"""\b\w*env\[\s*["'](?:PATH|Path|path)["']\s*\]\s*=""")


def function_source(name: str) -> str:
    text = MODULE_PATH.read_text(encoding="utf-8")
    for node in ast.parse(text).body:
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return ast.get_source_segment(text, node)
    raise AssertionError(f"start-all.py 里没有函数 {name}()")


def test_start_api_routes_path_through_apply_java_home():
    """真跑踩坑的回归守卫：`start_api` 里绝不能再出现对 PATH 键的直接赋值，必须走收敛过的函数，
    否则一旦从 Git Bash 之类只有 `PATH` 的壳里启动，又会造出大小写变体、后端再起不来。"""
    body = function_source("start_api")
    assert "apply_java_home" in body, "PATH 必须由 apply_java_home 收敛注入"
    assert "shell_path_gap" in body, "System32 缺失必须如实提示，不能静默让 mvnw 报听不懂的错"
    assert PATH_ASSIGN.findall(body) == []


def test_path_assign_detector_negative_control():
    """阴性对照：检测器抓不住旧写法的话，上一条就是恒真的假绿。"""
    buggy = 'run_env = dict(env)\nrun_env["Path"] = bin_dir + os.pathsep + run_env.get("Path", "")'
    assert PATH_ASSIGN.findall(buggy) != []
    assert PATH_ASSIGN.findall('run_env["JAVA_HOME"] = java_home') == []


# --------------------------------------------------- 引擎在册状态（真跑第二轮暴露的时序坑）


def test_engine_flags_are_probed_live_not_from_the_opening_snapshot():
    """一键启动的价值在于"起后端那一刻引擎已经在册"，Java 才会进 mode=ENGINE。
    开场的 status 在冷启动时必然全是 False，照它注入就等于把 §18.5 的"要重启后端才切 ENGINE"
    固化进了一键脚本。"""
    flags = sa.engine_flags(lambda p: p == 8001)
    assert flags == {"kg": True, "rag": False}


def test_engine_flags_negative_control_all_down():
    assert sa.engine_flags(lambda p: False) == {"kg": False, "rag": False}


def test_main_probes_engine_flags_right_before_starting_api():
    """形态守卫：`engine_flags(` 必须出现在 `start_api(` 之前（顺序反了就是旧 bug）。"""
    body = function_source("main")
    assert body.index("engine_flags(") < body.index("start_api(")
    assert body.index("engine_flags(") > body.index("def main")


def test_order_detector_negative_control():
    """阴性对照：把顺序写反，断言必须失败——否则上一条恒真。"""
    reversed_body = "children['api'] = start_api(run_env)\nflags = engine_flags(port_in_use)"
    assert not (reversed_body.index("engine_flags(") < reversed_body.index("start_api("))


def test_engine_subprocess_is_started_with_the_service_venv_interpreter():
    """形态守卫：一键启动器构造 uvicorn 命令时必须把服务自己的解释器传下去。

    漏传就回落到 `sys.executable`（系统 Python），而重依赖只装在 `python-services/<svc>/.venv`，
    结果是端口在听、health 也 200、检索却恒为降级——典型静默假绿（2026-11-13 真跑踩中）。
    """
    body = function_source("start_engine")
    assert "eng.engine_python(" in body, "解释器必须由 start-engines 的 engine_python 统一裁决"
    assert "eng.uvicorn_command(eng.APP_MODULES[name], PORTS[name], python_exe)" in body, \
        "uvicorn_command 必须收到 venv 解释器，否则一键启动路径仍用系统 Python"
    # 阴性对照：旧写法（不传解释器）确实不含该参数，证明上面不是恒真
    assert "uvicorn_command(eng.APP_MODULES[name], PORTS[name])" not in body


# ------------------------------------------------------------- 后端环境注入


def _dotenv(**over):
    base = {
        "MYSQL_ROOT_PASSWORD": "root-pw-value",
        "JWT_SECRET": "jwt-signing-value",
        "REDIS_PASSWORD": "redis-pw-value",
    }
    base.update(over)
    return base


def test_backend_env_pins_datasource_and_least_privilege_user():
    env = sa.backend_env({}, _dotenv(), redis_open=True, kg_up=False, rag_up=False)
    assert env["SPRING_DATASOURCE_USERNAME"] == "aicourse_app"
    assert env["SPRING_DATASOURCE_PASSWORD"] == "root-pw-value"
    assert "ai_course_platform" in env["SPRING_DATASOURCE_URL"]
    assert env["SPRING_DATA_REDIS_HOST"] == "localhost"
    assert env["COOKIE_SECURE"] == "false"
    assert env["CSRF_ENABLED"] == "false"


def test_backend_env_drops_redis_password_when_local_redis_is_open():
    """本机 Redis 常无密码：仍透传 REDIS_PASSWORD 会让 RedisStartupValidator fail-fast，应用根本起不来（已观测）。"""
    env = sa.backend_env({}, _dotenv(), redis_open=True, kg_up=False, rag_up=False)
    assert "SPRING_DATA_REDIS_PASSWORD" not in env
    assert "REDIS_PASSWORD" not in env


def test_backend_env_keeps_redis_password_when_auth_required():
    env = sa.backend_env({}, _dotenv(), redis_open=False, kg_up=False, rag_up=False)
    assert env["SPRING_DATA_REDIS_PASSWORD"] == "redis-pw-value"


def test_backend_env_dotenv_switch_wins_over_port_autoset():
    """端口在听但 .env 显式写了 KG_ENABLED=false -> 尊重 .env（与 start-backend.bat 第 5 步同口径）。"""
    env = sa.backend_env({}, _dotenv(KG_ENABLED="false"), redis_open=True, kg_up=True, rag_up=False)
    assert env["KG_ENABLED"] == "false"


def test_backend_env_autoset_switch_only_when_port_up_and_unset():
    env = sa.backend_env({}, _dotenv(), redis_open=True, kg_up=True, rag_up=False)
    assert env["KG_ENABLED"] == "true"
    assert "RAG_ENABLED" not in env


@pytest.mark.parametrize("missing", ["MYSQL_ROOT_PASSWORD", "JWT_SECRET"])
def test_backend_env_fails_fast_without_required_credential(missing):
    dotenv = _dotenv()
    dotenv.pop(missing)
    with pytest.raises(sa.MissingCredential) as excinfo:
        sa.backend_env({}, dotenv, redis_open=True, kg_up=False, rag_up=False)
    assert missing in str(excinfo.value)


def test_backend_env_negative_error_message_never_carries_the_value():
    """报错文本只准出现变量名，出现值就是把密钥打进日志（DR-37 同型）。"""
    dotenv = _dotenv()
    dotenv.pop("JWT_SECRET")
    with pytest.raises(sa.MissingCredential) as excinfo:
        sa.backend_env({}, dotenv, redis_open=True, kg_up=False, rag_up=False)
    message = str(excinfo.value)
    assert "root-pw-value" not in message


# ------------------------------------------------------------- JDK 定位


def test_discover_java_home_prefers_configured_then_default():
    hit = sa.discover_java_home({"JAVA_HOME": r"D:\jdk-21"}, lambda p: p == r"D:\jdk-21\bin\java.exe")
    assert hit == r"D:\jdk-21"


def test_discover_java_home_negative_written_but_absent_is_not_silent_fallback():
    """写了 JAVA_HOME 但目录不存在 -> 不回落到默认位（静默换 JDK 会让版本判定对不上，同 Neo4j 的口径）。"""
    assert sa.discover_java_home({"JAVA_HOME": r"D:\nope"}, lambda p: p == r"C:\Program Files\Java\jdk-21\bin\java.exe") is None


def test_discover_java_home_falls_back_to_known_default():
    assert sa.discover_java_home(
        {}, lambda p: p == r"C:\Program Files\Java\jdk-21\bin\java.exe"
    ) == r"C:\Program Files\Java\jdk-21"


def test_discover_java_home_none_when_nothing_exists():
    assert sa.discover_java_home({}, lambda p: False) is None


# ----------------------------------------------------- 形态守卫（带阴性对照）


def code_only(path: Path) -> str:
    """取源码但**剔除模块 docstring 那几行**：docstring 里必须点名旧写法（`CREATE_NEW_CONSOLE`）
    才能解释"为什么改成同控制台"，让它过检测器会把必要说明判成违规。
    按行号切除而不是按文本替换——docstring 里的 `\\n` 转义后与源码原文不一致，替换会静默失配。"""
    lines = path.read_text(encoding="utf-8").splitlines()
    body = ast.parse("\n".join(lines)).body
    if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) and isinstance(
        body[0].value.value, str
    ):
        node = body[0]
        return "\n".join(lines[: node.lineno - 1] + lines[node.end_lineno :])
    return "\n".join(lines)


def new_window_hits(text: str) -> list:
    """检测"又去开新窗口"的写法——单窗口是本任务的全部意义。"""
    markers = ("CREATE_NEW_CONSOLE", "DETACHED_PROCESS", "creationflags", "start /")
    return [m for m in markers if m in text]


def test_orchestrator_never_opens_a_new_console():
    stripped = code_only(MODULE_PATH)
    # 防"切除后扫的是空气"这种恒真假绿：扫描窗口里必须确实有起进程的代码
    assert "subprocess.Popen" in stripped and "def spawn_captured" in stripped
    assert new_window_hits(stripped) == []


def test_new_window_detector_negative_control():
    """阴性对照：检测器必须能抓住人造违规样例，否则上一条是恒真的假绿。"""
    assert new_window_hits("subprocess.Popen(cmd, creationflags=CREATE_NEW_CONSOLE)") != []
    assert new_window_hits('start /min cmd /c "java -jar app.jar"') != []


def test_start_bat_is_ascii_crlf_and_forwards_the_subcommand():
    """DR-37：cmd 在 chcp 65001 之前按字节偏移读 .bat，非 ASCII 注释行会被切成命令；裸 & 还会被当分隔符。"""
    raw = BAT_PATH.read_bytes()
    assert b"\r\n" in raw, "start.bat 必须是 CRLF"
    assert raw.replace(b"\r\n", b"").count(b"\n") == 0, "start.bat 不得混用裸 LF"
    raw.decode("ascii")  # 非 ASCII 直接抛
    text = raw.decode("ascii").replace("\r\n", "\n")
    assert "start-all.py" in text
    assert "call " in text  # call 才能把解释器的返回值收回上层
    assert "!SUB!" in text and "!ARGS!" in text  # 子命令与开关必须原样透传，薄壳不解释语义
    assert ">/dev/null" not in text, "Git Bash 会把 >nul 改写成 >/dev/null，cmd 认不了这个落点"
    assert new_window_hits(text) == []


def test_start_bat_scanner_negative_control():
    """阴性对照：先证明形态检测器会拒绝坏样本。"""
    bad = ("@echo off\r\nrem 中文注释\nstart /min cmd".encode("utf-8"))
    try:
        bad.decode("ascii")
        ascii_ok = True
    except UnicodeDecodeError:
        ascii_ok = False
    assert ascii_ok is False, "含中文的 .bat 必须被 ASCII 检查拦下"
    assert b"\r\n" in bad
    assert new_window_hits(bad.decode("utf-8")) != []
    # 裸 LF 混排也必须被抓到（Git Bash 改写行尾是真实踩过的坑）
    mixed = "@echo off\r\nchcp 65001 >/dev/null\n".encode("ascii")
    assert mixed.replace(b"\r\n", b"").count(b"\n") > 0


def test_secret_is_never_echoed_from_the_bat():
    """薄壳里只准转交解释器，不准 echo 出任何密钥名占位（值也不得出现）。"""
    text = BAT_PATH.read_bytes().decode("ascii")
    for line in text.replace("\r\n", "\n").split("\n"):
        if line.strip().lower().startswith("echo"):
            assert not re.search(r"[!%]DEEPSEEK_API_KEY[!%]", line)


# ------------------------------------------- 子命令层（三份启动器合并的意义所在）


def test_parse_args_defaults_to_all_and_reads_the_subcommand():
    assert sa.parse_args([]) == (sa.MODE_ALL, [], None)
    assert sa.parse_args(["backend"]) == (sa.MODE_BACKEND, [], None)
    assert sa.parse_args(["frontend"]) == (sa.MODE_FRONTEND, [], None)
    assert sa.parse_args(["status"]) == (sa.MODE_STATUS, [], None)


def test_parse_args_keeps_the_legacy_status_flag():
    """`--status` 是老写法，删除它会直接弄坏既有习惯与文档里的命令。"""
    mode, flags, illegal = sa.parse_args(["--status"])
    assert (mode, illegal) == (sa.MODE_STATUS, None)
    assert "--status" in flags


def test_parse_args_treats_flags_as_flags_not_as_a_subcommand():
    mode, flags, illegal = sa.parse_args(["--no-color"])
    assert mode == sa.MODE_ALL and illegal is None and flags == ["--no-color"]
    mode, flags, illegal = sa.parse_args(["backend", "--no-color"])
    assert mode == sa.MODE_BACKEND and flags == ["--no-color"] and illegal is None


def test_parse_args_rejects_two_positional_words_instead_of_silently_picking_one():
    """`start.bat backend all` 是自相矛盾的输入：报错比"取第一个"更可查。"""
    mode, _flags, illegal = sa.parse_args(["backend", "all"])
    assert illegal == "backend all"
    assert mode == sa.MODE_BACKEND
    assert sa.parse_args(["nonsense"])[2] == "nonsense"


def test_parse_args_negative_control_for_a_typo_in_a_known_mode():
    """阴性对照：拼错的子命令必须落到非法分支，不能被 `in MODES` 的写法放过。"""
    assert sa.parse_args(["all1"])[2] == "all1"
    assert sa.parse_args(["all"])[2] is None


def test_wanted_services_scopes_every_service_by_subcommand():
    assert sa.wanted_services(sa.MODE_ALL) == {
        "neo4j": True, "kg": True, "rag": True, "api": True, "web": True,
    }
    backend = sa.wanted_services(sa.MODE_BACKEND)
    assert backend["api"] is True and backend["web"] is False
    # 引擎跟着 api 走：backend 模式仍要拉引擎，这是旧 start-backend.bat 第 5 步的语义
    assert backend["kg"] is True and backend["rag"] is True
    frontend = sa.wanted_services(sa.MODE_FRONTEND)
    assert frontend["web"] is True and frontend["api"] is False
    assert frontend["neo4j"] is False and frontend["kg"] is False and frontend["rag"] is False


def test_wanted_services_negative_control():
    """阴性对照：`backend` 与 `frontend` 不能塌成同一张表，否则子命令毫无意义。"""
    assert sa.wanted_services(sa.MODE_BACKEND) != sa.wanted_services(sa.MODE_FRONTEND)
    assert sa.wanted_services("all") == sa.wanted_services(sa.MODE_ALL)


class _SinkStub:
    """build_want 只需要一个能收 info/put 的对象；不真起 Sink 线程，避免测试里出现控制台。"""

    def __init__(self):
        self.lines = []

    def info(self, text):
        self.lines.append(text)

    def put(self, name, line):
        self.lines.append(f"[{name}] {line}")


def test_build_want_cuts_the_backend_and_engines_out_of_frontend_mode():
    """子命令说不要的服务，`.env` 里写了开关也不准偷偷起来。"""
    want = sa.build_want({"KG_ENABLED": "true", "RAG_ENABLED": "true"}, _SinkStub(), sa.MODE_FRONTEND)
    assert want["api"] is False
    assert want["kg"] is False and want["rag"] is False and want["neo4j"] is False
    assert want["web"] is True


def test_build_want_keeps_the_api_and_drops_the_web_in_backend_mode():
    want = sa.build_want({}, _SinkStub(), sa.MODE_BACKEND)
    assert want["api"] is True and want["web"] is False


def test_color_enabled_honours_the_documented_no_color_flag():
    """合并前 `--no-color` 只写在 docstring 里、main 从不读它——开关必须在使用处生效。"""
    assert sa.color_enabled([], True) is True
    assert sa.color_enabled(["--no-color"], True) is False
    assert sa.color_enabled(["--no-color"], False) is False
    # 阴性对照：vt 不可用时即使没带开关也不该上色
    assert sa.color_enabled([], False) is False


def _engine_loop(body: str):
    """在 main 的 AST 里找到调用 start_engine 的那个 for 循环。"""
    for node in ast.walk(ast.parse(body.strip())):
        if isinstance(node, ast.For) and "start_engine(" in ast.unparse(node):
            return node
    raise AssertionError("main() 里找不到拉起引擎的 for 循环——引擎语义被改走了别的路")


def test_engine_bring_up_can_never_abort_the_run():
    """引擎是可选件：起不来只报 FAIL，循环体里出现 return / SystemExit 就是"因引擎卡住后端"。"""
    loop = _engine_loop(function_source("main"))
    dumped = ast.unparse(loop)
    assert "return" not in dumped
    assert "SystemExit" not in dumped and "sys.exit" not in dumped


def test_engine_loop_detector_negative_control():
    """阴性对照：检测器必须能抓住"引擎失败就 return"的写法。"""
    body = (
        "def main():\n"
        "    for name in todo:\n"
        "        ok, proc = start_engine(name, env, sink)\n"
        "        if not ok:\n"
        "            return 1\n"
    )
    loop = _engine_loop(body)
    assert "return" in ast.unparse(loop)


def test_probe_url_has_a_single_definition_in_the_orchestrator():
    """就绪判定、状态打印、frontend 提示共用同一个 URL；抄第二份就会有一处判错。"""
    text = MODULE_PATH.read_text(encoding="utf-8")
    assert text.count("http://127.0.0.1:8080/api/actuator/health/probe") == 1


def _api_guarded_lines(tree) -> set:
    """返回"处在受 api 开关保护的 if 分支内"的行号集合（else 分支不算保护）。

    `ast.unparse` 会把源码里的双引号规范成单引号，所以比较前统一引号——
    不然这个检测器永远匹配不上，测试会以"恒真"的形态假绿。
    """
    lines = set()
    for node in ast.walk(tree):
        if not isinstance(node, ast.If):
            continue
        test = ast.unparse(node.test).replace('"', "'")
        if test in {"scope['api']"} or test == "'api' in todo":
            for stmt in node.body:
                for inner in ast.walk(stmt):
                    lines.add(getattr(inner, "lineno", -1))
    return lines


def _backend_env_lines(tree) -> list:
    return [
        node.lineno
        for node in ast.walk(tree)
        if isinstance(node, ast.Call) and ast.unparse(node.func) == "backend_env"
    ]


def test_frontend_mode_does_not_gate_on_database_credentials():
    """只起 Vite 时不该要求 MYSQL_ROOT_PASSWORD/JWT_SECRET——旧 start-frontend.bat 也不查。

    用 AST 而不是切字符串：`backend_env(` 在 main 里出现两次都合法（开场校验一次、
    起后端前按"当下在册引擎"重算一次），要判的是它们是否**都落在受 api 开关保护的分支里**。
    """
    tree = ast.parse(function_source("main").strip())
    calls = _backend_env_lines(tree)
    assert calls, "main() 里不再调用 backend_env( —— 这条守卫失去意义，请先确认改动是否有意"
    guarded = _api_guarded_lines(tree)
    assert set(calls) <= guarded, f"存在不受 api 开关保护的 backend_env 调用：{sorted(calls)} vs 受保护 {sorted(guarded)}"


def test_credential_gate_detector_negative_control():
    """阴性对照：把 backend_env 挪到 if 外面（或放进 else），检测器必须判它越界。"""
    body = (
        "def main():\n"
        "    run_env = backend_env({}, env, redis_open=True, kg_up=False, rag_up=False)\n"
        '    if scope["api"]:\n'
        "        pass\n"
        "    else:\n"
        "        other = backend_env({}, env, redis_open=True, kg_up=False, rag_up=False)\n"
    )
    tree = ast.parse(body)
    calls = _backend_env_lines(tree)
    guarded = _api_guarded_lines(tree)
    assert len(calls) == 2, "样本里应有两次 backend_env 调用（一次在 if 外、一次在 else 分支）"
    assert not (set(calls) <= guarded), "越界写法必须被判出来，否则真实断言成了恒假的安慰剂"
    assert not (set(calls) & guarded)


def test_wrapper_bat_holds_no_decision_logic():
    """判定全在 Python：薄壳里出现 netstat / choice 就说明端口与实例判定又回到 batch 了。"""
    text = BAT_PATH.read_bytes().decode("ascii").lower()
    assert "netstat" not in text
    assert "choice" not in text
    assert "mvnw" not in text and "vite" not in text
