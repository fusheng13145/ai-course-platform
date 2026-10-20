# 前端视觉升级 · 改动台账（唯一）

协议见 `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` §5（证据与台账协议）。
本文件是**整个视觉升级 Phase 0–6 唯一的台账**：追加式、不拆多份；仓库里任何其他文档都不得成为第二本台账。
改基线用 `PW_PORT=5175 npx playwright test -c playwright.visual.ts --update-snapshots`（工作目录 `frontend/`），
且仅在上一行结论为「保留」时执行。

首批行记的是 Phase 0 自己——台账必须先于改动存在，否则"改前截图"这一栏无处可取。

**引用口径**（Task 5 评审 Critical-2 后改写；全局规矩由控制器立于 commit `7da1233` 的计划全局约束段）：
本文件里的 `design.md` / `spec §N.N` = `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` 及其章节/编号条目；
`计划` = `docs/superpowers/plans/2026-10-18-frontend-visual-phase0.md`；`计划 Task N Step M` 指其任务与步骤标题。
**引用 design.md 与计划一律写章节 / Task / Step 名，不写裸行号**——这两份文档本项目每周改动几十次，
`34181db` 入库时转录的 8 处行号指针**在提交当时就已指向别的条文**（评审 Critical-2 逐处核出）。
必须带行号时，同一格给出该号成立的 commit。`task-N-report.md` / `task-N-brief.md` / `progress.md` =
`.superpowers/sdd/2026-10-18-frontend-visual-phase0/` 下的同名文件——它们**未入 git**，行号无法用 commit 钉，
只许指到条目/段落标题（如 progress.md「进度」下"Task 11: 复核 Needs fixes"条目）。
**永不用 `HEAD` 当锚**：2026-10-19 一天内 HEAD 移动约 15 次，`HEAD:` 作为标签入库即失效。
**`证据 §N`**（2026-10-19 补，结构性洞）= `docs/visual-upgrade/evidence/phase0-measurements.md` 的章节锚。
Phase 0 的实测表已从**未入 git、且被 agent 追加改写**的 `task-4-report.md` / `task-4b-report.md` **逐字转录**进该文件，
它因此有 commit 可钉，而报告没有——报告行号属"永远无法钉"的一类（不是"改动太快"那一类）。
**凡台账引用的像素数、pixelmatch 计数、最大单通道色差、免检额算术、对比度比值，一律指 `证据 §N`**，
原报告名与节名保留为**原始出处**注记，不删。转录 ≠ 重测：`证据 §2` 逐条写明哪些数今天能由一条已入库命令重生成、
哪些目前**只有这份转录**（后者由计划 Task 4c Step 1g 补，本表不承诺其日期）。
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
   或 `证据 §N`（`docs/visual-upgrade/evidence/phase0-measurements.md`）。
   **指向未入 git 报告的裸行号不再算来源**（那一棵树无 commit 可钉，Phase 0 的数已整体转录进 `证据`）。
   没量过的一律写 `未实测（原因）`，**不许用看起来合理的数填空**。
2. **禁止拿"22 张基线没动"当作某个改动生效或没生效的证据。** 本仓已实测：单卡 `border-radius` 16→14px
   只产生 198 个像素差，低于现配置的 648 px 免检额 ⇒ 漏检（见 `P0a-THRESH-01`）。
   需要像素证据的改动必须出**定向元素截图对**，或者直写"未验证"。
3. 过渡类属性（`transition-duration` / `transition-timing-function`）不构成可见回归，不得写进「有效性」
   （spec §6.11）。
4. 每条"零 diff"必须附量程出处（spec §6.12）。
5. 暗色量程未被单独证明前，不得用浅色结论代证（spec §6.13）。
6. 锚点须各自证明"本页数据已到位"，通用守卫已被实测绕过（spec §6.14；本表 `P0a-GUARD-01`）。

## 表内书写规则（Phase 1–6 沿用，否则表会自己撑破）

- 每行必须正好 9 个竖线分隔出的字段。自检命令：
  `awk -F'|' '/^\|/ {print NF-2": "substr($0,1,40)}' docs/visual-upgrade/LEDGER.md`，Expected 每行都是 `9:`。
- **单元格内容里不要出现任何竖线字符**（含转义写法）。原因：`awk -F'|'` 是逐字符切分，
  反斜杠转义后的竖线**照样被当成列分隔符**，写了就让该行变成 10 或 11 列——
  本表初稿就因此把 `P0a-HARNESS-01` 撑成 11 列，改法是措辞绕开该字符（"或""加""配 … 使用"）。
- 同一行需要多条证据时，用句号或分号续写，不要另起物理行；一行 = 一个单元，跨行会让列数自检失效。
- **本表指向代码文件的行号，默认都是"当时成立"的历史坐标，不是当前坐标。** 除非同一格内写明 `@<sha>`，
  否则一律按 `git show <该读数所属提交>:<路径>` 去读，不要 `sed` 当前文件。
  **已知漂移（`a212fc1` 起）**：`visual-helpers.ts:344` → 现 `:350`；`:72` → 现 `:78`；
  `sensitivity.spec.ts:273-277` 的 `免检额` 输出行已上移。⇒ 引这些指针时先 `grep` 符号名再定行，
  或直接把指针改成符号名（本表 `P0a-THRESH-01` 起的新写入一律用符号名，不再新增裸行号）。

## 表

| ID | 改前（截图/数值） | 操作（file:line + 理由） | 改后（截图/数值） | 必要性 | 到位 | 有效性 | 结论 | 验证 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P0a-HARNESS-01 | 视觉基线 **0 张**；全库 `toHaveScreenshot` 命中 0。本会话复核：`git grep -c toHaveScreenshot 072743b -- frontend/src frontend/e2e` 无输出（退出码 1），`git ls-tree -r 072743b` 内无 `__snapshots__` 路径；072743b 是首个视觉提交 08b6676 的父提交。同口径记录见 spec §2.1"视觉回归基线 0"行（原引 `design.md:55` 行号指针已按 Critical-2 改表锚）⇒ "改前截图"无处可取 | 新增 `frontend/playwright.visual.ts` + `frontend/e2e/visual/`（config 与助手 08b6676，三角色 storageState 真登录夹具 daa60c7，基线入库 bf40e41）：独立 config 不与 9 个流程 spec 共用 projects/screenshot、时钟冻结、稳定态轮询、`testIgnore` 挡出流程套件。理由＝spec §5 要求每行有"改前"，此前为 0 | **22 张**，目录 `frontend/e2e/visual/__snapshots__/anchors.spec.ts/`。本会话复核：对该目录的 `.png` 用 `ls` 加 `wc -l` 计数 = 22 = 11 个锚点 × 明暗两主题。**不是 24**：计划与 brief 写的"12 锚点 × 明暗 = 24"未达成，`error-404` 明暗两张按缺陷 D-1 主动拒收（`证据 §7.1` 与 §7 表 #23/#24，原始出处 `task-4-report.md` 那张二十四行表的第 23、24 行），是有意不入库、不是漏跑 | 不建则 Phase 1–6 全部改动无痕可言：§5 的"改前"栏无处可取，回滚也无可对照底图 | 前置类，不套 §3 三条根因。命中 spec §2.1 的"安全网为 0 / 视觉基线 0"这一留痕缺口，非表层观感 | 机器判（**由报告转引，本任务未重跑套件**）：`anchors --repeat-each=2` → `44 passed`、22 张 × 2 次全部零 diff、`find test-results -name "*-diff.png" -o -name "*-actual.png"` 0 结果（`证据 §7.1`，原始出处 `task-4-report.md` §3 的 Step 4 输出块），收尾第三次全目录 31 passed 仍零 diff（`证据 §7.1`，原始出处同报告 §7 自查）。本会话自行复核的机器事实：22 张在册；`git status --porcelain frontend/e2e/visual/__snapshots__` 输出为**空** ⇒ 入库基线未被重生成，二次零 diff 的结论与当前文件一致 | 保留 | 本任务（docs-only）**只跑只读检查**：基线目录 `ls` 计数 = 22、`git status --porcelain …__snapshots__` 空、`git grep`/`git ls-tree` 反查 072743b 的 0 命中、`git log --diff-filter=A` 定位 08b6676。lint / typecheck:gate / test / build / size:gate / 视觉套件**本轮一律未跑**（与在飞实现者共用 :8080 后端与 5175 vite）；套件级结果一律标注来源（`证据 §N` 加原始报告节名）、不冒充本会话实测 |
| P0a-THRESH-01 | `SNAPSHOT_THRESHOLDS = { threshold: 0.2, maxDiffPixelRatio: 0.002 }`。旧配置**假绿**：注入 `body{filter:brightness(0.97)}` 的最大单通道色差只有 **8/255**，而 `threshold: 0.2` 的**逐像素闸**要求 Δ>52.8/255（`264.0×0.2`），pixelmatch **一像素都不计数**（真基线实测 `证据 §3.1` gross 行与要点 1、`证据 §3.3`，原始出处 `task-4b-report.md` §R2.2 表 A；非合成台的"220 px 被免检额吸收"见 `证据 §3.4`——那是 1440×1070 合成页的离线自检台读数，其归因已由 `6d1d9e1` 作废）。免检额 2592 px 是**另一道闸**，它只在 `threshold: 0` 下才开始杀人（radius 198 < 648）。**两道闸不得混为一谈**（spec §6 第 12 条）：旧配置下只调低 `maxDiffPixelRatio` 同样抓不到这个亮度注入 ⇒ 该配置下产出的任何"零 diff"一律作废（spec §6.12） | `frontend/e2e/visual/visual-helpers.ts:285` 收为 `{ threshold: 0, maxDiffPixelRatio: 0.0005 }`（本修复会话 `sed -n 285p` 读到该行**当时**的值，逐字一致；`0.0005` 不是现值，现值与其可重放命令见本行末列）。理由＝量程必须先有实测下限，否则 Phase 1–6 报出的每个"零 diff"只表示"这层看不见"（`证据 §6` 末条，原始出处 `task-4b-report.md` 第 1 轮 §8）。提交 e8def37 把阈值固化为单一来源；5675989 在 22 张真基线上量完**四档**注入（含 `noise` 对照档，`证据 §3.1`，原始出处 `task-4b-report.md` §R2.2 表 A）后定稿——"三档"只是 `e8def37` 当时的口径（其提交标题"用三档注入量出基线量程"），第四档 noise 后补（`证据 §3` 引言，原始出处同报告 §2.4）；16ada6c 只改注释归属 | `{0, 0.0005}`。免检额算术（22 张均为 1440×900 = 1,296,000 px，`证据 §4.1`，原始出处 `task-4b-report.md` 第 1 轮 §9.4 与 §R2.2 首段）：r=0.002 → **2592 px**，r=0.0005 → **648 px**，r=0 → **0 px**。**为什么收不到 0**：4 张基线有确定性日期漂移 110–321 px（见 `P0a-CLOCK-01` 与 `证据 §9`），321 px 会直接点红（`证据 §6` 逐档表与 §9，原始出处 `task-4b-report.md` §R2.4；**计数 321·295·110·111 出自被拒的中间档 `{0.02, 0}`，不是交付配置**，交付配置下它们被 648 吸收） | 不收紧则 Phase 1 起所有"零 diff"读数是假的——旧配置连整页亮度 3% 都漏检（真基线逐像素闸计数为 0，`证据 §3.1` gross 行的 t=0.2 列），等于没有量程 | 前置类（证据前提），不套 §3；但它决定 §3.1 层次失效那类改动（阴影 alpha）日后能否被证明 | 机器判，**量程边界与最大单通道色差必须和读数一起写出**（spec §6 第 15 条①）：阴影类改动（`--sh-l1` alpha 0.05/0.06→0.12/0.13）在 t=0 下报 55,903 / 21,059 px = 4.31% / 1.62%，是 648 免检额的 **32~86 倍** ⇒ 本配置**可见**（`证据 §3.2` shadow 行，原始出处 `task-4b-report.md` §R2.7 第 1 条）；单卡 `radius` 16→14px 只有 **198 px < 648 px** ⇒ **仍检不出**（198 对 648 的出处是 `证据 §3.2` radius 行与 §6 的红绿归属条，代码侧另见 `visual-helpers.ts:262` 注释；本行原引的 `task-4b-report.md:410` 与 `:440` 写的是 `198 < 2592`，那是旧档 2592 的比较、属另一比较，已更正）。四档实测的最大单通道色差一并入档（`证据 §3.3` 整表，原始出处 `task-4b-report.md` §R2.2 表 A）：noise 0/255、gross **8/255**、shadow **20/255**（5 卡页）与 **12/255**（1 卡页）、radius **19/255**（5 卡页）与 **18/255**（1 卡页）。结论：Phase 2（圆角阶）的整页像素证据**目前不成立**（spec §6.12"Phase 2 不得以整页像素 diff 作有效性证据"段）。**读数不可重生成**：取证脚本 `frontend/probe-analysis.cjs` 用完即删、从未入库（`证据 §1` 事实 2 与 §2 判定表，原始出处 `task-4b-report.md` §R2.1 末段与 §R2.8；本会话复核 `git log --all -- frontend/probe-analysis.cjs frontend/probe-harness` 无输出），交付的 `sensitivity.spec.ts:273-277` 只 `console.log` 免检额、既不读计数也算不出单通道色差 ⇒ 由计划 Task 4c Step 1g 补"每个被引用的数一条命令重生成" | 保留 | 本修复会话（只读）：`sed -n 285p` 读到该行**当时**是 `{0, 0.0005}` ⇒ 本行记的是**该行入库时的交付口径**，不是现值（行号与数值都只成立于那次读取，按本表书写规则属历史坐标）。现值走代码与断言两个入口且可重放：`grep -n 'export const SNAPSHOT_THRESHOLDS' frontend/e2e/visual/visual-helpers.ts` ⇒ `{ threshold: 0, maxDiffPixelRatio: 0.0001 }`（收紧动作入库于 commit `ed7df1c`；该常量行其后随文件增长下移，本表按新规则不再给它的行号），同目录 `helpers.spec.ts` 的「量程常量钉死，改动必须显式决策」一条以 `toEqual` 钉住它，改阈值必须显式决策；`task-4b-report.md` 的 87 / 115 / 125 / 284 / 302 / 389 / 391 / 393 / 400-405 / 408 / 410 / 422 / 440 / 489 / 490 / 498 / 526 各引用行逐条打开核对（`:115,:125` 现只作为**被作废归因**的证据保留；这些行号只成立于未入 git 报告的当日读取，本行的数已转录进 `证据 §3`–`§6`，逐指针的对照见 `证据 §11`，此后的引用走章节锚）；免检额与倍数算术自核（`node -e`：264.0×0.2=52.8、21059÷648≈32.5、55903÷648≈86.3）。**四档 ladder 与整表 anchors 本任务未重跑**（禁用项见 `P0a-HARNESS-01` 验证栏），本行所有像素读数都是开行核对、非本会话重测 |
| P0a-GUARD-01 | 图谱锚点走"列表首项"默认路径 ⇒ 渲染空态。旧 `student-knowledge-graph-dark.png` 入库的正是**空态**，而它与真图谱只差 **`channel>32` 口径 4325 px（0.33%）**；**pixelmatch 计数未量（取证脚本已删、不可重生成）**——旧配置判"相同"靠的是**逐像素闸计数为 0**（同 `P0a-THRESH-01` 的两道闸机制，spec §6.12），不是免检额吸收 ⇒ **这张暗色基线一直在假绿**，连 `--update-snapshots` 都拒绝重写它（要删文件才逼得出来），`证据 §7.2` 末段，原始出处 `task-4-report.md` §F-5 的 harness 判据条。空态页能通过全部四条通用断言（`anchors.spec.ts:35-37` 自陈：占位图非空壳、无 `.error-boundary`、`data-theme` 写对、URL 不在 /403 或 /503） | commit f568130 两件事。① 锚点改指真有数据的课：`frontend/e2e/visual/visual-helpers.ts:344` = `/student/knowledge-graph?courseId=4001`（本会话实测行号；选课依据是逐课实测：38 门课里 4001 有 8 节点 10 边，列表首项 4060 与 4047 等是 0 节点 0 边，`:335-340`）。② 加三道绘制守卫 `assertGraphPainted()`：`anchors.spec.ts:51` canvas 已挂载、`:55` 角标节点数 > 0、`:70` 画布非透明像素 ≥ 300（常量 `GRAPH_MIN_PAINTED_PX` 在 `:30`），调用点 `:168` 落在截图**之前**。理由＝spec §6.14 | 两张图谱基线重生成并逐张人工看过：画布非透明像素 **6051**、角标"8 个知识点"、10 条边 + 8 组文字标签可见（`证据 §7.2` 与 §7 表 #17，原始出处 `task-4-report.md` §6 与 §F-1，与 `anchors.spec.ts:26` 的自陈互校一致） | 不加守卫则一个空态页能通过全部既有断言并成为 Phase 1–6 的对照底图——**这是实测出来的，不是假想**（`证据 §7.2`） | 前置类。命中 spec §6.14"锚点须各自证明数据已到位"；把判据从"页面渲染了"升级为"这一页的核心内容真画出来了"，不是表层涂抹 | 机器判：`GRAPH_MIN_PAINTED_PX = 300` 是"完全没画"的哨兵（实测参照 6051，差约 20 倍，`anchors.spec.ts:24-30`），不是质量门槛。RED 已验：把路径改回不带 courseId，明暗两条都报 `图谱锚点截到的是空态：画布没渲染出来`，而不是"通过但图是空的"（`证据 §7.2` 末段，原始出处 `task-4-report.md` §F-1 的自守卫条）。**残余**：守卫只到"有墨/没墨"这一层，节点圆点仍不画 → 缺陷 D-9，不在本行有效性范围内 | 保留 | 本会话复核（只读）：`awk NR=20..80 anchors.spec.ts` 拿到守卫真实行号 51 / 55 / 70 与常量 30、调用点 168；`grep -n 4001` 确认锚点在 `visual-helpers.ts:344`；`task-4-report.md` 的 267 / 336-348 / 436-440 各引用行逐行打开核对（当日读取，报告未入 git；本行的数已转录进 `证据 §7.2`，对照见 `证据 §11`）。**注意**：报告里的旧行号（`anchors.spec.ts:29,48-70`、`:143`、`visual-helpers.ts:284`）已因文件增长过期，本行一律采用本会话重测的行号。视觉套件未重跑 |
| P0a-CLOCK-01 | `freezeClock` 用单步 `page.clock.install({ time })`——本机实测**仍按真实速率前进**（时钟根本没冻住）。且计划原先写的 `pause()` 在**已安装的** Playwright 1.63 不存在：`frontend/package.json:43` 声明的是 `^1.62.0`，1.63.0 为**已安装版本**（`node_modules/@playwright/test/package.json`，本修复会话读到）；该版本 Clock 接口（已装 `playwright-core/types/types.d.ts:20450-20581`，整接口无 `pause` 成员——原先引 `:20466-20480` 撑不住、由 `7da1233` 引用核验改定）只导出 install / fastForward / pauseAt / resume / runFor / setFixedTime / setSystemTime（`visual-helpers.ts:63-64` 自陈一致） | commit 9c73f65 改为两步：`frontend/e2e/visual/visual-helpers.ts:72` `page.clock.install({ time: CLOCK_EPOCH })` + `:73` `page.clock.pauseAt(CLOCK_EPOCH + CLOCK_WARMUP_MS)`（本会话读到这两行）。理由＝基线要可比就必须在同一页面时间上采样；`pauseAt` 正是"推进到该刻即停"的那一个（`:65`），实测之后页面 `Date.now()` 差值恒为 0 | 截图时刻由"经验同刻"改成**预算制常量断言**（commit f568130）：`frontend/e2e/visual/anchors.spec.ts:144` `expect(await page.evaluate(() => Date.now())).toBe(SNAPSHOT_PAGE_TIME_MS)`（本会话实测行号）。总额常量在 `visual-helpers.ts:31` 拆开：epoch + pauseAt(5000) + runFor(5000) + 轮询预算(1200) + 帧数沉降(3200) | 不冻时钟则 CountUp / 入场动画停在随机帧，且解析 courseId 会多走一次列表导航、只有 23/24 落在同一页面时间上（`anchors.spec.ts:127-129` 自陈；括注：24 为计划口径 12 锚点×明暗，实际入库 22，见 `P0a-HARNESS-01`）⇒ 基线之间根本不可比 | 前置类。命中留痕缺口的"时间维"，与 §3 三条根因无关 | 机器判：`:144` 的常量断言就是"多 settle 一次直接判红"的探针（`anchors.spec.ts:139-144`）；配合 `--repeat-each=2` 的 44 passed 零 diff（`证据 §7.1`，原始出处 `task-4-report.md` §3 的 Step 4）。**已知未闭**：4 张基线（student-dashboard 明/暗、admin-governance-analytics 明/暗）与改动前代码的新截图仍**确定性**差 **110–321 px**（逐张计数与配置归属见 `证据 §9`：321、295、110、111 四张是漂移、第 5 红的 13 px 是抖动；这组计数出自**被拒的中间档 `{0.02, 0}`**，交付配置 `{0, 0.0005}` 下它们被 648 免检额吸收，而同一对图的**逐字节差是 618**——两个数不矛盾，差额是 pixelmatch 的抗锯齿分类器），漂移点全在图表时间轴标签行（y 537..544 / y 696..703）、标签整组往后挪一天（`visual-helpers.ts:267-270`），根因 `frontend/src/composables/useAnalytics.ts:535` 把后端返回的 `d.date` 直接铺进 x 轴 ⇒ **`freezeClock` 只伪造浏览器时钟，伪造不了后端返回值**（`:271-272`）。跨次**产物** md5 一致 ⇒ 只确证**确定性**（两次都同样差），**不确证因果**；日期漂移的因果证明在本行后半：后端 `useAnalytics.ts:535` 把 `d.date` 直接铺进 x 轴，加上漂移带几何（标签行 y 537..544 与 y 696..703、标签整组平移一天），两者均已在案（`证据 §9` 的 md5 表与其前一段，原始出处 `task-4b-report.md` §R2.3 与 §R2.5 那句"两次独立跑测截出来的图 md5 完全一致"）。**引用更正（本会话开行核对，读取日 2026-10-19）**：本行原引 `task-4b-report.md:444-446` 撑不住——`:446` 量的是 **`visual-helpers.ts` 配置文件自己的 md5**，用途是把读数归属到某一版阈值（"没串台"），不是产物的确定性；跨次产物 md5 在 `:430` 与 `:455`（三处行号只成立于未入 git 报告的当日读取）。那条 config md5 `242981ec…` 本会话逐版重算**在任何已入库版本上都对不上**：`git show` 加 `md5sum` 得 e8def37 为 eeb47e1e…、5675989 为 def1d3d2…、16ada6c 与 777d9bd 与 HEAD 均为 76363460…（三者同值 ⇒ 该文件自 16ada6c 起未再改动），所以 `242981ec…` 标识的是一个**从未入库的工作树中间态**（§R2bis.3 记着并发代理在 00:43 改过这文件），它不复现属预期、不构成反证，但它自己也**永远无法再钉**；行尾不是原因（`.gitattributes` 全局 `eol=lf`，本会话实测三份均零 CR） | **保留（带已知残余，转 Task 4c）**——不许写成已解决：r 收到 0 的前提是先把这 321 px 日期漂移消掉，再把 `maxDiffPixelRatio` 推进到 0.0001 且 22 张仍零 diff（spec §6.12"先把漂移消掉、再推进 0.0001"那段；行号指针已按 Critical-2 改章节锚） | 本会话复核（只读）：`awk` 读 `visual-helpers.ts:58-73` 确认两步写法与 1.63 无 `pause()` 的自陈、读 `anchors.spec.ts:127-144` 确认常量断言行号、读 `useAnalytics.ts:528-542` 确认 `:535` 就是铺后端日期的那一行；`git show 9c73f65 --stat` 未跑，仅按 `git log --oneline -- frontend/e2e/visual/` 归属提交。时钟与套件均未重跑 |
| P0b-MODAL-01 | `ui/Modal` 无 Teleport。**以 `git show 37b9414:frontend/src/components/ui/Modal.vue` 为准**（37b9414 是改动该文件的提交，且本修复会话实测 `git diff --stat 37b9414 5c221c8 -- …Modal.vue` 输出为空 ⇒ 它与本行入库时 HEAD 读出的版本一致；评审裁定 `HEAD` 永不许当锚，本行原有的 `git show HEAD:` 已全部改为 SHA 写法）：全文 Teleport **零命中**，模板根节点是 `<div v-if="open" class="overlay">`、位于**第 44 行**——行号只成立于 `37b9414` 版本，不得迁移到其他版本。brief 写的 `Modal.vue:19` 已过期——`37b9414` 的第 19 行是 `defineProps` 的收尾 `})`。**本行一律以 `git show 37b9414:` 为准、未读工作树版本**：本行入库时会话 `git status --porcelain` 实测在飞的是 `Drawer.vue` 与 `Drawer.test.ts`（均 `M`）另加两个未跟踪的 `*.drawerGate.test.ts`（那两个文件后由 `8b827aa` 入库），并非任务书所说的 Modal 两文件；为避免与并发写者抢同一文件，读 Modal 也走 `git show 37b9414:` | commit `c869ad9`：模板收成**单个** `<Teleport to="body" :disabled="!toBody">`，不并列两条分支、不复制 DOM ⇒ 按 brief 核定**不抽** `ModalPanel.vue`；新增 `teleported` prop（`{ type: Boolean, default: undefined }`）。尺寸档 `wide`/`xwide`/`narrow` 由「attribute 契约」改为声明式 props，`sizeClass` computed 与 props 声明**同一 commit** 落地（brief Step 3 硬要求）。另补一条 brief 未预见但实测存在的缺口：根变成 Teleport 后 Vue **不再自动贴 fall-through attrs**（探针：`attrs:{'data-test':'x', class:'extra-cls'}` 时 `.overlay` 只剩 `class="overlay"`、`.modal` 只剩 `class="modal"`，两处全丢），故显式 `v-bind="$attrs"` + `defineOptions({ inheritAttrs: false })` 把落点**维持回 `.overlay`**（brief Step 3 括注的"落到 `.modal` 上"未采纳——那会把 `class` 落点挪走，属行为变更）。理由＝"必要性"栏的包含块实测已成，且 Phase 4 的 65 处 `el-dialog` 需要 `append-to-body` 的等价通道 | 模板里的 `<Teleport to="body" :disabled="!toBody">` **元素 1 处**，判据命令为 `grep -c '<Teleport to=' frontend/src/components/ui/Modal.vue` = 1（闭合标签 `grep -c '</Teleport>'` 同为 1）。⚠️ 别用 `grep -c Teleport` 复现本格：它数的是**含该词的行**，修复轮之前是 5、把 inheritAttrs 理由重写后是 13，两个都不是元素数（本行原写"命中 1 处"却给不出可复现命令，属本表要防的那类不可检陈述）；`src/components/ui/` **10 files / 97 tests 全绿**（该目录在 Task 8 前是 **93**，**+4 恰为新增 `Modal.teleport.test.ts` 的四条**；全包同时从 163 files/1737 tests 变 164/1741，两处 +4 互相咬合。本会话早期版本此处误写"96、+1"，已由只读评审用静态计数否证并改正；计数可重放：`git show 6d2379d:frontend/src/components/ui/__tests__/` 与该目录逐文件数 `it`/`it.each` 展开，或跑 `npx vitest run src/components/ui/`）。`npm run typecheck:gate` **402 / 402 持平、本任务新增 0**（棘轮口径，不写"typecheck 全绿"）；`npm run lint` **0 error / 0 warning**。查询路径与 Teleport **同批原子迁移**（不拆两次提交）：compat 10 条 + vmodel 11 条里的 `wrapper.find` / `get` 取 `.modal` / `.overlay` 全部改走 `document.body.querySelector`；实现过程中一次性跑出 **15 条红**（compat 8 + vmodel 7），逐条核为"取不到节点"而非"断言意图被推翻"，迁移后逐条回绿、判定对象一条未松 | **spec §2.6 原文只测三例就判"当前未爆"，本任务把 13 处逐个核完，实测有两处今日就生效**（该节已据本次实测由 commit `cba970a` 改判为"现存缺陷"，旧文不再成立）。① `views/student/components/SdCourseGrid.vue:111` 的 `<Modal narrow>` 就在该组件**模板根** `<section class="l1-card animate-in d2">`（:2）之内——`.animate-in` 是 `animation: rise-in 0.42s var(--ease-out) both`（`design-system.css:1358-1359`，`rise-in` 的 `to` 帧 `transform: translateY(0)` 在 :1353-1355）；`both` 是**填充**，动画跑完这一帧永久留在计算值里，而 `translateY(0)` 不等于 `none` ⇒ 成为 fixed 遮罩的包含块；`.l1-card` 还带 `overflow: hidden`（`design-system.css:62`）⇒ 遮罩被**裁进卡片**，症状就是"弹窗缩在卡片中间"。② `views/teacher/tools/KnowledgeManagement.vue:96` 的 `<ExcelImportDialog>`（其模板根即 `<Modal wide>`，`components/ui/ExcelImportDialog.vue:2`）挂在页面根 `<div class="fade-in">`（:2）之下——`.fade-in` 是 `animation: fadeIn 0.35s cubic-bezier(0.4, 0, 0.2, 1) forwards`（`index.scss:506-508`，`to` 帧同为 `translateY(0)`，:500-503）⇒ 同一机制，遮罩铺不满视口、盖不住侧栏与顶栏。这两处**都不在 §2.6 当年测的三例里**。其余 11 处逐个核为清：`Courses.vue:221`、`teacher/Dashboard.vue:129`、`Notifications.vue:130` 与 §2.6 一致是兄弟级；`Um{Create,Delete,Detail,Reset}Dialog` 与 `UmImportDialog` 都在 `UserManagement.vue:80` 的 `</section>` **之后**，该页根是无类 `<div>`；`CourseChapters.vue:131`、`CourseSubmissions.vue:164/224`、`NotificationManagement.vue:31`、`ExcelImportDialog` 的另两个父站点均在零 `animate-in`/根级 `fade-in` 的文件或兄弟位。`will-change` 全仓 **0 处**。"存在但被覆盖"与"这条赢"的区分：`grep` transform、filter、backdrop-filter 三类属性带 `!important` 者 **0 命中**，且 `.animate-in` 只在 `design-system.css:1358` 定义、`.fade-in` 只在 `index.scss:506` 定义，无第二份竞争 ⇒ 两条都**赢** | 组件级到位：Modal 仍是**一个组件文件**，13 处使用（12 文件）的 `<Modal …>` 写法与业务页**零改动**，可由 `git show c869ad9 --stat` 直接核。**宽度档不等价于像素到位**：jsdom 不应用 `<style scoped>`（Task 8 起样式块位置随 Teleport 包裹而后移，故不按行号引），vitest 只证明"class 挂上了"，证明不了"宽度按 CSS 真的生效"——补这个缺口的只有 Playwright 基线（brief Step 3 已承认此边界）。另记一条**无人使用的通道**：`xwide`（1060px）在 13 个调用点命中 **0**（档位分布 wide 5、narrow 7、裸 1、xwide 0），本批不删（删除属功能面变更、超出"只改样式"边界），但它只被测试保护、不被使用保护 ⇒ Phase 4 收 `el-dialog` 时应明确启用或废弃，别把"xwide 绿了"读成"13 个调用点都确认过" | 机器判，变异自证且**实际跑过**（原文见 `task-8-report.md` §5）：M1 把 `:class="sizeClass"` 改成 `:class="{}"` ⇒ compat 的 wide/xwide/narrow **三条红**（`Tests 3 failed / 7 passed`），`默认宽度` 那条仍绿（它断言的是"不含"，符合预期）；M2 把 `:disabled="!toBody"` 焊成 `:disabled="false"` ⇒ teleport「teleported=false 退回原位」**红**；M3 删 `teleported` 的 `default: undefined` ⇒ teleport「遮罩渲染到 body 下」**红**（Boolean 缺省被 cast 成 false，"默认 teleport"的方向正好写反）；M5 只改 props 声明、模板退回读 `$attrs.wide`（brief 点名的静默塌回 640px 形状）⇒ **三条红**，且同一次探针打出 `wrapper.props('wide')` 为 `true` 而渲染出的 class 只有 `modal` ⇒ **实证"改断 prop 值"会假绿**，故那三条只许断言 `.modal` 渲染出的 class 列表，本任务一条都没换成 prop 断言。M4 是**反例、必须记**：把 `wide` 的类型数组翻成 `[String, Boolean]` ⇒ **十条全绿、没红**，这推翻了我起初写进 `Modal.vue` 注释的"顺序翻转会塌档"，注释已按实测改弱重写——承重的是 `sizeClass` 里 `''` 与 `true` 两支并列，**不是数组顺序** | 保留。⚠️ 同时记一条**盲区**，不许把本行读成"基线已覆盖"：`design-system.css:1379-1382` 的 `@media (prefers-reduced-motion: reduce) { .animate-in, .fade-in { animation: none } }` 会把两条陷阱的 transform 打回 `none` ⇒ **包含块在 reduce 下不存在**，而视觉基线正是在 `reducedMotion: 'reduce'` + `animations: 'disabled'` 下采集的（spec §6.11）。⇒ 24 张（现 22 张）基线**在结构上拍不到这个缺陷**，它只在默认 `no-preference` 的真实浏览器里出现；本行唯一的保险是 `Modal.teleport.test.ts` 的结构断言。另记一条会**新造**包含块的东西：`AppShell.vue:208-222` 给被路由的组件根挂 `.page-enter-active { transition: … transform 0.22s }` 与 `.page-enter-from { transform: translateY(8px) }` ⇒ 导航后那 **220ms** 内整页内容是一个临时包含块，"进页面就自动弹"的 Modal 会被困一次；过渡结束摘类后回 `none` | 本会话实测、均可重放：`npx vitest run src/components/ui/`（10 files / 97 tests）、`npm run lint`（0/0）、`npm run typecheck:gate`（402/402 持平）、`npm run test`（结果见 `task-8-report.md` §6，跑没跑如实记）、变异 M1–M5 的实际报错行数。祖先链结论由一次性标签栈探针得出并经**人工逐例复核修正**，该探针**未入库**（不在 Task 8 允许写的文件清单内）⇒ 复现走两处 file:line 直读模板与 CSS，或按 spec §5"证据纪律 2"在 `no-preference` 下对 `SdCourseGrid` 的"加入课程"出定向截图对。`Modal.vmodel.test.ts` 里"brief 从未授权 `=== true` 守卫"那句不实陈述已撤回（`b4287ef`）：`=== true` 逐字写在 task-7 brief 的 Step 3 代码里，是控制器裁定作废的，不是原文从未有过 |
| P0b-MODAL-02 | spec §9.2 未验证项第 2 条原样在案：Phase 0b 的 `$el` 可达性是焦点接入方案成立的前提，当时**未经实测**（原引 `design.md:392-393` 已随 Critical-2 改为章节锚——该号在入库时指向别处）。若不可达，Modal 的焦点接入须改为组件自持引用。另记本行入库后被评审 I-3 抓出的第二次误述：`Modal.vmodel.test.ts` 与 `helpers/trackMount.ts` 把根上的 `nodeType 8` 说成"关闭态的 v-if 占位注释、Teleport 的锚点没有顶掉它"，把两回事说成了一回事，已按下列实测改写 | 修复轮 commit `acecd4a`（**不改行为**：只改注释、并在既有用例内部加断言，用例总数不变）——`Modal.vmodel.test.ts` 的「modelValue=false 时不渲染遮罩」与「modelValue=true 时渲染」两条各钉 `wrapper.element.nodeType` 与该注释的 `data`，开态另钉 `$el` 的父节点不是 body、`.overlay` 的父节点是 body；`helpers/trackMount.ts` 的 `element: Node` 论证改口径；`Modal.vue` 的 `teleported` jsdoc 记下 disabled 翻转会搬节点这条交接事实。本行原引的 `计划 1581-1583` 行号早已失效，分支定义按 Critical-2 走 Task/Step 名（计划 Task 9 Step 2 的 3-A 与 3-B）。Vue 侧依据按台账新规则用符号名锚而非裸行号（本机 vue 3.5.43 的 `runtime-core.cjs.js`：`TeleportImpl.process` 内 `n2.el = createComment('teleport start')`，组件挂载尾部 `initialVNode.el = subTree.el`）。理由＝计划 Task 9 的 3-A/3-B 分叉需要一个实测数，不是静态推断 | 实测（VTU 的 `wrapper.element` 与调用方模板 ref 的 `$refs.dlg.$el` 两条通道同值；jsdom，`attachTo: document.body`）：① 开启态 `$el` 是 `#comment`、nodeType 8、`data` 等于 `teleport start`，其父节点是调用方容器而不是 body；`.overlay` 的父节点是 `document.body` ⇒ 二者既不是同一节点也不在同一父节点下。② 关闭态 `$el` **仍是同一条** `teleport start` 注释（nodeType 8、isConnected 为真），`.overlay` 取不到；`v-if` 的占位注释确实生成了，但它挂在 Teleport 的 target 里 ⇒ body 的直接子节点多一条 `v-if` 注释。⇒ 开与关的差别在"东西住在哪儿"，不在"根是不是注释"。③ `teleported=false` 时遮罩回到调用方 DOM 子树内，入库判据是既有用例「teleported=false 时退回原位」的 `.host-box .overlay` 命中；探针另见原位子节点顺序为 teleport start、div.overlay、teleport end，即该形态下 `el.nextSibling` 才是面板（后半句只有当轮临时探针、未入库）。④ Teleport 之内的模板 ref 拿得到真元素（探针：`instanceof Element` 为真、父节点是 body、且不在 wrapper 的元素树内 ⇒ 计划 3-B 那条"自持 `rootRef` 加 `defineExpose`"的通道可行；此读数同样未入库，须由 Task 9 的 `Modal.focus.test.ts` 接成入库断言）。⑤ 计划 Task 9 Step 1 探针里那句 `expect(el?.nodeType).toBe(1)` 今天必红（实测 8）——`700699c` 只把它标成待实测，本行给的就是那个实测值 | 不记的后果：Task 9 若照 3-A（沿用与 `el-dialog` 同构的 `dialogRef.$el` 口径）实现，拿到的永远是锚点注释，焦点陷阱要么挂空、要么只能退回 body 查询；而 ⑤ 那次红若不预先记明，执行者会把它读成实现故障，最省事的出路就是删断言 | 前置类（证据前提），不套 §3 三条根因。它把 spec §9.2 第 2 条从"未经实测"消解为实测签字，并改正一条已入库注释的事实错误 | 机器判：`npx vitest run src/components/ui/__tests__/Modal.vmodel.test.ts` 11 条通过，含新钉的两条 `$el` 身份断言。变异实测（当轮实跑）：把 `Modal.vue` 模板根的 `<Teleport>` 摘掉而 `inheritAttrs` 保持 false ⇒ 关态那条报 `expected 'v-if' to be 'teleport start'`、开态那条报 `expected 1 to be 8`、teleport 文件的「遮罩渲染到 body 下」报父节点不等 body——三条新断言全部可证伪，不是空断言；另一次在同一个**已摘掉 `<Teleport>` 的元素根世界**里再翻 `inheritAttrs: true` ⇒ `.overlay` 的 `extra-cls` 计数由 1 变 2 转红，这条只证 `Modal.vue` 注释里残余价值 (b) 那句"根不再是 Teleport 才真的落两遍"，它**不**否证 Teleport 世界里的任何主张；真正把 `P0b-MODAL-01` 曾据以立论的"inheritAttrs 防二次套用"旧理由从依据降为保险的，是另一次 `<Teleport>` 原样保留、只翻 `inheritAttrs: true` 的实测 ⇒ `npx vitest run src/components/ui/` 仍 10 files / 97 tests 一条不红（**该读数只成立于 `2fc628d` 之前**——那之后同一变异会红一条，见本表 `P0b-MODAL-03`；修复轮 2 重跑复现；同口径亦见 commit `fc47493` 的提交信息与 `Modal.vue` 的 inheritAttrs 注释段）——那个世界里根本没有可抑制的运行时落点，旧理由连被证伪的对象都没有（现文已按实测改为"保险不是承重"） | 保留。分支判定仍留给 Task 9：本行答的是"`$el` 能不能当面板"（不能），3-A 死、3-B 可行是实测方向而非推断；Task 9 仍须按自己的规矩用 `Modal.focus.test.ts` 重签一次，并把 ③ 后半句与 ④ 这两条未入库探针读数接成入库断言 | 本会话实跑：`npx vitest run src/components/ui/` = 10 files / 97 tests（**本行所有 97 读数一律限 `2fc628d` 之前，其后该目录为 98，前向指针见 `P0b-MODAL-03`**；用例数与本行修复前一致，新事实全部加在原用例内部 ⇒ 不动 `P0b-MODAL-01` 的 93 对 97 加四条口径）、`npm run lint` 无输出、`npm run typecheck:gate` 402 对 402 持平。探针 stdout 原文见 `task-8-report.md` 的修复轮一节（该报告未入 git，所以 ③④ 的可重放性只到"照命令形状重跑"这一层：挂一个把子节点 Teleport 到 body 的宿主组件再读 `$refs`）。`npm run test` 与视觉套件本轮按要求未跑（全包门由控制器持有，18:07 读数 1741） |
| P0b-GUARD-01 | 焦点覆盖率护栏 `frontend/src/composables/__tests__/useDialogFocus.coverage.test.ts` 以字面量 `<el-dialog` 为扫描键，并硬写 ≥ 50 的防空转下限（spec §2.1 表"弹层焦点覆盖率护栏"行 + §9.5 第 5 条；原引 `design.md:56`、`design.md:399-402` 两个行号指针经评审核实入库时即失效，已按 Critical-2 改章节锚。本修复会话复核该测试现文：扫描键断言在第 37-43 行、下限断言 `toBeGreaterThanOrEqual(50)` 在第 49 行）。它"为永远别有人漏接线而设计，不是为弹层可换实现而设计"⇒ Phase 4 按原样迁移，第一批就会红；若为了让它绿而删断言或调低阈值，等于无声拆掉 D2-7 的机械保障 | 待回填（由 Task 13 替换）：改写为**弹层实现无关**，阈值改为受控期望总数。**地板值以计划现文为准——台账此前记的 60 / 54 是草稿值（Important-3）**：计划 Task 13 题注按实测定 `EXPECTED_OVERLAY_COMPONENT_MIN = 73`（弹层组件文件数）与 `EXPECTED_OVERLAY_MIN = 82`（开标签总数，构成 `el-dialog` 63 + `Modal` 13 + `Drawer` 2 + `el-drawer` 4），且**只升不降、开工时须重测**；同任务 Step 4 示例代码里仍抄着草稿 60 / 54，那是代码占位不是取值依据。台账若留草稿值，Phase 4 就按低于实测的地板设防——开标签口径 60 对 82、组件口径 54 对 73，弹层被静默删掉一截护栏仍可能绿。并把**计划 Task 13 Step 6 破坏性实验**的结论写进"有效性"栏（原指针"计划 2272"失效：它当时落在别处，现文该引文在 Task 13 Step 7 回填条——"这是本阶段唯一能证明"护栏没被改废"的证据"） | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）。不改的后果：Phase 4 的 65 处结构收敛里，第一批迁移就会撞上假红 → 最省事的出路是放宽护栏，而那正是 §9.5 明令禁止的 | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）：只允许写破坏性实验的实际结果（把守卫打红的那次运行与它的报错文本），不得写"护栏已覆盖"这类无对偶实验的断言 | 待回填（由 Task 13 替换） | 待回填（由 Task 13 替换）。本任务未跑 `npm run test`。已知顺序约束：Task 13 必须排在 Task 11 之后（见下文"结构性前置"焦点覆盖缺口条） |
| P0b-DRAWER-01 | 旧 `ui/Drawer` 不声明 props：`:model-value` 落进 `$attrs` ⇒ `.drawer-overlay` 的 0.38 黑幕 + 面板常驻。实证调用点 `frontend/src/views/admin/system/NotificationManagement.vue:47`（`<NmPublishDrawer v-model="drawerOpen">`，而同文件 `:84` 写的是 `drawerOpen = ref(false)`）⇒ 传进去的就是 `modelValue:false`，旧 Drawer 仍渲染整屏遮罩（progress.md「进度」下"Task 11 的两条决策与一条既有缺陷（待评审确认）"段把它记为该文件**当时提交态**的既有产品缺陷，"Task 11: 复核 Needs fixes"条目的评审回源码证实——progress.md 未入 git 且控制器持续追加，原 `progress.md:433-434 / 523-525` 行号指针已按 Critical-2 改为条目锚） | commit **999862c**（只含 `frontend/src/components/ui/Drawer.vue` + `Drawer.test.ts`）：对齐 v-model / Teleport / 焦点 / 滚动锁 / Esc / `@closed` 通道，为 Phase 4 收 4 处 `el-drawer` 铺路。**行号钉死 commit**：`git show 999862c:frontend/src/components/ui/Drawer.vue` 实测 `<Teleport to="body" :disabled="teleported === false">` 在 `:131`、`role="dialog"` 在 `:137`、`aria-modal="true"` 在 `:138`、`z-index: var(--z-drawer)` 在 `:182`（本修复会话重跑 `git grep -n` 核对；这些号当时之所以能当"HEAD 行号"用，是因为本修复会话实测 `git diff --stat 999862c 34181db` 与 `999862c 5c221c8` 对该文件的输出均为空，而"HEAD 当锚"已被评审作废，且该文件其后又被 `8b827aa` 改动） | 4 处 `el-drawer` 使用点的能力通道打通（`task-11-report.md` 第 7 节，标题"那 4 个 el-drawer 现在能收敛吗"，段首即"能力层面已经打通"；该报告未入 git、只许指节标题，且它是**能力清单不是 Phase 0 的测量数**，故未转录进 `证据`，见 `证据 §10.13`）：`:model-value` / `v-model`（`TaskDrawer.vue:3` 用 v-model）、`append-to-body`→Teleport、`direction`→`placement`、`@closed`、Esc、滚动锁、焦点。**改后尚未定稿**：评审判 Needs fixes（4 Important + 6 Minor，来源为 progress.md"Task 11: 复核 Needs fixes"条目）；修复轮已落地 `8b827aa`（见本行有效性栏的状态更新），但尚未过评审复核 | 待回填（由 Task 11 修复轮替换）：Phase 4 要迁的 4 处 `el-drawer`（`EgEventsDrawer.vue:2`、`DscDetailDrawer.vue:2`、`TaskDrawer.vue:2`、`CdsDetailDrawer.vue:2`，本会话 grep 实测）缺任一条通道就会在迁移时静默丢行为 | 前置类，不套 §3；但抽屉贴边圆角复用 `--r-l1`（`Drawer.vue@999862c:202-204`，本修复会话重读该版本核对），兑现度直接受本行 I-2 影响 | 未实测（本轮不填机器数值；像素级证据按"证据纪律 2"须由 Task 11 修复轮复评审补定向截图对）。**入库时确证的两条缺口，现况已更新——修复轮落地于 commit `8b827aa`，本修复会话用 `git show 8b827aa:frontend/src/components/ui/Drawer.vue` 逐条实测**：① 焦点陷阱缺失而 `aria-modal="true"` 已声明 ⇒ 语义与实现不一致；控制器裁定 R-1 = 撤下该属性、保留 `role="dialog"`，陷阱与 Modal 共用一套、归 Task 10 建一次（progress.md"Task 11: 复核 Needs fixes"条目"控制器裁定（两条我不让执行者自己选）"段）。**该裁定已落地**：`8b827aa` 版中 `aria-modal` 代码零命中（仅 `:24` 与 `:201` 两处撤回说明注释），`role="dialog"` 保留在该版 `:207`；**焦点陷阱本身仍未闭合，等 Task 10**。② 圆角只兑现一半：999862c 版 `.drawer` 无 `overflow: hidden`，页脚/主体的实底背景会把圆角盖成直角（评审 I-2，同一 progress.md 段落"另：I-2"）——**这条也由 `8b827aa` 补上**：该版 `.drawer` 在 `:269` 有 `overflow: hidden`，`:267-268` 与 `:277` 注释写明"圆角与裁剪成对、删一条等于删两条" | **需调**（评审 Needs fixes：4 Important + 6 Minor，来源为 progress.md"Task 11: 复核 Needs fixes"条目；修复轮部分落地 `8b827aa` 但未过评审复核，回填后按实测改判） | 本修复会话重跑的只读核对：`git show 999862c:…/Drawer.vue` 的 Teleport/role/aria-modal/z-index 四处行号；`git show 8b827aa:…/Drawer.vue` 的 aria-modal 零命中、`role="dialog"` 在 :207、`.drawer` `overflow: hidden` 在 :269、撤回注释在 :24 与 :201；`git show 8b827aa --stat` 确认该提交含 Drawer.vue、Drawer.test.ts 与两个 drawerGate 测试（与 `P0b-MODAL-01` 记录的"drawerGate 未跟踪"呼应——那两个文件已由此提交入库）。4 个 el-drawer 使用点为原会话实测。工作树 `Drawer.vue` 由并发实现者占用，**本轮未读、未改、未 stage**。npm 验证链一行未跑，故本行不写任何"全绿" |
| P0b-MODAL-03 | `Modal.vue` 注释把 `defineOptions({ inheritAttrs: false })` 的残余价值 (a) 写成"实测 `inheritAttrs: true` 时关闭态打出该警告"，但入库套件里**没有任何用例挂载"关闭态加透传属性"这个组合**：修复轮 2 复跑只翻 `inheritAttrs` 的变异，得 10 files / 97 tests 全绿、控制台连 `[Vue warn]` 一行都不出。那句"实测"当时只剩一份未入 git 的一次性探针背书，与已作废的 M8 属于同一种依据 | 控制器补钉（不改行为，只加用例）：`frontend/src/components/ui/__tests__/Modal.teleport.test.ts` 末尾新增「关闭态带透传属性时不打 fall-through 的 dev 警告」，用 `vi.spyOn(console, 'warn')` 收警告并断言 `Extraneous non-props attributes` 计数为 0；同条先钉关闭态确实**没有** `.overlay`，否则"没警告"可能只是压根没挂载。同时重写 `Modal.vue` 注释的 (a) 与"97 条一条不红"那段，使口径变成"关闭态已有入库面、开启态故意不钉"——开启态在两种设置下都不响（`v-bind="$attrs"` 已把 attrs 标成 accessed），钉上得到的是翻不红的断言。探针临时文件跑完即删 | 改前 97 全绿且翻 `inheritAttrs` 无一条可红；改后 `npx vitest run src/components/ui/` = 10 files / 98 tests 全绿。变异复跑（只把 `Modal.vue` 的 `inheritAttrs` 翻 true）= 1 failed，97 passed（共 98），红的正是新用例，报错原文含 `[Vue warn]: Extraneous non-props attributes (data-test, class) were passed to component but could not be automatically inherited because component renders fragment or text or teleport root nodes.`；还原后再跑回到 98 全绿 | (a) 是这行 `inheritAttrs: false` 今天**唯一**还站得住的机制性理由（(b) 属前瞻，不是当下机制）；不给它入库面，就等于把一句"实测"永远留在无法证伪的位置——本任务族已因这类依据翻过三次（虚构 EP 引文、机制洗白、M8）。代价：用例总数加一，本表与注释里所有"97"读数从此须按 commit 限定后再读 | 双向证据齐：正向 98 绿、反向只红新用例那一条（由本轮变异直接证明，不是推断）。**本格原稿还写着"排除了『因未挂载而假绿』这一条退路"，那句说过头了**——第 2 轮 scoped re-review 判它：只断言 `.overlay` 为 null，"组件压根没挂上"和"根不是 Teleport"两种假绿都排除不了。已按裁定把挂载证明补进用例（`nodeType` 为 8 且 `data` 为 `teleport start`，与 `Modal.vmodel.test.ts` 同一事实），并实跑对偶实验：摘掉模板根的 `<Teleport>` ⇒ 本用例报 `expected 1 to be 8` 转红（还原后 10 files / 98 tests 全绿），所以这条退路现在是真的堵住了 | 机器判且命令入库可重放：`cd frontend && npx vitest run src/components/ui/` 应 98 全绿；证伪面是把 `Modal.vue` 的 `defineOptions({ inheritAttrs: false })` 改成 true 再跑同一命令，应 1 failed 且红的就是这一条。视觉套件与 `npm run test` 本轮按要求未跑（全包门由控制器持有） | 保留。(a) 从"只有探针背书"升级为"有入库可证伪面"；Task 9/10 若再动 `inheritAttrs` 或模板根，这条会先响。同一轮还消掉两处指向失效：`Modal.vue` 注释里"97 条一条不红"补上"当时"限定，`P0a-THRESH-01` 操作列残留的第三处"现值"（`sed -n 285p` 读到该行现值）改为"当时的值"并指向该行末列的 `grep -n` 命令——THRESH-01 只有一行物理行，引用一律用列名不用行号，且本表从来没有独立的 4c 行 | 本会话实跑（两次读数都按原文记上，非转述）；工作树除本行、`Modal.vue`、`Modal.teleport.test.ts` 三处外无其它改动。修复轮 2 上报的"`P0a-THRESH-01` 验证 cell 约 153 行"与"`P0a-THRESH-01` 之后的 4c 行"两条指向均不成立，已按上列口径改写；探针 stdout 原文所在的 `task-8-report.md` 仍未入 git，所以本行的可重放性靠上面的命令而不是那份报告 |
| P0b-MODAL-04 | spec §9.2 第 2 条交给 Task 9 的三件事在开工时仍未签字：`P0b-MODAL-02` 的 ③ 后半句与 ④ 只有当轮一次性探针（stdout 在未入 git 的 `task-8-report.md`），而该行 ①/② 钉的是 VTU 的 `wrapper.element` 通道——**调用方模板 ref** 这一条（`useDialogFocus.ts:50` 真正读的 `dialogRef.value.$el`）在库里从来没有入库断言。开工基线本会话重跑：`cd frontend && npx vitest run src/components/ui/` = **10 files / 98 tests**（`P0b-MODAL-01`/`-02` 里的 97 读数按 `P0b-MODAL-03` 的限定只成立于 `2fc628d` 之前，不得当现值读） | commit `8218ee8`：新增 `frontend/src/components/ui/__tests__/Modal.focus.test.ts` 两条入库断言（**不动 `Modal.vue`**）另加本行。理由＝计划 Task 9 Step 2 要求分支判定由实测签字、不接受静态推断，而 Task 8 只量完了 `$el` 一侧。读数编号改用控制器口径 P-a/P-b/P-c：brief 的 ①/② 与本行所引 `P0b-MODAL-02` 的 ③/④ 撞名 | P-a 与 P-b 已入库（`npx vitest run src/components/ui/__tests__/Modal.focus.test.ts` = `Tests 2 passed (2)`，同一次目录跑为 11 files / 100 tests）。**P-a**：模板根是 `<Teleport>` 时，绑在**面板节点**上的对象 ref 拿到的就是 body 下那块真元素（`instanceof Element`，identity 等于 `document.body.querySelector` 的结果），且它与组件自己的根不是同一节点。**P-b**：调用方模板 ref 在尚无 expose 的 Modal 上读到的 `$el` 是 nodeType 8 的 `teleport start` 注释，`contains(overlay)` 为 false，注释上没有 `querySelector` ⇒ 原语 `useDialogFocus.ts:51` 那句 `typeof root.querySelector !== 'function'` 守卫**静默 return**（不报错、不红，焦点永远进不去）。⇒ **分支判定：3-A（沿用 `dialogRef.$el` 口径）死，3-B（Modal 自持 `rootRef`）采用**；机制依据是 `setRef` 里的 `refValue`（本机 vue 3.5.43 的 runtime-core.cjs.js:1770，函数体起始 :1751）对元素 vnode 取 `vnode.el`，而 SFC 里 `ref="rootRef"` 的编译产物就是对象 ref，与本探针同源，所以面板侧 `rootRef` 是 Modal 唯一够得着面板的通道 | 不签的后果：3-A 若照 brief Step 3-A 实施，Phase 4 的 65 个迁移点里凡 `<Modal ref="dlg">` 配 `useDialogFocus(dlg, visible)` 的写法都会**静默无焦点**，而 `P0b-GUARD-01` 那道覆盖率护栏只扫 import 与开标签、看不见这件事 ⇒「覆盖率 100%」与「实际零焦点覆盖」可同时为真。本行另给 `P0b-MODAL-02` 末列那条欠账（③ 后半句与 ④ 须由 Task 9 接成入库断言）一个去向，前向指针见结论栏，原行单元格一字未改 | 前置类（证据前提），不套 §3 三条根因；它把 `P0b-MODAL-02` 留给 Task 9 的探针读数升级为入库断言。**同时否证一条交接给本任务的推断**：R-T9-2 说"`defineExpose` 一写，父级可见面就从 `instance.proxy`（全部 script setup 绑定）收窄到 exposed 的键加 public properties"——实测在**没有 expose 的今天** `captured.close` 就已是 `undefined`，因为 `hasSetupBinding`（runtime-core.cjs.js:3435）要求 `!state.__isScriptSetup`，script-setup 的绑定从来就不挂在 `instance.proxy` 上 ⇒ `defineExpose` 对这张面是**加键、不是收窄**，Phase 4 的迁移点不会从这里继承"读不到别的绑定"那道闸（本来就只读得到 public properties）。存量代价因此只剩一条：`$el` 的返回值由锚点注释变成面板元素——`git grep -Pzo "(?s)<Modal\b[^>]*?\bref=" -- 'src/**/*.vue'` 与单行式 `git grep -n "<Modal[^>]*ref=" -- 'src/**/*.vue'` 两条退出码均为 1（`git grep -o "<Modal" -- 'src/**/*.vue'` = 17，其中 4 处在 `Modal.vue` 自身注释里，调用点 13 个：`ExcelImportDialog.vue` 1、`CourseSubmissions.vue` 2、其余 11 个文件各 1），今天没有任何调用点读 `$el` | 机器判且命令入库可重放：`cd frontend && npx vitest run src/components/ui/` = 11 files / 100 tests（基线 98 加本行两条）。P-a 的对偶实验（本轮实跑，报错原文）：把探针里 `ref: rootRef` 从 `.probe-panel` 挪到 `.probe-overlay` ⇒ `AssertionError: expected <div class="probe-overlay">…(1)</div> to be <div class="probe-panel"></div> // Object.is equality`，汇总行 `Tests 1 failed / 1 passed (2)`；还原后回到 2 passed。P-b 的可证伪面是 expose 这件事本身：Step 3 给 Modal 写上 `defineExpose({ $el })` 后同一行断言当场转红（原文与代价一并追加进本格） | 保留。本行只定档到"分支判定"这一层：3-A 死、3-B 采用是实测签字而非推断；`P0b-MODAL-02` 交给 Task 9 的 ③ 后半句与 ④ 由本行接管（前向指针）。P-c（`defineExpose` 的解包发生在哪一层）与焦点行为三条用例不在本行本次读数里，由同任务的 Step 3/4 提交追加进本行同一物理行，不另起 ID | 本会话实跑：`npx vitest run src/components/ui/` 两次（加用例前 10 files / 98 tests、加用例后 11 files / 100 tests）、`npm run lint` 无输出、`npm run typecheck:gate` 402 与基线持平；新文件单独跑 `npx prettier --check` 合规（仓库整体 `npm run format:check` 有不合规存量，本行不据以写"格式全绿"）。变异跑完即还原，`git status --porcelain` 与 `git diff --stat` 核对过只余本文件与新增用例。`npm run test` 与视觉套件本轮按要求未跑（全包门由控制器持有） |

## 缺陷清单（Phase 0 测出、**尚未修改**的产品缺陷）

这些是 Phase 0 的度量副产品，不是改动单元。**单独列出、不进上面的表**——否则台账会把"发现了"混成"已修了"。
每条给：实测对比度、适用门槛、受影响的锚点图、来源（`证据 §N` 加原始报告节名）。
比值与门槛归属的转录在 `证据 §8`，可重生成性判定在 `证据 §2`（**这些对比度目前无任何已入库命令能重算**）。

- **D-1 · `error` 路由从不写 `data-theme`（这就是 22 而不是 24 的原因）**
  生产代码里唯一的 `data-theme` 写入点是 `frontend/src/composables/useTheme.ts:15`，它在 `applyTheme()` 内；
  该函数只由 `useTheme()` 的 `onMounted`（`:45-47`）与 `cycleTheme` / `setTheme` 触发，而 `useTheme()` 在全仓
  仅被 `components/ThemeSwitcher.vue:52` 与 `views/common/Profile.vue:72` 调用（本会话 grep 实测）。
  `views/error/404.vue`、`403.vue`、`503.vue` 三个文件内没有任何 theme 相关代码（本会话 grep 零命中）
  ⇒ 暗色 error 基线**无法诚实采集**：暗色从未生效 ⇒ 采不到任何与 light 不同的暗色；"两主题像素比对相同"一事**未实测**（无对象可比，不得写成断言）。
  受影响锚点图：`error-404-light.png`、`error-404-dark.png` **均未入库**（`证据 §7.1` 与 §7 表 #23/#24，原始出处 `task-4-report.md` §6 判"拒收"）；
  二次跑里 4 条失败全是同一个 `data-theme` 断言、没有一条是像素差（`证据 §7.1` 的命令与输出块，原始出处 `task-4-report.md` §3 Step 4）。

- **D-5 · 暗色登录页 feature 项对比度 2.96:1（正文门槛 4.5:1）⇒ 违反**
  实测：红底 `rgb(193,59,48)` 上 `.feat-item` 合成色 `rgb(43,32,39)` → **2.96:1**；
  该元素 13px / 400 / opacity .88 ⇒ 属**正文** ⇒ 门槛 4.5:1 ⇒ 违反（`证据 §8.1` 表，原始出处 `task-4-report.md` §5 D-5）。
  受影响锚点图：`login-dark.png`（`证据 §7` 表 #2）。
  **同页必须照抄的区分**：主标题 `.brand-title` 实测 22px / 700 → 属**大字** → 门槛 3:1 → 3.21:1 **通过、不计缺陷**
  （`证据 §8.1` 与 §7 表 #2，原始出处 `task-4-report.md` §5 D-5 与 §F-6 Minor 6；门槛分配口径 spec §6.3，
  报告当时写的 `spec` 行号指针已不落在该条，见 `证据 §10.2`）。这条区分是"别把主观感受写成违规"的样例：
  同一个数字 3.21:1，挂错门槛就变成假缺陷。
  未决：副标题 `.brand-sub` 约 1.05:1 是**推算值不是量出来的**，`证据 §8.1` 末行与 §10.3 明确要求标"待复测"
  （原始出处 `task-4-report.md` §5 D-5 与 §F-6 Minor 6）。

- **D-6 · 管理页角色徽章 1.22:1（且上一轮记成了 3.80:1，真实缺陷比记录的更严重）**
  实测：`/admin/profile` 暗色下 `.role-tag--admin` 的计算样式 `color: rgb(193,59,48)`（即 `#c13b30`）、
  `background: rgba(193,59,48,0.18)`、`font-size: 12px`、`font-weight: 500`；文字压在实测徽章底 `rgb(197,86,77)` 上
  = **1.22:1**（12px 正文，门槛 4.5:1）；徽章**边界** `rgb(197,86,77)` 对横幅红 `rgb(193,59,48)` = **1.22:1**
  （非文本控件边界，门槛 3:1）。两个 1.22 不是抄重——底色本就是同色系半透明叠出来的（`证据 §8.2` 表，原始出处 `task-4-report.md` §5 D-6）。
  受影响锚点图：`admin-profile-dark.png`（`证据 §7` 表 #22）。
  **必须记下这次更正**：上一轮把文字对比度误记为 3.80:1，`证据 §8.2` 的更正记录已证伪（原始出处 `task-4-report.md` §5 D-6 与 §F-6 Minor 5）——
  那个 3.85:1 是旁边 34px/700 的近白字（`.hero-avatar__img`，`ProfileHero.vue:133-136`）与徽章底配出来的对，
  不可能是徽章文字（CSS 只允许 `#c13b30`）。真实值是 **1.22:1**。

- **D-8 · 暗色知识图谱标签用浅色主题的墨 ⇒ 1.13:1，肉眼几乎看不见**
  实测：暗色下标签墨色 `#1f2733` 画在 `rgb(23,29,38)` 面板上 = **1.13:1**（正文，门槛 4.5:1）；
  同一张图里边 `#d59a2a` 对底 = **6.84:1** 可见；浅色侧同墨对白底 = 15.04:1 正常
  （`证据 §8.3` 表，原始出处 `task-4-report.md` §6 表 #18 与 §F-5 D-8）。
  根因（记录在案，供修复用）：`graphOption` 在 composable 求值时就固化了 `getChartTheme()`，
  而 `data-theme` 要等 `ThemeSwitcher` 的 `onMounted` 才写 ⇒ `resolveTheme()` 回落到 `prefers-color-scheme`，
  headless Chromium 默认 light；`updateChartOption()` 之后只换节点色、不重建 `series[0].label.color`
  （`证据 §8.3` 根因段，原始出处 `task-4-report.md` §F-5 D-8）。
  受影响锚点图：`student-knowledge-graph-dark.png`。

- **D-9 · 图谱节点圆点根本不画（两主题）**
  实测：zrender 显示列表里 8 个 `path` 元素 `scaleX = scaleY = 0`、`shape = { symbolType:'circle', width:2, height:2 }`；
  按 EASY/MEDIUM/HARD 六个色值扫画布区域 → **0 个像素**；排除 harness 嫌疑的对照实验也做过了
  （不冻时钟实时等 4s 仍是 0，`reducedMotion: 'no-preference'` 仍是 0）⇒ 基线画的是"边 + 节点标签"，没有节点本身
  （`证据 §8.4`，原始出处 `task-4-report.md` §F-5 D-9）。
  受影响锚点图：`student-knowledge-graph-light.png`、`student-knowledge-graph-dark.png`（`证据 §7` 表 #17/#18）。
  与 `P0a-GUARD-01` 的关系：三道守卫的判据只到"有墨/没墨"（下限 300 px、实测 6051 px），
  **节点不画不在其覆盖范围内**，别把"守卫过了"读成"图画全了"。

## 结构性前置（约束后续每一行）

1. **自建 z 阶整体低于 Element Plus 一层。** 本仓 `--z-*` 实测于 `frontend/src/styles/theme.css:60-66`：
   `--z-sticky: 30`（:62）、`--z-sidebar: 40`（:60）、`--z-topbar: 50`（:61）、`--z-drawer: 80`（:63）、
   `--z-modal: 100`（:64）、`--z-menu: 130`（:65）、`--z-toast: 160`（:66）。
   EP 侧本会话在依赖包内复核到：`element-plus/es/hooks/use-z-index/index.mjs:8` `defaultInitialZIndex = 2e3`、
   `:19-23` `nextZIndex()` 返回 `defaultInitialZIndex + 递增计数`，消费点
   `element-plus/es/components/dialog/src/use-dialog.mjs:139` `zIndex.value = nextZIndex()`
   （修复会话逐条重读已装包核对，三处均在——**以上行号只对已安装的 EP 2.14.7 生效**：`frontend/package.json:32`
   只声明 `^2.4.4`，`node_modules/element-plus/package.json` 实读 2.14.7）。
   ⇒ **EP 任何弹层天然盖在自建浮层之上**，`ui/Drawer` 叠在 `el-dialog` 之上会被压住
   （spec §2.5 "自有 z 阶整体低于 Element Plus"行，`7da1233` 更正后的现文）。**备案（Minor-13）**：
   spec 该行原引的 `popup-manager/pom-hd:2108`、`:2125`、`useDialog:132` 是**虚构路径**（已装 2.14.7 里没有
   `es/components/popup/` 目录，本会话 `ls` 计数 0；EP 2.9+ 已删 popup manager），由控制器在 `7da1233` 更正为
   本行所引三条 ⇒ **台账的引用是对的、spec 的原引用是错的**，结论（EP 弹层从 2000 起）两版一致。
   后续每一行只要动到浮层，"改后"栏就得给 z 读数，不能只写"层级正确"。
2. **覆写债：`!important` 实测 98 条，目标 ≤ 25。** 本会话跑
   `grep -c "!important" frontend/src/styles/index.scss` → **98**（与 spec §2.2 表 `index.scss` 行一致；
   原引 `design.md:63` 行号指针已按 Critical-2 改表锚）。
   加载序 `main.ts` 先 `index.scss` 后 `design-system.css`，同权重时新体系赢，但 98 条 `!important` 不参与这个比较
   （spec §2.2 加载序段，原引 `spec:66` 同批改锚）⇒ 任何新增质感都可能在加载顺序上被旧覆写吃掉（spec §3.3"98 条 !important 使新增质感可能被旧覆写吃掉"段，原引 `design.md:138-139` 已改锚）。
   收敛目标与 Phase 3 门禁见 spec §6.5 第 5 条（98 → ≤ 25；原引 `design.md:207` 已改锚）。**Phase 1/2 报"改动没生效"之前，先排除这一条。**
3. **焦点管理覆盖缺口。** 4 个 `el-drawer` 使用点（`EgEventsDrawer.vue:2`、`DscDetailDrawer.vue:2`、
   `TaskDrawer.vue:2`、`CdsDetailDrawer.vue:2`）**从未 import `useDialogFocus`**——本修复会话用
   `git grep -l "useDialogFocus" 7da1233 -- frontend/src` 钉 commit 复测：命中 **63** 个文件
   （口径写明：去掉 `__tests__` 后 **57**、只算 `.vue` 是 **56**；本行原先记的"60 个文件清单"是口径混写的错数，已由评审核出）；
   四个抽屉宿主文件**无一在列**，逐文件反查各 **0 命中**（本会话实测）——实质结论不变。
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
