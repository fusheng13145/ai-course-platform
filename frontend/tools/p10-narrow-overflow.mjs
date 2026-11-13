#!/usr/bin/env node
/**
 * P-10 窄屏浏览器实测（编码完成基线 §12.1 C 条）
 * ------------------------------------------------------------------
 * 用途：对 的**全部路由**（真值取自运行时的 vue-router getRoutes()，
 *       出处 src/ui/router.ts 的 `path:` 与 `page()` 两类记录，src/ui/nav.ts 的
 *       PATHS 作交叉核对）在三档窄视口 360x740 / 375x667 / 768x1024 下做真实量测：
 *
 *   ① document.documentElement.scrollWidth  vs  window.innerWidth   → 文档级横向溢出
 *   ② .main 的 scrollWidth vs clientWidth                             → 被 overflow:clip 静默裁切
 *   ③ 溢出元素清单（tag.class + position + 越界像素）                  → 定位证据
 *
 * 判定三态：
 *   OVERFLOW  ① > 0        文档能横向滚（用户可救，但等于横向滚动条）
 *   CLIP      ① == 0 且 ② > 0  内容被 .main{overflow:clip} 静默丢失（最危险）
 *   OK        两者皆 <= 0
 *   REDIRECT  导航被守卫改写（落点与请求路径不同）——不计入三态，单独如实登记
 *   NOENTRY   需要写操作造数据才能进入，未量测
 *
 * 会话：复用 e2e/visual/.auth/{student,teacher,admin}.json 已落盘的 storageState，
 *       不重新登录（后端 ratelimit.login 10 次/60 秒）。cookie 域是 localhost，
 *       与端口无关，可直接换到本脚本自己的 5176；localStorage 是按 origin 存的，
 *       所以把夹具里的键值用 addInitScript 重放到本 origin。
 *
 * 用法：
 *   node tools/p10-narrow-overflow.mjs --label=before
 *   node tools/p10-narrow-overflow.mjs --label=after --viewports=360x740,375x667,768x1024
 *   可选：--port=5176  --out=<json>  --control=1440x900（桌面档对照组）
 *
 * 不做的事：不写 test-results/（跑测会整目录清空）、不碰 e2e/、不改任何判据、
 *           不截图基线、不触发 --update-snapshots。
 */
import { spawn } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { chromium } from '@playwright/test'

const HERE = dirname(fileURLToPath(import.meta.url))
const FRONTEND = resolve(HERE, '..')

const argv = process.argv.slice(2)
const flag = (name, dflt) => {
  const hit = argv.find((a) => a.startsWith(`--${name}=`))
  return hit ? hit.slice(name.length + 3) : dflt
}

const LABEL = flag('label', 'run')
const PORT = Number(flag('port', 5176))
const BASE = `http://localhost:${PORT}`
const parsePairs = (raw) =>
  String(raw)
    .split(',')
    .map((s) => {
      const [w, h] = s.split('x').map(Number)
      return { key: `${w}x${h}`, width: w, height: h }
    })
    .filter((v) => Number.isInteger(v.width) && Number.isInteger(v.height))
const NARROW = parsePairs(flag('viewports', '360x740,375x667,768x1024'))
const CONTROL = flag('control', 'off') === 'off' ? [] : parsePairs(flag('control', ''))
const OUT = resolve(flag('out', join(FRONTEND, 'tools', `p10-results-${LABEL}.json`)))
const SETTLE_MS = Number(flag('settle', 1200))
const ONLY_GROUPS = flag('groups', 'guest,student,teacher,admin').split(',')

const ROLE_HOME_PATH = { student: '/student/home', teacher: '/teacher/home', admin: '/admin/home' }
// meta.role 是大写枚举（STUDENT/TEACHER/ADMIN），这里做的是 meta -> 分组键的映射；
// 早先写成 ROLE_PREFIX[meta.role] 会把所有角色路由都漏进 guest 组，症状是全线 REDIRECT。
const ROLE_KEYS = ['student', 'teacher', 'admin']

/* --------------------------------------------------------------- dev server */
async function startDevServer() {
  const child = spawn(
    process.execPath,
    [join(FRONTEND, 'node_modules', 'vite', 'bin', 'vite.js'), '--port', String(PORT), '--strictPort'],
    { cwd: FRONTEND, env: { ...process.env, FORCE_COLOR: '0' }, stdio: ['ignore', 'pipe', 'pipe'] },
  )
  let log = ''
  child.stdout.on('data', (b) => (log += b.toString()))
  child.stderr.on('data', (b) => (log += b.toString()))
  const deadline = Date.now() + 90_000
  for (;;) {
    try {
      const res = await fetch(`${BASE}/login`, { signal: AbortSignal.timeout(2000) })
      if (res.ok) break
    } catch {
      /* not up yet */
    }
    if (Date.now() > deadline) {
      child.kill('SIGKILL')
      throw new Error(`dev server 未在 90s 内就绪（端口 ${PORT}）：\n${log}`)
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  return {
    child,
    portBusy: false,
    stop: () => {
      try {
        child.kill('SIGKILL')
      } catch {
        /* ignore */
      }
    },
  }
}

/* ------------------------------------------------------------- auth fixture */
const readJSON = (p) => JSON.parse(readFileSync(p, 'utf8'))
const TOOLS_AUTH_CACHE = (role) => join(FRONTEND, 'tools', `p10-auth-${role}.json`)

/** 夹具的会话在 cookies[]（HttpOnly），origins[].localStorage 只有 user/permissions/引导已读标记 */
function fixtureFor(role) {
  return storageToSession(readJSON(join(FRONTEND, 'e2e', 'visual', '.auth', `${role}.json`)))
}

/* ---------------------------------------------------------------- measure */
const MEASURE = `(() => {
  const de = document.documentElement;
  const vw = window.innerWidth;
  const main = document.querySelector('.main');
  const mainScroll = main ? main.scrollWidth : null;
  const mainClient = main ? main.clientWidth : null;
  const mainClip = main ? getComputedStyle(main).overflowX : null;
  const offenders = [];
  const scrollers = [];
  let widestRight = 0;
  const all = document.body ? document.body.querySelectorAll('*') : [];
  const label = (el) =>
    el.tagName.toLowerCase() + (typeof el.className === 'string' && el.className ? '.' + el.className.trim().split(/\\s+/).join('.') : '');
  // 溢出元素分两类，这是"能不能救"的分水岭：
  //   rescued  祖先链上先遇到 overflow-x:auto/scroll —— 用户能滚到，代价是容器内横向滚动条
  //   lost     先遇到 clip/hidden（典型就是 .main{overflow:clip}）—— 内容静默丢失
  const nearestClipAncestor = (el) => {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const ox = getComputedStyle(p).overflowX;
      if (ox === 'auto' || ox === 'scroll') return { kind: 'rescued', node: p };
      if (ox === 'clip' || ox === 'hidden') return { kind: 'lost', node: p };
    }
    return { kind: 'rescued', node: null };
  };
  const lost = [];
  const cosmetic = []; // 装饰性出血：被组件自身 overflow:hidden 裁掉的 ≤4px 像素级溢出，不算内容丢失
  const rescued = [];
  for (const el of all) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    if (r.right > widestRight) widestRight = r.right;
    const cs = getComputedStyle(el);
    if (cs.visibility === 'hidden') continue;
    const over = Math.round(r.right - vw);
    if (over > 1) {
      const cls = nearestClipAncestor(el)
      const item = {
        sel: label(el),
        pos: cs.position,
        over,
        w: Math.round(r.width),
        by: cls.node ? label(cls.node) + '[' + getComputedStyle(cls.node).overflowX + ']' : 'viewport',
      };
      if (cls.kind === 'lost' && over <= 4) cosmetic.push(item);
      else (cls.kind === 'lost' ? lost : rescued).push(item);
      offenders.push(item);
    }
    // 内部已可横向滚动的容器：这是"没溢出"背后的代价所在（表格自己滚，不换行）
    const inner = Math.round(el.scrollWidth - el.clientWidth);
    if (inner > 1 && (cs.overflowX === 'auto' || cs.overflowX === 'scroll' || cs.overflowX === 'clip' || cs.overflowX === 'hidden')) {
      scrollers.push({ sel: label(el), ox: cs.overflowX, inner });
    }
  }
  lost.sort((a, b) => b.over - a.over);
  cosmetic.sort((a, b) => b.over - a.over);
  rescued.sort((a, b) => b.over - a.over);
  offenders.sort((a, b) => b.over - a.over);
  scrollers.sort((a, b) => b.inner - a.inner);
  return {
    innerWidth: vw,
    docScrollWidth: de.scrollWidth,
    bodyScrollWidth: document.body ? document.body.scrollWidth : null,
    mainScroll,
    mainClient,
    mainClip,
    widestRight: Math.round(widestRight),
    appMarked: document.body ? document.body.dataset.app || '' : '',
    landingPath: location.pathname,
    offenders: offenders.slice(0, 8),
    lost: lost.slice(0, 6),
    cosmetic: cosmetic.slice(0, 6),
    rescued: rescued.slice(0, 6),
    scrollers: scrollers.slice(0, 6),
  };
})()`

const ROUTES = `(() => {
  const host = document.getElementById('app');
  const app = host && host.__vue_app__;
  const router = app && app.config && app.config.globalProperties && app.config.globalProperties.$router;
  if (!router) return null;
  return router.getRoutes().map((r) => ({ path: r.path, meta: r.meta || {}, name: r.name || null }));
})()`

async function settle(page) {
  await page.waitForLoadState('domcontentloaded')
  await page
    .waitForFunction(
      () => {
        const m = document.querySelector('.main-inner')
        const b = document.body
        if (!b || b.dataset.app !== 'ui') return false
        if (!m) return true
        return Boolean(m.firstElementChild)
      },
      null,
      { timeout: 15_000, polling: 200 },
    )
    .catch(() => {})
  await page.waitForTimeout(SETTLE_MS)
}

async function measureRoute(page, url, viewport) {
  let navError = null
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45_000 })
  } catch (e) {
    navError = String(e.message).split('\n')[0]
  }
  await settle(page)
  let m = null
  try {
    m = await page.evaluate(MEASURE)
  } catch (e) {
    navError = (navError ? navError + ' / ' : '') + 'measure: ' + String(e.message).split('\n')[0]
    return { viewport: viewport.key, verdict: 'NOENTRY', error: navError }
  }
  const docOver = m.docScrollWidth - m.innerWidth
  const mainOver = m.mainScroll != null ? m.mainScroll - m.mainClient : 0
  let verdict
  if (m.landingPath.replace(/\/$/, '') !== url.replace(/^https?:\/\/[^/]+/, '').replace(/\/$/, '')) {
    verdict = 'REDIRECT'
  } else if (docOver > 0) verdict = 'OVERFLOW'
  else if (mainOver > 0) verdict = 'CLIP'
  else if (m.lost.length > 0) verdict = 'LOST'
  else verdict = 'OK'
  return {
    viewport: viewport.key,
    verdict,
    innerWidth: m.innerWidth,
    docScrollWidth: m.docScrollWidth,
    docOver,
    mainScroll: m.mainScroll,
    mainClient: m.mainClient,
    mainOver,
    mainClip: m.mainClip,
    widestRight: m.widestRight,
    lostCount: m.lost.length,
    cosmeticCount: (m.cosmetic || []).length,
    landingPath: m.landingPath,
    offenders: m.offenders,
    lost: m.lost,
    cosmetic: m.cosmetic || [],
    rescued: m.rescued,
    scrollers: m.scrollers,
    error: navError,
  }
}

/* ------------------------------------------------------------------- main */
const server = await startDevServer()
const browser = await chromium.launch()
const report = {
  meta: {
    label: LABEL,
    base: BASE,
    port: PORT,
    narrowViewports: NARROW.map((v) => v.key),
    controlViewport: CONTROL.map((v) => v.key),
    settleMs: SETTLE_MS,
    startedAt: new Date().toISOString(),
    sessions: {},
  },
  routeUniverse: null,
  rows: [],
  unmeasured: [],
}

const SESSIONS = {}

function ctxInit(ctx, entries) {
  return ctx.addInitScript((kv) => {
    for (const e of kv) localStorage.setItem(e.name, e.value)
    localStorage.setItem('theme', 'light')
  }, entries)
}

/** 会话是否真被后端认账：直接问 /api/auth/permissions，401 即夹具已死。 */
async function probeSession(page) {
  return page.evaluate(() =>
    fetch('/api/auth/permissions', { credentials: 'include' })
      .then((r) => r.status)
      .catch(() => 0),
  )
}

/**
 * 会话来源三级兜底，全程最多一次真登录：
 *  ① e2e/visual/.auth/<role>.json（项目主要求复用的既有夹具）
 *  ② tools/p10-auth-<role>.json（本脚本上一轮缓存的等价会话）
 *  ③ 走一次真登录（内置演示账号 admin/teacher/student + 123456），把 storageState
 *     落在 tools/ 而不是 e2e/ —— ②的命中让 before/after 两轮合计只烧 1 次登录额度，
 *     后端 ratelimit.login 是 10 次/60 秒/IP，绝不可能撞。
 */
async function resolveSession(role) {
  const candidates = [
    { source: 'e2e-fixture', data: fixtureFor(role) },
    ...(existsSync(TOOLS_AUTH_CACHE(role))
      ? [{ source: 'tools-cache', data: storageToSession(readJSON(TOOLS_AUTH_CACHE(role))) }]
      : []),
  ]
  for (const c of candidates) {
    const ctx = await browser.newContext({ baseURL: BASE, viewport: { width: 375, height: 667 } })
    await ctxInit(ctx, c.data.entries)
    if (c.data.cookies.length) await ctx.addCookies(c.data.cookies)
    const page = await ctx.newPage()
    await page.goto(`${BASE}/404`, { waitUntil: 'domcontentloaded' })
    const status = await probeSession(page).catch(() => 0)
    await ctx.close()
    if (status === 200) {
      SESSIONS[role] = c.data
      report.meta.sessions[role] = { source: c.source, probe: status, logins: 0 }
      return c.data
    }
    report.meta.sessions[role] = report.meta.sessions[role] || { rejected: [] }
    if (report.meta.sessions[role].rejected) report.meta.sessions[role].rejected.push({ source: c.source, probe: status })
  }
  const ctx = await browser.newContext({ baseURL: BASE, viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  const user = page.locator('input[name="username"]').first()
  const pass = page.locator('input[name="password"]').first()
  await user.focus()
  await user.fill(role)
  await pass.focus()
  await pass.fill('123456')
  await page.locator('.av-check input[type=checkbox]').check().catch(() => {})
  const resp = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/auth/login', {
    timeout: 20_000,
  })
  await page.getByRole('button', { name: /登录|登 录|Login/i }).click()
  const r = await resp.catch(() => null)
  if (!r || r.status() !== 200) {
    await ctx.close()
    throw new Error(
      `${role} 会话重建失败：/api/auth/login 返回 ${r ? r.status() : '无响应'}` +
        (r && r.status() === 429 ? '（IP 限流，等 60 秒再跑，不要循环重试）' : ''),
    )
  }
  await page.waitForURL(new RegExp(`/${role}/`), { timeout: 20_000 })
  await page.waitForTimeout(1200)
  const state = await ctx.storageState({ path: TOOLS_AUTH_CACHE(role) })
  await ctx.close()
  const data = storageToSession(state)
  SESSIONS[role] = data
  const prev = report.meta.sessions[role]
  report.meta.sessions[role] = {
    ...(prev && prev.rejected ? { previouslyRejected: prev.rejected } : {}),
    source: 'relogin-once',
    probe: 200,
    logins: 1,
    note: 'e2e 夹具里的 admin/teacher/student 会话已被服务端作废（cookie 名义未过期但 /api/auth/permissions 401），' +
      '所以补一次真登录并把等价会话缓存在 tools/，不写回 e2e/。',
  }
  return data
}

function storageToSession(state) {
  const out = { cookies: (state.cookies || []).map((c) => ({ ...c })), entries: [] }
  const seen = new Set()
  for (const o of state.origins || []) {
    // 既有夹具是按 5175 存的，本脚本自己起在 5176；cookie 域是 localhost（与端口无关），
    // localStorage 却按 origin 隔离，所以这里把任意 localhost origin 的键值取出来，
    // 再用 addInitScript 重放到本 origin —— 不是重新登录，是同一份会话换个落点。
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(o.origin)) continue
    for (const kv of o.localStorage || []) {
      if (kv.name === 'theme') continue
      if (seen.has(kv.name)) continue
      seen.add(kv.name)
      out.entries.push({ name: kv.name, value: kv.value })
    }
  }
  return out
}

async function newRoleContext(role, viewport) {
  const ctx = await browser.newContext({ baseURL: BASE, viewport })
  if (role) {
    const s = SESSIONS[role]
    await ctxInit(ctx, s.entries)
    if (s.cookies.length) await ctx.addCookies(s.cookies)
  } else {
    await ctxInit(ctx, [])
  }
  return ctx
}

try {
  // 0) 三角色的会话都先落到 SESSIONS：能用既有夹具就用夹具，夹具死了才补一次真登录
  for (const role of ['student', 'teacher', 'admin']) await resolveSession(role)

  // 1) 取运行时路由全集（路由表与角色无关，用 student 会话即可）
  {
    const ctx = await newRoleContext('student', { width: 1440, height: 900 })
    const page = await ctx.newPage()
    await page.goto(BASE + ROLE_HOME_PATH.student, { waitUntil: 'domcontentloaded' })
    await settle(page)
    const routes = await page.evaluate(ROUTES)
    if (!routes) throw new Error('未能从 __vue_app__ 取到 router：路由真值清单为空')
    report.routeUniverse = routes
    await ctx.close()
  }

  // 2) 参数路由的真实落地值（真实数据驱动，不造数据）：
  //    :id 由该角色自己的「我的课程」课程卡（button.course-tile-cover → router.push）点进去后读真 URL；
  //    :tab 从课程空间页签条的真实 DOM 取（页签集合由 courseTabs(role) 决定，
  //         这里不复制清单，点一个读一个 URL，所以隐藏/未提供的页签天然不会混进来）。
  const courseIdByRole = {}
  const courseTabsByRole = {}
  for (const role of ['student', 'teacher', 'admin']) {
    const ctx = await newRoleContext(role, { width: 375, height: 667 })
    const page = await ctx.newPage()
    await page.goto(`${BASE}/${role}/courses`, { waitUntil: 'domcontentloaded' })
    await settle(page)
    // 课程卡是 button.course-tile-cover + router.push，不是 a[href]，
    // 所以真实 courseId 靠"点课程卡、读落地 URL"取，不猜也不造。
    await page
      .waitForFunction(() => document.querySelectorAll('.course-tile-cover').length > 0, null, {
        timeout: 10_000,
      })
      .catch(() => {})
    let id = null
    const tiles = await page.evaluate(() => document.querySelectorAll('.course-tile-cover').length)
    for (let i = 0; i < tiles && !id; i++) {
      const before = await page.evaluate(() => location.pathname)
      await page.evaluate((idx) => document.querySelectorAll('.course-tile-cover')[idx].click(), i)
      await page.waitForTimeout(700)
      const after = await page.evaluate(() => location.pathname)
      const hit = after.match(new RegExp('^/' + role + '/courses/(\\d+)'))
      if (hit) id = hit[1]
      if (after !== before) await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {})
    }
    courseIdByRole[role] = id
    const tabs = []
    if (id) {
      await page.goto(`${BASE}/${role}/courses/${id}`, { waitUntil: 'domcontentloaded' })
      await settle(page)
      await page
        .waitForFunction(
          () => document.querySelectorAll('.course-mobile-tabs button').length > 0,
          null,
          { timeout: 10_000 },
        )
        .catch(() => {})
      await page.evaluate(() => window.scrollTo(0, 0))
      const n = await page.evaluate(() => document.querySelectorAll('.course-mobile-tabs button').length)
      for (let i = 0; i < n; i++) {
        const hit = await page.evaluate((idx) => {
          const btn = document.querySelectorAll('.course-mobile-tabs button')[idx]
          if (!btn) return null
          btn.click()
          return { label: btn.textContent.trim() }
        }, i)
        if (!hit) continue
        await page.waitForTimeout(350)
        const path = await page.evaluate(() => location.pathname)
        if (new RegExp(`^/${role}/courses/\\d+/[a-zA-Z]+$`).test(path)) {
          tabs.push({ path, label: hit.label })
        }
      }
    }
    courseTabsByRole[role] = tabs
    await ctx.close()
  }
  report.meta.courseIdByRole = courseIdByRole
  report.meta.courseTabsByRole = Object.fromEntries(
    Object.entries(courseTabsByRole).map(([k, v]) => [k, v.map((t) => t.path)]),
  )

  // 3) 逐档 × 逐路由量测
  const groups = { guest: [], student: [], teacher: [], admin: [] }
  for (const r of report.routeUniverse) {
    const role = String(r.meta?.role || '').toLowerCase()
    if (ROLE_KEYS.includes(role)) groups[role].push(r)
    else groups.guest.push(r)
  }
  report.groups = Object.fromEntries(Object.entries(groups).map(([k, v]) => [k, v.length]))
  // 自检：分组塌陷（全部落 guest）会让"未登录被守卫弹回登录页"伪装成全线 REDIRECT，
  // 读起来像结果、其实是脚本 bug。此处直接拒绝出报告。
  if (groups.guest.length > 12 || groups.student.length === 0) {
    throw new Error(`路由分组自检失败：${JSON.stringify(report.groups)}（guest 组应只含 10 条公共路由）`)
  }

  const VIEWPORTS = [...NARROW, ...CONTROL]

  for (const groupRole of ['guest', 'student', 'teacher', 'admin']) {
    if (!ONLY_GROUPS.includes(groupRole)) continue
    const list = groups[groupRole]
    if (!list.length) continue
    const ctx = await newRoleContext(groupRole === 'guest' ? null : groupRole, {
      width: 1440,
      height: 900,
    })
    const page = await ctx.newPage()

    // 先把该组的"可测落点"展开成具体 URL（参数路由用真实 courseId；
    // 课程空间的 :tab? 按页签条实测集合逐条展开）
    const targets = []
    for (const r of list) {
      if (r.path.includes(':pathMatch')) {
        targets.push({ r, concrete: '/p10-not-a-real-route' })
        continue
      }
      if (r.path.includes(':id')) {
        const id = courseIdByRole[groupRole]
        if (!id) {
          report.unmeasured.push({
            route: r.path,
            group: groupRole,
            reason: `课程空间路由需要真实 courseId，而夹具账号在 /${groupRole}/courses 未点到任何课程卡（无数据或需写操作选课）—— 未量测`,
          })
          continue
        }
        targets.push({ r, concrete: r.path.replace(':id', id).replace('/:tab?', '') })
        for (const t of courseTabsByRole[groupRole] || []) {
          targets.push({ r, concrete: t.path, tabLabel: t.label })
        }
        continue
      }
      targets.push({ r, concrete: r.path })
    }

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      for (const t of targets) {
        const abs = t.concrete.startsWith('/') ? t.concrete : '/' + t.concrete
        const row = await measureRoute(page, BASE + abs, vp)
        report.rows.push({
          group: groupRole,
          route: t.r.path,
          concretePath: abs,
          tabLabel: t.tabLabel || '',
          title: t.r.meta?.title || '',
          pageKey: t.r.meta?.pageKey || (t.r.meta?.courseSpace ? '(course)' : ''),
          control: !NARROW.some((v) => v.key === vp.key),
          ...row,
        })
        process.stdout.write(
          `[${vp.key}] ${groupRole.padEnd(7)} ${abs.padEnd(40)} ${row.verdict.padEnd(9)} doc=${row.docOver ?? '-'} main=${row.mainOver ?? '-'}\n`,
        )
      }
    }
    await ctx.close()
  }

  report.finishedAt = new Date().toISOString()
  writeFileSync(OUT, JSON.stringify(report, null, 2), 'utf8')
  const tally = {}
  for (const row of report.rows) {
    if (row.control) continue
    tally[row.verdict] = (tally[row.verdict] || 0) + 1
  }
  console.log('\n=== 三档窄屏判定汇总 ===', JSON.stringify(tally))
  console.log('行数：', report.rows.length, '未量测：', report.unmeasured.length)
  console.log('写出：', OUT)
} finally {
  await browser.close()
  server.stop()
}
