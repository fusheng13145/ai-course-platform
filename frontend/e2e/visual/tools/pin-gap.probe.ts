import { expect, test, type BrowserContextOptions } from '@playwright/test'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { ANCHORS, storagePathFor, type AnchorSpec } from '../visual-helpers'

/**
 * Task 0 Step 1 的**只读差集取证**（修复轮 1 的 I-3② 把它从 gitignore 目录挪进入库面）。
 * 跑法（工作目录 `frontend/`，需后端 8080 与 dev server 5175 在听）：
 *   `npx playwright test -c e2e/visual/tools/pin-gap.config.ts`
 *
 * 【它回答的问题】三格里"页面实际发出的 `/api/` GET"与"该格 `pinPaths` 声明的键"差几条，
 * 以及差集里哪几条的响应体带着服务端"今天"的日期。差集表进报告，改哪一格由项目主判。
 *
 * 【纪律（逐条都是踩过坑之后写死的）】
 *  · 三格路径与现值 `pinPaths` **全部从 `visual-helpers.ts` 的 ANCHORS 按 slug 现读**，
 *    脚本里不写死任何一条路径或端点——简报那句"`/admin/governance` 类"就是靠这条纪律被纠正的
 *    （ANCHORS 实读 `path: '/admin/analytics'`；写成 governance 会被 catch-all 兜成 404 页而断言全绿）。
 *  · 只读：不点任何控件、不发非 GET（非 GET 只**记方法名**，SPA 自己的写请求拦不住也不该拦）。
 *  · 绝不打印 `.auth/*.json` 的内容——只把路径交给 Playwright 的 storageState，本脚本从不读那个文件。
 *  · 复用会话夹具、绝不重登（DR-85：登录限流默认 10 次/60 秒）。
 *  · 读数同时落 `test-results/pin-gap/`（简报要求的位置）与系统临时目录
 *    ——`test-results/` 会被任何一次官方套件跑测清空（见 `pin-gap.config.ts` 与 LEDGER `P0a-HARNESS-03`）。
 *
 * 【与 I-2 那条运行时断言的分工】这条脚本是**测量面**（哪几条键、带不带今天、跑在改动之前），
 * `payload-pin.ts` 的 `assertAllApiGetsPinned` 是**断言面**（每次跑 anchors 都重判一次、漏钉当场红）。
 * 修复轮 1 之前只有前者，所以"下一次漂"是静默的；现在两者都在，脚本继续负责"为什么"，断言负责"有没有"。
 */
const TARGET_SLUGS = ['login', 'student-dashboard', 'admin-governance-analytics']

interface CellReport {
  slug: string
  role: string
  /** 从 ANCHORS 实读的 path */
  path: string
  /** 从 ANCHORS 实读的 pinPaths（无该键 ⇒ 空数组） */
  pinPaths: string[]
  /** 页面实际发出的 GET `/api/` 请求（pathname+search，去重、按出现序） */
  apiGets: string[]
  /** 差集＝发出过而没钉 */
  unpinned: string[]
  /** 差集中响应体含服务端"今天"日期的那些（日期敏感＝最可能把基线弄漂） */
  unpinnedTodayBearing: string[]
  /** 这一页发过的非 GET `/api` 请求（钉桩只支持 GET，如实记下不钉它） */
  nonGetApi: string[]
  /** 诊断：证明"这一页真打开了"而不是被踢回 /login */
  title: string
  pathnameAfter: string
  headingCount: number
}

function isoDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

test('三格 /api 请求清单与 pinPaths 的差集（Task 0 Step 1 只读取证）', async ({
  browser,
  baseURL,
}) => {
  test.setTimeout(240000)
  const serverToday = isoDay(new Date())
  const out: CellReport[] = []

  for (const slug of TARGET_SLUGS) {
    const anchor = ANCHORS.find((a) => a.slug === slug) as AnchorSpec
    expect(anchor, `ANCHORS 里没有 ${slug}`).toBeTruthy()
    expect(
      typeof anchor.path,
      `${slug} 的 path 不是字符串（__FROM_COURSE_LIST__ 这类动态路径不归本脚本）`,
    ).toBe('string')

    const ctxOpts: BrowserContextOptions = { baseURL }
    if (anchor.role !== 'ANON') ctxOpts.storageState = storagePathFor(anchor.role)
    const ctx = await browser.newContext(ctxOpts)
    const page = await ctx.newPage()

    const gets: string[] = []
    const nonGet: string[] = []
    const seenGet = new Set<string>()
    // 与 payload-pin.ts 那条审计同口径：pathname+search、排除已钉，只看响应体里有没有服务端"今天"
    const todayBearing = new Set<string>()
    const pending: Promise<void>[] = []

    page.on('request', (req) => {
      const u = new URL(req.url())
      if (!u.pathname.startsWith('/api/')) return
      if (req.method() === 'GET') {
        const k = `${u.pathname}${u.search}`
        if (!seenGet.has(k)) {
          seenGet.add(k)
          gets.push(k)
        }
        return
      }
      nonGet.push(`${req.method()} ${u.pathname}${u.search}`)
    })
    page.on('response', (res) => {
      const u = new URL(res.url())
      if (!u.pathname.startsWith('/api/') || res.request().method() !== 'GET') return
      const k = `${u.pathname}${u.search}`
      pending.push(
        res
          .text()
          .then((t) => {
            if (t.includes(serverToday)) todayBearing.add(k)
          })
          .catch(() => undefined),
      )
    })

    await page.goto(anchor.path as string, { waitUntil: 'load' })
    await page.waitForLoadState('networkidle')
    // 给懒加载与图表初始化的补量请求一次机会；纯等待，不交互
    await page.waitForTimeout(3000)
    await Promise.all(pending)

    const pinPaths = anchor.pinPaths ?? []
    const report: CellReport = {
      slug,
      role: anchor.role,
      path: anchor.path as string,
      pinPaths,
      apiGets: gets,
      unpinned: gets.filter((k) => !pinPaths.includes(k)),
      unpinnedTodayBearing: [...todayBearing].filter((k) => !pinPaths.includes(k)),
      nonGetApi: [...new Set(nonGet)],
      title: await page.title(),
      pathnameAfter: await page.evaluate(() => location.pathname),
      headingCount: await page.locator('h1, h2, h3').count(),
    }
    out.push(report)
    console.log(
      `[gap] ${slug} path=${report.path} 落在=${report.pathnameAfter} GET=${gets.length} ` +
        `未钉=${report.unpinned.length} 日期敏感未钉=${report.unpinnedTodayBearing.length} 标题="${report.title}"`,
    )
    for (const k of report.unpinned) {
      console.log(
        `       未钉 ${k}${report.unpinnedTodayBearing.includes(k) ? '  [响应体含服务端今天]' : ''}`,
      )
    }
    await ctx.close()
  }

  const json = `${JSON.stringify({ serverToday, cells: out }, null, 2)}\n`
  const repoDir = join(process.cwd(), 'test-results', 'pin-gap')
  mkdirSync(repoDir, { recursive: true })
  const repoCopy = join(repoDir, 'pin-gap-diff.json')
  writeFileSync(repoCopy, json, 'utf8')
  const tmpCopyDir = join(tmpdir(), 'pin-gap-master')
  mkdirSync(tmpCopyDir, { recursive: true })
  const tmpCopy = join(tmpCopyDir, 'pin-gap-diff.json')
  writeFileSync(tmpCopy, json, 'utf8')
  console.log(`[gap] 写出 ${repoCopy}（副本 ${tmpCopy}）`)
  expect(existsSync(repoCopy)).toBe(true)
})
