import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  resetGuide,
  guideSnapshot,
  readStatus,
  focusProbe,
  type FocusProbe,
} from './onboarding-helpers'

/**
 * 三端新人引导 e2e（改前 F10 那条"e2e 面零断言"就是这里补上的）。
 *
 * 【只用 storageState，绝不 loginAs】登录端点按 IP 限流（10 次/60 秒、429 还不打日志），
 * 三端 ×N 条重登会把失败集合变成随机集合。夹具在 `e2e/visual/.auth/`，由视觉 config 的
 * globalSetup 生成；这一份流程 config 不带 globalSetup，所以**夹具过期时会跳 /login**——
 * 那种红不是产品缺陷。
 *
 * 【⚠️ 三份 `.auth/*.json` 绑定 origin，含端口】本套件按 `playwright.config.ts` 默认跑
 * `http://localhost:5173`，而夹具是**按某个具体端口**生成的：Playwright 只对
 * `origins[].origin` **完全相等**（scheme+host+**port**）的 `localStorage` 生效；cookie 是
 * 域级（`localhost`）、不比分端口，所以**症状极具误导性**——会话看着"有 cookie"，
 * 但 `user`／`permissions` 没被还原，路由守卫判定未登录，整份套件连既有用例一起红在
 * `resetGuide` 的 `waitForSelector('.ob-card')` 上（页面其实是 `/login`）。
 * 2026-10-23 实测就是这个原因让 18 条全红，一度被误读成"引导打不开"。
 * ⇒ 换端口（`PW_PORT`）或换机器后必须按 LEDGER `P0c-ONB-05` 的配方**按同一端口**重生成：
 *   `cd frontend && PW_PORT=5173 PW_BASE_URL=http://localhost:5173 npx playwright test -c playwright.visual.ts helpers --grep "settlePage"`
 * （`--grep` 只负责触发 globalSetup 的三次 `loginAs` 并把 `storageState` 写盘；`settlePage`
 *   是现版本里确实能匹配到的用例名。跑 5175 一档时把 `5173` 全换成 `5175`，两处必须同改。）
 * 【视口】显式钉 1440×900：项目默认的 1280×720 与取证脚本用的那一档不是同一档，
 * 卡片定位是视口函数，换档等于换判据。
 */
const ROLES = [
  { role: 'student', lastTitle: '先加入一门课' },
  { role: 'teacher', lastTitle: '用 AI 出一批题' },
  { role: 'admin', lastTitle: '先看审计' },
] as const

for (const { role, lastTitle } of ROLES) {
  test.describe(`新人引导 · ${role}`, () => {
    test.use({
      storageState: `e2e/visual/.auth/${role}.json`,
      viewport: { width: 1440, height: 900 },
    })

    test('走完 5 步：每步整卡可见，末步真执行并写 done', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      for (let i = 1; i <= 4; i++) {
        const s = await guideSnapshot(page)
        expect(s.cardFullyVisible, `第 ${i} 步整卡可见：${JSON.stringify(s)}`).toBe(true)
        if (i > 1) expect(s.scrimCount, `第 ${i} 步应有洞外压暗块`).toBeGreaterThan(0)
        await page.keyboard.press('ArrowRight')
        await page.waitForTimeout(300)
      }
      expect((await guideSnapshot(page)).title).toBe(lastTitle)

      const urlBefore = page.url()
      await page.locator('.ob-card').getByRole('button', { name: '开始使用' }).click()
      await expect(page.locator('.ob-card')).toHaveCount(0)
      // 【为什么是 poll 而不是直接读】这是**测试与异步落盘的竞态**，不是产品缺陷：
      // `onAnchorClick` 先把 `visible` 置 false（卡片当场消失），再 `await router.push(...)`，
      // 之后才 `exit('done')` 写 localStorage；teacher／admin 正是导航型动作，`router.push`
      // 要跨一次路由解析。于是"卡片没了"与"状态落了"之间有一段真空，`toHaveCount(0)` 一过就读，
      // 约 1/3 概率抢在写盘之前拿到 null（R-06 取证轮实测 3 轮红 1 轮）。
      // 用 poll 等它落盘，**判据强度一字未降**：超时仍红、值仍必须是 'done'；
      // 只是不再把"还没写完"读成"没写"。产品侧的写序未动（改它属行为变更，不在本轮）。
      await expect
        .poll(async () => (await readStatus(page))?.status, {
          message: '末步激活动作应写 done：等了 5s 仍未落盘',
          timeout: 5000,
        })
        .toBe('done')
      // "引导关了"不等于"用户开始用了"：激活动作必须真执行，且两端执行的是两种不同的事。
      // student 那一步的动作是点页面里的加入课程卡 ⇒ 只能断弹窗；teacher／admin 是导航 ⇒ 断 URL。
      if (role === 'student') {
        const dialog = page.locator('.modal')
        await expect(dialog).toHaveCount(1)
        await expect(dialog.locator('.modal-title')).toHaveText('加入新课程')
        expect(page.url(), '这一端的激活动作是开弹窗，不该跳页').toBe(urlBefore)
      } else {
        await expect(page).not.toHaveURL(urlBefore)
        expect(page.url(), `末步动作没执行：仍停在 ${urlBefore}`).not.toBe(urlBefore)
      }
    })

    test('中途点洞内真元素只前进：URL 不变、不写 done（改前是点一下即永久跳过）', async ({
      page,
    }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(300)
      const anchorKey = (await guideSnapshot(page)).revealedAnchorKey
      expect(anchorKey, '第 2 步的洞里应该有一个带锚点键的真实元素可点').toBeTruthy()
      const urlBefore = page.url()
      await page.locator(`[data-onboard="${anchorKey}"]`).click()
      await page.waitForTimeout(400)
      expect(page.url(), '中间步骤的点击被引导消费，不该导航').toBe(urlBefore)
      expect((await guideSnapshot(page)).count).toBe('3 / 5')
      expect(await readStatus(page), '中间步骤不写终态（一条记录都不该落盘）').toBeNull()
    })

    test('Esc 暂停后刷新，从第 3 步续看', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      await page.keyboard.press('ArrowRight')
      await page.keyboard.press('ArrowRight')
      await page.keyboard.press('Escape')
      await expect(page.locator('.ob-card')).toHaveCount(0)
      expect(await readStatus(page)).toMatchObject({ status: 'paused', atStep: 2 })
      await page.reload()
      await page.waitForSelector('.ob-card')
      expect((await guideSnapshot(page)).count).toBe('3 / 5')
    })

    test('跳过写 skipped，与 done 在数据上可区分', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      await page.locator('.ob-card').getByRole('button', { name: '跳过' }).click()
      expect(await readStatus(page)).toMatchObject({ status: 'skipped' })
      await page.reload()
      // 自动弹出走的是 600ms 定时器；不等过去就断言 toHaveCount(0) 是假绿
      await page.waitForTimeout(1200)
      await expect(page.locator('.ob-card')).toHaveCount(0)
    })

    test('暗色下压暗看得见（色差参照＝洞内那个元素，阈值 24/255）', async ({ page }) => {
      await page.goto(`/${role}/dashboard`)
      await resetGuide(page)
      // 主题必须在 resetGuide 之后再设：reload 会让 data-theme 回到 localStorage 里的值
      await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(300)
      const s = await guideSnapshot(page)
      expect(s.scrimBg, `scrim 色没读到：${s.scrimBg}`).toMatch(/rgba/)
      expect(
        s.deltaOnRevealed,
        `洞内元素 ${s.revealedElement}｜参照＝${s.effectiveBgSource}（有效底色 ${s.effectiveBg?.join(',')}）｜` +
          `合成后单通道差 ${s.deltaOnRevealed}/255，阈值 24（改前 3/255）｜` +
          `同一把尺量页面基底＝${s.deltaOnBody}/255，那条参照不成立`,
      ).not.toBeNull()
      expect(
        s.deltaOnRevealed as number,
        `洞内元素 ${s.revealedElement}｜参照＝${s.effectiveBgSource}｜色差 ${s.deltaOnRevealed}/255 未达 24`,
      ).toBeGreaterThanOrEqual(24)
    })
  })
}

/**
 * 用户菜单的叠放层级（浏览器面）。
 *
 * 【为什么必须有这一条】层级在 jsdom 里量不到（没有布局、没有真堆叠上下文），那边只能钉源码常数；
 * 而 EP 弹层的 `.el-overlay` 层号是 popup manager 在运行时现场分配的（实测读到 2010），
 * 源码里根本没有这个数——只有真浏览器里才分得开"盖得过"和"只是常数写大了"。
 * 判据用 `elementFromPoint` 命中谁，不用比 z-index 字符串。
 */
test.describe('新人引导 · 弹层叠放', () => {
  test.use({
    storageState: 'e2e/visual/.auth/teacher.json',
    viewport: { width: 1440, height: 900 },
  })

  const menuTopPoint = () => {
    const menu = document.querySelector('.pop-menu')
    if (!menu) return null
    const r = menu.getBoundingClientRect()
    const cx = Math.round(r.left + r.width / 2)
    const cy = Math.round(r.top + 12)
    const top = document.elementFromPoint(cx, cy)
    const zOf = (sel: string) => {
      const el = document.querySelector(sel)
      return el ? getComputedStyle(el).zIndex : null
    }
    return {
      cx,
      cy,
      hit: top
        ? `${top.tagName.toLowerCase()}.${(top as HTMLElement).className.trim().split(/\s+/)[0]}`
        : null,
      overEpOverlay: !!(top && top.closest('.el-overlay')),
      inMenu: !!(top && top.closest('.pop-menu')),
      onScrim: !!(top && top.classList.contains('ob-scrim')),
      z: {
        popMenu: zOf('.pop-menu'),
        epOverlay: zOf('.el-overlay'),
        obScrim: zOf('.ob-scrim'),
        obOverlay: zOf('.ob-overlay'),
      },
    }
  }

  test('用户菜单盖得过 EP 弹层的遮罩', async ({ page }) => {
    await page.goto('/teacher/bank')
    await page.waitForTimeout(1500)
    await page.evaluate(() => {
      Object.keys(localStorage)
        .filter((k) => k.includes('onboarding'))
        .forEach((k) => localStorage.removeItem(k))
    })
    await page.reload()
    await page.waitForTimeout(1200)
    await page.getByRole('button', { name: /新建题目/ }).click()
    await page.waitForSelector('.el-dialog', { timeout: 10000 })
    // EP 的遮罩铺满视口，所以菜单所在那点正被它盖着；菜单由 JS 触发——
    // "能不能挂载"是组件状态，"谁盖住谁"才是要量的那一件事，两者分开取。
    await page.evaluate(() =>
      document.querySelector<HTMLElement>('[data-onboard="side-user"]')?.click(),
    )
    await page.waitForTimeout(300)
    const m = await page.evaluate(menuTopPoint)
    expect(m, '菜单没弹出来').not.toBeNull()
    expect(m!.z.epOverlay, 'EP 遮罩不在场，这条就是空读数').not.toBeNull()
    expect(m!.inMenu, `菜单点上的顶元素是 ${m!.hit}（EP 遮罩 z=${m!.z.epOverlay}）`).toBe(true)
    expect(m!.overEpOverlay, `菜单被 EP 遮罩盖住了：命中 ${m!.hit}`).toBe(false)
  })

  test('引导开着时，用户菜单在压暗块之下', async ({ page }) => {
    await page.goto('/teacher/dashboard')
    await resetGuide(page)
    // 走到第 2 步：第 1 步没有压暗块，拿它比层级是空读数；
    // 且当前锚点不是 side-user，这次 JS 点击不会被引导当"点洞内"消费掉，菜单得以与引导同时在场
    await page.keyboard.press('ArrowRight')
    await page.waitForTimeout(300)
    await page.evaluate(() =>
      document.querySelector<HTMLElement>('[data-onboard="side-user"]')?.click(),
    )
    await page.waitForTimeout(300)
    const m = await page.evaluate(menuTopPoint)
    expect(m, '菜单没弹出来').not.toBeNull()
    expect(m!.z.obScrim, '引导的压暗块不在场，这条就是空读数').not.toBeNull()
    expect(m!.onScrim, `菜单点上的顶元素是 ${m!.hit}，不是压暗块（z=${m!.z.obScrim}）`).toBe(true)
  })
})

/* ============================================================================
 * 判据 4 · 真实键盘焦点陷阱（路线图 R-06）
 * ----------------------------------------------------------------------------
 * 【这条用例要闭掉的是哪张空头支票】判据 4（Tab 12 次 / Shift+Tab 回绕仍留在卡内）此前
 * 只有 jsdom 面证据：`OnboardingGuide.focus.test.js` 那两条走的是 `__trapStep()` →
 * `useFocusTrap.ts` 挂在卡片元素上的 `__trapAdvance`，即"转调同一个可聚焦环"的**代触发钩子**。
 * jsdom 不实现顺序聚焦导航，代触发能证明的只有"环是怎么算的"，证不了"真实 keydown 打进来、
 * 焦点会不会漏到背景页"。本用例钉的是后者：**只用 `page.keyboard.press()`，
 * 全程不碰 `__trapStep` / `__trapAdvance` / `locator.focus()`**。
 * spec §8.2 第 1 条把它列为待完成、§8.4 明确"禁止当结论引用"，就是这一段。
 *
 * 【断言口径（只有一条判据）】每次按键后读 `document.activeElement`，判
 * `.ob-card.contains(activeElement)`——`focusProbe().inCard`。`.ob-card` 是
 * OnboardingGuide 模板里包住全部可聚焦控件的那个节点（`role="dialog"` 就挂在它上面；
 * 组件没有 `[data-onboarding-card]` 之类属性，造一个等于改 `src/**`，本轮不许）。
 * 除这条之外只加两条**防空转**的量：12 次正向必须走过环上每一个控件（否则"留在卡内"
 * 可能只是原地不动），以及回绕次数下限（否则"回绕"这一半根本没被走到）。
 *
 * 【步序为什么钉第 2 步】第 2 步 `index>0` 才有「上一步」、又非末步还有「下一步」⇒
 * 卡内 4 个可聚焦控件，12 次 Tab 正好 3 圈、两次"尾→首"回绕；第 1 步只有 3 个，圈数与回绕数都更薄。
 *
 * 【取证】逐步读数（tagName ＋ 可读标识 ＋ 卡内环下标）落
 * `docs/visual-upgrade/evidence/onboarding-tab-2026-10-23/sequence.json`，带本地时
 * `captured_at` 与运行时的 HEAD。**断言失败也照样落盘**——漏出的元素与步序正是这条用例
 * 要留下的东西（R-06 风险栏：跑出真缺陷不算失败，放宽断言才算）。
 *
 * 【落盘默认关，`PW_ONBOARDING_EVIDENCE=1` 才写】见 `EVIDENCE_ENABLED` 的注释：
 * 取证是有意动作，不该是每次跑测的副作用。
 * ========================================================================== */

/** 仓库根：优先问 git（`cwd` 是 frontend 还是仓库根都能对），拿不到再退回 `../` */
const REPO_ROOT = (() => {
  try {
    return execFileSync('git', ['rev-parse', '--show-toplevel'], {
      cwd: process.cwd(),
      encoding: 'utf8',
    }).trim()
  } catch {
    return join(process.cwd(), '..')
  }
})()

const runGit = (args: string[]): string => {
  try {
    return execFileSync('git', args, { cwd: REPO_ROOT, encoding: 'utf8' }).trim()
  } catch {
    return '(git 不可用)'
  }
}

const TAB_EVIDENCE_DIR = join(
  REPO_ROOT,
  'docs',
  'visual-upgrade',
  'evidence',
  'onboarding-tab-2026-10-23',
)
const SEQUENCE_PATH = join(TAB_EVIDENCE_DIR, 'sequence.json')
const SEQUENCE_REL = 'docs/visual-upgrade/evidence/onboarding-tab-2026-10-23/sequence.json'

/**
 * 取证落盘开关：**默认关**，显式带 `PW_ONBOARDING_EVIDENCE=1` 才写 `sequence.json`。
 *
 * 【为什么默认关】取证是**有意的动作**，不是每次跑测的副作用。这份证据已经入库，而它带
 * `captured_at`（本地时）与 `worktree_porcelain_at_write` 两格天然逐次不同 ⇒ 默认写就是
 * "跑一次脏一次"：日常跑套件、CI、乃至 rebase 前顺手跑一遍，工作树都会多出一次与本轮改动
 * 无关的 diff，读 diff 的人还得逐个排除它。默认关之后，"这份证据是不是刚跑出来的"由
 * **有没有显式带开关**回答，而不是靠猜时间戳。
 *
 * 【用法（Git Bash / bash 同法；PowerShell 用 `$env:PW_ONBOARDING_EVIDENCE='1'` 前置）】
 *   cd frontend && PW_ONBOARDING_EVIDENCE=1 npx playwright test onboarding.spec.ts -g "真实键盘"
 *   cd frontend && PW_ONBOARDING_EVIDENCE=1 npx playwright test onboarding.spec.ts --reporter=list
 *
 * 【红了更该开】断言失败时这份序列是这条用例唯一的产出（漏出的元素与步序）。开关关着却跑出红时，
 * `writeSequence()` 会往 stderr 打一行"带开关重跑"的提示，不会静默吞掉。
 * 【关着时不写任何文件，连目录都不建】——`mkdirSync` 也在闸内，否则空目录也会出现在工作树里。
 */
const EVIDENCE_ENABLED = process.env.PW_ONBOARDING_EVIDENCE === '1'

/** 取证时点：**本地时**带偏移（`evidence` 要能被本地时直接读，UTC 会误导） */
const localStamp = (d: Date): string => {
  const pad = (n: number) => String(Math.abs(Math.trunc(n))).padStart(2, '0')
  const offsetMinutes = -d.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` +
    `${sign}${pad(offsetMinutes / 60)}:${pad(offsetMinutes % 60)}`
  )
}

interface FocusReading extends FocusProbe {
  /** 本方向的第几次按键（1 起） */
  step: number
  key: 'Tab' | 'Shift+Tab'
}

interface TabCapture {
  step: { count: string; title: string; placement: string }
  start: FocusProbe | null
  forward: FocusReading[]
  backward: FocusReading[]
  visited: number[]
  forwardWraps: number
  backwardWraps: number
}

const writeSequence = (capture: TabCapture, failure: string | null): void => {
  if (!EVIDENCE_ENABLED) {
    // 关着跑出红：这份序列本该是唯一产出，至少把"怎么拿到它"打给人看，别静默吞掉
    if (failure) {
      console.warn(
        '[onboarding] 取证默认关（PW_ONBOARDING_EVIDENCE 未置 1）：本条红了但没落 sequence.json。\n' +
          '  要留下漏出的元素与步序，带开关重跑：\n' +
          '    cd frontend && PW_ONBOARDING_EVIDENCE=1 npx playwright test onboarding.spec.ts -g "真实键盘"',
      )
    }
    return
  }
  const porcelain = runGit(['status', '--porcelain']).split('\n').filter(Boolean)
  const presses = capture.forward.length + capture.backward.length
  const payload = {
    title: '判据 4 · 真实键盘焦点陷阱取证（路线图 R-06）',
    judgement:
      '引导改造准出判据 4：真实键盘 Tab 12 次与 Shift+Tab 回绕之后，document.activeElement 仍在引导卡子树内',
    captured_at: localStamp(new Date()),
    commit: runGit(['rev-parse', '--short', 'HEAD']),
    commit_subject: runGit(['log', '-1', '--pretty=%s']),
    commit_note:
      '`commit` 是取证运行时工作树的 HEAD。本条用例与 import 它的首个提交可由 ' +
      `\`git log --oneline -- ${SEQUENCE_REL}\` 直接读出（evidence 与该用例同一提交入库，` +
      '所以当时的 HEAD 恰好是它的父提交）。',
    worktree_porcelain_at_write: porcelain,
    run: {
      command: 'cd frontend && npx playwright test onboarding.spec.ts --reporter=list',
      project: 'chromium',
      role: 'student',
      route: '/student/dashboard',
      viewport: '1440x900',
    },
    assertion: {
      card_root_selector: '.ob-card',
      per_press: 'document.activeElement 满足 .ob-card.contains(activeElement)',
      focusable_ring:
        '卡内 tabIndex >= 0 且 getClientRects().length > 0 的元素，按 DOM 序（＝Tab 序）；由浏览器口径判定，不抄陷阱的选择器',
      not_used: '全程未调用 __trapStep / __trapAdvance / locator.focus()，只用 page.keyboard.press',
    },
    step: capture.step,
    card: {
      selector: '.ob-card',
      focusable_count: capture.start ? capture.start.ringSize : null,
      focusable_labels: capture.start ? capture.start.ring : [],
    },
    focus_at_start: capture.start,
    forward: {
      key: 'Tab',
      presses: capture.forward.length,
      all_inside_card: capture.forward.every((r) => r.inCard),
      visited_ring_indices: capture.visited,
      tail_to_head_wraps: capture.forwardWraps,
      sequence: capture.forward,
    },
    backward: {
      key: 'Shift+Tab',
      presses: capture.backward.length,
      all_inside_card: capture.backward.every((r) => r.inCard),
      head_to_tail_wraps: capture.backwardWraps,
      sequence: capture.backward,
    },
    verdict: failure
      ? { result: 'failed', detail: failure }
      : {
          result: 'passed',
          detail:
            `${presses} 次真实按键后 activeElement 全程在 .ob-card 子树内；` +
            `正向尾→首回绕 ${capture.forwardWraps} 次、反向首→尾回绕 ${capture.backwardWraps} 次`,
        },
  }
  mkdirSync(TAB_EVIDENCE_DIR, { recursive: true })
  writeFileSync(SEQUENCE_PATH, JSON.stringify(payload, null, 2) + '\n', 'utf8')
}

test.describe('新人引导 · 真实键盘焦点陷阱', () => {
  test.use({
    storageState: 'e2e/visual/.auth/student.json',
    viewport: { width: 1440, height: 900 },
  })

  test('真实键盘：Tab 12 次 + Shift+Tab 回绕，activeElement 全程在卡内', async ({ page }) => {
    const capture: TabCapture = {
      step: { count: '', title: '', placement: '' },
      start: null,
      forward: [],
      backward: [],
      visited: [],
      forwardWraps: 0,
      backwardWraps: 0,
    }
    let failure: string | null = null

    /** 按一次真实键 → 读一次 activeElement → 当场断言仍在卡内（读数先落 capture，红了也在证据里） */
    const press = async (
      key: 'Tab' | 'Shift+Tab',
      bucket: FocusReading[],
      step: number,
    ): Promise<FocusReading> => {
      await page.keyboard.press(key)
      const reading: FocusReading = { step, key, ...(await focusProbe(page)) }
      bucket.push(reading)
      await expect
        .poll(async () => (await focusProbe(page)).inCard, {
          message:
            `${key} 第 ${step} 次之后 activeElement 逃出引导卡：落在 ${reading.path}；` +
            `卡内可聚焦环 ${reading.ringSize} 个（${reading.ring.join(' / ')}）`,
        })
        .toBe(true)
      return reading
    }

    try {
      await page.goto('/student/dashboard')
      await resetGuide(page)
      // 走到第 2 步。ArrowRight 走的是组件已有的窗口级快捷键，不是本用例的被测面；
      // 被测面从下面第一次 `Tab` 才开始。
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(300)

      const snap = await guideSnapshot(page)
      capture.step = { count: snap.count, title: snap.title, placement: snap.placement }
      expect(snap.count, '必须先真的走到第 2 步：本用例的可聚焦环就是第 2 步那几个控件').toBe(
        '2 / 5',
      )

      const start = await focusProbe(page)
      capture.start = start
      expect(start.cardPresent, '引导卡不在场，后面每一次按键读到的都会是空读数').toBe(true)
      expect(
        start.ringSize,
        `卡内可聚焦控件只有 ${start.ringSize} 个（${start.ring.join(' / ') || '空'}），回绕这一半量不到`,
      ).toBeGreaterThanOrEqual(2)

      // ---- 正向：真实 Tab 12 次 ----
      for (let i = 1; i <= 12; i++) await press('Tab', capture.forward, i)

      const visited = Array.from(
        new Set(capture.forward.map((r) => r.ringIndex).filter((idx) => idx >= 0)),
      ).sort((a, b) => a - b)
      capture.visited = visited
      const ringSize = capture.forward[capture.forward.length - 1].ringSize
      expect(
        visited.length,
        `12 次 Tab 只走过 ${visited.length} 个控件（环上 ${ringSize} 个：${capture.forward[0].ring.join(' / ')}）` +
          ' ⇒ 焦点没真的在环上移动，"留在卡内"就只是原地不动',
      ).toBe(ringSize)

      const forwardWraps = capture.forward.filter(
        (r, i) => i > 0 && capture.forward[i - 1].ringIndex === r.ringSize - 1 && r.ringIndex === 0,
      ).length
      capture.forwardWraps = forwardWraps
      const forwardPath = capture.forward.map((r) => r.ringIndex).join('→')
      expect(
        forwardWraps,
        `12 次 Tab 里"尾→首"回绕只发生 ${forwardWraps} 次（环 ${ringSize} 个 ⇒ 至少 2 次）｜序列 ${forwardPath}`,
      ).toBeGreaterThanOrEqual(2)

      // ---- 反向：真实 Shift+Tab，至少走满一圈（覆盖一次"首→尾"回绕）----
      const reversePath: FocusReading[] = [capture.forward[capture.forward.length - 1]]
      const reversePresses = ringSize + 2
      for (let i = 1; i <= reversePresses; i++) {
        reversePath.push(await press('Shift+Tab', capture.backward, i))
      }
      const backwardWraps = reversePath.filter(
        (r, i) => i > 0 && reversePath[i - 1].ringIndex === 0 && r.ringIndex === r.ringSize - 1,
      ).length
      capture.backwardWraps = backwardWraps
      expect(
        backwardWraps,
        `Shift+Tab 按了 ${reversePresses} 次仍未出现"首→尾"回绕｜序列 ${reversePath
          .map((r) => r.ringIndex)
          .join('→')}`,
      ).toBeGreaterThanOrEqual(1)
    } catch (err) {
      failure = err instanceof Error ? err.message : String(err)
      throw err
    } finally {
      // 绿了要留证，红了更要留证：漏出的元素与步序是这条用例唯一的产出
      writeSequence(capture, failure)
    }
  })
})
