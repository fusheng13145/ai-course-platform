#!/usr/bin/env bash
# 原生启动 backend（连本机 MySQL:3306 / Redis:6379），并放开 actuator 抓取、指向本地 Mock LLM。
# 凭据从 .env 注入（不回显明文）。
set -a
while IFS='=' read -r k v; do
  [[ "$k" =~ ^[[:space:]]*# ]] && continue
  [[ -z "$k" ]] && continue
  v="${v%$'\r'}"
  export "$k=$v"
done < <(grep -vE '^[[:space:]]*#' .env | grep -vE '^[[:space:]]*$')
set +a

# 确保目标库存在（Flyway 不自动建库）
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS ai_course_platform CHARACTER SET utf8mb4;" 2>/dev/null \
  || echo "[warn] 无法用 MYSQL_ROOT_PASSWORD 建库，后端可能连不上（请确认本机 MySQL root 口令与 .env 一致）"

export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"
export SPRING_DATASOURCE_PASSWORD="$MYSQL_ROOT_PASSWORD"
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
# 注意：本机原生 Redis 未启用 requirepass（实测返回 'no password is set'），
# 故不向 Spring 透传 REDIS_PASSWORD，否则 RedisStartupValidator 强校验会失败退出。
# 若投产 Redis 有密码，请在此恢复：export SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD"

cd backend
exec java -Xmx2g -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local \
  --app.actuator.secured=false \
  --app.cookie.secure=false \
  --app.security.require-https=false \
  --app.security.csrf.enabled=false \
  --ai.deepseek.base-url=http://localhost:9099/v1 \
  --ai.deepseek.api-key=mock \
  --ai.provider=deepseek \
  > ../calibration/backend.log 2>&1
