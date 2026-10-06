# -*- coding: utf-8 -*-
"""大创·AI 智能课程平台 V6 计算机网络示范课种子生成器。

用法：python scripts/generate-cn-showcase-seed.py
输出：backend/src/main/resources/db/seed-demo/V6__computer_network_showcase.sql

目标：把《计算机网络（A班）》(course 4007, teacher ts04/1004) 打造成可验证
平台全部功能的示范课——
  * 课程激活为 2026秋 在读状态；
  * 知识点图谱 69 节点（12 个模块父点 + 49 个子点 + 挂接 V5 原有 8 点）、150+ 条边；
  * 题库 54 题（单选/多选/判断/填空/简答），期中/单元试卷 + 60 人考试会话 + 错题本；
  * 章节 / 任务点 / 作业提交 / 三维成绩 / 签到 / 投票 / 讨论 / 学习路径 /
    笔记 / 资源 / 学习行为 / AI 对话 全链路互联数据。

约束：确定性随机（固定种子）；新 ID 全部落在 7,000,000+ 独立号段，
不与 V1-V5 任何数据冲突；状态域遵循 V2-V4 规范化。
"""

import datetime as dt
import json
import os
import random

R = random.Random(20261005)
NOW = dt.datetime(2026, 10, 5, 10, 0, 0)
FALL_START = dt.datetime(2026, 9, 7, 8, 0, 0)   # 2026秋 第1周周一
COURSE, TEACHER = 4007, 1004
OLD15 = list(range(2046, 2061))                  # V5 已选课学生
NEW45 = list(range(2001, 2046))                  # 本次扩补学生（含 st001）
ALL60 = NEW45 + OLD15


def wk(week, day=0, hh=8, mm=0):
    return FALL_START + dt.timedelta(days=(week - 1) * 7 + day, hours=hh - 8, minutes=mm)


def J(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "''")


def r2(x):
    return round(x, 2)


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, dt.datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, (int, float)):
        return repr(v)
    return "'" + esc(str(v)) + "'"


class Band:
    def __init__(self, base, limit):
        self.cur, self.base, self.limit = base, base, limit

    def nxt(self):
        self.cur += 1
        if self.cur >= self.base + self.limit:
            raise RuntimeError(f"号段 {self.base} 溢出，请调大 limit")
        return self.cur


B = {
    "kp": Band(7000000, 999), "kprel": Band(7001000, 1999), "chap": Band(7003000, 99),
    "ckp": Band(7004000, 999), "res": Band(7005000, 199), "resver": Band(7005300, 199),
    "question": Band(7006000, 199), "paper": Band(7007000, 9), "examq": Band(7007010, 299),
    "session": Band(7008000, 199), "wrong": Band(7009000, 999), "task": Band(7010000, 19),
    "taskcompl": Band(7011000, 999), "subm": Band(7012000, 499), "gi": Band(7013000, 299),
    "si": Band(7014000, 9), "sir": Band(7015000, 999), "vote": Band(7016000, 9),
    "vr": Band(7016010, 399), "dt": Band(7017000, 19), "dp": Band(7017020, 199),
    "dl": Band(7018000, 299), "lp": Band(7019000, 9), "lpn": Band(7019010, 99),
    "note": Band(7020000, 99), "lr": Band(7021000, 999), "vlr": Band(7022000, 299),
    "vws": Band(7023000, 299), "aic": Band(7024000, 29), "aim": Band(7025000, 999),
    "enroll": Band(7026000, 99),
}

OUT = []


def dump(table, cols, rows, comment):
    OUT.append(f"-- ── {comment} ({len(rows)} 行)")
    col_sql = ",".join(cols)
    for i in range(0, len(rows), 100):
        chunk = rows[i:i + 100]
        values = ",\n".join("(" + ",".join(lit(v) for v in r) + ")" for r in chunk)
        OUT.append(f"INSERT INTO {table} ({col_sql}) VALUES\n{values};")
    OUT.append("")


def abl(sid):
    """学生水平系数（确定性）"""
    return min(0.97, max(0.25, 0.42 + ((sid * 37) % 45) / 100.0))


# ═══════════════════════════ 0. 课程激活 ═══════════════════════════

OUT.append("UPDATE courses SET status='ACTIVE', semester='2026秋', year=2026,"
           " start_date='2026-09-07 00:00:00', end_date='2027-01-17 23:59:59',"
           " description='计算机网络示范课（2026秋 在读）。以分层模型为主线，覆盖物理层→应用层全部协议栈，"
           "含 69 知识点互联图谱、54 题题库、期中测验、任务点作业、签到投票、讨论区、个性化学习路径与 AI 问答，"
           "可用于验证平台全部教学功能。', updated_at='2026-10-05 10:00:00' WHERE id=4007;")
OUT.append("")

# ═══════════════════════════ 1. 选课（st001–st045） ═══════════════════════════

enroll_rows = []
for sid in NEW45:
    a = abl(sid)
    enroll_rows.append((
        B["enroll"].nxt(), sid, COURSE, "APPROVED", wk(1, 0, 10), TEACHER, None, 1,
        None, r2(a * 100), r2(a * 92), None, r2(35 + a * 40), int(28 * 3600 * a),
        wk(1, 0, 9), wk(1, 0, 9), NOW))

dump("course_enrollments",
     "id,student_id,course_id,approval_status,approval_time,approved_by,approval_comment,is_active,"
     "final_score,attendance_score,assignment_score,exam_score,progress,total_study_time,enrolled_at,"
     "created_at,updated_at".split(","), enroll_rows, "选课：计算机网络示范课补入 st001–st045（2026秋）")

# ═══════════════════════════ 2. 知识点图谱 ═══════════════════════════

# (模块名, 描述, [(子点, type, diff, importance, minutes)])
MODULES = [
    ("网络体系结构与分层模型", "从应用视角自顶向下理解网络，掌握分层、协议与封装。", [
        ("OSI 七层模型", "concept", "easy", 9, 40),
        ("TCP/IP 四层模型", "concept", "easy", 10, 35),
        ("带宽、时延与吞吐量", "concept", "medium", 8, 45),
        ("报文封装与逐层交付", "principle", "medium", 8, 40)]),
    ("物理层与数据通信基础", "比特传输的媒介、编码与信道容量极限。", [
        ("传输介质：双绞线/光纤/无线", "concept", "easy", 6, 30),
        ("奈奎斯特与香农公式", "principle", "hard", 8, 55),
        ("数字编码与调制技术", "concept", "medium", 6, 40),
        ("电路交换与分组交换", "concept", "medium", 7, 35)]),
    ("数据链路层控制协议", "相邻节点间可靠成帧、差错控制与流量控制。", [
        ("差错检测与 CRC 校验", "principle", "medium", 8, 50),
        ("停等协议与信道利用率", "principle", "medium", 6, 40),
        ("滑动窗口与 GBN/SR 协议", "principle", "hard", 9, 60),
        ("HDLC 与 PPP 封装", "concept", "easy", 5, 30)]),
    ("以太网与局域网", "最成功的局域网技术：MAC、交换机与冲突控制。", [
        ("MAC 地址与以太网帧格式", "concept", "medium", 9, 40),
        ("交换原理与生成树协议", "principle", "medium", 7, 50),
        ("无线局域网 802.11 家族", "concept", "medium", 6, 35)]),
    ("网络层与 IPv4 编址", "异构网互联的核心：无连接的数据报服务与地址体系。", [
        ("IP 数据报格式与分片重组", "principle", "hard", 9, 55),
        ("CIDR 与路由聚合", "skill", "hard", 9, 50),
        ("ICMP 与 ping/traceroute", "skill", "medium", 7, 35),
        ("IPv6 地址与过渡技术", "concept", "medium", 6, 40)]),
    ("路由算法与协议", "包如何在网络中选出路径：域内与域间路由体系。", [
        ("距离矢量算法与 Bellman-Ford", "principle", "hard", 8, 55),
        ("链路状态算法与 OSPF", "principle", "hard", 9, 60),
        ("BGP 域间路由策略", "concept", "medium", 6, 45),
        ("SDN 与 OpenFlow", "concept", "medium", 5, 35)]),
    ("传输层 TCP/UDP", "端到端进程通信：不可靠的尽力服务与可靠的字节流。", [
        ("UDP 数据报与校验和", "concept", "easy", 7, 30),
        ("TCP 首部与滑动窗口流量控制", "principle", "hard", 9, 60),
        ("TCP 拥塞控制四阶段", "principle", "hard", 10, 65),
        ("超时重传与 RTT 估算", "principle", "medium", 8, 45)]),
    ("应用层协议", "直接面向用户：域名、Web、邮件与文件服务。", [
        ("DNS 层次域名系统与解析流程", "skill", "medium", 9, 50),
        ("HTTP 请求/响应报文与状态码", "skill", "medium", 9, 50),
        ("Cookie、会话与 CDN 缓存", "concept", "medium", 6, 35),
        ("电子邮件 SMTP/POP3/IMAP", "concept", "easy", 6, 30),
        ("FTP 与远程登录协议", "concept", "easy", 4, 25)]),
    ("网络编程与抓包实践", "把协议用起来：Socket 编程与报文分析。", [
        ("Socket API 与地址绑定", "skill", "medium", 8, 55),
        ("UDP/TCP 套接字编程", "skill", "hard", 9, 70),
        ("Wireshark 抓包与协议分析", "skill", "medium", 8, 50)]),
    ("无线网络与移动性", "跨越有线边界：Wi-Fi 竞争、移动 IP 与内容分发。", [
        ("CSMA/CA 与隐藏站问题", "principle", "medium", 6, 40),
        ("移动 IP 与切换机制", "concept", "medium", 5, 35),
        ("CDN 内容分发原理", "concept", "medium", 6, 30)]),
    ("网络安全基础", "机密性、完整性与认证：密码学及其工程落地。", [
        ("消息摘要与数字签名", "principle", "medium", 8, 45),
        ("PKI 与身份认证体系", "concept", "medium", 6, 40),
        ("防火墙与 VPN 隧道", "concept", "medium", 6, 35),
        ("TLS 握手过程", "principle", "hard", 8, 55)]),
    ("综合组网实战", "把八章知识装进一张真实网络。", [
        ("小型园区网组网设计", "skill", "hard", 9, 90),
        ("子网划分综合练习", "skill", "hard", 9, 60),
        ("协议抓包综合实验", "skill", "medium", 7, 60)]),
]

OLD_ATTACH = {  # V5 原有 8 点 → 挂接到哪个模块（索引）
    20049: 0, 20050: 3, 20051: 4, 20052: 4, 20053: 5, 20054: 6, 20055: 7, 20056: 10}
OLD_NAMES = {20049: "分层模型与封装", 20050: "CSMA/CD 与交换机", 20051: "IPv4 地址与子网划分",
             20052: "ARP 协议", 20053: "IP 路由与 CIDR", 20054: "TCP 三次握手与四次挥手",
             20055: "HTTP 与 DNS", 20056: "对称与非对称加密"}

kp_rows, PAR, CHILD = [], {}, {}
for mi, (mname, mdesc, children) in enumerate(MODULES):
    pid = B["kp"].nxt()
    PAR[mname] = pid
    kp_rows.append((pid, mname, mdesc, COURSE, None, "medium", 10, 60, "module", mi * 20 + 1, NOW))
    for ci, (cname, ctype, cdiff, cimp, ctime) in enumerate(children):
        cid = B["kp"].nxt()
        CHILD[cname] = cid
        desc = f"{mname}的子知识点：{cname}。建议先修完模块主线内容后逐项突破。"
        kp_rows.append((cid, cname, desc, COURSE, pid, cdiff, cimp, ctime, ctype, mi * 20 + ci + 2, NOW))
KP = {}
KP.update(CHILD)
KP.update(PAR)
OLD_NAME2ID = {v: k for k, v in OLD_NAMES.items()}
MOD_MAP = {m[0]: m[2] for m in MODULES}
KP.update(OLD_NAME2ID)

# V5 原有 8 点挂接到模块父点并统一排序（须位于 knowledge_points INSERT 之后，外键依赖）
OLD_KP_UPDATES = []
for oi in sorted(OLD_ATTACH):
    pname = MODULES[OLD_ATTACH[oi]][0]
    OLD_KP_UPDATES.append(f"UPDATE knowledge_points SET parent_id={PAR[pname]}, order_index={900 + oi},"
                          f" updated_at='2026-10-05 10:00:00' WHERE id={oi};")

dump("knowledge_points",
     "id,name,description,course_id,parent_id,difficulty_level,importance,estimated_time,point_type,order_index,created_at"
     .split(","), kp_rows, f"知识点图谱：{len(MODULES)} 个模块 + {len(CHILD)} 个子点（另挂接 V5 原 8 点）")
OUT.extend(OLD_KP_UPDATES)
OUT.append("")

rel_rows = []


def rel(src, tgt, rtype, strength, desc):
    assert src and tgt, "关系边端点必须已解析"
    rel_rows.append((B["kprel"].nxt(), src, tgt, rtype, strength, desc, NOW))


# 模块主链：分层 → 物理 → 链路 → 以太网 → IP → 路由 → 传输 → 应用 → 编程 → 实战
chain = [("网络体系结构与分层模型", "物理层与数据通信基础", "先建立分层与封装的全局图景，再进入比特传输。"),
         ("物理层与数据通信基础", "数据链路层控制协议", "比特在信道上传输后，需要成帧与差错控制。"),
         ("数据链路层控制协议", "以太网与局域网", "链路层协议在局域网中的工程化身。"),
         ("以太网与局域网", "网络层与 IPv4 编址", "从单网段跨越到互联网络，引出逻辑地址。"),
         ("网络层与 IPv4 编址", "路由算法与协议", "有了编址，才有选路问题。"),
         ("路由算法与协议", "传输层 TCP/UDP", "网络层把包送到主机，传输层送到进程。"),
         ("传输层 TCP/UDP", "应用层协议", "端到端通道之上构建用户可见协议。"),
         ("应用层协议", "网络编程与抓包实践", "协议从书本走向代码与报文。"),
         ("网络编程与抓包实践", "综合组网实战", "课程收官：真实组网与综合实验。")]
for a, b_, d in chain:
    rel(PAR[a], PAR[b_], "PREREQUISITE", 1.0, d)
rel(PAR["以太网与局域网"], PAR["无线网络与移动性"], "PREREQUISITE", 0.9, "无线局域网是以太网在频谱上的延伸。")
rel(PAR["传输层 TCP/UDP"], PAR["网络安全基础"], "PREREQUISITE", 0.8, "TLS 建立在 TCP 与会话语义之上。")
rel(PAR["网络层与 IPv4 编址"], PAR["网络安全基础"], "PREREQUISITE", 0.6, "防火墙/VPN 的过滤对象是 IP 与端口。")

# 模块父点 → 子点：全覆盖先修边
for mname, _, children in MODULES:
    for cname, *_ in children:
        rel(PAR[mname], CHILD[cname], "PREREQUISITE", 0.9, "模块主线知识。")

# 子点级跨模块先修
cross = [("TCP/IP 四层模型", "MAC 地址与以太网帧格式", 0.8),
         ("IP 数据报格式与分片重组", "子网划分综合练习", 0.9),
         ("CIDR 与路由聚合", "小型园区网组网设计", 0.9),
         ("链路状态算法与 OSPF", "小型园区网组网设计", 0.8),
         ("滑动窗口与 GBN/SR 协议", "TCP 首部与滑动窗口流量控制", 0.9),
         ("TCP 首部与滑动窗口流量控制", "超时重传与 RTT 估算", 0.8),
         ("超时重传与 RTT 估算", "TCP 拥塞控制四阶段", 0.9),
         ("HTTP 请求/响应报文与状态码", "Cookie、会话与 CDN 缓存", 0.8),
         ("DNS 层次域名系统与解析流程", "HTTP 请求/响应报文与状态码", 0.7),
         ("Socket API 与地址绑定", "UDP/TCP 套接字编程", 0.95),
         ("Wireshark 抓包与协议分析", "协议抓包综合实验", 0.9),
         ("差错检测与 CRC 校验", "MAC 地址与以太网帧格式", 0.7),
         ("带宽、时延与吞吐量", "奈奎斯特与香农公式", 0.8),
         ("消息摘要与数字签名", "TLS 握手过程", 0.9),
         ("PKI 与身份认证体系", "TLS 握手过程", 0.8),
         ("移动 IP 与切换机制", "CDN 内容分发原理", 0.5),
         ("IPv4 地址与子网划分", "子网划分综合练习", 0.95),
         ("IP 路由与 CIDR", "链路状态算法与 OSPF", 0.8),
         ("TCP 三次握手与四次挥手", "UDP/TCP 套接字编程", 0.7)]
for a, b_, s in cross:
    rel(KP[a], KP[b_], "PREREQUISITE", s, f"掌握「{a}」后再学「{b_}」更顺畅。")

# V5 原 8 点并入图谱
rel(OLD_NAME2ID["分层模型与封装"], CHILD["TCP/IP 四层模型"], "related", 0.9, "示范课图谱融合：分层视角互补。")
rel(OLD_NAME2ID["CSMA/CD 与交换机"], CHILD["MAC 地址与以太网帧格式"], "related", 0.9, "示范课图谱融合。")
rel(OLD_NAME2ID["CSMA/CD 与交换机"], CHILD["交换原理与生成树协议"], "related", 0.8, "示范课图谱融合。")
rel(OLD_NAME2ID["IPv4 地址与子网划分"], CHILD["CIDR 与路由聚合"], "PREREQUISITE", 0.9, "示范课图谱融合：先子网后聚合。")
rel(OLD_NAME2ID["ARP 协议"], CHILD["IP 数据报格式与分片重组"], "PREREQUISITE", 0.8, "示范课图谱融合：转发离不开地址解析。")
rel(OLD_NAME2ID["ARP 协议"], CHILD["小型园区网组网设计"], "related", 0.5, "组网调试离不开 ARP 表项排查。")
rel(OLD_NAME2ID["IP 路由与 CIDR"], CHILD["距离矢量算法与 Bellman-Ford"], "PREREQUISITE", 0.8, "示范课图谱融合。")
rel(OLD_NAME2ID["IP 路由与 CIDR"], CHILD["BGP 域间路由策略"], "related", 0.6, "示范课图谱融合。")
rel(OLD_NAME2ID["TCP 三次握手与四次挥手"], CHILD["TCP 首部与滑动窗口流量控制"], "related", 0.9, "示范课图谱融合。")
rel(OLD_NAME2ID["TCP 三次握手与四次挥手"], CHILD["超时重传与 RTT 估算"], "related", 0.7, "示范课图谱融合。")
rel(OLD_NAME2ID["HTTP 与 DNS"], CHILD["DNS 层次域名系统与解析流程"], "related", 0.9, "示范课图谱融合。")
rel(OLD_NAME2ID["HTTP 与 DNS"], CHILD["HTTP 请求/响应报文与状态码"], "related", 0.9, "示范课图谱融合。")
rel(OLD_NAME2ID["对称与非对称加密"], CHILD["消息摘要与数字签名"], "PREREQUISITE", 0.9, "示范课图谱融合。")
rel(OLD_NAME2ID["对称与非对称加密"], CHILD["TLS 握手过程"], "PREREQUISITE", 0.8, "示范课图谱融合。")

# 同主题关联边
sib = [("停等协议与信道利用率", "滑动窗口与 GBN/SR 协议"),
       ("滑动窗口与 GBN/SR 协议", "HDLC 与 PPP 封装"),
       ("差错检测与 CRC 校验", "HDLC 与 PPP 封装"),
       ("电路交换与分组交换", "移动 IP 与切换机制"),
       ("数字编码与调制技术", "无线局域网 802.11 家族"),
       ("CSMA/CA 与隐藏站问题", "无线局域网 802.11 家族"),
       ("IPv6 地址与过渡技术", "CIDR 与路由聚合"),
       ("ICMP 与 ping/traceroute", "Wireshark 抓包与协议分析"),
       ("UDP 数据报与校验和", "UDP/TCP 套接字编程"),
       ("UDP/TCP 套接字编程", "Socket API 与地址绑定"),
       ("DNS 层次域名系统与解析流程", "电子邮件 SMTP/POP3/IMAP"),
       ("FTP 与远程登录协议", "UDP/TCP 套接字编程"),
       ("防火墙与 VPN 隧道", "IPv6 地址与过渡技术"),
       ("TLS 握手过程", "Cookie、会话与 CDN 缓存"),
       ("带宽、时延与吞吐量", "TCP 拥塞控制四阶段"),
       ("BGP 域间路由策略", "SDN 与 OpenFlow"),
       ("报文封装与逐层交付", "MAC 地址与以太网帧格式"),
       ("子网划分综合练习", "小型园区网组网设计"),
       ("协议抓包综合实验", "子网划分综合练习"),
       ("CDN 内容分发原理", "HTTP 请求/响应报文与状态码")]
for a, b_ in sib:
    rel(KP[a], KP[b_], "related", 0.6, "同一主题簇，建议对照学习。")

dump("knowledge_point_relations",
     "id,source_id,target_id,relation_type,strength,description,created_at".split(","),
     rel_rows, f"知识图谱边：模块链+父子先修+跨点先修+关联，共 {len(rel_rows)} 条")

# ═══════════════════════════ 3. 章节与知识点映射 ═══════════════════════════

CHAPTERS = [
    ("第1章 网络概述与体系结构", "网络的功能、拓扑、分层模型与性能指标。", 1, 2,
     ["网络体系结构与分层模型"], ["分层模型与封装"]),
    ("第2章 物理层", "传输介质、编码调制与信道容量。", 3, 4,
     ["物理层与数据通信基础"], []),
    ("第3章 数据链路层与以太网", "成帧、差错/流量控制、CSMA/CD 与交换机。", 5, 6,
     ["数据链路层控制协议", "以太网与局域网"], ["CSMA/CD 与交换机"]),
    ("第4章 网络层：编址与 ARP", "IP 数据报、IPv4 编址、子网划分、ICMP。", 7, 8,
     ["网络层与 IPv4 编址"], ["IPv4 地址与子网划分", "ARP 协议"]),
    ("第5章 路由算法与协议", "距离矢量、链路状态、BGP 与 SDN。", 9, 10,
     ["路由算法与协议"], ["IP 路由与 CIDR"]),
    ("第6章 传输层", "UDP 与 TCP：握手挥手、流量与拥塞控制。", 11, 12,
     ["传输层 TCP/UDP"], ["TCP 三次握手与四次挥手"]),
    ("第7章 应用层与网络编程", "DNS/HTTP/邮件协议与 Socket、Wireshark 实践，兼论无线与移动性。", 13, 14,
     ["应用层协议", "网络编程与抓包实践", "无线网络与移动性"], ["HTTP 与 DNS"]),
    ("第8章 网络安全与综合实战", "密码学、TLS、防火墙 VPN 与组网课程设计。", 15, 16,
     ["网络安全基础", "综合组网实战"], ["对称与非对称加密"]),
]

chap_rows, ckp_rows = [], []
CH = {}
for ti, (title, content, w1, w2, mods, old_names) in enumerate(CHAPTERS, start=1):
    cid = B["chap"].nxt()
    CH[title] = cid
    chap_rows.append((cid, COURSE, None, title, content, ti, (w2 - w1 + 1) * 160, 1, wk(w1)))
    for m in mods:
        ckp_rows.append((B["ckp"].nxt(), cid, PAR[m]))
        for cname, *_ in MOD_MAP[m]:
            ckp_rows.append((B["ckp"].nxt(), cid, CHILD[cname]))
    for on in old_names:
        ckp_rows.append((B["ckp"].nxt(), cid, OLD_NAME2ID[on]))

dump("course_chapters",
     "id,course_id,parent_id,title,content,order_index,duration,is_published,created_at".split(","),
     chap_rows, "2026秋 教学章节：8 章覆盖协议栈全貌")
dump("chapter_knowledge_points", "id,chapter_id,knowledge_point_id".split(","),
     ckp_rows, "章节-知识点挂载（图谱与学习路径共用）")

# ═══════════════════════════ 4. 教学资源 ═══════════════════════════

RES = []
for i, (title, *_rest) in enumerate(CHAPTERS, start=1):
    RES.append((f"计算机网络·{title.split(' ')[0]}讲义.pdf", "application/pdf", title, 45 + i * 6))
RES += [
    ("实验一：Wireshark 抓包分析指导视频.mp4", "video/mp4", "第3章 数据链路层与以太网", 220),
    ("实验二：Socket 聊天程序演示.mp4", "video/mp4", "第7章 应用层与网络编程", 260),
    ("HTTPS 握手全流程动画解析.mp4", "video/mp4", "第8章 网络安全与综合实战", 180),
    ("子网划分五步法微课.mp4", "video/mp4", "第4章 网络层：编址与 ARP", 120),
    ("园区网组网实战录屏.mp4", "video/mp4", "第8章 网络安全与综合实战", 320),
    ("历年期中复习题集.pdf", "application/pdf", "第5章 路由算法与协议", 12),
    ("协议对照速查表.pdf", "application/pdf", "第1章 网络概述与体系结构", 6),
    ("TCP 拥塞算法图解.pdf", "application/pdf", "第6章 传输层", 18),
]

res_rows, resver_rows = [], []
RES_IDS, VIDEO_RES = [], []
for ri, (fname, ftype, ch_title, size_mb) in enumerate(RES, start=1):
    rid = B["res"].nxt()
    RES_IDS.append(rid)
    if ftype == "video/mp4":
        VIDEO_RES.append(rid)
    url = f"/files/network/{'videos' if ftype == 'video/mp4' else 'docs'}/{ri:02d}"
    up = wk(1 + ri % 5, 1, 10)
    res_rows.append((rid, fname, ftype, url, COURSE, CH[ch_title], TEACHER, up,
                     f"2026秋示范课配套资源：{fname}", 1))
    resver_rows.append((B["resver"].nxt(), rid, 1, fname, ftype, url, "首发版本", size_mb, TEACHER, 0, up))
    if ri % 3 == 0:
        suffix = "（修订版）"
        fname2 = (fname[:-4] + suffix + fname[-4:]) if fname.endswith(".pdf") or fname.endswith(".mp4") else fname + suffix
        resver_rows.append((B["resver"].nxt(), rid, 2, fname2, ftype, url + "-v2",
                            "根据学生反馈补充示例", size_mb + 6, TEACHER, 1, wk(6 + ri % 3, 0, 15)))

dump("resources",
     "id,file_name,file_type,url,course_id,chapter_id,uploader_id,upload_time,description,visible_to_all"
     .split(","), res_rows, "课件/实验视频/复习资料 16 项")
dump("resource_versions",
     "id,resource_id,version_number,file_name,file_type,url,description,file_size,uploader_id,is_active,created_at"
     .split(","), resver_rows, "资源版本历史")

# ═══════════════════════════ 5. 题库（54 题） ═══════════════════════════

TF = ["正确", "错误"]
# (type, content, options, answers, diff, kp_names, explanation)
Q = [
    ("SINGLE_CHOICE", "计算机网络中，「协议」的准确含义是？",
     ["通信双方事先约定并共同遵守的通信规则", "仅指 TCP/IP 家族中的某一层次标准", "硬件接口的电气规范", "操作系统提供的 API 集合"],
     ["A"], "easy", ["网络体系结构与分层模型"], "协议是三要素：语法、语义、同步/时序。"),
    ("SINGLE_CHOICE", "OSI 七层模型中，负责端到端可靠传输的是哪一层？",
     ["传输层", "网络层", "会话层", "数据链路层"],
     ["A"], "easy", ["OSI 七层模型"], "传输层提供进程间端到端的可靠/不可靠传输。"),
    ("SINGLE_CHOICE", "TCP/IP 体系中，网络层的协议数据单元称为？",
     ["报文段", "帧", "数据报/分组", "比特"],
     ["C"], "easy", ["TCP/IP 四层模型", "IP 数据报格式与分片重组"], "IP 层 PDU 为数据报；传输层为报文段。"),
    ("SINGLE_CHOICE", "若链路带宽 1 Gbps，传输 5 MB 文件的理论最短耗时约为？",
     ["0.04 秒", "40 秒", "5 秒", "0.4 秒"],
     ["A"], "medium", ["带宽、时延与吞吐量"], "5MB=40Mbit，40Mbit/1Gbps=0.04s（忽略时延）。"),
    ("SINGLE_CHOICE", "奈奎斯特定理指出，无噪声理想低通信道（带宽 W）的最高码元传输速率为？",
     ["W", "2W", "4W", "W/2"],
     ["B"], "medium", ["奈奎斯特与香农公式"], "最高码元速率 = 2W Baud。"),
    ("SINGLE_CHOICE", "香农公式给出的信道极限是？",
     ["码元速率上限", "数据传输速率上限", "帧长上限", "带宽产品上限"],
     ["B"], "medium", ["奈奎斯特与香农公式"], "C = W·log2(1+S/N) 限定信息传输速率极限。"),
    ("SINGLE_CHOICE", "以太网中，地址 00-1A-2B-3C-4D-5E 属于？",
     ["IP 地址", "MAC 地址", "端口号", "域名"],
     ["B"], "easy", ["MAC 地址与以太网帧格式"], "48 位十六进制是 MAC 地址特征。"),
    ("SINGLE_CHOICE", "CSMA/CD 的核心思想是？",
     ["先听后发、边发边听、冲突停发、随机重发", "时隙轮询避免冲突", "令牌传递独占信道", "频分复用并行传输"],
     ["A"], "medium", ["CSMA/CD 与交换机"], "CSMA/CD 四句口诀。"),
    ("SINGLE_CHOICE", "CRC 校验能够检测出的错误类型主要是？",
     ["所有错误", "绝大多数突发错误", "仅单个比特错误", "仅介质损坏"],
     ["B"], "medium", ["差错检测与 CRC 校验"], "CRC 对突发错误检测能力强，但非完备纠错。"),
    ("SINGLE_CHOICE", "ARQ 协议中，GBN（回到 N）的特点是？",
     ["只有确认帧需要重传", "接收方按序缓存乱序帧", "出错帧及其后所有已发帧都要重传", "使用选择确认"],
     ["C"], "medium", ["滑动窗口与 GBN/SR 协议"], "GBN 累积确认+回退重传；SR 才做选择重传。"),
    ("SINGLE_CHOICE", "二层交换机工作在 OSI 的哪一层？",
     ["物理层", "数据链路层", "网络层", "传输层"],
     ["B"], "easy", ["交换原理与生成树协议", "分层模型与封装"], "二层交换机基于 MAC 表转发。"),
    ("SINGLE_CHOICE", "生成树协议（STP）解决的本质问题是？",
     ["广播风暴与以太网环", "IP 地址冲突", "TCP 拥塞", "DNS 解析缓慢"],
     ["A"], "medium", ["交换原理与生成树协议"], "STP 通过阻塞冗余端口消除交换环。"),
    ("SINGLE_CHOICE", "ARP 协议的作用是？",
     ["把域名解析为 IP", "把 IP 地址解析为 MAC 地址", "把 MAC 解析为 IP", "分配动态 IP"],
     ["B"], "easy", ["ARP 协议"], "ARP 解决同网段内三层地址到二层地址的映射。"),
    ("SINGLE_CHOICE", "IPv4 数据报中的 TTL 字段含义是？",
     ["报文生存时间（最大跳数）", "传输时延", "窗口大小", "校验超时"],
     ["A"], "medium", ["IP 数据报格式与分片重组"], "TTL 每经过一个路由器减 1，防止环路。"),
    ("SINGLE_CHOICE", "子网 192.168.10.0/26 可容纳的可用主机数是？",
     ["62", "64", "126", "30"],
     ["A"], "hard", ["IPv4 地址与子网划分", "子网划分综合练习"], "2^6-2=62，网络地址与广播地址不可用。"),
    ("SINGLE_CHOICE", "CIDR 表示法 10.3.0.0/16 聚合了哪段地址？",
     ["10.3.0.0~10.3.255.255", "10.0.0.0~10.255.255.255", "10.3.0.0~10.3.128.255", "172.16.0.0~172.31.255.255"],
     ["A"], "medium", ["CIDR 与路由聚合"], "/16 覆盖 256 个 /24。"),
    ("SINGLE_CHOICE", "ping 命令依赖的协议是？",
     ["TCP", "UDP", "ICMP", "ARP"],
     ["C"], "easy", ["ICMP 与 ping/traceroute"], "ping 发送 ICMP 回显请求/应答。"),
    ("SINGLE_CHOICE", "RIP 协议限制最大跳数 15 是为了？",
     ["控制路由环带来的慢收敛", "节省带宽", "提高转发速度", "加密路由信息"],
     ["A"], "medium", ["距离矢量算法与 Bellman-Ford"], "16 跳记为不可达，抑制计数到无穷。"),
    ("SINGLE_CHOICE", "OSPF 属于哪一类路由算法？",
     ["距离矢量", "链路状态", "路径矢量", "集中控制"],
     ["B"], "medium", ["链路状态算法与 OSPF"], "OSPF 泛洪 LSDB 并运行 Dijkstra。"),
    ("SINGLE_CHOICE", "BGP 与其他域内路由协议最大的不同在于？",
     ["支持 AS 级策略路由", "使用组播更新", "收敛速度更快", "只支持无类编址"],
     ["A"], "medium", ["BGP 域间路由策略"], "BGP 是路径矢量协议，强调自治系统策略。"),
    ("SINGLE_CHOICE", "UDP 相比 TCP 的主要优势是？",
     ["提供可靠交付", "头部小、时延低、无连接", "自动流量控制", "保证报文有序"],
     ["B"], "easy", ["UDP 数据报与校验和"], "牺牲可靠性换取低开销，适合实时业务。"),
    ("SINGLE_CHOICE", "TCP 三次握手的主要目的是？",
     ["加密通信密钥", "同步初始序号并确认双向收发能力", "分配端口号", "协商窗口"],
     ["B"], "medium", ["TCP 三次握手与四次挥手"], "握手确认双向序列号空间。"),
    ("SINGLE_CHOICE", "TCP 慢启动阶段的拥塞窗口变化是？",
     ["线性增加", "指数增加", "保持不变", "随机变化"],
     ["B"], "medium", ["TCP 拥塞控制四阶段"], "每 RTT cwnd 翻倍，直到 ssthresh。"),
    ("SINGLE_CHOICE", "TCP 收到连续 3 个重复 ACK 会触发？",
     ["慢启动", "快重传与快恢复", "断开连接", "窗口清零"],
     ["B"], "hard", ["TCP 拥塞控制四阶段", "超时重传与 RTT 估算"], "3 重复 ACK → 快重传，cwnd 减半后线性增。"),
    ("SINGLE_CHOICE", "DNS 中权威回答域名记录查询的是？",
     ["根域名服务器", "顶级域服务器", "权限域名服务器", "本地缓存"],
     ["C"], "medium", ["DNS 层次域名系统与解析流程"], "权威服务器持有区域文件。"),
    ("SINGLE_CHOICE", "HTTP 状态码 304 表示？",
     ["服务器内部错误", "资源未修改，读取本地缓存", "永久重定向", "需要身份认证"],
     ["B"], "medium", ["HTTP 请求/响应报文与状态码", "Cookie、会话与 CDN 缓存"], "304 配合 ETag/If-None-Match 使用。"),
    ("SINGLE_CHOICE", "HTTPS 默认使用的传输层端口是？",
     ["80", "443", "8080", "21"],
     ["B"], "easy", ["TLS 握手过程", "HTTP 请求/响应报文与状态码"], "HTTPS 走 TCP 443。"),
    ("SINGLE_CHOICE", "SMTP 协议用于？",
     ["接收邮件到本地客户端", "推送/发送邮件", "解析域名", "传输文件"],
     ["B"], "easy", ["电子邮件 SMTP/POP3/IMAP"], "SMTP 推，POP3/IMAP 拉。"),
    ("SINGLE_CHOICE", "Socket 编程中，TCP 服务端建立连接的正确顺序是？",
     ["socket→bind→listen→accept", "socket→connect→send→close", "bind→socket→accept→listen", "listen→bind→connect→accept"],
     ["A"], "medium", ["Socket API 与地址绑定"], "服务端被动打开四步。"),
    ("SINGLE_CHOICE", "802.11 用 CSMA/CA 而不是 CSMA/CD 的原因是？",
     ["无线无法边发边听检测冲突", "帧更长", "不需要确认", "速率更高"],
     ["A"], "medium", ["CSMA/CA 与隐藏站问题", "无线局域网 802.11 家族"], "无线碰撞不可测，只能避免。"),
    ("SINGLE_CHOICE", "数字签名的正确流程是？",
     ["用私钥加密摘要", "用公钥加密原文", "用对称密钥加密全文", "直接哈希原文即可"],
     ["A"], "medium", ["消息摘要与数字签名"], "签名=私钥对摘要加密；验证用公钥。"),
    ("SINGLE_CHOICE", "关于防火墙与 VPN 的说法正确的是？",
     ["VPN 通过隧道技术跨越公共网络", "防火墙一定能防病毒", "IPsec 工作在应用层", "防火墙只能软件实现"],
     ["A"], "medium", ["防火墙与 VPN 隧道"], "隧道封装是 VPN 的本质。"),
    ("MULTIPLE_CHOICE", "以下属于网络性能指标的有？",
     ["带宽", "时延", "吞吐量", "丢包率", "色彩深度"],
     ["A", "B", "C", "D"], "easy", ["带宽、时延与吞吐量"], "色彩深度与网络无关。"),
    ("MULTIPLE_CHOICE", "数据链路层的帧定界方法包括？",
     ["字节计数法", "字符填充的首尾定界法", "比特填充的标志位法", "非法编码法"],
     ["A", "B", "C", "D"], "medium", ["HDLC 与 PPP 封装"], "四种经典定界方法。"),
    ("MULTIPLE_CHOICE", "关于 IPv6 正确的有？",
     ["地址长度 128 位", "取消广播改用组播", "首部比 IPv4 更简化", "内置 NAT"],
     ["A", "B", "C"], "medium", ["IPv6 地址与过渡技术"], "IPv6 主张端到端，不内置 NAT。"),
    ("MULTIPLE_CHOICE", "TCP 提供的服务特性包括？",
     ["面向连接", "可靠有序", "字节流服务", "点对点单播", "支持组播"],
     ["A", "B", "C", "D"], "medium", ["TCP 首部与滑动窗口流量控制", "传输层 TCP/UDP"], "TCP 不直接支持组播。"),
    ("MULTIPLE_CHOICE", "DNS 解析过程涉及的记录类型有？",
     ["A 记录", "NS 记录", "CNAME 记录", "MX 记录", "VLAN 记录"],
     ["A", "B", "C", "D"], "medium", ["DNS 层次域名系统与解析流程"], "VLAN 是二层概念。"),
    ("MULTIPLE_CHOICE", "对称加密的特点包括？",
     ["加解密同一密钥", "速度快适合大量数据", "密钥分发困难", "可提供数字签名"],
     ["A", "B", "C"], "medium", ["对称与非对称加密"], "签名是非对称密钥的用途。"),
    ("MULTIPLE_CHOICE", "Wireshark 可以完成的任务有？",
     ["按协议过滤报文", "跟踪 TCP 流", "还原 HTTP 对象", "修改路由表"],
     ["A", "B", "C"], "easy", ["Wireshark 抓包与协议分析"], "Wireshark 只观察不改变网络。"),
    ("MULTIPLE_CHOICE", "TCP 拥塞控制包含的阶段有？",
     ["慢启动", "拥塞避免", "快重传与快恢复", "随机早退"],
     ["A", "B", "C"], "medium", ["TCP 拥塞控制四阶段"], "四阶段：慢启动/拥塞避免/快重传/快恢复。"),
    ("TRUE_FALSE", "分组交换相比电路交换提高了链路利用率，但可能引入排队时延与丢包。",
     TF, ["A"], "easy", ["电路交换与分组交换"], "统计复用是分组交换的核心优势。"),
    ("TRUE_FALSE", "以太网交换机既可以隔离冲突域，也可以隔离广播域。",
     TF, ["B"], "medium", ["交换原理与生成树协议", "CSMA/CD 与交换机"], "交换机隔离冲突域，广播域需路由器/VLAN。"),
    ("TRUE_FALSE", "TCP 发送窗口取 min(rwnd, cwnd)，兼顾流量控制与拥塞控制。",
     TF, ["A"], "hard", ["TCP 首部与滑动窗口流量控制", "TCP 拥塞控制四阶段"], "有效窗口=min(rwnd,cwnd)。"),
    ("TRUE_FALSE", "DNS 用 TCP 做区域传送、用 UDP 做普通查询。",
     TF, ["A"], "medium", ["DNS 层次域名系统与解析流程"], "区域传送需可靠传输故用 TCP。"),
    ("TRUE_FALSE", "TLS 握手之后，数据传输仍然全程使用非对称加密保证安全。",
     TF, ["B"], "medium", ["TLS 握手过程", "对称与非对称加密"], "握手协商对称会话密钥，之后用对称加密。"),
    ("TRUE_FALSE", "CIDR 消除了有类地址边界，支持更灵活的路由聚合。",
     TF, ["A"], "easy", ["CIDR 与路由聚合", "IP 路由与 CIDR"], "无类编址是 CIDR 的本质。"),
    ("FILL_BLANK", "ARP 协议工作在 ______ 与网络层之间，用于由 IP 地址求取 MAC 地址。",
     None, ["数据链路层"], "medium", ["ARP 协议"], "ARP 是二、三层之间的桥梁。"),
    ("FILL_BLANK", "TCP 四次挥手中，被动关闭方最后发送的报文段标志位是 ______。",
     None, ["FIN"], "medium", ["TCP 三次握手与四次挥手"], "FIN 段结束连接，随后进入 TIME_WAIT。"),
    ("FILL_BLANK", "10.0.0.0/8、172.16.0.0/12、192.168.0.0/16 统称为 ______ 地址。",
     None, ["私有"], "easy", ["IPv4 地址与子网划分", "CIDR 与路由聚合"], "RFC1918 私有地址段。"),
    ("FILL_BLANK", "香农公式中信噪比 S/N 取 30 ______ 时对应功率比 1000 倍。",
     None, ["分贝(dB)"], "hard", ["奈奎斯特与香农公式"], "10·log10(1000)=30dB。"),
    ("FILL_BLANK", "HTTP/1.1 默认使用 ______ 连接复用传输对象，减少重复握手。",
     None, ["持久(keep-alive)"], "easy", ["HTTP 请求/响应报文与状态码"], "持久连接是 HTTP/1.1 默认。"),
    ("FILL_BLANK", "OSI 参考模型自底向上第三层是 ______ 层。",
     None, ["网络"], "easy", ["OSI 七层模型"], "物理/链路/网络/传输/会话/表示/应用。"),
    ("ESSAY", "简述 TCP 拥塞控制的四个阶段及其触发条件。",
     None, ["慢启动（连接建立或超时后指数增）；拥塞避免（超过 ssthresh 线性增）；快重传（3 个重复 ACK 立即重传）；快恢复（cwnd 减半后线性增）"],
     "hard", ["TCP 拥塞控制四阶段"], "结合超时与重复 ACK 两类信号区分阶段切换。"),
    ("ESSAY", "比较电路交换、报文交换与分组交换的适用场景。",
     None, ["电路交换适合长时恒定带宽（语音专线）；报文交换存储转发、报文大而少（早期电报）；分组交换统计复用、弹性好，是互联网基石"],
     "medium", ["电路交换与分组交换", "网络体系结构与分层模型"], "从时延、利用率、公平性三角度比较。"),
    ("ESSAY", "从用户输入网址到页面渲染，描述经历的主要协议步骤。",
     None, ["DNS 解析（递归+迭代）获取 IP；TCP 三次握手；可选 TLS 握手；HTTP 请求/响应获取对象；并行请求 CSS/JS/图片；连接复用或关闭"],
     "hard", ["DNS 层次域名系统与解析流程", "HTTP 请求/响应报文与状态码", "TCP 三次握手与四次挥手"], "综合考查分层协议协作。"),
    ("ESSAY", "设计题：为 4 栋教学楼规划 IP 地址与 VLAN，说明依据。",
     None, ["按楼分 /24，VLAN 划分隔离广播域；核心三层交换路由； DHCP 池与网关规划；ACL/防火墙策略收口"],
     "hard", ["小型园区网组网设计", "交换原理与生成树协议", "IPv4 地址与子网划分"], "对应课程设计任务，参考组网录屏。"),
]

SCORE = {"SINGLE_CHOICE": 5.0, "MULTIPLE_CHOICE": 6.0, "TRUE_FALSE": 3.0, "FILL_BLANK": 4.0, "ESSAY": 10.0}

q_rows, QIDS = [], []
for qi, (qt, content, opts, ans, diff, kps, expl) in enumerate(Q, start=1):
    qid = B["question"].nxt()
    QIDS.append(qid)
    q_rows.append((qid, qt, content, J(opts) if opts else None, J(ans), diff, SCORE[qt],
                   J(kps), expl, R.randint(0, 6), r2(0.3 + R.random() * 0.6),
                   COURSE, TEACHER, 1, wk(2 + qi // 8, 1, 10), NOW))

dump("questions",
     "id,type,content,options,correct_answers,difficulty,score,knowledge_points,explanation,usage_count,"
     "correct_rate,course_id,created_by,is_active,created_at,updated_at".split(","),
     q_rows, f"计算机网络示范课题库 {len(q_rows)} 题（覆盖全部 12 个知识模块）")

QMETA = {QIDS[i]: dict(qt=Q[i][0], content=Q[i][1], opts=Q[i][2] or TF, ans=Q[i][3],
                       diff=Q[i][4], score=SCORE[Q[i][0]], kps=Q[i][5], expl=Q[i][6])
         for i in range(len(QIDS))}

# ═══════════════════════════ 6. 试卷 / 考试会话 / 错题 ═══════════════════════════

nonessay = [qid for qid in QIDS if QMETA[qid]["qt"] != "ESSAY"]
mid_ids = nonessay[:30]                                   # 第 1–5 章范围
q2_ids = [qid for qid in QIDS
          if any(k in QMETA[qid]["content"] for k in ("TCP", "UDP", "DNS", "HTTP", "邮件", "TLS", "Socket", "OSPF", "拥塞"))][:16]
final_ids = QIDS[:34]

p1, p2, p3 = B["paper"].nxt(), B["paper"].nxt(), B["paper"].nxt()
PAPERS = [
    dict(id=p1, title="计算机网络（A班）2026秋 期中测验",
         desc="考试范围：第 1–5 章（体系结构、物理层、链路层与以太网、网络层、路由）。共 30 题。",
         st="PUBLISHED", start=wk(5, 0, 9), end=wk(5, 0, 10), dur=60, qs=mid_ids, shuffle=True),
    dict(id=p2, title="计算机网络（A班）单元测验二（传输层与应用层）",
         desc="范围：第 6–7 章。计划于第 10 教学周进行，当前正在进行。",
         st="PUBLISHED", start=NOW, end=NOW + dt.timedelta(minutes=45), dur=45, qs=q2_ids, shuffle=False),
    dict(id=p3, title="计算机网络（A班）2026秋 期末复习卷（草稿）",
         desc="全范围复习卷，命题中，暂不下发。",
         st="DRAFT", start=wk(17, 0, 9), end=wk(17, 0, 12), dur=120, qs=final_ids, shuffle=True),
]

paper_rows, examq_rows = [], []
for p in PAPERS:
    total = sum(QMETA[q]["score"] for q in p["qs"])
    paper_rows.append((p["id"], p["title"], p["desc"], COURSE, TEACHER, p["st"],
                       "闭卷，禁止夹带；实验环境统一提供。", p["start"], p["end"], total, p["dur"],
                       len(p["qs"]), 1 if p["shuffle"] else 0, 0, 1, 1, 1, p["dur"], 1, 1, NOW))
    for oi, qid in enumerate(p["qs"], start=1):
        examq_rows.append((B["examq"].nxt(), p["id"], qid, oi, QMETA[qid]["score"]))

dump("exam_papers",
     "id,title,description,course_id,creator_id,status,instructions,start_time,end_time,total_score,duration,"
     "question_count,shuffle_questions,shuffle_options,show_score,show_correct_answer,max_attempts,time_limit,"
     "allow_review,is_active,created_at".split(","), paper_rows, "3 张试卷：期中已考/单元二进行中/期末草稿")
dump("exam_questions", "id,exam_paper_id,question_id,order_index,points".split(","),
     examq_rows, "试卷组卷明细")

MID_TOTAL = sum(QMETA[q]["score"] for q in mid_ids)
sess_rows, wrong_rows, EXAM_PCT = [], [], {}
wrong_cnt = {}
for sid in ALL60:
    a = abl(sid)
    if R.random() > 0.90:
        continue  # 少数缺考
    start = wk(5, 0, 9) + dt.timedelta(minutes=R.randint(0, 12))
    spent = int(60 * 60 * R.uniform(0.55, 0.92))
    ans, qscores, tot = {}, {}, 0.0
    for qid in PAPERS[0]["qs"]:
        m = QMETA[qid]
        p_ok = min(0.97, max(0.05, a + {"easy": 0.15, "medium": 0.0, "hard": -0.2}[m["diff"]]))
        ok = R.random() < p_ok
        if m["qt"] in ("SINGLE_CHOICE", "TRUE_FALSE"):
            correct = m["ans"][0]
            given = correct if ok else R.choice([o for o in "ABCD"[:len(m["opts"])] if o != correct])
        elif m["qt"] == "MULTIPLE_CHOICE":
            given = m["ans"] if ok else sorted(R.sample("ABCDE"[:len(m["opts"])], 2))
        elif m["qt"] == "FILL_BLANK":
            given = m["ans"][0] if ok else "（记不清了）"
        else:
            given = m["ans"][0]
        earned = m["score"] if ok else 0.0
        ans[str(qid)] = given
        qscores[str(qid)] = r2(earned)
        tot += earned
        if not ok and wrong_cnt.get(sid, 0) < 4:
            wrong_cnt[sid] = wrong_cnt.get(sid, 0) + 1
            mastered = R.random() < 0.3
            kp_disp = "、".join(m["kps"])
            wrong_rows.append((
                B["wrong"].nxt(), sid, qid, COURSE,
                J(given) if not isinstance(given, str) else given, "概念混淆或记忆模糊",
                f"该题考点为「{kp_disp}」，建议回到知识图谱重学对应节点并做 2 道配套练习。",
                1 if mastered else 0, "exam", R.randint(1, 3) if mastered else R.randint(0, 1),
                (start + dt.timedelta(days=R.randint(2, 8))) if mastered else None,
                start + dt.timedelta(hours=1), NOW))
    sub = start + dt.timedelta(seconds=spent)
    stt = "GRADED" if R.random() < 0.88 else "AUTO_SUBMITTED"
    pct = r2(tot / MID_TOTAL * 100)
    EXAM_PCT[sid] = pct
    sess_rows.append((
        B["session"].nxt(), p1, sid, stt, 1, start, sub, spent, r2(tot), pct, J(ans), J(qscores),
        f"172.18.{(sid % 50) + 2}.{(sid % 250) + 2}",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/138.0 Safari/537.36",
        None, J(mid_ids), None, NOW))
# 单元测验二：12 名学生进行中（STARTED，部分作答）
for sid in NEW45[:12]:
    start = NOW - dt.timedelta(minutes=R.randint(5, 20))
    ans = {str(q): ("A" if R.random() < 0.6 else "C") for q in q2_ids[:R.randint(3, 6)]}
    sess_rows.append((B["session"].nxt(), p2, sid, "STARTED", 1, start, None, None, None, None,
                      J(ans), None, f"172.18.{(sid % 50) + 2}.9",
                      "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/138.0 Safari/537.36",
                      None, None, None, start))

dump("exam_sessions",
     "id,exam_paper_id,student_id,status,attempt_number,start_time,submit_time,time_spent,total_score,percentage,"
     "answers,question_scores,ip_address,user_agent,cheating_flags,question_order,option_orders,created_at"
     .split(","), sess_rows, "期中测验答卷会话 + 单元测验进行中样本")
dump("wrong_questions",
     "id,student_id,question_id,course_id,wrong_answer,reason,ai_analysis,mastered,source_type,practice_count,"
     "last_practiced_at,created_at,updated_at".split(","), wrong_rows, "错题本（自动生成，带 AI 分析与掌握状态）")

# ═══════════════════════════ 7. 任务点 / 作业提交 ═══════════════════════════

CHQ, CHA = "quiz", "assignment"
TASKS = [
    ("第1周课前测：分层模型自查", CHQ, "第1章 网络概述与体系结构", "CLOSED", wk(1, 4, 23, 59), 1, None),
    ("信道容量与编码计算作业", CHA, "第2章 物理层", "CLOSED", wk(3, 2, 23, 59), 1, "docx"),
    ("实验一：Wireshark 抓包分析", CHQ, "第3章 数据链路层与以太网", "CLOSED", wk(4, 3, 23, 59), 1, None),
    ("子网划分综合练习（12 小题）", CHA, "第4章 网络层：编址与 ARP", "PUBLISHED", wk(6, 2, 23, 59), 1, "docx"),
    ("实验二：Socket 聊天程序", CHQ, "第7章 应用层与网络编程", "PUBLISHED", wk(7, 5, 23, 59), 1, None),
    ("课程设计：小型园区网组网方案", CHA, "第8章 网络安全与综合实战", "PUBLISHED", wk(12, 0, 23, 59), 1, "zip"),
]

task_rows, TASK_IDS = [], []
for ti, (title, ttype, ch_title, st, due, pub, fmt) in enumerate(TASKS, start=1):
    tid = B["task"].nxt()
    TASK_IDS.append(tid)
    start = due - dt.timedelta(days=7)
    task_rows.append((tid, f"计算机网络·{title}", f"{title}（2026秋 示范课任务点，对应{ch_title}）。", ttype,
                      COURSE, TEACHER, CH[ch_title], due, 100, st, pub, fmt,
                      "按讲义规范作答，提交后不可修改；迟交每天扣 10%。",
                      f"/files/network/tasks/{tid}.{'zip' if fmt == 'zip' else 'pdf'}",
                      start, 10.0, 3, start - dt.timedelta(days=1), NOW))

dump("tasks",
     "id,title,description,task_type,course_id,creator_id,chapter_id,due_date,max_score,status,is_published,"
     "submission_format,requirements,attachment_url,start_date,late_penalty_per_day,max_submissions,created_at,updated_at"
     .split(","), task_rows, "2026秋 任务点：3 测验任务 + 3 作业任务")

tc_rows, sub_rows = [], []
HOMEWORK_SCORE = {}
for ti, (title, ttype, ch_title, st, due, pub, fmt) in enumerate(TASKS):
    tid, chid = TASK_IDS[ti], CH[ch_title]
    if st == "CLOSED":
        part = ALL60
    elif ti == 3:                     # 子网划分作业：截至今天已 28 人开始
        part = ALL60[:28]
    elif ti == 4:                     # 实验二测验：14 人开始
        part = ALL60[:14]
    else:
        part = []                     # 课程设计：第 12 周才截止，尚无进度
    for sid in part:
        a = abl(sid)
        score = r2(min(100, max(40, a * 100 + R.uniform(-9, 9))))
        comp = R.random() < (0.96 if st == "CLOSED" else 0.8)
        done_at = due - dt.timedelta(days=R.randint(0, 3), hours=R.randint(1, 30))
        if done_at > NOW:
            done_at = NOW - dt.timedelta(hours=R.randint(1, 40))
        tc_rows.append((B["taskcompl"].nxt(), sid, tid, COURSE, chid, ttype,
                        "ASSIGNMENT" if ttype == CHA else "QUIZ", None, None, None,
                        score if comp else None, 1 if comp else 0,
                        done_at if comp else None, done_at - dt.timedelta(hours=1), NOW))
        if ttype == CHA and comp:
            graded = st == "CLOSED" or R.random() < 0.55
            late = R.random() < 0.06
            if graded:
                HOMEWORK_SCORE.setdefault(sid, []).append(score)
            sub_rows.append((
                B["subm"].nxt(), tid, sid,
                f"{title}提交：解题过程与结果汇总（文本摘录）",
                J([f"/files/network/submissions/{sid}_{tid}.docx"]) if fmt == "docx" else
                (J([f"/files/network/submissions/{sid}_{tid}.zip"]) if fmt == "zip" else None),
                "GRADED" if graded else "SUBMITTED",
                done_at, done_at + dt.timedelta(days=1) if graded else None, None,
                score if graded else None,
                "步骤完整，结论正确。" if graded and score >= 85 else "注意报文格式与单位细节。" if graded else None,
                "AI 预审：与范文相似度 8%，未发现抄袭特征。" if graded else None,
                TEACHER if graded else None, 1, 1 if late else 0, r2(10 * late),
                R.randint(600, 3600), R.randint(0, 4), done_at - dt.timedelta(hours=1), NOW))

dump("task_completions",
     "id,student_id,task_id,course_id,chapter_id,task_type,completion_type,watched_duration,last_position,"
     "total_duration,score,completed,completed_at,created_at,updated_at".split(","), tc_rows, "任务点完成记录")
dump("submissions",
     "id,task_id,student_id,content,file_urls,status,submitted_at,graded_at,returned_at,score,feedback,"
     "ai_feedback,grader_id,attempt_number,is_late,late_penalty,time_spent,view_count,created_at,updated_at"
     .split(","), sub_rows, "作业提交与批改")

# ═══════════════════════════ 8. 三维成绩（新 45 人） ═══════════════════════════

POLICY = 580007  # V5 生成的 4007 成绩方案（考勤10/作业30/考试60）
gi_rows = []
for sid in NEW45:
    a = abl(sid)
    att = r2(a * 100)
    hw_list = HOMEWORK_SCORE.get(sid) or []
    hw = r2(sum(hw_list) / len(hw_list)) if hw_list else r2(a * 92)
    ex = EXAM_PCT.get(sid)
    for key, val in (("SIGN_IN", att), ("HOMEWORK", hw), ("EXAM", ex)):
        if val is None:
            continue
        gi_rows.append((B["gi"].nxt(), POLICY, sid, key, r2(val), r2(val), None,
                        TEACHER, wk(6, 1, 16), NOW))
dump("grade_items",
     "id,policy_id,student_id,component_key,score,raw_score,remark,grader_id,created_at,updated_at"
     .split(","), gi_rows, "三维成绩项：考勤/作业/考试（2026秋 截至第 6 周）")

OUT.append("-- 同步选课表考试分（期中已判卷学生）")
for sid in sorted(EXAM_PCT):
    if sid in NEW45:
        OUT.append(f"UPDATE course_enrollments SET exam_score={r2(EXAM_PCT[sid])},"
                   f" updated_at='{NOW.strftime('%Y-%m-%d %H:%M:%S')}'"
                   f" WHERE course_id={COURSE} AND student_id={sid};")
OUT.append("")

# ═══════════════════════════ 9. 签到 ═══════════════════════════

SI_TYPES = [("第1周课堂签到", "NORMAL", 1, "第1章 网络概述与体系结构"),
            ("第2周二维码签到", "QR", 2, "第2章 物理层"),
            ("第3周手势签到", "GESTURE", 3, "第3章 数据链路层与以太网"),
            ("第4周定位签到", "LOCATION", 4, "第4章 网络层：编址与 ARP")]
si_rows, sir_rows = [], []
for title, typ, w, ch_title in SI_TYPES:
    sgi = B["si"].nxt()
    start = wk(w, 2, 10)
    si_rows.append((sgi, COURSE, CH[ch_title], f"计算机网络（A班）·{title}", typ, "CLOSED", 5,
                    "左上右下" if typ == "GESTURE" else None, "8842" if typ == "QR" else None,
                    TEACHER, start, start + dt.timedelta(minutes=5), NOW))
    for student in ALL60:
        roll = R.random()
        stt = "SIGNED" if roll < 0.82 else "LATE" if roll < 0.90 else "ABSENT" if roll < 0.96 else "LEAVE"
        on_site = stt in ("SIGNED", "LATE")
        sir_rows.append((
            B["sir"].nxt(), sgi, student, stt,
            start + dt.timedelta(minutes=R.randint(1, 4) if stt == "SIGNED" else R.randint(6, 12)) if on_site else None,
            f"172.18.{(student % 40) + 2}.{(student % 200) + 2}" if on_site else None,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18) Mobile Safari/605.1.15" if on_site else None,
            r2(30.6767 + R.uniform(-0.002, 0.002)) if typ == "LOCATION" and on_site else None,
            r2(114.3576 + R.uniform(-0.002, 0.002)) if typ == "LOCATION" and on_site else None,
            "8842" if typ == "QR" and on_site else None,
            "左上右下" if typ == "GESTURE" and on_site else None))
# 第 5 周（今天）进行中签到
open_start = NOW - dt.timedelta(minutes=3)
si_rows.append((B["si"].nxt(), COURSE, CH["第5章 路由算法与协议"],
                "计算机网络（A班）·第5周课堂签到", "NORMAL", "ONGOING", 5, None, None,
                TEACHER, open_start, None, NOW))

dump("sign_ins",
     "id,course_id,chapter_id,title,type,status,duration_minutes,gesture,sign_code,creator_id,started_at,"
     "closed_at,created_at".split(","), si_rows, "2026秋 签到：4 次已关闭（NORMAL/QR/GESTURE/LOCATION）+ 1 次进行中")
dump("sign_in_records",
     "id,sign_in_id,student_id,status,sign_time,ip,user_agent,latitude,longitude,submitted_code,"
     "submitted_gesture".split(","), sir_rows, "签到记录")

# ═══════════════════════════ 10. 投票 ═══════════════════════════

VOTES = [
    ("期中后教学难度感受", "SINGLE_CHOICE", [("a", "简单"), ("b", "适中"), ("c", "偏难")], "CLOSED", 5,
     "第5章 路由算法与协议"),
    ("后续实验课选题（可多选）", "MULTIPLE_CHOICE",
     [("a", "HTTP 缓存分析"), ("b", "TLS 握手抓包"), ("c", "路由收敛模拟"), ("d", "拥塞算法对比")], "OPEN", 6,
     "第7章 应用层与网络编程"),
    ("是否增加每周习题讲评直播", "SINGLE_CHOICE", [("a", "需要"), ("b", "不需要"), ("c", "看时间安排")], "OPEN", 6,
     "第6章 传输层"),
    ("第 9 周课堂时间微调表决", "SINGLE_CHOICE", [("a", "维持原时间"), ("b", "提前到 10:00")], "PENDING", 9,
     "第8章 网络安全与综合实战"),
]
vote_rows, vr_rows = [], []
for title, mode, opts, st, w, ch_title in VOTES:
    vid = B["vote"].nxt()
    start = wk(w, 2, 10)
    vote_rows.append((vid, COURSE, CH[ch_title], title, "随堂表决，用于教学决策参考。", mode,
                      J([dict(id=i, text=t) for i, t in opts]), st, TEACHER, start,
                      start + dt.timedelta(hours=1) if st == "CLOSED" else None))
    n = {"CLOSED": 58, "OPEN": 41, "PENDING": 0}[st]
    for student in ALL60[:n]:
        if mode == "MULTIPLE_CHOICE":
            choice = J(sorted(R.sample([o[0] for o in opts], R.randint(1, 3))))
        else:
            choice = J(R.choice([o[0] for o in opts]))
        vr_rows.append((B["vr"].nxt(), vid, student, choice,
                        f"172.18.{(student % 30) + 2}.88", start + dt.timedelta(minutes=R.randint(2, 40))))

dump("votes", "id,course_id,chapter_id,title,description,vote_mode,options,status,creator_id,started_at,closed_at"
     .split(","), vote_rows, "随堂投票 4 个（单选/多选/待开始）")
dump("vote_records", "id,vote_id,student_id,choice,ip,created_at".split(","), vr_rows, "投票记录")

# ═══════════════════════════ 11. 讨论区 ═══════════════════════════

TOPICS = [
    ("QA", "【置顶】实验工具清单与环境配置指南（Wireshark/Mininet）", 1, 0, 0, TEACHER, "TEACHER", 1, 5),
    ("QA", "子网划分计算里主机数为什么要减 2？", 0, 0, 0, NEW45[0], "STUDENT", 4, 6),
    ("NORMAL", "【精华】两本教材分层讲解差异对照笔记", 0, 1, 1, NEW45[5], "STUDENT", 6, 4),
    ("QA", "TCP 三次握手为什么不能是两次？", 0, 0, 0, NEW45[9], "STUDENT", 8, 7),
    ("QA", "Wireshark 抓不到本机回环流量，是设置问题吗？", 0, 0, 0, NEW45[13], "STUDENT", 10, 4),
    ("NORMAL", "实验二 Socket 聊天程序：多人房间怎么扩展？思路讨论", 0, 0, 0, NEW45[20], "STUDENT", 11, 6),
    ("QA", "OSPF 与 RIP 收敛速度差异的本质原因", 0, 0, 0, NEW45[28], "STUDENT", 13, 5),
    ("NORMAL", "课程设计分组名单公示（每组 3 人，附选题范围）", 1, 0, 0, TEACHER, "TEACHER", 14, 3),
]
REPLY_POOL = ["因为网络地址和广播地址要保留，可用主机只有 2^n-2。",
              "两次握手无法确认客户端的接收能力，还会浪费服务器资源。",
              "回环流量需要 npcap/Loopback Adapter，管理员模式重启 Wireshark 即可。",
              "建议用线程池或 select/epoll，先画状态机再写代码。",
              "OSPF 泛洪+Dijkstra 全局视图，天然比逐跳距离矢量收敛快。",
              "同意，补充：分组已按学号段划分，见班级共享文档。",
              "注意 TCP 首部标志位顺序，实验报告要附抓包截图。",
              "受教了，我之前混淆了 CIDR 与子网掩码的概念。",
              "推荐配合微课《子网划分五步法》一起看。",
              "老师第 3 章课件里的例题 5 可以再讲一遍吗？"]
AUTHORS60 = NEW45 + OLD15
dt_rows, dp_rows, dl_rows = [], [], []
for i, (typ, title, pin, _a, essence, author, role, w, n_posts) in enumerate(TOPICS):
    tid = B["dt"].nxt()
    created = wk(w, 1, 20)
    ch = CH[CHAPTERS[min(7, w)][0]]
    dt_rows.append((tid, COURSE, ch, title,
                    f"{title}。欢迎跟帖讨论。" + ("（教师发布，务必阅读）" if role == "TEACHER" else ""),
                    typ, "OPEN", author, role, pin, essence, n_posts,
                    created + dt.timedelta(days=1, hours=R.randint(2, 20)), created))
    replies, floor = [], 1
    for j in range(n_posts):
        pid = B["dp"].nxt()
        rid = R.choice(replies) if replies and R.random() < 0.35 else None
        au = TEACHER if (i == 0 and j == 2) or (i == 3 and j == 1) else R.choice(AUTHORS60)
        likes = R.randint(0, 12)
        dp_rows.append((pid, tid, rid, au, "TEACHER" if au == TEACHER else "STUDENT",
                        REPLY_POOL[(i * 3 + j) % len(REPLY_POOL)], likes, floor, None,
                        created + dt.timedelta(hours=2 + 5 * j)))
        floor += 1
        replies.append(pid)
    for p in dp_rows[-n_posts:]:
        for _ in range(min(6, p[6] // 2)):
            liker = R.choice(AUTHORS60)
            if (p[0], liker) in {(x[1], x[2]) for x in dl_rows}:
                continue
            dl_rows.append((B["dl"].nxt(), p[0], liker, p[9] + dt.timedelta(hours=R.randint(1, 24))))

dump("discussion_topics",
     "id,course_id,chapter_id,title,content,type,status,creator_id,creator_role,is_pinned,is_essence,reply_count,"
     "last_reply_at,created_at".split(","), dt_rows, "讨论区主题 8 个（置顶/精华/QA）")
dump("discussion_posts",
     "id,topic_id,parent_id,author_id,author_role,content,like_count,floor,mentions,created_at".split(","),
     dp_rows, "讨论回帖（含楼中楼）")
dump("discussion_post_likes", "id,post_id,user_id,created_at".split(","), dl_rows, "回帖点赞")

# ═══════════════════════════ 12. 个性化学习路径 ═══════════════════════════

PATH_STUDENTS = NEW45[:6]
PATH_MODS = ["网络体系结构与分层模型", "网络层与 IPv4 编址", "传输层 TCP/UDP", "应用层协议", "网络安全基础"]
lp_rows, lpn_rows = [], []
PATH_NODES = ["OSI 七层模型", "TCP/IP 四层模型", "IPv4 地址与子网划分", "CIDR 与路由聚合",
              "TCP 三次握手与四次挥手", "TCP 拥塞控制四阶段", "DNS 层次域名系统与解析流程",
              "HTTP 请求/响应报文与状态码", "对称与非对称加密", "小型园区网组网设计"]
for pi, sid in enumerate(PATH_STUDENTS):
    lpid = B["lp"].nxt()
    rate = R.randint(25, 75)
    stages = [{"name": f"阶段{i + 1}：{m}", "estimatedDays": 7} for i, m in enumerate(PATH_MODS)]
    lp_rows.append((lpid, sid, COURSE, "计算机网络个性化学习路径",
                    "基于 计算机网络（A班） 知识点图谱，按掌握度自动编排的学习路线", J(stages), None, None,
                    "期中考试后查漏补缺，12 周内走完图谱主干", rate // 20 + 1, 5, float(rate), "IN_PROGRESS",
                    wk(14 + pi, 0, 20), None, wk(2 + pi, 0, 21), NOW))
    done = int(len(PATH_NODES) * rate / 100)
    for ni, n in enumerate(PATH_NODES):
        lpn_rows.append((B["lpn"].nxt(), lpid, KP[n], None, n, f"图谱路径节点：{n}",
                         "practice" if ni % 4 == 3 else "study",
                         "COMPLETED" if ni < done else "IN_PROGRESS" if ni == done else "PENDING",
                         ni + 1, 45, R.randint(30, 80) if ni < done else 0,
                         wk(3 + ni, 0, 20) if ni < done else None))

dump("learning_paths",
     "id,student_id,course_id,path_name,description,stages,plan_detail,daily_tasks,learning_goal,current_stage,"
     "total_stages,completion_rate,status,estimated_completion_date,actual_completion_date,created_at,updated_at"
     .split(","), lp_rows, "个性化学习路径 6 条（st001–st006）")
dump("learning_path_nodes",
     "id,learning_path_id,knowledge_point_id,chapter_id,title,description,node_type,status,order_index,"
     "estimated_time,actual_time,completed_at".split(","), lpn_rows, "路径节点（与图谱知识点挂钩）")

# ═══════════════════════════ 13. 笔记 / 学习行为 ═══════════════════════════

CHILD_NAMES = list(CHILD)
note_rows = []
for i in range(24):
    sid = NEW45[i % 45]
    name = CHILD_NAMES[(i * 7) % len(CHILD_NAMES)]
    note_rows.append((B["note"].nxt(), sid, COURSE, None, CHILD[name], f"「{name}」学习笔记",
                      f"要点：{name}。先记定义与边界条件，再看协议报文示例，最后做配套测验；"
                      f"易错点已整理进错题本。（2026秋 计算机网络示范课）",
                      1 if i % 5 == 0 else 0, wk(3 + i % 3, 0, 21), NOW))
dump("notes",
     "id,user_id,course_id,chapter_id,knowledge_point_id,title,content,is_favorite,created_at,updated_at"
     .split(","), note_rows, "学生课程笔记 24 篇（锚定图谱知识点）")

lr_rows = []
PDF_RES = [r for r in RES_IDS if r not in VIDEO_RES]
for i in range(220):
    sid = ALL60[i % 60]
    rid = RES_IDS[(i * 7) % len(RES_IDS)]
    is_video = rid in VIDEO_RES
    act = "download" if R.random() < 0.25 else "view"
    lr_rows.append((B["lr"].nxt(), sid, rid, None, COURSE, act,
                    R.randint(180, 1800) if act == "view" else 1,
                    float(R.randint(20, 100)) if act == "view" and not is_video else 0.0,
                    J({"device": "Chrome/138", "source": "cn-showcase"}),
                    wk(2 + i % 4, 1, 9 + (i % 12))))
dump("learning_records",
     "id,user_id,resource_id,chapter_id,course_id,action_type,duration,progress,metadata,created_at"
     .split(","), lr_rows, "资源阅读/下载行为 220 条")

vlr_rows, vws_rows = [], []
for vid in VIDEO_RES:
    for sid in ALL60[:24]:
        prog = float(R.randint(15, 100))
        last = int(900 * prog / 100)
        lw = NOW - dt.timedelta(days=R.randint(0, 20), hours=R.randint(0, 12))
        vlr_rows.append((B["vlr"].nxt(), sid, vid, COURSE, last, prog, last,
                         1 if prog == 100 else 0, lw, NOW))
        if prog >= 60:
            start = lw - dt.timedelta(minutes=12)
            vws_rows.append((B["vws"].nxt(), sid, vid, max(0, last - R.randint(60, 300)), last,
                             R.randint(120, 600), start, lw))
dump("video_learning_records",
     "id,user_id,resource_id,course_id,watch_duration,progress,last_position,complete_count,last_watched_at,created_at"
     .split(","), vlr_rows, "视频观看进度")
dump("video_watch_sessions",
     "id,user_id,resource_id,start_position,end_position,duration,started_at,ended_at".split(","),
     vws_rows, "视频观看会话明细")

# ═══════════════════════════ 14. AI 问答 ═══════════════════════════

AI_USERS = [2001, 2002, 2003, 2004, 2005, 2046, 2050]
ai_sess_rows, ai_msg_rows = [], []
for ui, uid in enumerate(AI_USERS):
    for k in range(2 if ui < 3 else 1):
        sess = f"seedcn-{uid}-{k}"
        n_msg = R.randint(4, 8)
        created = wk(4 + ui, 0, 20)
        ai_sess_rows.append((B["aic"].nxt(), sess, uid, f"计算机网络示范课问答 {k + 1}", n_msg, 0, created))
        for m in range(n_msg):
            kp_name = CHILD_NAMES[(uid * 5 + m * 13 + k) % len(CHILD_NAMES)]
            if m % 2 == 0:
                content = f"老师，「{kp_name}」到底怎么理解？能结合抓包给我讲讲吗？"
            else:
                content = (f"结合本课程讲义：先记住「{kp_name}」的定义与适用边界，再看它在协议栈中的上下游关系；"
                           f"建议在实验一的 Wireshark 会话里观察对应报文，配合知识图谱中它的先修链完成预习。")
            ai_msg_rows.append((B["aim"].nxt(), sess, "user" if m % 2 == 0 else "assistant", content, 0,
                                created + dt.timedelta(minutes=3 * m)))
dump("ai_chat_sessions",
     "id,session_id,user_id,title,message_count,deleted,created_at".split(","),
     ai_sess_rows, "AI 助手会话（st001 等 7 人）")
dump("ai_chat_messages", "id,session_id,role,content,deleted,created_at".split(","),
     ai_msg_rows, "AI 问答消息")

# ═══════════════════════════ 输出 ═══════════════════════════

header = ("-- ============================================================\n"
          "-- V6：计算机网络示范课（course 4007, 2026秋）全功能种子数据\n"
          "-- 由 scripts/generate-cn-showcase-seed.py 生成，请勿手工编辑。\n"
          "-- 号段：700xxxx 独立区间；不删除 V1–V5 任何既有行\n"
          "-- （仅 UPDATE：课程激活、V5 原 8 知识点挂接新模块、期中考试分回填）。\n"
          "-- ============================================================\n\n"
          "SET NAMES utf8mb4;\n")
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "backend", "src", "main", "resources", "db", "seed-demo",
                    "V6__computer_network_showcase.sql")
with open(path, "w", encoding="utf-8") as f:
    f.write(header + "\n".join(OUT) + "\n")

print(f"V6 生成完成 → {os.path.relpath(path, os.getcwd())}")
print(f"  知识点: 模块 {len(MODULES)} + 子点 {len(CHILD)}（图谱节点共 {len(MODULES) + len(CHILD) + 8}）")
print(f"  关系边: {len(rel_rows)}  章节: {len(chap_rows)}  ckp: {len(ckp_rows)}  题库: {len(q_rows)}  试卷: {len(paper_rows)}")
print(f"  考试会话: {len(sess_rows)}  错题: {len(wrong_rows)}  任务: {len(task_rows)}  完成: {len(tc_rows)}  提交: {len(sub_rows)}")
print(f"  成绩项: {len(gi_rows)}  签到: {len(si_rows)}/记录 {len(sir_rows)}  投票: {len(vote_rows)}/记录 {len(vr_rows)}")
print(f"  讨论: {len(dt_rows)}/帖 {len(dp_rows)}/赞 {len(dl_rows)}  路径: {len(lp_rows)}/节点 {len(lpn_rows)}")
print(f"  笔记: {len(note_rows)}  资源: {len(res_rows)}/版本 {len(resver_rows)}  行为: {len(lr_rows)}/{len(vlr_rows)}/{len(vws_rows)}")
print(f"  AI: {len(ai_sess_rows)}/{len(ai_msg_rows)}  选课: {len(enroll_rows)}")
