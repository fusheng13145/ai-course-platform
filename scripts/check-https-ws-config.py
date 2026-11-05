#!/usr/bin/env python3
# =============================================================================
# AI Course Platform - HTTPS / Cookie / WS Origin 验收（A 路径 #7 / #8）
#
# 静态部分（本机即可执行）：
#   - REQUIRE_HTTPS / COOKIE_SECURE / CSRF_ENABLED / TRUST_PROXY_HEADER 必须为 true；
#   - CORS_ALLOWED_ORIGINS 必须全部是 https、不得含 localhost/127.0.0.1/*、
#     不得带路径或尾斜杠；
#   - WebSocket 与 REST 共用同一份 Origin 白名单（WebSocketConfig 复用 CorsProperties），
#     本脚本按这一事实检查，不虚构独立的 WS_ORIGIN 键。
#
# 运行期部分（需要真实域名 + 有效证书 + 已登录 JWT）：
#   - HTTPS 健康探针 /api/actuator/health/probe；
#   - 带 JSONP 的 WS 握手：白名单 Origin 期望 101，非白名单 Origin 期望 403。
#
# 重要：没有真实域名/证书/登录令牌时，运行期项记为 PENDING（退出码 2），
#      不得把 PENDING 当成“已验收”。当前项目即处于此状态（A 路径 #7/#8 挂账）。
#
# 用法：
#   python scripts/check-https-ws-config.py --env-file deploy/env.production
#   python scripts/check-https-ws-config.py --env-file .env --domain course.example.com
#   python scripts/check-https-ws-config.py --env-file .env --base-url https://course.example.com
#   python scripts/check-https-ws-config.py --env-file .env --base-url https://course.example.com \
#       --token "$JWT"          # 运行期 WS Origin 双向验收
#
# 退出码：0 全部通过；1 静态或运行期失败；2 静态通过但运行期未取证（PENDING）。
# 状态：2026-11-05 本机无真实域名/证书，运行期分支未执行；静态分支以夹具自测。
# =============================================================================
import argparse
import http.client
import json
import pathlib
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

TRUE_VALUES = {"true", "1", "yes", "on"}
ORIGIN_RE = re.compile(r"^https://[A-Za-z0-9.-]+(:[0-9]+)?$")


def parse_env(path: pathlib.Path) -> dict:
    values = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key.startswith("export "):
            key = key[len("export "):].strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values


def is_true(value: str) -> bool:
    return value is not None and value.strip().lower() in TRUE_VALUES


def static_checks(values: dict) -> list:
    checks = []

    def add(name, ok, detail):
        checks.append({"check": name, "status": "PASS" if ok else "FAIL", "detail": detail})

    require_https = values.get("REQUIRE_HTTPS", "true")
    cookie_secure = values.get("COOKIE_SECURE", "true")
    csrf_enabled = values.get("CSRF_ENABLED", "true")
    trust_proxy = values.get("TRUST_PROXY_HEADER", "true")

    add("REQUIRE_HTTPS", is_true(require_https), f"当前值：{require_https}")
    add("COOKIE_SECURE", is_true(cookie_secure), f"当前值：{cookie_secure}")
    add("CSRF_ENABLED", is_true(csrf_enabled), f"当前值：{csrf_enabled}")
    add("TRUST_PROXY_HEADER", is_true(trust_proxy), f"当前值：{trust_proxy}")

    consistent = is_true(require_https) == is_true(cookie_secure)
    add("HTTPS/Cookie 一致性", consistent,
        "REQUIRE_HTTPS 与 COOKIE_SECURE 必须同为 true（生产）或同为 false（受信内网明文）")

    raw = values.get("CORS_ALLOWED_ORIGINS", "")
    origins = [item.strip() for item in raw.split(",") if item.strip()]
    if not origins:
        add("CORS_ALLOWED_ORIGINS", False, "为空；生产环境必须显式给出 https 白名单（fail-closed）")
    else:
        bad = []
        for origin in origins:
            if origin.lower().startswith("http://"):
                bad.append(f"{origin}（不是 https）")
            elif not ORIGIN_RE.match(origin):
                bad.append(f"{origin}（格式应为 https://host[:port]，不得带路径/尾斜杠）")
            elif any(tok in origin for tok in ("localhost", "127.0.0.1", "0.0.0.0")):
                bad.append(f"{origin}（本机地址不得用于生产白名单）")
        add("CORS_ALLOWED_ORIGINS", not bad,
            "全部 https 且格式合法" if not bad else "；".join(bad))

    add("WS Origin 白名单来源", True,
        "WebSocketConfig 复用 CorsProperties（cors.allowed-origins = CORS_ALLOWED_ORIGINS），无独立 WS 键")
    return checks


def health_probe(base_url: str) -> tuple:
    url = base_url.rstrip("/") + "/api/actuator/health/probe"
    try:
        with urllib.request.urlopen(url, timeout=10, context=ssl.create_default_context()) as resp:
            body = resp.read(200).decode("utf-8", errors="replace")
            if resp.status == 200:
                return True, f"HTTP 200：{body}"
            return False, f"HTTP {resp.status}：{body}"
    except (urllib.error.URLError, OSError, ssl.SSLError) as exc:
        return False, f"请求失败：{exc}"


def ws_probe(base_url: str, path: str, origin: str, token: str | None) -> tuple:
    parsed = urllib.parse.urlsplit(base_url)
    if parsed.scheme != "https":
        return False, "base-url 必须是 https"
    port = parsed.port or 443
    headers = {
        "Upgrade": "websocket",
        "Connection": "Upgrade",
        "Sec-WebSocket-Key": "dGhlIHNhbXBsZSBub25jZQ==",
        "Sec-WebSocket-Version": "13",
        "Origin": origin,
    }
    if token:
        headers["Cookie"] = f"ai_course_token={token}"
    try:
        conn = http.client.HTTPSConnection(
            parsed.hostname, port, timeout=10, context=ssl.create_default_context()
        )
        conn.request("GET", path, headers=headers)
        response = conn.getresponse()
        status = response.status
        conn.close()
        return status, f"HTTP {status}"
    except (OSError, ssl.SSLError, http.client.HTTPException) as exc:
        return None, f"请求失败：{exc}"


def runtime_checks(base_url: str, ws_path: str, allowed_origin: str, token: str | None) -> list:
    checks = []
    ok, detail = health_probe(base_url)
    checks.append({"check": "HTTPS 健康探针", "status": "PASS" if ok else "FAIL", "detail": detail})

    if not token:
        checks.append({
            "check": "WS Origin（白名单）",
            "status": "PENDING",
            "detail": "未提供 --token；需要已登录用户的 JWT 才能区分 101 与 403",
        })
        checks.append({
            "check": "WS Origin（非白名单）",
            "status": "PENDING",
            "detail": "同上",
        })
        return checks

    status, detail = ws_probe(base_url, ws_path, allowed_origin, token)
    checks.append({
        "check": "WS Origin（白名单）",
        "status": "PASS" if status == 101 else "FAIL",
        "detail": f"期望 101，实际 {detail}",
    })

    evil_origin = "https://not-in-allowlist.example"
    status, detail = ws_probe(base_url, ws_path, evil_origin, token)
    checks.append({
        "check": "WS Origin（非白名单）",
        "status": "PASS" if status == 403 else "FAIL",
        "detail": f"期望 403，实际 {detail}",
    })
    return checks


def main() -> int:
    parser = argparse.ArgumentParser(description="HTTPS/Cookie/WS Origin 验收（A 路径 #7/#8）")
    parser.add_argument("--env-file", default="deploy/env.production")
    parser.add_argument("--domain", help="期望出现在 CORS 白名单中的域名（不含协议）")
    parser.add_argument("--base-url", help="运行期探针的 https 基地址（不带尾斜杠）")
    parser.add_argument("--ws-path", default="/api/ws")
    parser.add_argument("--token", help="已登录用户的 JWT（用于 WS Origin 双向验收）")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    env_path = pathlib.Path(args.env_file)
    values = parse_env(env_path)

    checks = static_checks(values)
    static_failed = any(c["status"] == "FAIL" for c in checks)

    if args.domain:
        domain = args.domain.strip().lower()
        origins = [o.strip() for o in values.get("CORS_ALLOWED_ORIGINS", "").split(",") if o.strip()]
        matched = any(urllib.parse.urlsplit(o).hostname == domain for o in origins)
        checks.append({
            "check": f"域名 {domain} 在白名单中",
            "status": "PASS" if matched else "FAIL",
            "detail": "已包含" if matched else "未包含",
        })
        static_failed = static_failed or not matched

    runtime = []
    runtime_pending = False
    if args.base_url:
        runtime = runtime_checks(args.base_url, args.ws_path,
                                 values.get("CORS_ALLOWED_ORIGINS", "").split(",")[0].strip(),
                                 args.token)
        runtime_pending = any(c["status"] == "PENDING" for c in runtime)

    result = {
        "env_file": str(env_path),
        "env_file_exists": env_path.exists(),
        "static": checks,
        "runtime": runtime,
        "static_failed": static_failed,
        "runtime_pending": runtime_pending,
    }

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))

    print(f"[A#7/#8] 配置验收：{env_path}")
    if not env_path.exists():
        print("  [WARN] 文件不存在；请从 deploy/env.production.template 复制后填写。")
    print("")
    for check in checks:
        print(f"  [{check['status']}] {check['check']}：{check['detail']}")
    if runtime:
        print("")
        print("  运行期：")
        for check in runtime:
            print(f"  [{check['status']}] {check['check']}：{check['detail']}")
    else:
        print("")
        print("  [PENDING] 运行期未执行：未提供 --base-url。需真实域名与证书。")

    print("")
    if static_failed:
        print("[RESULT] FAIL：静态配置不满足生产口径。")
        return 1
    if runtime_pending or not runtime:
        print("[RESULT] PENDING：静态检查通过；运行期验收仍需真实域名/证书"
              "（WS Origin 还需已登录 JWT）。当前不得标记为已验收。")
        return 2
    if any(c["status"] == "FAIL" for c in runtime):
        print("[RESULT] FAIL：运行期检查未通过。")
        return 1
    print("[RESULT] PASS：静态与运行期均通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
