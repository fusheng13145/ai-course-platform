import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CiSignDialog 打开聚焦（D2-7 第二批）
 *
 * <p>批 4a 起该对话框由 ui/Modal 承载（el-dialog 等价替换）：调用方 ref="dialogRef"
 * 与 useDialogFocus 接线按 R-T9-7 删除，焦点职责由 Modal 内部接线接管——
 * 接线若被误删/误改，Modal 自身的 focus 套件与本用例会一起红。</p>
 * <p>原语契约（Modal.focus.test.ts 已钉）：打开 → 焦点移入面板内首个可聚焦控件；
 * 面板头部关闭按钮在 DOM 序最前，故落点为 `.modal-head .t-icon-btn`
 * （与 el-dialog「危险操作不默认聚焦确认」的安全惯例同源，不是行为回退）。</p>
 */

const rafQueue: FrameRequestCallback[] = []

beforeEach(() => {
  rafQueue.length = 0
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
    rafQueue.push(cb)
    return rafQueue.length
  })
})
afterEach(() => {
  vi.unstubAllGlobals()
})

import CiSignDialog from '@/views/teacher/cockpit/components/CiSignDialog.vue'

const stubs = {
  // Modal 用真实组件：焦点行为正是本用例要守的契约，不能桩掉
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-button': { template: '<button><slot /></button>' },
}

async function flushFocus() {
  for (let i = 0; i < 3; i++) {
    await flushPromises()
    rafQueue.splice(0).forEach((cb) => cb(0))
  }
}

describe('CiSignDialog — D2-7 打开聚焦（Modal 承载后）', () => {
  it('modelValue 由 false → true：焦点移入弹层面板首个可聚焦控件', async () => {
    const wrapper = mount(CiSignDialog, {
      props: { modelValue: false, saving: false, form: { title: '', duration: 5 } },
      attachTo: document.body,
      global: { stubs },
    })
    expect(document.activeElement).toBe(document.body)

    await wrapper.setProps({ modelValue: true })
    await flushFocus()

    // Modal 遮罩 Teleport 到 body，须从 document 上取
    const first = document.body.querySelector('.modal-head .t-icon-btn')
    expect(first).not.toBeNull()
    expect(document.activeElement).toBe(first)
    wrapper.unmount() // 卸载归还焦点，避免污染后续用例
  })

  it('保持关闭：不抢焦点，弹层不渲染（v-if 卸载契约，强于旧 el-dialog 隐藏态）', async () => {
    const wrapper = mount(CiSignDialog, {
      props: { modelValue: false, saving: false, form: { title: '', duration: 5 } },
      attachTo: document.body,
      global: { stubs },
    })
    await flushFocus()
    expect(document.activeElement).toBe(document.body)
    expect(document.body.querySelector('.overlay')).toBeNull()
    wrapper.unmount()
  })
})
