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
/**
 * `--update-snapshots` 的机械闸门（Task 4c Step 1c）。
 *
 * 【它挡的是什么】这一条命令会**重写全部 22 张基线**，而在本轮之前它毫无机械阻力：
 * 计划里两处（Task 4 建立基线步、LEDGER 表头那句"改基线用"）给的都是**不带文件过滤**的
 * `npx playwright test -c playwright.visual.ts --update-snapshots`。
 * 叠上原来探针默认档 `gross`（任何不带过滤的 visual 跑天然带 3 条设计好的红），
 * 后来的代理会被训练成"红了也别管"，而真正危险的那次红——把缺陷吸收进重新生成的基线——
 * 恰恰混在这片红里看不出来。日期漂移那 618 px 字节差就是靠这个口子能被静默洗掉的。
 *
 * 【口径】必须显式带 `PW_ALLOW_UPDATE_SNAPSHOTS=1` 才放行；同时打印一条提醒：
 * 先跑 `anchors`（不带 --update-snapshots）看清红的是哪几张、为什么红，再决定要不要重生成。
 * 本闸拦 `--update-snapshots`／`-u`／`--update` 三种写法（判据是 argv 里出现这三个之一即拒），
 * 不拦 `--llm`／其它。**这一句在 4c-fix-C 的 C2 步之前写的是"只拦两个写法"**——那是 F3／第 12 项
 * 把第三种 `--update` 加进行为级断言之后的滞后措辞；条数现在由 `helpers.spec.ts`
 * 「闸门注释自陈的“拦几种写法”必须与实际判据同条数」那条用例钉住（注释与 `argv.includes(...)`
 * 两侧都不许漂），所以改判据要同批改这句，别再让它滞后一次。
 * 为什么放在 global-setup 而不是各 spec：这里是"整个跑测即将开始"的唯一入口，
 * 拦在导入期连一张图都不会被写。
 *
 * 【4c-fix-C 第 12 项补的自动化守卫为什么长这样】这道门原先**只有手工反证**——删掉下面
 * `globalSetup` 里那句调用，全部套件照样绿。现在由 `helpers.spec.ts`
 * 「--update-snapshots 闸门」那条用例钉两半：调用点（本文件默认导出的第一条语句、且不带实参）
 * ＋ 这道门的牙（三种写法都拦、显式承认才放行）。
 * 为此本函数导出，并把 argv 做成**缺省读 `process.argv` 的可选参数**——
 * 生产路径的取值表达式一字未变（默认实参就是它），而用例可以喂合成 argv，
 * 不必去改 `process.argv`、更不必真的把 `--update-snapshots` 交给 runner
 * （围栏明令永不跑该旗标：门一旦真的失效，那一跑就把 22 张已提交基线重写掉，
 *   这正是计划提的 `spawnSync` 形态验不下去的原因，取舍理由写在用例注释里）。
 */
export function assertUpdateSnapshotsAllowed(argv: string[] = process.argv): void {
  const wantsUpdate =
    argv.includes('--update-snapshots') || argv.includes('-u') || argv.includes('--update')
  if (!wantsUpdate) return
  if (process.env.PW_ALLOW_UPDATE_SNAPSHOTS === '1') {
    console.log(
      '[gate] --update-snapshots 已被 PW_ALLOW_UPDATE_SNAPSHOTS=1 显式承认放行。\n' +
        '       它会重写**匹配到的**每一条基线：请务必带文件/用例过滤（-g），' +
        '并在跑完后逐张复核新图；把缺陷吸收进基线是本仓不可挽回的一类错误。',
    )
    return
  }
  throw new Error(
    '[gate] 拒绝了 --update-snapshots：它会重写全部 22 张已提交基线，而"红了就重生成"正是本套件最禁止的动作。\n' +
      '  先看红的是什么：PW_PORT=5175 npx playwright test -c playwright.visual.ts anchors -g "<slug>"\n' +
      '  读数与出处：node e2e/visual/tools/measure-diff.cjs <基线.png> <取证图.png> --bands\n' +
      '  确认这次重生成合理之后，显式承认：PW_ALLOW_UPDATE_SNAPSHOTS=1 <同一条命令>\n' +
      '  （理由见计划 Task 4c Step 1c 与 spec §6.12/§6.16。）',
  )
}

export default async function globalSetup() {
  assertUpdateSnapshotsAllowed()
  // 显式建目录：Playwright 写 storageState 时自己也会 mkdirIfNeeded，这里留一次是把
  // "夹具目录归 setup 管"这个意图写进代码，不承担正确性。
  mkdirSync(AUTH_DIR, { recursive: true })
  const browser = await chromium.launch()
  try {
    for (const role of ROLES) {
      const ctx = await browser.newContext({
        baseURL: process.env.PW_BASE_URL || 'http://localhost:5175',
      })
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
