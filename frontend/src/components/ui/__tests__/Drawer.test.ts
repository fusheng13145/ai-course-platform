import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Drawer from '../Drawer.vue'
import { DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'

/**
 * Drawer 与 Modal 同通道钉（Task 11）
 * ==================================
 * Phase 4 要把 4 处裸 `<el-drawer>`（EgEventsDrawer / DscDetailDrawer / TaskDrawer /
 * CdsDetailDrawer）收进 ui/Drawer，而它们恰是全仓弹层文件里**唯一没有任何焦点管理**的一组。
 * 若 Drawer 的能力比 Modal 少一截，Phase 4 就得对弹窗、抽屉写两套迁移规则，
 * 并在迁移后再补一次能力——本文件把「两套」钉成「一套」。
 *
 * 与 brief 的 4 处偏离（依据见 task-11-report §2）：
 * - D-1 挂载点：brief 用 `attachTo: document.body`，那样「是否 Teleport」这条断言
 *   **不可能失败**——就地渲染的 `.drawer-overlay` 同样是 body 的直接子节点。
 *   这里改挂独立 host `<div>`，用「节点在不在 host 里」区分两条渲染路径。
 * - D-2 断言对象：brief 写 `panel.parentElement === document.body`，但本仓结构是
 *   `.drawer-overlay > .drawer`（Drawer.vue:8-9），面板的直接父节点永远是遮罩而不是 body。
 *   改成「overlay 挂 body」+「panel 挂 overlay」两条，覆盖同一意图且真的会失败。
 * - D-3 负向断言：brief 的 `wrapper.find('.drawer-overlay').exists()` 在 Teleport 下恒为
 *   false（wrapper 取不到传送内容），单写它是假阳性锁；必须与 body 查询成对才成立。
 * - D-4 交互查询：Teleport 一落地 `wrapper.find/get` 就取不到面板内节点，所以本文件的
 *   点击/按下全部走 `document.body.querySelector` + `dispatchEvent`，与「加 Teleport」
 *   写在同一个 commit 里（Task 6 给 Task 8 留的教训，这里同样适用）。
 *
 * requestAnimationFrame 用可控队列替换（对齐 useDialogFocus.test.ts:19-24 的既有做法——
 * 该原语的焦点动作就排在 rAF 里，不接管就无法确定性地断点）。
 */

interface TrackedWrapper {
  element: Element
  unmount(): void
  vm: { $nextTick(): Promise<void> }
}

const wrappers: TrackedWrapper[] = []
const hosts: HTMLElement[] = []
let rafQueue: FrameRequestCallback[] = []

/** 抽屉的受控通道（与 Modal 同名；显式建模，避免用 Record<string, unknown> 绕过类型） */
interface DrawerProps {
  modelValue?: boolean
  placement?: 'rtl' | 'ltr'
  teleported?: boolean
  closeOnClickModal?: boolean
  ariaLabel?: string
  ariaLabelledby?: string
}

function factory(
  props: DrawerProps = {},
  slots: Record<string, string> = {},
  attrs: Record<string, string> = {},
) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  hosts.push(host)
  const wrapper = mount(Drawer, {
    props,
    attrs,
    slots: { title: '标题', ...slots },
    attachTo: host,
  })
  wrappers.push(wrapper)
  return { wrapper, host }
}

/** 冲刷 useDialogFocus 的 rAF 队列（watch/nextTick 已过、帧回调待执行） */
function flushFrames() {
  const queue = rafQueue
  rafQueue = []
  queue.forEach((cb) => cb(0))
}

/**
 * 等渲染 + 冲刷帧回调。useDialogFocus 的链路是 watch(pre) → nextTick → requestAnimationFrame
 * （useDialogFocus.ts:45-55），而「挂载即打开」时 onMounted 里翻 mounted 才算第一次触发，
 * nextTick 回调落在哪一轮 flush 取决于 Vue 当前的队列位置——所以来回三轮，
 * 每轮都既让微任务推进、又冲刷帧队列，实现侧少排一次就永远等不到焦点（用例照红）。
 */
async function settle(wrapper: TrackedWrapper) {
  for (let i = 0; i < 3; i++) {
    await wrapper.vm.$nextTick()
    flushFrames()
  }
  await wrapper.vm.$nextTick()
}

/** body 里当前所有抽屉面板，按挂载顺序（Teleport 后这是唯一可靠的查询路径） */
function panels() {
  return Array.from(document.body.querySelectorAll('.drawer'))
}

/** 最近挂载的那个面板：同一条用例挂第二个抽屉时，不能用第一个的节点做断言 */
function lastPanel() {
  return panels().at(-1) ?? null
}

/** 从 body 取第一个抽屉面板 */
function panel() {
  return panels()[0] ?? null
}

/** 最近挂载的那个遮罩：同一条用例挂第二个抽屉时，不能用第一个的节点做断言 */
function lastOverlay() {
  return Array.from(document.body.querySelectorAll('.drawer-overlay')).at(-1) ?? null
}

function fire(el: Element | null, type: 'mousedown' | 'click') {
  expect(el, `找不到 ${type} 目标元素`).not.toBeNull()
  el?.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true }))
}

beforeEach(() => {
  rafQueue = []
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
    rafQueue.push(cb)
    return rafQueue.length
  })
  vi.stubGlobal('cancelAnimationFrame', () => {})
  // 滚动锁是 body 上的引用计数状态，跨用例必须清零，否则「还原」类断言会读到上一条的残留。
  delete document.body.dataset.overlayCount
  document.body.style.overflow = ''
})

afterEach(() => {
  for (const w of wrappers) {
    if (w.element.isConnected) w.unmount()
  }
  wrappers.length = 0
  // Teleport 的落点在 body 上，unmount 是否连带摘掉传送节点属 Vue 内部行为；
  // 不赌它——直接清场，保证下一条用例的 body 查询只命中自己挂的节点。
  document.body.querySelectorAll('.drawer-overlay, .drawer').forEach((n) => n.remove())
  for (const h of hosts) h.remove()
  hosts.length = 0
  delete document.body.dataset.overlayCount
  document.body.style.overflow = ''
  vi.unstubAllGlobals()
})

describe('Drawer — 可见性与传送', () => {
  it('modelValue=false → 不渲染（挂载点与 body 都查不到节点）', async () => {
    const { wrapper, host } = factory({ modelValue: false }, { title: 'T' })
    await settle(wrapper)
    expect(host.querySelector('.drawer-overlay')).toBeNull()
    expect(document.body.querySelectorAll('.drawer-overlay')).toHaveLength(0)
    expect(panel()).toBeNull()
    wrapper.unmount()
  })

  it('不传 modelValue 仍渲染（RagDocs.vue:90 的 v-if + 无 model-value 形态）', async () => {
    // 钉的是「三态」而不是布尔：Boolean prop 缺省又没写 default 时，Vue 把缺席值强制成
    // false（runtime-core resolvePropValue 的 isAbsent && !hasDefault 分支），
    // v-if 式调用点会直接打不开——brief 的 `defineProps<{ modelValue?: boolean }>()` 正踩这个坑。
    const { wrapper } = factory()
    await settle(wrapper)
    expect(panel()).not.toBeNull()
  })

  it('打开时 Teleport 到 body：节点离开挂载点，overlay 直接挂 body', async () => {
    const { wrapper, host } = factory({ modelValue: true }, { title: '风险明细' })
    await settle(wrapper)
    const overlay = document.body.querySelector('.drawer-overlay')
    expect(host.querySelector('.drawer')).toBeNull()
    expect(overlay?.parentElement).toBe(document.body)
    expect(panel()?.parentElement).toBe(overlay)
    expect(panel()?.getAttribute('role')).toBe('dialog')
    expect(panel()?.getAttribute('aria-modal')).toBe('true')
  })

  it('teleported=false → 留在挂载点内（与 Modal 同名通道）', async () => {
    const { wrapper, host } = factory({ modelValue: true, teleported: false })
    await settle(wrapper)
    const overlay = host.querySelector('.drawer-overlay')
    expect(overlay).not.toBeNull()
    // VTU 在 attachTo 之内还会再套一层挂载容器，所以这里不比 parentElement === host，
    // 比的是「没被传送」：节点仍在 host 子树内，且不是 body 的直接子节点。
    expect(overlay?.parentElement).not.toBe(document.body)
    expect(host.querySelector('.drawer')?.getAttribute('role')).toBe('dialog')
  })

  it('placement 默认 rtl、显式 ltr 时挂 ltr 类（对齐 index.scss:342-347 的方向命名）', async () => {
    const rtl = factory({ modelValue: true })
    await settle(rtl.wrapper)
    expect(rtl.host.querySelector('.drawer')).toBeNull()
    expect(lastPanel()?.classList.contains('rtl')).toBe(true)
    expect(lastPanel()?.classList.contains('ltr')).toBe(false)

    const ltr = factory({ modelValue: true, placement: 'ltr' })
    await settle(ltr.wrapper)
    expect(lastPanel()?.classList.contains('ltr')).toBe(true)
    expect(panels()).toHaveLength(2)
  })

  it('wide 仍走 $attrs 布尔通道，且类落在渲染后的 .drawer 上', async () => {
    // 属性契约（Task 6 的 R-T6-3）：wide 不声明进 props，模板从 $attrs 读。
    // 断言看**渲染出来的 classList**，不看 props 值——把 wide 声明进 props 却忘了改模板
    // 读取时，$attrs.wide 变 undefined、类静默消失，而 prop 断言仍绿。
    const wide = factory({}, {}, { wide: '' })
    await settle(wide.wrapper)
    expect(lastPanel()?.classList.contains('wide')).toBe(true)

    const plain = factory({ modelValue: true })
    await settle(plain.wrapper)
    expect(lastPanel()?.classList.contains('wide')).toBe(false)
    expect(lastPanel()?.classList.contains('rtl')).toBe(true)
  })

  it('title 插槽提供时用 aria-labelledby 指向标题节点；同页多抽屉的 id 不撞车', async () => {
    const { wrapper } = factory({ modelValue: true }, { title: '违规事件核查' })
    await settle(wrapper)
    const labelledby = lastPanel()?.getAttribute('aria-labelledby')
    expect(labelledby).toBeTruthy()
    expect(document.getElementById(labelledby as string)?.textContent).toBe('违规事件核查')

    const labelled = factory({ modelValue: true, ariaLabel: '话题详情' })
    await settle(labelled.wrapper)
    expect(lastPanel()?.getAttribute('aria-label')).toBe('话题详情')

    // 一个页面同时开两个抽屉是迁移后的常态（TaskDrawer + 确认层）：id 取实例 uid，
    // 写死常量的话第二个的 aria-labelledby 会指回第一个的标题，读屏朗读张冠李戴。
    const second = factory({ modelValue: true }, { title: '第二个抽屉' })
    await settle(second.wrapper)
    const ids = panels().map((p) => p.getAttribute('aria-labelledby'))
    expect(ids).toHaveLength(3)
    expect(new Set(ids).size).toBe(3)
  })

  it('调用点的 class 仍落到遮罩根上（inheritAttrs:false 之后由 v-bind="$attrs" 承接）', async () => {
    // 改动前根节点就是 .drawer-overlay，class 自动落它上面。本文件声明了
    // defineOptions({ inheritAttrs: false })（根换成 Teleport 后自动继承并不可靠），
    // 那条通道只能靠模板里的 v-bind="$attrs"——漏写不报错、不红灯，只是样式静默丢。
    const { wrapper } = factory({ modelValue: true }, {}, { class: 'site-hook' })
    await settle(wrapper)
    expect(lastOverlay()?.classList.contains('drawer-overlay')).toBe(true)
    expect(lastOverlay()?.classList.contains('site-hook')).toBe(true)
  })
})

describe('Drawer — 关闭通道', () => {
  it('Esc emit close + update:modelValue(false)（行为新增：Drawer 此前无 Esc 通道）', async () => {
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle(wrapper)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('关闭态按 Esc 不发幽灵 close（监听只在打开期间挂）', async () => {
    const { wrapper } = factory({ modelValue: false })
    await settle(wrapper)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle(wrapper)
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('关闭按钮 emit close + update:modelValue(false)', async () => {
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    fire(document.body.querySelector('.drawer-head .t-icon-btn'), 'click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('按下遮罩本体 emit close；按下面板内不 emit（mousedown 通道不变）', async () => {
    const overlay = factory({ modelValue: true })
    await settle(overlay.wrapper)
    fire(document.body.querySelector('.drawer-overlay'), 'mousedown')
    expect(overlay.wrapper.emitted('close')).toHaveLength(1)

    const inner = factory({ modelValue: true })
    await settle(inner.wrapper)
    // 面板本体：事件冒泡到遮罩，但 target 不是遮罩本体 → 不得关闭
    fire(panel(), 'mousedown')
    expect(inner.wrapper.emitted('close')).toBeUndefined()
  })

  it('closeOnClickModal=false → 遮罩按下不关闭', async () => {
    const { wrapper } = factory({ modelValue: true, closeOnClickModal: false })
    await settle(wrapper)
    fire(document.body.querySelector('.drawer-overlay'), 'mousedown')
    expect(wrapper.emitted('close')).toBeUndefined()
    // 该开关只管遮罩：关闭按钮仍然可用
    fire(document.body.querySelector('.drawer-head .t-icon-btn'), 'click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('受控父级关抽屉时 emit closed（排在 close 之后）并把焦点还给打开前的元素', async () => {
    const { wrapper, host } = factory({ modelValue: false })
    const opener = document.createElement('button')
    opener.textContent = '打开'
    host.appendChild(opener)
    opener.focus()
    expect(document.activeElement).toBe(opener)

    await wrapper.setProps({ modelValue: true })
    await settle(wrapper)
    expect(panel()?.contains(document.activeElement)).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('closed')).toBeUndefined()
    // 受控父级收到 update:modelValue 后才真正把值落下来：closed 属可见性边沿，不属 close() 调用
    await wrapper.setProps({ modelValue: false })
    await settle(wrapper)
    expect(wrapper.emitted('closed')).toHaveLength(1)
    const evts = Object.keys(wrapper.emitted())
    expect(evts.indexOf('close')).toBeLessThan(evts.indexOf('closed'))
    expect(document.activeElement).toBe(opener)
  })

  it('卸载必须摘掉打开期间挂上的 document keydown 监听', async () => {
    // 观察点是监听器注册表，不是 emitted 计数：VTU 的 unmount() 会 removeEventHistory
    // （vue-test-utils.cjs.js:7601-7610、7857-7867），Vue 的 emit() 对已卸载实例起手即 return
    // （runtime-core.cjs.js:4483-4484），所以「卸载后再按 Esc、断言 emitted 不增长」不可能失败。
    // 调用点靠 v-if 反复开关实例（RagDocs.vue:90），残留 handler 是真实的幽灵回调来源。
    interface RawListeners {
      addEventListener: (type: string, listener: EventListener, options?: unknown) => void
      removeEventListener: (type: string, listener: EventListener, options?: unknown) => void
    }
    const raw = document as unknown as RawListeners
    const rawAdd = raw.addEventListener.bind(document)
    const rawRemove = raw.removeEventListener.bind(document)
    const captureOf = (o?: boolean | { capture?: boolean }): boolean =>
      typeof o === 'boolean' ? o : !!o?.capture
    type Registration = readonly [type: string, listener: EventListener, capture: boolean]
    const added: Registration[] = []
    const removed: Registration[] = []

    const addSpy = vi
      .spyOn(document, 'addEventListener')
      .mockImplementation((type, listener, options) => {
        added.push([type, listener as EventListener, captureOf(options)])
        rawAdd(type, listener as EventListener, options)
      })
    const removeSpy = vi
      .spyOn(document, 'removeEventListener')
      .mockImplementation((type, listener, options) => {
        removed.push([type, listener as EventListener, captureOf(options)])
        rawRemove(type, listener as EventListener, options)
      })

    try {
      const closed = factory({ modelValue: false })
      await settle(closed.wrapper)
      expect(added.filter(([t]) => t === 'keydown')).toHaveLength(0)
      closed.wrapper.unmount()

      const open = factory({ modelValue: true })
      await settle(open.wrapper)
      const keydownAdds = added.filter(([t]) => t === 'keydown')
      expect(keydownAdds.length).toBeGreaterThan(0)
      open.wrapper.unmount()
      const leaked = keydownAdds.filter(
        ([t, fn, cap]) => !removed.some(([rt, rf, rc]) => rt === t && rf === fn && rc === cap),
      )
      expect(leaked).toEqual([])
    } finally {
      addSpy.mockRestore()
      removeSpy.mockRestore()
    }
  })
})

describe('Drawer — 滚动锁与焦点', () => {
  it('打开期间锁滚动，关闭还原（计数落在 dataset.overlayCount）', async () => {
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.dataset.overlayCount).toBe('1')

    await wrapper.setProps({ modelValue: false })
    await settle(wrapper)
    expect(document.body.style.overflow).toBe('')
    expect(document.body.dataset.overlayCount).toBe('0')
  })

  it('与 Modal 共享同一把计数锁：外层还开着时不得提前还原', async () => {
    // dataset.overlayCount 是 Modal（Task 10 的 useOverlayScrollLock）与 Drawer 共用的状态键。
    // 布尔版锁在这里必然穿：抽屉一关就把 overflow 置回 ''，而外层弹窗还开着却能滚背景。
    document.body.dataset.overlayCount = '1'
    document.body.style.overflow = 'hidden'
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    expect(document.body.dataset.overlayCount).toBe('2')

    await wrapper.setProps({ modelValue: false })
    await settle(wrapper)
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('v-if 式调用点卸载即归还滚动锁（RagDocs.vue:90 正是该形态）', async () => {
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    expect(document.body.style.overflow).toBe('hidden')
    wrapper.unmount()
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(document.body.style.overflow).toBe('')
  })

  it('关闭态挂载不得白扣计数（初值 false 不是「从开到关」的过渡）', async () => {
    // Task 10 草案的 watch(open, ..., { immediate: true }) 只挡 `on === prev`，
    // 初次 false→undefined 会走过去减 1：叠在已开弹窗下面的关闭抽屉会把外层的滚动锁解开。
    document.body.dataset.overlayCount = '1'
    document.body.style.overflow = 'hidden'
    const { wrapper } = factory({ modelValue: false })
    await settle(wrapper)
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('计数被脏写成非数字时按 0 重开，而不是从此锁不住', async () => {
    // Number('abc') → NaN：NaN+1 仍是 NaN，Math.max(0, NaN)=NaN，
    // 于是 dataset 变 'NaN'、`next > 0` 恒 false → overflow 永远回 ''，滚动锁整体失效。
    document.body.dataset.overlayCount = 'abc'
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('v-model 式打开：焦点移入抽屉内首个可聚焦控件', async () => {
    const { wrapper } = factory({ modelValue: false }, { default: '<input class="probe-input" />' })
    await settle(wrapper)
    await wrapper.setProps({ modelValue: true })
    await settle(wrapper)
    const expected = panel()?.querySelector(DIALOG_FOCUSABLE_SELECTOR)
    expect(expected).not.toBeNull()
    expect(document.activeElement).toBe(expected)
    expect(panel()?.contains(document.activeElement)).toBe(true)
  })

  it('v-if 式打开（挂载即 open）同样接上焦点：TaskDrawer/EgEventsDrawer 迁移后靠这条', async () => {
    const { wrapper } = factory({ modelValue: true }, { default: '<input class="probe-input" />' })
    await settle(wrapper)
    const expected = panel()?.querySelector(DIALOG_FOCUSABLE_SELECTOR)
    expect(expected).not.toBeNull()
    expect(document.activeElement).toBe(expected)
  })

  it('明细型抽屉（无可输入控件）也把焦点送进面板：停在头部关闭按钮上', async () => {
    // 迁移目标 4 个 el-drawer 里有 3 个是只读明细（CdsDetailDrawer / DscDetailDrawer /
    // EgEventsDrawer），没有 input/textarea；此时面板内首个可聚焦控件就是头部的关闭按钮。
    // 焦点必须仍然进入抽屉——否则读屏与键盘用户被留在抽屉外的背景页里。
    const { wrapper } = factory({ modelValue: true })
    await settle(wrapper)
    const closeBtn = panel()?.querySelector('.drawer-head .t-icon-btn')
    expect(document.activeElement).toBe(closeBtn)
    expect(panel()?.contains(document.activeElement)).toBe(true)
  })
})
