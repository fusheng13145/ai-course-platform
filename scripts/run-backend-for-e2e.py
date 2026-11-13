#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""E2E 常驻后端启动器（配合 scripts/e2e/ 的 E2E 四件套使用）。

为什么需要它：
  1. **后端必须跨工具调用常驻**。在本项目的沙箱里，用 `subprocess` 起 java 后立刻返回，
     进程会被回收（`DETACHED_PROCESS` 也无效），E2E 表现为主链路全 `HTTP 0`。
     正确做法是用宿主工具的后台任务能力把本脚本挂住（本脚本 `p.wait()` 直到被杀）。
  2. **两种姿态不能混用**。功能/深度轮要放开 CSRF 与登录限流；安全轮必须用**默认**
     CSRF 与默认限流阈值（10 次/60 秒），否则 S3–S6 断言全部失效。
  3. **启动前必须 FLUSHDB**：绕过 `@CacheEvict` 造成的陈旧缓存，并重置限流计数。

用法：
    python scripts/run-backend-for-e2e.py functional   # 功能/深度轮姿态（CSRF 关、登录限流 200）
    python scripts/run-backend-for-e2e.py secure       # 安全轮姿态（CSRF 默认开、限流默认 10/60s）

    建议以「后台任务」方式运行（前台会阻塞且进程可能被回收）。

前置：
  - MySQL(3306) 与 Redis(6379，无密码) 已运行；
  - 仓库根有 `.env`（提供 MYSQL_ROOT_PASSWORD / JWT_SECRET）；
  - `backend/target/backend-0.0.1-SNAPSHOT.jar` 已由 `mvn -o package -DskipTests` 生成
    （改了后端代码务必先重新打包，否则跑的是旧产物）。

注意：**不要**把整个 `.env` 注入本进程。`MAIL_USERNAME` / `MAIL_PASSWORD` 会让
`/common/verify-code` 走真实 SMTP（本地无 3025 服务）而返回 500，属测试环境配置问题。
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(ROOT, ".workbuddy", "tmp")
JAR = os.path.join(ROOT, "backend", "target", "backend-0.0.1-SNAPSHOT.jar")
LOG = os.path.join(TMP, "backend_e2e.log")
PORT = "8080"


def load_env_file() -> dict:
    path = os.path.join(ROOT, ".env")
    if not os.path.exists(path):
        print("[env] 未找到 .env，请先 cp .env.example .env 并填入真实值", flush=True)
        sys.exit(1)
    out = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def flush_redis() -> None:
    with socket.create_connection(("127.0.0.1", 6379), timeout=5) as s:
        s.sendall(b"*1\r\n$7\r\nFLUSHDB\r\n")
        print("[redis] FLUSHDB ->", s.recv(64).decode().strip(), flush=True)


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "functional"
    if mode not in ("functional", "secure"):
        print("用法: python scripts/run-backend-for-e2e.py [functional|secure]", flush=True)
        return 2

    if not os.path.exists(JAR):
        print("[jar] 未找到 %s，请先 mvn -o package -DskipTests" % JAR, flush=True)
        return 1

    env_file = load_env_file()
    os.makedirs(TMP, exist_ok=True)
    flush_redis()

    env = dict(os.environ)
    env.update({
        "SPRING_DATASOURCE_PASSWORD": env_file.get("MYSQL_ROOT_PASSWORD", ""),
        "JWT_SECRET": env_file.get("JWT_SECRET", ""),
        "COOKIE_SECURE": "false",
        "REQUIRE_HTTPS": "false",
        # 直连 localhost 时必须绕开本机拦截代理，否则探活得到 502/10061
        "NO_PROXY": "localhost,127.0.0.1",
        "no_proxy": "localhost,127.0.0.1",
    })
    if mode == "functional":
        env["CSRF_ENABLED"] = "false"
        env["RATELIMIT_LOGIN_LIMIT"] = "200"
    else:
        env.pop("CSRF_ENABLED", None)
        env.pop("RATELIMIT_LOGIN_LIMIT", None)
    # Redis 本机无密码，勿注入（注入了会连接失败）
    env.pop("SPRING_DATA_REDIS_PASSWORD", None)
    print("[mode]", mode, flush=True)

    log = open(LOG, "w", encoding="utf-8")
    proc = subprocess.Popen(["java", "-jar", JAR, "--server.port=" + PORT],
                            cwd=os.path.join(ROOT, "backend"), env=env,
                            stdout=log, stderr=subprocess.STDOUT)
    print("[backend] PID =", proc.pid, "日志:", os.path.relpath(LOG, ROOT), flush=True)

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    # 探 probe 组（readinessState + database + redis + diskSpace），不探综合 /health：
    # 综合状态含 llmProvider，本机不注入 DEEPSEEK_API_KEY 时恒 503，原实现把 503 当"就绪"
    # 放行 → DB/Redis 真挂了也照样往下跑 E2E。改探 probe 后 200 才是"依赖可用"。
    for i in range(60):
        time.sleep(3)
        if proc.poll() is not None:
            print("[backend] 提前退出 exit =", proc.returncode, flush=True)
            return 1
        code = None
        try:
            with opener.open("http://127.0.0.1:%s/api/actuator/health/probe" % PORT, timeout=3) as r:
                code = r.status
        except urllib.error.HTTPError as e:
            code = e.code
        except Exception:
            continue
        if code == 200:
            print("[backend] HTTP 200 就绪（%ds）" % (i * 3 + 3), flush=True)
            break
        print("[backend] probe HTTP %s（%ds），继续等待依赖就绪…" % (code, i * 3 + 3), flush=True)
    else:
        print("[backend] 探活超时（probe 未达 200，多为 MySQL/Redis 不可用）", flush=True)
        return 1

    if mode == "functional":
        rc = assert_demo_logins(os.environ.get("PW_PASS", "123456"))
        if rc:
            return rc

    print("[backend] 常驻中，等待被终止（终止本进程即停后端）…", flush=True)
    proc.wait()
    print("[backend] 已退出 exit =", proc.returncode, flush=True)
    return 0


def assert_demo_logins(password):
    """功能轮的登录前置自检：student/teacher/admin 三个演示号必须真能登录。

    为什么必须在起栈时就判：e2e 的登录口令取自 frontend/e2e/test-helpers.ts
    （默认 123456，可用 PW_PASS 覆盖），而库里的口令可能被「演示账号加固」
    （DEMO_ACCOUNTS_ENABLED=false）单向改掉（停用 + 改密为不可知口令，之后任何启动都不回退）。
    那种情况下 Playwright 只会给出「admin 未落到首页」加一串随 15 分钟节流漂移的红灯集合，
    看着像产品缺陷。2026-11-06 验收实测即为此：8 例红，全部依赖 admin 登录。
    """
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    bad = []
    for account in ("student", "teacher", "admin"):
        body = json.dumps({"username": account, "password": password, "rememberMe": True}).encode()
        req = urllib.request.Request(
            "http://127.0.0.1:%s/api/auth/login" % PORT,
            data=body,
            headers={"Content-Type": "application/json"},
        )
        msg = None
        try:
            with opener.open(req, timeout=15) as r:
                payload = json.loads(r.read().decode("utf-8", "replace") or "{}")
                if payload.get("code") not in (200, None):
                    msg = "业务码 %s：%s" % (payload.get("code"), payload.get("message"))
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace") if e.fp else ""
            try:
                msg = "HTTP %s：%s" % (e.code, json.loads(raw).get("message"))
            except Exception:
                msg = "HTTP %s" % e.code
        except Exception as exc:
            msg = "请求未送达：%s" % type(exc).__name__
        if msg:
            bad.append((account, msg))
    if not bad:
        print("[selfcheck] 演示账号登录 3/3 可用（student/teacher/admin）", flush=True)
        return 0
    print("[selfcheck] ✗ 演示账号登录失败：%s" % "、".join("%s→%s" % b for b in bad), flush=True)
    print(
        "[selfcheck]   口令来源：PW_PASS=%s。若曾以 DEMO_ACCOUNTS_ENABLED=false 在本库启动过，"
        "admin 已被单向改密并停用，需先恢复种子口令再跑验收，否则失败集合不可复现。"
        % ("已显式设置" if "PW_PASS" in os.environ else "默认 123456"),
        flush=True,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
