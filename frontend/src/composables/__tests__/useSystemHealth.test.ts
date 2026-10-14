import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useSystemHealth 单元测试（管理端引擎健康）
 * <p>钉三类契约：① 请求失败必须"失败可见"（loadError 非空 + 各项回落 "--"，不得表现为"全部正常"）；
 * ② DISABLED 是「如实告知开关状态」而非故障，基调须与 DOWN/DISCONNECTED 区分；
 * ③ 健康指示器自身异常时原样透出后端 pythonServicesError，且各项仍显示 "--"。</p>
 */

const mocks = vi.hoisted(() => ({
  getSystemHealth: vi.fn(),
}))

vi.mock('@/api/system', () => ({
  default: { getSystemHealth: mocks.getSystemHealth },
}))

import { useSystemHealth } from '@/composables/useSystemHealth'

/** 组合式函数含 onMounted，须由宿主组件驱动（本用例显式调 load，不依赖自动触发） */
function withSetup() {
  let state!: ReturnType<typeof useSystemHealth>
  mount(
    defineComponent({
      setup() {
        state = useSystemHealth()
        return () => h('div')
      },
    }),
  )
  return state
}

/** 成功载荷：DB/Redis 连通，KG 未启用、RAG 正常、微调 DOWN。 */
const HEALTH = {
  status: 'UP',
  timestamp: '2026-10-14 10:00:00',
  database: 'CONNECTED',
  redis: 'CONNECTED',
  pythonServices: {
    'knowledge-graph': { enabled: false, baseUrl: 'http://localhost:8001', status: 'DISABLED' },
    rag: { enabled: true, baseUrl: 'http://localhost:8002', status: 'UP' },
    'fine-tune': {
      enabled: true,
      baseUrl: 'http://localhost:8003',
      status: 'DOWN',
      error: 'ConnectException',
    },
  },
}

describe('useSystemHealth — 引擎健康读取与失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功加载：解包 data 信封，五项状态与基调正确', async () => {
    mocks.getSystemHealth.mockResolvedValue({ data: HEALTH })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.rows.value.map((r) => r.key)).toEqual([
      'database',
      'redis',
      'knowledge-graph',
      'rag',
      'fine-tune',
    ])
    const byKey = Object.fromEntries(state.rows.value.map((r) => [r.key, r]))
    expect(byKey.database).toMatchObject({ status: 'CONNECTED', tone: 'ok' })
    expect(byKey.redis).toMatchObject({ status: 'CONNECTED', tone: 'ok' })
    // DISABLED 是「未启用」而非故障：基调 info，且不应把整体判为有问题
    expect(byKey['knowledge-graph']).toMatchObject({ status: 'DISABLED', tone: 'info' })
    expect(byKey.rag).toMatchObject({ status: 'UP', tone: 'ok' })
    expect(byKey['fine-tune']).toMatchObject({ status: 'DOWN', tone: 'warn' })
    // 失败原因作为明细透出
    expect(byKey['fine-tune'].detail).toBe('ConnectException')
    expect(state.hasIssue.value).toBe(true)
  })

  it('仅存在 DISABLED（未启用）时不算异常：hasIssue 为 false', async () => {
    mocks.getSystemHealth.mockResolvedValue({
      data: {
        status: 'UP',
        database: 'CONNECTED',
        redis: 'CONNECTED',
        pythonServices: {
          'knowledge-graph': { enabled: false, status: 'DISABLED' },
          rag: { enabled: false, status: 'DISABLED' },
          'fine-tune': { enabled: false, status: 'DISABLED' },
        },
      },
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.rows.value.every((r) => r.tone !== 'warn')).toBe(true)
    expect(state.hasIssue.value).toBe(false)
  })

  it('请求失败：给出持久提示，各项回落 "--" 且不得表现为正常', async () => {
    mocks.getSystemHealth.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.loadError.value).toBe('系统健康状态加载失败，请检查网络后重试')
    expect(state.health.value).toEqual({})
    expect(state.rows.value).toHaveLength(5)
    expect(state.rows.value.every((r) => r.status === '--')).toBe(true)
    expect(state.rows.value.every((r) => r.tone === 'warn')).toBe(true)
    expect(state.loading.value).toBe(false)
  })

  it('失败后再成功：提示必须被清空（每次加载复位，不是一次失败永久残留）', async () => {
    mocks.getSystemHealth.mockRejectedValueOnce(new Error('500'))
    const state = withSetup()
    await state.load()
    expect(state.loadError.value).not.toBe('')

    mocks.getSystemHealth.mockResolvedValue({ data: HEALTH })
    await state.load()

    expect(state.loadError.value).toBe('')
    expect(state.health.value.database).toBe('CONNECTED')
  })

  it('pythonServices 缺失/为 null：三个引擎项显示 "--"，不伪装为正常', async () => {
    mocks.getSystemHealth.mockResolvedValue({
      data: { status: 'UP', database: 'CONNECTED', redis: 'CONNECTED', pythonServices: null },
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    const engines = state.rows.value.filter((r) => r.key !== 'database' && r.key !== 'redis')
    expect(engines.every((r) => r.status === '--')).toBe(true)
    expect(engines.every((r) => r.tone === 'warn')).toBe(true)
  })

  it('健康指示器自身异常：原样透出后端 pythonServicesError', async () => {
    mocks.getSystemHealth.mockResolvedValue({
      data: {
        status: 'UP',
        database: 'CONNECTED',
        redis: 'CONNECTED',
        pythonServices: null,
        pythonServicesError: 'Python 微服务健康检查失败，请查看后端日志',
      },
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.pythonNote.value).toBe('Python 微服务健康检查失败，请查看后端日志')
  })
})
