import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useQuestions（题库管理逻辑）单元测试
 *
 * <p>本文件此前 0% 覆盖，且承载 P1-12 缺陷修复：本组合式函数曾发送 0 基页码
 * （`page: pagination.page - 1`、课程下拉写死 `page: 0`），而后端
 * QuestionController/MyBatis-Plus `Page.of(page,size)` 是 **1 基**口径
 * （与 useExamPapers 的实现相互矛盾），第 1 页会打到非法的 page=0。
 * 这里把「页码基准 + 筛选参数真的进请求 + 信封兼容 + 失败降级」全部钉成回归用例。</p>
 */

const mocks = vi.hoisted(() => ({
  getQuestionList: vi.fn(),
  createQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  deleteQuestion: vi.fn(),
  generateByAI: vi.fn(),
  getErrorProne: vi.fn(),
  exportQuestionsExcel: vi.fn(),
  importQuestionsExcel: vi.fn(),
  getCourseList: vi.fn(),
  reqGet: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  confirm: vi.fn(),
}))

vi.mock('@/api/question', () => ({
  default: {
    getQuestionList: mocks.getQuestionList,
    createQuestion: mocks.createQuestion,
    updateQuestion: mocks.updateQuestion,
    deleteQuestion: mocks.deleteQuestion,
    generateByAI: mocks.generateByAI,
    getErrorProne: mocks.getErrorProne,
    exportQuestionsExcel: mocks.exportQuestionsExcel,
    importQuestionsExcel: mocks.importQuestionsExcel,
  },
}))
vi.mock('@/api/course', () => ({ default: { getCourseList: mocks.getCourseList } }))
// viewStats 直接用 axios 实例，其余走领域 api
vi.mock('@/api/request', () => ({
  default: { get: mocks.reqGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm },
}))

import { useQuestions, OPTION_LABELS } from '@/composables/useQuestions'

function withSetup() {
  let state!: ReturnType<typeof useQuestions>
  mount(
    defineComponent({
      setup() {
        state = useQuestions()
        return () => h('div')
      },
    }),
  )
  return { state }
}

/**
 * 挂载即触发 onMounted 的 fetchCourses + fetchQuestions，
 * 需要断言「保存/删除后又刷新了一次」的用例必须先等首轮跑完并清空计数，
 * 否则 toHaveBeenCalled 会因为首轮调用而恒真。
 */
async function setupQuiet() {
  const ctx = withSetup()
  await flushPromises()
  mocks.getQuestionList.mockClear()
  mocks.getCourseList.mockClear()
  return ctx
}

/** document.createElement 被替换前保存原生实现，供假 input 之外的标签回落到真实元素 */
const originalCreateElement = document.createElement.bind(document)

type FakeFileInput = {
  type: string
  accept: string
  onchange: (e: unknown) => Promise<void> | undefined
  click: () => unknown
}

/** 由测试驱动的假文件选择框（jsdom 无法弹出真实对话框） */
let fakeInput: FakeFileInput | null = null

function stubFilePicker(pick: File | null) {
  const input: FakeFileInput = {
    type: '',
    accept: '',
    // 源码会把真实处理函数赋值到 onchange 上，click 时直接回放选择事件
    onchange: () => Promise.resolve(),
    click() {
      return Promise.resolve(input.onchange({ target: { files: pick ? [pick] : [] } }))
    },
  }
  fakeInput = input
  return input
}

const sentParams = () =>
  mocks.getQuestionList.mock.calls[mocks.getQuestionList.mock.calls.length - 1][0] as Record<
    string,
    unknown
  >

/**
 * questionFormRef/aiFormRef 由 `ref(null)` 推导为 Ref<null>，测试需注入校验桩；
 * validate 返回回调的 promise，使 saveQuestion/generateByAI 的 await 能覆盖整条内部链路。
 */
function injectValidator(target: { value: unknown }, valid: boolean) {
  target.value = {
    validate: (cb: (v: boolean) => unknown) => Promise.resolve(cb(valid)),
  }
}

describe('useQuestions — 分页参数基准（P1-12 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseList.mockResolvedValue({ data: [] })
    mocks.getQuestionList.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  it('课程下拉按后端 1 基口径请求 page=1（旧实现传 0）', async () => {
    const { state } = withSetup()
    await state.fetchCourses()

    expect(mocks.getCourseList).toHaveBeenCalledWith({ page: 1, size: 100 })
  })

  it('课程下拉：list 为空数组、数据在 records 时不得被吞成空下拉', async () => {
    // 缺陷回归（2026-10-04）：旧写法 `res.data?.list || res.data` 里空数组是 truthy，
    // MyBatis-Plus Page 形状（records）永远取不到。
    mocks.getCourseList.mockResolvedValue({
      data: { list: [], records: [{ id: 7, name: '编译原理' }] },
    })
    const { state } = withSetup()
    await state.fetchCourses()

    expect(state.courseList.value).toEqual([{ id: 7, name: '编译原理' }])
  })

  it('第 1 页必须发 page=1，而不是被换算成 0', async () => {
    const { state } = withSetup()
    await state.fetchQuestions()

    expect(sentParams()).toMatchObject({ page: 1, size: 10 })
  })

  it('翻到第 3 页时页码原样透传（旧实现会发 page=2）', async () => {
    const { state } = withSetup()
    state.pagination.page = 3
    state.pagination.size = 20

    await state.fetchQuestions()

    expect(sentParams()).toMatchObject({ page: 3, size: 20 })
  })

  it('四个筛选条件都要真正进入请求', async () => {
    const { state } = withSetup()
    Object.assign(state.filters, {
      keyword: '递归',
      type: 'SINGLE_CHOICE',
      difficulty: 'HARD',
      courseId: 5,
    })

    await state.fetchQuestions()

    expect(sentParams()).toEqual({
      page: 1,
      size: 10,
      keyword: '递归',
      type: 'SINGLE_CHOICE',
      difficulty: 'HARD',
      courseId: 5,
    })
  })

  it('未填筛选时不得带上空值键，避免后端把空串当条件', async () => {
    const { state } = withSetup()
    await state.fetchQuestions()

    expect(Object.keys(sentParams()).sort()).toEqual(['page', 'size'])
  })
})

describe('useQuestions — 响应信封与统计', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseList.mockResolvedValue({ data: [] })
  })

  it.each<[string, unknown, number]>([
    ['data.records', { data: { records: [{ id: 1 }, { id: 2 }], total: 9 } }, 2],
    ['data.list', { data: { list: [{ id: 1 }] } }, 1],
    ['data 直接是数组', { data: [{ id: 1 }] }, 1],
    ['axios 顶层即数组', [{ id: 1 }, { id: 2 }, { id: 3 }], 3],
    ['axios 顶层 {records,total}', { records: [{ id: 1 }], total: 57 }, 1],
  ])('%s 形状解析出 %i 条题目', async (_name, payload, expected) => {
    mocks.getQuestionList.mockResolvedValue(payload as never)
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(state.questionList.value).toHaveLength(expected)
    expect(state.loading.value).toBe(false)
  })

  it('顶层 {records,total} 信封的后端总数要落到分页与统计卡', async () => {
    mocks.getQuestionList.mockResolvedValue({ records: [{ id: 1 }, { id: 2 }], total: 57 })
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(state.pagination.total).toBe(57)
    expect(state.questionStats.value[0].value).toBe(57)
  })

  it('list 为空数组、数据在 records 时不得被 || 短路吞成空列表', async () => {
    // 缺陷回归（2026-10-04，P1-10 同类）：`res.data?.records || res.data?.list` 看似
    // records 优先，但换到 PagedResponse 形状时 `list || records` 的空数组会吞掉数据。
    mocks.getQuestionList.mockResolvedValue({
      data: { list: [], records: [{ id: 3 }, { id: 4 }], total: 12 },
    })
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(state.questionList.value).toHaveLength(2)
    expect(state.pagination.total).toBe(12)
  })

  it('翻到超出范围的空页时仍保留后端总数，分页控件不塌成 1 页', async () => {
    // 缺陷回归：旧实现裸数组分支 `pagination.total = rawData.length`，
    // records=[] 时 total 归零，用户再也翻不回后面的页。
    mocks.getQuestionList.mockResolvedValue({ data: { records: [], total: 200 } })
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(state.questionList.value).toHaveLength(0)
    expect(state.pagination.total).toBe(200)
    expect(state.questionStats.value[0].value).toBe(200)
  })

  it('后端不含 total 字段时才退化为当前页长度', async () => {
    mocks.getQuestionList.mockResolvedValue({ data: [{ id: 1 }, { id: 2 }] })
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(state.pagination.total).toBe(2)
  })

  it('无法识别的形状与后端一致地置空，不残留上一页数据', async () => {
    mocks.getQuestionList.mockResolvedValue({ data: { records: [{ id: 1 }], total: 1 } })
    const { state } = withSetup()
    await state.fetchQuestions()
    expect(state.questionList.value).toHaveLength(1)

    // 契约（2026-10-04 定稿）：响应形状无法解析出列表时，列表与总数一并归零。
    // 旧实现保留上一批数据，会让界面显示与筛选/分页状态不一致的陈旧行
    // （用户看到的是「上一页的内容」却以为已刷新），且统计卡数字与列表条数矛盾。
    mocks.getQuestionList.mockResolvedValue({ data: { unexpected: 'shape' } })

    await state.fetchQuestions()

    expect(state.questionList.value).toHaveLength(0)
    expect(state.pagination.total).toBe(0)
    expect(state.questionStats.value[0].value).toBe(0)
  })

  it('列表接口失败：提示且结束 loading', async () => {
    mocks.getQuestionList.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await state.fetchQuestions()

    expect(mocks.message.error).toHaveBeenCalledWith('加载题目列表失败')
    expect(state.loading.value).toBe(false)
  })

  it('课程接口失败时下拉置空而不抛异常', async () => {
    mocks.getCourseList.mockResolvedValue({ data: { list: [{ id: 1, name: 'CS' }] } })
    const { state } = withSetup()
    await state.fetchCourses()
    expect(state.courseList.value).toEqual([{ id: 1, name: 'CS' }])

    mocks.getCourseList.mockRejectedValue(new Error('500'))
    await state.fetchCourses()
    expect(state.courseList.value).toEqual([])
  })
})

describe('useQuestions — 表单填充与保存', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseList.mockResolvedValue({ data: [] })
    mocks.getQuestionList.mockResolvedValue({ data: { records: [], total: 0 } })
    mocks.createQuestion.mockResolvedValue({ code: 200 })
    mocks.updateQuestion.mockResolvedValue({ code: 200 })
  })

  it('openCreateDialog 重置为 4 个空选项', () => {
    const { state } = withSetup()
    state.questionForm.content = '脏数据'

    state.openCreateDialog()

    expect(state.isEditing.value).toBe(false)
    expect(state.questionForm).toMatchObject({
      type: 'SINGLE_CHOICE',
      difficulty: 'MEDIUM',
      score: 5,
      content: '',
    })
    expect(state.questionForm.options).toHaveLength(4)
    expect(state.showEditDialog.value).toBe(true)
  })

  it('editQuestion 兼容 text/isAnswer 别名并把正确项映射为选项标签', () => {
    const { state } = withSetup()

    state.editQuestion({
      id: 12,
      content: '栈的特点是？',
      type: 'SINGLE_CHOICE',
      options: [
        { content: '先进后出', isCorrect: true },
        { text: '先进先出', isAnswer: true },
        { content: '随机存取' },
      ],
    } as never)

    expect(state.isEditing.value).toBe(true)
    expect(state.questionForm.options).toEqual([
      { content: '先进后出', isCorrect: true },
      { content: '先进先出', isCorrect: true },
      { content: '随机存取', isCorrect: false },
    ])
  })

  it('选择题保存：选项压成字符串数组、答案拼成 A/C 标签（后端契约）', async () => {
    const { state } = await setupQuiet()
    state.openCreateDialog()
    Object.assign(state.questionForm, {
      courseId: 8,
      content: '以下哪些是排序算法？',
      options: [
        { content: '快排', isCorrect: true },
        { content: ' BFS', isCorrect: false },
        { content: '归并', isCorrect: true },
        { content: ' DFS', isCorrect: false },
      ],
    })
    injectValidator(state.questionFormRef, true)

    await state.saveQuestion()

    expect(mocks.createQuestion).toHaveBeenCalledWith({
      type: 'SINGLE_CHOICE',
      difficulty: 'MEDIUM',
      courseId: 8,
      content: '以下哪些是排序算法？',
      score: 5,
      explanation: '',
      options: ['快排', ' BFS', '归并', ' DFS'],
      answer: `${OPTION_LABELS[0]},${OPTION_LABELS[2]}`,
    })
    expect(mocks.message.success).toHaveBeenCalledWith('创建成功')
    expect(state.saving.value).toBe(false)
    // 保存成功后必须刷新列表
    expect(mocks.getQuestionList).toHaveBeenCalled()
  })

  it('编辑态走 update 并带上被编辑题目 id', async () => {
    const { state } = await setupQuiet()
    state.editQuestion({ id: 12, content: '旧题干', type: 'FILL_BLANK', answer: 'TCP' } as never)
    injectValidator(state.questionFormRef, true)

    await state.saveQuestion()

    expect(mocks.updateQuestion).toHaveBeenCalledWith(
      12,
      expect.objectContaining({ content: '旧题干', answer: 'TCP' }),
    )
    expect(mocks.createQuestion).not.toHaveBeenCalled()
  })

  it('判断题答案必须是字符串', async () => {
    const { state } = withSetup()
    state.openCreateDialog()
    Object.assign(state.questionForm, { type: 'TRUE_FALSE', answer: true })
    injectValidator(state.questionFormRef, true)

    await state.saveQuestion()

    expect(mocks.createQuestion.mock.calls[0][0]).toMatchObject({ answer: 'true' })
  })

  it('校验不通过时不发任何写请求', async () => {
    const { state } = withSetup()
    injectValidator(state.questionFormRef, false)

    await state.saveQuestion()

    expect(mocks.createQuestion).not.toHaveBeenCalled()
    expect(mocks.updateQuestion).not.toHaveBeenCalled()
    expect(state.saving.value).toBe(false)
  })

  it('保存失败提示且结束保存态', async () => {
    const { state } = withSetup()
    mocks.createQuestion.mockRejectedValue(new Error('500'))
    injectValidator(state.questionFormRef, true)

    await state.saveQuestion()

    expect(mocks.message.error).toHaveBeenCalledWith('保存失败')
    expect(state.saving.value).toBe(false)
  })

  it('addOption/removeOption 维护选项数组', () => {
    const { state } = withSetup()
    state.openCreateDialog()

    state.addOption()
    expect(state.questionForm.options).toHaveLength(5)
    state.removeOption(4)
    expect(state.questionForm.options).toHaveLength(4)
  })

  it('删除确认后调用 DELETE 并重载，取消时静默', async () => {
    const { state } = await setupQuiet()
    mocks.confirm.mockResolvedValue('confirm')

    await state.deleteQuestion({ id: 3, content: '待删题目' } as never)

    expect(mocks.deleteQuestion).toHaveBeenCalledWith(3)
    expect(mocks.message.success).toHaveBeenCalledWith('删除成功')

    mocks.confirm.mockRejectedValue('cancel')
    mocks.getQuestionList.mockClear()
    await state.deleteQuestion({ id: 4, content: '另一题' } as never)

    expect(mocks.deleteQuestion).toHaveBeenCalledTimes(1)
    expect(mocks.getQuestionList).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('删除接口失败提示删除失败', async () => {
    const { state } = withSetup()
    mocks.confirm.mockResolvedValue('confirm')
    mocks.deleteQuestion.mockRejectedValue(new Error('500'))

    await state.deleteQuestion({ id: 3, content: '待删题目' } as never)

    expect(mocks.message.error).toHaveBeenCalledWith('删除失败')
  })
})

describe('useQuestions — AI 出题 / 统计 / 易错题', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseList.mockResolvedValue({ data: [] })
    mocks.getQuestionList.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  it('AI 生成成功：提示数量、关弹窗并重载', async () => {
    const { state } = await setupQuiet()
    Object.assign(state.aiForm, {
      topic: '二叉树',
      type: 'MIXED',
      difficulty: 'EASY',
      count: 3,
      courseId: '6',
    })
    mocks.generateByAI.mockResolvedValue({ data: { questions: [{ id: 1 }, { id: 2 }, { id: 3 }] } })
    injectValidator(state.aiFormRef, true)

    await state.generateByAI()

    expect(mocks.generateByAI).toHaveBeenCalledWith({
      topic: '二叉树',
      type: 'MIXED',
      difficulty: 'EASY',
      count: 3,
      courseId: '6',
      requirements: undefined,
    })
    expect(mocks.message.success).toHaveBeenCalledWith('成功生成 3 道题目')
    expect(state.showAIDialog.value).toBe(false)
    expect(mocks.getQuestionList).toHaveBeenCalled()
    expect(state.aiGenerating.value).toBe(false)
  })

  it('AI 返回空列表时只警告，不刷新列表', async () => {
    const { state } = await setupQuiet()
    mocks.generateByAI.mockResolvedValue({ data: { questions: [] } })
    injectValidator(state.aiFormRef, true)

    await state.generateByAI()

    expect(mocks.message.warning).toHaveBeenCalledWith('未生成有效题目')
    expect(mocks.getQuestionList).not.toHaveBeenCalled()
  })

  it('AI 接口失败提示且不关弹窗', async () => {
    const { state } = withSetup()
    state.showAIDialog.value = true
    mocks.generateByAI.mockRejectedValue(new Error('503'))
    injectValidator(state.aiFormRef, true)

    await state.generateByAI()

    expect(mocks.message.error).toHaveBeenCalledWith('AI生成失败，请稍后重试')
    expect(state.showAIDialog.value).toBe(true)
    expect(state.aiGenerating.value).toBe(false)
  })

  it('题目统计成功时展示后端数据', async () => {
    const { state } = withSetup()
    mocks.reqGet.mockResolvedValue({ data: { usageCount: 12, correctRate: 0.42 } })

    await state.viewStats({ id: 7 } as never)

    expect(mocks.reqGet).toHaveBeenCalledWith('/questions/7/stats')
    expect(state.currentStats.value).toEqual({ usageCount: 12, correctRate: 0.42 })
  })

  it('统计接口不可用时回落到占位值，绝不伪造数字', async () => {
    const { state } = withSetup()
    mocks.reqGet.mockRejectedValue(new Error('404'))

    await state.viewStats({ id: 7 } as never)

    expect(state.currentStats.value).toEqual({
      usageCount: 0,
      correctRate: null,
      avgTime: '--',
      errorCount: 0,
      recentUsage: [],
    })
    expect(state.showStatsDialog.value).toBe(true)
  })

  it('易错题榜：有数据/空数据/接口失败三种提示', async () => {
    const { state } = withSetup()

    mocks.getErrorProne.mockResolvedValue({ data: { list: [{ id: 1 }, { id: 2 }] } })
    await state.viewErrorProne()
    expect(mocks.message.success).toHaveBeenCalledWith('获取到 2 道易错题')

    mocks.message.success.mockClear()
    mocks.getErrorProne.mockResolvedValue({ data: { list: [] } })
    await state.viewErrorProne()
    expect(mocks.message.info).toHaveBeenCalledWith('暂无易错题数据')

    mocks.getErrorProne.mockRejectedValue(new Error('500'))
    await state.viewErrorProne()
    expect(mocks.message.error).toHaveBeenCalledWith('获取易错题失败，请稍后重试')
  })
})

describe('useQuestions — 批量导出/导入', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fakeInput = null
    // handleImport 靠 document.createElement('input') 选文件，此处替换成可编程的假控件
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) =>
      tag === 'input' && fakeInput
        ? (fakeInput as unknown as HTMLElement)
        : originalCreateElement(tag),
    )
    mocks.getCourseList.mockResolvedValue({ data: [] })
    mocks.getQuestionList.mockResolvedValue({ data: { records: [], total: 0 } })
    window.URL.createObjectURL = vi.fn(() => 'blob:fake')
    window.URL.revokeObjectURL = vi.fn()
    vi.spyOn(window.HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('导出：常驻提示走 ElMessage 函数式调用，成功后关闭并下载', async () => {
    const { state } = withSetup()
    state.filters.courseId = '5'
    mocks.exportQuestionsExcel.mockResolvedValue({ data: new Blob(['x']) })

    await state.handleExport()

    expect(mocks.message).toHaveBeenCalledWith({ message: '正在导出题库...', duration: 0 })
    expect(mocks.exportQuestionsExcel).toHaveBeenCalledWith({ courseId: '5' })
    expect(mocks.message.closeAll).toHaveBeenCalled()
    expect(window.URL.createObjectURL).toHaveBeenCalled()
    expect(mocks.message.success).toHaveBeenCalledWith('导出成功')
  })

  it('导出失败也要关闭常驻提示，否则 loading 条永远挂着', async () => {
    const { state } = withSetup()
    mocks.exportQuestionsExcel.mockRejectedValue(new Error('500'))

    await state.handleExport()

    expect(mocks.message.closeAll).toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('导出失败，请稍后重试')
  })

  it('导入：按 8 列表头约定的 xlsx 以 multipart 上传并带上当前课程筛选', async () => {
    const { state } = await setupQuiet()
    state.filters.courseId = '9'
    mocks.importQuestionsExcel.mockResolvedValue({ data: { importedCount: 7, total: 9 } })
    stubFilePicker(new File(['x'], '题库.xlsx'))

    state.handleImport()
    await flushPromises()

    const [formData] = mocks.importQuestionsExcel.mock.calls[0] as [FormData]
    expect(formData).toBeInstanceOf(FormData)
    expect((formData.get('file') as File).name).toBe('题库.xlsx')
    expect(formData.get('courseId')).toBe('9')
    expect(mocks.message).toHaveBeenCalledWith({ message: '正在导入题目...', duration: 0 })
    expect(mocks.message.success).toHaveBeenCalledWith('导入完成：成功 7 / 共 9 题')
    expect(mocks.getQuestionList).toHaveBeenCalled()
  })

  it('未选择文件时不发导入请求', async () => {
    const { state } = await setupQuiet()
    stubFilePicker(null)

    state.handleImport()
    await flushPromises()

    expect(mocks.importQuestionsExcel).not.toHaveBeenCalled()
  })

  it('导入失败提示格式要求并关闭常驻提示', async () => {
    const { state } = await setupQuiet()
    mocks.importQuestionsExcel.mockRejectedValue(new Error('400'))
    stubFilePicker(new File(['x'], 'bad.xlsx'))

    state.handleImport()
    await flushPromises()

    expect(mocks.message.closeAll).toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('导入失败，请检查文件格式（.xlsx，8 列表头）')
  })
})

describe('useQuestions — 展示辅助', () => {
  it('题型/难度/正确率映射覆盖未知值', () => {
    const { state } = withSetup()

    expect(state.isChoiceType('MULTIPLE_CHOICE')).toBe(true)
    expect(state.isChoiceType('CODING')).toBe(false)
    expect(state.getTypeText('SINGLE_CHOICE')).toBe('单选')
    expect(state.getTypeText('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE')
    expect(state.getTypeTagType('CODING')).toBe('danger')
    expect(state.getTypeTagType('UNKNOWN_TYPE')).toBe('')
    expect(state.getDifficultyValue('HARD')).toBe(3)
    expect(state.getDifficultyValue('UNKNOWN')).toBe(1)
    expect(state.getAccuracyClass(0.9)).toBe('high')
    expect(state.getAccuracyClass(0.7)).toBe('medium')
    expect(state.getAccuracyClass(0.1)).toBe('low')
    expect(state.getAccuracyClass(null)).toBe('')
  })
})
