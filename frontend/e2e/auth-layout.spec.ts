import { test, expect } from '@playwright/test'

/**
 * 认证页布局守卫。
 *
 * 2026-11-19 修复的真实缺陷：注册页双列表单在窄容器里被输入框固有宽度撑破，
 * 右侧「姓名 / 手机号 / 确认密码」越过卡片边界。这里只钉布局语义（不钉具体像素）：
 * 页面无横向滚动、所有控件落在卡片内、窄屏下左侧品牌区退出。
 */
const ROUTES = ['/login', '/register', '/forgot-password'] as const
const VIEWPORTS = [
  { width: 1440, height: 900, label: 'desktop' },
  { width: 768, height: 900, label: 'tablet' },
  { width: 390, height: 844, label: 'mobile' },
] as const

test.describe('认证页布局', () => {
  for (const viewport of VIEWPORTS) {
    test(`${viewport.label} 三页均无横向溢出且控件不越出卡片`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })

      for (const route of ROUTES) {
        await page.goto(route, { waitUntil: 'domcontentloaded' })
        await expect(page.locator('.av-form')).toBeVisible()

        const layout = await page.evaluate(() => {
          const card = document.querySelector('.av-card')?.getBoundingClientRect()
          if (!card) throw new Error('缺少 .av-card')
          const controls = [...document.querySelectorAll('.av-control, .av-code-btn')].map((el) =>
            el.getBoundingClientRect(),
          )
          return {
            scrollWidth: document.documentElement.scrollWidth,
            viewportWidth: window.innerWidth,
            cardRight: card.right,
            maxControlRight: Math.max(...controls.map((box) => box.right)),
            contextDisplay: getComputedStyle(document.querySelector('.av-context')!).display,
          }
        })

        expect(
          layout.scrollWidth,
          `${route} 在 ${viewport.width}x${viewport.height} 下出现横向滚动`,
        ).toBeLessThanOrEqual(layout.viewportWidth)
        expect(layout.maxControlRight, `${route} 有控件越过卡片右边界`).toBeLessThanOrEqual(
          layout.cardRight + 0.5,
        )
        expect(layout.contextDisplay, `${route} 品牌区显示状态异常`).toBe(
          viewport.width > 1060 ? 'flex' : 'none',
        )
      }
    })
  }

  test('密码显隐与记住我可用', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    const password = page.locator('input[name=password]')
    const remember = page.locator('.av-check input[type=checkbox]')

    await expect(password).toHaveAttribute('type', 'password')
    await page.getByRole('button', { name: '显示密码' }).click()
    await expect(password).toHaveAttribute('type', 'text')
    await page.getByRole('button', { name: '隐藏密码' }).click()
    await expect(password).toHaveAttribute('type', 'password')

    await expect(remember).not.toBeChecked()
    await remember.check()
    await expect(remember).toBeChecked()
  })

  test('找回密码三步指示随步骤推进', async ({ page }) => {
    await page.goto('/forgot-password', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.av-step.is-active')).toHaveCount(1)
    await expect(page.locator('.av-step.is-active')).toContainText('邮箱')
    await expect(page.locator('.av-step.is-done')).toHaveCount(0)
  })
})
