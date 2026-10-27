# 改前读数：新人引导审计（2026-10-21）

## 来源与重放口径

- 原始取证是一次性 Playwright 脚本（真登录 `student`、复用会话夹具躲登录限流），读数与 17 张截图落在
  `frontend/test-results/onboarding-audit-2026-10-21/`；该目录被 gitignore，故本文件是它的入库转录。
- 2026-10-21 由控制器重放原四个审计脚本并恢复 `readings.json` ＋ 17 张 PNG，读数与
  `docs/历史归档/收口批次-2026-10/onboarding-redesign/2026-10-21-onboarding-redesign-design.md` §1 逐条一致；唯一的脚本改动是给登录竞态加了一个稳定输入助手。
- 本棒未重跑任何审计脚本或 Playwright。脚本 1 未写进 `readings.json` 的键
  （`1_persistent_el_overlay`、`2_hotkey_ab`、`3_side_user_menu`、`4_topbar_user_menu_items`、
  `6_step2_card_at_vh_900/1080/1440`、`5_side_user_when_collapsed`）转录自控制器重放输出
  `.superpowers/sdd/2026-10-21-onboarding-redesign/audit-replay-1.log` … `audit-replay-4.log`。
- F12 的 `npm view driver.js version → 1.10.2` 转录自 spec §1，本次未重跑。
- `frontend/test-results/` 是每次 Playwright 运行的清空目标（两份配置都没设 `outputDir`），所以原始读数
  必须转录入库；2026-10-21 控制器正是被一次官方视觉运行清空该目录后，按原脚本重放才恢复的。

## 12 行改前事实

| 编号 | 事实 | 读数 | 读数键 |
| --- | --- | --- | --- |
| F1 | 第 2 步气泡整体在视口外 | 视口 900／1080／1440 三档下 `cardTop`＝912／1092／1452，整卡完全在视口下方；`elementFromPoint` 在卡片位命中 `.ob-overlay` | `6_step2_card_at_vh_900`／`6_step2_card_at_vh_1080`／`6_step2_card_at_vh_1440`（`audit-replay-3.log`）；卡片位命中读数见 `A_step2_offscreen.hitTestAtCardCenter`（`audit-replay-2.log`） |
| F2 | 暗色（默认主题）下压暗不可见 | `bodyBg`＝`rgb(15,20,25)`，遮罩 `rgba(10,14,24,.45)` 合成后≈`rgb(13,17,25)` ⇒ 最大单通道差 **3/255**；默认值 `dark` 见 `src/composables/useTheme.ts` | `dark.s1.bodyBg`／`dark.s1.overlayBg`（`audit-replay-4.log`；`light` 同值） |
| F3 | 点被点亮的目标＝永久跳过 | 在聚光区点侧栏：URL 未变、遮罩消失、`ai-course:onboarding:done:v1:4`＝`'1'` | `click_on_highlighted_target`、`flag_after_click`、`after_reload_auto_show`（`readings.json`）；`B_click_lit_element`（`audit-replay-2.log`） |
| F4 | 重开入口分叉且位置与文案不符 | 侧栏底部 `.side-user`（实测 top 834／left 0）弹出的菜单在 **top 52／left 1276**；顶栏 `.user-btn` 的菜单只有「个人中心／退出登录」，**无「新手引导」** | `3_side_user_menu`、`4_topbar_user_menu_items`（`audit-replay-3.log`）；`E_user_btn_location`、`E_menu_items`（`audit-replay-2.log`） |
| F5 | `aria-modal="true"` 但无焦点陷阱 | Tab 八步轨迹：跳过→下一步→**BODY**→顶栏按钮→搜索 INPUT→通知→`.user-btn`→背景「加入课程」；`Shift+Tab` 停在 `.user-btn` | `tab_escape`、`shift_tab`（`readings.json`） |
| F6 | 四个出口写同一个值，完成率不可测 | `skip()` 与 `finish()` 函数体逐字相同；值恒为 `'1'`；无 emit／无请求；`backend/src` 与 `python-services` 内 `onboarding` 零命中 | `flag_after_click`（`ai-course:onboarding:done:v1:4`＝`'1'`，`readings.json`）；函数体相同与零命中为代码面事实，无读数键 |
| F7 | 中途退出＝永久完成且不保存进度 | 第 3 步按 Esc → 写 `'1'` → 刷新后不再弹；`show()` 硬置 `index=0` | `mid_way_escape`（`readings.json`） |
| F8 | 5 步里只有 2 步指向元素，0 步指向激活动作 | 逐步骤实测：`spotlight` 仅第 2、3 步为 true；第 1、4、5 步为居中卡片 | `per_step`（`readings.json`） |
| F9 | 窄屏同样溢出 | 375×667 下第 2 步卡片 `bottom` 923 > 视口 667；卡片宽 320 为硬编码常量 | `narrow_375_step2_sidebar_target`、`narrow_375_step1`（`readings.json`） |
| F10 | 回归锁缺失 | 单测喂**假** `.sidebar`；`e2e/` 无任何断言引导本身的用例（只有 `dismissOnboarding` 把它点掉）；视觉锚点表无引导格 | 代码面事实，无读数键 |
| F11 | 全局快捷键被存在性守卫掐死（A/B 实测） | 常驻节点 `<div class="el-overlay el-modal-dialog" style="display:none">`；**它在场时按 Ctrl+K 焦点停在 `BODY`，移除该节点后再按，焦点落到 `INPUT.gs-input`** | `1_persistent_el_overlay`、`2_hotkey_ab`（`audit-replay-3.log`） |
| F12 | 组件头注释"受离线依赖安装限制"已过期 | `npm view driver.js version` → **1.10.2**，registry 可达（exit 0） | 转录自 spec §1，本次未重跑，无读数键 |

## 关键截图（3 张，已入库）

- `onboarding-audit-2026-10-21/07-step2-fullpage.png` —— 第 2 步整页：气泡整体落在视口下方（F1）。
- `onboarding-audit-2026-10-21/11-dark-step2.png` —— 暗色主题第 2 步：压暗不可见（F2）与聚光区点击即跳过（F3）。
- `onboarding-audit-2026-10-21/03-step-3.png` —— 第 3 步：焦点轨迹与中途 Esc 的现场（F5／F7）。

反证不成立／未测（**禁止当结论引用**）：折叠侧栏后入口仍在（原怀疑"入口消失"不成立）；
浅色主题下压暗是否可见未测（两态读数同为 dark，主题键未改成功）；330px 下第 2 步溢出是推算。
