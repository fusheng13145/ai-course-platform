# 历史归档说明

> 本目录收录项目的**时点性审查、验证与修复报告**，以及**已收口批次的规格与计划**。这些文档是特定日期/批次的快照，**已冻结、不再更新**——其结论已被提炼进 `docs/项目手册.md` 的**附录D 审查修正台账**与正文。
>
> **当前 32 个文件 = 31 份正文 + 本 README**（2026-10-27 实测）：2026-09 月报告 21 份 ＋ `收口批次-2026-10/` 5 批 9 份 ＋ 外部接手速查 1 份。

**建立日期**：2026-09-15（随仓库文档收口一并归档）

---

## 1. 收录判据（什么应该进归档）

满足以下**任一**条件的文档应移入本目录：

| 判据 | 说明 |
| --- | --- |
| 时点快照 | 内容绑定某个具体日期/轮次的实测结果（如「2026-09-14 端到端验证」），结论不会随代码演进而更新 |
| 已被取代 | 有更新轮次的同类报告覆盖了它的结论（如 2026-09-08 上线前审查被 2026-09-14 上线前全检取代） |
| 结论已上收 | 其有效结论已并入手册正文或手册附录D，原文仅剩史料价值 |
| 一次性过程产物 | 为该轮工作专门产生、不构成长期规范或操作指南的报告 |
| 已收口批次 | 规格/计划/PRD 所属批次已交付并验收：判据与实现均已落库，状态节从此冻结（如 `收口批次-2026-10/` 的 5 批） |

**不应进归档**（应留在 `docs/`）：

- 唯一权威文档（`项目手册.md`）
- 仍在生效的操作指南（`密钥注入与Secret管理.md`）
- 尚在推进的规划方案（`产品化转化方案.md`）
- **未收口的在办批次**（`docs/进行中/`、`docs/visual-upgrade/`）——收口后整批移入 `收口批次-YYYY-MM/`
- 导航索引（`文档索引.md`）

> **端到端汇总报告已从"例外"改为归档**（2026-10-08 文档收口）：原 `docs/端到端验证与缺陷修复总报告.md` 承担的"当前有效端到端结论"已由手册 **§43** 接手，专题文档因此不再需要例外保留。今后判据按上表执行——**汇总性结论落在手册，不在 `docs/` 保留平行汇总文档**。

## 2. 冻结声明

- 本目录内文件**不再修改**。若发现其中结论有误，做法是：在**新的报告**中更正，并同步更新手册附录D，而不回改归档件。
- 归档件中的**数字、覆盖率读数、缺陷编号**均为当时实测值，可能与当前代码状态不一致，引用时须注意日期。
- **相对链接可能失效**：归档时未批量重写归档件之间的相对链接（有意为之，避免二次改动引入错误）。若需跳转，请以手册附录D 的溯源表与 `docs/文档索引.md` 为准。

## 3. 本目录清单

### 3.1 2026-09 月时点报告（21 份）

| 文件 | 日期 | 内容 |
| --- | --- | --- |
| `实战走查报告-2026-09-08.md` | 2026-09-08 | 启动体验 / 后端性能 / 健壮性与监控 / 安全与配置 / 前端；含同日修复记录（F1–F4）与 2 项误判撤回 |
| `上线前审查报告-2026-09-08.md` | 2026-09-08 | P0-1–P0-5 / P1-1–P1-2 / P2-1–P2-3 / P3-1–P3-3 |
| `结构与代码规范审查报告-2026-09-13.md` | 2026-09-13 | 目录、命名、重复代码、错误处理、门禁配置、仓库卫生 + §8 目标结构、§9 风格约定、§10 可执行清单、§11 修复执行记录 |
| `端到端全流程验证方案与验收报告-计算机网络课程-2026-09-14.md` | 2026-09-14 | 主链路 9 环节验收、角色矩阵、三条造数路径、环境前置 |
| `端到端缺陷修复报告-2026-09-14.md` | 2026-09-14 | R-01–R-12 根因 / 修法 / 验证证据 / 修改清单 |
| `多维端到端深度验证报告-2026-09-14.md` | 2026-09-14 | A 协同 / B 边界 / C 越权 / D 一致性 / E 分片 / F 退回重交 / G 资源版本 + S 安全维；N-01–N-07 |
| `上线前全检报告-2026-09-14.md` | 2026-09-14 | 门禁全绿证据、安全控制确认、上线前清单、子代理失败说明 |
| `pre-launch-check-2026-09-14.md` | 2026-09-14 | 21 项发现（F-01–F-19 / CR-1–CR-12 / Q-1–Q-3 / Q-P1 / CMP-1）与行动清单 |
| `e2e-verify-iteration-2026-09-14.md` | 2026-09-14 | Q-1 并发无超卖 / F-01 静态 ACL / Q-P1 越权负向，迭代复验 8/8 |
| `bugfix-priority-2026-09-15.md` | 2026-09-15 | 21 项按「严重性 × 影响范围」排序逐一处置与验证证据 |
| `secret-governance-F02-2026-09-15.md` | 2026-09-15 | F-02 密钥治理全流程：密钥轮换、git 历史擦除、文件式 Secret 接入、演示账号加固。⚠️ **其中「git 历史擦除」一项已被 2026-10-18 实测推翻**——含 `.env` 的 `dbafa82` 仍是 `origin/main` 的祖先，且当时在用的 `NEO4J_PASSWORD` 逐字节等于历史值，擦除从未发生。归档件按纪律不回改原文，更正记于手册附录 **D.5 F-02 行**与 **DR-83** |
| `仓库整理与文档收口-2026-09-15.md` | 2026-09-15 | 仓库目录收口与文档整合时点报告（原 `deliverables/gstack/`，2026-10-05 归入本目录） |
| `安全加固回归验证签字报告-2026-09-15.md` | 2026-09-15 | 安全加固回归验证签字（原 `deliverables/gstack/`） |
| `上线前全检-全栈三线-2026-09-16.md` | 2026-09-16 | 上线前全检收口报告（原 `deliverables/gstack/`） |
| `上线前全检-修复验证与放行报告-2026-09-16.md` | 2026-09-16 | 修复验证与放行结论（原 `deliverables/gstack/`） |
| `上线前复审-架构师修复规格-2026-09-16.md` | 2026-09-16 | 架构师复审与修复规格（原 `deliverables/gstack/`） |
| `工作区漂移收口与测试对齐-2026-09-16.md` | 2026-09-16 | 工作区漂移收口 / 测试对齐（原 `deliverables/gstack/`） |
| `下一轮迭代-优先改进项清单-2026-09-16.md` | 2026-09-16 | 下轮迭代改进清单（原 `deliverables/gstack/`） |
| `端到端全链路与压力测试报告-2026-09-17.md` | 2026-09-17 | E2E 四件套 131/131 + 压力实测（原 `deliverables/gstack/`） |
| `全维度完成度审查报告-2026-09-17.md` | 2026-09-17 | 全维度完成度审查（原 `deliverables/gstack/`） |
| `端到端验证与缺陷修复总报告.md` | 2026-09-15 定稿（2026-10-08 归档） | 原③专题层的累计端到端汇总（R/N/Q/F/CR/CMP 系列 + 回归基线 + 已知局限）。归档原因见第 1 节；当前有效内容在手册 **§43 / §19.1 / §46.1** 与附录 **D.10 / D.12** |

### 3.2 2026-10 收口批次（`收口批次-2026-10/`，5 批 9 份）

> 原 `docs/superpowers/{plans,specs,prds}/` 下的过程稿，2026-10-27 按批次聚簇归档。**状态节随归档冻结**：回答"现在做到哪了"要回到手册 §16.1 或 `docs/文档索引.md` §5。

| 批次目录 | 文件 | 内容 |
| --- | --- | --- |
| `admin-course-catalog-nav/` | `2026-10-09-admin-course-catalog-nav-design.md`、`2026-10-09-admin-course-catalog-nav.md` | 管理端课程目录导航（规格＋施工计划） |
| `lora-fine-tune-engine/` | `2026-10-17-lora-fine-tune-engine-design.md`、`2026-10-17-lora-fine-tune-engine.md` | LoRA 8003 引擎（八任务；DR-78~DR-81） |
| `visual-phase0/` | `2026-10-18-frontend-visual-phase0.md` | 视觉升级 Phase 0 施工计划（已执行完；主线 spec 仍在 `docs/visual-upgrade/`） |
| `onboarding-redesign/` | `2026-10-21-onboarding-redesign-design.md`、`2026-10-21-onboarding-redesign.md` | 新人引导改造（判据 1–9 与状态在 spec §8；台账 DR-95、LEDGER `P0c-ONB-01…05`＋`P0b-FOCUS-02`） |
| `exam-core-flow/` | `2026-10-26-exam-core-flow-prd.md`、`2026-10-26-exam-core-flow-design.md` | 考试核心流程（创建→选题→发布→提交→批阅；真实后端冒烟 15+ 步） |

### 3.3 外部接手速查（1 份）

| 文件 | 日期 | 内容 |
| --- | --- | --- |
| `handoff-20261027-125336.md` | 2026-10-27 | 由外部工具生成的交接文档（目录结构、依赖版本、端口、部署路径、已知风险）。**含 4 处事实性错误**，勘误见 §5；接手请优先看 `docs/文档索引.md` §8 的实测版速查 |

## 4. 归档件的可追溯性

### 4.1 2026-09 批次

归档采用**字节级复制 + md5 校验一致后移除原件**的方式执行；Git 将 11 处变更识别为 **R100 纯重命名（零内容变更）**。因此归档件的每一字节都可回溯到归档前的仓库状态：

```bash
# 查看归档前的原件内容（提交 10d8078 之前）
git show 10d8078^:"docs/结构与代码规范审查报告-2026-09-13.md"
git show 10d8078^:"deliverables/gstack/bugfix-priority-2026-09-15.md"
```

### 4.2 2026-10-27 迁移批（`docs/superpowers/` → `进行中/` 与 `收口批次-2026-10/`）

同一纪律执行：复制 → md5 一致 → 移除原件（**未使用 `git rm` / `git mv`**）。冻结件 md5（2026-10-27 复核，与移动当轮一致）：

```
c1445b546cc5d9c668fc7815ce860c35  收口批次-2026-10/admin-course-catalog-nav/2026-10-09-admin-course-catalog-nav-design.md
7d0259f81dac94d9624b85bde973cc4e  收口批次-2026-10/admin-course-catalog-nav/2026-10-09-admin-course-catalog-nav.md
692e1a18e2ebffb8d3b72d9860792b04  收口批次-2026-10/lora-fine-tune-engine/2026-10-17-lora-fine-tune-engine-design.md
43762b8aab33a5150a62aca69ab2bf28  收口批次-2026-10/lora-fine-tune-engine/2026-10-17-lora-fine-tune-engine.md
a7a7942876c559bc2d78835e5aa03542  收口批次-2026-10/visual-phase0/2026-10-18-frontend-visual-phase0.md
ca6a557179d5b08fc1c8fb5622bc6f37  收口批次-2026-10/onboarding-redesign/2026-10-21-onboarding-redesign-design.md
13c0f687583e9cf6147b1627cd8109af  收口批次-2026-10/onboarding-redesign/2026-10-21-onboarding-redesign.md
7d1fd37668ce1055f040566f6b873732  收口批次-2026-10/exam-core-flow/2026-10-26-exam-core-flow-prd.md
40d7d4bf466c9e0eaaa6b3a2b4091eb1  收口批次-2026-10/exam-core-flow/2026-10-26-exam-core-flow-design.md
871153616abc60903e8f5adda8e4859e  handoff-20261027-125336.md
```

同时有 3 份**在办文档**只是换目录（未归档）：`docs/进行中/2026-10-23-aicourse-optimization-roadmap.md`、`docs/进行中/2026-10-24-frontend-deep-review-and-improvement-plan.md`、`docs/visual-upgrade/2026-10-18-frontend-visual-upgrade-design.md`。移动时同样做过 md5 一致校验；此后它们按新结构做了路径与校准编辑，故当前 md5 不再等于移动时值（在办文档不在冻结范围内）。

## 5. 勘误：`handoff-20261027-125336.md` 的 4 处事实错误

> 归档件按纪律不回改原文；更正记在这里。全部结论于 2026-10-27 按仓库实测复核，重放命令附后。

| # | 原文说法 | 实测 |
| --- | --- | --- |
| 1 | `.env.example` 与 `.env` 均未含 `NEO4J_PASSWORD`，起 `rag` profile 会失败 | **两者各含 1 行** `NEO4J_PASSWORD`（模板已提供占位） |
| 2 | service 层有「166 个接口」 | 口径混淆：`service/` 顶层 **61 个接口** ＋ `service/impl/` **67 个实现** ＋ `service/component/` **37 个组件** = 165 个文件，不是 166 个接口 |
| 3 | 约 110 个 `composables/use*.ts` | **84 个**（`composables/` 顶层） |
| 4 | CI 作业清单不全（只列到 10 个量级） | `ci.yml` 共 **12 个作业**：`frontend-quality` / `frontend-build` / `backend` / `trivy-scan` / `secret-scan` / `backend-security` / `docker` / `python-kg-contract` / `python-rag-contract` / **`python-fine-tune-contract`** / **`launcher-tests`** / **`frontend-e2e`** |

重放命令（仓库根，Git Bash）：

```bash
grep -c "^NEO4J_PASSWORD" .env.example .env
find backend/src/main/java/com/aicourse/service -maxdepth 1 -name "*.java" -print | wc -l
find backend/src/main/java/com/aicourse/service/impl -maxdepth 1 -name "*.java" -print | wc -l
find backend/src/main/java/com/aicourse/service/component -name "*.java" -print | wc -l
find frontend/src/composables -maxdepth 1 -name "use*.ts" -print | wc -l
grep -nE "^  [a-zA-Z0-9_-]+:$" .github/workflows/ci.yml
```

> 该文档其余可核实项（Python 三服务默认关闭且无 Dockerfile、端口表、JWT HttpOnly Cookie + CSRF 双提交、`--profile full` 明文 HTTP 需显式关 HTTPS/CookieSecure、`local` profile 无独立配置文件、Flyway + `SchemaMigration` 双轨迁移）与仓库现状一致，已吸收进 `docs/文档索引.md` §8 的实测速查。

---

> 结论索引见 `docs/项目手册.md` 附录D；文档体系总览见 `docs/文档索引.md`。
