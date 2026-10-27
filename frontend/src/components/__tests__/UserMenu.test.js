import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import fs from 'node:fs'
import UserMenu from '@/components/UserMenu.vue'
import { USER_MENU_ITEMS } from '@/components/userMenuItems'

const strip = (s) =>
  s
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')

/**
 * 源码读取走变量传参：路径写成字面量的 `new URL('./x.vue', import.meta.url)` 会被
 * Vite 的静态资源引用改写掉（URL 不再是 file:），只有非字面量才原样留给 fs。
 */
const readSource = (rel) => strip(fs.readFileSync(new URL(rel, import.meta.url), 'utf8'))

const items = USER_MENU_ITEMS({
  router: { push: vi.fn() },
  role: 'STUDENT',
  logout: vi.fn().mockResolvedValue(),
  onGuide: vi.fn(),
})

const box = { top: 10, left: 10, bottom: 48 }

/**
 * 触发物用真 DOM 按钮：焦点归还与 document.activeElement 都得落在真元素上，
 * 拿普通对象冒充会让"焦点回去了"这条断言恒真。矩形按用例覆写。
 */
function rectOf(r) {
  return {
    top: r.top,
    left: r.left,
    bottom: r.bottom,
    right: r.left + 40,
    width: 40,
    height: r.bottom - r.top,
    x: r.left,
    y: r.top,
  }
}
function realTrigger(r = box) {
  const el = document.createElement('button')
  el.getBoundingClientRect = () => rectOf(r)
  document.body.appendChild(el)
  return el
}
function setRect(el, r) {
  el.getBoundingClientRect = () => rectOf(r)
}
const topOf = (w) => Number(/top:\s*(-?\d+)px/.exec(w.find('.pop-menu').attributes('style'))[1])
const leftOf = (w) => Number(/left:\s*(-?\d+)px/.exec(w.find('.pop-menu').attributes('style'))[1])

describe('UserMenu 单一来源', () => {
  it('常量顺序固定且含「新手引导」', () => {
    expect(items.map((i) => i.label)).toEqual(['个人中心', '新手引导', '退出登录'])
  })

  it('渲染出的 items 与常量逐字相同', () => {
    const w = mount(UserMenu, { props: { items, triggerEl: realTrigger() } })
    expect(w.findAll('.menu-item').map((n) => n.text().trim())).toEqual(items.map((i) => i.label))
  })

  it('触发物在下半屏 ⇒ 向上开（改前恒 top:52px ⇒ 点左下角、菜单飞右上角）', () => {
    const up = mount(UserMenu, {
      props: { items, triggerEl: realTrigger({ top: 834, left: 0, bottom: 900 }) },
    })
    const down = mount(UserMenu, {
      props: { items, triggerEl: realTrigger({ top: 10, left: 1300, bottom: 48 }) },
    })
    expect(topOf(up)).toBeLessThan(834)
    expect(topOf(down)).toBeGreaterThan(48)
  })

  it('点项 ⇒ 关闭并只触发一次该动作；不再用 innerHTML 拼标签', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], triggerEl: realTrigger() },
    })
    await w.findAll('.menu-item')[0].trigger('click')
    expect(action).toHaveBeenCalledTimes(1)
    expect(w.emitted('closed')).toBeTruthy()
    expect(w.html()).not.toMatch(/innerHTML/)
  })

  it('键盘可达：menuitem 带 tabindex，Enter 等同点击', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], triggerEl: realTrigger() },
    })
    const item = w.findAll('.menu-item')[0]
    expect(item.attributes('tabindex')).toBe('0')
    await item.trigger('keydown.enter')
    expect(action).toHaveBeenCalledTimes(1)
  })

  it('点菜单外面 ⇒ 关闭；点菜单里面不算点外面', () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    w.find('.menu-item').trigger('mousedown')
    expect(w.emitted('closed')).toBeUndefined()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(w.emitted('closed')).toBeTruthy()
    w.unmount()
  })

  it('Esc ⇒ 关闭', () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(w.emitted('closed')).toBeTruthy()
    w.unmount()
  })

  it('卸载后监听摘干净：点外面与 Esc 都不再 emit', () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    w.unmount()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(w.emitted('closed')).toBeUndefined()
  })

  it('打开即把焦点送进第一项（role="menu" 的入口惯例，鼠标打开也不显示焦点环）', async () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    await nextTick()
    expect(document.activeElement).toBe(w.findAll('.menu-item')[0].element)
    w.unmount()
  })

  it('方向键在项间循环：末项往下回首项，首项往上回末项', async () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    await nextTick()
    const els = w.findAll('.menu-item')
    const n = els.length
    // 真实键盘路径下 keydown 永远落在当前聚焦项上，所以先 focus 再按键
    els[n - 1].element.focus()
    await els[n - 1].trigger('keydown.down')
    expect(document.activeElement).toBe(els[0].element)
    await els[0].trigger('keydown.up')
    expect(document.activeElement).toBe(els[n - 1].element)
    els[0].element.focus()
    await els[0].trigger('keydown.down')
    expect(document.activeElement).toBe(els[1].element)
    w.unmount()
  })

  it('Home／End 跳到首尾项', async () => {
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    await nextTick()
    const els = w.findAll('.menu-item')
    const n = els.length
    els[1].element.focus()
    await els[1].trigger('keydown.end')
    expect(document.activeElement).toBe(els[n - 1].element)
    await els[n - 1].trigger('keydown.home')
    expect(document.activeElement).toBe(els[0].element)
    w.unmount()
  })

  it('Space 等同点击（自定义控件不能只认 Enter）', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], triggerEl: realTrigger() },
    })
    await w.findAll('.menu-item')[0].trigger('keydown.space')
    expect(action).toHaveBeenCalledTimes(1)
  })

  it('Esc 把焦点归还触发物（改前菜单销毁后焦点掉回 body，键盘用户丢失位置）', async () => {
    const trigger = realTrigger()
    trigger.focus()
    const w = mount(UserMenu, { props: { items, triggerEl: trigger }, attachTo: document.body })
    await nextTick()
    expect(document.activeElement).not.toBe(trigger)
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(document.activeElement).toBe(trigger)
    w.unmount()
  })

  it('触发物滚走 ⇒ 菜单位置跟着重算（.pop-menu 是 position:fixed，快照矩形会留在原地）', async () => {
    const trigger = realTrigger({ top: 10, left: 10, bottom: 48 })
    const w = mount(UserMenu, { props: { items, triggerEl: trigger }, attachTo: document.body })
    await nextTick()
    const before = { top: topOf(w), left: leftOf(w) }
    setRect(trigger, { top: 600, left: 300, bottom: 640 })
    window.dispatchEvent(new Event('scroll'))
    await nextTick()
    expect(topOf(w)).not.toBe(before.top)
    expect(leftOf(w)).toBe(300)
    setRect(trigger, { top: 120, left: 55, bottom: 160 })
    window.dispatchEvent(new Event('resize'))
    await nextTick()
    expect(leftOf(w)).toBe(55)
    w.unmount()
  })

  it('卸载把 scroll／resize 监听一起摘掉（常驻壳层里漏一次就永久多一个订阅者）', async () => {
    const off = vi.spyOn(window, 'removeEventListener')
    const w = mount(UserMenu, {
      props: { items, triggerEl: realTrigger() },
      attachTo: document.body,
    })
    await nextTick()
    w.unmount()
    const removed = off.mock.calls.map((c) => c[0])
    expect(removed).toContain('scroll')
    expect(removed).toContain('resize')
    off.mockRestore()
  })

  it('profile 路径：宿主给的 profilePath 优先，缺省按角色小写回退', () => {
    const push = vi.fn()
    USER_MENU_ITEMS({
      router: { push },
      role: 'STUDENT',
      profilePath: '/x/profile',
      logout: vi.fn(),
      onGuide: vi.fn(),
    })[0].action()
    expect(push).toHaveBeenCalledWith('/x/profile')
    USER_MENU_ITEMS({
      router: { push },
      role: 'TEACHER',
      logout: vi.fn(),
      onGuide: vi.fn(),
    })[0].action()
    expect(push).toHaveBeenCalledWith('/teacher/profile')
  })

  it('两处壳层都不得再自带菜单标签字面量（分叉的根）', () => {
    for (const p of [
      '../../layouts/AppShell.vue',
      '../../layouts/components/topbar/AtbUserMenu.vue',
    ]) {
      const src = strip(fs.readFileSync(new URL(p, import.meta.url), 'utf8'))
      expect(src, `${p} 里还写着「新手引导」字面量 ⇒ 它没走单一来源`).not.toMatch(
        /'新手引导'|"新手引导"/,
      )
      expect(src).toMatch(/USER_MENU_ITEMS/)
    }
  })

  // 堆叠层级在 jsdom 里量不到（没有布局、没有真堆叠上下文），只能把源码钉住：
  // 删掉那条 scoped 例外、或把 token 调到 EP 弹层之下，这条必须红。
  // 层级必须绑在 `.pop-menu` 这条选择器上：只匹配 "z-index: var(--z-shell-menu)" 的话，
  // 把它挪到 .menu-item／.mi-ico 上照样绿，而弹层本体又掉回 --z-menu(130) 那档——
  // 正是本轮评审抓到的那条缺陷的另一种复发形态。浏览器面的真实叠放读数另有一条（e2e 引导套件）。
  it('层级守卫：本组件只给 .pop-menu 补一条 scoped 层级例外，且 --z-shell-menu 落在 EP 弹层与引导层之间', () => {
    const vue = readSource('../UserMenu.vue')
    const rule = /\.pop-menu\s*\{([^}]*)\}/.exec(vue)
    expect(rule, '.pop-menu 这条 scoped 规则不在源码里 ⇒ 层级例外没了主').not.toBeNull()
    expect(
      /z-index:\s*var\(--z-shell-menu\)/.test(rule[1]),
      '.pop-menu 规则里没有 z-index: var(--z-shell-menu) ⇒ 弹层掉回全局 --z-menu(130)',
    ).toBe(true)
    expect(vue.match(/z-index:/g) || [], '本组件只许补这一条层级例外').toHaveLength(1)

    const css = readSource('../../styles/theme.css')
    // 两个数都从文件里解析后比大小：token 允许被合理重调，写死数值既挡不住改错方向也挡不住正常调参
    const tokenValue = (name) => {
      const hit = new RegExp(`--${name}:\\s*(-?\\d+)`).exec(css)
      expect(hit, `theme.css 里解析不到 --${name}`).not.toBeNull()
      return Number(hit[1])
    }
    const shellMenu = tokenValue('z-shell-menu')
    const onboarding = tokenValue('z-onboarding')
    const EP_FLOOR = 2300 // EP 弹层的 z-index 起点（App.vue el-config-provider :z-index="2300"，与 theme.css --z-onboarding 注释同源；P4-GATE-01 起由 2000 抬至 2300，下界必须跟随真实起点，否则钉测护不住 e2e 实测的 z=2310 遮罩）
    expect(shellMenu >= EP_FLOOR, `${shellMenu} 压不过 EP 弹层起点 ${EP_FLOOR}`).toBe(true)
    expect(shellMenu < onboarding, `${shellMenu} 盖过了引导层 ${onboarding}`).toBe(true)
  })

  it('图标盒守卫：.mi-ico 的四条属性在源码里（jsdom 不套 scoped 样式，量不到计算值）', () => {
    const vue = readSource('../UserMenu.vue')
    const box = /\.mi-ico\s*\{([^}]*)\}/.exec(vue)
    expect(box, '.mi-ico 规则不在源码里 ⇒ 图标盒又变成无样式钩子').not.toBeNull()
    for (const [prop, value] of [
      ['display', 'grid'],
      ['place-items', 'center'],
      ['width', '15px'],
      ['height', '15px'],
    ]) {
      expect(new RegExp(`${prop}:\\s*${value}`).test(box[1]), `.mi-ico 缺 ${prop}: ${value}`).toBe(
        true,
      )
    }
  })
})
