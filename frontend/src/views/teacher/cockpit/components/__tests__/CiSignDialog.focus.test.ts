import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CiSignDialog 打开聚焦（D2-7 第二批）
 *
 * <p>第二批由脚本机械接入 9 个标准对话框（:model-value="modelValue" prop 模式）。
 * 本用例作为机械接线的回归守护：el-dialog 的 ref="dialogRef" 与
 * useDialogFocus 接线若被误删/误改，此测试失败。</p>
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
  'el-dialog': { template: '<div class="dialog-stub"><slot /></div>' },
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

describe('CiSignDialog — D2-7 第二批机械接线回归', () => {
  it('打开后焦点移入首个输入框（签到标题）', async () => {
    const wrapper = mount(CiSignDialog, {
      props: { modelValue: false, saving: false, form: { title: '', duration: 5 } },
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
