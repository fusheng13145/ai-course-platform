#!/bin/bash
# 创建最小权限应用账户 aicourse_app，替代后端直连 root（纵深防御 / 最小权限原则）。
#
# 行为说明：
# - 凭据默认复用 MYSQL_ROOT_PASSWORD（与 root 同口令，仅权限收窄）；
#   如需更强隔离，可在 .env 另设 MYSQL_APP_PASSWORD，本脚本优先采用。
# - 仅授权单库 ${MYSQL_DATABASE} 的必要 DML + Flyway 所需 DDL，
#   不含任何全局权限（无 GRANT OPTION / SUPER / FILE / RELOAD 等）。
#   即便凭据泄露，影响面也仅限于单库，不会危及实例级安全。
# - 该脚本仅在 MySQL 容器【首次初始化】（数据卷为空）时由 entrypoint 执行；
#   若 mysql-data 卷已存在历史数据，需重置该卷后方能生效。
set -euo pipefail

APP_USER="aicourse_app"
APP_PW="${MYSQL_APP_PASSWORD:-${MYSQL_ROOT_PASSWORD}}"
DB="${MYSQL_DATABASE:-ai_course_platform}"

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql -u root <<-EOSQL
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PW}';
CREATE USER IF NOT EXISTS '${APP_USER}'@'localhost' IDENTIFIED BY '${APP_PW}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES ON ${DB}.* TO '${APP_USER}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES ON ${DB}.* TO '${APP_USER}'@'localhost';
FLUSH PRIVILEGES;
EOSQL

echo "init-app-db-user: created least-privilege user '${APP_USER}' on database '${DB}'"
