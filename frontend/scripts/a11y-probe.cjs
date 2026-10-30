#!/usr/bin/env node
/**
 * v2 无障碍与对比度探针（docs/前端待办项.md §4 · P-01 / P-02）
 *
 * 为什么是探针而不是门禁：
 * - 之前只有 2026-10-21 旧端控制台的「近似」读数（各页若干文本节点 <4.5、若干 input 缺名），
 *   既没有可复跑的口径，也从未真正建立 axe 门禁（P-01「违规数只降不升」从未成立）。
 * - 本脚本只做**测量**：在真实登录态下逐页、明暗两档收集
 *   (a) 小字号文本的 WCAG 对比度（<4.5:1 计数 + 最差 10 条）
 *   (b) 表单控件 / 按钮 / 链接的可访问名缺失（计数 + 前 10 条）
 *   并原样打一份紧凑 JSON 到 stdout。**有违规也 exit 0**——这是尺子，不是闸门。
 * - 产物写到 `frontend/test-results/tmp/a11y-probe.json`（gitignored），
 *   不把某台机器某一天的环境读数入库冒充基线。
 *
 * 登录口径与 e2e 一致（e2e/test-helpers.ts）：种子账号 admin/teacher/student、口令 123456、
 * 勾「记住我」让会话落 localStorage 以便同 context 复用；登录接口有 IP 限流
 * （`ratelimit.login` 默认 10 次/60 秒），命中 429 只重试一次（等 15s），
 * 仍 429 就作为 blocker 上报状态码，绝不 mock 响应把读数变成假绿。
 *
 * 覆盖口径（有意收窄，避免把探针写成审计器）：
 * - 文本节点：有直接文本子节点、可见、font-size < 18.66px 的元素（即 WCAG「非大字号」）。
 * - 背景：从元素自身向上找第一层不透明背景，沿途半透明层按 over 合成；默认画布白底。
 * - 渐变 / 背景图是已知盲区：`backgroundImage !== 'none'` 会打 `bgImage:true` 标出来，
 *   但仍按 background-color 计算——这类读数只能当“疑似”，不能当结论。
 * - 可访问名：aria-label / aria-labelledby（解析后的文本）/ 关联 <label> / 按钮与链接的文本内容
 *   / title / submit 类 input 的 value。**placeholder 不算可访问名**（WCAG 3.3.2 要的是标签），
 *   select 的 option 文本也不算控件名（那是值，不是名字）。
 *
 * 用法：
 *   node scripts/a11y-probe.cjs                # 默认 http://localhost:5173
 *   A11Y_BASE_URL=http://localhost:5175 node scripts/a11y-probe.cjs
 */

const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright')

const BASE_URL = process.env.A11Y_BASE_URL || 'http://localhost:5173'
const OUT_FILE = path.join(__dirname, '..', 'test-results', 'tmp', 'a11y-probe.json')
const PASSWORD = '123456'
const SETTLE_MS = 800
const CONTRAST_MIN = 4.5
const MAX_FONT_PX = 18.66
const LOGIN_RETRY_WAIT_MS = 15000

const PAGES = [
  { key: 'student-home', role: 'student', url: '/student/home', ready: '.kpi-grid' },
  {
    key: 'student-course-overview',
    role: 'student',
    url: '/student/courses/4001/overview',
    ready: '.course-head',
  },
  { key: 'teacher-inbox', role: 'teacher', url: '/teacher/inbox', ready: '.inbox-body' },
  { key: 'teacher-exams', role: 'teacher', url: '/teacher/exams', ready: '.module .kpi-grid' },
  { key: 'admin-users', role: 'admin', url: '/admin/users', ready: '.users-body' },
  { key: 'admin-uploads', role: 'admin', url: '/admin/uploads', ready: '.module .kpi-grid' },
]

const ACCOUNTS = { student: 'student', teacher: 'teacher', admin: 'admin' }

function log(message) {
  process.stderr.write(`[a11y-probe] ${message}\n`)
}

/**
 * 页面内执行的收集器。必须自包含（序列化后注入浏览器），不能引用外部变量。
 * 返回结构：计数 + 最差 10 条 + 缺名前 10 条；不做任何判定/豁免决定。
 */
function collectA11y(options) {
  const { contrastMin, maxFontPx } = options

  const SKIP_TAGS = new Set([
    'SCRIPT',
    'STYLE',
    'NOSCRIPT',
    'TEMPLATE',
    'OPTION',
    'OPTGROUP',
    'TITLE',
    'HEAD',
    'META',
    'LINK',
  ])

  function isVisible(el) {
    if (!(el instanceof Element)) return false
    const cs = getComputedStyle(el)
    if (cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') {
      return false
    }
    if (Number.parseFloat(cs.opacity || '1') === 0) return false
    // 视觉隐藏的 sr-only（clip / clip-path 收成 0 尺寸）不参与对比度：它不是给眼睛看的
    if (
      cs.clip !== 'auto' ||
      cs.clipPath === 'inset(50%)' ||
      (cs.position === 'absolute' && Number.parseFloat(cs.width) <= 1)
    ) {
      return false
    }
    const rect = el.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0
  }

  function parseColor(input) {
    if (!input) return null
    const text = String(input).trim()
    let m = text.match(
      /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.%]+))?\s*\)$/i,
    )
    if (m) {
      let a =
        m[4] === undefined ? 1 : m[4].endsWith('%') ? Number.parseFloat(m[4]) / 100 : Number(m[4])
      return { r: Number(m[1]), g: Number(m[2]), b: Number(m[3]), a }
    }
    // Chromium 新版对 color()/lab() 颜色可能原样返回；这里只认 srgb 三分量，其余交给 CSS 归一化
    m = text.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)(?:\s*\/\s*([\d.%]+))?\)$/i)
    if (m) {
      let a =
        m[4] === undefined ? 1 : m[4].endsWith('%') ? Number.parseFloat(m[4]) / 100 : Number(m[4])
      return { r: Number(m[1]) * 255, g: Number(m[2]) * 255, b: Number(m[3]) * 255, a }
    }
    m = text.match(/^#([0-9a-f]{3,8})$/i)
    if (m) {
      let hex = m[1]
      if (hex.length === 3 || hex.length === 4) {
        hex = hex
          .split('')
          .map((c) => c + c)
          .join('')
      }
      const r = Number.parseInt(hex.slice(0, 2), 16)
      const g = Number.parseInt(hex.slice(2, 4), 16)
      const b = Number.parseInt(hex.slice(4, 6), 16)
      const a = hex.length >= 8 ? Number.parseInt(hex.slice(6, 8), 16) / 255 : 1
      return { r, g, b, a }
    }
    // 兜底：借 canvas 把任意合法 CSS 颜色归一化成 rgb()
    try {
      const canvas = document.createElement('canvas')
      canvas.width = 1
      canvas.height = 1
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#010203'
      ctx.fillStyle = text
      const normalized = ctx.fillStyle
      if (normalized !== '#010203' && normalized !== text) return parseColor(normalized)
    } catch {
      /* 忽略：颜色解析失败按未知处理 */
    }
    return null
  }

  function over(fg, bg) {
    const a = fg.a + bg.a * (1 - fg.a)
    if (a === 0) return { r: 0, g: 0, b: 0, a: 0 }
    const mix = (f, b) => (f * fg.a + b * bg.a * (1 - fg.a)) / a
    return { r: mix(fg.r, bg.r), g: mix(fg.g, bg.g), b: mix(fg.b, bg.b), a }
  }

  function backgroundOf(el) {
    const layers = []
    let node = el
    let bgImage = false
    while (node && node.nodeType === 1) {
      const cs = getComputedStyle(node)
      if (cs.backgroundImage && cs.backgroundImage !== 'none') bgImage = true
      const color = parseColor(cs.backgroundColor)
      if (color && color.a > 0) {
        layers.push(color)
        if (color.a >= 1) break
      }
      node = node.parentElement
    }
    let base = { r: 255, g: 255, b: 255, a: 1 }
    for (let i = layers.length - 1; i >= 0; i -= 1) base = over(layers[i], base)
    return { color: base, bgImage }
  }

  function relativeLuminance(color) {
    const channel = (value) => {
      const v = value / 255
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b)
  }

  function contrastRatio(a, b) {
    const l1 = relativeLuminance(a)
    const l2 = relativeLuminance(b)
    const light = Math.max(l1, l2)
    const dark = Math.min(l1, l2)
    return (light + 0.05) / (dark + 0.05)
  }

  function cssPath(el) {
    const parts = []
    let node = el
    let depth = 0
    while (node && node.nodeType === 1 && depth < 5) {
      if (node.id && document.querySelectorAll(`#${CSS.escape(node.id)}`).length === 1) {
        parts.unshift(`#${CSS.escape(node.id)}`)
        break
      }
      let part = node.tagName.toLowerCase()
      const classes = Array.from(node.classList)
        .filter((name) => !/^(v-|data-v-)/.test(name))
        .slice(0, 2)
      if (classes.length) part += `.${classes.map((name) => CSS.escape(name)).join('.')}`
      const parent = node.parentElement
      if (parent) {
        const sameTag = Array.from(parent.children).filter(
          (child) => child.tagName === node.tagName,
        )
        if (sameTag.length > 1) part += `:nth-of-type(${sameTag.indexOf(node) + 1})`
      }
      parts.unshift(part)
      node = node.parentElement
      depth += 1
    }
    return parts.join(' > ')
  }

  function formatColor(color) {
    const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)))
    const alpha = Math.round(color.a * 100) / 100
    return alpha >= 1
      ? `rgb(${clamp(color.r)}, ${clamp(color.g)}, ${clamp(color.b)})`
      : `rgba(${clamp(color.r)}, ${clamp(color.g)}, ${clamp(color.b)}, ${alpha})`
  }

  function hasOwnText(el) {
    for (const node of el.childNodes) {
      if (node.nodeType === 3 && node.nodeValue && node.nodeValue.trim()) return true
    }
    return false
  }

  // ---------- (a) 对比度 ----------
  const failures = []
  let textNodes = 0
  let bgImageNodes = 0
  let bgImageFailures = 0
  const all = document.body ? document.body.querySelectorAll('*') : []
  for (const el of all) {
    if (SKIP_TAGS.has(el.tagName)) continue
    if (!hasOwnText(el)) continue
    if (!isVisible(el)) continue
    const cs = getComputedStyle(el)
    const fontSize = Number.parseFloat(cs.fontSize)
    if (!Number.isFinite(fontSize) || fontSize >= maxFontPx) continue
    textNodes += 1

    const bg = backgroundOf(el)
    const fgRaw = parseColor(cs.color)
    if (!fgRaw) continue
    const fg = fgRaw.a < 1 ? over(fgRaw, bg.color) : fgRaw
    const ratio = contrastRatio(fg, bg.color)
    if (bg.bgImage) bgImageNodes += 1
    if (ratio < contrastMin) {
      if (bg.bgImage) bgImageFailures += 1
      failures.push({
        target: cssPath(el),
        text: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 40),
        ratio: Math.round(ratio * 100) / 100,
        fg: formatColor(fg),
        bg: formatColor(bg.color),
        bgImage: bg.bgImage,
        fontSize,
        // 禁用态控件在 WCAG 1.4.3 里是豁免的；探针仍计入，但标出来供分诊
        disabled:
          (typeof el.matches === 'function' && el.matches(':disabled')) ||
          Boolean(el.closest('button:disabled, [aria-disabled="true"]')),
      })
    }
  }
  failures.sort((a, b) => a.ratio - b.ratio)

  // 按「前景色 on 背景色」聚合失败数：一处令牌修正能清掉多少条，靠这张表看
  const pairMap = new Map()
  for (const failure of failures) {
    const key = `${failure.fg} on ${failure.bg}`
    const entry = pairMap.get(key) || {
      fg: failure.fg,
      bg: failure.bg,
      ratio: failure.ratio,
      count: 0,
    }
    entry.count += 1
    pairMap.set(key, entry)
  }
  const byPair = Array.from(pairMap.values())
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)

  // ---------- (b) 可访问名 ----------
  const nameCandidates = document.querySelectorAll(
    'input:not([type="hidden"]), select, textarea, button, a[href]',
  )
  const missingNames = []
  let namedControls = 0
  for (const el of nameCandidates) {
    if (!isVisible(el)) continue
    namedControls += 1
    const ariaLabel = (el.getAttribute('aria-label') || '').trim()
    const labelledBy = (el.getAttribute('aria-labelledby') || '')
      .split(/\s+/)
      .filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent || '')
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim()
    const labelText = el.labels
      ? Array.from(el.labels)
          .map((label) => label.textContent || '')
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
      : ''
    const title = (el.getAttribute('title') || '').trim()
    const alt = (el.getAttribute('alt') || '').trim()
    const imgAlt = (el.querySelector('img[alt]')?.getAttribute('alt') || '').trim()
    // 文本内容只能给 button / a 当名字。select 的 textContent 是 option 值、textarea 的是初值，
    // 按 accname 规范都不是控件名——否则「未标注的下拉框」会被 option 文本静默洗白。
    const text = /^(BUTTON|A)$/.test(el.tagName)
      ? (el.textContent || '').replace(/\s+/g, ' ').trim()
      : ''
    const isSubmitish = el.tagName === 'INPUT' && /^(submit|button|reset)$/i.test(el.type || '')
    const value = isSubmitish ? (el.value || '').trim() : ''
    const name = ariaLabel || labelledBy || labelText || text || title || alt || imgAlt || value
    if (!name) {
      missingNames.push({
        target: cssPath(el),
        tag: el.tagName.toLowerCase(),
        type: el.getAttribute('type') || '',
        placeholder: el.getAttribute('placeholder') || '',
      })
    }
  }

  return {
    url: location.pathname + location.search,
    theme:
      document.documentElement.dataset.theme ||
      document.documentElement.getAttribute('data-theme') ||
      '',
    shellTheme: document.body?.dataset?.theme || '',
    contrast: {
      textNodes,
      failures: failures.length,
      bgImageNodes,
      bgImageFailures,
      byPair,
      worst: failures.slice(0, 10),
    },
    names: {
      controls: namedControls,
      missing: missingNames.length,
      samples: missingNames.slice(0, 10),
    },
  }
}

async function fillField(page, selector, value) {
  const input = page.locator(selector).first()
  await input.waitFor({ state: 'visible', timeout: 15000 })
  await input.focus()
  await input.fill(value)
  // v2 登录页会防自动填充：挂载后短暂清空。落在窗口内就重填一次（与 e2e 同口径）。
  await page.waitForTimeout(80)
  if ((await input.inputValue()) !== value) {
    await input.focus()
    await input.fill(value)
  }
}

/**
 * 单次登录尝试，返回 /api/auth/login 的 HTTP 状态码（拿不到响应记 0）。
 * 429 的判定必须来自响应而不是 waitForURL 超时：后端限流不打日志，
 * 只看超时的话分不清“被限流”和“页面坏了”。
 */
async function attemptLogin(page, account) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
  await page.locator('form.av-form').waitFor({ state: 'visible', timeout: 20000 })
  await fillField(page, 'input[name="username"]', account)
  await fillField(page, 'input[name="password"]', PASSWORD)
  const remember = page.locator('.av-check input[type="checkbox"]')
  if ((await remember.count()) > 0)
    await remember
      .first()
      .check()
      .catch(() => {})

  const responsePromise = page
    .waitForResponse((res) => new URL(res.url()).pathname === '/api/auth/login', {
      timeout: 20000,
    })
    .catch(() => null)
  await page
    .getByRole('button', { name: /登录|登\s*录|Login/i })
    .first()
    .click()
  const response = await responsePromise
  return response ? response.status() : 0
}

async function loginAs(page, account, role) {
  let status = await attemptLogin(page, account)
  if (status === 429) {
    log(`登录 ${account} 命中 429（IP 限流），等 ${LOGIN_RETRY_WAIT_MS / 1000}s 重试一次`)
    await page.waitForTimeout(LOGIN_RETRY_WAIT_MS)
    status = await attemptLogin(page, account)
    if (status === 429) {
      log(`登录 ${account} 重试后仍 429，登记为 blocker`)
      return { ok: false, status }
    }
  }
  if (status !== 200) {
    log(`登录 ${account} 失败：HTTP ${status}`)
    return { ok: false, status }
  }
  try {
    await page.waitForURL(new RegExp(`/${role}/`), { timeout: 20000 })
  } catch {
    return {
      ok: false,
      status,
      detail: '登录接口返回 200 但未跳到角色首页（可能是业务错误码或会话未建立）',
    }
  }
  return { ok: true, status }
}

async function collectPage(page, pageDef, theme) {
  const result = {
    page: pageDef.key,
    role: pageDef.role,
    path: pageDef.url,
    theme,
    ready: false,
    error: '',
  }
  try {
    await page.goto(`${BASE_URL}${pageDef.url}`, { waitUntil: 'domcontentloaded' })
    try {
      await page.locator(pageDef.ready).first().waitFor({ state: 'visible', timeout: 20000 })
      result.ready = true
    } catch {
      result.error = `ready selector ${pageDef.ready} 未出现（仍按当前 DOM 采集）`
      log(`${pageDef.key}/${theme}: ${result.error}`)
    }
    await page.waitForTimeout(SETTLE_MS)
    const data = await page.evaluate(collectA11y, {
      contrastMin: CONTRAST_MIN,
      maxFontPx: MAX_FONT_PX,
    })
    Object.assign(result, data)
    if (data.theme && data.theme !== theme) {
      result.error = [result.error, `theme mismatch: expected ${theme}, got ${data.theme}`]
        .filter(Boolean)
        .join('; ')
      log(`${pageDef.key}: 主题未生效（期望 ${theme}，实际 ${data.theme}）`)
    }
  } catch (err) {
    result.error = [result.error, String(err && err.message ? err.message : err)]
      .filter(Boolean)
      .join('; ')
    log(`${pageDef.key}/${theme} 采集失败：${result.error}`)
  }
  return result
}

async function collectForTheme(page, pages, theme) {
  const out = []
  for (const pageDef of pages) out.push(await collectPage(page, pageDef, theme))
  return out
}

function summarize(results) {
  const totals = { light: {}, dark: {} }
  for (const theme of ['light', 'dark']) {
    const rows = results.filter((row) => row.theme === theme && row.contrast)
    totals[theme] = {
      pages: rows.length,
      contrastFailures: rows.reduce((sum, row) => sum + row.contrast.failures, 0),
      missingNames: rows.reduce((sum, row) => sum + row.names.missing, 0),
    }
  }
  return totals
}

async function main() {
  const startedAt = new Date()
  const summary = {
    probe: 'a11y-probe',
    version: 1,
    generatedAt: startedAt.toISOString(),
    baseUrl: BASE_URL,
    thresholds: {
      contrastMin: CONTRAST_MIN,
      maxFontPx: MAX_FONT_PX,
      note: 'bgImage=true 的节点背景是渐变/图片，读数按 background-color 近似，只作疑似',
    },
    logins: {},
    results: [],
    totals: {},
    blockers: [],
  }

  const browser = await chromium.launch({ headless: true })
  try {
    // ---- 未登录：/login 明暗两档 ----
    const guestContext = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const guestPage = await guestContext.newPage()
    const loginDef = { key: 'login', role: 'guest', url: '/login', ready: '.av-form' }
    summary.results.push(await collectPage(guestPage, loginDef, 'light'))
    await guestContext.addInitScript(() => {
      try {
        localStorage.setItem('v2-theme', 'dark')
      } catch {
        /* about:blank 上可能不可写，导航后由页面自身脚本再写 */
      }
    })
    summary.results.push(await collectPage(guestPage, loginDef, 'dark'))
    await guestContext.close()

    // ---- 每个角色一个 context：登录一次，先浅后深复用会话（避免 3 次额外登录触发限流） ----
    const roles = ['student', 'teacher', 'admin']
    for (const role of roles) {
      const pages = PAGES.filter((pageDef) => pageDef.role === role)
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
      const page = await context.newPage()
      const login = await loginAs(page, ACCOUNTS[role], role)
      summary.logins[role] = login
      if (!login.ok) {
        summary.blockers.push({
          kind: 'login',
          role,
          account: ACCOUNTS[role],
          status: login.status,
          detail:
            login.detail ||
            (login.status === 429
              ? '登录被 IP 限流（ratelimit.login 默认 10 次/60 秒），15s 后重试仍 429；未做任何 mock'
              : `登录未成功（HTTP ${login.status}）`),
        })
        await context.close()
        continue
      }
      summary.results.push(...(await collectForTheme(page, pages, 'light')))

      await context.addInitScript(() => {
        try {
          localStorage.setItem('v2-theme', 'dark')
        } catch {
          /* 忽略 */
        }
      })
      summary.results.push(...(await collectForTheme(page, pages, 'dark')))
      await context.close()
    }
  } finally {
    await browser.close()
  }

  summary.totals = summarize(summary.results)

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true })
  fs.writeFileSync(OUT_FILE, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
  log(`产物：${OUT_FILE}`)
  process.stdout.write(`${JSON.stringify(summary)}\n`)
}

main().catch((err) => {
  // 启动/浏览器级失败也要留下结构化输出，但这是环境故障，用非 0 退出让调用方看见
  process.stderr.write(`[a11y-probe] fatal: ${err && err.stack ? err.stack : err}\n`)
  process.exit(1)
})
