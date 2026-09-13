#!/usr/bin/env python3
# AI 故障注入测量 v2（健壮版）：Mock 已宕机时持续调用 /api/ai/qa/ask，
# 遇 429 自动退避重试越过限流窗口，读取 aicourse_ai_calls_total/_failed_total
# 计数器增量，计算真实 AI 失败率峰值，验证 AiFailureRateHigh 异常侧。
import os, sys, json, time, urllib.request, urllib.error, re
sys.path.insert(0, os.path.dirname(__file__))
import run_calibration as rc

token, cookies = rc.login()
hdr = rc.build_session(token, cookies)
print(f"[ai_fault2] session token={'Y' if token else 'N'}", flush=True)

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

def send_one():
    st, _, _ = rc.http_json(f"{rc.BASE}/ai/qa/ask", method="POST",
                            data=json.dumps({"question":"测试故障注入","sessionId":"fault2"}).encode(),
                            headers=hdr, timeout=15)
    return st

t0, f0 = read_counters()
print(f"[ai_fault2] 初始计数器 total={t0} failed={f0}", flush=True)

statuses = []
attempts = 0
sent = 0
while sent < 10 and attempts < 30:
    attempts += 1
    st = send_one()
    statuses.append(st)
    if st == 429:
        print(f"[ai_fault2] 第{attempts}次 429（限流），退避 15s 重试...", flush=True)
        time.sleep(15)
        continue
    sent += 1
    print(f"[ai_fault2] 第{attempts}次 状态码={st}（已发 {sent}/10）", flush=True)
    time.sleep(2)

time.sleep(3)
t1, f1 = read_counters()
print(f"[ai_fault2] 最终计数器 total={t1} failed={f1}", flush=True)
print(f"[ai_fault2] 状态码分布: {dict((s,statuses.count(s)) for s in set(statuses))}", flush=True)
if t1 is not None and t0 is not None and (t1 - t0) > 0:
    rate = (f1 - f0) / (t1 - t0) if (f1 is not None and f0 is not None) else float('nan')
    print(f"[ai_fault2] 实测 AI 失败率 = {rate:.4f}  (totalΔ={t1-t0:.0f}, failedΔ={f1-f0:.0f})", flush=True)
else:
    print(f"[ai_fault2] 计数器未变化（totalΔ={t1-t0 if (t1 is not None and t0 is not None) else 'NA'}）；"
          f"若状态码非 429 则说明请求未到达 LLM 层或已降级", flush=True)
