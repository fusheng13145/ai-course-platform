/* eslint-disable vue/one-component-per-file -- 组件测试桩：同一测试文件内需要定义 el-input / el-button 两个轻量 stub */
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import KbRagPlayground from '../KbRagPlayground.vue'

const InputStub = defineComponent({
  props: { modelValue: { type: [String, Number], default: '' } },
  emits: ['update:modelValue', 'keyup'],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup="$emit(\'keyup\', $event)" />',
})

const ButtonStub = defineComponent({
  props: { disabled: Boolean },
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>',
})

function mountPlayground(selectedCourseId: string | number | null = 12) {
  return mount(KbRagPlayground, {
    props: {
      selectedCourseId,
      askForm: { question: '', topK: 5 },
      askResult: null,
      asking: false,
      retrieveForm: { query: '', topK: 5 },
      retrieveResult: null,
      retrieving: false,
      preview: (value: string) => value,
    },
    global: {
      stubs: {
        'el-input': InputStub,
        'el-button': ButtonStub,
      },
    },
  })
}

describe('KbRagPlayground', () => {
  it('forwards button clicks and Enter presses to the parent events', async () => {
    const wrapper = mountPlayground()
    const buttons = wrapper.findAll('button')
    const inputs = wrapper.findAll('input')

    await buttons[0].trigger('click')
    await inputs[0].trigger('keyup.enter')
    await buttons[1].trigger('click')
    await inputs[1].trigger('keyup.enter')

    expect(wrapper.emitted('ask')).toHaveLength(2)
    expect(wrapper.emitted('retrieve')).toHaveLength(2)
  })

  it('keeps the actions disabled when no course is selected', async () => {
    const wrapper = mountPlayground(null)
    const buttons = wrapper.findAll('button')

    expect(buttons).toHaveLength(2)
    expect(buttons.every((button) => (button.element as HTMLButtonElement).disabled)).toBe(true)
  })
})
