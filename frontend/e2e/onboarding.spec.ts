import { test, expect } from '@playwright/test'
import { resetGuide, guideSnapshot, readStatus } from './onboarding-helpers'

/**
 * 三端新人引导 e2e（改前 F10 那条"e2e 面零断言"就是这里补上的）。
 *
 * 【只用 storageState，绝不 loginAs】登录端点按 IP 限流（10 次/60 秒、429 还不打日志），
 * 三端 ×N 条重登会把失败集合变成随机集合。夹具在 `e2e/visual/.auth/`，由视觉 config 的
 * globalSetup 生成；这一份流程 config 不带 globalSetup，所以**夹具过期时会跳 /login**——
 * 那种红不是产品缺陷，重生成：`PW_PORT=5175 npx playwright test -c playwright.visual.ts helpers --grep "会话"`。
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
      expect((await readStatus(page))?.status).toBe('done')
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

    test('中途点洞内真元素只前进：URL 不变、不写 done（改前是点一下即永久跳过）', async ({ page }) => {
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
      hit: top ? `${top.tagName.toLowerCase()}.${(top as HTMLElement).className.trim().split(/\s+/)[0]}` : null,
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
    await page.evaluate(() => document.querySelector<HTMLElement>('[data-onboard="side-user"]')?.click())
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
    await page.evaluate(() => document.querySelector<HTMLElement>('[data-onboard="side-user"]')?.click())
    await page.waitForTimeout(300)
    const m = await page.evaluate(menuTopPoint)
    expect(m, '菜单没弹出来').not.toBeNull()
    expect(m!.z.obScrim, '引导的压暗块不在场，这条就是空读数').not.toBeNull()
    expect(m!.onScrim, `菜单点上的顶元素是 ${m!.hit}，不是压暗块（z=${m!.z.obScrim}）`).toBe(true)
  })
})
