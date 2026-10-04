import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useGrades（管理员成绩管理页逻辑）单元测试
 *
 * <p>本 composable 此前 0% 覆盖，且带一个真实缺陷（P0-1，2026-10-04）：
 * 组装了 `params`（页码/课程/学期/关键词）却调用 `getMyExamSessions()` **不传参**，
 * 导致筛选与分页整体失效；同时 `pagination.total = data.length`（当前页长度），
 * 第 2 页只剩 3 条时总数就变成 3。这里把两条都钉成回归用例。</p>
 *
 * <p>另锁定「异常成绩」口径：列表筛选与统计卡片必须共用同一判定，且未批阅
 * （score=null）不得被当成异常（JS 里 `null < 45` 会被强制成 0）。</p>
 */

const mocks = vi.hoisted(() => ({
  getMyExamSessions: vi.fn(),
  getCourseList: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/exam', () => ({ default: { getMyExamSessions: mocks.getMyExamSessions } }))
vi.mock('@/api/course', () => ({ default: { getCourseList: mocks.getCourseList } }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
// xlsx 只在导出路径使用，桩掉以免拖入真实打包库
vi.mock('xlsx', () => ({ utils: { book_new: vi.fn(), json_to_sheet: vi.fn() }, write: vi.fn() }))

import { useGrades, isAbnormalGrade } from '@/composables/useGrades'

/**
 * jsdom 不提供 window.matchMedia，而 useGrades 构造图表配置时会经
 * chartTheme.resolveTheme 读取系统偏好（与 chartTheme.test.ts 同一桩法）。
 */
window.matchMedia = vi.fn().mockImplementation((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
})) as unknown as typeof window.matchMedia

function withSetup() {
  let state!: ReturnType<typeof useGrades>
  mount(
    defineComponent({
      setup() {
        state = useGrades()
        return () => h('div')
      },
    }),
  )
  return { state }
}

const statValue = (state: ReturnType<typeof useGrades>, label: string) =>
  state.overviewStats.value.find((s: { label: string }) => s.label === label)?.value

describe('useGrades — 请求参数与分页（P0-1 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseList.mockResolvedValue({ data: [] })
  })

  it('筛选条件必须真正进入请求参数，而不是组装后被丢弃', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: { list: [], total: 0 } })
    const { state } = withSetup()

    state.pagination.page = 3
    state.pagination.size = 20
    state.filters.courseId = '7'
    state.filters.semester = '2026春'
    state.filters.keyword = '张'

    await state.fetchGrades()

    expect(mocks.getMyExamSessions).toHaveBeenCalledWith({
      page: 3,
      size: 20,
      courseId: '7',
      semester: '2026春',
      keyword: '张',
    })
  })

  it('未填筛选条件时不得带上空的 courseId/semester/keyword 键', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: { list: [], total: 0 } })
    const { state } = withSetup()

    await state.fetchGrades()

    const sent = mocks.getMyExamSessions.mock.calls[0][0]
    expect(sent).toEqual({ page: 1, size: 10 })
    expect('courseId' in sent).toBe(false)
  })

  it('后端回传 total 时以后端为准，绝不用当前页长度覆盖', async () => {
    // 第 2 页只有 3 条，但真实总数是 57 —— 旧实现会把 total 变成 3
    mocks.getMyExamSessions.mockResolvedValue({
      data: { list: [{ score: 70 }, { score: 80 }, { score: 90 }], total: 57 },
    })
    const { state } = withSetup()
    state.pagination.page = 2

    await state.fetchGrades()

    expect(state.pagination.total).toBe(57)
    expect(state.gradeList.value).toHaveLength(3)
  })

  it('后端返回全量裸数组时，本地筛选 + 分页切片必须同时生效', async () => {
    const all = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1,
      courseId: i % 2 === 0 ? 1 : 2,
      score: 60 + (i % 30),
    }))
    mocks.getMyExamSessions.mockResolvedValue({ data: all })
    const { state } = withSetup()

    state.filters.courseId = '1'
    state.pagination.page = 2
    state.pagination.size = 5
    await state.fetchGrades()

    // courseId=1 共 13 条 -> total 是筛选后的完整条数，不是本页长度
    expect(state.pagination.total).toBe(13)
    expect(state.gradeList.value).toHaveLength(5)
    expect(state.gradeList.value.every((g: { courseId: number }) => g.courseId === 1)).toBe(true)
    // 第 2 页首条应是筛选结果的第 6 条（id=11）
    expect(state.gradeList.value[0].id).toBe(11)
  })

  it('超末页时列表为空但 total 不变（翻页控件不会误判）', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: [{ id: 1, score: 70 }] })
    const { state } = withSetup()
    state.pagination.page = 99

    await state.fetchGrades()

    expect(state.gradeList.value).toEqual([])
    expect(state.pagination.total).toBe(1)
  })

  it('课程下拉按后端 1 基口径请求 page=1', async () => {
    mocks.getCourseList.mockResolvedValue({ data: { records: [{ id: 1 }] } })
    const { state } = withSetup()

    await state.fetchCourses()

    expect(mocks.getCourseList).toHaveBeenCalledWith({ page: 1, size: 100 })
    expect(state.courseList.value).toEqual([{ id: 1 }])
  })
})

describe('useGrades — 响应信封兼容与失败降级', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    ['裸数组', { data: [{ score: 60 }] }],
    ['{list}', { data: { list: [{ score: 60 }], total: 1 } }],
    ['{records}', { data: { records: [{ score: 60 }], total: 1 } }],
    ['{grades}', { data: { grades: [{ score: 60 }] } }],
    ['{items}', { data: { items: [{ score: 60 }] } }],
    ['axios 顶层即数据体', { list: [{ score: 60 }], total: 1 }],
  ])('%s 形状都能被解析为 1 条', async (_name, payload) => {
    mocks.getMyExamSessions.mockResolvedValue(payload)
    const { state } = withSetup()

    await state.fetchGrades()

    expect(state.gradeList.value).toHaveLength(1)
  })

  it('无法识别的形状降级为空列表而不是抛错', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: { unexpected: true } })
    const { state } = withSetup()

    await expect(state.fetchGrades()).resolves.toBeUndefined()
    expect(state.gradeList.value).toEqual([])
  })

  it('请求失败清空列表与总数，并结束 loading', async () => {
    mocks.getMyExamSessions.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await state.fetchGrades()

    expect(state.gradeList.value).toEqual([])
    expect(state.pagination.total).toBe(0)
    expect(state.loading.value).toBe(false)
  })

  it('空数据时统计卡保持占位符，不出现 NaN/Infinity（除零回归）', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: [] })
    const { state } = withSetup()

    await state.fetchGrades()

    expect(statValue(state, '平均分')).toBe('--')
    expect(statValue(state, '及格率')).toBe('--')
    expect(statValue(state, '标准差')).toBe('--')
  })

  it('全部未批阅（score 均为 null）时同样不得除零', async () => {
    mocks.getMyExamSessions.mockResolvedValue({
      data: { list: [{ score: null }, { score: undefined }], total: 2 },
    })
    const { state } = withSetup()

    await state.fetchGrades()

    expect(statValue(state, '平均分')).toBe('--')
    expect(statValue(state, '及格率')).toBe('--')
  })

  it('平均分/标准差/及格率按数值正确计算', async () => {
    mocks.getMyExamSessions.mockResolvedValue({
      data: { list: [{ score: 60 }, { score: 80 }, { score: 55 }, { score: 90 }], total: 4 },
    })
    const { state } = withSetup()

    await state.fetchGrades()

    expect(statValue(state, '平均分')).toBe(71.25)
    // 总体标准差 = sqrt(((60-71.25)^2+(80-71.25)^2+(55-71.25)^2+(90-71.25)^2)/4) = 14.3
    expect(statValue(state, '标准差')).toBe('14.3')
    expect(statValue(state, '及格率')).toBe('75.0%')
  })

  it('成绩分布按五档计数且只统计有效分数', async () => {
    mocks.getMyExamSessions.mockResolvedValue({
      data: {
        list: [
          { score: 59 },
          { score: 60 },
          { score: 70 },
          { score: 80 },
          { score: 90 },
          { score: null },
        ],
        total: 6,
      },
    })
    const { state } = withSetup()

    await state.fetchGrades()

    expect(state.distributionOption.value.series[0].data).toEqual([1, 1, 1, 1, 1])
  })
})

describe('useGrades — 异常成绩口径统一', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('isAbnormalGrade：<45 与 ≥98 为异常，边界 45/97 正常，null 不算异常', () => {
    expect(isAbnormalGrade({ score: 44.9 })).toBe(true)
    expect(isAbnormalGrade({ score: 45 })).toBe(false)
    expect(isAbnormalGrade({ score: 97.9 })).toBe(false)
    expect(isAbnormalGrade({ score: 98 })).toBe(true)
    expect(isAbnormalGrade({ score: null })).toBe(false)
    expect(isAbnormalGrade({})).toBe(false)
    expect(isAbnormalGrade({ score: 70, isAbnormal: true })).toBe(true)
    expect(isAbnormalGrade(null)).toBe(false)
  })

  it('「仅看异常」筛出的行必须与异常数卡片完全一致（同一口径）', async () => {
    mocks.getMyExamSessions.mockResolvedValue({
      data: [
        { id: 1, score: 20 }, // 异常（<45）
        { id: 2, score: 100 }, // 异常（≥98）
        { id: 3, score: null }, // 未批阅，不得算异常
        { id: 4, score: 70 }, // 正常
        { id: 5, score: 50, isAbnormal: true }, // 后端标记
      ],
    })
    const { state } = withSetup()

    await state.fetchGrades()
    const abnormalIds = state.abnormalGrades.value.map((g: { id: number }) => g.id)
    expect(abnormalIds).toEqual([1, 2, 5])
    expect(statValue(state, '异常数')).toBe(3)

    // 打开「仅看异常」后重取，列表行必须与卡片同源
    state.showAbnormalOnly.value = true
    await state.fetchGrades()
    expect(state.gradeList.value.map((g: { id: number }) => g.id)).toEqual(abnormalIds)
  })

  it('resetFilters 清空所有条件并重新加载', async () => {
    mocks.getMyExamSessions.mockResolvedValue({ data: [] })
    const { state } = withSetup()
    state.filters.courseId = '7'
    state.filters.keyword = 'x'
    state.showAbnormalOnly.value = true
    mocks.getMyExamSessions.mockClear()

    state.resetFilters()
    await vi.waitFor(() => expect(mocks.getMyExamSessions).toHaveBeenCalled())

    expect(state.filters).toEqual({ courseId: '', semester: '', keyword: '' })
    expect(state.showAbnormalOnly.value).toBe(false)
    expect(mocks.getMyExamSessions.mock.calls[0][0]).toEqual({ page: 1, size: 10 })
  })
})

describe('useGrades — 展示辅助与详情', () => {
  it('getScoreClass 覆盖各分数段与非法输入', () => {
    const { state } = withSetup()
    expect(state.getScoreClass(95)).toBe('excellent')
    expect(state.getScoreClass(85)).toBe('good')
    expect(state.getScoreClass(75)).toBe('medium')
    expect(state.getScoreClass(60)).toBe('pass')
    expect(state.getScoreClass(59)).toBe('fail')
    expect(state.getScoreClass(null)).toBe('')
    expect(state.getScoreClass('abc')).toBe('')
  })

  it('viewGradeDetail 打开详情并带入行数据', () => {
    const { state } = withSetup()
    const row = { id: 9, score: 88 }

    state.viewGradeDetail(row)

    expect(state.showDetailDialog.value).toBe(true)
    // ref 会把对象包成 reactive 代理，引用不相等，按值断言
    expect(state.selectedGrade.value).toStrictEqual(row)
  })

  it('空列表导出不调用 xlsx 且给出提示', () => {
    const { state } = withSetup()

    state.exportGrades()

    expect(mocks.message.warning).toHaveBeenCalledWith('暂无可导出的成绩数据')
  })
})
