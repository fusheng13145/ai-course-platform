# 前端整体视觉升级（增量提质 · 全站收敛）· 设计定稿

- 日期：2026-10-18
- 批次登记：`docs/项目手册.md` 附录 D 新增 **DR-94**。登记前须重跑
  `grep -oh "DR-[0-9]\+" docs/项目手册.md | sort -n | tail -1`（本文撰写时实测最高 **DR-93**）。
- 分支：本地 `main`，上游 `origin/leyon`（§22.1 口径）；远程 `main` 逐批授权。
- 状态：**设计定稿**。项目主逐节批准基础设计后追加四条加码（C 档纳入 / 全程留痕 / 暗色同等 / 不设技术上限），
  并授权"目标可自主设定但须严于常规"。本文即按该授权定案，转入实施计划。

---

## 1. 需求来源与决策链

项目主原话口径：现有组件、卡片、面板"过于朴素、塑料感强、缺乏视觉层次与交互特效"，要求参考阿里/字节/腾讯/
小米/DeepSeek 的 UI，在**完整保留现有功能与交互**的前提下做整体视觉升级；本次为**增量改进，不重构整体风格**，
配色与风格体系留待后续单独处理。

四轮加码（构成对基础设计的正式修订，非可选项）：

1. **C 档纳入计划**——结构收敛（65 处 `el-dialog` → `ui/Modal`、8 处 `el-tabs` → `TabGroup`、
   4 处 `el-drawer` → `ui/Drawer`）不再是"以后再说"。
2. **全程留痕**——每个改动单元须有原始截图、修改操作、修改后新截图，并做新旧对比评估，
   判定该次修改**是否必要、是否到位、是否有效**。
3. **暗色模式同等精致**——视觉质量不得低于亮色。
4. **技术选型与成本不设限**——允许引入新依赖，涟漪等交互细节只要带来体验提升即可大胆做。

已定案的四个关键取舍：

| 议题 | 定案 | 依据 |
| --- | --- | --- |
| C 档能否直接开工 | **不能**。须先补强 `ui/Modal` 至与 `el-dialog` 能力齐平、并把焦点覆盖率护栏改为对弹层实现无关（两者同为 Phase 4 的准入门槛） | `ui/Modal` 无 Teleport / 无焦点管理 / 无滚动锁（**无 v-model 一项已由 Task 7 闭掉**，见 §2.5 同表）；而 D2-7 已把 `ref="dialogRef"` + `useDialogFocus()` 铺到**全部 `el-dialog`（63 处）并由机械护栏在构建期强制**；**抽屉不在其覆盖范围内**——Phase 0b 实测 4 个 `<el-drawer>` 使用者是全仓唯一未接焦点管理的弹层（旧护栏的扫描键只有 `<el-dialog`，所以从未照出来）。直接替换等于拆掉整条键盘可达性资产，且护栏同时变红 |
| 证据留存形态 | **两层**：机器层进 git（24 张锚点基线，滚动更新）；记录层 `LEDGER.md` 一份表 + 精选证据图 | 全量 per-unit 截图估算 240–320 张 PNG（约 20–40 MB）进 git 不可持续，且没人回看；判据以文本表格保全，图只留失败项与代表项 |
| 暗色实现路径 | **独立一套表现层设计**（描边优先 + 抬亮表面），不是浅色值换色镜像 | 黑投影在深色底上不可见，现 `--sh-*` 暗色值实际只增噪声 |
| 新依赖范围 | `lucide-vue-next`（推荐）+ `motion-v`（限 4 处）+ 自研 `v-ripple`（零依赖） | 见 §7 逐条代价账 |

---

## 2. 现状与取证

以下数字为 **2026-10-18 本轮实测**，非引用旧文档。项目手册的统计读数有漂移前科，实施时每阶段开头须重测。

### 2.1 规模与安全网

| 位置 | 实测 |
| --- | --- |
| `frontend/src/**/*.vue` | **285** 个，其中 **244** 个含 `<style scoped>` |
| `<el-dialog>` 开标签 | **65** 处 / **55** 文件。属性完整解析到 **63** 处（2 处属性值内含 `>` 截断正则，Phase 0 精算） |
| 该 63 处的属性分布 | `width` 63、`title` 63、**`ref` 63**、`:model-value` 49、`v-model` 14、`@closed` 6、`close-on-click-modal` 1、`draggable`/`fullscreen`/`custom-class`/`:before-close` 各 **0** |
| 带 `#footer` / `#header` 插槽的 dialog 文件 | **38** / 55 |
| `components/ui/Modal.vue` 引用 | **12** 文件（ExcelImportDialog、NotificationManagement、UmCreate/UmDelete/UmDetail/UmResetDialog、Notifications、SdCourseGrid、student/Courses、CourseChapters、CourseSubmissions、teacher/Dashboard） |
| `<el-tabs>` / `<el-drawer>` | **8** / **4** 文件 |
| vitest | **153** 个 test 文件（views 57 / composables 41 / api 23 / components 10）。`components/ui/__tests__/` **仅 1 个**（`EmptyState.test.ts`）——Modal / Drawer / StatCard / TabGroup / CollapsePanel / DropdownMenu / TreeView **无单测** |
| 引用 dialog/tabs/drawer 的单测 | **14** 个文件引用 `el-dialog`/`ElDialog`/`ui/Modal`；**5** 个引用 `el-tabs`/`el-drawer` |
| Playwright e2e | **9** 个 spec（admin-chunk-upload / auth / exam-resume / navigation / permission-denied / student-ai / teacher-exam / teacher-grade / teacher-question-bank），均为流程断言型 |
| 视觉回归基线 | **0**。全库 `toHaveScreenshot` / `toMatchSnapshot` 命中数为 0，无 `__snapshots__` 目录 |
| **弹层焦点覆盖率护栏（关键既有机制）** | `composables/__tests__/useDialogFocus.coverage.test.ts` — 去 HTML 注释后扫描全仓 `.vue`，凡含 `<el-dialog` 者必须含 `useDialogFocus`，否则 `offenders` 非空即红；**并硬要求扫描数 ≥ 50**（注释自陈"防止扫描逻辑退化后空转"） |

### 2.2 三层样式的实际权责

| 文件 | `!important` | 角色 | 问题 |
| --- | --- | --- | --- |
| `styles/theme.css` | 0 | token 唯一来源 + EP 变量映射 + 暗色 token | token 阶梯本身有缺陷（见 §3.1） |
| `styles/index.scss` | **98** | 旧"莫兰迪"EP 覆写层 + 响应式 + 暗色覆写（`:513-620`） | 与 design-system.css 对同一批选择器写了两套值，靠加载顺序硬碰 |
| `styles/design-system.css` | 15 | 新体系表现层（L1/L2/L3、按钮、表单、表格、弹层、动效） | 词汇表齐但落地极不均 |

加载序：`main.ts:51-52` 先 `index.scss` 后 `design-system.css` ⇒ 同权重时新体系赢，但 98 条 `!important` 不参与这个比较。

### 2.3 体系类名渗透率（按引用文件数）

```
l1-card 55   l1-head 43   l1-body 54   animate-in 42   el-table 54   el-dialog 55   el-button 156
l3-block  7   stat-card  5   course-card  4   tabs-pill  2   tabs-seg  2
l2-panel  1   row-card   1   desc-grid    1   split-view 2   mini-progress 1   radio-cards 1
```

`EmptyState` 96 / `StatCard` 44 / `CollapsePanel` 9 / `Drawer` 2 / `TabGroup` 2 / `DropdownMenu` 1 / `TreeView` 1。
⇒ L1 层已铺开，**L2/L3 纵深与富组件基本闲置**；`DropdownMenu`、`TreeView` 各只有 1 个调用点，属孤岛。

### 2.4 硬编码污染（`.vue` 内实测行数）

`border-radius:` **313** / 6 位十六进制色 **175** / `rgba(` **110** / `box-shadow:` **73**；**22** 个文件含 `linear-gradient`/`radial-gradient`。

Top 5：`layouts/components/AppSidebar.vue` 19、`views/auth/Login.vue` 18、`views/auth/Register.vue` 17、
`views/teacher/cockpit/CourseProctor.vue` 16、`views/admin/system/components/OlOperationPanel.vue` 15。

### 2.5 单点事实（逐条可定位）

| 事实 | 位置 |
| --- | --- |
| `--surface-3: #eff1f5` 与页面底 `--bg: #eef0f4` 几乎同色 | `styles/theme.css:10,14` |
| `--sh-l1` 环境阴影 alpha 仅 `0.06` | `styles/theme.css:52` |
| 卡片 16px / 控件 6px 的圆角错位 | `theme.css:46`（`--r-l1:16px`）vs `theme.css:149`（`--ac-radius-sm:6px`），控件套用在 `index.scss:202,319` |
| `.el-card` 被主动削平：`box-shadow: none !important` | `styles/index.scss:179` |
| 暗色 EP 覆写是独立一块，与浅色覆写不对齐 | `styles/index.scss:513-620`。**措辞已按引用核验更正**：原文称它是"独立一套 `!important` 集群"名实不符——该块内只有 **5** 条 `!important`（全文 98 条，其余 93 条在块外），块内主体是 `--el-*` 变量赋值。所以"暗色要单独收敛"的依据是**它与浅色覆写写了两套值**，不是它自带 `!important` 权重 |
| 65 个 dialog 的焦点管理原语；注释自陈"全仓 64 个对话框此前 `.focus()` 零调用" | `composables/useDialogFocus.ts:1-29,41-57` |
| D2-7 收官记录：分六批把焦点管理铺到"**含 `<el-dialog` 的 54 个组件 100% 接入**"，并新增上述机械护栏 | `docs/项目现状分析与未来发展规划-2026-10-13.md:634`（D2-7 行，第六批段） |
| 护栏去注释扫描得 **54** 文件；本文 grep 含注释得 **55** 文件 / 65 开标签——差 1 个文件的 `<el-dialog` 写在 HTML 注释里 | 两者口径不同，Phase 0 精算时对齐为单一口径 |
| a11y 原语已复活并在用（`inputA11y`/`buttonA11y`/`landmarkA11y`/`liveRegionA11y`），auth 三页输入控件与提交按钮已接线 | `composables/useA11y.ts`；D2-7 第一批段（同上 :634） |
| `useDialogFocus` 经 `dialogRef.value.$el` 取根元素 | `useDialogFocus.ts:50` |
| `ui/Modal` 无 Teleport / 无焦点管理 / 无滚动锁。**"无 v-model"这一条已由 Task 7（`37b9414`）闭掉**，本行按引用核验更新；行号也从 `1-17,56-65` 改为 Task 7 后的 **`1-42, 81-90`**（旧号只成立于 `2b01f9a`） | `components/ui/Modal.vue@2b01f9a:1-17,56-65` → 现读 `Modal.vue@37b9414` |
| `StatCard` 与 `PageHeader` **各抄了一份约 70 条的手写 SVG path 表**（两份重复、可能已不同步） | `components/ui/StatCard.vue:12-75`、`components/PageHeader.vue:20-82` |
| ECharts 不读 CSS 变量，故图表色走独立 JS 色板 | `utils/chartTheme.ts:1-3,29-48`（102 行，明暗两套 PALETTE） |
| 三端 Dashboard 结构不同脏度 | `views/admin/Dashboard.vue`（105 行薄壳，委派 `Ad*` 组件）；`views/student/Dashboard.vue:99-115` 自带重复 `.fade-in`+`@keyframes`；`views/teacher/Dashboard.vue:237-242` 六组硬编码渐变封面色 + `:342,362,380` 硬编码 rgba/圆角 |
| 登录三页各自复制同一套样式且互不一致 | `views/auth/Login.vue:218-225,263-272,362-381`（含伪玻璃卡与多处 `!important`）、`Register.vue`、`ForgotPassword.vue` |
| e2e 端口陷阱：5173 被别的项目占用时 `reuseExistingServer` 会**静默复用并测出假缺陷** | `frontend/playwright.config.ts:3-6,34-40` |
| e2e 截图策略为 `only-on-failure`，成功即无图 | `playwright.config.ts:25` |
| **自有 z 阶整体低于 Element Plus 一层**：本文体系是 `--z-modal: 100` / `--z-menu: 130` / `--z-toast: 160`，而 EP 弹层用全局递增计数器、**从 2000 起**。⇒ EP 的任何弹层天然盖在自建浮层之上。**本行原写的 EP 源码路径是我（控制器）凭空造的，已作废**：`popup-manager/pom-hd:2108`、`:2125`、`useDialog:132` 三个引用在已安装的包里**一个都不存在**（2.14.7 里连 `es/components/popup/` 这个目录都没有）。现已改为在装包内可核对的三条：`element-plus/es/hooks/use-z-index/index.mjs:8` `const defaultInitialZIndex = 2e3`、`:19-23` `nextZIndex()`、`element-plus/es/components/dialog/src/use-dialog.mjs:139` `zIndex.value = nextZIndex()`。**结论不变，只有证据换了**——这正说明"结论对"不能给"引用编"免罚 | `theme.css:60-66` vs 已安装 EP（见下条版本漂移） |
| ⚠️ **依赖版本漂移：声明 ≠ 已安装**（Task 5 评审实测，本节所有 EP 行为断言的前提）：`frontend/package.json:32` 声明 `element-plus: ^2.4.4`，而 `package-lock.json:3448` 与实际安装均为 **2.14.7**；`package.json:43` 声明 `@playwright/test: ^1.62.0` 而安装 **1.63.0**（旧版浏览器二进制仍在 `AppData/Local/ms-playwright`，锁与机器不一致）。⇒ 本文凡是"EP 在 `x.y.z` 下如何做"的表述一律**不得引用版本号**，只能引用已安装路径。`^` 未锁 ⇒ 任意一次 `npm install` 都可能换掉 Phase 3（覆写层收敛）与 Phase 4（`el-drawer` 迁移）所依赖的 EP 内部行为。**处置（2026-10-19，使用方拍板后已执行）**：`package.json` 与 `package-lock.json` 的声明由 `^2.4.4` **收紧为精确 `2.14.7`**（已安装即此版本）；`npm ls element-plus` 退出码 0、解析 `element-plus@2.14.7`，`node_modules` 未被改动。代价已认下：**"按 2.4.4 写的假设"从此显式作废**，EP 的补丁更新要人工点头才进得来。**同型的 `@playwright/test` 暂未动**（声明 `^1.62.0`、实装 1.63.0）：它只影响测试工具自身、不影响产品像素，且本机 `AppData/Local/ms-playwright` 存的正是 1.63.0 对应的浏览器二进制，收紧它须连带确认 CI 的 `playwright install` 版本——等使用方一句话再动 | `package.json` vs `package-lock.json` vs `node_modules`；`npm ls` 复验 |
| 抽屉/弹窗**叠在 EP 对话框之上会被压住**——这是 Phase 4/5 的结构前提，不是假想：现有 `EgEventsDrawer → InResultDialog` 一叠就中 | 由 Phase 0b 评审实测确认 |

### 2.6 已核为"当前未爆"的一条风险

`.animate-in` 用 `animation: rise-in ... both`（`design-system.css:1358-1359`），`both` 填充会保留
`transform: translateY(0)`，**成为 `position: fixed` 后代的包含块**——即 `ui/Modal` 的遮罩若落在带 `transform`
的祖先内，会被困在该祖先里而不是铺满视口。

实测三例均为兄弟级、**当前未被困**：`views/student/Courses.vue:17`（`.split-view.animate-in`）vs `:221`（`<Modal>`）；
`views/teacher/Dashboard.vue:5,24` vs `:129`；`views/common/Notifications.vue:21` vs `:130`。
⇒ 本文将其记为 **Phase 4 扩量时必须用测试钉住的前瞻风险**，不记作现存缺陷。

---

## 3. 成因判定（三条根因，全部对应可测改动）

### 3.1 层次：不是没有分层，是分层本身失效

`L0 #eef0f4 → L1 #ffffff → L2 #f7f8fa → L3 #eff1f5` 的明度跨度只有约 5 级，且 **L3 与页面底同色**。
嵌进 L2 面板的 L3 块因此不是"凹进去"而是"看穿了"。叠加 `--sh-l1` 环境阴影 alpha 0.06，卡片间几乎无高度差。

**结论**：层次问题在 token 数值层，不在组件层。改 token 一处，全站 285 页同时受益。

### 3.2 塑料感：圆角阶断裂 + 被主动削平的阴影

6px 控件装在 16px 容器里、中间没有过渡尺寸；`.el-card` 静态无阴影仅 hover 才有。二者共同构成"塑料"观感。

### 3.3 无生气：动效词汇存在但只有 42 个文件在用，且缺微交互

`animate-in` 有 42 个文件用，但按钮无按压/光泽反馈、表格行无指示、弹层无入场编排、列表无交错。
`index.scss` 98 条 `!important` 使任何新增质感都可能在加载顺序上被旧覆写吃掉——**这是必须先收敛覆写层、
后谈微交互的根本原因**，不是洁癖。

---

## 4. 阶段划分

依赖关系：`Phase 0a → 一切`（无基线则项目主要求 2 的"新旧对比"无从执行，LEDGER 的"改前截图"字段也不存在）；
`Phase 0b → Phase 4`（硬门槛，含焦点护栏改写）；`Phase 3 → Phase 6`（覆写不收敛，动效会被 `!important` 抵消）。
**Phase 1、2 不得早于 Phase 0a 完成**——它们的改动本身就是需要留痕的单元，没有基线就成了无痕改样式。

**计划分份**（本 spec 体量为 60–80 个单元、2–3 周连续批次，不是一份实施计划能吃下的）：
转入 `superpowers:writing-plans` 时按四份出——① Phase 0（地基，必须先完成并验收）；② Phase 1–3（表现层，一个可回滚整体）；
③ Phase 4（结构收敛，按 6 个目录批次各自成子计划）；④ Phase 5–6（页面级 + 动效）。
**先只做第 ①份的计划**，②③④ 待 ① 验收后再出，避免计划与现实漂移。

### Phase 0a · 视觉台账 harness（C 档与留痕要求的地基）

- 新增 `frontend/e2e/visual/anchors.spec.ts`：**12 个锚点页 × 明暗两主题 = 24 张基线**。
  锚点：`auth/Login`、`admin/Dashboard`、`student/Dashboard`、`teacher/Dashboard`、`admin/teaching/CourseManagement`
  （代表 54 处表格页）、`admin/system/SystemSettings`（代表表单页）、`student/Courses`（代表 Modal 复用页）、
  `student/course/LearningCenter`、`knowledge` 图视图、`admin/governance/Analytics`（代表图表页）、
  `common/Profile`、`error/404`。
- 钉死变量：viewport 1440×900、DPR=1、`--force-prefers-reduced-motion`、等待网络空闲 + 字体就绪 +
  `document.fonts.ready`、冻结 `CountUp`/骨架屏等时序组件、`maxDiffPixelRatio` 逐页设定并写进 spec 注释。
- Windows 抖动是真实代价：字体抗锯齿与子像素渲染会造成噪声，首版阈值须实测标定，**不照抄经验值**。
- 基线目录 `frontend/e2e/visual/__snapshots__/` 进 git，`.gitattributes` 标 binary。滚动更新
  （`--update-snapshots`）只允许在台账某单元判定为"改后为正确状态"时执行，并在 LEDGER 行内记录新旧基线的 commit。
- 顺带修 `playwright.config.ts:25`：视觉 spec 单独用 `screenshot: 'only-on-failure'` 之外的显式截图，
  避免与既有 9 个流程 spec 的配置耦合。

### Phase 0b · `ui/Modal` 能力齐平（Phase 4 的准入门槛）

必须做到，缺一则 Phase 4 不得开工：

1. `v-model` 支持，**同时保留现有 `@close`**——12 个存量调用点一个不破。
2. `<Teleport to="body">`，并用测试钉死 §2.6 的包含块风险场景。
3. 接入 `useDialogFocus` + 滚动锁 + `role="dialog"` + `aria-modal` + 关闭后焦点返还。
4. `width` / `title` / `#footer` / `#header` / `@closed` / `close-on-click-modal` 语义对齐——覆盖 §2.1 实测到的全部属性。
5. 为 `Modal` / `Drawer` / `StatCard` / `TabGroup` / `CollapsePanel` / `DropdownMenu` / `TreeView` 补单测
   （`components/ui/__tests__/` 现仅 1 个）。
6. **改写焦点覆盖率护栏为"弹层实现无关"**（本项是 Phase 4 的真正闸门，漏掉则迁移一开始就构建期全红）：
   现护栏扫描键是字面量 `<el-dialog`，且带 `≥ 50` 的防空转下限。把 65 处迁移走会同时触发两条失败——
   `dialogFiles.length` 跌破 50（判为"扫描退化"）与 `offenders` 为空但扫不到东西。
   改写方向：扫描键扩为 `<el-dialog` ∪ `<Modal` ∪ `<ui-Modal`（含 `ui/Drawer` 与 `el-drawer` 同口径评估），
   断言改为"**任一含弹层标记的组件必须接焦点管理，且弹层总数不得低于迁移前基线数**"——
   即把"防空转下限"从写死 50 改为读一个受控的期望总数，让新写弹层漏接线仍然构建期即红。
   护栏改写的 PR 必须先于第一批迁移合入并单独跑绿。

待实测、不凭推断的一项：`useDialogFocus` 读 `dialogRef.value.$el`，而 `<script setup>` 组件在**未 `defineExpose`**
时经模板 ref 能拿到的 public instance 面有边界。该组合是否真能取到 `$el`，须以单测证明；若取不到，
改为 `Modal` 内部自持根元素引用并 `defineExpose` 一个 DOM getter。

### Phase 1 · token 纵深

**浅色**（`theme.css` 约 8 行）：`--bg` 压深一档使与 `--surface` 拉开约 6 级明度；`--surface-2` 收至 `#f6f7f9`；
L3 由"更深"改为**凹槽**——`inset` 内阴影 + 顶边内高光，脱离"与页面底同色"；阴影改双成对结构
（接触阴影 1px 高 alpha + 环境阴影 alpha 0.06 → 0.10）；新增 `--r-ctl`（10px）、`--scrim`、`--glow-brand`、动效时长/曲线各补一档。

**暗色**（独立设计，非镜像）：高度靠**描边优先 + 抬亮表面**——浮得越高底色越亮，配 1px 上高光边；
阴影只留给真浮层（modal / drawer / pop），因为黑投影在深色底上不可见。现 `--sh-*` 暗色值（`theme.css:225-229`）
按此重排。

### Phase 2 · 圆角阶对齐

建立 `16 / 12 / 10 / 6 / 999` 五阶，原则：**外圆 > 内圆，相邻层级差 4px**。把 `--ac-radius-sm: 6px` 从控件路径上摘掉。

### Phase 3 · EP 覆写层收敛

`index.scss` 98 条 `!important` → 目标 ≤ 25：能走 `--el-*` 变量的全部走变量（EP 本身变量驱动）；
删除与 `design-system.css` 重复定义的选择器；删除自削规则（`:179`）；
EP 2.4.4 确实写死样式、必须保留 `!important` 的，逐条加一行注释说明"为什么必须"。
**浅暗两套覆写须同步收敛**——`index.scss:513-620` 的暗色集群不得与浅色覆写脱节。

### Phase 4 · 结构收敛（C 档）

**准入门（Phase 0b 实测补入，2026-10-18；任一项未闭则本阶段不得开工）**：

| # | 缺口 | 实测来源 |
| --- | --- | --- |
| 1 | Modal / Drawer **无任意宽度通道**。4 处 `el-drawer` 需要 460 / 560px，`TaskDrawer.vue:217` 还是 JS 算出的 `100%\|620px`；两原语目前只有 `wide/xwide/narrow` 三档 | `task-11-report` concerns |
| 2 | 调用点写的是 `title="字符串"`，而原语要 `#title` **插槽**。**这条的范围我原先写错了，现按实测更正**：对 `<Modal>` **多行标签一起扫**的结果是 **0 处**传 `title="…"`（`grep -rnz -o "<Modal[^>]*>"` 后筛），而 `template #title` 有 **15** 处 ⇒ **13 个存量 Modal 调用点没有这个缺口**。真正有 9 处 `title="字符串"` 的是 **`el-dialog` / `el-drawer`**（Phase 4 的迁移来源），所以本项不是"原语少一条通道导致现有 Modal 坏"，而是"**迁移那 9 处时必须有字符串→插槽的转换规则，或给原语补一个字符串 prop**"。缺口仍然要闭，但闭的是迁移期的规则，不是存量 bug | 同上；实测命令与计数 2026-10-19 由控制者跑 |
| 3 | **层叠不足**：`--z-drawer: 80` < `--z-modal: 100` < EP 弹层实测约 2000。EP 默认 z 更高，会盖在自定义抽屉之上 | 同上 + `theme.css:60-66` |
| 4 | `@closed` 现在在 **prop 边沿**触发，不是离开过渡结束之后（现在根本没有离开过渡） | 同上 |
| 5 | ⚠️ **`aria-modal="true"` 但没有焦点陷阱** —— 语义与实现不一致，比不写更糟：读屏会以为已被模态封闭，而 Tab 仍能走出去 | 同上 |
| 6 | **Esc 会一次关掉所有已打开的抽屉**（每个实例各自往 `document` 绑 keydown），Phase 4 的 4 处抽屉叠加场景下必须改成"只关最上层"。现状已被测试钉为已知行为，改动须显式拆锁 | Phase 0b Task 11 复核 I-3 |

**这六项 `Modal` 与 `Drawer` 两者都还缺**（Task 7-11 的落地状态决定各差几项；
上表已由 d2e1cf8 加到第 6 项，"这五项"是加进来之前的计数，引用核验于 2026-10-19 纠正）。
第 5 项必须在 Phase 0b 内闭掉，不得带着它进入结构收敛；若补焦点陷阱代价过大，
则**撤下 `aria-modal`**，而不是留着一个说不通的属性。
> **第 5 项的范围比标题宽（Task 11 复核实测，2026-10-19）**：`aria-modal` 无陷阱这个缺陷类**不止两个载体**——
> `frontend/src/components/OnboardingGuide.vue:19` 同样写着 `aria-modal="true"` 且没有任何陷阱。
> 该原语不在 Phase 0b 的 Modal/Drawer 收口范围内，但**Phase 4 准入门按"全部自建弹层"判，不按"这两个组件"判**；
> 否则会出现"两个原语干净、第三个仍在说谎"的假闭口。陷阱落地时须把它一并接上或撤下该属性。

65 处 `el-dialog` → `ui/Modal`；8 处 `el-tabs` → `TabGroup`；4 处 `el-drawer` → `ui/Drawer`。

**这一阶段撞测试的是 template，不是 CSS。** 14 个单测引用 `el-dialog`/`ElDialog`/`ui/Modal`、5 个引用
`el-tabs`/`el-drawer`，57 个 views 测试存在。**外加 §4 Phase 0b 第 6 项的焦点覆盖率护栏——它必须在第一批迁移前就已改写到位，
否则第一批直接红。**

执行纪律：按目录分 6 批，每批 ≤ 12 个对话框，每批跑完整验证链 + 更新 LEDGER 后进下一批，**不攒到最后**：
`components/course` → `components/knowledge` → `views/admin` → `views/teacher` → `views/student` → `views/auth|common`。
每处替换前后，同名断言必须同样通过；断因需修改测试才能通过的，一律视为替换有问题，回退。
**唯一允许改测试的情形**是断言对象从 `el-dialog` 换成了 `Modal` 这一等价替换，且改动必须是机械的类名/选择器替换而非放宽断言。

### Phase 5 · 页面级升级与硬编码清理

- **登录三页**：抽成全局 `styles/auth.css`（`.auth-page` 前缀，**模板零改动**）；伪玻璃卡
  （`Login.vue:265-272`）改为有真实高度差的实体层——`backdrop-filter` 是塑料感另一来源。
  前提：三页 markup 一致，Phase 5 开头先核，不一致则该页退化为"只升级、不合并"。
- **三端 Dashboard**：`teacher/Dashboard.vue:237-242` 六组硬编码渐变封面抽 `utils/coverPalette.ts`，
  与 `chartTheme.ts` 色板同源单点；`student/Dashboard.vue:99-115` 重复 `.fade-in` 删除改用 `animate-in`；
  `admin/Dashboard` 的升级下沉到 `Ad*` 子组件。
- **壳层**：`AppSidebar`（19 处硬编码，全站第一）与 `AppTopbar` 优先；激活项补 hover 微位移、分组标题字距、
  mini 折叠过渡、滚动条质感。
- **全站硬编码清理**：按 §2.4 计数从 5 个重灾文件起，逐文件清零。
- **孤岛原语处置**：`DropdownMenu`、`TreeView` 各 1 调用点，本批只做质感升级，不做推广——推广属结构调整，超出本批。

### Phase 6 · 微交互与依赖

- **涟漪**：自研 `v-ripple` 指令（约 60 行，零依赖）。理由：涟漪须精确跟随 `--accent` / `--accent-soft`
  且暗色须换 alpha，引库反而更难对齐 token。用于主按钮、侧栏项、可点卡片、tab。
- **图标统一**：`components/ui/AppIcon.vue` 包 `lucide-vue-next`，**保持 `icon="users"` 这类字符串 prop 不变**，
  调用点零改动，一次性替换掉 `StatCard.vue:12-75` 与 `PageHeader.vue:20-82` 两份重复手写 path 表。
  本项为全部改动中性价比最高者。
- **动效编排**：`motion-v` **仅用于 4 处**——列表交错、tab 指示条 layout 动画、dialog/drawer 弹性入场、数字滚动。
  不铺全站：285 个组件的收益递减与不透明层合成的性能代价真实存在。
- **图表质感**：`chartTheme.ts` 扩面积渐变、tooltip 质感、坐标轴柔化（ECharts 不读 CSS 变量，必须在此单点处理）。
- **微交互清单**：卡片 hover 抬升 + 顶部内高光、主按钮光泽扫过 + `active scale(.98)`、
  表格行左侧 2px 品牌指示条（**不改行高，避免抖动**）、dialog 入场 `scale(.96)+translateY(8px)`、
  drawer 滑入、popper 缩放原点跟随 `is-placement`、`animate-in` 补 d7–d12、骨架屏与真实内容交叉淡出。
- 全部动效须有 `prefers-reduced-motion` 降级路径（延续现有约定：`design-system.css:1134-1138,1379-1384`）。
- `motion-v` 若包体实测超 §7 阈值，退化为纯 CSS 方案，不影响其余阶段。

---

## 5. 证据与台账协议

唯一台账文档：`docs/visual-upgrade/LEDGER.md`（一份表，追加式，不拆多份——项目主对文档数量的标准是越少越好）。

每个改动单元一行，字段：

| 字段 | 内容 |
| --- | --- |
| 单元 ID | `P<Phase>-<域>-<序号>`，如 `P3-DIALOG-01` |
| 改前 | 截图相对路径 + **当时的具体数值**（如 `.el-input` radius 6px） |
| 操作 | 精确到 `file:line` 的 diff 摘要 + **改的理由**（指向 §3 哪条根因） |
| 改后 | 截图相对路径 + 新数值 |
| 对比判定 | **必要性**（不改会怎样）／**到位**（是否命中成因而非表层涂抹）／**有效性**（观感或可读性是否可测地变好） |
| 结论 | 三态：`保留` / `回退` / `需调` |
| 验证 | 该行对应的 lint / typecheck / 单测 / build / e2e / 视觉 spec 实际执行结果 |

**判定分两类，不得混淆**：

- **机器可测**（对比度比值、阴影 alpha、radius 阶合规、token 合规率、`useDialogFocus` 覆盖率、
  axe 违规数、包体差值）：脚本产出数值，写进表。
- **主观**（层次、精致、现代感）：明确标注"主观·我自评"，不伪装成已被证明。

**图片留存策略**（解决"全量 240–320 张 PNG 不可持续"）：
锚点基线图进 git 且滚动更新（历史由 git 版本追溯，不靠堆文件）；
单元级 before/after 图仅在两种情况下留存于 `docs/visual-upgrade/evidence/<Phase>/<单元ID>/`——
(a) 结论为 `回退` 或 `需调` 的单元，(b) 每 Phase 一组代表性对比。其余单元只把 diff 百分比与结论写进表。

**自评可信度的反向约束**：若整批 LEDGER 中 `回退`/`需调` 为 **0**，须在表末书面说明"为何全部一次通过"，
并由项目主随机抽 3 个单元复核。全绿自评更可能是自评失真，不是判断精准。

---

## 6. 验收标准（可测，严于常规）

1. **功能不变量**：Phase 1–3、5–6 **不改任何 `<template>` 语义**（不增删改 `v-if`/`v-for`/事件/props/slot 名）。
   Phase 4 仅允许等能力替换，且每处替换前后同名断言同样通过。
   判据（可执行）：对 Phase 1–3/5/6 的提交跑 `git diff` 结构校验脚本——`<template>` 块内仅允许 `class` / `:class` /
   `style` / `:style` 属性行发生变更，其余任何行变更即视为越界，退回。
2. **无障碍不退**：弹层焦点覆盖率保持 **100%**，且由 §4 Phase 0b 第 6 项改写后的机械护栏持续强制（**不得靠放宽护栏来让它绿**）；
   升级前后各跑一次 axe，违规数**只降不升**；键盘路径（Tab 序、Esc 关闭、焦点返还）逐锚点页实测。
   `v-ripple` 注入的涟漪节点须 `aria-hidden="true"` 且不得命中 `DIALOG_FOCUSABLE_SELECTOR`
   （`useDialogFocus.ts:60` 导出），避免抢焦点或改变首个可聚焦控件的判定。
3. **对比度**：明暗两主题下正文 ≥ 4.5:1、大字 ≥ 3:1、非文本控件边界 ≥ 3:1，**逐 token 实测换算**，不查表。
4. **token 合规**：凡 LEDGER 中列为改动对象的 `.vue` 文件，文件内 `border-radius` / `box-shadow` / 6 位十六进制色
   硬编码计数降为 **0**（未列入本批的文件不计入，但 Phase 5 收尾须报告全站残余计数相对 §2.4 基线的下降幅度）。
5. **覆写债**：`index.scss` `!important` 98 → **≤ 25**；"零选择器重叠"定义为——同一选择器不得在
   `index.scss` 与 `design-system.css` 中对**同一批 CSS 属性**各写一套值；确需分层的须以注释写明分工边界。
6. **暗色同等**：暗色锚点页与浅色同 12 页、同页数打分；
   暗色主观评审均分不得比浅色低 0.5 以上（5 分制），且暗色**不得**只在浅色截图下被判通过。
7. **动效降级**：以 forced `prefers-reduced-motion` 截图证明无残留位移/淡入依赖，逐锚点页；
   使用 `motion-v` 的 4 处须单独验证其 JS 驱动动画也走降级路径（CSS 的 media query 管不到 JS 动画）。
8. **性能预算**：3 个锚点页 Lighthouse performance 相对 baseline 降幅 **≤ 5 分**；
   新依赖 gzip 增量合计 **≤ 60 KB**（见 §7）；`npm run size:gate` 通过。
9. **验证链完整**：每单元收尾跑齐 `lint` → `typecheck` → `test`（153）→ `build` → `size:gate` → e2e，
   e2e 须带 **`PW_PORT=5175`**（`playwright.config.ts:3-6` 自陈 5173 被占用会静默复用别项目服务、测出假缺陷）。
10. **留痕完整**：LEDGER 每行字段齐全，无空"理由"、无空"对比判定"。
11. **过渡类属性不得充当证据**（Phase 0 实测追加）。视觉基线在 `reducedMotion: reduce` +
    `animations: 'disabled'` 下采集，Playwright 会在捕获瞬间把过渡/动画**强制落到终态**；
    叠加 `page.clock` 冻结后，页面里"已排队但推不动"的过渡既不会出现在像素上，
    也会让 `getComputedStyle` 在读不到终态时返回**旧值**（实测：`.l1-card` 的
    `transition: box-shadow 0.25s` 使第一版量程守卫把"注入生效"误报成"被优先级挡掉"，
    而不过渡的 `border-radius` 正常）。
    因此：**改 `transition-duration` / `transition-timing-function` 不构成可见回归，不得写进"有效性"栏**；
    任何用 `getComputedStyle` 取证的脚本必须先显式消除冻结时钟的影响（临时压掉过渡再读，
    且证明这一动作不改变被截图的那一帧），否则读到的是历史值。
    过渡本身的合理性改由 §4 Phase 6 的降级实测与 §7 的动效清单承担，不由基线像素承担。
12. **量程必须有实测下限，每条"零 diff"必须附量程出处**（Phase 0b 实测追加，独立评审复核归因）。
    旧配置 `{threshold:0.2, maxDiffPixelRatio:0.002}` 下四档注入**全部 PASSED**——连"整页亮度改 3%"
    这种哨兵级改动都检不出 ⇒ 该配置下产生的任何"零 diff"一律作废，不得作为证据沿用。
    **两道闸不得混为一谈**（这里原先的归因是错的，评审纠正）：`threshold` 是**逐像素闸**，
    要求单通道差 `> 264.0 × t`（`t=0.2` ⇒ 52.8/255），而 3% 亮度变化最多 `8/255`，
    所以旧配置下 pixelmatch **一个像素都不计数**——漏检的是这道闸，不是免检额；
    `maxDiffPixelRatio` 才是免检额（1440×900 = 1,296,000 px ⇒ `0.002` 为 2592 px、`0.0005` 为 648 px）。
    推论：**旧"零 diff"对薄型/低对比形态（正是 Phase 1–3 要动的形状）全部作废，
    但对高对比大面积替换并没有失效**（Δ>52.8 且 >2592 px 一样会红）——本条是保守禁令，不是说旧配置在两类上一样瞎。
    ~~现配置 `{0, 0.0005}` 的实测边界须随读数一起写出：改动面积小于免检额 648 px 不可见，
    单卡 `16px→14px` 圆角即被漏检（实测计数 198 px）。故 Phase 2 的圆角/描边工作不得以整页像素 diff 作有效性证据~~
    ——**这一段是 Task 4c 之前的状态，保留只为记录判断怎么变的**。Task 4c 消除日期漂移后把免检额一路收到
    **`{ threshold: 0, maxDiffPixelRatio: 0.0001 }`（129.6 px）**，22 张基线仍全绿
    （控制者复跑全量 visual：**51 passed / 2 failed**，两条失败都是已知的 `error-404` 明暗 = D-1）。

    > **⚠️ 我曾据这次收档把 Phase 2 改判为"像素证据成立、只是余量 1.5 倍"（commit `0f4c2cd`），
    > 该改判已被独立评审用几何上界驳回，现撤回。判据维持原样：非像素判据是准入门。**
    > 错在把**探针选的那一档**（`16→14`）的余量当成了 **Phase 2 整条阶梯**的余量。
    > 一对角弧贡献 `π(r1²−r2²)` px，对 129.6 逐档算：
    > 16→14 = 188.5（实测 198，**1.53×**）；16→12 = 351.9（2.71×）；12→10 = 138.2（**1.07× 勉强**）；
    > 9→10 = 59.7（**0.46× 检不出**，而 `--r-l3` 现值就是 9px、阶梯目标含 9）；
    > 8→6 = 87.9（0.68×）、8→10 = 113.1（0.87×）——**Phase 2 自己的档从 0.46× 跨到 2.71×**，
    > 且孤立控件的多卡相邻角会重叠，真实计数还低于这些上界。
    > ⇒ **"1.5 倍"是探针那一档的性质，不是 Phase 2 的性质**；
    > 逐卡 `borderRadius` 计算值等式 + 角部定向裁剪**仍是准入门**
    > （这与 `task-4c-brief.md:179-181`、`visual-helpers.ts:349-351`、报告 §9 `P0a-RADIUS-GATE` 一致——
    > 我那次改判同时 contradict 了代码和本条自己下一段，属于"改判时没读完上下文"）。
    > **另一半理由分两条、别混**：免检额按**期望图**面积算 ⇒ 基线变长会**稀释灵敏度**
    > （1440×3000 ⇒ 免检额 432，同一个 198 只剩 0.46×）；
    > 而若只有**实际页**变高、期望图没变，runner 报 `sizesMismatchError` **直接变红**（不是变瞎）。
    > 一条是"绿得没意义"，一条是"红得有意义"。
    > **且 `1440×900` 目前只是"真"、没有被断言**（无测试读 IHDR，也没有 `1440*900*0.0001 === 129.6` 的算式）
    > ⇒ 列为 Task 4c 修复轮项。`threshold: 0` 那一半确实被三重钉住
    > （字面单测 + 禁第二副本 + 尺从源码运行时重解析）。
    日期漂移的根因与消除过程（4 张基线原本确定性差 110–321 px；
    **根因由后端代码与差异几何证明，不由 md5 证明**：
    `StudentVisualizationComponent.java:50,74` 用 `LocalDate.now()` 回推 x 轴、`SdVizSection.vue:93,162` 直接渲染该串，
    而实测差异恰好是**每页一条 8 px 高的横带**（`page.clock` 管不到服务端）；
    **admin 那两张此前缺后端引证，现按引用核验补齐**：`AdminAnalyticsComponent.java:343-344`
    （`LocalDate endDate = LocalDate.now()` / `startDate = endDate.minusDays(days - 1)`），
    链路为 `useAnalytics.ts:483 getUserGrowth(30)` → `AnalyticsController.java:237-242 /users/growth` → 该组件，
    消费端 `useAnalytics.ts:535` 把后端 `d.date` 直接铺进 `xAxis.data`；
    且同一窗口还会**重新取数**（`StudentVisualizationComponent.java:59,134` 用 `today.minusDays(13/6)` 去查库），
    换一天可能动的是 series 几何而非文字），
    ⚠️ 同处两组读数的证据等级**已在 Task 4c 之后改变，别照旧引用**：
    **"字节差 618 / 计数 321 / AA 吃掉 297"可加合（618=321+297）、属已证**；
    而 **`1983`（radius 字节差）此前只在离线重算的转述里、报告表格查无原始数**，
    现已由**入库的只读尺**（`frontend/e2e/visual/tools/measure-diff.cjs`）重算并复核为 1,983（计数 1,394）
    ⇒ 这一组数**从"不可重生成的转述"升级为"一条命令可重放"**。
    引用时仍要带上各数所属的 `t/r` 口径（Task 4c 评审实测：漂移那几轮跑在 `r=0.0005`，
    而 `t=0` 下计数与 ratio 无关——这条免责须写在漂移段里，见 §6 Step 1f 的残留项）。
    最终采纳 `maxDiffPixelRatio: 0.0001`（免检额 129.6 px，残余抖动实测最大 46 px）且 22 张仍全绿；
    `r=0` **未跑、未采纳**（实现者如实记下"那个 46 px 本轮没重测"）。
    **但 `0.0001` 也救不了圆角**：16→14px 只重绘四个角弧，`4·(π/4)(16²−14²)=189 px`（实测 198），
    这是几何上界 ⇒ Phase 2 的准入门是**为非像素判据**（逐卡 `borderRadius` 计算值等式 + 角部定向裁剪），
    diff % 只留给会按面积放大的形态。
13. **暗色量程未被证明前，不得用浅色结论代证**（Phase 0b 追加）。暗色阴影本身低可见、且 `--sh-*` 暗色值
    与浅色系不同，"浅色检得出"不蕴含"暗色检得出"。若 Task 4c 测得暗色漏检，则 Phase 1 的暗色部分须改用
    可静态断言的量（抬亮值、描边宽度）替代像素 diff，并在 LEDGER 写明替换理由——不允许沉默地报"零 diff"。
14. **锚点须各自证明"数据已到位"**（Phase 0b 追加）。通用守卫（`#app` 非空、`.error-boundary` 计数 0、
    URL 未被改写）已被实测绕过：后端瞬时失败使 `teacher-dashboard` 渲染成空态而四道守卫全过。
    ⇒ 每个锚点要有属于该页的数据到位断言，并用"把接口打成空响应"验证它真的会红。
15. **Phase 1 是"带条件放行"，不是无条件放行**（4b 独立评审后定）。可放行的是
    shadow-alpha（实测计数为免检额的 **32~86 倍**）与整页亮度阶（约 **1905 倍**）；
    四个条件缺一不可，违其一的改动有效性判据自动降级为"主观标注"：
    ① 每个单元须报 **pixelmatch 计数 + 最大单通道色差**，不得只报 diff %（单报百分比会丢掉"是刚过线还是余量百倍"）；
       **本条的前置是 Task 4c Step 1g**：当前交付的探针只 `console.log` 免检额、既不读 diff 计数也算不出单通道色差，
       而算过它们的一次性脚本已被删除且从未入库 ⇒ **harness 补不出这两个数之前，本条无人能守**，
       任何单元若据旧 harness 报数，须写"读数不可重生成（原因）"而不是报一个引用不到的数。
    ② **4 张日期锚点在 Task 4c 钉死载荷之前不得充当证据**（其字节差已达 618 px，是 648 px 免检额的 **95%**，
       不是原先以为的 2 倍余量）；
    ③ 暗色未探针前沿用第 13 条（暗色 `--sh-l1` 是 `rgba(0,0,0,.4/.5)` 压在 `#171d26` 上，
       是另一个灵敏度问题，见 `theme.css:208,225`）；
    ④ **内高光 `inset` 与局部表面色阶 `surface` 尚未量程化**——"t=0 什么都能报"的论证只在
       pixelmatch 的抗锯齿分类器不吃掉高光边缘时成立，而 radius 恰恰演示了会被吃
       （1983 字节差 → 1394 计数）。Task 4c 补出这两档之前，Phase 1 不得对 inner highlight 报"零 diff"。
16. **"绿"不等于"一致"，措辞受数值约束**（4b 独立评审追加）。`threshold: 0` 下被比对的仍是
    **分类器计数**，不是字节比较：抗锯齿差异会被判为 same 而不计数。故 LEDGER 与任何结论里，
    **"byte-identical / 一字不差"只允许在两张图 md5 相等时使用**；其余场合一律写"计数为 0（字节差 N）"。
    同类地，`animations: 'disabled'` 对**无限动画是 `cancel()` 而非跑到终态**，
    加载态/呼吸态在基线里拍到的是"被取消的那一帧"，不得描述成"稳定态"。
17. **交付量程的三条硬前提**（Task 4c 实测，`{0, 0.0001}` 档；判据由 `helpers.spec.ts` 的常量单测钉住）：
    ① **暗色像素证据成立当且仅当 `threshold` 保持 0**。Phase 1 的精确幅度（`--sh-l1` 环境层 `.06→.10`，
       暗色按同绝对增量 `.5→.54`）在暗色计数 20,690 / 7,164（免检额的 160× / 55×，看着很安全），
       但**最大单通道色差只有 2/255 与 1/255**（浅色同档是 7 / 6）。逐像素闸要求 `Δ > 264.0 × t` ⇒
       **`t` 一旦回到 0.01（要求 Δ>2.64）暗色这一档立刻失明**，而计数不会告诉你它失明了。
       任何人回调 `threshold`，暗色 `--sh-*` 改动就变成沉默——所以这条必须由单测钉住，不能只写文档。
    ② **内高光 `inset` 在两主题都不得报"零 diff"，而原因不同**：浅色是**效应本身为 0**
       （注入 `inset 0 1px 0 rgba(255,255,255,.25)` 到纯白卡底 `--surface:#fff` 后与基线 **md5 相等、字节差 0**）
       ⇒ 这不是免检额吸收，**收任何 ratio 都没用**，只能换证据形式（对该元素做定向截图对，或静态断言）；
       暗色有效应但极薄（字节差 3,610 / 1,182，pixelmatch 只计 **107 / 24**）⇒
       **抗锯齿分类器吃掉 97%**，这是 §6.15 ④ 那个机制第一次拿到实测数且可重生成。
    ③ **描边/边界色 `border` 在浅色单卡页漏检**（计数 26 < 129.6；暗色同档 2,374 检得出），
       所以"边框加深"这类改动**不能沿用阴影档的结论**。
    另外两条正面结论：**`surface`（整块面色阶）两主题 1,248×~3,073× 充分**，Phase 1 的暗色"抬亮"可走整页 diff；
    **`radius` 探针四格全检出（1.5×~17×）但这不构成 Phase 2 的准入门**——那 1.5× 属于 `16→14` 这一档，
    Phase 2 自己的阶梯是 0.46×~2.71×，详见第 12 条末的驳回与几何表。

---

## 7. 依赖选型与代价账

| 选型 | 用途 | 代价（须实测，不引用估计值充数） | 判定 |
| --- | --- | --- | --- |
| `lucide-vue-next` | 替换两份重复手写 SVG path 表，统一全站图标 | 估 gzip +8–15 KB（tree-shaken）；进 osv-scanner / trivy / gitleaks 三道门禁；须核 `size:gate` 阈值 | **采纳**，收益明确且不触碰调用点 |
| `motion-v` | 4 处高价值动效编排 | 估 gzip +18–30 KB；同样过三道门禁；引入 JS 驱动动画后 CSS 的 reduced-motion 降级须重做验证路径 | **有条件采纳**：超 §6.8 预算即退化纯 CSS |
| 自研 `v-ripple` | 主按钮/侧栏项/可点卡片/tab 按压反馈 | 零依赖、约 60 行；须自证 token 跟随与暗色 alpha | **采纳** |
| CSS + Vue 内置 `<Transition>` | 其余全部动效 | 零成本 | **采纳** |
| Tailwind / 其他 UI 库 | — | 与现有 Element Plus 双轨，属风格体系重构，超出"增量"边界 | **否** |

包体估计值以 `npm run build` + `size:gate` 实测替换；**实测超阈值时改方案，不改阈值**。

---

## 8. 与 D2-8 既定风格的冲突（须正式废止，否则成果会被改回去）

`docs/项目现状分析与未来发展规划-2026-10-13.md:635`（D2-8 待办）要求"贯彻极简 / **无渐变 / 无 hover** 既定风格"。
这与本次方向正面冲突，且该条与实际代码早已背离：

- 说"无渐变"，但 `teacher/Dashboard.vue:237-242` 六组 `linear-gradient`、`Login.vue:218-219` `radial-gradient`、
  共 22 个 `.vue` 在用渐变；`design-system.css:33,278,548` 本身就在用渐变。
- 说"无 hover"，但 `design-system.css` 有 22 处 `transition`，`theme.css:53` 专门定义了 `--sh-l1-hover`。

**处置**：本批须改写 D2-8 措辞为"克制但不寡淡：层次靠高度差与描边、动效须有 reduced-motion 降级"，
并在 §5 LEDGER 登记该改写的 commit。**不做这一步，下一批次会照旧条文把本次成果回退。**

仓库内无独立设计系统文档；`docs/项目手册.md:240` 仅把三个样式文件记为"CSS 变量双主题"，无 token 清单
——即文档层面视觉规范实质空缺，本批不存在需要同步的旧视觉规范文档，只需废止 D2-8 措辞。

---

## 9. 知情风险与未验证项

诚实标注，实施计划须逐条消解：

1. **「功能完整保留」的可证明边界**：本批能证明到"模板语义未改 + 153 单测 + 9 e2e 全绿"。
   **观感层无自动化证明**，靠 Phase 0a 建成的锚点基线 + 人工复核。在项目主批准的留痕要求下，
   这条由"完全无证据"提升为"有截图与数值证据，但主观判定仍为自评"。
2. **Phase 0b 的 `$el` 可达性**是设计成立的前提，未实测（见 Phase 0b 末段）。若结论为不可达，
   Modal 的焦点接入方案须改为组件自持引用，属设计微调不影响阶段划分。
3. **`append-to-body` 存量分布未测准**：属性解析对多行标签的布尔属性有漏计，仅确认至少 1 处显式使用
   （`views/admin/teaching/components/RmUploadDialog.vue:2`）。已安装 EP（当前 2.14.7，见 §2.5 版本漂移条）
   的 `el-dialog` 默认 teleport 行为**不凭记忆断言**，列为 Phase 0 实测项。
4. **2 处 `<el-dialog>` 属性未解析成功**（65 vs 63，原因见 §2.1），Phase 0 精算后可能给 Modal 增加能力项。
   同理，护栏口径（54）与本文 grep 口径（55）的 1 个文件差异须对齐为单一口径，否则 Phase 4 的"总数不降"断言会失真。
5. **⚠️ 焦点覆盖率护栏是本批最容易踩的既有机制**（`useDialogFocus.coverage.test.ts`，见 §2.1 末行）。
   它以字面量 `<el-dialog` 为扫描键并写死 `≥ 50` 的防空转下限——**这是为"永远别有人漏接线"设计的，
   不是为"弹层可换实现"设计的**。Phase 4 若按原样迁移，第一批就会红；若为了让它绿而直接删断言或调低阈值，
   则等于无声拆掉 D2-7 的机械保障。定案见 §4 Phase 0b 第 6 项：改写为弹层实现无关，且阈值改为受控期望总数。
6. **暗色同等的工作量**是本项目最大的不确定项：`index.scss:513-620` 的暗色覆写是独立 `!important` 集群，
   重排风险与浅色覆写收敛耦合。若暗色在 Phase 3 拖住进度，**按 §4 顺序继续、暗色单列补做**，
   但不允许把暗色降格为"保底不劣化"——那与项目主要求 3 相违。
7. **工作量与批次现实**：Phase 0–6 估算 60–80 个 LEDGER 单元。按"一天一批次"节奏，这是 **2–3 周连续批次**
   的体量，不是一天的活。实施计划须按 §4 的"计划分份"排期，而非把六个 Phase 塞进一天。
8. **Windows 视觉基线抖动**是 §5 协议的真实成本：阈值标定可能要迭代 2–3 轮，且基线图（约 2–4 MB）进 git。
9. **本设计不含配色体系重构**。Phase 1 动到 `--bg`/`--surface-2` 的明度数值（约 8 行，集中 `theme.css`），
   这是"层次失效"的必要修复而非风格改写；色相、品牌赭红主色、语义色一律不动，与项目主"配色后续单独调整"的排期一致。

---

## 10. 回滚与文档落点

- **每 Phase 一个可回滚提交点**；Phase 1 的 token 改动集中在 `theme.css` 约 8 行，单行即可回滚。
- Phase 4 按目录分批，**每批一个 commit**，任何一批撞测试且无法当场解释，整批回退而非局部修补。
- 新依赖与 `package-lock.json` 变更单独成 commit，便于依赖层与样式层回滚互不牵连。
- 文档落点（遵循"份数尽量少"）：
  - 设计与本台账：`docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md`（本文，唯一）
    + `docs/visual-upgrade/LEDGER.md`（唯一台账表）
  - 证据图：`docs/visual-upgrade/evidence/`（按 §5 策略，非全量）
  - 基线快照：`frontend/e2e/visual/__snapshots__/`
  - 批次登记：`docs/项目手册.md` 附录 D **DR-94**；D2-8 措辞改写见 §8
  - **不新建** README、实施参考、任务分解等配套文件；实施计划走 `superpowers:writing-plans` 的产物路径。
