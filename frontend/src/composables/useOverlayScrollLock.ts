import { onScopeDispose, watch } from 'vue'
import type { Ref } from 'vue'

/**
 * 弹层滚动锁（Modal / Drawer 共用一把）—— 引用计数版
 * ====================================================
 * 本文件是 `Drawer.vue` 里那份内联实现的**抽取**，不是第二套实现：键名、消毒口径、
 * 归零时的还原语义全部沿用 Drawer 评审后落定的那一版（Task 11 修复轮），因为
 * 「抽屉叠在弹窗上时最后一个关闭才还原滚动」这条**只有在两个原语读写同一个计数器时才成立**。
 * 状态住在 `document.body` 的 dataset 上（键名 camelCase：`dataset.overlayCount`；
 * 写成 `dataset['overlay-count']` 不保证命中，后果是计数恒 0、锁永不还原，
 * 而单测若清的是另一个键就两边都"看起来对"）。
 *
 * 为什么不退回布尔版：布尔锁在"确认框叠在编辑框上"这种嵌套场景里内层一关就还原，
 * 外层还开着但背景已能滚（本项目 drawer/dialog 叠加是真实形状，见 e2e 的考试弹层链）。
 *
 * 三条既有语义（丢了就是行为变更，逐条有入库用例钉着）：
 * 1. 归零时**还原上锁前那个原值**（`body.dataset.overlayOverflowPrev`，由真正完成 0→1
 *    的那次上锁写入），不是无条件写 `''`——页面别处可能自己写过 `body.style.overflow`。
 * 2. 「计数归 0 但 `overlayOverflowPrev` 不存在」时**什么都不动**：说明这把锁不是我上的
 *    （计数被外部写过），宁可不还原，也不去猜别人该是什么值。
 * 3. 增减对每个实例幂等，且开着被卸载必须归还（`v-if` 式调用点 RagDocs.vue 就是这个形状）。
 *
 * 计划 Task 10 Step 3 那份草稿里的三个 bug，本文件各自对应一条会红的用例（用例名见注释）：
 * ① `immediate` 首次回调的 prev 是 `undefined`，`(on, off) => on === off` 挡不住 `(false, undefined)`
 *    ⇒ 从没打开过的实例挂载即白扣 1。这里用 `claimed`（本实例是否已登记）而不是"上一次的布尔值"。
 *    → 用例「从未打开过的实例：挂载与卸载都不许动计数」。
 * ② 草稿没有任何卸载路径 ⇒ 开着被卸载时 `+1` 永久漏。这里在 `onScopeDispose` 里归还并 `stop()`。
 *    （时机已核：本机 vue 3.5.43 的 runtime-core.cjs.js `unmountComponent` 里 `scope.stop()` 在
 *    `beforeUnmount` 钩子之后、子树卸载之前**同步**调用，所以组件卸载当场计数就归还，不需要等 flush。）
 *    → 用例「开着时被卸载必须归还 +1」。
 * ③ 脏值不消毒：`Number('abc')` 是 NaN，`Math.max(0, NaN)` 还是 NaN，而 `NaN > 0` 为 false
 *    ⇒ 表现是"还开着却把滚动解了"，比直接崩难查。读写两侧都要消毒。
 *    → 用例「计数被脏写成非数字时按 0 重开」与其解锁半边。
 */

/** 与 Drawer.vue 抽取前逐字同键（camelCase；DOMStringMap 的 supported property name 就是这个形式） */
const COUNT_KEY = 'overlayCount'
/** 上锁前的内联 overflow 存在这里，与计数同域（body dataset），归零时按它还原 */
const PREV_KEY = 'overlayOverflowPrev'

/**
 * 消毒口径选定为「只接受正整数，否则当 0」（计划 Step 3 的 `Number.isInteger(raw) && raw > 0`），
 * **不是** Drawer 抽取前的 `Number.isFinite(raw)`。理由不是风格，是这两条对脏写的后果不同：
 * - `isFinite` 接受 `'1.5'`，而本函数**自己会把算出来的 `next` 写回 dataset**，
 *   于是一次脏写之后计数从此带着小数走：`1.5 + 1 - 1 = 1.5`，`next > 0` 恒真 ⇒
 *   锁再也解不开（关到最后一个仍是 hidden），而且这个非整数状态是被我们自己的写入**续命**的，
 *   永远回不到整数域。
 * - 正整数口径下，`'1.5'` 当 0 重开，代价是 brief 也点过的那个反向风险：一次脏写会让下一次上锁
 *   把别人正持有的锁当成"没人锁"而覆盖 prev。该风险只在**已经发生外部脏写**这一异常态里存在，
 *   而 `isFinite` 版的失效是**永久的**，且会污染同一个共享键、波及另一个原语；
 *   取后者（正整数）换来的是「我们写进 dataset 的值永远是正整数或 0」这条不变量。
 * - 附带一条：`isFinite` 还接受负数，`'-1' + 1 = 0` ⇒ 挂载即上锁却根本不锁。正整数口径把它归 0。
 * 这条差异由用例「非整数脏值按 0 重开」单独钉住，改回 `isFinite` 它就红。
 */
function count(): number {
  const raw = Number(document.body.dataset[COUNT_KEY])
  return Number.isInteger(raw) && raw > 0 ? raw : 0
}

/**
 * 上锁 / 解锁一次。调用方（watch 与 onScopeDispose）保证同一实例不会连续两次同向登记，
 * 所以这里只管计数器本身，不掺实例状态。
 */
function applyLock(on: boolean): void {
  const base = count()
  const next = Math.max(0, base + (on ? 1 : -1))
  // 只有真正把计数从 0 抬到 1 的那次上锁才记原值：嵌套时第二个弹层不该覆盖第一个记下的值，
  // 这样交错关闭时谁抢到 0 都拿到同一个原值。
  if (on && base === 0) document.body.dataset[PREV_KEY] = document.body.style.overflow
  document.body.dataset[COUNT_KEY] = String(next)
  if (next > 0) {
    document.body.style.overflow = 'hidden'
  } else if (PREV_KEY in document.body.dataset) {
    document.body.style.overflow = document.body.dataset[PREV_KEY] as string
    delete document.body.dataset[PREV_KEY]
  }
  // next 归 0 却没找到原值：这把锁不是我们上的（计数被外部写过），什么都不动——
  // 写 '' 会覆盖别处自己的 overflow，猜个值更是无据。（语义 2；改坏它的那条用例见测试文件。）
}

/** 供单测与调试读取当前计数（已消毒：脏值读回 0） */
export function overlayCount(): number {
  return count()
}

/**
 * 把滚动锁绑到一个「是否开着」的响应式来源上。组件侧传 `open` computed 即可：
 * `useOverlayScrollLock(open)`（Modal.vue 与 Drawer.vue 各自一行，共用同一个计数器）。
 *
 * `active: Ref<boolean>` 接得住 `ComputedRef<boolean>`：vue 3.5.43 的类型里
 * `BaseComputedRef<T, S> extends Ref<T, S>`（reactivity.d.ts，`ComputedRef` 经它继承 `Ref`），
 * 所以两个调用点传 computed 不额外放宽签名。
 */
export function useOverlayScrollLock(active: Ref<boolean>): void {
  /** 本实例当前是否已登记一个 +1：增减都以它为准，故对同一实例幂等（bug ① 的解法） */
  let claimed = false
  const stop = watch(
    active,
    (on) => {
      if (on === claimed) return
      claimed = on
      applyLock(on)
    },
    { immediate: true },
  )
  onScopeDispose(() => {
    stop()
    // 开着就被卸载（v-if、路由切换）：把本实例那 1 还回去；没登记过就一个都不动。
    if (claimed) {
      claimed = false
      applyLock(false)
    }
  })
}
