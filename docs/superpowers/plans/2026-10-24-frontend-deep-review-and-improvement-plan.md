# 前端系统性深度评估与改良方案（2026-10-24）

> **产出者**：ZCode 会话（项目主委托）· **性质**：评估报告 + 规划件；**除 P0 快赢批（FE-01/02/03，本批实施）外零代码改动**
> **证据基线**：代码与配置引用一律钉 `main@0660775`（2026-10-24 实测与工作树 HEAD 一致，`.ua/meta.json` 的 `gitCommitHash` 同点）；全量知识图谱取 `.ua/knowledge-graph.json`（3328 节点 / 5725 边 / 10 层，analyzeFiles=1551，lastAnalyzedAt=2026-10-23T16:42:44Z，与手册、README 三方一致）。本会话读数时点 **2026-10-24**，均为本机现量。
> **数字纪律**：沿用路线图口径——每个读数带时点与出处；引用 = 采自手册附录 B / 路线图 / LEDGER，本轮未复核；禁百分比/清零式断言；工作量口径为单人串行人日。
> **与母文档的关系**：本文件**不取代** 2026-10-23 路线图（R-01…R-22）、视觉升级 spec（Phase 0–6）、LEDGER（D-1…D-10）；凡既有条目已覆盖的债，只做**映射与排序**，不重开判据。状态唯一落点是本文件 **§7**。冲突时以手册为准。

---

## §0 约束继承（本方案的一切批次都受这些既定条款约束）

1. **判据与状态分离**（视觉 spec / 引导 spec 既定纪律）：本文件 §3 的验收判据一经落盘不得随执行改写；执行偏差只写 §7 状态节。改判据必须同一次 diff 同步手册 §16.1 与测试断言。
2. **棘轮只许向紧**：类型基线（`typecheck-baseline.json`，现 402 条 / 90 文件）只许下降；覆盖率阈值只许上调；`size:gate` 预算只许收紧。禁"改方案不改阈值"。
3. **视觉基线纪律**：`anchors` 全表一律两轮且两轮都记；`--update-snapshots` 须人工看图 + 台账记 commit；禁拿"基线没动"当视觉改动的生效证据（本批 P0 为纯逻辑重构，基线未动 + 行为测试绿即为预期结果，属合法证据形态）。
4. **单一写者文件**（路线图 K-09）：`anchors.spec.ts`、`visual-helpers.ts`、`ci.yml`、`typecheck-baseline.json`、`eslint.config.js`——本方案任何批次不触碰，除非该批次本身就是其唯一目的。
5. **路线图 anti-goal（AG-1…AG-9）全部继承**，本方案不新增与它们冲突的条目；本方案新增的"不做"列在 §5。
6. **仓库纪律**：严禁 `git rm` / `git mv`（中文路径教训）；`.bat` 纯 ASCII + CRLF；新增文档先同步 `docs/文档索引.md`；推送决策归项目主（R-02 口径）。
7. **视觉批次顺序**：D-R5 裁定 Phase 1（token 纵深）后置且"带条件放行"——本方案所有对比度/圆点类修复（FE-05）须**标注与 Phase 1 scrim 家族的边界**，只修单点缺陷值，不做 token 架构改造。

---

## §1 评估方法

- **输入一**：`.ua/` 全量知识图谱（10 层分层 + 5725 条依赖边），用于确定模块依赖主干与跨层数据流，交叉验证代码实证。
- **输入二**：前端源码九维度逐项取证（技术栈与工程化 / 代码组织 / 状态管理 / 组件复用 / 性能 / 数据请求 / 代码质量 / 测试 / UX 与 a11y），每条结论有 `file:line`（钉 `main@0660775`）或 grep 读数。
- **输入三**：过往方案全量对账——路线图 R-01…R-22（含 §3.5 执行台账现状）、视觉升级 spec Phase 0–6、LEDGER D-1…D-10、规划文档 §6.4 Q1–Q6、手册 §19/§21/§43/§46。
- **方法约束**：只认可复现读数；每条痛点先问"既有条目是否已覆盖"，避免另起炉灶。

---

## §2 九维度评估结论

### 2.1 总判断

**前端底子成熟，痛点不在"缺基建"，在"结构收敛未完成 + 已登记缺陷未收口"。** 工程化门禁成体系且全部在 CI 生效（ESLint 0/0、类型棘轮、覆盖率棘轮、体积预算、Prettier、`lint:api-refs` 自研 API 引用一致性门）；数据请求层（`request.ts`）具备 CSRF 双提交、401 单飞静默刷新、幂等重试、AbortController 页面级取消、消息节流去重等成熟能力；测试 171 文件（vitest 1899 例，B.2 口径）+ Playwright 流程 52 例 + 视觉 24 张基线。以下痛点按"影响 × 修复代价"排序。

### 2.2 逐维度读数与痛点

| # | 维度 | 现状读数（时点 2026-10-24，钉 main@0660775） | 痛点 / 技术债 | 既有条目映射 |
| --- | --- | --- | --- | --- |
| 1 | 类型与解包债 | 棘轮基线 **402 条 / 90 文件**（`typecheck-baseline.json` 自述；strict:true 实测存量 2774/257）；**145 处**防御式双取值 `res.data?.records \|\| …`；`as X` 断言 68 处；`: any` 仅 7 处、`as any` 0、ts-ignore 0 | AG-7 已禁 strict 全量迁移；债的**恶化**被棘轮封死，但存量持续抬高新人理解成本；145 处解包是"后端分页信封不统一"的前端代偿 | **R-11**（P2 六批 13.5–18 人日，后置）；Q1 |
| 2 | 状态管理 | Pinia 仅 1 个 store（`stores/auth.ts` 374 行，职责正确）；状态主体在 **63 个 composables**，其中 **7 个 ≥500 行**：useStudentExams 797 / useAnalytics 689 / useKnowledgeGraph 591 / useTeacherGrades 588 / useAIAssistant 514 / useQuestions 506 / useCourseDetail 502 | "每页一个巨型 composable"模式**本身自洽**（跨页共享少、生命周期清晰），不需要改造成全局 store；真正的债是单个 composable 内聚合了"列表/详情/弹窗/倒计时"多视图状态，改动半径大、回归面大 | 无既有条目 → **本方案新增 FE-06/FE-14** |
| 3 | 组件复用 | `formatDateTime`（`toLocaleString('zh-CN')` 版）**4 处同源重复**（CourseExamPanel:234 / CourseTaskPanel:224 / CourseResourcePanel:213 / CctAssignmentTable:78——末者名为 formatDate 实为完整时间）；另 3 处 `toLocaleString` 为**数字千分位**（不同类，不属本债）；手写 `class="tbl"` 表格 ×8 视图、`l1-card` 样式 ×37 视图复制粘贴；`el-dialog` 开标签 **151 处**（`ui/Modal` 已有能力齐平却未迁移） | 复用原语已存在（utils/datetime、ui/Modal、EmptyState 96 文件引用、useListFeedback/useFormSubmit/useClientPagination），缺的是**收口执行** | Q2；视觉 Phase 4（el-dialog→Modal） |
| 4 | 性能（包体） | dist 4.3MB；`size:gate` 预算 JS 合计 3400KB / EP 830KB / echarts 900KB / xlsx 380KB；`manualChunks` 8 组分包；路由全懒加载；echarts 按需注册（8 图表 + 7 组件）；EP 样式全量引入（注释声明莫兰迪主题覆盖依赖此文件，**有意决策**） | **icons 全量全局注册 ~300 组件**（main.ts:57-59，与按需策略相反）；xlsx `import *` 静态引入 ×3（grade 族 composables，随路由懒加载但页面即载）；无 bundle 构成分解报告——R-15 判据要求的"先量"从未做过 | **R-15**（本轮不做 → FE-08 先量 / FE-11 后治） |
| 5 | 性能（渲染） | `deep:true` watch 0 处（无深层监听反模式）；模板内函数调用 3 处（CgtSidePanel:10 / GradeAnalyticsPanel:21 / QmStatsDialog:36 均为 `.slice()`）；`key=index` **32 处**（逐处定性见 FE-03） | 渲染层无系统性反模式；模板切片是微小重复分配；key=index 中**真正的缺陷类别是"输入行可增删 + v-model 按下标"**（3 个对话框），display-only 点位属可豁免 | 无 → **FE-02 / FE-03** |
| 6 | 数据请求 | `request.ts` 504 行；能力齐全（见 §2.1）；API 集中 43 模块 + `lint:api-refs` 门 | 错误处理单函数约 **180 行**（request.ts:324-501），分类/文案/跳转/刷新耦合，改动半径大 | 无 → **FE-07** |
| 7 | 代码质量 | TODO/FIXME/HACK **0**；console.log 1 处（logger 内 isDev 门控）；死代码 1 处（`utils/permission.ts` getDefaultRouteByRole，注释自述保留）；未 TS 化 `.js` 文件 **10 个**（userMenuItems / useSpotlightOverlay / onboarding×8）；onboarding 3 个 .js 刚经 R-19b 收口 | 质量面干净；遗留主要是 .js 未 TS 化（onboarding 8 个建议最后动，避免扰动刚闭合的判据 9/9） | Q 系列 → **FE-09** |
| 8 | 测试 | vitest **171 文件 / 1899 例**（B.2：181/**1899**，R-19b 后口径）；覆盖率棘轮仅统计逻辑层 .ts（287 个 .vue 显式排除，配置注释有理由）；Playwright 流程 52 例 + 视觉 24 张；e2e 仍 `workflow_dispatch`（R-04 已接线待 3 连绿） | .vue 视图层零覆盖率是**有意取舍**非缺陷；真缺口是 e2e 未进主触发链（待 R-04 收口） | **R-04**（进行中） |
| 9 | UX 与 a11y | 暗色主题完整（D-1 已修两载体）；引导批判据 9/9（DR-95）；a11y 组合函数族（useA11y/useFocusTrap/useAnnouncer…）+ Modal 焦点陷阱有单测；移动端仅 <768px 侧栏抽屉；i18n 无（全中文硬编码，大创赛范围内合理） | **LEDGER 未收口缺陷 5 项**：D-5 暗色登录 feature 对比度 2.96:1、D-6 admin 徽章 1.22:1、D-8 暗色图谱标签 1.13:1、D-9 图谱节点圆点不画（两主题）、**D-10 分析页 finalScore 列不存在 → 整块空态被钉进基线**（修复须同批重录 fixture + 2 张基线） | **D-5…D-10**（LEDGER）→ FE-04/FE-05 |

### 2.3 知识图谱确认的数据流主干（与代码实证一致）

`views → api/*.ts（43 模块）→ request.ts（/api + withCredentials + CSRF + 单飞刷新）→ 48 Controller → Service → Mapper → MySQL`；AI 流式走裸 fetch + ReadableStream（`api/ai.ts`）；实时通知/监考走 WebSocket（JwtHandshakeInterceptor 握手鉴权）；KG/RAG/LoRA 三引擎经 `integration/` 契约层，不可达时 503 降级 + 本地 BFS / 纯 LLM 兜底。前端对这套契约的消费面（43 个 api 模块）有 `lint:api-refs` 门保护，**数据流层面无需重构**。

---

## §3 改良项总表（FE-01…FE-14）

> 编号规则：**FE-\*** 为本方案新体系，不占用 R 序列；"映射"列指向既有条目。优先级：P0 = 本批实施（低风险快赢）；P1 = 下一批（结构收敛，判据已立）；P2 = 长线（与既有母文档对齐，本轮不启动）。

| 编号 | 标题 | 优先 | 依赖 | 人日 | 验收判据（一句话） | 映射 |
| --- | --- | --- | --- | --- | --- | --- |
| **FE-01** | formatDateTime 同源重复收口（4 处 → utils/datetime 单源） | P0 | — | 0.3 | 4 文件本地实现删除、统一 import `@/utils/datetime`；`npm run test` 全量绿；CourseDetail 不在任何视觉锚点内（12 锚点清单已核，§7 状态节记证据） | Q2 |
| **FE-02** | 模板内切片调用转 computed（3 处） | P0 | — | 0.2 | CgtSidePanel / GradeAnalyticsPanel / QmStatsDialog 三处 `slice` 移入 computed；vitest 全量绿 | Q2 |
| **FE-03** | 输入行对话框 v-for 稳定键（CgPolicyDialog） | P0 | — | 0.3 | `:key` 由下标改为行标识 `rowKey`；保存 payload 为显式映射（key/name/weight），行标识不外泄；32 处 key=index 的其余点位逐处定性豁免（豁免清单入 §7） | Q2 |
| **FE-04** | D-10 分析页空态修复 | P1 | 后端 SQL | 1–2 | `finalScore` 列修复 + fixture 同批重录 + 2 张基线重录（LEDGER 原文纪律："只改后端不改桩＝把修复藏进桩里"）；`anchors` 两轮全绿 | **D-10** |
| **FE-05** | D-5/D-6/D-8 对比度 + D-9 图谱圆点 | P1 | — | 1–2 | 四缺陷逐一闭环；涉及基线的点位两轮重录 + 人工看图 + LEDGER 记 commit；**只修单点色值/绘制，不动 token 架构**（D-R5 边界） | **D-5/6/8/9** |
| **FE-06** | useStudentExams（797 行）拆解 | P1 | — | 1.5–2.5 | 拆为子 composables（列表/答题/倒计时/自动保存），**对外导出面不变**；vitest + e2e `exam-resume` 全绿；拆后无单文件 >500 行 | Q1 |
| **FE-07** | request.ts 错误处理拆纯函数 | P1 | — | 1 | 324-501 的错误处理拆为可独立测试的分类/文案/动作纯函数；行为逐分支等价（api 23 个测试文件保护）；`request.ts` 单文件行数下降 | 无 |
| **FE-08** | R-15"先量"半步：bundle 构成分解 + 六项读数 | P1 | — | 0.5–1 | 产出一页构成分解（按 manualChunks 分组）+ 六项读数逐条记录入 `docs/visual-upgrade/evidence/`，零代码改动 | **R-15**（判据前半） |
| **FE-09** | 10 个 .js 文件 TS 化 | P1 | — | 1–1.5 | 先 userMenuItems / useSpotlightOverlay，onboarding 8 个最后（避免扰动刚闭合的判据 9/9）；每文件迁移后 vitest 全量绿 | Q 系列 |
| **FE-10** | 类型债 402 → 0（六批） | P2 | — | 13.5–18 | 沿用路线图 R-11 全部判据（errors+files 双降、byFile 键集合一致），**本轮不启动** | **R-11** |
| **FE-11** | R-15"后治"：icons 按需、xlsx/html2canvas 动态 import | P2 | FE-08 读数 + 拍板 | 1.5–2.5 | 需先拍板重启 R-15；icons 治理须先核 menus.ts 动态组件解析面（`resolveComponent`/字符串名引用），防 tree-shaking 断供；size:gate 预算随实测收紧 | **R-15**（判据后半） |
| **FE-12** | 视觉升级 Phase 1–6 | P2 | R-09/R-10、D-R5 | 见 spec | 严格按视觉 spec 顺序（Phase 1 带条件放行；Phase 4 迁移弹层时删调用方 ref+useDialogFocus 两行；22 处 teleport 缺省方向差异逐点核对） | **Phase 1–6** |
| **FE-13** | el-dialog 151 处 → ui/Modal 迁移 | P2 | FE-12 Phase 4 准入门 | 3–5 | 归入 Phase 4 批内执行，不单独开批；护栏地板（82 开标签/73 文件）随迁移只紧不松 | **Phase 4** |
| **FE-14** | 巨型 composable 族拆解推广 | P2 | FE-06 模式验证 | 3–5 | useAnalytics/useKnowledgeGraph/useTeacherGrades/useAIAssistant/useQuestions/useCourseDetail 逐个按 FE-06 模式拆；每个拆解批独立提交 + 全量回归 | Q1 |

**P0 批补注（执行定性，证据见 §7）**：FE-01 初拟"三面板"，实核发现 CctAssignmentTable.vue:78 为第 4 处同源实现（名为 formatDate、行为为完整时间格式化），并入同批；FE-03 初拟三点位（BarChart/DropdownMenu/InVoteDialog），实核定性地收敛为 **CgPolicyDialog 一处**——BarChart 的下标参与几何定位且数据为全量替换、DropdownMenu 为全量替换无行内状态、OnboardingGuide 为静态步进列表（刚收口不碰），display-only 其余点位无重排状态可错位；InVoteDialog/QmEditDialog 的 form 是**父组件共享对象**，改行标识须连父组件与提交映射同批改造，升格为 P1（记入 FE-06 批附页）。

---

## §4 批次执行序

### 4.1 P0 快赢批（本批，2026-10-24）

- **串行**：FE-01 → FE-02 → FE-03，各自独立提交（一事项一提交，便于单提交 revert）。
- **回归门（全绿才算闭）**：`npm run test`（vitest 全量）；`npm run lint` 0/0；`npm run typecheck:gate` 402/90 持平；`npm run build` + `size:gate` 通过；`npx playwright test` 流程 52 例；`npx playwright test -c playwright.visual.ts` 两轮（纯逻辑重构，24 张基线应零变化；任何差异当场排查，**禁止** `--update-snapshots` 吸收；跑后 `git status --porcelain docs/` 为空）。
- **文档同批动作（R-22 口径）**：本文件随批提交入库；`docs/文档索引.md` plans/specs 组数 5 → 6；B.2 用例数如随本批变化则回写。

### 4.2 P1 结构收敛批（下一批，建议 3–5 人日窗口）

- **可并行**：FE-08（零代码，先做——其读数是 FE-11 的前置）；FE-04（后端 + 基线重录，单独提交序列）；FE-05（纯视觉缺陷，独立于 FE-04 的基线点位）。
- **串行**：FE-06 →（其模式验证后）FE-14；FE-07 独立。
- **硬门槛**：FE-04 与 FE-05 都涉基线重录，**不得同一天混做**——一次跑 visual 只验证一个缺陷批的像素变化，否则归因污染（K-07 同源教训）。

### 4.3 P2 长线批（不启动，只挂账）

FE-10/11/12/13/14 全部沿用既有母文档的触发条件与重启信号；本方案不新增排期。

---

## §5 明确不做（本方案新增；AG-1…AG-9 继续有效）

| # | 不做的事 | 理由 | 重启信号 |
| --- | --- | --- | --- |
| N-1 | i18n / 多语言 | 界面全中文硬编码，大创赛交付范围内无外部用户诉求；引入 vue-i18n 是全量文案工程 | 出现真实的多语种交付要求 |
| N-2 | 虚拟滚动引入 | 现有 `useClientPagination` 客户端分页原语已覆盖长列表场景（只渲染当前页切片），无一处列表证实需要万行级渲染 | 任一列表实测 >200 行且滚动掉帧 |
| N-3 | 引入 TanStack Query / SWR | `request.ts` 已具备缓存窗口外的全部关键能力（单飞刷新/重试/取消/节流），引入数据缓存库是全 composable 层重写，收益不抵扰动半径 | composables 数量因请求态样板持续膨胀且 FE-14 拆解后仍失控 |
| N-4 | Pinia store 扩张（把 composables 迁入全局 store） | 全仓仅 auth 一处真全局态，现状模式自洽；巨型 composable 的债在"单文件聚合过多"，不在"缺全局 store" | 出现 ≥3 个页面共享同一份非会话状态的真实需求 |
| N-5 | husky / lint-staged 本地钩子 | CI 门禁已全量生效且团队单人主导；本地钩子在 Windows 环境的维护成本暂大于收益 | 多人协作且本地漏跑门禁成为实际返工原因 |
| N-6 | EP 样式改按需引入 | main.ts:4 注释声明莫兰迪主题覆盖依赖全量样式文件，属**有意决策**；动它属 token 级改造 | FE-12 Phase 1 token 纵深落地后一并评估 |

---

## §6 风险登记

| 编号 | 风险 | 触发信号 | 缓解 |
| --- | --- | --- | --- |
| K-FE-1 | FE-01 格式统一改变用户可见文本（`2026/10/24 14:30:00` → `2026-10-24 14:30:00`） | 基线或 e2e 断言红 | 已核 12 锚点不含 CourseDetail 宿主页、e2e 无日期文本断言（§7 记证据）；若仍红，按基线纪律排查而非吸收 |
| K-FE-2 | FE-03 行标识泄漏进保存 payload | 后端收到未知字段 | savePolicy 为显式映射构建 payload（key/name/weight），泄漏面为零；提交说明中注明 |
| K-FE-3 | 巨型 composable 拆解引入回归（FE-06/14） | e2e exam-resume / vitest 红 | 对外导出面不变 + 每步拆解独立提交；考试流有 e2e 保护 |
| K-FE-4 | 类型债"搬运式下降"（FE-10 重启时） | 总数降但 byFile 键集合与声明不符 | 沿用 R-11 三判据（双数 + 键集合 + diff 范围） |
| K-FE-5 | 基线重录批（FE-04/05）互相污染归因 | 同批多张基线变化 | §4.2 硬门槛：两个缺陷批不同天混做 |
| K-FE-6 | 文档漂移（本文件写完未进主线，v1 前科） | 本文件仍是未跟踪状态 | 本批随 P0 提交入库；索引同步为出口判据 |

---

## §7 执行台账（状态节——状态只写这里，不改 §3 判据）

| 项 | 状态 | 提交 | 本机验收读数（2026-10-24） |
| --- | --- | --- | --- |
| 评估 | ✅ 完成 | `a869ae0` | 九维度读数见 §2（钉 main@0660775，`.ua` 图谱 3328 节点/5725 边交叉验证）；文档索引 5→6 组 |
| FE-01 | ✅ 已闭 | `cba40b4` | 4 处同源 `toLocaleString('zh-CN')` 版 formatDateTime 删除（三面板 + CctAssignmentTable，末者名为 formatDate 实为完整时间），统一 import `utils/datetime`；三面板组件测试 14 例绿；12 视觉锚点不含宿主页 CourseDetail、e2e 无日期文本断言（零基线扰动取证） |
| FE-02 | ✅ 已闭 | `e1db2c0` | CgtSidePanel/GradeAnalyticsPanel/QmStatsDialog 三处切片转 computed，QmStatsDialog 的 v-if 同步收敛；GradeAnalyticsPanel 测试 2 例绿；`typecheck:gate` 402/90 持平（GradeAnalyticsPanel 的 4 条 TS2339 为基线 byFile 存量，非本批引入） |
| FE-03 | ✅ 已闭 | `2097cbe` | CgPolicyDialog 构成项行改自增 rowKey 稳定键（savePolicy 为显式映射，rowKey 不外泄）；其余 31 处 key=index 逐点定性豁免：BarChart（下标参与几何定位 + 数据全量替换）、DropdownMenu（全量替换无行内态）、OnboardingGuide（静态步进、刚收口不碰）、其余 display-only 列表（无重排状态可错位）；InVoteDialog/QmEditDialog 因 form 由父组件共享，升格 P1 与父组件同批改造（记 FE-06 批附页） |
| 计划外发现 | ✅ 已闭 | `7ac9ef3` | 视觉第 1 轮 84 passed + 1 red：helpers.spec 的 sha256 篡改守卫仍钉 22 张且 4 哈希过期（R-08 三次基线入库均未同步清单：`ea48fe1` error-404 ×2、`93b09cb` student-dashboard ×2、`c0b94ce` 暗色图表 ×2；当轮只跑 anchors 未跑 helpers）。按"工作树=HEAD 字节"取证后清单同步 24 张全集；非本批引入 |
| P0 回归门 | ✅ 全绿 | — | vitest **181 文件 / 1899 例** ×2（改动前后各一）；lint 0/0；`typecheck:gate` **402/90 持平**；build 2.29s + `size:gate` 五项全过（JS 合计 3119.55/3400、echarts 805.06/900、EP 735.14/830、xlsx 323.28/380、其余 1256.07/1300、EP CSS 381.6/420）；Playwright 流程 **54 passed**（2.8m；52 口径 + R-06/R-07 各新增 1 例）；视觉 **85 passed 两轮**（含 anchors 24 张零差异）；跑测后 `git status --porcelain docs/` 三张 motion 取证图被改写，按 K-03 纪律 `git checkout --` 还原后为空 |
| 观察挂账 | 🟡 待下批 | — | ① 手册 §243 行/B.2 的 e2e "35 例" 为 2026-10-18 时点读数，现量流程套件 54 例（R-06/R-07 批次增量未回写 B.2，本批未改用例数故不代写）；② onboarding.spec 17 例（手册 REQ-FE-008 行）与 e2e 现量的对账建议随 R-22 下批一并做 |
| **P1 批（2026-10-24 续）** | | | |
| FE-08 | ✅ 已闭 | `2cfc724` | 构成分解 + 六项读数入 `evidence/bundle-composition-2026-10-24.md`（零代码）：JS 3119.54 KiB，较 2026-10-13 +106.34 全在"其余功能块"；三个 FE-11 候选（icons 落 EP 块内、xlsx 动态 import、html2canvas 死依赖零引用） |
| FE-07 | ✅ 已闭 | `c7da1df`+`01a4103` | 纯函数层提至 `src/utils/requestError.ts`（首版落 api/ 被死代码守卫正确拦截后按守卫语义归位）；request.ts 504→460 行；+13 例单测；422 边角行为逐字等价 |
| FE-06 | ✅ 已闭 | `4060e38` | useStudentExams 797 → 编排层 455 + 列表 127 + 作答 236 + 定时器 100（全 <500 行）；导出面逐名不变；21 例绿、全量绿、类型持平 |
| FE-09（部分） | ✅ 首两件已闭 | `7c35af5` | userMenuItems / useSpotlightOverlay TS 化（消费方零波及）；onboarding 8 个按判据缓办；src 下非 onboarding .js 清零 |
| FE-04 | ✅ 已闭 | `9bb2dba` | D-10 根因＝AdminAnalyticsComponent 原生 SQL 驼峰 `finalScore`（表列 `final_score`，V1:267）；列名纠正后实测 gradeDistribution [6,40,76,73,81]、roleActivity 3 条、completionRate 54.1。**连带发现并同批修复**：useAnalytics 对 completionRate/growthRate/userGrowth 再乘 100（5410.0%→54.1%）。fixture（7 端点、可疑串 0）+ 基线 ×4 重录（governance 明暗钉桩页 + **admin-dashboard 明暗——未钉桩走真后端，同一修复的合法爆炸半径**）并逐张看图；sha256 清单同步 |
| FE-05 | ✅ 已闭（D-9 除外） | `5561aac` | D-5：根因＝`.brand-side` 文字 `var(--surface)` 暗色翻深墨；改主题无关白 + opacity 0.94/0.92，精确 WCAG 读数 4.89/4.75/5.34 全部带余量过线。D-6：暗色限定浅色 pill，6.51/4.17 双过线。三基线重录看图。**D-8 实证已被 R-08（`ea48fe1`+`c0b94ce`）顺带修复**（根因前提拆除 + 暗色令牌已入基线，逐图复核 8 组标签可读；残余边界"中途切主题不重建图谱"已登记 LEDGER）。**D-9 修复尝试证伪**（animation:false 根级+系列级双写像素零变化，已撤销），根因收窄、保持开项（LEDGER 有完整记录） |
| P1 回归门 | ✅ 全绿 | — | vitest **182 文件 / 1912 例**、lint 0/0、typecheck 402/90 持平、build 2.93s + size:gate 全过、流程 **54 passed**、视觉全表两轮 **85/85**、helpers 32/32、docs/ 零污染。⚠️ 过程坑（已按既有配方处置，非产品回归）：视觉套件 globalSetup 按 5175 重生成 `.auth` 夹具 → 流程套件 onboarding 19 例 origin 失配整批红（spec 注释与 LEDGER P0c-ONB-05 早已登记此坑）；按配方 `PW_PORT=5173 PW_BASE_URL=http://localhost:5173 helpers --grep settlePage` 重生成后 54/54 |
| FE-10…FE-14 | ⏳ 未启动 | — | P2 按 §3 挂账（FE-11 的三项候选读数已由 FE-08 备妥，待拍板重启 R-15 后治） |
