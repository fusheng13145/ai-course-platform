import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, watch } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * I-4：ui/Drawer 调用点行为钉（NotificationManagement 那一头）
 * ==========================================================
 * 改前的 `NotificationManagement.vue:47` 是 `<NmPublishDrawer v-model="drawerOpen">`，
 * 而 `NmPublishDrawer.vue:3` 往 Drawer 传 `:model-value="modelValue"`（初值 false）。
 * 旧 Drawer **没声明 props**，这个绑定只是落到根元素上的一枚惰性 attribute（`modelvalue="false"`），
 * 于是该页一进来看得到一个 `position:fixed; inset:0` 的 0.38 alpha 全页遮罩 + 恒显示的发布抽屉。
 * 可见性门是真被修好了，但当时没有任何测试在这两个调用点上挂过抽屉——
 * 一次「顺手重构」就能悄悄退回原样。本文件就是那把锁（另一头见 RagDocs.drawerGate.test.ts）。
 *
 * 三条断言的分工：
 * - A 门本身：drawerOpen=false 时 DOM 里既没有遮罩也没有面板；true 才有。
 * - B 关闭链：Esc → 页面的 drawerOpen 落回 false → 面板消失且滚动锁归还（计数归 0）。
 * - C 转发形状：受控关闭时页面收到**一次** update:modelValue(false)、状态变一次。
 *   **这条在 Task 10C 被显式拆过锁**：原样钉的是"两次相同写回、状态只变一次"（双转发现状），
 *   本棒按裁定删掉 `@close` 那条重复写回后它必然转红（`expected 1 to be 2`，实跑在册），
 *   改钉为"一条腿一次写回"是**批准的行为变更**、不是放宽——判据方向没动，动的是被钉的现状。
 *   ⚠️ 别把本条读成"overlayCount 由它保护"：滚动锁归还是 B 的职责，C 只管写回次数。
 */

const mocks = vi.hoisted(() => ({
  getList: vi.fn(),
  send: vi.fn(),
  getAllUsers: vi.fn(),
  confirmDanger: vi.fn(),
  toastShow: vi.fn(),
}))

vi.mock('@/api/notification', () => ({
  default: {
    getNotificationList: mocks.getList,
    deleteNotification: vi.fn(),
    markAsRead: vi.fn(),
    publishNotification: vi.fn(),
    sendNotification: mocks.send,
  },
}))
vi.mock('@/api/admin', () => ({ default: { getAllUsers: mocks.getAllUsers } }))
vi.mock('@/utils/dangerConfirm', () => ({ confirmDanger: mocks.confirmDanger }))
vi.mock('@/composables/useToast', () => ({ toast: { show: mocks.toastShow } }))

import NotificationManagement from '@/views/admin/system/NotificationManagement.vue'
import NmPublishDrawer from '@/views/admin/system/components/NmPublishDrawer.vue'

const overlays = () => Array.from(document.body.querySelectorAll('.drawer-overlay'))
const panels = () => Array.from(document.body.querySelectorAll('.drawer'))

/** script setup 的内部绑定在测试环境经 vm 暴露；按仓内既有范式做类型桥接 */
interface PageVm {
  drawerOpen: boolean
}
const pageVm = (w: { vm: unknown }) => w.vm as unknown as PageVm

const trackers: Array<{ unmount(): void }> = []

function mountPage() {
  const wrapper = mount(NotificationManagement, {
    attachTo: document.body,
    global: { directives: { loading: { mounted() {}, updated() {} } } },
  })
  trackers.push(wrapper)
  return wrapper
}

/** 抽屉头部的关闭按钮（Teleport 后 wrapper.find 取不到，一律从 document 查，见 D-4） */
function clickPanelClose() {
  const btn = document.body.querySelector('.drawer-head .t-icon-btn')
  expect(btn, '找不到抽屉关闭按钮').not.toBeNull()
  btn?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getList.mockResolvedValue({ data: { records: [], total: 0 } })
})

afterEach(() => {
  while (trackers.length) trackers.pop()?.unmount()
  // Teleport 落点在 body，不赌 Vue 是否连带摘掉，直接清场（与 Drawer.test.ts 同一口径）。
  document.body.querySelectorAll('.drawer-overlay, .drawer').forEach((n) => n.remove())
  document.body.style.overflow = ''
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
})

describe('NmPublishDrawer 调用点 — 可见性门与关闭链', () => {
  it('drawerOpen=false → 遮罩与面板都不存在；点「发布通知」才出现（A）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(overlays()).toHaveLength(0)
    expect(panels()).toHaveLength(0)
    // 这一句在改前会红：旧 Drawer 恒渲染，遮罩此刻就在 DOM 里。

    const openBtn = wrapper.findAll('button').find((b) => b.text().trim() === '发布通知')
    expect(openBtn, '找不到页头「发布通知」按钮').toBeTruthy()
    await openBtn!.trigger('click')
    await nextTick()

    expect(overlays()).toHaveLength(1)
    expect(panels()).toHaveLength(1)
    expect(panels()[0].classList.contains('wide')).toBe(true)
    expect(panels()[0].getAttribute('role')).toBe('dialog')
    // Minor-5 在真实调用点上的表现：wide 归面板，不该跟着 $attrs 落到遮罩。
    expect(overlays()[0].hasAttribute('wide')).toBe(false)

    pageVm(wrapper).drawerOpen = false
    await nextTick()
    expect(panels()).toHaveLength(0)
    expect(overlays()).toHaveLength(0)
  })

  it('抽屉里按 Esc → 页面 drawerOpen 落回 false、面板消失、滚动锁归还（B）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    pageVm(wrapper).drawerOpen = true
    await nextTick()
    expect(panels()).toHaveLength(1)
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.dataset.overlayCount).toBe('1')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    await flushPromises()

    expect(pageVm(wrapper).drawerOpen).toBe(false)
    expect(panels()).toHaveLength(0)
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(document.body.style.overflow).toBe('')
  })

  it('抽屉头部关闭按钮同样把页面的 drawerOpen 关掉（Task 10C 起承重腿是 update:modelValue，不是 @close）', async () => {
    // 这条在改前证明的是"close 腿也接得住"；双转发删掉后它证明的是"不需要 close 腿也接得住"——
    // 同一次点击、同一个终态（drawerOpen 落 false、面板消失），只是写回通道收窄成一条。
    const wrapper = mountPage()
    await flushPromises()
    pageVm(wrapper).drawerOpen = true
    await nextTick()
    expect(panels()).toHaveLength(1)

    clickPanelClose()
    await nextTick()
    await flushPromises()

    expect(pageVm(wrapper).drawerOpen).toBe(false)
    expect(panels()).toHaveLength(0)
  })

  it('受控关闭：页面收到一次 update:modelValue(false)、状态变一次（C，Task 10C 拆锁后的形状）', async () => {
    // 写回只剩 `@update:model-value` 这一条腿（NmPublishDrawer.vue 的注释写明留哪条为什么）：
    // Drawer 的 close() 从此成对发 close 与 update:modelValue(false)，`close` 这条腿在本组件不接，
    // 所以一次关闭 = 一次写回。改前这条钉的是 handlerCalls 2 / stateChanges 1（双转发），
    // 删腿后同一对读数变成 1 / 1；stateChanges 仍是 1 —— 两次写回里第二次本来就因
    // Vue 的 ref setter 走 hasChanged 判定而不触发 effect，那正是当时判"状态层面幂等"的依据。
    // 这条仍是**会红的断言**：谁把 @close 那条腿加回来，handlerCalls 回到 2、本条转红。
    const drawerOpen = ref(true)
    let handlerCalls = 0
    let stateChanges = 0
    watch(drawerOpen, () => {
      stateChanges += 1
    })
    const Host = defineComponent({
      setup: () => () =>
        h(NmPublishDrawer, {
          modelValue: drawerOpen.value,
          'onUpdate:modelValue': (v: boolean) => {
            handlerCalls += 1
            drawerOpen.value = v
          },
        }),
    })
    const wrapper = mount(Host, { attachTo: document.body })
    trackers.push(wrapper)
    await nextTick()
    expect(panels()).toHaveLength(1)

    clickPanelClose()
    await nextTick()
    await flushPromises()

    expect(handlerCalls).toBe(1)
    expect(stateChanges).toBe(1)
    expect(drawerOpen.value).toBe(false)
    expect(panels()).toHaveLength(0)
  })
})
