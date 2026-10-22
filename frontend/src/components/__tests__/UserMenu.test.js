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
})
