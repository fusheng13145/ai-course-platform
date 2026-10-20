import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Task 10B：Modal 的 ARIA／焦点返还／`closed`／`closeOnClickModal` ＋ 焦点陷阱组件面。
 * ================================================================================
 * 形状参考是 **Drawer.vue 已交付的那一套**（题面明令别自创）：props 三态、`closed` 在 emits、
 * `titleId = modal-title-${uid}`、`role="dialog"` ＋ `:aria-label` ＋ `:aria-labelledby`、
 * `watch(open, …, { immediate: true })` 的 opener/返还块。陷阱本体四条要求的单元面在
 * `composables/__tests__/useFocusTrap.test.ts`；本文件按题面把**四条要求各配一条组件面断言**
 * （以 Modal 为载体），外加新行为本身。
 *
 * 夹具口径：挂载/卸载走 helpers/trackMount（族内规矩，不复制夹具）；查询全走 document.body
 * （Teleport 后 wrapper.find 取不到遮罩子树）；rAF 用可控队列替换（useDialogFocus 的焦点动作
 * 排在 nextTick 之后的帧里，不接管就断不到确定时刻——Modal.focus.test.ts/Drawer.test.ts 同一做法）。
 * 焦点是跨用例状态：afterEach 里 blur 归零 + 清 body 节点；滚动锁 dataset 一并清零
 * （Modal 挂载即上锁，本文件的用例不测锁，但不清会脏 body.style）。
 */

interface ModalProps {
  modelValue?: boolean
  closeOnClickModal?: boolean
  ariaLabel?: string
  ariaLabelledby?: string
}

let rafQueue: FrameRequestCallback[] = []
const extras: HTMLElement[] = []

function factory(props: ModalProps = {}, slots: Record<string, string> = {}) {
  const wrapper = mount(Modal, { props, slots, attachTo: document.body })
  return track(wrapper)
}

function flushFrames() {
  const queue = rafQueue
  rafQueue = []
  queue.forEach((cb) => cb(0))
}

/** 等渲染 + 冲刷帧（三轮，理由同 Drawer.test.ts 的 settle：边沿落在哪轮 flush 不可预判） */
async function settle(wrapper: { vm: { $nextTick(): Promise<void> } }) {
  for (let i = 0; i < 3; i++) {
    await wrapper.vm.$nextTick()
    flushFrames()
  }
  await wrapper.vm.$nextTick()
}

function panel() {
  return document.body.querySelector('.modal')
}

function panels() {
  return Array.from(document.body.querySelectorAll('.modal'))
}

function lastPanel() {
  return panels().at(-1) ?? null
}

/** 挂一个"打开前就被聚焦"的元素（焦点返还的用形与 Drawer.test.ts 的 opener 一致） */
function focusableOpener(text = '打开'): HTMLElement {
  const btn = document.createElement('button')
  btn.textContent = text
  document.body.appendChild(btn)
  extras.push(btn)
  return btn
}

function pressTab(el: Element | null, shift = false): KeyboardEvent {
  expect(el, '找不到 Tab 目标元素').not.toBeNull()
  const e = new KeyboardEvent('keydown', {
    key: 'Tab',
    bubbles: true,
    cancelable: true,
    shiftKey: shift,
  })
  el!.dispatchEvent(e)
  return e
}

function fire(el: Element | null, type: 'mousedown' | 'click') {
  expect(el, `找不到 ${type} 目标元素`).not.toBeNull()
  el!.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true }))
}

beforeEach(() => {
  rafQueue = []
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
    rafQueue.push(cb)
    return rafQueue.length
  })
  vi.stubGlobal('cancelAnimationFrame', () => {})
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
  document.body.style.overflow = ''
})

afterEach(() => {
  unmountTracked()
  // Teleport 落点在 body：不赌 unmount 连带摘掉传送节点，直接清场（Drawer.test.ts 同处置）。
  document.body.querySelectorAll('.overlay, .modal').forEach((n) => n.remove())
  for (const el of extras) el.remove()
  extras.length = 0
  ;(document.activeElement as HTMLElement | null)?.blur?.()
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
  document.body.style.overflow = ''
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Modal — ARIA 与模态语义', () => {
  it('面板带 role="dialog" 与 aria-modal="true"（陷阱落地才挂回，兑现 R-1 的补回前提）', async () => {
    const wrapper = factory({ modelValue: true }, { title: 'T' })
    await settle(wrapper)
    expect(panel()?.getAttribute('role')).toBe('dialog')
    // aria-modal 不是裸挂：它兑现的是 useFocusTrap(rootRef, open) 这条接线（Modal.vue）。
    // 跑红配方：删模板里的 aria-modal 属性 ⇒ 本条 `expected null to be 'true'`。
    expect(panel()?.getAttribute('aria-modal')).toBe('true')
  })

  it('title 插槽 ⇒ aria-labelledby 指向带 id 的标题节点，id/文本可读', async () => {
    const wrapper = factory({ modelValue: true }, { title: '课程详情' })
    await settle(wrapper)
    const labelledby = panel()?.getAttribute('aria-labelledby')
    expect(labelledby).toBeTruthy()
    // labelledby 指向的节点**就是**面板里那块带 id 的标题（不比 uid 字符串，比节点身份）。
    expect(document.getElementById(labelledby as string)).toBe(
      panel()?.querySelector('.modal-title'),
    )
    expect(document.getElementById(labelledby as string)?.textContent).toBe('课程详情')
    expect(document.getElementById(labelledby as string)?.id.startsWith('modal-title-')).toBe(true)
  })

  it('同页两个 Modal 的 titleId 不撞车（uid 生成，Drawer 同口径）', async () => {
    const a = factory({ modelValue: true }, { title: '第一个' })
    await settle(a)
    const b = factory({ modelValue: true }, { title: '第二个' })
    await settle(b)
    const ids = panels().map((p) => p.getAttribute('aria-labelledby'))
    expect(ids).toHaveLength(2)
    expect(new Set(ids).size).toBe(2)
    expect(ids.every((id) => id && id.startsWith('modal-title-'))).toBe(true)
  })

  it('ariaLabel 直接命名；ariaLabelledby 传了就直接用（?? 优先级：不落到 titleId）', async () => {
    const labelled = factory({ modelValue: true, ariaLabel: '话题详情' })
    await settle(labelled)
    expect(panel()?.getAttribute('aria-label')).toBe('话题详情')

    const override = factory({ modelValue: true, ariaLabelledby: 'ext-42' }, { title: 'T' })
    await settle(override)
    expect(lastPanel()?.getAttribute('aria-labelledby')).toBe('ext-42')
  })

  it('无 title 插槽、无 aria prop ⇒ 两个标注属性都不出现（不指向空标题节点）', async () => {
    const wrapper = factory({ modelValue: true })
    await settle(wrapper)
    // `ariaLabel || null` 与 `$slots.title ? titleId : null` 的空值分支：绑 null 是不落属性。
    // 跑红配方（本文件实跑）：把三元收成 `?? titleId` ⇒ 本条报
    // `expected 'modal-title-13' to be null`（13 是当时实例的 uid，属性出现了就是红）。
    expect(panel()?.getAttribute('aria-label')).toBeNull()
    expect(panel()?.getAttribute('aria-labelledby')).toBeNull()
  })
})

describe('Modal — closed 与焦点返还', () => {
  it('受控关闭：closed 排在 close 之后，焦点还给打开前的元素', async () => {
    const openerEl = focusableOpener()
    const wrapper = factory({ modelValue: false })
    await settle(wrapper)
    openerEl.focus()
    expect(document.activeElement).toBe(openerEl)

    await wrapper.setProps({ modelValue: true })
    await settle(wrapper)
    expect(panel()?.contains(document.activeElement)).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('closed')).toBeUndefined()
    // closed 属可见性下降沿，不属 close() 调用：受控父级把值落下来才发（Drawer.test.ts 同一条钉）。
    await wrapper.setProps({ modelValue: false })
    await settle(wrapper)
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('closed')).toHaveLength(1)
    const evts = Object.keys(wrapper.emitted())
    expect(evts.indexOf('close')).toBeLessThan(evts.indexOf('closed'))
    expect(document.activeElement).toBe(openerEl)
  })

  it('v-if 挂载即开 → 关闭仍记得到 opener 并返还焦点（{ immediate: true } 的承重面，R-T10-2）', async () => {
    const openerEl = focusableOpener()
    openerEl.focus()
    // 不传 modelValue：13 个存量调用点的形状，可见性由外层 v-if（这里是"挂载这件事"）决定。
    // 没有 immediate，watch 看不见挂载时的 true ⇒ opener 永远是 null ⇒ 下面的焦点断言红
    // （closed 反而会照发——下降沿 (true→false) 自己成立，所以**焦点断言才是这条用例的钉子**）。
    const wrapper = factory()
    await settle(wrapper)
    expect(panel()?.contains(document.activeElement)).toBe(true)

    await wrapper.setProps({ modelValue: false })
    await settle(wrapper)
    expect(wrapper.emitted('closed')).toHaveLength(1)
    expect(document.activeElement).toBe(openerEl)
  })

  it('关闭态挂载不记 opener、不发 closed；开着被卸载也不补发 closed（与 Drawer 同形）', async () => {
    const closed = factory({ modelValue: false })
    await settle(closed)
    expect(closed.emitted('closed')).toBeUndefined()
    closed.unmount()
    expect(closed.emitted('closed')).toBeUndefined()
    // 跑红配方：给 onScopeDispose/onBeforeUnmount 补一句 emit('closed') ⇒ 最后一处
    // `expected [ [ … ] ] to be undefined`。Drawer 也没在卸载时补发，两原语一套规则。
  })
})

describe('Modal — closeOnClickModal', () => {
  it('false ⇒ 按下遮罩不关闭；关闭按钮与 Esc 照常（开关只管遮罩）', async () => {
    const wrapper = factory({ modelValue: true, closeOnClickModal: false })
    await settle(wrapper)
    fire(document.body.querySelector('.overlay'), 'mousedown')
    expect(wrapper.emitted('close')).toBeUndefined()
    fire(document.body.querySelector('.modal-head .t-icon-btn'), 'click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('close')).toHaveLength(2)
  })
  // 跑红配方：① 删 `if (props.closeOnClickModal === false) return` ⇒ 本文件实跑 1 failed，
  // 第一处报 `expected [ [] ] to be undefined`（遮罩按下补发了一次 close）；
  // ② 把门挪到 target 判定**之后**不红——门在 `close()` 之前任何位置语义都成立，
  // 这条用例钉的是"有没有门"，不是"门在第几行"（如实记：挪位置这个方向在此不可证伪）。

  it('true 显式传入 ⇒ 遮罩按下照常关闭（三态：缺省面已由 Modal.compat.test.ts 钉住，不重复）', async () => {
    const wrapper = factory({ modelValue: true, closeOnClickModal: true })
    await settle(wrapper)
    fire(document.body.querySelector('.overlay'), 'mousedown')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

describe('Modal — 焦点陷阱组件面（题面四条要求各一）', () => {
  const SLOT = {
    title: 'T',
    default: '<input class="probe-a" /><button class="probe-b">b</button>',
  }

  it('需求 1：尾元素 Tab 回绕到面板首个可聚焦控件；Shift+Tab 反向回绕；中段不误伤', async () => {
    const wrapper = factory({ modelValue: true }, SLOT)
    await settle(wrapper)
    const closeBtn = panel()!.querySelector('.modal-head .t-icon-btn')
    const probeA = panel()!.querySelector('.probe-a')
    const probeB = panel()!.querySelector('.probe-b')
    // 面板文档序 = [closeBtn, probeA, probeB]（.modal-head 在 .modal-body 之前，
    // Modal.focus.test.ts 量过的落点口径）。回绕目标按导入的 DIALOG_FOCUSABLE_SELECTOR 现算，
    // 不写死节点——写死就是把"第二套可聚焦口径"塞进测试。
    const ring = Array.from(panel()!.querySelectorAll<HTMLElement>(DIALOG_FOCUSABLE_SELECTOR))
    expect(ring).toEqual([closeBtn, probeA, probeB].filter(Boolean))

    ;(ring.at(-1) as HTMLElement).focus()
    expect(pressTab(ring.at(-1)!).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(ring[0])

    ring[0].focus()
    expect(pressTab(ring[0], true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(ring.at(-1))

    // 中段元素上的 Tab 不该被动过（只拦越界）：
    ring[1].focus()
    expect(pressTab(ring[1]).defaultPrevented).toBe(false)
    expect(document.activeElement).toBe(ring[1])
  })

  it('需求 1 续：可聚焦集合就是 DIALOG_FOCUSABLE_SELECTOR——select 参与回绕、disabled 不入环', async () => {
    // 尾部塞一个 disabled 按钮：若环把 disabled 算进去，"回绕尾"就不是 select；
    // 若集合是自创的 button/input/a 清单（缺 select），回绕尾会停在 closeBtn 自己。
    // 两个方向的变异都红在同一处 activeElement。
    const wrapper = factory(
      { modelValue: true },
      {
        title: 'T',
        default: '<select class="probe-sel"></select><button class="probe-off" disabled></button>',
      },
    )
    await settle(wrapper)
    const closeBtn = panel()!.querySelector('.modal-head .t-icon-btn') as HTMLElement
    const sel = panel()!.querySelector('.probe-sel')
    closeBtn.focus()
    expect(pressTab(closeBtn, true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(sel)
  })

  it('需求 2：开时 document 上多挂 1 个 keydown（陷阱），关闭边沿摘掉它，卸载后清零', async () => {
    interface RawListeners {
      addEventListener: (type: string, listener: EventListener, options?: unknown) => void
      removeEventListener: (type: string, listener: EventListener, options?: unknown) => void
    }
    const raw = document as unknown as RawListeners
    const rawAdd = raw.addEventListener.bind(document)
    const rawRemove = raw.removeEventListener.bind(document)
    const added: EventListener[] = []
    const removed: EventListener[] = []
    const addSpy = vi
      .spyOn(document, 'addEventListener')
      .mockImplementation((type, listener, o) => {
        if (type === 'keydown') added.push(listener as EventListener)
        rawAdd(type, listener as EventListener, o)
      })
    const removeSpy = vi
      .spyOn(document, 'removeEventListener')
      .mockImplementation((type, listener, o) => {
        if (type === 'keydown') removed.push(listener as EventListener)
        rawRemove(type, listener as EventListener, o)
      })
    try {
      const wrapper = factory({ modelValue: false })
      await settle(wrapper)
      // 关闭态只有 Modal 自己的常驻 Esc 监听（Task 6/7 的形状），陷阱不得在场。
      expect(added).toHaveLength(1)

      await wrapper.setProps({ modelValue: true })
      await settle(wrapper)
      expect(added).toHaveLength(2) // +1：陷阱装载

      await wrapper.setProps({ modelValue: false })
      await settle(wrapper)
      expect(removed).toHaveLength(1) // 关闭边沿：只摘陷阱那一个
      expect(added.filter((fn) => !removed.includes(fn))).toHaveLength(1) // 剩 Esc

      wrapper.unmount()
      expect(removed).toHaveLength(2) // 卸载归还：清零
    } finally {
      addSpy.mockRestore()
      removeSpy.mockRestore()
    }
  })
  // 跑红配方（本文件实跑，删 Modal.vue 的 useFocusTrap(rootRef, open) 一行 ⇒ 5 条陷阱用例全红）：
  // 四条回绕/跳过滤用例报 `expected false to be true`（没人拦 Tab 了）；
  // 计数那条报 `expected [ [Function onEsc] ] to have a length of 2 but got 1`（陷阱根本没挂）。
  // 单元级对偶：删 useFocusTrap.ts 的 onScopeDispose / 删 watch 关闭分支的 removeEventListener，
  // 各自在 useFocusTrap.test.ts 的「装载/卸载边沿」条红（那边的原文记在那文件的注释与台账 P0b-FOCUS-01）。

  it('需求 3：display:none 与 disabled 的尾控件被跳过，回绕落在最后一个可见可用者', async () => {
    const wrapper = factory(
      { modelValue: true },
      {
        title: 'T',
        default:
          '<input class="probe-vis" />' +
          '<button class="probe-hidden" style="display:none">隐</button>' +
          '<button class="probe-off" disabled>禁</button>',
      },
    )
    await settle(wrapper)
    const closeBtn = panel()!.querySelector('.modal-head .t-icon-btn') as HTMLElement
    const vis = panel()!.querySelector('.probe-vis')
    closeBtn.focus()
    expect(pressTab(closeBtn, true).defaultPrevented).toBe(true)
    // 无 isHiddenWithin 过滤时环尾是 probe-hidden（jsdom 照样能把焦点给 display:none 元素），
    // 本处报 `expected <button class="probe-hidden">… to be <input class="probe-vis">…`。
    expect(document.activeElement).toBe(vis)
  })

  it('需求 4：两个 Modal 面板叠着开——内层 Tab 在内层回绕，外层陷阱不得动焦点', async () => {
    const outer = factory(
      { modelValue: true },
      { title: '外层', default: '<button class="probe-x">o</button>' },
    )
    await settle(outer)
    const inner = factory(
      { modelValue: true },
      { title: '内层', default: '<button class="probe-x">i</button>' },
    )
    await settle(inner)
    expect(panels()).toHaveLength(2)

    const innerPanel = lastPanel()!
    const innerProbe = innerPanel.querySelector('.probe-x') as HTMLElement
    const innerClose = innerPanel.querySelector('.modal-head .t-icon-btn') as HTMLElement
    innerProbe.focus()

    // 数"焦点被移动了几次"：contains 守卫健在时，外层监听对这次 Tab 一个字都不该说；
    // 守卫被摘 ⇒ 外层先把焦点抢去 outerClose、内层再抢回 innerClose——末态碰巧对，
    // 但移动次数 2 当场暴露（useFocusTrap.test.ts 嵌套条同一手法，这里过一遍真实组件）。
    const focusSpy: HTMLElement[] = []
    const realFocus = HTMLElement.prototype.focus
    const spy = vi.spyOn(HTMLElement.prototype, 'focus').mockImplementation(function (
      this: HTMLElement,
    ) {
      focusSpy.push(this)
      realFocus.call(this)
    })
    try {
      focusSpy.length = 0
      expect(pressTab(innerProbe).defaultPrevented).toBe(true)
      expect(document.activeElement).toBe(innerClose)
      expect(focusSpy).toEqual([innerClose])
    } finally {
      spy.mockRestore()
    }
  })
})
