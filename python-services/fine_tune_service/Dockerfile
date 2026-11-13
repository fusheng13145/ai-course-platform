# ============================================================
# LoRA 微调服务 8003（2026-11-07 P1 整改批补齐，闭环 README 残项清单）
#
# 引擎依赖通路：
#   · requirements-engine.txt 含 torch==2.11.0；Linux 下 PyPI 默认拉 CUDA 版（>2GB），
#     容器默认改用 PyTorch CPU 源（TORCH_CPU_INDEX 构建参数可覆盖为完整源）。
#   · 训练/导出/推理契约见 docs/项目手册.md 第八部分 §37；FT_LLM_PROVIDER=engine|ollama。
#   · 数据目录 FT_DATA_DIR 固定 /app/data，compose 用命名卷 ft-data 承载
#     （runs/ 训练产物、weights/ 导出包、数据集元数据都在其中）。
# 基础镜像钉到补丁级；容器以非 root 运行；健康检查走标准库，不额外装 curl。
# ============================================================
FROM python:3.11.9-slim-bookworm

ARG TORCH_CPU_INDEX=https://download.pytorch.org/whl/cpu

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# requirements.txt = 契约运行依赖（CI 契约护栏同款）；requirements-engine.txt = 真实 PEFT 训练重依赖。
# 先从 CPU 源装 torch，再装其余锁定依赖（pip 见 torch==2.11.0 已满足即跳过）。
# 【2026-11-10 实构建发现（V6 批存档，待 Docker 环境修复验证）】：Linux 上 torch==2.11.0 的
# CPU 索引 wheel 疑似缺位/标签不匹配 → requirements-engine.txt 安装阶段回落 PyPI 拉取 CUDA 系
# 依赖（nvidia_cudnn 519MB 等），FT 镜像构建未完成（构建机引擎同时 EOF 退出，交叉因素未定案）。
# 修复候选（届时二选一并实测）：① 显式 `torch==2.11.0+cpu` 本地版本号；② constraints 文件把
# torch 钉到 CPU 索引后再装其余依赖。backend/kg/frontend 三镜像同轮构建成功（kg/frontend 命名落盘在案）。
COPY requirements.txt requirements-engine.txt ./
RUN pip install --no-cache-dir torch==2.11.0 --index-url ${TORCH_CPU_INDEX} \
    && pip install --no-cache-dir -r requirements.txt -r requirements-engine.txt

COPY lora_engine.py lora_service.py ft_llm.py ./

RUN useradd --create-home --uid 10001 app \
    && mkdir -p /app/data \
    && chown -R app:app /app
USER app

EXPOSE 8003

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8003/api/v1/fine-tune/health', timeout=3)"

CMD ["uvicorn", "lora_service:app", "--host", "0.0.0.0", "--port", "8003"]
