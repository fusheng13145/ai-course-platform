#!/bin/sh
# AI Course Platform - MySQL 定时全量备份脚本
# 由 docker compose mysql-backup 服务调用，滚动保留 BACKUP_RETENTION_DAYS 天
set -e

BACKUP_DIR=/backups
DB_HOST=${MYSQL_HOST:-mysql}
DB=${MYSQL_DATABASE:-ai_course_platform}
PASS=${MYSQL_ROOT_PASSWORD}
RETENTION=${BACKUP_RETENTION_DAYS:-14}
DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/${DB}_${DATE}.sql"

mkdir -p "$BACKUP_DIR"

echo "[$(date -Iseconds)] Starting backup of $DB from $DB_HOST ..."
mysqldump -h "$DB_HOST" -u root -p"$PASS" \
  --single-transaction --routines --triggers "$DB" > "$FILE"
gzip "$FILE"

# 滚动清理超出保留期的备份
find "$BACKUP_DIR" -name "${DB}_*.sql.gz" -mtime +"$RETENTION" -delete

echo "[$(date -Iseconds)] Backup completed: ${FILE}.gz"
