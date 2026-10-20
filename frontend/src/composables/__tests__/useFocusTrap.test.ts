import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import type { Ref } from 'vue'
import { useFocusTrap } from '@/composables/useFocusTrap'

/**
 * useFocusTrap 单元测试（Task 10B 子步骤 1）
 * ==========================================
 * 与 `useOverlayScrollLock.test.ts` 同一处置：只用 `effectScope` + `ref` 造"实例"，
 * 不挂组件——组件侧的接入面（Modal/Drawer 各一行的 `useFocusTrap(rootRef, open)`）
 * 由 `Modal.a11y.test.ts` 的「焦点陷阱」一节与 `Drawer.test.ts` 钉。
 * 每条用例后面的「跑红配方」是变异实验的锚：改坏列出的那一处，该条必须红。
 * 全部跑在 jsdom（vitest.config.ts 默认 environment）。jsdom **不实现** Tab 的
 * 顺序聚焦导航（按 Tab 不会自己移动焦点），所以"逃逸"只能由实现主动移动、
 * "拦截"只能由 preventDefault + 主动 focus 观察——不存在"绿在浏览器行为上"的断言。
 */

/** 往 body 挂一块面板，返回元素本体（afterEach 统一摘） */
function fixture(innerHtml: string): HTMLElement {
  const root = document.createElement('div')
  root.innerHTML = innerHtml
  document.body.appendChild(root)
  return root
}

/** 在元素上派发可取消的 Tab keydown（bubbles 必真：陷阱的监听挂在 document） */
function tab(el: HTMLElement, shift = false): KeyboardEvent {
  const e = new KeyboardEvent('keydown', {
    key: 'Tab',
    bubbles: true,
    cancelable: true,
    shiftKey: shift,
  })
  el.dispatchEvent(e)
  return e
}

/** 挂一个 composable 实例（等价组件 setup 里调一次），返回 dispose */
function mountTrap(root: HTMLElement, active: boolean): [Ref<boolean>, () => void] {
  const rootRef = ref<HTMLElement | null>(root)
  const activeRef = ref(active)
  const scope = effectScope()
  scope.run(() => useFocusTrap(rootRef, activeRef))
  return [activeRef, () => scope.stop()]
}

/** document 监听注册表探针（与 Drawer.test.ts / Modal.compat.test.ts 的三元组口径一致） */
function spyOnDocumentListeners(): {
  added: [string, EventListener, boolean][]
  removed: [string, EventListener, boolean][]
  restore(): void
} {
  const added: [string, EventListener, boolean][] = []
  const removed: [string, EventListener, boolean][] = []
  const raw = document.addEventListener.bind(document)
  const rawRemove = document.removeEventListener.bind(document)
  const captureOf = (o?: boolean | { capture?: boolean }): boolean =>
    typeof o === 'boolean' ? o : !!o?.capture
  const addSpy = vi
    .spyOn(document, 'addEventListener')
    .mockImplementation((type, listener, options) => {
      added.push([type, listener as EventListener, captureOf(options)])
      raw(type, listener as EventListener, options)
    })
  const removeSpy = vi
    .spyOn(document, 'removeEventListener')
    .mockImplementation((type, listener, options) => {
      removed.push([type, listener as EventListener, captureOf(options)])
      rawRemove(type, listener as EventListener, options)
    })
  return {
    added,
    removed,
    restore() {
      addSpy.mockRestore()
      removeSpy.mockRestore()
    },
  }
}

const panelNodes: HTMLElement[] = []
afterEach(() => {
  panelNodes.forEach((n) => n.remove())
  panelNodes.length = 0
  ;(document.activeElement as HTMLElement | null)?.blur?.()
})

function panel(html: string): HTMLElement {
  const root = fixture(html)
  panelNodes.push(root)
  return root
}

describe('useFocusTrap — 回绕与可聚焦集合', () => {
  it('挂载即激活（immediate 承重面）：尾元素 Tab 回绕到首、首元素 Shift+Tab 回绕到尾', async () => {
    // 13 个存量 <Modal> 与 RagDocs 式 <Drawer> 全是"外层 v-if、挂载时已开"——
    // 这条从 active=true 直接起步，走的就是那条路；删掉 watch 的 { immediate: true }
    // 本条第一处断言就红（监听根本没挂上，defaultPrevented 恒 false）。
    const root = panel(
      '<button id="t1-a"></button><button id="t1-b"></button><button id="t1-c"></button>',
    )
    const [, dispose] = mountTrap(root, true)

    const a = root.querySelector('#t1-a') as HTMLElement
    const c = root.querySelector('#t1-c') as HTMLElement
    c.focus()
    expect(tab(c).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(a)

    a.focus()
    expect(tab(a, true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(c)

    // 中段元素上的 Tab 不该被动过（只拦越界）：
    const b = root.querySelector('#t1-b') as HTMLElement
    b.focus()
    expect(tab(b).defaultPrevented).toBe(false)
    expect(document.activeElement).toBe(b)
    dispose()
  })
  // 跑红配方：① 删 `{ immediate: true }` ⇒ 本文件实跑 4 failed，三处断言全报
  //   `expected false to be true`（监听根本没挂，没人拦也没人允许回绕）；
  // ② 尾界判定 `idx === items.length - 1` 改成永不成立的比较（永不回绕）⇒ 同 4 红；
  // ③ 反向变异"任何元素 Tab 都 preventDefault+focus(first)" ⇒ 中段防误伤那处红
  //   （`expected false to be true`；本轮未实跑，红法为构造保证——该断言就在同一条用例里）。

  it('可聚焦集合复用 DIALOG_FOCUSABLE_SELECTOR：select/textarea 在环内，disabled 不入环', async () => {
    // 文档序：button → select → textarea → button[disabled]。
    // 尾 = textarea（select/textarea 只在那个导出里有、常见"button/input/a"自创清单里没有）；
    // disabled 尾按钮若误入环，"textarea 是尾"与"Tab 被拦"两处一起塌。
    const root = panel(
      '<button id="t2-btn"></button><select id="t2-sel"></select><textarea id="t2-ta"></textarea><button id="t2-dis" disabled></button>',
    )
    const [, dispose] = mountTrap(root, true)
    const btn = root.querySelector('#t2-btn') as HTMLElement
    const ta = root.querySelector('#t2-ta') as HTMLElement

    btn.focus()
    expect(tab(btn, true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(ta) // 首的回绕尾 = textarea；跳过 disabled

    ta.focus()
    expect(tab(ta).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(btn)
    dispose()
  })
  // 跑红配方：把 `visibleFocusables` 里的 `DIALOG_FOCUSABLE_SELECTOR` 换成自创清单
  // `'button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'`
  // （缺 select/textarea 的"看着差不多"版）⇒ 本文件实跑 1 failed，报错原文
  // `expected <button id="t2-btn"></button> to be <textarea id="t2-ta"></textarea>`
  // （环只剩 [btn]，首的回绕尾变成 btn 自己）。
  // 配方二（**未实跑**，推断红法、如实标注为推断）：把导入的口径换成"不排除 disabled"的
  // 自写清单 ⇒ disabled 尾按钮入环、textarea 不再是尾；回绕调用 focus() 时 jsdom 不给
  // disabled 元素焦点，红点是 activeElement 身份断言（`expected <button id="t2-btn">… to be
  // <textarea…>`——焦点留在 btn），`defaultPrevented` 半边仍绿，所以这半边的独占证据在身份断言。

  it('display:none 的控件跳过——自身隐藏与祖先隐藏都算', async () => {
    // 文档序：v1 → v2 → hidden(自身 display:none) → deep(祖先 span 被隐藏)。
    // 环必须只有 [v1, v2]：v1 的 Shift+Tab 回绕尾必须是 v2；
    // 过滤失效时尾变成 deep/hidden，jsdom 照样能把焦点给 display:none 元素 ⇒ 断言红。
    const root = panel(
      '<button id="t3-v1"></button><button id="t3-v2"></button>' +
        '<button id="t3-hid" style="display:none"></button>' +
        '<span style="display:none"><button id="t3-deep"></button></span>',
    )
    const [, dispose] = mountTrap(root, true)
    const v1 = root.querySelector('#t3-v1') as HTMLElement
    const v2 = root.querySelector('#t3-v2') as HTMLElement

    v1.focus()
    expect(tab(v1, true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(v2)

    // 正向半边：v2 已是可见尾——在它上面 Tab 必须被拦并回绕 v1；
    // 没过滤时 v2 不是尾（deep 才是），事件不会被 preventDefault ⇒ 第二处红。
    v2.focus()
    expect(tab(v2).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(v1)
    dispose()
  })
  // 跑红配方：把 `.filter((el) => !isHiddenWithin(el, root))` 摘掉 ⇒ 本文件实跑 1 failed，
  // 报错原文 `expected <button id="t3-deep"></button> to be <button id="t3-v2"></button>`
  // （无过滤时文档尾的隐藏按钮成了环尾，jsdom 照样把焦点给 display:none 元素）。
  // 只删祖先链、保留自身 display 检查的半残版 ⇒ 同一处红（deep 仍入环）。
})

describe('useFocusTrap — 装载/卸载边沿', () => {
  it('关闭态挂载零装载；开→挂 1 个 keydown；关→摘回；开着被卸载→onScopeDispose 摘回', async () => {
    const spy = spyOnDocumentListeners()
    try {
      const root = panel('<button id="t4-a"></button>')

      // ① 关闭态挂载：document 上不得留下本陷阱的任何监听（题面需求 2 的字面要求）。
      const [active, dispose] = mountTrap(root, false)
      await nextTick()
      expect(spy.added.filter(([t]) => t === 'keydown')).toHaveLength(0)

      // ② 打开边沿：装载恰好 1 个。
      active.value = true
      await nextTick()
      const keydownAdds = spy.added.filter(([t]) => t === 'keydown')
      expect(keydownAdds).toHaveLength(1)

      // ③ 关闭边沿：同一个三元组 (keydown, fn, capture) 被摘掉，不多不少。
      active.value = false
      await nextTick()
      const leaks = keydownAdds.filter(
        ([t, fn, cap]) => !spy.removed.some(([rt, rf, rc]) => rt === t && rf === fn && rc === cap),
      )
      expect(leaks).toEqual([])

      // ④ 开着被卸载（v-if 式调用点的形状）：dispose 当场归还，不留监听。
      active.value = true
      await nextTick()
      const beforeDispose = spy.added.filter(([t]) => t === 'keydown').length
      dispose()
      const stillBound =
        spy.added.filter(([t]) => t === 'keydown').length -
        spy.removed.filter(([t]) => t === 'keydown').length
      expect(stillBound).toBe(0)
      expect(beforeDispose).toBeGreaterThanOrEqual(2) // 走了两轮装载，不是恒 1 的假象
    } finally {
      spy.restore()
    }
  })
  // 跑红配方：① 删 `onScopeDispose(...)` 整块 ⇒ ④ `expected 1 to be +0`；
  // ② watch 去掉 `{ immediate: true }` 不影响本条（关闭起步），但会红第一条；
  // ③ 把卸载段 `if (bound)` 去掉（无条件 removeEventListener 一个没挂过的 fn）⇒ 本条仍绿——
  //     如实记：这个方向**不可证伪**（移除未注册的监听是 no-op），故不为其立断言，只作为代码对称性存在。

  it('active 翻 false 之后、watcher 冲刷之前：处理函数已被 active 闸哑火（Task 7 Esc 闸同一语义）', async () => {
    const root = panel('<button id="t5-a"></button><button id="t5-b"></button>')
    const [active, dispose] = mountTrap(root, true)
    const b = root.querySelector('#t5-b') as HTMLElement
    b.focus()

    // 同步翻转、不 await 冲刷：pre-flush watcher 还在微任务队列里，监听**还挂在 document 上**。
    // 没有处理函数里那道 `active.value` 闸，这次 Tab 会照常回绕。
    active.value = false
    expect(tab(b).defaultPrevented).toBe(false)
    expect(document.activeElement).toBe(b)

    await nextTick() // 冲刷后监听已摘，同样哑火——两截都要不响应。
    expect(tab(b).defaultPrevented).toBe(false)
    expect(document.activeElement).toBe(b)
    dispose()
  })
  // 跑红配方：删处理函数开头的 `|| !active.value` ⇒ 本文件实跑 1 failed，第一处红报
  // `expected true to be false`（回绕发生了、preventDefault 真的被调用）。这是
  // "翻转到冲刷之间"这个真实窗口的唯一钉子。
})

describe('useFocusTrap — 嵌套分层', () => {
  it('两层各管各层：焦点在内层时外层哑火；焦点在外层时内层哑火', async () => {
    const outer = panel('<button id="t6-o1"></button><button id="t6-o2"></button>')
    const inner = panel('<button id="t6-i1"></button><button id="t6-i2"></button>')
    // 绑定顺序刻意"先内后外"（与弹层真实挂载序相反）：无守卫的变异版里，
    // 若外层先跑、内层后跑，内层会把外层误抢的焦点再抢回来，末态碰巧正确、变异漏网；
    // 内层先跑时，外层若在焦点已回绕后**又**动了焦点，就当场暴露。
    const [, disposeInner] = mountTrap(inner, true)
    const focusSpy: HTMLElement[] = []
    const realFocus = HTMLElement.prototype.focus
    const spy = vi.spyOn(HTMLElement.prototype, 'focus').mockImplementation(function (
      this: HTMLElement,
    ) {
      focusSpy.push(this)
      realFocus.call(this)
    })
    const [, disposeOuter] = mountTrap(outer, true)

    try {
      const i2 = inner.querySelector('#t6-i2') as HTMLElement
      const i1 = inner.querySelector('#t6-i1') as HTMLElement
      i2.focus()
      focusSpy.length = 0
      expect(tab(i2).defaultPrevented).toBe(true)
      expect(document.activeElement).toBe(i1)
      // 核心钉：整次事件里焦点只被移动了一次（内层自己的回绕）。
      // `contains` 守卫摘掉后：本序（内层先注册）下外层还会再动一次焦点、这里红；
      // ⚠️ 产品挂载序（外层先注册）这一半**是推断、没有实跑**：按机制推是"误抢被内层纠正回来、
      // 末态身份碰巧对，但 focus 调用次数变成 2"。这条世界今天没有入库可重放的跑法
      // （要跑得新增一条产品序用例，那属弹层栈那一棒的范围），所以只当**边界陈述**用，
      // 不许在任何地方引用成"两种注册序都实跑过"。
      // 两种序都红，红面不同，独占证据见下方配方注释。
      expect(focusSpy).toEqual([i1])

      const o2 = outer.querySelector('#t6-o2') as HTMLElement
      const o1 = outer.querySelector('#t6-o1') as HTMLElement
      o2.focus()
      focusSpy.length = 0
      expect(tab(o2).defaultPrevented).toBe(true)
      expect(document.activeElement).toBe(o1)
      expect(focusSpy).toEqual([o1]) // 反方向：内层不得动焦点
    } finally {
      spy.mockRestore()
      disposeInner()
      disposeOuter()
    }
  })
  // 跑红配方：注释掉处理函数里 `if (!current || !root.contains(current)) return` ⇒
  // 本文件实跑 1 failed（**内层先注册**序），红在身份断言 `expect(document.activeElement).toBe(i1)`，
  // 报错原文 `expected <button id="t6-o1"></button> to be <button id="t6-i1"></button>`
  // （外层把内层焦点拽走，内层注册在前、纠正不及）。**产品挂载序**（外层先注册）下末态可能
  // 碰巧正确（外层误抢、内层后注册抢回 i1），届时红的是这里的 `focusSpy` 计数（2 次移动）——
  // 所以本条用"焦点移动次数恰为 1"当独占钉子；反方向半段（焦点在外层时内层哑火）在两种序下
  // 末态都可能碰巧对，**不**充当该变异的独占证据，如实标注（未实跑产品序变体）。
})
