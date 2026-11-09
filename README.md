# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-blue)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

面向高校课程教学的 AI 课程平台，服务**学生 / 教师 / 管理员**三类角色，提供个性化学习、教学支持与管理治理。

**核心能力域**：多角色 RBAC、课程／章节／选课与审批、题库与在线考试（防作弊）、作业布置／提交／批改／查重、知识图谱可视化与学习路径规划、AI 问答／出题／批改／学情建议、数据分析与学情报告、WebSocket 实时通知、内容审核与运维治理。

**当前阶段**：**Phase 1 单租户生产系统**（多租户为 Phase 2）。开发/验收可本机全栈真跑；生产上线要求 TLS/反代、容器或等价编排、密钥管理、备份恢复、监控告警与 A 路径断链清零。Docker/A 路径当前尚未在生产环境实跑，不能把本机开发姿态当生产交付。

**想知道"做到哪了、还剩什么"**：定位与状态看 `docs/项目手册.md`；剩余迭代任务的分层排期与未闭环缺口看 [`docs/剩余迭代任务清单.md`](docs/剩余迭代任务清单.md)；未完成事项看 `docs/前端待办项.md`／`docs/后端待办项.md`。对外表述不得高于待办文档里已闭行的证据。

> **⚠️ 参考项目的方法学边界**：`大创赛/类似项目/DeepSeek-RAG-Chatbot` 的 GraphRAG 实体抽取用正则 `\b[A-Z][a-z]+...`，**仅支持英文实体**，且是单机通用文档问答工具，与本项目的中文知识图谱（`knowledge_points`／`knowledge_point_relations`）在语言适配、领域建模、工程规模上不可类比。**不得把它当成本项目中文 KG 方案的对标或引用依据**；中文 KG 的关系建模以 `相关论文/`（杜彩丽 IPA／IREM、中职计算机网络多源异构 KG 等）与 PRD 接口矩阵为准。

---

## 文档地图（九份权威文档）

| 文档 | 定位 | 什么时候看它 |
| --- | --- | --- |
| `docs/项目手册.md` | **总入口与总账本**：项目是什么、角色与权限、功能清单、里程碑、术语、协作约定、审计台账 DR-1…DR-111、结构性计数 | 接手项目的第一读；口径争议时的仲裁 |
| `docs/PRD产品规划.md` | 需求基线与生产验收标准、核心流程验收脚本、14 维度 140 项规划、Phase 1/Phase 2 路线 | 判断"该不该做、做到什么算好" |
| `docs/系统架构设计.md` | 模块与进程拓扑、数据模型、接口契约（`ContractPaths` 为唯一路径来源）、架构决策记录 | 改后端／引擎边界之前 |
| `docs/后端开发规范.md` | 分层、异常与事务、鉴权、缓存、日志、批处理、AI 降级的编码约定 | 写 Java 之前 |
| `docs/前端开发规范.md` | 目录与命名、组件与设计令牌、状态与请求层、可访问性的编码约定 | 写 Vue/TS 之前 |
| `docs/部署文档.md` | 开发/验收环境与生产部署基线、启动步骤与依赖自检、`.env` 与文件式密钥、健康判据、容器/A 路径、备份恢复、故障排查 | 起不来、做生产部署、上线验收 |
| `docs/测试文档.md` | **测试与质量读数的唯一来源**：分层与命令、门禁判据、e2e／视觉前置、全栈四件套与压力、CI 作业键与证据纪律 | 跑验证、引用数字、判红 |
| `docs/前端待办项.md` | 前端未完成事项（视觉 Phase 5/6、体验与无障碍缺口、工程尾量、明确不做） | 排下一批工作 |
| `docs/后端待办项.md` | 后端／引擎／基础设施未完成事项（性能与契约、CI 与集成层、A 路径断链、明确不做） | 排下一批工作 |

**数字纪律**：结构性计数只写在手册 §8，测试读数只写在测试文档 §2。本 README **不复制任何读数**——同一数字在两处各写一套，必然漂移。

**配套支撑文档**（非九份权威文档，随改造批次新增）：

| 文档 | 定位 |
| --- | --- |
| [`docs/剩余迭代任务清单.md`](docs/剩余迭代任务清单.md) | 2026-11-09 基线下的剩余任务四层分层（阻塞／可代码／外部／打磨）＋验收判据＋未闭环缺口 |
| [`docs/交接文档-2026-11-09.md`](docs/交接文档-2026-11-09.md) | 交接验收：已完成范围、验收结论、已知问题清单（含 V18/4007 勘误）、变更摘要与下一步 |
| [`docs/B-12-P-08-拆包与排期方案.md`](docs/B-12-P-08-拆包与排期方案.md) | 教学组织与教学班改造的 WP1–WP10 拆包、依赖顺序、人日与验收；**WP1–WP10 已全量落地（V12–V17，2026-11-06）**，方案留作过程史料 |
| [`docs/后端信息架构改造交接.md`](docs/后端信息架构改造交接.md) | 信息架构改造交接与 §9 十条决策（1A–10A，2026-11-05 已全部拍板） |
| [`docs/组织映射清单-2026-11-06.md`](docs/组织映射清单-2026-11-06.md) | 学院／专业／行政班／学期到新组织模型的映射清单（§12 已定稿 D1–D10、§10 三角色汇总签已通过；[模板](docs/组织映射清单模板.md)仅作格式参考） |
| [`docs/恢复演练记录模板.md`](docs/恢复演练记录模板.md) | 一次真实 MySQL 恢复演练的记录模板（模板已有，演练未做） |
| [`docs/异地备份与TLS件方案骨架.md`](docs/异地备份与TLS件方案骨架.md) | 异地备份、TLS 件与 IaC 的缺口骨架（未验证） |
| [`docs/独立验收报告-2026-11-07.md`](docs/独立验收报告-2026-11-07.md) | 企业生产标准独立验收审计＋整改跟踪；2026-11-09 复核勘误节为准 |
| [`docs/后续扩展路线图-2026-11-07.md`](docs/后续扩展路线图-2026-11-07.md) | A–E 五域 22 项扩展规划池（范围/价值/依赖/优先级） |
| `docs/alignment/` | 战略基线（最高权威）：维度决策、里程碑、评估与缺口、成功判据；快照类附录以日期为准 |
| [`docs/慢SQL与N1审计-2026-11-07.md`](docs/慢SQL与N1审计-2026-11-07.md) ／ [`docs/脱敏核查-2026-11-07.md`](docs/脱敏核查-2026-11-07.md) ／ [`docs/消息队列引入评估-2026-11-07.md`](docs/消息队列引入评估-2026-11-07.md) | DR-111 专项核查：N+1 静态修复与护栏、脱敏三面核查（3 处受控例外留档）、MQ 结论（Phase 1 不引独立中间件） |

---

## 技术栈

| 层级 | 技术 | 版本 |
| --- | --- | --- |
| 后端框架 | Spring Boot | 3.5.15（JDK 21） |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全 | Spring Security + JWT | 6.x / jjwt 0.12.6 |
| 数据库迁移 | Flyway | 结构 V1–V4、V8–V18（`db/migration`，全环境）＋ 演示种子 V5–V7、V12_1（`db/seed-demo`，prod 排除）；下一个空号 V19；已应用迁移校验和冻结不可改 |
| 数据库 / 缓存 | MySQL / Redis | 8.4 / 7.x（Redis 为**必选**依赖） |
| 前端框架 | Vue + Vite | 3.5.x / 8.1.5 |
| UI 层 | 自研 `src/ui/components/` 原语 + CSS 变量令牌 | **无第三方组件库**（Element Plus／echarts 已随旧前端在 `15fb858` 整体摘除） |
| 状态 / 路由 / 图表 | Pinia / Vue Router / 内联 SVG（`Sparkline`／`Sprite`） | 2.1.x / 4.2.x / — |
| 测试 | Vitest / Playwright / Testcontainers | 3.2.7 / **1.63.0（精确锁定）** / — |
| 质量门禁 | JaCoCo / SpotBugs / ArchUnit | LINE ≥85% 且 BRANCH ≥65%（阻断）／0 bug（阻断）／8 条架构守护 |
| 实时通信 | Spring WebSocket | 通知与监考 |
| 限流熔断 | Sentinel | 下游流控 + 慢调用熔断 |
| AI 引擎 | Python FastAPI | KG 8001 / RAG 8002 / LoRA 8003 |
| 图数据库（可选） | Neo4j | 5.26，本机独立实例；缺失时 KG 自动回落内存图 |

数据面口径：当前库表 **61** 张（V12 组织四表后 V13–V17 续增）；`index.sql`（测试库建表脚本）已回补至 61，`V1` 因 Flyway 校验和冻结仍为 48 张——漂移管控见 `docs/后端待办项.md` S-03 与手册 DR-61。

### Python 侧三服务

Java 后端经标准化集成层 `com.aicourse.integration` 与 Python 微服务对接（`ContractPaths` 是调用约定的唯一事实来源）：

| 服务 | 目录 | 端口 | 说明 |
| --- | --- | --- | --- |
| 知识图谱 | `python-services/kg_service/` | 8001 | 11 个契约端点；`kg_store.py` 支持 Neo4j 与内存双后端（不可达时自动回落内存图）；多跳路径规划在图库侧完成，Java 侧只做本地 BFS 兜底；CI 护栏 `test_kg_contract.py` |
| RAG 检索 | `python-services/rag_service/` | 8002 | 9 个契约端点 + `rag_engine.py` 真实混合检索（BM25 + 向量 + RRF，重依赖懒加载）。⚠️ 语料落在 gitignore 的 `data/`，换机或重建环境时用 `python scripts/seed-rag-corpus.py` 从 `scripts/rag_corpus.json` 重放并重建索引（现仅课程 1 有效；语料中 4007 段已随 V18 成孤儿，待清理） |
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

> Docker／Compose 与"一台云主机上线"的全部内容（15 服务与 profile、必填环境变量清单、存量卷 `chown` 一次性迁移、TLS 与两条传输层路径、监控与备份、断链清单）已收在 `docs/部署文档.md` §5／§6／§7／§9。**编排尚未在真实环境实跑**：镜像从未实际 build，TLS、恢复演练、容量压测与 deploy secrets 仍挂账（2026-11-09 复核后的逐项状态见部署文档 §6）。本节不再重复命令。

---

## 演示账号

| 角色 | 用户名 | 口令 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `123456` | 全部权限 |
| 教师 | `teacher` / `teacher2` | `123456` | 张建国 / 李文博（V18 已真实化改名，username/口令不动） |
| 学生 | `student` / `student2`~`student5` | `123456` | 以上 8 个为 **V1 内置**（`SeedAccounts.DEMO_USERNAMES` 是单一来源） |
| 教师（富种子） | `ts02`…`ts12` | `123456` | V5 原 12 位教师 + 150 名学生；**2026-11-07 V18 已清除 `ts01`/`st001`/`st101`**，开发库余 ts×11、st×148 |
| 示范课 | — | — | V6 计算机网络 course 4007 **已随 V18 删除**（V18 头注声称保留与实际 SQL 不符，勘误见 `docs/交接文档-2026-11-09.md`）；e2e 图谱锚点已改课程 1 |

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
- **CI**：`.github/workflows/ci.yml` 13 个作业；连 `deploy`／`eval-nightly`／`release` 共 17 个唯一作业键（清单见 `docs/测试文档.md` §7）；`frontend-e2e` 仍是 `workflow_dispatch` 手动触发，且 Actions 结果本机不可观测 ⇒ **本机绿不等于 CI 绿**。DR-20／DR-26 仍是残项；DR-23（引擎容器化）连同其余 Docker 相关项已从「留待后续」改为 **Phase 1 上线必做**，逐项前置见 `docs/后端待办项.md` §6 与 `docs/部署文档.md` §6。

---

## 已知边界（表述不得越过这条线）

- 当前只有**本机原生进程**这一条部署形态可执行；Compose 配置已齐备（15 服务，含 `ft` profile）但从未在目标环境实跑（断链清单见 `docs/部署文档.md` §6 与 `docs/后端待办项.md` §6）。**这条形态只服务开发与验收，不构成生产就绪**：Phase 1 上线必须补齐容器或等价编排、TLS/反代、密钥、备份恢复与监控告警。
- 集成层（12 例 Testcontainers）与 Trivy 漏洞库侧**只在 CI 有读数**。
- 视觉基线覆盖 5 个锚点页（10 张，明暗各一），非锚点页的渲染缺陷抓不到；无障碍只有近似读数，**不构成 WCAG 合规结论**。
- 压力读数来自本机笔记本单实例，**不是生产容量结论**。
- 微调引擎可用 ≠ 能力可售：小样本必然过拟合，推理文本会退化为语料外乱码（`docs/后端待办项.md` §7 AG-2）。
- 多租户、计费、小程序、桌面端属 Phase 2/后续路线；Phase 1 是**单租户生产**，当前代码 `tenant_id` 零命中。

---

## 参与贡献

> 由 AI 代理参与本仓工作时，先读根目录 [`AGENT.md`](AGENT.md)：那份文件只写**代理该怎么干活**（职责边界、验证链、密钥与 Windows 约束、不可宣称清单），不承载架构／规范／测试判据——判据一律在 `docs/` 九份文档里。

1. 先读 `docs/项目手册.md` §6（分支与推送、提交与评审、密钥与敏感信息、证据纪律、Windows 本机约定）。
2. 动手前认领对应待办文档里的条目——判据、进入点、代价与「明确不做」都在 `docs/前端待办项.md`／`docs/后端待办项.md`。
3. 提交前过 `.github/pull_request_template.md` 的自检清单；**数据点改动只改唯一来源**（测试文档 §2 或手册 §8）。
4. 涉及 Python 契约字段时：同步 `ContractPaths` 常量 + `docs/系统架构设计.md` 的集成契约章节 + 两侧契约测试。
5. 报告 Bug／提需求请使用 Issue 模板（`.github/ISSUE_TEMPLATE/`）。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源，完整文本见仓库根目录 [`LICENSE`](LICENSE)。
