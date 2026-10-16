import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * ClassEditDialog 打开聚焦（D2-7）
 *
 * <p>el-dialog 默认焦点停在容器，键盘用户需多次 Tab 才到首字段。接入
 * useDialogFocus 后：打开 → 焦点移入首个可聚焦输入（本对话框为班级名称）。</p>
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

import ClassEditDialog from '@/views/admin/user/components/ClassEditDialog.vue'

const stubs = {
  // el-dialog 桩：渲染默认插槽（真实环境为 teleport + 过渡，焦点逻辑在 rAF 后执行）
  'el-dialog': { template: '<div class="dialog-stub"><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue'],
    template: '<input :value="modelValue" />',
  },
  'el-button': { template: '<button><slot /></button>' },
}

function mountDialog() {
  // attachTo：jsdom 中未挂载文档的元素 focus() 无效
  return mount(ClassEditDialog, {
    props: {
      modelValue: false,
      editing: false,
      form: {
        name: '',
        code: '',
        major: '',
        gradeLevel: '',
        maxStudents: 60,
        description: '',
        isActive: true,
      },
    },
    attachTo: document.body,
    global: { stubs },
  })
}

/** 冲刷「watch → nextTick → rAF」三段异步链（多轮确保收敛） */
async function flushFocus() {
  for (let i = 0; i < 3; i++) {
    await flushPromises()
    rafQueue.splice(0).forEach((cb) => cb(0))
  }
}

describe('ClassEditDialog — D2-7 打开聚焦', () => {
  it('modelValue 由 false → true：焦点移入首个输入框', async () => {
    const wrapper = mountDialog()
    expect(document.activeElement).toBe(document.body)

    await wrapper.setProps({ modelValue: true })
    await flushFocus()

    const first = wrapper.get('.dialog-stub input')
    expect(document.activeElement).toBe(first.element)
    wrapper.unmount() // 卸载归还焦点，避免污染后续用例
  })

  it('保持关闭：不抢焦点', async () => {
    const wrapper = mountDialog()
    await flushFocus()
    expect(document.activeElement).toBe(document.body)
    expect(wrapper.find('.dialog-stub input').exists()).toBe(true)
  })
})
