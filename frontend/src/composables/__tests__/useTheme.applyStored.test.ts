import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * `applyStoredTheme()` —— 入口在 `app.mount()` 之前调的那一下（缺陷 D-1）
 * =====================================================================
 * 为什么单开一个文件而不塞进 `useTheme.test.ts`：那个文件测的是**组合式函数的行为**
 * （`cycleTheme` / `setTheme` / `onMounted`），每一条都要 `mount()` 一个 harness 组件；
 * 而这里的被测事实恰好相反——**不挂载任何组件**也必须把主题落到 `<html>` 上。混进去会让
 * "这条用例有没有 mount"变成读代码才能分辨的细节，而它正是 D-1 的全部内容。
 *
 * 口径：JS 侧只有一个可观察量——`document.documentElement.dataset.theme`。断言钉
 * "localStorage 里存的是什么 ⇒ 属性变成什么"，覆盖三值 + `system` 跟随 `matchMedia`
 * 的两支（与 `theme.css` 的 `[data-theme='dark']` 块对得上名字）。
 */

// 与 useTheme.test.ts 同一份 mock：chartTheme 的 emitThemeChange 会派发 CustomEvent 并回调监听者，
// 本文件不关心图表侧反应，隔离掉以免把 jsdom 的事件机制卷进判据。
vi.mock('@/utils/chartTheme', () => ({
  emitThemeChange: vi.fn(),
}))

// jsdom 未实现 matchMedia；形状与 useTheme.test.ts 的工厂保持一致（转 .ts 后 mockReturnValue 需要完整成员）
const mediaQueryList = (matches: boolean) => ({
  matches,
  media: '(prefers-color-scheme: dark)',
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
})
const matchMediaMock = vi.fn(() => mediaQueryList(false))
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: matchMediaMock,
})

const THEME_KEY = 'fz-theme'

/** 每个用例一份干净模块：`theme` 是模块级单例，它在 import 期就读 localStorage */
async function loadApplyStoredTheme() {
  const mod = await import('@/composables/useTheme')
  return mod.applyStoredTheme
}

describe('applyStoredTheme（启动时应用已存主题）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    matchMediaMock.mockReturnValue(mediaQueryList(false))
    // 起点刻意清空而不是设成 light：D-1 的症状就是"属性根本不存在"
    document.documentElement.removeAttribute('data-theme')
    vi.resetModules()
  })

  it('未挂载任何组件也能把已存 light 写上去（改前：属性一直是 null）', async () => {
    localStorage.setItem(THEME_KEY, 'light')
    const applyStoredTheme = await loadApplyStoredTheme()

    // 正读数前置：调用之前属性必须**不存在**——否则这条用例证明不了"是它写的"
    expect(document.documentElement.getAttribute('data-theme')).toBeNull()

    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('已存 dark 时把 dark 写上去（这就是 error-404 暗色基线的那一格）', async () => {
    localStorage.setItem(THEME_KEY, 'dark')
    const applyStoredTheme = await loadApplyStoredTheme()

    expect(document.documentElement.getAttribute('data-theme')).toBeNull()
    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('已存 system 且系统偏好 light ⇒ 解析为 light', async () => {
    localStorage.setItem(THEME_KEY, 'system')
    matchMediaMock.mockReturnValue(mediaQueryList(false))
    const applyStoredTheme = await loadApplyStoredTheme()

    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('已存 system 且系统偏好 dark ⇒ 解析为 dark', async () => {
    localStorage.setItem(THEME_KEY, 'system')
    matchMediaMock.mockReturnValue(mediaQueryList(true))
    const applyStoredTheme = await loadApplyStoredTheme()

    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('无存储偏好时落到模块默认值 dark（与 useTheme 的默认一致，不各写一套）', async () => {
    const applyStoredTheme = await loadApplyStoredTheme()

    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('幂等：连调两次结果一样，且不会把属性写回未设置', async () => {
    localStorage.setItem(THEME_KEY, 'dark')
    const applyStoredTheme = await loadApplyStoredTheme()

    applyStoredTheme()
    applyStoredTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
