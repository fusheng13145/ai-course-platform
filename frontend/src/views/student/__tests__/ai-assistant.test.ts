/**
 * AI 学习助手问答流程功能测试
 *
 * 挂载真实 AIAssistant.vue 组件，通过 DOM 交互驱动发送流程，
 * 覆盖真实实现路径：
 *  - qaStream（SSE 流式，onToken/onDone 回调）
 *  - qaAsk（非流式兜底，读取信封 data.answer + data.sources 后逐字渲染）
 *  - 空消息 / loading 重复发送拦截
 *  - 会话历史与课程列表加载
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// 组件按 /student/courses/:id/ai 路由预选课程；测试环境无 router，mock 空路由
vi.mock('vue-router', () => ({ useRoute: () => ({ params: {}, query: {} }) }))

/**
 * 组件脚本层暴露面：messages / loading / conversations / courseList 为组件内部状态，
 * 测试直接读取与赋值以驱动断言。显式断言而非 any（lint 口径 0 error / 0 warning）。
 */
type QaSource = {
  documentId?: number
  documentName?: string
  relevanceScore?: number
  snippet?: string
}

type AssistantInternal = {
  messages: Array<{
    role: string
    content: string
    time?: string
    error?: boolean
    loading?: boolean
    sources?: QaSource[]
  }>
  loading: boolean
  selectedCourseId: number | null
  conversations: Array<{ id: number; title: string }>
  courseList: Array<{ name: string }>
  handleGenerateQuestion: () => Promise<void>
}
const assistantVm = (wrapper: { vm: unknown }) => wrapper.vm as unknown as AssistantInternal

// ---- 模块级 mock：导出与真实 @/api/ai 完全一致（qaAsk/qaStream/getSessions/...）----
const { mockQaAsk, mockGetSessions, mockQaStream, mockGetAllCourses, mockQuestionRecommendations } =
  vi.hoisted(() => ({
    mockQaAsk: vi.fn(),
    mockGetSessions: vi.fn(),
    mockQaStream: vi.fn(),
    mockGetAllCourses: vi.fn(),
    mockQuestionRecommendations: vi.fn(),
  }))

vi.mock('@/api/ai', () => ({
  default: {
    qaAsk: mockQaAsk,
    qaContinue: vi.fn(),
    getSessions: mockGetSessions,
    qaStream: mockQaStream,
    essayCorrect: vi.fn(),
    essayPolish: vi.fn(),
    learningRecommendations: vi.fn(),
    questionRecommendations: mockQuestionRecommendations,
    weakPointsAnalysis: vi.fn(),
    learningAdvice: vi.fn(),
    generateLearningPath: vi.fn(),
  },
}))

vi.mock('@/api/course', () => ({
  default: { getAllCourses: mockGetAllCourses },
}))

// 组件仅从 element-plus 导入 ElMessage，el-* 组件由下方 mount stubs 提供
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

// 图标仅作占位（与渲染测试同款做法）
vi.mock('@element-plus/icons-vue', () => {
  const stub = (name) => ({ name, render: () => null })
  return {
    Plus: stub('Plus'),
    ChatDotRound: stub('ChatDotRound'),
    Fold: stub('Fold'),
    MagicStick: stub('MagicStick'),
    Document: stub('Document'),
    EditPen: stub('EditPen'),
    QuestionFilled: stub('QuestionFilled'),
    Calendar: stub('Calendar'),
    User: stub('User'),
    CopyDocument: stub('CopyDocument'),
    RefreshRight: stub('RefreshRight'),
    Promotion: stub('Promotion'),
  }
})

let AIAssistant

// el-* 组件桩：el-input 需支持双向绑定并把 keydown 监听透传到 textarea
const stubs = {
  'el-icon': { template: '<span class="stub-icon"><slot /></span>' },
  'el-button': {
    name: 'ElButton',
    props: ['type', 'icon', 'circle', 'loading', 'disabled', 'text', 'size'],
    emits: ['click'],
    // 保持默认 inheritAttrs=true：模板上的 class（如 send-btn）会透传合并到桩根元素
    template:
      '<button class="stub-btn" :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
  },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    // 保持 inheritAttrs 默认透传：keydown.enter 监听落到 textarea 上
    template:
      '<textarea data-test="ai-input" :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>',
  },
  'el-select': {
    name: 'ElSelect',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  },
  'el-option': {
    name: 'ElOption',
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  'el-empty': { template: '<div class="stub-empty"><slot /></div>' },
}

function mountAssistant() {
  return mount(AIAssistant, { global: { stubs } })
}

/** 输入消息并点击发送按钮 */
async function sendMessage(wrapper, text) {
  await wrapper.find('[data-test="ai-input"]').setValue(text)
  await wrapper.find('.stub-btn.send-btn').trigger('click')
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

describe('AI 学习助手问答流程（挂载真实组件）', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    mockGetAllCourses.mockResolvedValue([])
    mockGetSessions.mockResolvedValue({ data: { list: [] } })
    // 动态导入，避免 vi.mock 提升导致的 TDZ 问题
    const mod = await import('../AIAssistant.vue')
    AIAssistant = mod.default
  })

  it('发送消息后追加 user + assistant 消息（SSE 流式路径）', async () => {
    mockQaStream.mockImplementation((params, cb) => {
      cb.onToken('你')
      cb.onToken('好')
      cb.onDone()
      return { abort: vi.fn() }
    })

    const wrapper = mountAssistant()
    await sendMessage(wrapper, '什么是TCP？')
    await flushPromises()

    expect(assistantVm(wrapper).messages).toHaveLength(2)
    expect(assistantVm(wrapper).messages[0].role).toBe('user')
    expect(assistantVm(wrapper).messages[0].content).toBe('什么是TCP？')
    expect(assistantVm(wrapper).messages[1].role).toBe('assistant')
    expect(assistantVm(wrapper).messages[1].content).toBe('你好')
    expect(assistantVm(wrapper).loading).toBe(false)
    expect(mockQaStream).toHaveBeenCalledWith(
      { question: '什么是TCP？' },
      expect.objectContaining({ onToken: expect.any(Function), onDone: expect.any(Function) }),
    )
    // 流式成功时不应走非流式兜底
    expect(mockQaAsk).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('空白消息不应发送', async () => {
    const wrapper = mountAssistant()
    await wrapper.find('[data-test="ai-input"]').setValue('   ')
    await wrapper.find('.stub-btn.send-btn').trigger('click')
    await flushPromises()

    expect(assistantVm(wrapper).messages).toHaveLength(0)
    expect(mockQaStream).not.toHaveBeenCalled()
    expect(mockQaAsk).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loading 期间重复点击不会重复发送', async () => {
    mockQaStream.mockImplementation(() => ({ abort: vi.fn() })) // 流挂起，loading 保持 true

    const wrapper = mountAssistant()
    await sendMessage(wrapper, '第一个问题')
    await flushPromises()
    await sendMessage(wrapper, '第二个问题')
    await flushPromises()

    expect(mockQaStream).toHaveBeenCalledTimes(1)
    expect(assistantVm(wrapper).messages).toHaveLength(2) // 仅第一次的 user + assistant 占位
    expect(assistantVm(wrapper).loading).toBe(true)
    wrapper.unmount()
  })

  it('SSE 流式失败时降级为非流式 qaAsk，渲染 data.answer 并带上检索出处', async () => {
    mockQaStream.mockImplementation((params, cb) => {
      cb.onError(new Error('SSE 不可用'))
      return { abort: vi.fn() }
    })
    // 真实信封形状：request.ts 拦截器返回 { code, message, data }，业务字段在 data 里
    mockQaAsk.mockResolvedValue({
      code: 200,
      data: {
        sessionId: 'sess-1',
        answer: 'TCP 通过三次握手建立连接。',
        sources: [
          {
            documentId: 9,
            documentName: '第6章 传输层',
            relevanceScore: 0.9937,
            snippet: '同步初始序号 ISN',
          },
        ],
      },
    })

    const wrapper = mountAssistant()
    await sendMessage(wrapper, '什么是TCP？')
    await flushPromises()
    await sleep(600) // 逐字渲染完成后才挂出处（15 字 × 20ms + 余量）

    expect(mockQaAsk).toHaveBeenCalledWith({ question: '什么是TCP？' })
    expect(assistantVm(wrapper).messages[1].content).toBe('TCP 通过三次握手建立连接。')
    expect(assistantVm(wrapper).messages[1].sources).toEqual([
      {
        documentId: 9,
        documentName: '第6章 传输层',
        relevanceScore: 0.9937,
        snippet: '同步初始序号 ISN',
      },
    ])
    expect(assistantVm(wrapper).loading).toBe(false)
    wrapper.unmount()
  })

  it('缺陷回归 DR-41：只有 response/content 旧字段时如实提示，不用假文案伪装成功', async () => {
    // 后端从不返回 response/content；此前按这些字段取值会永远取不到，
    // 于是把「感谢您的提问！」这类固定文案当成回答播出，真实 RAG 结果被静默丢弃。
    mockQaStream.mockImplementation((params, cb) => {
      cb.onError(new Error('SSE 不可用'))
      return { abort: vi.fn() }
    })
    mockQaAsk.mockResolvedValue({
      code: 200,
      data: { sessionId: 'sess-1', response: '旧字段', content: '旧字段' },
    })

    const wrapper = mountAssistant()
    await sendMessage(wrapper, '什么是TCP？')
    await flushPromises()

    expect(assistantVm(wrapper).messages[1].content).toBe('AI 未返回内容，请稍后再试。')
    expect(assistantVm(wrapper).messages[1].error).toBe(true)
    wrapper.unmount()
  })

  it('answer 的展示层转义在渲染前还原，不把 &lt; 直接播给用户', async () => {
    mockQaStream.mockImplementation((params, cb) => {
      cb.onError(new Error('SSE 不可用'))
      return { abort: vi.fn() }
    })
    mockQaAsk.mockResolvedValue({
      code: 200,
      data: {
        sessionId: 'sess-1',
        answer: '若 &lt;span&gt; 未闭合，浏览器会&#39;自动补全&amp;纠正',
        sources: [{ documentId: 3, documentName: '&lt;脚本&gt;危险标题', snippet: '&amp; 号' }],
      },
    })

    const wrapper = mountAssistant()
    await sendMessage(wrapper, 'HTML 实体')
    await flushPromises()
    await sleep(900)

    expect(assistantVm(wrapper).messages[1].content).toBe(
      "若 <span> 未闭合，浏览器会'自动补全&纠正",
    )
    expect(assistantVm(wrapper).messages[1].sources[0].documentName).toBe('<脚本>危险标题')
    expect(assistantVm(wrapper).messages[1].sources[0].snippet).toBe('& 号')
    wrapper.unmount()
  })

  it('生成练习题读取 recommendations 业务字段，不再把整个 JSON 当回答播出', async () => {
    mockQuestionRecommendations.mockResolvedValue({
      code: 200,
      data: {
        courseId: 4007,
        difficulty: 'MEDIUM',
        recommendations: [
          { content: '下列哪个协议工作在传输层？', type: 'SINGLE_CHOICE', difficulty: 'MEDIUM' },
          {
            content: 'IP 数据报首部校验和覆盖哪些字段？',
            type: 'SINGLE_CHOICE',
            difficulty: 'HARD',
          },
        ],
      },
    })

    const wrapper = mountAssistant()
    assistantVm(wrapper).selectedCourseId = 4007
    await assistantVm(wrapper).handleGenerateQuestion()
    await flushPromises()
    await sleep(600)

    expect(mockQuestionRecommendations).toHaveBeenCalledWith({ courseId: 4007 })
    expect(assistantVm(wrapper).messages[1].content).toBe(
      '1. 下列哪个协议工作在传输层？\n\n2. IP 数据报首部校验和覆盖哪些字段？',
    )
    wrapper.unmount()
  })

  it('SSE 与 qaAsk 均失败时显示错误占位消息', async () => {
    mockQaStream.mockImplementation((params, cb) => {
      cb.onError(new Error('SSE 不可用'))
      return { abort: vi.fn() }
    })
    mockQaAsk.mockRejectedValue(new Error('网络异常'))

    const wrapper = mountAssistant()
    await sendMessage(wrapper, '问题')
    await flushPromises()

    expect(assistantVm(wrapper).messages).toHaveLength(2)
    expect(assistantVm(wrapper).messages[1].content).toBe('抱歉，AI 服务暂时不可用，请稍后再试。')
    expect(assistantVm(wrapper).messages[1].error).toBe(true)
    expect(assistantVm(wrapper).loading).toBe(false)
    wrapper.unmount()
  })

  it('加载对话历史并映射字段（无标题时生成默认标题）', async () => {
    mockGetSessions.mockResolvedValue({
      data: {
        list: [
          { id: 1, title: 'TCP 三次握手', updatedAt: '2026-07-30T10:00:00.000Z' },
          { id: 2, createdAt: '2026-07-30T08:00:00.000Z' },
        ],
      },
    })

    const wrapper = mountAssistant()
    await flushPromises()

    expect(assistantVm(wrapper).conversations).toHaveLength(2)
    expect(assistantVm(wrapper).conversations[0].id).toBe(1)
    expect(assistantVm(wrapper).conversations[0].title).toBe('TCP 三次握手')
    expect(assistantVm(wrapper).conversations[1].title.startsWith('对话')).toBe(true)
    wrapper.unmount()
  })

  it('list 为空数组、历史在 records 时不得被吞成「无历史」', async () => {
    // 缺陷回归（2026-10-04，P1-10 同形）：`res.data?.list || res.data?.records`
    // 里空数组 truthy，后端换成 records 形状后历史会话整批消失且无任何报错。
    mockGetSessions.mockResolvedValue({
      data: { list: [], records: [{ sessionId: 9, title: '操作系统分页' }] },
    })

    const wrapper = mountAssistant()
    await flushPromises()

    expect(assistantVm(wrapper).conversations).toHaveLength(1)
    expect(assistantVm(wrapper).conversations[0].id).toBe(9)
    wrapper.unmount()
  })

  it('课程列表不会在前 10 门处被静默截断', async () => {
    mockGetAllCourses.mockResolvedValue(
      Array.from({ length: 15 }, (_, i) => ({ id: i + 1, name: `课程${i + 1}` })),
    )

    const wrapper = mountAssistant()
    await flushPromises()

    expect(assistantVm(wrapper).courseList).toHaveLength(15)
    expect(assistantVm(wrapper).courseList[0].name).toBe('课程1')
    wrapper.unmount()
  })

  it('新建对话会清空当前消息', async () => {
    const wrapper = mountAssistant()
    assistantVm(wrapper).messages = [{ role: 'user', content: '旧消息', time: '10:00' }]
    await flushPromises()

    const newBtn = wrapper.findAll('.stub-btn').find((b) => b.text().includes('新建'))
    expect(newBtn).toBeTruthy()
    await newBtn.trigger('click')

    expect(assistantVm(wrapper).messages).toHaveLength(0)
    wrapper.unmount()
  })
})
