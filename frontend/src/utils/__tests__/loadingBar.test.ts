import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * src/utils/loadingBar.ts 行为测试。
 * requestCount / loadingTimer 是模块级状态，因此每个用例重新导入模块。
 */

let showLoading: () => void
let hideLoading: (force?: boolean) => void

function barEl(): HTMLElement {
  return document.getElementById('global-loading-bar') as HTMLElement
}

beforeEach(async () => {
  vi.useFakeTimers()
  document.body.innerHTML = '<div id="global-loading-bar" style="display:none;width:0%"></div>'
  vi.resetModules()
  const mod = await import('@/utils/loadingBar')
  showLoading = mod.showLoading
  hideLoading = mod.hideLoading
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('showLoading 进度条启动', () => {
  it('显示进度条并从 0% 强制回流后动画到 85%', () => {
    showLoading()
    const bar = barEl()
    expect(bar.style.display).toBe('block')
    expect(bar.style.width).toBe('85%')
    expect(bar.style.transition).toBe('width 8s cubic-bezier(0.1, 0.05, 0, 1)')
  })

  it('挂载 30s 安全兜底定时器，超时后自动隐藏', () => {
    showLoading()
    expect(vi.getTimerCount()).toBe(1)

    vi.advanceTimersByTime(29999)
    expect(barEl().style.width).toBe('85%')

    vi.advanceTimersByTime(1)
    expect(barEl().style.width).toBe('100%')
    vi.advanceTimersByTime(350)
    expect(barEl().style.display).toBe('none')
    expect(barEl().style.width).toBe('0%')
  })

  it('重复 showLoading 会清除上一个兜底定时器，只保留最新的一个', () => {
    showLoading()
    vi.advanceTimersByTime(20000)
    showLoading()
    expect(vi.getTimerCount()).toBe(1)

    // 旧定时器若没被 clearTimeout，累计到 30s（即再走 10s）就该被强制隐藏
    vi.advanceTimersByTime(10000)
    expect(barEl().style.display).toBe('block')
    expect(barEl().style.width).toBe('85%')

    // 兜底窗口从第二次 showLoading 起重新计时
    vi.advanceTimersByTime(20000)
    expect(barEl().style.width).toBe('100%')
  })
})

describe('hideLoading 计数与关闭', () => {
  it('计数模式下需与 showLoading 次数配对才真正隐藏', () => {
    const bar = barEl()
    showLoading()
    showLoading()

    hideLoading()
    expect(bar.style.width).toBe('85%')
    expect(bar.style.display).toBe('block')

    hideLoading()
    expect(bar.style.width).toBe('100%')
    expect(bar.style.transition).toBe('width 0.3s ease-out')
    // 350ms 后收尾动画结束才真正隐藏
    vi.advanceTimersByTime(349)
    expect(bar.style.display).toBe('block')
    vi.advanceTimersByTime(1)
    expect(bar.style.display).toBe('none')
    expect(bar.style.width).toBe('0%')
  })

  it('force=true 直接清零计数并立即结束进度条', () => {
    showLoading()
    showLoading()
    hideLoading(true)

    expect(barEl().style.width).toBe('100%')
    vi.advanceTimersByTime(350)
    expect(barEl().style.display).toBe('none')

    // 计数已被清零：下一次请求只需一次 hideLoading 即可结束
    showLoading()
    hideLoading()
    expect(barEl().style.width).toBe('100%')
  })

  it('计数不会跌破 0：多余的 hideLoading 之后仍需完整配对才隐藏', () => {
    hideLoading()
    hideLoading()

    showLoading()
    showLoading()
    expect(barEl().style.width).toBe('85%')

    // 若计数被减成负数，这里会误判为「已归零」而提前隐藏
    hideLoading()
    expect(barEl().style.display).toBe('block')
    expect(barEl().style.width).toBe('85%')

    hideLoading()
    expect(barEl().style.width).toBe('100%')
  })

  it('hideLoading 会清除兜底定时器，避免超时后再被强制隐藏一次', () => {
    showLoading()
    hideLoading(true)
    // 兜底定时器已清除，只剩 350ms 的收尾动画定时器
    expect(vi.getTimerCount()).toBe(1)
    vi.advanceTimersByTime(30000)
    expect(barEl().style.display).toBe('none')
    expect(barEl().style.width).toBe('0%')
  })

  it('隐藏收尾动画结束后再次 showLoading 会重新从 0% 起步', () => {
    showLoading()
    hideLoading(true)
    vi.advanceTimersByTime(350)
    expect(barEl().style.display).toBe('none')

    showLoading()
    expect(barEl().style.display).toBe('block')
    expect(barEl().style.width).toBe('85%')
  })
})

describe('进度条 DOM 缺失的降级路径', () => {
  it('无 #global-loading-bar 节点时 show/hide 均不抛错，计数依旧正确', () => {
    document.body.innerHTML = ''
    expect(() => showLoading()).not.toThrow()
    expect(() => hideLoading()).not.toThrow()
    expect(() => hideLoading(true)).not.toThrow()

    // 计数已被上面的 force hide 归零，兜底定时器随之清除
    expect(vi.getTimerCount()).toBe(0)

    showLoading()
    expect(() => vi.advanceTimersByTime(30000)).not.toThrow()
  })
})
