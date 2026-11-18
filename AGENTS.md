# AGENTS.md · AI 代理入口（2026-11-17）

> **先读这一份**。5 分钟拿全"这个仓库是什么、怎么跑、什么不能碰、现在做到哪"。
> **单文件入口**：本文件是代理唯一入口。原 `AGENT.md`（工程纪律、验证命令、证据纪律、机械守卫写法）已于 2026-11-15 并入本文件 **§10–§12**，原文件删除；文档索引在 [`docs/README.md`](./docs/README.md)。
> **内部冲突**：战略与权限口径以 §1–§5 为准；执行细节（命令、守卫写法）以 §10–§12 为准。

---

## 0. 30 秒速览

| 项 | 事实 |
| --- | --- |
| **项目** | 校级大学生创新训练项目：**基于知识图谱 + LLM 的 AI 助教系统**，以《计算机网络》为样例课程 |
| **交付** | 系统一套 + 研究报告一份 + 数据集 + 技术文档 + 测试报告 + 软著 + 论文 |
| **时间** | 2026.4–2027.4（**结题 2027.4**，答辩 2026.4，答辩人李昂） |
| **形态** | Spring Boot 3.5（Java 21）+ Vue 3.5 + **Python 三服务**（8001 KG / 8002 RAG / 8003 LoRA） |
| **规模（实测）** | **620 main Java / 271 test Java；71 Controller / 519 方法级 HTTP 映射；61 张表；91 `.vue` / 103 `.test.ts` / 13 Playwright spec** |
| **当前阶段** | 阶段 1–3 已交付；阶段 4 已执行 P0-A1/A2/A3/B/C 等多批收口；当前主线为结题材料（K-01–K-04）与前端 P1 尾量；工作分支 `fix/frontend-loading-spinner`，PR #120 待合并 |
| **最高约束** | **短期大创结题 > 一切**；泛化平台 / 多课程 / 软著扩展**必须结题后** |

**一句话记住优先级**：`PRD > 泛化课程平台 > 多课程 > 软著`，但**结题材料（三指标 + 数据集 + 报告 + 论文）排在最前**。

**当前任务唯一总表**：[`docs/当前状态与待办.md`](./docs/当前状态与待办.md)；本文件 §5 只保留接手必须知道的基线快照。

---

## 1. 权威顺序（冲突时按此裁决，不得自行取舍）

| 层级 | 来源 | 状态 |
| --- | --- | --- |
| **① 最高** | **云端 12 份立项资料**（飞书协作项目云盘） | **只读，不得改写**；含立项书、申报表、原始 PRD、答辩 PPT/稿、3 篇中文论文 + 1 篇英文、DeepSeek RAG 技术文档 |
| **②** | `docs/PRD产品规划.md` | 云端 PRD 只规定**最低限度**；仓库版在其上演进 |
| **③** | **代码实测** | 与 ①② **无矛盾**时以代码为准；**有冲突**时以 ①② + 项目主裁定为准 |

**冲突处理铁律**：任何来源与云端资料冲突，**立即暂停、列出双方出处与差异、上报项目主**，不得默认采用任一方。

---

## 2. 10 分钟上手

### 2.1 启动

```bat
start.bat              :: 全栈单窗口（依赖自愈 + Ollama/MinIO/Neo4j/KG/RAG/FT/API/Web，8 路日志、Ctrl+C 收子树）
start.bat backend      :: 除前端外的全量后端栈
start.bat frontend     :: 只起 Vite
start.bat status       :: 只报全量服务状态，不起进程
```

> 8080 被别的项目占用时用 `API_PORT=8081`；启动器会同时改 `SERVER_PORT` 与 `VITE_PROXY_TARGET`。

Linux / macOS 用同目录的 `start.sh`，子命令与开关完全一致
（`./start.sh` / `backend` / `frontend` / `status`；`--no-color`）。

判定逻辑在 `scripts/start-all.py`；`start.bat` 与 `start.sh` 都只是薄壳。

### 2.2 验证（改什么跑什么）

```bash
# 后端：单元口径 + JaCoCo + SpotBugs + ArchUnit（同一轮全过）
cd backend && mvn -B -o clean verify -DskipITs

# 单个测试类（本机实测可用；PowerShell 下必须用 --% 否则 -D 参数被拆）
mvn --% -Dtest=RagEngineModeTest -Djacoco.skip=true test

# 前端静态门与单测
cd frontend && npm run lint && npm run typecheck && npm run lint:api-refs
cd frontend && npm run test && npm run build && npm run size:gate

# 引擎（仓库根）
python -m pytest python-services/kg_service -q
python -m pytest python-services/rag_service -q
python -m pytest scripts/tests -q

# 只读盘点（结构性数字与密钥状态）
python scripts/audit-stats.py
```

> Windows 本机：系统 `python` 可能指向无 pytest 的 sandbox runtime，改用 `C:\Users\ASUS\AppData\Local\Programs\Python\Python311\python.exe`；PowerShell 用 `;` 连接命令，`-Dkey=value` 前加 `--%`。测试读数口径与最近结果见 [`docs/测试文档.md`](./docs/测试文档.md) §2。

### 2.3 端口与地址

| 端口 | 用途 | 注意 |
| --- | --- | --- |
| **8080** | 后端 API | 当前文档口径；`8081` 是 e2e 并行端口 |
| 8081 | e2e 后端 | 不要与日常 8080 混用 |
| **5173** | 前端 Vite | **必须用 `localhost`**，`127.0.0.1` 连不上（只绑 `[::1]`） |
| 5174 / 5175 | e2e / 视觉 | **5176+ 会被 CORS 白名单拒登录** |
| **8001** | Python KG 服务 | |
| **8002** | Python RAG 服务 | **本项目核心中的核心** |
| 8003 | LoRA 微调 | `start-all.py` 在 `FT_ENABLED=true` 时自动编排；compose `ft` profile 另备 |
| 9000 / 9001 | 本机 MinIO | `start-all.py` 在 `MINIO_ENABLED=true` 时启动原生进程；非 Docker |
| 11434 | 本机 Ollama | `AI_PROVIDER=ollama` 或 `FT_LLM_PROVIDER=ollama` 时自动启动；缺模型只告警 |
| 3306 / 6379 / 7687 | MySQL / Redis / Neo4j | **Redis 为必选依赖**（用户裁定，文档已回写） |

---

## 3. 三条不可破红线（原 `AGENT.md` §1.5.5，现为本文件 §10 的一部分）

1. **知识图谱构建流程必须真实存在**——不得用 prompt 假装图谱；问答/诊断对图谱的依赖必须可演示、可溯源。
2. **学生薄弱点诊断必须基于图谱**——由答题记录映射到图谱节点，不得随机或规则写死。
3. **AI 问答须结合知识库精准回答且防幻觉**——基于课程知识库/图谱；不确定时**如实说不知道**，不得编造。

> 当前 **R-C1 未达标**：`python-services/kg_service/irem.py:13` 自述"不得表述为已实现的、有效的 IREM 能力"；`/api/v1/graph/ingest` 是快照写入而非抽取。**任何"图谱已构建"的表述都是越线**。

---

## 4. 授权边界

### 4.1 可自主做

读代码 / 检索 / 跑只读验证；在同批次内改源码、测试、文档并跑完整验证；新增或**收紧**机械守卫；修复验证暴露的真实缺陷（补测试锁住）；删除**本轮自己创建的**临时产物；按批次本地 `git commit`（默认不推送；推送与 PR 仅在里程碑，PR 由项目主提交）。

### 4.2 必须先取得授权

- 任何**不可逆或影响共享状态**的动作：`git push`、开 PR、删除跟踪文件、删分支、`reset --hard`、`clean -fd`、`DROP` / `TRUNCATE`、改库结构、装/升依赖、改 `.env` 语义、**放宽任何阈值**。
- 产品口径取舍（某能力对外怎么说、某条待办做还是挂账）。
- **跨批次边界的清理**。

### 4.3 绝对禁止（本仓库特有，已付过学费）

| 禁止 | 原因 |
| --- | --- |
| `git rm` / `git mv` / `git restore --worktree` | 父目录含中文，已发生过工作树静默丢失；删文件用文件操作 + `git add` |
| 用 `sed` / `echo >` 改文件 | shell 会改写行尾与特殊字符；用 Edit / Write |
| 把凭据值打印、粘贴、提交、写日志 | 一旦贴进对话即已外泄，唯一止血是轮换 |
| 用 `127.0.0.1` 访问 Vite | 只绑 `[::1]`，连不上 |
| 并发跑覆盖率构建 | JaCoCo exec 落 `${user.home}` 全仓共用，并发会互相截成 0 字节；必须**串行 + clean** |
| 引第二套"看起来一样"的实现 | 同一规则两处各写一份必然分叉（DR-96 启动器合并已付学费） |
| 顺手做批次外清理 | 哪怕明显正确，也另开批次 |
| `.bat` 写 LF 或含中文 | 必须**纯 ASCII + CRLF**（DR-37） |

---

## 5. 当前基线（2026-11-17，接手必读）

> 当前任务、负责人、阻塞项以 [`docs/当前状态与待办.md`](./docs/当前状态与待办.md) 为唯一总表；本章只给基线快照与历史批次入口。

### 5.1 当前快照

| 项 | 当前事实 |
| --- | --- |
| 分支 / 基线 | `fix/frontend-loading-spinner`（本地领先 `origin`；当前提交以 `git log -1` 为准）；`main` 本地与远程均 `94eb6278`；PR #120 待合并 |
| 工作树 | 干净；`frontend/dist` 是构建产物，已忽略 |
| 结构 | 620 main Java / 271 test Java；71 Controller / 519 方法级 HTTP 映射；61 表；91 `.vue` / 103 `.test.ts` / 13 Playwright spec |
| 课程/选课/行政班 | V29 退役 `classrooms` 与 `users.class_id`；退课走 `POST /enrollments/{id}/cancel`（`CANCELLED` + 原因 + 状态/结课门禁）；行政班批量成员 `POST /admin/org/admin-classes/{id}/members/batch`；教学班名单 `GET /teaching-classes/{id}/members/detail` |
| 后端门禁 | 3778 例 / 0 失败 / 0 错误 / 1 跳过；LINE 87.70%、BRANCH 70.79%（门禁 85/65）；SpotBugs 0；ArchUnit 8 |
| 前端门禁 | Vitest 100 文件 / 1227 例；lint / typecheck / Prettier 0；`build` 成功；`size:gate` 通过；视觉 10/10 无像素变化 |
| Python / 启动器 | kg 61 passed；rag 79 passed；fine-tune 55 passed；`scripts/tests` **122 passed**（2026-11-18，含全量服务编排守卫） |
| CI | `ci.yml` 13 job；PR 跑核心 3 spec；PR #120 检查结果以 Actions 当前 run 为准 |
| 分支保护 | `main` 无保护（GitHub API **404 Branch not protected**）；无必填 review / required checks |
| Compose | 16 服务 / 17 卷 / 9 healthcheck；CI 已构建镜像，目标环境未 `up` |
| 存储 | 新上传与分片合并产物写 MinIO（bucket `aicourse-uploads`；`/uploads/**` 由后端代理）；存量 **155 文件 / 28,816,671B** 已迁入并做 SHA-256 **155/155** 校验，原 `storage` 转备份；本机原生 MinIO 已起（非 Docker）；分片仍写 `chunks` |
| 文档 | `docs` 21 个 Markdown + `assets/`；`接管材料` 11 项（00–10 共 11 个 Markdown） |

### 5.2 历史批次（B0–B11）

| 批次 | 内容 | 结果 |
| --- | --- | --- |
| 阶段 1 | 项目全景摸盘 | 《项目全景文档》v1.0 / v1.1，已通过 |
| 阶段 2 | 代码审计 | 585 Java × 12 类模式扫描 + 12 关键类深读；三处系统性错位（A-1 越权 / I-1 503 嗅探 / V-1,V-2 资源） |
| 阶段 3 | 差距分析 + 结构整理方案 + 云端精读 + 论文取全 | 已通过；P0–P2 重排；DB squash 后移 P2 |
| **B0** | 只读取证 | 47 表 **100% 存在**；实测 61 表 = 47 + 14；62 Mapper（`MetricsMapper` 无实体） |
| **B1** | 零风险清理 | 释放 **12,790,578B ≈ 12.20MB**；`logs/` 剩 4 个被进程占用（当时 35.05MB；当前约 37.60MB）未删 |
| **B2** | 文档整合 | 根目录 md **6 → 2**；4 份孤儿移入 `docs/engine`、`docs/archive`；新建 `docs/README.md`；**删除 0 个** |
| **B3** | 双引擎优先 Python | `application.yaml` + `RAGServiceImpl` + `HttpRagProvider` + `CourseKnowledgeBaseServiceImpl` + 新增 `RagEngineModeTest`；**26/26 测试绿** |
| **B4** | HyDE + Cross-Encoder | `rag_engine.py` v1.1：HyDE（假设答案替换向量查询）+ `bge-reranker-base` 重排；**79/79 测试绿**；实测 `retrieval=HYDE`、`rerank_score=0.7545`；顺带修复 D1（pyarrow 16.1→24.0） |
| **B5** | 计网语料入库 | 样例课 **4178《计算机网络》**（`CN202`，8 章 / 5 名选课学生，2026-11-14 建）已灌 **10 篇 / 49 chunks**；`rag_corpus.json` course_id 4007→4178；`/retrieve` 命中 score 1.0、`/ask` 走本地 Ollama 生成 10 条来源，均实测 |
| **B6** | KG 真实构建（IPA + IREM） | 新增 `kg_service/ipa.py`（论文两阶段抽取：学习者抽术语+证据 / 专家命名+说明，`_reconcile` 保证不丢条目）与 `kg_service/irem.py:classify_pairs`（论文五类 INCLUDES/PREREQUISITE/SAME/SIBLING/OTHER）；驱动脚本 `scripts/extract-kg.py`、`scripts/extract-relations.py`。实测 4178 十篇→27 段→**166 候选**，抽检后 16 条入库 → 知识点 **43→59**、Neo4j **59 节点/55 边**；关系 57→58（**全库口径**：4178 单课 54→55，另 3 条为课程 1 的 Python 关系；新增 1 条 SAME 揭示“TCP/IP四层体系结构≡TCP/IP体系结构”重复点）。**kg_service 61 passed** |
| **B7** | 三指标可自动测部分 | 新增 `evaluation/retrieval-hit-eval.py`；实测 4178 top_k=5：10 题、召回 **10/10=100%**、关键词命中 **9/10=90%**、0 失败。`calibration/kg-annotation-4178.md`（59 知识点 + 55 关系 = 114 条，A/B/C 三人标注栏）与 `satisfaction-survey-4178.md`（10 题 5 级量表）**待真人填**。**90% 是规则化近似（关键词出现在 top-5 正文），不等于人工判定「答案正确」** |
| **B8** | A-1 越权 / V-1 删盘 / V-2 可见性 | `CourseAccessControl` 4 处 `isAdmin` 直返改为 `AdminScopeGuard.assertCourseAccess`，`readableCourseIds` 按 `collegeId` 收本院（空值回退教师学院）；新增 `common/UploadPaths`（穿越防护 + 兼容 `/uploads/x` 与 `uploads/x` 两种落库形态；**该类已于后续批次随仓库 `uploads/` 目录移除，存储路径口径唯一由 `StorageLayout` 承担**），`ModerationServiceImpl` 删库行后 `deleteQuietly`；`ResourceController` 上传 `visibleToAll` 改 `FALSE`，存量 12 条全收紧（`12/0/12`）。**31/31 测试绿**（17 + 5 + 9） |
| **B9** | 工程收口 | ①`@Transactional` 实测 **250 处**，真实缺口 **6 处**（非 73）已补 `rollbackFor`，Mapper 接口上 3 处无效注解跳过；②`rag_engine.reset_store()` 注释如实化（原写「清空并重建」实则不删盘）；③**I-1 503 契约化**：`ContractPaths.isUnavailable(JsonNode)` 为判定唯一来源，Python `/retrieve` `/ask` 主降级改 **HTTP 503**，Java `isServiceUnavailable` 命中 503 不重试直接降级。**Python 79 passed / 后端编译 OK / 实测 9999→503、4178→200+5 条** |
| **B10** | 交付链收口（2026-11-16） | ①**#88 冲突**：`main` 新增 `V20__exam_session_question_comments.sql`，结构迁移白名单测试与契约 yml 迁移清单同步补该文件（`d838f2bd`）；②**#89 冲突**：`ClassroomController` 补 `RoleUtil` 引入、`ExamSessionController` 构造器取 main 侧（#95 的 `TrustedClientIpResolver`，取分支侧等于回退监考 IP 单源）（`aac43f5d`）；③**#90 CI 红灯**：`ProfileBody.vue` 的 `String(ui.role ?? '')` 对 ref 恒得 `[object Object]`，`uiRole` 整层失效、角色判定只能靠异步 `profileRole`，profile 未就绪时教师按 student 兜底发一次 `/student/**` → 改为兼容 ref／值两种形态取值并保留 `onMounted` 串行（`3e13075c`）。**RED→GREEN：`--coverage` 下 1 failed → 22 passed；全量带 coverage 91 files passed；`vue-tsc` EXIT=0**；④**P1 三件**：E-05 Neo4j 真连取证（`backend=neo4j`／4178 图 59 节点／`path/plan` 真实 PREREQUISITE 边 1 hop，启动器路径亦复验）、I-01 IT 口径选定②、I-02 Actions 取证步骤入 `docs/测试文档.md` §7.3（`c55dde16`） |
| **B11** | 数据库一致性修复 + Linux 启动器（2026-11-16） | ①**V20 缺位**：本地库 `flyway_schema_history` 21 条、19 之后直接跳 21，`exam_sessions.question_comments` 列不存在 ⇒ #96 的「人工阅卷评语」在本地库实质不可用。按裁定 **A**（`--spring.flyway.out-of-order=true` 一次性）补跑：`Migrating ... to version "20 - exam session question comments" [out of order]` → `Successfully applied 1 migration`；列已存在、历史 `success=1`、总行数 21→22。**过程中踩到并纠正一处**：先用旧 jar 启动，Flyway 应用的是 jar 内残留的旧名 `V20__resources_file_type_len.sql`（与 V21 同内容），已删该错误历史行并改用 `target/classes` 重跑；随后 `mvn package` 重建 jar（校验：含 `V20__exam_session_question_comments.sql`、无旧名残留）。**未留持久配置**（`out-of-order` 仅作启动参数，未写入 yaml）。②**Linux 启动器**：新增 `start.sh`（100755、`sh -n` 通）+ `start-engines.py` 的 `neo4j_bin()` 按平台选（`47282c8e`、`fbb641c2`）。③文档：`项目手册.md` 迁移盘点 21→22（结构 17→18，删「V20 为空号，不补」）；`start.sh` 进 §2.1 上手 |

### 5.3 双引擎的真实含义（**M-13，别搞错**）

- **Java 侧没有任何 RAG 引擎实现**：`integration/rag` 只有 `HttpRagProvider` / `DisabledRagProvider` / `RagContract`，全仓无 BM25 实现类。
- 因此"双引擎" = **Python RAG（8002）** ↔ **纯 LLM（无检索）**，不是"Java 也有一套检索"。
- 切换键：
  - `RAG_PROVIDER=python`（默认）→ 调 Python 8002，不可达自动降级为纯 LLM
  - `RAG_PROVIDER=java` → 不调 Python，直接纯 LLM（**唯一回退手段，无需改代码**）
  - `RAG_ENABLED` 仅在 `rag.provider` 未配置时生效（老配置兼容）

### 5.4 当前优先级（摘要）

| 优先级 | 事项 | 去向 |
| --- | --- | --- |
| **P0** | 结题材料：三指标实测、数据集/标注、研究报告/测试报告/论文/软著 | `docs/当前状态与待办.md` §3 K-01–K-04 |
| **P1** | 前端尾量：C-07 完整 e2e 连续 3 轮、P-10 响应式、P-12 XSS 处置 | 同上 §5 |
| **P1** | 目标环境上线闭环：E-01 起栈、E-03 备份异地、E-04 告警演练 | 同上 §4 |
| **P2** | DB squash、泛化平台 / 多课程、`.git` 瘦身 | 同上 §4 |

> 已关闭的 A-1 越权、V-1/V-2 资源、B9 事务与 503 契约、Redis 必选文档化等不再列为本表待办。

### 5.5 待项目主裁定（不得自行决定）

1. **样例课程口径 —— 已裁定（A）**：**新建《计算机网络》课程 + 灌语料**；课程 1（Python 程序设计）保持不动。
2. **C9 LoRA 基座 —— 已裁定**：以答辩 PPT 的 `deepseek-r1:7b`（训 0.1% 参数）为申报口径。
3. **`logs/` 运行期日志 —— 已裁定（B）**：留待下次重启前清理，不停服；当前约 37.60MB。
4. **【新增，B4 取证】DeepSeek 额度**：实测 `POST /v1/chat/completions` 返回 **402 Payment Required**，`/ask` 与 HyDE 全部降级（有资料→`EXTRACTIVE`）。需项目主处理账户额度，否则三指标实测与答辩演示都拿不到真实生成质量。

---

## 6. 已知陷阱（红灯先怀疑这些）

| 现象 | 先怀疑 | 判据 |
| --- | --- | --- |
| 后端 `Failures: 0` 但套件不绿 | 上下文加载失败记成 **Errors** | 同时读 failures 与 errors |
| 覆盖率 0% 或异常低 | 并发构建互截 exec / 非 clean | 串行 + `clean` 重跑 |
| e2e 大面积 403/429 | 后端未按 `RATELIMIT_LOGIN_LIMIT=200` 启动（DR-85） | 报错文本会点名该变量 |
| e2e 整批跳 `/login` | `.auth` 夹具过期或端口变了 | 按配方重生成夹具，不改断言 |
| grep 零命中就下"不存在" | 搜索面不对（产物/生成文件/被忽略目录） | 换目录换口径复验，负向断言配阳性对照 |
| 中文路径下 JaCoCo 不生成 exec | 已知环境坑 | 加 `-Djacoco.skip=true` 或换短英文路径 |
| PowerShell 下 `-Dkey=value` 被拆 | PowerShell 参数解析 | 加 `--%` 停止解析 |
| Python 引擎 `dep_errors` 里出现 `pyarrow` | `pyarrow < 24` → `pa.json_` 缺失 → datasets/ST 整链 import 失败 | 按 `requirements-engine.txt` 装 `pyarrow>=24,<25` |
| `import sentence_transformers` 进程直接死（0xC0000005） | torch(cu126) 后首次加载 `pyarrow.dataset` 的 DLL 冲突 | 在 ST 之前预加载 `pyarrow.dataset`（引擎已内置） |
| 重排模型报 `Unrecognized processing class` | 未设 `HF_ENDPOINT`，AutoProcessor 去官方站取 model info 超时 | 设 `HF_ENDPOINT=https://hf-mirror.com`（引擎已 `setdefault`） |
| 问答只出 `EXTRACTIVE`、HyDE 恒 `QUERY` | **DeepSeek 账户欠费（402 Payment Required）** | 看日志里的 `402`；这不是代码问题 |
| `/retrieve` 返回里没有 `retrieval` / `rerank_score` | Pydantic 响应模型未声明字段，引擎写好也被静默裁掉 | 检查 `rag_service.py:RagSource`（2026-11-15 B5 已修并加契约断言） |
| HyDE 一直不生效（`hyde.llm_ready=false`） | `provider=ollama` 时需显式放行 `RAG_HYDE_ALLOW_OLLAMA=true`（默认 false 是性能权衡：本地 8B 单次 5–15s） | 本地演示要 HyDE 时在 `.env` 打开并重启 8002 |
| Ollama 抽取“0 条候选”或术语名全是“术语/名称/概念” | ① deepseek-r1 思考过程吃光 `num_predict`（给 800 时最终 JSON 永远出不来）；② 模型复读 prompt 里的格式示例 | 必须 `format:"json"` + `num_predict≥2500`；`think:false` 在 Ollama **0.40.14 不生效**（`ipa.py` 文档串记录） |
| IREM 五类几乎全判 SIBLING，理由写“同一章节相邻” | 候选对的 evidence 只有位置信息，模型把“相邻”直接当“并列”，甚至自述“证据不足但必须五类选一” | 已修 `_CLASS_PROMPT`：明确“相邻不是语义证据、不足时必须判 OTHER”；8B 判关系能力仍弱 → **必须人工抽检，不得当作已具备 IREM 能力** |

---

## 7. 不可宣称清单（越线即失信）

| 不能这么说 | 原因 |
| --- | --- |
| "CI 已绿 / 受 CI 门禁保护" | Actions 页面可观测，但匿名 API 受限流；`main` 无分支保护/required checks；`frontend-e2e` PR 只跑核心 3 spec，完整套件在 main／手动 |
| "集成层本机验证过" | 本机只跑 `-DskipITs`，16 例 IT 由 CI 取数 |
| "依赖无漏洞" | Trivy 漏洞库侧只在 CI 有读数 |
| "无障碍已达标 / WCAG 合规" | 只有近似控制台读数，非 axe、非门禁 |
| "全站无视觉回归" | 基线只覆盖 5 个锚点页（10 张） |
| "生产可承载 N QPS" | 压力是笔记本单实例 |
| "已具备上线能力" | A 路径从未在目标环境实跑 |
| **"知识图谱已构建" / "IREM 已实现"** | `irem.py:13` 明写不得如此表述 |
| **"三指标已达标"** | 目前**零实测** |

---

## 8. 文档去哪查（唯一来源）

| 想知道 | 去哪 |
| --- | --- |
| 文档总索引 / 权威顺序 / 归档 | [`docs/README.md`](./docs/README.md) |
| 项目是什么、角色权限、术语、审计台账 | `docs/项目手册.md` |
| 需求、验收标准、14 维度 140 项 | `docs/PRD产品规划.md` |
| 进程拓扑、数据模型、接口契约 | `docs/系统架构设计.md` |
| 后端 / 前端怎么写 | `docs/开发规范.md` |
| 怎么起、密钥、上线预案 | `docs/部署文档.md` |
| 测试怎么跑、门禁判什么、读数 | `docs/测试文档.md` |
| 还剩什么没做 | **`docs/当前状态与待办.md`（唯一总表）**；`docs/后端待办项.md` / `docs/前端待办项.md` 为证据库 |
| **战略决策、功能边界、三条红线** | `docs/alignment/01-决策对齐总表.md`（最高层需求） |
| 引擎侧真实链路与 D1–D10 断点 | `docs/系统架构设计.md` §8（D1 已修，D3–D10 由 B3–B6 关闭） |
| 代理操作细则（证据纪律、机械守卫、验证命令） | 本文件 §10–§12 |

---

## 9. 新会话接手 5 步

1. `git status --porcelain` + `git log --oneline -8`：确认工作树与最近批次。
2. 读本文件 §1、§3、§5；读 [`docs/当前状态与待办.md`](./docs/当前状态与待办.md)（唯一总表）与 `docs/alignment/01-决策对齐总表.md`。
3. 跑最小读数：`python -m pytest scripts/tests -q`、`cd frontend && npm run lint && npm run typecheck`；需要后端时 `start.bat status`。
4. 确认本批是否触碰 §4.2；触碰就先问。
5. 开工；收口按 §10.1 第 5 步交付（改了什么 / 实跑了什么与没跑什么 / 行为变化 / 下一步）。

---

## 10. 工程纪律（原 `AGENT.md` §1.5 并入）

> 本节原在 `AGENT.md`，2026-11-15 并入本文件，原文件已删除。以下六条对**所有接手代理**有强制约束力。

### 10.1 一批次的标准循环

1. **认领**：从对应待办项取一条（或项目主直接指派），复述判据与出口条件。
2. **建改前证据**：需要视觉／性能／行为对比的，先跑一次留改前读数，再动手。
3. **动手**：最小实现；同批改测试；不把无关清理夹带进来。
4. **验证**：跑齐 §11 里与本批相关的每一层，记录命令与时点。
5. **收口**：更新唯一来源的读数、把结论写成“可主张／不可主张”两栏、按批次本地 `git commit`（**不推送**；推送与 PR 只在里程碑，PR 由项目主提交）。

### 10.2 第一性原理与批判性（不可跳过）

- 任何改动先问“为什么”，从本质出发，不照搬旧码、不跟风。
- **不默认已有代码正确**：旧实现可能是“壳子重、内核浅”的 demo；复用前先做完成度分级（生产级／Demo 级／未实现／有漏洞）。
- **不为省事留劣构**：绝不因成本高、改架构麻烦、工期紧而保留更差的方案；只要利大于弊，小修到大改都该做，不计沉没成本。
- **必须指出项目主的错误**：要求有误、有瑕疵或存在更优解时，先输出“可能错在哪 / 更优方案”，再给结论，抑制顺从偏差；涉及口径与不可逆动作仍先取授权。

### 10.3 范围纪律

- **单课优先**：首期把单门课做深做透；多课程、教务模块不与单课关键路径（三端跑通 > 图谱可演示 > RAG 防幻觉）争资源。
- **愿景隔离**：“上位替代学习通”只是对外叙事，执行上永不占用垂直利器资源。
- **可砍清单（NICE）**：LoRA 微调、社群、激励、移动端——时间告急依次可砍，不阻关键路径。
- 任何超批次范围的清理（哪怕明显正确）都要**另开批次**。

### 10.4 证据纪律（结论没有命令就不算证据）

- 每个结论都要有**一条可重放命令**；只有截图没有命令的不算交付证据。
- 数字必须带**时点 + 口径**（后端“单元口径”与“含 IT”是两套，不可拼接引用）。
- RED → GREEN **成对**留痕：先留失败断言原文，再留通过读数。
- 像素类结论：只有与基线 **md5 相等且字节差 0** 才说“逐字节相同”（`threshold:0` 仍会跳过抗锯齿像素）；引用读数要带截图尺寸。
- 负向断言（“零命中”“不存在”）必须配**阴性对照**：先证明检测器能抓住人造违规样例；静态扫描要先断言扫描窗口里确实有待测代码。
- 断言**不钉会被迁移的活文件**（路径、文件名会变），钉语义、钉结构，不钉行号。
- 注意 `ast.unparse` 会把双引号规范成单引号——按源码文本子串匹配前必须归一化，否则守卫恒假。

### 10.5 密钥与 Windows 约束

- **永不回显值**（含报错、日志、diff 片段、对话正文）；判断状态只引 `python scripts/audit-stats.py` 的派生结论。
- 值只进 `.env`（模板 `.env.example`）与 `deploy/secrets/`；会话夹具 `frontend/e2e/visual/.auth/*.json` 内含令牌，同样严禁入库与打印。口令须 8–32 位且含大小写字母、数字、特殊字符四类；**不要用 `openssl rand -base64 32`**（44 字符超上限）。
- 形状门：`DEEPSEEK_API_KEY` 必须纯 ASCII 且以 `sk-` 开头；同一行键值上写中文备注会让请求在发出前失败（DR-82）。
- 凭据一旦贴进对话即已外泄，唯一止血是到控制台轮换（轮换 `JWT_SECRET` 会使所有会话失效，必须在演示彩排前做）。
- `.bat` 必须**纯 ASCII + CRLF**（DR-37）；不要在 Git Bash 里 heredoc/echo 写 `.bat`——shell 会把 `>nul` 改写成 `>/dev/null`。
- 产物不入库：`target/`、`dist/`、`node_modules/`、`test-results/`、`e2e/visual/tools/evidence/`、`logs/`、`*.log`、`e2e_*_result.json`、`.auth`。

### 10.6 沟通与交付格式

- 中文沟通；结论先行、简短；待拍板事项**摆代价 + 给推荐**，不代替项目主决定，不堆选项。
- 收尾摘要写四段：改了什么（表格／清单）、**实跑了什么与没跑什么**、行为变化、下一步建议。未验证的事不写进结论。

---

## 11. 验证命令全集（原 `AGENT.md` §4.3 并入，按需取用）

```bash
# 后端：单元口径 + JaCoCo + SpotBugs + ArchUnit（同一轮全过）
cd backend && mvn -B -o clean verify -DskipITs
# 含集成层（需 Docker；本机自 2026-10-08 不跑，权威读数在 CI）
cd backend && mvn -B -o clean verify
# 单个测试类（PowerShell 下必须 --%）
mvn --% -Dtest=RagEngineModeTest -Djacoco.skip=true test

# 前端静态门与单测
cd frontend && npm run lint && npm run typecheck && npm run lint:api-refs
cd frontend && npm run test && npm run build && npm run size:gate
cd frontend && npm run format:check && npm run format:check:e2e
# 浏览器侧（前置条件见 docs/测试文档.md §4.3／§4.4）
cd frontend && PW_PORT=5174 npx playwright test
cd frontend && PW_PORT=5175 npx playwright test -c playwright.visual.ts

# 引擎与启动器（仓库根）
python -m pytest python-services/kg_service -q
python -m pytest python-services/rag_service -q
python -m pytest python-services/fine_tune_service -q
python -m pytest scripts/tests -q

# 全栈四件套：两种姿态不可混用（functional → secure 必须重启）
python scripts/run-backend-for-e2e.py functional        # CSRF 关、登录限流 200（后台常驻）
python scripts/e2e/e2e_verify_network_course.py
python scripts/e2e/e2e_deep_verify.py
python scripts/e2e/e2e_stress_verify.py
python scripts/run-backend-for-e2e.py secure           # 重启为默认 CSRF + 默认限流
python scripts/e2e/e2e_security_verify.py

# 只读盘点与数据重建
python scripts/audit-stats.py
python scripts/generate-secrets.py --write             # 只改已存在的键行
python scripts/seed-rag-corpus.py
python scripts/sync-kg-to-python.py <courseId>
```

### 11.1 CI 与门禁位置

- `.github/workflows/ci.yml` 13 个作业；连 `deploy.yml`／`eval-nightly.yml`／`release.yml` 共 **17 个唯一作业键**；`frontend-e2e` 在 PR 跑核心 3 spec，push `main`／`workflow_dispatch` 跑完整流程套件。
- push 事件对 `docs/**`、`archive/**`、`*.md` 走 `paths-ignore` ⇒ **纯文档改动不触发 CI**，不能拿“CI 没红”当验证。
- `main` 当前**无分支保护**（GitHub API 返回 404 Branch not protected），无强制评审、无 required checks；合并采用“CI 可执行 job 全绿”的团队约定。
- 开 PR 走浏览器；GitHub MCP 的写类工具在本仓历史实测报 schema 错且真的没生效，用之前先做一次无害写入复验。

---

## 12. 不可宣称清单 · 细目（原 `AGENT.md` §7 并入，含出处）

| 不能这么说 | 原因 | 出处 |
| --- | --- | --- |
| “CI 已绿／受 CI 门禁保护” | Actions 页面可观测，但匿名 API 受限流；`main` 无分支保护/required checks；`frontend-e2e` PR 只跑核心 3 spec，完整套件在 main／手动 | `docs/测试文档.md` §7、§11 |
| “集成层本机验证过” | 本机自 2026-10-08 只跑 `-DskipITs`；16 例 IT 由 CI 取数 | `docs/测试文档.md` §2.1 |
| “依赖无漏洞” | Trivy 漏洞库侧只在 CI 有读数 | `docs/后端待办项.md` I-05 |
| “无障碍已达标／WCAG 合规” | 只有近似控制台读数，非 axe、非门禁 | `docs/测试文档.md` §4.5 |
| “全站无视觉回归” | 基线只覆盖 5 个锚点页（10 张，明暗各一） | `docs/前端待办项.md` §1 |
| “生产可承载 N QPS” | 压力是笔记本单实例 | `docs/测试文档.md` §6 |
| “已具备上线能力” | A 路径从未在目标环境实跑，只有预案与断链清单 | `docs/部署文档.md` §6 |
| “微调能力可售” | 小样本必然过拟合，推理文本会退化为语料外乱码 | `docs/后端待办项.md` §7 AG-2 |
| “跨设备完成率” | 引导三态只落本地 `localStorage` | `docs/前端待办项.md` §4 |
| “知识图谱已构建” / “IREM 已实现” | `irem.py` 明写不得如此表述（B6 后仍成立） | `docs/PRD产品规划.md` §14.2 R-C1 |
| “三指标已达标” | 目前**零实测** | `docs/PRD产品规划.md` §14.2 R-K1/K2/K3 |

---

## 13. 界面纪律（2026-11-17）

**一句话**：页面靠布局自解释，不靠解释性文字。像 Linear 官网／苹果备忘录那样干净，同时改善 UX、DX、AX。

- 一页 = `PageHead(title)` + 动作 + 内容；页头没有副标题／kicker／来源章，`PageDef` 没有 `desc`。
- 加文案先过三问：动作标签？数据？改不了的事实（不可撤回／仅查询）？都不是就删。删了看不懂就改布局，不要加回一句话。
- 空态一行 `<b>`，错误态一行 + 重试；不写「XX 会出现在这里」。
- 视觉：一套系统字族、四档字号（22／14／13／12）、无衬线标题、无渐变／入场动画／hover 位移／辉光，分隔只用 1px 线。
- 退役类名（模板出现即红）：`page-kicker`／`page-sub`／`card-kicker`／`card-sub`／`card-hint`／`ai-caveat`／`eyebrow`／`lede`／`av-*`（story／feat／motto）。
- 守卫：`frontend/src/ui/__tests__/copyLaw.test.ts`（文案，含阴性对照）＋ `visualLaw.test.ts`（静态视觉）。规则全文见 `docs/开发规范.md` §8.2。