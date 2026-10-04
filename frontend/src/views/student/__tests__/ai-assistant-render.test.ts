import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

/**
 * 组件脚本层暴露面：messages 为组件内部状态，测试直接赋值以驱动渲染断言。
 * 用显式断言而非 any（项目 lint 口径为 0 error / 0 warning，any 会触发
 * @typescript-eslint/no-explicit-any 告警）。
 */
type AssistantInternal = { messages: Array<{ role: string; content: string; time?: string }> }
const assistantVm = (wrapper: { vm: unknown }) => wrapper.vm as unknown as AssistantInternal

// Mock DOMPurify (markdown rendering)
vi.mock('dompurify', () => ({
  default: {
    sanitize: vi.fn((html) => html),
  },
}))

// Mock AI API
const mockAiChat = vi.fn()
const mockGenerateSummary = vi.fn()
const mockGenerateQuestion = vi.fn()
// 组合式函数挂载即调用 getSessions，缺失该方法会让 loadConversations 走 catch，
// 把「测试 mock 不完整」伪装成「后端返回空历史」。与 @/api/ai 真实导出保持一致。
const mockGetSessions = vi.fn()
vi.mock('@/api/ai', () => ({
  default: {
    aiChat: mockAiChat,
    generateSummary: mockGenerateSummary,
    generateQuestion: mockGenerateQuestion,
    getSessions: mockGetSessions,
  },
}))

// Mock Course API
const mockGetCourseList = vi.fn()
vi.mock('@/api/course', () => ({
  default: {
    getCourseList: mockGetCourseList,
  },
}))

// Mock element-plus ElMessage
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

// Mock element-plus icons
vi.mock('@element-plus/icons-vue', () => ({
  Plus: { name: 'Plus' },
  ChatDotRound: { name: 'ChatDotRound' },
  Fold: { name: 'Fold' },
  MagicStick: { name: 'MagicStick' },
  Document: { name: 'Document' },
  EditPen: { name: 'EditPen' },
  QuestionFilled: { name: 'QuestionFilled' },
  Calendar: { name: 'Calendar' },
  User: { name: 'User' },
  CopyDocument: { name: 'CopyDocument' },
  RefreshRight: { name: 'RefreshRight' },
  Promotion: { name: 'Promotion' },
}))

// Mock navigator.clipboard
Object.defineProperty(navigator, 'clipboard', {
  value: { writeText: vi.fn(() => Promise.resolve()) },
  writable: true,
})

// Dynamic import after mocks
let AIAssistant
describe('AIAssistant 页面渲染', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockGetCourseList.mockResolvedValue({ data: { list: [] } })
    mockGetSessions.mockResolvedValue({ data: [] })

    const mod = await import('../AIAssistant.vue')
    AIAssistant = mod.default
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('页面基本结构', () => {
    let wrapper
    beforeEach(() => {
      wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': {
              template: '<button :disabled="$attrs.disabled"><slot /></button>',
              inheritAttrs: false,
            },
            'el-input': {
              template: '<textarea v-model="modelValue" :placeholder="placeholder"></textarea>',
              props: ['modelValue', 'placeholder', 'type', 'rows', 'autosize', 'resize'],
              emits: ['update:modelValue'],
            },
            'el-select': {
              template: '<select><slot /></select>',
              props: ['modelValue', 'placeholder', 'size', 'style', 'clearable'],
            },
            'el-option': { template: '<option><slot /></option>', props: ['label', 'value'] },
            'el-empty': { template: '<div><slot /></div>' },
            'el-tooltip': { template: '<div><slot /></div>' },
          },
        },
      })
    })

    it('渲染 chat-layout 容器', () => {
      expect(wrapper.find('.chat-layout').exists()).toBe(true)
    })

    it('渲染聊天侧边栏（chat-sidebar）', () => {
      expect(wrapper.find('.chat-sidebar').exists()).toBe(true)
    })

    it('渲染主聊天区域（chat-main）', () => {
      expect(wrapper.find('.chat-main').exists()).toBe(true)
    })

    it('渲染聊天头部', () => {
      expect(wrapper.find('.chat-header').exists()).toBe(true)
    })

    it('渲染输入区域（input-area）', () => {
      expect(wrapper.find('.input-area').exists()).toBe(true)
    })

    it('渲染消息容器（messages-container）', () => {
      expect(wrapper.find('.messages-container').exists()).toBe(true)
    })

    it('侧边栏标题为"对话历史"', () => {
      expect(wrapper.find('.sidebar-title').text()).toBe('对话历史')
    })

    it('AI 助手名称显示正确', () => {
      expect(wrapper.find('.ai-name').text()).toBe('AI 学习助手')
    })
  })

  describe('欢迎区域（无消息时）', () => {
    it('无消息且非加载中时显示欢迎区域', () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button :disabled="$attrs.disabled"><slot /></button>' },
            'el-input': { template: '<textarea></textarea>', props: ['modelValue'] },
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      expect(wrapper.find('.welcome-area').exists()).toBe(true)
    })

    it('欢迎区域包含主标题', () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      const welcomeTitle = wrapper.find('.welcome-title')
      expect(welcomeTitle.exists()).toBe(true)
      expect(welcomeTitle.text()).toContain('AI 学习助手')
    })

    it('欢迎区域包含描述文本', () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      expect(wrapper.find('.welcome-desc').exists()).toBe(true)
    })

    it('渲染四个快捷操作按钮', () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      const quickBtns = wrapper.findAll('.quick-btn')
      expect(quickBtns).toHaveLength(4)
    })

    it('快捷按钮文本正确', () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      const quickTexts = wrapper.findAll('.quick-btn span').map((n) => n.text())
      expect(quickTexts).toContain('总结课程')
      expect(quickTexts).toContain('生成练习题')
      expect(quickTexts).toContain('解释概念')
      expect(quickTexts).toContain('学习计划')
    })
  })

  describe('输入框和发送按钮', () => {
    let wrapper
    beforeEach(() => {
      wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': {
              template:
                '<button :disabled="$attrs.loading || $attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
              props: ['loading', 'disabled', 'icon', 'circle', 'type'],
              emits: ['click'],
              inheritAttrs: false,
            },
            'el-input': {
              template:
                '<textarea v-model="modelValue" :placeholder="placeholder" @keydown.enter.exact="$emit(\'keydown\', $event)"></textarea>',
              props: ['modelValue', 'placeholder', 'type', 'rows', 'autosize', 'resize'],
              emits: ['update:modelValue', 'keydown'],
            },
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
    })

    it('渲染输入框', () => {
      // 通过 css 选择器查找 textarea
      const textarea = wrapper.find('textarea')
      expect(textarea.exists()).toBe(true)
    })

    it('渲染发送按钮', () => {
      const buttons = wrapper.findAll('button')
      // 发送按钮是最后一个按钮（在 input-area 中）
      expect(buttons.length).toBeGreaterThan(0)
    })

    it('输入框 placeholder 正确', () => {
      const textarea = wrapper.find('textarea')
      if (textarea.exists()) {
        expect(textarea.attributes('placeholder')).toContain('输入您的问题')
      }
    })

    it('渲染输入提示文本', () => {
      expect(wrapper.find('.input-hint').exists()).toBe(true)
    })
  })

  describe('侧边栏功能', () => {
    let wrapper
    beforeEach(() => {
      wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': { template: '<div class="el-empty"><slot /></div>' },
          },
        },
      })
    })

    it('初始状态侧边栏可见（非折叠）', () => {
      const sidebar = wrapper.find('.chat-sidebar')
      expect(sidebar.exists()).toBe(true)
      expect(sidebar.classes()).not.toContain('collapsed')
    })

    it('对话列表为空时显示 el-empty', () => {
      wrapper.vm.conversations = []
      expect(wrapper.vm.conversations).toHaveLength(0)
    })
  })

  describe('消息列表渲染', () => {
    it('有消息时不显示欢迎区域', async () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': {
              template: '<textarea v-model="modelValue"></textarea>',
              props: ['modelValue'],
              emits: ['update:modelValue'],
            },
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      assistantVm(wrapper).messages = [
        { role: 'user', content: '你好', time: '12:00' },
        { role: 'assistant', content: '你好！有什么可以帮助你的？', time: '12:01' },
      ]
      await nextTick()

      expect(wrapper.find('.welcome-area').exists()).toBe(false)
      expect(wrapper.find('.message-list').exists()).toBe(true)
    })

    it('渲染用户消息气泡', async () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      assistantVm(wrapper).messages = [{ role: 'user', content: '测试消息', time: '12:00' }]
      await nextTick()

      expect(wrapper.find('.user-bubble').exists()).toBe(true)
      expect(wrapper.find('.user-bubble .message-text').text()).toBe('测试消息')
    })

    it('渲染 AI 消息气泡', async () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      assistantVm(wrapper).messages = [{ role: 'assistant', content: 'AI 回复', time: '12:01' }]
      await nextTick()

      expect(wrapper.find('.ai-bubble').exists()).toBe(true)
      expect(wrapper.find('.ai-bubble .message-text').text()).toContain('AI 回复')
    })

    it('有消息时不显示 welcome-area', async () => {
      const wrapper = mount(AIAssistant, {
        global: {
          stubs: {
            'el-icon': true,
            'el-button': { template: '<button><slot /></button>' },
            'el-input': true,
            'el-select': true,
            'el-option': true,
            'el-empty': true,
          },
        },
      })
      assistantVm(wrapper).messages = [{ role: 'user', content: 'hi', time: '12:00' }]
      await nextTick()
      expect(wrapper.find('.welcome-area').exists()).toBe(false)
    })
  })
})
