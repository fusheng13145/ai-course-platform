import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { ElConfigProvider, ElDialog } from 'element-plus'
import Modal from '../Modal.vue'
import Drawer from '../Drawer.vue'

/**
 * P4-GATE-01：Phase 4 准入门收口的五个新能力面（spec §11.3 #1/#2/#3/#4/#6）。
 * ================================================================================
 * - #1 任意宽度通道：Modal/Drawer 的 width prop（数字 px、字符串原样、优先于尺寸档）
 * - #2 字符串标题通道：title prop 与 #title 插槽并存（插槽优先）、aria-labelledby 联动
 * - #3 层叠：theme.css 的 --z-modal 2200 / --z-drawer 2250（源码断言，jsdom 不解析 CSS 变量，
 *   计算值断言做不了；EP 计数器起点 2300 用 ElDialog 的 inline z-index 实测钉住）
 * - #4 closed 挂 after-leave（Modal.a11y / Drawer.test 的既有用例已随本批改写，不重复）
 * - #6 Esc 只关最上层：跨原语栈（Modal 开→Drawer 开→Esc 只关 Drawer）
 *
 * rAF 用可控队列替换（a11y/Drawer 同做法）：Transition 的 leave→after-leave 链路
 * 至少两帧，真实 rAF 的异步性会跨用例泄漏。
 */

let rafQueue: FrameRequestCallback[] = []

function flushFrames() {
  const q = rafQueue
  rafQueue = []
  q.forEach((cb) => cb(0))
}

async function settle(wrapper: { vm: { $nextTick(): Promise<void> } }) {
  for (let i = 0; i < 3; i++) {
    await wrapper.vm.$nextTick()
    flushFrames()
  }
  await wrapper.vm.$nextTick()
}

const wrappers: { unmount(): void }[] = []
const extras: HTMLElement[] = []

function trackMount<W extends { unmount(): void }>(w: W): W {
  wrappers.push(w)
  return w
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
  for (const w of wrappers) w.unmount()
  wrappers.length = 0
  document.body
    .querySelectorAll('.overlay, .modal, .drawer-overlay, .drawer')
    .forEach((n) => n.remove())
  for (const el of extras) el.remove()
  extras.length = 0
  ;(document.activeElement as HTMLElement | null)?.blur?.()
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
  document.body.style.overflow = ''
  rafQueue = []
  vi.unstubAllGlobals()
})

describe('P4-GATE-01 #1 任意宽度通道', () => {
  it('Modal：width 数字按 px 写进面板 style，优先于 wide 档', async () => {
    const w = trackMount(
      mount(Modal, {
        props: { wide: true, width: 600 },
        slots: { title: 'T' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w)
    const panel = document.body.querySelector<HTMLElement>('.modal')
    expect(panel).not.toBeNull()
    expect(panel!.style.width).toBe('600px')
    // 尺寸档类仍在（视觉继承面不变），但内联 style 压过类的 880px
    expect(panel!.classList.contains('wide')).toBe(true)
  })

  it('Modal：width 字符串原样透传（rem/min() 等表达式合法）', async () => {
    const w = trackMount(
      mount(Modal, {
        props: { width: 'min(90vw, 720px)' },
        slots: { title: 'T' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w)
    const panel = document.body.querySelector<HTMLElement>('.modal')
    expect(panel!.style.width).toBe('min(90vw, 720px)')
  })

  it('Drawer：width 数字按 px 写进面板 style，优先于 wide 档', async () => {
    const w = trackMount(
      mount(Drawer, {
        props: { modelValue: true, wide: true, width: 560 },
        slots: { title: 'T' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w)
    const panel = document.body.querySelector<HTMLElement>('.drawer')
    expect(panel).not.toBeNull()
    expect(panel!.style.width).toBe('560px')
  })
})

describe('P4-GATE-01 #2 字符串标题通道', () => {
  it('Modal：title prop 渲染且 aria-labelledby 指向标题节点；插槽内容优先于 prop', async () => {
    const w = trackMount(
      mount(Modal, {
        props: { title: '来自 prop 的标题' },
        slots: {},
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w)
    const panel = document.body.querySelector<HTMLElement>('.modal')
    const titleEl = document.body.querySelector<HTMLElement>('.modal-title')
    expect(titleEl!.textContent).toContain('来自 prop 的标题')
    expect(panel!.getAttribute('aria-labelledby')).toBe(titleEl!.id)

    // 插槽优先：给了 #title 插槽时 prop 不渲染
    const w2 = trackMount(
      mount(Modal, {
        props: { title: '来自 prop 的标题' },
        slots: { title: '来自插槽的标题' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w2)
    const title2 = document.body.querySelectorAll('.modal-title')
    expect(title2[title2.length - 1]!.textContent).toBe('来自插槽的标题')
  })

  it('Drawer：title prop 渲染且 aria-labelledby 指向标题节点', async () => {
    const w = trackMount(
      mount(Drawer, {
        props: { modelValue: true, title: '抽屉标题' },
        slots: {},
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(w)
    const panel = document.body.querySelector<HTMLElement>('.drawer')
    const titleEl = document.body.querySelector<HTMLElement>('.modal-title')
    expect(titleEl!.textContent).toContain('抽屉标题')
    expect(panel!.getAttribute('aria-labelledby')).toBe(titleEl!.id)
  })
})

describe('P4-GATE-01 #6 Esc 只关最上层（跨原语共享栈）', () => {
  it('Modal 开 → Drawer 开 → Esc 只关 Drawer；写回后再 Esc 轮到 Modal', async () => {
    const modal = trackMount(
      mount(Modal, {
        props: { modelValue: true },
        slots: { title: 'M' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(modal)
    const drawer = trackMount(
      mount(Drawer, {
        props: { modelValue: true },
        slots: { title: 'D' },
        attachTo: document.body,
        global: { stubs: { transition: false } },
      }),
    )
    await settle(drawer)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle(drawer)
    expect(drawer.emitted('close')).toHaveLength(1)
    expect(drawer.emitted('update:modelValue')?.[0]).toEqual([false])
    expect(modal.emitted('close')).toBeUndefined()

    // v-model 写回（迁移点的真实形状）→ Drawer 出栈
    await drawer.setProps({ modelValue: false })
    await settle(drawer)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle(modal)
    expect(modal.emitted('close')).toHaveLength(1)
    expect(modal.emitted('update:modelValue')?.[0]).toEqual([false])
    expect(drawer.emitted('close')).toHaveLength(1) // 仍只有一次
  })
})

describe('P4-GATE-01 #3 EP 弹层计数器起点（ElConfigProvider zIndex=2300）', () => {
  it('ElDialog 的 inline z-index ≥ 2300（恒高于 drawer 2250，modal 内下拉可弹出）', async () => {
    trackMount(
      mount(
        {
          template: `<el-config-provider :z-index="2300"><el-dialog v-model="visible" title="z" /></el-config-provider>`,
          data: () => ({ visible: true }),
          // 单测没有 unplugin 的按需解析，kebab 标签须显式注册（ConfigProvider 无样式，
          // 不受 P3-EPSCSS-01「EP 样式按需注入」事实影响）
          components: { ElConfigProvider, ElDialog },
        },
        { attachTo: document.body, global: { stubs: { transition: false } } },
      ),
    )
    await nextTick()
    // EP 2.14 的 z inline style 挂在 .el-overlay（遮罩层）上，.el-dialog 只是它的子节点
    const overlay = document.body.querySelector<HTMLElement>('.el-overlay')
    expect(overlay).not.toBeNull()
    const z = parseInt(overlay!.style.zIndex, 10)
    expect(z).toBeGreaterThanOrEqual(2300)
  })
})
