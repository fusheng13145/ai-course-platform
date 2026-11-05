#!/usr/bin/env bash
# =============================================================================
# AI Course Platform - MySQL 隔离库恢复与校验（A 路径 #12）
#
# 目标：把一份 mysqldump 备份恢复到**隔离数据库**，不覆盖生产库；随后做
#   ① Flyway 版本集合核对；② 关键表行数核对；③ 输出校验清单供演练记录留档。
# 上传卷的 sha256 抽检由 scripts/verify-uploads-sha256.sh 单独执行。
#
# 安全约束：
#   1. 目标库不得等于源库（默认 ai_course_platform），也不得是 mysql /
#      information_schema / performance_schema / sys。
#   2. 目标库名默认 aicourse_restore_verify，且必须带 restore 前缀；
#      除非显式 --allow-any-target，否则拒绝恢复。
#   3. 只对目标库执行 DROP/CREATE，绝不触碰源库。
#   4. 默认恢复后保留目标库供人工复核；--drop 才清理。
#
# 用法：
#   ./scripts/restore-mysql.sh --backup /backups/ai_course_20261105.sql.gz
#   ./scripts/restore-mysql.sh --backup ... --compare-live --strict
#   ./scripts/restore-mysql.sh --backup ... --container aicourse-mysql --drop
#
# 依赖：docker CLI、容器内 mysql 客户端、gunzip、sha256sum（或 shasum）。
# 环境：MYSQL_ROOT_PASSWORD 可用 --root-password 或环境变量提供。
# 状态：2026-11-05 本机无 Docker，脚本未在本机实跑；首次演练必须真实执行并填
#       docs/恢复演练记录模板.md，不得预填“通过”。
# =============================================================================
set -euo pipefail

BACKUP=
CONTAINER=${MYSQL_CONTAINER:-aicourse-mysql}
ROOT_PASS=${MYSQL_ROOT_PASSWORD:-}
TARGET_DB=${TARGET_DB:-aicourse_restore_verify}
SOURCE_DB=${SOURCE_DB:-ai_course_platform}
MIGRATIONS_DIR=${MIGRATIONS_DIR:-backend/src/main/resources/db/migration}
COUNTS_OUT=${COUNTS_OUT:-restore-verify-counts.txt}
COMPARE_LIVE=0
STRICT=0
DROP_AFTER=0
ALLOW_ANY_TARGET=0

usage() {
    cat <<'EOF'
用法: restore-mysql.sh --backup FILE [选项]

必填:
  --backup FILE           mysqldump 备份（.sql 或 .sql.gz）

选项:
  --container NAME        MySQL 容器名（默认 aicourse-mysql）
  --root-password PASS    root 密码（默认取 MYSQL_ROOT_PASSWORD）
  --target-db NAME        隔离目标库（默认 aicourse_restore_verify，须带 restore 前缀）
  --source-db NAME        对照源库（默认 ai_course_platform）
  --migrations-dir DIR    Flyway 迁移目录（默认 backend/src/main/resources/db/migration）
  --counts-out FILE       校验清单输出（默认 restore-verify-counts.txt）
  --compare-live          将恢复库关键表行数与源库比对（源库在恢复期间应无写入）
  --strict                版本缺失或行数不一致即失败（演练冻结快照时使用）
  --drop                  校验完成后删除隔离库（默认保留）
  --allow-any-target      允许非 restore 前缀的目标库名（慎用）
  -h, --help              显示本帮助

退出码: 0 通过；1 恢复失败 / 版本缺失 / --strict 下行数不一致。
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --backup) BACKUP=$2; shift ;;
        --container) CONTAINER=$2; shift ;;
        --root-password) ROOT_PASS=$2; shift ;;
        --target-db) TARGET_DB=$2; shift ;;
        --source-db) SOURCE_DB=$2; shift ;;
        --migrations-dir) MIGRATIONS_DIR=$2; shift ;;
        --counts-out) COUNTS_OUT=$2; shift ;;
        --compare-live) COMPARE_LIVE=1 ;;
        --strict) STRICT=1 ;;
        --drop) DROP_AFTER=1 ;;
        --allow-any-target) ALLOW_ANY_TARGET=1 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "[ERROR] 未知参数：$1" >&2; usage >&2; exit 1 ;;
    esac
    shift
done

[ -n "$BACKUP" ] || { echo "[ERROR] 缺少 --backup" >&2; exit 1; }
[ -f "$BACKUP" ] || { echo "[ERROR] 备份文件不存在：$BACKUP" >&2; exit 1; }
[ -n "$ROOT_PASS" ] || { echo "[ERROR] 缺少 root 密码（--root-password 或 MYSQL_ROOT_PASSWORD）" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "[ERROR] docker CLI 不可用" >&2; exit 1; }

case "$TARGET_DB" in
    mysql|information_schema|performance_schema|sys)
        echo "[ERROR] 目标是系统库：$TARGET_DB" >&2; exit 1 ;;
esac
if [ "$TARGET_DB" = "$SOURCE_DB" ]; then
    echo "[ERROR] 目标库不得等于源库（$SOURCE_DB）；本脚本只做隔离恢复。" >&2
    exit 1
fi
if [ "$ALLOW_ANY_TARGET" -ne 1 ]; then
    case "$TARGET_DB" in
        aicourse_restore_*|restore_*) ;;
        *) echo "[ERROR] 目标库需带 aicourse_restore_/restore_ 前缀；确需自定义请加 --allow-any-target" >&2; exit 1 ;;
    esac
fi

if printf '%s' "$BACKUP" | grep -q '.gz$'; then
    gzip -t "$BACKUP" || { echo "[ERROR] gzip 完整性检查失败：$BACKUP" >&2; exit 1; }
    restore_cmd="gunzip -c '$BACKUP'"
else
    restore_cmd="cat '$BACKUP'"
fi

running=$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || true)
[ "$running" = "true" ] || { echo "[ERROR] 容器 $CONTAINER 未运行" >&2; exit 1; }

mysql_exec() {
    docker exec -e MYSQL_PWD="$ROOT_PASS" -i "$CONTAINER" \
        mysql -uroot -N -B -e "$1"
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "sha256-unavailable"
    fi
}

BACKUP_SHA=$(sha256_of "$BACKUP")
START_TS=$(date '+%Y-%m-%d %H:%M:%S')

echo "[1/5] 准备隔离库 ${TARGET_DB}（源库 ${SOURCE_DB} 不动）"
mysql_exec "DROP DATABASE IF EXISTS \`${TARGET_DB}\`;
CREATE DATABASE \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null

echo "[2/5] 恢复备份：$BACKUP（sha256=${BACKUP_SHA}）"
set +e
eval "$restore_cmd" | docker exec -e MYSQL_PWD="$ROOT_PASS" -i "$CONTAINER" mysql -uroot "$TARGET_DB"
rc=${PIPESTATUS[0]}
set -e
if [ "$rc" -ne 0 ]; then
    echo "[ERROR] 恢复失败（gunzip/mysql 退出码 ${rc}）" >&2
    exit 1
fi

echo "[3/5] Flyway 版本集合核对"
restored_versions=$(mysql_exec "SELECT version FROM \`${TARGET_DB}\`.flyway_schema_history WHERE success=1 ORDER BY installed_rank;" 2>/dev/null || true)
if [ -z "$restored_versions" ]; then
    echo "[ERROR] 恢复库中没有 flyway_schema_history 或没有成功版本" >&2
    exit 1
fi

expected_versions=""
if [ -d "$MIGRATIONS_DIR" ]; then
    expected_versions=$(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' 2>/dev/null \
        | sed -E 's/^V([0-9._]+)__.*/\1/' | sort -V || true)
else
    echo "[WARN] 迁移目录不存在，跳过期望版本集合：$MIGRATIONS_DIR"
fi

missing_versions=""
if [ -n "$expected_versions" ]; then
    missing_versions=$(comm -23 \
        <(printf '%s\n' "$expected_versions" | sort -u) \
        <(printf '%s\n' "$restored_versions" | sort -u))
fi

echo "  期望版本数：$(printf '%s\n' "$expected_versions" | grep -c . || true) / 恢复库版本数：$(printf '%s\n' "$restored_versions" | grep -c . || true)"
if [ -n "$missing_versions" ]; then
    echo "  [FAIL] 缺失版本：$(printf '%s' "$missing_versions" | tr '\n' ' ')"
else
    echo "  [OK]   恢复库版本集合完整"
fi

echo "[4/5] 关键表行数核对"
TABLES="users courses course_enrollments tasks submissions exam_papers questions learning_records"
counts_tmp=$(mktemp)
printf 'table\trestored\tlive\tdiff\n' > "$counts_tmp"
count_mismatch=0
for table in $TABLES; do
    exists=$(mysql_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TARGET_DB}' AND table_name='${table}';")
    if [ "$exists" = "0" ]; then
        printf '%s\t-\t-\t-\n' "$table" >> "$counts_tmp"
        continue
    fi
    restored_count=$(mysql_exec "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${table}\`;")
    live_count="-"
    diff="-"
    if [ "$COMPARE_LIVE" -eq 1 ]; then
        live_count=$(mysql_exec "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${table}\`;" 2>/dev/null || echo "-")
        if [ "$live_count" != "-" ]; then
            diff=$((restored_count - live_count))
            if [ "$diff" -ne 0 ]; then count_mismatch=1; fi
        fi
    fi
    printf '%s\t%s\t%s\t%s\n' "$table" "$restored_count" "$live_count" "$diff" >> "$counts_tmp"
done
column -t -s $'\t' "$counts_tmp"

echo "[5/5] 写出校验清单：$COUNTS_OUT"
{
    echo "# MySQL 隔离恢复校验清单"
    echo "时间: ${START_TS}"
    echo "备份: ${BACKUP}"
    echo "备份 sha256: ${BACKUP_SHA}"
    echo "容器: ${CONTAINER}"
    echo "源库: ${SOURCE_DB}"
    echo "隔离库: ${TARGET_DB}"
    echo "期望 Flyway 版本:"
    printf '%s\n' "$expected_versions" | sed '/^$/d' | sed 's/^/  - /'
    echo "缺失版本: ${missing_versions:-无}"
    echo "关键表行数:"
    cat "$counts_tmp"
} > "$COUNTS_OUT"
rm -f "$counts_tmp"

if [ "$DROP_AFTER" -eq 1 ]; then
    echo "清理隔离库 ${TARGET_DB} ..."
    mysql_exec "DROP DATABASE IF EXISTS \`${TARGET_DB}\`;" >/dev/null
fi

if [ -n "$missing_versions" ]; then
    echo "[RESULT] FAIL：Flyway 版本缺失，恢复不可信。" >&2
    exit 1
fi
if [ "$STRICT" -eq 1 ] && [ "$count_mismatch" -eq 1 ]; then
    echo "[RESULT] FAIL：--strict 下关键表行数与源库不一致（源库可能在恢复期间有写入）。" >&2
    exit 1
fi

echo "[RESULT] PASS：恢复完成，版本集合完整；上传卷抽检请另跑 verify-uploads-sha256.sh。"
