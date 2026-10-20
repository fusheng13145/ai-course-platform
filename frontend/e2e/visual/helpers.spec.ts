import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import zlib from 'node:zlib'
// 第 12 项：把 `--update-snapshots` 那道门本体导进来验它的牙（取值口径见该用例注释）
import { assertUpdateSnapshotsAllowed } from './global-setup'
import {
  ANCHORS,
  applyEvidenceWrite,
  assertManifestDates,
  assertPinnedPayloadContent,
  BASELINE_DAY,
  CLOCK_DATE,
  freezeClock,
  inspectPinnedPayload,
  loadPinManifest,
  maskOf,
  parseNopinValue,
  PIN_PAYLOAD_TRIVIAL_EXCUSED,
  pngSize,
  readNopinEnv,
  setTheme,
  settlePage,
  snapName,
  SNAPSHOT_PAGE_TIME_MS,
  SNAPSHOT_THRESHOLDS,
  storagePathFor,
  type AnchorSpec,
  type PinManifest,
  THEME_KEY,
  waitForStableText,
} from './visual-helpers'

/** 手写一个最小 PNG 编码器（只用于下面那条解码回归；过滤类型按参数选） */
function encodePng(w: number, h: number, colorType: 2 | 6, filterType: 0 | 3): Buffer {
  const ch = colorType === 2 ? 3 : 4
  const rows: Buffer[] = []
  let prev: Buffer | null = null
  for (let y = 0; y < h; y++) {
    const row = Buffer.alloc(w * ch)
    for (let x = 0; x < w; x++) {
      row[x * ch] = (x * 37 + y * 11) & 255
      row[x * ch + 1] = (x * 91 + y * 7) & 255
      row[x * ch + 2] = (x * 13 + y * 57) & 255
      if (ch === 4) row[x * ch + 3] = ((x + y) * 13) & 255
    }
    const f = Buffer.alloc(row.length)
    for (let i = 0; i < row.length; i++) {
      const a = i >= ch ? row[i - ch] : 0
      const b = prev ? prev[i] : 0
      const pred = filterType === 0 ? 0 : (a + b) >> 1
      f[i] = (row[i] - pred) & 255
    }
    rows.push(Buffer.concat([Buffer.from([filterType]), f]))
    prev = row
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8
  ihdr[9] = colorType
  const chunk = (type: string, data: Buffer): Buffer => {
    const len = Buffer.alloc(4)
    len.writeUInt32BE(data.length)
    const t = Buffer.from(type, 'ascii')
    const body = Buffer.concat([t, data])
    const c = Buffer.alloc(4)
    c.writeUInt32BE(crc32(body))
    return Buffer.concat([len, body, c])
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

function crc32(buf: Buffer): number {
  let c = 0xffffffff
  for (const b of buf) {
    c ^= b
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  }
  return (c ^ 0xffffffff) >>> 0
}

function md5(file: string): string {
  return createHash('md5').update(readFileSync(file)).digest('hex')
}

/**
 * 剥掉**整行注释**（`//`、块注释起始 `/*`、块注释续行 `*`），只留代码面。
 * 第 11 项与第 12 项的源码级负向断言都要用它：这几份 spec 里留着大量"原先错在哪"的历史注释，
 * 里面逐字写着被禁的那个写法（如 `process.env.PW_VISUAL_NOPIN === '1'`）和 `threshold: 0` 之类的散文。
 * 不排除注释行，负向守卫会**假红**；只排注释行而不动代码，正向断言照旧。
 * 口径是"行首（忽略缩进）就是注释标记"，多行字符串中间的行不在此列——本仓那两个守卫的目标写法
 * 都在行首，够用且可预测；真要扩面时改这里，别改断言。
 */
function isFullLineComment(line: string): boolean {
  return /^\s*(\/\/|\/\*|\*)/.test(line)
}

function stripCommentLines(src: string): string {
  return src
    .split('\n')
    .filter((line) => !isFullLineComment(line))
    .join('\n')
}

/**
 * 「谁又自读 NOPIN 那个开关」这把尺（C1 修复轮 1 的 F1／复核 I-2）。
 * 【原先缺什么】负向半边只有两条判据：`process.env.PW_VISUAL_NOPIN`（点号）与 `process.env[NOPIN_ENV]`，
 * 于是 `process.env["PW_VISUAL_NOPIN"]` 这种**同一件事的第三种拼法**能整条溜过去——复核席在内存里
 * 给探针加一行这种写法，四条断言全绿而"两个消费者力度分岔"原样回来了。
 * 【现口径】`[.\[]` 与两侧引号**都是可选**的，所以点号、裸名方括号、双引号、单引号加空格四种拼法都命中；
 * 而另一种开关（`process.env.PW_RECORD_PAYLOADS`）与唯一合法取法（`readNopinEnv()`）都不命中。
 * 【为什么不是"引号必选那一版"】本条最早给过 `process\.env[\[.]\s*(["']PW_VISUAL_NOPIN["']|NOPIN_ENV)`
 * 这类写法，它把引号做成必选 ⇒ **漏掉的恰恰是原本要拦的那一种**（点号写法）。
 * 下面那条「四种拼法」用例是这次错误的永久拦截网：想改这把尺，先在那里加拼法，别只改这里。
 */
const SELF_READ_NOPIN_ENV = /process\.env\s*[.\[]\s*["']?(PW_VISUAL_NOPIN|NOPIN_ENV)["']?/

/** 匹配器本体：消费者守卫与 F1 那条反例用例共用**同一个**表达式，不许两边各写一把尺。 */
function selfReadsNopinEnv(src: string): boolean {
  return SELF_READ_NOPIN_ENV.test(src)
}

/**
 * 递归列出 `dir` 下所有 `.spec.ts`（C1 修复轮 1 的 F2c／复核 I-3 第 3 处缺口）。
 * 【原先缺什么】判据用的是 `readdirSync(dir).filter(endsWith('.spec.ts'))`——**不递归**。
 * 而 runner 的 `testDir: './e2e/visual'` 收的是整棵树 ⇒ 放进子目录的一份 spec 会**同时**逃过这条扫描
 * 与"文件集合钉住"那条断言（后者只列顶层名），覆盖面静默缩水而全表照绿。
 * 先例取本仓现成的 `src/composables/__tests__/coverage-lib.ts` 的 `readVueSources`（递归＋跳过测试目录）。
 * 【跳过这三个目录】`__snapshots__`／`__fixtures__`／`__tests__`——那三处是产物不是 spec。
 * 这里有个真实的陷阱：`snapshotPathTemplate` 让 `__snapshots__` 下面存在一个**目录名就叫
 * `anchors.spec.ts`** 的目录，所以判据必须"先看是不是目录、再看名字"（本函数就是那个顺序）；
 * 反过来写会把产物目录当 spec 收进扫描面，`readFileSync` 当场在目录上炸开。
 * 【返回相对路径、`/` 分隔】这样"钉住集合"那条断言能原样写出子目录里的文件（`sub/x.spec.ts`），
 * 且比较结果不随宿主的路径分隔符变。
 */
function listSpecFiles(dir: string, rel = ''): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const relPath = rel ? `${rel}/${entry}` : entry
    if (statSync(full).isDirectory()) {
      if (['__snapshots__', '__fixtures__', '__tests__'].includes(entry)) continue
      out.push(...listSpecFiles(full, relPath))
      continue
    }
    if (entry.endsWith('.spec.ts')) out.push(relPath)
  }
  return out.sort()
}

/**
 * 阈值键扫描的判据本体（C1 修复轮 1 的 F2a＋F2b／复核 I-3 第 1、2 处缺口）。
 * 【原先缺什么 (a)】扫描逻辑整段内联在那条用例的 for 循环里 ⇒ "这条扫描会红"只有未入 git 的
 * 过程记录能证，交付面缺内容。抽成纯函数后反例可以喂**合成文件**，跟着仓库每次跑。
 * 【原先缺什么 (b)】放行判据是"同一行提到 `SNAPSHOT_THRESHOLDS` 这个名字就放过"，
 * 于是 `{ ...SNAPSHOT_THRESHOLDS, threshold: 0.5 }` **一行即绕开**（复核席实测守卫放过）——
 * 而 spread 后面紧跟覆盖键恰恰是最该拦的那种：名字在场、值却不是常量给的。
 * 【现口径：钉的是"没有本地覆盖"】剥整行注释后，`threshold`／`maxDiffPixelRatio` 这两个键的**值以数字开头**
 * 即违规，与同一行有没有提到那个名字**无关**。
 * 【两侧都不许误伤，两种形态本仓真实存在】值是同享常量的属性引用（探针的
 * `threshold: knob ?? SNAPSHOT_THRESHOLDS.threshold`、账本里的 `threshold: THRESHOLDS.threshold`）放行；
 * 类型声明（`threshold: number`）放行——判据是"数值字面量"，**不是**"键名出现过"。
 * 本棒按"键名即红"那把放宽的尺实测过五个 spec 的剥注释文本：anchors 2 条＋sensitivity 8 条
 * ＋本文件 1 条 = **11 条假红**（本文件那 1 条已随本轮改成属性断言，放宽的代价仍剩 10 条）。
 * 定义处 `visual-helpers.ts` 不在扫描面内：`listSpecFiles` 只收 `.spec.ts`。
 * 【行号是真的】按原始行逐行走、用 `isFullLineComment` 跳过注释行而**不塌缩索引**，
 * 所以返回串里的行号就是文件里的行号（`stripCommentLines` 会改变行号，不能拿它做定位）。
 */
function findThresholdLiterals(sources: { file: string; text: string }[]): string[] {
  const offenders: string[] = []
  for (const src of sources) {
    src.text.split('\n').forEach((line, i) => {
      if (isFullLineComment(line)) return
      if (/(?:threshold|maxDiffPixelRatio)\s*:\s*[\d.]/.test(line)) {
        offenders.push(`${src.file}:${i + 1}  ${line.trim()}`)
      }
    })
  }
  return offenders
}

// helper 自身的测试（smoke.spec.ts 只管主题注入在页面上生效）。
// 为什么值得单开：settlePage / freezeClock 是 24 张基线的公共依赖，它们出错的方式是
// "每页都假红"或"每页都假绿"，比 smoke 的用例更隐蔽——上一轮的 #1/#2 两个缺陷正是
// 落在这段无断言覆盖区里。
// 前四条用 setContent 造页面，不依赖后端；第五条真起 dev server 打 /login；
// 第六、七条是纯函数与常量。

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
  // /login 是**零匹配页**：waitForStableText 一轮都不推进，1200ms 轮询预算全靠 settlePage 补齐。
  // 总额 = pauseAt 5000 + runFor 5000 + 轮询预算 1200 + 帧数沉降 3200，写在 SNAPSHOT_PAGE_TIME_MS 里。
  expect(t1).toBe(SNAPSHOT_PAGE_TIME_MS)
})

// 评审 Important 3 的结构证明：截图时刻曾经 = 09:00:10 + 120ms×(轮数−1)，
// 于是同一批基线里"零匹配页"与"文本仍在变的页"落在两个不同瞬间，
// helper 注释却宣称它们同刻——是个假口径而不是碰巧没漂。
// 下面这条与上一条**必须同时成立**才算修好：轮数不同的两类页面，
// settlePage 之后 Date.now() 都等于同一个常量（差额由预算补齐）。
test('settlePage 在需要多轮轮询的页上落到同一个截图时刻', async ({ page }) => {
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
  await settlePage(page)
  // 文本真的收敛了（CountUp 之类"还没跑完就截"的老毛病仍被拦）
  await expect(page.locator('.s-value')).toHaveText('7')
  // 且时刻与零匹配页一模一样——轮数被预算吸收了
  expect(await page.evaluate(() => Date.now())).toBe(SNAPSHOT_PAGE_TIME_MS)
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

/* ------------------------------------------------------------------ *
 * Task 4c Step 1b / 1c：量程常量与 22 张基线的"篡改痕迹"
 * ------------------------------------------------------------------ */

/**
 * 量程常量钉死（Step 1b）。
 * 4b 这一轮里这个常量**在飞的过程中就被另一个代理改过**（0.2→0.05→0.02），
 * 而后 60+ 个任务都要读它——它当时只靠散文保护，现在有一条断言保护。
 * 规矩：**只许收紧**（把这条断言与注释一起改），不许放宽。
 *
 * 【这一格为什么从 0.0005 收到 0.0001】Task 4c Step 4 实测（命令与出处见 visual-helpers.ts 的
 * SNAPSHOT_THRESHOLDS 注释块）：日期漂移被载荷钉死抵住之后，4 张日期锚点与基线 **md5 相等、字节差 0**，
 * 于是 r 可以一路收到 0.0001（免检额 1440×900×0.0001 = 129.6 ≈ 130 px）而整表仍 22 绿——
 * **三次独立全量 anchors 全绿**（各 `34 passed / 2 failed`，红的是 error-404 明暗 = 缺陷 D-1），
 * 外加 0.0002 一档单次全绿。残余真抖动最大实测 46 px（`student-courses-modal` 暗，Task 4b §R2.3 的读数，
 * 本会话未重测）⇒ 130 对 46 还有约 2.8 倍余量，这条余量是本档唯一的软肋。
 * 收到 0.0001 的直接收益：`radius` 单卡页 198 px 与 `border` 单卡页…（见判定表）从**漏检**变**检出**。
 * 再往 0 收不行：0 会让那条 46 px 抖动和任何未来的薄改动一起把整表点红，而那不是回归。
 */
test('量程常量钉死，改动必须显式决策', () => {
  // 【C1 修复轮 1 的 F2b 改了这条的写法，牙没变】原先是
  // `expect(SNAPSHOT_THRESHOLDS).toEqual({ threshold: 0, maxDiffPixelRatio: 0.0001 })`——
  // 那一行既是"钉常量本身"、又逐字写着 `threshold:` 加数值，正是新扫描判据要拦的形态。
  // 旧守卫靠"同一行提到 SNAPSHOT_THRESHOLDS 就放行"躲过去，而那条放行判据本身就是假绿口
  // （`{ ...SNAPSHOT_THRESHOLDS, threshold: 0.5 }` 同样提到那个名字）。
  // 现改成"逐键取属性 + 钉形状"：两个数值仍逐个写死（收紧它照样红在这里），
  // 但不再用 `键: 数字` 的对象字面量写法 ⇒ 本文件不再需要任何放行豁免，扫描面保持"零后门"。
  expect(
    SNAPSHOT_THRESHOLDS.threshold,
    '交付档 threshold 变了——放宽必须与量程注释、LEDGER 一起显式决策',
  ).toBe(0)
  expect(
    SNAPSHOT_THRESHOLDS.maxDiffPixelRatio,
    '交付档 maxDiffPixelRatio 变了——本仓所有"免检额几倍余量"的算术随之作废，见 §6.12 与台账',
  ).toBe(0.0001)
  expect(
    Object.keys(SNAPSHOT_THRESHOLDS).sort(),
    '常量多了键：新增的比较口径不会自动落进上面两条断言，必须一起补',
  ).toEqual(['maxDiffPixelRatio', 'threshold'])
})

/**
 * 单来源的机械版本：两处消费方都必须**引用**这个常量，不许各自再写一份字面量。
 * 上一轮"量程测量用的阈值"和"回归用的阈值"不是同一组数，正是靠这个约定挡住的；
 * 散文挡不住并发代理（Task 4b 那次 00:43 的覆盖事故就是并发写的）。
 *
 * 【4c-fix-C 第 12 项把负向半边从两个文件扩到扫遍所有 spec】原先这条只读 `anchors.spec.ts` 与
 * `sensitivity.spec.ts` ⇒ Task 14 的 `motion-observation.spec.ts` 写一份 `threshold: 0.2` 也全绿，
 * 而"比较口径有第二份来源"这件事从此不被任何东西看见。扩面要有两条精度限定，缺一条就假红：
 *  ① **排除整行注释**——计划原话就是"负向正则会被散文注释里含 `threshold: 0` 误红"。
 *     本仓现状立刻有一处：本文件讲 `stripCommentLines` 的那段注释逐字写着 `threshold: 0`。
 *  ② ~~放行"同一行引用了 `SNAPSHOT_THRESHOLDS`"的那一行~~ ——**这条放行判据已由 C1 修复轮 1 的 F2b 撤销**：
 *     它只看"有没有提到那个名字"，于是 `{ ...SNAPSHOT_THRESHOLDS, threshold: 0.5 }` 一行即绕开。
 *     现口径是钉**没有本地覆盖**：判据本体见 `findThresholdLiterals`（值以数字开头即违规，与名字在场无关）。
 *     「量程常量钉死」那条断言同步改成逐键属性断言，因此本文件不再需要豁免——扫描面零后门。
 * 【正例】本仓现况下扫遍所有 spec **应当仍然全绿**（`motion-observation.spec.ts` 与 `smoke.spec.ts`
 * 全文不出现阈值字面量，也不用 `toHaveScreenshot`），所以这条是"新增一个绿的正例"；
 * 会红的反例原先只在过程记录里，C1 修复轮 1 的 F2a 已把它抽成 `findThresholdLiterals` 并入库一条
 * 喂合成文件的用例（纯字面量、spread 加覆盖两种形态都在里面），不再依赖任何人的手工证明。
 */
test('阈值只有一个来源：消费侧引用常量，且 e2e/visual/ 下所有 spec 都不写字面量（第 12 项扫遍）', () => {
  const dir = join(process.cwd(), 'e2e/visual')
  const anchors = readFileSync(join(dir, 'anchors.spec.ts'), 'utf8')
  const probe = readFileSync(join(dir, 'sensitivity.spec.ts'), 'utf8')
  expect(anchors).toContain('...SNAPSHOT_THRESHOLDS')
  // 探针的 fallback 必须指向同一常量；env 旋钮只许收紧（由 sensitivity.spec.ts 自己 fail fast）
  expect(probe).toContain('SNAPSHOT_THRESHOLDS.threshold')
  expect(probe).toContain('SNAPSHOT_THRESHOLDS.maxDiffPixelRatio')
  // 负向半边：扫遍 e2e/visual/ 下**所有** spec（F2c 后是递归收，子目录里的也算），而不是点名两个
  const specs = listSpecFiles(dir)
  expect(
    specs,
    'e2e/visual/ 下的 spec 集合变了——本条的覆盖面跟着变，先数清楚再加文件（现 5 个，全在顶层；子目录里的会带相对路径）。' +
      '新增文件若写了阈值字面量，本条的下一条断言就会点名它',
  ).toEqual([
    'anchors.spec.ts',
    'helpers.spec.ts',
    'motion-observation.spec.ts',
    'sensitivity.spec.ts',
    'smoke.spec.ts',
  ])
  const sources = specs.map((f) => ({ file: f, text: readFileSync(join(dir, f), 'utf8') }))
  const offenders = findThresholdLiterals(sources)
  // 读数两个口径都写进红话：剥注释后应为 0 条；不剥则本文件自己就有散文命中——这正是"为什么不剥会假红"的实物
  expect(
    offenders,
    `${sources.length} 个 spec 的代码面出现阈值字面量（比较口径有了第二份来源，第 12 项／F2b）：\n  ${offenders.join('\n  ')}\n` +
      `  口径：剥整行注释后按"键的值是数字面量"判，本仓现况 5 个 spec 共 0 条；同一路不剥注释时本文件命中 9 条散文（本轮实测，91f541b 态是 5 条）。`,
  ).toEqual([])
})

/**
 * C1 修复轮 1 的 F2a＋F2b（复核 I-3 第 1、2 处缺口）：把"这条扫描会红"从过程记录变成入库断言。
 * 【原先缺什么】第 12 项交付时反例只在未入 git 的报告与台账单元格的重放说明里（手工），
 * 用例体内除了"文件集合相等"没有任何合成负例 ⇒ 按围栏口径等于"这条改写没测"。
 * 【两侧都钉】命中侧两条正是复核席构造出来的那两个旁路形态；不命中侧四条是本仓真实存在的合法写法，
 * 把它们钉住是为了下一条棒不会为了"更严"把判据放宽成"键名即红"——那把尺本棒实测在剥注释后有 10 条假红。
 * 【为什么合成数值要走 `${}` 插值】这些样本字符串写在**被扫描的文件本身**（`helpers.spec.ts`）里，
 * 逐字写出 `threshold: 0.5` 会让本文件自己成为违规行、上一条用例当场红（本棒实测过这个自命中陷阱）。
 * 插值后源码面是 `threshold: ${num}`（冒号后不是数字，判据不认），喂给函数的字符串仍是真形态。
 * 【已知残留，未在本轮修】键名带引号那种（`{"threshold": 0.5}`）不命中——冒号前多一个引号，
 * 判据（与第 12 项原口径一致）要求键名紧接可选空白再跟冒号。放宽它会新增假红面，本轮不动，记进报告。
 */
test('阈值扫描判据自己会红：合成反例两类都拦、合法形态与真实清单都放行（F2 反例入库）', () => {
  const num = 0.5
  const ratioNum = 0.2
  const knobNum = 0.05

  // —— 命中侧：两种形态各一条（纯字面量／spread 后面跟着覆盖键）——
  const pureLiteral = findThresholdLiterals([
    {
      file: 'synthetic-a.spec.ts',
      text: `  await expect(page).toHaveScreenshot("x.png", { threshold: ${num} })`,
    },
  ])
  expect(pureLiteral, '纯字面量阈值没被拦住——扫描面形同虚设').toHaveLength(1)
  expect(
    pureLiteral[0],
    '违规串必须带定位信息（文件与真行号），别让下一个人对着整文件猜',
  ).toContain('synthetic-a.spec.ts:1')

  const spreadOverride = findThresholdLiterals([
    {
      file: 'synthetic-b.spec.ts',
      text: `  const opts = { ...SNAPSHOT_THRESHOLDS, threshold: ${num} }`,
    },
  ])
  expect(
    spreadOverride,
    '{ ...SNAPSHOT_THRESHOLDS, 覆盖键 } 这种一行即绕开旧放行判据的形态没被拦住',
  ).toHaveLength(1)
  // 这条断言钉的是"为什么旧判据会放它走"：违规行里**确实提到**那个常量名，
  // 所以"名字在场即放行"这套逻辑当场放过它——现口径只看值是不是本地字面量。
  expect(spreadOverride[0], '拦下的这一行本来就含常量名——这正是旧放行判据的假绿口').toContain(
    'SNAPSHOT_THRESHOLDS',
  )

  const trailingComment = findThresholdLiterals([
    {
      file: 'synthetic-c.spec.ts',
      text: `  await expect(page).toHaveScreenshot("x", { maxDiffPixelRatio: ${ratioNum} }) // 放宽一档`,
    },
  ])
  expect(
    trailingComment,
    '代码行尾的同行注释不改变"键值本地字面量"这件事，仍须命中（不塌行号的口径）',
  ).toHaveLength(1)
  expect(trailingComment[0]).toContain('synthetic-c.spec.ts:1')

  // 真行号：第三条才是违规行时，报的必须是 3 而不是剥掉注释后的 1。
  const numbered = findThresholdLiterals([
    {
      file: 'synthetic-d.spec.ts',
      text: `// 历史注释里写着 ${'threshold'}: 0 不算\n/** 块注释也不算 */\n  const opts = { threshold: ${knobNum} }\n`,
    },
  ])
  expect(numbered, '两条注释行之间的真违规行没被拦').toHaveLength(1)
  expect(numbered[0], '行号被 stripCommentLines 塌过就不是文件里的真坐标').toContain(
    'synthetic-d.spec.ts:3',
  )

  // —— 不命中侧：本仓真实存在的四种合法写法 ——
  const legal = findThresholdLiterals([
    {
      file: 'synthetic-legal.spec.ts',
      text: [
        '  const specs = { ...SNAPSHOT_THRESHOLDS }',
        '  const t = knobThreshold ?? SNAPSHOT_THRESHOLDS.threshold',
        '  threshold: THRESHOLDS.threshold,',
        '  threshold: number',
      ].join('\n'),
    },
  ])
  expect(
    legal,
    `四种合法写法被判违规（假红，判据被放宽成"键名即红"了？）：\n  ${legal.join('\n  ')}`,
  ).toEqual([])

  // —— 整行注释不许计入（第 12 项那条精度限定的本体）——
  // 这两条样本里的数值也走插值：它们的**内容**是注释、但**物理行是代码行**，
  // 逐字写出数字会让本文件自己变成违规行（本棒第一轮就是这么红的，实测 2 条）。
  const commentSample = `  // 原先这里写着 threshold: ${num}，现已收进常量`
  const docSample = ` * 免检额算式 threshold: ${num} 与 0.0001 都要写出来才有牙`
  expect(
    findThresholdLiterals([{ file: 'synthetic-comment.spec.ts', text: commentSample }]),
  ).toEqual([])
  expect(findThresholdLiterals([{ file: 'synthetic-doc.spec.ts', text: docSample }])).toEqual([])

  // —— F2c：递归遍历本体也得有入库证据（只在 os.tmpdir() 下造，跑完删；不碰仓库树）——
  // 上面"钉住集合"那条断言在**现况无子目录**时，与退化成一层的 `readdirSync().filter()` **完全同值**
  // ——这正是"守卫绿在不存在的东西上"那一族。所以这里拿一个临时目录把两侧都逼出来：
  //  ① 子目录里的 spec 必须被收进来（漏了就等于复核 I-3 那条旁路还在）；
  //  ② `__snapshots__` 里那份**同名像 spec** 的产物必须被排除（本仓 `__snapshots__/anchors.spec.ts/`
  //     真实存在的是一个目录，这里额外造一个文件形态的诱饵，让"跳过"这件事在测试面有后果）。
  const tmp = join(tmpdir(), `visual-spec-walk-${process.pid}`)
  try {
    mkdirSync(join(tmp, 'nested'), { recursive: true })
    mkdirSync(join(tmp, '__snapshots__', 'anchors.spec.ts'), { recursive: true })
    writeFileSync(join(tmp, 'top.spec.ts'), 'test("top", () => {})\n')
    writeFileSync(join(tmp, 'nested', 'deep.spec.ts'), 'test("deep", () => {})\n')
    writeFileSync(join(tmp, '__snapshots__', 'anchors.spec.ts', 'a.png'), '不是 spec\n')
    writeFileSync(join(tmp, '__snapshots__', 'ghost.spec.ts'), 'test("ghost", () => {})\n')
    expect(
      listSpecFiles(tmp),
      '递归遍历退化成一列了——子目录里的 spec 会同时逃过本条扫描与"钉住集合"那条断言（复核 I-3 第 3 处）',
    ).toEqual(['nested/deep.spec.ts', 'top.spec.ts'])
  } finally {
    rmSync(tmp, { recursive: true, force: true })
  }

  // —— 真实清单必须为空（这就是"新增一个绿的正例"跟着仓库跑的那一半）——
  const dir = join(process.cwd(), 'e2e/visual')
  const real = listSpecFiles(dir).map((f) => ({
    file: f,
    text: readFileSync(join(dir, f), 'utf8'),
  }))
  expect(findThresholdLiterals(real), '本仓五个 spec 的代码面出现阈值字面量').toEqual([])

  // —— 分口径读数入库：同一路判据**不剥注释**在本文件必有色 ——
  // 这一条就是"为什么非剥注释不可"的实物证据。散文命中数随措辞增减，所以钉"至少 1 条"，
  // 具体数写进红话；哪天降到 0，说明剥注释这一层已经没东西可拦，口径要重写而不是静默留着。
  const selfRaw = readFileSync(join(dir, 'helpers.spec.ts'), 'utf8')
  const unstripped = selfRaw
    .split('\n')
    .filter((l) => /(?:threshold|maxDiffPixelRatio)\s*:\s*[\d.]/.test(l))
  expect(
    unstripped.length,
    `不剥注释时本文件的散文命中已为 ${unstripped.length} 条（91f541b 态实测是 5 条）——剥注释这层失去要拦的对象，本条口径要重定`,
  ).toBeGreaterThan(0)
})

/**
 * 4c-fix-C 第 12 项另一半：`--update-snapshots` 那道门（`global-setup.ts` 默认导出的第一条语句）
 * **原先没有自动化测试**——把那句调用删掉，全部套件照样绿，22 张已提交基线从此可以被一次
 * "红了就重生成"洗掉。本条把两半都钉上：
 * ① **调用点**（源码级）：它必须是默认导出体里的**第一条语句**且**不带实参**
 *   （带实参就等于生产路径不再读 `process.argv`，门形同虚设）。删掉那行 ⇒ ① 红，这就是题面要的"能红"。
 * ② **牙**（行为级）：三种写法都拦得住，`PW_ALLOW_UPDATE_SNAPSHOTS=1` 才放行，
 *   不带旗标的正常跑测不被误伤。
 * 【为什么不做成计划里提的 `spawnSync` 形态——如实记这条取舍】任何 spawnSync 要真验这道门，
 * 就得把 `--update-snapshots` 交给 runner；而在"门已被删掉"这个**待验场景**下，那一跑会当场重写
 * （或按 `-g` 过滤后 prune 掉）22 张基线——围栏明令**永不**跑该旗标，所以 spawnSync 形态在本仓
 * 是"验它的代价就是它的破坏"。② 因此走进程内调用：只喂合成 argv 与一个环境变量的副本，
 * `finally` 原样还原，Playwright 从不收到那个旗标（本条自己一次都没跑过 `--update-snapshots`）。
 * 判据的另一半早就有自动化了：基线 sha256 清单那条用例——门被绕过时它兜底变红。
 */
test('--update-snapshots 闸门（第 12 项）：调用点是 globalSetup 第一条语句，且三种写法都拦得住', () => {
  // ① 调用点：删掉这句、或给它塞实参（生产路径就不再读 process.argv）都让本条红
  const setupRaw = stripCommentLines(
    readFileSync(join(process.cwd(), 'e2e/visual/global-setup.ts'), 'utf8'),
  )
  expect(
    setupRaw,
    'globalSetup 的第一条语句不再是 assertUpdateSnapshotsAllowed()——这道门又回到"只有手工证明过"的状态（第 12 项）',
  ).toMatch(/export default async function globalSetup\(\) \{\s*assertUpdateSnapshotsAllowed\(\)/)
  // ② 牙：三种写法各钉一条，报错文案点名"拒绝了"
  const base = [
    '/node/bin/node',
    '/repo/frontend/node_modules/.bin/playwright',
    'test',
    '-c',
    'playwright.visual.ts',
  ]
  const envRaw = process.env.PW_ALLOW_UPDATE_SNAPSHOTS
  try {
    expect(
      () => assertUpdateSnapshotsAllowed([...base, '--update-snapshots']),
      '长写法没被拦——门对最常见的拼法失效',
    ).toThrow(/拒绝了 --update-snapshots/)
    expect(
      () => assertUpdateSnapshotsAllowed([...base, '-u']),
      '短写法 -u 没被拦——只认长写法的门等于没门',
    ).toThrow(/拒绝了/)
    expect(
      () => assertUpdateSnapshotsAllowed([...base, '--update']),
      '缩写 --update 没被拦——同上，三种写法是本闸的口径',
    ).toThrow(/拒绝了/)
    // 不带任何 update 旗标的正常跑测必须一声不响地过（误伤会让每一次跑测都红）
    expect(() => assertUpdateSnapshotsAllowed([...base])).not.toThrow()
    // 显式承认才放行
    process.env.PW_ALLOW_UPDATE_SNAPSHOTS = '1'
    expect(
      () => assertUpdateSnapshotsAllowed([...base, '--update-snapshots']),
      'PW_ALLOW_UPDATE_SNAPSHOTS=1 仍被抛——放行通道断了，只会逼人绕过这道门',
    ).not.toThrow()
    expect(
      () => assertUpdateSnapshotsAllowed([...base, '-u']),
      '承认过的值下 -u 仍被抛——放行口径与拦的口径不一致',
    ).not.toThrow()
  } finally {
    if (envRaw === undefined) delete process.env.PW_ALLOW_UPDATE_SNAPSHOTS
    else process.env.PW_ALLOW_UPDATE_SNAPSHOTS = envRaw
  }
})

/**
 * 22 张基线的 sha256 清单（Step 1c 的"篡改痕迹"）。
 * 症状设计成"基线被换掉即红"——包括被 `--update-snapshots` 换掉、也包括被别的代理覆盖掉。
 * 重算清单（改基线时必须同步这里，且要逐张复核新图）：
 *   cd frontend && node e2e/visual/tools/measure-diff.cjs --manifest e2e/visual/__snapshots__/anchors.spec.ts --json
 * 张数写成 22 而不是 24：`error-404` 两张按缺陷 D-1（错误路由从不写 data-theme）拒收，
 * 引用这个数一律写 22/24 并带原因。
 */
const BASELINE_SHA256: Record<string, string> = {
  'admin-courses-table-dark.png':
    '4fceb28bd3390934207b32576f35b7d1113d403d3f36f758d30e0992351f113d',
  'admin-courses-table-light.png':
    'a16b22e487a6390833bc115e8176d6eb6f87439e1e27c83d19a25e1b0122e517',
  'admin-dashboard-dark.png': 'd8162bbfaf8c40074592b4eda13bc212cd81b920727b1c03306b60265a72e985',
  'admin-dashboard-light.png': 'f97e461499d12b40345443ca8fe5ad2e9f3479f81688352021caebfe59ee2baf',
  'admin-governance-analytics-dark.png':
    'fdcf4eca3fd2a3f77789f673e882f51192283b662e35753ef36abb2bf26cde1a',
  'admin-governance-analytics-light.png':
    'd0a57821fc4ebcde8481f8a1f1f8bac35e13d99c614a2a6b37a6430e11a35ae7',
  'admin-profile-dark.png': 'd3838b8d9582abadc639792e32b80420d6446affd39fdd47f6e1faf842292ead',
  'admin-profile-light.png': '5deaacad8f257fcb651cb8a56faa35aad0e83dafdd6c2b4cffd4dd5782de1e68',
  'admin-system-form-dark.png': '5719dc5155c4c0e5171b2a44e3f35fca86fbbce8a6381fb6b1f9db784636a76a',
  'admin-system-form-light.png': 'f2924fa041f6a3a435445e337995a297808e62c2d9f3257904930127bdc35d1c',
  'login-dark.png': 'bae3de236ef5afcf99ce00a17b95011b945e6f22224982f1a90e67abb74c72e4',
  'login-light.png': 'aa253c2a88df0586be3ded71a824678f7804fe07a69ec2d4b3912238697a6b2d',
  'student-courses-modal-dark.png':
    '737739917fe742e17aef3706e3ec88f0fe386c73d7fb83939891b5212f695038',
  'student-courses-modal-light.png':
    'b6a9882cdb6bf55af9a711fb0b7b4cbc83ff7e716be550ceed86fa5aa5673263',
  'student-dashboard-dark.png': 'f45ad39a511257101bef54cfddb12a4ab41b3867c748d4fe36913457a717b7db',
  'student-dashboard-light.png': '3b09fb5563abf9308b829875ddd13d46158ebfb06b53baf27a45353f4b1c259f',
  'student-knowledge-graph-dark.png':
    '787954e0647d4cfb0bf58fc2b0b2693555d19380772c8f7ce14809693aa006aa',
  'student-knowledge-graph-light.png':
    '35e7caf803ca83f70c23d2a08372019883495770b603769234fafd4bde548c84',
  'student-learning-center-dark.png':
    '88cce625da003e8be1f6abf27d6d541dcac54ceb6071104a4db79805fc33e790',
  'student-learning-center-light.png':
    '5ac5cda3d46030111d3358f216a05ea6838a7f86c02abb460ceb7183c7731b62',
  'teacher-dashboard-dark.png': '76be1c8a6b0333abab02e04427b2d45ef6576f45ef75c48c4a0fc020d7f8f1bf',
  'teacher-dashboard-light.png': '7f44ad35146c4ebe438814245a290b70fe448cba607ca41a68d9a57adec42a01',
}

test('22 张已提交基线的 sha256 与入库清单一致（篡改/重生成即红）', () => {
  const dir = join(process.cwd(), 'e2e/visual/__snapshots__/anchors.spec.ts')
  const onDisk = readdirSync(dir)
    .filter((f) => f.toLowerCase().endsWith('.png'))
    .sort()
  expect(onDisk.length, '基线张数变了：22 是"12 锚点 × 明暗 − error 两张（D-1 拒收）"').toBe(22)
  expect([...Object.keys(BASELINE_SHA256)].sort()).toEqual(onDisk)
  for (const f of onDisk) {
    const digest = createHash('sha256')
      .update(readFileSync(join(dir, f)))
      .digest('hex')
    expect(digest, `${f} 与入库清单不符——若这是有意的重生成，必须逐张复核后连同清单一起改`).toBe(
      BASELINE_SHA256[f],
    )
  }
})

/**
 * B8：把 Phase 2 全部余量的**分母**钉住。
 *
 * 【原先缺什么】这一族算术在这个仓里被引用了十几次——`129.6`（当前档）、`648`（r=0.0005 那几轮）、
 * `2592`（原生产配置），spec §6.12/§6.17 与 `visual-helpers.ts` 的量程注释都靠它把"计数"折算成"几倍余量"。
 * 但**没有任何一条断言**读过基线的 IHDR，也没有任何一条算式被跑过：
 * `1440×900` 今天只是"真"（评审手读过 22 张）。这件事有两个方向会翻车：
 *   ① 某页一旦**真的能滚**（Phase 4 的结构收敛最容易碰），fullPage 截图就变长，
 *      免检额随**期望图**面积一起涨 ⇒ 同一个 198 px 的圆角改动从 1.53× 掉到 0.46× —— **绿得没意义**
 *      （spec §6.12 末把它与"只有实际页变高、期望图没变 ⇒ sizesMismatchError 直接红"分开说过，
 *       一条是变瞎、一条是变红，别混）。
 *   ② `maxDiffPixelRatio` 一旦改动，注释里所有"几倍余量"当场作废，而没有任何东西会提醒。
 * 【这一条同时钉两件事】(a) 每张已提交基线的宽高；(b) `w·h·r` 重算出来正是注释引用的那三个数。
 *      r **从 `SNAPSHOT_THRESHOLDS` 读**，不在这重写一遍 `0.0001`——收紧它必须让这条红、
 *      逼着人去把注释与台账里的数一起改，而不是让注释静默失效。
 * 【比较口径】判据是 `count > expected.width * expected.height * ratio`（**无 ceil、无取整**，
 *   `playwright-core/lib/coreBundle.js:7903` 取的是**期望图**的尺寸、`:7910` 才是那道 `>` 比较；
 *   本机安装的是 playwright-core 1.63.0）。所以"免检额 130 px"是 129.6 的**向上**简写：
 *   计数恰好 130 的那一格是**红**的。下面把方向也钉住，免得哪天有人拿"130 免检"当判据。
 * 【为什么复用 `pngSize` 而不是再写一个读头函数】这仓已经有**两把**会读 PNG 头的东西
 *   （`tools/measure-diff.cjs` 的 chunk 扫描、`sensitivity.spec.ts` 的私有 `pngSize`）。
 *   第三份副本一旦写出，"免检额"就有三个可能不一致的来源；`pngSize` 已上收到 `visual-helpers.ts`，
 *   两边同一份（它只读 8 字节头、不还原像素，判据仍然只有那把尺）。
 * 【与 sha256 清单那条的分工】上面那条管"图没被换过"，本条管"图**长多大**"。
 *   `error-404` 那两张（缺陷 D-1）哪天补齐进清单时，本条会跟着多读两张——**这是有意的**：
 *   新增基线的尺寸同样要落进这条分母，不能只在清单里多个名字。
 */
test('每张已提交基线都是 1440×900，且免检额算式与注释里的引用一致', () => {
  const dir = join(process.cwd(), 'e2e/visual/__snapshots__/anchors.spec.ts')
  const ratio = SNAPSHOT_THRESHOLDS.maxDiffPixelRatio

  for (const f of Object.keys(BASELINE_SHA256).sort()) {
    const { width, height } = pngSize(join(dir, f))
    expect(
      `${width}x${height}`,
      `${f} 的尺寸不再是 1440x900 ⇒ 免检额的分母变了（这一页真能滚？还是视口配置被动过？）`,
    ).toBe('1440x900')
    expect(
      width * height * ratio,
      `${f} 自己的免检额与注释引用的 129.6 不符（w·h·r = ${width}·${height}·${ratio}）`,
    ).toBeCloseTo(129.6, 6)
  }

  // ② 算术本身：注释里引用过的三个数必须由同一个分母重算出来。
  const IMAGE_PX = 1440 * 900
  expect(IMAGE_PX * ratio).toBeCloseTo(129.6, 6) // 当前交付档（r=0.0001）
  expect(IMAGE_PX * 0.0005).toBe(648) // Task 4b/4c Step 1 那几轮的交付档
  expect(IMAGE_PX * 0.002).toBe(2592) // 原生产配置（spec §6.12 判它作废的那一档）

  // ③ 方向：129.6 不是整数，"免检额 130"只是向上简写 ⇒ 计数 130 必须算红。
  expect(
    Number.isInteger(IMAGE_PX * ratio),
    '免检额一旦变成整数，"130 px"这个简写与判据的关系就要重写',
  ).toBe(false)
  expect(130, '130 不再大于免检额 ⇒ 注释里"免检额 130 px"那类写法全部要按新数重抄').toBeGreaterThan(
    IMAGE_PX * ratio,
  )
})

/**
 * 比较器的解码回归（Task 4c Step 1g 的尺本身）。
 * 为什么这条必须存在：本机第一次实现 `measure-diff.cjs` 时把 Average 过滤写成
 * `line[x] + (a + b) >> 1`（JS 里 `+` 比 `>>` 结合更紧 ⇒ 少一层括号就是另一种算法），
 * 症状是凡 Chromium 用 Average 过滤的行全部解错，radius 档计数从跑测器的 1394 漂到 2257。
 * **同一张图的两种过滤编码必须解成同一样东西**——这条不变式一断，红就在这里，
 * 而不是等某个 LEDGER 数字与跑测器对不上才发现。
 *
 * 【这条断言自己被证伪过吗？证伪脚本已入库，一条命令可重放】
 *   cd frontend && node e2e/visual/tools/kat-falsification.cjs
 *   好尺：KAT 对 byteDiffPx 0；radius 计数 1,394 / 字节差 1,983。
 *   把注释点名那个括号错误注入一份坏尺副本：**KAT 对 byteDiffPx = 696 ⇒ 这条断言会红**；
 *   同一把坏尺上 radius 计数 = **2,257**（与跑测器的 1,394 差 863）⇒ 本注释里那个历史数**可重放**，
 *   不再是转述。脚本还证了反方向：**不是所有"少写括号"都会变**——把括号挪到 `avg` 外面那一版
 *   与正确写法**语义相同**（KAT 0、radius 1,394），所以"少个括号就一定错"这种说法本身也不成立。
 *   另注：跑测器自己的 `[探针无效] 尺不同源` 断言（`sensitivity.spec.ts`）才是日常的门，
 *   本用例是"没有注入可跑时也能拦住"的那道。
 */
test('measure-diff 的 PNG 解码与过滤类型无关（同一张图 filter0 与 filter3 必须解成同一份 RGBA）', () => {
  const dir = join(process.cwd(), 'e2e/visual', 'tools', 'evidence')
  mkdirSync(dir, { recursive: true })
  const a = join(dir, 'kat-filter0.png')
  const b = join(dir, 'kat-filter3.png')
  const w = 41
  const h = 17
  writeFileSync(a, encodePng(w, h, 2, 0))
  writeFileSync(b, encodePng(w, h, 2, 3))
  expect(md5(a)).not.toBe(md5(b)) // 两种编码的字节确实不同，否则这条用例什么都没测
  const out = execFileSync(
    process.execPath,
    [join(process.cwd(), 'e2e/visual/tools/measure-diff.cjs'), a, b, '--json'],
    {
      encoding: 'utf8',
    },
  )
  const reading = JSON.parse(out) as { byteDiffPx: number; pixelmatchCount: number }
  expect(reading.byteDiffPx, '同一张图换了过滤类型就解成不同像素——比较器的 unfilter 有 bug').toBe(0)
  expect(reading.pixelmatchCount).toBe(0)
})

/**
 * Task 4c-fix-A A2：取证账本（`probe-results.json`）的**写入语义**。
 * 【原先错在哪】`all[key] = {...旧格, ...新}`——只在"这一格没测成"时才该存在的字段
 * （`skipped:true` + `reason`）会活到后一条同格读数上，反向同理（`pixelmatchCount`/`verdict`
 * 会活到一条 skipped 记录上）。`890e3c0` 给 key 打上 `@nopin`/`@shift+k` 之后**跨口径**的覆盖没了，
 * 但**同一个 key 跨轮次**的字段残留还在：读账本的人要么把量到了的一格当成没量到，
 * 要么把没测成的一格当成有读数——两种都是"会被误信的读数"。
 * 【为什么这条断言放在这里而不是靠探针跑】这个残留要在"同一格先 SKIPPED 再量成功"
 * 或反序时才现形，而 `login` × `.l1-card` 系档位的 SKIPPED 是由页面结构决定的，
 * 本仓当前**造不出**那个转换（要改 CSS 作用域或改锚点名单）⇒ 它是**潜伏缺陷**，
 * 盘上也没有现成反例。潜伏的东西只能用纯函数断言钉住，所以 `applyEvidenceWrite` 放在 helper 里、
 * 探针只薄薄一层落盘包装。**如实记：这条不是在描述一次实测到的事故，是在描述一条已被消除的能力。**
 */
test('取证账本默认整格替换（不留上一轮的 skipped 残渣），只有本轮第二笔才 merge', () => {
  const key = 'login/light/inset'
  const cell = (all: Record<string, unknown>): Record<string, unknown> =>
    all[key] as Record<string, unknown>

  // 第一笔：某轮把这格判成 SKIPPED
  const skipped = applyEvidenceWrite({}, key, {
    slug: 'login',
    theme: 'light',
    ladder: 'inset',
    skipped: true,
    reason: '选择器零命中，注入是空操作',
  })
  expect(cell(skipped).skipped).toBe(true)

  // 第二笔：另一轮同一格真量出了读数——上一轮的 skipped/reason 必须**不能**活下来
  const fresh = applyEvidenceWrite(skipped, key, {
    slug: 'login',
    theme: 'light',
    ladder: 'inset',
    pixelmatchCount: 12,
    byteDiffPx: 40,
  })
  expect(cell(fresh).pixelmatchCount, '新读数的字段没写进去').toBe(12)
  expect(
    cell(fresh).skipped,
    '上一轮的 skipped 活到了这一轮的读数上：读账本会把量到的一格当成没量到',
  ).toBeUndefined()
  expect(cell(fresh).reason).toBeUndefined()
  expect(cell(fresh).key).toBe(key)

  // 第三笔：**本轮**的判定笔仍必须 merge 到本轮第一笔的读数上（读数先落盘、判定后落盘是刻意的）
  const verdict = applyEvidenceWrite(
    fresh,
    key,
    { verdict: '检出', detected: true, playwrightCount: 12 },
    true,
  )
  expect(cell(verdict).pixelmatchCount, 'merge 语义丢了——判定那一笔会把本轮刚写的读数覆盖掉').toBe(
    12,
  )
  expect(cell(verdict).verdict).toBe('检出')
  expect(cell(verdict).skipped, 'merge 只许用于本轮第二笔，别把历史残渣又带回来').toBeUndefined()

  // 兄弟格不受影响（替换是"这一格"的替换，不是重写整本账）
  const siblingKey = 'admin-dashboard/light/shadow'
  const withSibling = applyEvidenceWrite(verdict, siblingKey, {
    slug: 'admin-dashboard',
    theme: 'light',
    ladder: 'shadow',
    pixelmatchCount: 55903,
  })
  expect(cell(withSibling).pixelmatchCount).toBe(12)
  expect((withSibling[siblingKey] as Record<string, unknown>).pixelmatchCount).toBe(55903)
})

/**
 * Task 4c-fix-A A3 → 4c-fix-C 第 11 项：`PW_VISUAL_NOPIN` 的**取法只有一个来源**，而 A3 的分工仍是两道门。
 * 【A3 那一版为什么必须改写】A3 交付时 anchors 自己现读 env（`process.env[NOPIN_ENV]` ＋ `nopinRaw !== undefined`
 * 那道抛），探针自己写 `=== '1'`——本文件旧断言逐字钉着那两个写法。第 11 项把取法收进 `visual-helpers.ts`
 * 的 `readNopinEnv()` 之后，那两个写法在两个消费者里都不存在了 ⇒ 旧断言的两条 anchors 侧判据**必然红**
 * （续棒棒 2026-10-21 实跑确认：`helpers` 由 17 passed 变 16 passed／1 failed，红在 `toContain` 那一行
 * ——它同时是"第 11 项写完没跑过 helpers 套件"的直接证据）。
 * 旧断言第三条（探针必须含 `process.env.PW_VISUAL_NOPIN === '1'`）改后**仍然绿，但绿在注释上**：
 * 那句话只剩 `sensitivity.spec.ts` 里解释历史的注释，源码里已经没有了 ⇒ 它守的是已被换掉的实现，属假保护。
 * 【A3 的分工没有作废】anchors 读到"开"必须**拒绝运行**（认了这个开关会让 `assertZeroDiff` 整条消失，
 * 见 A3 与第 11 项两处注释块）；探针读到"开"才做**反向对照**。作废的只是"各自现读 env"这一件事。
 * 【本条守四件事】① 两个消费者都只能经 `readNopinEnv()` 取值；② 谁都不许再自读那个开关——**四种拼法
 * 一条正则全拦**（点号、`[NOPIN_ENV]`、`["PW_VISUAL_NOPIN"]`、`[ 'PW_VISUAL_NOPIN' ]`；F1 之前只认前两种，
 * **排除整行注释**：两份文件都留着解释这段历史的注释、里面就写着那个变量名，不排除会把守卫自己弄成假红）；
 * ③ anchors 的导入期抛仍在原位；
 * ④ 探针那条"读到开就不装钉桩"的分支仍在原位（否则 anchors 的报错把人指向一条同样无效的命令）。
 * ⚠️ ①–④ 是**源码文本**级；行为级证据是四条导入期跑测（`--list` 不启动浏览器、不写文件）：
 * `PW_VISUAL_NOPIN=true` ⇒ anchors 与 sensitivity **都**抛（第 11 项交付物本体），
 * `=1` ⇒ anchors 抛、探针那条例向命令正常列出用例。命令与读数见报告第 11 节。
 */
test('NOPIN 取法单一来源（第 11 项）：两个消费者只经 readNopinEnv，分工两道门仍在', () => {
  const anchors = stripCommentLines(
    readFileSync(join(process.cwd(), 'e2e/visual/anchors.spec.ts'), 'utf8'),
  )
  const probe = stripCommentLines(
    readFileSync(join(process.cwd(), 'e2e/visual/sensitivity.spec.ts'), 'utf8'),
  )
  // ① 取值入口：两处都必须是共享函数，而不是各自现读
  expect(
    anchors,
    'anchors 又不通过 readNopinEnv() 取这个开关了——第 11 项的统一取法被绕开',
  ).toContain('readNopinEnv()')
  expect(probe, '探针又不通过 readNopinEnv() 取这个开关了——同一道门少了一个消费者').toContain(
    'readNopinEnv()',
  )
  // ② 负向半边就是本轮改掉的那个毛病本身：代码面再出现自读 env 的写法。
  //   【F1（复核 I-2）】原先这里是**两条判据、四种拼法只认两种**（点号与 `[NOPIN_ENV]`），
  //   方括号加引号那种写法当场溜过 ⇒ 换成 `SELF_READ_NOPIN_ENV` 一把尺，尺的本体与它的反例见上一条用例。
  expect(
    anchors,
    'anchors 里又出现自读 NOPIN 开关的写法（四种拼法之一）：单一来源破了，两个消费者的力度会重新分岔',
  ).not.toMatch(SELF_READ_NOPIN_ENV)
  expect(
    probe,
    '探针里又出现自读 NOPIN 开关的写法（四种拼法之一）：`=== 1` 那一版会让 true 与 yes 被静默忽略（第 11 项题面）',
  ).not.toMatch(SELF_READ_NOPIN_ENV)
  // ③ 分工·anchors 侧：取到"开"必须是为了在导入期抛，不是为了接受
  expect(
    anchors,
    'anchors 的 `if (readNopinEnv()) throw` 被删了——静默接受一个本套件不实现的开关 = 假反向对照（A3）',
  ).toMatch(/if \(readNopinEnv\(\)\) \{\s*throw new Error/)
  // ④ 分工·探针侧：读到"开"才做反向对照，所以那条不装钉桩的分支必须还在
  expect(
    probe,
    '探针那边的 !nopin 分支没了——开关在探针侧不再起作用，而 anchors 的报错还把人往那儿指',
  ).toMatch(/pinPaths\?\.length && !nopin/)
})

/**
 * C1 修复轮 1 的 F1（复核 I-2）：上面那把尺**自己的反例**，入库跟着仓库跑。
 * 【原先缺什么】负向半边原先只认两种拼法，复核席构造 `process.env["PW_VISUAL_NOPIN"]` 一条就把它旁路掉了
 * （四条断言全绿）。"第三种写法能溜"这件事过去只有未入 git 的过程记录能证，
 * 本条把它变成每次跑测都重做一次断言的内容。
 * 【两侧都钉】漏拦＝静默旁路，所以四种拼法**都**必须命中；误伤＝守卫假红，所以两种合法写法**都不许**命中。
 * 【这里为什么允许出现被禁的写法】本条喂的是**合成字符串**，不是任何 spec 的源码；
 * 上一条用例读的只有 `anchors.spec.ts` 与 `sensitivity.spec.ts` 两个文件（`visual-helpers.ts` 里
 * 那两处是合法定义，扩进去就误伤）。**若将来把该守卫的作用域扩到扫遍所有 spec，本文件要与
 * `visual-helpers.ts` 同列豁免**，否则这八行合成样本会当场变成假红——这是设计，不是疏忽。
 */
test('自读 NOPIN 那把尺覆盖四种拼法、且不误伤两种合法写法（F1 反例入库）', () => {
  // 命中侧：四种拼法各一条。前两条是旧守卫本来就认的，后两条是旧守卫溜过去的那一族。
  for (const spelling of [
    'const nopin = process.env.PW_VISUAL_NOPIN === "1"',
    'const nopin = !!process.env[NOPIN_ENV]',
    'const legacy = process.env["PW_VISUAL_NOPIN"] === "1" ? false : nopin',
    "const spaced = process.env[ 'PW_VISUAL_NOPIN' ]",
  ]) {
    expect(selfReadsNopinEnv(spelling), `${spelling} 没被拦住——负向守卫又开了一条静默旁路`).toBe(
      true,
    )
  }
  // 不命中侧：另一个开关（录制模式）与唯一合法取法，都是本仓真实存在的写法。
  for (const legal of [
    'const recording = process.env.PW_RECORD_PAYLOADS === "1"',
    'const nopin = readNopinEnv()',
  ]) {
    expect(selfReadsNopinEnv(legal), `${legal} 是合法写法却被拦——守卫在这里会假红`).toBe(false)
  }
})

/**
 * 4c-fix-C 第 11 项：`parseNopinValue` 抽成纯函数的**唯一理由**就是"能被本文件直接弄红"
 * （见 visual-helpers.ts 里那句原话）。所以这里逐个值断言，不用一条 `toBeTruthy` 糊过去——
 * 糊过去的话，"某几个非法值被放行"这种回归正好落在盲区里，而那是 A3 清除过的那类假读数。
 * 合法语义只有三种：未设置／空串／`0` 判关；`1` 判开；其余抛。
 */
test('parseNopinValue：三种合法语义钉死，非法值逐个断言会抛（第 11 项纯函数面）', () => {
  expect(parseNopinValue(undefined), '未设置必须判"关"——这是默认交付路径').toBe(false)
  expect(parseNopinValue(''), '空串必须判"关"（A3 那版也放行空串，语义未变）').toBe(false)
  expect(parseNopinValue('0'), '显式 0 必须判"关"（关掉开关的合法写法）').toBe(false)
  expect(parseNopinValue('1'), '1 必须判"开"（探针的反向对照档）').toBe(true)
  // 非法值：**每个单独一条**，钉的是"抛"这件事，不是"返回了个假值"
  expect(
    () => parseNopinValue('true'),
    'true 被静默读成"没开"——第 11 项题面那个分裂的原样复发',
  ).toThrow(/非法值/)
  expect(() => parseNopinValue('yes'), 'yes 被静默读成"没开"——同上，非法值一律 fail-fast').toThrow(
    /非法值/,
  )
  expect(
    () => parseNopinValue('2'),
    '2 被静默读成"没开"——同上；1 之外的数字也算拼错的开关',
  ).toThrow(/非法值/)
})

/**
 * 第 11 项：`readNopinEnv()` 必须是"读 env 后走同一解析"，不许自己再判一遍。
 * 上面那条钉解析本身，这条钉**接线**——把 `readNopinEnv` 改回 `process.env.PW_VISUAL_NOPIN === '1'`
 * 会让这条红（而 `parseNopinValue` 的单元测试仍绿），那正是"两个消费者力度不一样"能悄悄回来的路径。
 * 本条改的是**本进程**的 env，所以在 `finally` 里原样还原；各 spec 的取值发生在导入期（早已取完），
 * 本文件的用例不读这个开关，所以互不影响。
 */
test('readNopinEnv 的 env 面与纯函数面同一条口径（接线断言，跑完原样还原 env）', () => {
  const raw = process.env.PW_VISUAL_NOPIN
  try {
    delete process.env.PW_VISUAL_NOPIN
    expect(readNopinEnv(), 'env 未设置时 readNopinEnv 必须判"关"').toBe(false)
    process.env.PW_VISUAL_NOPIN = '0'
    expect(readNopinEnv(), 'env 为 0 时 readNopinEnv 必须判"关"').toBe(false)
    process.env.PW_VISUAL_NOPIN = '1'
    expect(readNopinEnv(), 'env 为 1 时 readNopinEnv 必须判"开"').toBe(true)
    process.env.PW_VISUAL_NOPIN = 'true'
    expect(
      () => readNopinEnv(),
      'env 为 true 时没抛——readNopinEnv 绕开了 parseNopinValue，单一来源名存实亡',
    ).toThrow(/非法值/)
  } finally {
    if (raw === undefined) delete process.env.PW_VISUAL_NOPIN
    else process.env.PW_VISUAL_NOPIN = raw
  }
})

/**
 * Task 4c-fix-A A4：**基线日必须是个被断言的事实，不是一句注释**。
 * 【原先错在哪】录制端把 fixture 的 ISO 日期整体回拨到 `CLOCK_DATE`（由 `CLOCK_EPOCH` 反推），
 * 于是"钉住后与 22 张基线 md5 相等"这条判据隐含依赖"**基线拍于 CLOCK_EPOCH 那一天**"——
 * 这句话原先只活在注释里，没有任何东西断言它。前提一旦破（比如有人挪 CLOCK_EPOCH），
 * 4 张日期图**永久红**，而且重录也救不回来（重录归一到新的那天，基线还是旧的），
 * 归因方向会被带去查"产品改了图表"。
 * 【这一条钉住三件事】① 冻结时钟那一天 == 基线日（正向 + 与导入期那条抛同源）；
 * ② 两份入库 fixture 自己声明的 clockDate == 基线日；③ backshiftDays 与自身日期自洽。
 * ②③ 用真文件跑正向、用内存对象跑反向——反向不能改 `__fixtures__`，那是入库产物。
 */
test('基线日是断言不是假设：时钟日、fixture 声明日、回拨量三者必须互洽', () => {
  // ① 正向。导入期那条抛（visual-helpers.ts 的 CLOCK_DATE !== BASELINE_DAY）与本行同源，
  //   差别是这里红在 helpers，那条红在**任何**套件导入时——所以 mutation 只需手工做一次（见报告 A4）。
  expect(CLOCK_DATE, '冻结时钟的那一天不再是基线日 ⇒ 日期锚点的 md5 判据永远不可能成立').toBe(
    BASELINE_DAY,
  )

  // ②③ 正向：两份入库 fixture 必须原样通过（这条就是"手改 fixture 想让 md5 对上"的拦截网）
  for (const slug of ['student-dashboard', 'admin-governance-analytics']) {
    expect(() => loadPinManifest(slug), `${slug}.json 没过日期自洽检查`).not.toThrow()
    const m = loadPinManifest(slug)
    expect(m.clockDate).toBe(BASELINE_DAY)
  }

  // ② 反向：clockDate 指到别的日子必须抛（这一格红 = 基线日与 fixture 脱节）
  const badDay = { serverDateAtRecord: '2026-10-19', clockDate: '2026-10-17', backshiftDays: 2 }
  let threwDay = ''
  try {
    assertManifestDates(badDay, '内存里的一份坏 manifest')
  } catch (e) {
    threwDay = (e as Error).message
  }
  expect(threwDay, 'clockDate 与 BASELINE_DAY 不符却放行了——"永久红"那个盲区又回来了').toContain(
    'clockDate',
  )

  // ③ 反向：只挪 backshiftDays（手改 fixture 最常见的形状）也必须抛
  const badShift = { serverDateAtRecord: '2026-10-19', clockDate: BASELINE_DAY, backshiftDays: 5 }
  let threwShift = ''
  try {
    assertManifestDates(badShift, '内存里的一份坏 manifest')
  } catch (e) {
    threwShift = (e as Error).message
  }
  expect(
    threwShift,
    'backshiftDays 与自身日期不符却放行了——这是"只挪 clockDate 想让 md5 对上"的那条路',
  ).toContain('backshiftDays')

  // 顺带钉住自洽算法本身：正例的 implied 必须真等于 fixture 里那个数
  const ok = { serverDateAtRecord: '2026-10-20', clockDate: BASELINE_DAY, backshiftDays: 2 }
  expect(() => assertManifestDates(ok, '跨月自洽例')).not.toThrow()
})

/**
 * Task 4c-fix-C 第 9 项（R-CF1）：被钉载荷的**内容**断言必须有牙。
 * 【原先错在哪】两份入库 fixture 的载荷全 0（student-dashboard：durationTrend 14 × seconds:0，
 * radar 与 heatmap 皆空；admin-governance-analytics：growth 30 × 全 0），而各锚点的"数据到位"
 * 断言只看 DOM 存在性、不看被钉载荷 ⇒ "到位绿 ＋ 复现 md5 相等"被读成"分析数据到位了"。
 * 【这条断言钉四件事】① 普查口径只走 `data` 内部（信封上的 code:200 不算内容，否则永不成立）；
 * ② 合成反例：未登记的钉桩锚点吃到全 0 载荷必须抛（**反例喂合成样本**，不碰 `__fixtures__` 入库产物）；
 * ③ 已登记的两条豁免对真 fixture 不抛（豁免 = 措辞降级，由 assertPinnedPayloadContent 打日志）；
 * ④ 登记表与实测必须双向一致：fixture 重录出内容后忘删豁免 ⇒ 红；新钉桩锚点全 0 没登记 ⇒ 也红。
 */
test('被钉载荷内容断言（R-CF1）：普查只走 data、合成反例必红、豁免表与 fixture 必须互洽', () => {
  // ① 合成全 0（形状逐字照抄 fixture 被钉端点的样子）：序列长 14 但数值全 0 ⇒ 平凡
  const trivialBody = JSON.stringify({
    code: 200,
    message: 'success',
    data: {
      durationTrend: Array.from({ length: 14 }, (_, i) => ({
        date: `2026-10-${String(i + 5).padStart(2, '0')}`,
        seconds: 0,
      })),
      radarData: [],
      heatmapData: [],
    },
  })
  const v = inspectPinnedPayload(trivialBody, '合成·全0')
  expect(v.nonTrivial, '14 长序列全 seconds:0 竟被判非平凡——口径＝序列 >0 且至少一个数值非 0').toBe(
    false,
  )
  expect(v.maxSeqLen).toBe(14)
  expect(v.nonZeroCount, 'data 信封上的 code:200 被当成内容了——普查必须只走 data 内部').toBe(0)
  // 正例：同样形状里给一个非 0 数值 ⇒ 非平凡；data:null（矩阵已能拦的形状）也须判平凡
  expect(
    inspectPinnedPayload(
      JSON.stringify({ code: 200, data: [{ date: '2026-10-05', seconds: 120 }] }),
      '合成·有内容',
    ).nonTrivial,
  ).toBe(true)
  expect(
    inspectPinnedPayload(JSON.stringify({ code: 200, data: null }), '合成·data:null').nonTrivial,
  ).toBe(false)

  // ② 合成反例：一个**不在登记表里**的钉桩锚点吃到全 0 载荷必须抛
  const synthSlug = 'synthetic-new-pin'
  const synthAnchor = {
    slug: synthSlug,
    title: synthSlug,
    role: 'ADMIN',
    path: '/x',
    pinPaths: ['/api/synthetic'],
  } as AnchorSpec
  const synthManifest: PinManifest = {
    slug: synthSlug,
    recordedAt: '',
    serverDateAtRecord: BASELINE_DAY,
    clockDate: BASELINE_DAY,
    backshiftDays: 0,
    requests: {
      '/api/synthetic': { status: 200, contentType: 'application/json', body: trivialBody },
    },
  }
  let threw = ''
  try {
    assertPinnedPayloadContent(synthAnchor, synthManifest)
  } catch (e) {
    threw = (e as Error).message
  }
  expect(threw, '未登记的钉桩锚点吃到全 0 载荷却放行——内容断言没有牙').toContain('没有内容')

  // ③ 登记表里的两条对**入库 fixture** 必须不抛（豁免 = 放行但每次打结论降级日志）
  for (const slug of Object.keys(PIN_PAYLOAD_TRIVIAL_EXCUSED)) {
    const anchor = ANCHORS.find((a) => a.slug === slug)
    expect(anchor, `${slug} 在登记表里却不是已声明锚点——登记条目悬空`).toBeTruthy()
    expect(
      () => assertPinnedPayloadContent(anchor!, loadPinManifest(slug)),
      `${slug} 已登记豁免仍被抛——豁免语义被改坏`,
    ).not.toThrow()
  }

  // ④ 双向一致：当前实测全 0 的钉桩锚点集合 == 登记表集合
  //    （重录出内容却不删豁免 ⇒ trivialNow 少一条、红；新钉桩全 0 未登记 ⇒ trivialNow 多一条、红）
  const trivialNow = ANCHORS.filter((a) => (a.pinPaths?.length ?? 0) > 0)
    .filter((a) => {
      const m = loadPinManifest(a.slug)
      return (a.pinPaths ?? []).some(
        (k) =>
          m.requests[k] && !inspectPinnedPayload(m.requests[k].body, `${a.slug}/${k}`).nonTrivial,
      )
    })
    .map((a) => a.slug)
    .sort()
  expect(
    trivialNow,
    '登记表与实测全 0 集合漂开了（一侧变化必须同批改另一侧与 LEDGER P0a-GUARD-02）',
  ).toEqual(Object.keys(PIN_PAYLOAD_TRIVIAL_EXCUSED).sort())
})

/**
 * Task 4c-fix-C 第 10 项：record 模式的日志与跳过必须过"flushRecord 真的执行过"这一道门。
 * 【原先错在哪】A4 把 flush 提前之后，`已写成` 日志与像素跳过仍只按 `recording` 判——全部 12 个锚点
 * 都进那条分支，其中 10 个非钉桩锚点 `flushRecord` 为 null（`__fixtures__/` 一个字节都没写），
 * 于是全量录制跑打出十个"fixture 已写成"并顺手跳过非钉桩那些的像素比对。
 * 行为反证（本棒实跑，原文见报告第 10 节）：修前 `PW_RECORD_PAYLOADS=1 ... anchors -g "login"`
 * 打出 `login/light 已写成` ＋ `login/dark 已写成` 两条谎（login 没有 pinPaths，什么都没写）；
 * 修后同一条命令 0 条 `已写成`、且真的跑了像素比对。本条是防"那道门被改回去"的入库面。
 */
test('录制模式的"已写成"与像素跳过只认 flushRecord（第 10 项）', () => {
  const anchorsRaw = readFileSync(join(process.cwd(), 'e2e/visual/anchors.spec.ts'), 'utf8')
  // ① 落盘、日志、return 必须在同一个 `if (flushRecord) {` 分支里（顺序：flush → 日志 → return）
  expect(
    anchorsRaw,
    '录制模式的跳过又不再按 flushRecord 判了——十个假"已写成"与静默跳像素会原样回来（4c-fix-C 第 10 项）',
  ).toMatch(
    /if \(flushRecord\) \{\s*await flushRecord\(\)[\s\S]{0,600}?已写成[\s\S]{0,500}?return\s*\}/,
  )
  // ② 旧形态（按 recording 布尔提前 return）不许回来——负向半边就是本轮改掉的那个 bug 本身
  expect(
    anchorsRaw,
    '`if (recording) {` 式提前 return 回来了：那是"12 个锚点全进、10 个什么都没写"的同型错误',
  ).not.toMatch(/if \(recording\) \{/)
})
