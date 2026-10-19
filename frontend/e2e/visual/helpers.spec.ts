import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import zlib from 'node:zlib'
import {
  applyEvidenceWrite,
  freezeClock,
  maskOf,
  setTheme,
  settlePage,
  snapName,
  SNAPSHOT_PAGE_TIME_MS,
  SNAPSHOT_THRESHOLDS,
  storagePathFor,
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
  expect(SNAPSHOT_THRESHOLDS).toEqual({ threshold: 0, maxDiffPixelRatio: 0.0001 })
})

/**
 * 单来源的机械版本：两处消费方都必须**引用**这个常量，不许各自再写一份字面量。
 * 上一轮"量程测量用的阈值"和"回归用的阈值"不是同一组数，正是靠这个约定挡住的；
 * 散文挡不住并发代理（Task 4b 那次 00:43 的覆盖事故就是并发写的）。
 */
test('阈值只有一个来源，消费侧不写字面量', () => {
  const anchors = readFileSync(join(process.cwd(), 'e2e/visual/anchors.spec.ts'), 'utf8')
  const probe = readFileSync(join(process.cwd(), 'e2e/visual/sensitivity.spec.ts'), 'utf8')
  expect(anchors).toContain('...SNAPSHOT_THRESHOLDS')
  // anchors 侧任何 `threshold:` / `maxDiffPixelRatio:` 字面量都会让比较口径悄悄换掉
  expect(anchors).not.toMatch(/threshold:\s*[\d.]/)
  expect(anchors).not.toMatch(/maxDiffPixelRatio:\s*[\d.]/)
  // 探针的 fallback 必须指向同一常量；env 旋钮只许收紧（由 sensitivity.spec.ts 自己 fail fast）
  expect(probe).toContain('SNAPSHOT_THRESHOLDS.threshold')
  expect(probe).toContain('SNAPSHOT_THRESHOLDS.maxDiffPixelRatio')
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
  'admin-courses-table-dark.png': '4fceb28bd3390934207b32576f35b7d1113d403d3f36f758d30e0992351f113d',
  'admin-courses-table-light.png': 'a16b22e487a6390833bc115e8176d6eb6f87439e1e27c83d19a25e1b0122e517',
  'admin-dashboard-dark.png': 'd8162bbfaf8c40074592b4eda13bc212cd81b920727b1c03306b60265a72e985',
  'admin-dashboard-light.png': 'f97e461499d12b40345443ca8fe5ad2e9f3479f81688352021caebfe59ee2baf',
  'admin-governance-analytics-dark.png': 'fdcf4eca3fd2a3f77789f673e882f51192283b662e35753ef36abb2bf26cde1a',
  'admin-governance-analytics-light.png': 'd0a57821fc4ebcde8481f8a1f1f8bac35e13d99c614a2a6b37a6430e11a35ae7',
  'admin-profile-dark.png': 'd3838b8d9582abadc639792e32b80420d6446affd39fdd47f6e1faf842292ead',
  'admin-profile-light.png': '5deaacad8f257fcb651cb8a56faa35aad0e83dafdd6c2b4cffd4dd5782de1e68',
  'admin-system-form-dark.png': '5719dc5155c4c0e5171b2a44e3f35fca86fbbce8a6381fb6b1f9db784636a76a',
  'admin-system-form-light.png': 'f2924fa041f6a3a435445e337995a297808e62c2d9f3257904930127bdc35d1c',
  'login-dark.png': 'bae3de236ef5afcf99ce00a17b95011b945e6f22224982f1a90e67abb74c72e4',
  'login-light.png': 'aa253c2a88df0586be3ded71a824678f7804fe07a69ec2d4b3912238697a6b2d',
  'student-courses-modal-dark.png': '737739917fe742e17aef3706e3ec88f0fe386c73d7fb83939891b5212f695038',
  'student-courses-modal-light.png': 'b6a9882cdb6bf55af9a711fb0b7b4cbc83ff7e716be550ceed86fa5aa5673263',
  'student-dashboard-dark.png': 'f45ad39a511257101bef54cfddb12a4ab41b3867c748d4fe36913457a717b7db',
  'student-dashboard-light.png': '3b09fb5563abf9308b829875ddd13d46158ebfb06b53baf27a45353f4b1c259f',
  'student-knowledge-graph-dark.png': '787954e0647d4cfb0bf58fc2b0b2693555d19380772c8f7ce14809693aa006aa',
  'student-knowledge-graph-light.png': '35e7caf803ca83f70c23d2a08372019883495770b603769234fafd4bde548c84',
  'student-learning-center-dark.png': '88cce625da003e8be1f6abf27d6d541dcac54ceb6071104a4db79805fc33e790',
  'student-learning-center-light.png': '5ac5cda3d46030111d3358f216a05ea6838a7f86c02abb460ceb7183c7731b62',
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
    const digest = createHash('sha256').update(readFileSync(join(dir, f))).digest('hex')
    expect(digest, `${f} 与入库清单不符——若这是有意的重生成，必须逐张复核后连同清单一起改`).toBe(BASELINE_SHA256[f])
  }
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
  const out = execFileSync(process.execPath, [join(process.cwd(), 'e2e/visual/tools/measure-diff.cjs'), a, b, '--json'], {
    encoding: 'utf8',
  })
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
  const cell = (all: Record<string, unknown>): Record<string, unknown> => all[key] as Record<string, unknown>

  // 第一笔：某轮把这格判成 SKIPPED
  const skipped = applyEvidenceWrite({}, key, { slug: 'login', theme: 'light', ladder: 'inset', skipped: true, reason: '选择器零命中，注入是空操作' })
  expect(cell(skipped).skipped).toBe(true)

  // 第二笔：另一轮同一格真量出了读数——上一轮的 skipped/reason 必须**不能**活下来
  const fresh = applyEvidenceWrite(skipped, key, { slug: 'login', theme: 'light', ladder: 'inset', pixelmatchCount: 12, byteDiffPx: 40 })
  expect(cell(fresh).pixelmatchCount, '新读数的字段没写进去').toBe(12)
  expect(cell(fresh).skipped, '上一轮的 skipped 活到了这一轮的读数上：读账本会把量到的一格当成没量到').toBeUndefined()
  expect(cell(fresh).reason).toBeUndefined()
  expect(cell(fresh).key).toBe(key)

  // 第三笔：**本轮**的判定笔仍必须 merge 到本轮第一笔的读数上（读数先落盘、判定后落盘是刻意的）
  const verdict = applyEvidenceWrite(fresh, key, { verdict: '检出', detected: true, playwrightCount: 12 }, true)
  expect(cell(verdict).pixelmatchCount, 'merge 语义丢了——判定那一笔会把本轮刚写的读数覆盖掉').toBe(12)
  expect(cell(verdict).verdict).toBe('检出')
  expect(cell(verdict).skipped, 'merge 只许用于本轮第二笔，别把历史残渣又带回来').toBeUndefined()

  // 兄弟格不受影响（替换是"这一格"的替换，不是重写整本账）
  const siblingKey = 'admin-dashboard/light/shadow'
  const withSibling = applyEvidenceWrite(verdict, siblingKey, { slug: 'admin-dashboard', theme: 'light', ladder: 'shadow', pixelmatchCount: 55903 })
  expect(cell(withSibling).pixelmatchCount).toBe(12)
  expect((withSibling[siblingKey] as Record<string, unknown>).pixelmatchCount).toBe(55903)
})

