import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
// ts 扩展名导入：与既有 spec 的相对导入风格一致（e2e/*.spec.ts 均 import from './test-helpers'）
import { loginAs } from '../test-helpers'
// storagePathFor 由 Task 1 定义在 visual-helpers.ts，此处只导入不复定义——
// 两份定义会让 spec 与 setup 写到不同路径，夹具"存在但读不到"。
import { storagePathFor } from './visual-helpers'

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN'] as const

/**
 * 计划原文这一行是 `join(__dirname, '.auth')`，本机跑直接
 * `ReferenceError: __dirname is not defined in ES module scope`：frontend/package.json
 * 是 `"type": "module"`，而 Playwright 的 requireOrImport 对 ESM 包走动态 import()
 * （playwright/lib/common/index.js 的 fileIsModule 分支），global-setup.ts 因此按 ESM 求值。
 * 也不改成 import.meta.url 派生的绝对路径——spec 侧读的是**相对 cwd** 的路径
 * （Playwright 的 storageState 按 cwd 解析），绝对/相对两套口径一旦 cwd 不是 frontend/
 * 就分叉，症状正是最难查的"夹具存在但读不到"。这里三个角色同目录，目录从同一个
 * storagePathFor 反推，写与读只剩一个表达式。
 */
const AUTH_DIR = dirname(join(process.cwd(), storagePathFor(ROLES[0])))

/**
 * 登录三次而非 24 次。两个理由都是实测出来的：
 * ①loginAs 每次都要点登录、等 /api/auth/login 响应、再关首次引导层；
 * ②后端 ratelimit.login 默认 10 次/60 秒且 429 不打日志（test-helpers.ts:169-171），
 *   多登录 = 高概率撞限流 = 失败集合随跑测快慢漂移，读到的红灯不是产品缺陷。
 * 引导浮层是按账号写的（键含 :<userId>），所以这里必须真实点掉，
 * 落进 storageState 的是"已看过"状态，后续导航不再弹出。
 *
 * 两个最容易写反的点：
 * ①`ctx.storageState({ path })` 是把**当前上下文**的会话**写到**该路径，不是从该路径读。
 *   用反了会得到一份"文件在、内容是空的"夹具而不报错——smoke.spec.ts 的 admin 断言就是为它设的。
 * ②本项目的凭据走后端下发的 HttpOnly Cookie（stores/auth.ts:34 明写 token 不落
 *   localStorage，请求侧靠 withCredentials 自动携带），所以夹具里的会话在 `cookies[]`，
 *   `origins[].localStorage` 只有 user/permissions/引导已读标记。判"空夹具"要两处都看。
 */
export default async function globalSetup() {
  // 显式建目录：Playwright 写 storageState 时自己也会 mkdirIfNeeded，这里留一次是把
  // "夹具目录归 setup 管"这个意图写进代码，不承担正确性。
  mkdirSync(AUTH_DIR, { recursive: true })
  const browser = await chromium.launch()
  try {
    for (const role of ROLES) {
      const ctx = await browser.newContext({ baseURL: process.env.PW_BASE_URL || 'http://localhost:5175' })
      const page = await ctx.newPage()
      await loginAs(page, role)
      // 落盘路径必须与 storagePathFor 完全一致（相对 cwd），否则 spec 侧 ENOENT
      await ctx.storageState({ path: join(process.cwd(), storagePathFor(role)) })
      await ctx.close()
    }
  } finally {
    await browser.close()
  }
}
