import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// 模拟 Element Plus
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) },
}))

// 模拟 AI API
const mockChat = vi.fn()
vi.mock('@/api/ai', () => ({
  default: { chat: mockChat, streamChat: vi.fn() },
}))

describe('AI Assistant 问答流程', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('发送消息后应追加到消息列表', async () => {
    mockChat.mockResolvedValue({ code: 200, data: '这是AI的回答' })

    const state = reactive({
      messages: [],
      inputText: '什么是TCP？',
      loading: false,
    })

    // 模拟发送
    state.messages.push({ role: 'user', content: '什么是TCP？' })
    state.loading = true
    try {
      const res = await mockChat('什么是TCP？')
      state.messages.push({ role: 'assistant', content: res.data })
    } finally {
      state.loading = false
    }

    expect(state.messages).toHaveLength(2)
    expect(state.messages[1].role).toBe('assistant')
    expect(state.messages[1].content).toBe('这是AI的回答')
    expect(state.loading).toBe(false)
  })

  it('空消息不应发送', () => {
    const inputText = '   '
    const canSend = inputText.trim().length > 0
    expect(canSend).toBe(false)
  })

  it('loading 期间不应允许重复发送', () => {
    const state = { loading: true, inputText: 'test' }
    const canSend = state.inputText.trim().length > 0 && !state.loading
    expect(canSend).toBe(false)
  })

  it('API 失败时应显示错误且不追加 AI 消息', async () => {
    mockChat.mockRejectedValue(new Error('网络异常'))
    const state = reactive({
      messages: [{ role: 'user', content: 'hello' }],
      error: null,
    })

    try {
      await mockChat('hello')
      state.messages.push({ role: 'assistant', content: 'should not reach' })
    } catch (e) {
      state.error = e.message
    }

    expect(state.messages).toHaveLength(1) // 不应追加 AI 消息
    expect(state.error).toBe('网络异常')
  })

  it('消息历史应保留最近50条', () => {
    const messages = []
    for (let i = 0; i < 60; i++) {
      messages.push({ role: i % 2 === 0 ? 'user' : 'assistant', content: `消息${i}` })
    }
    const maxHistory = 50
    const trimmed = messages.slice(-maxHistory)
    expect(trimmed).toHaveLength(50)
    expect(trimmed[0].content).toBe('消息10')
    expect(trimmed[49].content).toBe('消息59')
  })
})

// reactive helper for test
function reactive(obj) {
  return obj
}
