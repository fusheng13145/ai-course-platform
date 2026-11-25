"""
平台自研运行配置（替代上游 `config.py`）
========================================
归属：本项目自有代码（2026-11-25 阶段3「第三方内容清零」重写）。
与上游同名模块的差异（全部是本项目的硬要求）：

1. **不依赖 streamlit**：原实现为 Streamlit 应用写死 `import streamlit as st`，
   而 8501 是 FastAPI 服务，为一个未使用的 UI 依赖拉整包是纯负担。
2. **不内置数据库口令**：原实现给 Neo4j 口令设了明文默认值，等于把口令写进仓库
   （阶段2 S2-D04）。这里没有回退值：未配置即空串，由 `neo4j_configured()` 明确判定为
   "未配置"，调用方据此降级而不是用错误口令重试。
3. **常量名与原实现保持一致**，让同目录尚未替换的模块（`neo4j_handler` 等）继续可用；
   这些模块在后续批次会被本项目自研实现替换。

所有值只从环境变量读取，不在代码里写任何凭据。
"""

from __future__ import annotations

import os

# ---------------------------------------------------------------- HuggingFace
# 嵌入/重排模型下载走镜像站；可用 HF_ENDPOINT 覆盖（离线部署可置空走本地缓存）。
HF_ENDPOINT = os.getenv("HF_ENDPOINT", "https://hf-mirror.com").strip()
if HF_ENDPOINT:
    os.environ.setdefault("HF_ENDPOINT", HF_ENDPOINT)

# ---------------------------------------------------------------- 模型与推理
OLLAMA_BASE_URL = os.getenv("OLLAMA_API_URL", os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")).rstrip("/")
OLLAMA_API_URL = f"{OLLAMA_BASE_URL}/api/generate"
OLLAMA_CHAT_URL = f"{OLLAMA_BASE_URL}/api/chat"

# 生成模型：优先 MODEL（历史键），其次 OLLAMA_MODEL，最后给一个通用默认。
MODEL = (os.getenv("MODEL") or os.getenv("OLLAMA_MODEL") or "qwen2.5:7b").strip()

EMBEDDINGS_MODEL = os.getenv("EMBEDDINGS_MODEL", "nomic-embed-text:latest").strip()
CROSS_ENCODER_MODEL = os.getenv(
    "CROSS_ENCODER_MODEL", "cross-encoder/ms-marco-MiniLM-L-6-v2"
).strip()

# 生成层超时（秒）：原实现全链路无超时，Ollama 挂起会把请求线程钉死。
LLM_TIMEOUT_SECONDS = float(os.getenv("LLM_TIMEOUT_SECONDS", "60") or 60)

# ---------------------------------------------------------------- Neo4j
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687").strip()
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j").strip()
# 刻意不给默认值：缺省即空串，由 neo4j_configured() 明确报告"未配置"。
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "")

# ---------------------------------------------------------------- OpenAI 兼容侧
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1").rstrip("/")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat").strip()
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "").strip()

OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "").rstrip("/")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "").strip()
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()


def neo4j_configured() -> bool:
    """图谱能力是否具备最小可用配置（URI + 口令）。缺口令一律视为未配置。"""
    return bool(NEO4J_URI) and bool(NEO4J_PASSWORD)


def ai_provider() -> str:
    """解析生成后端：显式 AI_PROVIDER 优先，否则按密钥可用性推断。"""
    explicit = (os.getenv("AI_PROVIDER") or "").strip().lower()
    if explicit in ("ollama", "deepseek", "openai"):
        return explicit
    if DEEPSEEK_API_KEY:
        return "deepseek"
    if OPENAI_API_KEY and OPENAI_BASE_URL:
        return "openai"
    return "ollama"


def describe_provider() -> str:
    """给健康检查用的可读描述（不含任何密钥）。"""
    provider = ai_provider()
    if provider == "deepseek":
        return f"deepseek:{DEEPSEEK_MODEL}@{DEEPSEEK_BASE_URL}"
    if provider == "openai":
        return f"openai:{OPENAI_MODEL or '<unset>'}@{OPENAI_BASE_URL or '<unset>'}"
    return f"ollama:{MODEL}@{OLLAMA_BASE_URL}"
