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
  BASELINE_DAY,
  CLOCK_DATE,
  freezeClock,
  FULLPAGE_MAX_HEIGHT,
  maskOf,
  setTheme,
  settlePage,
  snapName,
  SNAPSHOT_PAGE_TIME_MS,
  SNAPSHOT_THRESHOLDS,
  storagePathFor,
  type AnchorSpec,
  THEME_KEY,
  waitForStableText,
} from './visual-helpers'
import { apiStubTakesPathname } from './data-arrival'
import {
  assertAllApiGetsPinned,
  assertManifestDates,
  assertPinnedPayloadContent,
  CANARY_PROBE_PATH,
  canaryFindings,
  findUnpinnedApiGets,
  inspectPinnedPayload,
  loadPinManifest,
  parseNopinValue,
  PIN_PAYLOAD_TRIVIAL_EXCUSED,
  PIN_PAYLOAD_TRIVIAL_KEYS,
  readNopinEnv,
  type PinManifest,
  type PinState,
} from './payload-pin'
import { pngSize } from './tools/png-size'

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
const SELF_READ_NOPIN_ENV = /process\.env\s*(?:\.|\[)\s*["']?(PW_VISUAL_NOPIN|NOPIN_ENV)["']?/

/** 匹配器本体：消费者守卫与 F1 那条反例用例共用**同一个**表达式，不许两边各写一把尺。 */
function selfReadsNopinEnv(src: string): boolean {
  return SELF_READ_NOPIN_ENV.test(src)
}

/**
 * F6（R-C1F-1 第 3 条，C2 步）：上面这把尺的**作用域**。
 * 【原先缺什么】F1 只把"拼法"从两种扩到四种，**扫描面仍点名的两个文件**
 * （`anchors.spec.ts`／`sensitivity.spec.ts`）⇒ 第三份、第四份 spec 自己现读 `PW_VISUAL_NOPIN`
 * 无人看：新文件（或以后新增的文件）里写一份 `=== '1'`，第 11 项那条"取法单一来源"照样全绿。
 * 【现口径】`e2e/visual/` 下**所有** `.spec.ts`（按 F2 那条 `listSpecFiles` 递归收，不再造一遍遍历），
 * 只豁免下面登记的那一个文件。
 * 【`payload-pin.ts` 为什么"不在扫描面"而不是"被豁免"】`listSpecFiles` 只收 `.spec.ts`，
 * 而那里 `parseNopinValue`／`readNopinEnv` 两处是这个开关的**唯一定义处**——把它们算进负向断言就是误伤。
 * 工单 F6 那句"除 `visual-helpers.ts` 外"说的正是这件事，它由遍历器的名字过滤器天然兑现，
 * 下面 `NOPIN 取法单一来源` 那条用例把"被扫面恰好是那四份 spec"钉住（防止有人哪天把 `.ts` 一起扫进来）。
 * 【豁免只有 `helpers.spec.ts` 一个，且它不是空白支票】本文件是这把尺的用例所在地，
 * 必然逐字写着被禁的写法（F1 的八行合成样本、`readNopinEnv` 接线用例的 env 读写、
 * F5 尺本体用例的接线样本、F6 那条扩面有牙用例的注入样本）。所以豁免**按用例体登记**，
 * 由 `helpers.spec.ts` 里"自读写法只许落在登记过的用例体内"那条断言兜住——
 * 在这个文件里新写一处**没登记**的自读，那条就红。
 * 【按符号定位，不按行号】用例改名会让切片定位不到，所以下面那条断言同时钉"每个登记位都必须切得到东西"。
 */
const NOPIN_SELF_READ_EXEMPT = ['helpers.spec.ts']
const NOPIN_SELF_READ_SCOPES: RegExp[] = [
  /^test\('自读 NOPIN 那把尺覆盖四种拼法/,
  /^test\('readNopinEnv 的 env 面与纯函数面同一条口径/,
  /^test\('剥注释那把尺自己有用例/,
  /^test\('自读 NOPIN 的作用域扩面后有牙/,
]

/**
 * `--update-snapshots` 闸门的三条源级钉（C1 修复轮 1 的 F3／复核 I-4）。
 * 【原先缺什么】第 12 项只钉了调用点那一句的**形态**。复核席构造：把 `global-setup.ts` 里
 * `assertUpdateSnapshotsAllowed` 的默认实参从 `= process.argv` 改成 `= []` ⇒ 调用点那句一字未变、
 * ① 照样 match，② 全程喂合成 argv 也照样绿，而**生产路径从此看不到那个旗标**——门死了且无人报警。
 * 题面注释里那句"生产取值表达式一字未变"原先只是声称，没有任何断言钉它。
 * 【两条合起来才封住】签名钉拦"改默认值"，调用点钉拦"改调用点（塞实参）"；
 * 两条各管一条旁路，缺任何一条都留一个静默删门的口子。下面 F3 那条用例把两条旁路都在内存里走一遍。
 * 【第三条】`playwright.visual.ts` 的 `globalSetup:` 必须仍指向该文件——复核席记为"次一等的第二条路径"：
 * 把那行从 config 删掉，上面两条源级钉读的都是 `global-setup.ts` 原文，两边照绿而门根本不再执行。
 * 这条是工单 F3 点名两条之外的「顺带」半条（出处＝复核 I-4 的修法句），不是自创。
 */
const GATE_SIGNATURE =
  /export function assertUpdateSnapshotsAllowed\(argv: string\[\] = process\.argv\)/
const GATE_CALL_SITE =
  /export default async function globalSetup\(\) \{\s*assertUpdateSnapshotsAllowed\(\)/
const GATE_WIRED_IN_CONFIG = /globalSetup:\s*['"]\.\/e2e\/visual\/global-setup\.ts['"]/

/** 三条钉的匹配器本体：闸门用例与 F3 的内存等价复刻共用，不许两边各写一把尺（F1 同款理由）。 */
function gateSignatureIsLive(src: string): boolean {
  return GATE_SIGNATURE.test(src)
}
function gateCallSiteIsLive(src: string): boolean {
  return GATE_CALL_SITE.test(src)
}
function gateIsWiredIntoConfig(src: string): boolean {
  return GATE_WIRED_IN_CONFIG.test(src)
}

/**
 * 取一个顶层函数的源码切片（C1 修复轮 1 的 F4／复核 I-5：把守卫的作用域收到函数体内）。
 * 【切法】从匹配 `header` 的那一行起，到下一个**顶层**定义为止——判据是行首无缩进的
 * `async function`／`function`／`test(`／`const`，即本仓 spec 里顶层成员的统一写法。
 * 【返回值带 startLine】切片里的行号要能换回**文件里的真行号**，否则红话里的坐标没用
 * （`stripCommentLines` 会塌行号，所以本函数与下面的判据都不用它，见 `recordingGateFindings`）。
 */
function sliceTopLevelFunction(src: string, header: RegExp): { text: string; startLine: number } {
  const lines = src.split('\n')
  const start = lines.findIndex((l) => header.test(l))
  if (start === -1) return { text: '', startLine: 0 }
  let end = lines.length
  for (let i = start + 1; i < lines.length; i++) {
    if (/^(?:async function |function |test\(|const )/.test(lines[i])) {
      end = i
      break
    }
  }
  return { text: lines.slice(start, end).join('\n'), startLine: start + 1 }
}

/**
 * 第 10 项那条守卫的判据本体（C1 修复轮 1 的 F4／复核 I-5）。
 * 【原先缺什么】两半都作用于**整份 `anchors.spec.ts`**，且是三条源级守卫里唯一没跟上"剥注释"口径的：
 *  ① 正向要求 `if (flushRecord) {` 之后**紧跟** `await flushRecord()` ⇒ 往块首插一行解释注释就当场假红
 *    （复核席实测块总长 350、`已写成` 在偏移 +108，600 的距离窗本身够用，红的是"紧跟"这一条）；
 *  ② 负向 `/if \(recording\) \{/` 不剥注释又全文件 ⇒ `anchors.spec.ts` 里那句
 *    `if (recording) flushRecord = attachPayloadRecorder(...)` 只是**碰巧没写花括号**才不红，
 *    在任何历史注释里逐字写出旧形态同样会红，而红话不指位置。
 * 【三处一起收紧】作用域＝`runAnchor` 函数体切片；先剥整行注释再匹配；红话带文件里的真行号与片段。
 * 【返回的是违规清单，不是布尔】空＝绿。这样合成反例（F4 那条用例）与真实扫描共用同一个函数，
 * 而不是一边写判据、一边写"我以为的判据"。
 * 【已知残留，本轮不动，记进报告】`runAnchor` 体内那行无花括号的 `if (recording) ...`
 * 若哪天被加上花括号（合法改写，不是旧形态），本条负向仍会红。把负向收紧成
 * "按 recording 开的块里带 return"能消掉它，但那会同时放过"带花括号、块里没有 return 的中间形态"，
 * 属判据口径变更——工单 F4 给的收紧项是作用域／剥注释／定位三条，不含这条，所以留给控制器裁定。
 */
function recordingGateFindings(src: string): string[] {
  const { text, startLine } = sliceTopLevelFunction(src, /async function runAnchor\(/)
  if (!text)
    return [
      '没能在 anchors.spec.ts 里定位到 runAnchor 的函数体——本条守卫的作用域塌了（头部判据失配就是这条红）',
    ]
  const code = text
    .split('\n')
    .map((line, i) => ({ n: startLine + i, line }))
    .filter((c) => !isFullLineComment(c.line))
  const joined = code.map((c) => c.line).join('\n')
  const findings: string[] = []
  const at = (re: RegExp): string => {
    const hit = code.find((c) => re.test(c.line))
    return hit ? `第 ${hit.n} 行「${hit.line.trim().slice(0, 72)}」` : '未命中'
  }
  if (
    !/if \(flushRecord\) \{\s*await flushRecord\(\)[\s\S]{0,600}?已写成[\s\S]{0,500}?return\s*\}/.test(
      joined,
    )
  ) {
    findings.push(
      'runAnchor 体内不再是「if (flushRecord) { → await flushRecord() → 已写成日志 → return }` 这一块' +
        '（第 10 项那道门被拆开了；十个假"已写成"与静默跳像素会原样回来）。\n' +
        `    定位：块首 ${at(/if \(flushRecord\) \{/)}；flush 调用 ${at(/await flushRecord\(\)/)}；` +
        `日志 ${at(/已写成/)}；提前返回 ${at(/return/)}`,
    )
  }
  for (const c of code) {
    if (/if \(recording\) \{/.test(c.line)) {
      findings.push(
        `第 ${c.n} 行又出现按 recording 布尔开的块「${c.line.trim().slice(0, 72)}」：` +
          '那是"12 个锚点全进、10 个什么都没写"的同型错误（旧形态半边）',
      )
    }
  }
  return findings
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

/**
 * 空态桩**接管面**的两把尺共用的候选清单（4c-fix-C2b 的 S1／R-C2-1 判据原文给的形状）。
 * 【为什么这三张清单要放在这里而不是各写一份】工单 S1 第 2 条要的是一条"会红的守卫"，
 *   它必须同时量两件事：判据本体（`apiStubTakesPathname`）收不收模块路径，以及
 *   `anchors.spec.ts` 里 `page.route` 的第一个参数还锚不锚 pathname 前缀。两边共用同一份样本，
 *   才不会"行为面绿、接线面绿、但两边量的不是同一批 URL"（本仓为这种分裂写过四次教训）。
 * 【API 那张的出处】`/api/courses` 与 `/api/student/courses/1/learning-center` 是 S1 改后那两格
 *   实测打印出来的真接口（改前它们不在 `served` 里，因为页面在取数之前就被打断了），
 *   另两条取自 `DATA_PATHS` 已登记的锚点前缀。
 * 【MODULE 那两条是工单点名的两类，但两者被"旧口径"吃到的方式不同】
 *   `/src/api/auth.ts` ＝ dev server 的**源码模块**，也是本轮事故的本体：它的路径里逐字含 `/api/`
 *   那一段，所以旧那个按子串命中的 route 参数**真的**会收它。
 *   `/node_modules/.vite/deps/api.js` ＝ **预打包产物**，同一族的下一形态：注意它含的是 `/api.js`
 *   而不是 `/api/`，旧通配串其实收不到它——它代表的是判据原文**不许**的那类放宽
 *   （"看起来像 API 就行"，即只要路径里有 api 这一段就接管），一旦有人这样放宽，它就中招。
 *   下面 ① 那组把这两层差别各自钉住，别让"两类都排除"这句话糊成一次断言。
 * 【SHELL 那两条】归 `SHELL_KEEP_ALIVE_PATHS`：它们**也是** `/api/` 前缀，但打空会得到 /login 重定向，
 *   红得无法归因——所以它们必须由判据本体的第二段排除，而不是靠 route 的参数排除。
 */
const STUB_PATHS_API = [
  '/api/courses',
  '/api/courses/1',
  '/api/student/courses/1/learning-center',
  '/api/admin/statistics',
  '/api/knowledge/relations/graph/4001',
]
const STUB_PATHS_MODULE = ['/src/api/auth.ts', '/node_modules/.vite/deps/api.js']
const STUB_PATHS_SHELL = ['/api/auth/permissions', '/api/notifications/unread-count']
/** URL 由 pathname 拼出来：正则那一侧要的是完整 URL，判据本体那一侧要的是 pathname，同源派生才不会漂。 */
const stubUrlOf = (p: string): string => `http://localhost:5175${p}`

/**
 * 接管面的**源码级**判据本体：读 `anchors.spec.ts`，切出 `stubApiToEmpty` 的函数体，
 * 检查它的 `page.route` 第一个参数与判据接线。返回违规清单，**空＝绿**。
 * 【为什么源码级与行为级两条都要】行为级那半只证明 `apiStubTakesPathname` 本身不收模块路径；
 *   但桩真正拦不拦得到模块请求还取决于 route 的参数——把那个参数改回按子串命中的通配串，
 *   行为级那半**照样全绿**（判据本体没错，错的是它前面的那道门）。工单明写"只改匹配不配守卫＝
 *   这条 bug 还能回来"，这条就是它要的入库面。
 * 【两种合法写法（R-C2-1 原话的二选一）】① route 参数是**锚定到 pathname 前缀的正则**——
 *   本函数把它从源码里取出来、`new RegExp` 还原、再喂同一批样本（正则必须收真接口、拒模块两类）；
 *   ② 保留通配串但在回调最前面加 `pathname.startsWith('/api/')` 那道守卫。
 *   两种都算合格，两种都不满足就是红。**不许**改成"看着像 API 就行"。
 * 【两条负向钉】体内必须真的调用判据本体（否则判据有两份副本），且不许把
 *   `prefixes.includes('ALL')` 那种收窄又抄回 anchors——抄回去的那一刻，第二把尺就自由了。
 * 【行号是真的】沿用 `sliceTopLevelFunction` 的 `startLine`，红话给的是文件里的坐标
 *   （`stripCommentLines` 会塌行号，所以只在"取代码面"时逐行过滤，不改索引）。
 */
function stubMatcherFindings(src: string): string[] {
  const { text, startLine } = sliceTopLevelFunction(src, /async function stubApiToEmpty\(/)
  if (!text)
    return [
      '没能在 anchors.spec.ts 里定位到 stubApiToEmpty 的函数体——本条守卫的作用域塌了（改名或搬走都要同批改这里）',
    ]
  const numbered = text
    .split('\n')
    .map((line, i) => ({ n: startLine + i, line }))
    .filter((c) => !isFullLineComment(c.line))
  const code = numbered.map((c) => c.line).join('\n')
  const at = (re: RegExp): string => {
    const hit = numbered.find((c) => re.test(c.line))
    return hit ? `第 ${hit.n} 行「${hit.line.trim().slice(0, 72)}」` : '未命中'
  }
  const findings: string[] = []
  const routeArg = /page\.route\(\s*(.+?),\s*async/.exec(code)?.[1]
  if (!routeArg) {
    findings.push(
      `stubApiToEmpty 体内找不到形如 page.route(<参数>, async …) 的接管声明（定位：${at(
        /page\.route\(/,
      )}）——本条守卫量不到匹配面了`,
    )
  } else if (routeArg.startsWith('/') && routeArg.endsWith('/') && routeArg.length > 2) {
    let re: RegExp | null = null
    try {
      re = new RegExp(routeArg.slice(1, -1))
    } catch {
      re = null
    }
    if (!re) {
      findings.push(`page.route 的第一个参数看着像正则却构造不出来：${routeArg}`)
    } else {
      for (const u of STUB_PATHS_MODULE.map(stubUrlOf)) {
        if (re.test(u))
          findings.push(
            `接管面把 dev server 的模块 URL 也算成接口（R-C2-1 的本体）：${u}；参数=${routeArg}`,
          )
      }
      for (const u of STUB_PATHS_API.map(stubUrlOf)) {
        if (!re.test(u))
          findings.push(
            `接管面连真接口都不收了——本条守卫的作用面塌了（桩什么都没打就等于实验没做成）：${u}；参数=${routeArg}`,
          )
      }
    }
  } else if (!/pathname\.startsWith\(['"]\/api\/['"]\)/.test(code)) {
    findings.push(
      `page.route 的参数是 ${routeArg}（不按子串锚定 pathname 的正则），而回调最前面也没有 pathname.startsWith('/api/') 那道守卫——` +
        '这就是改前那个形态：按 URL 里含 /api/ 那一段命中，会把源码模块一起 fulfill 成 JSON（R-C2-1 给的两种合法写法一种都不满足）',
    )
  }
  if (!code.includes('apiStubTakesPathname(')) {
    findings.push(
      `stubApiToEmpty 体内没走 data-arrival 那份判据本体 apiStubTakesPathname（定位：${at(
        /apiStubTakesPathname\(/,
      )}）——外壳保活与 DATA_PATHS 收窄又变成第二把尺`,
    )
  }
  if (/prefixes\.includes\(\s*['"]ALL['"]\s*\)/.test(code)) {
    findings.push(
      `stubApiToEmpty 体内重新写了一遍 ALL 判定（定位：${at(
        /prefixes\.includes\(/,
      )}）：判据唯一来源是 apiStubTakesPathname，抄回来就等于把 S1 那次修复拆掉一半`,
    )
  }
  return findings
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
 * 【这一格为什么从 0.0005 收到 0.0001】Task 4c Step 4 实测（量程账本见 `docs/测试文档.md` §4.4）：日期漂移被载荷钉死抵住之后，4 张日期锚点与基线 **md5 相等、字节差 0**，
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
 * ①b **签名**（源码级，C1 修复轮 1 的 F3 补）：默认实参必须逐字是 `argv: string[] = process.argv`。
 *   原先这一半没人钉——把默认值改成 `= []` 就能在**不动调用点一个字**的前提下把门做成死的。
 * ①c **接线**（同上，复核 I-4 的"顺带"半条）：`playwright.visual.ts` 的 `globalSetup:` 仍指向该文件。
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
    gateCallSiteIsLive(setupRaw),
    'globalSetup 的第一条语句不再是**不带实参**的 assertUpdateSnapshotsAllowed()——这道门又回到"只有手工证明过"的状态（第 12 项）',
  ).toBe(true)
  // ①b 签名（F3 补的那半）：默认实参就是生产取值表达式，改它等于删门
  expect(
    gateSignatureIsLive(setupRaw),
    'assertUpdateSnapshotsAllowed 的默认实参不再逐字是 argv: string[] = process.argv——把默认值改成 [] 就能在不动调用点的前提下让门永远看不到旗标（复核 I-4）',
  ).toBe(true)
  // ①c 接线（F3 顺带的那半条）：源级两条钉读的都是 global-setup.ts 原文，config 不指它时门根本不会执行
  const configRaw = stripCommentLines(
    readFileSync(join(process.cwd(), 'playwright.visual.ts'), 'utf8'),
  )
  expect(
    gateIsWiredIntoConfig(configRaw),
    'playwright.visual.ts 里 globalSetup: 不再指向 ./e2e/visual/global-setup.ts——门体还在但没人调用它（复核 I-4 的次一路径）',
  ).toBe(true)
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
 * C1 修复轮 1 的 F3（复核 I-4）：**内存等价复刻**——证明上面那三条源级钉真的会红。
 * 【为什么不去把默认值真改掉再跑】要验"门已失效"就得把 `--update-snapshots` 交给 runner，
 * 而那一跑会当场重写／prune 掉 22 张已提交基线；围栏明令该旗标**永不**跑（本轮一次都没跑过）。
 * 复核席给的是同一思路的另一半：旁路是**源级**的，所以红面也可以在源级证——
 * 把源码字符串在内存里按那种改法改一遍，喂给**同一个匹配函数**，断言它不再 match。
 * 【两侧都钉，缺一侧就是空转】每条变异都先断言"替换真的发生了"（`!== raw`），
 * 否则"不再 match"会因为字符串根本没变而假成立；再各钉一条"另一条旁路与它无关"，
 * 这正是"为什么必须两条一起"的证据：改默认值只被签名钉拦住、改调用点只被调用点钉拦住。
 */
test('闸门源级钉自己会红：内存里复刻「改默认实参」与「改调用点」两条旁路（F3 等价复刻，未跑任何 update-snapshots）', () => {
  const setupRaw = stripCommentLines(
    readFileSync(join(process.cwd(), 'e2e/visual/global-setup.ts'), 'utf8'),
  )
  const configRaw = stripCommentLines(
    readFileSync(join(process.cwd(), 'playwright.visual.ts'), 'utf8'),
  )
  // 现状：三条都成立（与上一条用例同一把尺，这里只是将下面的变异比较锚在真实现上）
  expect(
    gateSignatureIsLive(setupRaw),
    '现况下签名钉就不成立——下面的等价复刻全部失去意义，先看上一条用例',
  ).toBe(true)
  expect(gateCallSiteIsLive(setupRaw), '现况下调用点钉就不成立——同上').toBe(true)
  expect(gateIsWiredIntoConfig(configRaw), '现况下接线钉就不成立——同上').toBe(true)

  // 变异①＝复核席构造的那条静默删门路径：默认实参改成空数组，调用点一字未动。
  const mutatedDefaultArg = setupRaw.replace('argv: string[] = process.argv', 'argv: string[] = []')
  expect(mutatedDefaultArg === setupRaw, '替换没发生：本条等价复刻是空转，变异构造要重定').toBe(
    false,
  )
  expect(
    gateSignatureIsLive(mutatedDefaultArg),
    '默认实参已被改成 []，签名钉却没红——这条旁路又没人看着了',
  ).toBe(false)
  // 这条就是复核席那句"① 仍 match"的入库版：单靠调用点钉拦不住改默认值，所以 F3 要补签名钉。
  expect(
    gateCallSiteIsLive(mutatedDefaultArg),
    '调用点钉对这条旁路必须无感（无感才证明"只钉调用点"不够）',
  ).toBe(true)

  // 变异②：给调用点塞实参 ⇒ 调用点钉红、签名钉仍绿（两条各管一条旁路，缺一留一孔）。
  const mutatedCallSite = setupRaw.replace(
    'export default async function globalSetup() {\n  assertUpdateSnapshotsAllowed()',
    'export default async function globalSetup() {\n  assertUpdateSnapshotsAllowed([])',
  )
  expect(mutatedCallSite === setupRaw, '替换没发生：本条等价复刻是空转，变异构造要重定').toBe(false)
  expect(gateCallSiteIsLive(mutatedCallSite), '调用点被塞了实参，调用点钉却没红').toBe(false)
  expect(
    gateSignatureIsLive(mutatedCallSite),
    '签名钉不该管调用点这一族（误伤会把两条旁路混成一条）',
  ).toBe(true)

  // 变异③＝接线被拆：config 里那行换成别的文件（或删除）⇒ 前两条读的是 global-setup.ts 原文，照绿而门不执行。
  const unwiredConfig = configRaw.replace('./e2e/visual/global-setup.ts', './some-other-setup.ts')
  expect(unwiredConfig === configRaw, '替换没发生：本条等价复刻是空转，变异构造要重定').toBe(false)
  expect(gateIsWiredIntoConfig(unwiredConfig), 'globalSetup 已不指向闸门文件，接线钉却没红').toBe(
    false,
  )
})

/**
 * 24 张基线的 sha256 清单（Step 1c 的"篡改痕迹"）。
 * 症状设计成"基线被换掉即红"——包括被 `--update-snapshots` 换掉、也包括被别的代理覆盖掉。
 * 重算清单（改基线时必须同步这里，且要逐张复核新图）：
 *   cd frontend && node e2e/visual/tools/measure-diff.cjs --manifest e2e/visual/__snapshots__/anchors.spec.ts --json
 * 张数为 24 = 12 锚点 × 明暗全集。2026-10-24 同步两笔：
 * ① 补齐三笔"只动基线没动清单"的欠账：error-404 ×2（`ea48fe1`）、student-dashboard 明暗 ×2
 *    （`93b09cb`，经授权重录）、admin-governance-analytics-dark 与 student-knowledge-graph-dark ×2
 *    （`c0b94ce`，D-1 第二载体重采）；
 * ② FE-04（D-10 修复）重录 4 张：admin-governance-analytics 明暗 ×2（fixture 同批重录）、
 *    admin-dashboard 明暗 ×2（该锚点未钉桩走真后端，D-10 修复令成绩分布与完成率卡变真数据，
 *    属同一后端修复的合法爆炸半径）。四张新图均已逐张人工复核（明暗各一，见台账）。
 * 2026-10-25 P1-FULLPAGE-01（缺陷 D-7）全量重录 14 张：视口扩到内容实高后，7 个可滚页
 * （admin-dashboard/courses-table/governance-analytics/profile、student-dashboard/
 * knowledge-graph、teacher-dashboard）明暗各一变长（1116–2874px，首屏之下内容首次进基线），
 * 均逐张人工看图；其余 10 张（login/error-404/admin-system-form/student-courses-modal/
 * student-learning-center）sha256 与上一版逐字节相同——expandViewportToContent 对内容不超
 * 视口的页零扰动，机器可证。
 * 2026-10-25 P1-TOKEN-01（阴影族）重录 18 张：浅色 --sh-l1 环境层 .06→.10 全站生效 + 暗色
 * 卡面三档阴影置 none（EP 弹层重映射到 --sh-pop 保影）。学生 5 张（modal/kg 明暗与 lc-dark）
 * 另含一处数据态差异——顶栏未读角标（昨录时未读>0、今录时已清零），该区域自此经 maskSelectors 遮罩恒在的铃铛按钮容器（见 ANCHORS 的 student-courses-modal 注释；首版遮 .n-badge 因元素随计数 0↔N 出没而失稳，实跑 6 红后换容器）；lc-light 仅剩角标带 114 px。
 * 逐张人工看图 + measure-diff 读数（Δmax：浅 7–8 / 暗 16–50 纯 token 效果；学生 5 张
 * 170–207 为角标移除项主导）见 LEDGER `P1-TOKEN-01`。
 * 2026-10-25 P2-RADIUS-01（圆角五阶对齐 + 控件路径摘 6px）重录 14 张：--r-l3 9→6 补齐
 * 16/12/10/6/999 五阶；EP base 映射改 --r-ctl（默认控件 9→10、small=calc(10-1)=9）；
 * 覆写层 primary/default 按钮与输入框从 --ac-radius-sm(6) 升控件档 10（spec 明文项）；
 * 四枚同值别名单源化（sm/lg/xl/EP-small 改引阶梯，零视觉）。角部族读数（计数 9–786、
 * Δmax 18–207）与计算值断言（默认 10/small 9/tag 6/l3 6）见 LEDGER `P2-RADIUS-01`。
 * 2026-10-25 P1-TOKEN-04（暗色表面抬亮 + 描边优先）重录 11 张暗色图（error-404-dark 无表面/
 * 描边消费不变、全部浅色图零变化）：表面三阶各 +3/通道、line 阶梯上移一档（Δmax 17＝
 * +15/+15/+17 逐通道算术）、.l1-card 暗色 1px 上高光边（--edge-hi，与 L3 凹槽单源）。
 * 读数 Δmax 17–22 见 LEDGER `P1-TOKEN-04`。
 * 2026-10-25 P1-TOKEN-02（表面阶浅色 + L3 凹槽）重录 14 张：--bg 压深一档（#eef0f4→#e9ebf0，
 * 恒差 Δ5）波及全部 12 张浅色图；--l3-groove 凹槽（浅=白.5 顶高光+内阴影 / 暗=1px 白.06 高光边，
 * 消费点 .l3-block）另改 admin-dashboard 与 student-courses-modal 明暗 4 张（暗面仅此 2 张、
 * Δ13 全在凹槽行）。读数与计算值断言/1px 行裁剪证据见 LEDGER `P1-TOKEN-02`。
 * 2026-10-25 P1-TOKEN-01 挂账收口：teacher/admin 五个未钉桩壳页锚点（admin-dashboard/
 * teacher-dashboard/admin-courses-table/admin-system-form/admin-profile）补同款铃铛遮罩并
 * 重录 10 张——至此 12 锚点中凡渲染顶栏铃铛且未钉 unread-count 的 8 格全部遮罩，钉桩的
 * student-dashboard 与 admin-governance-analytics 保持不遮（角标画 fixture 值、确定性可验）。
 * 同步以"工作树除本文件外干净、字节与 HEAD 一致"为前提取证，引用这个数一律带张数与原因。
 * 2026-10-28 P5-05b 第二批（surface-3 凹槽推广 + 暗色 L2 顶边）重录 9 张：`.tabs-pill`／
 * `.tabs-seg`／`.empty-state .e-ico` 三处接 `--l3-groove`（消费点 1→4，由
 * `src/styles/__tests__/token-single-source.test.ts` 钉死），暗色 `.l2-panel` 补 `--edge-hi`；
 * `EmptyState.vue` 的 scoped 副本圆角 18→`--r-l1`（上一批改的是全局那枚，而组件副本才是生效位）。
 * byteDiff 与 pixelmatch 两口径并记（亮 468–4114、暗 6–66）：暗色那条 1px 顶边高光
 * 988 字节差只被 pixelmatch 计 6 点，交付门因此看不见它，能看见的是零漂移那道 byteDiff。
 * 逐锚点账与看图见 docs/前端待办项.md §2.1。
 */
const BASELINE_SHA256: Record<string, string> = {
  'admin-courses-table-dark.png':
    'a8d3266df186d1955ae8adcb528fcff99e39909971c3ab465e3d4b3c9e1b6a5d',
  'admin-courses-table-light.png':
    'c8f47453b9e32c85562fb6bcd6ab0f98dbe09c00745b8ee7933d02ff36f72893',
  'admin-dashboard-dark.png': 'e4b192fbdf9f1a5f6bd33e0ad2e8aad4dfe9f3ffd9ab89a1fa0e62a800e86c08',
  'admin-dashboard-light.png': '153d9bed086877c4fb54c23a090447ab84258d3fedeb2e64818a3c90aec6766d',
  'admin-governance-analytics-dark.png':
    '496fb40099ff2092ed9440d6ed31f9d6675b822855edcfbb65b6a5bb0e802456',
  'admin-governance-analytics-light.png':
    '76aa878c7eef431833a8de619522325bee242ac5dbabaab0f3e07dc971dcd311',
  'admin-profile-dark.png': 'dcbbe819911642ca8129f4ef20e92bb3f0906d152f60ee0cd6109ca198330d61',
  'admin-profile-light.png': '9193e75e8180587edd1a94877ed0119cbaa606aa595452371d20e876fccbd984',
  'admin-system-form-dark.png': 'e5ea06e14d8ef48714bd7e40cff4930c1b3cb1a3a2eb49839d68dfd0d783478e',
  'admin-system-form-light.png': 'e71d4ded06754afd4fb2e54350c8e339ded83795e988319206d914b602bfcf13',
  'error-404-dark.png': 'b2d03c673791a14b37bb9c6f92cf46f862108051620f0cabfcbe1faf53afe0a4',
  'error-404-light.png': '0755e2afbe53f433ddc690495c8b188d39a989abc0403b75e0ecd2b6e77691a7',
  'login-dark.png': 'c57f5e4db9a85653dab2a359fc4a183b65a8716647a86abae1c2257350545ab9',
  'login-light.png': '451ad3801b50483fa655e61d6cb1b2806c00aa2a446e05c5e336496ee6f1619e',
  'student-courses-modal-dark.png':
    '1c3706b48129a17f3386534b4770b3a09dfac97ee9a8afda63fdd9422b99e189',
  'student-courses-modal-light.png':
    'ef2eec5dea0994d9b60e2e43a83fa34875a5d00d0ebbb3bd7d5d412b2901a3de',
  'student-dashboard-dark.png': '986a01bb19d2821f22263f6887959d66855e7ad321ad9345918beb32b1b7cc95',
  'student-dashboard-light.png': 'ee65f752b8d92e7f2805f0079c71355d030c4d018dac2e02e74a0ab891bd7ddd',
  'student-knowledge-graph-dark.png':
    '4bdaeeec6369219d1fdfe3c26afc78585163d58b82b94836d6a78522d38ac90c',
  'student-knowledge-graph-light.png':
    'e639e36165194500cbd5501a4befe1dd33fa919c012f1695eb690d545b0ba82e',
  'student-learning-center-dark.png':
    '04c0c2aa6781708cc407665ab6c5870234b48456f51e9ee6b7b08d7915e2c92e',
  'student-learning-center-light.png':
    '5eee9aa4ef0fde63124b56620ed894d7b86111c5751e44dff551809feb38f9b8',
  'teacher-dashboard-dark.png': '30cbb78b5cfc11b04badd5788e25de211a9162693ab94b688c2fe6e2e15ed324',
  'teacher-dashboard-light.png': '418fa48e4fdc6fc429c2792008653d90d89c384b9ba9449302bf901460c2f404',
}

test('24 张已提交基线的 sha256 与入库清单一致（篡改/重生成即红）', () => {
  const dir = join(process.cwd(), 'e2e/visual/__snapshots__/anchors.spec.ts')
  const onDisk = readdirSync(dir)
    .filter((f) => f.toLowerCase().endsWith('.png'))
    .sort()
  expect(
    onDisk.length,
    '基线张数变了：24 是"12 锚点 × 明暗"全集（D-1 修复后 error 两张已入库）',
  ).toBe(24)
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
 * 【P1-FULLPAGE-01 改写（2026-10-25）】基线几何从"清一色 1440×900"变为"宽 1440 × 逐页实高 H"
 * （expandViewportToContent 把视口扩到内容实高、封顶 FULLPAGE_MAX_HEIGHT，修缺陷 D-7）。
 * 本条随之改判：宽度仍逐张钉死 1440；高度钉在 [900, FULLPAGE_MAX_HEIGHT] 区间——
 * **每张基线的免检额从此是 w·h·r 现算**，旧数 129.6 只对 1440×900 成立，量程账本里
 * 1440×900 几何下的各档读数仍按旧分母引用（历史读数不改），**新引用必须带几何**。
 * 内容变高 ⇒ sha256 那条先红（图被换过）⇒ 逐张人工复核后才许连清单一起改——
 * 分母的每一次变化都走那道门，本条只拦"几何越界"（宽度漂移 / 视口配置被动过 / 超 CAP）。
 *
 * 【原先缺什么】这一族算术在这个仓里被引用了十几次——`129.6`（1440×900 几何的当前档）、`648`（r=0.0005 那几轮）、
 * `2592`（原生产配置），量程账本（`docs/测试文档.md` §4.4）靠它把"计数"折算成"几倍余量"。
 * 在"清一色 1440×900"的年代它有两个翻车方向：
 *   ① 某页一旦**真的能滚**（Phase 4 的结构收敛最容易碰），fullPage 截图就变长，
 *      免检额随**期望图**面积一起涨 ⇒ 同一个 198 px 的圆角改动从 1.53× 掉到 0.46× —— **绿得没意义**
 *      （spec §6.12 末把它与"只有实际页变高、期望图没变 ⇒ sizesMismatchError 直接红"分开说过，
 *       一条是变瞎、一条是变红，别混）。P1-FULLPAGE-01 就是把"变长"从隐患变成**显式几何**：
 *      每张图多高、免检额多大，逐张现算、有 sha256 把门。
 *   ② `maxDiffPixelRatio` 一旦改动，注释里所有"几倍余量"当场作废，而没有任何东西会提醒。
 * 【这一条现在钉的是】(a) 每张基线宽 1440、高在 [900, CAP]；(b) 1440×900 几何下的三个历史算术常量
 *      仍由同一分母重算出来（量程账本的历史读数引用它们）。
 *      r **从 `SNAPSHOT_THRESHOLDS` 读**，不在这重写一遍 `0.0001`——收紧它必须让这条红、
 *      逼着人去把注释与台账里的数一起改，而不是让注释静默失效。
 * 【比较口径】判据是 `count > expected.width * expected.height * ratio`（**无 ceil、无取整**，
 *   `playwright-core/lib/coreBundle.js:7903` 取的是**期望图**的尺寸、`:7910` 才是那道 `>` 比较；
 *   本机安装的是 playwright-core 1.63.0）。1440×900 几何下"免检额 130 px"是 129.6 的**向上**简写：
 *   计数恰好 130 的那一格是**红**的；逐页几何下同理按 w·h·r 现算、不取整。方向断言保留在
 *   1440×900 分母上（那是"简写不取整"这件事的最小可证样本）。
 * 【为什么复用 `pngSize` 而不是再写一个读头函数】这仓已经有**两把**会读 PNG 头的东西
 *   （`tools/measure-diff.cjs` 的 chunk 扫描、`sensitivity.spec.ts` 的私有 `pngSize`）。
 *   第三份副本一旦写出，"免检额"就有三个可能不一致的来源；`pngSize` 已上收到 `tools/png-size.ts`，
 *   两边同一份（它只读 8 字节头、不还原像素，判据仍然只有那把尺）。
 * 【与 sha256 清单那条的分工】上面那条管"图没被换过"，本条管"图**长多大**"。
 *   新增基线的尺寸同样要落进这条分母，不能只在清单里多个名字。
 */
test('每张已提交基线宽 1440、高在 [900, CAP] 内；1440×900 历史免检额算式仍成立', () => {
  const dir = join(process.cwd(), 'e2e/visual/__snapshots__/anchors.spec.ts')
  const ratio = SNAPSHOT_THRESHOLDS.maxDiffPixelRatio

  for (const f of Object.keys(BASELINE_SHA256).sort()) {
    const { width, height } = pngSize(join(dir, f))
    expect(
      width,
      `${f} 的宽度不再是 1440 ⇒ 视口配置被动过（P1-FULLPAGE-01 只改高度不改宽度）`,
    ).toBe(1440)
    expect(height, `${f} 的高度低于视口 900 ⇒ 内容没量到或几何逻辑被动过`).toBeGreaterThanOrEqual(
      900,
    )
    expect(
      height,
      `${f} 的高度超过 FULLPAGE_MAX_HEIGHT=${FULLPAGE_MAX_HEIGHT} ⇒ 超长页要在台账显式登记截断边界，不得静默`,
    ).toBeLessThanOrEqual(FULLPAGE_MAX_HEIGHT)
  }

  // ② 1440×900 几何的历史算术：量程账本各档读数的引用基准，逐数现算。
  const IMAGE_PX = 1440 * 900
  expect(IMAGE_PX * ratio).toBeCloseTo(129.6, 6) // 1440×900 交付档（r=0.0001）
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
 * ①–④ 是**源码文本**级；行为级证据是四条导入期跑测（`--list` 不启动浏览器、不写文件）：
 * `PW_VISUAL_NOPIN=true` ⇒ anchors 与 sensitivity **都**抛（第 11 项交付物本体），
 * `=1` ⇒ anchors 抛、探针那条例向命令正常列出用例。命令与读数见报告第 11 节。
 * 【C2 步的 F6 改的是②的**作用域**】②原先只读两个具名文件 ⇒ 第三、第四份 spec 自读那个开关无人看。
 * 现在②跑在 `listSpecFiles` 收来的"除豁免文件外的所有 spec"上（现况四份：anchors／
 * motion-observation／sensitivity／smoke），被扫集合本身钉死，扩面有没有牙由
 * 下面那条「作用域扩面后有牙」用例喂内存注入样本证（不在盘上改任何 spec）。
 */
test('NOPIN 取法单一来源（第 11 项／F6 扫遍所有 spec）：消费者只经 readNopinEnv，分工两道门仍在', () => {
  const dir = join(process.cwd(), 'e2e/visual')
  const anchors = stripCommentLines(readFileSync(join(dir, 'anchors.spec.ts'), 'utf8'))
  const probe = stripCommentLines(readFileSync(join(dir, 'sensitivity.spec.ts'), 'utf8'))
  // ① 取值入口：两处都必须是共享函数，而不是各自现读
  expect(
    anchors,
    'anchors 又不通过 readNopinEnv() 取这个开关了——第 11 项的统一取法被绕开',
  ).toContain('readNopinEnv()')
  expect(probe, '探针又不通过 readNopinEnv() 取这个开关了——同一道门少了一个消费者').toContain(
    'readNopinEnv()',
  )
  // ② 负向半边：代码面再出现自读 env 的写法（**四种拼法一条正则全拦**，F1）。
  //   【F1（复核 I-2）】原先这里只认两种拼法（点号与 `[NOPIN_ENV]`），方括号加引号那种当场溜过。
  //   【F6（R-C1F-1 ③）】原先这里只读**两个具名文件**，第三份 spec 自读无人看 ⇒ 改成扫被豁免之外的一切 spec。
  const scanned = listSpecFiles(dir).filter((f) => !NOPIN_SELF_READ_EXEMPT.includes(f))
  expect(
    scanned,
    'NOPIN 自读守卫的被扫面变了——现在它读的是"除豁免文件外的所有 spec"，新增文件会自动落进来，' +
      '而豁免名单只有 helpers.spec.ts 一个（理由与它的位置级约束见上一条常量注释）。现况 4 份',
  ).toEqual([
    'anchors.spec.ts',
    'motion-observation.spec.ts',
    'sensitivity.spec.ts',
    'smoke.spec.ts',
  ])
  for (const f of scanned) {
    const code = stripCommentLines(readFileSync(join(dir, f), 'utf8'))
    expect(
      code,
      `${f} 的代码面又出现自读 NOPIN 开关的写法（四种拼法之一）：单一来源破了，消费者力度会重新分岔（第 11 项／F6）`,
    ).not.toMatch(SELF_READ_NOPIN_ENV)
  }
  // ②b `payload-pin.ts` **不在扫描面**是因为遍历器只收 `.spec.ts`（那里是开关的唯一定义处）。
  //   这句原先只是声称，现在钉住：定义确实只在该文件里出现两处，而它不在被扫集合内。
  const helpersDefs = stripCommentLines(readFileSync(join(dir, 'payload-pin.ts'), 'utf8'))
  expect(
    helpersDefs,
    'payload-pin.ts 里 readNopinEnv 的定义不见了——被扫面的排除项失去依据（那两处是唯一定义处）',
  ).toContain('export function readNopinEnv')
  expect(scanned, '遍历器开始收非 .spec.ts 文件了——开关的定义处会被负向断言误伤').not.toContain(
    'payload-pin.ts',
  )
  // ②c 豁免文件不是空白支票：helpers.spec.ts 里的自读写法必须**全部**落在登记过的用例体内。
  const selfFile = stripCommentLines(readFileSync(join(dir, 'helpers.spec.ts'), 'utf8'))
  const selfLines = selfFile.split('\n').filter((l) => selfReadsNopinEnv(l))
  expect(
    selfLines.length,
    '本文件的自读写法一处也没有了——豁免面空转，应把 helpers.spec.ts 从豁免名单里摘出去重测',
  ).toBeGreaterThan(0)
  const scopeTexts = NOPIN_SELF_READ_SCOPES.map((re) => sliceTopLevelFunction(selfFile, re).text)
  expect(
    scopeTexts.filter((t) => t !== '').length,
    '有登记位置定位不到（用例改名或切片判据变了）——豁免会静默扩大到整份文件',
  ).toBe(NOPIN_SELF_READ_SCOPES.length)
  const insideCount = scopeTexts.reduce(
    (n, t) => n + t.split('\n').filter((l) => selfReadsNopinEnv(l)).length,
    0,
  )
  expect(
    insideCount,
    `本文件有 ${selfLines.length - insideCount} 处自读写法落在四个登记用例体之外——豁免按位置给，不按文件给：` +
      '要么改回 readNopinEnv()，要么把这处新增显式登记进 NOPIN_SELF_READ_SCOPES 并说明为什么',
  ).toBe(selfLines.length)
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
 * 上一条用例读的只有 `anchors.spec.ts` 与 `sensitivity.spec.ts` 两个文件（`payload-pin.ts` 里
 * 那两处是合法定义，扩进去就误伤）。**若将来把该守卫的作用域扩到扫遍所有 spec，本文件要与
 * `payload-pin.ts` 同列豁免**，否则这八行合成样本会当场变成假红——这是设计，不是疏忽。
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
 * （见 payload-pin.ts 里那句原话）。所以这里逐个值断言，不用一条 `toBeTruthy` 糊过去——
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
 * 修复轮 1 的 I-4①：把"钉桩锚点的平凡键集合 vs 登记表"那条比较抽成**判据本体**（纯函数）。
 * 【为什么不是直接在用例里 toEqual】本仓的规矩是"守卫自己也要测"——一条从没红过的比较不是守卫。
 *   写成纯函数后，下面那条用例既拿真 fixture 跑一遍（绿的那半），又喂四组合成表证明它有牙（红的半）。
 * 【口径】两侧都排序后逐键比；只登记**有平凡键**的锚点（某锚点全部键都非平凡 ⇒ 它不该出现在任一侧，
 *   这与旧 `.some()` 折成 slug 集合的行为一致，免得把"非平凡"这件事也当成豁免）。
 */
function trivialKeyFindings(
  measured: Record<string, readonly string[]>,
  declared: Record<string, readonly string[]>,
): string[] {
  const findings: string[] = []
  const slugs = [...new Set([...Object.keys(measured), ...Object.keys(declared)])].sort()
  for (const slug of slugs) {
    const m = [...(measured[slug] ?? [])].sort()
    const d = [...(declared[slug] ?? [])].sort()
    if (m.join('\u0000') !== d.join('\u0000')) {
      findings.push(`${slug}：实测平凡键 ${JSON.stringify(m)} ≠ 登记表 ${JSON.stringify(d)}`)
    }
  }
  return findings
}

/** 从**入库 fixture** 实量每个钉桩锚点的平凡键集合（只读，不写盘、不重录）。 */
function measureTrivialKeysBySlug(): Record<string, string[]> {
  const out: Record<string, string[]> = {}
  for (const a of ANCHORS.filter((x) => (x.pinPaths?.length ?? 0) > 0)) {
    const m = loadPinManifest(a.slug)
    const trivial = (a.pinPaths ?? [])
      .filter(
        (k) =>
          m.requests[k] && !inspectPinnedPayload(m.requests[k].body, `${a.slug}/${k}`).nonTrivial,
      )
      .sort()
    if (trivial.length > 0) out[a.slug] = trivial
  }
  return out
}

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

  const measuredTrivialKeysBySlug = measureTrivialKeysBySlug()
  // ④ 逐键一致（修复轮 1 的 I-4①，**取代**原先"只比 slug 集合"那半条）：
  //    当前实测的 `slug → 平凡键列表` 必须与 `PIN_PAYLOAD_TRIVIAL_KEYS` 逐键相等。
  //    【为什么原先那条不够】旧写法用 `.some()` 折成 slug 级集合 ⇒ Task 0 新钉的
  //    `/api/notifications/unread-count`（裸数值、按口径判平凡）混在已经登记的 slug 里，
  //    键集合从"每格 1 条"漂成"每格 2 条"而本文件照样全绿——盲区登记在 findings I-4。
  //    集合级的那半条仍然留着（它管"豁免表与键表不许各自漂"），只是不再单独充当平凡集合的守卫。
  const trivialNow = Object.keys(measuredTrivialKeysBySlug).sort()
  expect(
    trivialNow,
    '登记表与实测全 0 集合漂开了（一侧变化必须同批改另一侧与 LEDGER P0a-GUARD-02）',
  ).toEqual(Object.keys(PIN_PAYLOAD_TRIVIAL_EXCUSED).sort())
  expect(
    trivialKeyFindings(measuredTrivialKeysBySlug, PIN_PAYLOAD_TRIVIAL_KEYS),
    '平凡键集合与 PIN_PAYLOAD_TRIVIAL_KEYS 不相等 ⇒ 一侧变化必须同批改另一侧、' +
      '`PIN_PAYLOAD_TRIVIAL_EXCUSED` 的依据文本与 LEDGER P0a-GUARD-02（修复轮 1 的 I-4①）',
  ).toEqual([])
  expect(
    Object.keys(PIN_PAYLOAD_TRIVIAL_KEYS).sort(),
    'PIN_PAYLOAD_TRIVIAL_KEYS 与 PIN_PAYLOAD_TRIVIAL_EXCUSED 两张表的 slug 集合分家了 ⇒ ' +
      '只改一张等于"登记了一条没有依据的豁免"或"写了一段没有键的依据"',
  ).toEqual(Object.keys(PIN_PAYLOAD_TRIVIAL_EXCUSED).sort())
  for (const slug of Object.keys(PIN_PAYLOAD_TRIVIAL_KEYS)) {
    const anchor = ANCHORS.find((a) => a.slug === slug)
    expect(anchor, `${slug} 的平凡键登记悬空：ANCHORS 里已没有这个锚点`).toBeTruthy()
    expect(
      (anchor!.pinPaths ?? []).length,
      `${slug} 登记了平凡键却已不再声明 pinPaths——豁免表是给钉桩用的，先删条目`,
    ).toBeGreaterThan(0)
    for (const k of PIN_PAYLOAD_TRIVIAL_KEYS[slug]) {
      expect(
        anchor!.pinPaths ?? [],
        `${slug} 登记的平凡键 ${k} 不在它的 pinPaths 里：键必须逐字（含 search）对得上才叫"被钉的载荷"`,
      ).toContain(k)
    }
  }
})

/**
 * 修复轮 1 的 I-4①：上面那条逐键比较**有牙**的证据（全喂合成表，不碰 `__fixtures__`、不重录）。
 * 【要证的三件事】① 少登记一条键会红；② 多登记一条键也会红（两侧对称，不是"实测必须被登记覆盖"）；
 * ③ search 逐字参与比较——`/api/x` 与 `/api/x?page=1&size=100` 是两条键，
 *   把带 query 的那条写成裸 pathname 不许被算成"已登记"（Task 0 的 pinPaths 全靠这条口径）。
 * 【零基】最后一条"两侧完全相同 ⇒ 空清单"必须有，否则前三条红可能红在判据写反上。
 */
test('平凡键逐键比较有牙：少一条、多一条、search 写漏都红，而相同两侧给空清单（修复轮 1 的 I-4①）', () => {
  const same = { 'x-anchor': ['/api/a', '/api/b?page=1'] }
  expect(
    trivialKeyFindings(same, same),
    '两侧逐字相同却报违规——判据写反了，上面三条红的归因全部作废',
  ).toEqual([])
  // ① 登记表少一条键（真实漂法：Task 0 新钉的裸数值端点没进表）
  expect(
    trivialKeyFindings({ 'x-anchor': ['/api/a', '/api/b?page=1'] }, { 'x-anchor': ['/api/a'] }),
    '登记表漏了一条平凡键却放行——I-4① 要修的正是这个盲区',
  ).toHaveLength(1)
  // ② 登记表多一条键（真实漂法：重录后那条键已有内容，表没删）
  expect(
    trivialKeyFindings({ 'x-anchor': ['/api/a'] }, { 'x-anchor': ['/api/a', '/api/b?page=1'] }),
    '登记表里挂着一条实测已非平凡的键却放行——豁免只能与实测逐键相等',
  ).toHaveLength(1)
  // ③ search 逐字：pathname 相同、query 不同 ⇒ 必须算两条键
  expect(
    trivialKeyFindings({ 'x-anchor': ['/api/b?page=1&size=100'] }, { 'x-anchor': ['/api/b'] }),
    '把带 search 的键写成裸 pathname 竟被判相等——钉桩与录制都按 pathname+search 逐字比，这张表不许例外',
  ).toHaveLength(1)
  // ④ slug 一侧多出整格（新钉桩锚点全 0 却没登记）
  expect(
    trivialKeyFindings(
      { 'x-anchor': ['/api/a'], 'y-anchor': ['/api/z'] },
      { 'x-anchor': ['/api/a'] },
    ),
    '新钉桩锚点有平凡键却没登记 ⇒ 判据必须点名那一格',
  ).toEqual(['y-anchor：实测平凡键 ["/api/z"] ≠ 登记表 []'])
})

/**
 * 修复轮 1 的 I-2：「这一页发出的每条 GET 都被声明过」那条断言**有牙**的证据。
 * 【形状】判据本体是纯函数 `findUnpinnedApiGets`（两侧都按 pathname+search 逐字比），
 *   `assertAllApiGetsPinned` 只是把它的清单包成红话——所以反例喂**合成 PinState**，
 *   不去给活锚点临时加 pinPaths（那会把入库 fixture 拖进一次真跑测，且断言会被下一次重录漂掉）。
 * 【两侧都要钉】漏钉红（本条主张家）、全集被覆盖时绿（否则"永不成立的判据比没判据更坏"）、
 *   未声明 pinPaths 的锚点整条跳过（`login` 那一格的既有口径）。
 */
test('钉桩覆盖断言有牙：漏一条键红、逐字含 search、未声明 pinPaths 的锚点跳过（修复轮 1 的 I-2）', () => {
  const stateOf = (observed: string[]): PinState => ({
    served: observed,
    unpinnedTodayBearing: [],
    observedApiGets: observed,
    shiftDays: 0,
  })
  const anchorOf = (pinPaths: string[] | undefined) =>
    ({
      slug: 'synthetic-i2',
      title: 'synthetic',
      role: 'STUDENT',
      path: '/x',
      pinPaths,
    }) as AnchorSpec

  // ① 零基：全集 ⊆ pinPaths ⇒ 不抛
  const ok = anchorOf(['/api/a', '/api/b?page=1&size=100'])
  expect(() =>
    assertAllApiGetsPinned(ok, stateOf(['/api/a', '/api/b?page=1&size=100'])),
  ).not.toThrow()
  // ② 漏一条（真实漂法：产品在这一页新加一条 GET，或把 size=100 改成别的 query）
  let threw = ''
  try {
    assertAllApiGetsPinned(
      anchorOf(['/api/a']),
      stateOf(['/api/a', '/api/notifications?page=1&size=5']),
    )
  } catch (e) {
    threw = (e as Error).message
  }
  expect(threw, '页面发了 pinPaths 之外的 GET 却放行——跨套件耦合就是这么静默回来的').toContain(
    '[钉桩覆盖]',
  )
  expect(threw, '红话必须点名漏的是哪一条键，否则下一个人回整页里猜').toContain(
    '/api/notifications?page=1&size=5',
  )
  // ③ search 逐字：把带 query 的键写成裸 pathname ⇒ 那条实测键算漏钉
  expect(
    findUnpinnedApiGets(['/api/b?page=1&size=100'], ['/api/b']),
    'pathname 相同、query 不同却算已钉——与 installPayloadPin 的匹配口径分家了',
  ).toEqual(['/api/b?page=1&size=100'])
  // ④ 未声明 pinPaths 的锚点整条跳过（login 那一格按简报"不为凑数硬钉"，不许被这条判红）
  expect(
    () => assertAllApiGetsPinned(anchorOf(undefined), stateOf(['/api/whatever'])),
    '没声明 pinPaths 的匿名锚点被这条判红——它管的是"钉了的格钉全没有"，不是"每页都必须钉"',
  ).not.toThrow()
  // ⑤ 探针键不许被钉（钉了以后 I-1 那条 canary 就绿在 fixture 上，等于没测）
  for (const a of ANCHORS.filter((x) => (x.pinPaths?.length ?? 0) > 0)) {
    expect(
      a.pinPaths ?? [],
      `${a.slug} 把 canary 探针键 ${CANARY_PROBE_PATH} 钉住了 ⇒ assertBackendCanary 从此绿在 fixture 上`,
    ).not.toContain(CANARY_PROBE_PATH)
  }
})

/**
 * 修复轮 1 的 I-1：canary 那条"真后端＋真会话仍可用"的判据**有牙**的证据。
 * 【为什么必须配正例】这条断言要拦的是"整页被 fixture 供数之后，后端下线/会话过期/契约改名都不会红"，
 *   如果它自己也永不成立（比如把 200 判成违规），那它就成了第二个假绿。
 *   所以 200 加完整信封那一侧必须给空清单，三种坏形状各点名一条理由。
 * 【它测的是判据本体】`canaryFindings(status, body)` 是纯函数；真打后端那一次由 anchors 跑测时
 *   每个钉桩锚点各跑一次（stdout 的 `[canary]` 行就是它的重放读数）。
 */
test('canary 判据有牙：401、HTML 兜页、缺 data 键三种都红，而 200 加完整信封放行（修复轮 1 的 I-1）', () => {
  // 零基（正例）：形状照后端 ApiResponse 信封，data 为空数组也算"结构在"（本条不判业务数值）
  expect(
    canaryFindings(200, JSON.stringify({ code: 200, message: 'success', data: [] })),
    '真后端正常信封被判违规 ⇒ 这条 canary 在所有锚点上都会假红，没人会信它的读数',
  ).toEqual([])
  // ① 会话过期／后端下线：401（信封本身是完整的 ⇒ 只该报状态那一条理由，
  //    这样"命中第一条就返回"与"信封判据写反"两种毛病都能被这一条分开抓住）
  const f401 = canaryFindings(401, JSON.stringify({ code: 401, message: '未认证', data: null }))
  expect(
    f401,
    '401 没被抓住——"被踢回 /login 那条守卫对钉桩锚点永久失效"这个洞原样留着',
  ).toHaveLength(1)
  expect(f401[0]).toContain('HTTP 401')
  // ② dev server 把未知路径兜成 index.html：状态 200 而体不是 JSON
  const fHtml = canaryFindings(200, '<!doctype html><html><head><title>AI 课程平台</title>')
  expect(fHtml, 'HTML 兜页放行了——只判状态等于没判').toHaveLength(1)
  expect(fHtml[0]).toContain('不是 JSON')
  // ③ 契约改名／被挪走：信封缺 data 键
  const fNoData = canaryFindings(200, JSON.stringify({ code: 200, message: 'ok' }))
  expect(fNoData, '信封里没有 data 键却放行——契约改名正落在这一族').toHaveLength(1)
  expect(fNoData[0]).toContain('data')
  // ④ 非 JSON 且非 200 同时发生 ⇒ 两条理由都要在（不许命中第一条就返回）
  expect(canaryFindings(502, '<html>Bad Gateway</html>')).toHaveLength(2)
  // ⑤ 探针键必须与两格的 pinPaths 无交集（这条用例与上面 I-2 的第⑤条同判据，两处都钉防漂）
  expect(
    CANARY_PROBE_PATH.startsWith('/api/'),
    '探针键不是 /api/ 路径 ⇒ 它根本不会经 dev server 代理',
  ).toBe(true)
})

/**
 * 修复轮 2 的 M-b：上面那条用例把三种坏形状钉住了，但**第四种没进账**——
 * `canaryFindings` 原先写的是 `parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)`，
 * 那个 `!Array.isArray` 让"合法 JSON 但顶层是数组"的响应体**整段信封检查被跳过**，一条理由都不产生
 * ⇒ 契约改名族里"后端把返回形状改成裸数组"这一支挡不住（复核席 findings M-b）。
 * 【两条都要配】负读数（裸数组必须红）配正读数（信封里套数组绝不能红）——
 *   后者是"这条 canary 在所有锚点上都会假红"的那族反例，缺了它这次最小改动就可能把门焊死。
 * 【只改这一处】探针路径、断言强度、`[钉桩内容]` 那两条降级日志、判据读数都不动。
 */
test('canary 判据对顶层裸数组体也给一条理由并点名探针键，而信封里套数组仍放行（修复轮 2 的 M-b）', () => {
  // 零基（正例）：数组落在 data 里、外层仍是 {code,message,data} 信封 ⇒ 一条都不该报
  expect(
    canaryFindings(200, JSON.stringify({ code: 200, message: 'success', data: [{ id: 1 }] })),
    '把"信封里套数组"判成违规 ⇒ 后端今天就有这种载荷，这条 canary 会全锚点假红',
  ).toEqual([])
  // 红例：顶层是裸数组（状态仍是 200，所以只靠状态判据抓不到）
  const fArr = canaryFindings(200, JSON.stringify([{ id: 1 }, { id: 2 }]))
  expect(fArr, '顶层数组放行 ⇒ "后端把返回形状改成裸数组"这一族契约漂移无人挡').toHaveLength(1)
  expect(fArr[0]).toContain('数组')
  expect(
    fArr[0],
    '理由必须点名是哪一条探针 ⇒ 否则读红话的人还得翻 CANARY_PROBE_PATH 才知道谁坏',
  ).toContain(CANARY_PROBE_PATH)
  // 状态与形状同时坏 ⇒ 两条理由都要在（不许命中第一条就返回）
  expect(
    canaryFindings(401, '[]'),
    '401 加裸数组只报了一条 ⇒ 收集改成短路，形状那半永远看不见',
  ).toHaveLength(2)
})

/**
 * Task 4c-fix-C 第 10 项：record 模式的日志与跳过必须过"flushRecord 真的执行过"这一道门。
 * 【原先错在哪】A4 把 flush 提前之后，`已写成` 日志与像素跳过仍只按 `recording` 判——全部 12 个锚点
 * 都进那条分支，其中 10 个非钉桩锚点 `flushRecord` 为 null（`__fixtures__/` 一个字节都没写），
 * 于是全量录制跑打出十个"fixture 已写成"并顺手跳过非钉桩那些的像素比对。
 * 行为反证（本棒实跑，原文见报告第 10 节）：修前 `PW_RECORD_PAYLOADS=1 ... anchors -g "login"`
 * 打出 `login/light 已写成` ＋ `login/dark 已写成` 两条谎（login 没有 pinPaths，什么都没写）；
 * 修后同一条命令 0 条 `已写成`、且真的跑了像素比对。本条是防"那道门被改回去"的入库面。
 * 【C1 修复轮 1 的 F4 收紧了三处，判据本体移到 `recordingGateFindings`】原先两半都跑在**整份文件、
 * 不剥注释**的文本上，是同轮三条源级守卫里唯一没跟上剥注释口径的：往块首插一行解释注释即假红
 * （正向半边要求 `{` 之后**紧跟** flush），而负向半边只因为 `anchors.spec.ts` 里那句
 * `if (recording) flushRecord = ...` 碰巧没写花括号才不红。现改为
 * **作用域＝`runAnchor` 函数体切片 ＋ 先剥整行注释 ＋ 红话带文件里的真行号与片段**，
 * 两种不误红情形与旧形态仍红都由下面那条 F4 用例喂内存源码钉住（`anchors.spec.ts` 一字未动）。
 */
test('录制模式的"已写成"与像素跳过只认 flushRecord（第 10 项）', () => {
  // 判据本体见 `recordingGateFindings`（F4：作用域＝runAnchor 体内、先剥注释、红话带真行号）。
  // 这里只把它的返回值判空——合成反例（插一行注释／块外写合法的 if (recording) { ／旧形态仍红）
  // 由下面那条 F4 用例喂**内存里的源码**跑同一个函数，不去改 anchors.spec.ts 落盘。
  const anchorsRaw = readFileSync(join(process.cwd(), 'e2e/visual/anchors.spec.ts'), 'utf8')
  const findings = recordingGateFindings(anchorsRaw)
  expect(
    findings,
    `录制模式那道门又松了（第 10 项／F4 收紧后的判据）。命中 ${findings.length} 条：\n  ${findings.join('\n  ')}`,
  ).toEqual([])
})

/**
 * C1 修复轮 1 的 F4（复核 RR-6 第 1、3 条／I-5）：收紧后的守卫**对两种正常改动不误红、对旧形态仍红**。
 * 【为什么这两条要入库】原先两半都跑在整份文件的不剥注释文本上——一次补解释注释或加花括号就会打出
 * 一句"旧形态回来了"的红而红话不指位置；并发代理最省事的出路就是删守卫。
 * 收紧若无负例配对，就等于"改完没人证明它不误红"，所以下面四个变异全在内存里做，
 * `anchors.spec.ts` 一字未动（围栏：改动面只有 helpers.spec.ts）。
 */
test('第 10 项守卫收紧后不误红也不空红：插注释与块外合法写法放行、旧形态仍红（F4 反例入库）', () => {
  const anchorsRaw = readFileSync(join(process.cwd(), 'e2e/visual/anchors.spec.ts'), 'utf8')
  // 现况必须绿——下面三条变异都以它为底本，这条不成立时其余三条的读数没有意义。
  expect(recordingGateFindings(anchorsRaw), '本仓现况被判违规').toEqual([])

  // ① 复核席那条最容易踩的：往 `if (flushRecord) {` 与 `await flushRecord()` 之间插一行解释注释。
  //    旧口径（整文件、不剥注释、要求"紧跟"）当场红；现口径剥掉那一行注释后仍绿。
  const withInsertedComment = anchorsRaw.replace(
    '  if (flushRecord) {\n    await flushRecord()',
    '  if (flushRecord) {\n    // 解释：先落盘再打这句，否则日志会在没写完盘时出现（A4 的次序纪律）\n    await flushRecord()',
  )
  expect(withInsertedComment === anchorsRaw, '变异① 没插入成功：本条等价复刻是空转').toBe(false)
  expect(
    recordingGateFindings(withInsertedComment),
    '往块首插一行注释被判成旧形态回来了——这条红会把下一个人推向"删守卫"',
  ).toEqual([])

  // ② 块外（准确说：`runAnchor` 作用域之外）写一个合法的 `if (recording) { ... }`。
  //    旧口径作用于整份文件，这一条会连带红；现口径只扫函数体切片，绿。
  const outsideRunAnchor = `const RECORDING_NOTE = 'x'\nfunction someOtherHelper(recording: boolean): void {\n  if (recording) {\n    console.log('  与录制分支无关的一处合法分支')\n  }\n}\n\nasync function runAnchor(recording: boolean): Promise<void> {\n  if (flushRecord) {\n    await flushRecord()\n    console.log('fixture 已写成')\n    return\n  }\n}\n`
  expect(
    recordingGateFindings(outsideRunAnchor),
    'runAnchor 体外的合法 if (recording) { 被判违规——作用域没切进函数体（复核 RR-6 第 1 条）',
  ).toEqual([])

  // ③ 旧形态必须仍红（绿了就是"收紧"变成了拆掉）：flush 不在花括号块里、日志与跳过按 recording 判。
  //    这条就是 `dee6ddc` 之前那个形状——正向半边与负向半边各抓它一半，所以应当给两条违规。
  const oldShape = `async function runAnchor(recording: boolean): Promise<void> {\n  if (flushRecord) await flushRecord()\n  if (recording) {\n    console.log('fixture 已写成')\n    return\n  }\n}\nasync function nextOne(): void {}\n`
  const oldFindings = recordingGateFindings(oldShape)
  expect(
    oldFindings.length,
    '旧形态（按 recording 提前 return、flush 不在同一块里）被判成合法——守卫被放宽成空转了',
  ).toBeGreaterThanOrEqual(2)
  // 正向半边给的定位必须说得出"块首不存在"，而不是笼统一句红（F4 的第 3 处收紧）
  expect(oldFindings[0], '正向半边的定位信息没指出块首未命中——红话又变成让人猜整文件了').toContain(
    '块首 未命中',
  )
  expect(oldFindings[1], '负向那条红话必须带文件里的真行号，别让下一个人对着整文件猜').toContain(
    '第 3 行',
  )

  // ④ 另一半的旧形态：块里根本没有 flush 调用（把 `await flushRecord()` 删掉）⇒ 正向半边红，
  //    且定位信息要指出块首在哪、flush 调用未命中。
  const noFlush = anchorsRaw.replace('    await flushRecord()\n', '')
  expect(noFlush === anchorsRaw, '变异④ 没删掉 await flushRecord()：本条等价复刻是空转').toBe(false)
  const noFlushFindings = recordingGateFindings(noFlush)
  expect(noFlushFindings.length, '块里没有 flush 调用却没判违规——正向半边没牙').toBe(1)
  expect(noFlushFindings[0]).toContain('flush 调用 未命中')
})

/* ------------------------------------------------------------------ *
 * 4c-fix-C 的 C2 步：三条守卫加固（F5 尺本体／F6 自读负向扩到所有 spec／F7 注释措辞）
 * ------------------------------------------------------------------ */

/**
 * 复核 I-1（C2 步的 F5，R-C1F-2 授权）：**这把被三条守卫共用的尺自己以前没有用例**。
 * 【为什么这条是上位形状】`isFullLineComment`／`stripCommentLines` 现在被三条负向守卫共用
 *  （F1 的自读 NOPIN、F2 的阈值扫描、F4 的第 10 项录制门）。一把被多人用的尺**没有任何断言**，
 *  意味着"把尺改窄一点（注释当代码）"会让三条守卫一起假红、"改宽一点（代码当注释）"会让三条
 *  **一起假绿**——而假绿这一侧恰好是本轮复核抓过四次的那个形状（存在性断言冒充内容断言）。
 *  本仓已经有过实证：`91f541b` 态那五条散文假红就是这把尺**生效时**才看得见的，尺本身坏了就连它一起瞎。
 * 【钉两侧加一条反例，按工单口径】命中侧＝整行 `//`、整行 `/* … *\/`、块注释续行 ` *`（三种各带缩进与
 *  制表符两版）；不命中侧＝行尾注释、行尾块注释、内容长得像注释的字符串字面量、空行；
 *  合成反例＝喂一行带行尾注释的代码，**代码部分必须仍在输出里**。
 * 【顺带钉住"它塌行号"这个口径】`findThresholdLiterals` 与 `recordingGateFindings` 的红话都要带
 *  文件里的真行号，所以它们**不**用这把尺做定位（各自逐行走）。这条今天成立、明天若有人让它
 *  保号，那两个判据的实现就该简化——所以把它钉住而不是只写在注释里。
 * 【最后一段是"尺与消费者的接线"】只断言尺本身还不够：尺改了但消费者不再调用它，用例照样绿。
 *  所以这里对两条共用它的负向守卫各验一次"注释里的写法不计数／代码行的写法计数"。
 */
test('剥注释那把尺自己有用例：整行三种形态判为注释、行尾注释不许（F5，复核 I-1）', () => {
  // —— 命中侧：三种整行形态，各带无缩进／空格缩进／制表符缩进 ——
  const commentForms = [
    '// 整行行注释',
    '  // 空格缩进的行注释',
    '\t// 制表符缩进的行注释',
    '/* 整行块注释 */',
    '  /* 缩进后的整行块注释 */',
    '\t/* 制表符缩进的整行块注释 */',
    '/** doc 块的开行 */',
    ' * 块注释的续行',
    '\t * 制表符缩进的续行',
  ]
  for (const line of commentForms) {
    expect(
      isFullLineComment(line),
      `${JSON.stringify(line)} 没被判为整行注释——尺被改窄会把注释当代码，共用它的三条负向守卫一起假红`,
    ).toBe(true)
  }
  // —— 不命中侧：真代码一行都不许被排掉（这一侧失效的症状是**假绿**，比假红贵）——
  const codeForms = [
    'code(); // 行尾注释',
    '  const a = 1 /* 行尾块注释 */',
    "  return '/* 这是字符串字面量，不是注释 */'",
    '  if (x) { // 带条件与花括号的真代码行',
    'const kept = 1',
    '',
  ]
  for (const line of codeForms) {
    expect(
      isFullLineComment(line),
      `${JSON.stringify(line)} 被判成了注释——尺被放宽会把真代码排掉，三条负向守卫当场变成假绿`,
    ).toBe(false)
  }
  // —— 工单点名要的合成反例：喂一行行尾注释，断言**代码部分仍留存** ——
  const mixed = ['const kept = compute(1); // 只解释为什么取 1', '// 这一行整行都是注释'].join('\n')
  const stripped = stripCommentLines(mixed)
  expect(stripped, '行尾注释那行的代码部分被一起剥掉了——尺放宽了（假绿那一侧）').toContain(
    'const kept = compute(1);',
  )
  expect(stripped, '整行注释还留在输出里——尺改窄了（假红那一侧）').not.toContain(
    '这一行整行都是注释',
  )
  // 行尾那一截**整行原样留存**：这把尺的口径是"行首就是注释标记"，不是"删掉标记之后的部分"。
  // 反过来写（连行尾注释一起吃掉）会把 `'http://x'` 之后的真违规一起遮掉——那是 F2 记过的假绿代价。
  expect(
    stripped.split('\n')[0],
    '行尾注释被一起吃掉了——口径已不是"只剥整行注释"，F2 那条"同代码行尾注释仍会红"的残留说明要重写',
  ).toBe('const kept = compute(1); // 只解释为什么取 1')
  // —— 口径另一半：它**塌行号**（两个带真行号的判据因此都不用它做定位，见各自注释）——
  expect(
    stripped.split('\n'),
    'stripCommentLines 不再塌行号了——F2／F4 那两条"红话带真行号"的口径要重定',
  ).toHaveLength(1)

  // —— 尺与共用它的两条守卫的**接线**（缺这一段，"三条守卫共用一把尺"仍是注释里的声称）——
  // ① F1 那一侧：同一条被禁写法**写成整行注释**时，匹配器本身仍然认它 ⇒ 消费侧必须先过这把尺，
  //    否则两份 spec 里那些解释历史的注释会让守卫当场假红（11.4 节实测过：raw 命中、stripped 不命中）。
  const bannedAsComment = '  // const legacy = process.env[NOPIN_ENV]'
  expect(
    selfReadsNopinEnv(bannedAsComment),
    '匹配器现在连注释里的写法都不认了——那 F1 的四种拼法断言先红，这把尺也就没东西可排了',
  ).toBe(true)
  expect(
    selfReadsNopinEnv(stripCommentLines(bannedAsComment)),
    '剥完注释仍认出来——F1 那条负向守卫没接上这把尺，散文注释会让它假红',
  ).toBe(false)
  // ② F2 那一侧：带**行尾注释**的违规行仍必须被报出来（它是代码行，不许因为尾巴上有注释就免检）。
  const ratioNum = 0.4
  const trailingViolation = `  const opts = { threshold: ${ratioNum} } // 放宽一档`
  expect(
    isFullLineComment(trailingViolation),
    '行尾注释让这一整行被判成注释了——阈值扫描会放过它（假绿），本条口径要从"整行"改成别的判据',
  ).toBe(false)
  expect(
    findThresholdLiterals([{ file: 'synthetic-e.spec.ts', text: trailingViolation }]),
    '带行尾注释的阈值字面量没被报出来——F2 的扫描与这把尺脱钩了',
  ).toHaveLength(1)
  // ③ F4 那一侧：同一条判据在 `runAnchor` 体内插一行**整行注释**必须仍绿（F4 用例①的底本口径）。
  expect(
    recordingGateFindings(
      "async function runAnchor(): void {\n  if (flushRecord) {\n    // 解释为什么先落盘\n    await flushRecord()\n    console.log('fixture 已写成')\n    return\n  }\n}\nconst NEXT = 1\n",
    ),
    '往块首插一行整行注释被判违规——第 10 项那条守卫也没接上这把尺（F4 的收紧白做）',
  ).toEqual([])
})

/**
 * C2 步的 F6（R-C1F-1 第 3 条）：**扩面之后到底有没有牙**。
 * 【为什么光靠上一条用例不够】把作用域从"两个具名文件"改成"除豁免外的所有 spec"之后，
 * 现况全绿——而这份绿可能只因为新进来的那两份（motion-observation／smoke）**恰好没写**那种写法。
 * 这正是复核 I-3 那一族的形状："覆盖面扩了，但没人验过它对新增内容成立"。
 * 【所以这里对每份被扫文件各塞一种被禁拼法】注入在**内存里**做，四种拼法轮着来（F1 那一条正则
 * 认全四种），断言剥过注释的代码面当场认出来。任何 spec 文件一字未动、不落盘。
 * 【每条都先证"注入真的发生了"】`injected === raw` 必须先为假——否则"没被拦住"会因为字符串
 * 根本没变而假成立（F3 那两条等价复刻同款纪律）。
 */
test('自读 NOPIN 的作用域扩面后有牙：四份被扫 spec 各塞一种拼法都当场红（F6 反例入库）', () => {
  const dir = join(process.cwd(), 'e2e/visual')
  const scanned = listSpecFiles(dir).filter((f) => !NOPIN_SELF_READ_EXEMPT.includes(f))
  expect(
    scanned.length,
    '被扫面塌到 3 份以下——本条只对"除豁免文件外的所有 spec"这个作用域有意义',
  ).toBeGreaterThanOrEqual(3)
  const spellings = [
    'process.env.PW_VISUAL_NOPIN === "1"',
    'process.env[NOPIN_ENV]',
    'process.env["PW_VISUAL_NOPIN"]',
    "process.env[ 'PW_VISUAL_NOPIN' ]",
  ]
  scanned.forEach((f, i) => {
    const raw = readFileSync(join(dir, f), 'utf8')
    const spelling = spellings[i % spellings.length]
    const injected = `const legacySelfRead = ${spelling}\n${raw}`
    expect(injected === raw, `往 ${f} 的注入没发生：本条反例是空转`).toBe(false)
    expect(
      selfReadsNopinEnv(spelling),
      `${spelling} 连尺本体都不认——F1 那条四种拼法的断言先红，本条的读数没有意义`,
    ).toBe(true)
    expect(
      selfReadsNopinEnv(stripCommentLines(injected)),
      `${f} 里塞进 ${spelling} 却没被拦住——F6 的扩面只是换个说法，第三份 spec 自读 env 仍无人看`,
    ).toBe(true)
  })
})

/**
 * C2 步的 F7（Minor M-2）：`global-setup.ts` 那句口径注释滞后于实际判据 ⇒ 措辞对齐。
 * 【原先错在哪】注释写"本闸只拦 `--update-snapshots` / `-u` **两个写法**"，而 F3／第 12 项的
 * 行为级断言现在拦**三种**（多一个 `--update`）——判据变了而注释没变，读注释的人会以为
 * `--update` 能溜过去（其实不能），这属于本仓一直在清的"注释与代码各说一套"。
 * 【为什么这条守卫放在这里而不是只改注释】只改注释的话，将来再往判据里加第四种写法时，
 * 注释又会滞后一次——而没有任何东西会提醒。所以这条断言把**注释里自陈的写法条数**与
 * **实际判据里的条数**钉成同一个数：改了 `assertUpdateSnapshotsAllowed` 的 `argv.includes(...)`
 * 而没同步那句注释，这条就红。
 * 【两侧都验】注释不许少于判据（滞后，本项的病），也不许多于判据（把没拦的写法吹成拦了——
 * 那正是 `.gitattributes` 那笔过度声称的同型毛病）。
 */
test('闸门注释自陈的"拦几种写法"必须与实际判据同条数（F7，Minor M-2）', () => {
  const dir = join(process.cwd(), 'e2e/visual')
  const setupRaw = readFileSync(join(dir, 'global-setup.ts'), 'utf8')
  // 实际判据：函数体里 argv.includes(...) 拦掉的那几个旗标（逐个取出来，不数总数）
  const gateBody = sliceTopLevelFunction(setupRaw, /export function assertUpdateSnapshotsAllowed\(/)
  expect(gateBody.text, '定位不到 assertUpdateSnapshotsAllowed 的函数体——本条判据塌了').not.toBe('')
  const wanted = [...gateBody.text.matchAll(/argv\.includes\(\s*'([^']+)'\s*\)/g)]
    .map((m) => m[1])
    .sort()
  expect(
    wanted,
    '闸门实际拦的写法集合变了——那句注释与 helpers 的行为级断言都要同批改（本条红话给出实测集合）',
  ).toEqual(['--update', '--update-snapshots', '-u'])
  // 注释自陈：那句"本闸拦 … 三种写法"里逐字列出的旗标
  const claim = setupRaw.match(/本闸拦([^\n]*)/)
  expect(
    claim,
    '闸门注释里那句"本闸拦…"找不到了——措辞被改成不自陈条数，本条守卫会空转',
  ).toBeTruthy()
  const claimed = [...(claim?.[1] ?? '').matchAll(/`(-?[-\w]+)`/g)].map((m) => m[1]).sort()
  expect(
    claimed,
    `注释自陈拦 ${JSON.stringify(claimed)}，实际判据拦 ${JSON.stringify(wanted)}——两侧必须逐字相同（F7）`,
  ).toEqual(wanted)
  // 滞后形态本身也要能红：老那句"只拦两个写法"不许回来
  expect(
    setupRaw,
    '那句"本闸只拦两个写法"回来了——它比判据少一种（第 12 项之后第三种是 --update）',
  ).not.toMatch(/本闸只拦[^\n]*两个写法/)
})

/**
 * S1／R-C2-1 的入库守卫（工单 S1 第 2 条判据原文：喂一份候选 URL 清单，断言模块后缀那两类被排除、
 * 真接口被保留）。放在 F5／F6 同一族里——那两条的形状就是"尺本体 ＋ 它自己的反例"。
 * 【五组断言，缺一组这条守卫就是半瞎的】
 *   ① 判据本体在 `ALL` 那类不收窄的锚点上仍不收模块路径（这是本轮 bug 的现场），并**分层**断言反例是真的：
 *      两条模块样本都要含 api 这一段（"看起来像 API 就行"那种放宽会收它们），其中事故本体那条还要含
 *      `/api/` 那一段（改前那个按子串命中的 route 参数收的正是它）——少一层，本条就绿在样本被改干净上；
 *   ② 非 ALL 锚点的前缀收窄语义没被这次改动动坏（正例一条、误收别家前例一条）；
 *   ③ 边界：`/api` 与 `/apifoo` 都不是数据接口；
 *   ④ 外壳与会话类端点即使在 ALL 锚点下也走 fallback（否则红会挪到 /login 去，归因不到空态）；
 *   ⑤ 接线面：`anchors.spec.ts` 里那份 `page.route` 参数与判据调用由 `stubMatcherFindings` 量，
 *      真实源码必须 0 违规，另两条**内存变异**（不落盘）各自必须报出违规——
 *      没有这两条，"接线"那半边可能绿得只是因为文件恰好没人动过。
 */
test('空态桩的接管面锚在 pathname 前缀：模块两类被排除、真接口被保留，anchors 的匹配面自己也没漂（R-C2-1）', () => {
  // ① 判据本体的行为面（`'ALL'` ＝ 不做前缀收窄那一类，正是出事的那两格）
  for (const p of STUB_PATHS_API) {
    expect(apiStubTakesPathname(p, ['ALL']), `真接口没被接管——桩打空了个寂寞：${p}`).toBe(true)
  }
  for (const p of STUB_PATHS_MODULE) {
    // 反例形状必须是真的：只要路径里出现 api 这一段，"看起来像 API 就行"那种放宽就会收它（判据原文不许），
    // 而现判据按 pathname 前缀判定，两条都不收。样本若哪天连 api 都不含了，本条守卫拦的就是不存在的洞。
    expect(p, `样本已不含 api 这一段，放宽口径也不会误收它，反例塌了：${p}`).toMatch(/api/)
    expect(
      apiStubTakesPathname(p, ['ALL']),
      `接管面吃进了 dev server 的模块路径：${p}——改前就是这条把 JS 模块 fulfill 成 JSON 的`,
    ).toBe(false)
  }
  // 事故本体那条还必须落在**改前那个 route 参数**的命中面里（路径里含 /api/ 那一段），
  // 否则本轮改的就不是 R-C2-1 说的那个洞。`api.js` 那条按旧口径本来就收不到，所以这里恰好是 1 条。
  expect(
    STUB_PATHS_MODULE.filter((p) => p.includes('/api/')),
    '旧口径（按 URL 里含 /api/ 那一段命中）现在一个模块样本都不收了——那条 bug 的形状变了，判据要重定',
  ).toHaveLength(1)
  // ④ 外壳类：它们是 /api/ 前缀，但必须被第二段排除
  for (const p of STUB_PATHS_SHELL) {
    expect(
      apiStubTakesPathname(p, ['ALL']),
      `外壳/会话端点被接管了（打空它会得到 /login 重定向，红无法归因到空态）：${p}`,
    ).toBe(false)
  }
  // ② 非 ALL 锚点的收窄语义（这次改动不该碰它）
  expect(apiStubTakesPathname('/api/courses', ['/api/courses'])).toBe(true)
  expect(
    apiStubTakesPathname('/api/courses', ['/api/admin/statistics']),
    '前缀收窄失效——其它锚点的打桩面被扩大了，矩阵那 7 格的读数要全部重算',
  ).toBe(false)
  // ③ 边界：这两个都不是数据接口
  expect(apiStubTakesPathname('/api', ['ALL']), '/api 本身不是接口前缀').toBe(false)
  expect(apiStubTakesPathname('/apifoo/bar', ['ALL']), '/apifoo 不是 /api/ 前缀').toBe(false)

  // ⑤ 接线面：读 anchors 的源码，真实文件必须 0 违规
  const dir = join(process.cwd(), 'e2e/visual')
  const anchorsRaw = readFileSync(join(dir, 'anchors.spec.ts'), 'utf8')
  const findings = stubMatcherFindings(anchorsRaw)
  expect(
    findings,
    `空态桩的匹配面不再锚定到 pathname 前缀（或判据本体被抄了第二份）——R-C2-1 那条 bug 回来了。\n  ${findings.join(
      '\n  ',
    )}`,
  ).toEqual([])

  // 变异一：route 参数换回按子串命中的通配串（正是改前那一行），且回调里没有第二道守卫
  const LEGACY_GLOB = '**/api/**'
  const m1 = anchorsRaw.replace(/page\.route\([^,]+,/, `page.route('${LEGACY_GLOB}',`)
  expect(m1, '变异一没落地（route 声明的写法变了，本条反例要跟着改）').not.toBe(anchorsRaw)
  const f1 = stubMatcherFindings(m1)
  expect(
    f1.length,
    `变异一（换回按子串命中的通配串）本该红，却报 ${f1.length} 条——接线那半边是假保护：${JSON.stringify(
      f1,
    )}`,
  ).toBe(1)
  expect(f1[0], '变异一的红话必须点名两种合法写法都不满足，否则下一个人不知道往哪改').toContain(
    "pathname.startsWith('/api/')",
  )

  // 变异二：把判据本体调用换回体内那份旧收窄（route 参数仍是锚定的，漂的这把尺在第二段）
  const m2 = anchorsRaw.replace(
    'apiStubTakesPathname(u.pathname, prefixes)',
    "prefixes.includes('ALL') || prefixes.some((p) => u.pathname.startsWith(p))",
  )
  expect(m2, '变异二没落地（stubApiToEmpty 的调用写法变了，本条反例要跟着改）').not.toBe(anchorsRaw)
  const f2 = stubMatcherFindings(m2).join('\n')
  expect(f2, '变异二没被抓住——判据本体可以从调用点被静默摘掉而全表照绿').toContain(
    '没走 data-arrival 那份判据本体',
  )
  expect(f2, '变异二没被抓住——ALL 判定抄回 anchors 就是判据的第二把尺').toContain(
    '重新写了一遍 ALL 判定',
  )
})
