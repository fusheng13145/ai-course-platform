import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * src/utils/loadingIndicator.ts 行为测试。
 * activeCount / 定时器是模块级状态，因此每个用例重新导入模块。
 */

let showLoading: () => void
let hideLoading: (force?: boolean) => void

function indicatorEl(): HTMLElement {
  return document.getElementById('global-loading-indicator') as HTMLElement
}

function visible(): boolean {
  return indicatorEl().classList.contains('is-visible')
}

beforeEach(async () => {
  vi.useFakeTimers()
  // 夹具与 index.html 的真实结构一致：容器 + 转圈元素
  document.body.innerHTML =
    '<div id="global-loading-indicator"><span class="gli-spinner"></span></div>'
  vi.resetModules()
  const mod = await import('@/utils/loadingIndicator')
  showLoading = mod.showLoading
  hideLoading = mod.hideLoading
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('showLoading 的延迟出现', () => {
  it('80ms 内完成的加载一次都不显示（快速跳转不再有视觉残留）', () => {
    showLoading()
    vi.advanceTimersByTime(50)
    hideLoading()
    vi.advanceTimersByTime(500)

    expect(visible()).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('超过 80ms 仍未结束才出现指示器', () => {
    showLoading()
    expect(visible()).toBe(false)

    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)
    expect(document.querySelectorAll('#global-loading-indicator').length).toBe(1)
  })

  it('重复 showLoading 不叠加多个指示器', () => {
    showLoading()
    showLoading()
    vi.advanceTimersByTime(80)

    expect(visible()).toBe(true)
    expect(document.querySelectorAll('.gli-spinner').length).toBe(1)
  })
})

describe('hideLoading 的计数与最短停留', () => {
  it('并发加载需全部结束才收起', () => {
    showLoading()
    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)

    hideLoading()
    vi.advanceTimersByTime(500)
    expect(visible()).toBe(true)

    hideLoading()
    vi.advanceTimersByTime(500)
    expect(visible()).toBe(false)
  })

  it('已显示后至少停留 300ms，避免一闪而过', () => {
    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)

    vi.advanceTimersByTime(60)
    hideLoading()

    vi.advanceTimersByTime(100)
    expect(visible()).toBe(true)

    vi.advanceTimersByTime(400)
    expect(visible()).toBe(false)
  })

  it('计数不会跌破 0：多余的 hideLoading 不影响后续显示', () => {
    hideLoading()
    hideLoading()

    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)

    hideLoading()
    showLoading()
    vi.advanceTimersByTime(500)
    expect(visible()).toBe(true)
  })

  it('force=true 立即收起并清零计数', () => {
    showLoading()
    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)

    hideLoading(true)
    expect(visible()).toBe(false)

    // 计数已清零，之后一次 show + hide 即可完整走完一轮
    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)
    hideLoading()
    vi.advanceTimersByTime(500)
    expect(visible()).toBe(false)
  })
})

describe('安全兜底与降级路径', () => {
  it('出现满 30s 后强制收起，避免转圈常驻', () => {
    showLoading()
    vi.advanceTimersByTime(80)
    expect(visible()).toBe(true)

    vi.advanceTimersByTime(30000)
    expect(visible()).toBe(false)

    // 兜底已把计数清零，后续 hideLoading 不应再改状态
    expect(() => hideLoading()).not.toThrow()
    expect(visible()).toBe(false)
  })

  it('无指示器节点时不抛错，也不留下待显示定时器', () => {
    document.body.innerHTML = ''

    expect(() => showLoading()).not.toThrow()
    expect(() => hideLoading()).not.toThrow()
    expect(() => hideLoading(true)).not.toThrow()

    vi.advanceTimersByTime(80)
    expect(vi.getTimerCount()).toBe(0)
  })
})
