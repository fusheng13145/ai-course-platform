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
 *   ① 只钉 AnchorSpec.pinPaths 声明的那几个端点（现在每个日期锚点各 1 个），
 *      其余请求照旧打真后端——否则"数据确已到位"会被一起钉成假命题（Step 2 的洞就是这么来的）；
 *   ② fixture 是从**真后端录下来的**，录的那一次必须先过 arrival 断言（录空态 = 基线空态，
 *      所以 record 模式在写出 fixture 前就跑一次 DATA_ARRIVAL_CHECKS）；
 *   ③ 钉死之后这几个端点**不再被像素门禁覆盖**：产品若改图表数据结构，本套件不会红，
 *      要红得先重录（重录会让图与已提交基线不符 ⇒ 那时才是真红）。这条盲区要写进 LEDGER。
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
    (Date.parse(`${m.serverDateAtRecord}T00:00:00Z`) - Date.parse(`${m.clockDate}T00:00:00Z`)) / 86400000,
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
    throw new Error(`[载荷钉死] ${file} 形状不对（slug 不符或 requests 为空）——宁可红，不拿空 fixture 当钉住`)
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
 * 返回的 state 交给 assertPinCoverage 验"声明的端点真的都被请求过"——
 * 钉了却没人调用 = fixture 过期或路径写错，那种"绿"和没钉一样，必须红。
 */
export async function installPayloadPin(page: Page, anchor: AnchorSpec): Promise<PinState> {
  const paths = anchor.pinPaths ?? []
  const manifest = loadPinManifest(anchor.slug)
  const shift = pinShiftDays()
  const state: PinState = { served: [], unpinnedTodayBearing: [], shiftDays: shift }
  const serverToday = localDay(new Date())

  await page.route((url) => paths.includes(`${url.pathname}${url.search}`), async (route) => {
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
        captured.set(key, { status: res.status(), contentType: res.headers()['content-type'] ?? 'application/json', body })
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
      backshiftDays: Math.round((Date.parse(`${serverDate}T00:00:00Z`) - Date.parse(`${BASELINE_DAY}T00:00:00Z`)) / 86400000),
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
    console.log(`[pin-record] ${anchor.slug} → ${fixturePathFor(anchor.slug)}（回拨 ${manifest.backshiftDays} 天，端点 ${Object.keys(manifest.requests).length} 个）`)
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
 */
export const PIN_PAYLOAD_TRIVIAL_EXCUSED: Record<string, string> = {
  'student-dashboard':
    '被钉 /api/student/analytics/visualization：durationTrend 14 × seconds:0、radarData []、heatmapData []（fixture 自 4ca2ea4 录制即此形状，来自真后端）⇒ 该学生账号基线日无分析数据。',
  'admin-governance-analytics':
    '被钉 /api/analytics/users/growth?days=30：30 × {newUsers:0, cumulativeUsers:0}（同上）⇒ 该 admin 账号 30 天窗口内无增长数据。',
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
