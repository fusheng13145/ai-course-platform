#!/usr/bin/env bash
# 注入 .env 后运行 真实负载压测编排器（等价 Prometheus 采集）
set -a
while IFS='=' read -r k v; do
  [[ "$k" =~ ^[[:space:]]*# ]] && continue
  [[ -z "$k" ]] && continue
  v="${v%$'\r'}"
  export "$k=$v"
done < <(grep -vE '^[[:space:]]*#' .env | grep -vE '^[[:space:]]*$')
set +a
cd calibration
exec "C:/Users/ASUS/.workbuddy/binaries/python/versions/3.13.12/python.exe" run_calibration.py
