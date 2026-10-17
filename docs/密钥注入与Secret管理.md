# 密钥注入与 Secret 管理（生产接入指南）

**日期**：2026-09-15 ｜ **2026-10-06 复核更新**（同步 compose 口令回退式、补齐密钥清单与当前状态）
**来源**：F-02 密钥治理行动项 P1 ——「生产接入 Vault / Secret Manager」
**状态**：✅ 应用侧接入点已实现并实测；部署侧提供 Vault Agent/K8s/Compose 三套接线方案 ｜ ✅ 2026-10-06 复核新增 §2.2「文件名精确匹配」规则（实机探针确证），并**已修复**接线件的 Redis 键名缺陷 **DR-24**（见 §3.3／§6-7）

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
/run/secrets/spring.data.redis.password   →  属性 spring.data.redis.password
```

文件名即属性名，**文件内容即属性值**（不含换行）。`application.yaml` 中既有的 `${SPRING_DATASOURCE_PASSWORD}` / `${JWT_SECRET}` 占位符无需改动即可解析到文件值。

> 注意第三行：Redis 口令的属性名是**点分形式**，不是 `SPRING_DATA_REDIS_PASSWORD`。为什么二者写法不同、以及各自的正确文件名，见 §2.2。


`optional:` 前缀表示**目录不存在时静默跳过**——本地 Windows 开发无 `/run/secrets`，属预期，不影响启动。

### 2.1 优先级铁律（务必牢记）

Spring Boot 属性源优先级（高 → 低）：

```
命令行参数 > 系统环境变量 > config data（application.yaml 及其 configtree 导入）
```

即 **环境变量 > configtree 文件**。含义：

- 若同名明文环境变量仍存在，它会**静默覆盖**文件值，安全意图被绕过且**不报错**；
- 因此改用文件式密钥时，必须**同时移除同名明文 env**。这也是本指南反复强调「移除明文键」的原因。

### 2.2 密钥清单与覆盖范围（2026-10-06 复核）

`.env.example` 现共 **33 个键**（2026-10-17 由 `scripts/audit-stats.py` 复算；本节标题的 2026-10-06 复核时点为 32，`e1b78cb`（2026-10-07）新增 `BACKUP_INTERVAL_SECONDS` 后 +1）。接管前须先牢记一条**机制差异**，否则会把「密钥没生效」误判成「密钥库坏了」：

> **configtree 的键名查找是精确 map 查找，不做反向松散绑定**（已用 `javap` 复核 `spring-boot-3.5.15` 的 `ConfigTreePropertySource.getProperty`，字节码仅做 `propertyFiles.get(name)`，无 `.`→`_`／大写化转换；该类也未覆写 `getConfigurationProperty`）。
> 而 `SystemEnvironmentPropertySource` **会**把 `APP_FOO_BAR` 松散匹配到 `app.foo.bar`。
> **后果**：同一个值，用环境变量能填上，用 `/run/secrets/<大写蛇形名>` 却填不上——占位符带默认值则**静默回落默认值**，不带默认值则启动失败。
> 故文件名必须与该属性**在 Spring 里被读取的形式**逐字一致：
> ① yaml 写了 `${大写下划线}` 占位符 → 文件名用**大写蛇形**；
> ② 属性只经 `@ConfigurationProperties` 绑定（yaml 无占位符，或写成点分名）→ 文件名用**点分小写**（如 `spring.data.redis.password`）。

按当前代码逐键复核：

| 密钥 | 在 Spring 中的读取形式（代码位置） | 生效的文件名 | 备注 |
| --- | --- | --- | --- |
| `JWT_SECRET` | `${JWT_SECRET}`（`application.yaml:176`，无默认值） | `JWT_SECRET` | 缺失即启动失败（不会静默） |
| `SPRING_DATASOURCE_PASSWORD` | `${SPRING_DATASOURCE_PASSWORD}`（`:59`，无默认值） | `SPRING_DATASOURCE_PASSWORD` | compose 现值为 `${MYSQL_APP_PASSWORD:-${MYSQL_ROOT_PASSWORD}}`（`docker-compose.yml:192`），回退 root 口令 |
| Redis 口令 | **yaml 无占位符**：`application.yaml:88-92` 只配 `host`/`port`/`database`/`timeout`；`SPRING_DATA_REDIS_PASSWORD` 在整个 `backend/src` **零引用**，纯靠系统 env 松散绑定到 `spring.data.redis.password` | ⚠️ **必须** `spring.data.redis.password` | 若挂成 `SPRING_DATA_REDIS_PASSWORD`，删明文 env 后接管失败 → 连 Redis 报 `NOAUTH`。写法见 §3.1 |
| `DEEPSEEK_API_KEY` | `${DEEPSEEK_API_KEY:}`（`:243`） | `DEEPSEEK_API_KEY` | 未注入时 AI 功能降级、`/api/actuator/health` 恒 503 |
| `MAIL_PASSWORD` | `${MAIL_PASSWORD:}`（`:40`） | `MAIL_PASSWORD` | 可选功能 |
| `DEMO_ACCOUNTS_ENABLED` | `${DEMO_ACCOUNTS_ENABLED:true}`（`:216`） | `DEMO_ACCOUNTS_ENABLED` | **默认值分歧**：yaml `true` vs compose `false`（`docker-compose.yml:203`），接管时须显式给值 |
| `APP_ADMIN_INITIAL_PASSWORD` | `@Value("${app.admin.initial-password:}")`（`AdminSeedPasswordInitializer.java:46`） | `app.admin.initial-password` | 点分名；删 compose 行会连带失去 `:?` 的 fail-fast（`:128`），需以启动脚本校验补位 |
| `APP_ACTUATOR_BASIC_PASSWORD` | `@Value("${app.actuator.basic.password:}")`（`SecurityConfig.java:82`） | `app.actuator.basic.password` | 点分名 |
| `MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`（**容器自身**口令） | mysql/redis 镜像 entrypoint 读 env | ❌ 不在 JVM 侧 | 改用 `MYSQL_ROOT_PASSWORD_FILE` / 改写 redis `command`，见 §6-1 |
| `NEO4J_PASSWORD` | compose `NEO4J_AUTH=neo4j/${NEO4J_PASSWORD:?…}`（`:440`）、探针（`:450`）；`kg_service` 经 `kg_store.py` 读进程 env | ❌ Python 容器侧 | backend 的 `/run/secrets` 对它无效 |
| `GRAFANA_ADMIN_PASSWORD` | grafana 镜像 entrypoint | ❌ 容器侧口令 | — |

**结论**：§3 的三套方案默认接管**前三行 + `DEEPSEEK_API_KEY`**（backend 的 JVM 侧核心密钥，其中 Redis 那条须用点分文件名 `spring.data.redis.password`）；表中「❌」项须在容器侧另行处理，**不可误认为「挂了 `/run/secrets` 就全量去明文」**。



---

## 3. 接入方案

### 3.1 Docker Compose secrets（自建主机部署）

Compose 的 file secret 会挂载为 `/run/secrets/<secret 名>`。步骤：

1. 准备密钥文件（`deploy/secrets/*` 已被 `.gitignore` 排除，不入库）：

   ```bash
   mkdir -p deploy/secrets
   openssl rand -base64 32 > deploy/secrets/jwt_secret.txt
   openssl rand -base64 24 > deploy/secrets/spring_datasource_password.txt
   openssl rand -base64 24 > deploy/secrets/redis_password.txt
   # 可选：AI 引擎密钥（未注入则 AI 功能降级、健康探针恒 503）
   printf '%s' '<DeepSeek API Key>' > deploy/secrets/deepseek_api_key.txt
   ```

2. 在 `docker-compose.yml` 的 `backend` 服务中**删除**以下 3 行明文键（不删则 env 覆盖文件，见 §2.1）。行号以 2026-10-06 复核为准：

   ```yaml
   # 删除这三行（当前 compose 实际内容，datasource 已改为回退式）：
   SPRING_DATASOURCE_PASSWORD: ${MYSQL_APP_PASSWORD:-${MYSQL_ROOT_PASSWORD}}   # :121
   SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}                                # :124
   JWT_SECRET: ${JWT_SECRET:?JWT_SECRET 必须设置（请 cp .env.example .env 后填入至少32位随机密钥）}   # :125
   ```

   > 删除 datasource 行后，`MYSQL_APP_PASSWORD` 不再被 backend 读取，但 `mysql` 容器初始化仍需要它——**只删 backend 的键，不要删 `.env` 里的数据库口令**。

3. 追加 `secrets` 声明。**注意 Redis 那条必须用 `target` 改名为点分属性名**（原因见 §2.2）：

   ```yaml
   services:
     backend:
       secrets:
         - SPRING_DATASOURCE_PASSWORD          # → /run/secrets/SPRING_DATASOURCE_PASSWORD
         - JWT_SECRET                          # → /run/secrets/JWT_SECRET
         - source: SPRING_DATA_REDIS_PASSWORD  # 密钥库里的名（蛇形大写）
           target: spring.data.redis.password  # 容器内文件名（点分，与绑定属性一致）
         - DEEPSEEK_API_KEY                    # 可选

   secrets:
     SPRING_DATASOURCE_PASSWORD:
       file: ./deploy/secrets/spring_datasource_password.txt
     JWT_SECRET:
       file: ./deploy/secrets/jwt_secret.txt
     SPRING_DATA_REDIS_PASSWORD:
       file: ./deploy/secrets/redis_password.txt
     DEEPSEEK_API_KEY:
       file: ./deploy/secrets/deepseek_api_key.txt
   ```

   > 上例中的 `target:` 改名与「大写蛇形 secret 名」需在目标环境用 `docker compose config` 与 §4.2 ②/④ 实测确认；
   > **`/run/secrets` 文件式供给已于 2026-10-07 在真容器端到端闭环**（DR-24 三向取证：三枚文件 + 三项同名 env
   > 缺席 → probe 200 且登录/鉴权链可用；去掉挂载则 `JWT_SECRET` 占位符解析失败退出；改回旧大写蛇形文件名
   > 则 Redis `NOAUTH` 启动失败。详见手册附录 **D.12.3 DR-24**）。仍未实跑的只剩 **Vault Agent 旁车进程**
   > （本机无 Vault 实例），其渲染目的地与已验路径同为 `/run/secrets`。若目标 Compose 版本不接受大写 secret 名，
   > 改为小写名 + `target` 显式指定容器内文件名即可（容器内**文件名**才是属性名，secret 名只是库内标识）。
   > 若含点的文件名不便使用，可改用**嵌套目录**（`spring/data/redis/password`）——两种写法都已在属性源层面实机确证（§4.1）。

> **Prometheus 同样是文件式密钥的消费者（2026-10-07，DR-30）**：`monitor` profile 下 Prometheus 抓取
> `/api/actuator/prometheus` 要用同一枚 Basic 口令，而 **Prometheus 不插值配置文件内的环境变量**
> （`${ACTUATOR_BASIC_PASSWORD}` 会被当字面量发出 → backend 抓取恒 401，而其余 job 与告警规则全部健康，
> 属"监控看着好在撒谎"），因此它只能读 `basic_auth.password_file`。仓库据此在 `docker-compose.yml`
> 顶层声明 `secrets: actuator_basic_password`（源 `deploy/secrets/actuator_basic_password`，
> 容器内落点 `/run/secrets/actuator_basic_password`）。启用 monitor 前按手册 §32.6 准备该文件，
> **末尾不可有换行**（Prometheus 会把换行算进口令），Linux 下还需让容器内 UID 65534 可读。


> **为什么必须「删除」而不是「用覆盖文件置 null」？** 实测结论（Compose v5.5.0）：
> - `KEY: ${VAR}` 这类普通插值，可在覆盖文件中写 `KEY: null` 被正确移除；
> - 但 `KEY: ${VAR:?必填提示}` 中的 **`:?` 必填插值会被 Compose 提前求值**，其求值结果会顶掉覆盖文件的 `null`，导致明文值仍然进入容器。
>
> 实测证据：以相同方式覆盖 3 个键后，`docker-compose config` 显示
> `SPRING_DATASOURCE_PASSWORD: null`、`SPRING_DATA_REDIS_PASSWORD: null`（已移除），
> 而 `JWT_SECRET` 仍保留 `.env` 中的明文值。
> 该行为会造成「以为 JWT 已由密钥库接管、实际仍走明文」的**静默降级**，正是本项目明令禁止的模式，
> 故**不提供**「一键覆盖文件」方案，改为上述显式删除步骤（删掉即不会静默回退）。

> **容器自身口令不在本方案范围**：本方案只接管后端进程的密钥。MySQL 官方镜像支持
> `MYSQL_ROOT_PASSWORD_FILE`（指向 secret 文件路径）；Redis 需改写 `command` 从
> `REDIS_PASSWORD_FILE` 读取；Neo4j 的 `NEO4J_AUTH`（`docker-compose.yml:440`）与其健康探针（`:450`）、
> Grafana 的 `GF_SECURITY_ADMIN_PASSWORD` 同样只能经各自镜像的 env／`*_FILE` 机制处理。
> 上述四项未在本方案强制改造，仍由 `.env` 明文提供 —— **这是当前已知残项**，见 §6。


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
              # key 决定 /run/secrets 下的文件名，必须与「被读取的属性名」逐字一致（见 §2.2）
              - key: jwt-secret
                path: JWT_SECRET
              - key: datasource-password
                path: SPRING_DATASOURCE_PASSWORD
              - key: redis-password
                path: spring.data.redis.password   # ⚠️ 点分名，不是 SPRING_DATA_REDIS_PASSWORD
              - key: deepseek-api-key
                path: DEEPSEEK_API_KEY
```

> 若要接管 `app.admin.initial-password` / `app.actuator.basic.password` 这类**点分形式**的属性，
> `path` 必须写成同样的点分名（如 `path: app.admin.initial-password`），不能写大写蛇形名——
> 原因见 §2.2。

创建 Secret（示意）：

```bash
kubectl create secret generic ai-course-backend-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  --from-literal=datasource-password="<强口令>" \
  --from-literal=redis-password="<强口令>" \
  --from-literal=deepseek-api-key="<API Key>"
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
| `deploy/vault/templates/redis-password.ctmpl`       | 渲染 Redis 口令 → 目的地为 `/run/secrets/spring.data.redis.password`（✅ 2026-10-06 由 `SPRING_DATA_REDIS_PASSWORD` 修正，DR-24） |

> 现有模板只覆盖 **3 个核心密钥**（与 F-02 行动项范围一致）。若要接管 `DEEPSEEK_API_KEY` 等其它
> backend 侧密钥，按同一模式新增一个 `.ctmpl`（输出文件名 = §2.2 表中的占位符逐字写法）并在 `agent.hcl` 注册。


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
>
> ✅ **DR-24 已修复（2026-10-06）**：`deploy/vault/agent.hcl` 第三个 template 原先写
> `destination = "/run/secrets/SPRING_DATA_REDIS_PASSWORD"`，而应用侧不存在该属性名（见 §2.2：
> Redis 口令只经 `spring.data.redis.password` 绑定）。已改为
> `destination = "/run/secrets/spring.data.redis.password"`，并同步修正 `redis-password.ctmpl`
> 首行注释与 `.env.example` 的示例段；`agent.hcl` 内留有修正说明与「嵌套目录」等效写法。
> 依据为实机探针（§4.1 第三条）。**仍未做**的是 Vault 端到端联通（本项目无实例，§6-3）。



---

## 4. 验证方法

### 4.1 已完成的静态验证

- `application.yaml` 中 `spring.config.import` 已生效（`optional:` 保证本地无 `/run/secrets` 不报错）。
- Compose 覆盖语义实验（§3.1 引用）：确认 `:?` 必填插值无法被 `null` 覆盖 —— 这是放弃「一键覆盖文件」方案的**实测依据**。
- **2026-10-06 新增**：`ConfigTreePropertySource` 的键名查找为精确 map 查找（先以 `javap` 复核字节码，再**用真实 jar 实机验证**：`spring-boot 3.5.15 + spring-core/context/beans 6.2.19`，对临时目录构树后直接查 `Environment` 与 `Binder`）。实测结论：
  - 挂 `SPRING_DATA_REDIS_PASSWORD` 文件 → `Binder.bind("spring.data.redis.password")` 返回 **`<none>`**（不可见）；
  - 挂 `spring.data.redis.password` 文件 → 绑定成功；挂 `spring/data/redis/password` **嵌套目录** → 同样成功；
  - 挂 `JWT_SECRET` / `SPRING_DATASOURCE_PASSWORD` → `${JWT_SECRET}` / `${SPRING_DATASOURCE_PASSWORD}` 占位符解析成功（逐字匹配）。

  据此得出 §2.2 的「可接管／不可接管」划分，并修正了接线件的 Redis 键名（**DR-24 已修复**，见 §3.3）。§4.2 ④ 为把该结论转成可执行反证的用例，**尚未实机运行**（探针是脱离 Spring 应用的单元级验证，非全栈启动验证）。


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

**④ 键名精确性反证（防止「以为接管了、其实取了默认值」）**

```bash
# 用点分属性名对应的占位符做一次对照：/run/secrets 里只放大写蛇形名
printf '%s' 'file-value' > /tmp/ai-secrets/APP_ADMIN_INITIAL_PASSWORD
SPRING_CONFIG_IMPORT=optional:configtree:/tmp/ai-secrets/ \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
# 期望：admin 口令替换**不生效**（启动日志无「[seed] admin 种子默认口令已替换…」；
#       该 initializer 在读到空值时直接 return，见 AdminSeedPasswordInitializer.java:56-58），
#       因为代码读的是 ${app.admin.initial-password}，而 configtree 不做反向松散绑定（§2.2）。
#       把文件改名为 app.admin.initial-password 后即生效。
```

---

## 5. 生产迁移检查清单

- [ ] 密钥已写入 Vault / 云 Secret Manager / K8s Secret，且访问凭据为最小权限、可审计。
- [ ] 已**移除** `docker-compose.yml` 中 backend 的明文键（`SPRING_DATASOURCE_PASSWORD` `:121` / `SPRING_DATA_REDIS_PASSWORD` `:124` / `JWT_SECRET` `:125`）。
- [ ] 移除 datasource 行后，确认 `mysql` 容器初始化所需的 `MYSQL_APP_PASSWORD` / `MYSQL_ROOT_PASSWORD` 仍保留（**只删 backend 的键，不删库侧口令**）。
- [ ] 每个 secret 的**容器内文件名／K8s `path` 与「该属性在 Spring 里被读取的形式」逐字一致**（大写蛇形与点分不可互换，见 §2.2）。特别是 **Redis 口令须命名为 `spring.data.redis.password`**，不是 `SPRING_DATA_REDIS_PASSWORD`。
- [ ] 走 Vault Agent 路径时：确认 `deploy/vault/agent.hcl` 的 Redis template 目的地为 `spring.data.redis.password`（该键名缺陷 **DR-24 已于 2026-10-06 修正**，此处仅作接线复核项）。

- [ ] 已移除部署清单中其它同名明文 env（K8s `env:`、systemd unit、`.env`）。
- [ ] 主机上的历史明文 `.env` 已安全销毁（并确认未进过版本库；历史泄露项的处置见 `历史归档/secret-governance-F02-2026-09-15.md`）。
- [ ] `/run/secrets` 权限为 `0400`、属主为应用运行用户。
- [ ] 轮转演练：在密钥库更新值 → 确认应用在预期时间内使用新值（Vault Agent 场景 ≤5 分钟）。
- [ ] 密钥轮转后同步更新 `deploy/secrets/*.txt`（Compose 场景）并 `docker compose up -d` 重投。
- [ ] 演示账号开关 `DEMO_ACCOUNTS_ENABLED=false` —— 注意 **yaml 默认为 `true`**（`application.yaml:216`）、compose 默认 `false`（`docker-compose.yml:132`）；改用文件式密钥时须**显式提供该值**，否则会回落到 yaml 的 `true`。
- [x] 种子弱口令收口：加固器候选集自 2026-10-06 起为「口令等于种子哈希」（不再按 `SeedAccounts.DEMO_USERNAMES` 白名单），V1 的 8 个 + Flyway V5/V6 引入的 **162 个**（`ts01..ts12` + `st001..st150`）共 170 个账号在 `DEMO_ACCOUNTS_ENABLED=false` 时一并停用改密。**环境隔离同日落地**：V5/V6 已移入 `db/seed-demo`，`application-prod.yml` 覆盖 `spring.flyway.locations=classpath:db/migration` → 激活 prod 即整目录不执行演示种子（本机 Flyway API 对照实跑：单目录只到 V4 / `users`=8，双目录到 V6 / `users`=170），故**全新生产库首启的日志计数为 8 而非 170**；若日志读到 170，说明该库不是全新库（已带 V5/V6 历史，Flyway 对此**不报错**，见手册 §24.5 边界③），须按手册 §29 处置（附录 D **DR-18**）

### 5.1 当前密钥状态（2026-10-06 复核）

| 密钥 | 当前状态 | 影响 / 待办 |
| --- | --- | --- |
| `JWT_SECRET`、数据源/Redis 口令 | 本机以 `.env` 明文提供（`.env` 不入库，本文档不复述其值）；由 `scripts/generate-secrets.py --write` 生成 | 应用侧接管点已就绪，生产切换按 §3 执行 |
| `DEEPSEEK_API_KEY` | **未注入**（待负责人提供） | AI 生成/问答类功能降级；`/api/actuator/health` 因 AI 健康指示器缺失恒返 **503**，业务链路不受影响——**不可据此判定服务不可用** |
| `NEO4J_PASSWORD` | 本地未挂 neo4j 时不必填；`docker compose --profile rag` 下为 `:?` 必填（`:440`） | 未连通时 `kg_store.py` 回退内存图，重启即丢图数据 |
| 容器侧口令（MySQL/Redis/Neo4j/Grafana） | 仍为 `.env` 明文 | 见 §6-1 残项 |

---

## 6. 已知局限与后续

| # | 局限                                                                       | 影响                                                    | 建议                                                     |
| - | -------------------------------------------------------------------------- | ------------------------------------------------------- | -------------------------------------------------------- |
| 1 | MySQL / Redis / Neo4j / Grafana **容器自身**口令仍由 `.env` 明文提供          | 主机 `.env` 仍存留多个明文口令                          | 采用 `MYSQL_ROOT_PASSWORD_FILE`；Redis 改写 command 读取文件；Neo4j/Grafana 用其 `*_FILE`／secret 卷机制 |
| 2 | Compose 场景需手工删除 3 行明文键（无一键覆盖方案，原因见 §3.1）             | 迁移需人工确认，存在漏删风险                            | 迁移后按 §4.2 反证用例验证；或改用 K8s/Vault 路径         |
| 3 | 本项目无真实 Vault / 云 Secret Manager 实例                                | 方案为接线级交付，未做端到端联通验证                    | 具备实例后按 §4.2 逐项实测并回填证据                     |
| 4 | 密钥轮转后应用内已缓存的连接（如 Hikari 池）不会自动重建                    | 数据源口令轮转需重启容器                                | 数据源口令轮转纳入「重启窗口」；JWT 密钥可热生效（无需重启）|
| 5 | `configtree` **不做反向松散绑定**（§2.2），点分属性须以点分文件名接管         | 误用大写蛇形文件名时会**静默回落默认值**，密钥看似已接管实则未生效 | 按 §2.2 表逐键核对；用 §4.2 ④ 反证用例回归 |
| 6 | `DEEPSEEK_API_KEY` 尚未注入任何环境                                        | AI 功能降级、健康探针恒 503，掩盖真实健康信号            | 密钥到位后按 §3 任一路径接入，并把健康指示器状态纳入告警口径 |
| 7 | ~~接线件与模板注释把 Redis 口令写成 `SPRING_DATA_REDIS_PASSWORD`~~ **已修复（DR-24，2026-10-06）**：`deploy/vault/agent.hcl` 目的地、`redis-password.ctmpl` 注释、`.env.example` 示例段三处均改为 `spring.data.redis.password` | 修复前按原文接线会**读不到口令**（Redis 认证失败），且注释误导后续运维判断 | 残留：Vault／Compose 路径的**端到端联通**仍未验（本机无实例，见 3）；接线前按 §4.2 ②/④ 复核 |



---

## 7. 关联产出

- `backend/src/main/resources/application.yaml` —— `spring.config.import` 接入点（含优先级铁律注释、`:59`/`:170`/`:210`/`:237` 占位符）
- `.env.example` —— 文件式密钥说明段（含「文件名逐字一致」规则与 Redis 正确写法，DR-24 修正后口径）
- `docker-compose.yml` —— backend 服务 `DEMO_ACCOUNTS_ENABLED` 默认关闭；`:121`/`:124`/`:125` 为待删除的明文键行
- `deploy/vault/agent.hcl` + `deploy/vault/templates/*.ctmpl` —— Vault Agent 接线（Redis template 目的地待修正，见 §3.3）
- `deploy/secrets/.gitkeep` —— 密钥目录占位（内容已被 `.gitignore` 排除）
- `scripts/generate-secrets.py` —— 强密钥生成器（`--write` 写入 `.env`）
- `历史归档/secret-governance-F02-2026-09-15.md` —— 密钥治理总报告
- `docs/项目手册.md` §24.4／§24.5、附录 D **DR-18/DR-24** —— 摘要与台账

