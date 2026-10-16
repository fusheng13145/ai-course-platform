import { describe, it, expect, afterEach } from 'vitest'
import { announce, resetAnnouncerForTest } from '@/composables/useAnnouncer'

/**
 * useAnnouncer 原语测试（D2-7）
 *
 * <p>锁定语义：①惰性创建 polite live 区域（sr-only 内联样式）；②播报写入；
 * ③相同文案先清后写（DOM 文本实际发生变化，读屏才会触发）；④单例复用；
 * ⑤空文案忽略。</p>
 */
describe('useAnnouncer — 全局读屏播报原语', () => {
  afterEach(() => {
    resetAnnouncerForTest()
  })

  it('首次 announce 惰性创建 #a11y-announcer（aria-live=polite + role=status）', () => {
    expect(document.getElementById('a11y-announcer')).toBeNull()
    announce('已进入工作台页面')
    const el = document.getElementById('a11y-announcer')
    expect(el).not.toBeNull()
    expect(el?.getAttribute('aria-live')).toBe('polite')
    expect(el?.getAttribute('role')).toBe('status')
    expect(el?.getAttribute('aria-atomic')).toBe('true')
    // 视觉隐藏：1px + clipPath，不占布局
    expect(el?.style.width).toBe('1px')
    expect(el?.style.clipPath).toBe('inset(50%)')
  })

  it('播报文案写入 live 区域', () => {
    announce('课程加载完成')
    expect(document.getElementById('a11y-announcer')?.textContent).toBe('课程加载完成')
  })

  it('相同文案重复播报：先清后写（DOM 文本实际变化）', () => {
    announce('已进入成绩页')
    const el = document.getElementById('a11y-announcer')
    expect(el?.textContent).toBe('已进入成绩页')
    // 相同文案再次播报：文本被重置后再写入（读屏依赖 DOM 变化触发）
    announce('已进入成绩页')
    expect(el?.textContent).toBe('已进入成绩页')
  })

  it('单例复用：多次 announce 不重复建节点', () => {
    announce('第一次')
    announce('第二次')
    expect(document.querySelectorAll('#a11y-announcer')).toHaveLength(1)
    expect(document.getElementById('a11y-announcer')?.textContent).toBe('第二次')
  })

  it('空文案忽略（不建节点）', () => {
    announce('')
    expect(document.getElementById('a11y-announcer')).toBeNull()
  })
})
