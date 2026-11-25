# ============================================================
# Engine Hub 镜像（单端口 8600）：KG(8001) + DeepGraph/知识库(8501) + LoRA 微调(8003)
#
# 构建上下文必须是 python-services/（不是本目录），因为要同时 COPY 三个引擎目录：
#   docker build -f engine_hub/Dockerfile -t aicourse/engine-hub:local python-services/
#
# 为什么合并成一个镜像：
#   三套引擎在本机一直是三个端口三个窗口；对外只需要一个口（8600）。合并发生在
#   「进程与端口」这一层，**引擎代码一行不改**——hub.py 按原始 path 前缀把请求
#   原样交给各子 app 的 ASGI 可调用对象，前缀不剥离，因此 Java 侧只改 host:port。
#
# 为什么 torch 单独先装：
#   与 fine_tune_service/Dockerfile 同因（2026-11-10 实构建发现）——Linux 上
#   torch==2.11.0 直装会回落 PyPI 拉 CUDA 系（nvidia_cudnn 519MB 等）。
#   显式 `torch==2.11.0+cpu` 满足 `torch==2.11.0` 规约，后续 requirements 阶段跳过。
# ============================================================
FROM python:3.11.9-slim-bookworm

ARG TORCH_CPU_INDEX=https://download.pytorch.org/whl/cpu

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TZ=Asia/Shanghai \
    DG_DATA_DIR=/app/data \
    RAG_DATA_DIR=/app/data/rag \
    FT_DATA_DIR=/app/data/ft \
    HF_HOME=/models/hf \
    ENGINE_HUB_PORT=8600

WORKDIR /app

# ---- 依赖：三套 requirements 的并集（engine_hub/requirements-hub.txt 用 -r 引用）----
# 每份都显式改名落盘。原因（2026-11-25 CI 实证）：deepgraph 与 fine_tune 各自都有
# requirements.txt，写成 `COPY a/requirements.txt b/requirements.txt /app/req/` 时
# 后者会静默覆盖前者，而下面的 sed 又按「不同目标名」去找 → pip 报
# `Could not open requirements file: /app/req/fine_tune_service-requirements.txt`。
COPY kg_service/requirements.txt /app/req/kg_service-requirements.txt
COPY deepgraph_rag/requirements.txt /app/req/requirements.txt
COPY deepgraph_rag/requirements-platform.txt /app/req/requirements-platform.txt
COPY fine_tune_service/requirements.txt /app/req/fine_tune_service-requirements.txt
COPY fine_tune_service/requirements-engine.txt /app/req/requirements-engine.txt
COPY engine_hub/requirements-hub.txt /app/req/requirements-hub.txt

# 先断言 6 份清单都在，再 sed + pip：缺文件当场报错，不让问题拖到 pip 阶段才暴露。
RUN set -e; for f in kg_service-requirements.txt requirements.txt requirements-platform.txt \
      fine_tune_service-requirements.txt requirements-engine.txt requirements-hub.txt; do \
      test -s "/app/req/$f" || { echo "missing or empty /app/req/$f"; exit 1; }; done

# -r 的相对路径以「引用文件所在目录」解析：这里把并集清单的引用改成容器内平铺路径，
# 避免 ../kg_service 在容器里不存在（容器内四份 requirements 都在 /app/req）。
RUN sed -i \
      -e 's#-r ../kg_service/requirements.txt#-r /app/req/kg_service-requirements.txt#' \
      -e 's#-r ../deepgraph_rag/requirements.txt#-r /app/req/requirements.txt#' \
      -e 's#-r ../deepgraph_rag/requirements-platform.txt#-r /app/req/requirements-platform.txt#' \
      -e 's#-r ../fine_tune_service/requirements.txt#-r /app/req/fine_tune_service-requirements.txt#' \
      -e 's#-r ../fine_tune_service/requirements-engine.txt#-r /app/req/requirements-engine.txt#' \
      /app/req/requirements-hub.txt \
 && pip install --upgrade pip \
 && pip install --no-cache-dir "torch==2.11.0+cpu" --index-url ${TORCH_CPU_INDEX} \
 && pip install --no-cache-dir -r /app/req/requirements-hub.txt

# ---- 三套引擎代码 + hub（目录名与 hub.py 里的 sys.path 约定一一对应）----
COPY kg_service/ /app/kg_service/
COPY deepgraph_rag/ /app/deepgraph_rag/
COPY fine_tune_service/ /app/fine_tune_service/
COPY engine_hub/ /app/engine_hub/

RUN mkdir -p /app/data /app/data/rag /app/data/ft \
 && useradd --create-home --uid 10001 app \
 && chown -R app:app /app
USER app

EXPOSE 8600

# 探聚合健康端点：三能力任一降级都会体现在 body.services[].ready=false，
# 但容器仍算「进程活着」——因此这里只断言 200，能力降级由 /health 的 status 字段如实上报，
# 交给编排/运维看图，而不是用探针把「能力降级」升级成「容器反复重启」。
HEALTHCHECK --interval=30s --timeout=8s --start-period=180s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8600/health', timeout=6).status == 200 else 1)"

CMD ["python", "-m", "uvicorn", "hub:app", "--app-dir", "/app/engine_hub", "--host", "0.0.0.0", "--port", "8600"]
