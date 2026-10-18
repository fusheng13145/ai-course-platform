import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatCard from '../StatCard.vue'

/**
 * Task 12：StatCard（44 个调用点）此前无任何单测。本文件是「现状快照」，不是规格——
 * 它钉的是 StatCard.vue 今天的行为，Phase 5 质感升级不得弄红；若确实要改，红的这一行
 * 必须被当作兼容性变更来评审，而不是「顺手修掉的测试」。
 *
 * 契约来源（均为 StatCard.vue 现状行号）：
 * - 六个 props：label / value / icon / tone / toneColor / foot（:4-11）
 * - value 是「具名插槽 + prop 兜底」（:85）：AdStatCards 与三端 Dashboard 都走 #value
 *   挂 CountUp，prop 通道只在没有插槽时生效——两条通道都得钉。
 * - foot 先 sanitizeHtml 再 v-html（:89）；图标 path 来自组件内 ICONS 常量表（:92 + :76-79）。
 */

/** ICONS.dot 的 path（StatCard.vue:57）：未知图标的回退目标，写死才能在回退失效时立刻变红 */
const DOT_PATH = 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z'

function mountStatCard(props: Record<string, unknown> = {}) {
  return mount(StatCard, { props: { label: 'a', value: 1, ...props } })
}

/** .s-ico 的 style 属性原文；jsdom 原样保留 var(...) 引用（实测记录见 task-12-report §1） */
function icoStyle(props: Record<string, unknown> = {}): string {
  return mountStatCard(props).get('.s-ico').attributes('style') ?? ''
}

describe('StatCard 现状', () => {
  it('渲染 label 与 value（Number 型 value 以字符串落地）', () => {
    const w = mount(StatCard, { props: { label: '在读学生', value: 128 } })
    expect(w.get('.s-label').text()).toBe('在读学生')
    expect(w.get('.s-value').text()).toBe('128')
    // 根类名是调用方 CSS 与 scoped 样式共同的锚点，Phase 5 改名必须惊动这一行
    expect(w.classes()).toEqual(['stat-card'])
  })

  it('#value 插槽覆盖 value prop（CountUp 通道，不是 fallback）', () => {
    const slotted = mount(StatCard, {
      props: { label: 'a', value: 128 },
      slots: { value: '<b class="cu">7</b>' },
    })
    expect(slotted.get('.s-value').text()).toBe('7')
    expect(slotted.find('.s-value .cu').exists()).toBe(true)
    // 同一 prop 不给插槽时走兜底文案：两条通道的差异才是这个插槽存在的意义
    expect(mountStatCard({ value: 128 }).get('.s-value').text()).toBe('128')
  })

  it('foot 缺省（含空串）时不渲染 .s-foot，而不是渲染一个空 div', () => {
    expect(mountStatCard().find('.s-foot').exists()).toBe(false)
    expect(mountStatCard({ foot: '' }).find('.s-foot').exists()).toBe(false)
  })

  it('foot 经 sanitizeHtml：script 与 onerror 被摘，class 保留（trend-up 通道不破）', () => {
    const foot = mountStatCard({
      foot: '<img src=x onerror=alert(1)><script>bad()</script><span class="trend-up">+3</span>',
    }).get('.s-foot')
    // v-html 注入的 <script> 不会执行、但会留在 DOM 里，所以「DOM 中没有 script 元素」
    // 只可能来自 :89 的 sanitizeHtml()——把那一行换成裸 foot 本用例即红。
    expect(foot.find('script').exists()).toBe(false)
    expect(foot.get('img').attributes('onerror')).toBeUndefined()
    // 反向护栏：白名单若收窄，趋势色会被一起洗掉（sanitize.ts 的 ALLOWED_ATTR 含 class）
    expect(foot.get('span.trend-up').text()).toBe('+3')
  })

  it('已知 icon 查 ICONS 表；未知 icon 回退 dot 的 path，而不是空 svg／抛错', () => {
    expect(mountStatCard({ icon: 'users' }).get('.s-ico path').attributes('d')).toContain(
      'M16 21v-2',
    )
    expect(mountStatCard({ icon: 'no-such-icon-name' }).get('.s-ico path').attributes('d')).toBe(
      DOT_PATH,
    )
    // 缺省 icon='dot'（:7）与「未知回退 dot」必须同源，否则默认卡与坏数据卡会长得不一样
    expect(mountStatCard().get('.s-ico path').attributes('d')).toBe(DOT_PATH)
  })

  it('tone / toneColor 以内联样式落到 .s-ico，默认走 accent 变量、可被覆盖', () => {
    expect(icoStyle()).toContain('background: var(--accent-soft)')
    expect(icoStyle()).toContain('color: var(--accent)')
    const custom = icoStyle({ tone: 'var(--warn-soft)', toneColor: 'var(--warn)' })
    expect(custom).toContain('background: var(--warn-soft)')
    expect(custom).toContain('color: var(--warn)')
  })
})
