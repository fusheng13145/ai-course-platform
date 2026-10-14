import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * TaskDrawer.vue（学生任务点抽屉）— 加载失败可见（2026-10-14，批次 18）
 *
 * <p>原实现 openTask 失败仅 logger.warn 静默：视频/测验内容拉取失败后抽屉内一片空白，
 * 学生看不出是「内容为空」还是「加载失败」。现补 loadError + 抽屉内持久 el-alert。</p>
 */

const mocks = vi.hoisted(() => ({
  loadVideo: vi.fn(),
  loadQuiz: vi.fn(),
  markTaskComplete: vi.fn(),
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/api/student', () => ({
  default: { markTaskComplete: mocks.markTaskComplete },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))
vi.mock('@element-plus/icons-vue', () => ({
  Select: { name: 'Select' },
  VideoPlay: { name: 'VideoPlay' },
  Files: { name: 'Files' },
  InfoFilled: { name: 'InfoFilled' },
  Check: { name: 'Check' },
  Close: { name: 'Close' },
}))

vi.mock('@/composables/useVideoTask', async () => {
  const { ref } = await import('vue')
  return {
    useVideoTask: () => ({
      videoEl: ref(null),
      videoSrc: ref(''),
      videoDuration: ref(0),
      localWatched: ref(0),
      videoState: ref({ completed: false }),
      loadVideo: mocks.loadVideo,
      onVideoMeta: vi.fn(),
      onVideoTime: vi.fn(),
      onVideoEnded: vi.fn(),
      markVideoFallback: vi.fn(),
      resetVideo: vi.fn(),
    }),
  }
})
vi.mock('@/composables/useQuizTask', async () => {
  const { ref } = await import('vue')
  return {
    useQuizTask: () => ({
      quizQuestions: ref([]),
      quizAnswers: ref({}),
      quizSubmitting: ref(false),
      quizResult: ref(null),
      loadQuiz: mocks.loadQuiz,
      submitQuizNow: vi.fn(),
      feedbackOf: vi.fn(() => ({ correct: true, correctAnswer: '', explanation: '' })),
      resetQuiz: vi.fn(),
      letter: vi.fn((i) => String.fromCharCode(65 + i)),
      isSingle: vi.fn(() => true),
      isMultiple: vi.fn(() => false),
      isJudge: vi.fn(() => false),
      qTypeLabel: vi.fn(() => '单选题'),
      qTypeClass: vi.fn(() => 't-single'),
    }),
  }
})

import TaskDrawer from '@/views/student/course/components/TaskDrawer.vue'

const VIDEO_TASK = { id: 1, taskType: 'VIDEO', title: '视频1', completed: false }

function mountPage() {
  return mount(TaskDrawer, {
    props: { modelValue: true, task: null, courseId: '5' },
    shallow: true,
    global: {
      // el-drawer 被浅挂载为 stub，需显式让其渲染默认插槽才能断言抽屉内容
      renderStubDefaultSlot: true,
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.loadVideo.mockResolvedValue(undefined)
  mocks.loadQuiz.mockResolvedValue(undefined)
  mocks.markTaskComplete.mockResolvedValue(undefined)
})

describe('TaskDrawer 任务点抽屉 — 加载失败可见', () => {
  it('加载失败：抽屉内给出持久提示', async () => {
    mocks.loadVideo.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await wrapper.setProps({ task: VIDEO_TASK })
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.alert-stub').text()).toContain('任务点内容加载失败，请检查网络后重试')
  })

  it('阴性对照：加载成功时无提示，任务点内容正常渲染', async () => {
    const wrapper = mountPage()
    await wrapper.setProps({ task: VIDEO_TASK })
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('该视频任务点暂未配置视频资源')
  })

  it('失败后再成功：提示必须被清空（每次 openTask 复位）', async () => {
    mocks.loadVideo.mockRejectedValueOnce(new Error('500'))
    const first = mountPage()
    await first.setProps({ task: VIDEO_TASK })
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await second.setProps({ task: VIDEO_TASK })
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).toContain('该视频任务点暂未配置视频资源')
  })
})
