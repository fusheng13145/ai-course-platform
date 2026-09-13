#!/usr/bin/env python3
# AI 故障注入测量：Mock LLM 已下线时，持续调用 /api/ai/qa/ask，
# 读取 aicourse_ai_calls_total / _failed_total 计数器增量，计算真实 AI 失败率峰值。
# 用途：为 AiFailureRateHigh 提供"异常态"参照（健康态由主压测给出 ≈0）。
#
# 用法：python ai_fault.py [--mode=1|2]（P1-11：原 ai_fault.py/ai_fault2.py 合并，行为保真）
#   --mode=1（默认）固定节奏直发：15 次请求、间隔 4s（低于限流 20/60s），不做 429 退避。
#   --mode=2 限流健壮版：目标成功 10 次（至多 30 次尝试），遇 429 退避 15s 重试越过限流窗口，
#             正常间隔 2s——用于确认限流后仍能触达 LLM 层并得到失败率。
import argparse, os, sys, json, time, urllib.request, urllib.error, re
sys.path.insert(0, os.path.dirname(__file__))
import run_calibration as rc

ap = argparse.ArgumentParser(description="AI 故障注入测量（mode=1 固定节奏 / mode=2 限流健壮）")
ap.add_argument("--mode", type=int, choices=(1, 2), default=1, help="1=固定节奏直发；2=429 退避重试健壮版")
args = ap.parse_args()
MODE = args.mode
TAG = "ai_fault" if MODE == 1 else "ai_fault2"        # 保留历史日志前缀，便于与旧测量输出对照
SESSION_ID = "fault" if MODE == 1 else "fault2"

token, cookies = rc.login()
hdr = rc.build_session(token, cookies)
print(f"[{TAG}] session token={'Y' if token else 'N'}", flush=True)

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
                            data=json.dumps({"question": "测试故障注入", "sessionId": SESSION_ID}).encode(),
                            headers=hdr, timeout=15)
    return st

def fmt_delta(a, b):
    return (a - b) if (a is not None and b is not None) else "NA"

statuses = []
if MODE == 1:
    N = 15
    print(f"[{TAG}] sending {N} AI requests (mock DOWN) ...", flush=True)
    t0, f0 = read_counters()
    for i in range(N):
        statuses.append(send_one())
        time.sleep(4)  # 低于 rate-limit 20/60s
    t1, f1 = read_counters()
    print(f"[{TAG}] AI 请求状态分布: {dict((s, statuses.count(s)) for s in set(statuses))}", flush=True)
else:
    print(f"[{TAG}] 初始计数器 total={read_counters()[0]} failed={read_counters()[1]}", flush=True)
    t0, f0 = read_counters()
    attempts = sent = 0
    while sent < 10 and attempts < 30:
        attempts += 1
        st = send_one()
        statuses.append(st)
        if st == 429:
            print(f"[{TAG}] 第{attempts}次 429（限流），退避 15s 重试...", flush=True)
            time.sleep(15)
            continue
        sent += 1
        print(f"[{TAG}] 第{attempts}次 状态码={st}（已发 {sent}/10）", flush=True)
        time.sleep(2)
    time.sleep(3)
    t1, f1 = read_counters()
    print(f"[{TAG}] 最终计数器 total={t1} failed={f1}", flush=True)
    print(f"[{TAG}] 状态码分布: {dict((s, statuses.count(s)) for s in set(statuses))}", flush=True)

print(f"[{TAG}] ai_total Δ={fmt_delta(t1, t0)}, ai_failed Δ={fmt_delta(f1, f0)}", flush=True)
if t1 is not None and t0 is not None and (t1 - t0) > 0:
    rate = (f1 - f0) / (t1 - t0) if (f1 is not None and f0 is not None) else float('nan')
    print(f"[{TAG}] 实测 AI 失败率 = {rate:.4f}", flush=True)
else:
    print(f"[{TAG}] 计数器未变化（可能被限流拦截，未到达 LLM 层），失败率需结合状态判断", flush=True)
