import { expect, test } from '@playwright/test'
import { freezeClock, maskOf, setTheme, settlePage, snapName, storagePathFor, THEME_KEY, waitForStableText } from './visual-helpers'

// helper 自身的六条测试（smoke.spec.ts 只管主题注入在页面上生效）。
// 为什么值得单开：settlePage / freezeClock 是 24 张基线的公共依赖，它们出错的方式是
// "每页都假红"或"每页都假绿"，比 smoke 的用例更隐蔽——上一轮的 #1/#2 两个缺陷正是
// 落在这段无断言覆盖区里。
// 前三条用 setContent 造页面，不依赖后端；第四、五条真起 dev server 打 /login；第六条是纯函数。

// 零统计卡的页面（登录页就是）必须直接判稳定，而不是空转到 15s 超时后
// 抛一句"可能仍在动画中"的假诊断。
test('settlePage 在无 .s-value/.pg-title 的页上不超时', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"><h1>纯静态</h1></div>')
  await settlePage(page)
})

// 时钟必须真的停下来：只 install 不冻结时，页面时间按真实速率前进（本机实测 400ms 真实
// → 416ms 页面），秒级相对文案会漂，Task 4 的"二次跑零 diff"就变成随机红灯。
// 这条断言就是判定 freezeClock 写法生效与否的仲裁，不许改成容差。
test('freezeClock 之后页面时间不再随真实耗时前进', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"></div>')
  const t1 = await page.evaluate(() => Date.now())
  await page.waitForTimeout(400)
  const t2 = await page.evaluate(() => Date.now())
  expect(t2 - t1).toBe(0)
})

// 有统计卡的页面，文本变化期间不得提前放行。
// 注意：时钟停住后 setInterval 不会自走，是 waitForStableText 每轮的 runFor 在推进它。
test('waitForStableText 在文本仍在变时不立即返回', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"><span class="s-value">0</span></div>')
  await page.evaluate(() => {
    const el = document.querySelector('.s-value')!
    let n = 0
    const id = setInterval(() => {
      el.textContent = String(++n)
      if (n > 6) clearInterval(id)
    }, 50)
  })
  await waitForStableText(page)
  await expect(page.locator('.s-value')).toHaveText('7')
})

// THEME_KEY 必须是活的常量：helper 里写死的 'fz-theme' 一旦与它漂移，
// 24 张基线会全部静默落回 useTheme 的默认 dark，而断言只比图的话根本发现不了。
test('setTheme 把主题写在 THEME_KEY 指定的键下', async ({ page }) => {
  await setTheme(page, 'light')
  await page.goto('/login')
  // THEME_KEY 必须当参数传进页面：page.evaluate 的函数体是序列化后在浏览器里跑的，
  // 直接引用闭包常量会得到 ReferenceError: THEME_KEY is not defined
  const stored = await page.evaluate((key) => localStorage.getItem(key), THEME_KEY)
  expect(stored).toBe('light')
})

// 真实锚点页上的契约：/login 既没有 PageHeader 也没有 StatCard，评审点名的
// "可能仍在动画中"假诊断正是在这一页炸的；顺带确认真实页面加载后时钟仍钉在冻结点。
test('settlePage 在真实 /login 锚点上收敛且时间仍冻结', async ({ page }) => {
  await freezeClock(page)
  await page.goto('/login')
  await settlePage(page)
  const t1 = await page.evaluate(() => Date.now())
  await page.waitForTimeout(300)
  expect((await page.evaluate(() => Date.now())) - t1).toBe(0)
  // 冻结点 = 09:00:00 + pauseAt 的 5000ms + settlePage 的 runFor(5000)，与机器快慢无关
  expect(t1 - new Date('2026-10-18T09:00:00').getTime()).toBe(10000)
})

// 三个纯函数也钉住：snapName 多写一个 `.png` 或留下 `{platform}` 字面量，
// maskOf 被标回 async，都是 Task 4 落 24 张基线时才暴露、届时最难归因的那类错。
test('snapName / maskOf / storagePathFor 的形状与同步签名', async ({ page }) => {
  expect(snapName('student-home', 'light')).toBe('student-home-light')
  expect(snapName('admin-user', 'dark')).toBe('admin-user-dark')
  const masks = maskOf(page, ['.avatar', '.cover'])
  expect(masks).toHaveLength(2) // 若 maskOf 回到 async，这里拿到的是 Promise，断言直接红
  expect(storagePathFor('STUDENT')).toBe('e2e/visual/.auth/student.json')
  expect(storagePathFor('ADMIN')).toBe('e2e/visual/.auth/admin.json')
})
