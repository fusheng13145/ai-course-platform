import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「系统设置」接真判据（批次 24）。
 *
 * 这一页此前是「待扩展」空态，而端点一直齐着（`GET /admin/configs`、`PUT /admin/configs/{key}`、
 * `GET /admin/health`）。接真之后最要紧的两条是：
 * ① **密钥类配置不能明文示人**——`ai_api_key` 这类键在列表里必须掩码，
 *    编辑时也不回显真实值（留空＝不修改），否则页面成了泄密面；
 * ② **健康探针与配置列表分路**——`/admin/health` 挂了不该让整页读不出配置。
 */
const adminApi = vi.hoisted(() => ({
  getConfigs: vi.fn(),
  getHealth: vi.fn(),
  updateConfig: vi.fn(),
}))
vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import SystemBody from '../views/specials/SystemBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const CONFIGS = [
  {
    key: 'site_name',
    value: 'AI智能课程平台',
    description: '站点名称',
    type: 'STRING',
    group: 'basic',
  },
  {
    key: 'ai_api_key',
    value: 'sk-live-secret',
    description: 'AI API密钥',
    type: 'STRING',
    group: 'ai',
  },
  {
    key: 'max_upload_size',
    value: '100',
    description: '最大上传文件大小(MB)',
    type: 'NUMBER',
    group: 'system',
  },
  { key: 'ui_compact', value: 'false', description: '紧凑排版', type: 'BOOLEAN', group: 'ui' },
]

// 真栈实测：依赖状态是平铺的，没有再套一层 health
const HEALTH = {
  status: 'UP',
  database: 'CONNECTED',
  redis: 'CONNECTED',
  timestamp: '2026-11-04 22:03:42',
  pythonServices: {
    'knowledge-graph': { enabled: false, status: 'DISABLED', baseUrl: 'http://localhost:8001' },
  },
}

/** 弹层是 Teleport 到 body 的，wrapper 里找不到；且用例失败时要防止残留弹层影响下一例 */
function modalInput(): HTMLInputElement {
  const input = document.querySelector('.modal-panel input') as HTMLInputElement | null
  if (!input) throw new Error('弹层里没有输入框')
  return input
}

/** 直接改 DOM 值并派发 input 事件，让 v-model 收到 */
async function setModalValue(value: string) {
  const input = modalInput()
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

function modalButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll('.modal-panel button')).find(
    (node) => node.textContent?.trim() === text,
  ) as HTMLButtonElement | undefined
}

afterEach(() => {
  // 用例中途失败时 Teleport 的弹层会留在 body 里，下一例点到的可能是上一例的按钮
  document.querySelectorAll('.modal-layer').forEach((node) => node.remove())
})

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.getConfigs.mockResolvedValue(envelope(CONFIGS))
  adminApi.getHealth.mockResolvedValue(envelope(HEALTH))
  adminApi.updateConfig.mockResolvedValue(envelope(null))
})

describe('管理端 · 系统设置', () => {
  it('挂载即拉配置与健康状态；配置行显示键、说明、类型与当前值', async () => {
    const wrapper = mount(SystemBody)
    await flushPromises()

    expect(adminApi.getConfigs).toHaveBeenCalledTimes(1)
    expect(adminApi.getHealth).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('site_name')
    expect(wrapper.text()).toContain('最大上传文件大小(MB)')
    expect(wrapper.text()).toContain('NUMBER')
    expect(wrapper.text()).toContain('AI智能课程平台')
    // 健康卡：数据库/缓存状态与 Python 服务状态
    expect(wrapper.text()).toContain('CONNECTED')
    expect(wrapper.text()).toContain('知识图谱服务')
    expect(wrapper.text()).toContain('DISABLED')
    wrapper.unmount()
  })

  it('密钥类配置掩码显示，编辑时也不回显真实值', async () => {
    const wrapper = mount(SystemBody)
    await flushPromises()

    expect(wrapper.text()).not.toContain('sk-live-secret')
    expect(wrapper.text()).toContain('••••••')

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('ai_api_key'))
    await row!.find('.text-btn').trigger('click')
    await flushPromises()

    expect(modalInput().value, '密钥真实值不应回显').toBe('')
    wrapper.unmount()
  })

  it('改值走 PUT，只带该键的 value 与 group；成功后刷新列表并提示', async () => {
    const wrapper = mount(SystemBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('site_name'))
    await row!.find('.text-btn').trigger('click')
    await flushPromises()

    await setModalValue('新站点名')
    modalButton('保存')!.click()
    await flushPromises()

    expect(adminApi.updateConfig).toHaveBeenCalledWith('site_name', {
      value: '新站点名',
      group: 'basic',
    })
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('site_name'), 'ok')
    expect(adminApi.getConfigs).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('密钥留空＝不修改：不发请求，并说明原因', async () => {
    const wrapper = mount(SystemBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('ai_api_key'))
    await row!.find('.text-btn').trigger('click')
    await flushPromises()
    modalButton('保存')!.click()
    await flushPromises()

    expect(adminApi.updateConfig).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('必须输入新值')
    wrapper.unmount()
  })

  it('保存失败时保留弹层并显示后端原因，不谎报成功', async () => {
    adminApi.updateConfig.mockRejectedValue({ response: { data: { message: '配置项不存在' } } })
    const wrapper = mount(SystemBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('site_name'))
    await row!.find('.text-btn').trigger('click')
    await flushPromises()
    await setModalValue('新站点名')
    modalButton('保存')!.click()
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('配置项不存在', 'danger')
    expect(document.body.textContent).toContain('配置项不存在')
    expect(modalButton('保存'), '失败后弹层不应自动关闭').toBeTruthy()
    wrapper.unmount()
  })

  it('分组筛选与关键词搜索在客户端生效', async () => {
    const wrapper = mount(SystemBody)
    await flushPromises()

    await wrapper.find('select').setValue('ai')
    await flushPromises()
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('ai_api_key')

    await wrapper.find('select').setValue('')
    await wrapper.find('input[type="search"]').setValue('上传')
    await flushPromises()
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('max_upload_size')
    wrapper.unmount()
  })

  it('配置列表失败与健康探针失败互不牵连', async () => {
    adminApi.getConfigs.mockRejectedValue(new Error('500'))
    adminApi.getHealth.mockRejectedValue(new Error('500'))
    const wrapper = mount(SystemBody)
    await flushPromises()

    expect(wrapper.text()).toContain('系统配置加载失败')
    expect(wrapper.text()).toContain('服务状态探针暂不可用')
    wrapper.unmount()

    // 反面：健康挂了但配置正常时，配置表仍要出数
    adminApi.getConfigs.mockResolvedValue(envelope(CONFIGS))
    adminApi.getHealth.mockRejectedValue(new Error('500'))
    const second = mount(SystemBody)
    await flushPromises()
    expect(second.text()).toContain('site_name')
    expect(second.text()).toContain('服务状态探针暂不可用')
    second.unmount()
  })
})
