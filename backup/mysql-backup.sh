#!/bin/sh
# AI Course Platform - MySQL 定时全量备份脚本
# 由 docker compose mysql-backup 服务调用，滚动保留 BACKUP_RETENTION_DAYS 天。
#
# 增强（2026-08-16，D26）：
# 1) 备份完成校验：gzip -t 验证压缩包完整性 + 非空检查，失败以非零退出码结束
#    （容器 exit != 0 会在 docker compose 中暴露，便于结合监控日志/告警发现备份故障）；
# 2) 结构化输出：每次备份输出本次文件大小与保留清理结果，便于 Promtail 采集回溯；
# 3) 异地同步预留：配置 BACKUP_REMOTE_SYNC（如 rclone 远端名:路径）后，备份完成
#    自动执行 rsync/rclone 同步（未配置则跳过），实现 S3/OSS 等异地容灾。
set -e

BACKUP_DIR=/backups
DB_HOST=${MYSQL_HOST:-mysql}
DB=${MYSQL_DATABASE:-ai_course_platform}
PASS=${MYSQL_ROOT_PASSWORD}
RETENTION=${BACKUP_RETENTION_DAYS:-14}
# 异地同步目标（可选）：如 "oss:aicourse-backups" / "s3:bucket/path"；留空跳过
REMOTE_SYNC=${BACKUP_REMOTE_SYNC:-}
DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/${DB}_${DATE}.sql"

mkdir -p "$BACKUP_DIR"

echo "[$(date -Iseconds)] Starting backup of $DB from $DB_HOST ..."

# 导出 + 压缩（--single-transaction 保证一致性快照，--routines/--triggers 保留存储过程）
mysqldump -h "$DB_HOST" -u root -p"$PASS" \
  --single-transaction --routines --triggers "$DB" > "$FILE"
gzip "$FILE"
GZ="$FILE.gz"

# ---- 备份完整性校验：损坏/空文件视为失败（非零退出码） ----
if [ ! -s "$GZ" ]; then
  echo "[$(date -Iseconds)] ERROR: backup file is empty or missing: $GZ" >&2
  exit 1
fi
if ! gzip -t "$GZ" 2>/dev/null; then
  echo "[$(date -Iseconds)] ERROR: backup integrity check failed (gzip -t): $GZ" >&2
  exit 1
fi
SIZE=$(du -h "$GZ" | awk '{print $1}')
echo "[$(date -Iseconds)] Backup OK: $GZ (${SIZE})"

# ---- 滚动清理超出保留期的备份 ----
CLEANED=$(find "$BACKUP_DIR" -name "${DB}_*.sql.gz" -mtime +"$RETENTION" -print -delete)
if [ -n "$CLEANED" ]; then
  echo "[$(date -Iseconds)] Retained $RETENTION days; cleaned: $(echo "$CLEANED" | tr '\n' ' ')"
fi

# ---- 异地同步（可选）：rclone 等工具经 BACKUP_REMOTE_SYNC 指定远端 ----
if [ -n "$REMOTE_SYNC" ]; then
  if command -v rclone >/dev/null 2>&1; then
    if rclone copy "$GZ" "$REMOTE_SYNC" --log-level ERROR; then
      echo "[$(date -Iseconds)] Remote sync OK: $REMOTE_SYNC"
    else
      echo "[$(date -Iseconds)] ERROR: remote sync failed (rclone): $REMOTE_SYNC" >&2
      exit 1
    fi
  else
    echo "[$(date -Iseconds)] WARN: BACKUP_REMOTE_SYNC set but rclone not installed, skipped" >&2
  fi
fi

echo "[$(date -Iseconds)] Backup completed: $GZ"
