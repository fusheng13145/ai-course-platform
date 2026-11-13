# ============================================================
# RAG 检索增强服务 8002（D-A4 选项②：随编排上线）
#
# 嵌入权重通路：
#   · RAG_EMBED_MODEL 默认 BAAI/bge-small-zh-v1.5；
#   · HF_ENDPOINT 构建期默认 https://hf-mirror.com，运行期可覆盖；
#   · HF_HOME 固定 /models/hf，compose 用命名卷挂载，离线部署可预置缓存后
#     置 HF_HUB_OFFLINE=1（rag_engine 命中缓存时本就 local_files_only 加载）。
# 基础镜像钉到补丁级；容器以非 root 运行；健康检查走标准库，不额外装 curl。
# ============================================================
FROM python:3.11.9-slim-bookworm

ARG HF_ENDPOINT=https://hf-mirror.com
ARG RAG_EMBED_MODEL=BAAI/bge-small-zh-v1.5

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_ENDPOINT=${HF_ENDPOINT} \
    HF_HOME=/models/hf \
    RAG_EMBED_MODEL=${RAG_EMBED_MODEL}

WORKDIR /app

# requirements.txt = 契约运行依赖；requirements-engine.txt = 真实混合检索重依赖
COPY requirements.txt requirements-engine.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-engine.txt

COPY rag_engine.py rag_service.py ./

RUN useradd --create-home --uid 10001 app \
    && mkdir -p /app/data /models/hf \
    && chown -R app:app /app /models
USER app

EXPOSE 8002

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8002/api/v1/rag/health', timeout=3)"

CMD ["uvicorn", "rag_service:app", "--host", "0.0.0.0", "--port", "8002"]
