import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TabGroup from '../TabGroup.vue'

/**
 * Task 12：TabGroup（UmDetailDialog、LoginLogs 两个调用点）现状快照。
 * 契约来源（TabGroup.vue 现状行号）：
 * - props：tabs（必填 Array）/ active / variant（B1 自 style 改名：原名撞 Vue 保留属性），emit：change
 * - 内部 cur + watch(props.active)（既接受父组件回写，也在点击时自行高亮），
 *   这是一个「半受控」组件——Phase 5 若把它改成纯受控，红的是下面第 4、5 两个用例。
 * - variant prop 决定根类名 tabs-pill / tabs-line / tabs-seg；
 *   改名后调用点写 variant="seg"（LoginLogs.vue:4 / UmDetailDialog.vue:9）。
 * - tabs[].icon 走 sanitizeHtml、tabs[].badge 是 `!= null` 判断。
 * 类名与 StatCard 无任何重叠，别照搬别处的选择器。
 */

const TABS = [
  { key: 'all', label: '全部' },
  { key: 'fail', label: '失败' },
]

function mountTabs(props: Record<string, unknown> = {}) {
  return mount(TabGroup, { props: { tabs: TABS, ...props } })
}

describe('TabGroup 现状', () => {
  it('variant prop 决定根类名', () => {
    const w = mountTabs({ variant: 'seg' })
    expect(w.classes()).toEqual(['tabs-seg'])
    // variant 是声明过的 prop，不会被当作 attr 落进 HTML style 属性
    expect(w.attributes('style')).toBeUndefined()
    expect(mountTabs({ variant: 'line' }).classes()).toEqual(['tabs-line'])
  })

  it('缺省 variant 为 pill；未知值不校验、原样拼进类名', () => {
    expect(mountTabs().classes()).toEqual(['tabs-pill'])
    expect(mountTabs({ variant: 'nope' }).classes()).toEqual(['tabs-nope'])
  })

  it('每个 tab 渲染一个 .tab-i，顺序同 tabs；active 命中才挂 active 类', () => {
    const w = mountTabs({ active: 'fail' })
    const items = w.findAll('.tab-i')
    expect(items).toHaveLength(2)
    expect(items.map((i) => i.text())).toEqual(['全部', '失败'])
    expect(items[0].classes()).not.toContain('active')
    expect(items[1].classes()).toContain('active')
    // active 缺省为空串（:7）：一个都不高亮，而不是默认高亮第一个
    expect(mountTabs().findAll('.tab-i.active')).toHaveLength(0)
  })

  it('点击 .tab-i：emit change(key) 且内部立刻高亮，不要求父组件回写', async () => {
    const w = mountTabs({ active: 'all' })
    await w.findAll('.tab-i')[1].trigger('click')
    expect(w.emitted('change')).toEqual([['fail']])
    expect(w.findAll('.tab-i')[0].classes()).not.toContain('active')
    expect(w.findAll('.tab-i')[1].classes()).toContain('active')
  })

  it('重复点击当前 tab 不去重：change 每次都会再 emit 一次', async () => {
    const w = mountTabs({ active: 'all' })
    await w.findAll('.tab-i')[0].trigger('click')
    await w.findAll('.tab-i')[0].trigger('click')
    expect(w.emitted('change')).toEqual([['all'], ['all']])
  })

  it('active prop 变化经 watch 回写内部高亮（父组件受控通道）', async () => {
    const w = mountTabs({ active: 'all' })
    await w.setProps({ active: 'fail' })
    expect(w.findAll('.tab-i')[1].classes()).toContain('active')
    expect(w.emitted('change')).toBeUndefined()
  })

  it('badge 用 != null 判定：badge=0 仍渲染，缺省不渲染', () => {
    const w = mount(TabGroup, {
      props: {
        tabs: [
          { key: 'a', label: 'A', badge: 0 },
          { key: 'b', label: 'B' },
        ],
      },
    })
    expect(w.findAll('.tab-badge').map((b) => b.text())).toEqual(['0'])
  })

  it('tab.icon 走 sanitizeHtml：svg/img 保留、onerror 摘除；无 icon 时不渲染图标 span', () => {
    const w = mount(TabGroup, {
      props: {
        tabs: [
          {
            key: 'a',
            label: 'A',
            icon: '<svg viewBox="0 0 24 24"><path d="M1 1"/></svg><img src=x onerror=alert(1)>',
          },
        ],
      },
    })
    expect(w.get('.tab-i svg path').attributes('d')).toBe('M1 1')
    expect(w.get('.tab-i img').attributes('onerror')).toBeUndefined()
    expect(mountTabs().get('.tab-i').find('span').exists()).toBe(false)
  })
})
