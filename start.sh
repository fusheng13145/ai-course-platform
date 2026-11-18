#!/usr/bin/env sh
# Linux / macOS 一键启动器：与 start.bat 等价——只找解释器并转发子命令。
# 所有判定逻辑在 scripts/start-all.py，本文件不复制任何判定
# （同一规则两处各写一份必然分叉，DR-96 已付学费）。
# 子命令：all（默认）| backend | frontend | status；开关：--no-color
# 例：./start.sh        ./start.sh backend       ./start.sh status
set -eu

SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT="$SELF_DIR"

SUB="all"
if [ "$#" -gt 0 ]; then
  case "$1" in
    all|backend|frontend|status) SUB="$1"; shift ;;
  esac
fi

PY_EXE=""
for cand in python3 python; do
  if command -v "$cand" >/dev/null 2>&1; then
    # 只认 3.11+：引擎与 start-all.py 都以 3.11 为基线
    if "$cand" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)' >/dev/null 2>&1; then
      PY_EXE="$cand"
      break
    fi
  fi
done

if [ -z "$PY_EXE" ]; then
  echo "[ERROR] No Python 3.11+ on PATH - this launcher is a thin wrapper and cannot run."
  echo "        Install Python 3.11 (the engines and the launcher both need it), then rerun."
  echo "        Prerequisites and manual bring-up steps: see docs/部署文档.md section 3."
  exit 1
fi

echo "============================================================"
echo " AI Course Platform - unified launcher"
echo " subcommand: $SUB"
echo " ports: Ollama 11434 / MinIO 9000 / Neo4j 7687 / KG 8001 / RAG 8002"
echo "        LoRA 8003 / API 8080 (API_PORT=8081 for parallel) / Web 5173-5175"
echo " services already listening are adopted, never restarted, never killed."
echo " Ctrl+C terminates only the processes this launcher started."
echo " Local MySQL 3306 and Redis 6379: auto-started if down (never Docker)."
echo "============================================================"
echo

# exec：Ctrl+C 直接落到 start-all.py，不留中间 shell 吞信号
exec "$PY_EXE" "$PROJECT_ROOT/scripts/start-all.py" "$SUB" "$@"
