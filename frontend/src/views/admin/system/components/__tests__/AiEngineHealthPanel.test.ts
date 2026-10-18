/* eslint-disable vue/one-component-per-file -- 组件测试桩：同一测试文件内定义多个 el-* 轻量 stub */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'

/**
 * AiEngineHealthPanel 组件测试
 * <p>钉：① 成功时渲染五项（DB/Redis/知识图谱/RAG/微调）与状态标签类型；
 * ② 加载失败时给出持久 alert 且各项回落 "--"，不得表现为"全部正常"；
 * ③ DISABLED 用 info 标签（未启用≠故障）而非 danger；④ 后端 pythonServicesError 原样透出。</p>
 */

const mocks = vi.hoisted(() => ({
  getSystemHealth: vi.fn(),
}))

vi.mock('@/api/system', () => ({
  default: { getSystemHealth: mocks.getSystemHealth },
}))

vi.mock('@element-plus/icons-vue', () => ({
  Monitor: defineComponent({ name: 'Monitor', render: () => null }),
  Refresh: defineComponent({ name: 'Refresh', render: () => null }),
}))

import AiEngineHealthPanel from '../AiEngineHealthPanel.vue'

const TagStub = defineComponent({
  name: 'ElTag',
  props: { type: { type: String, default: '' } },
  template: '<span class="stub-tag" :data-type="type"><slot /></span>',
})
const AlertStub = defineComponent({
  name: 'ElAlert',
  props: { title: { type: String, default: '' }, type: { type: String, default: '' } },
  template: '<div class="stub-alert" :data-type="type">{{ title }}</div>',
})
const ButtonStub = defineComponent({
  name: 'ElButton',
  emits: ['click'],
  template: '<button class="stub-btn" @click="$emit(\'click\', $event)"><slot /></button>',
})

function mountPanel() {
  return mount(AiEngineHealthPanel, {
    global: {
      stubs: {
        'el-icon': { template: '<i class="stub-icon"><slot /></i>' },
        'el-button': ButtonStub,
        'el-tag': TagStub,
        'el-alert': AlertStub,
      },
      directives: { loading: {} },
    },
  })
}

const HEALTH = {
  status: 'UP',
  database: 'CONNECTED',
  redis: 'CONNECTED',
  pythonServices: {
    'knowledge-graph': { enabled: false, status: 'DISABLED' },
    rag: { enabled: true, status: 'UP' },
    'fine-tune': { enabled: true, status: 'DOWN', error: 'ConnectException' },
  },
}

describe('AiEngineHealthPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功加载：渲染五项，异常项 danger、DISABLED 用 info、正常项 success', async () => {
    mocks.getSystemHealth.mockResolvedValue({ data: HEALTH })
    const wrapper = mountPanel()
    await flushPromises()

    const tags = wrapper.findAll('.stub-tag')
    expect(tags).toHaveLength(5)
    const text = tags.map((t) => t.text())
    expect(text).toEqual(['CONNECTED', 'CONNECTED', 'DISABLED', 'UP', 'DOWN'])
    const types = tags.map((t) => t.attributes('data-type'))
    expect(types).toEqual(['success', 'success', 'info', 'success', 'danger'])
    // 失败原因作为明细渲染
    expect(wrapper.text()).toContain('ConnectException')
    // 成功时不应出现失败告警
    expect(wrapper.find('.stub-alert').exists()).toBe(false)
  })

  it('加载失败：给出持久 alert 且各项回落 "--"，不得表现为正常', async () => {
    mocks.getSystemHealth.mockRejectedValue(new Error('500'))
    const wrapper = mountPanel()
    await flushPromises()

    const tags = wrapper.findAll('.stub-tag')
    expect(tags).toHaveLength(5)
    expect(tags.map((t) => t.text())).toEqual(['--', '--', '--', '--', '--'])
    expect(tags.every((t) => t.attributes('data-type') === 'danger')).toBe(true)
    const alert = wrapper.find('.stub-alert')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('系统健康状态加载失败')
  })

  it('后端 pythonServicesError 原样透出为告警', async () => {
    mocks.getSystemHealth.mockResolvedValue({
      data: {
        status: 'UP',
        database: 'CONNECTED',
        redis: 'CONNECTED',
        pythonServices: null,
        pythonServicesError: 'Python 微服务健康检查失败，请查看后端日志',
      },
    })
    const wrapper = mountPanel()
    await flushPromises()

    const alerts = wrapper.findAll('.stub-alert')
    expect(alerts).toHaveLength(1)
    expect(alerts[0].text()).toBe('Python 微服务健康检查失败，请查看后端日志')
  })

  it('点击刷新按钮重新拉取健康状态', async () => {
    mocks.getSystemHealth.mockResolvedValue({ data: HEALTH })
    const wrapper = mountPanel()
    await flushPromises()
    expect(mocks.getSystemHealth).toHaveBeenCalledTimes(1)

    await wrapper.find('.stub-btn').trigger('click')
    await flushPromises()

    expect(mocks.getSystemHealth).toHaveBeenCalledTimes(2)
  })
})
