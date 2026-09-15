# ============================================================
# Vault Agent 配置示例 —— 把 Vault KV 密钥渲染成 configtree 可直接读取的文件
#
# 定位：应用侧不引入 Spring Cloud Vault（backend/pom.xml 已明确「本项目未引入 Spring Cloud」），
#   而是由 Vault Agent 作为旁车（sidecar）把密钥"落地"为文件，落到与 Docker/K8s Secret
#   完全相同的 /run/secrets/ 目录。这样密钥来源从「环境变量明文」升级为「Vault 受管」，
#   应用代码与打包产物零改动，且天然支持密钥轮转（Agent 周期重渲染）。
#
# 用法：
#   1) Vault 侧（KV v2）：
#      vault kv put secret/ai-course-platform/datasource password='<强口令>'
#      vault kv put secret/ai-course-platform/jwt        secret='<≥32字节随机串>'
#      vault kv put secret/ai-course-platform/redis      password='<强口令>'
#   2) 凭据：以 AppRole 为例，把 role_id / secret_id 以文件形式挂入容器（勿写成明文 env）。
#   3) 启动 Agent（与 backend 共享 /run/secrets 卷）：
#      vault agent -config=/etc/vault/agent.hcl
#   4) 同步移除同名明文 env（否则 env 优先级更高会静默覆盖文件值）。
#
# 注意：本文件是接线示例，address / mount_path / 密钥路径需按实际 Vault 部署替换。
# ============================================================

pid_file = "/tmp/vault-agent.pid"

vault {
  address = "https://vault.internal:8200"
}

# ---- 认证：AppRole（生产建议；此处以文件承载凭据，避免明文 env）----
auto_auth {
  method "approle" {
    mount_path = "auth/approle"
    config = {
      role_id_file_path                   = "/etc/vault/role-id"
      secret_id_file_path                 = "/etc/vault/secret-id"
      remove_secret_id_file_after_reading = false
    }
  }

  # 令牌落到文件，供 Agent 自身复用
  sink "file" {
    config = {
      path = "/tmp/vault-token"
    }
  }
}

# ---- 渲染策略 ----
template_config {
  # 周期重渲染间隔：Vault 侧密钥轮转后，最迟 5 分钟自动落盘生效
  static_secret_render_interval = "5m"
  # 凭据暂时失效时不退出，等待恢复（避免密钥短时不可用导致整机停摆）
  exit_on_retry_failure = false
}

# ---- 每个 template 目标一个文件：destination 的文件名 = configtree 读取到的属性名 ----

# 数据源密码 → ${SPRING_DATASOURCE_PASSWORD}
template {
  source      = "/etc/vault/templates/spring-datasource-password.ctmpl"
  destination = "/run/secrets/SPRING_DATASOURCE_PASSWORD"
  perms       = "0400"
}

# JWT 签名密钥 → ${JWT_SECRET}
template {
  source      = "/etc/vault/templates/jwt-secret.ctmpl"
  destination = "/run/secrets/JWT_SECRET"
  perms       = "0400"
}

# Redis 密码 → ${SPRING_DATA_REDIS_PASSWORD}
template {
  source      = "/etc/vault/templates/redis-password.ctmpl"
  destination = "/run/secrets/SPRING_DATA_REDIS_PASSWORD"
  perms       = "0400"
}
