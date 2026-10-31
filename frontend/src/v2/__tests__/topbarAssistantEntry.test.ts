import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const ui = vi.hoisted(() => ({
  role: null as unknown as { value: string },
}))

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  ui.role = ref('student')
  return {
    useV2Ui: () => ({
      role: ui.role,
      roleLabel: ref('学生'),
      displayName: ref('演示学生'),
      avatar: ref('学'),
      theme: ref('light'),
      toggleTheme: vi.fn(),
      sidebarCollapsed: ref(false),
      mobileNavOpen: ref(false),
      paletteOpen: ref(false),
      notifOpen: ref(false),
      unread: ref(0),
      courseContext: {
        id: null,
        name: '',
        code: '',
        semester: '',
        credits: null,
        teacher: '',
        status: '',
      },
      toast: vi.fn(),
    }),
  }
})
vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: {}, params: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

import V2Topbar from '../components/V2Topbar.vue'

function mountTopbar() {
  return mount(V2Topbar, { global: { stubs: { V2Icon: true } } })
}

describe('v2 topbar AI entry', () => {
  it('学生不显示已判下线的全局 AI 助手入口', () => {
    ui.role.value = 'student'
    const wrapper = mountTopbar()
    expect(wrapper.find('button[aria-label="AI 助手"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('教师仍保留课程内 AI 助手入口', () => {
    ui.role.value = 'teacher'
    const wrapper = mountTopbar()
    expect(wrapper.find('button[aria-label="AI 助手"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
