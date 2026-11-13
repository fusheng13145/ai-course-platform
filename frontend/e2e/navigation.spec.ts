import { expect, test } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 侧栏全量可达性巡检（F0「前端可见入口必须与实际能力一致」的浏览器面判据）。
 *
 * 为什么要有这一条：`nav.test.ts` 已经在单测里用真实 router 解析每条侧栏项，
 * 但那只证明「路由表能匹配」，不证明「点下去页面真的出内容、且落到它自己的页面」。
 * 实际发生过的问题恰好落在这一层：路由匹配成功、组件渲染出壳层兜底「该页尚未迁入新版」，
 * 单测全绿而用户看到空壳。这里用真浏览器逐条点，断言四件事：
 *   ① 落点在 `/<role>/` 命名空间内，没被守卫踢回登录页；
 *   ② 落点**与入口一一对应**——除首页那一条外，每条入口都必须换到自己的路径，
 *      且整轮不能出现两条入口落到同一路径（入口别名是"看着有入口、其实去别处"的典型形态）；
 *   ③ 页面上出现正文容器，且不出现兜底文案与 404/503 错误页；
 *   ④ 整轮没有未捕获的运行时异常（pageerror）。
 *
 * ⚠️ 踩过的坑：`@click` 里的 router.push 是**异步**的（守卫要先探一次权限），点完立刻读
 * `page.url()` 仍是旧地址。初版就是这样"3 passed"而每条落点都还是首页。
 * 也不能靠 `page.waitForURL`：它依赖导航事件，在「地址已变、监听才挂上」的间隙里会空等到超时；
 * 本仓这套版本组合下 `expect.poll(...).not` 同样按旧值判红。所以改用 `waitForPathChange`
 * 手写轮询，只有地址真的换了才继续。
 *
 * 另一个坑：中途一版在每个入口之间 `page.goto` 重载，重载后的首次点击偶发不生效
 * （测试侧竞态，同一操作在单次会话里连点则稳定生效）。现在整轮只在开头进一次首页，
 * 后续逐条连点——这既是更接近真实使用的路径，也把这条竞态从判据里排除掉。
 *
 * 侧栏条目从 DOM 读，不从 `nav.ts` 导入：e2e 侧对 `nav.json` 的 JSON 导入在
 * Playwright 的 ESM 加载器下会报「needs an import attribute of type: json」，
 * 而「用户实际看到的入口」本来就以 DOM 为准，反而是更硬的口径。
 */

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN'] as const
type Role = (typeof ROLES)[number]

const ROLE_HOME: Record<Role, string> = {
  STUDENT: '/student/home',
  TEACHER: '/teacher/home',
  ADMIN: '/admin/home',
}

/** 各端侧栏第一条就是首页，点它本来就不该换地址；其余每一条都必须换。 */
const HOME_LABEL: Record<Role, string> = {
  STUDENT: '工作台',
  TEACHER: '工作台',
  ADMIN: '治理总览',
}

/** 兜底/失败文案：出现任意一条即视为该入口没有真实内容面。 */
const FORBIDDEN_TEXT = ['该页尚未迁入新版', '页面不存在', '服务连接失败']

/**
 * 等地址离开 `from`（SPA 的 router.push 是 async 的：守卫里要先探一次权限，
 * 所以点完到地址变化之间有几百毫秒到 1 秒的间隔）。
 *
 * 不用 `page.waitForURL` 也不用 `expect.poll`：前者依赖导航事件，在"地址已变、监听才挂上"
 * 的间隙里会空等到超时；后者在本仓这套 vitest/playwright 版本组合下对 `.not` 的重试行为
 * 与预期不符（实测 20s 后仍按旧值判红）。手写轮询没有这些不确定面。
 */
async function waitForPathChange(page: import('@playwright/test').Page, from: string) {
  const deadline = Date.now() + 20000
  while (Date.now() < deadline) {
    if (new URL(page.url()).pathname !== from) return
    await page.waitForTimeout(100)
  }
  throw new Error(`点了没换页：20s 内仍停在 ${from}`)
}

for (const role of ROLES) {
  test(`侧栏每条入口都能落到自己的真实正文（${role}）`, async ({ page }) => {
    test.setTimeout(300000)
    const pageErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(`${page.url()} :: ${error.message}`))

    await loginAs(page, role)
    await page.goto(ROLE_HOME[role], { waitUntil: 'domcontentloaded' })
    await expect(page.locator('main.main')).toBeVisible({ timeout: 20000 })

    const navItems = page.locator('.sidenav .nav-item')
    await expect(navItems.first()).toBeVisible({ timeout: 20000 })
    const labels = (await navItems.allInnerTexts())
      .map((text) => text.trim().split('\n')[0].trim())
      .filter(Boolean)
    expect(labels.length, `${role} 侧栏一条入口都没有，巡检无从谈起`).toBeGreaterThan(0)

    const landed: Array<{ label: string; path: string }> = []
    let previousPath = new URL(page.url()).pathname

    for (const label of labels) {
      const item = page.locator('.sidenav .nav-item', { hasText: label }).first()
      await expect(item, `${role} 侧栏找不到「${label}」`).toBeVisible({ timeout: 10000 })
      await item.click()

      if (label === HOME_LABEL[role]) {
        await page.waitForTimeout(300)
      } else {
        await waitForPathChange(page, previousPath).catch((error: Error) => {
          throw new Error(`${role}「${label}」${error.message}`)
        })
      }

      const path = new URL(page.url()).pathname
      previousPath = path
      expect(path, `${role}「${label}」被踢出了本端命名空间（${path}）`).toContain(
        `/${role.toLowerCase()}/`,
      )
      await expect(page.locator('main.main'), `${role}「${label}」没有渲染主容器`).toBeVisible({
        timeout: 15000,
      })

      const bodyText = await page.locator('main.main').innerText()
      for (const forbidden of FORBIDDEN_TEXT) {
        expect(bodyText, `${role}「${label}」出现了兜底文案：${forbidden}`).not.toContain(forbidden)
      }
      expect(bodyText.trim().length, `${role}「${label}」正文是空白`).toBeGreaterThan(20)
      landed.push({ label, path })
    }

    // 入口唯一性：两条侧栏项落到同一路径，说明有一条是别名，用户点的是"看起来在这里"的页
    const byPath = new Map<string, string[]>()
    for (const entry of landed) {
      byPath.set(entry.path, [...(byPath.get(entry.path) || []), entry.label])
    }
    const duplicates = [...byPath.entries()].filter(([, names]) => names.length > 1)
    expect(
      duplicates,
      `${role} 有多条入口落到同一路径：${duplicates.map(([p, n]) => `${p}←${n.join('/')}`).join('；')}`,
    ).toEqual([])

    // 逐条落点打印成可复核的行，失败时能直接看出是哪一条
    console.log(
      `[nav-smoke][${role}] ${landed.length} 条：${landed.map((e) => `${e.label}→${e.path}`).join(' | ')}`,
    )
    expect(pageErrors, `${role} 巡检期间出现未捕获异常`).toEqual([])
  })
}
