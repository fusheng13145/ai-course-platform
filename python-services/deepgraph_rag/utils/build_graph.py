import streamlit as st
import networkx as nx
import re
import requests
import json
import ast
import hashlib
import concurrent.futures
from config import OLLAMA_API_URL, MODEL
from neo4j_handler import neo4j_db
from utils.llm_client import call_llm, get_provider_name
import os
from collections import Counter
import jieba.posseg as pseg

os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

# ============================================================
# 同义词归一化字典 (Synonym Normalization)
# 用于后端剪枝：将大模型抽出的异名实体合并为规范名称
# ============================================================
SYNONYM_DICT = {
    "TCP": "传输控制协议",
    "UDP": "用户数据报协议",
    "IP": "网际协议",
    "HTTP": "超文本传输协议",
    "HTTPS": "超文本传输安全协议",
    "FTP": "文件传输协议",
    "SMTP": "简单邮件传输协议",
    "DNS": "域名系统",
    "DHCP": "动态主机配置协议",
    "ARP": "地址解析协议",
    "ICMP": "网际控制报文协议",
    "OSI": "OSI参考模型",
    "TCP/IP": "TCP/IP协议簇",
    "AI": "人工智能",
    "ML": "机器学习",
    "DL": "深度学习",
    "CNN": "卷积神经网络",
    "RNN": "循环神经网络",
    "LSTM": "长短期记忆网络",
    "GAN": "生成对抗网络",
    "NLP": "自然语言处理",
    "CPU": "中央处理器",
    "GPU": "图形处理器",
    "RAM": "随机存取存储器",
    "ROM": "只读存储器",
    "DB": "数据库",
    "SQL": "结构化查询语言",
    "API": "应用程序接口",
    "SDK": "软件开发工具包",
    "GUI": "图形用户界面",
    "CLI": "命令行界面",
}

# ============================================================
# 关系词归一化映射表
# 将大模型可能输出的同义关系词统一为标准关系词
# ============================================================
RELATION_NORMALIZATION = {
    "包含": "包含",
    "包括": "包含",
    "由...组成": "包含",
    "由...构成": "包含",
    "组成部分": "包含",
    "子类": "包含",
    "属于": "属于",
    "是": "属于",
    "是一种": "属于",
    "定义为": "定义",
    "定义": "定义",
    "是指": "定义",
    "指的是": "定义",
    "功能": "功能",
    "负责": "功能",
    "用于": "功能",
    "作用": "功能",
    "实现": "功能",
    "具有": "具有",
    "拥有": "具有",
    "具备": "具有",
    "属性": "具有",
    "特征": "具有",
    "导致": "导致",
    "引起": "导致",
    "产生": "导致",
    "造成": "导致",
    "依赖": "依赖",
    "依赖于": "依赖",
    "基于": "依赖",
    "前提": "依赖",
    "前置": "依赖",
    "相关": "相关",
    "关联": "相关",
    "联系": "相关",
    "关系": "相关",
    "应用": "应用",
    "使用": "应用",
    "利用": "应用",
    "采用": "应用",
    "示例": "示例",
    "例如": "示例",
    "比如": "示例",
    "对比": "对比",
    "区别": "对比",
    "不同": "对比",
    "比较": "对比",
    "优势": "优势",
    "优点": "优势",
    "劣势": "劣势",
    "缺点": "劣势",
    # 学术级关系词（计算机网络专用）
    "包含协议": "包含协议",
    "属于层级": "属于层级",
    "运行于": "运行于",
    "核心功能": "核心功能",
    "工作机制": "工作机制",
    "包含字段": "包含字段",
    "前置依赖": "前置依赖",
    "特征": "特征",

}


def normalize_relation(rel):
    """关系词归一化：将同义关系词映射为标准关系词"""
    rel = rel.strip()
    # 精确匹配
    if rel in RELATION_NORMALIZATION:
        return RELATION_NORMALIZATION[rel]
    # 模糊匹配：如果关系词包含某个标准词，也进行映射
    for key, value in RELATION_NORMALIZATION.items():
        if key in rel or rel in key:
            return value
    # 如果关系词过长（>8字），可能是描述性短语，统一为"相关"
    if len(rel) > 8:
        return "相关"
    return rel


def merge_edge_labels(label1, label2):
    """
    智能熔断并合并两个关系标签，杜绝出现 '包含/包含'、'相关/具有' 等复读机现象。
    优先保留更具体的学术/技术级关系词。
    """
    l1 = normalize_relation(label1)
    l2 = normalize_relation(label2)
    
    if l1 == l2:
        return l1
        
    # 如果互相包含（例如 "包含协议" 和 "包含"），保留更具体的那个
    if l1 in l2:
        return l2
    if l2 in l1:
        return l1
        
    # 建立弱关系词过滤链（如果一个是万能模糊词，另一个是具体动词，留具体动词）
    weak_words = ["相关", "具有", "属于", "包含", "特征"]
    if l1 in weak_words and l2 not in weak_words:
        return l2
    if l2 in weak_words and l1 not in weak_words:
        return l1
        
    # 如果两个都很具体（如 "运行于" 和 "核心功能"），说明这条边承载了多重属性，用规范的斜杠隔开，但做去重
    labels = sorted(list(set([l1, l2])))
    return "/".join(labels)


# ============================================================
# 词性标注过滤 (POS Tagging Filter)
# 使用 jieba.posseg 对候选实体进行词性标注，仅保留名词性实体
# ============================================================
# 名词词性标记集合（涵盖常见名词类型）
NOUN_POS_TAGS = {
    "n",     # 名词
    "nr",    # 人名
    "ns",    # 地名
    "nt",    # 机构名
    "nz",    # 其他专名
    "nl",    # 名词性惯用语
    "ng",    # 名词性语素
    "nw",    # 新词（未登录词）
    "an",    # 名形词
    "vn",    # 名动词
    "f",     # 方位名词
    "s",     # 处所名词
    "t",     # 时间名词
    "b",     # 区别词（属性词）
    "v",     # 动词（保留部分动词性概念，如"传输"、"发送"）
    "vd",    # 副动词
    "vn",    # 名动词
}

# 需要过滤掉的非概念性词性标记
NON_CONCEPT_POS_TAGS = {
    "c",     # 连词
    "d",     # 副词
    "e",     # 叹词
    "m",     # 数词
    "o",     # 拟声词
    "p",     # 介词
    "q",     # 量词
    "r",     # 代词
    "u",     # 助词
    "w",     # 标点符号
    "x",     # 非语素字
    "y",     # 语气词
    "z",     # 状态词
    "uj",    # 助词"的"
    "ul",    # 助词"了"
    "uz",    # 助词"着"
    "ug",    # 助词"过"
}


# ============================================================
# 第二级防线增强：黑名单/白名单机制
# ============================================================
# 黑名单正则：匹配"数字+单位"模式，如 "64字节"、"10Mbps"、"100μs"、"32位"
UNIT_BLACKLIST_PATTERN = re.compile(
    r'^\d+[\.\d]*\s*(字节|比特|位|bps|Mbps|Gbps|KB|MB|GB|TB|ms|μs|ns|s|Hz|MHz|GHz|V|A|W|dB|dBm|米|千米|厘米|毫米|微米|纳米)'
    r'|^\d+[\.\d]*\s*[次个种类]'
    r'|^\d+[\.\d]*\s*[字位]节?'
    r'|^\d+[\.\d]*\s*[%％]'
)

# 句式黑名单：包含逻辑连接词或副词的实体，判定为"短语"而非独立实体
PHRASE_BLACKLIST_PATTERN = re.compile(
    r'[后内不每有利于从而则但虽因由与及或并而]'
)

# 白名单：即使匹配了黑名单模式，但属于以下已知技术概念的实体，仍然保留
TECH_WHITELIST = {
    "32位", "64位", "128位", "256位",  # CPU/地址总线位数
    "8位", "16位",                     # 指令集位数
    "10Mbps", "100Mbps", "1Gbps",      # 标准以太网速率
    "10BASE-T", "100BASE-TX",          # 以太网标准
    "802.11", "802.3", "802.1Q",       # IEEE标准
    "IPv4", "IPv6",                    # IP协议版本
    "3G", "4G", "5G", "LTE",           # 移动通信标准
    "2.4GHz", "5GHz",                  # 无线频段
}


def pos_filter_entity(entity_name):
    """
    使用 jieba.posseg 对候选实体进行词性标注，判断是否为有效的名词性实体。
    
    增强策略（第二级防线升级）：
    1. 黑名单拦截：拦截"数字+单位"模式（如"64字节"、"10Mbps"），禁止作为节点
    2. 句式拦截：拦截包含逻辑连接词/副词的实体（如"后"、"内"、"不"、"每"、"有利于"）
    3. 白名单豁免：已知技术概念（如"32位"、"IPv4"、"802.11"）即使匹配黑名单也保留
    4. 原有词性标注过滤逻辑保持不变
    
    返回:
        bool: True 表示是有效的名词性实体，False 表示应过滤掉
    """
    if not entity_name or len(entity_name) < 1:
        return False
    
    # ===== 黑名单拦截：数字+单位模式 =====
    # 检查是否在白名单中（已知技术概念豁免）
    if entity_name in TECH_WHITELIST:
        pass  # 跳过黑名单检查
    elif UNIT_BLACKLIST_PATTERN.search(entity_name):
        return False  # 拦截"数字+单位"模式
    
    # ===== 句式拦截：包含逻辑连接词/副词 =====
    # 检查是否在白名单中
    if entity_name not in TECH_WHITELIST:
        if PHRASE_BLACKLIST_PATTERN.search(entity_name):
            return False  # 拦截包含逻辑连接词的短语
    
    # 对于纯英文缩写/术语（如 "TCP", "HTTP", "IP"），直接保留
    if re.match(r'^[A-Za-z0-9+\-/]+$', entity_name):
        return True
    
    # 对于包含英文/数字的混合实体（如 "TCP协议", "IP地址", "3G网络"），放宽检查
    if re.search(r'[A-Za-z0-9]', entity_name):
        # 对这些实体进行词性标注，只要核心部分是名词就保留
        words = list(pseg.cut(entity_name))
        if words:
            # 检查最后一个词（通常是核心名词，如"协议"、"地址"）
            last_word, last_flag = words[-1]
            if last_flag in NOUN_POS_TAGS:
                return True
            # 如果最后一个词不是名词，检查第一个词
            first_word, first_flag = words[0]
            if first_flag in NOUN_POS_TAGS:
                return True
            # 如果实体整体被标注为名词
            if len(words) == 1 and words[0].flag in NOUN_POS_TAGS:
                return True
            # 如果实体中包含名词性成分
            for _, flag in words:
                if flag in NOUN_POS_TAGS:
                    return True
            return False
    
    # 对纯中文实体进行词性标注
    words = list(pseg.cut(entity_name))
    
    if not words:
        return False
    
    # 情况1：实体整体被标注为一个词
    if len(words) == 1:
        word, flag = words[0]
        # 名词性词性直接保留
        if flag in NOUN_POS_TAGS:
            return True
        # 动词（如"传输"、"发送"、"读取"）在技术概念中常作为名词使用，保留
        if flag.startswith("v"):
            return True
        # ❌ 形容词（如"可靠"、"高效"、"安全"）不是独立的技术概念实体，过滤掉
        return False
    
    # 情况2：实体包含多个词（如"邮件传输协议"、"计算机网络体系结构"）
    # 策略：检查核心词（通常为最后一个词，如"协议"、"结构"）是否为名词
    last_word, last_flag = words[-1]
    if last_flag in NOUN_POS_TAGS:
        return True
    
    # 检查第一个词是否为名词（如"网络层"中的"网络"）
    first_word, first_flag = words[0]
    if first_flag in NOUN_POS_TAGS:
        return True
    
    # 检查是否包含名词性成分
    noun_count = sum(1 for _, flag in words if flag in NOUN_POS_TAGS)
    if noun_count >= 1:
        return True
    
    # 对于长度 >= 3 的实体，如果包含动词性成分，也可能是有意义的技术概念
    if len(entity_name) >= 3:
        verb_count = sum(1 for _, flag in words if flag.startswith("v"))
        if verb_count >= 1:
            return True
    
    return False


def extract_core_noun(entity_name):
    """
    从实体中提取核心名词部分。
    例如：
    - "邮件传输协议" → "协议"（核心名词）
    - "电子邮件地址" → "地址"
    - "网络层" → "网络层"
    - "TCP/IP协议簇" → "协议簇"
    
    返回:
        str: 核心名词，如果无法提取则返回原实体
    """
    if not entity_name:
        return entity_name
    
    # 纯英文/数字术语直接返回
    if re.match(r'^[A-Za-z0-9+\-/]+$', entity_name):
        return entity_name
    
    words = list(pseg.cut(entity_name))
    
    if not words:
        return entity_name
    
    # 如果只有一个词，直接返回
    if len(words) == 1:
        return entity_name
    
    # 从后往前找第一个名词性词作为核心词
    for word, flag in reversed(words):
        if flag in NOUN_POS_TAGS:
            return word
    
    # 如果没找到名词，返回最后一个词
    return words[-1][0]


# ============================================================
# 概念变体归一化 (Concept Variant Normalization)
# 将同一概念的不同表达形式合并为统一的核心概念
# 例如："邮件"、"电子邮件"、"E-mail"、"邮件协议"、"邮件读取"、"邮件发送" → "邮件"
# ============================================================
# 核心概念映射表：将各种变体映射到核心概念
# 格式：{核心概念: [变体列表]}
CORE_CONCEPT_MAP = {
    "邮件": ["电子邮件", "E-mail", "Email", "email", "邮件协议", "邮件读取", "邮件发送", "邮件服务", "邮件系统", "邮件客户端", "邮件服务器", "邮件传输", "邮件地址"],
    "网络": ["互联网", "因特网", "Internet", "internet", "网络系统", "网络结构", "网络体系", "网络技术", "网络协议", "网络层"],
    "数据": ["数据信息", "数据内容", "数据包", "数据帧", "数据报", "数据段", "数据结构", "数据单元", "数据块"],
    "协议": ["通信协议", "网络协议", "协议簇", "协议栈", "协议体系", "协议标准"],
    "传输": ["传送", "传递", "发送", "接收", "通信", "通讯", "数据传输", "信息传输"],
    "安全": ["安全性", "信息安全", "网络安全", "防护", "保护", "加密", "认证", "鉴权"],
    "计算": ["计算机", "运算", "算力", "计算能力", "计算技术", "计算系统"],
    "存储": ["储存", "存储器", "存储设备", "存储系统", "内存", "外存", "缓存"],
    "管理": ["管控", "治理", "管理功能", "管理系统", "管理机制"],
    "控制": ["操控", "调控", "控制机制", "控制系统", "控制协议"],
    "服务": ["服务器", "服务端", "服务系统", "服务功能", "服务机制"],
    "应用": ["应用程序", "应用层", "应用系统", "应用软件", "APP", "App"],
    "接口": ["界面", "交互接口", "API接口", "API", "应用程序接口"],
    "地址": ["IP地址", "IP", "网址", "URL", "地址信息", "地址格式"],
    "路由": ["路由器", "路由协议", "路由表", "路由选择", "路由算法"],
    "交换": ["交换机", "交换技术", "交换网络", "交换设备", "交换方式"],
    "连接": ["链接", "链路", "联接", "互联", "互连", "连接方式"],
    "信号": ["信令", "信号量", "信号传输", "信号处理", "信号编码"],
    "编码": ["解码", "编解码", "编码方式", "编码技术", "编码方案"],
    "压缩": ["解压", "压缩算法", "压缩技术", "压缩比", "数据压缩"],
    "加密": ["解密", "加解密", "加密算法", "加密技术", "加密方式"],
    "认证": ["鉴权", "身份认证", "身份验证", "授权", "权限验证"],
    "错误": ["差错", "误差", "错误检测", "错误纠正", "差错控制", "纠错"],
    "流量": ["数据流", "码流", "比特流", "流量控制", "流量管理"],
    "窗口": ["滑动窗口", "窗口大小", "窗口机制", "窗口控制"],
    "缓存": ["缓冲", "缓冲区", "缓冲机制", "Cache", "cache", "高速缓存"],
    "队列": ["排队", "队列机制", "队列管理", "消息队列"],
    "调度": ["调度算法", "调度机制", "调度策略", "任务调度"],
    "同步": ["异步", "同步机制", "同步方式", "时钟同步"],
    "复用": ["多路复用", "复用技术", "复用方式", "频分复用", "时分复用", "波分复用", "码分复用"],
    "拓扑": ["拓扑结构", "网络拓扑", "拓扑图", "拓扑关系"],
    "帧": ["数据帧", "帧结构", "帧格式", "帧头", "帧尾"],
    "报文": ["数据报文", "报文段", "报文格式", "消息报文"],
    "分组": ["数据分组", "分组交换", "分组格式", "包", "数据包"],
    "宽带": ["带宽", "频宽", "宽带网络", "带宽管理"],
    "无线": ["WiFi", "Wi-Fi", "WLAN", "无线网络", "无线通信", "无线传输"],
    "光纤": ["光缆", "光纤通信", "光纤网络", "光传输"],
    "卫星": ["卫星通信", "卫星网络", "卫星传输"],
    "移动": ["移动通信", "移动网络", "移动互联网", "蜂窝网络", "4G", "5G", "LTE"],
    "以太网": ["Ethernet", "ethernet", "以太网网络", "以太网协议"],
    "虚拟": ["虚拟化", "虚拟机", "虚拟网络", "虚拟专用", "VPN", "VLAN"],
    "云": ["云计算", "云服务", "云存储", "云平台", "Cloud", "cloud"],
    "大数据": ["海量数据", "大数据技术", "大数据分析", "大数据处理"],
    "人工智能": ["AI", "智能", "机器学习", "深度学习", "神经网络", "NLP", "自然语言处理", "计算机视觉", "CV"],
    "软件": ["程序", "软件系统", "软件工程", "软件开发", "软件架构"],
    "硬件": ["硬件设备", "硬件系统", "硬件架构", "物理设备"],
    "操作系统": ["OS", "os", "操作平台", "系统软件", "OSI"],
    "数据库": ["DB", "数据库系统", "数据库管理", "关系数据库", "NoSQL", "SQL"],
    "算法": ["算法设计", "算法分析", "算法实现", "算法优化", "排序算法", "搜索算法"],
    "函数": ["方法", "功能函数", "函数调用", "函数定义"],
    "变量": ["参数", "参数变量", "变量定义", "变量类型"],
    "类": ["类别", "分类", "类型", "种类", "对象类"],
    "对象": ["Object", "object", "实例", "对象实例"],
    "进程": ["线程", "任务", "进程管理", "进程调度", "多线程", "并发"],
    "内存": ["主存", "内存管理", "内存分配", "内存地址", "虚拟内存"],
    "文件": ["文档", "文件系统", "文件管理", "文件格式", "文件类型"],
    "指令": ["命令", "指令集", "指令系统", "指令格式", "操作码"],
    "中断": ["中断处理", "中断机制", "中断向量", "中断请求"],
    "总线": ["系统总线", "数据总线", "地址总线", "控制总线", "总线结构"],
    "接口": ["端口", "接口类型", "接口标准", "接口规范", "通信接口"],
    "设备": ["装置", "器件", "设备管理", "设备驱动", "外围设备", "外设"],
    "模块": ["组件", "部件", "模块化", "模块设计", "功能模块"],
    "架构": ["体系结构", "框架", "架构设计", "系统架构", "软件架构"],
    "引擎": ["驱动", "驱动引擎", "搜索引擎", "推荐引擎"],
    "平台": ["平台系统", "平台架构", "平台服务", "技术平台"],
    "工具": ["工具软件", "开发工具", "调试工具", "测试工具", "辅助工具"],
    "测试": ["检测", "测试方法", "测试技术", "测试用例", "自动化测试"],
    "优化": ["优化算法", "优化技术", "优化方法", "性能优化", "调优"],
    "配置": ["配置管理", "配置信息", "配置参数", "配置项"],
    "部署": ["发布", "上线", "部署方式", "部署架构", "持续部署"],
    "监控": ["监测", "监控系统", "监控工具", "性能监控", "日志监控"],
    "日志": ["日志记录", "日志文件", "日志系统", "日志分析"],
    "备份": ["容灾", "备份恢复", "数据备份", "灾备"],
    "负载": ["负载均衡", "负载平衡", "负载管理", "负载调度"],
    "容错": ["容错机制", "容错技术", "故障恢复", "故障转移", "高可用"],
    "分布式": ["分布系统", "分布式系统", "分布式计算", "分布式存储", "分布式架构"],
    "集群": ["集群系统", "集群管理", "集群部署", "服务器集群"],
    "容器": ["Docker", "docker", "容器化", "容器技术", "容器编排", "Kubernetes", "k8s"],
    "微服务": ["微服务架构", "微服务设计", "微服务治理", "服务网格"],
    "区块链": ["Blockchain", "blockchain", "区块链技术", "分布式账本", "智能合约"],
    "物联网": ["IoT", "iot", "物联网络", "传感网络", "RFID"],
    "边缘计算": ["边缘节点", "边缘网络", "边缘智能", "边缘设备"],
    "AR": ["增强现实", "Augmented Reality", "augmented reality"],
    "VR": ["虚拟现实", "Virtual Reality", "virtual reality"],
    "元宇宙": ["Metaverse", "metaverse", "虚拟世界", "数字孪生"],
}


def normalize_concept_variant(entity_name):
    """
    概念变体归一化：将同一概念的不同表达形式映射到核心概念。
    
    例如：
    - "电子邮件" → "邮件"
    - "E-mail" → "邮件"
    - "邮件协议" → "邮件"
    - "邮件读取" → "邮件"
    - "邮件发送" → "邮件"
    - "IP地址" → "地址"
    - "滑动窗口" → "窗口"
    
    策略：
    1. 先检查是否已存在于 SYNONYM_DICT 中（优先使用已有的同义词映射）
    2. 遍历 CORE_CONCEPT_MAP，检查实体名称是否包含核心概念或其变体
    3. 如果匹配到多个核心概念，选择匹配度最高的（最长匹配优先）
    
    返回:
        str: 归一化后的核心概念名称
    """
    if not entity_name:
        return entity_name
    
    name = entity_name.strip()
    
    # 先检查已有的同义词映射
    normalized = normalize_entity_name(name)
    if normalized != name:
        return normalized
    
    # 遍历核心概念映射表，寻找最佳匹配
    best_match = None
    best_match_len = 0
    
    for core_concept, variants in CORE_CONCEPT_MAP.items():
        # 检查实体是否直接等于核心概念
        if name == core_concept:
            return core_concept
        
        # 检查实体是否等于某个变体
        for variant in variants:
            if name == variant:
                return core_concept
        
        # 检查实体是否包含核心概念（如"邮件协议"包含"邮件"）
        if core_concept in name and len(core_concept) > best_match_len:
            best_match = core_concept
            best_match_len = len(core_concept)
        
        # 检查实体是否包含某个变体（如"电子邮件地址"包含"电子邮件"）
        for variant in variants:
            if variant in name and len(variant) > best_match_len:
                best_match = core_concept
                best_match_len = len(variant)
    
    # 如果找到匹配的核心概念，返回它
    if best_match:
        return best_match
    
    return name


def merge_concept_variants_in_graph(G):
    """
    在图谱中合并概念变体节点。
    将同一核心概念的不同变体（如"电子邮件"、"E-mail"、"邮件协议"）合并到核心概念（如"邮件"）。
    
    返回:
        nx.Graph: 合并后的图
    """
    nodes = list(G.nodes())
    merge_map = {}
    
    for node in nodes:
        node_str = str(node).strip()
        # 对每个节点进行概念变体归一化
        core_concept = normalize_concept_variant(node_str)
        if core_concept != node_str:
            merge_map[node] = core_concept
    
    # 执行合并
    for old_name, new_name in merge_map.items():
        if old_name == new_name:
            continue
        if old_name not in G:
            continue
        
        # 如果新名称不在图中，添加它
        if new_name not in G:
            G.add_node(new_name)
        
        # 将旧节点的所有边迁移到新节点
        for neighbor in list(G.neighbors(old_name)):
            edge_data = G.get_edge_data(old_name, neighbor)
            if edge_data:
                new_label = edge_data.get("label", "相关")
                # 如果新节点和邻居之间已有边，使用智能熔断器合并关系标签
                if G.has_edge(new_name, neighbor):
                    existing_label = G[new_name][neighbor].get("label", "")
                    G[new_name][neighbor]["label"] = merge_edge_labels(existing_label, new_label)
                    existing_sources = G[new_name][neighbor].setdefault("source_chunks", [])
                    existing_sources.extend(edge_data.get("source_chunks", []))
                else:
                    G.add_edge(new_name, neighbor, **edge_data)
        
        # 删除旧节点
        G.remove_node(old_name)
    
    return G


# ============================================================
# 第一级防线：前端文本预清洗 (Frontend Text Pre-cleaning)
# ============================================================

def preclean_text(text):
    """
    在将文本喂给 LLM 之前，使用正则删除噪音：
    - 纯页码（如 "12" 单独一行）
    - 章节编号（如 "3.1 节"、"第1章" 等孤立标题）
    - 断开的残句（长度 < 5 的孤立行）
    - 图片/表格引用（如 "如图1-1所示"、"见表2-3"）
    - 年份数字（如 "1989年"）
    - 步骤编号（如 "步骤二"、"Step 1"）
    - 示例代码块、数学公式
    - 参考文献引用
    - URL链接
    - 表格数据行

    ⚠️ 重点保护：文档中的加粗内容（如 **TCP协议**、**三次握手** 等）
    通常是重要的核心概念，不会被误删。具体保护策略：
    1. 包含加粗标记 **...** 的行，即使长度 < 5 也保留
    2. 包含加粗标记 **...** 的行，不会被当作纯符号行删除
    3. 包含加粗标记 **...** 的行，不会被当作代码行误删
    4. 提取并保留加粗内容中的核心术语，用于后续实体识别
    """
    # ⚠️ 第〇步：PDF/DOCX 提取文本的预处理修复
    # 修复常见的PDF提取问题：连字符换行（如 "计算-\\n机网络" → "计算机网络"）
    text = re.sub(r'(\w)-\s*\n\s*(\w)', r'\1\2', text)
    # 修复英文单词的连字符换行
    text = re.sub(r'([a-zA-Z])-\s*\n\s*([a-zA-Z])', r'\1\2', text)
    # 删除多余的空白字符（多个空格合并为一个）
    text = re.sub(r'[ \t]+', ' ', text)
    # 删除乱码字符（保留中文、英文、数字、常见标点）
    text = re.sub(r'[^\u4e00-\u9fff\u3000-\u303f\uff00-\uffefa-zA-Z0-9\s\.\,\;\:\!\?\-\+\=\/\(\)\[\]\{\}\*\#\@\&\$\'\"【】《》「」、，。！？：；""''（）]', '', text)

    # ⚠️ 第一步：预先提取并保护加粗内容
    # 在清洗前先扫描所有加粗内容，将其加入保护列表
    bold_terms = set()
    for m in re.finditer(r'\*\*(.+?)\*\*', text):
        term = m.group(1).strip()
        if term and len(term) >= 2:
            bold_terms.add(term)
    # 同时检测【】标记的重点内容
    for m in re.finditer(r'【(.+?)】', text):
        term = m.group(1).strip()
        if term and len(term) >= 2:
            bold_terms.add(term)

    lines = text.split("\n")
    cleaned_lines = []
    for line in lines:
        line = line.strip()
        if not line:
            continue

        # ⚠️ 保护规则：包含加粗内容的行，跳过所有删除检查，直接保留
        has_bold = bool(re.search(r'\*\*.+?\*\*', line))
        # 也保护包含【】的重点标记内容
        has_emphasis = bool(re.search(r'【.+?】', line))

        # 删除纯数字行（页码）
        if re.match(r"^\d+$", line):
            if not has_bold:
                continue
        # 删除纯数字+标点行（如 "12."、"12、"）
        if re.match(r"^\d+[\.、,，。]?\s*$", line):
            if not has_bold:
                continue
        # 删除孤立章节编号（如 "3.1"、"3.1.1"）
        if re.match(r"^\d+(\.\d+)+\s*[节章]?\s*$", line):
            if not has_bold:
                continue
        # 删除"第X章"、"第X节"等孤立标题行（如果长度很短且没有其他内容）
        if re.match(
            r"^第\s*[零一二三四五六七八九十百千万0-9]+\s*[章篇节部分]\s*$", line
        ):
            if not has_bold:
                continue
        # 删除"步骤X"、"Step X"等
        if re.match(
            r"^(步骤|Step|步骤\s*[一二三四五六七八九十0-9])", line, re.IGNORECASE
        ):
            if not has_bold:
                continue
        # 删除过短的行（残句）
        if len(line) < 5:
            if not has_bold and not has_emphasis:
                continue
        # 删除明显的代码行（包含等号、分号、大括号等代码特征）
        # ⚠️ 优化：只有同时包含代码关键字和符号特征的行才判定为代码
        if re.match(r"^.*[=;{}\[\]\(\)].*$", line) and len(line) < 100:
            if has_bold or has_emphasis:
                cleaned_lines.append(line)
                continue
            # 必须同时包含代码关键字才判定为代码行，避免误删正常文本
            code_patterns = [
                r"\bint\b", r"\bfloat\b", r"\bchar\b", r"\bvoid\b",
                r"\breturn\b", r"\bif\b", r"\belse\b", r"\bfor\b",
                r"\bwhile\b", r"\bprintf\b", r"\bscanf\b",
                r"#include", r"import ", r"def ", r"class ", r"public ",
                r"private ", r"static ", r"void ", r"struct ", r"typedef ",
            ]
            # 统计代码特征
            code_match_count = 0
            for pattern in code_patterns:
                if re.search(pattern, line, re.IGNORECASE):
                    code_match_count += 1
            if code_match_count < 2:
                cleaned_lines.append(line)
                continue
            else:
                continue  # 跳过代码行
        
        # 删除图片/表格引用（如 "如图1-1所示"、"见表2-3"）
        if re.search(r'[如见]图\d+[-_]\d+', line) or re.search(r'[如见]表\d+[-_]\d+', line):
            if not has_bold and not has_emphasis:
                continue
        
        # 删除参考文献引用（如 "[1]"、"［1］"）
        if re.match(r'^\[\d+\]', line) or re.match(r'^［\d+］', line):
            if not has_bold:
                continue
        
        # 删除URL链接
        if re.match(r'^https?://', line):
            continue
        
        # 删除纯符号行（如 "--------"、"******"）
        if re.match(r'^[\*\-=\~\#\s]+$', line):
            if not has_bold:
                continue
        
        cleaned_lines.append(line)
    
    cleaned_text = "\n".join(cleaned_lines)
    
    # ⚠️ 第二步：删除连续的重复行（PDF提取常见问题）
    lines_after_dedup = []
    prev_line = ""
    for line in cleaned_text.split("\n"):
        if line.strip() != prev_line.strip():
            lines_after_dedup.append(line)
        prev_line = line
    cleaned_text = "\n".join(lines_after_dedup)
    
    # ⚠️ 第三步：删除过长的段落（可能是PDF提取的乱码合并）
    final_lines = []
    for line in cleaned_text.split("\n"):
        if len(line) > 2000 and not re.search(r'[\u4e00-\u9fff]', line):
            continue
        final_lines.append(line)
    cleaned_text = "\n".join(final_lines)
    
    return cleaned_text



def normalize_entity_name(name):
    """实体名称归一化：将同义实体映射为标准名称"""
    if not name:
        return name
    name = name.strip()
    if name in SYNONYM_DICT:
        return SYNONYM_DICT[name]
    name_lower = name.lower()
    for key, value in SYNONYM_DICT.items():
        if key.lower() == name_lower:
            return value
    return name


def _render_empty_graph_html(title, message):
    """渲染空图谱占位HTML"""
    return f"""<!DOCTYPE html>
<html style="height: 100%">
<head><meta charset="utf-8">
<style>
body {{ height: 100%; margin: 0; display: flex; justify-content: center; align-items: center; background-color: #fafafa; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }}
.empty-state {{ text-align: center; color: #999; }}
.empty-state h3 {{ font-size: 24px; margin-bottom: 10px; color: #666; }}
.empty-state p {{ font-size: 16px; color: #999; }}
</style></head>
<body><div class="empty-state"><h3>📭 {title}</h3><p>{message}</p></div></body>
</html>"""


def render_echarts_hierarchical_graph(tree_data):
    """
    渲染层次知识图谱（树形结构），样式与章节知识图谱一致。
    使用 ECharts 树图布局，包含工具栏、交互式图例、路径提示等。
    """
    import json

    if tree_data is None or (isinstance(tree_data, dict) and not tree_data.get("name")):
        return _render_empty_graph_html("📂 层次知识图谱", "暂无层次图谱数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    def add_path(node, path_parts=None):
        if path_parts is None:
            path_parts = []
        node_name = node.get("name", "")
        current_path = path_parts + [node_name]
        node["_path"] = " → ".join(current_path)
        for child in node.get("children", []):
            add_path(child, current_path)

    def assign_colors(node, level=0):
        color_idx = min(level, 4)
        node["itemStyle"] = {"color": neo4j_palette[color_idx], "borderColor": "#e0e0e0", "borderWidth": 1}
        for child in node.get("children", []):
            assign_colors(child, level + 1)

    colored_tree = json.loads(json.dumps(tree_data, ensure_ascii=False))
    assign_colors(colored_tree)
    add_path(colored_tree)

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .custom-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .custom-title h2 { margin: 0; font-size: 24px; letter-spacing: 1px; font-weight: 700; }
        .custom-title p { margin: 8px 0 0 0; font-size: 14px; color: #666; }
        .custom-legend { position: absolute; bottom: 35px; left: 50%; transform: translateX(-50%); display: flex; gap: 22px; background: #ffffff; padding: 12px 28px; border-radius: 12px; border: 1px solid #e0e0e0; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08); color: #444; font-size: 14px; z-index: 10; cursor: pointer; }
        .legend-item { display: flex; align-items: center; gap: 8px; user-select: none; transition: opacity 0.2s ease; }
        .legend-item:hover { opacity: 0.7; }
        .legend-color { width: 14px; height: 14px; border-radius: 50%; }
        .legend-item.inactive { opacity: 0.35; filter: grayscale(100%); }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="custom-title"><h2>知识图谱全景</h2><p>层次结构自动可视化图谱</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <div class="custom-legend" id="legend">
        <div class="legend-item" data-level="0"><div class="legend-color" style="background-color: #68BDF6;"></div><span>核心知识点</span></div>
        <div class="legend-item" data-level="1"><div class="legend-color" style="background-color: #6DCE9E;"></div><span>一级章/讲</span></div>
        <div class="legend-item" data-level="2"><div class="legend-color" style="background-color: #FFD86E;"></div><span>二级节</span></div>
        <div class="legend-item" data-level="3"><div class="legend-color" style="background-color: #FF9800;"></div><span>三级知识点</span></div>
        <div class="legend-item" data-level="4"><div class="legend-color" style="background-color: #E91E63;"></div><span>深层细节</span></div>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var originalTreeData = JSON.parse(JSON.stringify(__JSON_DATA_PLACEHOLDER__));

        function markLevels(node, level) {
            if (!node) return;
            node._level = level || 0;
            if (node.children && node.children.length > 0) {
                node.children.forEach(function(child) { markLevels(child, (level || 0) + 1); });
            }
        }

        function filterTreeByLevels(node, activeLevelsSet) {
            if (!node) return null;
            var currentLevel = node._level !== undefined ? node._level : 0;
            var checkLevel = currentLevel >= 4 ? 4 : currentLevel;
            if (currentLevel !== 0 && !activeLevelsSet.has(checkLevel)) { return null; }
            var newNode = JSON.parse(JSON.stringify(node));
            if (newNode.children && newNode.children.length > 0) {
                newNode.children = newNode.children.map(function(child) { return filterTreeByLevels(child, activeLevelsSet); }).filter(Boolean);
            }
            return newNode;
        }

        markLevels(originalTreeData);

        var option = {
            backgroundColor: '#ffffff',
            tooltip: {
                trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1,
                textStyle: { color: '#444', fontSize: 14 }, padding: [10, 14],
                extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;',
                formatter: function(params) {
                    if (params.data && params.data._path) {
                        return '📌 <b>' + params.data.name + '</b><br/>🔗 路径: ' + params.data._path;
                    }
                    return params.name;
                }
            },
            series: [{
                type: 'tree', data: [originalTreeData], layout: 'radial', radius: '72%', roam: true,
                symbol: 'circle',
                lineStyle: { width: 1.5, curveness: 0.1, color: '#A5ABB6', opacity: 0.8 },
                label: { position: 'right', align: 'left', fontSize: 13, color: '#444', distance: 10, rotate: 0, overflow: 'truncate', width: 180 },
                expandAndCollapse: true, animationDuration: 800, animationDurationUpdate: 500, animationEasing: 'cubicOut', initialTreeDepth: 3,
                emphasis: { focus: 'ancestor', lineStyle: { width: 2.5, color: '#A5ABB6', opacity: 1 }, itemStyle: { shadowBlur: 12, shadowColor: 'rgba(0,0,0,0.3)' } },
                levels: [
                    { symbolSize: 52, label: { fontSize: 16, fontWeight: 'bold', color: '#444' } },
                    { symbolSize: 40, label: { fontSize: 15, fontWeight: '600', color: '#444' } },
                    { symbolSize: 32, label: { fontSize: 14, color: '#444' } },
                    { symbolSize: 26, label: { fontSize: 13, color: '#444' } },
                    { symbolSize: 20, label: { fontSize: 13, color: '#666' } }
                ]
            }]
        };

        myChart.setOption(option);

        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }

        var legendItems = document.querySelectorAll('.legend-item');
        var activeLevels = new Set([0, 1, 2, 3, 4]);
        legendItems.forEach(function(item) {
            item.addEventListener('click', function() {
                var level = parseInt(this.getAttribute('data-level'));
                if (activeLevels.has(level)) { activeLevels.delete(level); this.classList.add('inactive'); }
                else { activeLevels.add(level); this.classList.remove('inactive'); }
                var filteredData = filterTreeByLevels(originalTreeData, activeLevels);
                option.series[0].data = [filteredData];
                myChart.setOption(option, true);
            });
        });

        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    return html_template.replace("__JSON_DATA_PLACEHOLDER__", json.dumps(colored_tree, ensure_ascii=False))


def render_echarts_global_graph(G):
    """
    使用 ECharts 渲染全局/章节知识图谱（力导向图）。
    支持工具栏、关系标签、箭头、交互式图例等。
    """
    import json

    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🌐 知识图谱", "暂无图谱数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    nodes = []
    node_name_to_id = {}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        # All nodes can have the same degree (for example a two-node graph).
        # Keep a stable fallback size instead of dividing by a zero range.
        degree_range = max_degree - min_degree
        size = 15 + 35 * (degree - min_degree) / degree_range if degree_range else 25
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        nodes.append({
            "id": i, "name": str(node), "symbolSize": size,
            "itemStyle": {"color": neo4j_palette[color_idx], "borderColor": "#fff", "borderWidth": 2, "shadowBlur": 6, "shadowColor": "rgba(0,0,0,0.15)"},
            "label": {"show": True, "fontSize": 12, "color": "#444", "fontWeight": "bold"},
            "degree": degree, "category": color_idx
        })

    edges = []
    edge_label_set = set()
    for u, v, data in G.edges(data=True):
        label = data.get("label", "相关") if data else "相关"
        source_id = node_name_to_id.get(str(u))
        target_id = node_name_to_id.get(str(v))
        if source_id is not None and target_id is not None:
            edge_key = f"{source_id}-{target_id}"
            if edge_key not in edge_label_set:
                edge_label_set.add(edge_key)
                edges.append({
                    "source": source_id, "target": target_id,
                    "label": {"show": True, "formatter": label, "fontSize": 10, "color": "#666",
                              "backgroundColor": "rgba(255,255,255,0.8)", "padding": [2, 6],
                              "borderRadius": 4, "borderColor": "#e0e0e0", "borderWidth": 1},
                    "lineStyle": {"color": "#A5ABB6", "width": 1.5, "opacity": 0.7, "curveness": 0.1}
                })

    categories = [
        {"name": "核心概念", "itemStyle": {"color": neo4j_palette[0]}},
        {"name": "重要概念", "itemStyle": {"color": neo4j_palette[1]}},
        {"name": "一般概念", "itemStyle": {"color": neo4j_palette[2]}},
        {"name": "次要概念", "itemStyle": {"color": neo4j_palette[3]}},
        {"name": "细节概念", "itemStyle": {"color": neo4j_palette[4]}},
    ]

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
        .graph-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .graph-title h2 { margin: 0; font-size: 22px; letter-spacing: 1px; font-weight: 700; }
        .graph-title p { margin: 6px 0 0 0; font-size: 13px; color: #888; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="graph-title"><h2>知识图谱全景</h2><p>实体关系自动可视化图谱</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var nodes = __NODES_DATA__;
        var edges = __EDGES_DATA__;
        var categories = __CATEGORIES_DATA__;
        var option = {
            backgroundColor: '#ffffff',
            tooltip: { trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1, textStyle: { color: '#444', fontSize: 13 }, padding: [8, 12], extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;', formatter: function(params) { if (params.dataType === 'node') { return '<b>' + params.name + '</b><br/>关联数量: ' + (params.data.degree || 0); } return params.name; } },
            series: [{ type: 'graph', layout: 'force', force: { repulsion: 500, edgeLength: [150, 300], gravity: 0.1, friction: 0.1, layoutAnimation: true }, roam: true, draggable: true, data: nodes, edges: edges, categories: categories, focusNodeAdjacency: true, edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 10], lineStyle: { color: 'source', width: 1.5, opacity: 0.7, curveness: 0.1 }, label: { show: true, position: 'right', fontSize: 12, color: '#444', fontWeight: 'bold' }, emphasis: { focus: 'adjacency', lineStyle: { width: 3, opacity: 1 } }, animation: true, animationDuration: 1000, animationEasing: 'cubicOut' }]
        };
        myChart.setOption(option);
        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }
        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    html = html_template.replace("__NODES_DATA__", json.dumps(nodes, ensure_ascii=False))
    html = html.replace("__EDGES_DATA__", json.dumps(edges, ensure_ascii=False))
    html = html.replace("__CATEGORIES_DATA__", json.dumps(categories, ensure_ascii=False))
    return html


def render_learning_path_echarts(G, target_node=None):
    """使用 ECharts 渲染学习路径图（力导向图）。"""
    import json

    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🗺️ 学习路径", "暂无学习路径数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    nodes = []
    node_name_to_id = {}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        # All nodes can have the same degree (for example a two-node graph).
        # Keep a stable fallback size instead of dividing by a zero range.
        degree_range = max_degree - min_degree
        size = 15 + 35 * (degree - min_degree) / degree_range if degree_range else 25
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        is_target = target_node and str(node) == target_node
        item_style = {"color": "#E91E63" if is_target else neo4j_palette[color_idx], "borderColor": "#fff", "borderWidth": 2, "shadowBlur": 12 if is_target else 6, "shadowColor": "rgba(233,30,99,0.4)" if is_target else "rgba(0,0,0,0.15)"}
        nodes.append({
            "id": i, "name": str(node), "symbolSize": 35 if is_target else size, "itemStyle": item_style,
            "label": {"show": True, "fontSize": 13 if is_target else 11, "color": "#E91E63" if is_target else "#444", "fontWeight": "bold" if is_target else "normal"},
            "degree": degree, "category": color_idx
        })

    edges = []
    edge_label_set = set()
    for u, v, data in G.edges(data=True):
        label = data.get("label", "相关") if data else "相关"
        source_id = node_name_to_id.get(str(u))
        target_id = node_name_to_id.get(str(v))
        if source_id is not None and target_id is not None:
            edge_key = f"{source_id}-{target_id}"
            if edge_key not in edge_label_set:
                edge_label_set.add(edge_key)
                edges.append({
                    "source": source_id, "target": target_id,
                    "label": {"show": True, "formatter": label, "fontSize": 10, "color": "#666",
                              "backgroundColor": "rgba(255,255,255,0.8)", "padding": [2, 6],
                              "borderRadius": 4, "borderColor": "#e0e0e0", "borderWidth": 1},
                    "lineStyle": {"color": "#A5ABB6", "width": 1.5, "opacity": 0.7, "curveness": 0.1}
                })

    categories = [
        {"name": "核心概念", "itemStyle": {"color": neo4j_palette[0]}},
        {"name": "重要概念", "itemStyle": {"color": neo4j_palette[1]}},
        {"name": "一般概念", "itemStyle": {"color": neo4j_palette[2]}},
        {"name": "次要概念", "itemStyle": {"color": neo4j_palette[3]}},
        {"name": "细节概念", "itemStyle": {"color": neo4j_palette[4]}},
    ]

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
        .graph-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .graph-title h2 { margin: 0; font-size: 22px; letter-spacing: 1px; font-weight: 700; }
        .graph-title p { margin: 6px 0 0 0; font-size: 13px; color: #888; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="graph-title"><h2>学习路径图</h2><p>知识依赖关系可视化</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var nodes = __NODES_DATA__;
        var edges = __EDGES_DATA__;
        var categories = __CATEGORIES_DATA__;
        var option = {
            backgroundColor: '#ffffff',
            tooltip: { trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1, textStyle: { color: '#444', fontSize: 13 }, padding: [8, 12], extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;', formatter: function(params) { if (params.dataType === 'node') { return '<b>' + params.name + '</b><br/>关联数量: ' + (params.data.degree || 0); } return params.name; } },
            series: [{ type: 'graph', layout: 'force', force: { repulsion: 500, edgeLength: [150, 300], gravity: 0.1, friction: 0.1, layoutAnimation: true }, roam: true, draggable: true, data: nodes, edges: edges, categories: categories, focusNodeAdjacency: true, edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 10], lineStyle: { color: 'source', width: 1.5, opacity: 0.7, curveness: 0.1 }, label: { show: true, position: 'right', fontSize: 12, color: '#444', fontWeight: 'bold' }, emphasis: { focus: 'adjacency', lineStyle: { width: 3, opacity: 1 } }, animation: true, animationDuration: 1000, animationEasing: 'cubicOut' }]
        };
        myChart.setOption(option);
        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }
        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    html = html_template.replace("__NODES_DATA__", json.dumps(nodes, ensure_ascii=False))
    html = html.replace("__EDGES_DATA__", json.dumps(edges, ensure_ascii=False))
    html = html.replace("__CATEGORIES_DATA__", json.dumps(categories, ensure_ascii=False))
    return html


def extract_triplets_with_llm(text):
    """
    使用 LLM 从文本中提取知识三元组（实体-关系-实体）。
    
    参数:
        text (str): 输入文本内容
    
    返回:
        list: 三元组列表，每个三元组为 [实体1, 关系, 实体2] 格式
    """
    if not text or len(text.strip()) < 20:
        return []
    
    # 截取文本前2000字符以避免超出上下文
    input_text = text[:2000]
    
    prompt = f"""请从以下文本中提取关键的知识三元组（实体-关系-实体），用于构建知识图谱。

要求：
1. 提取重要的概念、术语、定义、功能、特性等实体及其关系
2. 关系词请使用以下标准关系之一：包含、属于、定义、功能、具有、导致、依赖、相关、应用、示例、对比、优势、劣势
3. 每个三元组格式为：["实体1", "关系", "实体2"]
4. 尽量提取高质量、有意义的三元组
5. 避免提取过于宽泛或模糊的关系
6. 只保留对理解本章核心知识有帮助的实体和关系；忽略目录页码、作者/读者、泛化的“内容/问题/过程”等词
7. 忽略仅用于举例、类比、历史背景或重复解释的内容，除非它们定义了核心概念或关键依赖
8. 实体必须是术语、概念、协议、算法、组件或明确的知识对象，不能是完整句子

文本内容：
{input_text}

请以JSON格式输出，格式为：{{"triples": [["实体1", "关系", "实体2"], ...]}}
"""
    try:
        response = call_llm(prompt)
        # 尝试解析 JSON
        try:
            result = json.loads(response)
            triples = result.get("triples", [])
            if triples:
                return triples
        except:
            pass
        
        # 尝试从文本中提取 JSON
        json_match = re.search(r'\{.*"triples".*\}', response, re.DOTALL)
        if json_match:
            try:
                result = json.loads(json_match.group())
                triples = result.get("triples", [])
                if triples:
                    return triples
            except:
                pass
        
        return []
    except Exception as e:
        print(f"extract_triplets_with_llm 出错: {e}")
        return []


def build_hierarchical_graph(full_text, file_name):
    """
    构建层次知识图谱（树形结构）。
    使用 LLM 从文档中提取层次化的章节结构。
    
    参数:
        full_text (str): 文档全文
        file_name (str): 文件名
    
    返回:
        dict: 树形结构数据，格式为 {"name": "...", "children": [...]}
    """
    if not full_text or len(full_text.strip()) < 50:
        return {"name": file_name, "children": []}
    
    # 截取文本前6000字符
    input_text = full_text[:6000]
    
    prompt = f"""请从以下教材/文档文本中提取其大纲结构，构建一个层次化的知识树。

要求：
1. 提取文档的章、节、子节等层次结构
2. 每个节点包含 name 字段
3. 子节点放在 children 数组中
4. 根节点名称为文档标题或"{file_name}"

文本内容：
{input_text}

请以JSON格式输出，格式示例：
{{"name": "文档标题", "children": [{{"name": "第1章 概述", "children": [{{"name": "1.1 背景"}}, {{"name": "1.2 目的"}}]}}, {{"name": "第2章 方法", "children": []}}]}}
"""
    try:
        response = call_llm(prompt)
        # 尝试解析 JSON
        try:
            result = json.loads(response)
            if isinstance(result, dict) and "name" in result:
                return result
        except:
            pass
        
        # 尝试从文本中提取 JSON
        json_match = re.search(r'\{.*"name".*\}', response, re.DOTALL)
        if json_match:
            try:
                result = json.loads(json_match.group())
                if isinstance(result, dict) and "name" in result:
                    return result
            except:
                pass
        
        return {"name": file_name, "children": []}
    except Exception as e:
        print(f"build_hierarchical_graph 出错: {e}")
        return {"name": file_name, "children": []}


def build_knowledge_graph(split_docs):
    """
    构建全局知识图谱。
    使用 LLM 从文档片段中提取三元组，构建 NetworkX 图。
    
    参数:
        split_docs (list): 切分后的文档片段列表（每个元素有 page_content 属性）
    
    返回:
        nx.Graph: 构建的知识图谱
    """
    G = nx.Graph()
    
    if not split_docs:
        return G
    
    # 取前10个文档片段进行三元组提取
    sample_docs = split_docs[:10]
    
    for doc in sample_docs:
        text = doc.page_content if hasattr(doc, 'page_content') else str(doc)
        triples = extract_triplets_with_llm(text)
        
        for triplet in triples:
            if isinstance(triplet, list) and len(triplet) == 3:
                subj = normalize_entity_name(triplet[0].strip())
                rel = normalize_relation(triplet[1].strip())
                obj = normalize_entity_name(triplet[2].strip())
                if subj and obj and rel:
                    G.add_edge(subj, obj, label=rel)
    
    # 合并概念变体
    G = merge_concept_variants_in_graph(G)
    
    return G


def retrieve_from_graph(query, G, top_k=10):
    """
    从知识图谱中检索与查询相关的节点。
    
    使用基于关键词匹配和节点关联度的方法，从 NetworkX 图中检索与查询最相关的节点。
    
    参数:
        query (str): 用户查询文本
        G (nx.Graph): 知识图谱 NetworkX 图对象
        top_k (int): 返回的最相关节点数量，默认 10
    
    返回:
        list: 相关节点的文本内容列表
    """
    if G is None or G.number_of_nodes() == 0:
        return []
    
    import jieba
    import re
    
    # 对查询进行分词
    query_words = set(jieba.lcut(query))
    # 过滤掉单字和无意义的词
    query_words = {w for w in query_words if len(w) > 1}
    
    if not query_words:
        return []
    
    # 计算每个节点与查询的相关性得分
    node_scores = {}
    for node in G.nodes():
        node_str = str(node)
        score = 0
        
        # 1. 关键词匹配得分
        for word in query_words:
            if word in node_str:
                score += 2  # 节点名称包含查询词，加2分
            # 检查节点名称中的词是否与查询词匹配
            node_words = set(jieba.lcut(node_str))
            for nw in node_words:
                if len(nw) > 1 and nw == word:
                    score += 1
        
        # 2. 关联节点加分（如果邻居节点也匹配查询，当前节点也加分）
        if score > 0:
            for neighbor in G.neighbors(node):
                neighbor_str = str(neighbor)
                for word in query_words:
                    if word in neighbor_str:
                        score += 0.5  # 邻居匹配，当前节点加0.5分
        
        if score > 0:
            node_scores[node] = score
    
    # 按得分排序，取 top_k
    sorted_nodes = sorted(node_scores.items(), key=lambda x: x[1], reverse=True)
    result_nodes = [str(node) for node, _ in sorted_nodes[:top_k]]
    
    return result_nodes


def _select_graph_source_text(cleaned_text, max_chars=6000):
    """Select knowledge-dense sentences before the LLM extraction stage."""
    if not cleaned_text:
        return ""

    definition_markers = (
        "是", "指", "称为", "定义", "包括", "由", "用于", "能够", "特点",
        "作用", "功能", "原理", "关键", "核心", "因此", "总结", "注意",
    )
    lines = [line.strip() for line in cleaned_text.splitlines() if line.strip()]
    scored = []
    for index, line in enumerate(lines):
        if len(line) < 12:
            continue
        score = 0
        if re.match(r"^(第[一二三四五六七八九十百0-9]+章|[0-9]+(?:\.[0-9]+)*[、. ]|[一二三四五六七八九十]+、)", line):
            score += 3
        if any(marker in line for marker in definition_markers):
            score += 2
        if "**" in line or "：" in line or ":" in line:
            score += 1
        if len(line) > 500:
            score -= 1
        scored.append((score, index, line))

    if not scored:
        return cleaned_text[:max_chars]

    # Keep original order after ranking, so definitions and their context stay
    # together instead of presenting the model with a shuffled document.
    selected = [item for item in scored if item[0] > 0]
    if not selected:
        selected = sorted(scored, reverse=True)[:12]
    selected.sort(key=lambda item: item[1])

    output = []
    size = 0
    for _, _, line in selected:
        if size + len(line) + 1 > max_chars:
            break
        output.append(line)
        size += len(line) + 1
    return "\n".join(output) or cleaned_text[:max_chars]


def _filter_important_triples(triples, max_triples=120):
    """Normalize and reject malformed, overly broad, or sentence-like triples."""
    if not isinstance(triples, list):
        return []

    filtered = []
    seen = set()
    for triple in triples:
        if not isinstance(triple, (list, tuple)) or len(triple) != 3:
            continue
        if not all(isinstance(value, str) for value in triple):
            continue
        subj, rel, obj = (value.strip() for value in triple)
        subj = normalize_entity_name(subj)
        obj = normalize_entity_name(obj)
        rel = normalize_relation(rel)
        if not subj or not obj or not rel or subj == obj:
            continue
        if len(subj) < 2 or len(obj) < 2 or len(subj) > 60 or len(obj) > 60:
            continue
        if any(mark in subj or mark in obj for mark in ("\n", "。", "！", "？", "；", ",", "，")):
            continue
        if len(rel) > 20:
            continue
        key = (subj.casefold(), rel.casefold(), obj.casefold())
        if key in seen:
            continue
        seen.add(key)
        filtered.append((subj, rel, obj))
        if len(filtered) >= max_triples:
            break
    return filtered


def _split_graph_source_text(text, chunk_size=1800, overlap=200):
    """Split selected source text while preserving a small context overlap."""
    if not text:
        return []
    chunks = []
    start = 0
    text_length = len(text)
    while start < text_length:
        end = min(start + chunk_size, text_length)
        if end < text_length:
            boundary = max(text.rfind("\n", start, end), text.rfind("。", start, end))
            if boundary > start + chunk_size // 2:
                end = boundary + 1
        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= text_length:
            break
        start = max(start + 1, end - overlap)
    return chunks


def _infer_source_page(text):
    """Return a page number when the source text contains an explicit marker."""
    match = re.search(r"(?:第\s*)?(\d+)\s*页|\bp\.?\s*(\d+)\b", text, re.IGNORECASE)
    if not match:
        return None
    return int(next(group for group in match.groups() if group is not None))


def _build_chunked_chapter_graph(candidate_text, progress_callback=None):
    """Extract each source chunk independently and merge provenance-aware edges."""
    chunks = _split_graph_source_text(candidate_text)
    graph = nx.Graph()
    seen = {}
    for chunk_index, chunk in enumerate(chunks, start=1):
        if progress_callback:
            progress_callback("extract_progress", f"正在提取第 {chunk_index}/{len(chunks)} 个知识片段...", f"片段长度: {len(chunk)}")
        triples = _filter_important_triples(extract_triplets_with_llm(chunk))
        page = _infer_source_page(chunk)
        for subj, rel, obj in triples:
            key = (subj.casefold(), rel.casefold(), obj.casefold())
            provenance = {
                "chunk": chunk_index,
                "page": page,
                "text": chunk[:500],
            }
            if key in seen:
                edge = seen[key]
                edge["source_chunks"].append(provenance)
                continue
            graph.add_edge(
                subj,
                obj,
                label=rel,
                source_chunks=[provenance],
            )
            seen[key] = graph.get_edge_data(subj, obj)

    graph = merge_concept_variants_in_graph(graph)
    return graph if graph.number_of_edges() else None


def build_chapter_knowledge_graph(chapter_name, chapter_text, progress_callback=None):
    """
    构建章节知识图谱。
    使用 LLM 从章节文本中提取三元组关系，构建知识图谱。
    """
    import streamlit as st

    if progress_callback:
        progress_callback("start", f"开始构建【{chapter_name}】的知识图谱", f"章节文本长度: {len(chapter_text)} 字符")

    # 文本预清洗
    if progress_callback:
        progress_callback("preclean", "正在清洗文本...", f"原始文本长度: {len(chapter_text)}")
    cleaned_text = preclean_text(chapter_text)
    if progress_callback:
        progress_callback("preclean", "文本清洗完成", f"清洗后文本长度: {len(cleaned_text)}")

    # Stage 1: keep headings, definitions, summaries and relationship-bearing
    # sentences. Examples and narrative filler remain available to FAISS, but
    # should not dominate the graph extraction prompt.
    candidate_text = _select_graph_source_text(cleaned_text)
    if progress_callback:
        progress_callback("select", "正在筛选关键知识内容...", f"候选文本长度: {len(candidate_text)}")
    if len(candidate_text.strip()) < 20:
        if progress_callback:
            progress_callback("error", "清洗后没有足够的关键知识内容", "跳过 LLM 抽取，避免生成噪声关系")
        return None

    # Extract every selected chunk so later sections are not lost. Each edge
    # keeps the chunk text and an optional page marker for explainable answers.
    chunked_graph = _build_chunked_chapter_graph(candidate_text, progress_callback)
    if chunked_graph is not None:
        if progress_callback:
            progress_callback("complete", "知识图谱构建完成", f"包含 {chunked_graph.number_of_nodes()} 个实体、{chunked_graph.number_of_edges()} 条关系")
        return chunked_graph
    if progress_callback:
        progress_callback("error", "未能从分片文本提取到有效三元组", "请检查 LLM 服务或文本内容")
    return None

    # 使用 LLM 提取三元组
    if progress_callback:
        progress_callback("extract", "正在使用 LLM 提取知识三元组...", "")

    # 构建提示词
    prompt = f"""请从以下文本中提取关键的知识三元组（实体-关系-实体），用于构建知识图谱。

要求：
1. 提取重要的概念、术语、定义、功能、特性等实体及其关系
2. 关系词请使用以下标准关系之一：包含、属于、定义、功能、具有、导致、依赖、相关、应用、示例、对比、优势、劣势
3. 每个三元组格式为：(实体1, 关系, 实体2)
4. 尽量提取高质量、有意义的三元组
5. 避免提取过于宽泛或模糊的关系

文本内容：
{candidate_text}

请以JSON格式输出，格式为：{{"triples": [["实体1", "关系", "实体2"], ...]}}
"""

    try:
        response = call_llm(prompt)
        if progress_callback:
            progress_callback("parse", "正在解析 LLM 返回结果...", "")

        # 尝试解析 JSON
        triples = []
        try:
            result = json.loads(response)
            triples = result.get("triples", [])
        except:
            # 尝试从文本中提取 JSON
            import re
            json_match = re.search(r'\{.*"triples".*\}', response, re.DOTALL)
            if json_match:
                try:
                    result = json.loads(json_match.group())
                    triples = result.get("triples", [])
                except:
                    pass

        if not triples:
            if progress_callback:
                progress_callback("error", "未能从 LLM 响应中提取到三元组", "请检查 LLM 服务是否正常")
            return None

        # Stage 3: validate, normalize and deduplicate model output before it
        # reaches the graph. LLMs sometimes return malformed rows or sentences
        # as entities; those must be ignored rather than crashing the build.
        triples = _filter_important_triples(triples)
        if not triples:
            if progress_callback:
                progress_callback("error", "关键内容中未提取到有效三元组", "已过滤空值、长句、重复项和无效关系")
            return None

        # 构建 NetworkX 图
        G = nx.Graph()
        for subj, rel, obj in triples:
            G.add_edge(subj, obj, label=rel)

        # 合并概念变体
        G = merge_concept_variants_in_graph(G)

        if progress_callback:
            progress_callback("complete", f"知识图谱构建完成", f"包含 {G.number_of_nodes()} 个实体, {G.number_of_edges()} 条关系")

        return G

    except Exception as e:
        if progress_callback:
            progress_callback("error", f"构建知识图谱时出错: {str(e)}", "")
        return None
