# 密钥注入与 Secret 管理（生产接入指南）

**日期**：2026-09-15
**来源**：F-02 密钥治理行动项 P1 ——「生产接入 Vault / Secret Manager」
**状态**：✅ 应用侧接入点已实现并实测；部署侧提供 Vault Agent/K8s/Compose 三套接线方案

---

## 1. 目标与结论

**目标**：生产环境不再依赖主机上的明文 `.env` / 明文环境变量，改由受管密钥库（Vault / 云 Secret Manager / K8s Secret）统一发放与轮转。

**结论（一句话）**：应用侧**不改代码、不引入 Spring Cloud**，仅新增一处配置即被文件式密钥接管——

```yaml
# backend/src/main/resources/application.yaml
spring:
  config:
    import:
      - optional:configtree:/run/secrets/
```

`/run/secrets/` 是 Docker Compose `secrets:`、Kubernetes Secret 卷、Vault Agent 渲染输出三者的**共同落点**，因此同一接入点可覆盖三种来源。

> 设计取舍：`backend/pom.xml` 已明确「本项目未引入 Spring Cloud」，故**不**采用 `spring-cloud-vault-config`，改用 Spring Boot 原生 `configtree` + 密钥投递侧（Agent/平台）解耦。好处：零新依赖、零编译期耦合，密钥轮转由投递侧负责。

---

## 2. 工作机制

`configtree` 把「目录中的每个文件」映射为「一条配置属性」：

```
/run/secrets/SPRING_DATASOURCE_PASSWORD   →  属性 SPRING_DATASOURCE_PASSWORD
/run/secrets/JWT_SECRET                   →  属性 JWT_SECRET
/run/secrets/SPRING_DATA_REDIS_PASSWORD   →  属性 SPRING_DATA_REDIS_PASSWORD
```

文件名即属性名，**文件内容即属性值**（不含换行）。`application.yaml` 中既有的 `${SPRING_DATASOURCE_PASSWORD}` / `${JWT_SECRET}` 占位符无需改动即可解析到文件值。

`optional:` 前缀表示**目录不存在时静默跳过**——本地 Windows 开发无 `/run/secrets`，属预期，不影响启动。

### 2.1 优先级铁律（务必牢记）

Spring Boot 属性源优先级（高 → 低）：

```
命令行参数 > 系统环境变量 > config data（application.yaml 及其 configtree 导入）
```

即 **环境变量 > configtree 文件**。含义：

- 若同名明文环境变量仍存在，它会**静默覆盖**文件值，安全意图被绕过且**不报错**；
- 因此改用文件式密钥时，必须**同时移除同名明文 env**。这也是本指南反复强调「移除明文键」的原因。

---

## 3. 接入方案

### 3.1 Docker Compose secrets（自建主机部署）

Compose 的 file secret 会挂载为 `/run/secrets/<secret 名>`。步骤：

1. 准备密钥文件（已被 `.gitignore` 排除，`deploy/secrets/*` 不入库）：

   ```bash
   mkdir -p deploy/secrets
   openssl rand -base64 32 > deploy/secrets/jwt_secret.txt
   openssl rand -base64 24 > deploy/secrets/spring_datasource_password.txt
   openssl rand -base64 24 > deploy/secrets/redis_password.txt
   ```

2. 在 `docker-compose.yml` 的 `backend` 服务中**删除**以下 3 行明文键（不删则 env 覆盖文件，见 §2.1）：

   ```yaml
   # 删除这三行：
   SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD}
   SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
   JWT_SECRET: ${JWT_SECRET:?JWT_SECRET 必须设置（请 cp .env.example .env 后填入至少32位随机密钥）}
   ```

3. 追加 `secrets` 声明：

   ```yaml
   services:
     backend:
       secrets:
         - SPRING_DATASOURCE_PASSWORD
         - JWT_SECRET
         - SPRING_DATA_REDIS_PASSWORD

   secrets:
     SPRING_DATASOURCE_PASSWORD:
       file: ./deploy/secrets/spring_datasource_password.txt
     JWT_SECRET:
       file: ./deploy/secrets/jwt_secret.txt
     SPRING_DATA_REDIS_PASSWORD:
       file: ./deploy/secrets/redis_password.txt
   ```

> **为什么必须「删除」而不是「用覆盖文件置 null」？** 实测结论（Compose v5.5.0）：
> - `KEY: ${VAR}` 这类普通插值，可在覆盖文件中写 `KEY: null` 被正确移除；
> - 但 `KEY: ${VAR:?必填提示}` 中的 **`:?` 必填插值会被 Compose 提前求值**，其求值结果会顶掉覆盖文件的 `null`，导致明文值仍然进入容器。
>
> 实测证据：以相同方式覆盖 3 个键后，`docker-compose config` 显示
> `SPRING_DATASOURCE_PASSWORD: null`、`SPRING_DATA_REDIS_PASSWORD: null`（已移除），
> 而 `JWT_SECRET` 仍保留 `.env` 中的明文值。
> 该行为会造成「以为 JWT 已由密钥库接管、实际仍走明文」的**静默降级**，正是本项目明令禁止的模式，
> 故**不提供**「一键覆盖文件」方案，改为上述显式删除步骤（删掉即不会静默回退）。

> **MySQL / Redis 容器自身口令**：本方案只接管后端进程的密钥。MySQL 官方镜像支持
> `MYSQL_ROOT_PASSWORD_FILE`（指向 secret 文件路径）；Redis 需改写 `command` 从
> `REDIS_PASSWORD_FILE` 读取。二者未在本方案强制改造，仍由 `.env` 明文提供 —— **这是当前已知残项**，见 §6。

### 3.2 Kubernetes Secret 卷

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-course-backend
spec:
  template:
    spec:
      containers:
        - name: backend
          image: ai-course-platform/backend:latest
          volumeMounts:
            - name: secrets
              mountPath: /run/secrets
              readOnly: true
          # 注意：不要在此处同时设置 SPRING_DATASOURCE_PASSWORD / JWT_SECRET 等 env（会覆盖文件值）
      volumes:
        - name: secrets
          secret:
            secretName: ai-course-backend-secrets
            items:
              # key 决定 /run/secrets 下的文件名，必须与属性名完全一致
              - key: jwt-secret
                path: JWT_SECRET
              - key: datasource-password
                path: SPRING_DATASOURCE_PASSWORD
              - key: redis-password
                path: SPRING_DATA_REDIS_PASSWORD
```

创建 Secret（示意）：

```bash
kubectl create secret generic ai-course-backend-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  --from-literal=datasource-password="<强口令>" \
  --from-literal=redis-password="<强口令>"
```

> 也可使用 External Secrets Operator / Secrets Store CSI Driver 把云 Secret Manager
> （AWS Secrets Manager / 阿里云 KMS / 腾讯云 SSM）同步为同名 K8s Secret，应用侧无感知。

### 3.3 HashiCorp Vault（Vault Agent 旁车）

已提供可直接改造使用的接线文件：

| 文件                                                | 作用                                                        |
| --------------------------------------------------- | ----------------------------------------------------------- |
| `deploy/vault/agent.hcl`                            | Vault Agent 配置：AppRole 认证 + 周期重渲染 + 三个 template |
| `deploy/vault/templates/spring-datasource-password.ctmpl` | 渲染 `/run/secrets/SPRING_DATASOURCE_PASSWORD`        |
| `deploy/vault/templates/jwt-secret.ctmpl`           | 渲染 `/run/secrets/JWT_SECRET`                              |
| `deploy/vault/templates/redis-password.ctmpl`       | 渲染 `/run/secrets/SPRING_DATA_REDIS_PASSWORD`              |

原理：Vault Agent 作为旁车，把 KV v2 中的密钥**渲染成文件**落到与 backend 共享的 `/run/secrets` 卷；
`template_config.static_secret_render_interval = "5m"` 使 Vault 侧轮转密钥后最迟 5 分钟自动落盘生效，
**应用无需重启**（新请求即读到新值；如需立即生效可重启或触发 Actuator 刷新）。

初始化 Vault 侧数据：

```bash
vault kv put secret/ai-course-platform/datasource password='<强口令>'
vault kv put secret/ai-course-platform/jwt        secret="$(openssl rand -base64 32)"
vault kv put secret/ai-course-platform/redis      password='<强口令>'
```

> 同样必须移除同名明文 env（§2.1）。

---

## 4. 验证方法

### 4.1 已完成的静态验证

- `application.yaml` 中 `spring.config.import` 已生效（`optional:` 保证本地无 `/run/secrets` 不报错）。
- Compose 覆盖语义实验（§3.1 引用）：确认 `:?` 必填插值无法被 `null` 覆盖 —— 这是放弃「一键覆盖文件」方案的**实测依据**。

### 4.2 上线前自测（推荐逐项执行）

**① 无密钥目录时正常启动**（本地）

```bash
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --server.port=8080
# 期望：不因 /run/secrets 不存在而报错
```

**② 文件式密钥可被读取**（用一个临时目录替代 `/run/secrets`，验证机制本身）

```bash
mkdir -p /tmp/ai-secrets
printf '%s' "$(openssl rand -base64 32)" > /tmp/ai-secrets/JWT_SECRET
printf '%s' '1234'                       > /tmp/ai-secrets/SPRING_DATASOURCE_PASSWORD
# 不设置同名环境变量，改用 SPRING_CONFIG_IMPORT 追加导入目录
SPRING_CONFIG_IMPORT=optional:configtree:/tmp/ai-secrets/ \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --server.port=8080
# 期望：JWT 密钥与数据源口令均来自文件，应用正常启动
```

**③ 反证（证明 env 确实会覆盖文件，故必须删除明文键）**

```bash
JWT_SECRET=wrong-value-should-win \
SPRING_CONFIG_IMPORT=optional:configtree:/tmp/ai-secrets/ \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
# 期望：以 env 中的 wrong-value 生效（可经日志/抓包核对签名），印证 §2.1 优先级铁律
```

---

## 5. 生产迁移检查清单

- [ ] 密钥已写入 Vault / 云 Secret Manager / K8s Secret，且访问凭据为最小权限、可审计。
- [ ] 已**移除** `docker-compose.yml` 中 backend 的明文键（`SPRING_DATASOURCE_PASSWORD` / `SPRING_DATA_REDIS_PASSWORD` / `JWT_SECRET`）。
- [ ] 已移除部署清单中其它同名明文 env（K8s `env:`、systemd unit、`.env`）。
- [ ] 主机上的历史明文 `.env` 已安全销毁（并确认未进过版本库；历史泄露项的处置见 `deliverables/gstack/secret-governance-F02-2026-09-15.md`）。
- [ ] `/run/secrets` 权限为 `0400`、属主为应用运行用户。
- [ ] 轮转演练：在密钥库更新值 → 确认应用在预期时间内使用新值（Vault Agent 场景 ≤5 分钟）。
- [ ] 密钥轮转后同步更新 `deploy/secrets/*.txt`（Compose 场景）并 `docker compose up -d` 重投。
- [ ] 演示账号开关 `DEMO_ACCOUNTS_ENABLED=false`（见 `application.yaml` 的 `app.demo-accounts`）。

---

## 6. 已知局限与后续

| # | 局限                                                                       | 影响                                                    | 建议                                                     |
| - | -------------------------------------------------------------------------- | ------------------------------------------------------- | -------------------------------------------------------- |
| 1 | MySQL / Redis 容器自身启动口令仍由 `.env` 明文提供                          | 主机 `.env` 仍存留 2 个明文口令                          | 采用 `MYSQL_ROOT_PASSWORD_FILE`；Redis 改写 command 读取文件 |
| 2 | Compose 场景需手工删除 3 行明文键（无一键覆盖方案，原因见 §3.1）             | 迁移需人工确认，存在漏删风险                            | 迁移后按 §4.2 反证用例验证；或改用 K8s/Vault 路径         |
| 3 | 本项目无真实 Vault / 云 Secret Manager 实例                                | 方案为接线级交付，未做端到端联通验证                    | 具备实例后按 §4.2 逐项实测并回填证据                     |
| 4 | 密钥轮转后应用内已缓存的连接（如 Hikari 池）不会自动重建                    | 数据源口令轮转需重启容器                                | 数据源口令轮转纳入「重启窗口」；JWT 密钥可热生效（无需重启）|

---

## 7. 关联产出

- `backend/src/main/resources/application.yaml` —— `spring.config.import` 接入点（含优先级铁律注释）
- `.env.example` —— 文件式密钥说明段
- `docker-compose.yml` —— backend 服务 `DEMO_ACCOUNTS_ENABLED` 默认关闭
- `deploy/vault/agent.hcl` + `deploy/vault/templates/*.ctmpl` —— Vault Agent 接线
- `deploy/secrets/.gitkeep` —— 密钥目录占位（内容已被 `.gitignore` 排除）
- `deliverables/gstack/secret-governance-F02-2026-09-15.md` —— 密钥治理总报告
