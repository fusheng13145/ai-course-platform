# 新人引导改造设计（Onboarding Redesign）

日期：2026-10-21　批次归属：前端视觉升级（本批，见 §0）
证据与台账协议沿用 `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` §5：
**唯一台账是 `docs/visual-upgrade/LEDGER.md`**，本文件不成为第二本账，只承载设计与判据。

**引用规矩**（全局约束，本仓已因此错过五次）：本文对被改文档一律用章节锚，不写裸行号；
对代码写行号时同处标注该读数成立的 commit（本文所有代码行号成立点＝`c068d79`）；永不以 `HEAD` 为锚。

---

## 0. 这份文档是什么，以及它插在批次的哪里

一句话：**把"新用户第一次登录看到的那 5 步介绍"改造成"走完就会用第一件事"的激活引导**，
并顺手收掉它牵连出的两处壳层缺陷（用户菜单分叉、全局快捷键被存在性守卫掐死）。

排期（项目主 2026-10-21 定）：**先解视觉升级 Phase 0 准入条件 (a)（跨套件数据隔离），紧接着做本改造，再开 Phase 1**。
理由与认下的代价：引导的缺陷是"默认主题下等于没有引导"，比 token 纵深更该先；
压暗色值先做成局部 token，**Phase 1 时并入 scrim 家族**——同一条色值会被改两次，多一次同步，已接受。

本改造**不新增像素基线**（(a) 未解除前新增基线＝新增不可复现资产），改用非像素判据；
这条与视觉升级 spec §11.3/§11.4 直接相关，落地时要在 LEDGER 立一行说明"为什么这里不加锚点"。

---

## 1. 改前事实（全部为实测，非推断）

取证方式：一次性 Playwright 脚本（真登录 `student`、复用会话夹具躲登录限流），
读数与 17 张截图落在 `frontend/test-results/onboarding-audit-2026-10-21/`。
**⚠ 该目录被 gitignore，按本仓规矩"未入库报告不算交付证据"⇒ 实施第一棒必须先把它转录进
`docs/visual-upgrade/evidence/onboarding-audit-2026-10-21.md`（含 3 张关键图），否则改后无从对照。**

| 编号 | 事实 | 读数 |
| --- | --- | --- |
| F1 | 第 2 步气泡整体在视口外 | 视口 900／1080／1440 三档下 `cardTop`＝912／1092／1452，整卡完全在视口下方；`elementFromPoint` 在卡片位命中 `.ob-overlay` |
| F2 | 暗色（默认主题）下压暗不可见 | `bodyBg`＝`rgb(15,20,25)`，遮罩 `rgba(10,14,24,.45)` 合成后≈`rgb(13,17,25)` ⇒ 最大单通道差 **3/255**；默认值 `dark` 见 `src/composables/useTheme.ts` |
| F3 | 点被点亮的目标＝永久跳过 | 在聚光区点侧栏：URL 未变、遮罩消失、`ai-course:onboarding:done:v1:4`＝`'1'` |
| F4 | 重开入口分叉且位置与文案不符 | 侧栏底部 `.side-user`（实测 top 834／left 0）弹出的菜单在 **top 52／left 1276**；顶栏 `.user-btn` 的菜单只有「个人中心／退出登录」，**无「新手引导」** |
| F5 | `aria-modal="true"` 但无焦点陷阱 | Tab 八步轨迹：跳过→下一步→**BODY**→顶栏按钮→搜索 INPUT→通知→`.user-btn`→背景「加入课程」；`Shift+Tab` 停在 `.user-btn` |
| F6 | 四个出口写同一个值，完成率不可测 | `skip()` 与 `finish()` 函数体逐字相同；值恒为 `'1'`；无 emit／无请求；`backend/src` 与 `python-services` 内 `onboarding` 零命中 |
| F7 | 中途退出＝永久完成且不保存进度 | 第 3 步按 Esc → 写 `'1'` → 刷新后不再弹；`show()` 硬置 `index=0` |
| F8 | 5 步里只有 2 步指向元素，0 步指向激活动作 | 逐步骤实测：`spotlight` 仅第 2、3 步为 true；第 1、4、5 步为居中卡片 |
| F9 | 窄屏同样溢出 | 375×667 下第 2 步卡片 `bottom` 923 > 视口 667；卡片宽 320 为硬编码常量 |
| F10 | 回归锁缺失 | 单测喂**假** `.sidebar`；`e2e/` 无任何断言引导本身的用例（只有 `dismissOnboarding` 把它点掉）；视觉锚点表无引导格 |
| F11 | 全局快捷键被存在性守卫掐死（A/B 实测） | 常驻节点 `<div class="el-overlay el-modal-dialog" style="display:none">`；**它在场时按 Ctrl+K 焦点停在 `BODY`，移除该节点后再按，焦点落到 `INPUT.gs-input`** |
| F12 | 组件头注释"受离线依赖安装限制"已过期 | `npm view driver.js version` → **1.10.2**，registry 可达（exit 0） |

反证不成立／未测（**禁止当结论引用**）：折叠侧栏后入口仍在（原怀疑"入口消失"不成立）；
浅色主题下压暗是否可见未测（两态读数同为 dark，主题键未改成功）——**这一条已于 2026-10-22 补测并转为实测，
结论与保留见 §5 第 3 条（浅色页面卡 107/255 看得见、浅色侧栏 11/255 低于阈值），本句保留为改前快照、不得再引为"未测"**；
330px 下第 2 步溢出是推算。

---

## 2. 裁定表（新会话不得重开，除非带来新证据）

| 裁定 | 内容 | 若错的代价 |
| --- | --- | --- |
| OB-1 定位 | 交付定位＝**B 档激活引导**，不动后端 | 完成率只能本地区分三态、e2e 取证；**对外不得宣称"跨设备完成率"**。若要 C 档需另立一个可回滚整体 |
| OB-2 交互 | **可操作引导**：洞内始终放行点击（不再"点一下即永久跳过"）；但**中间步骤的点击被引导消费**＝前进，不导航、不写 `done`；**只有最后一步的激活动作**先 release 焦点陷阱再真执行并结束引导 | 最重的一块。做错的症状是"点了没反应"或"焦点丢失键盘卡死"，必须 e2e 真点一遍才能证。**这条是 spec 自审时对原裁定的收窄**：原写"点目标即执行并前进"，但中途执行会打开弹窗／跳页，与引导自己的焦点陷阱冲突（两个 trap），且让后续 page 锚点必然失效 |
| OB-3 差异化 | **丙**：每端一份步骤数组＋按端不同的激活动作 | 步骤配置 1→3 份，e2e 面 ×3；演示换号行为要重新取证 |
| OB-4 菜单 | 收成一份 `UserMenu` 原语，两处渲染，弹层跟随触发物 | 动三处壳层文件；`AppShell.test.ts` 菜单顺序断言要**重签不是删** |
| OB-5 排期 | 先解 (a) → 本改造 → Phase 1；scrim 先做局部 token 并立前向指针 | 色值改两次、多一次同步；Phase 1 开工后移一批 |
| OB-6 出口语义 | **Esc 与点洞外＝暂停并记 `atStep`，不再等于永久完成**；新增「稍后再看」 | 这是本节最易被外部感知的一处：演示时刷新/换号的重弹行为会变。必须同步手册 REQ-FE-008 与 DR-52 口径 |
| OB-7 方案 | 自研四单元，几何层做成可替换边界（将来换 driver.js 只动 `useSpotlightOverlay`） | 多写约 120 行几何代码；换来的是"10 条缺陷里 7 条库根本不管"的那部分仍在我们手里 |

---

## 3. 设计

### 3.1 单元边界与数据流

| 单元 | 唯一职责 | 不许做 |
| --- | --- | --- |
| `src/onboarding/steps/{student,teacher,admin}.js` | 纯数据：步骤、锚点键、文案、action、expect | 不碰 DOM、不依赖 Vue |
| `src/onboarding/anchors.js` | 锚点键→元素解析；导出 `ALL_ANCHOR_KEYS` | 不做几何、不读存储 |
| `composables/useSpotlightOverlay.js` | 视口感知定位＋四块矩形拼洞＋滚动跟随 | 不认 anchor、不渲染卡片、不读存储 |
| `components/OnboardingGuide.vue` | 卡片、进度、出口、焦点交接 | **不碰 localStorage**，只发 `@done`/`@paused`/`@skipped` |
| `src/onboarding/state.js` | 契约 v2 读写与迁移；`onTransition` 接缝 | 不认角色以外的业务 |

数据流：`AppShell` 挂载 → `state.read(role, userId)` 判"不弹／续看／首看" → `drive(steps[role])` →
每步 `anchors.resolve()` 交给几何层与卡片 → 用户点洞内真元素 ⇒ 执行动作并前进 → 出口写 `state`。

边界一句话：**数据不碰 DOM，几何不认选择器，组件不碰存储，存储不认角色以外的东西。**

### 3.2 几何、可见性与焦点交接

- **拼洞**：上／下／左／右四块矩形铺满视口减去目标矩形（外扩 6px）。洞内没有元素 ⇒ 点击天然落到真实控件。
  不用 `clip-path` 的理由是可测性（每块是普通 div，可断言 rect 与合成色）。
  滚动／resize 必须走 `rAF` 节流——现状每次 scroll 直接写样式还挂 0.22s 过渡，必然追不上。
- **视口感知定位**：候选序 `下→上→右→左→视口内贴边`，每候选要求**整卡完全可见 + 8px 安全边距**；
  卡片高度取实测 `offsetHeight`，删除 `240` 魔法数。满高目标（侧栏）走"右侧"；窄屏右侧不足退化为底部贴边浮条。
- **暗色可见性**：新增 `--onboarding-scrim`（明 `rgba(10,14,24,.45)` 原值保留／暗 `rgba(0,0,0,.66)`）
  与 `--onboarding-ring`（暗色描边 2px＋外发光）。判据可测：**洞外采样点与洞内同元素合成后单通道色差 ≥ 24/255**
  （失败读数 3/255，阈值留 8 倍余量，且高于本仓已知盲区 8/255）。
- **z-index**：硬编码 3000 换成 `--z-onboarding`，注释写明须高于 EP 弹层默认起点 2000 与本仓 `--z-toast`。
- **焦点交接**：卡片开时 `useFocusTrap(rootRef, active)`（签名与 Modal／Drawer 同源）锁卡片。
  **中间步骤**：洞内点击被引导消费（`preventDefault` ＋ 前进），焦点始终留在卡内——不导航，所以不存在"引导还开着、背景弹层又抢焦点"的两 trap 冲突。
  **激活动作步（最后一步）**：先 `release()` 再执行动作并结束引导（写 `done`），把页面完整交给用户。
  这条顺序是 §2 OB-2 收窄的落点，也是 §3.4 排序不变式成立的前提。
  **锚点解析失败不许静默居中**：卡片退化为不阻塞点击的居中态，同时把 `anchor-miss:<key>` 显式记入台账并 `console.warn`。

### 3.3 状态机、存储契约、壳层收口

状态机：`idle → running(n) → { paused(n) | skipped(n) | done }`

| 出口 | 写入 | 下次登录 | 菜单重看 |
| --- | --- | --- | --- |
| 完成（走完最后一步，其 action 已真执行） | `done` | 不弹 | 从第 1 步 |
| 跳过（按钮） | `skipped@n` | 不弹 | 从第 1 步 |
| 稍后再看（新增按钮） | `paused@n` | **从第 n 步续弹** | 从第 n 步 |
| Esc／点洞外矩形 | `paused@n` | 续弹 | — |

存储 v2：`ai-course:onboarding:v2:<userId>` ＝ `{status, atStep, role, seenAt}`。
迁移只有一条：读到 v1 键为 `'1'` 且无 v2 ⇒ 写 `done`（**绝不让老用户莫名重弹**）；v1 键不删、留作回滚证据，
台账记一条"两键并存"的读法。按账号口径不变（DR-52／D2-9 已定案，不重开）。
`state.js` 写失败不得静默——现状 `catch {}` 把写失败与读失败一样吞掉，改为读侧可降级、**写侧留痕**。

完成率取证（B 档边界）：三态值本身可区分 ＋ 三端各一条 e2e ＋ `onTransition(from,to)` 单点接缝（本批只接 console 与测试）。
将来上后端只改这一处。

壳层收口两处：
1. 新建 `components/UserMenu.vue`（items 由 props 给、含「新手引导」），顶栏与侧栏底部都渲染它；
   两份"动态建 DOM ＋ `innerHTML` 拼图标"的实现删掉。补一条断言：**两处触发物渲染出的 items 逐字相同**。
2. `hasOpenOverlay()` 由判存在改为判可见（`display!=='none' && rect.height>0`），另加 `hasBlockingOverlay()` 认 `.ob-overlay`
   ⇒ 全站快捷键恢复可用，且引导开着时 `Ctrl+K`／`/`／`?` 不穿透。F11 那条 A/B 读数**反过来**就是判据。

### 3.4 步骤模型与三端内容

步骤形状：`{ id, title, desc, anchor?, scope: 'shell'|'page', action?, expect }`

锚点成本实测比预估小：侧栏菜单项的 `key` 由 `src/layouts/menus.ts` 统一供给（且有 `menus.test.ts`），
`AppSidebar.vue` 只在一处循环渲染 ⇒ **`data-onboard="'nav:' + item.key"` 一行覆盖全部导航锚点**（一／二／三级各一行）。
总计 6 处绑定，其中 3 处是循环内一行。

**排序不变式**：`scope:'page'` 的步骤**只能是最后一步**（即激活动作步），且最后一步之前的所有步骤必须是 `shell` 锚点或无锚点。
写成 `steps` loader 的入库校验（违反即红）——它是唯一会让引导中途锚不到元素的结构原因，
前提正是 §3.2 那条"中间步骤不导航"。

| # | student | teacher | admin |
| --- | --- | --- | --- |
| 1 | 欢迎（无锚点）：一句"这是什么"，删掉宣传语 | 同左（教师口径） | 同左（管理口径） |
| 2 | `nav:ai-assistant`（shell） | `nav:t-assess`（试题与考试，shell） | `nav:dashboard`（治理大盘，shell） |
| 3 | `nav:graph`（知识图谱，shell） | `nav:t-grades`（成绩总览，shell） | `nav:users`（用户与账号，shell） |
| 4 | `side-user`（shell，"随时从这儿重看"，文案与实际弹层位置一致） | 同左 | 同左 |
| 5 | **激活动作**`join-course`（page，课程大厅「+ 加入课程」）——点它即结束引导并真的开始用 | **激活动作**`nav:t-ai`（AI 教学中心，出题从这里开始） | **激活动作**`nav:audit`（审计，先看清谁动过什么） |

**2026-10-21 控制器实测更正（原文保留为史料）**：本表第 2 行 admin 原写 `nav:governance`（治理大盘），但
`frontend/src/layouts/menus.ts` 里 `governance` 是「运行监管」、`dashboard` 才是「治理大盘」⇒ 已改为 `nav:dashboard`，
Task 1 的 `admin.js` 同步。另两处激活动作路由以实际路由为准：teacher 用 `/teacher/ai`（原写 `/teacher/ai-center`，
该路由不存在），admin 用 `/admin/logs`（原写 `/admin/audit`，该路由不存在；anchor 仍取可见的 `nav:audit` 组，
子项折叠与否不影响导航）。原文值保留在本句里，不另删史料行。

**2026-10-22 浏览器实测补注（本表三格的锚点键与路由逐条真跑过，无一格需要回改；只补两处落点措辞）**：
第 5 行 student 原写"课程大厅「+ 加入课程」"，实测落点是**学生端首页** `SdCourseGrid` 的
`.course-card.join-card`（可见文案「加入新课程／凭选课码加入」），`/student/courses` 那一页并不参与本步；
第 5 行 teacher／admin 两格的 `action.to` 实测把 URL 分别带到 `/teacher/ai` 与 `/admin/logs`（引导同时关闭、
v2 写 `done@4`）。student 那格的动作实测是**弹窗**：`.modal` 出现、标题「加入新课程」，URL 不变——
"激活动作真执行"在这一端只能断弹窗面。读数出处同 §5 首部那条命令与 `report.json`。

每步文案统一三句式：**这是什么 → 点这里会发生什么 → 你什么时候会再用到它**。
（现状：5 步里 2 步是纯宣传语、0 步有"点这里会怎样"。）

### 3.5 测试、留痕、回滚

- 单元：steps 排序不变式（**喂合成 steps 数组**，不读活文件——Task 13 I-1 的教训）；
  state v2 迁移／续看／三态互不覆盖；`ALL_ANCHOR_KEYS` 与三端 steps 引用集合相等（多一少一都红）；
  `computeCardPlacement(rect, cardSize, viewport)` 四档视口断言完全可见＋边距，
  **并配一条正常目标走"下"候选的正读数**（只有负读数时"把候选全删光"会绿）。
- 组件：Tab N 次仍在卡内；Esc 写 `paused` 不写 `done`；点洞内元素触发 action 且前进；`anchor-miss` 既留痕又不阻塞点击。
- 壳层：两处 items 逐字相同；快捷键 A/B 反向断言。
- e2e：三端各走完一条（含真跳转与真弹窗）；一条 `paused@2` 刷新后续看；一条"跳过不再弹"。
- 视觉：**不加像素基线**，用色差与 rect 两条非像素判据。代价写明：引导"长什么样"没有像素回归网。
- 留痕：每个单元一条 LEDGER 行（9 列，单元格内禁竖线）；**改前读数先入库**（§1 的 ⚠）。
- 回滚切三块：① 几何／可见性（组件内）② 状态契约 v2（回滚时 v1 键保留）③ 壳层（UserMenu＋快捷键守卫）——
  ③ 影响全站，回滚面最大，单独一棒单独判。

---

## 4. 准出判据（逐条可测，缺一不叫改完）

1. 四档视口（900／1080／1440／375）下每一步卡片**整卡完全可见**，且有一条把候选位序全删光的变异必红。
   （口径边界：§3 给几何的候选序要求"整卡完全可见 **+ 8px 安全边距**"，那 **8px 是设计目标**；本判据只要求
   "整卡完全可见"。两者别混用——R-07 的假阴性正是把 8px 缓冲当成了判据，见 §8.1 判据 1 行与 §8.2 第 2 条。）
2. 暗色下洞外／洞内合成色差 **≥ 24/255**（改前 3/255）。
3. 中间步骤点洞内真元素：**前进、URL 不变、不写 `done`**（改前是"点一下即永久完成"）；
   激活动作步点它：**引导关闭、URL 或弹窗状态变化、写 `done`**。两条各一条断言，缺一不算 OB-2 落地。
4. Tab 连续 12 次，焦点始终在卡内可枚举集合中；`Shift+Tab` 亦然（改前第 3 步就逃到 BODY）。
5. 三态值可区分：`done`／`skipped@n`／`paused@n` 各一条入库断言；`paused` 刷新后从第 n 步续。
6. 两处用户菜单 items 逐字相同且都含「新手引导」；引导第 5 步文案与实际弹层位置一致。
7. `display:none` 的 `.el-overlay` 在场时 Ctrl+K **响应**；`.ob-overlay` 在场时**不响应**（F11 反向）。
8. `ALL_ANCHOR_KEYS` 与三端 steps 引用集合相等；把 `.sidebar` 类名改掉必有测试红（改前零告警）。
9. 三端各一条 e2e 全绿；`npm run test`、`typecheck:gate`（棘轮只许降）、`size:gate` 三项按当次实跑读数入台账，
   **禁写"全绿"**（`lint`／`format:check` 覆盖面只有 `src/**`，e2e 面为 0）。

---

## 5. 未证事项（2026-10-22 逐条转实测；仍未来得及证的，留在本节气不转述）

**取证方式与量程（读下面任何一条之前先看这段）**：脚本 `frontend/e2e/visual/tools/verify-onboarding.mjs`，
重放命令 `cd frontend && PW_PORT=5175 node e2e/visual/tools/verify-onboarding.mjs`
（前提：后端 8080 在听、5175 上有 dev server；会话走 `e2e/visual/.auth/*.json`，全程不重登、不打印其内容）。
产物入库在 `docs/visual-upgrade/evidence/onboarding-verify-2026-10-22/`（`report.json` ＋ 38 张截图）。
色差一条的量程＝把 scrim 的**计算样式**按 alpha 合成到"洞内被露出的那个元素"的有效底色之上，再与该底色比单通道最大差——
**是计算样式合成，不是渲染像素**；参照物逐条随读数落盘（`effectiveBgSource` 一格），因为拿页面基底 `--bg` 当参照
会得到一条永不成立的判据（暗色 body＝`#0f1419`，0.66 的黑压上去实测只差 **16/255**，阈值 24——那是探针选错，不是压暗失效）。

1. teacher／admin dashboard 上是否存在比 `nav:*` 更直接的激活动作——**已实测＝不存在，§3.4 那三格按原样成立**：
   teacher 首页可点动作实测只有「新建课程」一项（建课，不是出题），admin 首页是「进入用户管理／课程库总览／查看详情」
   三项（都不是审计）；三端激活动作的落点逐条真跑过：student 末步点「开始使用」⇒ 引导关、`.modal` 弹出、
   标题实测「加入新课程」、v2 落 `done@4`；teacher ⇒ URL `/teacher/dashboard` → `/teacher/ai`；
   admin ⇒ `/admin/dashboard` → `/admin/logs`。三端末步都写 `status:'done'`、`atStep:4`、`role` 与登录端一致。
2. `join-course` 的真实选择器与它在折叠侧栏／窄屏下的存在性——**已实测**：选择器 `[data-onboard="join-course"]`
   （落点＝学生端首页 `SdCourseGrid` 的 `.course-card.join-card`，文案「加入新课程／凭选课码加入」，
   **不是**课程大厅页——§3.4 那格的措辞按实测补注，原文不删）。
   折叠侧栏（顶栏「折叠侧边栏」＝mini 态）下三端五步全部整卡可见、描边在场、`[data-onboard]` 计数不变
   （student 49／teacher 122／admin 376）、`anchor-miss` 零命中。
   窄屏一条要分开说：**375 与 330 都在 768 断点以下，侧栏被 `translateX(-100%)` 推出画外**——
   锚点仍在 DOM 里（同上计数），但洞的 rect 实测 `left:-240 / right:-4`，即**洞在视口外、压暗块被夹到屏幕边上**；
   学生端末步的 `join-course` 在页面内容里（375 下 DOM 中仍在），但该档采样时刻洞的 rect 实测
   `top:1043.73`（视口高 667，`report.json` 的 `roles.student.narrowWalk["375x667"][4].ringRect.top`）
   ——平滑滚动尚未落位，卡片本身走 `bar-bottom` 兜底、底边 659.17 仍在 667 之内。
   末步真点通是在 1440 档证的，375 档只证到"锚点在场、卡片在视口内"——**不是**"整卡可见"，
   那一档的 `cardFullyVisible` 实测 `false`，差的正是下面第 4 条末段那笔 0.17px 安全边距账。
   （**2026-10-23 更正**：本句当时成立，R-07 把判决函数回归 §4 原文后已不成立——375×667 五步
   `cardFullyVisible` 实测**全为 true**，末步底边 `659.17 < 667`；那笔"0.17px 账"是 8px 设计目标
   被当成判据，卡片并没有出界。见 §8.1 判据 1 行与 §8.2 第 2 条。）
   （改正痕迹：本条原转录作 `top:970.73`，2026-10-23 收尾复核时对 `report.json` 逐格开行核对，
   报告两个版本（`3e57d8a` 与现文）都没有 970 这一枚数，实测值是 1043.73，已按报告改正；
   同段"卡片可见"的措辞一并降级，因为它与本文件第 4 条"不许记成整卡可见"自相矛盾。）
   ⇒ 结论：手机宽度下"指向侧栏"的那几步只是不报错，**并不真的指向一个看得见的东西**；本批不修（三端承诺的视口是桌面四档），
   登记为已知边界。
3. 浅色主题下压暗是否可见——**已实测＝分两处说，且必须带保留**：
   切主题的机制是 `document.documentElement` 上的 `data-theme` 属性（`useTheme` 的 `applyTheme` 写的就是它，
   只有 `[data-theme='dark']` 一个覆盖块 ⇒ 属性置 `light` 即浅色令牌全集）；改前那次"两态读数同为 dark"
   是因为动了 localStorage 键而没动这个属性。实测：浅色 scrim＝`rgba(10, 14, 24, 0.45)`，
   压在**页面卡片**上（student 末步 join-card，有效底色实测 `rgb(247,248,250)`）差 **107/255**，看得见；
   压在**侧栏**上差只有 **11/255**——侧栏那条渐变两主题同值（实测色标 `rgb(35,43,58)`→`rgb(26,33,48)`），
   本来就已经是深色，再压一层 45% 的黑几乎不动（同一位置在暗色主题下是 **32/255**）。
   样本量如实记：六格 lightProbe 里**只有 student 末步那一格是页面内卡片**（107/255），其余五格（含 teacher／admin 末步）
   洞都压在侧栏上（11/255）——teacher／admin 的激活动作锚点是 `nav:ai`／`nav:logs`，本身就在侧栏；
   取证脚本给这两格打的 `at` 标签写着"页面内锚点"是硬编码字符串（`verify-onboarding.mjs` 里那一行的字面量），
   与实际落点不符，**读数不受影响**（`revealedElement`／`effectiveBgSource` 两格是真的），标签缺陷在此登记、不改脚本重跑。
   ⇒ 浅色主题下"洞压在侧栏"的那几步压暗**低于 24 的阈值**，本批不改色值（scrim 家族归 Phase 1 并轨时一起判），
   这一条按"已实测、结论是部分不达标"入账，不许简写成"浅色也 OK"。
4. 330px 宽下第 2 步溢出——**已实测＝不溢出，但暴露一条真缺陷**：卡片宽 320 在 330 视口里两侧各剩 5px，
   五个候选位全不满足 8px 安全边距 ⇒ `computeCardPlacement` 返回 null；
   几何组合层据此把 `placement` 写成 `centered`，而模板的 `.centered` 类只绑在"无锚点或锚点解析失败"上 ⇒
   卡片既没有坐标也没有居中类，实测落在 `(0,0,320,207.17)` 即覆盖层的静态位置——**压住顶栏，不是居中**。
   三端同形。330 不在承诺的四档视口内，但"没有候选位可放"这条路径是可达的，
   故本轮按实测改判：卡片模板的 `centered` 类补上 `placement === 'centered'` 一支（改判后组件级用例与浏览器面各一条守着）。
   **改判后重测**＝卡片实测 `top:256.41 / left:5 / 320×207.17`，已在视口正中、不再压顶栏；
   但 320 宽的卡在 330 视口里两侧各只剩 5px，8px 安全边距这一档**按定义就不可能满足**——
   这一条如实记为"降级正确、边距不达标"，不许记成整卡可见。
   改前两端的读数与截图（`(0,0)` 那一版）留在提交 `3e57d8a` 的同名文件里，
   取法：`git show 3e57d8a:docs/visual-upgrade/evidence/onboarding-verify-2026-10-22/student-330-step-2.png`。
   另记一条同族量程事实：375 档学生末步走 `bar-bottom`，几何层按 `offsetHeight`（取整 207）判"贴边后仍在边距内"，
   实际渲染高 207.17 ⇒ 距底边的安全边距差 **0.17px** 未满足（卡片仍在视口内，`bottom 659.17 < 667`），
   属读数口径差而非溢出。
5. driver.js 1.10.2 的能力清单——**仍未证**（本批未做 spike，也没有任何代码依赖它）。要换库先补半棒 spike 再决定，
   本条不许被引用成"我们评估过 driver.js"。

**本轮实测新增、原表没有的两条观察**（都不改判据，只登记）：
① 无锚点的第 1 步也会打一条 `console.warn`，实测文本是 `[onboarding] anchor-miss:(none)（第 1 步…）`——
`useSpotlightOverlay` 把"没有目标"与"目标解析失败"合成同一条回调，于是设计上属于正常态的那一步
也在往"锚点解析失败必须留痕"这条信号里灌噪声。本批不改（改它要动几何单元的回调契约），登记为待办。
② 层级这件事第一次有了浏览器面读数：EP 弹层开着时用户菜单的命中点实测顶元素是 `div.menu-item`
（`.pop-menu` 运行时 z＝2100 vs EP 遮罩运行时 z＝2010，EP 的层号由 popup manager 现场分配、源码里根本不存在）；
引导开着时同一个点实测顶元素是 `div.ob-scrim`（z＝3000）⇒ 菜单在引导层之下。两条都在 `report.json` 的
`stackingMenuOverEpDialog` / `stackingMenuUnderGuide` 两格里。

---

## 6. 与既有定案的关系（不重开，只同步）

- **DR-52**（必须有重看入口、门控不得跳过副作用注册）：本设计**加强**它（两处入口都渲染同一份 items），
  但改了它的"重看从第 1 步"含义——`paused` 用户重看从第 n 步。手册 REQ-FE-008 与 D.12.3 需同步一条改判。
- **D2-9／冲突 5**（按账号 or 按浏览器）：口径不变（按账号），本设计只补"多角色差异化步骤"这半边。
- **视觉升级 Phase 4 准入门第 5 项**（"全部自建弹层带焦点陷阱"）：本改造落地即把该项从**半闭推到闭**
  ——`OnboardingGuide.vue` 正是那个第三载体。要在 LEDGER 记这条连带收益。
- **Phase 0 准入条件 (a)**：本改造排在其后，且明确不新增像素基线。

---

## 7. 转 writing-plans 的分份建议

一份计划吃 5 个棒：① 改前读数入库＋锚点与 steps 骨架（含排序不变式）② 几何层与视口四档测试
③ 状态契约 v2 与出口语义 ④ 壳层收口（UserMenu＋快捷键守卫，独立可回滚）⑤ 三端 e2e 与文案落地。
②③ 有接口依赖但无文件冲突，可并行；④ 单独一棒单独判（回滚面最大）。

---

## 8. 交付状态（2026-10-23 收口，判据 1–9 逐条结论）

新会话从这里接手：§1 是改前事实、§4 是准出表、本节是**按 §4 逐条判过之后的现状**。
过程账（每一棒的裁定与修复轮）在 `.superpowers/sdd/2026-10-21-onboarding-redesign/progress.md`（gitignore，不入 git）；
账面账在 `docs/visual-upgrade/LEDGER.md` 的 `P0c-ONB-01…05` ＋ `P0b-FOCUS-02`。

**收口点**：`main` 本地 `456bf34`，批次区间 `8f9466f..456bf34`（Task 1–5 各棒另见 LEDGER 行的 commit 指针）。
**未做且经项目主明示跳过**：整枝最终评审、`finishing-a-development-branch`、任何推送（上游 `origin/leyon` 一字未动）。

### 8.1 判据逐条

| 判据 | 结论 | 由什么测到 | 保留（别当成已了结） |
| --- | --- | --- | --- |
| 1 四档视口整卡可见 | ✅ | 单测 `geometry.test.js`（900／1080／1440／375 四档，10 运行时）＋ e2e 走完 5 步逐帧 `cardFullyVisible`（钉 1440×900）＋ e2e **375×667 五步逐帧**（`onboarding.spec.ts` 的「375 档整卡可见」，2026-10-23 补） | 375 档五步余量实测 **27.5／8／8／8／7.83 px**，末步底边 `659.17 < 667`（**整卡在视口内**），`cardFullyVisible` 全为 true。旧读数"差 0.17px 为 false"是**实现常量与判据混淆**造成的假阴性（R-07）：判据原文只有"整卡完全可见"，8px 是 §3 的设计目标，判决函数已回归 §4 原文；8px 目标保留为只读读数 `cardMarginPx`／`cardBufferOk`（末步 `cardBufferOk=false`，如实可见） |
| 2 暗色合成色差 ≥ 24/255 | ✅ | 单测 `scrim.contrast.test.js`（参照 `--surface` 25／侧栏渐变暗端 32）＋ e2e 暗色那条（参照＝洞内元素有效底色） | 量程＝**计算样式按 alpha 合成后再比单通道差，不是渲染像素**；本批按排期裁定不新增像素基线 |
| 3 洞内点击前进且不写终态／激活动作真执行 | ✅ | e2e 各一条（中间步骤断 `readStatus()` 为 null 且 URL 不变；末步 student 断弹窗、teacher／admin 断 URL 变化）＋ 组件级 `OnboardingGuide.focus.test.js` | 生产面靠 document 捕获监听（只在当前锚点子树内消费）；若日后有锚点自带导航处理器，需复核 `preventDefault` 的顺序 |
| 4 Tab 12 次与 Shift+Tab 留在卡内 | ✅ jsdom 面 ＋ **浏览器面（2026-10-23 补）** | `OnboardingGuide.focus.test.js`（正向 12 次＋至少走过 2 个不同元素的正读数、反向 Shift+Tab 回绕）＋共享层 `useFocusTrap.test.ts` 8 例；**真实键盘**由 `onboarding.spec.ts` 新增用例补齐（`page.keyboard.press` 12 次正向 + 6 次反向，每步断言 `activeElement` 在 `.ob-card` 内，正向回绕 2 次/反向 1 次；commit `e8ad9b5`） | 量程＝**真实浏览器**，取证序列在 `docs/visual-upgrade/evidence/onboarding-tab-2026-10-23/sequence.json`（`verdict.result = passed`）。`__trapAdvance` 代触发路径仍在单测里保留，作为组件级快速回归 |
| 5 三态可区分且 paused 续看 | ✅ | 单测 `state.test.js`（9 条）＋ e2e 三条（Esc→`paused@2`→刷新见 `3 / 5`；跳过→`skipped`；末步→`done`） | 三态只落本地 `localStorage`，**没有服务端口径**，"完成率"目前只是本地可区分，不可跨设备 |
| 6 两处用户菜单 items 逐字相同、第 5 步文案对位 | ✅ | `UserMenu.vue` ＋ `userMenuItems.js` 单一来源、`UserMenu.test.js` 源码级守卫（两处都不得自带字面量）、`AppShell.test.ts` 全链（顶栏点击→`resume`） | 菜单方向键导航未做；`openMenuFrom` 仍按 `document.querySelector` 取首个 |
| 7 隐藏 `.el-overlay` 在场时 Ctrl+K 响应、`.ob-overlay` 在场时不响应 | ✅ | `keyboardShortcuts.overlay.test.js`（正向用例在 jsdom 里 stub rect）＋ `AppShell.shortcuts.test.ts`（`?` 那条正反配对）＋ 三次变异 | jsdom 恒 0×0 是这条的量程天花板；层级关系（菜单 2100 压过 EP、低于引导 3000）已由 e2e 叠放两条补上浏览器面 |
| 8 锚点集合相等、改类名必红 | ✅ | `anchors.test.js` 双向包含、`steps.keys.test.js` 每端精确集合、`anchors.bindings.test.js` 剥注释后钉四处绑定，两次变异 | 精确集合是手工钉的：spec §3.4 那张表改了要同步改测试（有意设计成人必须看见） |
| 9 三端 e2e 各一条 ＋ 三项门禁按实跑入账 | ✅ | `onboarding.spec.ts` **17 passed（18.7s）**；`npm run test` 180 files／1895 tests；`typecheck:gate` 402 条／90 文件持平（未 `--sync`）；`lint` 零问题（覆盖面只 `src/**`，e2e 面为 0）；`size:gate` JS 3119.8／3400 KB、EP 735.14／830 KB、echarts 805.06／900 KB、xlsx 323.28／380 KB、其余 1256.32／1300 KB、EP CSS 381.6／420 KB 全部在预算内（dist 构建时间晚于本批最后一笔源码提交） | 全流程未写"全绿"式结论；`anchors` 基线面 40 passed／0 failed 由控制器 2026-10-23 实跑（排除 D-1 那两格 `error-404`） |

### 8.2 待完成（本批没做、需要一次决定或一次工）

1. ~~**判据 4 的浏览器面**：真键盘 Tab／Shift+Tab 在真浏览器里走 12 次仍留在卡内。~~ **✅ 2026-10-23 已闭**（`e8ad9b5`，`page.keyboard.press` 18 次、序列入库，见 §8.1 判据 4）。
2. ~~**判据 1 的 375 档那 0.17px**~~ —— **已闭（2026-10-23，R-07）**。两难的前提本身不成立：§4 判据 1 的原文只有"整卡完全可见"，**没有 8px**；8px 是 §3 给几何的设计目标。所以 0.17px 从来不是判据缺口，而是 `frontend/e2e/onboarding-helpers.ts` 的 `cardFullyVisible` 把 8px 缓冲**当成判据**造成的假阴性。处置：判决函数回归 §4 原文（矩形落在视口内即成立，`EPS=0.5` 只吸收亚像素取整），8px 目标降为只读读数 `cardMarginPx`／`cardBufferOk`（继续可见、不参与断言）；新增 e2e 375×667 五步逐帧用例。**不是把判据改成能过**——判据一字未改，也没动 `geometry.js` 的 245 常量与 8px 目标值。
3. **整枝最终评审 ＋ 收分支**：经项目主明示未做；`finishing-a-development-branch` 尚未走。
4. **推送**：未做，需项目主单独点头（本地只留 `main`，上游 `origin/leyon` 未动）。
5. **D-1 那两条恒红**（`error-404` 明暗格，LEDGER `P1-ERRTHEME-01`／`P1-ERROR404-01`）属视觉升级批，本批只排除、未修。
6. `hasOpenOverlay` 现在**没有生产调用点**（`?` 改判 `hasBlockingOverlay` 之后），要么降级为内部函数、要么在文档里钉住它作为"EP-only 探针"的对外语义。
7. `onTransition` 只有测试在用（为壳层埋点预留，最终没接）。留或删要一次决定。
8. 取证脚本 `verify-onboarding.mjs` 给 lightProbe 硬编码的 `at` 标签写"页面内锚点"，而 teacher／admin 末步锚点在侧栏 ⇒ 六格浅色读数里只有 student 那一格是页面内卡片。读数本身有效（参照物是真读的），标签要改。
9. 环境前提：会话重启会带走后端（8080）与引擎；`.auth/{student,teacher,admin}.json` 过期时 e2e 直接跳 `/login`。重放配方见 LEDGER `P0c-ONB-05` 验证栏。

### 8.3 可扩展（不欠，但可以往前推）

- **完成率上报后端**：v2 已有 `status`／`atStep`／`role`／`seenAt`，加一张表与一次 emit 即可从"本地可区分"升到"可统计"，需要隐私口径。
- **steps 外置**：三份 `.js` 改成后端/JSON 配置，文案与步骤可由产品改，排序不变式仍由 `validateSteps` 守。
- **菜单完整键盘语义**：`role="menu"` 内方向键移动焦点、Home／End、Esc 归还焦点给触发物。
- **UserMenu 跟随重定位**：`pos` 目前只在 setup 期算一次，开着时滚动／改窗不重排；`openMenuFrom` 改模板 ref 去掉"取首个 `.user-btn`"。
- **`.pop-menu` 样式收敛**：现存 design-system 全局 ＋ `ui/DropdownMenu.vue` scoped 两份副本，外加 `UserMenu.vue` 的一条层级例外；`DropdownMenu` 那张私有 ICONS 表也仍与 `layouts/icons.ts` 并行。属视觉批次口径。
- **引导像素基线**：本批按排期裁定不加（spec §3.5 写明代价＝"长什么样"没有回归网）；若 Phase 1 的 scrim 家族落定，可一并纳入。
- **锚点注册表扩容**：目前只有 `side-user`／`join-course` 两个裸键加 `nav:*`；页内元素级锚点（如某个按钮/卡片）需要新裸键时，`anchors.test.js` 的双向包含会自动要求"有步骤用它"。

### 8.4 未证与未定性（禁止当结论引用）

- 浅色主题压暗的**可见性判断**：数值已取到（页面卡片 107/255 看得见、侧栏 11/255 看不见），但"用户实际会不会觉得没压暗"仍是主观判断，只有 rect 与色差两个非像素读数。
- 真实浏览器里的焦点顺序导航（判据 4）——见 §8.2 第 1 条。
- 375 档"整卡可见"——**已闭（2026-10-23，R-07）**：浏览器面实测 **true**（`onboarding.spec.ts` 的「375 档整卡可见」五步逐帧；末步底边余量 **7.83px**，`bottom 659.17 < 667`）。此条原记的"差 0.17px 为 false"是判决函数把 8px 设计目标当成判据造成的假阴性，不是产品缺陷，也不是判据降级。
- 完成率的可跨设备性：无服务端口径。
