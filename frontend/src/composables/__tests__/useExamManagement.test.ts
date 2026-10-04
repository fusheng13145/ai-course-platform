import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useExamManagement（全校考试管理逻辑）单元测试
 *
 * <p>本文件此前 0% 覆盖，且承载 P1-12 缺陷修复：本组合式函数曾把 UI 的 1 基页码
 * 换算成 0 基（`page: pagination.page - 1`）发给后端，而后端 ExamPaperController 是
 * `@RequestParam(defaultValue="1") long page` + MyBatis-Plus `Page.of(page,size)` 的
 * **1 基**口径 —— 第 1 页会打到非法的 page=0，翻页与 useExamPapers 相互矛盾。
 * 这里把页码基准、筛选参数真正进请求、信封兼容与失败降级钉成回归用例。</p>
 */

const mocks = vi.hoisted(() => ({
  getExamPaperList: vi.fn(),
  getActiveExams: vi.fn(),
  forceSubmit: vi.fn(),
  getCheatingDetect: vi.fn(),
  getExamStatistics: vi.fn(),
  reqGet: vi.fn(),
  reqPost: vi.fn(),
  reqPatch: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  confirm: vi.fn(),
  prompt: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  default: {
    getExamPaperList: mocks.getExamPaperList,
    getActiveExams: mocks.getActiveExams,
    forceSubmit: mocks.forceSubmit,
    getCheatingDetect: mocks.getCheatingDetect,
    getExamStatistics: mocks.getExamStatistics,
  },
}))
vi.mock('@/api/request', () => ({
  default: {
    get: mocks.reqGet,
    post: mocks.reqPost,
    patch: mocks.reqPatch,
    put: vi.fn(),
    delete: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm, prompt: mocks.prompt },
}))

import { useExamManagement } from '@/composables/useExamManagement'

type Paper = { id: number; title?: string; status?: string }

function withSetup() {
  let state!: ReturnType<typeof useExamManagement>
  mount(
    defineComponent({
      setup() {
        state = useExamManagement()
        return () => h('div')
      },
    }),
  )
  return { state }
}

/**
 * 挂载即触发 fetchExamList + fetchActiveSessions；需要断言「操作后又刷新了一次」
 * 的用例必须先跑完首轮并清空计数，否则 toHaveBeenCalled 恒真。
 */
async function setupQuiet() {
  const ctx = withSetup()
  await flushPromises()
  mocks.getExamPaperList.mockClear()
  mocks.getActiveExams.mockClear()
  return ctx
}

const lastListParams = () =>
  mocks.getExamPaperList.mock.calls[mocks.getExamPaperList.mock.calls.length - 1][0] as Record<
    string,
    unknown
  >

describe('useExamManagement — 分页参数基准（P1-12 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [], total: 0 } })
    mocks.getActiveExams.mockResolvedValue({ data: [] })
  })

  it('第 1 页必须发 page=1，而不是被换算成非法的 page=0', async () => {
    const { state } = withSetup()
    await state.fetchExamList()

    expect(mocks.getExamPaperList).toHaveBeenCalledWith({ page: 1, size: 10, all: true })
  })

  it('翻到第 3 页时页码原样透传（旧实现会发 page=2）', async () => {
    const { state } = withSetup()
    state.pagination.page = 3
    state.pagination.size = 20

    await state.fetchExamList()

    expect(lastListParams()).toMatchObject({ page: 3, size: 20 })
  })

  it('状态与关键词筛选都要真正进入请求', async () => {
    const { state } = withSetup()
    state.filters.status = 'PENDING'
    state.filters.keyword = '操作系统'

    await state.fetchExamList()

    expect(lastListParams()).toEqual({
      page: 1,
      size: 10,
      all: true,
      status: 'PENDING',
      keyword: '操作系统',
    })
  })

  it('未填筛选时只带 page/size/all 三个键', async () => {
    const { state } = withSetup()
    await state.fetchExamList()

    expect(Object.keys(lastListParams()).sort()).toEqual(['all', 'page', 'size'])
  })

  it('概览的试卷总数与分页总数同源', async () => {
    mocks.getExamPaperList.mockResolvedValue({ list: [{ id: 1 }], total: 57 })
    const { state } = withSetup()
    await state.fetchExamList()

    // 顶层 {list,total} 信封是唯一能保住后端总数的形状
    expect(state.pagination.total).toBe(57)
    expect(state.overview.totalPapers).toBe(57)
    expect(state.examList.value).toHaveLength(1)
  })

  it('嵌套 data.{records,total} 同样保住后端总数', async () => {
    // 缺陷回归（2026-10-04）：旧实现只在 `rawData` 顶层找 total，
    // `{data:{records,total}}` 形状下 total 恒为 undefined → 回退成本页长度。
    mocks.getExamPaperList.mockResolvedValue({
      data: { records: [{ id: 1 }, { id: 2 }], total: 138 },
    })
    const { state } = withSetup()
    await state.fetchExamList()

    expect(state.pagination.total).toBe(138)
    expect(state.overview.totalPapers).toBe(138)
  })

  it('翻到空页（records=[] 但 total>0）时分页不塌成 1 页', async () => {
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [], total: 138 } })
    const { state } = withSetup()
    await state.fetchExamList()

    expect(state.examList.value).toHaveLength(0)
    expect(state.pagination.total).toBe(138)
  })
})

describe('useExamManagement — 试卷列表信封兼容', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getActiveExams.mockResolvedValue({ data: [] })
  })

  it.each<[string, unknown, number]>([
    ['data.records', { data: { records: [{ id: 1 }, { id: 2 }] } }, 2],
    ['data.list', { data: { list: [{ id: 1 }] } }, 1],
    ['data 直接是数组', { data: [{ id: 1 }, { id: 2 }, { id: 3 }] }, 3],
    ['axios 顶层即数组', [{ id: 1 }], 1],
    ['空信封', { data: {} }, 0],
    ['无法识别的形状', { data: { unexpected: true } }, 0],
  ])('%s 形状解析出 %i 份试卷', async (_name, payload, expected) => {
    mocks.getExamPaperList.mockResolvedValue(payload as never)
    const { state } = withSetup()

    await state.fetchExamList()

    expect(state.examList.value).toHaveLength(expected)
    expect(state.loading.value).toBe(false)
  })

  it('列表接口失败：提示 + 结束 loading，不把已加载数据清掉', async () => {
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [{ id: 1 }] } })
    const { state } = withSetup()
    await state.fetchExamList()

    mocks.getExamPaperList.mockRejectedValue(new Error('500'))
    await state.fetchExamList()

    expect(mocks.message.error).toHaveBeenCalledWith('加载试卷列表失败')
    expect(state.examList.value).toHaveLength(1)
    expect(state.loading.value).toBe(false)
  })
})

describe('useExamManagement — 进行中考试（挂载即加载）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  it('裸数组与 list 形状都落到 activeSessions，并同步在考数概览', async () => {
    mocks.getActiveExams.mockResolvedValue({
      data: [
        { id: 1, paperTitle: '期中' },
        { id: 2, paperTitle: '期末' },
      ],
    })
    withSetup()
    await flushPromises()

    expect(mocks.getActiveExams).toHaveBeenCalledTimes(1)

    mocks.getActiveExams.mockResolvedValue({ data: { list: [{ id: 3 }] } })
    const { state } = withSetup()
    await flushPromises()

    expect(state.activeSessions.value).toEqual([{ id: 3 }])
    expect(state.overview.activeExams).toBe(1)
    expect(state.loadingSessions.value).toBe(false)
  })

  it('进行中考试失败时静默降级为空（列表页不应被次要接口打断）', async () => {
    mocks.getActiveExams.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await flushPromises()

    expect(state.activeSessions.value).toEqual([])
    expect(state.overview.activeExams).toBe(0)
    expect(mocks.message.error).not.toHaveBeenCalled()
    expect(state.loadingSessions.value).toBe(false)
  })
})

describe('useExamManagement — 审核与强制交卷', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [], total: 0 } })
    mocks.getActiveExams.mockResolvedValue({ data: [] })
    mocks.reqPatch.mockResolvedValue({ code: 200 })
    mocks.reqPost.mockResolvedValue({ code: 200 })
    mocks.forceSubmit.mockResolvedValue({ code: 200 })
  })

  it('审核通过：PATCH 审批端点后重载列表', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockResolvedValue('confirm')

    await state.handleApprove({ id: 7, title: '期中试卷' } as never)

    expect(mocks.reqPatch).toHaveBeenCalledWith('/exam-papers/7/approve')
    expect(mocks.message.success).toHaveBeenCalledWith('审核通过')
    expect(mocks.getExamPaperList).toHaveBeenCalledTimes(1)
  })

  it('审核通过被取消时不发请求也不报错', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockRejectedValue('cancel')

    await state.handleApprove({ id: 7, title: '期中试卷' } as never)

    expect(mocks.reqPatch).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('审批接口失败提示操作失败', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockResolvedValue('confirm')
    mocks.reqPatch.mockRejectedValue(new Error('403'))

    await state.handleApprove({ id: 7, title: '期中试卷' } as never)

    expect(mocks.message.error).toHaveBeenCalledWith('操作失败')
  })

  it('审核拒绝：把填写的原因 POST 给后端', async () => {
    const { state } = await setupQuiet()
    mocks.prompt.mockResolvedValue({ value: '题干超纲' })

    await state.handleReject({ id: 8, title: '期末试卷' } as never)

    expect(mocks.reqPost).toHaveBeenCalledWith('/exam-papers/8/reject', { reason: '题干超纲' })
    expect(mocks.message.success).toHaveBeenCalledWith('已拒绝该试卷')
    expect(mocks.getExamPaperList).toHaveBeenCalledTimes(1)
  })

  it('拒绝弹窗取消时不发请求', async () => {
    const { state } = await setupQuiet()
    mocks.prompt.mockRejectedValue('cancel')

    await state.handleReject({ id: 8, title: '期末试卷' } as never)

    expect(mocks.reqPost).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('强制交卷：调用会话端点并重载进行中考试', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockResolvedValue('confirm')

    await state.forceSubmit({ id: 21, paperTitle: '期中' } as never)

    expect(mocks.forceSubmit).toHaveBeenCalledWith(21)
    expect(mocks.message.success).toHaveBeenCalledWith('已强制交卷')
    expect(mocks.getActiveExams).toHaveBeenCalledTimes(1)
  })

  it('强制交卷失败提示操作失败', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockResolvedValue('confirm')
    mocks.forceSubmit.mockRejectedValue(new Error('500'))

    await state.forceSubmit({ id: 21, paperTitle: '期中' } as never)

    expect(mocks.message.error).toHaveBeenCalledWith('操作失败')
  })
})

describe('useExamManagement — 作弊检测与试卷统计', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [], total: 0 } })
    mocks.getActiveExams.mockResolvedValue({ data: [] })
  })

  it('作弊日志：list/裸数组形状解析并同步作弊嫌疑数', async () => {
    const { state } = await setupQuiet()
    mocks.getCheatingDetect.mockResolvedValue({ data: { list: [{ id: 1 }, { id: 2 }] } })

    await state.viewCheating({ id: 30 } as never)

    expect(mocks.getCheatingDetect).toHaveBeenCalledWith(30)
    expect(state.cheatingLogs.value).toHaveLength(2)
    expect(state.overview.cheatingCases).toBe(2)
    expect(state.showCheatingDialog.value).toBe(true)
  })

  it('作弊日志返回非数组形状时置空', async () => {
    const { state } = await setupQuiet()
    mocks.getCheatingDetect.mockResolvedValue({ data: { unexpected: true } })

    await state.viewCheating({ id: 30 } as never)

    expect(state.cheatingLogs.value).toEqual([])
    expect(state.overview.cheatingCases).toBe(0)
  })

  it('作弊检测接口失败时静默清空，不打断弹窗', async () => {
    const { state } = await setupQuiet()
    mocks.getCheatingDetect.mockRejectedValue(new Error('500'))

    await state.viewCheating({ id: 30 } as never)

    expect(state.cheatingLogs.value).toEqual([])
    expect(state.showCheatingDialog.value).toBe(true)
  })

  it('统计：gradeDistribution 优先，distribution 作为别名兼容', async () => {
    const { state } = await setupQuiet()
    mocks.getExamStatistics.mockResolvedValue({ data: { gradeDistribution: [1, 2, 3, 4, 5] } })
    await state.viewStatistics({ id: 3 } as never)
    expect(state.gradeChartOption.value.series[0].data).toEqual([1, 2, 3, 4, 5])

    mocks.getExamStatistics.mockResolvedValue({ data: { distribution: [0, 1, 0, 1, 0] } })
    await state.viewStatistics({ id: 3 } as never)
    expect(state.gradeChartOption.value.series[0].data).toEqual([0, 1, 0, 1, 0])
    expect(state.showStatsDialog.value).toBe(true)
  })

  it('统计接口失败时清空图表数据而不是留旧图', async () => {
    const { state } = await setupQuiet()
    mocks.getExamStatistics.mockResolvedValue({ data: { gradeDistribution: [1, 1, 1, 1, 1] } })
    await state.viewStatistics({ id: 3 } as never)

    mocks.getExamStatistics.mockRejectedValue(new Error('500'))
    await state.viewStatistics({ id: 3 } as never)

    expect(state.statistics.value).toBeNull()
    expect(state.gradeChartOption.value.series[0].data).toEqual([])
  })
})

describe('useExamManagement — 展示辅助', () => {
  it('状态文案/标签映射覆盖未知值', () => {
    const { state } = withSetup()

    expect(state.getStatusText('PENDING')).toBe('待审核')
    expect(state.getStatusText('DRAFT')).toBe('草稿')
    expect(state.getStatusText('OTHER')).toBe('OTHER')
    expect(state.getStatusType('PUBLISHED')).toBe('success')
    expect(state.getStatusType('OTHER')).toBe('info')
  })

  it('风险等级：字符串映射与数值夹在 1..3', () => {
    const { state } = withSetup()

    expect(state.getRiskLevel('HIGH')).toBe(3)
    expect(state.getRiskLevel('low')).toBe(1)
    expect(state.getRiskLevel('UNKNOWN')).toBe(1)
    expect(state.getRiskLevel(null)).toBe(1)
    expect(state.getRiskLevel(0)).toBe(1)
    expect(state.getRiskLevel(9)).toBe(3)
  })

  it('viewDetail 目前仅给出提示', () => {
    const { state } = withSetup()

    state.viewDetail({ title: '期中试卷' } as never)

    expect(mocks.message.info).toHaveBeenCalledWith('查看试卷: 期中试卷')
  })

  it('试卷行按 title 展示且分页游标可被外部改写后生效', async () => {
    mocks.getExamPaperList.mockResolvedValue({ data: { records: [{ id: 1, title: 'A' }] } })
    const { state } = await setupQuiet()
    const row = (state.examList.value as unknown as Paper[])[0]

    expect(row.title).toBe('A')

    state.pagination.page = 2
    await state.fetchExamList()
    expect(lastListParams()).toMatchObject({ page: 2 })
  })
})
