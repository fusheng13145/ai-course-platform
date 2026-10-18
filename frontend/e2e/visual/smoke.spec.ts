// 与下方会话夹具用例合并成一条 import：两处各 `import { expect } from '@playwright/test'`
// 是 SyntaxError: Identifier 'expect' has already been declared（本机 node ESM 实测），
// 整个 spec 会在加载期就炸掉，两条主题用例连带失效。
import { expect, test, test as base } from '@playwright/test'
import { freezeClock, setTheme, settlePage } from './visual-helpers'

// 目的只有一件：证明"主题是被钉住的"，不是碰巧渲染成某个样子。
// useTheme.ts:6 的默认值是 'dark'，所以"不设主题就当作浅色"的假设必然错；
// 若 setTheme 注入失败，light 分支会读到默认的 dark 而两遍同图，本用例即失效而不报错——
// 因此两条断言分别校验 data-theme 属性，而不是只比截图。
for (const theme of ['light', 'dark'] as const) {
  test(`锚点页在 ${theme} 主题下 data-theme 命中`, async ({ page }) => {
    await setTheme(page, theme)
    await page.goto('/login')
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
  })
}

// 会话必须真的可用：auth 加固后，未登录访问受限页会被守卫踢回 /login
// （router/index.ts:598）。本用例用 admin 夹具进 /admin/dashboard，
// 断言 URL 没被改写、且侧边栏 landmark 已渲染（AppShell 的 nav，D2-7 第五批）。
//
// 为什么写成 base.describe 而不是文件级 test.use：夹具一挂到文件级，上面两条 /login
// 主题用例也会带着 admin 会话跑，而已登录访问 /login 会被守卫跳走默认落地页
// （router/index.ts:579-591 的 P0-3 分支），data-theme 断言就变成了测重定向。
// 会话夹具的作用域必须和它的需求一样大——只有这一条。
base.describe('会话夹具', () => {
  base.use({ storageState: 'e2e/visual/.auth/admin.json' })
  base('admin 夹具可直达受限工作台', async ({ page }) => {
    await freezeClock(page)
    await setTheme(page, 'light')
    await page.goto('/admin/dashboard')
    await settlePage(page)
    // 断 pathname，不能断 toContain('/admin/dashboard')：未登录时守卫跳的是
    // /login?redirect=/admin/dashboard，目标路径被原样塞进 query，toContain 照样绿——
    // 负控实测（空夹具）就是这一行放过、只靠下面的 nav 兜住的。
    expect(new URL(page.url()).pathname).toBe('/admin/dashboard')
    await expect(page.locator('nav[aria-label="主导航"]')).toBeVisible()
  })
})
