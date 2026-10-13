import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest'

/**
 * useStudentCourseDetail 选课进度单元测试（2026-10-10，D.12.4 ⑤）
 * <p>契约事实：`course_enrollments.progress` 是 DECIMAL(5,2) 的**百分制**列（本机实库取值
 * 0.00–100.00、无 >100 行），后端 `StudentTaskCompletionComponent` 也按"0-100 百分制"写入并原样回传。
 * 因此消费侧只需取整，**不得再乘 100**——乘了会把 56.60 变成 5660，
 * 表现为 `CduHeader.vue` 的 `<el-progress>` prop 校验告警 + 页面显示"5660%"，
 * 且 `getProgressColor` 的 80/50/30 阈值恒被判为最高档。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseById: vi.fn(),
  getChapters: vi.fn(),
  getMyEnrollments: vi.fn(),
  startRecord: vi.fn(),
  getResourceList: vi.fn(),
  message: { error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() },
  route: { params: { id: '4007' } },
  mounted: null as unknown,
}))

vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))
vi.mock('@/api/course', () => ({ default: { getCourseById: mocks.getCourseById } }))
vi.mock('@/api/chapter', () => ({ default: { getChapters: mocks.getChapters } }))
vi.mock('@/api/enrollment', () => ({ default: { getMyEnrollments: mocks.getMyEnrollments } }))
vi.mock('@/api/learningRecord', () => ({ default: { startRecord: mocks.startRecord } }))
vi.mock('@/api/resource', () => ({ default: { getResourceList: mocks.getResourceList } }))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: vi.fn() },
}))

// onMounted 桩成"捕获回调"，好让用例显式驱动一次加载；其余 Vue API 保持真实实现
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onMounted: vi.fn((cb) => {
      mocks.mounted = cb
    }),
    onUnmounted: vi.fn(),
  }
})

type Fn = typeof import('@/composables/useStudentCourseDetail').useStudentCourseDetail
let useStudentCourseDetail: Fn

beforeAll(async () => {
  const mod = await import('@/composables/useStudentCourseDetail')
  useStudentCourseDetail = mod.useStudentCourseDetail
}, 60000)

/** 后端 `/enrollments/my` 的真实包装形态：{ code, data: [ {...} ] } */
function stubEnrollments(list) {
  mocks.getMyEnrollments.mockResolvedValue({ code: 200, data: list })
}

async function loadOnce() {
  const ctx = useStudentCourseDetail()
  await (mocks.mounted as () => Promise<void>)()
  return ctx
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getCourseById.mockResolvedValue({ code: 200, data: { id: 4007, name: '计算机网络示范课' } })
  mocks.getChapters.mockResolvedValue({ code: 200, data: [] })
  stubEnrollments([])
})

describe('useStudentCourseDetail 选课进度口径', () => {
  it('把后端百分制的 56.60 落成 57，而不是 5660', async () => {
    stubEnrollments([{ id: 1, courseId: 4007, progress: 56.6, isActive: true }])

    const { enrollment } = await loadOnce()

    expect(enrollment.progress).toBe(57)
    expect(enrollment.id).toBe(1)
    expect(enrollment.isActive).toBe(true)
  })

  it('进度满分 100.00 仍落在 0–100 区间（el-progress 的合法上界）', async () => {
    stubEnrollments([{ id: 2, courseId: 4007, progress: 100.0, isActive: true }])

    const { enrollment } = await loadOnce()

    expect(enrollment.progress).toBe(100)
    expect(enrollment.progress).toBeLessThanOrEqual(100)
  })

  it('课程未选按时进度为 0（阴性对照：不因缺数据而放大）', async () => {
    stubEnrollments([{ id: 3, courseId: 4001, progress: 88.5, isActive: true }])

    const { enrollment } = await loadOnce()

    expect(enrollment.progress).toBe(0)
    expect(enrollment.isActive).toBe(false)
  })

  it('进度颜色按百分制阈值分档（57 取主色而非最高档绿）', async () => {
    stubEnrollments([{ id: 1, courseId: 4007, progress: 56.6, isActive: true }])

    const { enrollment, getProgressColor } = await loadOnce()

    expect(getProgressColor(enrollment.progress)).toBe('var(--ac-primary)')
  })
})
