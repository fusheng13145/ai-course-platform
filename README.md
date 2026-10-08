# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

基于知识图谱+LLM的AI助教系统是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

**核心能力域**：多角色 RBAC、课程/章节/选课管理、题库与在线考试（防作弊）、作业批改与查重、知识图谱可视化与学习路径规划、AI 智能问答/出题/批改/学情建议、数据分析与学情报告、WebSocket 实时通知。

**2026-10 更新**：侧边栏改为**三级动态课程导航**（考试/成绩/作业/图谱/教学工具收敛为每门课程下的独立页签，93 条路由 / 281 个 `.vue`）；知识图谱改为**可拖拽力导向布局**并修正学习路径规划语义；Flyway 新增 **V5 富种子**（48 表互联、12 教师 + 150 学生、春秋双学期）与 **V6 计算机网络示范课**（course 4007：14 章节 / 65 知识点 / 120 关系边 / 8 套试卷 / 60 条选课），供参赛演示直接使用；**2026-10-06 起这两个脚本移入 `db/seed-demo`，`prod` profile 的 Flyway `locations` 整目录排除 → 演示数据不进生产库**；同批另加 **V7 前向修正**（V6 落库后的示范课文案已按实测 65 知识点 / 56 题改正，V6 本体因 Flyway 校验和冻结不可回改，见手册 DR-19）（口径见 `docs/项目手册.md` §13.1，生产约束与实测取证见 §24.5）。

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
| ③ 专题 | `docs/产品化转化方案.md`<br>`docs/密钥注入与Secret管理.md` | 单一专题的**当前有效**方案／操作指南。⚠️ **端到端与压力结论不再另立专题文档**——唯一来源是手册 **§43**，缺陷台账见手册**附录 D** |
| ④ 归档 | `docs/历史归档/` | 时点性报告**冻结**原文（**21 份报告 + 1 份目录 README = 22 个文件**，2026-10-08 实测；收录判据与冻结声明见该目录 `README.md`）|

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
| 数据库迁移         | Flyway                       | 7 个脚本：结构迁移 V1–V4（`db/migration`，全环境）+ 演示种子 V5–V7（`db/seed-demo`，prod 排除） |
| 图数据库（可选）   | Neo4j                        | 5.x，本阶段用**本机独立实例**（compose `rag` 路径已搁置，§18.5）；缺失时 KG 回落内存图 |
| 代码质量门禁       | JaCoCo / SpotBugs / ArchUnit | LINE≥85%+BRANCH≥65%（阻断）/ 0 bug（阻断）/ 6 条架构守护 |
| 前端 Lint          | ESLint                       | **9.39.5 + flat config**（`frontend/eslint.config.js`） |
| KB/RAG（Python）   | FastAPI 契约服务             | rag 8002 / kg 8001    |

## Python 侧服务（KB / RAG）

Java 后端通过 HTTP 契约（`com.aicourse.integration.rag.RagContract`）与 Python 微服务对接，代码位于 `python-services/`：

| 服务 | 目录 | 端口 | 说明 |
| ---- | ---- | ---- | ---- |
| 知识图谱服务 | `python-services/kg_service/` | 8001 | **11 个契约端点**；`kg_store.py` 支持 **Neo4j 与内存双后端**（Neo4j 不可达时自动回落内存图），GraphRAG 多跳推理与学习路径规划；CI 护栏 `test_kg_contract.py`（含 Neo4j service 容器） |
| RAG 检索引擎 | `python-services/rag_service/` | 8002 | **9 个契约端点** + `rag_engine.py` 真实混合检索引擎（BM25 + 向量 + RRF 融合，重依赖懒加载）；CI 护栏 `test_rag_contract.py`。⚠️ 语料落在 gitignore 的 `data/`，演示前用 `python scripts\seed-rag-corpus.py 1` 从 `scripts/rag_corpus.json` 重放并重建索引 |
| LoRA 微调服务 | —（**Python 端尚无实现**） | 8003 | Java 侧 `ContractPaths` 的 8 条 `/api/v1/fine-tune/*` 契约与消费侧已就绪，启用即 503 降级；等待队友交付 |

容器化启动知识图谱栈（`docker compose --profile rag up -d neo4j`，需先在 `.env` 配 `NEO4J_PASSWORD`）的用法与前置已随 2026-10-08 的编排搁置**整体转入手册 §24.2**（含重新启用前提清单①–⑤），本阶段不再执行。**但"搁置 Docker"不等于"搁置引擎"**：本阶段以本机独立 Neo4j 5.26 + `uvicorn` 两进程运行 KG(8001) / RAG(8002)，`start-backend.bat` 探到监听后自动开启 Java 侧对接，2026-10-08 运行验收实测路径规划 / 子图 / 摄取与检索问答均为 `mode=ENGINE`。**未起 Neo4j 仍不阻塞**：KG 自动回落内存图。三进程的启动顺序、必须导出到引擎进程的环境变量与取证清单见手册 **§18.5**。

> 早期 RAG 成员上传的 Streamlit 原型（原 `python-services/deepgraph_rag/`，端口 8501）不参与正式功能，已整体归档至仓库根 `archive/deepgraph_rag/`，仅作历史参考。

---

## 快速开始

项目提供两条启动路径：**本地开发**（**本阶段唯一使用的路径**——本机 MySQL / Redis + 两份 `.bat` 启动器）与 **容器化部署**（**2026-10-08 起整体搁置**：编排文件、镜像版本、用法与重新启用前提清单已全部归档到 `docs/项目手册.md` §24.2，仓库文件一件没删、CI 的 `docker`／`trivy-scan` 作业照常按容器判定，但**本机不再执行**）。详细步骤见 `docs/项目手册.md`（第六部分开发 / 第七部分部署）。

> ⚠️ **本阶段的依赖纪律**：开发阶段**仅使用本地服务**。**必要**依赖（MySQL 3306 / Redis 6379 / JDK 21 / Node 20.19+）由两份 `.bat` 启动时自检，缺失即报错退出；**可选**依赖（Neo4j 7687 / KG 8001 / RAG 8002）缺失只降级不阻塞——`KG_ENABLED` / `RAG_ENABLED` 保持关闭，AI 走 Java 原生 Provider，功能仍可用。任一必要依赖本机没有时，**不要自行 `docker pull`、也不要安装替代物 → 直接上报项目主**。可选依赖的启动方式与本轮运行验收见手册 **§18.5**。

### 方式一：本地开发（推荐）

前置：JDK 21、Node 20.19+，以及**本机在跑的 MySQL 8.4（3306）与 Redis（6379）**。缺任何依赖 → **上报项目主，不要自行拉容器**（本阶段 Docker 编排搁置，手册 §17 / §24.2）。AI 图谱/检索引擎（Neo4j 7687 + KG 8001 + RAG 8002）为**可选**前置：不起也能演示（走 Java 原生 Provider），起法与启动顺序见手册 **§18.5**——`start-backend.bat` 会探测它们并自动开启对接。

```bash
# 数据库初始化（首次）
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql

start-backend.bat        # 后端：六步自检后 spring-boot:run
start-frontend.bat       # 前端：另开一个终端
```

两个脚本各自做什么（逐步口径见手册 **§18.2 / §18.3**）：

- **`start-backend.bat`**：读 `.env`（缺 `JWT_SECRET` / `MYSQL_ROOT_PASSWORD` 直接 fail-fast，无弱默认）→ 校验 JDK 21 → 探测本机 3306 / 6379（未监听只提示 `net start MySQL84` / `net start Redis` 后退出，**脚本已不含任何 docker 分支**）→ 检查可选的 `DEEPSEEK_API_KEY`（先判在不在、再按前缀 `sk-` 判形状，占位串打 `[WARN]` 而非 `[OK]`，全程不打印值，DR-38）→ 探测 8001 / 8002 引擎，`.env` 未显式给开关时**为本次进程**自动 `KG_ENABLED` / `RAG_ENABLED=true` → 8080 被占用时**默认不启第二个实例**（双实例共用一套 MySQL/Redis 却各自计限流、日志分叉，DR-36）→ 以 `profiles=local` 启动。
- **`start-frontend.bat`**：定位 Node → 依赖检查（`node_modules` 已存在则跳过 `install`，另提示 lockfile 时间戳供 `npm ci` 判断）→ 探后端 `GET /api/actuator/health/probe` → 在 **5173 / 5174 / 5175** 中取第一个空闲端口起 Vite。

访问：前端 http://localhost:5173（脚本自动择空）｜ API http://localhost:8080/api ｜ Swagger http://localhost:8080/api/swagger-ui.html ｜ 探活 http://localhost:8080/api/actuator/health/probe

> 📦 **依赖源已迁移到官方 registry**（2026-10-08，DR-35 相邻项闭合）：`frontend/package-lock.json` 的 509 条 `resolved` 已全部改为 `registry.npmjs.org`（仅 `cdn.sheetjs.com` 的 xlsx tarball 按原样保留）。**本机不需要为此改 npm 配置**——npm 默认的 `replace-registry-host=npmjs` 会把官方主机名重写到**你配置的**镜像源，所以同一份 lockfile 同时服务 CI（官方源）与本机（npmmirror 提速），`integrity` 哈希与源无关。取证见手册 §24.2.3 根因 G 续写。

> 前端 dev server 为 `strictPort: true`：5173 被其他应用占用时 Vite **直接退出而不自动换口**——`start-frontend.bat` 因此预先探测端口并显式传 `--port`。手动起则用 `cd frontend && npx vite --port 5174`。后端 CORS 白名单只含 `5173 / 5174 / 5175`（`application.yaml:220-226`，`localhost` 与 `127.0.0.1` 两种写法），**5176 及以上端口登录会被 403 拒绝**。本机 Vite 实测监听 `[::1]`，请用 `http://localhost:5173/`（`127.0.0.1` 反而连不上）。

> 本机未装 MySQL / Redis 时：**按上面的依赖纪律上报项目主**，不要自行拉容器（编排搁置，用法与配置留档见手册 §24.2）。
> `start-backend.bat` 里原先"探测 3307 则切数据源"的分支**已删除**——脚本现在不含任何 docker 路径。

#### 健康检查与常见排障（2026-09-14 补充）

- **探活**：人读整体状态用 `GET http://localhost:8080/api/actuator/health`；**编排/容器探针请用 `GET /api/actuator/health/probe`**（返回 200 即实例可服务）。
- **不要**因为 `/health` 返回 `503 / {"status":"DOWN"}` 就判定服务不可用：整体健康度会把**可选依赖**一并聚合。
  典型场景是**未注入 AI / 邮件凭据**——`llmProvider`（未配置 API Key）与 `mail`（SMTP 认证失败）会报 DOWN，
  但业务接口全部正常。以完整 `.env` 启动（含 `DEEPSEEK_API_KEY` / `MAIL_USERNAME` / `MAIL_PASSWORD`）后即为 `UP`。
- **`probe` 组**（`readinessState` + `database` + `redis` + `diskSpace`）就是为这条边界建的：AI/邮件凭据缺失属**按契约降级**，不属"实例不可服务"，所以不计入探针。
  ⚠️ 反例：compose 的 backend 探针原先打的是综合 `/health`，在没有 `DEEPSEEK_API_KEY` 的机器上恒 503 → 容器反复重启、`frontend` 的 `service_healthy` 依赖卡死、整栈起不来（2026-10-07 实跑发现，见手册 §25 / 附录 D **DR-28**）。
- 另可用 `GET /api/actuator/health/readiness` / `/liveness`（Spring 内建组，只含生命周期状态，不含 DB/Redis）。
- **手动用 jar 启动务必显式指定端口**：`java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --server.port=8080`。
  否则端口可能被外部环境变量覆盖，出现 `Port xxxxx was already in use` 而并非 8080 冲突。
- 本机 Redis 为**无密码**模式：手动启动**勿注入** `SPRING_DATA_REDIS_PASSWORD`（注入会使 RedisStartupValidator fail-fast）。
- 本地 HTTP 联调需关闭传输层强制：`COOKIE_SECURE=false`、`REQUIRE_HTTPS=false`、`CSRF_ENABLED=false`
  （`start-backend.bat` 已代劳）。CSRF 校验失败会返回 403 且提示缺失 `X-XSRF-TOKEN`。

### 方式二：Docker 容器化部署（**本阶段搁置，以下内容转为留档**）

> 🗄️ **2026-10-08 状态**：本节命令**当前不在本机执行**（镜像与容器已从本机全部移除，约 8.7 GB 释放；16 个数据卷一个没动）。文件与知识都保留着，`docker-compose.yml`／三份 `Dockerfile`／`.dockerignore` **不可删**（CI 的 `docker`、`trivy-scan` 作业仍按它们判定）。**重新启用前请先读手册 §24.2 的「重新启用前提清单①–⑤」**（钉版本拉取 → `.env` 必填键 → 存量卷一次性 `chown` → `REQUIRE_HTTPS`/`COOKIE_SECURE` 显式决策 → 用 `/api/actuator/health/probe` 探活）。以下原文保留供后续参考。

```bash
cp .env.example .env      # Windows: copy .env.example .env（共 33 个生效键 + 21 行注释示例，填入真实强密码与密钥）
docker compose --profile full up -d --build
# 可选叠加 profile（compose 共 12 个服务 / 12 个命名卷）：
#   --profile ops      每日全量备份（mysql-backup：入口守护循环，周期 BACKUP_INTERVAL_SECONDS，默认 86400s）
#   --profile monitor  Prometheus + Grafana + Loki + Promtail + node-exporter + Alertmanager
#   --profile rag      Neo4j 5-community（知识图谱真图库后端，需 NEO4J_PASSWORD）
# 注：`mysql` 与 `redis` 无 profile，任何 `up` 都会拉起；其余服务须显式指定 profile。
# ⚠️ 启用 --profile monitor 前还需一枚文件式凭据（Prometheus 不插值配置文件、只读 password_file，DR-30）：
#      mkdir -p deploy/secrets
#      printf %s "$APP_ACTUATOR_BASIC_PASSWORD" > deploy/secrets/actuator_basic_password   # 末尾不可有换行
```

> 🔄 **从 2026-10-08 之前的版本升级上来（编排重新启用时），先做这一件事**（DR-34：两个镜像已真正非 root 运行——backend uid/gid **10001**、frontend uid/gid **101**；⚠️ **下面这条一次性迁移本轮未执行**，已随 Docker 一并顺延，执行前先确认卷仍在）：
> Docker 只在**卷首次创建**时把镜像内挂载点的属主传给卷，所以**存量** `aicourse_uploads-data` / `aicourse_backups-data`
> 仍是 root:root，直接换新镜像会让**教学资源上传与每日备份静默 500**（容器照常 Healthy，问题只在写文件时暴露）。
> ```bash
> docker run --rm -v aicourse_uploads-data:/v alpine:3 sh -c 'ls -la /v | head -40'   # 先看有没有既有文件
> docker run --rm -v aicourse_uploads-data:/v alpine:3 sh -c 'chown -R 10001:10001 /v'
> docker run --rm -v aicourse_backups-data:/v  alpine:3 sh -c 'chown -R 10001:10001 /v'
> ```
> 空卷可以直接删卷让 compose 重建（新卷自动是 10001:10001）；**有既有文件的库必须走上面的 in-place chown，不要删卷**。
> 范围只有这两卷：`--profile ops` 的 mysql-backup 容器写的是**另一个卷** `mysql-backups:/backups`（它未声明 `user`，仍以 root 运行），
> 与 backend 的 `backups-data:/app/backups` 互不相干，**不要顺手 chown 它**。
> 完整取证（含"存量卷不改会 Permission denied"的对照实验、上传/下载全链路复验）见 `docs/项目手册.md` §24.2.4。

> ⚠️ **务必先选一条路径再启动。** `--profile full` 起的是一个**明文 HTTP 单机栈**：`frontend/nginx.conf`
> 在容器内监听 **8080**（DR-34 非 root 化后 nginx 以 uid 101 运行，绑不了 <1024 的特权端口），
> compose 把它映射回**宿主机 80**（`${FRONTEND_HOST_BIND:-127.0.0.1}:${FRONTEND_HTTP_PORT:-80}:8080`），
> 且**没有挂载任何证书卷**，因此不存在 TLS 终止。而 backend 的
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
| `NEO4J_PASSWORD` | 强随机（叠加 `--profile rag` 时） | compose 以 `${NEO4J_PASSWORD:?…}` **插值失败、直接报错退出**（neo4j 服务的 `NEO4J_AUTH` 行） |
| `MYSQL_APP_PASSWORD` | 可留空 | 留空则 compose 回退用 `MYSQL_ROOT_PASSWORD` 连接业务库（backend 服务的 `SPRING_DATASOURCE_PASSWORD: ${MYSQL_APP_PASSWORD:-${MYSQL_ROOT_PASSWORD}}`）；生产建议独立建业务账号 |
| `APP_ADMIN_INITIAL_PASSWORD` | 8–32 位，含大小写字母 + 数字 + 特殊字符（**共 4 类**） | **启动 fail-fast 拒绝启动**（`.env.example` 里的 `CHANGE_ME_*` 是**刻意不合规**的占位符，属有意设计——宁可拒绝启动，也不允许"照抄示例"形成公开的已知 admin 口令）。注意：`openssl rand -base64 32` 输出 44 字符、**超 8–32 上限**，不要用它生成此项，请用上面的脚本 |

> ⚠️ **`DEMO_ACCOUNTS_ENABLED` 默认值两处不一致（已知，按环境生效范围区分）**：`application.yaml:216` 为 `${DEMO_ACCOUNTS_ENABLED:true}`（本地开发默认保留演示账号），compose backend 服务的 `DEMO_ACCOUNTS_ENABLED: ${DEMO_ACCOUNTS_ENABLED:-false}` 为容器化默认加固。容器里未显式传值即为 `false`（自动停用并改密），本地 `start-backend.bat` 未设则为 `true`。**生产必须显式 `=false`**，并注意其收敛范围自 2026-10-06 起为「口令等于种子哈希」的**全部 170 个**种子账号（V1 内置 8 个 + V5/V6 号段 162 个；此前只覆盖 V1 的 8 个）。⚠️ 同日 V5/V6/V7 已移出 prod 的 Flyway `locations`，故**全新生产库里实际只有 V1 的 8 个**待收敛，170 这个数字只在开发库或被误用的旧库上出现（见下方「生产边界」与手册 §24.5 / DR-18）。

#### 路径 ①：本地 / 内网评估（HTTP 明文，仅限本机或内网）

在 `.env` 中**显式**放宽传输层强制：

```dotenv
REQUIRE_HTTPS=false
COOKIE_SECURE=false
CSRF_ENABLED=true      # 保持 true，不要关
```

- **原因**：nginx 容器内只听 **8080**（宿主机侧仍是 80，访问 URL 不变）、compose 未挂载任何证书卷，没有任何 TLS 终止；不关掉强制，`/api/**` 会被 302 到不存在的 443。
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

**当前实测基线**：本机当前口径 = `mvn -B -o clean verify -DskipITs`（编排 2026-10-08 起搁置，Testcontainers 集成层交回 CI `backend` 作业取数）。⚠️ **下表数值一律从手册附录 B.2 抄取，B.2 是唯一来源**；改 B.2 后必须同步此处，两处不一致时以 B.2 为准。构建纪律（JaCoCo exec 落在 `${user.home}`、被所有 worktree 共用，**带覆盖率的构建必须串行**）见手册 §3 告警块与 §24.2.3 根因 D。

| 门禁 | 读数（当前本机口径） |
| --- | --- |
| 后端单元测试 | **2831 用例 / 0 失败 / 0 错误 / 0 跳过** |
| 后端集成测试 | **12 用例 / 0 失败**（含 IT 那轮，现由 CI 取数） |
| JaCoCo | **LINE 89.15% / BRANCH 70.60%**（341 类）→ 在**阈值未降低**（LINE ≥ 85% / BRANCH ≥ 65%）的前提下达标，靠补真实测试而非降阈值 |
| ArchUnit | **6 条**架构守护全通过 |
| SpotBugs | **0 缺陷**（`failOnError=true` 硬阻断，13 个框架性 `<Match>` 豁免） |
| 前端单测 / ESLint | **55 文件 / 1096 用例全绿**；lint **0 error / 0 warning** |
| 前端类型检查 | `vue-tsc` 棘轮基线 **576 条 / 108 文件**（只许下调） |
| CI | 3 个工作流；`ci.yml` **10 个作业**（`frontend-e2e` 暂为 `workflow_dispatch`）——**本机绿不等于 CI 绿**，且本机对 Actions 结果不可观测（手册 §21.1） |
质量门禁基线（渐进收敛制，只许上调不得下调）：JaCoCo LINE ≥ 85% / BRANCH ≥ 65%（生产收敛目标指令覆盖 ≥ 85%）、SpotBugs 0 error、ArchUnit 架构约束。测试方法、契约测试清单与 CI 流水线见 `docs/项目手册.md` 第六部分。

### 端到端全链路与压力测试（四件套）

`scripts/e2e/` 下有**四件套**实机脚本（仅依赖 Python 标准库），**改动后端后必跑**。用例规模、运行前提（`FLUSHDB`、双姿态、cookie jar、造数据契约）与读数一律见手册 **§43**——README 只给命令顺序，不复制数字；历史结果快照在 `archive/deliverables/`，本地重跑生成的 `e2e_*_result.json` 落在脚本目录（已 gitignore）。

```bash
# 前置：MySQL(3306) 与 Redis(6379) 已运行；仓库根有 .env；
#       backend/target/backend-0.0.1-SNAPSHOT.jar 已是最新（改了后端代码务必先重新打包）

# ── 第 1 轮：功能姿态（CSRF 关、登录限流 200）────────────────────
# 必须以「后台任务」方式常驻启动，前台运行会阻塞且进程可能被回收
python scripts/run-backend-for-e2e.py functional

cd scripts/e2e
python e2e_verify_network_course.py   # ① 主链路（建课→审批→资料→作业→考试→成绩）
python e2e_deep_verify.py             # ② 多维深度（A 协同/B 边界/C 越权/D 一致性/E 分片下线态/F 退回重交/G 版本）
python e2e_stress_verify.py           # ④ 压力与稳定性（加 --quick 走快速档）

# ── 第 2 轮：安全姿态（CSRF 默认开、登录限流默认阈值）──────────────
# 先停掉功能姿态后端，再：
python scripts/run-backend-for-e2e.py secure
cd scripts/e2e
python e2e_security_verify.py         # ③ 安全维度（CSRF 双提交 + 双层登录限流）
```

> **姿态纪律**：功能轮与安全轮**不可混用配置** —— 安全轮若沿用功能姿态（CSRF 关、限流放宽），CSRF 与限流用例会全部失效。两轮起测前脚本均会自行 `FLUSHDB`，无需手工清 Redis。

> **登录保护为两层**：① 速率层 `RateLimitFilter`（IP 滑动窗口 10 次/60 秒）；② 尝试层 `AuthSessionComponent`（同账号连续失败 5 次锁定 15 分钟）。尝试层阈值更低、失败路径下先触发，验证时须分层断言。详见 `docs/项目手册.md` 附录 D 的 2026-09-17 条目。
>
> ⚠️ **DR-36（2026-10-08 已修复）改写了上面这层的真实历史**：速率层四种规则（`login`/`register`/`verify`/`ai`）**共用同一条 Lua**，而该脚本越界引用了不存在的 `ARGV[2]` → 脚本每次报错、只回落实例内存计数，**Redis 侧限流在修复前从未生效**（副作用：计数键无 TTL 慢性泄漏；多实例部署时限流额度按实例数放大）。修复为一处下标更正 + 一条从真实调用现场取参的守卫用例（含阴性对照），机制、A/B 取证与规约升见手册 **附录 D.12.3 DR-36** 与 **§19.1「脚本内联 DSL 的下标一致性」**。

最近一次全栈复跑为 **2026-10-06**（以示范课 4007 为主链路对象，功能 + 安全双姿态），其后 2026-10-07/08 的收口轮又复验过安全维度。E2E 四件套与压力测试的**读数唯一来源是 `docs/项目手册.md` §43**，README 不复制数字；最早那轮的时点报告在 `docs/历史归档/端到端全链路与压力测试报告-2026-09-17.md`。

> 📌 **仍开放的半边**：`ci.yml` 的 `frontend-e2e` 作业为 `workflow_dispatch` 手动触发，不在 push/PR 触发链上 ⇒ **本机 Playwright 全绿不等于 CI 绿**，E2E 门禁在主干上没有强制力（**DR-20 未闭合半边**，2026-10-08 经项目主定案留待后续，手册 §D.12.5）。演示或放行前建议按上面的顺序本机实跑一轮。
>
> 📌 **2026-10-08 定案**：**DR-20 / DR-23 / DR-26 连同 Docker 相关项，经项目主明确为「留待后续推进」，不是本轮遗漏**；逐项「本轮为何不做 / 重新拾起的前置 / 现在的兜底状态」见手册 **§D.12.5**。当前里程碑判定（核心功能已完成、可交付且可扩展）以手册第五部分与 §43 的实测门禁为准。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

- 完整许可证文本见仓库根目录 [`LICENSE`](LICENSE)。
- 参与贡献请阅读 [`docs/项目手册.md`](docs/项目手册.md)（附录 C：分支策略、编码规范、质量门禁与 PR 流程）。
- 报告 Bug / 提需求请使用 Issue 模板（`.github/ISSUE_TEMPLATE/`）。
