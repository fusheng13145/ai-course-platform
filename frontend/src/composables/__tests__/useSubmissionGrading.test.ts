import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import {
  parseAiSuggestion,
  useSubmissionGrading,
  type Submission,
} from '@/composables/useSubmissionGrading'

const mocks = vi.hoisted(() => ({
  gradeSubmission: vi.fn(),
  returnSubmission: vi.fn(),
  aiGrade: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/submission', () => ({
  default: {
    gradeSubmission: mocks.gradeSubmission,
    returnSubmission: mocks.returnSubmission,
    aiGrade: mocks.aiGrade,
  },
}))
// 浮层宿主接缝：断言经同一组 message spy 观察，语义不变。
import { setOverlayHost } from '@/bootstrap/overlayHost'
setOverlayHost({ message: mocks.message })

const message = mocks.message

function makeSubmission(overrides: Partial<Submission>): Submission {
  return { id: 1, taskId: 31, studentId: 8, status: 'SUBMITTED', ...overrides }
}

function setup(submissions: Submission[]) {
  const list = ref<Submission[]>(submissions)
  const onGraded = vi.fn()
  const grading = useSubmissionGrading({
    courseId: ref<number | null>(7),
    submissions: list,
    onGraded,
  })
  return { list, onGraded, grading }
}

describe('useSubmissionGrading - 状态筛选', () => {
  it('待批集合 = SUBMITTED + RESUBMITTED，GRADED/RETURNED 不计入', () => {
    const { grading } = setup([
      makeSubmission({ id: 1, status: 'SUBMITTED' }),
      makeSubmission({ id: 2, status: 'RESUBMITTED' }),
      makeSubmission({ id: 3, status: 'GRADED' }),
      makeSubmission({ id: 4, status: 'RETURNED' }),
    ])
    expect(grading.pendingCount.value).toBe(2)

    grading.statusFilter.value = 'PENDING'
    expect(grading.filteredSubmissions.value.map((row) => row.id)).toEqual([1, 2])

    grading.statusFilter.value = 'GRADED'
    expect(grading.filteredSubmissions.value.map((row) => row.id)).toEqual([3])

    grading.statusFilter.value = 'RETURNED'
    expect(grading.filteredSubmissions.value.map((row) => row.id)).toEqual([4])

    grading.statusFilter.value = 'ALL'
    expect(grading.filteredSubmissions.value).toHaveLength(4)
  })

  it('状态大小写不敏感，未知状态不落入任何分组（ALL 除外）', () => {
    const { grading } = setup([makeSubmission({ id: 9, status: 'resubmitted' })])
    expect(grading.pendingCount.value).toBe(1)
    grading.statusFilter.value = 'PENDING'
    expect(grading.filteredSubmissions.value).toHaveLength(1)
    grading.statusFilter.value = 'GRADED'
    expect(grading.filteredSubmissions.value).toHaveLength(0)
  })
})

describe('useSubmissionGrading - 批改流校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('空分数不提交并提示「请填写分数」', async () => {
    const row = makeSubmission({ id: 1, maxScore: 100 })
    const { grading } = setup([row])
    grading.openGrade(row)
    grading.gradeModal.form.score = null
    await expect(grading.submitGrade()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请填写分数')
    expect(mocks.gradeSubmission).not.toHaveBeenCalled()
  })

  it('分数超上限不提交（上限取 row.maxScore）', async () => {
    const row = makeSubmission({ id: 1, maxScore: 100 })
    const { grading } = setup([row])
    grading.openGrade(row)
    grading.gradeModal.form.score = 101
    await expect(grading.submitGrade()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('分数需在 0 至 100 之间')
    expect(mocks.gradeSubmission).not.toHaveBeenCalled()
  })

  it('合法分数提交成功：payload 含 score/feedback，弹层关闭并触发 onGraded', async () => {
    const row = makeSubmission({ id: 42, maxScore: 100, feedback: '' })
    mocks.gradeSubmission.mockResolvedValue({ code: 200, data: {} })
    const { grading, onGraded } = setup([row])
    grading.openGrade(row)
    grading.gradeModal.form.score = 88
    grading.gradeModal.form.feedback = '整体不错'
    await expect(grading.submitGrade()).resolves.toBe(true)
    expect(mocks.gradeSubmission).toHaveBeenCalledWith(42, { score: 88, feedback: '整体不错' })
    expect(grading.gradeModal.visible).toBe(false)
    expect(onGraded).toHaveBeenCalledTimes(1)
  })

  it('提交失败返回 false，不关闭弹层（教师可重试）', async () => {
    const row = makeSubmission({ id: 42, maxScore: 100 })
    mocks.gradeSubmission.mockRejectedValue(new Error('boom'))
    const { grading, onGraded } = setup([row])
    grading.openGrade(row)
    grading.gradeModal.form.score = 60
    await expect(grading.submitGrade()).resolves.toBe(false)
    expect(grading.gradeModal.visible).toBe(true)
    expect(onGraded).not.toHaveBeenCalled()
  })
})

describe('useSubmissionGrading - AI 预批与采纳回填', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('runAiPreview 解析 aiFeedback + submissionData 到建议区，不改表单', async () => {
    const row = makeSubmission({
      id: 7,
      content: '作答内容',
      aiFeedback: '结构清晰',
      submissionData: JSON.stringify({
        aiSuggestedScore: '85',
        aiHighlights: '论点明确',
        aiSuggestions: '补充案例',
      }),
    })
    mocks.aiGrade.mockResolvedValue({ code: 200, data: row })
    const { grading } = setup([row])
    grading.openGrade(row)
    grading.gradeModal.form.score = 60
    await grading.runAiPreview()

    expect(mocks.aiGrade).toHaveBeenCalledWith(7)
    expect(grading.gradeModal.aiResult).toEqual({
      suggestedScore: 85,
      highlights: '论点明确',
      suggestions: '补充案例',
      feedback: '结构清晰',
    })
    // 预批不自动改表单
    expect(grading.gradeModal.form.score).toBe(60)
    expect(grading.gradeModal.form.feedback).toBe('')
  })

  it('parse 失败（坏 JSON）静默降级为无建议，不阻塞批改表单', async () => {
    const row = makeSubmission({ id: 7, content: '作答', aiFeedback: '', submissionData: '{oops' })
    mocks.aiGrade.mockResolvedValue({ code: 200, data: row })
    const { grading } = setup([row])
    grading.openGrade(row)
    await grading.runAiPreview()
    expect(grading.gradeModal.aiResult).toBeNull()
    // 表单仍可提交
    grading.gradeModal.form.score = 70
    mocks.gradeSubmission.mockResolvedValue({ code: 200, data: {} })
    await expect(grading.submitGrade()).resolves.toBe(true)
  })

  it('applyAiSuggestion 把建议分/评语回填表单（可再修改）', async () => {
    const row = makeSubmission({
      id: 7,
      content: '作答',
      aiFeedback: 'AI 评语',
      submissionData: JSON.stringify({
        aiSuggestedScore: 92,
        aiHighlights: '亮点',
        aiSuggestions: '建议',
      }),
    })
    mocks.aiGrade.mockResolvedValue({ code: 200, data: row })
    const { grading } = setup([row])
    grading.openGrade(row)
    await grading.runAiPreview()
    grading.applyAiSuggestion()
    expect(grading.gradeModal.form.score).toBe(92)
    expect(grading.gradeModal.form.feedback).toBe('AI 评语')
  })

  it('无建议时采纳操作只提示不回填', () => {
    const row = makeSubmission({ id: 7, content: '作答' })
    const { grading } = setup([row])
    grading.openGrade(row)
    grading.applyAiSuggestion()
    expect(message.warning).toHaveBeenCalledWith('暂无 AI 建议，请先点击「AI 预批」')
    expect(grading.gradeModal.form.score).toBeNull()
  })
})

describe('useSubmissionGrading - 退回流', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('退回原因为空不提交', async () => {
    const row = makeSubmission({ id: 5 })
    const { grading } = setup([row])
    grading.openReturn(row)
    grading.returnModal.form.reason = '   '
    await expect(grading.submitReturn()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请填写退回原因')
    expect(mocks.returnSubmission).not.toHaveBeenCalled()
  })

  it('退回成功：body 为 {reason}，弹层关闭并触发 onGraded', async () => {
    const row = makeSubmission({ id: 5 })
    mocks.returnSubmission.mockResolvedValue({ code: 200, data: {} })
    const { grading, onGraded } = setup([row])
    grading.openReturn(row)
    grading.returnModal.form.reason = '缺少关键步骤'
    await expect(grading.submitReturn()).resolves.toBe(true)
    expect(mocks.returnSubmission).toHaveBeenCalledWith(5, { reason: '缺少关键步骤' })
    expect(grading.returnModal.visible).toBe(false)
    expect(onGraded).toHaveBeenCalledTimes(1)
  })
})

describe('useSubmissionGrading - closeAll', () => {
  it('一键关闭并重置两个弹层的全部状态', async () => {
    const row = makeSubmission({
      id: 7,
      content: '作答',
      aiFeedback: '评语',
      submissionData: JSON.stringify({ aiSuggestedScore: 90 }),
    })
    mocks.aiGrade.mockResolvedValue({ code: 200, data: row })
    const { grading } = setup([row])
    grading.openGrade(row)
    await grading.runAiPreview()
    grading.openReturn(row)

    grading.closeAll()

    expect(grading.gradeModal.visible).toBe(false)
    expect(grading.gradeModal.row).toBeNull()
    expect(grading.gradeModal.form.score).toBeNull()
    expect(grading.gradeModal.form.feedback).toBe('')
    expect(grading.gradeModal.aiResult).toBeNull()
    expect(grading.gradeModal.aiLoading).toBe(false)
    expect(grading.returnModal.visible).toBe(false)
    expect(grading.returnModal.row).toBeNull()
    expect(grading.returnModal.form.reason).toBe('')
  })
})

describe('parseAiSuggestion - 纯函数边界', () => {
  it('submissionData 缺失但 aiFeedback 有值时仍产出建议', () => {
    const result = parseAiSuggestion({ aiFeedback: '只有评语' })
    expect(result).toEqual({
      suggestedScore: null,
      highlights: '',
      suggestions: '',
      feedback: '只有评语',
    })
  })

  it('空入参与非对象 JSON 返回 null', () => {
    expect(parseAiSuggestion(null)).toBeNull()
    expect(parseAiSuggestion({})).toBeNull()
    expect(parseAiSuggestion({ submissionData: 'null' })).toBeNull()
    expect(parseAiSuggestion({ submissionData: '["array"]' })).toBeNull()
  })

  it('aiSuggestedScore 非数值时降级为 null，不影响其余字段', () => {
    const result = parseAiSuggestion({
      aiFeedback: '评语',
      submissionData: JSON.stringify({ aiSuggestedScore: 'abc', aiHighlights: '亮点' }),
    })
    expect(result?.suggestedScore).toBeNull()
    expect(result?.highlights).toBe('亮点')
  })
})
