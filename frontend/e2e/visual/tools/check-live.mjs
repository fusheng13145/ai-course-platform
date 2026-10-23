import { request } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Task 0 的**归因判据**（只读；修复轮 1 的 I-3② 把它从 gitignore 目录挪进入库面）。
 * 跑法（工作目录 `frontend/`，需后端 8080 与 dev server 5175 在听）：
 *   `PW_PORT=5175 node e2e/visual/tools/check-live.mjs`
 *
 * 【它要分开的是两种长得一样的结论】
 *  A. 钉桩生效 ⇒ 画面冻在 fixture 那个值，流程套件再怎么写后端都不进画面（出路① 要的效果）；
 *  B. 只是这一轮流程套件恰好没动那个值 ⇒ 钉桩有没有起作用根本没被测到（**零信息读数**，
 *     修复轮 1 的 M-1 就是按这条把 admin 格那次"同值"改判的）。
 * 只看像素数分不开这两条（两种情况下像素数都一样），所以直接比**载荷本身**。
 * 【会话复用】storageState 走 `e2e/visual/.auth/*.json`，**绝不重登**（DR-85 登录限流 10 次/60 秒）；
 *  本脚本从不打印夹具内容或任何 cookie／令牌，只打印 fixture 里那个业务数值与真后端返回值。
 * 【与 I-1 的分工】`payload-pin.ts` 的 `assertBackendCanary` 在每次 anchors 跑里断"真后端仍可用"
 *  （存在性），本脚本断"真后端的**值**与 fixture 不同 ⇒ 钉桩正在挡"（对照性）。两者不互相替代。
 */
const BASE = `http://localhost:${process.env.PW_PORT || '5175'}`
const CELLS = [
  {
    slug: 'student-dashboard',
    role: 'student',
    keys: ['/api/notifications/unread-count', '/api/student/dashboard'],
  },
  {
    slug: 'admin-governance-analytics',
    role: 'admin',
    keys: ['/api/notifications/unread-count', '/api/analytics/dashboard'],
  },
]

function fixtureData(slug, key) {
  const m = JSON.parse(readFileSync(join('e2e', 'visual', '__fixtures__', `${slug}.json`), 'utf8'))
  const hit = m.requests[key]
  if (!hit) return { present: false }
  const body = JSON.parse(hit.body)
  return { present: true, data: body.data }
}

function summarize(v) {
  if (v === null || v === undefined) return String(v)
  if (typeof v === 'number' || typeof v === 'string') return String(v)
  if (typeof v === 'object') {
    const o = v
    // 只取"未读数"这一个业务数值，别的字段一概不打（不打整份载荷，避免把用户/课程数据抄进日志）
    if ('unreadCount' in o) return `unreadCount=${o.unreadCount}`
    return `object(keys=${Object.keys(o).length})`
  }
  return typeof v
}

for (const cell of CELLS) {
  const statePath = join('e2e', 'visual', '.auth', `${cell.role}.json`)
  const api = await request.newContext({ baseURL: BASE, storageState: statePath })
  for (const key of cell.keys) {
    const res = await api.get(key)
    const live = res.ok() ? JSON.parse(await res.text()) : null
    const fx = fixtureData(cell.slug, key)
    const liveSum = live && live.data !== undefined ? summarize(live.data) : `HTTP ${res.status()}`
    const fxSum = fx.present ? summarize(fx.data) : '(fixture 无此键)'
    console.log(
      `[live-vs-fixture] ${cell.slug} ${key} ⇒ 真后端 ${liveSum} · fixture ${fxSum} · ` +
        `${liveSum === fxSum ? '同值（这一轮没测出差别 ⇒ 零信息读数，见脚本头那段 B）' : '**不同值 ⇒ 钉桩确实在挡后端**'}`,
    )
  }
  await api.dispose()
}
