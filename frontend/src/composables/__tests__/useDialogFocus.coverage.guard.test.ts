import { describe, it, expect } from 'vitest'
import {
  BASELINE_TAG_BY_MARKER,
  KNOWN_UNWIRED_EL_DRAWER_FILES,
  LEGACY_OVERLAY_MARKERS,
  OVERLAY_MARKERS,
  PRIMITIVE_OVERLAY_MARKERS,
  PRIMITIVE_REQUIRED_CALLS,
  classifyOverlays,
  countMarker,
  diffAgainstAllowlist,
  legacyOffenders,
  missingPrimitiveCalls,
  overlayTotals,
  primitiveOnlySitesWithDeadWiring,
  readOverlaySources,
  stripOverlayNoise,
} from './coverage-lib'

/**
 * 测**护栏本身**（计划 Task 13 Step 2）。逻辑都抽在 `coverage-lib.ts` 里，所以这里喂的是合成样本：
 * 仓库今天怎么漂都不影响这些用例，而每一条都对应"护栏改坏后哪种回归会溜过去"。
 * 真仓的读数在 `useDialogFocus.coverage.test.ts`，两处分工别混。
 */

const src = (file: string, text: string) => ({ file, text })
const htmlOnly = (text: string) => text.replace(/<!--[\s\S]*?-->/g, '')

describe('覆盖率护栏逻辑（Task 13）', () => {
  it('扫描键覆盖弹层的两种实现，且分成 legacy 与原语两组', () => {
    expect([...OVERLAY_MARKERS].sort()).toEqual(
      ['<Drawer', '<Modal', '<el-dialog', '<el-drawer'].sort(),
    )
    expect([...LEGACY_OVERLAY_MARKERS]).toEqual(['<el-dialog', '<el-drawer'])
    expect([...PRIMITIVE_OVERLAY_MARKERS]).toEqual(['<Modal', '<Drawer'])
    // 迁移期两种实现并存是常态：同一段模板里两种键都要命中
    const hits = classifyOverlays([
      src('a.vue', '<template><el-dialog v="d"></el-dialog><Modal v="m"></Modal></template>'),
    ])[0]
    expect(hits.byMarker['<el-dialog']).toBe(1)
    expect(hits.byMarker['<Modal']).toBe(1)
    expect(hits.legacyTags).toBe(1)
    expect(hits.primitiveTags).toBe(1)
  })

  it('按开标签**次数**计，不按命中几种标记计', () => {
    // 按种类计的话，一个含 3 个 el-dialog 的文件只算 1，"总数不降"守的就是脱钩的数字
    const r = classifyOverlays([
      src(
        'three.vue',
        '<template><el-dialog a></el-dialog><el-dialog b></el-dialog><el-dialog c></el-dialog></template>',
      ),
    ])
    expect(overlayTotals(r).tags).toBe(3)
    expect(r[0].byMarker['<el-dialog']).toBe(3)
  })

  it('HTML 注释里的标记不计入（65 与 63 的差就是这一层）', () => {
    // 形状照抄真仓的两处散文（EpStatsDialog.vue 与 CourseExams.vue 的对话框注释，都紧贴一个真开标签），
    // 但**喂合成样本、不读那两个活文件**：Phase 4 第一批就把它们搬成 <Modal>，
    // 把反例钉在将被迁移的文件上，护栏会在"迁移成功"那一刻判红、且红话说的是反的（I-1）。
    // 真仓读数只有一个归口，就是 coverage.test.ts 的逐 marker 地板表。
    const prose = [
      '<template>',
      '  <!-- v-loading 不能绑在 <el-dialog> 上：弹层内容在 teleport 的非元素根上，运行时指令无处安放 -->',
      '  <el-dialog ref="dialogRef" :model-value="modelValue" />',
      '</template>',
    ].join('\n')
    // 先钉样本形状本身：它不依赖被检的口径，所以口径一旦被动，下面的断言一定被跑到而不是被上面跳过
    expect(
      countMarker(prose, '<el-dialog'),
      '原始 grep 的 65 就是这么来的：注释散文里的 <el-dialog 被当成使用点',
    ).toBe(2)
    expect(countMarker(stripOverlayNoise(prose), '<el-dialog')).toBe(1)
    expect(classifyOverlays([src('prose.vue', prose)])[0].byMarker['<el-dialog']).toBe(1)
    expect(classifyOverlays([src('a.vue', '<!-- <el-dialog /> -->')])).toEqual([])
  })

  it('JS 块注释与其续行不计入（只剥 HTML 注释会得到 89，不是 82）', () => {
    // 先钉"护栏把自己算进地板"这个害处本身，再钉机制：口径被改坏时这条一定跑到（见变异记录）
    const docOnly = [
      '<script setup>',
      '/**',
      ' * 用法：<Modal title="x">内容</Modal>',
      ' * Phase 4 要把 4 处裸 <el-drawer> 迁到这里',
      ' */',
      '</script>',
      '<template><div class="drawer" /></template>',
    ].join('\n')
    expect(
      overlayTotals(classifyOverlays([src('doc-only.vue', docOnly)])).tags,
      '原语的说明块被当成调用点，地板值就会把护栏自己算进去',
    ).toBe(0)
    // 上面那行就是"第二口径 89、现口径 82"的机制本身。真仓的载体是 Modal.vue 与 Drawer.vue 的
    // 说明块（`<Modal wide>`／`<el-drawer>` 的散文），同样**不在这里读那两个活文件**：
    // 护栏自己的注释一改就红、红的是散文不是护栏，而 Phase 4 迁移一定会重写它们（I-1）。
    const doc = [
      '/**',
      ' * 用法：',
      ' * <Modal title="x">内容</Modal>',
      ' * 另见 <el-drawer> 的说明',
      ' */',
    ].join('\n')
    expect(stripOverlayNoise(doc)).not.toContain('<Modal')
    expect(stripOverlayNoise(doc)).not.toContain('<el-drawer')
    expect(
      countMarker(htmlOnly(doc), '<Modal'),
      '第二口径必须能在同一份样本上数出 <Modal',
    ).toBeGreaterThan(0)
    expect(
      countMarker(htmlOnly(docOnly), '<Modal') + countMarker(htmlOnly(docOnly), '<el-drawer'),
      '只剥 HTML 注释的第二口径应当能在这里数到 2——数不到就说明两口径已同化，89 那个岔数不再成立',
    ).toBe(2)
  })

  it('行注释层是读数中性的：加了它 82 / 73 不变，但被 // 注释掉的接线不再算数', () => {
    const threeLayer = (t: string) =>
      t
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\*.*$/gm, '')
    const sources = readOverlaySources()
    const totalsOf = (strip: (t: string) => string) =>
      overlayTotals(classifyOverlays(sources.map((s) => src(s.file, strip(s.text)))))
    expect(
      totalsOf(threeLayer).tags,
      '三层与四层剥离读到不同的开标签总数——说明有 <Modal> / <el-drawer> 之类字样落进了整行 // 注释里，' +
        '这时地板值必须重测、口径不能悄悄改',
    ).toBe(totalsOf(stripOverlayNoise).tags)
    expect(totalsOf(threeLayer).files).toBe(totalsOf(stripOverlayNoise).files)
    // 剩下的是这一层存在的理由本身：被 // 注释掉的接线不能算已接线
    const commented = '<template><el-dialog ref="d"></el-dialog></template>\n//useDialogFocus(d, v)'
    expect(threeLayer(commented)).toContain('useDialogFocus(')
    expect(stripOverlayNoise(commented)).not.toContain('useDialogFocus(')
    expect(legacyOffenders(classifyOverlays([src('c.vue', commented)])).elDialog).toEqual(['c.vue'])
  })

  it('地板表与缺口表是同一口径的产物（合计 83、缺口 4 个）', () => {
    expect(Object.values(BASELINE_TAG_BY_MARKER).reduce((a, b) => a + b, 0)).toBe(83)
    expect(KNOWN_UNWIRED_EL_DRAWER_FILES).toHaveLength(4)
  })

  it('legacy 调用点漏接 useDialogFocus 会被列为 offender', () => {
    const r = classifyOverlays([
      src('bare.vue', '<template><el-dialog v="d"></el-dialog></template>'),
      src(
        'wired.vue',
        '<template><el-dialog ref="d"></el-dialog></template><script setup>useDialogFocus(d, v)</script>',
      ),
    ])
    expect(legacyOffenders(r).elDialog).toEqual(['bare.vue'])
  })

  it('原语调用点没有接线不算缺口（义务一与义务二的分界）', () => {
    const r = classifyOverlays([src('m.vue', '<template><Modal title="t">x</Modal></template>')])
    expect(legacyOffenders(r)).toEqual({ elDialog: [], elDrawer: [] })
    // 而它必须落在原语那一侧的检查里 —— 见 missingPrimitiveCalls 两条
  })

  it('原语缺任一件接线都能点名；接线被注释掉也算缺', () => {
    const wired = PRIMITIVE_REQUIRED_CALLS.map((c) => `${c}x)`).join('\n')
    expect(missingPrimitiveCalls(wired)).toEqual([])
    expect(missingPrimitiveCalls('useDialogFocus(d, v)\nuseOverlayScrollLock(o)')).toEqual([
      'useFocusTrap(',
    ])
    // 注释掉的接线：老口径（includes('useDialogFocus')）看不见，剥噪声后的括号口径看得见
    const commented = '/* useDialogFocus(d, v) */\nuseFocusTrap(r, o)\nuseOverlayScrollLock(o)'
    expect(stripOverlayNoise(commented)).not.toContain('useDialogFocus(')
    expect(missingPrimitiveCalls(stripOverlayNoise(commented))).toEqual(['useDialogFocus('])
  })

  it('缺口表双向都红：新增未接线文件、或表里条目已经失效', () => {
    const one = ['x.vue']
    expect(diffAgainstAllowlist(one, one)).toEqual({ novel: [], stale: [] })
    expect(diffAgainstAllowlist(['x.vue', 'new.vue'], one).novel).toEqual(['new.vue'])
    expect(diffAgainstAllowlist([], one).stale).toEqual(['x.vue'])
  })

  it('R-T9-7：纯原语调用点留死接线会红，legacy 与原语并存的迁移中间态不红', () => {
    const dead = classifyOverlays([
      src(
        'dead.vue',
        '<template><Modal></Modal></template><script setup>useDialogFocus(d, v)</script>',
      ),
    ])
    expect(primitiveOnlySitesWithDeadWiring(dead)).toEqual(['dead.vue'])
    const midMigration = classifyOverlays([
      src(
        'half.vue',
        '<template><el-dialog ref="d"></el-dialog><Modal></Modal></template><script setup>useDialogFocus(d, v)</script>',
      ),
    ])
    expect(primitiveOnlySitesWithDeadWiring(midMigration)).toEqual([])
  })

  it('小写与 kebab 变体今天 0 命中（所以不往口径里塞没测过的键）', () => {
    const sources = readOverlaySources()
    expect(sources.length).toBeGreaterThan(0)
    for (const variant of ['<modal', '<ui-modal', '<drawer']) {
      const hits = sources.reduce((a, s) => a + countMarker(stripOverlayNoise(s.text), variant), 0)
      expect(hits, `出现 ${variant} 写法时要连同扫描键与地板一起重测`).toBe(0)
    }
  })
})
