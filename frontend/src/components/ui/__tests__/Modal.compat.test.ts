import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

// 12 个调用点的实际用法抽成三类：尺寸档、@close、插槽有无。
// 这一组测试是 Phase 4 每批迁移前的「存量不破」门禁：加能力之前先把现状钉死，
// 后续 Task 7-10 只在本文件追加断言、不另开文件。一旦它们变红，说明改的是
// 不兼容而非纯增强——红的是信号，不是待修的 bug。
function factory(
  props: Record<string, unknown> = {},
  slots: Record<string, string> = {},
  attrs: Record<string, string> = {},
) {
  return mount(Modal, {
    props,
    attrs,
    slots: { title: '标题', ...slots },
    attachTo: document.body,
  })
}

describe('Modal 现状兼容钉', () => {
  it('默认宽度 640px 档（无修饰 prop）', () => {
    // 用 get 而非 find：find 落空时 classes() 返回 []，三条 not.toContain 会空转通过。
    const w = factory().get('.modal')
    expect(w.classes()).not.toContain('wide')
    expect(w.classes()).not.toContain('narrow')
    expect(w.classes()).not.toContain('xwide')
  })

  it.each(['wide', 'xwide', 'narrow'])('%s 走 $attrs 布尔通道', async (size) => {
    // 用 attrs 而非 props：现状 Modal.vue 是从 $attrs 读尺寸档（Modal.vue:22-26），
    // 组件并未声明这些 props；用 props 传未声明键在 VTU 下的落点与 attrs 不同。
    const wrapper = factory({}, {}, { [size]: '' })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.modal').classes()).toContain(size)
  })

  it('点击关闭按钮 emit close', async () => {
    const wrapper = factory()
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('Esc emit close', async () => {
    const wrapper = factory()
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('按下遮罩本体 emit close；按下内容区不 emit', async () => {
    // jsdom 下 trigger('mousedown') 令 e.target === 该元素本体，故 .self 守卫在遮罩上成立
    const content = factory()
    await content.find('.modal').trigger('mousedown')
    expect(content.emitted('close')).toBeUndefined()
    content.unmount()

    const overlay = factory()
    await overlay.find('.overlay').trigger('mousedown')
    expect(overlay.emitted('close')).toHaveLength(1)
    overlay.unmount()
  })

  it('title 插槽渲染、sub 无则不渲染、footer 无则不渲染', () => {
    const wrapper = factory()
    expect(wrapper.find('.modal-title').text()).toBe('标题')
    expect(wrapper.find('.modal-sub').exists()).toBe(false)
    expect(wrapper.find('.modal-foot').exists()).toBe(false)
  })

  it('foot 与 sub 提供时渲染', () => {
    const wrapper = factory({}, { sub: '副标', footer: '<button>确定</button>' })
    expect(wrapper.find('.modal-sub').text()).toBe('副标')
    expect(wrapper.find('.modal-foot button').text()).toBe('确定')
  })

  it('卸载必须摘掉挂载期注册的 document keydown 监听（后续 Esc 不能打到僵尸实例）', async () => {
    // 观察点必须落在监听器注册表，而不是 emitted 计数。实测（见 task-6-report）：
    // VTU 在 unmount() 时清空该实例事件史，父组件的 @close 也收不到已卸载实例的 emit，
    // 所以「卸载后 dispatch Esc，断言 emitted close 长度不增长」这种写法无论
    // removeEventListener 有没有被调用都会绿——它钉不住任何东西，而 12 个调用点全靠
    // v-if 反复开关实例，残留 handler 是真实的内存/幽灵回调来源。
    interface DocListeners {
      addEventListener: (type: string, listener: EventListener, options?: unknown) => void
      removeEventListener: (type: string, listener: EventListener, options?: unknown) => void
    }
    const doc = document as unknown as DocListeners
    const rawAdd = doc.addEventListener.bind(document)
    const rawRemove = doc.removeEventListener.bind(document)
    const added: EventListener[] = []
    const removed: EventListener[] = []

    doc.addEventListener = (type, listener, options) => {
      if (type === 'keydown') added.push(listener)
      rawAdd(type, listener, options)
    }
    doc.removeEventListener = (type, listener, options) => {
      if (type === 'keydown') removed.push(listener)
      rawRemove(type, listener, options)
    }

    try {
      const wrapper = factory()
      await wrapper.vm.$nextTick()
      // 挂载期确实往 document 挂了 keydown（>0 而非 ===1：Phase 0b 加焦点管理时
      // 允许再挂别的 keydown，只要卸载时一起摘干净就不算破兼容）
      expect(added.length).toBeGreaterThan(0)
      wrapper.unmount()
      const leaked = added.filter((fn) => !removed.includes(fn))
      expect(leaked).toEqual([])
    } finally {
      doc.addEventListener = rawAdd
      doc.removeEventListener = rawRemove
    }
  })
})
