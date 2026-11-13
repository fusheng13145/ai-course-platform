#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 课程平台 · 真实负载压测编排器 （等价 Prometheus 采集）
修正：
 - 兼容无标签指标行（aicourse_pending_submissions / process_cpu_usage）
 - 采样线程全包裹异常保护，绝不中途退出；原始样本落盘 samples.jsonl
 - 主负载剔除本环境故障的 analytics 端点；另设 analytics_error 阶段专门测量"组件故障"日志突增
 - 三模式负载（斜坡/突发/稳态）+ 空闲基线 + 逼近瓶颈
输出：calibration_data.json + samples.jsonl
"""
import os, sys, json, time, threading, urllib.request, urllib.error, shutil, re, math

BASE = "http://localhost:8080/api"
PROM = "http://localhost:8080/api/actuator/prometheus"
SAMPLE_INTERVAL = 5
LOG_PATH = os.path.join(os.path.dirname(__file__), "backend.log")
OUT_JSON = os.path.join(os.path.dirname(__file__), "calibration_data.json")
OUT_JSONL = os.path.join(os.path.dirname(__file__), "samples.jsonl")

ADMIN_USER = "admin"
ADMIN_PASS = os.environ.get("APP_ADMIN_INITIAL_PASSWORD", "")

CURRENT_PHASE = {"name": "init"}
STOP_LOAD = threading.Event()
samples = []
_lock = threading.Lock()

def http_json(url, method="GET", data=None, headers=None, timeout=20):
    headers = dict(headers or {})
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read().decode("utf-8", "ignore")
            return r.status, body, r.headers.get_all("Set-Cookie")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore"), e.headers.get_all("Set-Cookie")
    except Exception as e:
        return 0, str(e), None

def login():
    st, body, cookie = http_json(f"{BASE}/auth/login", method="POST",
                                 data=json.dumps({"username": ADMIN_USER, "password": ADMIN_PASS}).encode(),
                                 headers={"Content-Type": "application/json"})
    print(f"[auth] login status={st}", flush=True)
    cookies = cookie if isinstance(cookie, list) else ([cookie] if cookie else [])
    token = None
    try:
        j = json.loads(body)
        for path in ("data.token", "data.accessToken", "data.access_token", "token", "accessToken"):
            cur = j
            for k in path.split("."):
                cur = cur.get(k) if isinstance(cur, dict) else None
                if cur is None: break
            if cur: token = cur; break
    except Exception:
        pass
    return token, cookies

def build_session(token, cookies):
    hdr = {"Content-Type": "application/json"}
    if token: hdr["Authorization"] = f"Bearer {token}"
    if cookies:
        pairs = []
        for c in (cookies if isinstance(cookies, list) else [cookies]):
            m = re.match(r"([^=]+)=([^;]+)", c)
            if m: pairs.append(f"{m.group(1)}={m.group(2)}")
        if pairs: hdr["Cookie"] = "; ".join(pairs)
    return hdr

def discover(token, cookie):
    hdr = build_session(token, cookie)
    healthy, analytics = [], []
    cands = [
        ("GET", f"{BASE}/auth/me", "healthy"),
        ("GET", f"{BASE}/courses", "healthy"),
        ("GET", f"{BASE}/chapters/course/1", "healthy"),
        ("GET", f"{BASE}/exam-papers/course/1", "healthy"),
        ("GET", f"{BASE}/ai/recommendations/learning", "healthy"),
        ("POST", f"{BASE}/ai/qa/ask", "healthy"),
        ("GET", f"{BASE}/analytics/dashboard", "analytics"),
        ("GET", f"{BASE}/analytics/overview", "analytics"),
    ]
    for method, url, grp in cands:
        data = json.dumps({"question": "什么是机器学习？", "sessionId": "calib"}).encode() if method == "POST" else None
        st, _, _ = http_json(url, method=method, data=data, headers=hdr)
        ok = 200 <= st < 300
        print(f"[discover] {grp} {method} {url.replace(BASE,'')} -> {st} {'OK' if ok else 'skip'}", flush=True)
        if ok:
            (healthy if grp == "healthy" else analytics).append((method, url))
    if not healthy:
        healthy.append(("GET", f"{BASE}/auth/me"))
    return healthy, analytics

def parse_prom(text):
    out = {"buckets": {}, "http_total": 0, "http_5xx": 0, "heap_used": 0.0, "heap_max": 0.0,
           "pending": None, "ai_total": 0, "ai_failed": 0, "cpu": None}
    for line in text.splitlines():
        if line.startswith("#") or not line.strip():
            continue
        # 拆分 指标名 与 数值（兼容有/无标签）
        if "{" in line:
            name, rest = line.split("}", 1)
            metric = name.split("{", 1)[0]
            valpart = rest.strip()
        else:
            parts = line.rsplit(None, 1)
            if len(parts) != 2:
                continue
            metric, valpart = parts
        try:
            val = float(valpart)
        except ValueError:
            continue
        le = None
        m = re.search(r'le="([^"]+)"', line)
        if m:
            try: le = float(m.group(1))
            except ValueError: le = math.inf
        if metric == "http_server_requests_seconds_bucket" and le is not None:
            out["buckets"][le] = out["buckets"].get(le, 0.0) + val
        elif metric == "http_server_requests_seconds_count":
            out["http_total"] += val
            sm = re.search(r'status="([^"]+)"', line)
            if sm and sm.group(1).startswith("5"):
                out["http_5xx"] += val
        elif metric == "jvm_memory_used_bytes" and 'area="heap"' in line:
            out["heap_used"] += val
        elif metric == "jvm_memory_max_bytes" and 'area="heap"' in line:
            out["heap_max"] += val
        elif metric == "aicourse_pending_submissions":
            out["pending"] = val
        elif metric == "aicourse_ai_calls_total":
            out["ai_total"] += val
        elif metric == "aicourse_ai_calls_failed_total":
            out["ai_failed"] += val
        elif metric == "process_cpu_usage":
            out["cpu"] = val
    return out

def histogram_quantile(q, buckets):
    items = sorted((le if le != math.inf else float("inf"), c) for le, c in buckets.items())
    if not items:
        return None
    total = items[-1][1]
    if total <= 0:
        return None
    target = q * total
    cum = 0
    for i, (le, c) in enumerate(items):
        cum += c
        if cum >= target:
            if i == 0:
                return le if le != float("inf") else (items[0][0] if items[0][0] != float("inf") else None)
            prev_le, prev_c = items[i - 1]
            if c - prev_c <= 0:
                return le
            frac = (target - prev_c) / (c - prev_c)
            res = prev_le + (le - prev_le) * frac
            return prev_le if le == float("inf") else res
    return items[-1][0]

prev = None
log_offset = 0
sample_err = 0
def sampler():
    global prev, log_offset, sample_err
    while True:
        try:
            if STOP_LOAD.is_set() and CURRENT_PHASE["name"] == "done":
                break
            st, text, _ = http_json(PROM, timeout=10)
            rec = parse_prom(text) if st == 200 else None
            log_rate = None
            try:
                if os.path.exists(LOG_PATH):
                    with open(LOG_PATH, "r", encoding="utf-8", errors="ignore") as f:
                        f.seek(log_offset); chunk = f.read(); log_offset = f.tell()
                    log_rate = (chunk.count(" ERROR ") + chunk.count(" ERROR\t")) / SAMPLE_INTERVAL
            except Exception:
                pass
            if rec:
                rec["t"] = time.time(); rec["phase"] = CURRENT_PHASE["name"]
                if prev:
                    rec["buckets_delta"] = {le: c - prev["buckets"].get(le, 0.0) for le, c in rec["buckets"].items()}
                    rec["http_total_delta"] = rec["http_total"] - prev["http_total"]
                    rec["http_5xx_delta"] = rec["http_5xx"] - prev["http_5xx"]
                    rec["ai_total_delta"] = rec["ai_total"] - prev["ai_total"]
                    rec["ai_failed_delta"] = rec["ai_failed"] - prev["ai_failed"]
                    tot = rec["http_total_delta"]
                    rec["err_rate"] = (rec["http_5xx_delta"] / tot) if tot > 0 else None
                    at = rec["ai_total_delta"]
                    rec["ai_fail_rate"] = (rec["ai_failed_delta"] / at) if at > 0 else None
                else:
                    rec["buckets_delta"] = dict(rec["buckets"])
                    rec["http_total_delta"] = rec["http_5xx_delta"] = rec["ai_total_delta"] = rec["ai_failed_delta"] = 0
                    rec["err_rate"] = rec["ai_fail_rate"] = None
                rec["log_err_rate"] = log_rate
                with _lock:
                    samples.append(rec)
                    try:
                        with open(OUT_JSONL, "a", encoding="utf-8") as fj:
                            fj.write(json.dumps(rec, ensure_ascii=False) + "\n")
                    except Exception:
                        pass
                prev = rec
        except Exception as e:
            sample_err += 1
            with _lock:
                try:
                    with open(OUT_JSONL + ".err", "a", encoding="utf-8") as fe:
                        fe.write(f"{time.time()} {e}\n")
                except Exception:
                    pass
        time.sleep(SAMPLE_INTERVAL)

def worker(stop, ep_list, hdr):
    idx = 0
    while not stop.is_set():
        method, url = ep_list[idx % len(ep_list)]
        idx += 1
        data = json.dumps({"question": "解释反向传播", "sessionId": "calib"}).encode() if method == "POST" else None
        try:
            http_json(url, method=method, data=data, headers=hdr, timeout=30)
        except Exception:
            pass

def run_phase(name, ep_list, hdr, concurrency, duration):
    CURRENT_PHASE["name"] = name
    print(f"\n=== PHASE {name} | concurrency={concurrency} | {duration}s ===", flush=True)
    stop = threading.Event()
    threads = [threading.Thread(target=worker, args=(stop, ep_list, hdr), daemon=True) for _ in range(max(1, concurrency))]
    for t in threads: t.start()
    end = time.time() + duration
    while time.time() < end:
        time.sleep(2)
        with _lock:
            recent = samples[-3:]
        if recent and recent[-1].get("heap_max"):
            hr = recent[-1]["heap_used"] / recent[-1]["heap_max"]
            if hr > 0.93:
                print(f"[safety] heap {hr:.1%} > 93%, easing", flush=True)
                stop.set(); break
    stop.set()
    for t in threads: t.join()

def agg(values):
    vals = [v for v in values if v is not None]
    if not vals: return None
    s = sorted(vals)
    def pct(p):
        k = (len(s) - 1) * p; f = math.floor(k); c = math.ceil(k)
        return s[int(k)] if f == c else s[f] * (c - k) + s[c] * (k - f)
    return {"min": s[0], "max": s[-1], "mean": sum(s)/len(s), "p50": pct(0.5), "p95": pct(0.95), "p99": pct(0.99), "n": len(s)}

def main():
    token, cookie = login()
    if not token and not cookie:
        print("[fatal] 登录失败", flush=True); return
    hdr = build_session(token, cookie)
    healthy, analytics = discover(token, cookie)
    print(f"[info] healthy={len(healthy)} analytics_ok={len(analytics)}", flush=True)
    st, text, _ = http_json(PROM, timeout=10)
    if st != 200:
        print(f"[fatal] PROM {st}", flush=True); return
    if os.path.exists(OUT_JSONL):
        os.remove(OUT_JSONL)
    threading.Thread(target=sampler, daemon=True).start()
    time.sleep(SAMPLE_INTERVAL * 2)

    CURRENT_PHASE["name"] = "baseline_idle"
    print("\n=== PHASE baseline_idle | 45s ===", flush=True)
    time.sleep(45)

    for c, d in [(10, 25), (30, 25), (60, 25), (100, 25)]:
        run_phase(f"ramp_{c}", healthy, hdr, c, d)
    run_phase("steady_80", healthy, hdr, 80, 110)
    run_phase("burst_150", healthy, hdr, 150, 50)
    run_phase("bottleneck_250", healthy, hdr, 250, 100)
    if analytics:
        run_phase("analytics_error", analytics, hdr, 40, 50)

    CURRENT_PHASE["name"] = "done"
    time.sleep(SAMPLE_INTERVAL * 2)
    STOP_LOAD.set()

    phases = {}
    for rec in samples:
        phases.setdefault(rec["phase"], []).append(rec)
    disk = shutil.disk_usage("/")
    result = {"meta": {"generated_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
                       "note": "等价 Prometheus 采集：直接抓取 actuator/prometheus，按 histogram_quantile/rate 复算；analytics 端点本环境报错已单列阶段"},
              "phases": {}, "host_disk_used_ratio": round(1 - disk.free/disk.total, 4)}
    for ph, recs in phases.items():
        lat = {}
        for r in recs:
            for le, c in (r.get("buckets_delta") or {}).items():
                lat[le] = lat.get(le, 0.0) + max(0.0, c)
        result["phases"][ph] = {
            "latency_seconds": {"p50": histogram_quantile(0.5, lat), "p95": histogram_quantile(0.95, lat), "p99": histogram_quantile(0.99, lat)},
            "http_5xx_error_rate": round(sum(r.get("http_5xx_delta", 0) or 0 for r in recs) / sum(r.get("http_total_delta", 0) or 0 for r in recs), 4) if sum(r.get("http_total_delta", 0) or 0 for r in recs) > 0 else 0.0,
            "http_total_requests": int(sum(r.get("http_total_delta", 0) or 0 for r in recs)),
            "ai_fail_rate": round(sum(r.get("ai_failed_delta", 0) or 0 for r in recs) / sum(r.get("ai_total_delta", 0) or 0 for r in recs), 4) if sum(r.get("ai_total_delta", 0) or 0 for r in recs) > 0 else 0.0,
            "ai_total_calls": int(sum(r.get("ai_total_delta", 0) or 0 for r in recs)),
            "heap_used_ratio": agg([(r["heap_used"]/r["heap_max"]) if r.get("heap_max") else None for r in recs]),
            "cpu_usage_ratio": agg([r.get("cpu") for r in recs]),
            "pending_submissions": agg([r.get("pending") for r in recs]),
            "log_error_rate_per_s": agg([r.get("log_err_rate") for r in recs]),
        }
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n[done] wrote {OUT_JSON} (samples={len(samples)}, sampler_err={sample_err})", flush=True)

if __name__ == "__main__":
    main()
