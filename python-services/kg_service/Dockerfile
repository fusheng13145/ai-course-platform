# ============================================================
# KG 知识图谱服务 8001（D-A4 选项②：随编排上线）
# 基础镜像钉到补丁级；容器以非 root 运行；健康检查走标准库，不额外装 curl。
# Neo4j 不可达时 kg_store 自动退回内存图（既有降级语义不变）。
# ============================================================
FROM python:3.11.9-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY kg_service.py kg_store.py neo4j_fake.py ./

RUN useradd --create-home --uid 10001 app \
    && chown -R app:app /app
USER app

EXPOSE 8001

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=20s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/api/v1/graph/health', timeout=3)"

CMD ["uvicorn", "kg_service:app", "--host", "0.0.0.0", "--port", "8001"]
