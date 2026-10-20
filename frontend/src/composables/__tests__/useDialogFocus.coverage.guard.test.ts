import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  BASELINE_TAG_BY_MARKER,
  KNOWN_UNWIRED_EL_DRAWER_FILES,
  LEGACY_OVERLAY_MARKERS,
  OVERLAY_MARKERS,
  PRIMITIVE_OVERLAY_MARKERS,
  PRIMITIVE_REQUIRED_CALLS,
  SRC,
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
    expect(classifyOverlays([src('a.vue', '<!-- <el-dialog /> -->')])).toEqual([])
    // 真实反例（spec §11.7 第②点）：这两个文件的注释散文里写着 "v-loading 不能绑在 <el-dialog> 上"
    for (const rel of [
      'views/teacher/assessment/components/EpStatsDialog.vue',
      'views/teacher/cockpit/CourseExams.vue',
    ]) {
      const raw = readFileSync(join(SRC, rel), 'utf8')
      expect(
        countMarker(htmlOnly(raw), '<el-dialog'),
        `${rel} 应当至少有一个真弹层`,
      ).toBeGreaterThan(0)
      expect(
        countMarker(stripOverlayNoise(raw), '<el-dialog'),
        `${rel}：注释散文里的 <el-dialog 被算进了使用点（原始 grep 的 65 就是这么来的）`,
      ).toBeLessThan(countMarker(raw, '<el-dialog'))
    }
  })

  it('JS 块注释与其续行不计入（只剥 HTML 注释会得到 89，不是 82）', () => {
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
    // 同一个机制的真仓面：两个原语自己的说明块里有 <Modal> / <el-drawer> 字样
    for (const rel of ['components/ui/Modal.vue', 'components/ui/Drawer.vue']) {
      const raw = readFileSync(join(SRC, rel), 'utf8')
      expect(
        countMarker(htmlOnly(raw), '<Modal') + countMarker(htmlOnly(raw), '<el-drawer'),
      ).toBeGreaterThan(0)
      expect(
        overlayTotals(classifyOverlays([src(rel, raw)])).tags,
        `${rel}：原语的说明块被当成调用点，地板值就会把护栏自己算进去`,
      ).toBe(0)
    }
  })

  it('地板表与缺口表是同一口径的产物（合计 82、缺口 4 个）', () => {
    expect(Object.values(BASELINE_TAG_BY_MARKER).reduce((a, b) => a + b, 0)).toBe(82)
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
