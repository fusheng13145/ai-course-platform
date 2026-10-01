import os
from dotenv import load_dotenv, find_dotenv
import streamlit as st

# 加载环境变量
load_dotenv(find_dotenv())

# ======================
# HuggingFace 镜像配置
# ======================
# 如果无法连接 hf-mirror.com，可改为 https://huggingface.co 或留空使用默认
HF_ENDPOINT = os.getenv("HF_ENDPOINT", "https://hf-mirror.com")
if HF_ENDPOINT:
    os.environ["HF_ENDPOINT"] = HF_ENDPOINT

# ======================
# 模型与接口配置
# ======================

OLLAMA_BASE_URL = os.getenv("OLLAMA_API_URL", "http://127.0.0.1:11434")
OLLAMA_API_URL = f"{OLLAMA_BASE_URL}/api/generate"

# 修改为你本地的 qwen25 模型，速度更快
MODEL = os.getenv("MODEL", "qwen25")
# 嵌入模型
EMBEDDINGS_MODEL = "nomic-embed-text:latest"
# 重排模型 - 可通过 .env 配置，如果下载失败可改为本地已缓存的模型路径
CROSS_ENCODER_MODEL = os.getenv("CROSS_ENCODER_MODEL", "cross-encoder/ms-marco-MiniLM-L-6-v2")


# ======================
# Neo4j 数据库配置
# ======================
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456Zyj")


