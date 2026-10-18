#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实 LLM 链路取证（D6-2 凭据有效性 + D13-1 四链路）。

为什么需要它：本仓此前**没有任何一条命令能证明大模型真的答过话**。
`/actuator/health` 的 `llmProvider` 组件只做静态判空（`OpenAiCompatibleProvider.isConfigured()`
即 `apiKey 非空白`），密钥填错也照样 UP；Python 侧又把 LLM 异常整段吞掉。
于是 401 可以长期存在而无人看见（DR-75 实测：四链路全部返回降级串，health 仍报 configured=true）。

用法：
    python scripts/probe-llm-chain.py engine      # 只测 Python 引擎的生成层（需 rag 依赖齐备）
    python scripts/probe-llm-chain.py chains      # 打 Java 四链路（问答/出题/批改/建议），需后端在跑
    python scripts/probe-llm-chain.py backend     # 用整份 .env 起一份取证后端（含密钥，常驻）
    python scripts/probe-llm-chain.py all         # engine + chains

纪律：
- **密钥值永不打印**，只打印键名、形状判定与派生结论（与 start-all.py 同口径）；
- 判据只有三档：真实生成（LLM）/ 抽取式或降级串（未真实生成）/ 传输或解析异常；
- `chains` 走的是产品端点，不自己造会话、不 mock，因此结论可直接对外主张。
"""
from __future__ import annotations

import importlib.util
import json
import logging
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAG_DIR = ROOT / "python-services" / "rag_service"
JAR = ROOT / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar"
PROBE_PORT = "8081"
DEGRADE = "AI 服务暂时不可用，请稍后再试。"  # AiCoreComponent.SERVICE_ERROR_MSG


def engines():
    spec = importlib.util.spec_from_file_location("eng", ROOT / "scripts" / "start-engines.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def dotenv() -> dict:
    f = ROOT / ".env"
    return engines().parse_env(f.read_text(encoding="utf-8", errors="replace")) if f.exists() else {}


def key_verdict(env: dict) -> str:
    """只报派生结论，不回报值本身。"""
    raw = env.get("DEEPSEEK_API_KEY")
    if raw is None:
        return ".env 无该键"
    v = raw.strip()
    if not v:
        return ".env 该键为空"
    if not engines().effective_api_key(v):
        # 与 start-engines.effective_api_key 同一判据：前缀 + 全 ASCII（DR-82）
        if not v.startswith("sk-"):
            return f"非 sk- 前缀（判为占位；长度 {len(v)}）"
        bad = [i for i, c in enumerate(v) if ord(c) > 127]
        return (f"sk- 前缀通过，但**含 {len(bad)} 个非 ASCII 字符**（自第 {bad[0]} 位起，"
                f"疑似备注粘连进键值）→ 形状门拒绝；请把说明另起注释行（DR-82）")
    return "sk- 前缀且全 ASCII（通过形状门）"


def probe_engine() -> int:
    """Python 引擎生成层：走 rag_engine.generate_answer 的真实代码路径。"""
    sys.path.insert(0, str(RAG_DIR))
    env = dotenv()
    key = engines().effective_api_key(env.get("DEEPSEEK_API_KEY"))
    exported = engines().engine_env(dict(os.environ), service="rag", api_key=key,
                                    dotenv=env, offline=engines().offline_flags(env))
    for k in ("DEEPSEEK_API_KEY", "DEEPSEEK_MODEL", "HF_HUB_OFFLINE", "TRANSFORMERS_OFFLINE"):
        if k in exported:
            os.environ[k] = exported[k]
    logging.basicConfig(level=logging.INFO, format="[engine-log] %(levelname)s %(name)s: %(message)s")
    print(f"[engine] 密钥形状：{key_verdict(env)}")
    import rag_engine  # noqa: PLC0415

    contexts = [{
        "document_name": "进程与线程",
        "snippet": "进程是操作系统资源分配的基本单位，拥有独立地址空间；"
                   "线程是CPU调度的基本单位，同一进程内的线程共享堆和全局变量。",
    }]
    answer, gen_mode = rag_engine.generate_answer("进程和线程的区别是什么？", contexts)
    print(f"[engine] gen_mode = {gen_mode}  （LLM 才算真实生成）")
    print(f"[engine] 回答前 40 字 = {(answer or '')[:40]!r}")
    return 0 if gen_mode == "LLM" else 1


def base_url() -> str:
    return f"http://127.0.0.1:{PROBE_PORT}/api"


def http(method: str, path: str, body=None, cookie: str = ""):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base_url() + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if cookie:
        req.add_header("Cookie", cookie)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=240) as r:
            return r.status, r.read().decode("utf-8", "replace"), r.headers.get("Set-Cookie", "")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), ""
    except Exception as e:  # 连不上要如实说，别把传输失败算成业务失败
        return None, f"{type(e).__name__}: {e}", ""


def probe_chains() -> int:
    status, text, cookie = http("POST", "/auth/login", {"username": "admin", "password": "123456"})
    if status != 200 or not cookie:
        print(f"[chains] 登录失败 HTTP {status} {text[:120]} —— 请先 `python scripts/probe-llm-chain.py backend`")
        return 2
    print("[chains] admin 登录成功（真实口令链路）")
    cases = [
        ("问答 QA_ASK", "POST", "/ai/qa/ask", {"question": "进程和线程的区别是什么？", "courseId": "1"}),
        ("出题 QUESTION_GENERATE", "POST", "/questions/generate-by-ai",
         {"courseId": "1", "type": "SINGLE_CHOICE", "difficulty": "EASY",
          "knowledgePointNames": "进程与线程"}),
        ("批改 ESSAY_CORRECT", "POST", "/ai/essay/correct",
         {"content": "操作系统里进程是资源分配单位，线程是调度单位，它们共享地址空间。"}),
        ("建议 LEARNING_ADVICE", "GET", "/ai/advice/learning", None),
    ]
    real = 0
    for label, method, path, body in cases:
        st, raw, _ = http(method, path, body, cookie)
        try:
            j = json.loads(raw)
        except Exception:
            j = {"code": None, "message": raw[:160], "data": None}
        blob = json.dumps(j.get("data"), ensure_ascii=False)
        if DEGRADE in blob:
            verdict = "❌ 降级串冒充成功（LLM 未真实生成）"
        elif st == 200 and (j.get("code") == 200) and blob not in ("null", ""):
            verdict = "✅ 非降级内容（需人工核对是否为模型原文）"
            real += 1
        else:
            verdict = f"⚠ 业务失败/异常：{j.get('message')}"
        print(f"[chains] {label:<26} HTTP={st} code={j.get('code')} -> {verdict}")
        print(f"         摘录 {blob[:80]!r}" if blob not in ("null",) else "         摘录 (data=null)")
    print(f"[chains] 密钥形状：{key_verdict(dotenv())}；401 原文见后端日志 [deepseek] 请求失败行")
    return 0 if real == len(cases) else 1


def probe_backend() -> int:
    """按产品启动器口径注入整份 .env 起后端（run-backend-for-e2e.py 刻意不注入密钥，不能复用它）。"""
    if not JAR.exists():
        print(f"[backend] 缺 {JAR.name}，先 mvn -o package -DskipTests")
        return 1
    env = dict(os.environ)
    env.update(dotenv())
    env.update({"COOKIE_SECURE": "false", "REQUIRE_HTTPS": "false", "CSRF_ENABLED": "false",
                "RATELIMIT_LOGIN_LIMIT": "200", "SPRING_DATASOURCE_PASSWORD": env.get("MYSQL_ROOT_PASSWORD", ""),
                "NO_PROXY": "localhost,127.0.0.1", "no_proxy": "localhost,127.0.0.1"})
    env.pop("SPRING_DATA_REDIS_PASSWORD", None)
    log_path = ROOT / ".workbuddy" / "tmp" / "probe_backend.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log = open(log_path, "w", encoding="utf-8")
    proc = subprocess.Popen(["java", "-jar", str(JAR), f"--server.port={PROBE_PORT}"],
                            cwd=str(ROOT / "backend"), env=env, stdout=log, stderr=subprocess.STDOUT)
    print(f"[backend] PID {proc.pid}，日志 {log_path.name}；密钥形状：{key_verdict(dotenv())}")
    for i in range(60):
        time.sleep(3)
        if proc.poll() is not None:
            print(f"[backend] 提前退出 {proc.returncode}")
            return 1
        st, _, _ = http("GET", "/actuator/health/probe")
        if st == 200:
            print(f"[backend] 就绪（{i * 3 + 3}s），Ctrl-C 结束")
            break
    try:
        proc.wait()
    except KeyboardInterrupt:
        proc.terminate()
    return 0


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode == "engine":
        return probe_engine()
    if mode == "chains":
        return probe_chains()
    if mode == "backend":
        return probe_backend()
    if mode == "all":
        return probe_engine() or probe_chains()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
