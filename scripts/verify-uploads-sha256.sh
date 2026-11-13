#!/usr/bin/env bash
# =============================================================================
# AI Course Platform - 上传卷 sha256 抽检（A 路径 #12 配套）
#
# 用途：恢复演练后对 uploads-data 卷抽检若干文件，输出 sha256；如给出
#       --compare-volume，则对同一批路径在两卷之间逐文件比对，验证文件内容一致。
#
# 设计约束：
#   1. 只读：不写卷、不改属主。
#   2. 按 compose 标签发现实际上传卷名，不硬编码旧卷名；也可 --volume 精确指定。
#   3. 默认抽检 5 个文件（按路径排序取前 N），也可用 --manifest 指定路径清单。
#   4. 本机无 Docker 时不可执行；不得把“未执行”写成“已通过”。
#
# 用法：
#   ./scripts/verify-uploads-sha256.sh
#   ./scripts/verify-uploads-sha256.sh --sample 10 --out uploads-sha256.txt
#   ./scripts/verify-uploads-sha256.sh --compare-volume aicourse_old_uploads-data
#   ./scripts/verify-uploads-sha256.sh --manifest paths.txt --volume myproj_uploads-data
#
# 依赖：docker CLI、alpine:3.20（可用 --tool-image 覆盖）。
# 状态：2026-11-05 本机无 Docker，脚本未在本机实跑；真机抽检结果须附在演练记录。
# =============================================================================
set -euo pipefail

TOOL_IMAGE=${TOOL_IMAGE:-alpine:3.20}
PROJECT=${COMPOSE_PROJECT_NAME:-}
VOLUME=
COMPARE_VOLUME=
SAMPLE=5
MANIFEST=
OUT=

usage() {
    cat <<'EOF'
用法: verify-uploads-sha256.sh [选项]

选项:
  --volume NAME           要抽检的卷（默认按 compose 标签发现 uploads-data）
  --compare-volume NAME   对照卷；对同一批路径逐文件比对 sha256
  --sample N              抽检文件数（默认 5）
  --manifest FILE         路径清单（每行一个，相对 /data），替代自动取样
  --project NAME          限定 compose 项目名（默认 COMPOSE_PROJECT_NAME）
  --tool-image IMAGE      工具镜像（默认 alpine:3.20）
  --out FILE              把结果写入文件（同时打印到 stdout）
  -h, --help              显示本帮助

退出码: 0 抽检完成（单卷）或两卷逐文件一致；1 存在缺失或 sha256 不一致。
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --volume) VOLUME=$2; shift ;;
        --compare-volume) COMPARE_VOLUME=$2; shift ;;
        --sample) SAMPLE=$2; shift ;;
        --manifest) MANIFEST=$2; shift ;;
        --project) PROJECT=$2; shift ;;
        --tool-image) TOOL_IMAGE=$2; shift ;;
        --out) OUT=$2; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "[ERROR] 未知参数：$1" >&2; usage >&2; exit 1 ;;
    esac
    shift
done

case "$SAMPLE" in
    ''|*[!0-9]*) echo "[ERROR] --sample 必须是正整数" >&2; exit 1 ;;
esac

command -v docker >/dev/null 2>&1 || { echo "[ERROR] docker CLI 不可用" >&2; exit 1; }

discover_volume() {
    logical=$1
    # shellcheck disable=SC2086
    set -- --filter "label=com.docker.compose.volume=${logical}"
    if [ -n "$PROJECT" ]; then
        set -- "$@" --filter "label=com.docker.compose.project=${PROJECT}"
    fi
    docker volume ls -q "$@"
}

if [ -z "$VOLUME" ]; then
    VOLUME=$(discover_volume uploads-data | head -n 1)
fi
[ -n "$VOLUME" ] || { echo "[ERROR] 未发现 uploads-data 卷；请用 --volume 指定。" >&2; exit 1; }

hash_volume() {
    vol=$1
    if [ -n "$MANIFEST" ]; then
        docker run --rm -i -v "${vol}:/data" "$TOOL_IMAGE" sh -c '
            cd /data || exit 1
            while IFS= read -r f; do
                [ -z "$f" ] && continue
                if [ -f "$f" ]; then sha256sum "$f"; else echo "MISSING  $f"; fi
            done
        ' < "$MANIFEST"
    else
        docker run --rm -v "${vol}:/data" "$TOOL_IMAGE" sh -c '
            cd /data || exit 1
            find . -type f | sort | head -n "$1" | while IFS= read -r f; do
                sha256sum "$f"
            done
        ' sh "$SAMPLE"
    fi
}

tmp1=$(mktemp)
hash_volume "$VOLUME" > "$tmp1"

echo "[INFO] 抽检卷：${VOLUME}（文件数：$(grep -c . "$tmp1" || true)）"
printf 'sha256\tpath\n'
sed -E 's/^([0-9a-f]{64})  /\1 /' "$tmp1"

rc=0
if [ -n "$COMPARE_VOLUME" ]; then
    paths_file=$(mktemp)
    sed -E 's/^[0-9a-f]{64}  //' "$tmp1" > "$paths_file"
    tmp2=$(mktemp)
    # 对照卷必须使用同一批路径，而不是重新取样
    MANIFEST="$paths_file" hash_volume "$COMPARE_VOLUME" > "$tmp2"

    echo ""
    echo "[INFO] 对照卷：${COMPARE_VOLUME}"
    if diff -u <(sort "$tmp1") <(sort "$tmp2") > /tmp/uploads-sha256-diff.txt 2>/dev/null; then
        echo "  [OK] 两卷同一批路径的 sha256 完全一致"
    else
        echo "  [FAIL] 存在差异或缺失："
        cat /tmp/uploads-sha256-diff.txt
        rc=1
    fi
    rm -f "$paths_file" "$tmp2" /tmp/uploads-sha256-diff.txt
fi

if [ -n "$OUT" ]; then
    {
        echo "# uploads-data sha256 抽检"
        echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "卷: ${VOLUME}"
        [ -n "$COMPARE_VOLUME" ] && echo "对照卷: ${COMPARE_VOLUME}"
        echo "路径\t sha256"
        cat "$tmp1"
    } > "$OUT"
    echo "[INFO] 结果已写入：$OUT"
fi
rm -f "$tmp1"

if [ "$rc" -ne 0 ]; then
    echo "[RESULT] FAIL：上传卷抽检不一致。" >&2
    exit 1
fi
echo "[RESULT] PASS：抽检完成（单卷留档 / 两卷一致）。"
