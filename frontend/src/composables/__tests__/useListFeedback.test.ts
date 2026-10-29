import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useListFeedback（D2-1 列表失败反馈统一原语）单元测试
 *
 * 锁定 FileUploadManage 样板的三态语义：
 * - 成功：清错误态、返回数据、loading 归位；
 * - 失败（含 fetcher 内部主动 throw 的响应格式校验）：置 listError + 弹 toast、
 *   返回 null、绝不向上抛；
 * - 双态空文案：有错误→「无法确认数据…」，无错误→「暂无数据」，让用户可辨识失败与空数据。
 */

const mocks = vi.hoisted(() => ({
  error: vi.fn(),
}))

// M-12a 起本原语不再静态引 Element Plus：默认提示走 HTTP 层宿主（apiHost），
// v2 侧改为注入 notify。断言钉「提示有没有按同一文案发出」，不钉具体宿主。
import { setApiHost } from '@/api/apiHost'
setApiHost({
  message: { error: mocks.error, success: vi.fn(), warning: vi.fn(), info: vi.fn() },
  push: vi.fn(),
  currentPath: () => '/',
})

import { useListFeedback } from '@/composables/useListFeedback'

function withSetup(options = {}) {
  let state!: ReturnType<typeof useListFeedback>
  mount(
    defineComponent({
      setup() {
        state = useListFeedback(options)
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useListFeedback', () => {
  beforeEach(() => {
    mocks.error.mockClear()
  })

  it('成功路径：返回数据、无错误态、loading 归位', async () => {
    const { loading, listError, run } = withSetup()
    const data = [{ id: 1 }, { id: 2 }]
    const result = await run(async () => data)
    expect(result).toBe(data)
    expect(listError.value).toBe('')
    expect(loading.value).toBe(false)
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('失败路径：fetcher reject 时置 listError + 弹 toast + 返回 null，不向上抛', async () => {
    const { loading, listError, run } = withSetup({
      failMessage: '课程加载失败，请稍后重试',
      toastMessage: '课程加载失败',
    })
    const result = await run(async () => {
      throw new Error('network down')
    })
    expect(result).toBeNull()
    expect(listError.value).toBe('课程加载失败，请稍后重试')
    expect(mocks.error).toHaveBeenCalledWith('课程加载失败')
    expect(loading.value).toBe(false)
  })

  it('fetcher 内部主动 throw（响应格式无效）也进入失败态', async () => {
    const { listError, run } = withSetup({ failMessage: '数据加载失败，请稍后重试' })
    const result = await run(async () => {
      const list = null
      if (!list) throw new Error('响应格式无效')
      return list
    })
    expect(result).toBeNull()
    expect(listError.value).toBe('数据加载失败，请稍后重试')
    expect(mocks.error).toHaveBeenCalledTimes(1)
  })

  it('loading 在请求期间为 true，结束后归位', async () => {
    const { loading, run } = withSetup()
    let resolveFn!: (v: number) => void
    const pending = new Promise<number>((res) => {
      resolveFn = res
    })
    const p = run(async () => pending)
    expect(loading.value).toBe(true)
    resolveFn(42)
    await p
    expect(loading.value).toBe(false)
  })

  it('双态空文案：无错误→emptyText，有错误→errorEmptyText', async () => {
    const { emptyDescription, run } = withSetup({
      emptyText: '暂无课程',
      errorEmptyText: '无法确认课程数据，请查看上方提示后重试',
    })
    expect(emptyDescription()).toBe('暂无课程')
    await run(async () => {
      throw new Error('boom')
    })
    expect(emptyDescription()).toBe('无法确认课程数据，请查看上方提示后重试')
  })

  it('默认文案：未传 options 时使用通用默认值', async () => {
    const { emptyDescription, run } = withSetup()
    expect(emptyDescription()).toBe('暂无数据')
    await run(async () => {
      throw new Error('boom')
    })
    expect(emptyDescription()).toBe('无法确认数据，请查看上方提示后重试')
    expect(mocks.error).toHaveBeenCalledWith('数据加载失败，请稍后重试')
  })

  it('toastMessage 缺省时回退为 failMessage', async () => {
    const { run } = withSetup({
      failMessage: '上传会话加载失败，请稍后刷新；已保留上次显示的数据。',
    })
    await run(async () => {
      throw new Error('boom')
    })
    expect(mocks.error).toHaveBeenCalledWith('上传会话加载失败，请稍后刷新；已保留上次显示的数据。')
  })

  it('注入 notify 时用注入的通道（v2 自有 toast），不再走 HTTP 层宿主', async () => {
    const notify = vi.fn()
    const { run } = withSetup({ notify, toastMessage: '会话加载失败' })
    await run(async () => {
      throw new Error('boom')
    })
    expect(notify).toHaveBeenCalledWith('会话加载失败')
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('reset() 清空错误态与 loading', async () => {
    const { listError, loading, run, reset } = withSetup()
    await run(async () => {
      throw new Error('boom')
    })
    expect(listError.value).not.toBe('')
    reset()
    expect(listError.value).toBe('')
    expect(loading.value).toBe(false)
  })

  it('连续调用：后一次成功会清除前一次的错误态', async () => {
    const { listError, run } = withSetup()
    await run(async () => {
      throw new Error('first fail')
    })
    expect(listError.value).not.toBe('')
    await run(async () => [1, 2, 3])
    expect(listError.value).toBe('')
    expect(mocks.error).toHaveBeenCalledTimes(1)
  })
})
