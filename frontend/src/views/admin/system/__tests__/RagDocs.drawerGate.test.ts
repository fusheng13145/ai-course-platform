import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * I-4：ui/Drawer 调用点行为钉（RagDocs 那一头）
 * ============================================
 * `RagDocs.vue:90` 是 `<Drawer v-if="showUpload" @close="showUpload = false">`：
 * **不传 model-value、靠外层 v-if 开关**的那一型（Phase 4 要收的 4 个 el-drawer 也全是这型）。
 * Task 11 给 modelValue 定的三态里「不传」必须继续等于「可见」，否则这条调用点一打开就永远打不开；
 * 而 Drawer 的 close() 契约（Minor-3 选的「close 恒发、update:modelValue 只在受控时发」）
 * 恰好由这一头验证：这里**只有 close 腿有人听**，把它改成只发 update:modelValue 就是断链。
 *
 * 该页此前没有任何测试文件（评审 I-4 点名的正是这个缺口），本文件是它的第一条。
 */

const mocks = vi.hoisted(() => ({
  listDocs: vi.fn(),
  addDoc: vi.fn(),
  delDoc: vi.fn(),
  retrieve: vi.fn(),
  getCourses: vi.fn(),
  toastShow: vi.fn(),
}))

vi.mock('@/api/rag', () => ({
  default: {
    ragListDocuments: mocks.listDocs,
    ragAddDocument: mocks.addDoc,
    ragDeleteDocument: mocks.delDoc,
    ragRetrieve: mocks.retrieve,
  },
}))
vi.mock('@/api/course', () => ({ default: { getAllCourses: mocks.getCourses } }))
vi.mock('@/composables/useToast', () => ({ toast: { show: mocks.toastShow } }))

import RagDocs from '@/views/admin/system/RagDocs.vue'

const panels = () => Array.from(document.body.querySelectorAll('.drawer'))
const overlays = () => Array.from(document.body.querySelectorAll('.drawer-overlay'))

interface PageVm {
  showUpload: boolean
}
const pageVm = (w: { vm: unknown }) => w.vm as unknown as PageVm

const trackers: Array<{ unmount(): void }> = []

function mountPage() {
  const wrapper = mount(RagDocs, { attachTo: document.body })
  trackers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.listDocs.mockResolvedValue({ documents: [] })
  mocks.getCourses.mockResolvedValue([])
})

afterEach(() => {
  while (trackers.length) trackers.pop()?.unmount()
  document.body.querySelectorAll('.drawer-overlay, .drawer').forEach((n) => n.remove())
  document.body.style.overflow = ''
  delete document.body.dataset.overlayCount
  delete document.body.dataset.overlayOverflowPrev
})

describe('RagDocs 调用点 — v-if 式抽屉（不传 modelValue）', () => {
  it('showUpload=false → 没有遮罩也没有面板；点页头「上传文档」才出现', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(overlays()).toHaveLength(0)
    expect(panels()).toHaveLength(0)

    const openBtn = wrapper.findAll('button').find((b) => b.text().trim() === '上传文档')
    expect(openBtn, '找不到页头「上传文档」按钮').toBeTruthy()
    await openBtn!.trigger('click')
    await nextTick()

    expect(overlays()).toHaveLength(1)
    expect(panels()).toHaveLength(1)
    expect(panels()[0].classList.contains('ltr')).toBe(false)
    expect(panels()[0].getAttribute('role')).toBe('dialog')
    // 这一头的三态门必须保持「不传=可见」：modelValue 缺席时 open 走 undefined 分支。
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('面板头部关闭按钮 → @close 把 showUpload 落回 false，面板消失且滚动锁归还', async () => {
    const wrapper = mountPage()
    await flushPromises()
    pageVm(wrapper).showUpload = true
    await nextTick()
    expect(panels()).toHaveLength(1)
    expect(document.body.dataset.overlayCount).toBe('1')

    const btn = document.body.querySelector('.drawer-head .t-icon-btn')
    expect(btn, '找不到抽屉关闭按钮').not.toBeNull()
    btn?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    await nextTick()
    await flushPromises()

    expect(pageVm(wrapper).showUpload).toBe(false)
    expect(panels()).toHaveLength(0)
    // 非受控调用点：close 腿是唯一的关闭通道（update:modelValue 在这里没人听）。
    expect(document.body.dataset.overlayCount).toBe('0')
    expect(document.body.style.overflow).toBe('')
  })

  it('抽屉开着就被卸载（v-if 形态的整页离开）→ 不留下 body 锁与监听', async () => {
    const wrapper = mountPage()
    await flushPromises()
    pageVm(wrapper).showUpload = true
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')

    wrapper.unmount()
    trackers.pop()

    expect(document.body.dataset.overlayCount).toBe('0')
    expect(document.body.style.overflow).toBe('')
    expect(panels()).toHaveLength(0)
  })
})
