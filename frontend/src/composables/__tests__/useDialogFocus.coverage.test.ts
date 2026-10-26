import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  BASELINE_TAG_BY_MARKER,
  EXPECTED_OVERLAY_COMPONENT_MIN,
  EXPECTED_OVERLAY_MIN,
  KNOWN_UNWIRED_EL_DRAWER_FILES,
  PRIMITIVE_OVERLAYS,
  classifyOverlays,
  diffAgainstAllowlist,
  legacyOffenders,
  missingPrimitiveCalls,
  overlayTotals,
  primitiveOnlySitesWithDeadWiring,
  stripOverlayNoise,
  SRC,
} from './coverage-lib'

/**
 * D2-7 弹层焦点覆盖率守护 ＋ Task 13 的"实现无关"改写（Phase 4 的准入门，spec §9.5 第 5 条）。
 *
 * <p>改写前：扫描键是字面量 `<el-dialog`，并硬写 `toBeGreaterThanOrEqual(50)` 防空转。
 * Phase 4 把 63 处弹层迁向 `ui/Modal` 会同时踩中两条失败——扫描数跌破 50（判成"扫描退化"）、
 * 或"扫不到就不检查"而静默通过。**改法是扩口径，不是降阈值。**</p>
 *
 * <p>改写后是**两套义务**（为什么要两套、各自怎么翻红，`coverage-lib.ts` 文件头写全了）：
 * ① `el-dialog`／`el-drawer` 靠调用点手接 `useDialogFocus(...)` ⇒ 扫每个使用文件自己；
 * ② `ui/Modal`／`ui/Drawer` 由原语内部接线（Task 9／10B／10C），调用点**不该**有接线
 * ⇒ 扫那两个原语文件里的 `useDialogFocus(` ／ `useFocusTrap(` ／ `useOverlayScrollLock(`。
 * 拿①去要求②会在刚建好的自持接线上误判红，拿②去放宽①就等于无声拆掉 63 处存量保障。</p>
 *
 * <p>计数口径只有一套，就是 `coverage-lib.ts` 的 `stripOverlayNoise`
 * （HTML 注释 ＋ JS 块注释 ＋ 块注释续行）。地板值 82／73 与逐 marker 的 63／13／2／4 都从它导出，
 * 重放命令（`cd frontend`）：
 * `node -e` 那份脚本在 spec §11.7「弹层计数口径」小节，读数是
 * `{"<el-dialog":63,"<Modal":13,"<Drawer":2,"<el-drawer":4,"_合计":82,"_文件数":73}`。
 * 别在别处再数一遍——本会话就出现过 65（原始 grep，含注释散文）与 89（只剥 HTML 注释）两个岔数。</p>
 */

const records = classifyOverlays()
const totals = overlayTotals(records)

describe('Task 13 弹层焦点覆盖率护栏（实现无关）', () => {
  it('地板：弹层开标签总数只升不降（现值 82，口径见 coverage-lib 的 stripOverlayNoise）', () => {
    expect(
      totals.tags,
      `弹层开标签总数 ${totals.tags} 低于地板 ${EXPECTED_OVERLAY_MIN}——迁移过程中总数只升不降，` +
        '下降意味着有弹层凭空消失或扫描退化。',
    ).toBeGreaterThanOrEqual(EXPECTED_OVERLAY_MIN)
  })

  // 两条地板分开放：红的含义不同——总数那条说的是"有弹层消失"，文件数那条说的是"扫描逻辑退化"。
  // 并成一个 it 时，一次失败只给一个消息，排查会先怀疑错的那半边。
  it('地板：含弹层的 .vue 文件数只升不降（现值 74）', () => {
    expect(
      totals.files,
      `含弹层的 .vue 文件数 ${totals.files} 低于地板 ${EXPECTED_OVERLAY_COMPONENT_MIN}——扫描逻辑可能退化。`,
    ).toBeGreaterThanOrEqual(EXPECTED_OVERLAY_COMPONENT_MIN)
  })

  it('口径：逐 marker 实测数必须等于 Task 13 冻结的那张表', () => {
    expect(
      totals.byMarker,
      `逐 marker 实测数漂了：读到 ${JSON.stringify(totals.byMarker)}，地板表是 ${JSON.stringify(BASELINE_TAG_BY_MARKER)}。` +
        'Phase 4 迁移本来就会改变这张表的每一项（一个 <el-dialog 变成一个 <Modal），' +
        '所以红了就用 spec §11.7 那条命令重测、同步改 BASELINE_TAG_BY_MARKER；' +
        '只有总数那条断言不许降。不许删本条来让它绿。',
    ).toEqual(BASELINE_TAG_BY_MARKER)
  })

  it('两数同源：地板表的合计必须等于 EXPECTED_OVERLAY_MIN（82）', () => {
    const baselineSum = Object.values(BASELINE_TAG_BY_MARKER).reduce((a, b) => a + b, 0)
    expect(
      baselineSum,
      `地板表合计 ${baselineSum} 与 EXPECTED_OVERLAY_MIN ${EXPECTED_OVERLAY_MIN} 不等——` +
        '说明有人改了表没改地板（或反之）。这两个数必须来自同一次实跑，否则"只升不降"守的是一个没人测过的数。',
    ).toBe(EXPECTED_OVERLAY_MIN)
  })

  it('义务一：每个含 <el-dialog 的文件必须自己接 useDialogFocus', () => {
    const { elDialog } = legacyOffenders(records)
    expect(
      elDialog,
      `以下文件含 <el-dialog 却没有 useDialogFocus( 调用（补 ref="dialogRef" ＋ 原语调用；` +
        `这是 Phase 4 迁移前的既有保障，一条都不许松）：\n${elDialog.join('\n')}`,
    ).toEqual([])
  })

  it('义务一之二：<el-drawer> 的未接线点必须恰好等于已知缺口表（新增或修好都会红）', () => {
    const { elDrawer } = legacyOffenders(records)
    const { novel, stale } = diffAgainstAllowlist(elDrawer, KNOWN_UNWIRED_EL_DRAWER_FILES)
    expect(
      novel,
      `新出现未接焦点的 <el-drawer> 使用者 ${JSON.stringify(novel)}。已知缺口只有 ` +
        `${KNOWN_UNWIRED_EL_DRAWER_FILES.length} 处（见 coverage-lib 的 KNOWN_UNWIRED_EL_DRAWER_FILES 注释），` +
        '要么改用 ui/Drawer（原语内部已接），要么手接 useDialogFocus——不许把新文件加进那张表。',
    ).toEqual([])
    expect(
      stale,
      `已知缺口表里有 ${JSON.stringify(stale)} 已经不成立（已接线或抽屉已搬走）——` +
        '好事，但请把 coverage-lib 里那一行条目一起删掉，否则这张表会变成永久的通行证。',
    ).toEqual([])
  })

  it('义务二：ui/Modal 与 ui/Drawer 原语自己必须接焦点＋陷阱＋滚动锁', () => {
    for (const { marker, componentFile } of PRIMITIVE_OVERLAYS) {
      const callSites = totals.byMarker[marker] || 0
      expect(
        callSites,
        `扫描键 ${marker} 一个调用点都读不到——口径或标记集被动过了`,
      ).toBeGreaterThan(0)
      const text = stripOverlayNoise(readFileSync(join(SRC, componentFile), 'utf8'))
      const missing = missingPrimitiveCalls(text)
      expect(
        missing,
        `${componentFile} 缺 ${JSON.stringify(missing)}——它替 ${callSites} 个 ${marker} 调用点兜焦点，` +
          '摘掉任何一件，那些调用点就静默失去键盘可达（调用点本来就不该有接线，所以只能在这儿钉）。',
      ).toEqual([])
    }
  })

  it('R-T9-7 迁移规矩：纯原语调用点不留死接线', () => {
    const dead = primitiveOnlySitesWithDeadWiring(records)
    expect(
      dead,
      `以下文件的弹层全是 ui/Modal／ui/Drawer，却还留着 useDialogFocus( 调用：\n${dead.join('\n')}\n` +
        '那是死线——useDialogFocus 读 dialogRef.value.$el，而原语实例的 $el 是 teleport 锚点注释' +
        '（nodeType 8、没有 querySelector），原语会静默 return（实测记在 LEDGER P0b-MODAL-04 的 P-b）。' +
        '改法＝删掉调用方的 ref 与 useDialogFocus 两行，不是把本条断言放宽。',
    ).toEqual([])
  })
})
