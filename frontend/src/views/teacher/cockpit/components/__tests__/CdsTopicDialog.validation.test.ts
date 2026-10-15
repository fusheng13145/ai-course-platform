import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CdsTopicDialog 表单校验交互（D2-4）
 *
 * <p>原实现为手写 if 校验（「请填写标题与内容」+ return），rules 未声明、
 * 无内联错误与首错定位。改造后：标题/内容声明 required 规则（blur 触发），
 * 提交经 validateFormFields 门（真实环境内联错误 + scroll-to-error），
 * 桩/无表单环境由原手写快检兜底。本测试锁定两条路径。</p>
 */

import CdsTopicDialog from '@/views/teacher/cockpit/components/CdsTopicDialog.vue'

/** script setup 内部绑定在测试环境经 vm 暴露；以类型桥接访问（对齐 ai-assistant-render 范式） */
interface DialogInternal {
  rules: Record<string, Array<{ required?: boolean; message?: string; trigger?: string }>>
  form: { title: string; content: string; chapterId: string }
  submit: () => Promise<void>
}
const dialogVm = (wrapper: { vm: unknown }) => wrapper.vm as unknown as DialogInternal

const elMessage = vi.hoisted(() => ({
  warning: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

function mountDialog(formStub) {
  return mount(CdsTopicDialog, {
    props: { saving: false },
    global: {
      stubs: {
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
        'el-form': formStub,
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': { template: '<input />', props: ['modelValue'] },
        'el-button': { template: '<button><slot /></button>' },
      },
    },
  })
}

describe('CdsTopicDialog — 规则声明与提交门（D2-4）', () => {
  it('rules 声明：标题/内容为必填且 blur 触发', () => {
    const wrapper = mountDialog(true)
    const rules = dialogVm(wrapper).rules
    expect(rules.title[0].required).toBe(true)
    expect(rules.title[0].trigger).toBe('blur')
    expect(rules.content[0].required).toBe(true)
    expect(rules.content[0].trigger).toBe('blur')
  })

  it('校验失败（validateField 拒绝）→ 不 emit submit', async () => {
    const wrapper = mountDialog({
      template: '<form><slot /></form>',
      methods: {
        validateField: vi.fn().mockRejectedValue(new Error('invalid')),
      },
    })
    dialogVm(wrapper).form.title = '标题'
    dialogVm(wrapper).form.content = '内容'
    await dialogVm(wrapper).submit()
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('校验通过 → emit submit 且字段 trim', async () => {
    const wrapper = mountDialog({
      template: '<form><slot /></form>',
      methods: { validateField: vi.fn().mockResolvedValue(true) },
    })
    dialogVm(wrapper).form.title = '  话题  '
    dialogVm(wrapper).form.content = '  内容  '
    await dialogVm(wrapper).submit()
    await flushPromises()

    const events = wrapper.emitted('submit')
    expect(events).toHaveLength(1)
    expect(events[0][0]).toMatchObject({ title: '话题', content: '内容' })
  })

  it('桩环境（无 validateField）→ 手写快检兜底：空标题警告且不 emit', async () => {
    const wrapper = mountDialog(true)
    dialogVm(wrapper).form.title = ''
    dialogVm(wrapper).form.content = '内容'
    await dialogVm(wrapper).submit()
    await flushPromises()

    expect(elMessage.warning).toHaveBeenCalledWith('请填写标题与内容')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})
