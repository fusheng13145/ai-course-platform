#!/usr/bin/env python3
# 只打印状态码/端点名，不打印任何令牌或响应体（避免触发敏感内容过滤）
import os, json, urllib.request, urllib.error, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import run_calibration as rc

token, cookies = rc.login()
if not token and not cookies:
    print("[smoke] LOGIN FAILED", flush=True); sys.exit(1)
hdr = rc.build_session(token, cookies)
print(f"[smoke] session built: token={'Y' if token else 'N'} cookie_pairs={len([c for c in (cookies if isinstance(cookies,list) else [cookies]) if c])}", flush=True)

eps = [
    ("GET", rc.BASE + "/auth/me"),
    ("GET", rc.BASE + "/courses"),
    ("GET", rc.BASE + "/analytics/dashboard"),
    ("GET", rc.BASE + "/classrooms/my"),
    ("GET", rc.BASE + "/ai/recommendations/learning"),
    ("POST", rc.BASE + "/ai/qa/ask"),
]
for method, url in eps:
    data = json.dumps({"question": "什么是机器学习？", "sessionId": "smoke"}).encode() if method == "POST" else None
    st, _, _ = rc.http_json(url, method=method, data=data, headers=hdr)
    print(f"[smoke] {method} {url.replace(rc.BASE,'')} -> {st}", flush=True)
