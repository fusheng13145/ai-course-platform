import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useGlobalHotkeys, hasBlockingOverlay } from '@/composables/useKeyboardShortcuts'

const put = (html) => {
  document.body.innerHTML = html
}

/**
 * jsdom 的 getBoundingClientRect 恒返回 0×0，"有面积"这一维只能就地改这枚节点的矩形。
 * 反向读数（0 面积）故意不打桩，用 jsdom 原生的 0×0。
 */
const giveArea = (selector, width = 800, height = 600) => {
  const el = document.querySelector(selector)
  el.getBoundingClientRect = () => ({
    width,
    height,
    top: 0,
    left: 0,
    right: width,
    bottom: height,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  })
  return el
}

/** 挂载一条裸绑定并按下 / —— 用来验证遮罩判定确实接进了热键循环 */
function pressSlashWithBinding(fired) {
  const wrapper = mount(
    defineComponent({
      setup() {
        useGlobalHotkeys([{ key: '/', handler: fired }])
        return () => null
      },
    }),
    { attachTo: document.body },
  )
  window.dispatchEvent(new KeyboardEvent('keydown', { key: '/', cancelable: true }))
  wrapper.unmount()
}

/**
 * 遮罩判定：存在 ≠ 打开（改前 F11：一个 display:none 的 .el-overlay 掐死全站快捷键）
 *
 * 【R-19b 起判据只有一份】原先这里还断言 `hasOpenOverlay()`（只认 `.el-overlay`）的行为，
 * 那是"只被测试保护的孤儿导出"——生产侧热键循环与 `AppShell` 的 `?` 分支都只认
 * `hasBlockingOverlay`。该导出已删，它那三条读数（隐藏不算／可见且有面积才算／0 面积不算）
 * 全部**并入本文件**、改断 `hasBlockingOverlay`：对 `.el-overlay` 而言两者结果一致，
 * 而覆盖 `.ob-overlay` 的那一条本来就只能在 `hasBlockingOverlay` 上断。
 */
describe('遮罩判定：存在 ≠ 打开（改前 F11：一个 display:none 的 .el-overlay 掐死全站快捷键）', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('隐藏的 .el-overlay 不算打开', () => {
    put('<div class="el-overlay" style="display:none"></div>')
    expect(hasBlockingOverlay()).toBe(false)
  })

  it('可见的 .el-overlay 算打开（正读数配对，否则"永远 false"也能绿）', () => {
    put('<div class="el-overlay" style="display:block;width:800px;height:600px"></div>')
    giveArea('.el-overlay')
    expect(hasBlockingOverlay()).toBe(true)
  })

  it('0 面积的可见节点不算（EP 收起态常见 rect 0×0）', () => {
    put('<div class="el-overlay" style="display:block;width:0;height:0"></div>')
    expect(hasBlockingOverlay()).toBe(false)
  })

  it('引导自己的 .ob-overlay 算阻塞遮罩：快捷键不得穿透引导', () => {
    put('<div class="ob-overlay" style="display:block;width:800px;height:600px"></div>')
    giveArea('.ob-overlay')
    expect(hasBlockingOverlay()).toBe(true)
  })

  it('没写尺寸、只有 display 的节点同样不算（判的是矩形面积，不是 style 字面量）', () => {
    put('<div class="el-overlay"></div>')
    expect(hasBlockingOverlay()).toBe(false)
  })

  it('阻塞遮罩在场时按 / 不触发（判定接进了热键循环，不只是导出的函数）', () => {
    put('<div class="ob-overlay" style="display:block;width:800px;height:600px"></div>')
    giveArea('.ob-overlay')
    const fired = vi.fn()
    pressSlashWithBinding(fired)
    expect(fired).not.toHaveBeenCalled()
  })

  it('常驻的隐藏 .el-overlay 在场时按 / 照常响应（F11 那条全站快捷键开关）', () => {
    put('<div class="el-overlay el-modal-dialog" style="display:none"></div>')
    const fired = vi.fn()
    pressSlashWithBinding(fired)
    expect(fired).toHaveBeenCalledTimes(1)
  })
})
