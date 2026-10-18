import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DropdownMenu from '../DropdownMenu.vue'

/**
 * Task 12：DropdownMenu（唯一调用点 NotificationManagement.vue:50-56，v-if 反复开关）现状快照。
 * 契约来源（DropdownMenu.vue 现状行号）：
 * - props：items / x / y（:4-8），emit：close（:10）。
 * - items 四态分流（:61-70）：'-' → .menu-divider；{label, group} → .menu-label；
 *   其余 → .menu-item（danger 附加类、hint 进 .m-key、icon 查内部 ICONS 表）。
 * - 未知 icon 名 **不回退**：iconSvg 查不到就返回空串（:52-57），于是 .m-ico 是个空壳。
 *   这一点与 StatCard（回退 dot）相反，别照着 StatCard 写。
 * - 关闭有三条路径：点项 emit close 后再 item.onClick?.()（:13-16）、菜单外的 mousedown
 *   （:27-30，注意 addEventListener 被 setTimeout 推迟了一个宏任务）；另有一条防线是根上的
 *   @click.stop（:60），它不关菜单、只是把点击关在菜单内。
 * - 定位是 onMounted 里命令式写 style.left/top，按视口减 10px 夹紧（:18-23）。
 *
 * 卸载观察点为什么落在监听器注册表而不是 emitted 计数：VTU 的 unmount() 会 removeEventHistory
 * （vue-test-utils.cjs.js:7601-7610 与 :7857-7867），Vue 的 emit() 对已卸载实例起手就 return
 * （runtime-core.cjs.js:4483-4484），所以「卸载后再 dispatch mousedown、断言 close 不增长」
 * 这种写法删掉 removeEventListener 也照样绿——Task 6 就是这么被评审抓出来的。
 */

interface TrackedWrapper {
  readonly element: Element
  unmount(): void
}
const mounted: TrackedWrapper[] = []

function factory(props: Record<string, unknown> = {}) {
  const wrapper = mount(DropdownMenu, { props, attachTo: document.body })
  mounted.push(wrapper)
  return wrapper
}

/** 让 onMounted 里那个 setTimeout(…, 0) 跑掉（组件内定时器先入队，所以必定先执行） */
async function flushMacrotask() {
  await new Promise((r) => setTimeout(r, 0))
}

describe('DropdownMenu 现状', () => {
  afterEach(() => {
    for (const w of mounted) {
      if (w.element.isConnected) w.unmount()
    }
    mounted.length = 0
  })

  it('items 缺省或为空数组时只渲染空 .pop-menu，不抛错', () => {
    expect(factory().findAll('.menu-item')).toHaveLength(0)
    const w = factory({ items: [] })
    expect(w.find('.pop-menu').exists()).toBe(true)
    expect(w.findAll('.menu-divider')).toHaveLength(0)
  })

  it('items 四态分流：普通项 / "-" 分隔线 / {label,group} 分组标题 / danger 附加类', () => {
    const w = factory({
      items: [
        { text: '查看' },
        '-',
        { label: '危险操作', group: true },
        { text: '删除', danger: true },
      ],
    })
    const items = w.findAll('.menu-item')
    expect(items).toHaveLength(2)
    expect(items.map((i) => i.text())).toEqual(['查看', '删除'])
    expect(items[0].classes()).not.toContain('danger')
    expect(items[1].classes()).toContain('danger')
    expect(w.findAll('.menu-divider')).toHaveLength(1)
    expect(w.get('.menu-label').text()).toBe('危险操作')
  })

  it('已知 icon 查 ICONS 表出 svg；未知 icon 名渲染空 .m-ico（不回退 dot）；hint 进 .m-key', () => {
    const w = factory({
      items: [
        { text: 'A', icon: 'eye', hint: 'Enter' },
        { text: 'B', icon: 'no-such-icon-name' },
      ],
    })
    const items = w.findAll('.menu-item')
    expect(items[0].get('.m-ico svg path').attributes('d')).toContain('M2 12s3.5-7')
    expect(items[0].get('.m-key').text()).toBe('Enter')
    // 未知图标：容器还在、里面没有 svg（:53 的 `ICONS[name] || ''`）。
    // 这正是与 StatCard 相反的现状，Phase 5 若统一成回退 dot，这条会红。
    expect(items[1].get('.m-ico').find('svg').exists()).toBe(false)
    expect(items[1].find('.m-key').exists()).toBe(false)
  })

  it('点 .menu-item：先 emit close、后执行 item.onClick；没有 onClick 也不抛', async () => {
    let closeCountAtClick = -1
    const onClick = vi.fn(() => {
      // onClick 里读 emitted：close 的记录发生在 emit() 内部、早于父级 handler 与本回调
      // （runtime-core.cjs.js:4522-4524 的 devtoolsComponentEmit → VTU recordEvent，
      // 而 props 上的 handler 到 :4538-4551 才取用），所以这里读到的是 1 而不是 0。
      closeCountAtClick = wrapper.emitted('close')?.length ?? 0
    })
    const wrapper = factory({ items: [{ text: 'A', onClick }] })
    await wrapper.get('.menu-item').trigger('click')
    expect(onClick).toHaveBeenCalledTimes(1)
    expect(closeCountAtClick).toBe(1)
    expect(wrapper.emitted('close')).toHaveLength(1)

    const bare = factory({ items: [{ text: 'A' }] })
    await bare.get('.menu-item').trigger('click')
    expect(bare.emitted('close')).toHaveLength(1)
  })

  it('点分隔线 / 分组标题不 emit；菜单内的 click 被根上的 @click.stop 挡在菜单内', async () => {
    const w = factory({ items: ['-', { label: '分组', group: true }] })
    await w.get('.menu-divider').trigger('click')
    await w.get('.menu-label').trigger('click')
    expect(w.emitted('close')).toBeUndefined()

    const seen: Event[] = []
    const onBodyClick = (e: Event) => seen.push(e)
    document.body.addEventListener('click', onBodyClick)
    try {
      const item = factory({ items: [{ text: 'A' }] })
      await item.get('.menu-item').trigger('click')
      expect(item.emitted('close')).toHaveLength(1)
      // trigger() 造的是 bubbles:true 的真事件（vue-test-utils.cjs.js:432-435）并直接
      // element.dispatchEvent（:7375）；attachTo 到 body 后走完整冒泡链。
      // 所以 seen 为 0 只可能是 :60 的 .stop 生效，不是「事件本来就不冒泡」。
      expect(seen).toHaveLength(0)
      // 对照组：证明监听器确实挂在生效的路径上，排除「压根没冒泡」导致的假绿
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(seen).toHaveLength(1)
    } finally {
      document.body.removeEventListener('click', onBodyClick)
    }
  })

  it('定位：x/y 直落 left/top；超出视口时按 10px 边距夹紧', () => {
    const fit = factory({ items: [], x: 40, y: 60 })
    const fitStyle = fit.get('.pop-menu').attributes('style') ?? ''
    expect(fitStyle).toContain('left: 40px')
    expect(fitStyle).toContain('top: 60px')

    // jsdom 的 getBoundingClientRect 恒返回 0 宽高（实测记录见 task-12-report §1），
    // 所以夹紧结果就是 innerWidth-10 / innerHeight-10
    const clamp = factory({ items: [], x: 99999, y: 99999 })
    const clampStyle = clamp.get('.pop-menu').attributes('style') ?? ''
    expect(clampStyle).toContain(`left: ${window.innerWidth - 10}px`)
    expect(clampStyle).toContain(`top: ${window.innerHeight - 10}px`)
  })

  it('监听器推迟一个宏任务才注册：打开菜单时的菜单外 mousedown 不会当场关掉它', async () => {
    // 观察点必须是「菜单外」的 mousedown：菜单内的那次无论监听器注册早晚都不 emit
    // （contains() 先挡掉），拿它当探针会永远绿——DM-2 变异实测踩过这个坑，
    // 把 setTimeout 改成同步注册时只有外侧 mousedown 才把它照出来。
    const w = factory({ items: [{ text: 'A' }] })
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await w.vm.$nextTick()
    // :30 的 setTimeout(…, 0) 还没跑，document 上尚无 handler
    expect(w.emitted('close')).toBeUndefined()

    await flushMacrotask()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await w.vm.$nextTick()
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('宏任务之后：菜单内的 mousedown 不 emit，菜单外的 mousedown emit close', async () => {
    const inMenu = factory({ items: [{ text: 'A' }] })
    await flushMacrotask()
    await inMenu.get('.menu-item').trigger('mousedown')
    expect(inMenu.emitted('close')).toBeUndefined()
    // 先卸掉它，否则它挂在 document 上的 handler 会收到下面那次 body mousedown、
    // 反过来污染 outside 的计数（每个用例只断言自己那一次挂载）
    inMenu.unmount()

    const outside = factory({ items: [{ text: 'A' }] })
    await flushMacrotask()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await outside.vm.$nextTick()
    expect(outside.emitted('close')).toHaveLength(1)
  })

  it('卸载必须摘掉 document 上的 mousedown 监听（onMounted 里注册的 onBeforeUnmount 也得生效）', async () => {
    // :31 的 onBeforeUnmount 是在 onMounted 回调内注册的（非常规写法）。它之所以还能在卸载时
    // 执行，是因为 injectHook 只往 instance.bum 数组里 push（runtime-core.cjs.js:3125-3140），
    // 而 unmountComponent 在卸载那一刻才解构读取 bum（:6848-6852）。Phase 5 若把注册位置挪走
    // 或删掉 removeEventListener，下面的 leaked 就会非空。
    interface RawListeners {
      addEventListener: (type: string, listener: EventListener, options?: unknown) => void
      removeEventListener: (type: string, listener: EventListener, options?: unknown) => void
    }
    const raw = document as unknown as RawListeners
    const rawAdd = raw.addEventListener.bind(document)
    const rawRemove = raw.removeEventListener.bind(document)
    const captureOf = (o?: boolean | { capture?: boolean }): boolean =>
      typeof o === 'boolean' ? o : !!o?.capture
    type Registration = readonly [type: string, listener: EventListener, capture: boolean]
    const added: Registration[] = []
    const removed: Registration[] = []

    const addSpy = vi
      .spyOn(document, 'addEventListener')
      .mockImplementation((type, listener, options) => {
        added.push([type, listener as EventListener, captureOf(options)])
        rawAdd(type, listener as EventListener, options)
      })
    const removeSpy = vi
      .spyOn(document, 'removeEventListener')
      .mockImplementation((type, listener, options) => {
        removed.push([type, listener as EventListener, captureOf(options)])
        rawRemove(type, listener as EventListener, options)
      })

    try {
      const wrapper = factory()
      await flushMacrotask()
      const mousedownAdds = added.filter(([t]) => t === 'mousedown')
      expect(mousedownAdds).toHaveLength(1)
      wrapper.unmount()
      const leaked = mousedownAdds.filter(
        ([t, fn, cap]) => !removed.some(([rt, rf, rc]) => rt === t && rf === fn && rc === cap),
      )
      expect(leaked).toEqual([])
    } finally {
      addSpy.mockRestore()
      removeSpy.mockRestore()
    }
  })
})
