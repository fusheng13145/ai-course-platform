import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import questionApi from '@/api/question'
import courseApi from '@/api/course'

/**
 * 审核状态推导（2026-09-21 修复，P1-1）。
 *
 * 后端 `Question` 实体/VO **没有 `reviewStatus` 字段**——审核动作
 * （`POST /questions/{id}/review`）只写 `is_active`，且只有「通过/不通过」二分，
 * 不存在独立的「已驳回」态。此前前端直接读 `q.reviewStatus`，取值恒为 undefined，
 * 经 `|| 'PENDING'` 兜底后**恒显示「待审核」**（统计卡恒为「全量 / 0 / 0」）。
 *
 * 现按真实语义推导：`is_active === false` → 待审核；否则 → 已通过。
 * 同时保留对 `reviewStatus` 字符串的兼容——若后端将来补上该字段，无需再改前端。
 *
 * 导出为模块级函数，供 `QbQuestionTable.vue` 复用，避免展示层与统计层逻辑漂移。
 */
export function resolveReviewStatus(question: Record<string, unknown> | null | undefined): string {
  if (question && typeof question.reviewStatus === 'string') return question.reviewStatus
  return question && question.isActive === false ? 'PENDING' : 'APPROVED'
}

/**
 * 教师题库管理领域逻辑
 * <p>题目多维筛选/分页、新建/编辑（表单校验）、删除/复制、审核流转、AI 智能出题。</p>
 */
export function useQuestionBank() {
  const loading = ref(false)
  const questionList = ref([])
  const courseList = ref([])
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)

  const filters = reactive({ courseId: '', type: '', difficulty: '', keyword: '' })

  const formVisible = ref(false)
  const isEdit = ref(false)
  const editingId = ref(null)
  const submitting = ref(false)
  const formRef = ref(null)
  const form = reactive({
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: '',
    content: '',
    answer: '',
    explanation: '',
  })
  const rules = {
    type: [{ required: true, message: '请选择题型', trigger: 'change' }],
    difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
    content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  }

  const reviewVisible = ref(false)
  const reviewing = ref(false)
  const reviewRow = ref(null)
  const reviewForm = reactive({ status: 'APPROVED', comment: '' })

  const aiVisible = ref(false)
  const aiGenerating = ref(false)
  const aiForm = reactive({
    courseId: '',
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    knowledgePoints: [],
  })

  onMounted(() => {
    loadCourses()
    loadQuestions()
  })

  async function loadCourses() {
    try {
      const r = await courseApi.getMyCourses()
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      courseList.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载课程失败', e)
    }
  }

  async function loadQuestions() {
    loading.value = true
    try {
      const params: Record<string, unknown> = { page: page.value, size: pageSize.value }
      if (filters.courseId) params.courseId = filters.courseId
      if (filters.type) params.type = filters.type
      if (filters.difficulty) params.difficulty = filters.difficulty
      if (filters.keyword) params.keyword = filters.keyword

      const r = await questionApi.getQuestionList(params)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      questionList.value = Array.isArray(list) ? list : []
      total.value = body?.total ?? body?.count ?? questionList.value.length
    } catch (e) {
      logger.error('加载题目列表失败', e)
      ElMessage.error('加载题目列表失败')
    } finally {
      loading.value = false
    }
  }

  function countByReview(status) {
    // 修复（2026-09-21，P1-1）：原为 (q.reviewStatus || 'PENDING')，该字段后端不存在，
    // 导致三张统计卡恒为「全量 / 0 / 0」。现统一走 resolveReviewStatus 由 is_active 推导。
    return questionList.value.filter((q) => resolveReviewStatus(q) === status).length
  }

  function handleFilterChange() {
    page.value = 1
    loadQuestions()
  }

  function resetForm() {
    Object.assign(form, {
      type: 'SINGLE_CHOICE',
      difficulty: 'MEDIUM',
      courseId: filters.courseId || '',
      content: '',
      answer: '',
      explanation: '',
    })
  }

  function openCreateDialog() {
    isEdit.value = false
    editingId.value = null
    resetForm()
    formVisible.value = true
  }

  function openEditDialog(row) {
    isEdit.value = true
    editingId.value = row.id
    Object.assign(form, {
      type: row.type || 'SINGLE_CHOICE',
      difficulty: row.difficulty || 'MEDIUM',
      courseId: row.courseId || filters.courseId || '',
      content: row.content || row.stem || '',
      answer: row.answer || '',
      explanation: row.explanation || row.analysis || '',
    })
    formVisible.value = true
  }

  async function submitForm() {
    if (!formRef.value) return
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    submitting.value = true
    try {
      const payload: Record<string, unknown> = {
        type: form.type,
        difficulty: form.difficulty,
        courseId: form.courseId || undefined,
        content: form.content,
        answer: form.answer,
        explanation: form.explanation,
      }
      if (isEdit.value) {
        await questionApi.updateQuestion(editingId.value, payload)
        ElMessage.success('题目已更新')
      } else {
        await questionApi.createQuestion(payload)
        ElMessage.success('题目已创建')
      }
      formVisible.value = false
      loadQuestions()
    } catch (e) {
      logger.error('保存题目失败', e)
      ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
      submitting.value = false
    }
  }

  async function handleDelete(row) {
    try {
      await ElMessageBox.confirm(`确定删除该题目？此操作不可恢复`, '删除确认', { type: 'warning' })
      await questionApi.deleteQuestion(row.id)
      ElMessage.success('题目已删除')
      loadQuestions()
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
    }
  }

  async function handleCopy(row) {
    try {
      await ElMessageBox.confirm(`确定复制该题目？`, '复制确认', { type: 'info' })
      await questionApi.copyQuestion(row.id)
      ElMessage.success('题目已复制')
      loadQuestions()
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error('复制失败')
    }
  }

  function openReviewDialog(row) {
    reviewRow.value = row
    reviewForm.status = 'APPROVED'
    reviewForm.comment = ''
    reviewVisible.value = true
  }

  async function submitReview() {
    if (!reviewRow.value) return
    reviewing.value = true
    try {
      await questionApi.reviewQuestion(reviewRow.value.id, {
        status: reviewForm.status,
        comment: reviewForm.comment,
      })
      ElMessage.success('审核已提交')
      reviewVisible.value = false
      loadQuestions()
    } catch (e) {
      logger.error('审核失败', e)
      ElMessage.error(e?.response?.data?.message || '审核失败')
    } finally {
      reviewing.value = false
    }
  }

  function openAIDialog() {
    aiForm.courseId = filters.courseId || ''
    aiForm.type = filters.type || 'SINGLE_CHOICE'
    aiForm.difficulty = filters.difficulty || 'MEDIUM'
    aiForm.knowledgePoints = []
    aiVisible.value = true
  }

  async function generateByAI() {
    aiGenerating.value = true
    try {
      const payload: Record<string, unknown> = {
        courseId: aiForm.courseId || undefined,
        type: aiForm.type,
        difficulty: aiForm.difficulty,
        knowledgePointNames: aiForm.knowledgePoints || [],
      }
      const r = await questionApi.generateByAI(payload)
      const body = unwrap(r)
      const generated = body?.questions || body?.list || body || []
      if (Array.isArray(generated) && generated.length) {
        ElMessage.success(`AI 成功生成 ${generated.length} 道题目`)
        aiVisible.value = false
        loadQuestions()
      } else {
        ElMessage.warning('未生成有效题目')
      }
    } catch (e) {
      logger.error('AI 生成题目失败', e)
      ElMessage.error(e?.response?.data?.message || 'AI 生成失败')
    } finally {
      aiGenerating.value = false
    }
  }

  function getTypeText(type) {
    const m = {
      SINGLE_CHOICE: '单选',
      MULTIPLE_CHOICE: '多选',
      TRUE_FALSE: '判断',
      FILL_BLANK: '填空',
      SHORT_ANSWER: '简答',
      ESSAY: '简答',
      CODING: '编程',
    }
    return m[type] || type || '未知'
  }
  function getTypeTag(type) {
    const m = {
      SINGLE_CHOICE: '',
      MULTIPLE_CHOICE: 'success',
      TRUE_FALSE: 'warning',
      FILL_BLANK: 'info',
      SHORT_ANSWER: '',
      ESSAY: '',
      CODING: 'danger',
    }
    return m[type] || ''
  }
  function getDifficultyText(d) {
    const m = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
    return m[d] || d || '-'
  }
  function getDifficultyColor(d) {
    const m = { EASY: 'var(--ac-success)', MEDIUM: 'var(--ac-warning)', HARD: 'var(--ac-danger)' }
    return m[d] || 'var(--ac-text-muted)'
  }
  function getReviewText(s) {
    const m = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
    return m[s] || '待审核'
  }
  function getReviewTag(s) {
    const m = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
    return m[s] || 'info'
  }

  return {
    loading,
    questionList,
    courseList,
    page,
    pageSize,
    total,
    filters,
    formVisible,
    isEdit,
    editingId,
    submitting,
    formRef,
    form,
    rules,
    reviewVisible,
    reviewing,
    reviewRow,
    reviewForm,
    aiVisible,
    aiGenerating,
    aiForm,
    countByReview,
    handleFilterChange,
    openCreateDialog,
    openEditDialog,
    submitForm,
    handleDelete,
    handleCopy,
    openReviewDialog,
    submitReview,
    openAIDialog,
    generateByAI,
    loadQuestions,
    getTypeText,
    getTypeTag,
    getDifficultyText,
    getDifficultyColor,
    getReviewText,
    getReviewTag,
  }
}
