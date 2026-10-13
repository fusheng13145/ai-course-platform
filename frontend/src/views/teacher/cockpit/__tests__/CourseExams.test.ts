import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseExams.vue（课程试卷）— 加载失败可见（2026-10-13）
 * <p>原实现试卷列表加载失败仅 logger.error 后静默置空 →
 * 页面显示「暂无试卷，点击右上角新建」，教师会误判该课程确实没有试卷。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllExamPapersByCourse: vi.fn(),
  createExamPaper: vi.fn(),
  updateExamPaper: vi.fn(),
  publishExam: vi.fn(),
  deleteExamPaper: vi.fn(),
  copyExam: vi.fn(),
  getExamQuestions: vi.fn(),
  getExamStatistics: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/exam', () => ({
  default: {
    getAllExamPapersByCourse: mocks.getAllExamPapersByCourse,
    createExamPaper: mocks.createExamPaper,
    updateExamPaper: mocks.updateExamPaper,
    publishExam: mocks.publishExam,
    deleteExamPaper: mocks.deleteExamPaper,
    copyExam: mocks.copyExam,
    getExamQuestions: mocks.getExamQuestions,
    getExamStatistics: mocks.getExamStatistics,
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

import CourseExams from '@/views/teacher/cockpit/CourseExams.vue'

function mountPage() {
  return mount(CourseExams, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        CeExamTable: {
          props: {
            list: Array,
            total: Number,
            page: Number,
            size: Number,
            emptyText: String,
          },
          template: '<div class="ce-stub">{{ emptyText }}</div>',
        },
        'el-button': true,
        'el-icon': true,
        'el-table': true,
        'el-table-column': true,
        'el-tag': true,
        'el-dialog': true,
        'el-empty': true,
        'el-pagination': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseExams — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('试卷列表加载失败时必须给出持久提示，且不得表现为「暂无试卷」', async () => {
    mocks.getAllExamPapersByCourse.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getAllExamPapersByCourse).toHaveBeenCalledWith(4007)
    expect(wrapper.find('.alert-stub').text()).toBe('试卷列表加载失败，请检查网络后重试')
    expect(wrapper.find('.ce-stub').text()).toBe('试卷列表未能加载，请查看上方提示后重试')
  })

  it('阴性对照：加载成功（空数组）时显示「暂无试卷」且无失败提示', async () => {
    mocks.getAllExamPapersByCourse.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.ce-stub').text()).toBe('暂无试卷，点击右上角新建')
  })

  it('失败后再成功：提示必须被清空（每次 load 都复位，不是一次失败永久残留）', async () => {
    mocks.getAllExamPapersByCourse
      .mockRejectedValueOnce(new Error('500'))
      .mockResolvedValue([{ id: 1, title: '试卷一' }])
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.find('.ce-stub').text()).toBe('暂无试卷，点击右上角新建')
  })
})
