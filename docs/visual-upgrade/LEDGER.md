# 前端视觉升级 · 改动台账（唯一）

协议见 `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` §5（证据与台账协议）。
本文件是**整个视觉升级 Phase 0–6 唯一的台账**：追加式、不拆多份；仓库里任何其他文档都不得成为第二本台账。
改基线用 `PW_PORT=5175 npx playwright test -c playwright.visual.ts --update-snapshots`（工作目录 `frontend/`），
且仅在上一行结论为「保留」时执行。

首批行记的是 Phase 0 自己——台账必须先于改动存在，否则"改前截图"这一栏无处可取。

**引用口径**：本文件里的 `design.md` = `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md`；
`计划 NNNN` = `docs/superpowers/plans/2026-10-18-frontend-visual-phase0.md` 的行号；
`task-N-report.md` / `task-N-brief.md` / `progress.md` = `.superpowers/sdd/2026-10-18-frontend-visual-phase0/`
下的同名文件；`spec §6.N` 指 design.md 第 6 节的编号条目。
**Phase 1–6 沿用同一套引用口径。**

## 单元 ID 命名法（Phase 1–6 每行都必须沿用）

`P<阶段>-<单元 slug 大写>-<两位序号>`

- **阶段**：`P0a` / `P0b` / `P1` / `P2` / `P3` / `P4` / `P5` / `P6`。
- **单元 slug**：取被改对象的大写短名，可复用同一 slug 递增序号。已用与预留的 slug：
  HARNESS（视觉 harness）/ THRESH（比对量程阈值）/ GUARD（锚点与护栏守卫）/ CLOCK（确定性时钟）/
  MODAL / DRAWER / TOKEN（token 数值层）/ SHADOW（阴影阶）/ RADIUS（圆角阶）/ OVERLAY（覆写层）/ MOTION（动效）。
- **序号**：两位、在该 slug 内递增。
- ID **唯一且追加式：已入库的 ID 永不复用、永不改号。** 改主意或推翻本行 → 另起新行，
  并在「结论」栏写明取代哪一行；被取代的行留在原地，删行等于毁证。

## 判定字段口径

- **必要性**：不改会怎样。写具体后果，不写"提升观感"。
- **到位**：是否命中 spec §3 的三条根因之一（层次失效 / 圆角阶断裂 / 无生气），还是只在表层涂抹。
  纯前置类（harness、阈值、守卫、时钟）不硬套 §3，但必须明写它属"证据/结构前提"，不得蒙成根因修复。
- **有效性**：机器数值（对比度比值、阴影 alpha、radius 阶、覆盖率、diff 像素数或百分比）
  或明确标注的主观判定，**二者不得互冒充**。
- **结论**：`保留` / `回退` / `需调`。

## 证据纪律（约束下面每一行，也约束 Phase 1–6 的每一行）

1. 每个数值都要能指回一个来源：本会话读过的 `file:line`、本会话跑过的命令与其输出，
   或报告文件的具体行号。没量过的一律写 `未实测（原因）`，**不许用看起来合理的数填空**。
2. **禁止拿"22 张基线没动"当作某个改动生效或没生效的证据。** 本仓已实测：单卡 `border-radius` 16→14px
   只产生 198 个像素差，低于现配置的 648 px 免检额 ⇒ 漏检（见 `P0a-THRESH-01`）。
   需要像素证据的改动必须出**定向元素截图对**，或者直写"未验证"。
3. 过渡类属性（`transition-duration` / `transition-timing-function`）不构成可见回归，不得写进「有效性」
   （spec §6.11，`design.md:327`）。
4. 每条"零 diff"必须附量程出处（spec §6.12，`design.md:337`）。
5. 暗色量程未被单独证明前，不得用浅色结论代证（spec §6.13，`design.md:345`）。
6. 锚点须各自证明"本页数据已到位"，通用守卫已被实测绕过（spec §6.14，`design.md:348`；本表 `P0a-GUARD-01`）。

## 表内书写规则（Phase 1–6 沿用，否则表会自己撑破）

- 每行必须正好 9 个竖线分隔出的字段。自检命令：
  `awk -F'|' '/^\|/ {print NF-2": "substr($0,1,40)}' docs/visual-upgrade/LEDGER.md`，Expected 每行都是 `9:`。
- **单元格内容里不要出现任何竖线字符**（含转义写法）。原因：`awk -F'|'` 是逐字符切分，
  反斜杠转义后的竖线**照样被当成列分隔符**，写了就让该行变成 10 或 11 列——
  本表初稿就因此把 `P0a-HARNESS-01` 撑成 11 列，改法是措辞绕开该字符（"或""加""配 … 使用"）。
- 同一行需要多条证据时，用句号或分号续写，不要另起物理行；一行 = 一个单元，跨行会让列数自检失效。

## 表

| ID | 改前（截图/数值） | 操作（file:line + 理由） | 改后（截图/数值） | 必要性 | 到位 | 有效性 | 结论 | 验证 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P0a-HARNESS-01 | 视觉基线 **0 张**；全库 `toHaveScreenshot` 命中 0。本会话复核：`git grep -c toHaveScreenshot 072743b -- frontend/src frontend/e2e` 无输出（退出码 1），`git ls-tree -r 072743b` 内无 `__snapshots__` 路径；072743b 是首个视觉提交 08b6676 的父提交。同口径记录见 spec §2.1 `design.md:55` ⇒ "改前截图"无处可取 | 新增 `frontend/playwright.visual.ts` + `frontend/e2e/visual/`（config 与助手 08b6676，三角色 storageState 真登录夹具 daa60c7，基线入库 bf40e41）：独立 config 不与 9 个流程 spec 共用 projects/screenshot、时钟冻结、稳定态轮询、`testIgnore` 挡出流程套件。理由＝spec §5 要求每行有"改前"，此前为 0 | **22 张**，目录 `frontend/e2e/visual/__snapshots__/anchors.spec.ts/`。本会话复核：对该目录的 `.png` 用 `ls` 加 `wc -l` 计数 = 22 = 11 个锚点 × 明暗两主题。**不是 24**：计划与 brief 写的"12 锚点 × 明暗 = 24"未达成，`error-404` 明暗两张按缺陷 D-1 主动拒收（`task-4-report.md:273-274`），是有意不入库、不是漏跑 | 不建则 Phase 1–6 全部改动无痕可言：§5 的"改前"栏无处可取，回滚也无可对照底图 | 前置类，不套 §3 三条根因。命中 spec §2.1 的"安全网为 0 / 视觉基线 0"这一留痕缺口，非表层观感 | 机器判（**由报告转引，本任务未重跑套件**）：`anchors --repeat-each=2` → `44 passed`、22 张 × 2 次全部零 diff、`find test-results -name "*-diff.png" -o -name "*-actual.png"` 0 结果（`task-4-report.md:79-88`），收尾第三次全目录 31 passed 仍零 diff（`:286-287`）。本会话自行复核的机器事实：22 张在册；`git status --porcelain frontend/e2e/visual/__snapshots__` 输出为**空** ⇒ 入库基线未被重生成，二次零 diff 的结论与当前文件一致 | 保留 | 本任务（docs-only）**只跑只读检查**：基线目录 `ls` 计数 = 22、`git status --porcelain …__snapshots__` 空、`git grep`/`git ls-tree` 反查 072743b 的 0 命中、`git log --diff-filter=A` 定位 08b6676。lint / typecheck:gate / test / build / size:gate / 视觉套件**本轮一律未跑**（与在飞实现者共用 :8080 后端与 5175 vite）；套件级结果一律标注来源行号、不冒充本会话实测 |
| P0a-THRESH-01 | `SNAPSHOT_THRESHOLDS = { threshold: 0.2, maxDiffPixelRatio: 0.002 }`。旧配置是**假绿**：注入 `body{filter:brightness(0.97)}` 这种肉眼可见的整体色差，旧阈值判 PASS（"PASS = 被吸收"，`task-4b-report.md:115`）⇒ 该配置下产出的任何"零 diff"一律作废（spec §6.12 `design.md:337-339`） | `frontend/e2e/visual/visual-helpers.ts:285` 收为 `{ threshold: 0, maxDiffPixelRatio: 0.0005 }`（本会话读到该行现值，逐字一致）。理由＝量程必须先有实测下限，否则 Phase 1–6 报出的每个"零 diff"只表示"这层看不见"（`task-4b-report.md:284`）。提交 e8def37 把阈值固化为单一来源，5675989 在 22 张真基线上量完三档注入后定稿；16ada6c 只改注释归属 | `{0, 0.0005}`。免检额算术（22 张均为 1440×900 = 1,296,000 px，`task-4b-report.md:302,393`）：r=0.002 → **2592 px**，r=0.0005 → **648 px**，r=0 → **0 px**。**为什么收不到 0**：4 张基线有确定性日期漂移 110–321 px（见 `P0a-CLOCK-01`），321 px 会直接点红（`task-4b-report.md:440`） | 不收紧则 Phase 1 起所有"零 diff"读数是假的——旧配置连整页亮度 3% 都漏检（`:115`），等于没有量程 | 前置类（证据前提），不套 §3；但它决定 §3.1 层次失效那类改动（阴影 alpha）日后能否被证明 | 机器判，**量程边界必须与读数一起写出**：阴影类改动（`--sh-l1` alpha 0.05/0.06→0.12/0.13）在 t=0 下报 55,903 / 21,059 px = 4.31% / 1.62%，是 648 免检额的 **32~86 倍** ⇒ 本配置**可见**（`task-4b-report.md:489-490`）；单卡 `radius` 16→14px 只有 **198 px < 648 px** ⇒ **仍检不出**（`:410`，`:440`）。结论：Phase 2（圆角阶）的整页像素证据**目前不成立**（spec §6.12 `design.md:341-343`） | 保留 | 本会话复核：`grep -n "SNAPSHOT_THRESHOLDS" -A6 frontend/e2e/visual/visual-helpers.ts` → 285 行现值；`task-4b-report.md` 的 115 / 302 / 393 / 410 / 440 / 490 六处引用逐条打开核对，数字均在所指行上；免检额与倍数算术自核（21059÷648≈32.5，55903÷648≈86.3）。`sensitivity` 四档与整表 `anchors` **本任务未重跑**（禁用项，见 `P0a-HARNESS-01` 验证栏） |
| P0a-GUARD-01 | 图谱锚点走"列表首项"默认路径 ⇒ 渲染空态。旧 `student-knowledge-graph-dark.png` 入库的正是**空态**，而它与真图谱只差 4325 px（0.33%，channel>32 口径），在旧阈值 0.2/0.002 下判"相同"⇒ **这张暗色基线一直在假绿**，连 `--update-snapshots` 都拒绝重写它（要删文件才逼得出来），`task-4-report.md:436-440`。空态页能通过全部四条通用断言（`anchors.spec.ts:35-37` 自陈：占位图非空壳、无 `.error-boundary`、`data-theme` 写对、URL 不在 /403 或 /503） | commit f568130 两件事。① 锚点改指真有数据的课：`frontend/e2e/visual/visual-helpers.ts:344` = `/student/knowledge-graph?courseId=4001`（本会话实测行号；选课依据是逐课实测：38 门课里 4001 有 8 节点 10 边，列表首项 4060 与 4047 等是 0 节点 0 边，`:335-340`）。② 加三道绘制守卫 `assertGraphPainted()`：`anchors.spec.ts:51` canvas 已挂载、`:55` 角标节点数 > 0、`:70` 画布非透明像素 ≥ 300（常量 `GRAPH_MIN_PAINTED_PX` 在 `:30`），调用点 `:168` 落在截图**之前**。理由＝spec §6.14 | 两张图谱基线重生成并逐张人工看过：画布非透明像素 **6051**、角标"8 个知识点"、10 条边 + 8 组文字标签可见（`task-4-report.md:267`，与 `anchors.spec.ts:26` 的自陈互校一致） | 不加守卫则一个空态页能通过全部既有断言并成为 Phase 1–6 的对照底图——**这是实测出来的，不是假想**（`:436-440`） | 前置类。命中 spec §6.14"锚点须各自证明数据已到位"；把判据从"页面渲染了"升级为"这一页的核心内容真画出来了"，不是表层涂抹 | 机器判：`GRAPH_MIN_PAINTED_PX = 300` 是"完全没画"的哨兵（实测参照 6051，差约 20 倍，`anchors.spec.ts:24-30`），不是质量门槛。RED 已验：把路径改回不带 courseId，明暗两条都报 `图谱锚点截到的是空态：画布没渲染出来`，而不是"通过但图是空的"（`task-4-report.md:346-348`）。**残余**：守卫只到"有墨/没墨"这一层，节点圆点仍不画 → 缺陷 D-9，不在本行有效性范围内 | 保留 | 本会话复核（只读）：`awk NR=20..80 anchors.spec.ts` 拿到守卫真实行号 51 / 55 / 70 与常量 30、调用点 168；`grep -n 4001` 确认锚点在 `visual-helpers.ts:344`；`task-4-report.md:267,336-348,436-440` 逐行打开核对。**注意**：报告里的旧行号（`anchors.spec.ts:29,48-70`、`:143`、`visual-helpers.ts:284`）已因文件增长过期，本行一律采用本会话重测的行号。视觉套件未重跑 |
| P0a-CLOCK-01 | `freezeClock` 用单步 `page.clock.install({ time })`——本机实测**仍按真实速率前进**（时钟根本没冻住）。且计划原先写的 `pause()` 在 **Playwright 1.63 不存在**：该版本 Clock 只导出 install / fastForward / pauseAt / resume / runFor / setFixedTime / setSystemTime（`visual-helpers.ts:63-64`；仓内 `package.json` 记 1.63.0） | commit 9c73f65 改为两步：`frontend/e2e/visual/visual-helpers.ts:72` `page.clock.install({ time: CLOCK_EPOCH })` + `:73` `page.clock.pauseAt(CLOCK_EPOCH + CLOCK_WARMUP_MS)`（本会话读到这两行）。理由＝基线要可比就必须在同一页面时间上采样；`pauseAt` 正是"推进到该刻即停"的那一个（`:65`），实测之后页面 `Date.now()` 差值恒为 0 | 截图时刻由"经验同刻"改成**预算制常量断言**（commit f568130）：`frontend/e2e/visual/anchors.spec.ts:144` `expect(await page.evaluate(() => Date.now())).toBe(SNAPSHOT_PAGE_TIME_MS)`（本会话实测行号）。总额常量在 `visual-helpers.ts:31` 拆开：epoch + pauseAt(5000) + runFor(5000) + 轮询预算(1200) + 帧数沉降(3200) | 不冻时钟则 CountUp / 入场动画停在随机帧，且解析 courseId 会多走一次列表导航、只有 23/24 落在同一页面时间上（`anchors.spec.ts:127-129` 自陈）⇒ 基线之间根本不可比 | 前置类。命中留痕缺口的"时间维"，与 §3 三条根因无关 | 机器判：`:144` 的常量断言就是"多 settle 一次直接判红"的探针（`anchors.spec.ts:139-144`）；配合 `--repeat-each=2` 的 44 passed 零 diff（`task-4-report.md:83-85`）。**已知未闭**：4 张基线（student-dashboard 明/暗、admin-governance-analytics 明/暗）与改动前代码的新截图仍**确定性**差 **110–321 px**，漂移点全在图表时间轴标签行（y 537..544 / y 696..703）、标签整组往后挪一天（`visual-helpers.ts:267-270`），根因 `frontend/src/composables/useAnalytics.ts:535` 把后端返回的 `d.date` 直接铺进 x 轴 ⇒ **`freezeClock` 只伪造浏览器时钟，伪造不了后端返回值**（`:271-272`）。跨次产物 md5 一致，故确证为确定性而非抖动（`task-4b-report.md:444-446`；该 md5 是当时文件指纹，本会话重算 `visual-helpers.ts` md5 为 76363460…——文件其后被 16ada6c / 777d9bd 两次 docs 提交改过，不复现属预期，不构成反证） | **保留（带已知残余，转 Task 4c）**——不许写成已解决：r 收到 0 的前提是先把这 321 px 日期漂移消掉，再把 `maxDiffPixelRatio` 推进到 0.0001 且 22 张仍零 diff（spec §6.12 `design.md:342-343`） | 本会话复核（只读）：`awk` 读 `visual-helpers.ts:58-73` 确认两步写法与 1.63 无 `pause()` 的自陈、读 `anchors.spec.ts:127-144` 确认常量断言行号、读 `useAnalytics.ts:528-542` 确认 `:535` 就是铺后端日期的那一行；`git show 9c73f65 --stat` 未跑，仅按 `git log --oneline -- frontend/e2e/visual/` 归属提交。时钟与套件均未重跑 |
| P0b-MODAL-01 | `ui/Modal` 无 Teleport。**本会话以 `git show HEAD:frontend/src/components/ui/Modal.vue` 为准**（HEAD = 37b9414）：全文 `grep -n Teleport` **零命中**，模板根节点是 `<div v-if="open" class="overlay">`，位于**第 44 行**。brief 写的 `Modal.vue:19` 已过期——HEAD 的 19 行是 `defineProps` 的收尾 `})`。**本行一律以 `git show HEAD:` 为准、未读工作树版本**：本会话 `git status --porcelain` 实测在飞的是 `Drawer.vue` 与 `Drawer.test.ts`（均 `M`）另加两个未跟踪的 `*.drawerGate.test.ts`，并非任务书所说的 Modal 两文件；为避免与并发写者抢同一文件，读 Modal 也走 HEAD | 待回填（由 Task 8 替换）：单一路径 `<Teleport to="body" :disabled="!toBody">`，并把 spec §2.6 的 `animate-in` transform 包含块陷阱钉成测试（`task-8-brief.md:1-16`，计划 Task 8）。**冲突已在案**：`task-8-brief.md:16` 声称"Task 7 已加好 `<Teleport to="body">`"，与本行改前的 HEAD 实测（零命中）矛盾；控制器的裁定是 Teleport 归 Task 8（`progress.md:454`），实现时以本行为准 | 待回填（由 Task 8 替换） | 待回填（由 Task 8 替换）。可确证的最低限度后果：不 Teleport 时遮罩会被困在带 transform 的祖先盒子里，弹层覆盖范围与 `el-dialog` 不等价，Phase 4 的 65 处等能力替换会静默改变层叠行为 | 待回填（由 Task 8 替换） | 待回填（由 Task 8 替换）。需要像素证据时按"证据纪律 2"出定向截图对，不得拿"22 张基线没动"充当 | 待回填（由 Task 8 替换） | 本会话只跑 `git show HEAD:` 只读核对（Teleport 零命中、根节点行号 44、19 行实为 `})`）。余下由 Task 8 回填真实验证链 |
| P0b-MODAL-02 | spec §9.2 未验证项原样在案：Phase 0b 的 `$el` 可达性是焦点接入方案成立的前提，**未实测**（`design.md:392-393`）。若不可达，Modal 的焦点接入须改为组件自持引用 | 待回填（由 Task 8 替换）：计划 1581-1583 已定死两条分支——`Modal.focus.test.ts` 若 PASS 走与 `el-dialog` 同构的 `dialogRef.$el` 口径（3-A），若 FAIL 走 `Modal.vue` 自持 `rootRef` + `defineExpose` 同形 `$el` getter、`useDialogFocus` 改接 `{ $el: rootRef }`（3-B）。**把实测结果与所选分支写进本行**，这是 §9.2 要求消解的条目 | 待回填（由 Task 8 替换） | 待回填（由 Task 8 替换）。不改的后果：分支未定就上焦点管理，等于把"焦点是否真的可达"留给猜测 | 待回填（由 Task 8 替换） | 待回填（由 Task 8 替换）：只允许写实测的可达性结论（PASS/FAIL 与用例名），不得用主观判断代证 | 待回填（由 Task 8 替换） | 待回填（由 Task 8 替换）。本任务按禁做项未跑 `npx vitest run src/components/ui/__tests__/Modal.focus.test.ts` |
| P0b-GUARD-01 | 焦点覆盖率护栏 `frontend/src/composables/__tests__/useDialogFocus.coverage.test.ts` 以字面量 `<el-dialog` 为扫描键，并硬写 ≥ 50 的防空转下限（spec §2.1 `design.md:56`、§9.5 `design.md:399-402`）。它"为永远别有人漏接线而设计，不是为弹层可换实现而设计"⇒ Phase 4 按原样迁移，第一批就会红；若为了让它绿而删断言或调低阈值，等于无声拆掉 D2-7 的机械保障 | 待回填（由 Task 13 替换）：改写为**弹层实现无关**，阈值改为受控期望总数（计划 2248-2264 起草 `EXPECTED_OVERLAY_MIN = 60` / `EXPECTED_OVERLAY_COMPONENT_MIN = 54`，只升不降）。并把计划 2272 要求的 Step 6 **破坏性实验**结论写进"有效性"栏——计划原文："这是本阶段唯一能证明护栏没被改废的证据" | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）。不改的后果：Phase 4 的 65 处结构收敛里，第一批迁移就会撞上假红 → 最省事的出路是放宽护栏，而那正是 §9.5 明令禁止的 | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）：只允许写破坏性实验的实际结果（把守卫打红的那次运行与它的报错文本），不得写"护栏已覆盖"这类无对偶实验的断言 | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）。本任务未跑 `npm run test`。已知顺序约束：Task 13 必须排在 Task 11 之后（见下文"结构性前置"焦点覆盖缺口条） |
| P0b-DRAWER-01 | 旧 `ui/Drawer` 不声明 props：`:model-value` 落进 `$attrs` ⇒ `.drawer-overlay` 的 0.38 黑幕 + 面板常驻。实证调用点 `frontend/src/views/admin/system/NotificationManagement.vue:47`（`<NmPublishDrawer v-model="drawerOpen">`，而同文件 `:84` 写的是 `drawerOpen = ref(false)`）⇒ 传进去的就是 `modelValue:false`，旧 Drawer 仍渲染整屏遮罩（`progress.md:433-434` 记为 HEAD 既有产品缺陷，`progress.md:523-525` 由评审回源码证实） | commit **999862c**（只含 `frontend/src/components/ui/Drawer.vue` + `Drawer.test.ts`）：对齐 v-model / Teleport / 焦点 / 滚动锁 / Esc / `@closed` 通道，为 Phase 4 收 4 处 `el-drawer` 铺路。本会话以 `git show HEAD:…/Drawer.vue` 实测：`<Teleport to="body" :disabled="teleported === false">` 在 `:131`、`role="dialog"` 在 `:137`、`aria-modal="true"` 在 `:138`、`z-index: var(--z-drawer)` 在 `:182`；且 `git diff --stat 999862c HEAD -- …/Drawer.vue` 输出为**空** ⇒ 999862c 之后该文件未再变动，HEAD 行号即 999862c 行号 | 4 处 `el-drawer` 使用点的能力通道打通（`task-11-report.md:186`）：`:model-value` / `v-model`（`TaskDrawer.vue:3` 用 v-model）、`append-to-body`→Teleport、`direction`→`placement`、`@closed`、Esc、滚动锁、焦点。**改后尚未定稿**：评审判 Needs fixes（4 Important + 6 Minor，`progress.md:520`） | 待回填（由 Task 11 修复轮替换）：Phase 4 要迁的 4 处 `el-drawer`（`EgEventsDrawer.vue:2`、`DscDetailDrawer.vue:2`、`TaskDrawer.vue:2`、`CdsDetailDrawer.vue:2`，本会话 grep 实测）缺任一条通道就会在迁移时静默丢行为 | 前置类，不套 §3；但抽屉贴边圆角复用 `--r-l1`（`Drawer.vue:202-204`），兑现度直接受本行 I-2 影响 | 未实测（评审修复轮未跑，本轮不得填机器数值）。已确证的**两条未闭缺口**：① 焦点陷阱缺失而 `aria-modal="true"` 已声明 ⇒ 语义与实现不一致，控制器裁定 R-1 = **撤下该属性、保留 `role="dialog"`**，陷阱与 Modal 共用一套、归 Task 10 建一次（`progress.md:438-439,534-535`；HEAD 上 `:138` 仍是 `true`，说明裁定尚未落地）。② 圆角只兑现一半：本会话实测 HEAD `.drawer`（`:184-196`）**没有 `overflow: hidden`**，而 `.drawer.rtl`（`:202-204`）设了 `border-radius`、`.drawer-foot`（`:224-230`）有实底背景 ⇒ 页脚方角会盖住圆角（评审 I-2，`progress.md:538-540`） | **需调**（评审 Needs fixes：4 Important + 6 Minor，来源 `progress.md:520`；修复轮回填后按实测改判） | 本会话只跑 `git show HEAD:` 与 `git diff --stat` 只读核对（Teleport/role/aria-modal/z-index/圆角四处行号 + 4 个 el-drawer 使用点）。工作树 `Drawer.vue` 当前显示为 `M`（被在飞实现者占用），**本任务未读、未改、未 stage**。npm 验证链一行未跑，故本行不写任何"全绿" |

## 缺陷清单（Phase 0 测出、**尚未修改**的产品缺陷）

这些是 Phase 0 的度量副产品，不是改动单元。**单独列出、不进上面的表**——否则台账会把"发现了"混成"已修了"。
每条给：实测对比度、适用门槛、受影响的锚点图、来源行。

- **D-1 · `error` 路由从不写 `data-theme`（这就是 22 而不是 24 的原因）**
  生产代码里唯一的 `data-theme` 写入点是 `frontend/src/composables/useTheme.ts:15`，它在 `applyTheme()` 内；
  该函数只由 `useTheme()` 的 `onMounted`（`:45-47`）与 `cycleTheme` / `setTheme` 触发，而 `useTheme()` 在全仓
  仅被 `components/ThemeSwitcher.vue:52` 与 `views/common/Profile.vue:72` 调用（本会话 grep 实测）。
  `views/error/404.vue`、`403.vue`、`503.vue` 三个文件内没有任何 theme 相关代码（本会话 grep 零命中）
  ⇒ 暗色 error 基线**无法诚实采集**：强行入库只会得到与 light 逐像素相同的假暗色。
  受影响锚点图：`error-404-light.png`、`error-404-dark.png` **均未入库**（`task-4-report.md:273-274` 判"拒收"）；
  二次跑里 4 条失败全是同一个 `data-theme` 断言、没有一条是像素差（`task-4-report.md:87`）。

- **D-5 · 暗色登录页 feature 项对比度 2.96:1（正文门槛 4.5:1）⇒ 违反**
  实测：红底 `rgb(193,59,48)` 上 `.feat-item` 合成色 `rgb(43,32,39)` → **2.96:1**；
  该元素 13px / 400 / opacity .88 ⇒ 属**正文** ⇒ 门槛 4.5:1 ⇒ 违反（`task-4-report.md:202,206`）。
  受影响锚点图：`login-dark.png`（`task-4-report.md:252`）。
  **同页必须照抄的区分**：主标题 `.brand-title` 实测 22px / 700 → 属**大字** → 门槛 3:1 → 3.21:1 **通过、不计缺陷**
  （`task-4-report.md:205,252`；门槛分配口径 spec §6.3）。这条区分是"别把主观感受写成违规"的样例：
  同一个数字 3.21:1，挂错门槛就变成假缺陷。
  未决：副标题 `.brand-sub` 约 1.05:1 是**推算值不是量出来的**，`task-4-report.md:211-214` 明确要求标"待复测"。

- **D-6 · 管理页角色徽章 1.22:1（且上一轮记成了 3.80:1，真实缺陷比记录的更严重）**
  实测：`/admin/profile` 暗色下 `.role-tag--admin` 的计算样式 `color: rgb(193,59,48)`（即 `#c13b30`）、
  `background: rgba(193,59,48,0.18)`、`font-size: 12px`、`font-weight: 500`；文字压在实测徽章底 `rgb(197,86,77)` 上
  = **1.22:1**（12px 正文，门槛 4.5:1）；徽章**边界** `rgb(197,86,77)` 对横幅红 `rgb(193,59,48)` = **1.22:1**
  （非文本控件边界，门槛 3:1）。两个 1.22 不是抄重——底色本就是同色系半透明叠出来的（`task-4-report.md:218-222`）。
  受影响锚点图：`admin-profile-dark.png`（`task-4-report.md:272`）。
  **必须记下这次更正**：上一轮把文字对比度误记为 3.80:1，`task-4-report.md:224-227,450-453` 已证伪——
  那个 3.85:1 是旁边 34px/700 的近白字（`.hero-avatar__img`，`ProfileHero.vue:133-136`）与徽章底配出来的对，
  不可能是徽章文字（CSS 只允许 `#c13b30`）。真实值是 **1.22:1**。

- **D-8 · 暗色知识图谱标签用浅色主题的墨 ⇒ 1.13:1，肉眼几乎看不见**
  实测：暗色下标签墨色 `#1f2733` 画在 `rgb(23,29,38)` 面板上 = **1.13:1**（正文，门槛 4.5:1）；
  同一张图里边 `#d59a2a` 对底 = **6.84:1** 可见；浅色侧同墨对白底 = 15.04:1 正常
  （`task-4-report.md:268,422-423`）。
  根因（记录在案，供修复用）：`graphOption` 在 composable 求值时就固化了 `getChartTheme()`，
  而 `data-theme` 要等 `ThemeSwitcher` 的 `onMounted` 才写 ⇒ `resolveTheme()` 回落到 `prefers-color-scheme`，
  headless Chromium 默认 light；`updateChartOption()` 之后只换节点色、不重建 `series[0].label.color`
  （`task-4-report.md:424-428`）。
  受影响锚点图：`student-knowledge-graph-dark.png`。

- **D-9 · 图谱节点圆点根本不画（两主题）**
  实测：zrender 显示列表里 8 个 `path` 元素 `scaleX = scaleY = 0`、`shape = { symbolType:'circle', width:2, height:2 }`；
  按 EASY/MEDIUM/HARD 六个色值扫画布区域 → **0 个像素**；排除 harness 嫌疑的对照实验也做过了
  （不冻时钟实时等 4s 仍是 0，`reducedMotion: 'no-preference'` 仍是 0）⇒ 基线画的是"边 + 节点标签"，没有节点本身
  （`task-4-report.md:430-431`）。
  受影响锚点图：`student-knowledge-graph-light.png`、`student-knowledge-graph-dark.png`（`task-4-report.md:267-268`）。
  与 `P0a-GUARD-01` 的关系：三道守卫的判据只到"有墨/没墨"（下限 300 px、实测 6051 px），
  **节点不画不在其覆盖范围内**，别把"守卫过了"读成"图画全了"。

## 结构性前置（约束后续每一行）

1. **自建 z 阶整体低于 Element Plus 一层。** 本仓 `--z-*` 实测于 `frontend/src/styles/theme.css:60-66`：
   `--z-sticky: 30`（:62）、`--z-sidebar: 40`（:60）、`--z-topbar: 50`（:61）、`--z-drawer: 80`（:63）、
   `--z-modal: 100`（:64）、`--z-menu: 130`（:65）、`--z-toast: 160`（:66）。
   EP 侧本会话在依赖包内复核到：`element-plus/es/hooks/use-z-index/index.mjs:8` `defaultInitialZIndex = 2e3`、
   `:19-23` `nextZIndex()` 返回 `defaultInitialZIndex + 递增计数`，消费点
   `element-plus/es/components/dialog/src/use-dialog.mjs:139` `zIndex.value = nextZIndex()`。
   ⇒ **EP 任何弹层天然盖在自建浮层之上**，`ui/Drawer` 叠在 `el-dialog` 之上会被压住（spec:107-108）。
   后续每一行只要动到浮层，"改后"栏就得给 z 读数，不能只写"层级正确"。
2. **覆写债：`!important` 实测 98 条，目标 ≤ 25。** 本会话跑
   `grep -c "!important" frontend/src/styles/index.scss` → **98**（与 spec §2.2 `design.md:63` 一致）。
   加载序 `main.ts` 先 `index.scss` 后 `design-system.css`，同权重时新体系赢，但 98 条 `!important` 不参与这个比较
   （spec:66）⇒ 任何新增质感都可能在加载顺序上被旧覆写吃掉（spec §3.3 `design.md:138-139`）。
   收敛目标与 Phase 3 门禁见 spec §6.5 `design.md:207`（98 → ≤ 25）。**Phase 1/2 报"改动没生效"之前，先排除这一条。**
3. **焦点管理覆盖缺口。** 4 个 `el-drawer` 使用点（`EgEventsDrawer.vue:2`、`DscDetailDrawer.vue:2`、
   `TaskDrawer.vue:2`、`CdsDetailDrawer.vue:2`）**从未 import `useDialogFocus`**——本会话对照
   `grep -rln "useDialogFocus" frontend/src` 的 60 个文件清单，四个抽屉宿主文件**无一在列**。
   现有护栏以 `<el-dialog` 为扫描键，所以抽屉从来不在它的视野里。
   ⇒ 这就是 **Task 13 必须排在 Task 11 之后**的原因：先让抽屉原语内部接上焦点，
   再把护栏改写为"弹层实现无关"；顺序反了就得靠放宽护栏换绿（spec §9.5）。
4. **证据纪律（再钉一遍，因为它最容易被后来者破）**：**禁止拿"22 张基线没动"当作某个改动没生效或生效的证据。**
   本仓已实测单卡 `border-radius` 16→14px 在该量程下**漏检**（198 px < 648 px 免检额，见 `P0a-THRESH-01`）。
   需要像素证据的改动必须出**定向元素截图对**（元素级 clip，不用整页），或在"有效性"栏直写"未验证（原因）"。

## 复核与抽样说明（spec §5 的反向约束）

本表当前 5 条已定档结论：**4 条 `保留`**（`P0a-HARNESS-01` / `P0a-THRESH-01` / `P0a-GUARD-01` 三条干净，
`P0a-CLOCK-01` 带已知残余、转 Task 4c）+ **1 条 `需调`**（`P0b-DRAWER-01`，依据是评审 Needs fixes 的 4 Important）。
`回退` 为 0。

因此按 spec §5 书面交代一次：**`回退` 为 0 不是因为判断精准，而是因为定档样本只有 5 条、
其中 4 条全是 harness 类前置改动**——它们本身不改像素，判"回退"没有作用对象；
剩下那条 `需调` 恰恰就不是前置。真正会失败的判断发生在 Phase 1–3 的 token / 阴影 / 圆角行上，
那里的 `回退`/`需调` 计数不该是 0。抽样复核请优先抽 `P0a-THRESH-01`（它给后面每一行的"有效性"定了量程上限）
和 `P0a-GUARD-01`（它的三道守卫是图谱两张基线唯一的可信性来源，而 D-9 就藏在它判据够不着的地方）。

四条在飞骨架行（`P0b-MODAL-01` / `P0b-MODAL-02` / `P0b-GUARD-01` / `P0b-DRAWER-01`）里那些"尚未实测"的
字段一律带着占位词写明**由哪个 Task 替换**（依次是 Task 8 / Task 8 / Task 13 / Task 11 修复轮），
除此之外全表不得出现占位词——尤其不得出现计划 Task 5 Step 2 里那个必须由实测值替换掉的下划线占位。
Phase 0 收口时（计划 Task 15 Step 5）会 grep 这两个占位词并要求**无输出**：
本文件的散文段落刻意不拼写它们，免得收口检查被规则说明本身误触发。仍有命中即该单元留痕缺失，
回去填实测值，**不许删行蒙过检查**。
