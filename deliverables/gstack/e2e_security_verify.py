# -*- coding: utf-8 -*-
"""
安全维度端到端验证（第三阶段）：CSRF 双提交防护 + 登录 IP 限流。

前置：以**默认安全配置**启动后端（即不设置 CSRF_ENABLED=false、不覆盖 RATELIMIT_* 阈值），
      使 app.security.csrf.enabled 走默认 true、登录保护走默认两层阈值。

用例：
  S1 GET 安全方法不受 CSRF 约束
  S2 登录（引导端点，豁免 CSRF）成功后浏览器获得 XSRF-TOKEN
  S3 写请求缺少 X-XSRF-TOKEN → 403 CSRF 校验失败
  S4 写请求携带与 Cookie 匹配的 X-XSRF-TOKEN → 通过 CSRF 校验（进入业务逻辑）
  S5 篡改 X-XSRF-TOKEN（与 Cookie 不匹配）→ 403
  S6a 尝试层：同账号连续失败达 5 次 → 429「登录尝试过于频繁」（AuthSessionComponent）
  S6b 速率层：IP 滑动窗口 10 次/60 秒 → 第 11 次 429「请求过于频繁」（RateLimitFilter）

注意：登录保护自 P0-1「登录锁定 DoS」修复后为**两层**结构，尝试层阈值（5 次）低于
      速率层（10 次），故失败路径下由尝试层先触发。二者必须分层断言。
"""
import json
import subprocess
import sys
import os
import urllib.error
import urllib.request
import http.cookiejar


def _mysql_pw():
    p = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), ".env")
    try:
        with open(p, encoding="utf-8") as f:
            for line in f:
                if line.startswith("MYSQL_ROOT_PASSWORD="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return os.environ.get("MYSQL_ROOT_PASSWORD", "")

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE = "http://127.0.0.1:8080/api"
DB = "ai_course_platform"
RESULTS = []


def db(sql):
    p = subprocess.run(["mysql", "-u", "root", "-p" + _mysql_pw(), "-N", "-s", "-e", sql],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return [ln for ln in (p.stdout or "").splitlines() if ln.strip()]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class S:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj), NoRedirect())

    def req(self, method, path, body=None, headers=None):
        h = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            h["Content-Type"] = "application/json"
        if headers:
            h.update(headers)
        r = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
        try:
            resp = self.opener.open(r, timeout=15)
            st, text = resp.getcode(), resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            st, text = e.code, e.read().decode("utf-8", "replace")
        except Exception as e:
            return 0, {"_err": str(e)}
        try:
            return st, json.loads(text)
        except Exception:
            return st, {"_raw": text[:200]}

    def cookie(self, name):
        for c in self.cj:
            if c.name == name:
                return c.value
        return None


def rec(cid, title, expect, actual, ok, note=""):
    RESULTS.append((cid, title, expect, actual, "PASS" if ok else "FAIL", note))
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {title}\n        期望: {expect}\n        实际: {actual}"
          + (f"\n        备注: {note}" if note else ""))


def flush_redis():
    """清空 Redis db0：重置限流计数与缓存，使本轮从确定性基线开始。"""
    try:
        import socket
        with socket.create_connection(("127.0.0.1", 6379), timeout=3) as s:
            s.sendall(b"*1\r\n$7\r\nFLUSHDB\r\n")
            s.recv(64)
        print("[setup] Redis db0 已清空（限流计数/缓存归零）")
    except Exception as e:  # noqa
        print("[setup] Redis 清空失败（忽略）：", e)


def main():
    print("=" * 96)
    print("安全维度验证：CSRF 双提交防护 + 登录 IP 限流（默认安全配置）")
    print("=" * 96)
    flush_redis()

    s = S()
    st, o = s.req("POST", "/auth/login", {"username": "teacher", "password": "123456"})
    xsrf = s.cookie("XSRF-TOKEN")
    rec("S2", "登录（豁免端点）并下发可读 XSRF-TOKEN Cookie",
        "200 且 Cookie 中存在 XSRF-TOKEN",
        f"HTTP {st} code={o.get('code')} hasXsrf={bool(xsrf)}", st == 200 and bool(xsrf))

    # 已登录的 GET：不受 CSRF 约束（缺 X-XSRF-TOKEN 也应放行到业务层）
    st, o = s.req("GET", "/courses?page=1&size=1")
    rec("S1", "GET 安全方法不受 CSRF 约束（已登录）", "200（无需 XSRF 令牌）",
        f"HTTP {st} code={o.get('code')}", st == 200)

    # 缺 X-XSRF-TOKEN 的写请求
    st, o = s.req("POST", "/teacher/courses", {"name": "CSRF用例课", "code": "CSRF-CASE-1"})
    rec("S3", "写请求缺少 X-XSRF-TOKEN", "403 CSRF 校验失败",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:60]}",
        st == 403 and "CSRF" in str(o.get("message", "")))

    # 携带不匹配令牌
    st, o = s.req("POST", "/teacher/courses", {"name": "CSRF用例课", "code": "CSRF-CASE-2"},
                  headers={"X-XSRF-TOKEN": "tampered-value"})
    rec("S4", "写请求携带不匹配的 X-XSRF-TOKEN", "403 CSRF 校验失败",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:60]}",
        st == 403 and "CSRF" in str(o.get("message", "")))

    # 携带匹配令牌 → 通过 CSRF，进入业务
    st, o = s.req("POST", "/teacher/courses", {"name": "CSRF用例课三", "code": "CSRF-CASE-3"},
                  headers={"X-XSRF-TOKEN": xsrf or ""})
    created = (o.get("data") or {}).get("id")
    rec("S5", "写请求携带匹配的 X-XSRF-TOKEN", "通过 CSRF 校验（200 或业务码，非 403-CSRF）",
        f"HTTP {st} code={o.get('code')} msg={str(o.get('message'))[:50]} courseId={created}",
        st != 403 and o.get("code") != 403)
    if created:
        db(f"delete from {DB}.courses where id={created}")

    # ---- S6：登录保护为**两层**结构（P0-1「登录锁定 DoS」修复后新增了尝试层）----
    #  第 1 层 速率层 RateLimitFilter：IP 滑动窗口，ratelimit.login.limit=10 / window=60s，
    #          文案「请求过于频繁，请稍后再试。」
    #  第 2 层 尝试层 AuthSessionComponent：同账号连续失败达 MAX_FAILED_ATTEMPTS=5 即锁定
    #          LOCK_MINUTES=15 分钟，文案「登录尝试过于频繁，请15分钟后再试」。
    #          该层阈值更低，故在**失败路径**下先于速率层触发——2026-09-17 实测第 5 次
    #          失败请求即返回第 2 层的 429。因此不可再用单一「第 11 次才有 429」断言，
    #          必须分层验证，否则会把「更严格」误判为「不符合预期」。
    flush_redis()  # 归零两层计数，使本轮从确定性基线出发

    # S6a 尝试层：失败路径
    seq_a, msg_a = [], []
    for i in range(1, 9):
        stx, ox = S().req("POST", "/auth/login",
                          {"username": "teacher", "password": "wrong-pwd"})
        seq_a.append(stx)
        msg_a.append(str(ox.get("message")))
        if stx == 429:
            break
    first_a = seq_a.index(429) + 1 if 429 in seq_a else None
    rec("S6a", "尝试层：同账号连续失败达 5 次即锁定 15 分钟",
        "前 4 次 400（口令错误），第 5 次 429 且文案含「登录尝试过于频繁」",
        f"响应码序列={seq_a} 首次429=第{first_a}次 msg={msg_a[-1][:44]}",
        first_a == 5 and "登录尝试过于频繁" in msg_a[-1],
        "阈值来自 AuthSessionComponent.MAX_FAILED_ATTEMPTS=5 / LOCK_MINUTES=15")

    # S6b 速率层：用**成功**登录（不计入尝试层失败计数）撑满 IP 滑动窗口
    flush_redis()
    seq_b, msg_b = [], []
    for i in range(1, 14):
        stx, ox = S().req("POST", "/auth/login",
                          {"username": "student4", "password": "123456"})
        seq_b.append(stx)
        msg_b.append(str(ox.get("message")))
    first_b = seq_b.index(429) + 1 if 429 in seq_b else None
    rec("S6b", "速率层：IP 滑动窗口 10 次/60 秒（与 application.yaml 一致）",
        "前 10 次 200，第 11 次 429 且文案含「请求过于频繁」",
        f"响应码序列={seq_b} 首次429=第{first_b}次 msg={msg_b[-1][:44]}",
        first_b == 11 and "请求过于频繁" in msg_b[-1],
        "阈值来自 application.yaml ratelimit.login.limit=10 / window-seconds=60；"
        "本层计数按请求数计，成功登录同样占用配额（正是 IP 维度防刷的设计意图）")

    print("\n" + "=" * 96)
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r[4] == "PASS")
    print(f"安全维度用例总数 {total}，通过 {passed}，失败 {total - passed}")
    print("=" * 96)
    for cid, t, e, a, v, n in RESULTS:
        if v == "FAIL":
            print(f"未通过: [{cid}] {t}\n   期望: {e}\n   实际: {a}")
    with open("e2e_security_result.json", "w", encoding="utf-8") as f:
        json.dump({"total": total, "passed": passed,
                   "results": [{"case": r[0], "title": r[1], "expect": r[2], "actual": r[3],
                                "verdict": r[4], "note": r[5]} for r in RESULTS]},
                  f, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
