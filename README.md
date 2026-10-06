# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

基于知识图谱+LLM的AI助教系统是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

**核心能力域**：多角色 RBAC、课程/章节/选课管理、题库与在线考试（防作弊）、作业批改与查重、知识图谱可视化与学习路径规划、AI 智能问答/出题/批改/学情建议、数据分析与学情报告、WebSocket 实时通知。

**2026-10 更新**：侧边栏改为**三级动态课程导航**（考试/成绩/作业/图谱/教学工具收敛为每门课程下的独立页签，93 条路由 / 281 个 `.vue`）；知识图谱改为**可拖拽力导向布局**并修正学习路径规划语义；Flyway 新增 **V5 富种子**（48 表互联、12 教师 + 150 学生、春秋双学期）与 **V6 计算机网络示范课**（course 4007：14 章节 / 65 知识点 / 120 关系边 / 8 套试卷 / 60 条选课），供参赛演示直接使用；**2026-10-06 起这两个脚本移入 `db/seed-demo`，`prod` profile 的 Flyway `locations` 整目录排除 → 演示数据不进生产库**（口径见 `docs/项目手册.md` §13.1，生产约束与实测取证见 §24.5）。

> **职责边界**：知识图谱引擎（图谱构建、多跳推理、GraphRAG）与 RAG 检索引擎（BM25 / 向量检索 / embedding / 融合）均由团队另一成员以 Python 微服务实现（kg **8001** / rag **8002**，两端均已接入真实引擎实现）。本端（Java 后端）实现**消费侧**与**标准化集成层** `com.aicourse.integration`：`ContractPaths` 常量（调用约定唯一事实来源，55 个契约常量）+ `KnowledgeGraphContract` / `RagContract` 契约接口（强类型 record）+ 双实现（本地 BFS / 禁用态默认不依赖 Python；HTTP 远端经 `knowledge-graph.enabled` / `rag.enabled=true` 调 Python 8001 / 8002，成功时返回 `mode=ENGINE`）。其中 `POST /api/knowledge/path/plan` 在引擎未启用/不可达时由消费侧**本地 BFS 兜底**；`max_hops` 自 2026-10-05 起**仅用于「是否采纳」判定、不再参与遍历剪枝**，上限不足时仍回传真实最短跳数（语义表见 `docs/项目手册.md` §35.2）。RAG 未接入时返回 `{"code":503}` 且 AI 问答自动回退纯 LLM。**LoRA 微调（8003）契约与消费侧已就绪，Python 端仍无实现**，启用即 503 降级。**完整对接契约见 `docs/项目手册.md`（第八部分，唯一标准基准）。**

> **⚠️ 参考项目方法学边界（避免误导）**：`大创赛/类似项目/DeepSeek-RAG-Chatbot` 的 GraphRAG 实体抽取采用正则 `\b[A-Z][a-z]+...`，**仅支持英文实体**，且为单机通用文档问答工具，与本项目的**中文知识图谱**（`knowledge_points` / `knowledge_point_relations`）在语言适配、领域建模、工程规模上均不可类比。**请勿将其作为本项目中文 KG 方案的对标或引用依据**；本项目中文 KG 的关系建模以 `相关论文/`（杜彩丽 IPA/IREM、中职计算机网络多源异构 KG 等）与 PRD 接口矩阵为准。

---

## 文档导航

项目文档已整合为**单一权威手册** `docs/项目手册.md`（后续所有任务唯一依据，统一标准基准），按**十一个部分** + 四个附录组织：

| 部分                              | 主题                                                           |
| --------------------------------- | -------------------------------------------------------------- |
| 第一部分 项目总览与背景           | 定位目标、参赛与申报要点、外部研究支撑精华                     |
| 第二部分 需求与技术基线           | 角色权限、三端功能、AI 能力、核心流程、非功能需求、产品演进    |
| 第三部分 系统架构                 | 总体架构、技术栈、目录结构、核心设计决策、标准化集成层         |
| 第四部分 数据模型与接口           | 48 表数据模型、接口矩阵、功能需求点完成明细                    |
| **第五部分 已实现功能**（活章节） | 已落地并验证的功能清单，随任务推进持续同步                     |
| 第六部分 开发指南                 | 环境、启动、编码规范、测试、质量门禁、CI、开发协作             |
| 第七部分 部署与运维               | 部署、健康检查、日志、备份、故障、安全加固、性能、监控告警体系 |
| 第八部分 Python 微服务集成契约    | KG(8001)/RAG(8002)/LoRA(8003) 完整契约与降级语义               |
| 第九部分 测试·质量·完成度评估     | 测试体系、质量门禁、完成度量化结论、安全机制、验收标准         |
| **第十部分 下一步计划**（活章节） | P0/P1/P2 待办清单与状态标记，随任务推进持续同步                |
| **第十一部分 产品化转化路线**（2026-09-14 新增） | 多租户 SaaS / 小程序 / 桌面形态可行性、目标形态、核心模块、架构与 M1–M4 路线（详见 `docs/产品化转化方案.md`） |
| 附录A 术语表                      | 领域术语与缩略语统一释义                                       |
| 附录B 统计口径（唯一基准）        | 全局统计数字唯一来源，取代旧「数据口径权威总表」               |
| 附录C 贡献与 PR 流程              | 分支策略、编码规范、PR 流程（原 CONTRIBUTING 内容）            |
| 附录D 审查修正台账                | 逐轮对抗式复核的实测清单、已修复缺陷确认与新增发现             |

> 快速索引：架构设计 / 开发测试 / 部署运维 / 知识图谱与 RAG 契约 / 需求与路线图 / 交付进度 / **统计口径唯一基准** → 均见 `docs/项目手册.md` 相应部分。

**文档分层（2026-09-15 收口）**——四层各司其职，避免平行文档：

| 层 | 位置 | 说明 |
| --- | --- | --- |
| ① 权威规范 | `docs/项目手册.md` | **唯一事实基准**，与他人冲突时以手册为准 |
| ② 导航 | `docs/文档索引.md` | 全仓文档的定位、时效与读者地图 |
| ③ 专题 | `docs/端到端验证与缺陷修复总报告.md`<br>`docs/产品化转化方案.md`<br>`docs/密钥注入与Secret管理.md` | 单一专题的**当前有效**结论／方案／指南 |
| ④ 归档 | `docs/历史归档/` | 时点性报告**冻结**原文（**20 份报告 + 1 份目录 README = 21 个文件**，2026-10-06 实测；收录判据与冻结声明见该目录 `README.md`）|

> 历轮审查与端到端验证的**结论索引**见手册 **附录D 审查修正台账**（含每条发现的处置状态与原文溯源坐标）。

---

## 技术栈

| 层级               | 技术                         | 版本                  |
| ------------------ | ---------------------------- | --------------------- |
| 后端框架           | Spring Boot                  | 3.5.15（JDK 21）      |
| ORM                | MyBatis-Plus                 | 3.5.7                 |
| 安全               | Spring Security + JWT        | 6.x / jjwt 0.12.6     |
| 数据库             | MySQL                        | 8.0+                  |
| 缓存               | Redis                        | 7.x（**必选**）       |
| 前端框架           | Vue 3                        | 3.5.x                 |
| 构建工具           | Vite                         | 8.x                   |
| UI库               | Element Plus                 | 2.4.x                 |
| 状态 / 路由 / 图表 | Pinia / Vue Router / ECharts | 2.1.x / 4.2.x / 6.1.x |
| 实时通信           | WebSocket                    | 集成Spring WebSocket  |
| 限流熔断           | Sentinel                     | 下游流控 + 慢调用熔断 |
| 数据库迁移         | Flyway                       | 6 个脚本：结构迁移 V1–V4（`db/migration`，全环境）+ 演示种子 V5–V6（`db/seed-demo`，prod 排除） |
| 图数据库（可选）   | Neo4j                        | 5.x，compose `rag` profile；缺失时 KG 回落内存图 |
| 代码质量门禁       | JaCoCo / SpotBugs / ArchUnit | LINE≥85%+BRANCH≥65%（阻断）/ 0 bug（阻断）/ 6 条架构守护 |
| 前端 Lint          | ESLint                       | **9.39.5 + flat config**（`frontend/eslint.config.js`） |
| KB/RAG（Python）   | FastAPI 契约服务             | rag 8002 / kg 8001    |

## Python 侧服务（KB / RAG）

Java 后端通过 HTTP 契约（`com.aicourse.integration.rag.RagContract`）与 Python 微服务对接，代码位于 `python-services/`：

| 服务 | 目录 | 端口 | 说明 |
| ---- | ---- | ---- | ---- |
| 知识图谱服务 | `python-services/kg_service/` | 8001 | **11 个契约端点**；`kg_store.py` 支持 **Neo4j 与内存双后端**（Neo4j 不可达时自动回落内存图），GraphRAG 多跳推理与学习路径规划；CI 护栏 `test_kg_contract.py`（含 Neo4j service 容器） |
| RAG 检索引擎 | `python-services/rag_service/` | 8002 | **9 个契约端点** + `rag_engine.py` 真实混合检索引擎（BM25 + 向量 + RRF 融合，重依赖懒加载）；CI 护栏 `test_rag_contract.py` |
| LoRA 微调服务 | —（**Python 端尚无实现**） | 8003 | Java 侧 `ContractPaths` 的 8 条 `/api/v1/fine-tune/*` 契约与消费侧已就绪，启用即 503 降级；等待队友交付 |

容器化启动知识图谱栈（Neo4j）：`docker compose --profile rag up -d neo4j`（需先在 `.env` 配置 `NEO4J_PASSWORD`，见 `.env.example`）。**未拉起 Neo4j 不阻塞**：KG 自动回落内存图，功能可用但持久化缺失。

> 早期 RAG 成员上传的 Streamlit 原型（原 `python-services/deepgraph_rag/`，端口 8501）不参与正式功能，已整体归档至仓库根 `archive/deepgraph_rag/`，仅作历史参考。

---

## 快速开始

项目提供两条启动路径：**本地开发**（默认，推荐——使用本机 MySQL / Redis，Docker 仅作兜底）与 **容器化部署**（备用/上线——全套 Docker）。详细步骤见 `docs/项目手册.md`（第六部分开发 / 第七部分部署）。

### 方式一：本地开发（推荐）

前置：JDK 21、Node 20.19+、MySQL 8.0+、Redis 7.x（必选，启动强校验）。

```bash
# 数据库初始化（首次）
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql

# 后端（一键脚本：自动加载 .env、校验 JDK 21、检测 MySQL/Redis）
start-backend.bat
# 或手动：cd backend && ./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 前端
start-frontend.bat
# 或手动：cd frontend && npm install && npm run dev
```

访问：前端 http://localhost:5173 ｜ API http://localhost:8080/api ｜ Swagger http://localhost:8080/api/swagger-ui.html

> 前端 dev server 为 `strictPort: true`：**5173 被其他项目占用时会直接报错退出**（不会自动换端口）。改用
> `cd frontend && npx vite --port 5174` 即可——后端 CORS 白名单已含 `5173 / 5174 / 5175`（`application.yaml:219-229`，
> `localhost` 与 `127.0.0.1` 两种写法），换端口不影响联调。

> 本机未装 MySQL / Redis 时：`docker compose up -d mysql redis` 拉起基础设施容器（MySQL 宿主端口 3307），`start-backend.bat` 自动切换数据源。

#### 健康检查与常见排障（2026-09-14 补充）

- **探活**：`GET http://localhost:8080/api/actuator/health`（返回 200 即服务可用）。
- **不要**因为探活返回 `503 / {"status":"DOWN"}` 就判定服务不可用：整体健康度会把**可选依赖**一并聚合。
  典型场景是**未注入 AI / 邮件凭据**——`llmProvider`（未配置 API Key）与 `mail`（SMTP 认证失败）会报 DOWN，
  但业务接口全部正常。以完整 `.env` 启动（含 `DEEPSEEK_API_KEY` / `MAIL_USERNAME` / `MAIL_PASSWORD`）后即为 `UP`。
- 需要区分「服务就绪」时请用分组端点：`/api/actuator/health/readiness`（K8s/容器探针用的就是这个），
  它不受 AI / 邮件等可选依赖影响。
- **手动用 jar 启动务必显式指定端口**：`java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --server.port=8080`。
  否则端口可能被外部环境变量覆盖，出现 `Port xxxxx was already in use` 而并非 8080 冲突。
- 本机 Redis 为**无密码**模式：手动启动**勿注入** `SPRING_DATA_REDIS_PASSWORD`（注入会使 RedisStartupValidator fail-fast）。
- 本地 HTTP 联调需关闭传输层强制：`COOKIE_SECURE=false`、`REQUIRE_HTTPS=false`、`CSRF_ENABLED=false`
  （`start-backend.bat` 已代劳）。CSRF 校验失败会返回 403 且提示缺失 `X-XSRF-TOKEN`。

### 方式二：Docker 容器化部署（备用 / 上线）

```bash
cp .env.example .env      # Windows: copy .env.example .env（共 32 个键，填入真实强密码与密钥）
docker compose --profile full up -d --build
# 可选叠加 profile（compose 共 12 个服务 / 12 个命名卷）：
#   --profile ops      每日备份（mysql-backup + cron sidecar）
#   --profile monitor  Prometheus + Grafana + Loki + Promtail + node-exporter + Alertmanager
#   --profile rag      Neo4j 5-community（知识图谱真图库后端，需 NEO4J_PASSWORD）
# 注：`mysql` 与 `redis` 无 profile，任何 `up` 都会拉起；其余服务须显式指定 profile。
```

> ⚠️ **务必先选一条路径再启动。** `--profile full` 起的是一个**明文 HTTP 单机栈**：`frontend/nginx.conf`
> 只监听 **80**、compose **没有挂载任何证书卷**，因此不存在 TLS 终止。而 backend 的
> `REQUIRE_HTTPS`/`COOKIE_SECURE` 生产默认是 `true` —— 若照着下面的默认值直接启动，
> `/api/**` 会被 `requiresSecure()` 302 重定向到**并不存在的 443**，表现为**全站 API 不可用**
> （首页能打开、所有接口报错）。这是本部署形态下最容易踩的坑，故拆成两条路径：
>
> 📌 **修复 B3（2026-09-16）**：`REQUIRE_HTTPS` / `COOKIE_SECURE` 现为**全 profile 可覆盖**（含 `prod`）。
> 此前 `application-prod.yml` 把 `require-https` 硬编码为 `true`，会**静默忽略**上面的 `.env` 开关——即容器/主配置填了 `REQUIRE_HTTPS=false` 也无用，明文栈无法自救。现已改为默认 `true`、可被环境变量覆盖，两条路径的开关在 `prod` profile 下同样生效。

#### 前置条件：必须填写的环境变量

`.env` 中以下变量**必须**替换为真实值，留空或照抄占位符会直接拒绝启动：

> 推荐一次性生成全部强密钥（脚本已内置 `PasswordPolicy` 自检，生成即必过）：
> `python scripts/generate-secrets.py --write` —— 直接写入 `.env` 对应键；去掉 `--write` 则仅打印。

| 变量 | 要求 | 未替换的后果 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | ≥16 位强随机 | compose 插值失败、直接报错退出 |
| `JWT_SECRET` | ≥32 字节随机（`openssl rand -base64 48`） | compose 插值失败、直接报错退出 |
| `REDIS_PASSWORD` | 强随机（backend 与健康探针自动同步） | 删掉该行 → compose 插值失败；仅留占位符 → 能启动，但 Redis 以**弱口令**运行 |
| `GRAFANA_ADMIN_PASSWORD` | 强随机（叠加 `--profile monitor` 时） | compose 插值失败、直接报错退出 |
| `NEO4J_PASSWORD` | 强随机（叠加 `--profile rag` 时） | compose 以 `${NEO4J_PASSWORD:?…}` **插值失败、直接报错退出**（`docker-compose.yml:440`） |
| `MYSQL_APP_PASSWORD` | 可留空 | 留空则 compose 回退用 `MYSQL_ROOT_PASSWORD` 连接业务库（`${MYSQL_APP_PASSWORD:-${MYSQL_ROOT_PASSWORD}}`，`docker-compose.yml:121`）；生产建议独立建业务账号 |
| `APP_ADMIN_INITIAL_PASSWORD` | 8–32 位，含大小写字母 + 数字 + 特殊字符（**共 4 类**） | **启动 fail-fast 拒绝启动**（`.env.example` 里的 `CHANGE_ME_*` 是**刻意不合规**的占位符，属有意设计——宁可拒绝启动，也不允许"照抄示例"形成公开的已知 admin 口令）。注意：`openssl rand -base64 32` 输出 44 字符、**超 8–32 上限**，不要用它生成此项，请用上面的脚本 |

> ⚠️ **`DEMO_ACCOUNTS_ENABLED` 默认值两处不一致（已知，按环境生效范围区分）**：`application.yaml:216` 为 `${DEMO_ACCOUNTS_ENABLED:true}`（本地开发默认保留演示账号），`docker-compose.yml:132` 为 `${DEMO_ACCOUNTS_ENABLED:-false}`（容器化默认加固）。容器里未显式传值即为 `false`（自动停用并改密），本地 `start-backend.bat` 未设则为 `true`。**生产必须显式 `=false`**，并注意其收敛范围自 2026-10-06 起为「口令等于种子哈希」的**全部 170 个**种子账号（V1 内置 8 个 + V5/V6 号段 162 个；此前只覆盖 V1 的 8 个）。⚠️ 同日 V5/V6 已移出 prod 的 Flyway `locations`，故**全新生产库里实际只有 V1 的 8 个**待收敛，170 这个数字只在开发库或被误用的旧库上出现（见下方「生产边界」与手册 §24.5 / DR-18）。

#### 路径 ①：本地 / 内网评估（HTTP 明文，仅限本机或内网）

在 `.env` 中**显式**放宽传输层强制：

```dotenv
REQUIRE_HTTPS=false
COOKIE_SECURE=false
CSRF_ENABLED=true      # 保持 true，不要关
```

- **原因**：nginx 仅监听 80、compose 无证书卷，没有任何 TLS 终止；不关掉强制，`/api/**` 会被 302 到不存在的 443。
- **风险**：令牌与口令将**明文传输**，可被同网段嗅探。**禁止用于公网**，仅限本机 / 受信内网。
- 访问：前端 http://localhost ｜ API http://localhost:8080/api
- 跨域：前端经 nginx 反代 `/api` 与后端**同源**，无需开放 CORS，`CORS_ALLOWED_ORIGINS` 可留空（留空即 fail-closed，拒绝一切跨域来源）。

#### 路径 ②：生产上线（保持安全默认 `true`）

**保持** `REQUIRE_HTTPS=true`、`COOKIE_SECURE=true`（不要为了"先跑起来"改成 false），并**先满足以下前置条件之一**，
否则全站 API 同样不可用：

1. **前置 TLS 终止反代（推荐）**：在本栈之前再放一层反向代理 / 负载均衡（入口 nginx、SLB、CDN 等）终止 TLS，
   并且该代理**必须回传** `X-Forwarded-Proto: https`，后端才认定为安全通道；或
2. **为前端容器补齐 443 + 证书**：给 `frontend/nginx.conf` 增加 `listen 443 ssl` 与证书挂载，并把 compose 的端口映射改为 `443:443`。

> 两条路径下 `CSRF_ENABLED` 都应保持 `true`。生产部署前请逐项核对 `docs/项目手册.md` 第七部分安全加固检查清单。

---

## 演示账号

| 角色   | 用户名               | 密码   | 说明                     |
| ------ | -------------------- | ------ | ------------------------ |
| 管理员 | admin                | 123456 | 系统管理员，拥有全部权限 |
| 教师   | teacher / teacher2   | 123456 | 张教授 / 李副教授        |
| 学生   | student / student2~5 | 123456 | 王同学等                 |

以上 8 个为 **V1 内置**演示账号（`SeedAccounts.DEMO_USERNAMES` 单一事实来源）。导入富种子迁移后另有：

| 来源    | 用户名             | 密码   | 说明                                                       |
| ------- | ------------------ | ------ | ---------------------------------------------------------- |
| `V5` 富种子 | `ts01`…`ts12`      | 123456 | 12 位教师，邮箱 `ts01@aicourse.com`；另 150 名学生 `st001`…`st150`（`@stu.aicourse.com`） |
| `V6` 示范课 | 同上号段内选课学生 | 123456 | 计算机网络示范课 **course 4007**，60 条选课 / 65 知识点 / 120 关系边 |

> ⚠️ **生产边界（务必读）**：演示数据不进生产库，2026-10-06 已落地并实跑取证（`docs/项目手册.md` §24.5 / 附录 D **DR-18**）。两道防线各自到位：
> ① **数据面**：V5/V6 移入 `db/seed-demo`，`application-prod.yml` 把 `spring.flyway.locations` 覆盖为 `classpath:db/migration` 单目录 → 激活 prod（jar 直跑或 compose 的 `SPRING_PROFILES_ACTIVE`）后全新生产库只跑到 **V4**，162 个号段演示账号与示范课 4007 不入库（本机 Flyway API 对照实跑：单目录 8 账号 / 无 4007，双目录 170 账号 / 有 4007）；该布局由 `DemoSeedMigrationIsolationTest` 5 例钉住。
> ② **弱口令面**：`DEMO_ACCOUNTS_ENABLED=false` 的启动期加固器按**「口令仍等于种子哈希」**筛选，把 V1 内置（因 `baseline-on-migrate` 而无法排除）的 **8 个**账号停用并重置为不可知口令；同口径下若跑过 V5/V6 也一并收敛（历史上共 170 个）。已改密账号绝不覆盖，加固幂等。
> ⚠️ **唯一残余风险是部署流程，不是代码**：把一个灌过 V5/V6 的开发库直接当生产库**不会报错**——Flyway 11.7.2 默认 `ignoreMigrationPatterns=[*:future]`，找不到的已应用迁移被静默忽略，只留一条 WARN。因此上线前须按 §29 核对生产库 `flyway_schema_history` 最高版本为 **4**。另注：演示 SQL 仍打进 jar（约 5.9 MB，prod 不执行），只占包体。
>
> 演示密码仅限开发/演示环境，生产环境务必修改（见 `docs/项目手册.md` 第七部分 §29 安全加固检查清单）。

---

## 测试与质量

```bash
# 后端：单元测试 + 集成测试 + JaCoCo/SpotBugs/ArchUnit 门禁
cd backend && mvn test -Dtest='!*IT,!Phase1Phase3IntegrationTest' -DfailIfNoTests=false
# 完整门禁（含覆盖率与 SpotBugs 阻断）：mvn -B verify

# 前端：ESLint（9 + flat config）+ 类型棘轮 + vitest + 构建校验
cd frontend && npm run lint && npm run typecheck:gate && npx vitest run && npm run build
```

**当前实测基线**（口径与逐行出处见 `docs/项目手册.md` 附录 B / §43；后端各行取自 2026-10-06 19:19 本机 `mvn -B -o clean verify -DskipITs` 实跑，`-DskipITs` 因本机无 Docker，Testcontainers 系 7 个 IT 只在 CI 取数。⚠️ JaCoCo 的 exec 落在 `${user.home}`、不随 `clean` 重置，见手册 §3 告警块）：

| 门禁 | 读数 |
| --- | --- |
| 后端单测 | **2574 用例 / 0 失败 / 0 错误 / 0 跳过**（195 个测试源文件，188 份 surefire 报告） |
| JaCoCo | **LINE 87.58% / BRANCH 66.19%**（指令 86.74%，341 类 / 336 类有覆盖）→ 门禁达标 |
| ArchUnit | **6 条**架构守护全通过 |
| SpotBugs | **0 缺陷**，`failOnError=true` 已硬阻断（13 个框架性 `<Match>` 豁免） |
| 前端单测 | **54 文件 / 1093 用例全绿**（2026-10-06 本机实测） |
| 前端 ESLint | **0 error / 0 warning**（ESLint 9 flat config） |
| 前端类型检查 | `vue-tsc` 存量 **586 条 / 107 文件**，棘轮基线只许下调 |
| CI | 3 个工作流文件；`ci.yml` **10 个作业**（含 `secret-scan`；`frontend-e2e` 暂为 `workflow_dispatch` 手动触发） |

质量门禁基线（渐进收敛制，只许上调不得下调）：JaCoCo LINE ≥ 85% / BRANCH ≥ 65%（生产收敛目标指令覆盖 ≥ 85%）、SpotBugs 0 error、ArchUnit 架构约束。测试方法、契约测试清单与 CI 流水线见 `docs/项目手册.md` 第六部分。

### 端到端全链路与压力测试（四件套）

`scripts/e2e/` 下有**四件套**实机脚本（仅依赖 Python 标准库）。**改动后端后建议全跑**；历史结果快照在 `archive/deliverables/`，本地重跑生成的 `e2e_*_result.json` 落在脚本目录（已 gitignore）。

```bash
# 前置：MySQL(3306) 与 Redis(6379) 已运行；仓库根有 .env；
#       backend/target/backend-0.0.1-SNAPSHOT.jar 已是最新（改了后端代码务必先重新打包）

# ── 第 1 轮：功能姿态（CSRF 关、登录限流 200）────────────────────
# 必须以「后台任务」方式常驻启动，前台运行会阻塞且进程可能被回收
python scripts/run-backend-for-e2e.py functional

cd scripts/e2e
python e2e_verify_network_course.py   # ① 主链路 75 例（建课→审批→资料→作业→考试→成绩）
python e2e_deep_verify.py             # ② 多维深度 49 例（A 协同/B 边界/C 越权/D 一致性/E 分片下线态/F 退回重交/G 版本）
python e2e_stress_verify.py           # ④ 压力与稳定性（加 --quick 走快速档）

# ── 第 2 轮：安全姿态（CSRF 默认开、登录限流默认阈值）──────────────
# 先停掉功能姿态后端，再：
python scripts/run-backend-for-e2e.py secure
cd scripts/e2e
python e2e_security_verify.py         # ③ 安全维度 7 例（CSRF 双提交 + 双层登录限流）
```

> **姿态纪律**：功能轮与安全轮**不可混用配置** —— 安全轮若沿用功能姿态（CSRF 关、限流放宽），CSRF 与限流用例会全部失效。两轮起测前脚本均会自行 `FLUSHDB`，无需手工清 Redis。

> **登录保护为两层**：① 速率层 `RateLimitFilter`（IP 滑动窗口 10 次/60 秒）；② 尝试层 `AuthSessionComponent`（同账号连续失败 5 次锁定 15 分钟）。尝试层阈值更低、失败路径下先触发，验证时须分层断言。详见 `docs/项目手册.md` 附录 D 的 2026-09-17 条目。

最近一次实测（2026-09-17）：E2E 四件套 **131/131 全绿**；压力测试 62,764 请求 / 峰值 1,808.6 req/s / 成功率 100% / 5xx 0。报告见 `docs/历史归档/端到端全链路与压力测试报告-2026-09-17.md`。

> ⚠️ **该基线已滞后于当前代码**：V5/V6 富种子、三级课程导航、KG 力导向与路径规划语义均在该次全绿**之后**落地，四件套尚未复跑；`ci.yml` 的 `frontend-e2e` 作业也仍为 `workflow_dispatch` 手动触发。**演示或放行前建议实跑一轮**（可用示范课 4007 作主链路对象），登记见 `docs/项目手册.md` 附录 D **DR-20**。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

- 完整许可证文本见仓库根目录 [`LICENSE`](LICENSE)。
- 参与贡献请阅读 [`docs/项目手册.md`](docs/项目手册.md)（附录 C：分支策略、编码规范、质量门禁与 PR 流程）。
- 报告 Bug / 提需求请使用 Issue 模板（`.github/ISSUE_TEMPLATE/`）。
