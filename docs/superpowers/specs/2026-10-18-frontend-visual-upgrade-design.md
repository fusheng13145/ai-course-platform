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

### 2.6 一条我当时判成"当前未爆"、实为**现存缺陷**的风险（Task 8 实测更正，2026-10-19）

`.animate-in` 用 `animation: rise-in ... both`（`design-system.css:1358-1359`），`both` 填充会保留
`transform: translateY(0)`，**成为 `position: fixed` 后代的包含块**——即 `ui/Modal` 的遮罩若落在带 `transform`
的祖先内，会被困在该祖先里而不是铺满视口。

我原先只测了三例、都是兄弟级，于是写下"**当前未被困**，记为 Phase 4 前瞻风险、不记作现存缺陷"（§2.6 旧文）。
**这个结论错在样本量：13 个使用点我只看了 3 个。** Task 8 逐个核完，实测有**两处当下就生效的包含块**：

| # | 被困的 Modal | 祖先 | 生效的那条 CSS | 为什么是"会赢"而非"存在但被覆盖" |
|---|---|---|---|---|
| 1 | `views/student/components/SdCourseGrid.vue:111`（`<Modal narrow>`，就在该组件模板根之内） | `<section class="l1-card animate-in d2">`（:2） | `design-system.css:1358-1359` `.animate-in{animation: rise-in .42s var(--ease-out) **both**}`，`rise-in` 的 `to` 是 `transform: translateY(0)` | 该类只在 `design-system.css` 定义、无第二份；全仓 `grep "(transform\|filter\|backdrop-filter)[^;]*!important"` 命中 **0 条** ⇒ 没有 `!important` 压得住它 |
| 2 | `views/teacher/tools/KnowledgeManagement.vue:96` 的 `<ExcelImportDialog>`（其模板根即 `<Modal wide>`） | 页面根 `<div class="fade-in">`（:2） | `index.scss:506-508` `.fade-in{animation: fadeIn .35s … **forwards**}`，`fadeIn` 的 `to` 是 `transform: translateY(0)` | `.fade-in` 在 `design-system.css` 只出现在 reduce 块里（:1381），定义就在 `index.scss:506`，无竞争者 |

**两者都不是"动画跑完就没了"**：`both`/`forwards` 是**填充**，动画结束后 `translateY(0)` 永久留在计算值里，
而 `translateY(0)` ≠ `none` ⇒ 包含块**永久存在**。**机制由 CSS 直读证成；下面的症状是推断，尚无像素实证**
（要铁证须在 `no-preference` 下对这两页出定向截图对，见本节末的盲区条）：#1 的 `.l1-card` 带 `overflow: hidden`
（`design-system.css:62`），`inset: 0` 的遮罩被**裁进卡片里**（"弹窗缩在卡片中间"）；
#2 的根不裁剪，遮罩铺满内容区但**盖不住侧栏与顶栏**，`z-index` 也落在内容区层叠上下文内。
其余 11 处 Task 8 逐个核为**未被困**（都在 `</section>`/`</div>` 之后做兄弟）；
全仓 `will-change` **0 处**，EP `.el-loading-mask` 的 `backdrop-filter`（`index.scss:372`）是**子节点**、不构成祖先。

> **⚠️ 这条缺陷 22 张基线在结构上拍不到，必须随结论一起报。**
> `design-system.css:1379-1382` 在 `@media (prefers-reduced-motion: reduce)` 里给 `.animate-in, .fade-in`
> 写了 `animation: none` ⇒ 计算 `transform` 回 `none` ⇒ **包含块在 reduce 下消失**，
> 而视觉基线正是 `reducedMotion: 'reduce'` + `animations: 'disabled'` 采集的（§6.11 记的就是这套）。
> ⇒ 它只在 `no-preference`（默认）的真实浏览器里出现。这不推翻量程结论，而是划出它的一条边界：
> **基线能证明"像素没变"，不能证明"这个缺陷没发生"**——凡由 reduce/`animation:none` 条件化掉的布局效应，
> 基线一律失明（与 §6.11"过渡属性不得充当证据"是同一根源的**另一半**：那条说过渡不产生证据，这条说条件重置会**抹掉**证据）。
> 处置：#1/#2 属**现存缺陷**，由 Task 8 的 Teleport 直接消解（Teleport 到 body 即脱离任何本地祖先）；
> 关闭后须以 **DOM 结构断言**（Modal 的渲染位置在 `document.body` 之下）而非像素来锁，
> 并把"reduce 条件化会抹掉布局证据"写进 Phase 1/5 的证据口径。
> 另记我当时错的形式：**样本 3/13 就下"未爆"结论**，且把"三例覆盖"当成"全部使用点"。

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
  - 会话对接快照**写进本文 §11**（同一份文档的最后一节），不另开 HANDOFF/进度类文件；状态只允许更新 §11，
    不得把状态写回 §1–§10（那十节是判据，判据被状态污染就没法是"约束权威"了）。

---

## 11. 对接快照（新会话先读这一节）

- 截至 commit **`eb5d38a`**（2026-10-20 本会话末态）。本节只记**状态、未闭合项与纪律**；
  凡判据原文一律指回 §1–§10 或 LEDGER 行 ID，不复述、不改写。
- 所有数值都带"截至某 commit"的限定。**引用前先跑 §11.7 的命令**——本项目已有读数漂移前科（§2 前言）。

### 11.1 读序（照这个顺序，五分钟内能接上）

1. 本节（状态与缺口）→ 2. §1（需求原话与四轮加码＝不可协商项）→ 3. §6（验收标准；第 12、15、16、17 条是三道门禁的正文）
4. `docs/visual-upgrade/LEDGER.md`（唯一台账表，每行 9 栏；**行 ID 是唯一可引用的坐标，行号不是**）
5. `docs/visual-upgrade/evidence/phase0-measurements.md`（读数转录，每节标注了"可重生成 / 不可重生成"）
6. `docs/superpowers/plans/2026-10-18-frontend-visual-phase0.md`（18 个 `### Task` 标题，执行载体）
7. `.superpowers/sdd/2026-10-18-frontend-visual-phase0/progress.md`（SDD 过程账：派发、复核、裁定逐条）
   —— **该目录被 git 忽略**，`git clean -fdx` 会毁掉它。承重裁定已全部回写进 §/LEDGER，别把它当唯一副本。

### 11.2 Phase 0 任务状态（结论列照 §5 的三问口径，不自行加判据）

| Task | 结论 | commit 锚 | 未闭合的部分 |
| --- | --- | --- | --- |
| 1 | 完成（1 轮修复后 Approved，9 项 finding） | 过程账有两条 complete 行且**起点对不齐**（`072743b..9c73f65` 与 `08b6676..9c73f65`）⇒ 别抄，用 `git log --oneline -- frontend/playwright.visual.ts` 现取 | — |
| 2 | 完成（Approved，0 Critical/Important） | `9c73f65..4a9a70c` | — |
| 3 | 完成（Approved） | `4a9a70c..daa60c7` | — |
| 5 | 完成（Needs fixes → `cdcb304` 修复 ＋ `84b3551` 证据可 pin 化，2 Critical 全闭） | `34181db..84b3551` | — |
| 6 | 完成（1 轮修复后 clean） | `f702400..cb909c4` | 3 条非阻塞遗留全部转进 Task 8 Step 3b |
| 7 | 完成（复核 6 项全 ADDRESSED） | `2b01f9a..6d2379d` | 1 条新 Minor 转 Task 8 Step 3b 第 0 条 |
| 11 | 完成（修复轮复核 6 项全 ADDRESSED、无新增 Critical/Important） | `999862c` ＋ `8b827aa` | 焦点陷阱本身按 R-1 留给 Task 10；**LEDGER `P0b-DRAWER-01` 仍写着"待回填（由 Task 11 修复轮替换）"而未消**（见 §11.4 末条） |
| 12 | 完成（review clean） | `999862c..b3965a7` | — |
| 4 | 完成 | `daa60c7..f568130` | 基线 **22/24**，缺的 2 张原因记在 LEDGER `P0a-HARNESS-01`；准出判据已据实由 24 改 22 |
| 4b | 完成（两轮独立评审 0 Critical） | `f568130..777d9bd` | 5+2 条 Important 全部转成 4c 步骤，未丢弃 |
| 4c → 拆为 4c-fix A/B/C | **未闭** | A：`4ca2ea4..d5a44aa`（评审 Approved） | B 轮已落地并交出 B5 逐锚实测，但**账内没有 B 轮复核结论**；C 轮 8 项（编号 9–16）未派，含 `data-theme=""` 三步取证、`PW_VISUAL_NOPIN` 语义统一、单一来源清扫加 `--update-snapshots` 门测、`visual-helpers.ts` 纯搬迁拆分、`smoke.spec.ts` 与 `global-setup.ts` 残留的 5 处行号引用 |
| 8 | **完成**（两轮复核都到席了） | 第 2 轮 `478c72e`/`b4821e3`/`38600e4` ＋ 控制器补钉 `2fc628d`/`eb5d38a` ＋ 第 3 轮 `78f5f0e` | scoped re-review 判 I-1…I-6 **全部 ADDRESSED**、无新增 Critical/Important，并独立跑出 10 files / 98 tests；它退回的两条 Minor 由控制器第 3 轮自修（补挂载证明 ＋ 给过期"97"读数加限定）。**R-T8-R3**：第 3 轮 16 行未再开复核席，兜底是末尾 whole-branch review |
| 9 | **完成**（两轮复开到席；实现者撞 turn 上限、控制器接手验收） | `8218ee8`/`5f314ca`/`e268c23`/`9bda5ad`/`f00e438` ＋ 台账 `d876188` ＋ 修复 `efa4cc7`/`cb82387` | 焦点接入的**前半**（Modal 自持面板侧 `rootRef` ＋ `open && mounted` 适配器）已入库，三条可证伪焦点用例与 P-a/P-b/P-c 三条读数也入库；**`defineExpose` 那半按 R-T9-7 主动不做**（见 §11.5）。控制器自验：目录门 11 files / 105 tests、全包 165 files / 1749 tests、lint 静默、棘轮 402 持平，并**独立复跑了那条决定性变异**（写 `defineExpose` ⇒ 18 failed / 87 passed，与执行者记数逐字相符）。**尚未过任务级评审** |
| 10A 引用计数滚动锁 | **完成**（复核到席） | `4445ef6`/`88c8ee6`/`8e03639`/`d72bab8` ＋ 必修 `98f5439` | Drawer 的内联锁抽成 `useOverlayScrollLock` 并与 Modal 共用一把计数器；复核退回三条必修（新行抄入占位词会炸 Task 15 的残留检查；三处把变异读数指向不入 git 的报告；**"13 个挂载点全被 v-if 门着"这个前提没有入库守卫** ⇒ 已补可重放配方并登记为缺口、归 Task 13） |
| 10B ARIA／焦点陷阱／焦点返还／`closed`／遮罩开关 | **落地；两轮复开到席，第 2 轮抓出**我**写的假对比并已改**（该轮修复未再开第三席，见 R-T10B-2） | `95f95c5`/`27eb3c6`/`4a13985`/`2c3a912` ＋ 必修 `275375c` ＋ 第 2 轮更正（未提交态见下条 commit） | 新建唯一一份 `useFocusTrap`，两原语各一行接线；Modal 抬到 Drawer 已交付的形状。**P-d 实测**：EP 2.14.7 的 `el-dialog` **确实**挂 `ElFocusTrap` ⇒ 本棒是 parity。**第 2 轮纠出两条我的失真**：① "EP 绑容器元素、不吃 `stopPropagation` 这一刀"是**假的**（它的 keydown 也绑冒泡，`focus-trap…mjs:89` 无 capture 参数）；② 我把第三个反例写成"未定位"，其实一层事件转发就找得到（`NmNoticeList.vue:15`）⇒ 少查就当保守。**边界如实兜住**：无 `focusout` 拽回、无 `focusableStack`；子节点 `stopPropagation()` 会静解除陷阱（今天全仓零命中，但没人守） |
| 10C 写回对称／双转发／`--sh-drawer` 与 ltr 两档 | **完成**（第 1 轮复开到席，两条 Important 归正在 `11787de`） | `d79aed8`/`ba63a3a`/`3cff12a`/`900da2a` ＋ 我改错锚 `a07f5a7` ＋ 复核必修 `11787de` | Drawer 的 `close()` 收到 Modal 形状（**方向不许反**）；`NmPublishDrawer` 双转发收成一条腿；补 `--sh-drawer-ltr` 明暗两档与暗色 `--sh-drawer`（**存量缺陷、非本棒引入**）。**我派单里的两处错引被执行者与复核各自抓到一个**：`37b9414` 不是 Modal 现状的证据（定形在 `6d2379d`），`5c221c8` 更在定形**之前**、不可能"保持"它；另"没有一道门会解析 `theme.css`"半错——`format:check` 的 glob 含 `css` |
| 13 / 14 / 15 | **未派发** | — | **Task 13 的地板值已由控制器在本会话复现，不必再猜**：剥掉 HTML 注释**与 JS 注释**后数开标签得 `el-dialog` 63 ＋ `Modal` 13 ＋ `Drawer` 2 ＋ `el-drawer` 4 ＝ **82**，含任一弹层的 `.vue` 文件数 ＝ **73** ⇒ 计划题注的 73 / 82 **精确复现**（命令见 §11.7）。我先前那句"按原始口径应是 84、护栏低两格设防"**是我算错的，收回**：原始 grep 的 65 里含**两处写在注释里的** `<el-dialog>`（`EpStatsDialog.vue`、`CourseExams.vue` 的"v-loading 不能绑在 `<el-dialog>` 上"说明），那不是弹层使用点。**剩下的真活**是把扫描键从字面量 `<el-dialog` 改成"弹层实现无关 ＋ 口径写死"，并跑破坏性自证 |
| 验收包（本会话任务清单第 24 项） | 未交 | — | 形态已定：① 裁定清单含"若错代价"；② 三道门禁结论；③ LEDGER 列数自校＋实跑数字；④ **未定性的事单独列**，不混进"已完成" |

### 11.3 三道门禁的当前结论（正文在 §6，这里只给判到哪一步）

- **Phase 1（token 纵深）**：**带条件放行**，见 §6 第 15 条四条件（含 Step 1g 前置）。不是无条件 GO。
- **Phase 2（圆角阶对齐）**：**准入门＝非像素判据**。`e46416a` 把这条恢复回原判据——我曾判"1.5× 余量、可降级为
  hardening"，被几何上限表推翻（单卡 `radius` 16→14 只有 198 px < 648 px 免检额那一档的比值区间 0.46×–2.71×，
  见 §2.6 与 LEDGER `P0b-MODAL-01`、`P0a-THRESH-01`）。
- **Phase 4（结构收敛 65 处 `el-dialog`）**：准入门 6 项中 **5 项未闭**（清单见计划 Phase 4 gate 节；
  第 2 项已按"迁移时点"收窄，不是全站静态计数）。**本会话新增一条规矩**（R-T9-7 连带）：迁移到 `<Modal>` 时
  **删掉调用方的 `ref` 与 `useDialogFocus(dlg, visible)` 两行**，焦点由组件内部负责——照搬那两行会静默无焦点。
- **Phase 3（EP 覆写层收敛）／Phase 5（页面级升级与硬编码清理）／Phase 6（微交互与依赖）**：**未开工、无门禁结论**；
  它们的前置是 Phase 1/2 的实测门。**§7 的三项新依赖（`lucide-vue-next`、`motion-v`、自研 `v-ripple`）至今一项都没引入**
  （`grep -n "lucide\|motion-v\|ripple" frontend/package.json` 无命中），本批对 `package.json` 的唯一改动是
  `058b907` 把 `element-plus` 从 `^2.4.4` 收紧为精确 `2.14.7`。

### 11.4 未爆缺陷与未定性项（不要把"未爆"读成"已修"）

- 产品缺陷 **D-1 / D-5 / D-6 / D-8 / D-9** 在 LEDGER"缺陷清单"节，含对比度比值与受影响锚点图；
  **这些比值目前没有任何已入库命令能重算**（该节自己写明，判定见 `证据 §2`）。
- 包含块陷阱：两处**今日就生效**（`SdCourseGrid.vue` 内嵌 `<Modal narrow>`、`KnowledgeManagement.vue` 页面根下的
  `<ExcelImportDialog>`），机制与"基线为何拍不到"见 §2.6 与 LEDGER `P0b-MODAL-01`。Task 8 的 Teleport 让遮罩搬到
  `document.body`、脱离一切祖先包含块，**但这条解法只覆盖 `ui/Modal` 自己**：① 谁显式写 `:teleported="false"`
  谁就自愿回到陷阱里（本会话实测调用点**一处都不写** `teleported`）；② `OnboardingGuide.vue` 这类**自带覆盖层**
  （其样式里有 `position: fixed` 加 `z-index: 3000` 那组）的弹层不在 Modal 的保护范围内，§6 Phase 4 门第 5 条已把
  范围扩到"所有自有覆盖层"，那条**仍未闭**；③ reduce 下 24 张基线在结构上拍不到这类缺陷，别拿基线绿当反证。
- **未定性（我明确不代业务方定性）**：接口返回空时 `student-learning-center` 与 `admin-profile` 两格渲染出
  `data-theme=""`。与 D-1"error 路由从不写 `data-theme`"同族但触发条件不同；三步最小取证已写进 4c-fix-C，
  **谁先跑完谁定性**，在那之前不得称它"已修"或"非缺陷"。
- 空态制度化风险（R-CF1）：`__fixtures__/student-dashboard.json` 钉的是**全 0 载荷**，而"数据已到位"守卫是存在性断言
  ⇒ 空态能被守卫放过。裁定是**加内容断言**，不动后端种子数据。
- **台账占位词未消**：LEDGER 里 `P0b-GUARD-01` 与 `P0b-DRAWER-01` 两行仍带"待回填"（前者等 Task 13，属正常；
  后者那格本可由 Task 11 修复轮**已有实测**直接填，不必等新证据）。Task 15 Step 5 会 grep 这两个占位词并要求
  **无输出**，所以现在不清就是收口时炸。
- **视觉套件在 D-1 修好之前结构性不能全绿**（10C 的基线复跑把这条从"22/24"的旧账逼成待拍项）：
  `error-404` 的明暗两张**照跑**却**没有入库基线**（Task 4 按 D-1 主动拒收：error 路由从不写 `data-theme`），
  于是每次都红在通用守卫 `assertComparableRender`（读数 `data-theme=` 空值），**不是红在像素**。
  控制器 2026-10-20 实测：`PW_PORT=5175 npx playwright test -c playwright.visual.ts` = **55 passed / 2 failed**，
  两条失败正是这两张。⇒ Task 15 必须**二选一并写进台账**：修 D-1（产品缺陷，让 error 路由也写 `data-theme`），
  或给这两条加**带理由的显式排除**；不许留成"每次跑都有两条已知红"的常态——那会把真正的回归淹死在噪声里。

### 11.5 控制者裁定（新会话不得重开，除非带来新证据）

- **R-1**：`Drawer` 的 `aria-modal` 撤下、`role="dialog"` 保留；焦点陷阱 Modal/Drawer **共用一套**，归 Task 10 建一次。
- **R-B**：Modal 与 Drawer 的 `v-model` 写回保持逐字对称（`git show 37b9414` 为既有现状证据）。
- **R-CF1**：见上条；修法限于测试面。
- **MODAL-03**：`Modal.vue` 里 `defineOptions({ inheritAttrs: false })` 的残余价值 (a) 是这行今天**唯一**站得住的
  机制性理由（(b) 属前瞻）⇒ 补了入库用例而不是降级措辞。开启态那半句**故意不钉**（翻不红的断言＝虚假覆盖）。
- **R-T9-1**：Task 9 的焦点时序缺口在 **Modal 侧**补，`useDialogFocus` 的 `watch(visible)` **不加 `immediate`、一行不改**
  （它是 63 处 `el-dialog` 的时序契约）；Modal 内用 `open && mounted` 适配，使"挂载即开"也能进一次焦点。
  **代价**：现存 13 个 `<Modal>` 站点全是 `v-if` 挂载即开，若只测 `setProps(false→true)` 会正好避开这个坑而用例照样绿。
- **R-T9-2**：Task 9 的三条探针读数（`rootRef` 开态是否触到真遮罩 / Teleport 内模板 ref 是否真元素 /
  `defineExpose({ $el })` 的解包层次）**必须全部转成入库断言**；③ 之前先在 `runtime-core.cjs.js` 查 `$` 前缀暴露键
  有无守卫或告警，**不许假设**。计划里 `expect(el?.nodeType).toBe(1)` 今天必红且**红是预期**（`d4a4445` 已倒置 3-A/3-B）。
- **R-T9-5 / R-T9-6**（Task 9 计划瑕疵的处置，已由 `9bda5ad` 落地）：Modal 的 `.modal-head` 把关闭按钮排在
  default slot **之前**，而原语按**文档顺序**取首个命中 ⇒ 首焦落在 `.modal-head .t-icon-btn`，
  计划写的 `toBe('INPUT')` 是注定红且红得没信息量 ⇒ 改钉**节点身份**，并在注释里写明这**就是**与 `el-dialog`
  对齐的结果（EP 对话框头部同样有关闭钮在前）；**不许**为凑绿去改原语选择器或加 `tabindex`。
  计划那条"禁用首个控件跳过"在 Modal 语境翻不红（关闭钮永不 `disabled`）⇒ 撤下，换三条真能红的
  （首焦身份 / `false→true` 再入焦 / 挂载即开能进焦）。
- **R-T9-7**（Task 9 末态）**：`defineExpose({ $el })` 那半**主动不做**，Task 9 以"3-B 前半落地"收口。
  判据是控制器**独立复跑**的变异（写上去 ⇒ 目录门 105 全绿变 18 failed / 87 passed；回落写法收到 1 条，
  而那 1 条是 Task 8 复核批准过的 `Modal.vmodel.test.ts` 开启态 `$el` 钉）：shadow 会顺带改掉 VTU 的
  `wrapper.element`，`helpers/trackMount.ts` 的 `w.element.isConnected` 在关闭态抛 TypeError。
  **连带改掉的是迁移规矩**：Phase 4 把弹层从 `el-dialog` 搬到 `<Modal>` 时，**删掉调用方的 `ref` 与
  `useDialogFocus(dlg, visible)` 两行，焦点由组件内部负责**（Modal 与 Drawer 同形）。
  重开条件：若真出现必须由调用方读 `$el` 的站点，须作为独立 LEDGER 单元，同时改那两处钉/夹具，缺一即 18 红复发。
- **R-T9-2 的代价栏被执行者推翻**（它是对的）："`defineExpose` 会把父级可见面从"全部 setup 绑定"收窄"
  **不成立**——`hasSetupBinding`（`runtime-core.cjs.js:3435`）要求 `!state.__isScriptSetup`，
  script-setup 的绑定从来就不挂在 `instance.proxy` 上 ⇒ expose 是**加键不是收窄**；
  真实存量代价只剩 `wrapper.element` 那一条。**由此立一条派单纪律**：控制器写给执行者的"机制结论"，
  即便来自控制器自己读源码，也只算待证伪假设，派发词里必须显式允许执行者推翻它。
- **R-T10-3／R-T10-4**：计划 Task 10 拆成 10A（滚动锁抽取）／10B（ARIA＋焦点陷阱＋焦点返还＋`closed`＋遮罩开关）／
  10C（R-B 写回对称、`NmPublishDrawer` 双转发、`--sh-drawer` 的 ltr token）——题面 Files 清单漏掉 `Drawer.vue` 等
  三个必须动的文件，照它执行正好做出 Step 3 自己警告的"第二套计数器"。R-T10-4 原写"若实测 EP 不锁焦点，
  `ui/Modal` 就比 `el-dialog` 更严"：**这条分支没被触发**——P-d 实测已安装的 EP 2.14.7 的 `el-dialog`
  确实挂 `ElFocusTrap`（`trapped: visible`）⇒ 10B 记为 **parity**，别把那句备选当成既成结论引用。
- **R-T10B-1（复核纠我）**：台账结论栏原写"关闭后焦点返还**触发元素**"说满了。触发物不可聚焦时
  （已实测**三处**：`SdCourseGrid.vue` 的 `<div class="course-card join-card" @click>`、
  `Notifications.vue` 的 `<div class="notice-card" @click>`，以及第 1 轮评审举、我起初回话说"未定位"的
  `NotificationManagement.vue:31`——第 2 轮复核替我找到路径：它由 `NmNoticeList.vue:15` 的
  `<div class="row-card" @click="emit('open', n)">` 经 `@open="openNotice"` 打开，同一失败形状。
  ⇒ **核不够就写"未定位"也是一种失真**：那句当时读起来像保守，实际是我少查了一层转发）
  上升沿那一刻的 `document.activeElement` 就是 `body`
  ⇒ 等于不返还；`<button>` 触发物在点击时是否获得焦点本身是引擎差异（Safari 不聚焦）。
  措辞已降级为"返还上升沿那一刻的 `document.activeElement`"，**这条可证伪缺口归 Task 13 或后续真的
  Playwright 键盘用例**（第 2 轮纠我：Task 14 是**非基线动效抽检组**、不是键盘用例的落点，我原来把它当归属写错了），
  不给 10C。同一轮还纠出：我派给复核的词里把"实现者拆掉了重复遮罩守卫"写成已接受的披露——**那件事没发生**
  （两条守卫都还在，代码与台账都说"两条都留"）⇒ 派单里凡"我已接受某条披露"，必须先回源码核对。
- **R-T10B-2（第 2 轮纠出的第二条我的假对比）**：我在台账里写"EP 绑在容器元素上，**不吃这一刀**"
  （指子节点 `stopPropagation()` 会解除陷阱那一刀）。**错**：已安装的
  `element-plus/es/components/focus-trap/src/focus-trap.vue_vue_type_script_lang.mjs:89` 是
  `addEventListener("keydown", onKeydown)`，**没有第三个 capture 参数**，同样绑冒泡、同样会被截住；
  EP 真多出来的是同文件 `:90-91` 的 `focusin`／`focusout`，而那正是上一条已经声明"本实现不兜"的那块。
  ⇒ 这属本任务族一直防的"机制洗白"形状（把对手的实现说成更强或更弱来抬高自己那条结论），已收回并改正。
- **推送口径（2026-10-19 19:32 起的现行指令，覆盖更早节奏）**：本批次**一次都不推**，全部任务完成后由 leyon 亲自验收；
  截至 `bfb9992`，本地 `main` 比 `origin/leyon` **领先 106 个 commit**（按天：10-18 有 25、10-19 有 70、10-20 有 11）。
  验收清单以 `git log --oneline origin/leyon..HEAD` 现取，别抄这里的数。

### 11.6 纪律（违反即返工，全部来自本会话真实翻车）

- 引用必须能被读者解开：文档用**章节锚**、台账用**行 ID ＋列名**、代码位置用**符号/选择器名**；
  行号只作辅助且必须标"可能已漂"。**永不以 `HEAD` 为锚**（`progress.md` 只能按标题引，它一直在长）。
  派发词也算文档——本会话我的派发词第 7 次被证伪，就是因为它抄了行号。
- 库行为与陈述性断言先自证：`file:line` ＋变异实验，且**变异必须真能跑**（编译期报错不是测试失败，M8 已因此作废）；
  变异证据必须写明它跑在哪个世界（Teleport 根 / 元素根），否则归错位置（M10a/b 与 M6 的教训）。
- 被执行的那份构建要当轮重测：vitest 跑的是 `runtime-core.cjs.js`（＋`vue-test-utils.cjs.js`），**不是** `esm-bundler`。
- 台账结构自校每次改完都跑（见 §11.7）；单元格内**不得出现竖线字符**（含转义写法与 `A|B` 交替式，
  要写多模式搜索一律 `grep -e`），一格一行、**一行之内更不得出现换行**——本会话就是用一个含换行的编辑串
  把一条已交付的行从中间切成四截，而当时的列数自校只按"以竖线开头"筛行，**根本看不见后三截**。
  ⇒ 自校要同时看两件事：每行列数是否为 9，**以及表区行数是否连续**（`grep -c '^|'` 与表内逻辑行数对得上）。
- 子代理：单个 brief 控制在 4–5 个可验证子步、每步一个 commit、报告边写边落盘；
  实现者不并行（只有只读评审可并行），全包测试套件由控制器作为合入门跑一次。
  **本会话两次被上限切断**（150 turn 与 Chat 日配额），判活看产物、不看"没通知"。
- 依赖收紧**逐条授权**，不接受顺手批量清 `^` 范围。

### 11.7 事实基线与可重放命令（读数截至 `11787de`，Task 10 三棒全部落地之后）

| 事实 | 现值 | 重放命令 |
| --- | --- | --- |
| 视觉阈值单一来源 | `{ threshold: 0, maxDiffPixelRatio: 0.0001 }`（1440×900 免检额 **129.6 px**） | `grep -n 'export const SNAPSHOT_THRESHOLDS' frontend/e2e/visual/visual-helpers.ts` |
| ui 原语套件 | **13 files / 132 tests**（`d79aed8` 起；此前 13 / 127、11 / 105、10 / 98 与 97 — 旧读数一律按 commit 限定后再读） | `cd frontend && npx vitest run src/components/ui/` |
| 全包单元测试门 | **169 files / 1790 tests 全绿**（控制器作为合入门跑，`900da2a` 态；此前 `4a13985` 是 169 / 1785、`d876188` 是 165 / 1749） | `cd frontend && npx vitest run`（**必须在 `frontend/` 里跑**：仓库根跑会得到"no tests"的假读数，本会话踩过两次、第二次是刚把这条写进本表之后当场复发的 ⇒ 靠记性不成立，命令要连着 `cd frontend &&` 一起复制） |
| 两目录合跑门（弹层与 composable 的落地面） | **56 files / 587 tests 全绿**（`900da2a` 态；此前 `4a13985` 是 56 / 582） | `cd frontend && npx vitest run src/components/ui/ src/composables/__tests__/` |
| 类型棘轮 | 402 / 402 持平（只许下降） | `cd frontend && npm run typecheck:gate` |
| Lint | 本轮静默；**只覆盖 `src/**`** | `cd frontend && npm run lint` |
| Format | 存量红（约 108 个文件，且 CI 在跑）⇒ **禁写"lint/format 全绿"**，引用前重测 | `cd frontend && npm run format:check` |
| Element Plus | 声明已收紧为精确 **`2.14.7`**（`058b907`）；`@playwright/test` 仍声明 `^1.62.0` 而装的是 1.63.0，**收紧须另行授权** | `cd frontend && npm ls element-plus` |
| LEDGER 列数 | 表内每一行都是 **9** | 命令见本小节末的副本（表格单元里放它会因转义而失真） |
| 视觉基线门（弹层与 token 改动的像素面） | **55 passed ／ 2 failed**（控制器 2026-10-20 实跑；两条失败是 `error-404` 明暗、红在通用守卫 `data-theme=` 空值，**无一条红在像素**，见 §11.4 末条）⇒ 10A／10B／10C 在**已入库的 22 张基线上像素中性**；浅色 ltr 与旧手算值逐字相同也是同一结论的机械面 | `cd frontend && PW_PORT=5175 npx playwright test -c playwright.visual.ts`（**前提**：8080／3306／6379 与三台引擎在听；会话一重启就要走 `start-backend.bat` 重来） |
| 基线可复现前提 | 后端与引擎须在听：8080 / 3306 / 6379 / 7687 / 8001 / 8002（本会话两次实测：起初只有 MySQL/Redis 在听，起完 `start-backend.bat` 后六个全回）；重启只能走 `start-backend.bat` | `netstat -ano \| grep LISTENING` 再看上述端口 |

LEDGER 列数自校的**原文在 `docs/visual-upgrade/LEDGER.md`「表内书写规则」节**（唯一来源），此处只放一份可直接复制的副本：

```bash
# 每行必须是 9；同时肉眼核表区内没有空行断开（空行会把一张表切成两张，列数自检照样过）
awk -F'|' '/^\|/ {print NR": "(NF-2)}' docs/visual-upgrade/LEDGER.md
```


弹层计数口径（Task 13 的地板值来源；本会话由控制器实测复现，`cd frontend` 后执行）：

```bash
node -e '
const {execSync}=require("child_process"),fs=require("fs");
const strip=(f)=>fs.readFileSync(f,"utf8")
  .replace(/<!--[\s\S]*?-->/g,"")          // HTML 注释
  .replace(/\/\*[\s\S]*?\*\//g,"")        // JS 块注释
  .replace(/^\s*\*.*$/gm,"");              // JS 块注释的续行
const pats=["<el-dialog","<Modal","<Drawer","<el-drawer"];
const all=new Set();
for(const p of pats)for(const f of execSync(`git grep -l "${p}" -- src/**/*.vue`,{encoding:"utf8"})
  .trim().split(/?
/).filter(Boolean))all.add(f);
const out={};for(const p of pats){out[p]=0;for(const f of all)out[p]+=strip(f).split(p).length-1;}
out._合计=pats.reduce((a,p)=>a+out[p],0);
out._文件数=[...all].filter(f=>pats.some(p=>strip(f).includes(p))).length;
console.log(JSON.stringify(out));
'
```

实测读数（`11787de` 态）＝ `{"<el-dialog":63,"<Modal":13,"<Drawer":2,"<el-drawer":4,"_合计":82,"_文件数":73}`：
计划 Task 13 题注的 **73 / 82 由此复现**。两个反直觉点必须一起记住：
① **只剥 HTML 注释不剥 JS 注释**时 `<Modal` 会数到 19（`Modal.vue`／`Drawer.vue` 的说明块里有 `<Modal>` 字样），
   合计虚高到 89 —— 护栏现文只剥 HTML 注释（`useDialogFocus.coverage.test.ts` 的去注释步骤），
   所以它口径下的数**本来就不是 82**；Task 13 改扫描键时要把这条口径写死在断言旁。
② 原始 `git grep -o "<el-dialog"` 得 65，多出的两处是**写在注释里**的说明
   （`EpStatsDialog.vue`、`CourseExams.vue` 的"v-loading 不能绑在 `<el-dialog>` 上"），不是使用点。

- 单元格内出现任何竖线（含转义写法）都会把该行撑成 10/11 列——`awk -F'|'` 是逐字符切分，反斜杠挡不住它。

- **工具坑**：SDD 的 `scripts/task-brief` 在本计划上围栏相位失配（Task 10 曾抽出 882 行、13/14/15 报 `not found`）。
  现改用行区间 `sed -n 'A,Bp'` 抽取并逐条核首行标题：10＝2009–2289、13＝2455–2687、14＝2688–2759、15＝2760–末。
  **计划文件一旦改动，这些区间立即作废**，必须重取行号再抽。

### 11.8 下一批次（按此顺序；每步之前先跑 §11.7 与 `git status`）

1. **Task 13**（把焦点覆盖率护栏改为弹层实现无关）。地板值以计划现文为准：**73 / 82**；示例代码里抄着的
   60 / 54 是草稿、不是取值依据；开工须重测，且 LEDGER `P0b-GUARD-01` 那行的占位词由它消。
   **本会话新增一条判据**：Task 9 已把 Modal 的焦点做成**组件内部自持**（`P0b-MODAL-04`），所以"扫调用点开标签
   有没有 `ref` ＋ `useDialogFocus`"这种口径从此**测不出真假**——护栏必须改成"弹层组件自身是否接了焦点"，
   否则覆盖率 100% 与零焦点覆盖可以同时为真（这正是 `P0b-MODAL-04` 必要性栏点出的那道缺口）。
2. **Task 14**（非基线动效抽检组）——补 reduce 模式下基线照不到的另一半；与 §11.4 的三条包含块边界一起做才有意义。
3. **4c-fix-C**（8 项，编号 9–16），含 `data-theme=""` 三步取证与 `visual-helpers.ts` 的纯搬迁拆分。
4. **Task 15**（Phase 0 收口）：占位词 grep 须无输出、LEDGER 列数自校须全 9、三道门禁逐条给结论。
5. **验收包**：按 §11.2 末行那四条形态呈报，未定性的事单独列。

- 每次动手前必查：**leyon 会在会话之间甚至同一轮之内自己改文件并 commit**，所以 `git status` 与
  `git log --oneline -5` 必须重跑，行号类引用一律重取；文档份数保持最少（§10），状态只更新本节。
