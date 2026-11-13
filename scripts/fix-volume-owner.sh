#!/bin/sh
# =============================================================================
# AI Course Platform - 存量命名卷属主修复（A 路径 #6）
#
# 背景：backend/Dockerfile 以 UID/GID 10001 运行（DR-34 非 root 化）。Docker 的
#   命名卷只在**首次创建**时从镜像目录继承属主；升级前已存在的 uploads-data /
#   backups-data 卷仍是 root:root，导致上传/备份 500。本脚本用于上线前一次性修复。
#
# 设计约束：
#   1. 先检查后改：默认只读检查，只有显式 --apply 才执行 chown。
#   2. 幂等：属主已是 10001:10001 的卷直接跳过，可反复执行。
#   3. 按实际卷名匹配：通过 compose 标签发现当前项目的真实卷名，不硬编码
#      aicourse_uploads-data 之类旧名字；也支持 --volume 指定精确卷名。
#   4. 只处理应用卷 uploads-data / backups-data；**不碰 mysql-backups**
#      （备份卷由 mysql 备份容器以 root 写入，改属主会破坏备份）。
#   5. 卷不存在（新环境尚未创建）视为“无需修复”，不报错。
#
# 用法：
#   ./scripts/fix-volume-owner.sh                  # 只检查（发现漂移时退出码 2）
#   ./scripts/fix-volume-owner.sh --apply          # 检查并修复（修复后退出码 0）
#   ./scripts/fix-volume-owner.sh --project myproj # 限定 compose 项目
#   ./scripts/fix-volume-owner.sh --volume myproj_uploads-data --apply
#
# 依赖：docker CLI；默认使用 alpine:3.20 作为一次性工具容器（可用 --tool-image 覆盖）。
# 平台：Linux / WSL2 / Git Bash。Windows 原生 cmd/PowerShell 不能直接运行 .sh。
# 状态：2026-11-05 本机无 Docker，脚本未在本机实跑；真机验收记录须另行填写。
# =============================================================================
set -eu

TOOL_IMAGE=${TOOL_IMAGE:-alpine:3.20}
PROJECT=${COMPOSE_PROJECT_NAME:-}
APPLY=0
EXACT_VOLUME=
LOGICAL_VOLUMES="uploads-data backups-data"

usage() {
    cat <<'EOF'
用法: fix-volume-owner.sh [选项]

选项:
  --apply              执行 chown（默认只检查）
  --project NAME       限定 docker compose 项目名（默认取 COMPOSE_PROJECT_NAME）
  --volume NAME        指定精确卷名（跳过标签发现，只处理这一个卷）
  --tool-image IMAGE   一次性工具镜像（默认 alpine:3.20）
  -h, --help           显示本帮助

退出码:
  0  检查通过 / 修复完成 / 卷尚未创建
  1  执行错误（docker 不可用、容器启动失败等）
  2  仅检查模式下发现属主漂移
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --apply) APPLY=1 ;;
        --project)
            [ $# -ge 2 ] || { echo "[ERROR] --project 缺少参数" >&2; exit 1; }
            PROJECT=$2
            shift
            ;;
        --volume)
            [ $# -ge 2 ] || { echo "[ERROR] --volume 缺少参数" >&2; exit 1; }
            EXACT_VOLUME=$2
            shift
            ;;
        --tool-image)
            [ $# -ge 2 ] || { echo "[ERROR] --tool-image 缺少参数" >&2; exit 1; }
            TOOL_IMAGE=$2
            shift
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "[ERROR] 未知参数：$1" >&2; usage >&2; exit 1 ;;
    esac
    shift
done

command -v docker >/dev/null 2>&1 || {
    echo "[ERROR] docker CLI 不可用。请在装有 Docker 的机器上执行。" >&2
    exit 1
}

# 按 compose 标签发现“逻辑卷名 -> 实际卷名”。返回 0 行或多行（每行一个卷名）。
discover_volumes() {
    logical=$1
    # shellcheck disable=SC2086
    set -- --filter "label=com.docker.compose.volume=${logical}"
    if [ -n "$PROJECT" ]; then
        set -- "$@" --filter "label=com.docker.compose.project=${PROJECT}"
    fi
    docker volume ls -q "$@"
}

check_or_fix() {
    vol=$1

    owner=$(docker run --rm --user 0:0 -v "${vol}:/data" "$TOOL_IMAGE" \
        sh -c 'stat -c "%u:%g" /data' 2>/dev/null) || {
        echo "[ERROR] 无法读取卷 ${vol} 的属主（工具镜像 ${TOOL_IMAGE} 是否可用？）" >&2
        return 1
    }

    if [ "$owner" = "10001:10001" ]; then
        echo "[OK]    ${vol}: 属主已是 10001:10001"
        return 0
    fi

    echo "[DRIFT] ${vol}: 当前属主 ${owner}，应为 10001:10001"
    if [ "$APPLY" -ne 1 ]; then
        return 2
    fi

    echo "[FIX]   ${vol}: 执行 chown -R 10001:10001 ..."
    docker run --rm --user 0:0 -v "${vol}:/data" "$TOOL_IMAGE" \
        chown -R 10001:10001 /data || {
        echo "[ERROR] chown 失败：${vol}" >&2
        return 1
    }

    owner_after=$(docker run --rm --user 0:0 -v "${vol}:/data" "$TOOL_IMAGE" \
        sh -c 'stat -c "%u:%g" /data' 2>/dev/null) || {
        echo "[ERROR] 修复后无法复检卷 ${vol}" >&2
        return 1
    }
    if [ "$owner_after" = "10001:10001" ]; then
        echo "[FIXED] ${vol}: 属主已修复为 10001:10001"
        return 0
    fi
    echo "[ERROR] ${vol}: 修复后属主仍为 ${owner_after}" >&2
    return 1
}

status=0

if [ -n "$EXACT_VOLUME" ]; then
    volumes=$EXACT_VOLUME
else
    volumes=""
    for logical in $LOGICAL_VOLUMES; do
        found=$(discover_volumes "$logical" || true)
        if [ -z "$found" ]; then
            echo "[SKIP]  逻辑卷 ${logical}: 未发现（新环境尚未创建，无需修复）"
            continue
        fi
        volumes="${volumes}${found}
"
    done
fi

# 去重（按精确卷名或标签发现都可能产生重复）
volumes=$(printf '%s\n' "$volumes" | awk 'NF && !seen[$0]++')

if [ -z "$volumes" ]; then
    echo "[DONE] 没有需要检查的存量卷。"
    exit 0
fi

echo "[INFO] 待检查卷："
printf '  - %s\n' $volumes

for vol in $volumes; do
    rc=0
    check_or_fix "$vol" || rc=$?
    case "$rc" in
        0) ;;
        2)
            if [ "$APPLY" -eq 1 ]; then
                status=1
            else
                status=2
            fi
            ;;
        *)
            status=1
            ;;
    esac
done

echo "[DONE] 检查完成（apply=${APPLY}，exit=${status}）。"
exit "$status"
