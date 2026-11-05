#!/usr/bin/env bash
# =============================================================================
# AI Course Platform - 可复现镜像构建命令（挂账清单第 7 项）
#
# 本机（Windows，无 Docker daemon）无法执行本脚本；以下命令已固化进
# .github/workflows/ci.yml 的 "Docker Images Build" 作业，由 CI 真机验证。
# 本机未构建、未验证，不得把本脚本的存在当成“镜像已构建通过”。
#
# 构建范围（docker-compose.yml 中带 build: 的四个服务）：
#   - backend      （Spring Boot，Java 21）
#   - frontend     （Vite 构建 + nginx 运行）
#   - kg-service   （profile: python）
#   - rag-service  （profile: python）
#
# 用法（在仓库根目录、有 Docker 且 .env 已填六个必填键的机器上）：
#   ./scripts/build_images.sh
#   ./scripts/build_images.sh --no-pull      # 不预拉基础镜像
#
# 说明：compose 的 ${VAR:?} 插值要求 .env（或环境变量）齐全；脚本先做静态检查，
#       缺键时直接失败，避免构建到一半才报错。
# =============================================================================
set -euo pipefail

NO_PULL=0
while [ $# -gt 0 ]; do
    case "$1" in
        --no-pull) NO_PULL=1 ;;
        -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "[ERROR] 未知参数：$1" >&2; exit 1 ;;
    esac
    shift
done

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO_ROOT"

command -v docker >/dev/null 2>&1 || {
    echo "[ERROR] docker CLI 不可用；本机无 Docker 时请交由 CI 执行。" >&2
    exit 1
}

if [ -f .env ]; then
    # shellcheck disable=SC1091
    set -a; . ./.env; set +a
fi

for key in MYSQL_ROOT_PASSWORD REDIS_PASSWORD JWT_SECRET APP_ADMIN_INITIAL_PASSWORD NEO4J_PASSWORD GRAFANA_ADMIN_PASSWORD; do
    eval "value=\${$key:-}"
    if [ -z "$value" ]; then
        echo "[ERROR] 缺少必填键 $key；先运行 scripts/check-required-env.py" >&2
        exit 1
    fi
done

echo "[1/3] 校验 compose 插值"
docker compose -f docker-compose.yml config -q

# backend/frontend 在 profiles: ["full"]，kg/rag 在 profiles: ["python"]；
# 只写 --profile python 会跳过 backend/frontend，必须两个 profile 同时启用。
echo "[2/3] 构建 backend / frontend / kg-service / rag-service"
if [ "$NO_PULL" -eq 0 ]; then
    docker compose --profile full --profile python build --pull
else
    docker compose --profile full --profile python build
fi

echo "[3/3] 本机构建产物"
docker image ls --format '{{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.Size}}' | grep -E 'backend|frontend|kg-service|rag-service' || true

echo "[DONE] 构建命令执行完毕。CI 侧以 .github/workflows/ci.yml 的 Docker 作业为准。"
