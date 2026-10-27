# 新人引导改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `OnboardingGuide.vue` 从"5 步产品介绍"改造成"走完就会用第一件事"的三端激活引导，并收掉它牵连出的两处壳层缺陷（用户菜单分叉、全局快捷键被存在性守卫掐死）。

**Architecture:** 五个单一职责单元——纯数据步骤（`src/onboarding/steps/*`）、锚点注册表（`anchors.js`）、几何（`geometry.js` ＋ `composables/useSpotlightOverlay.js`）、卡片组件、本地契约（`state.js`）；壳层另立两处收口（`UserMenu.vue` 单一来源、`hasOpenOverlay` 改判可见）。几何层是可替换边界：将来若换 driver.js 只动 `useSpotlightOverlay`。

**Tech Stack:** Vue 3.5.43 plain-JS SFC、Vitest 3.2.7 ＋ @vue/test-utils 2.5.1、Playwright 1.63.0、Element Plus 2.14.7。**本批不新增任何依赖。**

**Spec:** `docs/superpowers/specs/2026-10-21-onboarding-redesign-design.md`（落盘于 commit `05386b7`）。
执行者必须先读 spec 的 §1（12 条改前实测读数）、§2（7 条裁定）、§4（9 条准出判据）——本计划从那份 spec 论证下来，不是独立文档。

## Global Constraints

- **前置门禁（未满足不得开工）**：视觉升级 Phase 0 准入条件 **(a) 跨套件数据隔离必须已解除**，且出路已由项目主选定。
  推荐出路①（纯测试面）＝把三格消费的未钉端点补进 `frontend/e2e/visual/visual-helpers.ts` 的 ANCHORS 表 `pinPaths`
  （`student-dashboard` 现值只有 `['/api/student/analytics/visualization']`，缺 `/api/student/dashboard`）。
  **本计划不含这一改动**（它属视觉升级批次）。开工前核对：
  `cd frontend && grep -n "pinPaths" e2e/visual/visual-helpers.ts | head -8` 与视觉升级 spec §11.4 那条 🔴 记录。
  未解除就**停下来报告**，不要绕、不要顺手替它做决定。
- **不新增像素基线**（spec §0／§3.5）。视觉判据只用非像素两把尺：合成色差 ≥ 24/255、rect 整卡可见。
- **不新增依赖**；不改 `package.json` 任何 `^` 范围（收紧是逐条拍板的事）。
- **新单元一律 `.js`，不写 `.ts`**：`tsconfig.json` 的 include 是 `src/**/*.ts` ＋ `src/**/*.vue`，`.js` 不进 vue-tsc
  ⇒ **不碰 402 条棘轮**。代价写明：这五个新单元没有类型检查，只有单测。
- **棘轮只许下降**：`node scripts/check-types.cjs` 必须仍是 402 条／90 文件或更少。`vue-tsc --noEmit` 永不绿，不许拿它当判据。
- **禁写"全绿"**：`npm run lint` 与 `format:check` 的 glob 只含 `src/**`，对 `e2e/` 覆盖面为 0。报告只写当次实跑读数并注明覆盖面。
- **vitest 必须在 `frontend/` 目录下跑**（`fileParallelism:false`，从仓库根跑解析不到配置）。
- **每棒一条 LEDGER 行**追加到 `docs/visual-upgrade/LEDGER.md`：**9 列、单元格内禁竖线、一行物理行**；
  改完必跑四条自校（列数／表行数只增／总行数只增／断行检测），命令原文在视觉升级 spec §11.7 与 LEDGER「表内书写规则」节。
- **不推送**：本地 `main`，上游 `origin/leyon` 不动；推送要项目主单独点头。
- **会话夹具含真令牌**：`frontend/e2e/visual/.auth/*.json` 保持 gitignore，任何棒不得打印其内容。
- **登录限流**：`ratelimit.login` 默认 10 次/60 秒/IP（DR-85）。e2e 一律复用 `storageState`，不得逐条重登。
- **跑过视觉套件后必须还原观察图**：`git checkout -- docs/visual-upgrade/evidence/P0a/motion/`（那 6 张每次跑都重写）。

---

## 判据 → 任务对照（spec §4 九条，缺一不叫改完）

| 判据 | 落在哪一棒 |
| --- | --- |
| 1 四档视口整卡可见 ＋ 删光候选必红 | Task 2 |
| 2 暗色合成色差 ≥ 24/255 | Task 2（token＋单测）／Task 5（真浏览器量法） |
| 3 中间步点击前进不写终态；激活动作步真执行并写 done | Task 3（实现）／Task 5（e2e） |
| 4 Tab 12 次仍在卡内 | Task 3 |
| 5 三态可区分、paused 刷新后从第 n 步续 | Task 3（契约）／Task 5（e2e） |
| 6 两处菜单 items 逐字相同且含「新手引导」 | Task 4 |
| 7 快捷键 A/B 反向 | Task 4 |
| 8 锚点集合相等；改 `item.key` 或删绑定必红 | Task 1 |
| 9 三端 e2e ＋ 三项门禁实跑读数入台账 | Task 5 |

---

### Task 1: 改前读数入库 ＋ 锚点注册表 ＋ 三端 steps 骨架（含排序不变式）

**Files:**
- Create: `docs/visual-upgrade/evidence/onboarding-audit-2026-10-21.md` ＋ 目录 `onboarding-audit-2026-10-21/`（3 张图）
- Create: `frontend/src/onboarding/anchors.js`
- Create: `frontend/src/onboarding/steps/loader.js`
- Create: `frontend/src/onboarding/steps/student.js`／`teacher.js`／`admin.js`
- Create: `frontend/src/onboarding/__tests__/anchors.test.js`／`steps.test.js`／`steps.keys.test.js`
- Modify: `frontend/src/layouts/components/AppSidebar.vue`（三处循环各加一行 `:data-onboard`；`.side-user` 加一行）
- Modify: `frontend/src/views/student/components/SdCourseGrid.vue`（`div.course-card.join-card` 加一行）

**Interfaces:**
- Consumes: 无（地基棒）。
- Produces:
  - `isRegisteredAnchor(key: string): boolean`、`resolveAnchor(key: string, root?: Document): HTMLElement | null`、`ALL_ANCHOR_KEYS: string[]`（`anchors.js`）
  - `loadSteps(role: 'student'|'teacher'|'admin'): Step[]`、`validateSteps(steps: Step[]): { ok: true }`（抛错即不合法）
  - `Step = { id: string, title: string, desc: string, anchor?: string, scope: 'shell'|'page', action?: { type: 'navigate', to: string } | { type: 'click-anchor' }, expect?: string }`
  - DOM 契约：`[data-onboard="<key>"]`；导航项键形如 `nav:<item.key>`，`item.key` 来自 `src/layouts/menus.ts`

- [ ] **Step 1: 把改前读数从 gitignore 目录转录进仓库**

Run: `ls frontend/test-results/onboarding-audit-2026-10-21/`
Expected: `readings.json` ＋ 17 张 `*.png`。若目录已被清，**停下来报告**——必须重跑取证脚本才能补，
不得凭记忆重写读数。

```bash
mkdir -p docs/visual-upgrade/evidence/onboarding-audit-2026-10-21
cp frontend/test-results/onboarding-audit-2026-10-21/07-step2-fullpage.png \
   frontend/test-results/onboarding-audit-2026-10-21/11-dark-step2.png \
   frontend/test-results/onboarding-audit-2026-10-21/03-step-3.png \
   docs/visual-upgrade/evidence/onboarding-audit-2026-10-21/
```

写 `docs/visual-upgrade/evidence/onboarding-audit-2026-10-21.md`：内容＝spec §1 那张 12 行表逐条读数，
每条后面附 `readings.json` 里对应的 key 名（`A_step2_offscreen`、`1_persistent_el_overlay`、`2_hotkey_ab`、
`3_side_user_menu`、`4_topbar_user_menu_items`、`6_step2_card_at_vh_900/1080/1440`、`narrow_375_step2_sidebar_target`、
`tab_escape`、`click_on_highlighted_target`、`flag_after_click`、`mid_way_escape`）＋ 三张图的相对路径。
末尾**原样保留** spec §1 那段"反证不成立／未测"三条。

- [ ] **Step 2: 写 anchors 的失败测试**

`frontend/src/onboarding/__tests__/anchors.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { ALL_ANCHOR_KEYS, isRegisteredAnchor, resolveAnchor } from '../anchors'
import { loadSteps } from '../steps/loader'

// 反例喂合成 DOM，不读活文件（Task 13 I-1 的教训：断言不许钉在会被改的活文件上）
describe('anchors 注册表', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('合成 DOM 里 data-onboard 命中即返回该元素', () => {
    document.body.innerHTML = '<div data-onboard="join-course" id="x"></div>'
    expect(resolveAnchor('join-course')?.id).toBe('x')
  })

  it('nav: 前缀锚点同样按 data-onboard 解析', () => {
    document.body.innerHTML = '<div data-onboard="nav:ai-assistant" id="y"></div>'
    expect(resolveAnchor('nav:ai-assistant')?.id).toBe('y')
  })

  it('未注册的 key 直接抛，不返回 null（静默 null 会让锚点漂移无人知）', () => {
    expect(() => resolveAnchor('nope')).toThrow(/未注册锚点/)
  })

  it('isRegisteredAnchor 认非前缀键与 nav: 前缀，不认野键', () => {
    expect(isRegisteredAnchor('side-user')).toBe(true)
    expect(isRegisteredAnchor('nav:whatever')).toBe(true)
    expect(isRegisteredAnchor('topbar')).toBe(false)
  })

  it('三端 steps 用到的每个锚点都已注册', () => {
    const used = new Set()
    for (const role of ['student', 'teacher', 'admin']) {
      loadSteps(role).forEach((s) => s.anchor && used.add(s.anchor))
    }
    const missing = [...used].filter((k) => !isRegisteredAnchor(k))
    expect(missing, `steps 引用了未注册锚点：${missing.join(', ')}`).toEqual([])
  })

  it('注册了但没人用的裸键必红（要么删、要么有步骤用它）', () => {
    const used = new Set()
    for (const role of ['student', 'teacher', 'admin']) {
      loadSteps(role).forEach((s) => s.anchor && used.add(s.anchor))
    }
    const unused = ALL_ANCHOR_KEYS.filter((k) => !used.has(k))
    expect(unused, `未使用的锚点：${unused.join(', ')}`).toEqual([])
  })
})
```

- [ ] **Step 3: 跑它确认失败**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/anchors.test.js`
Expected: FAIL — `Cannot find module '../anchors'`（或 `'../steps/loader'`）。

- [ ] **Step 4: 写 anchors.js**

```js
/**
 * 锚点注册表：引导唯一允许用来找元素的地方。
 *
 * 为什么不用 CSS 类名当锚：`.sidebar`／`.topbar` 这类布局类名会被视觉升级批次改，
 * 而改红了没人发现——审计实测旧单测喂的是假 `.sidebar`，真类名改掉零告警。
 * 这里把"锚点键 → 元素"关进一张表：类名漂移只可能红在一处，且有集合相等断言守着。
 */
const NAV_PREFIX = 'nav:'

/** 裸键 → 选择器。新增裸键必须同时被某一步用到，否则 anchors.test.js 红。 */
const BARE = {
  'side-user': '[data-onboard="side-user"]',
  'join-course': '[data-onboard="join-course"]',
}

export const ALL_ANCHOR_KEYS = Object.keys(BARE)

export function isRegisteredAnchor(key) {
  if (typeof key !== 'string') return false
  return key.startsWith(NAV_PREFIX) || Object.prototype.hasOwnProperty.call(BARE, key)
}

export function resolveAnchor(key, root = document) {
  if (!isRegisteredAnchor(key)) {
    throw new Error(`未注册锚点：${String(key)}（加进 BARE 或改 steps，不要静默返回 null）`)
  }
  const selector = key.startsWith(NAV_PREFIX) ? `[data-onboard="${CSS.escape(key)}"]` : BARE[key]
  return root.querySelector(selector)
}
```

- [ ] **Step 5: 写 steps loader 的失败测试（排序不变式）**

`frontend/src/onboarding/__tests__/steps.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { validateSteps, loadSteps } from '../steps/loader'

const step = (over) => ({ id: 'x', title: 't', desc: 'd', scope: 'shell', ...over })

describe('steps 排序不变式（spec §3.4）', () => {
  it('page 锚点只能是最后一步', () => {
    expect(() => validateSteps([step({ anchor: 'join-course', scope: 'page' }), step({})])).toThrow(
      /page 锚点只能是最后一步/,
    )
  })

  it('page 步骤在末位时通过', () => {
    expect(() => validateSteps([step({}), step({ anchor: 'join-course', scope: 'page' })])).not.toThrow()
  })

  it('两个 page 步骤必红（哪怕第二个在末位）', () => {
    expect(() =>
      validateSteps([
        step({ anchor: 'join-course', scope: 'page' }),
        step({ anchor: 'side-user', scope: 'page' }),
      ]),
    ).toThrow(/page 锚点只能是最后一步/)
  })

  it('未注册锚点红', () => {
    expect(() => validateSteps([step({ anchor: 'nav:nope-not-registered' })])).not.toThrow()
    // nav: 前缀天然"已注册"，真正的漂移由 steps.keys.test.js 按 menus.ts 兜住
    expect(() => validateSteps([step({ anchor: 'bogus' })])).toThrow(/未注册锚点/)
  })

  it('少于两步红（欢迎＋激活动作是下限）', () => {
    expect(() => validateSteps([step({})])).toThrow(/至少两步/)
  })

  it('三端真实 steps 全部通过，且每端恰好一个激活动作步、且它在末位', () => {
    for (const role of ['student', 'teacher', 'admin']) {
      const steps = loadSteps(role)
      expect(() => validateSteps(steps)).not.toThrow()
      const acts = steps.filter((s) => s.action)
      expect(acts.length, `${role} 端激活动作步数`).toBe(1)
      expect(steps[steps.length - 1].action, `${role} 端激活动作必须在最后一步`).toBeTruthy()
    }
  })
})
```

- [ ] **Step 6: 跑它确认失败**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/steps.test.js`
Expected: FAIL — `Cannot find module '../steps/loader'`。

- [ ] **Step 7: 写 loader 与三份 steps**

`frontend/src/onboarding/steps/loader.js`:

```js
import { isRegisteredAnchor } from '../anchors'
import student from './student'
import teacher from './teacher'
import admin from './admin'

const BY_ROLE = { student, teacher, admin }

/** 排序不变式：page 锚点只能是最后一步；锚点必须已注册 */
export function validateSteps(steps) {
  if (!Array.isArray(steps) || steps.length < 2) throw new Error('steps 至少两步（欢迎＋激活动作）')
  steps.forEach((s, i) => {
    if (s.anchor && !isRegisteredAnchor(s.anchor)) {
      throw new Error(`未注册锚点：${s.anchor}（第 ${i + 1} 步）`)
    }
    if (s.scope === 'page' && i !== steps.length - 1) {
      throw new Error(`page 锚点只能是最后一步（第 ${i + 1} 步用了 ${s.anchor}）`)
    }
  })
  return { ok: true }
}

export function loadSteps(role) {
  const steps = BY_ROLE[role]
  if (!steps) throw new Error(`无该角色步骤配置：${role}`)
  return steps
}
```

`frontend/src/onboarding/steps/student.js`:

```js
export default [
  {
    id: 'welcome',
    title: '这里是学生端',
    desc: '课程、作业、考试和 AI 助手都在左边那条栏里。下面四步，每步点一下高亮的地方就能走完。',
    scope: 'shell',
  },
  {
    id: 'ai',
    title: 'AI 助手',
    desc: '上课没听懂的地方直接在这里问，它会带着这门课的知识图谱来回答。',
    anchor: 'nav:ai-assistant',
    scope: 'shell',
    expect: '进入 AI 助手页',
  },
  {
    id: 'graph',
    title: '知识图谱',
    desc: '点这里看这门课的知识点关系图，能看出你哪一块还没掌握。',
    anchor: 'nav:graph',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '这里就是你的账号，点开有「新手引导」，忘了路径随时回来重走一遍。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '先加入一门课',
    desc: '点这里，输入老师给的邀请码。加入之后这门课的作业、考试和进度才会出现。',
    anchor: 'join-course',
    scope: 'page',
    action: { type: 'click-anchor' },
    expect: '打开加入课程弹窗，引导结束',
  },
]
```

`frontend/src/onboarding/steps/teacher.js`:

```js
export default [
  {
    id: 'welcome',
    title: '这里是教师端',
    desc: '左边是你要用的几件事：出题考试、看成绩、管课堂、用 AI。下面走一遍。',
    scope: 'shell',
  },
  {
    id: 'assess',
    title: '试题与考试',
    desc: '从这里建题、组卷、发布考试。题库和试卷库在它下面两级。',
    anchor: 'nav:t-assess',
    scope: 'shell',
  },
  {
    id: 'grades',
    title: '成绩总览',
    desc: '点这里看全班成绩，导出的表就是这一页上那份。',
    anchor: 'nav:t-grades',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '左下角你的账号里放着「新手引导」，随时能重走。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '用 AI 出一批题',
    desc: '点这里进 AI 教学中心，选知识点就能生成题目草稿，改完再入库。',
    anchor: 'nav:t-ai',
    scope: 'shell',
    action: { type: 'navigate', to: '/teacher/ai-center' },
    expect: '进入 AI 教学中心，引导结束',
  },
]
```

`frontend/src/onboarding/steps/admin.js`:

```js
export default [
  {
    id: 'welcome',
    title: '这里是管理端',
    desc: '左边按"治理—资源—系统"三段排。先看三处，最后一步带你进审计。',
    scope: 'shell',
  },
  {
    id: 'governance',
    title: '治理大盘',
    desc: '全校教学运行状态在这一页，异常从这里的卡片点进去。',
    anchor: 'nav:governance',
    scope: 'shell',
  },
  {
    id: 'users',
    title: '用户与账号',
    desc: '开课、换老师、停账号都在这。改角色权限要到下一项。',
    anchor: 'nav:users',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '左下角你的账号里有「新手引导」，随时重走。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '先看审计',
    desc: '点这里进审计——谁在什么时候改过什么都在这一张表里。',
    anchor: 'nav:audit',
    scope: 'shell',
    action: { type: 'navigate', to: '/admin/audit' },
    expect: '进入审计页，引导结束',
  },
]
```

- [ ] **Step 8: 跑这两个文件确认通过**

Run: `cd frontend && npx vitest run src/onboarding`
Expected: PASS（anchors 6 条＋steps 6 条）。

- [ ] **Step 9: 写"锚点键必须真的存在于 menus.ts"的漂移测试（判据 8 的第一半）**

`frontend/src/onboarding/__tests__/steps.keys.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { STUDENT_SECTIONS, TEACHER_SECTIONS, ADMIN_SECTIONS } from '@/layouts/menus'
import { loadSteps } from '../steps/loader'

const flatten = (sections) => {
  const keys = new Set()
  const walk = (items) => items.forEach((i) => (keys.add(i.key), i.children && walk(i.children)))
  sections.forEach((s) => walk(s.items))
  return keys
}

const BY_ROLE = {
  student: flatten(STUDENT_SECTIONS),
  teacher: flatten(TEACHER_SECTIONS),
  admin: flatten(ADMIN_SECTIONS),
}

describe('导航锚点键必须真的来自 menus.ts', () => {
  for (const role of ['student', 'teacher', 'admin']) {
    it(`${role} 端每个 nav: 锚点都能在 menus.ts 找到对应 key`, () => {
      const used = loadSteps(role)
        .map((s) => s.anchor)
        .filter((a) => a?.startsWith('nav:'))
        .map((a) => a.slice(4))
      expect(used.length).toBeGreaterThanOrEqual(2)
      const ghost = used.filter((k) => !BY_ROLE[role].has(k))
      expect(ghost, `menus.ts 里没有这些 key：${ghost.join(', ')}——改 steps 或补菜单，不要留幽灵锚点`).toEqual([])
    })
  }
})
```

Run: `cd frontend && npx vitest run src/onboarding/__tests__/steps.keys.test.js`
Expected: PASS。**变异自证**：临时把 `student.js` 里 `nav:graph` 改成 `nav:graphX` ⇒ 必红 ⇒ 还原。
（这条就是判据 8 里"改 `item.key` 必红"那一半。）

- [ ] **Step 10: 埋 `data-onboard`（三处循环各一行，不要逐个菜单项手写）**

`AppSidebar.vue` 一级 `div.nav-item` 开标签内加：

```
            class="nav-item"
            :data-onboard="`nav:${item.key}`"
```

二级 `div.nav-sub-item` 加 `:data-onboard="`nav:${child.key}`"`，三级 `div.nav-leaf` 加
`:data-onboard="`nav:${leaf.key}`"`；`div.side-user` 加 `data-onboard="side-user"`。
`SdCourseGrid.vue` 的 `<div class="course-card join-card" @click="showJoin = true">` 加 `data-onboard="join-course"`。

- [ ] **Step 11: 写"绑定不许被删"的源码级守卫（判据 8 的第二半）**

`frontend/src/onboarding/__tests__/anchors.bindings.test.js`:

```js
import { describe, it, expect } from 'vitest'
import fs from 'node:fs'

// 剥注释后再匹配：否则"注释里提了一句"也能把守卫喂绿（本仓已犯过四次同型错）
const strip = (s) =>
  s
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')

const read = (p) => strip(fs.readFileSync(new URL(p, import.meta.url), 'utf8'))

describe('data-onboard 绑定必须还在壳层里', () => {
  it('侧栏三处循环各自绑定 nav:key，用户区绑定 side-user', () => {
    const src = read('../../layouts/components/AppSidebar.vue')
    expect((src.match(/data-onboard=/g) || []).length, 'AppSidebar 里的 data-onboard 绑定数').toBeGreaterThanOrEqual(4)
    expect(src).toMatch(/:data-onboard="`nav:\$\{item\.key\}`"/)
    expect(src).toMatch(/:data-onboard="`nav:\$\{child\.key\}`"/)
    expect(src).toMatch(/:data-onboard="`nav:\$\{leaf\.key\}`"/)
    expect(src).toMatch(/data-onboard="side-user"/)
  })

  it('学生端加入课程卡绑定 join-course', () => {
    expect(read('../../views/student/components/SdCourseGrid.vue')).toMatch(/data-onboard="join-course"/)
  })
})
```

Run: `cd frontend && npx vitest run src/onboarding/__tests__/anchors.bindings.test.js`
Expected: PASS。**变异自证**：把 `AppSidebar.vue` 一级那行删掉 ⇒ 必红 ⇒ 还原。

- [ ] **Step 12: 全量单测 ＋ 棘轮 ＋ LEDGER ＋ 提交**

```bash
cd frontend && npm run test 2>&1 | tail -5
node scripts/check-types.cjs 2>&1 | tail -2
```

Expected: 用例数比改前多 4 文件／≥19 条；棘轮读数 **≤ 402 条／90 文件**（新文件是 `.js`，不该进 vue-tsc）。
LEDGER 追加 `P0c-ONB-01`：改前列抄 spec §1 的 F1／F4／F8／F10 读数与 evidence 路径；操作列＝本棒十二步；
结论列＝保留；验证列＝上面两条命令 ＋ 两次变异。

```bash
git add docs/visual-upgrade/evidence/onboarding-audit-2026-10-21.md docs/visual-upgrade/evidence/onboarding-audit-2026-10-21 \
  frontend/src/onboarding frontend/src/layouts/components/AppSidebar.vue \
  frontend/src/views/student/components/SdCourseGrid.vue docs/visual-upgrade/LEDGER.md
git commit -m "feat(onboarding): 锚点注册表＋三端 steps 骨架（page 排序不变式与 menus.ts 漂移测试入库）＋改前读数转录"
```

---

### Task 2: 几何——四块矩形拼洞、视口感知定位、暗色可见性

**Files:**
- Create: `frontend/src/onboarding/geometry.js`
- Create: `frontend/src/composables/useSpotlightOverlay.js`
- Create: `frontend/src/onboarding/__tests__/geometry.test.js`／`scrim.contrast.test.js`
- Modify: `frontend/src/styles/theme.css`（新增 token）
- Modify: `frontend/src/components/OnboardingGuide.vue` 的 `<style>` 段（`.ob-scrim`／`.ob-ring`，删 `box-shadow: 0 0 0 9999px` 与 `pointer-events: none`）

**Interfaces:**
- Consumes: Task 1 的 `resolveAnchor`。
- Produces:
  - `PLACEMENT_CANDIDATES: string[]`（长度 ≥ 4）
  - `computeCardPlacement({ targetRect, cardSize, viewport, gap?, margin? }): { top, left, placement } | null`
  - `computeScrimRects(targetRect, viewport, pad?): Array<{ top, left, width, height }>`（恒 4 块，可能退化为 0 面积）
  - `useSpotlightOverlay({ getTarget, cardRef, onAnchorMiss }) → { scrimRects, ringStyle, cardStyle, placement, anchorMiss, refresh, follow }`
  - CSS 变量：`--onboarding-scrim`、`--onboarding-ring`、`--onboarding-glow`（暗段独有）、`--z-onboarding`

- [ ] **Step 1: 写 geometry 的失败测试（负读数必配正读数）**

`frontend/src/onboarding/__tests__/geometry.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { PLACEMENT_CANDIDATES, computeCardPlacement, computeScrimRects } from '../geometry'

const VP = (width, height) => ({ width, height })
const CARD = { width: 320, height: 245 }
const visible = (box, vp, margin = 8) =>
  box.top >= margin &&
  box.left >= margin &&
  box.top + CARD.height <= vp.height - margin &&
  box.left + CARD.width <= vp.width - margin

describe('computeCardPlacement', () => {
  it('候选位序至少 4 个（变异检测器：把候选删光必红）', () => {
    expect(PLACEMENT_CANDIDATES.length).toBeGreaterThanOrEqual(4)
  })

  // 正读数：矮目标且下方有空间 ⇒ below
  it('目标够矮、下方有空间时 placement 为 below', () => {
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 600, width: 900, height: 56 },
      cardSize: CARD,
      viewport: VP(1440, 900),
    })
    expect(got.placement).toBe('below')
    expect(got.top).toBeGreaterThan(56)
  })

  // 改前缺陷 F1 的形状：满高侧栏 ⇒ 旧算法把卡片推到 912（视口外）
  it.each([900, 1080, 1440])('视口高 %i 时满高侧栏的卡片仍整卡可见', (h) => {
    const vp = VP(1440, h)
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 0, width: 256, height: h },
      cardSize: CARD,
      viewport: vp,
    })
    expect(got, '无可用候选位（改前就是这一档丢的）').not.toBeNull()
    expect(visible(got, vp), JSON.stringify(got)).toBe(true)
    expect(['right', 'bar-bottom']).toContain(got.placement)
  })

  it('375×667 窄屏仍整卡可见（改前 F9：bottom 923 > 667）', () => {
    const vp = VP(375, 667)
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 0, width: 256, height: 667 },
      cardSize: CARD,
      viewport: vp,
    })
    expect(got).not.toBeNull()
    expect(visible(got, vp), JSON.stringify(got)).toBe(true)
  })

  it('卡片放不下时返回 null（调用方降级为居中且不阻塞点击）', () => {
    expect(
      computeCardPlacement({
        targetRect: { top: 0, left: 0, width: 60, height: 60 },
        cardSize: { width: 900, height: 400 },
        viewport: VP(320, 480),
      }),
    ).toBeNull()
  })
})

describe('computeScrimRects', () => {
  it('目标在中间 ⇒ 四块，面积和＝视口减洞', () => {
    const vp = VP(1000, 800)
    const rects = computeScrimRects({ top: 300, left: 300, width: 200, height: 100 }, vp, 6)
    expect(rects.length).toBe(4)
    const area = rects.reduce((s, r) => s + r.width * r.height, 0)
    expect(area).toBe(1000 * 800 - (200 + 12) * (100 + 12))
  })

  it('目标贴满视口 ⇒ 不留任何有面积的压暗块', () => {
    const rects = computeScrimRects({ top: 0, left: 0, width: 1000, height: 800 }, VP(1000, 800), 6)
    expect(rects.every((r) => r.width <= 0 || r.height <= 0)).toBe(true)
  })
})
```

- [ ] **Step 2: 跑它确认失败**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/geometry.test.js`
Expected: FAIL — `Cannot find module '../geometry'`。

- [ ] **Step 3: 写 geometry.js**

```js
/**
 * 纯几何，不碰 DOM。改前 F1／F9 的根在这里：旧实现把气泡一律放"目标下方 + 12px"，
 * 翻转条件写的是 `top > 240`，而满高侧栏 top=0 ⇒ 条件永不成立 ⇒ 气泡被推到 912（视口外）。
 */
export const PLACEMENT_CANDIDATES = ['below', 'above', 'right', 'left', 'bar-bottom']

const fits = (box, cardSize, vp, margin) =>
  box.top >= margin &&
  box.left >= margin &&
  box.top + cardSize.height <= vp.height - margin &&
  box.left + cardSize.width <= vp.width - margin

export function computeCardPlacement({ targetRect: t, cardSize: c, viewport: v, gap = 12, margin = 8 }) {
  const centerX = t.left + t.width / 2 - c.width / 2
  const midY = t.top + Math.min(t.height, v.height) / 2 - c.height / 2
  const candidates = {
    below: { top: t.top + t.height + gap, left: centerX },
    above: { top: t.top - gap - c.height, left: centerX },
    right: { top: midY, left: t.left + t.width + gap },
    left: { top: midY, left: t.left - gap - c.width },
    'bar-bottom': { top: v.height - margin - c.height, left: v.width / 2 - c.width / 2 },
  }
  for (const name of PLACEMENT_CANDIDATES) {
    const box = candidates[name]
    const clamped = { ...box, left: Math.min(Math.max(box.left, margin), v.width - margin - c.width) }
    if (fits(clamped, c, v, margin)) return { top: clamped.top, left: clamped.left, placement: name }
  }
  return null
}

/** 洞外压暗＝四块矩形（上/下/左/右）。洞本身没有元素 ⇒ 点击天然落到真实控件上。 */
export function computeScrimRects(t, v, pad = 6) {
  const hole = {
    top: Math.max(0, t.top - pad),
    left: Math.max(0, t.left - pad),
    right: Math.min(v.width, t.left + t.width + pad),
    bottom: Math.min(v.height, t.top + t.height + pad),
  }
  const mid = Math.max(0, hole.bottom - hole.top)
  return [
    { top: 0, left: 0, width: v.width, height: hole.top },
    { top: hole.bottom, left: 0, width: v.width, height: Math.max(0, v.height - hole.bottom) },
    { top: hole.top, left: 0, width: hole.left, height: mid },
    { top: hole.top, left: hole.right, width: Math.max(0, v.width - hole.right), height: mid },
  ]
}
```

- [ ] **Step 4: 跑它确认通过**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/geometry.test.js`
Expected: PASS（8 条）。

- [ ] **Step 5: 加 token（判据 2 的地基）**

`theme.css` 明段（`:root`）加：

```css
  /* 明：保持改前值，别顺手"统一"成暗色值 */
  --onboarding-scrim: rgba(10, 14, 24, 0.45);
  --onboarding-ring: 2px solid var(--accent);
```

暗段（`[data-theme="dark"]`）加：

```css
  /* 暗：改前用同一个 rgba(10,14,24,.45) 压 rgb(15,20,25) 的近黑底色，合成后只差 3/255 ⇒ 等于没压。
     0.66 的黑压上去合成后单通道差 > 24/255（判据 2 阈值），且高于本仓已知盲区 8/255。 */
  --onboarding-scrim: rgba(0, 0, 0, 0.66);
  --onboarding-glow: 0 0 0 4px rgba(193, 59, 48, 0.28);
```

`--z-toast: 160;` 之后加（明段即可）：

```css
  /* 引导层必须压过 EP 弹层：EP 默认 z-index 从 2000 起（读已安装的
     element-plus/es/hooks/use-z-index/index.mjs，不引版本号） */
  --z-onboarding: 3000;
```

- [ ] **Step 6: 写 scrim 合成色差的入库断言**

`frontend/src/onboarding/__tests__/scrim.contrast.test.js`:

```js
import { describe, it, expect } from 'vitest'
import fs from 'node:fs'

const css = fs.readFileSync(new URL('../../styles/theme.css', import.meta.url), 'utf8')
const rgb = (s) => {
  const m = /rgba?\(([^)]+)\)/.exec(s)
  return m ? m[1].split(',').map((x) => Number(x.trim())) : null
}
const hex = (s) => {
  const m = /^#([0-9a-f]{6})$/i.exec(s.trim())
  return m ? [0, 2, 4].map((i) => parseInt(m[1].slice(i, i + 2), 16)) : null
}
const varOf = (block, name) => {
  const m = new RegExp(`--${name}:\\s*([^;]+);`).exec(block)
  return m ? m[1] : null
}

describe('暗色 scrim 可见性（spec §4 判据 2）', () => {
  it('暗色 scrim 压在本体底色上，最大单通道差 ≥ 24/255', () => {
    const dark = css.slice(css.indexOf('[data-theme="dark"]'))
    const scrim = rgb(varOf(dark, 'onboarding-scrim') ?? varOf(css, 'onboarding-scrim'))
    expect(scrim, '没读到 --onboarding-scrim').not.toBeNull()
    const raw = varOf(dark, 'bg-body') ?? varOf(dark, 'bg') ?? varOf(dark, 'surface')
    const body = raw ? rgb(raw) ?? hex(raw) : [15, 20, 25]
    expect(body, `底色变量解析失败：${raw}`).not.toBeNull()
    const a = scrim[3] ?? 1
    const over = scrim.slice(0, 3).map((c, i) => Math.round(c * a + body[i] * (1 - a)))
    const delta = Math.max(...over.map((c, i) => Math.abs(c - body[i])))
    expect(delta, `合成后只差 ${delta}/255（改前实测 3/255），阈值 24`).toBeGreaterThanOrEqual(24)
  })
})
```

Run: `cd frontend && npx vitest run src/onboarding/__tests__/scrim.contrast.test.js`
Expected: PASS。**若因变量名解析不到而红，先 `grep -n "^\s*--bg\|^\s*--surface" src/styles/theme.css` 看真实变量名并改这条的读取顺序——不许改阈值 24。**

- [ ] **Step 7: 写 useSpotlightOverlay（rAF 节流 ＋ scrollIntoView ＋ ring 几何）**

```js
import { ref, computed, onBeforeUnmount } from 'vue'
import { computeCardPlacement, computeScrimRects } from '@/onboarding/geometry'

const FALLBACK = { width: 320, height: 245 }
const PAD = 6

/**
 * 只负责几何：不认锚点键、不读存储、不渲染卡片。
 * getTarget() 由调用方给（Task 3 里它接 anchors.resolveAnchor）。
 */
export function useSpotlightOverlay({ getTarget, cardRef, onAnchorMiss }) {
  const rects = ref([])
  const ring = ref(null)
  const cardStyle = ref({})
  const placement = ref('centered')
  const anchorMiss = ref(false)
  let raf = 0

  function refresh() {
    raf = 0
    const el = getTarget()
    anchorMiss.value = !el
    if (!el) {
      rects.value = []
      ring.value = null
      cardStyle.value = {}
      placement.value = 'centered'
      onAnchorMiss?.()
      return
    }
    const vp = { width: window.innerWidth, height: window.innerHeight }
    const r = el.getBoundingClientRect()
    const targetRect = { top: r.top, left: r.left, width: r.width, height: r.height }
    rects.value = computeScrimRects(targetRect, vp, PAD)
    ring.value = {
      top: targetRect.top - PAD,
      left: targetRect.left - PAD,
      width: targetRect.width + PAD * 2,
      height: targetRect.height + PAD * 2,
    }
    const cardSize = cardRef.value
      ? {
          width: cardRef.value.offsetWidth || FALLBACK.width,
          height: cardRef.value.offsetHeight || FALLBACK.height,
        }
      : FALLBACK
    const box = computeCardPlacement({ targetRect, cardSize, viewport: vp })
    if (box) {
      cardStyle.value = { top: `${box.top}px`, left: `${box.left}px` }
      placement.value = box.placement
    } else {
      cardStyle.value = {}
      placement.value = 'centered'
    }
  }

  function schedule() {
    if (raf) return
    raf = requestAnimationFrame(refresh)
  }

  /** 改前只读 rect、从不滚动 ⇒ 目标在视口外时聚光框跑到屏外 */
  function follow(el) {
    el?.scrollIntoView?.({ block: 'center', inline: 'center', behavior: 'smooth' })
    window.setTimeout(schedule, 320)
  }

  window.addEventListener('resize', schedule)
  window.addEventListener('scroll', schedule, true)
  onBeforeUnmount(() => {
    if (raf) cancelAnimationFrame(raf)
    window.removeEventListener('resize', schedule)
    window.removeEventListener('scroll', schedule, true)
  })

  const ringStyle = computed(() =>
    ring.value
      ? {
          top: `${ring.value.top}px`,
          left: `${ring.value.left}px`,
          width: `${ring.value.width}px`,
          height: `${ring.value.height}px`,
        }
      : {},
  )

  return { scrimRects: computed(() => rects.value), ringStyle, cardStyle, placement, anchorMiss, refresh: schedule, follow }
}
```

- [ ] **Step 8: 样式段改造（`.ob-*` 在 `OnboardingGuide.vue` 的 `<style scoped>` 内）**

删掉 `.ob-overlay` 的 `background: rgba(10,14,24,.45)`／`backdrop-filter`／`has-target` 透明那套，
以及 `.ob-spotlight` 整块（`box-shadow: 0 0 0 9999px …` 与 `pointer-events: none` 就是改前"点不到目标"的成因）。
新增：

```css
.ob-scrim {
  position: fixed;
  background: var(--onboarding-scrim);
  z-index: var(--z-onboarding);
}
.ob-ring {
  position: fixed;
  box-sizing: border-box;
  border: var(--onboarding-ring);
  box-shadow: var(--onboarding-glow, none);
  border-radius: 10px;
  z-index: var(--z-onboarding);
  pointer-events: none;
}
.ob-card {
  position: fixed;
  z-index: calc(var(--z-onboarding) + 1);
}
```

`.ob-overlay` 只留 `position: fixed; inset: 0; z-index: var(--z-onboarding)` 且**透明不拦截**
（拦截交给四块 scrim；这样洞内点击直达真实控件）。

- [ ] **Step 9: 全量单测 ＋ 棘轮 ＋ LEDGER  提交**

```bash
cd frontend && npm run test 2>&1 | tail -5
node scripts/check-types.cjs 2>&1 | tail -2
```

Expected: 新增 2 文件／≥9 条全绿；棘轮 ≤ 402。LEDGER 追加 `P0c-ONB-02`：
改前列＝F1/F9 那组视口读数（912／1092／1452、375 下 bottom 923）；改后列＝四档整卡可见 ＋ 色差阈值断言通过；
验证列＝`npx vitest run src/onboarding` ＋ 两条变异（删候选位序、把 scrim 改回 `.45`）。

```bash
git add frontend/src/onboarding/geometry.js frontend/src/onboarding/__tests__ frontend/src/composables/useSpotlightOverlay.js \
  frontend/src/styles/theme.css frontend/src/components/OnboardingGuide.vue docs/visual-upgrade/LEDGER.md
git commit -m "feat(onboarding): 几何层——四块矩形拼洞＋视口感知定位（删 240/320 魔法数）＋暗色 scrim 可见性判据入库"
```

---

### Task 3: 状态契约 v2 ＋ 出口语义 ＋ 卡片接线（焦点陷阱）

**Files:**
- Create: `frontend/src/onboarding/state.js`
- Create: `frontend/src/onboarding/__tests__/state.test.js`
- Modify: `frontend/src/components/OnboardingGuide.vue`（script 与模板整体重写）
- Create: `frontend/src/components/__tests__/OnboardingGuide.focus.test.js`
- Modify: `frontend/src/components/__tests__/OnboardingGuide.test.ts`（按新形状**重签，不许删**）
- Modify: `frontend/src/layouts/AppShell.vue`（挂载处传 props）

**Interfaces:**
- Consumes: Task 1 `loadSteps`／`resolveAnchor`／`validateSteps`，Task 2 `useSpotlightOverlay`。
- Produces:
  - `readState(userId): { status: 'idle'|'done'|'skipped'|'paused', atStep: number }`
  - `writeStatus(userId, status, atStep): void`（写失败 `console.warn`，不静默）
  - `migrateV1IfPresent(userId): boolean`
  - `onTransition(cb: (from, to) => void): () => void`
  - `KEY_V1_PREFIX`／`KEY_V2_PREFIX`
  - 组件 props：`userId`、`role`；emits：`done`／`paused`／`skipped`；`defineExpose({ show, resume, getIndex, __key, __scrimClick, __anchorClick, __goto, __relocate })`

- [ ] **Step 1: 写 state 的失败测试**

`frontend/src/onboarding/__tests__/state.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  readState,
  writeStatus,
  migrateV1IfPresent,
  onTransition,
  KEY_V1_PREFIX,
  KEY_V2_PREFIX,
} from '../state'

const k1 = (id) => `${KEY_V1_PREFIX}:${id ?? 'anon'}`
const k2 = (id) => `${KEY_V2_PREFIX}:${id ?? 'anon'}`

describe('onboarding state 契约 v2', () => {
  beforeEach(() => localStorage.clear())

  it('空存储 ⇒ idle@0', () => expect(readState(7)).toEqual({ status: 'idle', atStep: 0 }))

  it('三态互不覆盖：paused@2 之后再写 skipped@2，读到的是 skipped', () => {
    writeStatus(7, 'paused', 2)
    writeStatus(7, 'skipped', 2)
    expect(readState(7)).toEqual({ status: 'skipped', atStep: 2 })
  })

  it('v1 有标记且无 v2 ⇒ 迁移成 done，且 v1 键不删（回滚证据）', () => {
    localStorage.setItem(k1(7), '1')
    expect(migrateV1IfPresent(7)).toBe(true)
    expect(readState(7).status).toBe('done')
    expect(localStorage.getItem(k1(7))).toBe('1')
  })

  it('已有 v2 ⇒ 不迁移、不覆盖', () => {
    localStorage.setItem(k1(7), '1')
    writeStatus(7, 'paused', 3)
    expect(migrateV1IfPresent(7)).toBe(false)
    expect(readState(7).atStep).toBe(3)
  })

  it('按账号隔离（DR-52／D2-9 定案口径不变）', () => {
    writeStatus(1, 'done', 5)
    expect(readState(2).status).toBe('idle')
  })

  it('userId 缺失退化为 anon，不压别的账号', () => {
    writeStatus(null, 'done', 5)
    expect(readState(undefined).status).toBe('done')
    expect(readState(1).status).toBe('idle')
  })

  it('写失败不静默（改前 catch{} 把写失败与读失败一样吞掉）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const real = window.localStorage
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: () => null,
        setItem: () => {
          throw new Error('quota')
        },
      },
    })
    writeStatus(7, 'paused', 2)
    expect(warn).toHaveBeenCalledWith(/写入失败/)
    Object.defineProperty(window, 'localStorage', { configurable: true, value: real })
    warn.mockRestore()
  })

  it('onTransition 收到 (from,to)，解绑后不再回调', () => {
    const cb = vi.fn()
    const off = onTransition(cb)
    writeStatus(9, 'paused', 1)
    writeStatus(9, 'done', 5)
    expect(cb).toHaveBeenCalledTimes(2)
    expect(cb.mock.calls[1][0]).toEqual({ status: 'paused', atStep: 1 })
    expect(cb.mock.calls[1][1]).toEqual({ status: 'done', atStep: 5 })
    off()
    writeStatus(9, 'skipped', 3)
    expect(cb).toHaveBeenCalledTimes(2)
  })

  it('脏值（status 不在枚举内）读成 idle，不把坏 JSON 抛给调用方', () => {
    localStorage.setItem(k2(3), JSON.stringify({ status: 'wat', atStep: 9 }))
    expect(readState(3)).toEqual({ status: 'idle', atStep: 0 })
  })
})
```

- [ ] **Step 2: 跑它确认失败**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/state.test.js`
Expected: FAIL — `Cannot find module '../state'`。

- [ ] **Step 3: 写 state.js**

```js
/**
 * 本地契约 v2。三态可区分就是"完成率"的定义本身——改前四个出口往同一个键写 '1'，
 * 于是"完成"与"中途 Esc"在数据上是同一件事（spec §1 F6）。
 */
export const KEY_V1_PREFIX = 'ai-course:onboarding:done:v1'
export const KEY_V2_PREFIX = 'ai-course:onboarding:v2'
const ANON = 'anon'
const STATUSES = ['done', 'skipped', 'paused']

const keyV2 = (userId) => `${KEY_V2_PREFIX}:${userId ?? ANON}`
const keyV1 = (userId) => `${KEY_V1_PREFIX}:${userId ?? ANON}`

const listeners = new Set()

export function onTransition(cb) {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

export function readState(userId) {
  try {
    const raw = localStorage.getItem(keyV2(userId))
    if (!raw) return { status: 'idle', atStep: 0 }
    const parsed = JSON.parse(raw)
    const atStep = Number.isInteger(parsed.atStep) && parsed.atStep >= 0 ? parsed.atStep : 0
    return { status: STATUSES.includes(parsed.status) ? parsed.status : 'idle', atStep }
  } catch {
    return { status: 'idle', atStep: 0 }
  }
}

export function writeStatus(userId, status, atStep) {
  const from = readState(userId)
  try {
    localStorage.setItem(
      keyV2(userId),
      JSON.stringify({ status, atStep, seenAt: new Date().toISOString() }),
    )
  } catch {
    // 读侧可以降级（拿不到就当没看过），写侧不许静默：静默＝"稍后再看"下次没续上而无人知道
    console.warn(`[onboarding] 写入失败（status=${status}@${atStep}），下次将按未看过处理`)
    return
  }
  listeners.forEach((cb) => cb(from, { status, atStep }))
}

export function migrateV1IfPresent(userId) {
  try {
    if (localStorage.getItem(keyV2(userId))) return false
    if (localStorage.getItem(keyV1(userId)) !== '1') return false
    writeStatus(userId, 'done', 0) // 老用户绝不再弹；v1 键保留作回滚证据
    return true
  } catch {
    return false
  }
}
```

- [ ] **Step 4: 跑它确认通过**

Run: `cd frontend && npx vitest run src/onboarding/__tests__/state.test.js`
Expected: PASS（9 条）。

- [ ] **Step 5: 写组件行为的失败测试（判据 3/4/5）**

`frontend/src/components/__tests__/OnboardingGuide.focus.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import { readState } from '@/onboarding/state'

const USER = 4
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/student/dashboard', component: { template: '<div/>' } },
    { path: '/:any(.*)', component: { template: '<div/>' } },
  ],
})

window.HTMLElement.prototype.scrollIntoView = window.HTMLElement.prototype.scrollIntoView || (() => {})

const open = async () => {
  const wrapper = mount(OnboardingGuide, {
    attachTo: document.body,
    props: { userId: USER, role: 'student' },
    global: { plugins: [createPinia(), router] },
  })
  wrapper.vm.show()
  await wrapper.vm.$nextTick()
  await wrapper.vm.$nextTick()
  return wrapper
}

describe('OnboardingGuide 出口语义与焦点', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.innerHTML = '<div data-onboard="side-user"></div><div data-onboard="nav:ai-assistant"></div>'
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  it('Tab 连续 12 次，焦点始终留在卡内（改前第 3 次就逃到 BODY）', async () => {
    const wrapper = await open()
    const card = document.querySelector('.ob-card')
    card.focus()
    for (let i = 0; i < 12; i++) {
      wrapper.vm.__trapStep()
      await wrapper.vm.$nextTick()
      const a = document.activeElement
      expect(card === a || card.contains(a), `第 ${i + 1} 次 Tab 逃到 ${a?.tagName}.${a?.className}`).toBe(true)
    }
  })

  it('Esc ⇒ paused@当前步，不写 done（改前 Esc 就是永久完成）', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(2)
    wrapper.vm.__key({ key: 'Escape' })
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 2 })
  })

  it('点洞外压暗块 ⇒ paused，不写 done', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    await wrapper.vm.__scrimClick()
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 1 })
  })

  it('中间步骤点洞内真元素 ⇒ 前进、不写任何终态、不导航', async () => {
    const wrapper = await open()
    const push = vi.spyOn(router, 'push')
    await wrapper.vm.__goto(1)
    await wrapper.vm.__anchorClick({ preventDefault() {}, stopPropagation() {} })
    expect(wrapper.vm.getIndex()).toBe(2)
    expect(readState(USER).status).toBe('idle')
    expect(push).not.toHaveBeenCalled()
  })

  it('激活动作步点它 ⇒ 真执行、写 done、组件关闭', async () => {
    document.body.innerHTML += '<div data-onboard="join-course" id="join"></div>'
    const wrapper = await open()
    await wrapper.vm.__goto(4)
    const el = document.getElementById('join')
    const clicked = vi.spyOn(el, 'click')
    await wrapper.vm.__anchorClick({ preventDefault() {}, stopPropagation() {} })
    expect(clicked, 'student 端末步是 click-anchor，必须真的点到那个卡').toHaveBeenCalledTimes(1)
    expect(readState(USER).status).toBe('done')
    expect(document.querySelector('.ob-card')).toBeNull()
  })

  it('「稍后再看」写 paused@n；resume() 从第 n 步续', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(3)
    await wrapper.get('button[data-exit="paused"]').trigger('click')
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 3 })
    const second = await open()
    second.vm.resume()
    await second.vm.$nextTick()
    expect(second.vm.getIndex()).toBe(3)
  })

  it('「跳过」写 skipped（与 done 在数据上可区分）', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    await wrapper.get('button[data-exit="skipped"]').trigger('click')
    expect(readState(USER)).toEqual({ status: 'skipped', atStep: 1 })
  })

  it('anchor-miss 留痕且不阻塞：解析不到锚点时 warn 且卡片仍在', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    // 只摘锚点节点，不清 body——清 body 会把挂载中的卡片一起删掉，测的就不是"降级"而是"消失"
    document.querySelectorAll('[data-onboard]').forEach((el) => el.remove())
    await wrapper.vm.__relocate()
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('anchor-miss'))
    expect(document.querySelector('.ob-card')).not.toBeNull()
  })
})
```

- [ ] **Step 6: 跑它确认失败并记录失败条数**

Run: `cd frontend && npx vitest run src/components/__tests__/OnboardingGuide.focus.test.js`
Expected: 全红（组件尚无 props／尚无这些出口）。**把失败条数写进 LEDGER 改前列**——它就是改前基线。

- [ ] **Step 7: 重写 `OnboardingGuide.vue` 的 `<script setup>`**

```js
<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { loadSteps, validateSteps } from '@/onboarding/steps/loader'
import { resolveAnchor } from '@/onboarding/anchors'
import { useSpotlightOverlay } from '@/composables/useSpotlightOverlay'
import { readState, writeStatus, migrateV1IfPresent } from '@/onboarding/state'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  role: { type: String, default: 'student' },
})
const emit = defineEmits(['done', 'paused', 'skipped'])
const router = useRouter()

const steps = computed(() => {
  const list = loadSteps(props.role)
  validateSteps(list)
  return list
})

const visible = ref(false)
const index = ref(0)
const cardEl = ref(null)
const active = computed(() => visible.value)
const isLast = computed(() => index.value >= steps.value.length - 1)
const current = computed(() => steps.value[index.value])

useFocusTrap(cardEl, active)

const { scrimRects, ringStyle, cardStyle, placement, anchorMiss, refresh, follow } = useSpotlightOverlay({
  getTarget: () => (current.value.anchor ? resolveAnchor(current.value.anchor) : null),
  cardRef: cardEl,
  onAnchorMiss: () => {
    // 不静默：锚点解析失败必须留痕（spec §3.2），但卡片仍可用，不能把引导变成一块砖
    console.warn(`[onboarding] anchor-miss:${current.value.anchor ?? '(none)'}（第 ${index.value + 1} 步，降级为居中卡片且不阻塞点击）`)
  },
})

function relocate() {
  nextTick(() => {
    refresh()
    const el = current.value.anchor ? resolveAnchor(current.value.anchor) : null
    if (el) follow(el)
  })
}

function open(at) {
  visible.value = true
  index.value = Math.min(at, steps.value.length - 1)
  nextTick(() => {
    cardEl.value?.focus?.()
    relocate()
  })
}

const show = () => open(0)
const resume = () => {
  const st = readState(props.userId)
  open(st.status === 'paused' ? st.atStep : 0)
}

function exit(status) {
  writeStatus(props.userId, status, index.value)
  visible.value = false
  emit(status, { atStep: index.value })
}

function next() {
  if (isLast.value) return
  index.value += 1
  relocate()
}
function prev() {
  if (index.value > 0) {
    index.value -= 1
    relocate()
  }
}

/** 中间步骤：点击被引导消费（前进、不导航、不写终态）；激活动作步：真执行并结束 */
async function onAnchorClick(event) {
  event?.preventDefault?.()
  event?.stopPropagation?.()
  if (!isLast.value) return next()
  const action = current.value.action
  const el = current.value.anchor ? resolveAnchor(current.value.anchor) : null
  if (action?.type === 'navigate') await router.push(action.to)
  else if (action?.type === 'click-anchor') el?.click()
  exit('done')
}

function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') exit('paused')
  if (e.key === 'ArrowRight') next()
  if (e.key === 'ArrowLeft') prev()
}

let autoTimer = null
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  migrateV1IfPresent(props.userId)
  const st = readState(props.userId)
  if (st.status === 'idle') autoTimer = setTimeout(() => open(0), 600)
  else if (st.status === 'paused') autoTimer = setTimeout(() => resume(), 600)
})
onBeforeUnmount(() => {
  if (autoTimer) clearTimeout(autoTimer)
  window.removeEventListener('keydown', onKeydown)
})

defineExpose({
  show,
  resume,
  getIndex: () => index.value,
  __trapStep: () => {
    // 走一步 Tab：交给真实陷阱实现，这里只代触发以保证测试不依赖 jsdom 的 Tab 语义
    cardEl.value?.__trapAdvance?.()
  },
  __key: onKeydown,
  __scrimClick: () => exit('paused'),
  __anchorClick: onAnchorClick,
  __relocate: relocate,
  __goto: async (n) => {
    index.value = n
    relocate()
    await nextTick()
  },
})
</script>
```

> **`__trapStep` 依赖 `useFocusTrap` 暴露一个"前进一格焦点"的钩子。**本仓 `useFocusTrap.ts` 现在只接
> `(rootRef, active)` 两个参数、不外露钩子——所以本步要顺手给它加一行：在 `useFocusTrap` 内部
> 把已有的"取下一可聚焦元素"函数挂到 `rootRef.value.__trapAdvance` 上（只加赋值一行，不改任何既有行为），
> 并**同步补一条 `useFocusTrap.test.ts` 用例**：开态下 `__trapAdvance()` 循环一轮不会把焦点放到 root 之外。
> 若不愿改原语，替代方案是测试里手工 `focus()` 卡内元素——但那测不到陷阱本身，**不许**那么做。

- [ ] **Step 8: 重写模板（四个出口都在，「跳过」不许删）**

`dismissOnboarding`（e2e 夹具）靠点「跳过」关掉浮层；删掉它会让整个流程套件红。`.ob-foot` 定为：

```html
          <div class="ob-foot">
            <div class="ob-foot-left">
              <button class="ob-btn ghost" type="button" data-exit="skipped" @click="exit('skipped')">跳过</button>
              <button class="ob-btn ghost" type="button" data-exit="paused" @click="exit('paused')">稍后再看</button>
            </div>
            <div class="ob-foot-right">
              <button v-if="index > 0" class="ob-btn ghost" type="button" @click="prev">上一步</button>
              <button v-if="!isLast" class="ob-btn primary" type="button" @click="next">下一步</button>
              <button v-else class="ob-btn primary" type="button" @click="onAnchorClick">开始使用</button>
            </div>
          </div>
```

卡片根节点（`aria-label` 恒定是改前缺陷的文案面，换成按步 `labelledby`）:

```html
          ref="cardEl"
          class="ob-card"
          :class="{ centered: !current.anchor || anchorMiss }"
          :style="cardStyle"
          :data-placement="placement"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="`ob-title-${current.id}`"
          tabindex="-1"
```

标题：`<h3 class="ob-title" :id="`ob-title-${current.id}`">{{ current.title }}</h3>`。
遮罩与 ring 渲染在卡片之前：

```html
        <div
          v-for="(r, i) in scrimRects"
          :key="i"
          class="ob-scrim"
          :style="{ top: r.top + 'px', left: r.left + 'px', width: r.width + 'px', height: r.height + 'px' }"
          @click="exit('paused')"
        ></div>
        <div v-if="current.anchor && !anchorMiss" class="ob-ring" :style="ringStyle" aria-hidden="true"></div>
```

- [ ] **Step 9: 挂载处传 props**

`AppShell.vue` 的 `<OnboardingGuide ref="guideRef" />` 改为：

```
    <OnboardingGuide ref="guideRef" :user-id="auth.userId" :role="role.toLowerCase()" />
```

Run: `cd frontend && npx vitest run src/components/__tests__/OnboardingGuide.focus.test.js`
Expected: PASS（8 条）。若 `useFocusTrap` 在 jsdom 下无 `__trapAdvance`，回到 Step 7 的注记把钩子补上，
**不要**把这条测试改成"手工 focus"。

- [ ] **Step 10: 重签旧单测（改判据形状，不许删用例）**

Run: `cd frontend && npx vitest run src/components/__tests__/OnboardingGuide.test.ts`
Expected: 部分红——旧断言钉的是"Esc＝skip＝写 v1 '1'"与"点遮罩＝skip"。逐条改：
`localStorage` 断言读 v2 键与 `status`；`1 / 5` 文案断言保留；"已看过不再自动弹"保留并改成 v2 形状；
新增两条：`paused@2` 刷新后从第 3 步出现、v1 迁移后不弹。**改完条数 ≥ 改前 11 条。**

- [ ] **Step 11: 全量单测 ＋ 棘轮 ＋ LEDGER ＋ 提交**

```bash
cd frontend && npm run test 2>&1 | tail -5
node scripts/check-types.cjs 2>&1 | tail -2
```

Expected: 棘轮 ≤ 402（本棒改了 `useFocusTrap.ts`，若新增 TS 错误必须当场修，**不许 `--sync` 基线**）。
LEDGER 追加 `P0c-ONB-03`：改前列＝F3／F6／F7 三条读数（含"点侧栏即写 `done:v1:4=1`"）；
改后列＝三态可区分、Esc＝paused、点洞内不写终态；验证列＝两条命令 ＋ `focus.test.js` 的改前全红条数。

```bash
git add frontend/src/onboarding/state.js frontend/src/onboarding/__tests__/state.test.js frontend/src/components/OnboardingGuide.vue \
  frontend/src/components/__tests__/OnboardingGuide.focus.test.js frontend/src/components/__tests__/OnboardingGuide.test.ts \
  frontend/src/composables/useFocusTrap.ts frontend/src/composables/__tests__ frontend/src/layouts/AppShell.vue docs/visual-upgrade/LEDGER.md
git commit -m "feat(onboarding)!: 出口语义分离（done/skipped/paused@n 可区分）＋焦点陷阱接线；Esc 与点洞外改为暂停"
```

---

### Task 4: 壳层收口——UserMenu 单一来源 ＋ 遮罩判定改判可见

**Files:**
- Create: `frontend/src/components/UserMenu.vue`
- Create: `frontend/src/components/__tests__/UserMenu.test.js`
- Modify: `frontend/src/layouts/AppShell.vue`（删本地 `showUserMenu`，改渲染 `UserMenu`）
- Modify: `frontend/src/layouts/components/topbar/AtbUserMenu.vue`（同上）
- Modify: `frontend/src/layouts/__tests__/AppShell.test.ts`（重签，不删）
- Modify: `frontend/src/composables/useKeyboardShortcuts.ts`
- Create: `frontend/src/composables/__tests__/keyboardShortcuts.overlay.test.js`

**Interfaces:**
- Consumes: Task 3 的 `defineExpose({ show, resume })`。
- Produces:
  - `UserMenu.vue` props：`items: Array<{ label, icon, action, danger? }>`、`anchorRect: { top, left, bottom? }`；emits：`closed`
  - `USER_MENU_ITEMS({ router, auth, onGuide }): MenuItem[]`（具名导出，单一来源）
  - `hasOpenOverlay(doc?): boolean`（**语义变更：判可见且有面积**）；`hasBlockingOverlay(doc?): boolean`（认 `.el-overlay` 与 `.ob-overlay`）

- [ ] **Step 1: 写失败测试**

`frontend/src/components/__tests__/UserMenu.test.js`:

```js
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import fs from 'node:fs'
import UserMenu, { USER_MENU_ITEMS } from '@/components/UserMenu.vue'

const strip = (s) =>
  s.replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')

const items = USER_MENU_ITEMS({
  router: { push: vi.fn() },
  auth: { role: 'STUDENT', logout: vi.fn().mockResolvedValue() },
  onGuide: vi.fn(),
})

describe('UserMenu 单一来源', () => {
  it('常量顺序固定且含「新手引导」', () => {
    expect(items.map((i) => i.label)).toEqual(['个人中心', '新手引导', '退出登录'])
  })

  it('渲染出的 items 与常量逐字相同', () => {
    const w = mount(UserMenu, { props: { items, anchorRect: { top: 10, left: 10, bottom: 48 } } })
    expect(w.findAll('.menu-item').map((n) => n.text().trim())).toEqual(items.map((i) => i.label))
  })

  it('触发物在下半屏 ⇒ 向上开（改前恒 top:52px ⇒ 点左下角、菜单飞右上角）', () => {
    const up = mount(UserMenu, { props: { items, anchorRect: { top: 834, left: 0, bottom: 900 } } })
    const down = mount(UserMenu, { props: { items, anchorRect: { top: 10, left: 1300, bottom: 48 } } })
    const topOf = (w) => Number(/top:\s*(-?\d+)px/.exec(w.find('.pop-menu').attributes('style'))[1])
    expect(topOf(up)).toBeLessThan(834)
    expect(topOf(down)).toBeGreaterThan(48)
  })

  it('点项 ⇒ 关闭并只触发一次该动作；不再用 innerHTML 拼标签', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], anchorRect: { top: 10, left: 10 } },
    })
    await w.findAll('.menu-item')[0].trigger('click')
    expect(action).toHaveBeenCalledTimes(1)
    expect(w.emitted('closed')).toBeTruthy()
    expect(w.html()).not.toMatch(/innerHTML/)
  })

  it('键盘可达：menuitem 带 tabindex，Enter 等同点击', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], anchorRect: { top: 10, left: 10 } },
    })
    const item = w.findAll('.menu-item')[0]
    expect(item.attributes('tabindex')).toBe('0')
    await item.trigger('keydown.enter')
    expect(action).toHaveBeenCalledTimes(1)
  })

  it('两处壳层都不得再自带菜单标签字面量（分叉的根）', () => {
    for (const p of ['../../layouts/AppShell.vue', '../../layouts/components/topbar/AtbUserMenu.vue']) {
      const src = strip(fs.readFileSync(new URL(p, import.meta.url), 'utf8'))
      expect(src, `${p} 里还写着「新手引导」字面量 ⇒ 它没走单一来源`).not.toMatch(/'新手引导'|\"新手引导\"/)
      expect(src).toMatch(/USER_MENU_ITEMS/)
    }
  })
})
```

- [ ] **Step 2: 跑它确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/UserMenu.test.js`
Expected: FAIL — `Cannot find module '@/components/UserMenu.vue'`。

- [ ] **Step 3: 写 UserMenu.vue**

```vue
<template>
  <div class="pop-menu" :style="pos" role="menu">
    <div
      v-for="item in items"
      :key="item.label"
      class="menu-item"
      :class="{ danger: !!item.danger }"
      role="menuitem"
      tabindex="0"
      @click="pick(item)"
      @keydown.enter.prevent="pick(item)"
    >
      <!-- 图标是仓内常量表（layouts/icons.ts），受控；标签一律走插值，不再 innerHTML 拼整行 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span class="mi-ico" v-html="iconHtml(item.icon, 14)"></span>
      <span>{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { iconHtml } from '@/layouts/icons'

const props = defineProps({
  items: { type: Array, required: true },
  anchorRect: { type: Object, required: true },
})
const emit = defineEmits(['closed'])

const ITEM_H = 34
/** 跟随触发物：在下半屏就向上开。改前是恒 top:52px ⇒ 点左下角头像、菜单出现在右上角 */
const pos = (() => {
  const r = props.anchorRect
  const openUp = r.top > window.innerHeight / 2
  const top = openUp ? Math.max(8, r.top - 8 - props.items.length * ITEM_H) : (r.bottom ?? r.top) + 8
  const left = Math.min(Math.max(8, r.left), window.innerWidth - 200)
  return { top: `${top}px`, left: `${left}px` }
})()

function pick(item) {
  emit('closed')
  item.action?.()
}

/** 单一来源：侧栏底部用户区与顶栏头像都渲染它 */
export const USER_MENU_ITEMS = ({ router, auth, onGuide }) => [
  { label: '个人中心', icon: 'user', action: () => router.push(`/${auth.role.toLowerCase()}/profile`) },
  { label: '新手引导', icon: 'grad', action: () => onGuide() },
  { label: '退出登录', icon: 'logout', danger: true, action: () => auth.logout().then(() => router.push('/login')) },
]
</script>
```

> `<script setup>` 里不能 `export const`——把 `USER_MENU_ITEMS` 放进同目录的 `userMenuItems.js` 并从那里导入。
> **执行者按此拆**：Create `frontend/src/components/userMenuItems.js`，`UserMenu.vue` 只 import 它；
> 测试的 import 路径同步改成 `@/components/userMenuItems`。这条注记就是本棒唯一允许的"计划内纠计划"，
> 因为它是语言限制，不是设计变更。

- [ ] **Step 4: 两处改挂它，删掉两份动态建 DOM**

`AppShell.vue`：删 `function showUserMenu()` 整块（含 `document.createElement` 与 `el.innerHTML = ...`），
改为：

```js
const menuOpen = ref(false)
const menuRect = ref({ top: 0, left: 0 })
function openMenuFrom(selector) {
  const el = document.querySelector(selector)
  if (!el) return
  const r = el.getBoundingClientRect()
  menuRect.value = { top: r.top, left: r.left, bottom: r.bottom }
  menuOpen.value = true
}
const menuItems = computed(() =>
  USER_MENU_ITEMS({ router, auth, onGuide: () => guideRef.value?.resume() }),
)
```

模板：`<AppSidebar :role="role" :sidebar-mini="sidebarMini" @user-menu="openMenuFrom('.side-user')" />`，
根末尾加 `<UserMenu v-if="menuOpen" :items="menuItems" :anchor-rect="menuRect" @closed="menuOpen = false" />`。
`AtbUserMenu.vue`：删本地 `showUserMenu` 与 `iconHtml` 拼接，改为同样的 `openMenuFrom('.user-btn')` 形状
（它自己持 `menuOpen`/`menuRect` 即可，不向父级发事件）。

- [ ] **Step 5: 重签 `AppShell.test.ts`**

保留顺序断言 `['个人中心','新手引导','退出登录']`；把"点新手引导调 `show()`"改成 **调 `resume()`**
（paused 用户要能续，这是 Task 3 定的语义）；负控"个人中心／退出登录永不触发引导"保留。

Run: `cd frontend && npx vitest run src/components/__tests__/UserMenu.test.js src/layouts/__tests__/AppShell.test.ts src/layouts/__tests__/AppShell.a11y.test.ts src/layouts/__tests__/AppShell.shortcuts.test.ts`
Expected: PASS。后两个文件把 `OnboardingGuide` stub 成 null-render——**保留 stub，但补一条断言**：
stub 的 props 里必须有 `userId` 与 `role`（否则壳层传参退化无人知）。

- [ ] **Step 6: 写遮罩判定的 A/B 测试（判据 7）**

`frontend/src/composables/__tests__/keyboardShortcuts.overlay.test.js`:

```js
import { describe, it, expect, afterEach } from 'vitest'
import { hasOpenOverlay, hasBlockingOverlay } from '@/composables/useKeyboardShortcuts'

const put = (html) => {
  document.body.innerHTML = html
}

describe('遮罩判定：存在 ≠ 打开（改前 F11：一个 display:none 的 .el-overlay 掐死全站快捷键）', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('隐藏的 .el-overlay 不算打开', () => {
    put('<div class="el-overlay" style="display:none"></div>')
    expect(hasOpenOverlay()).toBe(false)
  })

  it('可见的 .el-overlay 算打开（正读数配对，否则"永远 false"也能绿）', () => {
    put('<div class="el-overlay" style="display:block;width:800px;height:600px"></div>')
    expect(hasOpenOverlay()).toBe(true)
  })

  it('0 面积的可见节点不算（EP 收起态常见 rect 0×0）', () => {
    put('<div class="el-overlay" style="display:block;width:0;height:0"></div>')
    expect(hasOpenOverlay()).toBe(false)
  })

  it('引导自己的 .ob-overlay 算阻塞遮罩：快捷键不得穿透引导', () => {
    put('<div class="ob-overlay" style="display:block;width:800px;height:600px"></div>')
    expect(hasBlockingOverlay()).toBe(true)
    expect(hasOpenOverlay()).toBe(false)
  })
})
```

- [ ] **Step 7: 改 `useKeyboardShortcuts.ts`**

```ts
/** 节点是否真的可见：display 不为 none、visibility 不为 hidden、且有面积。
 *  改前只判 querySelector 存在 ⇒ 页面上常驻一个 display:none 的 .el-overlay，全站快捷键永久失效。 */
function isVisible(el: Element | null): boolean {
  if (!el) return false
  const cs = getComputedStyle(el)
  if (cs.display === 'none' || cs.visibility === 'hidden') return false
  const r = el.getBoundingClientRect()
  return r.width > 0 && r.height > 0
}

export function hasOpenOverlay(doc: Document = document): boolean {
  return [...doc.querySelectorAll('.el-overlay')].some((el) => isVisible(el))
}

/** 阻塞壳层热键的遮罩：EP 弹层 ＋ 自建引导层（引导渲染的是 .ob-overlay，改前谁都不认它） */
export function hasBlockingOverlay(doc: Document = document): boolean {
  return (
    [...doc.querySelectorAll('.el-overlay, .ob-overlay')].some((el) => isVisible(el))
  )
}
```

`useGlobalHotkeys` 里 `if (!binding.allowWhenOverlayOpen && hasOpenOverlay()) continue` 改为 `hasBlockingOverlay()`；
`AppShell.vue` 中 `?` handler 里那句 `document.querySelector('.el-overlay')` 早退同步换成 `hasOpenOverlay()`。

- [ ] **Step 8: 守卫自证（本仓老毛病是守卫自己被改坏没人发现）**

两次变异，各跑一次再还原：
① `isVisible` 改成 `return Boolean(el)` ⇒ 第 1 条必红；
② `hasBlockingOverlay` 的选择器里去掉 `.ob-overlay` ⇒ 第 4 条必红。

Run: `cd frontend && npx vitest run src/composables/__tests__/keyboardShortcuts.overlay.test.js`
Expected: 变异期红、还原后 4 条全绿。

- [ ] **Step 9: 全量单测 ＋ 棘轮 ＋ LEDGER ＋ 提交**

```bash
cd frontend && npm run test 2>&1 | tail -5
node scripts/check-types.cjs 2>&1 | tail -2
```

Expected: 棘轮 ≤ 402（本棒改了 `.ts`）。LEDGER 追加 `P0c-ONB-04`：
改前列＝F4（侧栏触发物 top 834／菜单 top 52 left 1276；顶栏 items 无「新手引导」）＋ F11（A/B 两条读数）；
改后列＝两处 items 相同、弹层跟随触发物、隐藏 overlay 在场时 Ctrl+K 响应；验证列＝两条命令 ＋ 两次变异。

```bash
git add frontend/src/components/UserMenu.vue frontend/src/components/userMenuItems.js frontend/src/components/__tests__/UserMenu.test.js \
  frontend/src/layouts/AppShell.vue frontend/src/layouts/components/topbar/AtbUserMenu.vue frontend/src/layouts/__tests__ \
  frontend/src/composables/useKeyboardShortcuts.ts frontend/src/composables/__tests__/keyboardShortcuts.overlay.test.js \
  docs/visual-upgrade/LEDGER.md
git commit -m "fix(shell)!: 用户菜单收成单一来源（两处 items 逐字相同、弹层跟随触发物）；遮罩判定改判可见，恢复全站快捷键"
```

---

### Task 5: 三端 e2e ＋ 未证事项转实测 ＋ 文档同步

**Files:**
- Create: `frontend/e2e/onboarding.spec.ts`
- Create: `frontend/e2e/onboarding-helpers.ts`
- Modify: `frontend/e2e/test-helpers.ts`（`setLocalStorageAuth` 写 v2；`dismissOnboarding` 保留行为、更新注释）
- Modify: `frontend/src/onboarding/steps/teacher.js`／`admin.js`（仅当 Step 1 实测与表不符）
- Modify: `docs/项目手册.md`（REQ-FE-008 行、§D.12.3 DR-52 追加改判、新增 DR-95）
- Modify: `docs/项目现状分析与未来发展规划-2026-10-13.md`（D2-9 转完成、§4.3 划掉一条）
- Modify: `docs/superpowers/specs/2026-10-21-onboarding-redesign-design.md`（§5 未证逐条转实测）
- Modify: `docs/visual-upgrade/LEDGER.md`（`P0c-ONB-05` ＋ `P0b-FOCUS-02`）

**Interfaces:**
- Consumes: Task 1–4 全部。
- Produces: 判据 2／3／5／9 的可重放证据；手册与规划口径同步；Phase 4 准入门第 5 项转闭的连带记录。

- [ ] **Step 1: 把 spec §5 的两条未证做成实测（先测后写，不许沿用推测）**

一次性脚本 `frontend/test-results/onboarding-verify.mjs`（**不入 git**，目录已 gitignore）：

```js
import { chromium } from '@playwright/test'
import fs from 'node:fs'
const OUT = 'test-results/onboarding-verify'
fs.mkdirSync(OUT, { recursive: true })
const browser = await chromium.launch()
const roles = ['student', 'teacher', 'admin']
const report = {}
for (const role of roles) {
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const page = await ctx.newPage()
  await page.goto(`http://localhost:5175/${role}/dashboard`)
  await page.waitForTimeout(600)
  await page.evaluate(() => {
    Object.keys(localStorage).filter((k) => k.includes('onboarding')).forEach((k) => localStorage.removeItem(k))
  })
  await page.reload()
  await page.waitForSelector('.ob-card', { timeout: 8000 })
  const per = []
  for (let i = 0; i < 5; i++) {
    per.push(
      await page.evaluate(() => {
        const card = document.querySelector('.ob-card')
        const r = card.getBoundingClientRect()
        const step = {
          count: document.querySelector('.ob-count')?.textContent?.trim(),
          title: document.querySelector('.ob-title')?.textContent?.trim(),
          placement: card.dataset.placement,
          fullyVisible: r.top >= 8 && r.left >= 8 && r.bottom <= innerHeight - 8 && r.right <= innerWidth - 8,
          scrim: document.querySelectorAll('.ob-scrim').length,
          anchorsInDom: document.querySelectorAll('[data-onboard]').length,
        }
        return step
      }),
    )
    if (i < 4) {
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(300)
    }
  }
  // 激活动作真的执行了吗
  const urlBefore = page.url()
  await page.locator('.ob-card').getByRole('button', { name: '开始使用' }).click()
  await page.waitForTimeout(700)
  report[role] = { per, urlBefore, urlAfter: page.url(), cardGone: await page.locator('.ob-card').count() === 0 }
  await page.screenshot({ path: `${OUT}/${role}-last.png` })
  await ctx.close()
}
fs.writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 2))
console.log(JSON.stringify(report, null, 2))
await browser.close()
```

Run: `cd frontend && node test-results/onboarding-verify.mjs`
Expected: 三端各 5 步 `fullyVisible: true`、末步 `cardGone: true`、`urlAfter` 与 `urlBefore` 不同（teacher/admin）
或弹窗出现（student 的 `click-anchor`）。
**若与 spec §3.4 表不符，以实测为准回改 `steps/*.js` 与 spec 那张表，并在 LEDGER 记一条改判**（原表不删，标"已按实测改"）。

- [ ] **Step 2: 把实测结论写回 spec §5**

逐条改成"已实测＝…"；取不到对照的（浅色主题压暗可见性）**如实保留为未证**，并写明为什么没取到
（本次改主题键未生效，两态读数同为 `dark`）。

- [ ] **Step 3: 写 e2e 夹具**

`frontend/e2e/onboarding-helpers.ts`:

```ts
import type { Page } from '@playwright/test'

export const V2_PREFIX = 'ai-course:onboarding:v2'

/** 清成"从没看过"并等引导真的弹出来 */
export async function resetGuide(page: Page) {
  await page.evaluate((prefix) => {
    Object.keys(localStorage)
      .filter((k) => k.includes('onboarding'))
      .forEach((k) => localStorage.removeItem(k))
    void prefix
  }, V2_PREFIX)
  await page.reload()
  await page.waitForSelector('.ob-card', { timeout: 8000 })
}

export const guideSnapshot = (page: Page) =>
  page.evaluate(() => {
    const card = document.querySelector('.ob-card')
    const r = card?.getBoundingClientRect()
    const scrim = document.querySelector('.ob-scrim')
    const v2key = Object.keys(localStorage).find((k) => k.startsWith('ai-course:onboarding:v2:'))
    return {
      count: document.querySelector('.ob-count')?.textContent?.trim() ?? '',
      title: document.querySelector('.ob-title')?.textContent?.trim() ?? '',
      placement: (card as HTMLElement | null)?.dataset?.placement ?? '',
      cardFullyVisible:
        !!r && r.top >= 8 && r.left >= 8 && r.bottom <= innerHeight - 8 && r.right <= innerWidth - 8,
      scrimCount: document.querySelectorAll('.ob-scrim').length,
      scrimBg: scrim ? getComputedStyle(scrim).backgroundColor : '',
      bodyBg: getComputedStyle(document.body).backgroundColor,
      v2: v2key ? localStorage.getItem(v2key) : null,
    }
  })

export const readStatus = (page: Page) =>
  page.evaluate(async () => {
    const k = Object.keys(localStorage).find((x) => x.startsWith('ai-course:onboarding:v2:'))
    return k ? JSON.parse(localStorage.getItem(k) as string) : null
  })
```

- [ ] **Step 4: 写三端 e2e（判据 2/3/5/9）**

`frontend/e2e/onboarding.spec.ts`:

```ts
import { test, expect } from '@playwright/test'
import { resetGuide, guideSnapshot, readStatus } from './onboarding-helpers'

const ROLES = [
  { role: 'student', lastTitle: '先加入一门课' },
  { role: 'teacher', lastTitle: '用 AI 出一批题' },
  { role: 'admin', lastTitle: '先看审计' },
] as const

for (const { role, lastTitle } of ROLES) {
  test.describe(`新人引导 · ${role}`, () => {
    test.use({ storageState: `e2e/visual/.auth/${role}.json` })

    test('走完 5 步：每步整卡可见，末步真执行并写 done', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      for (let i = 1; i <= 4; i++) {
        const s = await guideSnapshot(page)
        expect(s.cardFullyVisible, `第 ${i} 步整卡可见：${JSON.stringify(s)}`).toBe(true)
        if (i > 1) expect(s.scrimCount, `第 ${i} 步应有洞外压暗块`).toBeGreaterThan(0)
        await page.keyboard.press('ArrowRight')
        await page.waitForTimeout(250)
      }
      expect((await guideSnapshot(page)).title).toBe(lastTitle)
      await page.locator('.ob-card').getByRole('button', { name: '开始使用' }).click()
      await expect(page.locator('.ob-card')).toHaveCount(0)
      expect((await readStatus(page))?.status).toBe('done')
    })

    test('Esc 暂停后刷新，从第 3 步续看（判据 5）', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      await page.keyboard.press('ArrowRight')
      await page.keyboard.press('ArrowRight')
      await page.keyboard.press('Escape')
      await expect(page.locator('.ob-card')).toHaveCount(0)
      expect((await readStatus(page))).toMatchObject({ status: 'paused', atStep: 2 })
      await page.reload()
      await page.waitForSelector('.ob-card')
      expect((await guideSnapshot(page)).count).toBe('3 / 5')
    })

    test('跳过写 skipped，与 done 在数据上可区分', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      await page.locator('.ob-card').getByRole('button', { name: '跳过' }).click()
      expect((await readStatus(page))).toMatchObject({ status: 'skipped' })
      await page.reload()
      // 自动弹出走的是 600ms 定时器；不等过去就断言 toHaveCount(0) 是假绿
      await page.waitForTimeout(1200)
      await expect(page.locator('.ob-card')).toHaveCount(0)
    })

    test('暗色下压暗看得见（判据 2，非像素量法）', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      // 主题必须在 resetGuide 之后再设：reload 会让 data-theme 回到 localStorage 里的值
      await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(250)
      const s = await guideSnapshot(page)
      const fg = (s.scrimBg.match(/\d+/g) ?? []).map(Number)
      const bg = (s.bodyBg.match(/\d+/g) ?? []).map(Number)
      expect(fg.length, `scrim 色没读到：${s.scrimBg}`).toBeGreaterThanOrEqual(4)
      const over = fg.slice(0, 3).map((c, i) => Math.round(c * fg[3] + bg[i] * (1 - fg[3])))
      const delta = Math.max(...over.map((c, i) => Math.abs(c - bg[i])))
      expect(delta, `合成后单通道差 ${delta}/255（改前 3/255），阈值 24`).toBeGreaterThanOrEqual(24)
    })
  })
}
```

Run: `cd frontend && npx playwright test e2e/onboarding.spec.ts --reporter=line`
Expected: 12 passed（三端 ×4）。若被限流影响，检查是否误用了 `loginAs`——本文件只准用 `storageState`。

- [ ] **Step 5: 改夹具读 v2（两键并存，不许删 v1 写入）**

`test-helpers.ts` 的 `setLocalStorageAuth`：把
`localStorage['ai-course:onboarding:done:v1:1'] = '1'` 之后**追加**一行写 v2：

```ts
      localStorage['ai-course:onboarding:v2:1'] = JSON.stringify({ status: 'done', atStep: 0 })
```

注释更新为："v1 键保留是给迁移断言用的（Task 3 Step 10 那条），两键并存的读法见 LEDGER `P0c-ONB-03`；
`dismissOnboarding` 仍按真实用户行为点「跳过」——那个按钮现在写的是 `skipped`，不再是 `done`。"

Run: `cd frontend && npx playwright test --list 2>&1 | tail -2`
Expected: 流程套件条数比改前多 12，逐 spec 加和自证（`--list` 不跑 globalSetup）。

- [ ] **Step 6: 全套回归（三处必留实跑读数，禁写"全绿"）**

```bash
cd frontend && npm run test 2>&1 | tail -5
node scripts/check-types.cjs 2>&1 | tail -2
npm run lint 2>&1 | tail -3
npx playwright test e2e/onboarding.spec.ts e2e/visual/anchors.spec.ts --reporter=line 2>&1 | tail -8
git checkout -- docs/visual-upgrade/evidence/P0a/motion/
```

Expected: 单测条数、棘轮读数、lint 覆盖面（只 `src/**`）逐条记进 LEDGER；
`anchors` 允许的红集合**只准是 D-1 那两条 `error-404`**（视觉升级 spec §11.4 已定）——
出现第三条红就是本改造引入的回归，当场查，不许"排除"掉。

- [ ] **Step 7: 文档同步（三处，一处不许漏）**

1. `docs/项目手册.md`：REQ-FE-008 行改成新形状（三端各 5 步、洞内可点、四出口三态、可续看）；
   §D.12.3 DR-52 追加一条改判——"重看从第 1 步"仅对 `done`／`skipped` 成立，`paused` 从第 n 步续；
   新增 **DR-95**（本批亲跑最高号 94）：引导改造全量结论，含"快捷键恢复"这条跨面收益。
2. `docs/项目现状分析与未来发展规划-2026-10-13.md`：D2-9 由"部分完成"转完成（多角色差异化已落地）；
   §4.3 待决策项「新手引导差异化」划掉并指向 DR-95。
3. 本计划所依据的 spec §5：逐条转实测或如实保留未证（Step 2 已做，此处复核不许回退成推测语气）。

- [ ] **Step 8: LEDGER 收尾两行 ＋ 四条自校**

`P0c-ONB-05`（三端 e2e 与判据落地，含 Step 1 的实测改判）＋ `P0b-FOCUS-02`
（连带收益：Phase 4 准入门第 5 项由"半闭"转闭——第三载体 `OnboardingGuide.vue` 已接 `useFocusTrap`）。

```bash
awk -F'|' '/^\|/ && NF-2!=9 {print NR": "NF-2}' docs/visual-upgrade/LEDGER.md
grep -c '^|' docs/visual-upgrade/LEDGER.md
```

Expected: 第一条无输出；第二条比改前大。再跑视觉升级 spec §11.7 的断行检测（LEDGER 与本计划两份都要跑），须无输出。

- [ ] **Step 9: 提交**

```bash
git add frontend/e2e/onboarding.spec.ts frontend/e2e/onboarding-helpers.ts frontend/e2e/test-helpers.ts \
  frontend/src/onboarding docs/项目手册.md docs/项目现状分析与未来发展规划-2026-10-13.md \
  docs/superpowers/specs/2026-10-21-onboarding-redesign-design.md docs/visual-upgrade/LEDGER.md
git commit -m "test(onboarding)+docs: 三端引导 e2e（走完/续看/跳过/暗色色差）＋手册 REQ-FE-008 与 DR-52 改判、登记 DR-95"
```

---

## 收尾（全计划完成后一次做）

1. **自主验收**按视觉升级那批的形态出四块：裁定清单带"若错代价"、判据 1–9 逐条结论、LEDGER 自校＋实跑读数、
   **未证与未定性单列**。
2. 结论要敢写"有保留"。已知至少两条保留：浅色主题压暗对照未取到；完成率只有本地三态、无跨设备口径。
3. 不推送。推送需项目主单独点头。

## 回滚切块（spec §3.5 的三块，各自可单独 revert）

| 块 | 涉及 commit | 回滚动作 | 回滚后仍成立的东西 |
| --- | --- | --- | --- |
| ① 几何与可见性 | Task 2 那一笔 | `git revert` 该 commit（含 `geometry.js`、`useSpotlightOverlay.js`、token、`.ob-*` 样式） | 锚点表与 steps 不受影响；Task 2 的两条测试随该笔一起消失，**不留红测试** |
| ② 状态契约 v2 与出口语义 | Task 3 那一笔（带 `!`） | `git revert` 该 commit。**必须同时手工确认 v1 键未被删**——迁移只写不删，revert 后老用户仍读得到 v1 `'1'`，不会出现"莫名重弹" | Task 1 的 steps／锚点、Task 2 的几何仍在 |
| ③ 壳层收口 | Task 4 那一笔（带 `!`） | `git revert` 该 commit ⇒ 回到两份菜单与"判存在"的遮罩守卫；**全站快捷键随之重新被那个常驻 `display:none` 的 `.el-overlay` 掐死**（这是回滚的已知代价，不是新缺陷） | 引导本身（①②）不受影响 |

Task 1 与 Task 5 是地基与取证，不单独回滚：回滚 ①②③ 时它们仍自洽（steps 与锚点不依赖几何实现，e2e 会红但红得正确）。
