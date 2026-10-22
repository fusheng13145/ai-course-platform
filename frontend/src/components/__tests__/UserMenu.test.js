import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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

describe('UserMenu 单一来源', () => {
  it('常量顺序固定且含「新手引导」', () => {
    expect(items.map((i) => i.label)).toEqual(['个人中心', '新手引导', '退出登录'])
  })

  it('渲染出的 items 与常量逐字相同', () => {
    const w = mount(UserMenu, { props: { items, anchorRect: box } })
    expect(w.findAll('.menu-item').map((n) => n.text().trim())).toEqual(items.map((i) => i.label))
  })

  it('触发物在下半屏 ⇒ 向上开（改前恒 top:52px ⇒ 点左下角、菜单飞右上角）', () => {
    const up = mount(UserMenu, { props: { items, anchorRect: { top: 834, left: 0, bottom: 900 } } })
    const down = mount(UserMenu, {
      props: { items, anchorRect: { top: 10, left: 1300, bottom: 48 } },
    })
    const topOf = (w) => Number(/top:\s*(-?\d+)px/.exec(w.find('.pop-menu').attributes('style'))[1])
    expect(topOf(up)).toBeLessThan(834)
    expect(topOf(down)).toBeGreaterThan(48)
  })

  it('点项 ⇒ 关闭并只触发一次该动作；不再用 innerHTML 拼标签', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], anchorRect: { top: 10, left: 10 } },
    })
    await w.findAll('.menu-item')[0].trigger('click')
    expect(action).toHaveBeenCalledTimes(1)
    expect(w.emitted('closed')).toBeTruthy()
    expect(w.html()).not.toMatch(/innerHTML/)
  })

  it('键盘可达：menuitem 带 tabindex，Enter 等同点击', async () => {
    const action = vi.fn()
    const w = mount(UserMenu, {
      props: { items: [{ label: '甲', icon: 'user', action }], anchorRect: { top: 10, left: 10 } },
    })
    const item = w.findAll('.menu-item')[0]
    expect(item.attributes('tabindex')).toBe('0')
    await item.trigger('keydown.enter')
    expect(action).toHaveBeenCalledTimes(1)
  })

  it('点菜单外面 ⇒ 关闭；点菜单里面不算点外面', () => {
    const w = mount(UserMenu, { props: { items, anchorRect: box }, attachTo: document.body })
    w.find('.menu-item').trigger('mousedown')
    expect(w.emitted('closed')).toBeUndefined()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(w.emitted('closed')).toBeTruthy()
    w.unmount()
  })

  it('Esc ⇒ 关闭', () => {
    const w = mount(UserMenu, { props: { items, anchorRect: box }, attachTo: document.body })
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(w.emitted('closed')).toBeTruthy()
    w.unmount()
  })

  it('卸载后监听摘干净：点外面与 Esc 都不再 emit', () => {
    const w = mount(UserMenu, { props: { items, anchorRect: box }, attachTo: document.body })
    w.unmount()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(w.emitted('closed')).toBeUndefined()
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
  it('层级守卫：本组件只补一条 scoped 层级例外，且 --z-shell-menu 落在 EP 弹层与引导层之间', () => {
    const vue = readSource('../UserMenu.vue')
    expect(vue).toMatch(/z-index:\s*var\(--z-shell-menu\)/)

    const css = readSource('../../styles/theme.css')
    // 两个数都从文件里解析后比大小：token 允许被合理重调，写死数值既挡不住改错方向也挡不住正常调参
    const tokenValue = (name) => {
      const hit = new RegExp(`--${name}:\\s*(-?\\d+)`).exec(css)
      expect(hit, `theme.css 里解析不到 --${name}`).not.toBeNull()
      return Number(hit[1])
    }
    const shellMenu = tokenValue('z-shell-menu')
    const onboarding = tokenValue('z-onboarding')
    const EP_FLOOR = 2000 // EP 弹层的 z-index 起点（theme.css 的 --z-onboarding 注释同源）
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
