import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'
import courseApi from '@/api/course'
import request from '@/api/request'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { confirmDanger } from '@/utils/dangerConfirm'

/** 选项标签（A-H） */
export const OPTION_LABELS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

/**
 * 题库管理（管理端）业务逻辑
 * <p>承载题库管理页全部状态与操作：题目列表（筛选/搜索/分页/统计）、题目增删改
 * （选择题选项编排/判断题/填空简答编程题答案处理）、AI 智能出题、题目统计、
 * 易错题榜、批量导出（xlsx 文件流）与批量导入（.xlsx 解析入库）。</p>
 */
export function useQuestions() {
  /* ---- 加载状态 ---- */
  const loading = ref(false)
  const saving = ref(false)
  const aiGenerating = ref(false)

  /* ---- 统计数据 ---- */
  const questionStats = ref([
    { label: '总题目数', value: 0 },
    { label: '今日新增', value: 0 },
    { label: '待审核', value: 0 },
    { label: '易错题数', value: 0 },
  ])

  /* ---- 筛选条件 ---- */
  const filters = reactive({
    keyword: '',
    type: '',
    difficulty: '',
    courseId: '',
  })

  /* ---- 分页 ---- */
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0,
  })

  /* ---- 数据列表 ---- */
  const questionList = ref([])
  const courseList = ref([])

  /* ---- 对话框状态 ---- */
  const showEditDialog = ref(false)
  const showAIDialog = ref(false)
  const showStatsDialog = ref(false)
  const isEditing = ref(false)
  const currentStats = ref(null)
  const editingId = ref(null)

  /* ---- 题目表单 ---- */
  const questionFormRef = ref(null)
  const questionForm = reactive({
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: '',
    content: '',
    options: [
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
    ],
    answer: '',
    explanation: '',
    score: 5,
  })

  const questionRules = {
    type: [{ required: true, message: '请选择题型', trigger: 'change' }],
    difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
    content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
    score: [{ required: true, message: '请设置分值', trigger: 'blur' }],
  }

  /* ---- AI 表单 ---- */
  const aiFormRef = ref(null)
  const aiForm = reactive({
    topic: '',
    type: 'MIXED',
    difficulty: 'MIXED',
    count: 5,
    courseId: '',
    requirements: '',
  })

  const aiRules = {
    topic: [{ required: true, message: '请输入知识点或主题', trigger: 'blur' }],
  }

  /* ---- 课程与题目加载 ---- */

  async function fetchCourses() {
    try {
      courseList.value = await courseApi.getAllCourses()
    } catch (error) {
      logger.warn('获取课程列表失败:', error)
      courseList.value = []
    }
  }

  async function fetchQuestions() {
    loading.value = true
    try {
      const params: Record<string, string | number | undefined> = {
        // 统一为后端实际口径：MyBatis-Plus `Page.of(page,size)` 为 **1 基**
        // （QuestionController `@RequestParam(defaultValue="1") long page`，无 ±1 转换）。
        // 历史缺陷（P1-12）：此处曾发送 `pagination.page - 1`（0 基），
        // 与 useExamPapers 的 1 基实现相互矛盾，且第 1 页会请求到 page=0 的非法页。
        page: pagination.page,
        size: pagination.size,
      }

      if (filters.keyword) params.keyword = filters.keyword
      if (filters.type) params.type = filters.type
      if (filters.difficulty) params.difficulty = filters.difficulty
      if (filters.courseId) params.courseId = filters.courseId

      const res = await questionApi.getQuestionList(params)
      const payload = unwrap(res)
      questionList.value = pickList(payload)
      // 缺陷修复（2026-10-04）：旧实现在裸数组分支把 `total` 写成本页条数，
      // 后端真实总数丢失后分页控件永远只显示 1 页；此处改为后端 total 优先，
      // 仅当响应确实不含总数字段时才退化为当前页长度。
      pagination.total = readTotal(payload, questionList.value.length)

      // 更新统计
      updateQuestionStats()
    } catch (error) {
      logger.error('获取题目列表失败:', error)
      ElMessage.error('加载题目列表失败')
    } finally {
      loading.value = false
    }
  }

  function updateQuestionStats() {
    questionStats.value[0].value = pagination.total
    // 其他统计数据可以从API获取或计算
  }

  /* ---- 创建/编辑题目 ---- */

  function openCreateDialog() {
    isEditing.value = false
    editingId.value = null
    Object.assign(questionForm, {
      type: 'SINGLE_CHOICE',
      difficulty: 'MEDIUM',
      courseId: '',
      content: '',
      options: [
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
      ],
      answer: '',
      explanation: '',
      score: 5,
    })
    showEditDialog.value = true
  }

  function editQuestion(row) {
    isEditing.value = true
    editingId.value = row.id

    // 填充表单数据
    Object.assign(questionForm, {
      type: row.type || 'SINGLE_CHOICE',
      difficulty: row.difficulty || 'MEDIUM',
      courseId: row.courseId || '',
      content: row.content || '',
      answer: row.answer || '',
      explanation: row.explanation || '',
      score: row.score || 5,
    })

    // 处理选择题选项
    if (row.options && Array.isArray(row.options)) {
      questionForm.options = row.options.map((opt) => ({
        content: opt.content || opt.text || '',
        isCorrect: opt.isCorrect === true || opt.isAnswer === true,
      }))
    } else {
      questionForm.options = [
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
      ]
    }

    showEditDialog.value = true
  }

  async function saveQuestion() {
    if (!questionFormRef.value) return

    await questionFormRef.value.validate(async (valid) => {
      if (!valid) return

      saving.value = true
      try {
        const payload: Record<string, unknown> = {
          type: questionForm.type,
          difficulty: questionForm.difficulty,
          courseId: questionForm.courseId,
          content: questionForm.content,
          score: questionForm.score,
          explanation: questionForm.explanation,
        }

        // 根据题型处理答案
        if (isChoiceType(questionForm.type)) {
          payload.options = questionForm.options.map((opt) => opt.content)
          payload.answer = questionForm.options
            .map((opt, idx) => (opt.isCorrect ? OPTION_LABELS[idx] : null))
            .filter(Boolean)
            .join(',')
        } else if (questionForm.type === 'TRUE_FALSE') {
          payload.answer = String(questionForm.answer)
        } else {
          payload.answer = questionForm.answer
        }

        if (isEditing.value && editingId.value) {
          await questionApi.updateQuestion(editingId.value, payload)
          ElMessage.success('更新成功')
        } else {
          await questionApi.createQuestion(payload)
          ElMessage.success('创建成功')
        }

        showEditDialog.value = false
        fetchQuestions()
      } catch (error) {
        logger.error('保存题目失败:', error)
        ElMessage.error('保存失败')
      } finally {
        saving.value = false
      }
    })
  }

  async function deleteQuestion(row) {
    try {
      // D2-2：题干截断进 subject，并明确不可逆性与恢复路径
      const ok = await confirmDanger({
        verb: '删除',
        target: '题目',
        subject: `${row.content.slice(0, 50)}...`,
      })
      if (!ok) return

      await questionApi.deleteQuestion(row.id)
      ElMessage.success('删除成功')
      fetchQuestions()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }

  /* ---- 选项操作 ---- */

  function addOption() {
    questionForm.options.push({ content: '', isCorrect: false })
  }

  function removeOption(index) {
    questionForm.options.splice(index, 1)
  }

  /* ---- AI 智能出题 ---- */

  async function generateByAI() {
    if (!aiFormRef.value) return

    await aiFormRef.value.validate(async (valid) => {
      if (!valid) return

      aiGenerating.value = true
      try {
        const payload: Record<string, unknown> = {
          topic: aiForm.topic,
          type: aiForm.type,
          difficulty: aiForm.difficulty,
          count: aiForm.count,
          courseId: aiForm.courseId || undefined,
          requirements: aiForm.requirements || undefined,
        }

        const res = await questionApi.generateByAI(payload)
        const generated = res.data?.questions || res.data || []

        if (Array.isArray(generated) && generated.length > 0) {
          ElMessage.success(`成功生成 ${generated.length} 道题目`)
          showAIDialog.value = false
          fetchQuestions()
        } else {
          ElMessage.warning('未生成有效题目')
        }
      } catch (error) {
        logger.error('AI生成题目失败:', error)
        ElMessage.error('AI生成失败，请稍后重试')
      } finally {
        aiGenerating.value = false
      }
    })
  }

  /* ---- 统计 / 易错题 / 导出 / 导入 ---- */

  async function viewStats(row) {
    showStatsDialog.value = true
    currentStats.value = null

    try {
      const res = await request.get(`/questions/${row.id}/stats`)
      currentStats.value = res.data || res
    } catch (error) {
      logger.warn('获取题目统计失败:', error)
      // API 不可用时使用空数据，不再伪造统计值
      currentStats.value = {
        usageCount: 0,
        correctRate: null,
        avgTime: '--',
        errorCount: 0,
        recentUsage: [],
      }
    }
  }

  async function viewErrorProne() {
    ElMessage.info('正在加载易错题榜单...')
    try {
      const res = await questionApi.getErrorProne({ limit: 10 })
      const list = res.data?.list || res.data || res || []
      if (Array.isArray(list) && list.length > 0) {
        ElMessage.success(`获取到 ${list.length} 道易错题`)
      } else {
        ElMessage.info('暂无易错题数据')
      }
    } catch (error) {
      logger.warn('获取易错题失败:', error)
      ElMessage.error('获取易错题失败，请稍后重试')
    }
  }

  /** 批量导出：调用后端 /teacher/questions/export 生成 xlsx 文件流下载（按当前课程筛选） */
  async function handleExport() {
    try {
      ElMessage({ message: '正在导出题库...', duration: 0 })
      const res = await questionApi.exportQuestionsExcel({
        courseId: filters.courseId || undefined,
      })
      ElMessage.closeAll()
      const blob = new Blob([res.data || res], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `题库导出_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } catch (e) {
      ElMessage.closeAll()
      logger.error('导出题库失败:', e)
      ElMessage.error('导出失败，请稍后重试')
    }
  }

  /** 批量导入：选择 .xlsx 文件后调用后端 /teacher/questions/import-excel 解析入库
   * 表头约定：题型/题干/选项(JSON)/正确答案/难度/分值/知识点/解析 */
  function handleImport() {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.xlsx'
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      try {
        ElMessage({ message: '正在导入题目...', duration: 0 })
        const formData = new FormData()
        formData.append('file', file)
        if (filters.courseId) formData.append('courseId', filters.courseId)
        const res = await questionApi.importQuestionsExcel(formData)
        ElMessage.closeAll()
        const result = res.data || {}
        ElMessage.success(
          `导入完成：成功 ${result.importedCount ?? 0} / 共 ${result.total ?? 0} 题`,
        )
        fetchQuestions()
      } catch (err) {
        ElMessage.closeAll()
        logger.error('导入题目失败:', err)
        ElMessage.error('导入失败，请检查文件格式（.xlsx，8 列表头）')
      }
    }
    input.click()
  }

  /* ---- 展示辅助 ---- */

  function isChoiceType(type) {
    return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)
  }

  function getTypeText(type) {
    const map = {
      SINGLE_CHOICE: '单选',
      MULTIPLE_CHOICE: '多选',
      TRUE_FALSE: '判断',
      FILL_BLANK: '填空',
      SHORT_ANSWER: '简答',
      CODING: '编程',
    }
    return map[type] || type
  }

  function getTypeTagType(type) {
    const map = {
      SINGLE_CHOICE: '',
      MULTIPLE_CHOICE: 'success',
      TRUE_FALSE: 'warning',
      FILL_BLANK: 'info',
      SHORT_ANSWER: '',
      CODING: 'danger',
    }
    return map[type] || ''
  }

  function getDifficultyValue(difficulty) {
    const map = { EASY: 1, MEDIUM: 2, HARD: 3 }
    return map[difficulty] || 1
  }

  function getAccuracyClass(rate) {
    if (rate == null) return ''
    if (rate >= 0.8) return 'high'
    if (rate >= 0.6) return 'medium'
    return 'low'
  }

  /* ---- 初始化 ---- */

  onMounted(() => {
    fetchCourses()
    fetchQuestions()
  })

  return {
    loading,
    saving,
    aiGenerating,
    questionStats,
    filters,
    pagination,
    questionList,
    courseList,
    showEditDialog,
    showAIDialog,
    showStatsDialog,
    isEditing,
    currentStats,
    editingId,
    questionFormRef,
    questionForm,
    questionRules,
    aiFormRef,
    aiForm,
    aiRules,
    fetchCourses,
    fetchQuestions,
    openCreateDialog,
    editQuestion,
    saveQuestion,
    deleteQuestion,
    addOption,
    removeOption,
    generateByAI,
    viewStats,
    viewErrorProne,
    handleExport,
    handleImport,
    isChoiceType,
    getTypeText,
    getTypeTagType,
    getDifficultyValue,
    getAccuracyClass,
  }
}
