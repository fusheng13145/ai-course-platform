import type { Page } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { BASELINE_DAY, localDay, type AnchorSpec } from './visual-helpers'

/* ============================================================================ *
 * 载荷钉死（payload pin）——Task 4c Step 1 的"首选路线"，不是遮罩
 * ============================================================================ *
 * 【为什么不用遮罩】评审给的算术决定了这一点，不能靠直觉：
 *   22 张基线实测全部 1440×900 = 1,296,000 px（这件事**从今天起由断言管**：
 *   `helpers.spec.ts` 的"每张已提交基线都是 1440×900，且免检额算式与注释里的引用一致"逐张读 IHDR，
 *   并把 `1,296,000 × SNAPSHOT_THRESHOLDS.maxDiffPixelRatio` 与注释里引用的免检额对齐——
 *   在这之前它只是"真"，没有任何东西钉着它）。
 *   Task 4b 当时交付档 **r=0.0005** ⇒ 免检额 648 px，
 *   而 student-dashboard **一天的**日期漂移就是**计数 321 / 字节差 618（t=0 / r=0.0005 口径）**
 *   （对 648 的余量 5%，不是原先以为的 2 倍）。
 *   Task 4c 复测：两个主题各 **计数 321 / 字节差 618**（student-dashboard）与
 *   **计数 112 / 字节差 222**（admin-governance-analytics），**同样是 t=0 / r=0.0005 那一轮的数**
 *   （计数只由 threshold 决定，所以两档交付配置下数值不变；变的是"这一格判绿还是判红"），
 *   差异带 y 537..544 / y 696..703，与 4b 的读数同带同量级。重生成（**必须带 NOPIN**——
 *   装了钉死这一轮的漂移就是 0，那不是"漂移没发生"而是"漂移被抵住"，两件事别混）：
 *   `cd frontend && PW_LADDER=noise PW_PROBE_THEME=light PW_VISUAL_NOPIN=1 \
 *      PW_PROBE_SLUGS=student-dashboard,admin-governance-analytics PW_PORT=5175 \
 *      npx playwright test -c playwright.visual.ts sensitivity`
 *   （把 NOPIN 换成 `PW_PIN_SHIFT_DAYS=1|2|3` 即是"服务端第 +k 天"的模拟，用于跨天外推；
 *    k=1 与真实 +1 天**不等价**（**计数 334 / 字节差 630（student）**、**计数 198 / 字节差 338（analytics）**，
 *    **t=0 口径**：探针那三轮按比较器 t=0/r=0 读，RED-5 那条 `anchors` 跑则是在**收紧后的 r=0.0001** 下由
 *    跑测器自报同样的 334/198 ⇒ 计数与 ratio 无关，两处的数本来就是同一批），
 *    所以模拟只用来证"漂移始终不归零"，不用来证幅度。
 *    ⚠️ **别把这里的 198 与阶梯表里 `radius` 在 1 卡页的 198 混成同一个数**——同值不同物
 *    （一个是模拟 k=1 的 analytics 漂移计数，一个是 16→14px 圆角的检出计数）；
 *    同理 618 在这里是 student 漂移的**字节差**，而 `docs/visual-upgrade/evidence/phase0-measurements.md`
 *    §6 逐张表 #6 里的 618 是**采样色数**。引用这类数必须同时给**档名 + 锚点 + t/r**。）
 *   遮掉标签带 = 那一带**永久**退出比较，
 *   而 Phase 1/2 的改动（卡片阴影、圆角）恰好长在图表面板那一带里——盲区与改动区重叠，
 *   遮罩等于把门禁的受益人换成空气。
 * 【更要紧的】同一个 `LocalDate.now()` 窗口**还会重新取数**（StudentVisualizationComponent
 *   的 buildDurationTrend 用 today.minusDays(13)、buildHeatmapData 用 today.minusDays(6) 去查库），
 *   所以换一天动的可能不只是轴标签文字，而是整条 series 的几何。遮标签带挡不住后者。
 * 【钉载荷做的是另一件事】把"服务端按今天算"换成"读一份入库的固定响应"，日期与数值**一起**冻住，
 *   被比对的区域一个像素都不遮。代价与边界：
 *   ① 只钉 AnchorSpec.pinPaths 声明的那几个端点。**Task 0（2026-10-21）之后这两个日期锚点各 5／7 个**
 *      （现值逐字读 `visual-helpers.ts` 的 ANCHORS，本文件不复制清单），且那是"本页实测发出的每一条 GET"
 *      ⇒ 原先那句"其余请求照旧打真后端"从此**只对非钉桩锚点、以及交互之后才发的请求成立**，
 *      不再对这两格的首屏成立。它原先靠"少钉"兜着的那件事（"数据确已到位"里凡来自未钉端点的那一半
 *      仍是真后端的证据）改由 I-1 的 canary 断言 `assertBackendCanary` 显式接手——
 *      用一个**不入 pinPaths** 的探针端点、在装钉桩**之前**证"真后端＋真会话仍可用"；
 *      "钉得全不全"也不再只靠注释，由 I-2 的 `assertAllApiGetsPinned` 当场判红；
 *   ② fixture 是从**真后端录下来的**，录的那一次必须先过 arrival 断言（录空态 = 基线空态，
 *      所以 record 模式在写出 fixture 前就跑一次 DATA_ARRIVAL_CHECKS）；
 *   ③ 钉死之后这几个端点**不再被像素门禁覆盖**；Task 0 把这两格的盲区口径从"那一个端点"扩到**整页首屏数据**：
 *      产品若改图表数据结构，本套件不会红，要红得先重录（重录会让图与已提交基线不符 ⇒ 那时才是真红）。
 *      这条盲区要写进 LEDGER。
 * 【回看一天的偏移怎么补】录制时把载荷里所有 ISO 日期整体回拨到冻结时钟那一天
 *   （CLOCK_EPOCH = 2026-10-18，见 freezeClock），于是 fixture 复现的是**基线当天**的画面。
 *   服务端今天的值与基线当天是否相同，不由注释说：由"钉了之后 4 张日期锚点与已提交基线
 *   逐字节相同（md5 相等）"这条硬断言说——它不成立就必须显式重生成基线，而不是悄悄放宽阈值。
 */

/** fixture 落点（入库；不是 .auth/ 那种含令牌的会话夹具） */
export const FIXTURE_DIR = 'e2e/visual/__fixtures__'

export interface PinnedResponse {
  status: number
  contentType: string
  body: string
}

export interface PinManifest {
  slug: string
  /** 录制时刻（Node 侧真实时间） */
  recordedAt: string
  /** 录制时服务端的本地日（= 后端 LocalDate.now() 的日） */
  serverDateAtRecord: string
  /**
   * 这份载荷被归一到哪一天 = **基线日**（`BASELINE_DAY`）。
   * 回放时由 `loadPinManifest()` 核两件事：它必须等于 BASELINE_DAY，
   * 且 `backshiftDays` 必须真等于 `serverDateAtRecord − clockDate`（A4 的 ②③）。
   */
  clockDate: string
  /** serverDateAtRecord − clockDate 的天数；录制时用它把日期整体回拨到基线当天 */
  backshiftDays: number
  /** key = pathname + search */
  requests: Record<string, PinnedResponse>
}

export interface PinState {
  /** 实际被钉掉的请求 key（含重复） */
  served: string[]
  /** 审计信息：/api 下**未**声明钉死、但响应体里带服务端"今天"字符串的端点 */
  unpinnedTodayBearing: string[]
  /**
   * 这一页**发出去的每一条** `/api/` GET 键（pathname+search，去重、按出现序），由 I-2 的
   * `page.on('request')` 收集；`assertAllApiGetsPinned` 拿它判"全集 ⊆ pinPaths"。
   * 口径边界（读结论时必须带着）：只覆盖到**调用断言那一刻为止**发出的请求，
   * 截图之后才发的交互请求不在账上——那条探针另由 I-1 的 canary 与流程套件负责。
   */
  observedApiGets: string[]
  /** 本轮回放的日期偏移（天）；>0 = 取证用的"服务端第 +k 天"模拟，非交付口径 */
  shiftDays: number
}

export function fixturePathFor(slug: string): string {
  return join(process.cwd(), FIXTURE_DIR, `${slug}.json`)
}

function isoDay(d: Date): string {
  return d.toISOString().slice(0, 10)
}

const ISO_DATE_RE = /20\d\d-(?:0[1-9]|1[0-2])-(?:[0-2]\d|3[01])/g

/**
 * 把载荷里所有 ISO 日期（含 `2026-10-19T08:00:00` 这种日期时间的前半段）整体回拨 `days` 天。
 * 只在**录制**时跑一次，产出的 fixture 从此不再变——服务时逐字回放到盘上那份。
 * 用文本替换而不是解析重序列化，是为了不让 JSON 的键序/数字格式改动混进载荷。
 */
export function shiftIsoDates(body: string, days: number): string {
  if (days === 0) return body
  return body.replace(ISO_DATE_RE, (m) => {
    const d = new Date(`${m}T00:00:00Z`)
    d.setUTCDate(d.getUTCDate() - days)
    return isoDay(d)
  })
}

/**
 * A4 的 ②③，抽成纯函数好让 `helpers.spec.ts` 能直接把它弄红（`loadPinManifest` 要先读盘才走得到这里，
 * 拿真实文件做反向测试会污染入库的 `__fixtures__`）。
 * 只查 manifest **自己声明**的那三个日期字段，不碰 requests。
 */
export function assertManifestDates(
  m: Pick<PinManifest, 'serverDateAtRecord' | 'clockDate' | 'backshiftDays'>,
  where: string,
): void {
  // ② 这份载荷归一到哪一天，是 manifest **自己写的**，不是由 CLOCK_EPOCH 反推的。
  //    不等 = 这份 fixture 复现的是另一天的画面，钉住它去比基线永远不会 md5 相等。
  if (m.clockDate !== BASELINE_DAY) {
    throw new Error(
      `[载荷钉死] ${where} 的 clockDate=${JSON.stringify(m.clockDate)} 与 BASELINE_DAY=${BASELINE_DAY} 不符。\n` +
        `  含义：这份 fixture 把日期归一到了**另一天**，而 22 张基线拍于 ${BASELINE_DAY} ⇒ 日期锚点会永久红。\n` +
        `  处置只有两种，且都必须显式：① 重录（PW_RECORD_PAYLOADS=1 那条，落盘端按 BASELINE_DAY 回拨）；\n` +
        `  ② 有意换基线日 ⇒ 同时改 BASELINE_DAY 与 CLOCK_EPOCH，并**重拍全部 22 张基线**\n` +
        `     （走 global-setup 那道 --update-snapshots 显式承认闸门，同步 helpers.spec.ts 的 sha256 清单）。`,
    )
  }
  // ③ manifest 三个日期字段必须自洽：backshiftDays 真等于 serverDateAtRecord − clockDate。
  //    手改过 fixture（比如只挪 clockDate 想让 md5 对上）在这里就红，而不是变成一张"看不出来"的图。
  const implied = Math.round(
    (Date.parse(`${m.serverDateAtRecord}T00:00:00Z`) - Date.parse(`${m.clockDate}T00:00:00Z`)) /
      86400000,
  )
  if (!Number.isInteger(m.backshiftDays) || m.backshiftDays !== implied) {
    throw new Error(
      `[载荷钉死] ${where} 的 backshiftDays=${m.backshiftDays} 与其自身日期不符（` +
        `serverDateAtRecord=${m.serverDateAtRecord} − clockDate=${m.clockDate} ⇒ 应为 ${implied}）` +
        `——这份 fixture 被手改过或落盘时算错，重录它。`,
    )
  }
}

/**
 * 读一份 fixture，并把**这份 fixture 自己声明的基线日**核对掉（A4 的 ②③）。
 * 原先这里只查形状（slug 对、requests 非空），日期字段一个字都不查
 * ⇒ "钉住后与基线 md5 相等"这条判据的前提（基线做于 `CLOCK_EPOCH` 那天）从来没人验过，
 * 前提破了表现为"永久红而且重录也救不回来"，归因方向会被带去查产品改动。
 */
export function loadPinManifest(slug: string): PinManifest {
  const file = fixturePathFor(slug)
  let raw: PinManifest
  try {
    raw = JSON.parse(readFileSync(file, 'utf8')) as PinManifest
  } catch (e) {
    throw new Error(
      `[载荷钉死] 读不到 ${file}：${(e as Error).message}\n` +
        `重录命令：cd frontend && PW_PORT=5175 PW_RECORD_PAYLOADS=1 npx playwright test -c playwright.visual.ts anchors -g "${slug}"\n` +
        `  （录制模式**先落 fixture 再**决定像素断言——Task 4c-fix-A A4 修的正是这条次序；\n` +
        `   那一轮会打 [pin-record] 与 [record] 两行，然后**必须**再跑一次不带 PW_RECORD_PAYLOADS 的同一条命令，\n` +
        `   由 assertZeroDiff 判"新 fixture 能不能复现基线"。别拿录制那一轮的红/绿当结论。）`,
    )
  }
  if (!raw || raw.slug !== slug || !raw.requests || Object.keys(raw.requests).length === 0) {
    throw new Error(
      `[载荷钉死] ${file} 形状不对（slug 不符或 requests 为空）——宁可红，不拿空 fixture 当钉住`,
    )
  }
  assertManifestDates(raw, file)
  return raw
}

/**
 * **模拟服务端日期的偏移**（Task 4c Step 1 的"≥3 天"那一半的取证开关）。
 *
 * 【它为什么必须存在】brief 要求"必须证漂移在 **≥3 天**跨度上仍 ≤ X，只量今天这一天的差不算闭"。
 * 而一次会话里真实日历只能推进一天，改系统日期会把后端 JWT 的过期判定 / Redis TTL / 登录限流窗口
 * 一起弄坏（且控制者明令不得自行重启后端）⇒ 这条路按字面**不可执行**，如实报为发现（见报告 §5）。
 * 【它证的是什么】fixture 里的日期已被 `backshiftDays` 回拨到**基线那天**，所以
 * `PW_PIN_SHIFT_DAYS=k` 回放的就是"服务端在基线日 +k 天会返回的那份载荷"：
 *  - k=0（默认）= 交付口径，钉住后与基线的差必须为 0；
 *  - k=1 应当**复现今天真实的 +1 天漂移**（今天的服务器日就是基线次日）——这是校准，
 *    拿它和 `PW_VISUAL_NOPIN=1`（真走后端、真漂移）在同一次里的读数对账；
 *    两者一致才说明"只平移日期"这个模拟是忠实的（本批两个 fixture 的序列值全 0，
 *    所以"同窗口重新取数"不会改变几何；这条限制在 k=1 的校准里被实测检验，不是靠说）；
 *  - k=2、k=3 外推出"换到第 3 天漂移仍在、且不会自己回到 0"，而 k=0 恒为 0。
 * 【口径】设了它这一轮就**不是交付读数**：证据里记 `pinShiftDays`，日志明写"模拟"。
 */
export function pinShiftDays(): number {
  const raw = process.env.PW_PIN_SHIFT_DAYS
  if (raw === undefined || raw === '') return 0
  const k = Number(raw)
  if (!Number.isInteger(k) || k < 0 || k > 30) {
    throw new Error(`[载荷钉死] PW_PIN_SHIFT_DAYS=${raw} 需为 0..30 的整数（k=天偏移，0=交付口径）`)
  }
  return k
}

/**
 * 装上钉死：**只**拦 AnchorSpec.pinPaths 声明的 GET；其余请求 fallback 走真后端。
 * 返回的 state 交给两条方向相反的断言用：
 *  - `assertPinCoverage` 验"声明的端点真的都被请求过"（钉了却没人调用 = fixture 过期或路径写错，
 *    那种"绿"和没钉一样，必须红）；
 *  - `assertAllApiGetsPinned`（修复轮 1 的 I-2）验"这一页发出去的每条 GET 都被声明了"（全集 ⊆ pinPaths）。
 * 本函数还顺手用 `page.on('request')` 把这一页发过的全部 `/api/` GET 键收进 `state.observedApiGets`——
 * 收集与判定分开：这里只测量，判红在那条断言里（它必须是纯函数，才能被 helpers.spec.ts 喂合成样本弄红）。
 */
export async function installPayloadPin(page: Page, anchor: AnchorSpec): Promise<PinState> {
  const paths = anchor.pinPaths ?? []
  const manifest = loadPinManifest(anchor.slug)
  const shift = pinShiftDays()
  const state: PinState = {
    served: [],
    unpinnedTodayBearing: [],
    observedApiGets: [],
    shiftDays: shift,
  }
  const serverToday = localDay(new Date())
  const observed = new Set<string>()

  await page.route(
    (url) => paths.includes(`${url.pathname}${url.search}`),
    async (route) => {
      const req = route.request()
      if (req.method() !== 'GET') return route.fallback()
      const key = `${new URL(req.url()).pathname}${new URL(req.url()).search}`
      const hit = manifest.requests[key]
      if (!hit) return route.fallback()
      state.served.push(key)
      await route.fulfill({
        status: hit.status,
        contentType: hit.contentType,
        body: shift === 0 ? hit.body : shiftIsoDates(hit.body, shift),
      })
    },
  )

  // I-2：全集侧的账本。`request` 事件对**被 fulfill 的请求同样触发**，所以这份清单是"这一页发过的全部 GET"，
  // 与 `served`（被钉桩接管的那一份）刚好是判"钉得全不全"需要的两个面。
  // canary 探针键（`CANARY_PROBE_PATH`）**显式不收**：它按设计就不入 pinPaths，收进来会让 I-1 与 I-2
  // 互相把对方判红。这里靠键名逐字豁免，同时 `page.request` 走的探针本来也不进 `page.on('request')`
  // （Playwright 的 page.route/请求事件都不作用于 APIRequestContext）⇒ 双保险，不是二选一。
  page.on('request', (req) => {
    if (req.method() !== 'GET') return
    const u = new URL(req.url())
    if (!u.pathname.startsWith('/api/')) return
    const key = `${u.pathname}${u.search}`
    if (key === CANARY_PROBE_PATH) return
    if (observed.has(key)) return
    observed.add(key)
    state.observedApiGets.push(key)
  })

  // 审计（不是断言）：页面还在消费哪些"带今天日期"的 /api 响应。
  // 它们多数不落在画上（未读数、时间戳字段），所以只报数、不判红；判红由零 diff 那条负责。
  page.on('response', (res) => {
    const u = new URL(res.url())
    if (!u.pathname.startsWith('/api/') || paths.includes(`${u.pathname}${u.search}`)) return
    void res
      .text()
      .then((t) => {
        if (t.includes(serverToday)) state.unpinnedTodayBearing.push(`${u.pathname}${u.search}`)
      })
      .catch(() => undefined)
  })
  return state
}

export function assertPinCoverage(anchor: AnchorSpec, state: PinState): void {
  const declared = anchor.pinPaths ?? []
  const missing = declared.filter((p) => !state.served.includes(p))
  if (missing.length > 0) {
    throw new Error(
      `[载荷钉死] ${anchor.slug} 声明要钉 ${JSON.stringify(declared)}，但页面没请求它们：${JSON.stringify(missing)}\n` +
        `钉了却没用上 = fixture 与产品已脱节，这次的"零 diff"不是钉出来的。要么改 pinPaths，要么重录 fixture。`,
    )
  }
}

/**
 * I-2 的**判据本体**（纯函数 ⇒ 它"会不会红"能像 F2a／F5 那样入库喂合成样本，不必等产品真加一条 GET）。
 * 返回**漏钉的键清单**，空＝这一页发过的每条 `/api/` GET 都在 `pinPaths` 里。
 * 逐字比较（`pathname+search`），与 `installPayloadPin` 的 route 匹配、`attachPayloadRecorder` 的落盘键
 * 同一把尺——少了 search 就是"钉了却没命中"，本条也不许把它算成已钉。
 */
export function findUnpinnedApiGets(
  observed: readonly string[],
  declared: readonly string[],
): string[] {
  return observed.filter((k) => !declared.includes(k))
}

/**
 * I-2：把"pinPaths 是否覆盖该页 GET 全集"从一条 `console.log` 升成断言。
 * 【为什么必须有这条】上一轮的差集是**一次性脚本量出来的**，产品在这一页加一条 GET、
 * 或把 `?size=100` 改成别的 query，跨套件耦合立刻回来而**没有一条入库断言会红**
 * （`assertPinCoverage` 只判"钉了的有没有被消费"这一个方向，方向相反、拦不住它）。
 * 【只对声明了 pinPaths 的锚点判】`login` 那类匿名页按设计不钉任何东西，
 * 给它加"全集必须为空"会变成一条谁都得绕的约束——它的路径由简报第 2 步"不为凑数硬钉"另案处理。
 */
export function assertAllApiGetsPinned(anchor: AnchorSpec, state: PinState): void {
  const declared = anchor.pinPaths ?? []
  if (declared.length === 0) return
  const unpinned = findUnpinnedApiGets(state.observedApiGets, declared)
  if (unpinned.length > 0) {
    throw new Error(
      `[钉桩覆盖] ${anchor.slug} 这一页发出了 pinPaths 之外的「/api/」GET：${JSON.stringify(unpinned)}\n` +
        `  pinPaths 现值：${JSON.stringify(declared)}\n` +
        `  本轮实测全集：${JSON.stringify(state.observedApiGets)}\n` +
        `含义：跨套件数据耦合从这条键回来了——流程套件写真后端数据时它的响应会变，画面跟着变，基线跟不上。\n` +
        `合法处置只有两种，且都必须显式：① 把这条键（逐字带 search）加进 ANCHORS 的 pinPaths\n` +
        `   并**同批重录** fixture（PW_RECORD_PAYLOADS=1 那条命令；重录会改动入库的 __fixtures__，那是有意的改动）；\n` +
        `   ② 证实这一页不再消费它 ⇒ 反过来改 pinPaths 并同步 data-arrival.ts 的 DATA_PATHS。\n` +
        `禁止的出路：把本条断言放宽成 console.log（那正是它被升成断言之前的形状）。\n` +
        `覆盖面口径（读这条红时必须带着，修复轮 2 的 M-c 补）：本条只量到**调用断言那一刻为止**发出的请求，\n` +
        `  截图之后才由交互产生的 GET 不在账上——那一段由 I-1 的 canary 与流程套件负责，本条不假装管得了它。`,
    )
  }
}

/**
 * I-1：canary 探针的**键**——一个按设计**不入任何 pinPaths** 的端点。
 * 选 `/api/notifications?page=1&size=5` 的理由（复核席给出、本棒核对后端确认）：
 * 它是 `NotificationController.getUserNotifications`（`@GetMapping("")`，page/size 都有缺省值），
 * 任何登录用户都能打（收件人由 `Authentication` 主体决定，无角色注解），
 * 而前端只在**点开通知弹层**时才发它 ⇒ 视觉锚点的首屏不会自然产生这条请求，钉桩清单里也就没有它。
 */
export const CANARY_PROBE_PATH = '/api/notifications?page=1&size=5'

/**
 * I-1 的**判据本体**（纯函数，理由同上——"这条会不会红"必须能在 helpers.spec.ts 里喂合成样本证明）。
 * 要证的只有一件事：**真后端仍在、真会话仍被认、契约名还没改**。
 * 返回违规清单，空＝可用。口径收得很窄，因为它挡的是"整页被 fixture 供数之后画面全绿的另一种原因"：
 *  ① 非 200（会话过期／后端下线 ⇒ 401/403/5xx 或代理层错误）；
 *  ② 解不出 JSON（dev server 把未知路径兜成 index.html 时状态也是 200）；
 *  ③ 信封结构不在（契约改名／路由被挪走：`code` 不是数字，或 `data` 这个键根本没有）；
 *  ④ 顶层是**数组**而不是信封（修复轮 2 的 M-b 补上——原先那半句 `!Array.isArray(parsed)` 把整个信封
 *     检查跳过 ⇒ "后端把返回形状改成裸数组"这一族一条理由都不产生，而它同属"契约改名"族）。
 * 它**不**判业务数值（未读数多少与本条无关），也不判权限角色——判的是"这条探针打得通"。
 */
export function canaryFindings(status: number, bodyText: string): string[] {
  const findings: string[] = []
  if (status !== 200)
    findings.push(`HTTP ${status}（期望 200 ⇒ 会话过期、后端下线或被守卫拦回都会落在这里）`)
  let parsed: unknown = null
  try {
    parsed = JSON.parse(bodyText)
  } catch {
    findings.push(
      '响应体不是 JSON（dev server 对未知路径回 index.html 时状态仍是 200，只判状态会放过）',
    )
  }
  if (Array.isArray(parsed)) {
    findings.push(
      `响应体顶层是裸数组、不是 {code,data} 信封（探针 ${CANARY_PROBE_PATH} ⇒ 后端把返回形状改成数组就是这一族，` +
        `实读 ${JSON.stringify(parsed).slice(0, 120)}）`,
    )
  } else if (parsed !== null && typeof parsed === 'object') {
    const env = parsed as Record<string, unknown>
    if (typeof env.code !== 'number')
      findings.push(`信封缺 code（实读到 ${JSON.stringify(Object.keys(env)).slice(0, 120)}）`)
    if (!('data' in env)) findings.push('信封缺 data 键（契约改名或被挪走的形状）')
  }
  return findings
}

/**
 * I-1：**不经钉桩**的后端＋会话 canary，装在 `runAnchor` 里、`installPayloadPin` **之前**。
 * 【它要堵的洞】两格现在把本页全部 GET 都钉住了 ⇒ 路由守卫 `fetchPermissions` 的 401/过期分支
 *   在这两格的跑测里**永不触发**：`.auth` 会话过期、后端下线、契约改名，画面照样全绿，
 *   连 `assertGenericGuards` 那条"被守卫踢回 /login"的守卫都对它们永久失效
 *   （`visual-helpers.ts` 的 collectGenericGuardReasons 里那句 `href.includes('/login')`
 *    要真有一次导航被踢回去才说得话，而两格的导航所需的全部响应都由 fixture 应答）。
 * 【为什么走 `page.request` 而不是页内 fetch】`page.route` 与 `page.on('request')` 都**不作用于**
 *   APIRequestContext（Playwright 文档明写 routing 只覆盖页面发出的请求），
 *   所以这条探针天生不经钉桩、也天生不进 I-2 的全集清单；会话由 context 的 cookie jar 带上
 *   （本项目凭据是 HttpOnly Cookie，`global-setup.ts` 注释在案），不需要也不允许在这里读令牌。
 * 【为什么放在装钉桩之前】放后面任何一条 route 一旦把探针键也接住（比如有人手滑把它加进 pinPaths），
 *   这条断言就绿在 fixture 上、失去意义；本函数另外显式拒绝"探针键已在 pinPaths"的那种配置。
 * 【极性】失败即该锚点判红，报错前缀 `[canary]` 与像素红（`[零漂移]`／`toHaveScreenshot`）
 *   在 stdout 里一眼分得开——这条红说的是"harness 之外没人给我担保后端了"，不是"画面变了"。
 */
export async function assertBackendCanary(page: Page, anchor: AnchorSpec): Promise<void> {
  const declared = anchor.pinPaths ?? []
  if (declared.length === 0) return
  if (declared.includes(CANARY_PROBE_PATH)) {
    throw new Error(
      `[canary] ${anchor.slug} 把探针键 ${CANARY_PROBE_PATH} 写进 pinPaths 了。\n` +
        `  这条断言的全部力量来自"它一定走真后端"；被钉桩接住以后它绿在 fixture 上，等于没测。\n` +
        `  要么把探针键从 pinPaths 删掉，要么把探针换成另一条同样不被首屏消费的端点并同步本函数。`,
    )
  }
  const res = await page.request.get(CANARY_PROBE_PATH)
  const text = await res.text()
  const findings = canaryFindings(res.status(), text)
  if (findings.length > 0) {
    throw new Error(
      `[canary] ${anchor.slug}：不经钉桩的探针 ${CANARY_PROBE_PATH} 没能证明"真后端＋真会话仍可用"——\n` +
        `  ${findings.join('\n  ')}\n` +
        `  这条红**不是像素红**：这一格的画面全部由 __fixtures__ 供数，所以后端下线、会话过期、\n` +
        `  契约改名这三族故障在像素上永远看不出来（见本函数注释）。先修环境或契约，再谈基线。\n` +
        `  禁止的出路：把本条降成 console.log，或给探针键加钉桩。`,
    )
  }
  console.log(
    `[canary] ${anchor.slug}：${CANARY_PROBE_PATH} ⇒ HTTP 200 且信封完整（真后端＋真会话仍可用；` +
      `这一条不经钉桩，与像素红分开）`,
  )
}

/**
 * 录制模式（PW_RECORD_PAYLOADS=1）：不装 route，改为收集真响应并按 CLOCK_DATE 回拨落盘。
 * 落盘前必须先过 arrival 断言——把空态录进 fixture 等于把"数据到位"钉成假命题。
 */
export function attachPayloadRecorder(page: Page, anchor: AnchorSpec): () => Promise<void> {
  const paths = anchor.pinPaths ?? []
  const captured = new Map<string, PinnedResponse>()
  const wanted = new Set(paths)
  // res.text() 是异步的：不把这些 promise 收着等完，flush 时可能还没落地，
  // 症状是"明明请求过的端点报没等到"（本机第一次录制就是这么炸的）。
  const pending = new Set<Promise<void>>()
  const seen: string[] = []
  page.on('response', (res) => {
    const u = new URL(res.url())
    const key = `${u.pathname}${u.search}`
    if (!u.pathname.startsWith('/api/') || res.request().method() !== 'GET') return
    seen.push(key)
    if (!wanted.has(key)) return
    const p = res
      .text()
      .then((body) => {
        captured.set(key, {
          status: res.status(),
          contentType: res.headers()['content-type'] ?? 'application/json',
          body,
        })
      })
      .catch(() => undefined)
    pending.add(p)
    void p.finally(() => pending.delete(p))
  })
  return async () => {
    for (let i = 0; i < 5 && pending.size > 0; i++) await Promise.all([...pending])
    const serverNow = new Date()
    const serverDate = localDay(serverNow)
    const manifest: PinManifest = {
      slug: anchor.slug,
      recordedAt: serverNow.toISOString(),
      serverDateAtRecord: serverDate,
      // A4：回拨目标是**基线日**（具名常量、入库），不是"CLOCK_EPOCH 反推出来的那一天"。
      // 两者当前由本文件导入期那条断言强制相等；写成 BASELINE_DAY 是为了让这份 manifest
      // **自己声明**它归一到哪天，回放侧 loadPinManifest 会核这一条——硬推的话这件事从不出现在数据里。
      clockDate: BASELINE_DAY,
      // 整天差用日历日算，不用毫秒差 round：跨月/跨夏令时都不会差一天
      backshiftDays: Math.round(
        (Date.parse(`${serverDate}T00:00:00Z`) - Date.parse(`${BASELINE_DAY}T00:00:00Z`)) /
          86400000,
      ),
      requests: {},
    }
    for (const [key, v] of captured) {
      if (!wanted.has(key)) continue
      manifest.requests[key] = { ...v, body: shiftIsoDates(v.body, manifest.backshiftDays) }
    }
    const absent = paths.filter((p) => !manifest.requests[p])
    if (absent.length > 0) {
      // 常见的错法是把 search 漏了：key 是 pathname+search，逐字比。
      // 把 pathname 对得上的真实 key 一起打出来，一眼能看出差在哪个 query。
      const near = absent.map((p) => {
        const base = p.split('?')[0]
        const hits = [...new Set(seen.filter((k) => k.split('?')[0] === base))]
        return `${base} → 实测 ${JSON.stringify(hits)}`
      })
      throw new Error(
        `[载荷录制] ${anchor.slug} 没等到这些端点的响应，拒绝写半成品 fixture：${JSON.stringify(absent)}\n` +
          `按 pathname 找相近的真实 key：${JSON.stringify(near)}\n` +
          `本次页面实际请求过的 /api GET：${JSON.stringify([...new Set(seen)])}\n` +
          `两种可能：pinPaths 里的 search 写错了（key 是 pathname+search，逐字比），或这一页已经不再取这份数（那要重选钉哪个端点）。`,
      )
    }
    mkdirSync(dirname(fixturePathFor(anchor.slug)), { recursive: true })
    writeFileSync(fixturePathFor(anchor.slug), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
    console.log(
      `[pin-record] ${anchor.slug} → ${fixturePathFor(anchor.slug)}（回拨 ${manifest.backshiftDays} 天，端点 ${Object.keys(manifest.requests).length} 个）`,
    )
  }
}

/* ============================================================================ *
 * R-CF1（4c-fix-C 第 9 项）：被钉载荷的**内容**断言——存在性断言不等于内容断言
 * ============================================================================ *
 * 【原先错在哪】`__fixtures__/student-dashboard.json` 钉的是 `/api/student/analytics/visualization`，
 * 而那份载荷是 `durationTrend` 14 × `seconds:0`、`radarData: []`、`heatmapData: []`（admin-governance-analytics
 * 同型：growth 30 × `{newUsers:0, cumulativeUsers:0}`）。上面 DATA_ARRIVAL_CHECKS 的三条
 * （`.l1-card ≥ 1`、`requireSomeStat`、`canvas ≥ 1`）**一条都不看被钉的这份载荷**：
 * 统计卡来自未被钉的 `/api/student/dashboard`（走真后端），趋势图全 0 也照样画出一个 canvas。
 * ⇒ 读者看到"到位断言绿 ＋ 钉桩复现 md5 相等"，就会相信**分析数据到位了**，而被钉区域其实是空的。
 * 【两条限定（结论必须带着走，别当本轮新造的 bug）】
 *   ① 这是**基线既有**状态——`4ca2ea4` 的 fixture 就已是全 0，C 轮只重打了时间戳；
 *   ② 空态可证伪矩阵（anchors.spec.ts Step 2）证明守卫抓得住 `data:null`，但抓不住
 *     "结构完整而数值全 0"这一形态——那组守卫是**存在性**断言，不是**内容**断言。
 * 【裁定（R-CF1）的落法】给被钉载荷加一条内容断言（钉住的 `data` 必须非平凡：
 * 序列长度 > 0 且至少一个数值字段非 0），**不动后端种子数据**（那属数据所有权、越界）。
 * 若某些账号确实没有分析数据（今天这两个锚点就是，载荷来自真后端录制），则该锚点的
 * "被钉区域"**不得作为数据到位的证据**——这条由 `PIN_PAYLOAD_TRIVIAL_EXCUSED` 显式登记、
 * 每次跑都用日志把结论降级说出来，并登记在 LEDGER `P0a-GUARD-02`。
 * 【为什么不是"全 0 就直接绿、只在注释里提醒"】那等于回到本项要修的起点（注释挡不住误读）。
 * 也不是"全 0 就一律红"：那会把**已核实的账号事实**变成永久红，而最省事的出路又会是删断言。
 * 所以判据是三分的：非平凡 ⇒ 正常绿；平凡且已登记 ⇒ 绿但明说"被钉区域不是数据到位证据"；
 * 平凡且未登记 ⇒ **抛**。登记表与实测全 0 集合的一致性由 `helpers.spec.ts` 的 R-CF1 用例双向钉住。
 */

/** inspectPinnedPayload 的普查结果（口径见函数注释）。 */
export interface PinnedPayloadContent {
  nonTrivial: boolean
  /** data 内最深嵌套里最长的**数组**长度（"序列"） */
  maxSeqLen: number
  /** data 内非零有限数的个数 */
  nonZeroCount: number
  detail: string
}

function countPinnedContent(node: unknown, acc: { maxSeqLen: number; nonZeroCount: number }): void {
  if (Array.isArray(node)) {
    if (node.length > acc.maxSeqLen) acc.maxSeqLen = node.length
    for (const v of node) countPinnedContent(v, acc)
    return
  }
  if (node !== null && typeof node === 'object') {
    for (const v of Object.values(node as Record<string, unknown>)) countPinnedContent(v, acc)
    return
  }
  if (typeof node === 'number' && Number.isFinite(node) && node !== 0) acc.nonZeroCount += 1
}

/**
 * 普查一份被钉响应：只走 `data` **内部**——信封上的 `code:200` 不是内容，
 * 否则任何响应都"非平凡"，这条断言就又成了一条永不成立的判据。
 * 非平凡 := 至少一个序列长度 > 0 **且** data 内至少一个数值字段非 0（R-CF1 原文口径）。
 */
export function inspectPinnedPayload(body: string, where: string): PinnedPayloadContent {
  let parsed: { data?: unknown }
  try {
    parsed = JSON.parse(body) as { data?: unknown }
  } catch (e) {
    throw new Error(
      `[钉桩内容] ${where} 的响应体不是合法 JSON：${(e as Error).message}\n` +
        `  被钉响应应为 JSON；解不开说明 fixture 被手改过或录制写坏了，重录它，别把解不开当成"有内容"。`,
    )
  }
  const acc = { maxSeqLen: 0, nonZeroCount: 0 }
  countPinnedContent(parsed.data, acc)
  return {
    nonTrivial: acc.maxSeqLen > 0 && acc.nonZeroCount > 0,
    maxSeqLen: acc.maxSeqLen,
    nonZeroCount: acc.nonZeroCount,
    detail: `data 内最长序列 ${acc.maxSeqLen}、非零数值 ${acc.nonZeroCount}`,
  }
}

/**
 * 按 R-CF1 第二款"该账号确实没有分析数据"**登记在案**的钉桩锚点。
 * 登记在这里 = 台账措辞降级（"被钉区域不作数据到位证据"），**不是**放行内容断言——
 * 载荷全 0 而未登记的钉桩锚点会当场抛；登记条目若对不上实测（比如重录后载荷有内容了），
 * `helpers.spec.ts` 的 R-CF1 一致性断言会红，逼着同一批里把表和 LEDGER 一起改。
 * 【Task 0 之后本表的值必须写**全部**平凡键】两格各钉 5／7 条以后，"平凡"不再只有那一条分析端点：
 * 未读数这种**裸数值**载荷（`data` 是数字、没有数组）按 inspectPinnedPayload 的口径同样判平凡
 * （最长序列 0），admin 格那条还是 `0`。所以下面每条依据逐键写形状，运行时日志里那句
 * `被钉载荷平凡([...])` 与这里必须是同一组键——键集合由 `PIN_PAYLOAD_TRIVIAL_KEYS` 与
 * `helpers.spec.ts` 的 ④ 逐键钉住（修复轮 1 的 I-4①；原先 ④ 用 `.some()` 只比 slug 集合，
 * 键集合漂开它看不见）。
 */
export const PIN_PAYLOAD_TRIVIAL_EXCUSED: Record<string, string> = {
  'student-dashboard':
    '两条平凡键：① 被钉 /api/student/analytics/visualization：durationTrend 14 × seconds:0、radarData []、heatmapData []（fixture 自 4ca2ea4 录制即此形状，来自真后端）⇒ 该学生账号基线日无分析数据；' +
    '② 被钉 /api/notifications/unread-count：data 是裸数值 42、无数组 ⇒ 按 inspectPinnedPayload 口径"最长序列 0"即平凡（Task 0 新钉的键，登记于修复轮 1）。其余三条（permissions／student/courses／student/dashboard）普查非平凡。',
  'admin-governance-analytics':
    '两条平凡键：① 被钉 /api/analytics/users/growth?days=30：30 × {newUsers:0, cumulativeUsers:0}（同上）⇒ 该 admin 账号 30 天窗口内无增长数据；' +
    '② 被钉 /api/notifications/unread-count：data 是裸数值 0 ⇒ 序列长度 0 且非零数值 0（Task 0 新钉的键，登记于修复轮 1）。其余五条（permissions／courses／heatmap／dashboard／ranking）普查非平凡。',
}

/**
 * 上面那两条豁免**各自覆盖哪些键**——按 `pathname+search` 逐字列出、**排序后**比较。
 * 【为什么要单独一张表而不是从理由散文里读】豁免按 slug 登记，运行时只问"这个 slug 有没有登记"，
 * 于是"多钉了一条平凡键"这件事在旧形状下**没有任何入库面看得见**（I-4① 点名的就是这个盲区：
 * `helpers.spec.ts` 的 ④ 当时用 `.some()`，slug 级一致就绿）。有了这张表，④ 才能逐键判相等。
 * 【改这张表的唯一合法时机】重录 fixture 之后同一批：平凡集合变了 ⇒ ④ 当场红 ⇒ 在这里改键、
 *   同步 `PIN_PAYLOAD_TRIVIAL_EXCUSED` 的依据文本与 LEDGER `P0a-GUARD-02`。
 * 【本表不参与运行时判定】运行时行为一字未动（仍是"有登记即放行加降级日志"），
 *   它只把"登记的到底是什么"变成可失败的断言面。
 */
export const PIN_PAYLOAD_TRIVIAL_KEYS: Record<string, readonly string[]> = {
  'student-dashboard': ['/api/notifications/unread-count', '/api/student/analytics/visualization'],
  'admin-governance-analytics': [
    '/api/analytics/users/growth?days=30',
    '/api/notifications/unread-count',
  ],
}

/**
 * 回放侧的内容门：anchors 在 `assertPinCoverage` 之后、`assertZeroDiff` 之前调用
 * （放在覆盖检查之后，是为了让"声明了却没被消费"那种红先按原归因报出来）。
 * 只在**交付口径**（装了钉桩）跑；录制模式产不出 pinState，由下一次回放跑补上这一课。
 */
export function assertPinnedPayloadContent(anchor: AnchorSpec, manifest: PinManifest): void {
  const trivial: string[] = []
  const details: string[] = []
  for (const key of anchor.pinPaths ?? []) {
    const hit = manifest.requests[key]
    if (!hit) continue // key 缺失由 assertPinCoverage 先拦住，这里不重复报
    const v = inspectPinnedPayload(hit.body, `${anchor.slug} → ${key}`)
    if (!v.nonTrivial) {
      trivial.push(key)
      details.push(`${key}：${v.detail}`)
    }
  }
  const census = details.join('；')
  if (trivial.length === 0) {
    if (PIN_PAYLOAD_TRIVIAL_EXCUSED[anchor.slug]) {
      console.log(
        `[钉桩内容] ${anchor.slug}：被钉载荷现已非平凡——PIN_PAYLOAD_TRIVIAL_EXCUSED 里的豁免已过时，\n` +
          `           必须同批删登记条目并更新 LEDGER P0a-GUARD-02（helpers.spec.ts 的一致性断言此刻就会红）。`,
      )
    }
    return
  }
  const reason = PIN_PAYLOAD_TRIVIAL_EXCUSED[anchor.slug]
  if (reason) {
    console.log(
      `[钉桩内容] ${anchor.slug}：被钉载荷平凡（${JSON.stringify(trivial)}；普查 ${census}）\n` +
        `           ⇒ 按 R-CF1（4c-fix-C 第 9 项），本锚点**被钉区域不得作为"数据到位"的证据**；\n` +
        `           canvas ≥ 1 这类存在性检查只证明画面不空，证明不了被钉区域里的数值是真的。登记依据：${reason}`,
    )
    return
  }
  throw new Error(
    `[钉桩内容] ${anchor.slug}：被钉载荷（${JSON.stringify(trivial)}）没有内容——序列全 0 或全空（普查：${census}）。\n` +
      `两种处置（R-CF1 裁定；**改后端种子数据不在本仓权限内**）：\n` +
      `  ① 该账号确实没有这类数 ⇒ 先拿真后端 curl 一次证实（别拿 fixture 自己当证据），再把它加进\n` +
      `     PIN_PAYLOAD_TRIVIAL_EXCUSED（登记依据要写形状与出处）并同步 LEDGER 的措辞降级条目；\n` +
      `  ② fixture 是录歪/录坏的新形态 ⇒ 重录（cd frontend && PW_PORT=5175 PW_RECORD_PAYLOADS=1 \\\n` +
      `       npx playwright test -c playwright.visual.ts anchors -g "${anchor.slug}"），随后不带 PW_RECORD_PAYLOADS 再跑一次，\n` +
      `     由本门复查。别把这条断言删掉或降成日志——"钉桩内容"一旦不可证，本项修的洞就原样回来。`,
  )
}

/* -------------------------------------------------------------------------- *
 * 4c-fix-C 第 11 项：`PW_VISUAL_NOPIN` 的取法只有这一个函数（消费者是 anchors 与 sensitivity）
 * -------------------------------------------------------------------------- *
 * 【原先错在哪】两个消费者的力度不一样：anchors 拒绝任何"真值但不等于 0/空串"的值，
 * 探针只认 `=== '1'` ⇒ `PW_VISUAL_NOPIN=true` 在**探针**侧被静默忽略、钉桩照装——
 * 这正是 A3 要清除的"开关被静默接受"同一类毛病，只是换了个消费者。
 * 【统一后的口径】合法值只有三种语义：未设置 / 空串 / `0` ⇒ 关；`1` ⇒ 开；
 * **其余一律当场抛**（fail-fast），谁都不许把一个拼错的开关读成"没开"。
 * 消费侧的差异只剩一件事（那是分工，不是取法分歧，见 A3 注释块）：
 * anchors 读到"开"必须拒绝运行（本套件只走钉住那条路），探针读到"开"才做反向对照。
 * 与 `pinShiftDays()` 同款先例：env 的解析与 fail-fast 归 helper，不归各 spec 各写一份。
 */

/** 纯函数：把 `PW_VISUAL_NOPIN` 的原始值解析成布尔；非法值抛。拆出来是为了能被 helpers.spec.ts 直接弄红。 */
export function parseNopinValue(raw: string | undefined): boolean {
  if (raw === undefined || raw === '' || raw === '0') return false
  if (raw === '1') return true
  throw new Error(
    `[口径] PW_VISUAL_NOPIN=${JSON.stringify(raw)} 是非法值：只接受 未设置/空串/0（关）或 1（开）。\n` +
      `  把一个拼错的开关静默读成"没开"正是 A3 清除过的那类假读数，两个消费者（anchors 与 sensitivity）\n` +
      `  现在共用本函数取法，非法值一律 fail-fast（第 11 项）。`,
  )
}

/** 读环境后走统一解析（导入期调用：非法值在任何一张图被截出来之前就抛）。 */
export function readNopinEnv(): boolean {
  return parseNopinValue(process.env.PW_VISUAL_NOPIN)
}
