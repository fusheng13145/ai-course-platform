import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * src/utils/loadingIndicator.ts 行为测试。
 * 计数、定时器都是模块级状态，因此每个用例用 vi.resetModules() 重新导入模块。
 *
 * jsdom 没有 canvas 2D 上下文，这里显式把它桩成 null：模块会退化为静态 SVG
 * 兜底图标——「图标被换掉 / 被还原」的行为仍可断言，也避免 jsdom 打印
 * "Not implemented" 噪声。逐帧动画本身只有真实浏览器里才有。
 */

let showLoading: () => void
let hideLoading: (force?: boolean) => void
let link: HTMLLinkElement | null

const ORIGINAL = '/favicon.svg'

function setupFavicon(href: string = ORIGINAL): HTMLLinkElement {
  const el = document.createElement('link')
  el.id = 'app-favicon'
  el.rel = 'icon'
  el.type = 'image/svg+xml'
  el.setAttribute('href', href)
  document.head.appendChild(el)
  return el
}

function currentHref(): string | null {
  return link?.getAttribute('href') ?? null
}

function isSpinningIcon(): boolean {
  const href = currentHref()
  return typeof href === 'string' && href.startsWith('data:image/')
}

beforeEach(async () => {
  vi.useFakeTimers()
  document.head.innerHTML = ''
  document.body.innerHTML = ''
  link = setupFavicon()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
    null as unknown as CanvasRenderingContext2D,
  )
  vi.resetModules()
  const mod = await import('@/utils/loadingIndicator')
  showLoading = mod.showLoading
  hideLoading = mod.hideLoading
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('延迟出现与最短停留', () => {
  it('80ms 内完成的加载完全不换图标', async () => {
    showLoading()
    await vi.advanceTimersByTimeAsync(30)
    hideLoading()
    await vi.advanceTimersByTimeAsync(500)
    expect(currentHref()).toBe(ORIGINAL)
  })

  it('超过 80ms 才把标签图标换成转圈', async () => {
    showLoading()
    await vi.advanceTimersByTimeAsync(70)
    expect(currentHref()).toBe(ORIGINAL)
    await vi.advanceTimersByTimeAsync(20)
    expect(isSpinningIcon()).toBe(true)
  })

  it('出现后至少停留 300ms 才还原', async () => {
    showLoading()
    await vi.advanceTimersByTimeAsync(100) // 已显示约 20ms
    hideLoading()
    await vi.advanceTimersByTimeAsync(270) // 距显示 290ms，仍不足 300ms
    expect(isSpinningIcon()).toBe(true)
    await vi.advanceTimersByTimeAsync(30)
    expect(currentHref()).toBe(ORIGINAL)
  })
})

describe('并发计数', () => {
  it('多次 show 只加计数，不会重复启动动画', async () => {
    showLoading()
    showLoading()
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(isSpinningIcon()).toBe(true)

    hideLoading()
    await vi.advanceTimersByTimeAsync(500)
    expect(isSpinningIcon()).toBe(true) // 还剩 2 个未结束

    hideLoading()
    hideLoading()
    await vi.advanceTimersByTimeAsync(500)
    expect(currentHref()).toBe(ORIGINAL)
  })

  it('多余的 hide 不会把计数压成负数', async () => {
    hideLoading()
    hideLoading()
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(isSpinningIcon()).toBe(true)

    hideLoading()
    await vi.advanceTimersByTimeAsync(500)
    expect(currentHref()).toBe(ORIGINAL)
  })

  it('还原的是原始图标地址，不是写死的路径', async () => {
    document.head.innerHTML = ''
    link = setupFavicon('/brand-mark.svg')
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(isSpinningIcon()).toBe(true)

    hideLoading()
    await vi.advanceTimersByTimeAsync(500)
    expect(currentHref()).toBe('/brand-mark.svg')
  })
})

describe('兜底', () => {
  it('force 立即还原，即使计数没有归零', async () => {
    showLoading()
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(isSpinningIcon()).toBe(true)

    hideLoading(true)
    expect(currentHref()).toBe(ORIGINAL)
    await vi.advanceTimersByTimeAsync(500)
    expect(currentHref()).toBe(ORIGINAL)
  })

  it('30s 安全兜底强制还原', async () => {
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(isSpinningIcon()).toBe(true)

    await vi.advanceTimersByTimeAsync(30000)
    expect(currentHref()).toBe(ORIGINAL)
  })

  it('页面里没有 favicon 节点时不抛错', async () => {
    document.head.innerHTML = ''
    link = null
    showLoading()
    await vi.advanceTimersByTimeAsync(100)
    expect(() => hideLoading()).not.toThrow()
    await vi.advanceTimersByTimeAsync(500)
    expect(link).toBeNull()
  })
})
