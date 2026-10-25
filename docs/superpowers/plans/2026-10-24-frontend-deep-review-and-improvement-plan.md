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
| **P2 批（2026-10-24 续，项目主指示推进）** | | | |
| FE-11 | ✅ 第一批已闭 | `4820b48` | ① html2canvas 死依赖卸载（零引用实锤自 FE-08，含 lockfile 与 manualChunks 死分支）；② xlsx 两层拆分（`utils/xlsxExport.ts` 静态 import 保树摇 + 调用方动态 import 按需加载）——成绩路由首载净减 ≈323 KiB，xlsx 块维持 323.28 未涨；**单层动态 import 的反例当场实测**（树摇丢失→481.74 KiB 击穿 380 预算）已记入证据文档 §六；③ icons 按需降级登记：`:is` 字符串名动态解析 ≥7 组件（fileIcon/tab.icon/'CircleCheckFilled' 等），树摇会静默断供，保持全局注册；size:gate 预算未动（总量口径几乎持平，真实收益在加载时机） |
| FE-10 | ✅ B0 已闭 | `351f53c` | 棘轮首次下降 **402→389 条 / 90→76 文件**：清零 14 个单错误文件（PropType 定型/收窄/显式断言；vitest.config 的 plugin-vue 版本错位经 unknown 双段收窄对齐）。**契约修正一条**：api/task.ts extendDeadline 原误标 `{deadline}`，按后端 TaskController 的 TaskCreateDTO 纠正为 `{newDueDate, reason}`。TeachingSupervision 8→9（定型后父级新暴露 number[] 形态，prop 放宽吸收）逐键登记，byFile 与实况合计校验一致。**B1 候选已侦察**：TabGroup 的 `style` prop 撞 Vue 保留名（三处调用点）需改名 variant，涉共享组件 API 与测试，独立成批 |
| P2 回归门 | ✅ 全绿 | — | vitest 182/1912、lint 0/0、typecheck 389/76 持平、build 2.38s + size:gate 全过、流程 **54/54**、视觉全表 **85/85 连续两轮**（另有一轮 smoke 的 admin 夹具用例偶发红、单跑复绿，判时序非回归，记录在案）、docs/ 零污染 |
| FE-10 B1 | ✅ 已闭 | `e2dfff5` | 棘轮第二次下降 **389→386 条 / 76→73 文件**：TabGroup 的 `style` prop 撞 Vue 保留名——本体 TS 化（props 可推断，两处调用点的 `Partial<{}>` 摩擦消解）+ 改名 `variant`（两调用点随改、测试契约注释同步；variant 保持 String 不收紧，"未知值不校验"是文档化契约）。连带 AnalyticsCharts 的 heatmapColors PropType<string[]> 定型消解 `:style` 摩擦。45+9 例绿 |
| FE-09 余量 | ✅ 已闭 | `27ee89e` | **src 下 .js 清零**：onboarding 8 文件（anchors/geometry/state/steps×4）迁 TS——锚点表、几何 Rect/PlacementName、契约 v2 状态、OnboardingStep 接口（steps/types.ts 新增）定型；逻辑与文案逐字不动（7 文件字符串保真校验，student.ts 一处抄写失真提交前修正）；validateSteps 参数 unknown 收窄、宽松签名防测试级联。onboarding 单测 37 例绿 + 流程 54/54（含 onboarding 19 例浏览器面） |
| 本批回归门 | ✅ 全绿 | — | vitest 182/1912、lint 0/0、typecheck 386/73 持平、流程 **54/54**、视觉两轮 **85/85**、docs/ 零污染。⚠️ 复现过一次 webServer 生命周期竞态：夹具生成跑（helpers）的 5173 dev server 被紧随的流程套件 reuse 后于其 teardown 时消失 → 当轮 45 例谓词超时假红；独立重跑即 54/54（app 本身健康，auth 单跑 4/4 佐证）。经验：helpers 夹具生成与全量流程套件之间留出独立启动间隔 |
| FE-10 B2 | ✅ 已闭 | `0d08f96` | 棘轮第三次下降 **386→361 条 / 73→60 文件**：≤2 错误文件簇清零 13 文件（unwrap 信封 unknown 收窄定型、Number/String 归一、ref 形状声明）；useSpeech 补 Web Speech 识别器最小类型面（dom lib ES2020 无 SpeechRecognition 声明）；useKnowledgeGraph 的 courseId 放宽 string|number 并定型 openForm 形状。byFile 逐键核对零变形零新增。受影响面 99 文件 782 例绿。**B3 候选**：CdStudentsTab 的 el-table 槽位类型摩擦、NotificationManagement 下拉状态双联错误 |
| 本批回归门 | ✅ 全绿 | — | vitest 182/1912、lint 0/0、typecheck 361/60 持平、build 3.44s + size:gate 全过、流程 **54/54**（`.auth` 5175→5173 重绑坑按配方复现并处置，与 P0c-ONB-05 登记一致）、视觉两轮 **85/85**、docs/ 零污染 |
| D-9 | ✅ 已修复（`d8abf2d`，含 LEDGER 证伪记录更正） | | 页内实例探针定位：锚点页实际消费 useStudentKnowledgeGraph（此前 animation:false 证伪实验做错了文件，LEDGER 已更正）；正确文件上 animation:false + layoutAnimation:false 后，真实浏览器 8 个彩色圆点+边+标签全部渲染（scale=[6,6]=12px 与 importance 解析吻合）。冻结时钟基线字节不变（harness 固有限制，双态声明入 LEDGER）。useStudentKnowledgeGraph 单测 5 例绿、视觉两轮 85/85、vitest 1912 |
| FE-10 B3/B4 | ✅ 已闭（B4 见 git log） | `63f9bab` | 棘轮第四次下降 **361→358 条 / 60→58 文件**：CdStudentsTab 审批列 v-if 移入槽位作用域（原 row 解析到组件级 undefined，一并消除潜在渲染缺陷）、NotificationManagement 右键菜单状态定型（items 允许分隔符与 danger 项、notice 可选）。57 例绿。**B4 候选**：剩余 58 文件中的 3 错误文件簇 |
| FE-10 B5/B6/B7 | ✅ 三批已闭（`2d620b1`/`b3fdc60`/`0531521`，均已推送） | | 棘轮 **337→311→299→287 条 / 51→50→49→48 文件**：CduChapterList 15→0 与 CoChapterTree 14→0（两大户 chapters/chapterTree PropType 定型）、TsvRiskCard 12→0（riskGroups/riskDetails 定型）、KntPathPlanner 12→0（points 双形状定型）。每批 byFile 实况重写、lint 0/0、门禁持平 |
| R-09 开工状态 | ✅ 已闭（2026-10-24：桩信封对齐 non_null 真实形状——null 态无 data 键、emptyArray 态 data:[]；/src/ 防护确认在位；helpers 32/32 + anchors 42/42 + 视觉两轮 85/85） | — | **侦察更新：②"命中清单无 /src/"已在位**——`stubApiToEmpty`（anchors.spec.ts:543）的 route 已锚定 `/api/` 前缀正则（S1/R-C2-1 修复），源码模块进不了回调；**剩余核心＝①桩信封形状**：现桩返回 `{code:200,message:'ok',data:null|[]}`（含 data 键，由 dataShape 参数支撑第 14 项②的 null/[] 两态），R-09 判据要求的"信封无 data 键"须先**用真后端验证各端点空响应的真实形状**（起后端逐端点 curl），确认后改桩体并保持 dataShape 两态机制、跑空态守卫全套 + R-C2-4 判据（689-736 行）。anchors.spec.ts 单一写者（K-09），独立提交 |
| R-10 开工状态 | ✅ 已闭——实测定案（2026-10-24）：两格均不可转正。探针证据：learning-center 真实数据即 0/0（课程 1–3 实测，其余无数据）、admin 不渲染被桩数据面（useProfile.ts:87）。缺口表两行保留并各补探针理由；矩阵维持 7 格、计数回退 7+2；anchors 42/42 复测绿。转正需先改产品代码（造任务点数据/admin 加课程面），已登记为前提项 | — | 空转根因（anchors.spec.ts:613-617 缺口表）：student-learning-center 断言查 `.shell-tab`≥1 与非空标题（壳层/路由名供给，桩后仍非空）；admin-profile 查 filled 输入框与 hero 统计（/api/auth/ 保活端点回填）。**转正设计**：① student-learning-center 的 DATA_ARRIVAL_CHECKS（data-arrival.ts:151）改为断言 `/api/student/courses/1/learning-center` 供给的数据驱动内容（学习中心 LcStatGrid/LcCalendarPanel 数据面元素——先读页组件选稳定选择器）；② admin-profile（:181）改为断言 `/api/courses` 供给的课程数据面（ProfileCourses 列表行）。**证伪协议（每页一条反向守卫）**：真实后端绿 → 桩空红 → 缺口表 613-617 删该行、EMPTY_API_FALSIFIABLE 加回（R-C2-4 同批、9 格并集 689-736 行保持绿）→ anchors 全表 + R-C2-4 + 反向守卫三绿才算闭。data-arrival.ts/anchors.spec.ts 单一写者（K-09），独立提交 |
| FE-14 首批 | ✅ 已闭（2026-10-24） | `b416d07` | useAnalytics 689 → 编排层 107 + 图表配置域 useAnalyticsChartOptions 382 + 总览数据域 useAnalyticsOverview 214（全 <500 行，FE-06 平铺同级模式）；导出面逐名不变（21 名，唯一消费方 Analytics.vue 不改）；图表 ref 经参数注入避免反向依赖（FE-06 handlers 注入同款方向）；指标卡初始/空态复用同一工厂逐字段一致；D-10 修复轮注释随代码迁移。回归门：vitest **182/1912**、lint 0/0、typecheck 现量 **276/47 持平**（基线文件 287 未 --sync——B8 清零未同步，属 FE-10 B9 批动作，本批不代写）、build 3.12s + size:gate 六项全过（其余功能块 1258.75/1300）、流程 **54/54**、视觉两轮 **85/85**（纯逻辑重构基线零变化）、docs/ 零污染（motion 三图按 K-03 还原）。⚠️ 过程坑（两件既有前置复发，非产品回归）：① 后端以默认限流启动 → 流程套件 429 假红 33 例（DR-85 前置：按 `RATELIMIT_LOGIN_LIMIT=200` 重启后 54/54）；② `.auth` 夹具 origin 失配 → onboarding 19 例整批跳 /login（P0c-ONB-05 配方 `PW_PORT=5173 PW_BASE_URL=http://localhost:5173 helpers --grep settlePage` 重生成后复跑绿；跑后已恢复 5173 夹具供下一会话） |
| FE-10 B9 | ✅ 已闭（2026-10-24） | `4959908` | 棘轮 **287→246 条 / 48→44 文件**（byFile 头部三户 PropType 定型清零：NmNoticeList 的 groups（NoticeGroup/NoticeRow 具名 type，rows 字段以列表消费为准）、LpDailyTasksPanel 的 dailyTasks（DailyTask，useLearningPath.loadDailyTasks 映射形状）、ExamFormDialog 的 courseOptions/selectedQuestions；并**补记 B8 欠账**——SdCourseGrid 287→276 当时未 --sync，本批一次同步落盘）。**连带发现：ExamFormDialog（teacher/components，269 行）为零引用孤儿组件**（全仓大小写不敏感零命中、components.d.ts 无注册、无模板标签使用；唯一命中是 CourseExams 的 CeExamFormDialog 子串）——本批只定型不删除，删除与否属项目主决策（utils/permission.ts 死代码先例同口径），已写入基线 note。回归门：vitest 182/1912、lint 0/0、typecheck **246/44 持平**（byFile 合计自校一致）、build 3.59s + size:gate 全过、流程 **54/54**、视觉两轮 **85/85**（PropType 编译期擦除、运行时零变化）、docs/ 零污染（motion 三图按 K-03 还原）、跑后已恢复 5173 夹具 |
| FE-14 二批 | ✅ 已闭（2026-10-25） | `9e71a7c` | useKnowledgeGraph 609 → 编排层 155 + 知识点域 useKnowledgeGraphPoints 218（列表+CRUD+AI 抽取）+ 图谱可视化域 useKnowledgeGraphViz 252（力导向图谱+路径规划）+ 引擎域 useKnowledgeGraphEngine 126（同步与子图）；全 <500 行；导出面 **60 名逐名不变**（页面与 KgDialogs 零改动）；概览/图谱刷新经注入、engineSyncStale 所有权在引擎域经注入置位（FE-06 handlers 注入同款方向）；D-9/加载失败可见/B2 定型注释随代码迁移。回归门：vitest 182/1912（含 useKnowledgeGraph 15 例）、lint 0/0、typecheck 246/44 持平、build 2.37s + size:gate 全过、流程 54/54、视觉两轮 85/85、docs/ 零污染 |
| FE-10 B10 | ✅ 已闭（2026-10-25） | `892fa03` | 棘轮 **246→210 条 / 44→40 文件**：TeachingSupervision / EgSessionList / ProfileActivity / KmDialogs 四户定型清零（36 条——sessions/activities/courseList/availablePrereqs/prerequisitesList 经 PropType 定型；useTeachingSupervision 的 overview 换具名 OverviewStats、topCourses/aiAlerts 收窄 unknown[]）。**连带修复真实渲染缺陷**：`heatValue` 自 TS 迁移（217c48c）起定义了却从未进返回块，页面解构得 undefined 传给 TsvHeatmapCard（required + 模板直接调用）——该页不在 12 视觉锚点内，视觉门抓不到，类型工作钓出（B3 CdStudentsTab 同类）；补 `heatValue` 入返回块。另 TsvHeatmapCard.dayOrder PropType 由 string[] 纠正为 number[]（B5 遗留的父级 number[] 形态，B5 当轮以放宽吸收、本批在卡侧定型收口）。回归门：vitest 182/1912、lint 0/0、typecheck **210/40 持平**（byFile 合计自校一致）、build 3.75s + size:gate 全过、流程 **54/54**、视觉两轮 **85/85**（TeachingSupervision 不在锚点内，基线零变化）、docs/ 零污染、跑后已恢复 5173 夹具 |
| FE-14 三批 | ✅ 已闭（2026-10-25） | `f1f0f34` | useTeacherGrades 588 → 编排层 435 + 纯函数合并域 useTeacherGradesMerge 112（mergeGradeRecords 回归点 + gradeLevelOf 导出，编排层 re-export 保持测试导入路径不变）+ 分布图域 useTeacherGradesDistribution 65（useTeacherGradeDistribution 自含，编排层只喂 updateDistribution(scores)）；编排层 435 行 <500；导出面 **22 名逐名不变**（页面零改动）。回归门：vitest 182/1912（含 useTeacherGrades 两文件 27 例）、lint 0/0、typecheck 210/40 持平、build 2.75s + size:gate 全过、流程 54/54、视觉 **连续两轮 85/85**（首轮 50/85 中断——35 例未达、R-C3-2 环境抖动同形，紧接两轮全绿后收口；纯逻辑重构基线零变化）、docs/ 零污染 |
| FE-12 准入复核 | ✅ 已查清（2026-10-25）——**Phase 1 准入解除** | — | 本表此前多批写的"准入条件 (b) axe 基线仍待控制器落账"是**滞后读数，现予更正**：spec §11.3（Task 15 行 + 2026-10-21 控制器追加）实测记载三条解除条件**全部已落**——(b) axe 升级前基线由控制器取数入库 `5e5fb04`（`docs/visual-upgrade/evidence/a11y-baseline-2026-10-21.md`，四类近似读数、可重放脚本、对外降级标注在案，同批把 §6.2 对外宣称降为"四类读数不升"）；(c) D2-8 措辞 `c04448b`（路线图 :635 现文核实）；(a) 跨套件数据隔离走"①+③-lite"（Task 0 全钉 + `93b09cb` 经项目主"甲"授权重录，LEDGER `P0a-PINGAP-01`）。其后项目主插入的新人引导批已收口（DR-95，明记"不阻断 Phase 1"）。**Phase 1（token 纵深）按 §6.15 四条件带条件放行**；首个单元为 `P1-FULLPAGE-01`（fullPage 壳页几何，D-7——决定后续 60+ 单元的证明力上限：动 harness 与 helpers.spec 的 1440×900 IHDR 断言、24 张基线全量重录 + 逐张人工看图 + sha256 清单同步，独立成批，勿与 token 改动同批混做以免归因污染） |
| **FE-12 Phase 1 开工（2026-10-25 续）** | | | |
| P1-FULLPAGE-01 | ✅ 已闭（2026-10-25） | `300ee71` | 视觉 Phase 1 首单元，修缺陷 D-7（fullPage 壳页失效）：harness 侧 `expandViewportToContent`（settle 后扩视口到内容实高、封顶 8000、只改高度、不重跑 settle 保 SNAPSHOT_PAGE_TIME_MS 不变式）+ anchors/sensitivity 双接入 + helpers.spec 几何断言改判（宽 1440 钉死、高 ∈[900,8000]、免检额逐张 w·h·r 现算 129.6–413.9）。**24 张基线全量重录：14 张变长**（1116–2874px，D-7 点名的首屏之下区域首次进基线，逐张人工看图）、**10 张与旧版逐字节相同**（零扰动机器证）；sha256 清单同步。回归门：重录 42/42、anchors 两轮 42/42 零 diff、视觉全表两轮 85/85（sensitivity 探针尺寸卫兵首跑 2 failed→接入后 3/3，过程入账）、helpers 32/32、vitest 182/1912、lint 0/0、typecheck 167/34 持平、build+size:gate 全过、流程 54/54（夹具重绑坑按配方处置）。LEDGER 行 `P1-FULLPAGE-01`；免检额引用纪律变更：旧数 129.6/648/2592 只在 1440×900 几何成立，新引用必须带几何 |
| FE-14 四批 | ✅ 已闭（2026-10-25） | `6ed7fbc` | useAIAssistant 514 → 编排层 128 + 共享纯函数域 useAIAssistantShared 103（类型 ChatMessage/QaSource/ConversationRow + mapSources/readQa/renderMarkdown/formatTime 族，编排层 re-export 保消费方导入路径不变）+ 聊天域 useAIAssistantChat 285（发送 SSE 流式优先/非流式降级、快捷两操作、重生成/复制、打字机；清理经 releaseResources 交还编排层）+ 语音域 useAIAssistantSpeech 79（STT/TTS，stopSpeech 同款注入方向）；全 <500 行；导出面 **23 名逐名不变**（AIAssistant.vue 零改动，AiaChatPanel 经 props）。回归门：vitest 182/1912、lint 0/0、typecheck 167/34 持平、build 2.59s + size:gate 全过、流程 **54/54**（首轮 19 属已知 webServer 生命周期竞态族，独立重跑复绿）、视觉两轮 **85/85**、docs/ 零污染 |
| FE-10 B15 | ✅ 已闭（2026-10-25） | `6cd0222` | 棘轮 **68→37 条 / 18→11 文件**：六处同型 courseList PropType（KbDocSection/GradeFilterBar/QmAiDialog/QmEditDialog/QmFilterBar/RmFilterBar）+ GradeAnalyticsPanel 的 abnormalGrades + useUserManagement 的 overview 具名定型，共 31 条。回归门：vitest 182/1912、lint 0/0、typecheck **37/11 持平**、流程 **54/54**、视觉两轮 **85/85** |
| P1-TOKEN-04 | ✅ 已闭（2026-10-25，Phase 1 收口） | `7c59a5d` | 暗表面三阶 +3/通道、line 阶梯上移一档（chrome 描边优先）、--edge-hi 单源（l3-groove 引用＋.l1-card 暗色上高光边）。**11 张暗图重录**（浅色零变化机器证；Δmax 17-22，17＝line 算术）。**spec §4 Phase 1 四改动面全部落地**（FULLPAGE 地基/TOKEN-01 阴影族/TOKEN-02 表面阶+凹槽/TOKEN-04 暗色三件套＋TOKEN-03 新 token 定义）。回归门全绿。挂账：卡片硬编码亮边框（4 处）归 Phase 5；--edge-hi 推广到 .l2-panel 留 Phase 5 逐点判 |
| P1-TOKEN-03 | ✅ 已闭（2026-10-25） | `4c21a72` | Phase 1 新增 token 五条（--r-ctl 10px／--scrim 明暗各一档／--glow-brand／--ease-spring／--t-xslow），纯增量零消费者——**零基线重录**（anchors 两轮 42/42 且快照目录零文件变化机器证）；vitest 182/1912、build+size:gate 全过。LEDGER 立"生效须给消费点"引用纪律 |
| FE-10 B14 | ✅ 已闭（2026-10-25） | `ee08235` | 棘轮 **98→68 条 / 24→18 文件**：六个 5 错误户定型清零 30 条——useStudentGrades（examMap 值类型放宽 courseId 双态+title 可选、pickList 泛型对齐、空回退具名定型）、CourseAnalytics（extra 具名）、AiaSidebar（conversations）、AnalyticsMetricCards（coreMetrics）、AdChartPanels（topCourses）、ExamDetailDialog（examDetailQuestions）。回归门：vitest 182/1912、lint 0/0、typecheck **68/18 持平**、build + size:gate 全过、流程 **54/54**、视觉两轮 **85/85**、docs/ 零污染、跑后已恢复 5173 夹具 |
| FE-14 六批 | ✅ 已闭（2026-10-25，六户收官） | `2d5b0fc` | useCourseDetail 502 → 编排层 375 + 章节排序域 useCourseDetailChapters 119（CRUD/拖拽/脏标记/排序基线，基线经 syncBaseline 注入同步）+ 通知域 useCourseDetailNotices 68；导出面 **54 名逐名不变**（CourseDetail 与 CdDialogs 零改动）。**FE-14 六户全部完成**：useStudentExams/useAnalytics/useKnowledgeGraph/useTeacherGrades/useAIAssistant/useCourseDetail（797/689/591/588/514/502 → 全 <500 行，导出面逐名不变）。回归门：vitest 182/1912、lint 0/0、typecheck 98/24 持平、build+size:gate 全过、流程 54/54、视觉两轮 85/85 |
| P1-TOKEN-02 | ✅ 已闭（2026-10-25） | `d2664b3` | Phase 1 表面阶浅色 + L3 凹槽：--bg 压深一档（#eef0f4→#e9ebf0，与 #fff 拉开 ≈6.9 级）、--surface-2 收 spec 精确值 #f6f7f9、新 --l3-groove（浅=白.5 顶高光+内阴影／暗=1px 白.06 上高光边）、消费点 .l3-block 一处（14 文件经共享类收口）。**14 张基线重录**（浅 12 张 Δmax 5=bg 恒差；凹槽 4 张 Δ9/Δ13）。**§6.15 ④ 双轨证据**：计算值断言（两主题 boxShadow 均含 inset 顶边形态）+ 顶边 1px 行定向裁剪（条带 Δ9 > 恒差 5，增量归因凹槽）。**过程翻车当场修复**：:root 注释含字面量暗色选择器骗过 scrim.contrast.test 的首现切片（读到浅色 scrim、合成差 7 红）——改措辞立规。回归门：重录 42/42、anchors 两轮零 diff、视觉两轮 85/85、helpers 32/32、vitest 182/1912（scrim 复绿）、lint 0/0、typecheck 98/24 持平、build+size:gate 全过、流程 54/54（短跑属已登记 webServer 竞态族）。LEDGER 行 `P1-TOKEN-02`；挂账：.l3-block 之外的零散 surface-3 控件类（12 处）凹槽化留 Phase 5 逐点判 |
| P1-TOKEN-01 挂账收口 | ✅ 已闭（2026-10-25，项目主指示） | `fe64b4a` | teacher/admin 五个未钉桩壳页锚点（admin-dashboard/teacher-dashboard/admin-courses-table/admin-system-form/admin-profile）补铃铛角标遮罩 + **10 张基线重录**（逐张看图；admin-profile-light 新旧 diff 1,084px 全落于遮罩块、Δ255=白→品红，受控机器证）+ sha256 同步。至此 8 个未钉铃铛锚点全遮、钉桩 2 格保持不遮（fixture 值确定性可验）。EP --sh-pop 合并档维持 Phase 4 排定退役（提前动＝塞半成品）。回归门：helpers 32/32、anchors 两轮零 diff、视觉两轮 85/85、vitest 182/1912、lint 0/0、typecheck 98/24、流程 54/54。LEDGER 闭口注记见 `P1-TOKEN-01` 行后 |
| FE-10 B13 | ✅ 已闭（2026-10-25） | `3107ae1` | 棘轮 **136→98 条 / 29→24 文件**：六户 PropType 定型（WqToolbar 的 knowledgePointTags、SkgGraphPanel 的 prerequisite/successorNodes、Cqb 三件的 typeOpts/diffOpts、KgListGraph 的 points）+ LearningCenter（route.params.id 单参数收窄、学习中心信封具名定型），共 38 条。回归门：vitest 182/1912、lint 0/0、typecheck **98/24 持平**、build 3.26s + size:gate 全过、流程 **54/54**、视觉连续两轮 **85/85**、docs/ 零污染、跑后已恢复 5173 夹具 |
| FE-14 五批 | ✅ 已闭（2026-10-25） | `0b9bb56` | useQuestions 506 → 编排层 235 + 编辑对话框域 useQuestionEditDialog 187（表单/选项编排/按题型装配 payload/删除）+ AI 出题域 useQuestionAiGenerate 74 + 导入导出域 useQuestionTransfer 74 + useQuestionsShared 2（OPTION_LABELS 单源，编排层重导出）；全 <500 行；导出面 **39 名逐名不变**（页面零改动）。回归门：vitest 182/1912（含 useQuestions 40 例）、lint 0/0、typecheck 136/29 持平、build + size:gate 全过、流程 54/54、视觉**连续两轮 85/85**（首轮 16 红与活跃通知源同窗、DB 实证未读在 0↔N 振荡、判环境瞬态入账） |
| FE-10 B12 | ✅ 已闭（2026-10-25） | e57e972 | 棘轮 **167→136 条 / 34→29 文件**：头部五户定型清零 31 条——RagDocs（`ragListDocuments()` 补 `undefined` 实参消 TS2554——axios 丢弃 undefined query 参数、请求逐字不变；四处信封局部定型）、useProfile（loadDashboard 信封具名定型含 courses 元素形状）、useStudentExams（totalScore 两处 `raw == null ? … : Number(raw)` 归一收窄，运行时恒等）、TsvAiMonitorCard（aiAlerts）、TsvCourseStatsCard（topCourses/categoryKeys）。回归门：vitest 182/1912、lint 0/0、typecheck **136/29 持平**（byFile 合计自校一致）、build 2.41s + size:gate 全过、流程 **54/54**、视觉两轮 **85/85**（涉改组件均不在锚点页渲染路径：Tsv* 在 TeachingSupervision（无锚点）、RagDocs 无锚点、useProfile 的 loadDashboard 对 ADMIN 早退——admin-profile 锚点不受影响）、docs/ 零污染、跑后已恢复 5173 夹具 |
| P1-TOKEN-01 | ✅ 已闭（2026-10-25） | f25b8f8 | 视觉 Phase 1 首个 token 单元（阴影族）：浅色 `--sh-l1` 环境层 .06→.10（接触层不动，与 sensitivity shadow-plan 档的单变量读法一致）；暗色卡面三档阴影置 none（"只留给真浮层"，pop/drawer/modal 保留）+ EP 三枚 box-shadow 变量暗色重映射到 --sh-pop（弹层保影）。**18 张基线重录**（逐张看图，Δmax 浅 7–8 / 暗 16–50 纯 token 效果）。**连带钓出并修**：学生三锚点顶栏未读角标未钉桩、被活跃通知源（当日 49 条"新考试通知"、门禁运行中仍在到账）实时污染、~50px 常低于满页免检额被静默吸收——加 `maskSelectors: ['button[title="通知"]']`（首版遮 .n-badge 因元素随计数出没失稳、实跑 6 红换恒在容器）；学生 5 张旧基线 Δmax 170–207 为角标数据态项、与 token 效果分离记账。回归门：重录 42/42、anchors 两轮零 diff、视觉两轮 **85/85**、helpers 32/32、vitest 182/1912、lint 0/0、typecheck 136/29 持平、build+size:gate 全过、流程 54/54。LEDGER 行 `P1-TOKEN-01`；挂账：teacher/admin 角标未遮（红了同款一行收口）、EP 暗色弹层 --sh-pop 合并档待 Phase 4 退役 |
| FE-10 B11 | ✅ 已闭（2026-10-25） | `4bc7e5a` | 棘轮 **210→167 条 / 40→34 文件**：五个 8 错误户定型清零——EmCheatingDialog 的 cheatingLogs、ProfileCourses 的 courses、ChapterTree 的 tasks 换 TaskNode 接口、LpPathSelector 的 paths、CourseProctor 的 sessions（ProctorSession）/paperMap/risk/events 与三处 arr 显式收窄（risk/events 行经 pick() 形状不可知消费，定型为 unknown[]）+ CoChapterTree 的 taskPoints 换 CoChapterTaskPoint（B5 定型后父级新暴露的同形 3 条，顺手收口），共 43 条。回归门：vitest 182/1912、lint 0/0、typecheck **167/34 持平**（byFile 合计自校一致）、build 2.37s + size:gate 全过、流程 **54/54**、视觉两轮 **85/85**、docs/ 零污染、跑后已恢复 5173 夹具 |
| 未启动 | ⏳ 挂账 | — | FE-12 Phase 1 余单元（P1-ERRTHEME-01 已随 `ea48fe1` 闭合；下一单元按视觉 spec §4 Phase 1 顺序：theme.css token 纵深——每单元像素证据现可覆盖全页）、FE-14 余量（useQuestions 506 / useCourseDetail 502，按前四批模式逐个拆、每拆独立提交）、FE-10 B12…（剩余 34 文件 / 现量 167 条，PropType 路线复制；下一头部户：RagDocs 7 / useProfile 6 / useStudentExams 6 / TsvAiMonitorCard 6 / TsvCourseStatsCard 6）、R-10 转正前提项（造任务点数据/admin 加课程面，须先改产品代码）、ExamFormDialog 孤儿组件删除决策（项目主）、远端 10 条 dependabot 分支（项目主处置） |

---

## §8 完成情况总览与剩余清单（2026-10-24 收口快照，钉 `origin/leyon@e1745b9`）

> 本节是给"新会话/评审者"的**一页式状态**：做了什么、剩什么、从哪进入。逐批明细在 §7；判据本体在 §3 不变。

### 8.1 已完成（按批，全部本机验证 + 已推送）

| 批 | 提交 | 内容 |
| --- | --- | --- |
| P0 快赢批 | `a869ae0`/`cba40b4`/`e1db2c0`/`2097cbe` | FE-01 四处 formatDateTime 单源化、FE-02 模板切片转 computed、FE-03 成绩方案行稳定键；方案文档与索引落盘 |
| P0 计划外 | `7ac9ef3` | helpers sha256 基线清单同步 22→24（R-08 遗留欠账） |
| P1 批 | `2cfc724`/`c7da1df`+`01a4103`/`4060e38`/`7c35af5`/`9bb2dba`/`5561aac` | FE-08 bundle 构成分解、FE-07 requestError 提纯、FE-06 useStudentExams 三域拆解、FE-09 首两件 TS 化、**FE-04 D-10 修复**（后端 SQL 列名+显示口径+四基线重录）、**FE-05 D-5/D-6 修复**（D-8 核实已修、D-9 证伪后转修复批） |
| P2 批 | `2cfc724` 后续 `4820b48`/`351f53c` | FE-11（html2canvas 死依赖卸载 + xlsx 两层拆分按需加载；icons 降级登记）、FE-10 B0 |
| B 批序列 | `e2dfff5`/`0d08f96`/`63f9bab`/`69561c0`/`2d620b1`/`b3fdc60`/`0531521` | FE-10 B1…B7 七轮棘轮：**402 条/90 文件 → 287 条/48 文件**（TabGroup variant 改名、≤2 错误簇、3 错误簇、两大户、TsvRiskCard、KntPathPlanner——每批 byFile 逐键核对零变形） |
| 视觉缺陷 | `d8abf2d` 等 | **D-1…D-10 全部闭环**（D-9 经页内实例探针定位修复；LEDGER 含证伪更正与双态声明） |
| 路线图项 | `78b3d35` 等 | R-02 推送闭合（main:leyon 口径）、**R-09 闭合**（桩信封对齐 non_null 真实形状，空态守卫矩阵真实形状全绿） |

### 8.2 剩余待完成（按依赖序，§7 已登记执行序/侦察）

| 项 | 状态 | 进入点 |
| --- | --- | --- |
| **R-10 两页空转格转正** | 🟡 侦察完成，下会话首任务 | §7 R-10 行：转正设计（两页断言改数据驱动）+ 证伪协议 + 单写者约束 |
| **Phase 1–6 视觉升级** | ⏳ 前置＝R-10 | 视觉 spec §11 准入门（部分条件已由 D 批闭合）；D-R5 顺序约束 |
| **FE-14 巨型 composable 族** | ⏳ | FE-06 模式已验证；目标 useAnalytics 689/useKnowledgeGraph 591/useTeacherGrades 588/useAIAssistant 514/useQuestions 506/useCourseDetail 502 |
| **FE-10 B8…** | ⏳ | 剩余 48 文件（SdCourseGrid 11、NmNoticeList/LpDailyTasksPanel/ExamFormDialog 各 10……），PropType 路线已验证可复制 |
| **远端 10 条 dependabot 分支** | ⏳ 待项目主处置 | 合并与否属新决策（vue 3.5.44/playwright 1.64.1/minio 9.0.3 等） |

### 8.3 明确不做（继承 + 本方案新增，含重启信号）

继承路线图 AG-1…9 与视觉线既有裁定；本方案新增 N-1…N-6（i18n、虚拟滚动、TanStack Query、Pinia 扩张、husky、EP 样式按需）见 §5。

### 8.4 当前门禁读数（2026-10-24，全部本机）

vitest **182 文件 / 1912 例**全绿 ｜ lint 0/0 ｜ 类型棘轮 **287/48**（七轮：402→311→287）｜ build 3.44s + size:gate 六项全过 ｜ Playwright 流程 **54/54** ｜ 视觉全表两轮 **85/85**（24 张基线）｜ **R-09 后空态守卫矩阵在真实形状信封下全绿** ｜ src 下 .js 文件 **0**（onboarding 已 TS 化）｜ B.2 已同步。
