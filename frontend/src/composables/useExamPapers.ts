import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import examApi from '@/api/exam'
import courseApi from '@/api/course'

/**
 * 试卷管理页数据逻辑（拆分自 teacher/assessment/ExamPapers.vue，2026-08-29）
 */
export function useExamPapers() {
  const loading = ref(false)
  const examList = ref([])
  const courseList = ref([])
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const filters = reactive({ courseId: '' })

  const formVisible = ref(false)
  const isEdit = ref(false)
  const editingId = ref(null)
  const submitting = ref(false)
  const form = reactive({ courseId: '', title: '', description: '', duration: 90, totalScore: 100 })
  const rules = {
    courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
    title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
    duration: [{ required: true, message: '请设置考试时长', trigger: 'blur' }],
    totalScore: [{ required: true, message: '请设置总分', trigger: 'blur' }],
  }

  const questionsVisible = ref(false)
  const currentExam = ref(null)
  const examQuestions = ref([])

  const statsVisible = ref(false)
  const statsLoading = ref(false)
  const examStats = ref(null)

  function countBy(status) {
    return examList.value.filter((e) => e.status === status).length
  }

  async function loadCourses() {
    try {
      courseList.value = await courseApi.getAllMyCourses()
    } catch (e) {
      logger.error('加载课程失败', e)
    }
  }

  async function loadExams() {
    loading.value = true
    try {
      const params: Record<string, unknown> = { page: page.value, size: pageSize.value }
      if (filters.courseId) params.courseId = filters.courseId
      const r = await examApi.getExamPaperList(params)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      examList.value = Array.isArray(list) ? list : []
      total.value = body?.total ?? body?.count ?? examList.value.length
    } catch (e) {
      logger.error('加载试卷列表失败', e)
      ElMessage.error('加载试卷列表失败')
    } finally {
      loading.value = false
    }
  }

  function handleFilterChange() {
    page.value = 1
    loadExams()
  }

  function resetForm() {
    Object.assign(form, {
      courseId: filters.courseId || '',
      title: '',
      description: '',
      duration: 90,
      totalScore: 100,
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
      courseId: row.courseId || filters.courseId || '',
      title: row.title || '',
      description: row.description || '',
      duration: row.duration || 90,
      totalScore: row.totalScore || 100,
    })
    formVisible.value = true
  }

  async function submitForm(formRef) {
    if (!formRef) return
    const valid = await formRef.validate().catch(() => false)
    if (!valid) return
    submitting.value = true
    try {
      const payload: Record<string, unknown> = {
        courseId: form.courseId,
        title: form.title,
        description: form.description,
        duration: form.duration,
        totalScore: form.totalScore,
      }
      if (isEdit.value) {
        await examApi.updateExamPaper(editingId.value, payload)
        ElMessage.success('试卷已更新')
      } else {
        await examApi.createExamPaper(payload)
        ElMessage.success('试卷已创建')
      }
      formVisible.value = false
      loadExams()
    } catch (e) {
      logger.error('保存试卷失败', e)
      ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
      submitting.value = false
    }
  }

  async function handlePublish(row) {
    try {
      await ElMessageBox.confirm(`确定发布试卷「${row.title}」？`, '发布确认', { type: 'info' })
      await examApi.publishExam(row.id)
      ElMessage.success('试卷已发布')
      loadExams()
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.message || '发布失败')
    }
  }

  async function handleDelete(row) {
    try {
      await ElMessageBox.confirm(`确定删除试卷「${row.title}」？此操作不可恢复`, '删除确认', {
        type: 'warning',
      })
      await examApi.deleteExamPaper(row.id)
      ElMessage.success('试卷已删除')
      loadExams()
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
    }
  }

  async function openQuestionsDialog(row) {
    currentExam.value = row
    examQuestions.value = row.questions ? row.questions : []
    questionsVisible.value = true
    if (!examQuestions.value.length) {
      try {
        const r = await examApi.getExamQuestions(row.id)
        const body = unwrap(r)
        examQuestions.value = Array.isArray(body) ? body : body?.list || body?.records || []
      } catch (e) {
        logger.error('加载题目失败', e)
        examQuestions.value = []
      }
    }
  }

  async function openStatsDialog(row) {
    statsVisible.value = true
    statsLoading.value = true
    examStats.value = null
    try {
      const r = await examApi.getExamStatistics(row.id)
      const body = unwrap(r)
      examStats.value = body && typeof body === 'object' ? body : null
    } catch (e) {
      logger.error('加载统计失败', e)
      examStats.value = null
    } finally {
      statsLoading.value = false
    }
  }

  onMounted(() => {
    loadCourses()
    loadExams()
  })

  return {
    loading,
    examList,
    courseList,
    page,
    pageSize,
    total,
    filters,
    formVisible,
    isEdit,
    submitting,
    form,
    rules,
    questionsVisible,
    currentExam,
    examQuestions,
    statsVisible,
    statsLoading,
    examStats,
    countBy,
    loadExams,
    handleFilterChange,
    openCreateDialog,
    openEditDialog,
    submitForm,
    handlePublish,
    handleDelete,
    openQuestionsDialog,
    openStatsDialog,
  }
}
