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

    print("[backend] 常驻中，等待被终止（终止本进程即停后端）…", flush=True)
    proc.wait()
    print("[backend] 已退出 exit =", proc.returncode, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
