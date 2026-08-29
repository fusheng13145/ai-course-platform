import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import examApi from '@/api/exam'
import questionApi from '@/api/question'
import courseApi from '@/api/course'
import { getMonoStatus } from '@/utils/chartTheme'

/** 试卷状态/题型/难度文案映射 */
const STATUS_TYPE = { DRAFT: 'info', PUBLISHED: 'success', ENDED: 'danger' }
const STATUS_TEXT = { DRAFT: '草稿', PUBLISHED: '已发布', ENDED: '已结束' }
const QTYPE_NAME = {
  SINGLE_CHOICE: '单选',
  MULTIPLE_CHOICE: '多选',
  TRUE_FALSE: '判断',
  FILL_BLANK: '填空',
  ESSAY: '简答',
}
const DIFFICULTY_TEXT = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }

/**
 * 试卷管理（教师）数据与业务逻辑
 * <p>承载试卷管理页全部状态与操作：试卷列表（筛选/搜索/分页）、试卷创建/编辑
 * （表单 + 题目编排与分值设定）、题库选题（搜索/题型筛选/多选添加）、发布/复制/删除、
 * 试卷详情与题目预览。</p>
 */
export function useTeacherExams() {
  /* ---- 列表状态 ---- */
  const loading = ref(false)
  const examList = ref([])
  const searchText = ref('')
  const statusFilter = ref('')
  const currentPage = ref(1)
  const pageSize = ref(15)
  const total = ref(0)

  /* ---- 表单对话框 ---- */
  const formDialogVisible = ref(false)
  const isEdit = ref(false)
  const editingId = ref(null)
  const submitting = ref(false)
  const formRef = ref(null)
  const courseOptions = ref([])

  const examForm = reactive({
    title: '',
    courseId: null,
    description: '',
    duration: 90,
    totalScore: 100,
    startTime: '',
    endTime: '',
    shuffleQuestions: false,
    shuffleOptions: false,
    showScore: true,
    maxAttempts: 1,
  })
  const formRules = {
    title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
    courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
    duration: [{ required: true, message: '请设置考试时长', trigger: 'blur' }],
    totalScore: [{ required: true, message: '请设置总分', trigger: 'blur' }],
  }

  /* ---- 已选题目 ---- */
  const selectedQuestions = ref([])
  const totalSelectedScore = computed(() =>
    selectedQuestions.value.reduce((sum, q) => sum + (q.score || 0), 0),
  )
  const draftCount = computed(() => examList.value.filter((e) => e.status === 'DRAFT').length)
  const publishedCount = computed(() => examList.value.filter((e) => e.status === 'PUBLISHED').length)

  /* ---- 题库选择器 ---- */
  const questionPickerVisible = ref(false)
  const questionLoading = ref(false)
  const questionBank = ref([])
  const questionSearch = ref('')
  const questionTypeFilter = ref('')
  const tempSelectedQuestions = ref([])
  const filteredQuestionBank = computed(() => questionBank.value)

  /* ---- 详情对话框 ---- */
  const detailDialogVisible = ref(false)
  const currentExam = ref(null)
  const examDetailQuestions = ref([])

  /** 加载试卷列表（分页/关键字/状态） */
  async function loadExamList() {
    loading.value = true
    try {
      const params = {
        page: currentPage.value,
        size: pageSize.value,
        keyword: searchText.value || undefined,
        status: statusFilter.value || undefined,
      }
      const res = await examApi.getExamPaperList(params)
      const rawData = res.data?.records || res.data?.list || res.data || res
      const data = Array.isArray(rawData) ? { list: rawData } : rawData || {}
      examList.value = Array.isArray(data.list) ? data.list : Array.isArray(rawData) ? rawData : []
      total.value = data.total ?? data.count ?? examList.value.length
    } catch (e) {
      logger.error('加载试卷列表失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 加载课程下拉选项 */
  async function loadCourseOptions() {
    try {
      const res = await courseApi.getMyCourses()
      const data = res.data?.list || res.data || res || []
      courseOptions.value = Array.isArray(data) ? data : []
    } catch (e) {
      logger.error('加载课程失败:', e)
    }
  }

  /** 打开创建对话框（重置表单与题目） */
  function openCreateDialog() {
    isEdit.value = false
    editingId.value = null
    Object.assign(examForm, {
      title: '',
      courseId: null,
      description: '',
      duration: 90,
      totalScore: 100,
      startTime: '',
      endTime: '',
      shuffleQuestions: false,
      shuffleOptions: false,
      showScore: true,
      maxAttempts: 1,
    })
    selectedQuestions.value = []
    formDialogVisible.value = true
  }

  /** 打开编辑对话框（回填表单与已有题目） */
  function openEditDialog(row) {
    isEdit.value = true
    editingId.value = row.id
    Object.assign(examForm, {
      title: row.title || '',
      courseId: row.courseId || null,
      description: row.description || '',
      duration: row.duration || 90,
      totalScore: row.totalScore || 100,
      startTime: row.startTime || '',
      endTime: row.endTime || '',
      shuffleQuestions: row.shuffleQuestions || false,
      shuffleOptions: row.shuffleOptions || false,
      showScore: row.showScore !== false,
      maxAttempts: row.maxAttempts || 1,
    })
    selectedQuestions.value = row.questions ? row.questions.map((q) => ({ ...q })) : []
    formDialogVisible.value = true
  }

  /** 提交表单：校验 + 至少一题 + 题目分值/顺序打包 */
  async function submitForm() {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
      if (!valid) return
      if (selectedQuestions.value.length === 0) {
        ElMessage.warning('请至少添加一道题目')
        return
      }
      submitting.value = true
      try {
        const payload = {
          ...examForm,
          questions: selectedQuestions.value.map((q, idx) => ({
            questionId: q.id,
            score: q.score || q.defaultScore || 5,
            order: idx + 1,
          })),
        }
        if (isEdit.value) {
          await examApi.updateExamPaper(editingId.value, payload)
          ElMessage.success('更新成功')
        } else {
          await examApi.createExamPaper(payload)
          ElMessage.success('创建成功')
        }
        formDialogVisible.value = false
        loadExamList()
      } catch (e) {
        ElMessage.error(e.response?.data?.message || '操作失败')
      } finally {
        submitting.value = false
      }
    })
  }

  /** 加载题库（搜索/筛选时用 search API，否则列表 API） */
  async function loadQuestionBank() {
    questionLoading.value = true
    try {
      const params = { size: 100 }
      if (questionSearch.value) params.keyword = questionSearch.value
      if (questionTypeFilter.value) params.type = questionTypeFilter.value
      let res
      if (params.keyword || params.type) {
        res = await questionApi.searchQuestions(params)
      } else {
        res = await questionApi.getQuestionList(params)
      }
      const data = res.data?.list || res.data || res || []
      questionBank.value = Array.isArray(data) ? data : []
    } catch (e) {
      logger.error('加载题库失败:', e)
      try {
        const res = await questionApi.getQuestionList({ size: 100 })
        const data = res.data?.list || res.data || res || []
        questionBank.value = Array.isArray(data) ? data : []
      } catch (e2) {
        questionBank.value = []
      }
    } finally {
      questionLoading.value = false
    }
  }

  function handleQuestionSelectionChange(rows) {
    tempSelectedQuestions.value = rows
  }

  function isQuestionSelected(id) {
    return selectedQuestions.value.some((q) => q.id === id)
  }

  /** 单题直接添加 */
  function addSingleQuestion(q) {
    if (isQuestionSelected(q.id)) return
    selectedQuestions.value.push({ ...q, score: q.defaultScore || 5 })
    ElMessage.success(`已添加: ${q.content?.slice(0, 20)}...`)
  }

  function removeQuestion(idx) {
    selectedQuestions.value.splice(idx, 1)
  }

  /** 批量添加勾选题目（先计数后清空，避免提示恒为 0） */
  function confirmAddQuestions() {
    for (const q of tempSelectedQuestions.value) {
      if (!isQuestionSelected(q.id)) {
        selectedQuestions.value.push({ ...q, score: q.defaultScore || 5 })
      }
    }
    const addedCount = tempSelectedQuestions.value.length
    tempSelectedQuestions.value = []
    questionPickerVisible.value = false
    ElMessage.success(`已添加 ${addedCount} 道题目`)
  }

  /** 查看详情并加载题目预览 */
  async function viewDetail(row) {
    currentExam.value = row
    detailDialogVisible.value = true
    examDetailQuestions.value = []
    try {
      const res = await examApi.getExamQuestions(row.id)
      const data = res.data || res || []
      examDetailQuestions.value = Array.isArray(data) ? data : []
    } catch (e) {
      logger.error('加载题目详情失败:', e)
    }
  }

  async function handlePublish(row) {
    try {
      await ElMessageBox.confirm(`确定发布试卷「${row.title}」？`, '发布确认', { type: 'info' })
      await examApi.publishExam(row.id)
      ElMessage.success('发布成功')
      loadExamList()
      if (detailDialogVisible.value) detailDialogVisible.value = false
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '发布失败')
    }
  }

  async function handleCopy(row) {
    try {
      await ElMessageBox.confirm(`确定复制试卷「${row.title}」？`, '复制确认', { type: 'info' })
      await examApi.copyExam(row.id, { title: `${row.title}(副本)` })
      ElMessage.success('复制成功，已创建副本')
      loadExamList()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '复制失败')
    }
  }

  async function handleDelete(row) {
    try {
      await ElMessageBox.confirm(`确定删除试卷「${row.title}」？此操作不可恢复`, '删除确认', {
        type: 'warning',
      })
      await examApi.deleteExamPaper(row.id)
      ElMessage.success('删除成功')
      loadExamList()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }

  /* ---- 文案工具 ---- */
  function getStatusType(status) {
    return STATUS_TYPE[status] || 'info'
  }
  function getStatusText(status) {
    return STATUS_TEXT[status] || status
  }
  function getQuestionTypeName(type) {
    return QTYPE_NAME[type] || type || '未知'
  }
  function getDifficultyColor(diff) {
    return getMonoStatus()[diff] || 'var(--ink-2)'
  }
  function getDifficultyText(diff) {
    return DIFFICULTY_TEXT[diff] || diff || '-'
  }

  return {
    loading,
    examList,
    searchText,
    statusFilter,
    currentPage,
    pageSize,
    total,
    formDialogVisible,
    isEdit,
    submitting,
    formRef,
    courseOptions,
    examForm,
    formRules,
    selectedQuestions,
    totalSelectedScore,
    draftCount,
    publishedCount,
    questionPickerVisible,
    questionLoading,
    questionBank,
    questionSearch,
    questionTypeFilter,
    tempSelectedQuestions,
    filteredQuestionBank,
    detailDialogVisible,
    currentExam,
    examDetailQuestions,
    loadExamList,
    loadCourseOptions,
    openCreateDialog,
    openEditDialog,
    submitForm,
    loadQuestionBank,
    handleQuestionSelectionChange,
    isQuestionSelected,
    addSingleQuestion,
    removeQuestion,
    confirmAddQuestions,
    viewDetail,
    handlePublish,
    handleCopy,
    handleDelete,
    getStatusType,
    getStatusText,
    getQuestionTypeName,
    getDifficultyColor,
    getDifficultyText,
  }
}
