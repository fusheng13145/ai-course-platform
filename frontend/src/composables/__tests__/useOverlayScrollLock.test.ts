import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import type { Ref } from 'vue'
import { overlayCount, useOverlayScrollLock } from '@/composables/useOverlayScrollLock'

/**
 * useOverlayScrollLock 单元测试
 * ==============================
 * 这里只用 `effectScope` + `ref` 造实例，不挂组件：锁的全部状态都在 `document.body` 上，
 * 组件侧的接入面另由 `Modal.scrolllock.test.ts`（Modal×Drawer 共用一把锁）与
 * `Drawer.test.ts` 的「滚动锁与焦点」一节钉。
 *
 * 每条用例后面的「怎么把它跑红」就是变异实验的配方：改坏列出的那一处，该条必须红。
 * 全部用例跑在 jsdom（vitest.config.ts 的默认 environment），document.body 真实存在。
 */

/** 一条锁状态：计数键 + 原值键 + body 内联 overflow，三者跨用例必须清零 */
function resetLockState(): void {
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
  document.body.style.overflow = ''
}

/** 挂载一个"实例"（等价于组件 setup 里调用一次 composable），返回卸载函数 */
function mountInstance(active: Ref<boolean>): () => void {
  const scope = effectScope()
  scope.run(() => useOverlayScrollLock(active))
  return () => scope.stop()
}

beforeEach(() => resetLockState())
afterEach(() => resetLockState())

describe('useOverlayScrollLock — 引用计数滚动锁', () => {
  it('打开即上锁并记 1；关闭还原「上锁前」的原值而不是写空', async () => {
    // 页面自己写过的 overflow（不属于这套计数）必须原样回来：Minor-4 定的那条语义。
    document.body.style.overflow = 'auto'
    const active = ref(false)
    const dispose = mountInstance(active)
    await nextTick()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('auto')

    active.value = true
    await nextTick()
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.dataset.overlayOverflowPrev).toBe('auto')

    active.value = false
    await nextTick()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('auto')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
    // 归还后不留 prev 键：下一次上锁要记的是那时的新原值，不是这次的旧值。
    dispose()
  })
  // 跑红配方：① 把归零分支的 `document.body.style.overflow = document.body.dataset[PREV_KEY]`
  // 改成无条件 `= ''` ⇒ 本条报 `expected 'auto' to be 'hidden'`（还原那半红在关闭段）。

  it('两个实例共用一把计数器：最后一个关闭才还原', async () => {
    const outer = ref(false)
    const inner = ref(false)
    const disposeOuter = mountInstance(outer)
    const disposeInner = mountInstance(inner)

    outer.value = true
    inner.value = true
    await nextTick()
    expect(overlayCount()).toBe(2)
    expect(document.body.style.overflow).toBe('hidden')

    inner.value = false
    await nextTick()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    outer.value = false
    await nextTick()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('')
    disposeInner()
    disposeOuter()
  })
  // 跑红配方：本条是"两处各自计数"的靶子，有两个可跑的世界，**报错文本不一样，别混着写**：
  // 世界 A（把 composable 的计数从 body dataset 换成函数内局部变量 `let n = 0`）：
  //   dataset 从头到尾没人写，`overlayCount()` 读回 0 ⇒ 本条第一处断言报 `expected 0 to be 2`。
  // 世界 B（M7 实跑的那次：把 `Drawer.vue` 换成自己那把键 `drawerOverlayCount`）：
  //   dataset 里只有 Modal 那一个 +1 ⇒ 报 `expected 1 to be 2`，三条 Modal×Drawer 用例一起红。
  // 组件侧同一形状由 `Modal.scrolllock.test.ts` 的 Modal×Drawer 那条再钉一遍；
  // 两个世界的实跑读数都记在台账 `P0b-SCROLLLOCK-01` 的"有效性"栏（报告文件不入 git，不算依据）。

  it('从未打开过的实例：挂载与卸载都不许动计数', async () => {
    // 计划草稿的 watch(open, (on, off) => { if (on === off) return }, { immediate: true })
    // 首次回调拿到的是 (false, undefined)：on !== off 成立 ⇒ 走下去白扣 1，
    // 叠在已开弹窗下面的关闭抽屉会把外层的滚动锁解开。
    document.body.dataset.overlayCount = '1'
    document.body.style.overflow = 'hidden'
    const active = ref(false)
    const dispose = mountInstance(active)
    await nextTick()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    dispose()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')
  })
  // 跑红配方：把 composable 里的 `let claimed = false` 换成草稿的 `(on, off) => { if (on === off) return; ... }`
  // ⇒ 挂载当场扣到 0，本条报 `expected +0 to be 1`。

  it('开着时被卸载必须归还 +1', async () => {
    // v-if 式调用点（RagDocs.vue）就是"打开状态下整棵子树被摘掉"，草稿没有卸载路径 ⇒ 永久漏 1。
    const active = ref(true)
    const dispose = mountInstance(active)
    await nextTick()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    dispose()
    expect(overlayCount()).toBe(0)
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(document.body.style.overflow).toBe('')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
  })
  // 跑红配方：删掉 `onScopeDispose(...)` 整块 ⇒ `expected +1 to be +0`。

  it('开→关→卸载：卸载不再补扣别人那一层的锁', async () => {
    // 下面外部那 1 代表另一层已持有的锁：本实例做完一个开→关循环后卸载，
    // 不得把它扣掉——卸载时无条件归还的写法（不看 claimed）在这里会扣成 0。
    // ⚠️ 本条不覆盖"重复赋同值"那一类误增：active 是布尔 ref，值没变时 watch 根本不触发，
    // 所以"挂两次 true 只加一次"这种形状在这里无法证伪，不写成断言（claimed 的证伪面是卸载路径）。
    document.body.dataset.overlayCount = '1'
    document.body.style.overflow = 'hidden'
    const active = ref(false)
    const dispose = mountInstance(active)

    active.value = true
    await nextTick()
    expect(overlayCount()).toBe(2)

    active.value = false
    await nextTick()
    expect(overlayCount()).toBe(1)

    dispose()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')
  })
  // 跑红配方：把 onScopeDispose 里的 `if (claimed)` 去掉（无条件归还）⇒ `expected +0 to be +1`。

  it('计数被脏写成非数字时按 0 重开，而不是从此锁不住', async () => {
    // 未消毒时：Number('abc') → NaN，Math.max(0, NaN+1) → NaN，dataset 写成 'NaN'，
    // 而 NaN > 0 为 false ⇒ 永远不 hidden，且下一次读还是 NaN，整个滚动锁从此失效。
    document.body.dataset.overlayCount = 'abc'
    const active = ref(false)
    const dispose = mountInstance(active)

    active.value = true
    await nextTick()
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    active.value = false
    await nextTick()
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('')
    dispose()
  })
  // 跑红配方：把 count() 改成 `return Number(document.body.dataset[COUNT_KEY])`（不消毒）
  // ⇒ 上锁段报 `expected 'NaN' to be '1'`。

  it('非整数脏值按 0 重开（消毒口径钉死为「正整数」，不是 isFinite）', async () => {
    // 这一条专挑两版草稿的分歧：isFinite 版接受 '1.5' ⇒ 写回 '2.5'，
    // 而 applyLock 自己会把带小数的值续在共享键上，next > 0 从此恒真（关到最后一个仍是 hidden）。
    // 选定口径把它当 0：我们写进 dataset 的值永远是正整数或 0。
    document.body.dataset.overlayCount = '1.5'
    document.body.style.overflow = 'scroll'
    const active = ref(false)
    const dispose = mountInstance(active)

    active.value = true
    await nextTick()
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    active.value = false
    await nextTick()
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(overlayCount()).toBe(0)
    // 反向风险也一并留下事实：当 0 重开时记下的"原值"就是脏写者留下的 'scroll'，
    // 归零按它还原。这是选择正整数口径时接受的代价（异常态才发生），见本文件注释。
    expect(document.body.style.overflow).toBe('scroll')
    dispose()
  })
  // 跑红配方：把 count() 的 `Number.isInteger(raw) && raw > 0` 换成 Drawer 抽取前的
  // `Number.isFinite(raw)` ⇒ 第一处断言报 `expected '2.5' to be '1'`，关闭段报 `expected 'hidden' to be 'scroll'`。

  it('归 0 但 overlayOverflowPrev 不存在 ⇒ 什么都不动（不猜别人的值）', async () => {
    // 可达形状：不认识 prev 键的另一套实现先把计数写成 1 并把 body 锁住；
    // 我们在 base≠0 时不记 prev（正确，那把锁不是我们上的），抬到 2；
    // 它先归还（外部把计数写回 1）；我们再归 0 —— 此刻没有 prev 可还原，
    // 语义要求**什么都不动**：写 '' 会把外部那套实现正在用的 overflow 抹掉。
    document.body.dataset.overlayCount = '1'
    document.body.style.overflow = 'hidden'
    const active = ref(false)
    const dispose = mountInstance(active)

    active.value = true
    await nextTick()
    expect(overlayCount()).toBe(2)
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()

    // 外部那一层自己归还它持有的 1（它从不写 prev 键）
    document.body.dataset.overlayCount = '1'

    active.value = false
    await nextTick()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
    dispose()
  })
  // 跑红配方：把归零那条 `else if (PREV_KEY in document.body.dataset)` 分支改成无条件
  // `else { document.body.style.overflow = '' }` ⇒ 本条报 `expected 'hidden' to be ''`。
  // （这条就是 brief 点名"今天只有注释、没有测试"的那个分支。）
})
