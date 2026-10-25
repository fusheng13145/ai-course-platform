import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'
import courseApi from '@/api/course'
import request from '@/api/request'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { useQuestionEditDialog } from './useQuestionEditDialog'
import { useQuestionAiGenerate } from './useQuestionAiGenerate'
import { useQuestionTransfer } from './useQuestionTransfer'

// 选项标签经编排层重导出，消费方（页面与学生端作答组件）的导入路径不变
export { OPTION_LABELS } from './useQuestionsShared'

/**
 * 题库管理（管理端）业务逻辑（编排层）
 * <p>承载题目列表（筛选/搜索/分页/统计）、课程加载、题目统计与易错题榜、展示辅助；
 * 增删改对话框拆见 useQuestionEditDialog，AI 出题拆见 useQuestionAiGenerate，
 * 批量导入导出拆见 useQuestionTransfer。</p>
 */
export function useQuestions() {
  /* ---- 加载状态 ---- */
  const loading = ref(false)

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

  /* ---- 统计对话框 ---- */
  const showStatsDialog = ref(false)
  const currentStats = ref(null)

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

  const editDialog = useQuestionEditDialog({ fetchQuestions })
  const aiGenerate = useQuestionAiGenerate({ fetchQuestions })
  const transfer = useQuestionTransfer({ filters, fetchQuestions })

  /* ---- 统计 / 易错题 ---- */

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

  /* ---- 展示辅助 ---- */

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
    saving: editDialog.saving,
    aiGenerating: aiGenerate.aiGenerating,
    questionStats,
    filters,
    pagination,
    questionList,
    courseList,
    showEditDialog: editDialog.showEditDialog,
    showAIDialog: aiGenerate.showAIDialog,
    showStatsDialog,
    isEditing: editDialog.isEditing,
    currentStats,
    editingId: editDialog.editingId,
    questionFormRef: editDialog.questionFormRef,
    questionForm: editDialog.questionForm,
    questionRules: editDialog.questionRules,
    aiFormRef: aiGenerate.aiFormRef,
    aiForm: aiGenerate.aiForm,
    aiRules: aiGenerate.aiRules,
    fetchCourses,
    fetchQuestions,
    openCreateDialog: editDialog.openCreateDialog,
    editQuestion: editDialog.editQuestion,
    saveQuestion: editDialog.saveQuestion,
    deleteQuestion: editDialog.deleteQuestion,
    addOption: editDialog.addOption,
    removeOption: editDialog.removeOption,
    generateByAI: aiGenerate.generateByAI,
    viewStats,
    viewErrorProne,
    handleExport: transfer.handleExport,
    handleImport: transfer.handleImport,
    isChoiceType: editDialog.isChoiceType,
    getTypeText,
    getTypeTagType,
    getDifficultyValue,
    getAccuracyClass,
  }
}
