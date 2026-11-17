# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-blue)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

面向高校课程教学的 AI 课程平台，服务**学生 / 教师 / 管理员**三类角色，提供个性化学习、教学支持与管理治理。

**核心能力域**：多角色 RBAC、课程／章节／选课与审批、题库与在线考试（防作弊）、作业布置／提交／批改／查重、知识图谱可视化与学习路径规划、AI 问答／出题／批改／学情建议、数据分析与学情报告、WebSocket 实时通知、内容审核与运维治理。

**当前阶段**：阶段 1–3 已完成，阶段 4 已执行多批 P0/P1 收口；主线为**大创结题优先**，工程尾量与前端体验修复并行。开发/验收可本机全栈真跑；生产上线要求 TLS/反代、容器或等价编排、密钥管理、备份恢复、监控告警与 A 路径断链清零，当前优先级后置。Docker/A 路径未在目标环境实跑，不能把本机开发姿态当生产交付。

**想知道"做到哪了、还剩什么"**：先看 [`docs/当前状态与待办.md`](docs/当前状态与待办.md)（当前状态、剩余任务、优先级、阻塞项唯一总表）；定位与总账看 `docs/项目手册.md`；判据与历史证据看 `docs/前端待办项.md`／`docs/后端待办项.md`；代理入口与能力边界看 [`AGENTS.md`](AGENTS.md)。对外表述不得高于待办文档里已闭行的证据。

> **⚠️ 参考项目的方法学边界**：`大创赛/类似项目/DeepSeek-RAG-Chatbot` 的 GraphRAG 实体抽取用正则 `\b[A-Z][a-z]+...`，**仅支持英文实体**，且是单机通用文档问答工具，与本项目的中文知识图谱（`knowledge_points`／`knowledge_point_relations`）在语言适配、领域建模、工程规模上不可类比。**不得把它当成本项目中文 KG 方案的对标或引用依据**；中文 KG 的关系建模以 `相关论文/`（杜彩丽 IPA／IREM、中职计算机网络多源异构 KG 等）与 PRD 接口矩阵为准。

---

## 文档地图（权威主干 8 份 + 运行待办 3 份）

> **新接手者入口**：先读 [`AGENTS.md`](AGENTS.md)（5 分钟速览 + 工程纪律 + 验证命令），再看 [`docs/README.md`](docs/README.md)（唯一索引与分层说明）。
> **当前 docs 口径**：`docs/` 共 **21 个 Markdown + 1 个 `assets/` 目录**；2026-11-15 的“46 份压缩到 14 份／10 份”为历史整理记录，不再是现行约束。

| 文档 | 定位 | 什么时候看它 |
| --- | --- | --- |
| [`docs/当前状态与待办.md`](docs/当前状态与待办.md) | **当前状态与剩余任务总表**：任务、状态、优先级、负责人或阻塞项 | 接手、排期、汇报进度 |
| `docs/项目手册.md` | **总入口与总账本**：项目是什么、角色与权限、功能清单、里程碑、术语、协作约定、审计台账、结构性计数 | 接手项目的第一读；口径争议时的仲裁 |
| `docs/PRD产品规划.md` | 需求基线与生产验收标准、核心流程验收脚本、14 维度 140 项规划、Phase 1/Phase 2 路线 | 判断"该不该做、做到什么算好" |
| `docs/系统架构设计.md` | 模块与进程拓扑、数据模型、接口契约（`ContractPaths` 为唯一路径来源）、架构决策记录 | 改后端／引擎边界之前 |
| `docs/开发规范.md` | **§1 后端**（分层、异常与事务、鉴权、缓存、日志、批处理、AI 降级）· **§2 前端**（目录与命名、组件与设计令牌、状态与请求层、可访问性）· **§3 协作规范** | 写 Java / Vue/TS、提 PR 之前 |
| `docs/部署文档.md` | 开发/验收环境与生产部署基线、启动步骤与依赖自检、`.env` 与文件式密钥、健康判据、容器/A 路径、备份恢复、故障排查 | 起不来、做生产部署、上线验收 |
| `docs/测试文档.md` | **测试与质量读数的唯一来源**：分层与命令、门禁判据、e2e／视觉前置、全栈四件套与压力、CI 作业键与证据纪律 | 跑验证、引用数字、判红 |
| `docs/alignment/01-决策对齐总表.md` | **战略基线**：核心定位、维度决策、三条红线、里程碑 M1–M8、风险登记 R1–R12、软著与答辩证据包 | 战略取舍、优先级冲突 |
| `docs/前端待办项.md` | 前端剩余项的判据、进入点、历史证据与明确不做；当前剩余项以总表为准 | 查判据与历史，不直接当排期表 |
| `docs/后端待办项.md` | 后端／引擎／基础设施剩余项的判据、进入点、历史证据与明确不做；当前剩余项以总表为准 | 查判据与历史，不直接当排期表 |
| [`docs/README.md`](docs/README.md) | 文档索引与分层说明 | 找文档 |

**数字纪律**：结构性计数只写在手册 §8，测试读数只写在测试文档 §2，剩余任务状态只写在当前状态与待办总表。本 README **不复制任何读数**——同一数字在多处各写一套，必然漂移。

**相关章节**：

| 想知道 | 去哪 |
| --- | --- |
| 仓库协作：分支模型、PR 规则、合并前置 | [`docs/开发规范.md`](docs/开发规范.md) **§3** |
| 脱敏口径与受控例外（3 处不做脱敏及理由） | [`docs/开发规范.md`](docs/开发规范.md) **§4** |
| 扩展路线池 A–E 五域 22 项（均未排期） | [`docs/PRD产品规划.md`](docs/PRD产品规划.md) **§7.4** |
| 为什么没上消息队列 | [`docs/系统架构设计.md`](docs/系统架构设计.md) **§4.9** |
| 异地备份与 TLS 件缺什么 | [`docs/部署文档.md`](docs/部署文档.md) **§6.9** |
| 恢复演练与空库开通做到什么程度 | [`docs/部署文档.md`](docs/部署文档.md) **§9.4** |
| 里程碑 M1–M8 / 风险登记 R1–R12 / 软著与答辩证据包 | [`docs/alignment/01-决策对齐总表.md`](docs/alignment/01-决策对齐总表.md) **§七–§九** |

---

## 技术栈

| 层级 | 技术 | 版本 |
| --- | --- | --- |
| 后端框架 | Spring Boot | 3.5.16（JDK 21） |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全 | Spring Security + JWT | 6.x / jjwt 0.12.6 |
| 数据库迁移 | Flyway | 结构 **V1–V27（23 个脚本；V20 实际存在；V22 为 seed-demo）**（`db/migration`，全环境）＋ 演示种子 V5–V7、V12_1、V22（5 个，`db/seed-demo`，prod 排除）；已应用迁移校验和冻结不可改 |
| 数据库 / 缓存 | MySQL / Redis | 8.4 / 7.x（Redis 为**必选**依赖） |
| 前端框架 | Vue + Vite | 3.5.x / 8.1.7 |
| UI 层 | 自研 `src/ui/components/` 原语 + CSS 变量令牌 | **无第三方组件库**（Element Plus／echarts 已随旧前端在 `15fb858` 整体摘除） |
| 状态 / 路由 / 图表 | Pinia / Vue Router / 内联 SVG（`Sparkline`／`Sprite`） | 2.1.x / 4.2.x / — |
| 测试 | Vitest / Playwright / Testcontainers | 3.2.7 / **1.64.1（精确锁定）** / — |
| 质量门禁 | JaCoCo / SpotBugs / ArchUnit | LINE ≥85% 且 BRANCH ≥65%（阻断）／0 bug（阻断）／8 条架构守护 |
| 实时通信 | Spring WebSocket | 通知与监考 |
| 限流熔断 | Sentinel | 下游流控 + 慢调用熔断 |
| AI 引擎 | Python FastAPI | KG 8001 / RAG 8002 / LoRA 8003 |
| 图数据库（可选） | Neo4j | 5.26，本机独立实例；缺失时 KG 自动回落内存图 |

数据面口径：当前库表 **61** 张（V12 组织四表后 V13–V17 续增）；`index.sql`（测试库建表脚本）已回补至 61，`V1` 因 Flyway 校验和冻结仍为 48 张——漂移管控见 `docs/后端待办项.md` S-03 与手册 DR-61。

文件面口径：新上传写入外部存储根 `AICOURSE_DATA_DIR/storage`（默认 `~/.aicourse-platform/storage`），分片写入 `AICOURSE_DATA_DIR/chunks`；对外 URL 仍为 `/uploads/<key>`。仓库根 `uploads/` 已移除；`backend/uploads/` 仅作旧目录只读兼容，原 138 个历史文件已复制到外部根并逐文件校验，当前只剩 11 个压测临时 PDF（可清理）。

### Python 侧三服务

Java 后端经标准化集成层 `com.aicourse.integration` 与 Python 微服务对接（`ContractPaths` 是调用约定的唯一事实来源）：

| 服务 | 目录 | 端口 | 说明 |
| --- | --- | --- | --- |
| 知识图谱 | `python-services/kg_service/` | 8001 | 11 个契约端点；`kg_store.py` 支持 Neo4j 与内存双后端（不可达时自动回落内存图）；多跳路径规划在图库侧完成，Java 侧只做本地 BFS 兜底；CI 护栏 `test_kg_contract.py` |
| RAG 检索 | `python-services/rag_service/` | 8002 | 9 个契约端点 + `rag_engine.py` 真实混合检索（BM25 + 向量 + RRF + **HyDE + Cross-Encoder 重排**，`ENGINE_VERSION=1.1`）。⚠️ 语料落在 gitignore 的 `data/`，换机或重建环境时用 `python scripts/seed-rag-corpus.py <courseId>` 从 `scripts/rag_corpus.json` 重放并重建索引（当前有效语料：课程 1 七篇 + 样例课 **4178《计算机网络》十篇**，共 49 chunks） |
| LoRA 微调 | `python-services/fine_tune_service/` | 8003 | 八条契约端点 + 数据集／语料六端点，真实 PEFT 训练／导出／推理。【2026-11-07 P1 整改批】已补齐 Dockerfile（CPU 版 torch，非 root + 健康检查）并纳入 compose（独立 profile `ft`，`docker compose --profile ft up -d fine-tune-service` 后置 `FT_ENABLED=true` 接通）。**残项**：一键启动器不拉起（本机裸跑仍须手工 `uvicorn`）、Java 侧未消费数据集端点 |

- `max_hops` 自 2026-10-05 起**只用于"是否采纳"判定，不参与遍历剪枝**，上限不足时仍回传真实最短跳数（语义见 `docs/系统架构设计.md`）。
- **AI 面失败不伪装成功**：大模型调用失败一律返回 HTTP 503 + `code=503`，`message` 指认病因（凭据被拒／额度／未配置／空响应）。没有有效凭据时 AI 端点就是不可用——这比"200 带一句客套话"更该先知道（DR-74）。
- 早期 RAG 成员的 Streamlit 原型（原 `python-services/deepgraph_rag/`，8501）不参与正式功能：先归档至仓库根 `archive/deepgraph_rag/`（`7c6ced0`），随后该冷备份目录整体退跟踪并移出工作区（`91d3970`）——现仅存于历史提交。

---

## 快速开始

### 依赖纪律（先看这条）

- **必要**：JDK 21、Node 20.19+、本机在跑的 MySQL 8.4（3306）与 Redis（6379）。两份 `.bat` 启动时自检，缺失即报错退出。
- **可选**：Neo4j（7687／7474）、KG 8001、RAG 8002、LoRA 8003——缺失只降级不阻塞（`KG_ENABLED`／`RAG_ENABLED` 关闭时走 Java 本地 Provider 与 BFS 兜底）。
- 本机缺**必要**依赖时：**上报项目主**，不要自行 `docker pull`、也不要安装替代物。启动器（`start.bat` → `scripts/start-all.py`）已不含任何 docker 分支（原"探测 3307 则切数据源"的路径已删除）。

### 三步起栈

```bash
# ① 建库（首次；结构由 Flyway 在应用启动时自动迁移）
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# ② 配密钥：复制模板后填入 MYSQL_ROOT_PASSWORD 与 JWT_SECRET（缺任一即 fail-fast；值不回显、不提交）
cp .env.example .env

# ③ 一键起完整项目（Neo4j / KG / RAG / Java / Vite，单窗口中继日志；已在跑的服务接管不重启）
start.bat
```

同一个入口按子命令分段：`start.bat backend`（引擎 + 后端，六步自检，第 5 步按 Neo4j → KG → RAG **只补缺失进程**并代引擎导出 `NEO4J_URI`／`DEEPSEEK_API_KEY`／`HF_HUB_OFFLINE`）、`start.bat frontend`（只起 Vite，在 5173／5174／5175 中取第一个空闲口）、`start.bat status`（什么都不起，只报五进程状态）。判定逻辑只在 `scripts/start-all.py` 一处，`.bat` 是薄壳；逐步口径与重启语义见 `docs/部署文档.md` §3。

### 访问与判活

| 项 | 值 |
| --- | --- |
| 前端 | `http://localhost:5173`（脚本自动择空） |
| 后端 API | `http://localhost:8080/api` ｜ Swagger `/api/swagger-ui.html` |
| 编排／容器探针 | **只打** `GET /api/actuator/health/probe`（`readinessState` + `database` + `redis` + `diskSpace`，200＝实例可服务） |
| 人读整体状态 | `GET /api/actuator/health`——它聚合可选依赖，**无 AI／邮件凭据时返 503 属设计**，不代表服务不可用 |

- `llmProvider` 组件 UP 只说明"填了东西"，**不证明凭据有效**（只静态判非空白）；有效性用 `python scripts/probe-llm-chain.py engine|chains` 实测。
- Vite `strictPort: true`：5173 被占时**直接退出而不自动换口**，所以 `start.bat frontend` 预先探测（双栈）并显式传 `--port`；手动起用 `cd frontend && npx vite --port 5174`。本机 Vite 实测只监听 `[::1]`，请用 `http://localhost:5173/`（`127.0.0.1` 反而连不上）。
- 后端 CORS 白名单只含 **5173／5174／5175**（`localhost` 与 `127.0.0.1` 两种写法），**5176 及以上端口登录会被 403 拒绝**。
- 手动跑 jar **必须显式** `java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --server.port=8080`，否则端口可能被外部环境变量覆盖，报错误的端口。
- 本机 Redis 是**无密码**模式：手动启动**勿注入** `SPRING_DATA_REDIS_PASSWORD`，注入会让启动校验 fail-fast。
- 本地 HTTP 联调需 `COOKIE_SECURE=false`、`REQUIRE_HTTPS=false`、`CSRF_ENABLED=false`（`start.bat` 的 all/backend 模式已代劳）；CSRF 校验失败返回 403 并提示缺失 `X-XSRF-TOKEN`。
- **依赖源已迁到官方 registry**：`frontend/package-lock.json` 的 `resolved` 全部为 `registry.npmjs.org`（仅 xlsx 的 `cdn.sheetjs.com` tarball 原样保留）。本机**不需要为此改 npm 配置**——npm 默认的 `replace-registry-host=npmjs` 会把官方主机名重写到你所配置的镜像源，同一份 lockfile 同时服务 CI（官方源）与本机（镜像提速），`integrity` 哈希与源无关。

> Docker／Compose 与"一台云主机上线"的全部内容（15 服务与 profile、必填环境变量清单、存量卷 `chown` 一次性迁移、TLS 与两条传输层路径、监控与备份、断链清单）已收在 `docs/部署文档.md` §5／§6／§7／§9。**编排尚未在真实环境实跑**：CI 的 `Docker Images Build` 只在 `main`/手动触发时构建，本机与目标环境均未 `up`；TLS、恢复演练、容量压测与 deploy secrets 仍挂账（2026-11-17 逐项状态见部署文档 §6）。本节不再重复命令。

---

## 演示账号

> **口径**：下表为**演示种子口径**，来源 `SeedAccounts` 与 `AdminSeedPasswordInitializer`。开发库是否已按该口径改密**未取证**；生产环境必须改用强口令并关闭演示账号。

| 角色 | 用户名 | 口令 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `123456` | 演示种子口径；生产必须改密 |
| 教师 | `teacher` / `teacher2` | `123456` | 张建国 / 李文博（V18 已真实化改名，username/口令不动） |
| 学生 | `student` / `student2`~`student5` | `123456` | 以上 8 个为 **V1 内置**（`SeedAccounts.DEMO_USERNAMES` 是单一来源） |
| 教师（富种子） | `ts02`…`ts12` | `123456` | V5 原 12 位教师 + 150 名学生；**2026-11-07 V18 已清除 `ts01`/`st001`/`st101`**，开发库余 ts×11、st×148 |
| 示范课 | — | — | **样例课 `4178`《计算机网络》（`CN202`，2026-11-14 建；8 章 / 5 名选课学生 / 59 知识点 / 55 条关系）**；旧 V6 course 4007 已随 V18 删除 |

演示账号原合计 **170 个**；V18 后开发库实测 **169**（2026-11-09：V1 的 8 个 + V5 残留 159 个 + 后注册 `regtest1105`/`leyon` 2 个）。`DEMO_ACCOUNTS_ENABLED` 在 `application.yaml` 默认 `true`（本地开发保留演示账号），compose 的 backend 服务默认 `false`（容器化默认加固）——**生产必须显式 `false`** 并提供 `APP_ADMIN_INITIAL_PASSWORD`，否则启动 fail-fast。加固器按"口令仍等于种子哈希"筛选，已改密的账号绝不覆盖。

> ⚠️ **生产边界**：演示数据不进生产库——V5–V7、V12_1 位于 `db/seed-demo`，prod 的 Flyway `locations` 整目录排除，由 `DemoSeedMigrationIsolationTest` 钉住。**但唯一残余风险在部署流程而不是代码**：把灌过演示种子的旧库直接当生产库**不会报错**（Flyway 默认 `ignoreMigrationPatterns=[*:future]`，静默忽略 + 一条 WARN）。所以上线前核对的是"**版本集合不含 5/6/7/12.1、`users` 只剩 V1 的 8 个、无 course 4007**"，**不是"最高版本=4"**（自 V8 起该判据已失效，DR-62）。清单见 `docs/部署文档.md` §7。

---

## 质量与验证

```bash
# 后端（单元口径 + 覆盖率 / SpotBugs / ArchUnit 全部门禁）
cd backend && mvn -B -o clean verify -DskipITs

# 前端（静态门 → 单测 → 构建与体积）
cd frontend && npm run lint && npm run typecheck && npm run test && npm run build && npm run size:gate

# 浏览器侧（前置条件必读）
cd frontend && PW_PORT=5174 npx playwright test                          # 流程 e2e
cd frontend && PW_PORT=5175 npx playwright test -c playwright.visual.ts   # 视觉回归

# 全栈四件套（第 1 轮功能姿态 / 第 2 轮安全姿态，不可混用）
python scripts/run-backend-for-e2e.py functional
python scripts/e2e/e2e_verify_network_course.py     # 主链路（建课→审批→资料→作业→考试→成绩）
python scripts/e2e/e2e_deep_verify.py               # 多维深度（协同/边界/越权/一致性/分片/退回重交/版本）
python scripts/e2e/e2e_stress_verify.py             # 压力与稳定性（--quick 走快速档）
python scripts/run-backend-for-e2e.py secure
python scripts/e2e/e2e_security_verify.py           # CSRF 双提交 + 双层登录限流
```

- **读数唯一来源**：`docs/测试文档.md` §2；引用时连同**日期与口径**（单元 vs 含 IT）一起引。
- **e2e 前置**：流程套件要求后端以 `RATELIMIT_LOGIN_LIMIT=200` 起、三份 `.auth` 会话夹具与端口匹配；视觉套件固定 5175 且**不用 `--update-snapshots` 吸收未知漂移**。缺前置时的红灯是环境问题，不是产品缺陷（判红纪律见 `docs/测试文档.md` §4）。
- **登录保护是两层**：速率层 `RateLimitFilter`（IP 滑动窗口 10 次/60 秒）与尝试层 `AuthSessionComponent`（同账号连续失败 5 次锁定 15 分钟）；尝试层阈值更低，失败路径下先触发，**必须分层断言**。
- **CI**：`.github/workflows/ci.yml` 13 个作业；连 `deploy`／`eval-nightly`／`release` 共 17 个唯一作业键（清单见 `docs/测试文档.md` §7）。`frontend-e2e` 在 **PR** 跑 `core`／`knowledge-graph`／`question-transfer` 三个核心 spec，在 `push main`／`workflow_dispatch` 跑完整流程套件；最新 PR #107 的本次 Actions run 为 **12 个可执行 job success、1 个 Docker job skipped**，不代表目标环境已部署。Actions 页面可观测；匿名 API 仍受限流。`main` 当前**无分支保护**（保护 API 返回 404），团队约定为 CI 可执行 job 全绿后合并，不设强制评审。

---

## 已知边界（表述不得越过这条线）

- 当前只有**本机原生进程**这一条部署形态可执行；Compose 配置已齐备（15 服务，含 `ft` profile），CI 的 Docker job 只在 `main`/手动触发时构建，本机与目标环境均未 `up` 验证（断链清单见 `docs/部署文档.md` §6 与 `docs/后端待办项.md` §6）。**这条形态只服务开发与验收，不构成生产就绪**：Phase 1 上线必须补齐容器或等价编排、TLS/反代、密钥、备份恢复与监控告警。
- 集成层（16 例 Testcontainers）与 Trivy 漏洞库侧**只在 CI 有读数**。
- 视觉基线覆盖 5 个锚点页（10 张，明暗各一），非锚点页的渲染缺陷抓不到；无障碍只有近似读数，**不构成 WCAG 合规结论**。
- 压力读数来自本机笔记本单实例，**不是生产容量结论**。
- 微调引擎可用 ≠ 能力可售：小样本必然过拟合，推理文本会退化为语料外乱码（`docs/后端待办项.md` §7 AG-2）。
- 多租户、计费、小程序、桌面端属 Phase 2/后续路线；Phase 1 是**单租户生产**，当前代码 `tenant_id` 零命中。

---

## 参与贡献

> 由 AI 代理参与本仓工作时，先读根目录 [`AGENTS.md`](AGENTS.md)——**唯一入口**：权威顺序、三条红线、授权边界、当前基线、命令速查、工程纪律（§10–§12）。原 `AGENT.md` 已于 2026-11-15 并入该文件并删除。它不承载架构／规范／测试判据——判据一律在 `docs/` 内，索引见 [`docs/README.md`](docs/README.md)。

1. 先读 `docs/项目手册.md` §6（分支与推送、提交与评审、密钥与敏感信息、证据纪律、Windows 本机约定）。
2. 动手前认领对应待办文档里的条目——判据、进入点、代价与「明确不做」都在 `docs/前端待办项.md`／`docs/后端待办项.md`。
3. 提交前过 `.github/pull_request_template.md` 的自检清单；**数据点改动只改唯一来源**（测试文档 §2 或手册 §8）。
4. 涉及 Python 契约字段时：同步 `ContractPaths` 常量 + `docs/系统架构设计.md` 的集成契约章节 + 两侧契约测试。
5. 报告 Bug／提需求请使用 Issue 模板（`.github/ISSUE_TEMPLATE/`）。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源，完整文本见仓库根目录 [`LICENSE`](LICENSE)。
