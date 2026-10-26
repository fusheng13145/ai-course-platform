import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * Task 13：焦点覆盖率护栏的扫描逻辑（本文件只被 `useDialogFocus.coverage.test.ts` 与
 * `useDialogFocus.coverage.guard.test.ts` 消费；抽出来的目的是"测护栏"不必真的破坏仓库）。
 *
 * ## 为什么护栏要管两套义务（spec §9.5 第 5 条、§11.5 R-T9-7）
 *
 * 弹层今天有两种实现，而它们的**焦点保证落点不同**：
 *
 * 1. `el-dialog` / `el-drawer`（legacy 形状，实测 63 ＋ 4 个开标签）：Element Plus 不认识本仓的
 *    `useDialogFocus`，首焦与关闭返还必须由**调用点**手接 `ref="dialogRef"` ＋ `useDialogFocus(...)`。
 *    ⇒ 义务一 = 每个含 legacy 开标签的文件自己必须出现 `useDialogFocus(`。
 * 2. `ui/Modal` / `ui/Drawer`（自持形状，实测 13 ＋ 2 个开标签）：Task 9／10B／10C 之后，
 *    **原语组件内部**自己调 `useDialogFocus` ＋ `useFocusTrap` ＋ `useOverlayScrollLock`，
 *    调用点**不传 ref**（R-T9-7 把 Phase 4 的迁移规矩定为"删掉调用方的 `ref` 与 `useDialogFocus` 两行"，
 *    实测全仓 `<Modal` 开标签上 `ref=` 零命中）。
 *    ⇒ 义务二 = 原语文件自身必须出现那三个调用；调用点没有接线**不是**缺口。
 *
 * 两套义务不能合成一套：拿义务一去要求 `<Modal>` 调用点接线，会在我们刚建好的东西上判红，
 * 而面对红灯最省事的做法是放宽护栏（正是本任务要防的事）；拿义务二去放 legacy 调用点，
 * 则 63 处真实接线的既有保障（D2-7）当场作废。所以本文件把两侧各自做成可单独翻红的断言。
 */

export const SRC = join(process.cwd(), 'src')

/**
 * **唯一的计数口径**（Task 13 的地板值就从它导出；换口径就得重测地板，别在同一个文件里留第二套数）。
 *
 * 剥三类噪声：
 * - HTML 注释 `<!-- … -->`：`EpStatsDialog.vue` 与 `CourseExams.vue` 各有散文写着
 *   "v-loading 不能绑在 `<el-dialog>` 上"。⇒ 原始 `git grep -o "<el-dialog"` 得 **65**，
 *   其中 2 处是注释散文、不是使用点（按 65 定地板＝86，本会话已否证过一次，见 spec §11.7）。
 * - JS 块注释 `/* … *\/` 与其续行（行首只余 `*` 的那些行）：`Modal.vue`／`Drawer.vue` 的说明块里
 *   写着 `<Modal>`／`<el-drawer>` 字样。**只剥 HTML 注释不剥这三行**时 `<Modal` 会数到 19、
 *   `<el-drawer` 数到 5、合计虚高到 **89**——护栏改版前只剥 HTML 注释，所以它口径下的数本来就不是 82。
 *   真仓的 89／82 用 spec §11.7「弹层计数口径」那条命令重放；`useDialogFocus.coverage.guard.test.ts`
 *   的"第二口径"用例只钉机制（喂合成样本，故不随 Phase 4 迁移变红）。
 *
 * 实跑（`cd frontend`，spec §11.7 的脚本；两个反直觉点都已由本口径消解）：
 * `{"<el-dialog":63,"<el-drawer":4,"<Modal":13,"<Drawer":2,"_合计":82,"_文件数":73}`
 *
 * 第四层（比 §11.7 那三条多出来的一条，**读数不变**）：整行 `//` 行注释。加它的理由是接线判定——
 * 把 `useDialogFocus(d, v)` 用 `//` 注释掉，前三层剥完那行还在文本里、护栏会照样绿；
 * 而它改变不了开标签计数（实测两种剥法同为 82 / 73，命令见 guard 用例「行注释层是读数中性的」）。
 */
export function stripOverlayNoise(text: string): string {
  return text
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\*.*$/gm, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

/** 义务一：调用点必须手接 `useDialogFocus` 的两种开标签。 */
export const LEGACY_OVERLAY_MARKERS = ['<el-dialog', '<el-drawer'] as const

/** 义务二：接线由原语自己负责的两种开标签（`Modal.vue`／`Drawer.vue` 按 marker 反查）。 */
export const PRIMITIVE_OVERLAY_MARKERS = ['<Modal', '<Drawer'] as const

/**
 * 扫描键＝弹层实现的并集。迁移把 `<el-dialog` 换成 `<Modal`，**总数不变**，
 * 所以护栏既不会因"扫不到东西"跌破防空转下限，也不会因"扫不到就不检查"而静默失效。
 * 小写/kebab 变体（`<modal`、`<ui-modal`、`<drawer`）实测全仓 0 命中（命令见 guard 用例），
 * 故不纳入本表——纳入就是往这个口径里加从未被测过的键。
 */
export const OVERLAY_MARKERS: readonly string[] = [
  ...LEGACY_OVERLAY_MARKERS,
  ...PRIMITIVE_OVERLAY_MARKERS,
]

/**
 * 原语自持接线的三件事。少任何一件，`aria-modal="true"` 与"键盘可达 100%"都是空话，
 * 而调用点看起来一切正常（它本来就不该有接线）——所以这一侧只能扫原语文件。
 */
export const PRIMITIVE_REQUIRED_CALLS = [
  'useDialogFocus(',
  'useFocusTrap(',
  'useOverlayScrollLock(',
] as const

export interface PrimitiveOverlay {
  /** 调用点扫描键（同时也是这类原语的代表 marker） */
  marker: string
  /** 原语文件相对 SRC 的路径 */
  componentFile: string
}

export const PRIMITIVE_OVERLAYS: readonly PrimitiveOverlay[] = [
  { marker: '<Modal', componentFile: 'components/ui/Modal.vue' },
  { marker: '<Drawer', componentFile: 'components/ui/Drawer.vue' },
]

export interface OverlaySource {
  file: string
  text: string
}

export interface OverlayRecord {
  file: string
  /** 该文件里所有弹层**开标签出现次数**之和（按次数计、不按"命中几种 marker"计，见 guard 用例） */
  tags: number
  legacyTags: number
  primitiveTags: number
  byMarker: Record<string, number>
  /** 该文件是否出现 `useDialogFocus(` 调用（剥噪声后的文本上判定） */
  callSiteWiring: boolean
}

export function countMarker(text: string, marker: string): number {
  return text.split(marker).length - 1
}

/** 读全仓 `.vue` 的**原样**文本（跳过 `__tests__`，那里的弹层是测试夹具、不是产品弹层）。 */
export function readVueSources(dir: string = SRC, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue
      readVueSources(full, out)
      continue
    }
    if (full.endsWith('.vue')) out.push(full)
  }
  return out
}

/**
 * 返回未剥噪声的原文——口径由 `classifyOverlays` 统一施加一次。
 * 谁再把口径提前施在别处，就会在 `classifyOverlays` 里被施第二遍（对本仓文件是幂等的，
 * 但"两处各自决定口径"正是本任务要防的失败模式，所以只留一处）。
 */
export function readOverlaySources(dir: string = SRC): OverlaySource[] {
  return readVueSources(dir).map((file) => ({
    file: relative(SRC, file).replace(/\\/g, '/'),
    text: readFileSync(file, 'utf8'),
  }))
}

/** 按 marker 分类计数；`sources` 缺省读全仓，传入合成样本时可以单测这套逻辑。 */
export function classifyOverlays(sources: OverlaySource[] = readOverlaySources()): OverlayRecord[] {
  return sources
    .map(({ file, text }) => {
      const stripped = stripOverlayNoise(text)
      const byMarker: Record<string, number> = {}
      let legacyTags = 0
      let primitiveTags = 0
      for (const m of OVERLAY_MARKERS) {
        const n = countMarker(stripped, m)
        if (n > 0) byMarker[m] = n
        if ((LEGACY_OVERLAY_MARKERS as readonly string[]).includes(m)) legacyTags += n
        if ((PRIMITIVE_OVERLAY_MARKERS as readonly string[]).includes(m)) primitiveTags += n
      }
      return {
        file,
        tags: legacyTags + primitiveTags,
        legacyTags,
        primitiveTags,
        byMarker,
        callSiteWiring: stripped.includes('useDialogFocus('),
      }
    })
    .filter((r) => r.tags > 0)
}

export interface OverlayTotals {
  files: number
  tags: number
  byMarker: Record<string, number>
}

export function overlayTotals(records: OverlayRecord[]): OverlayTotals {
  const byMarker: Record<string, number> = {}
  for (const m of OVERLAY_MARKERS) byMarker[m] = 0
  let tags = 0
  for (const r of records) {
    tags += r.tags
    for (const m of OVERLAY_MARKERS) byMarker[m] += r.byMarker[m] || 0
  }
  return { files: records.length, tags, byMarker }
}

/**
 * 义务一的缺口：含 legacy 开标签却没有调用点接线的文件。
 * el-dialog 与 el-drawer 分开返回，因为后者今天有一张已知缺口清单（见下）。
 */
export function legacyOffenders(records: OverlayRecord[]): {
  elDialog: string[]
  elDrawer: string[]
} {
  const elDialog: string[] = []
  const elDrawer: string[] = []
  for (const r of records) {
    if (r.callSiteWiring) continue
    if ((r.byMarker['<el-dialog'] || 0) > 0) elDialog.push(r.file)
    if ((r.byMarker['<el-drawer'] || 0) > 0) elDrawer.push(r.file)
  }
  return { elDialog, elDrawer }
}

/**
 * 集合差的双向检查（护栏自己的豁免表必须双向，否则它就是一张只增不减的通行证）：
 * `novel` ＝ 缺口表里没有的新缺口（必须为空）；`stale` ＝ 表里已经不再成立的那些条目（必须为空）。
 */
export function diffAgainstAllowlist(
  offenders: readonly string[],
  allowlist: readonly string[],
): { novel: string[]; stale: string[] } {
  return {
    novel: offenders.filter((f) => !allowlist.includes(f)),
    stale: allowlist.filter((f) => !offenders.includes(f)),
  }
}

/**
 * 义务二：原语文件自身缺哪一件接线（返回空数组即三项俱全）。
 * 传入的是**已剥噪声**的原语文本——剥噪声这步很重要，否则注释里那句
 * "useDialogFocus 的 watch 不带 immediate" 会让删掉真调用也照样绿。
 */
export function missingPrimitiveCalls(
  strippedText: string,
  requiredCalls: readonly string[] = PRIMITIVE_REQUIRED_CALLS,
): string[] {
  return requiredCalls.filter((c) => !strippedText.includes(c))
}

/**
 * R-T9-7 的迁移规矩（LEDGER `P0b-MODAL-04` 结论栏同款措辞）：搬到 `<Modal>`／`<Drawer>` 时
 * **删掉**调用方的 `ref` 与 `useDialogFocus(...)` 两行。留在原地的接线是**死线**——
 * `useDialogFocus.ts` 读 `dialogRef.value.$el`，而 Modal 实例的 `$el` 是 nodeType 8 的
 * teleport 锚点注释、没有 `querySelector` ⇒ 原语静默 return，不报错也不红（实测记在 `P0b-MODAL-04`）。
 * 只在"该文件的弹层全是原语"时判红：Phase 4 分批迁移中同一文件两种实现并存是合法中间态，
 * 那时那行接线属于另一个还没搬走的 `el-dialog`，不归本条管。
 */
export function primitiveOnlySitesWithDeadWiring(records: OverlayRecord[]): string[] {
  return records
    .filter((r) => r.primitiveTags > 0 && r.legacyTags === 0 && r.callSiteWiring)
    .map((r) => r.file)
}

/* ------------------------------------------------------------------ *
 * 地板值（口径同上，实跑于 Task 13 开工时；来源＝spec §11.7 的脚本，读数 73 / 82）
 * ------------------------------------------------------------------ */

/** Task 13 落地时逐 marker 的实测开标签数——Phase 4 每迁移一批就按同一命令重测、只改这张表。 */
export const BASELINE_TAG_BY_MARKER: Readonly<Record<string, number>> = {
  '<el-dialog': 59,
  '<Modal': 17,
  '<Drawer': 2,
  '<el-drawer': 4,
}

/** 弹层开标签总数地板＝**只升不降**：一个 `<el-dialog` 变成一个 `<Modal` 时总数不变，下降即有弹层凭空消失。 */
export const EXPECTED_OVERLAY_MIN = 82

/** 含弹层的 `.vue` 文件数地板，同样只升不降。 */
export const EXPECTED_OVERLAY_COMPONENT_MIN = 73

/**
 * 已知缺口清单（`<el-drawer>` 的 4 个使用者）：旧扫描键只有 `<el-dialog`，抽屉从来不在射程内
 * ——spec §9.5 第 5 条与计划 Task 13 题注指出的就是这 4 个**真实**未接线点（不是误报）。
 * 本棒不许改 view（Task 13 的许可面只有护栏），所以它登记成一张**只许清空**的表：
 * ① 新增一个未接线的 el-drawer ⇒ `novel` 非空即红；② 谁把这 4 处补了 `useDialogFocus` 或改用
 * `ui/Drawer` ⇒ `stale` 非空即红，必须同时删掉这行条目。⇒ 它既能拦退化，也不给"豁免表悄悄长大"留缝。
 * EP 2.14.7 的 `el-drawer` 自己挂了 focus-trap（`node_modules/element-plus/es/components/drawer/src/`
 * `drawer.vue_vue_type_script_setup_true_lang.mjs` 里 `import focus_trap_default`），
 * 所以这 4 处缺的是**本仓的首焦＋关闭返还**，不是"完全没有陷阱"。
 */
export const KNOWN_UNWIRED_EL_DRAWER_FILES: readonly string[] = [
  'views/admin/governance/components/EgEventsDrawer.vue',
  'views/student/academic/components/DscDetailDrawer.vue',
  'views/student/course/components/TaskDrawer.vue',
  'views/teacher/cockpit/components/CdsDetailDrawer.vue',
]
