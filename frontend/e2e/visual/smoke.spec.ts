import { expect, test } from '@playwright/test'
import { setTheme } from './visual-helpers'

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
