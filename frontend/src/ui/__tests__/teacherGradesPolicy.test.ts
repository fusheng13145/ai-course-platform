import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const gradeApi = vi.hoisted(() => ({
  getGradePolicy: vi.fn(),
  saveGradePolicy: vi.fn(),
}))
vi.mock('@/api/grade', () => ({ ...gradeApi, default: gradeApi }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/composables/useTeacherGrades', async () => {
  const { ref } = await import('vue')
  return {
    useTeacherGrades: () => ({
      loading: ref(false),
      searchText: ref(''),
      selectedCourseId: ref(1),
      courseList: ref([{ id: 1, name: '计算机网络' }]),
      overview: ref({ avgScore: null, maxScore: null, minScore: null, pendingCount: null }),
      gradeList: ref([]),
      pendingTasks: ref([]),
      pendingUnavailable: ref(false),
      loadNotice: ref(''),
      detailDialogVisible: ref(false),
      currentStudent: ref(null),
      filteredGradeList: ref([]),
      distributionOption: ref(null),
      hasDistribution: ref(false),
      loadCourseOptions: vi.fn(),
      loadGradeData: vi.fn(),
      refresh: vi.fn(),
      handleExport: vi.fn(),
      viewStudentDetail: vi.fn(),
      goToGrading: vi.fn(),
      getGradeTagType: vi.fn(),
    }),
  }
})

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import TeacherGradesView from '../views/teacher/TeacherGradesView.vue'

const stubs = {
  Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

function footerButtons(wrapper: ReturnType<typeof mount>) {
  return wrapper.find('.modal-stub').findAll('button')
}

beforeEach(() => {
  vi.clearAllMocks()
  gradeApi.getGradePolicy.mockResolvedValue(
    envelope({
      name: '默认方案',
      components: [
        { key: 'HOMEWORK', name: '作业', weight: 40 },
        { key: 'EXAM', name: '考试', weight: 60 },
      ],
    }),
  )
  gradeApi.saveGradePolicy.mockResolvedValue(envelope({}))
})

describe('教师成绩方案', () => {
  it('读取现有方案后按 100% 校验保存', async () => {
    const wrapper = mount(TeacherGradesView, { global: { stubs } })
    await flushPromises()

    await wrapper
      .findAll('.page-head-actions button')
      .find((node) => node.text() === '成绩方案')!
      .trigger('click')
    await flushPromises()

    expect(gradeApi.getGradePolicy).toHaveBeenCalledWith(1)
    expect(wrapper.find('[data-testid="policy-name-0"]').element).toHaveProperty('value', '作业')

    await wrapper.find('[data-testid="policy-weight-0"]').setValue(50)
    let buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(gradeApi.saveGradePolicy).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('权重合计须为 100%', 'warn')

    await wrapper.find('[data-testid="policy-weight-0"]').setValue(40)
    buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(gradeApi.saveGradePolicy).toHaveBeenCalledWith({
      courseId: 1,
      name: '默认方案',
      components: [
        { key: 'HOMEWORK', name: '作业', weight: 40, source: 'manual' },
        { key: 'EXAM', name: '考试', weight: 60, source: 'manual' },
      ],
    })
    expect(toast).toHaveBeenCalledWith('成绩方案已保存', 'ok')
    wrapper.unmount()
  })
})
