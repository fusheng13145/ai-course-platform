import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import Drawer from '../Drawer.vue'
import { overlayCount } from '@/composables/useOverlayScrollLock'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Task 10A：Modal 的滚动锁接入 + **跨组件一把锁**（题面验收 1、2、4 的组件面）。
 * ==============================================================
 * 为什么单独开这个文件而不是塞进 `Modal.vmodel.test.ts`：那些文件在 Task 9 的许可之外、
 * 且钉的是可见性与 `$el` 身份；本文件断言的全是 `document.body` 上的锁状态，
 * 夹具（dataset 清零 + 统一卸载）自成一套，用 helpers/trackMount（族内规矩：不复制夹具）。
 * `Modal.a11y.test.ts` 在本目录不存在，按 brief 的括注走这个名字（滚动锁专属，不掺 ARIA——
 * `role`/`aria-*`/焦点陷阱是 10B，本棒碰一个都算越界）。
 *
 * 与 Drawer.test.ts 的「滚动锁与焦点」六条的分工：那六条钉 Drawer 单原语的形状（抽取前后一字未改，
 * 是本次抽取的回归网），本文件钉**两个原语共用同一个计数器**这一条只有跨组件才成立的事实。
 * 计数与 body dataset 是跨用例状态，所以 beforeEach/afterEach 都清零（与 Drawer.test.ts 同一处置）。
 */

function resetLockState(): void {
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
  document.body.style.overflow = ''
}

beforeEach(() => resetLockState())
afterEach(() => {
  unmountTracked()
  // Teleport 落点在 body：不赌 unmount 连带摘掉传送节点，直接清场（Drawer.test.ts 同处置）。
  document.body
    .querySelectorAll('.overlay, .modal, .drawer-overlay, .drawer')
    .forEach((n) => n.remove())
  resetLockState()
})

/** 挂载即上锁的路径靠 immediate 回调同步完成；setProps 那条要走 pre-flush watcher */
async function settle(): Promise<void> {
  await nextTick()
}

function mountModal(props: Record<string, unknown> = {}): VueWrapper {
  return track(mount(Modal, { props, attachTo: document.body }))
}

function mountDrawer(props: Record<string, unknown> = {}): VueWrapper {
  return track(mount(Drawer, { props: { modelValue: true, ...props }, attachTo: document.body }))
}

describe('Modal — 滚动锁（引用计数）', () => {
  it('打开锁滚动、计 1；关闭还原「上锁前」的原值而不是写空', async () => {
    document.body.style.overflow = 'auto' // 页面自己写的、不属于这套计数的值
    const w = mountModal({ modelValue: true })
    await settle()
    expect(document.body.style.overflow).toBe('hidden')
    expect(overlayCount()).toBe(1)
    expect(document.body.dataset.overlayCount).toBe('1')
    expect(document.body.dataset.overlayOverflowPrev).toBe('auto')

    await w.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('auto')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
  })
  // 跑红配方：composable 的归零分支改成无条件写 ''（M5，已在 useOverlayScrollLock.test.ts 实跑）。

  it('不传 modelValue（13 个存量调用点的 v-if 形状）挂载即上锁', async () => {
    // open = modelValue !== false：undefined 也是「开」，那正是那些调用点的可见性形状，
    // 所以挂载即上锁是有意的（题面验收 1 的"打开"含这条路径）。
    const w = mountModal()
    await settle()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')
    w.unmount()
    expect(overlayCount()).toBe(0)
  })
  // 跑红配方：把接线那行 `useOverlayScrollLock(open)` 摘掉（本文件 M7 的兄弟形状，
  // 已在子步骤 2 对 Drawer 实跑过同名变异：`expected '' to be 'hidden'`）。

  it('Modal 与 Drawer 同时开着 ⇒ 计数 2；关一个仍 hidden 且 1；关第二个才还原', async () => {
    // 这一条就是"两处各自计数"的靶子：Modal 与 Drawer 各自一套计数器时，
    // overlayCount()（读共享键）在两个都开着时只会是 1。变异 M7 的实跑读数记在台账
    // `P0b-SCROLLLOCK-01` 的"有效性"栏（报告文件不入 git，按本表证据纪律不算依据）。
    document.body.style.overflow = 'scroll'
    const modal = mountModal({ modelValue: true })
    await settle()
    const drawer = mountDrawer()
    await settle()
    expect(overlayCount()).toBe(2)
    expect(document.body.style.overflow).toBe('hidden')

    await modal.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    await drawer.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('scroll')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
  })

  it('反过来的嵌套顺序也一样：抽屉在外、弹窗在内时 prev 只记一次', async () => {
    document.body.style.overflow = 'auto'
    const drawer = mountDrawer()
    await settle()
    const modal = mountModal({ modelValue: true })
    await settle()
    expect(overlayCount()).toBe(2)
    // prev 由真正完成 0→1 的那次（抽屉挂载）写入，弹窗那次不覆盖它（base≠0）。
    expect(document.body.dataset.overlayOverflowPrev).toBe('auto')

    await modal.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    await drawer.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('auto')
  })
  // 跑红配方：把 composable 里 `if (on && base === 0)` 的原值守卫改成 `if (on)`（每次上锁都记），
  // 内层那次就会把 prev 覆盖成 'hidden' ⇒ 最后还原出 'hidden'，本条报 `expected 'hidden' to be 'auto'`。

  it('Modal 开着时被卸载 ⇒ 计数归还（v-if 与路由切换的真实形状）', async () => {
    const w = mountModal({ modelValue: true })
    await settle()
    expect(overlayCount()).toBe(1)
    w.unmount()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('')
    expect(document.body.dataset.overlayOverflowPrev).toBeUndefined()
  })

  it('卸载内层抽屉不得解开还开着的 Modal', async () => {
    const modal = mountModal({ modelValue: true })
    await settle()
    const drawer = mountDrawer()
    await settle()
    expect(overlayCount()).toBe(2)

    drawer.unmount()
    expect(overlayCount()).toBe(1)
    expect(document.body.style.overflow).toBe('hidden')

    await modal.setProps({ modelValue: false })
    await settle()
    expect(overlayCount()).toBe(0)
    expect(document.body.style.overflow).toBe('')
  })
  // 跑红配方：给任一原语换回布尔锁（卸载时无条件写 ''）⇒ `expected '' to be 'hidden'`，
  // 症状就是"内层一关、外层还开着却能滚背景"。
})
