import { chromium } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'

/**
 * 三端新人引导的**改后浏览器实测**取证脚本（非像素判据：整卡可见 rect ＋ scrim 合成色差 ＋ 出口落盘）。
 *
 * 跑法（工作目录 `frontend/`；需后端 8080 在听、dev server 起在本端口上）：
 *   PW_PORT=5175 npx playwright test e2e/onboarding.spec.ts --reporter=line   ← 先看会话夹具是否还能用
 *   npm run dev -- --port 5175 --strictPort &                               ← 本脚本自己不起服务，复用同端口
 *   PW_PORT=5175 node e2e/visual/tools/verify-onboarding.mjs
 *
 * 【为什么脚本入库、产物也入库】`test-results/` 被两份 Playwright config 共用且都没设 `outputDir`
 * ——任何一次跑测都会整目录清空，落在里面的证据活不过下一次 `npx playwright test`。
 * 报告与截图因此固定写到 `docs/visual-upgrade/evidence/onboarding-verify-2026-10-22/`（入库面）。
 *
 * 【量程声明，读结论前必看】色差分量的算法是：取**洞内被露出的那个元素**的有效底色，
 * 把 scrim 的计算样式按 alpha 合成到它上面，再与未合成的它比单通道差。
 * 这是**计算样式层面的合成**，不是渲染像素——它能量出"压暗到底有没有发生"，
 * 不能替代像素基线（本改造明确不加像素基线，见设计文档的测试节）。
 * 参照物逐条写进读数（`effectiveBgSource`），因为拿页面基底 `--bg` 当参照会得到一条永不成立的判据：
 * 暗色 body＝`#0f1419`，0.66 的黑压上去只差 17/255，而阈值是 24——那不是"压暗看不见"，那是探针选错。
 *
 * 【会话夹具】storageState 走 `e2e/visual/.auth/{student,teacher,admin}.json`，**绝不重登**
 * （登录限流 10 次/60 秒，且这三份文件含会话令牌）：本脚本从不读取、也从不打印它们的内容。
 */
const PORT = process.env.PW_PORT || '5175'
const BASE = `http://localhost:${PORT}`
const OUT_URL = new URL(
  '../../../../docs/visual-upgrade/evidence/onboarding-verify-2026-10-22/',
  import.meta.url,
)
const OUT = fileURLToPath(OUT_URL)
const ROLES = ['student', 'teacher', 'admin']
/** 可见性阈值：与既有单测（`src/onboarding/__tests__/scrim.contrast.test.js`）同一把尺，不在此处放宽。 */
const THRESHOLD = 24
/** 浏览器侧读数器的参数：安全边距与阈值都从这一处喂进去，函数体内不留第二份常数。 */
const GUIDE_OPTS = { threshold: THRESHOLD, safePad: 8 }

mkdirSync(OUT, { recursive: true })

/**
 * 浏览器侧唯一读数器：整卡可见性、洞外压暗块、洞内被露出的元素及其有效底色、
 * scrim 合成后的单通道最大色差（三种参照各算一遍，参照物随读数落盘）。
 * 参数只有 `opts`，其余全部就地定义——Playwright 只序列化这一个函数体。
 */
function readGuideInBrowser(opts) {
  const parse = (s) => {
    const str = String(s || '').trim()
    const m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+))?\s*\)/.exec(str)
    if (m) return [Number(m[1]), Number(m[2]), Number(m[3]), m[4] === undefined ? 1 : Number(m[4])]
    const h = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(str)
    if (h) {
      const hex =
        h[1].length === 3
          ? h[1]
              .split('')
              .map((c) => c + c)
              .join('')
          : h[1]
      return [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16)).concat(1)
    }
    return null
  }
  const MAX = (a, b) => Math.max(...a.map((c, i) => Math.abs(c - b[i])))
  /** fg 按自身 alpha 合成到 bg 之上 */
  const over = (fg, bg) => {
    const a = fg[3] ?? 1
    return fg.slice(0, 3).map((c, i) => Math.round(c * a + bg[i] * (1 - a)))
  }
  const label = (el) => {
    if (!el || !el.tagName) return String(el)
    const cls = (typeof el.className === 'string' ? el.className : '')
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 3)
    const onboard = el.dataset && el.dataset.onboard ? `[data-onboard=${el.dataset.onboard}]` : ''
    return `${el.tagName.toLowerCase()}${onboard}${cls.length ? '.' + cls.join('.') : ''}`
  }

  const card = document.querySelector('.ob-card')
  const scrim = document.querySelector('.ob-scrim')
  const ring = document.querySelector('.ob-ring')
  const cs = getComputedStyle(document.documentElement)
  const surfaceToken = parse(cs.getPropertyValue('--surface'))
  const bgToken = parse(cs.getPropertyValue('--bg'))
  const bodyBg = parse(getComputedStyle(document.body).backgroundColor)

  // 洞本身没有元素（四块矩形拼出来的空洞），所以环的中心就是被露出元素的采样点；
  // `.ob-ring`／`.ob-overlay` 都是 pointer-events:none，elementFromPoint 会跳过它们命中真实控件。
  let revealed = null
  if (ring) {
    const rr = ring.getBoundingClientRect()
    revealed = document.elementFromPoint(rr.left + rr.width / 2, rr.top + rr.height / 2)
  }

  // 沿祖先链求"洞内真正被看到的那层颜色"：透明层记账待合成，遇到不透明层即止；
  // 途中撞见渐变（侧栏那条 linear-gradient）时不继续下潜——渐变盖在它下面的一切之上，
  // 而计算样式没法把渐变归约成单色，参照退回 --surface 令牌值（比渐变暗端更保守）。
  const trace = []
  const film = []
  let effective = null
  let source = null
  let gradientStops = null
  let node = revealed
  while (node && node.nodeType === 1) {
    const st = getComputedStyle(node)
    const c = parse(st.backgroundColor)
    const gradient = /(linear|radial)-gradient/.test(st.backgroundImage || '')
    trace.push(`${label(node)}:${st.backgroundColor}${gradient ? '+gradient' : ''}`)
    if (gradient) {
      effective = surfaceToken
      source = `祖先 ${label(node)} 画的是渐变，计算样式不可归约为单色 ⇒ 参照退回令牌 --surface`
      // 渐变本身仍要量：计算样式里的 var() 已被解析成字面色，所以直接从 background-image 串里
      // 取各停靠色，逐个合成一遍，取**最小**的那个差作为保守读数。
      // 侧栏那条渐变两主题同值（#232b3a→#1a2130），浅色主题下 --surface 是白的，
      // 只报退回值会把"浅色下压在侧栏的几步其实几乎没压暗"这件事盖掉。
      gradientStops = Array.from(String(st.backgroundImage).matchAll(/rgba?\([^)]*\)/g))
        .map((m) => parse(m[0]))
        .filter(Boolean)
        .map((p, gi) => ({ index: gi, rgb: p.slice(0, 3) }))
      break
    }
    if (c && c[3] === 1) {
      const base = c.slice(0, 3)
      effective = film.reduceRight((acc, layer) => over(layer, acc), base)
      source = `不透明层 ${label(node)}（其上的半透明层逐层合成）`
      break
    }
    if (c && c[3] > 0) film.push(c)
    node = node.parentElement
  }
  if (!effective) {
    effective = surfaceToken
    source = '整条祖先链都透明 ⇒ 参照退回令牌 --surface'
  }
  if (film.length && !source) source = '仅半透明层 ⇒ 合成到令牌 --surface'

  const scrimColor = scrim ? parse(getComputedStyle(scrim).backgroundColor) : null
  const r = card ? card.getBoundingClientRect() : null
  const pad = opts && opts.safePad ? opts.safePad : 8
  const v = { w: window.innerWidth, h: window.innerHeight }

  return {
    themeAttr: document.documentElement.getAttribute('data-theme'),
    count: document.querySelector('.ob-count')?.textContent?.trim() ?? '',
    title: document.querySelector('.ob-title')?.textContent?.trim() ?? '',
    desc: document.querySelector('.ob-desc')?.textContent?.trim() ?? '',
    placement: card?.dataset?.placement ?? '',
    centered: !!card?.classList?.contains('centered'),
    card: r
      ? {
          top: Math.round(r.top),
          left: Math.round(r.left),
          bottom: Math.round(r.bottom),
          right: Math.round(r.right),
          width: Math.round(r.width),
          height: Math.round(r.height),
        }
      : null,
    /** 未取整的 rect 与几何层实际看到的 offsetWidth/offsetHeight 并列落盘：
     *  几何层按 offsetHeight（整数）判"贴边后仍在 8px 边距内"，渲染高带小数时两者会差不到 1px，
     *  这一格就是那条读数的量程来源。 */
    cardExact: r
      ? {
          top: Number(r.top.toFixed(2)),
          left: Number(r.left.toFixed(2)),
          bottom: Number(r.bottom.toFixed(2)),
          right: Number(r.right.toFixed(2)),
          height: Number(r.height.toFixed(2)),
        }
      : null,
    cardOffset: card ? { width: card.offsetWidth, height: card.offsetHeight } : null,
    viewport: v,
    cardFullyVisible:
      !!r && r.top >= pad && r.left >= pad && r.bottom <= v.h - pad && r.right <= v.w - pad,
    scrimCount: document.querySelectorAll('.ob-scrim').length,
    scrimBg: scrim ? getComputedStyle(scrim).backgroundColor : '',
    ringPresent: !!ring,
    /** 描边矩形（＝洞的位置）与它是否整块在视口内：窄屏下侧栏被 translateX(-100%) 推出画外，
     *  锚点仍在 DOM 里但洞只能被夹到视口边上——这一格就是那条读数的来源。 */
    ringRect: ring
      ? (() => {
          const rr = ring.getBoundingClientRect()
          return {
            top: Number(rr.top.toFixed(2)),
            left: Number(rr.left.toFixed(2)),
            bottom: Number(rr.bottom.toFixed(2)),
            right: Number(rr.right.toFixed(2)),
          }
        })()
      : null,
    ringFullyInView: ring
      ? (() => {
          const rr = ring.getBoundingClientRect()
          return (
            rr.top >= 0 &&
            rr.left >= 0 &&
            rr.bottom <= window.innerHeight &&
            rr.right <= window.innerWidth
          )
        })()
      : null,
    revealedAnchor: revealed?.dataset?.onboard ?? null,
    revealedElement: label(revealed),
    ancestorTrace: trace,
    effectiveBg: effective,
    effectiveBgSource: source,
    gradientStops: gradientStops || null,
    /** 渐变两个色标各合成一遍取最小差（保守读数）；没有渐变时为 null */
    deltaOnGradientMin:
      scrimColor && gradientStops && gradientStops.length
        ? Math.min(...gradientStops.map((s) => MAX(over(scrimColor, s.rgb), s.rgb)))
        : null,
    surfaceToken,
    bgToken,
    bodyBg,
    deltaOnRevealed:
      scrimColor && effective ? MAX(over(scrimColor, effective), effective.slice(0, 3)) : null,
    deltaOnSurface:
      scrimColor && surfaceToken
        ? MAX(over(scrimColor, surfaceToken), surfaceToken.slice(0, 3))
        : null,
    deltaOnBody: scrimColor && bodyBg ? MAX(over(scrimColor, bodyBg), bodyBg.slice(0, 3)) : null,
    threshold: opts.threshold,
    anchorsInDom: document.querySelectorAll('[data-onboard]').length,
    anchorKeys: Array.from(document.querySelectorAll('[data-onboard]')).map(
      (e) => e.dataset.onboard,
    ),
    v2Raw: (() => {
      const k = Object.keys(localStorage).find((x) => x.startsWith('ai-course:onboarding:v2:'))
      if (!k) return null
      const p = JSON.parse(localStorage.getItem(k))
      return { key: k, status: p.status, atStep: p.atStep, role: p.role, hasSeenAt: !!p.seenAt }
    })(),
  }
}

const GUIDE_KEY_CLEAN = () => {
  Object.keys(localStorage)
    .filter((k) => k.includes('onboarding'))
    .forEach((k) => localStorage.removeItem(k))
}

async function openFreshGuide(page, role) {
  await page.goto(`${BASE}/${role}/dashboard`)
  await page.waitForTimeout(700)
  await page.evaluate(GUIDE_KEY_CLEAN)
  await page.reload()
  await page.waitForSelector('.ob-card', { timeout: 12000 })
  await page.waitForTimeout(400)
}

const shot = (name) => (page) => page.screenshot({ path: `${OUT}/${name}.png` })

const browser = await chromium.launch()
const report = {
  generatedFrom: 'e2e/visual/tools/verify-onboarding.mjs',
  baseUrl: BASE,
  threshold: THRESHOLD,
  measureCaveat:
    '色差＝scrim 的计算样式按 alpha 合成到"洞内被露出元素"的有效底色之上，再与该底色比单通道最大差；是计算样式合成，不是渲染像素。',
  roles: {},
}

for (const role of ROLES) {
  const per = {
    mainWalkDark: [],
    lightProbe: [],
    activation: {},
    narrowWalk: {},
    tinyWalk: {},
    miniSidebarWalk: [],
  }

  // —— 主走查：1440×900，默认暗色，逐步骤读数＋截图 ——
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const consoleHits = []
  ctx.on('console', (msg) => {
    const t = msg.text()
    if (t.includes('onboarding') || t.includes('anchor-miss'))
      consoleHits.push(`${msg.type()}: ${t}`)
  })
  const page = await ctx.newPage()
  await openFreshGuide(page, role)
  for (let i = 0; i < 5; i++) {
    per.mainWalkDark.push(await page.evaluate(readGuideInBrowser, GUIDE_OPTS))
    await shot(`${role}-step-${i + 1}`)(page)
    if (i < 4) {
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(320)
    }
  }

  // —— 浅色对照：同一步在 data-theme 切到 light 后重读（主题必须在清完引导之后再设）——
  await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'light'))
  await page.waitForTimeout(120)
  per.lightProbe.push({
    // 标签更正（R-19b）：第 5 步的锚点 student 在页面内（join-course 卡），teacher/admin
    // 在侧栏导航上——旧标签把三端都写成"页面内锚点"，六格浅色读数里只有 student 那格
    // 的参照物真的是页面内卡片。读数本身有效（参照物是真读的），只是标签错。
    at: '第 5 步（激活步；锚点位置按端不同：student 页面内，teacher/admin 侧栏导航）',
    ...(await page.evaluate(readGuideInBrowser, GUIDE_OPTS)),
  })
  await shot(`${role}-light-step-5`)(page)
  await page.keyboard.press('ArrowLeft')
  await page.waitForTimeout(320)
  await page.keyboard.press('ArrowLeft')
  await page.waitForTimeout(320)
  per.lightProbe.push({
    at: '第 3 步（shell 导航锚点，洞压在侧栏渐变上）',
    ...(await page.evaluate(readGuideInBrowser, GUIDE_OPTS)),
  })
  await shot(`${role}-light-step-3`)(page)

  // —— 激活动作真的执行了吗：末步点「开始使用」后，弹窗面（student）或 URL（teacher/admin）——
  await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
  await page.keyboard.press('ArrowRight')
  await page.waitForTimeout(320)
  await page.keyboard.press('ArrowRight')
  await page.waitForTimeout(320)
  const urlBefore = page.url()
  await page.locator('.ob-card').getByRole('button', { name: '开始使用' }).click()
  await page.waitForTimeout(1200)
  const urlAfter = page.url()
  const dialogTitle = await page
    .locator('.modal .modal-title')
    .first()
    .textContent()
    .catch(() => null)
  const dialogCount = await page.locator('.modal').count()
  per.activation = {
    urlBefore,
    urlAfter,
    urlChanged: urlBefore !== urlAfter,
    cardGone: (await page.locator('.ob-card').count()) === 0,
    overlayGone: (await page.locator('.ob-overlay').count()) === 0,
    dialogCount,
    dialogTitle: dialogTitle ? dialogTitle.trim() : null,
    stateAfterActivate: await page.evaluate(readGuideInBrowser, GUIDE_OPTS),
  }
  await shot(`${role}-after-activate`)(page)
  per.consoleHits = consoleHits
  await ctx.close()

  // —— dashboard 上有没有更直接的激活动作（spec §5 第 1 条要的对照面）——
  const ctxDash = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const pageDash = await ctxDash.newPage()
  await pageDash.goto(`${BASE}/${role}/dashboard`)
  await pageDash.waitForTimeout(1400)
  per.dashboardPrimaryActions = await pageDash.evaluate(() =>
    Array.from(
      document.querySelectorAll(
        '.content button, .content [role="button"], .content .btn, .content .course-card',
      ),
    )
      .map((el) => ({
        text: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 28),
        cls: (typeof el.className === 'string' ? el.className : '')
          .trim()
          .split(/\s+/)
          .slice(0, 3)
          .join('.'),
        onboard: el.dataset?.onboard ?? null,
      }))
      .filter((x) => x.text)
      .slice(0, 14),
  )
  await ctxDash.close()

  // —— 窄屏 375×667 逐步骤整卡可见性（判据 1 的那一档）——
  const ctxN = await browser.newContext({
    viewport: { width: 375, height: 667 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const pageN = await ctxN.newPage()
  await openFreshGuide(pageN, role)
  for (let i = 0; i < 5; i++) {
    const s = await pageN.evaluate(readGuideInBrowser, GUIDE_OPTS)
    per.narrowWalk['375x667'] = per.narrowWalk['375x667'] || []
    per.narrowWalk['375x667'].push({
      count: s.count,
      title: s.title,
      placement: s.placement,
      card: s.card,
      cardExact: s.cardExact,
      cardOffset: s.cardOffset,
      viewport: s.viewport,
      cardFullyVisible: s.cardFullyVisible,
      scrimCount: s.scrimCount,
      ringRect: s.ringRect,
      ringFullyInView: s.ringFullyInView,
      deltaOnRevealed: s.deltaOnRevealed,
      deltaOnGradientMin: s.deltaOnGradientMin,
      effectiveBgSource: s.effectiveBgSource,
      anchorsInDom: s.anchorsInDom,
    })
    if (i === 1) await shot(`${role}-375-step-2`)(pageN)
    if (i === 4) await shot(`${role}-375-step-5`)(pageN)
    if (i < 4) {
      await pageN.keyboard.press('ArrowRight')
      await pageN.waitForTimeout(320)
    }
  }
  await ctxN.close()

  // —— 330 宽：原为推算的一条，这里实测 ——
  const ctxT = await browser.newContext({
    viewport: { width: 330, height: 720 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const pageT = await ctxT.newPage()
  await openFreshGuide(pageT, role)
  await pageT.keyboard.press('ArrowRight')
  await pageT.waitForTimeout(360)
  const tiny = await pageT.evaluate(readGuideInBrowser, GUIDE_OPTS)
  per.tinyWalk['330x720'] = {
    count: tiny.count,
    title: tiny.title,
    placement: tiny.placement,
    card: tiny.card,
    cardExact: tiny.cardExact,
    cardOffset: tiny.cardOffset,
    viewport: tiny.viewport,
    cardFullyVisible: tiny.cardFullyVisible,
    scrimCount: tiny.scrimCount,
    ringRect: tiny.ringRect,
    ringFullyInView: tiny.ringFullyInView,
    deltaOnRevealed: tiny.deltaOnRevealed,
    deltaOnGradientMin: tiny.deltaOnGradientMin,
    effectiveBgSource: tiny.effectiveBgSource,
    anchorsInDom: tiny.anchorsInDom,
  }
  await shot(`${role}-330-step-2`)(pageT)
  await ctxT.close()

  // —— 折叠侧栏（mini）：锚点是否仍在、卡片是否仍整卡可见 ——
  const ctxM = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: `e2e/visual/.auth/${role}.json`,
  })
  const pageM = await ctxM.newPage()
  await openFreshGuide(pageM, role)
  await pageM.locator('[title="折叠侧边栏"]').click()
  await pageM.waitForTimeout(700)
  const miniHits = []
  pageM.on('console', (msg) => {
    const t = msg.text()
    if (t.includes('anchor-miss')) miniHits.push(t)
  })
  for (let i = 0; i < 5; i++) {
    const s = await pageM.evaluate(readGuideInBrowser, GUIDE_OPTS)
    per.miniSidebarWalk.push({
      count: s.count,
      title: s.title,
      placement: s.placement,
      card: s.card,
      viewport: s.viewport,
      cardFullyVisible: s.cardFullyVisible,
      ringPresent: s.ringPresent,
      ringFullyInView: s.ringFullyInView,
      revealedAnchor: s.revealedAnchor,
      deltaOnRevealed: s.deltaOnRevealed,
      deltaOnGradientMin: s.deltaOnGradientMin,
      effectiveBgSource: s.effectiveBgSource,
      anchorsInDom: s.anchorsInDom,
      anchorKeys: s.anchorKeys,
    })
    if (i < 4) {
      await pageM.keyboard.press('ArrowRight')
      await pageM.waitForTimeout(320)
    }
  }
  await shot(`${role}-mini-step-2`)(pageM)
  per.miniSidebarAnchorMissHits = miniHits
  await ctxM.close()

  report.roles[role] = per
  console.log(
    `[verify] ${role}：主走查 ${per.mainWalkDark.length} 步 · 色差（洞内参照）${per.mainWalkDark.map((s) => s.deltaOnRevealed ?? '-').join('/')}`,
  )
}

// —— 层级读数：真实叠放面（用户菜单 vs EP 弹层的遮罩 vs 引导的压暗块）——
// 【为什么不能只看 z-index 数值】两份 Playwright 配置之外的那条 jsdom 守卫比的是源码里的令牌常数；
// EP 的 `.el-overlay` 由 popup manager 在运行时分配层号（2000 起步、逐个自增），
// 那个数在源码里根本不存在——只有浏览器里才量得到。所以这里两条读数都落到 elementFromPoint：
// 同一点上命中谁，谁就在上。菜单由 JS 直接 click() 触发（EP 遮罩会吃掉真实点击的命中测试，
// 而"菜单能不能挂载"与"谁盖住谁"是两件事，前者靠状态、后者靠命中点）。
{
  const hitTest = () => {
    const menu = document.querySelector('.pop-menu')
    if (!menu) return null
    const r = menu.getBoundingClientRect()
    const cx = Math.round(r.left + r.width / 2)
    const cy = Math.round(r.top + 12)
    const top = document.elementFromPoint(cx, cy)
    const z = (sel) => {
      const el = document.querySelector(sel)
      return el ? getComputedStyle(el).zIndex : null
    }
    const name = (el) =>
      !el
        ? null
        : `${el.tagName.toLowerCase()}${
            typeof el.className === 'string' && el.className.trim()
              ? '.' + el.className.trim().split(/\s+/)[0]
              : ''
          }`
    return {
      menuPoint: { cx, cy },
      hitTop: name(top),
      hitIsMenuItem: !!top?.closest?.('.pop-menu'),
      hitIsScrim: !!top?.classList?.contains('ob-scrim'),
      hitIsEpOverlay: !!top?.closest?.('.el-overlay'),
      zIndexes: {
        popMenu: z('.pop-menu'),
        epOverlay: z('.el-overlay'),
        epDialog: z('.el-dialog'),
        obOverlay: z('.ob-overlay'),
        obScrim: z('.ob-scrim'),
        obCard: z('.ob-card'),
      },
    }
  }

  // 读数一：EP 弹层开着 ⇒ 菜单在它之上（命中点在遮罩里，遮罩铺满视口）
  const ctxD = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: 'e2e/visual/.auth/teacher.json',
  })
  const pageD = await ctxD.newPage()
  await pageD.goto(`${BASE}/teacher/bank`)
  await pageD.waitForTimeout(1500)
  await pageD.evaluate(GUIDE_KEY_CLEAN)
  await pageD.reload()
  await pageD.waitForTimeout(1200)
  await pageD.getByRole('button', { name: /新建题目/ }).click()
  await pageD.waitForSelector('.el-dialog', { timeout: 8000 })
  await pageD.waitForTimeout(400)
  await pageD.evaluate(() => document.querySelector('[data-onboard="side-user"]')?.click())
  await pageD.waitForTimeout(300)
  report.stackingMenuOverEpDialog = {
    ...(await pageD.evaluate(hitTest)),
    dialogTitle: await pageD
      .locator('.el-dialog .el-dialog__title')
      .first()
      .textContent()
      .catch(() => null),
  }
  await shot('stacking-menu-over-ep-dialog')(pageD)
  await ctxD.close()

  // 读数二：引导开着 ⇒ 菜单在压暗块之下（同一个命中点被 .ob-scrim 拿走）
  const ctxG = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: 'e2e/visual/.auth/teacher.json',
  })
  const pageG = await ctxG.newPage()
  await openFreshGuide(pageG, 'teacher')
  // 先走到第 2 步：第 1 步无锚点、根本没有压暗块，拿它比层级是空读数。
  // 停在第 2 步（当前锚点不是 side-user）时引导不会把这次 JS 点击当"点洞内"消费掉，菜单得以与引导同时挂载。
  await pageG.keyboard.press('ArrowRight')
  await pageG.waitForTimeout(400)
  await pageG.evaluate(() => document.querySelector('[data-onboard="side-user"]')?.click())
  await pageG.waitForTimeout(300)
  report.stackingMenuUnderGuide = {
    ...(await pageG.evaluate(hitTest)),
    guideCount: await pageG.locator('.ob-card').count(),
    scrimCount: await pageG.locator('.ob-scrim').count(),
    stepAt: (await pageG.locator('.ob-count').textContent())?.trim(),
  }
  await shot('stacking-menu-under-guide')(pageG)
  await ctxG.close()

  const tokens = await (async () => {
    const c = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      storageState: 'e2e/visual/.auth/teacher.json',
    })
    const p = await c.newPage()
    await p.goto(`${BASE}/teacher/dashboard`)
    await p.waitForTimeout(1200)
    const v = await p.evaluate(() => {
      const root = getComputedStyle(document.documentElement)
      const tok = (n) => root.getPropertyValue(n).trim()
      return {
        menu: tok('--z-menu'),
        shellMenu: tok('--z-shell-menu'),
        toast: tok('--z-toast'),
        onboarding: tok('--z-onboarding'),
      }
    })
    await c.close()
    return v
  })()
  report.stackingTokens = tokens
}

writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 2))
await browser.close()
console.log(`[verify] 报告与截图落点：${pathToFileURL(OUT).href}`)
