import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CdKbBuildPanel（教师课程详情「知识管理」页签内的课程知识库构建入口）— 失败可见契约。
 *
 * <p>入口本身只是按钮 + 结果区，但「结果区」承担关键职责：把构建失败/降级如实、常驻地展示出来，
 * 而不是靠 ElMessage 一闪而过。这里锁定三件事：
 * <ul>
 *   <li>初始无提示（不误报）；</li>
 *   <li>引擎不可用（UNAVAILABLE）→ type=error 常驻提示，文案为后端 error；</li>
 *   <li>引擎未接入（LOCAL）→ type=warning，明确「未真正入库」，不得显示成功态。</li>
 * </ul>
 */

const mocks = vi.hoisted(() => ({
  ragBuildCourseKnowledgeBase: vi.fn(),
}))

vi.mock('@/api/rag', () => ({
  default: { ragBuildCourseKnowledgeBase: mocks.ragBuildCourseKnowledgeBase },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

import CdKbBuildPanel from '@/views/teacher/components/CdKbBuildPanel.vue'

function mountPanel() {
  return mount(CdKbBuildPanel, {
    props: { courseId: 1 },
    global: {
      stubs: {
        'el-button': {
          emits: ['click'],
          template: `<button class="kb-btn" @click="$emit('click')"><slot /></button>`,
        },
        'el-alert': {
          props: ['title', 'type'],
          template: `<div class="kb-alert" :data-type="type">{{ title }}</div>`,
        },
        'el-icon': true,
      },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('CdKbBuildPanel — 构建结果失败可见', () => {
  it('初始不渲染任何提示（不误报）', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('.kb-alert').exists()).toBe(false)
    expect(wrapper.find('.kb-btn').exists()).toBe(true)
  })

  it('引擎不可用：点击构建后渲染 error 常驻提示，文案取自后端 error', async () => {
    // rag 接口经 .then(unwrap) 已解包，桩直接给内层业务数据（勿再套 { data } 信封）
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'UNAVAILABLE',
      total: 2,
      added: 0,
      skipped: 0,
      error: 'RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用',
      code: 503,
    })
    const wrapper = mountPanel()

    await wrapper.get('.kb-btn').trigger('click')
    await flushPromises()

    expect(mocks.ragBuildCourseKnowledgeBase).toHaveBeenCalledWith(1)
    const alert = wrapper.get('.kb-alert')
    expect(alert.attributes('data-type')).toBe('error')
    expect(alert.text()).toContain('服务未接入或不可用')
    // 常驻：再次渲染（无交互）仍在，不会自动消失
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.kb-alert').exists()).toBe(true)
  })

  it('引擎未接入（LOCAL）：渲染 warning 提示，不得呈现为成功', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'LOCAL',
      total: 4,
      added: 0,
      skipped: 0,
      note: 'RAG 引擎未接入：仅枚举课程资料',
    })
    const wrapper = mountPanel()

    await wrapper.get('.kb-btn').trigger('click')
    await flushPromises()

    const alert = wrapper.get('.kb-alert')
    expect(alert.attributes('data-type')).toBe('warning')
    expect(alert.text()).toContain('未接入')
    expect(wrapper.text()).toContain('识别课程资料 4 条')
  })

  it('ENGINE 成功：渲染 success 提示与写入读数', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'ENGINE',
      total: 5,
      added: 3,
      skipped: 2,
      note: '已写入 3 篇、跳过 2 篇（知识库中已存在），并重建课程索引',
    })
    const wrapper = mountPanel()

    await wrapper.get('.kb-btn').trigger('click')
    await flushPromises()

    const alert = wrapper.get('.kb-alert')
    expect(alert.attributes('data-type')).toBe('success')
    expect(alert.text()).toContain('已写入 3 篇')
    expect(wrapper.text()).toContain('已写入 3 条')
    expect(wrapper.text()).toContain('跳过 2 条')
  })
})
