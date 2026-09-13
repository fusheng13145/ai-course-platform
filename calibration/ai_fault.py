#!/usr/bin/env python3
# AI 故障注入测量：Mock LLM 已下线时，持续调用 /api/ai/qa/ask，
# 读取 aicourse_ai_calls_total / _failed_total 计数器增量，计算真实 AI 失败率峰值。
# 用途：为 AiFailureRateHigh 提供"异常态"参照（健康态由主压测给出 ≈0）。
import os, sys, json, time, urllib.request, urllib.error, re
sys.path.insert(0, os.path.dirname(__file__))
import run_calibration as rc

token, cookies = rc.login()
hdr = rc.build_session(token, cookies)
print(f"[ai_fault] session token={'Y' if token else 'N'}", flush=True)

def read_counters():
    st, text, _ = rc.http_json(rc.PROM, timeout=10)
    total = failed = None
    if st == 200:
        for line in text.splitlines():
            if line.startswith("#"): continue
            name = line.split("{",1)[0] if "{" in line else line.rsplit(None,1)[0]
            try: v = float(line.rsplit(None,1)[1])
            except: continue
            if name == "aicourse_ai_calls_total": total = v
            elif name == "aicourse_ai_calls_failed_total": failed = v
    return total, failed

N = 15
print(f"[ai_fault] sending {N} AI requests (mock DOWN) ...", flush=True)
t0, f0 = read_counters()
statuses = []
for i in range(N):
    st, _, _ = rc.http_json(f"{rc.BASE}/ai/qa/ask", method="POST",
                           data=json.dumps({"question": "测试故障注入", "sessionId": "fault"}).encode(),
                           headers=hdr, timeout=15)
    statuses.append(st)
    time.sleep(4)  # 低于 rate-limit 20/60s
t1, f1 = read_counters()
print(f"[ai_fault] AI 请求状态分布: {dict((s,statuses.count(s)) for s in set(statuses))}", flush=True)
print(f"[ai_fault] ai_total Δ={t1-t0 if (t1 is not None and t0 is not None) else 'NA'}, "
      f"ai_failed Δ={f1-f0 if (f1 is not None and f0 is not None) else 'NA'}", flush=True)
if t1 is not None and t0 is not None and (t1 - t0) > 0:
    print(f"[ai_fault] 实测 AI 失败率 = {(f1-f0)/(t1-t0):.4f}", flush=True)
else:
    print("[ai_fault] 计数器未变化（可能被限流拦截，未到达 LLM 层），失败率需结合状态判断", flush=True)
