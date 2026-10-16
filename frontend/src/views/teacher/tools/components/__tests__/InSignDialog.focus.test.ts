import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * InSignDialog 打开聚焦（D2-7 第六批）
 *
 * <p>第六批补齐剩余查看/表单类弹层接线；本用例作为机械接线的回归守护：
 * `ref="dialogRef"` 与 `useDialogFocus` 接线若被误删/误改，此测试失败。
 * 选取带输入框的代表性表单弹层（首批之后的又一批统一形态文件）。</p>
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

import InSignDialog from '@/views/teacher/tools/components/InSignDialog.vue'

const stubs = {
  'el-dialog': { template: '<div class="dialog-stub"><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-input-number': { props: ['modelValue'], template: '<input type="number" />' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-button': { template: '<button><slot /></button>' },
}

async function flushFocus() {
  for (let i = 0; i < 3; i++) {
    await flushPromises()
    rafQueue.splice(0).forEach((cb) => cb(0))
  }
}

describe('InSignDialog — D2-7 第六批机械接线回归', () => {
  it('打开后焦点移入首个输入框（签到标题）', async () => {
    const wrapper = mount(InSignDialog, {
      props: {
        modelValue: false,
        submitting: false,
        form: { title: '', duration: 60, type: 'NORMAL' },
      },
      attachTo: document.body,
      global: { stubs },
    })

    await wrapper.setProps({ modelValue: true })
    await flushFocus()

    const first = wrapper.get('.dialog-stub input')
    expect(document.activeElement).toBe(first.element)
    wrapper.unmount()
  })
})
