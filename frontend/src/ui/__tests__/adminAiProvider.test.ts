import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「AI 供应商」页（2026-11-07 P1 整改批）接真判据。
 *
 * /admin/ai/* 三端点（current/provider/test）此前在前端零消费——唯一封装
 * api/aiSupplier.ts 是全仓零引用的死模块（已随 DR-110 删除）。本页补齐
 * 「管理端热切换」的前端闭环，测试钉四件事：
 *  1) 挂载即拉当前供应商（GET /admin/ai/current）；
 *  2) 切换按钮组按当前供应商高亮 + aria-pressed；
 *  3) 点击切换调用 POST {provider} 且成功后刷新读数并 toast；
 *  4) 探测把后端 AdminAiTestVO 的 ok/message 如实透出（失败不伪装成功）。
 */
const adminApi = vi.hoisted(() => ({
  getAdminAiCurrent: vi.fn(),
  switchAiProvider: vi.fn(),
  testAiProvider: vi.fn(),
}))
vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import AdminAiBody from '../views/specials/AdminAiBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

function current(overrides: Record<string, unknown> = {}) {
  return { provider: 'deepseek', llmName: 'router(deepseek)', configured: true, ...overrides }
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.getAdminAiCurrent.mockResolvedValue(envelope(current()))
})

describe('管理端 AI 供应商：热切换闭环', () => {
  it('挂载即请求当前供应商并渲染读数与配置状态', async () => {
    const wrapper = mount(AdminAiBody)
    await settle()
    expect(adminApi.getAdminAiCurrent).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('deepseek')
    expect(wrapper.text()).toContain('router(deepseek)')
    expect(wrapper.text()).toContain('已配置')
  })

  it('四个供应商按钮按当前供应商高亮（aria-pressed）', async () => {
    const wrapper = mount(AdminAiBody)
    await settle()
    const buttons = wrapper.findAll('.provider-btn')
    expect(buttons).toHaveLength(4)
    expect(buttons[0].attributes('aria-pressed')).toBe('true')
    expect(buttons[1].attributes('aria-pressed')).toBe('false')
  })

  it('点击切换调用 POST {provider} 并更新高亮与 toast', async () => {
    adminApi.switchAiProvider.mockResolvedValue(envelope({ provider: 'qwen', llmName: 'router(qwen)' }))
    const wrapper = mount(AdminAiBody)
    await settle()
    await wrapper.findAll('.provider-btn')[2].trigger('click')
    await settle()
    expect(adminApi.switchAiProvider).toHaveBeenCalledWith('qwen')
    expect(adminApi.getAdminAiCurrent).toHaveBeenCalledTimes(1) // 切换用返回值就地更新，不再多拉一次
    expect(wrapper.findAll('.provider-btn')[2].attributes('aria-pressed')).toBe('true')
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('qwen'), 'ok')
  })

  it('切换失败透出后端 message 且不改变当前高亮', async () => {
    adminApi.switchAiProvider.mockRejectedValue({
      response: { data: { message: 'provider 不能为空' } },
    })
    const wrapper = mount(AdminAiBody)
    await settle()
    await wrapper.findAll('.provider-btn')[1].trigger('click')
    await settle()
    expect(toast).toHaveBeenCalledWith('provider 不能为空', 'danger')
    expect(wrapper.findAll('.provider-btn')[0].attributes('aria-pressed')).toBe('true')
  })

  it('探测结果如实透出 ok/message（失败不伪装成功）', async () => {
    adminApi.testAiProvider.mockResolvedValue(
      envelope({ ok: false, provider: 'deepseek', configured: false, message: '未配置 API Key 或服务端点' }),
    )
    const wrapper = mount(AdminAiBody)
    await settle()
    await wrapper.find('button.btn-primary').trigger('click')
    await settle()
    expect(adminApi.testAiProvider).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('探测失败')
    expect(wrapper.text()).toContain('未配置 API Key 或服务端点')
  })
})
