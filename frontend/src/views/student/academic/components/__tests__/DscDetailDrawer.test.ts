import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DscDetailDrawer from '../DscDetailDrawer.vue'

/**
 * D2-3：详情抽屉内嵌空态统一为 EmptyState（compact 尺寸）。
 */
function mountDrawer(posts: unknown[] = [], postLoading = false) {
  return mount(DscDetailDrawer, {
    props: {
      modelValue: true,
      currentTopic: { id: 1, title: '讨论话题', content: '话题正文内容', status: 'OPEN' },
      posts: posts as never,
      postLoading,
      canDelete: () => false,
    },
    global: {
      stubs: {
        // Drawer 桩：渲染默认插槽，使抽屉内内容可断言（真组件 Teleport 到 body，批 3b 同款等价桩）
        Drawer: {
          props: ['modelValue'],
          template: '<div class="drawer-stub"><slot /></div>',
        },
        'el-icon': true,
        'el-button': { template: '<button class="btn-stub"><slot /></button>' },
        'el-select': { template: '<div class="select-stub" />' },
        'el-option': { template: '<div class="option-stub" />' },
        'el-input': { template: '<input class="input-stub" />' },
        'el-alert': { template: '<div class="alert-stub" />' },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('DscDetailDrawer 详情抽屉空态（D2-3）', () => {
  it('无回复且非加载中：渲染统一 EmptyState（compact + message 语义）', async () => {
    const wrapper = mountDrawer([])
    await flushPromises()
    const empty = wrapper.get('.empty-state')
    expect(empty.classes()).toContain('is-compact')
    expect(empty.get('.e-title').text()).toBe('暂无回复，来抢沙发')
  })

  it('有回复：渲染回复楼层，不显示空态', async () => {
    const wrapper = mountDrawer([
      { id: 10, content: '第一条回复', authorName: '张三', likeCount: 2, liked: false },
    ])
    await flushPromises()
    expect(wrapper.find('.empty-state').exists()).toBe(false)
    expect(wrapper.text()).toContain('第一条回复')
    expect(wrapper.text()).toContain('张三')
  })

  it('加载中：既不显示空态也不显示回复列表', async () => {
    const wrapper = mountDrawer([], true)
    await flushPromises()
    expect(wrapper.find('.empty-state').exists()).toBe(false)
  })
})
