import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import questionApi from '@/api/question'
import { resolveReviewStatus } from '@/composables/useQuestionBank'

/**
 * 课程题库页数据逻辑（拆分自 teacher/cockpit/CourseQuestionBank.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseQuestionBank(courseIdRef) {
  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)

  const TYPE_OPTS = [
    { value: 'SINGLE', label: '单选题' },
    { value: 'MULTIPLE', label: '多选题' },
    { value: 'JUDGE', label: '判断题' },
    { value: 'FILL', label: '填空题' },
    { value: 'ESSAY', label: '简答题' },
    { value: 'CODE', label: '编程题' },
  ]
  const DIFF_OPTS = [
    { value: 'EASY', label: '简单' },
    { value: 'MEDIUM', label: '中等' },
    { value: 'HARD', label: '困难' },
  ]

  const filters = reactive({ type: '', difficulty: '', keyword: '' })

  // 审核态统一由 resolveReviewStatus 从 is_active 推导（缺陷 B5，同 2026-09-21 P1-1 口径）。
  // Question 实体没有 reviewed 字段，直接读它会让统计卡恒为「全量待审核 / 已审核 0」。
  const countPending = computed(
    () => list.value.filter((r) => resolveReviewStatus(r) === 'PENDING').length,
  )
  const countReviewed = computed(
    () => list.value.filter((r) => resolveReviewStatus(r) === 'APPROVED').length,
  )

  function typeLabel(t) {
    return TYPE_OPTS.find((o) => o.value === t)?.label || t || '未知'
  }
  function diffLabel(d) {
    return DIFF_OPTS.find((o) => o.value === d)?.label || d || '未知'
  }
  function typeTag(t) {
    return (
      {
        SINGLE: 'primary',
        MULTIPLE: 'success',
        JUDGE: 'warning',
        FILL: 'info',
        ESSAY: '',
        CODE: 'danger',
      }[t] || 'info'
    )
  }
  function diffTag(d) {
    return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
  }

  async function load() {
    loading.value = true
    try {
      const r = await questionApi.getQuestionList({
        courseId: courseIdRef.value,
        page: page.value,
        size: size.value,
        type: filters.type || undefined,
        difficulty: filters.difficulty || undefined,
        keyword: filters.keyword || undefined,
      })
      const body = unwrap(r)
      list.value = body?.records || body?.list || body || []
      total.value = body?.total ?? body?.totalElements ?? list.value.length
    } catch (e) {
      logger.error('加载题库失败', e)
    } finally {
      loading.value = false
    }
  }

  function onFilter() {
    page.value = 1
    load()
  }
  function onSizeChange() {
    page.value = 1
    load()
  }
  function resetFilter() {
    filters.type = ''
    filters.difficulty = ''
    filters.keyword = ''
    page.value = 1
    load()
  }

  // 新建 / 编辑
  // 字段名对齐后端契约（fix-1a）：POST/PUT 绑的是 entity/Question（QuestionController.java:304/:319），
  // 题干=content（@NotBlank，缺失即 400）、答案=correctAnswers、解析=explanation；
  // 旧写法用的 stem/answer/analysis 在实体上不存在，会被 Jackson 静默丢弃。
  const dialog = reactive({ visible: false, editing: false, saving: false })
  const form = reactive({
    id: null,
    type: 'SINGLE',
    difficulty: 'MEDIUM',
    content: '',
    correctAnswers: '',
    explanation: '',
  })

  function resetForm() {
    form.id = null
    form.type = 'SINGLE'
    form.difficulty = 'MEDIUM'
    form.content = ''
    form.correctAnswers = ''
    form.explanation = ''
    dialog.editing = false
  }
  function openCreate() {
    resetForm()
    dialog.editing = false
    dialog.visible = true
  }
  function openEdit(row) {
    resetForm()
    Object.assign(form, {
      id: row.id,
      type: row.type,
      difficulty: row.difficulty,
      // /questions 列表直接返回实体（QuestionController.java:65-68），故 content/correctAnswers/
      // explanation 优先，旧键 stem/answer/analysis 仅作兜底（同仓写法见 QbQuestionTable.vue:6）。
      content: row.content || row.stem || row.title || '',
      correctAnswers: row.correctAnswers || row.answer || '',
      explanation: row.explanation || row.analysis || '',
    })
    dialog.editing = true
    dialog.visible = true
  }
  async function save() {
    if (!form.content.trim()) {
      ElMessage.warning('请输入题干')
      return
    }
    dialog.saving = true
    try {
      if (dialog.editing) {
        await questionApi.updateQuestion(form.id, {
          courseId: courseIdRef.value,
          type: form.type,
          difficulty: form.difficulty,
          content: form.content,
          correctAnswers: form.correctAnswers,
          explanation: form.explanation,
        })
        ElMessage.success('题目已更新')
      } else {
        await questionApi.createQuestion({
          courseId: courseIdRef.value,
          type: form.type,
          difficulty: form.difficulty,
          content: form.content,
          correctAnswers: form.correctAnswers,
          explanation: form.explanation,
        })
        ElMessage.success('题目已创建')
      }
      dialog.visible = false
      load()
    } catch (e) {
      logger.error('保存题目失败', e)
    } finally {
      dialog.saving = false
    }
  }

  async function review(row, approved) {
    try {
      await questionApi.reviewQuestion(row.id, { approved })
      // 驳回写的是 is_active=false，行上回显为「待审核」，提示语按真实结果说而不是造一个已驳回态
      ElMessage.success(approved ? '已审核通过' : '已驳回（该题转入待审核）')
      load()
    } catch (e) {
      logger.error('审核题目失败', e)
    }
  }

  async function copy(row) {
    try {
      await questionApi.copyQuestion(row.id)
      ElMessage.success('已复制题目')
      load()
    } catch (e) {
      logger.error('复制题目失败', e)
    }
  }

  async function remove(row) {
    try {
      await ElMessageBox.confirm(
        `确认删除题目「${row.content || row.stem || row.title || ''}」？此操作不可恢复。`,
        '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
      await questionApi.deleteQuestion(row.id)
      ElMessage.success('题目已删除')
      load()
    } catch (e) {
      if (e !== 'cancel' && e?.toLowerCase) logger.error('删除题目失败', e)
    }
  }

  // AI 生成
  const ai = reactive({ visible: false, loading: false })
  const aiForm = reactive({ type: 'SINGLE', difficulty: 'MEDIUM' })
  const kpText = ref('')
  function resetAIForm() {
    aiForm.type = 'SINGLE'
    aiForm.difficulty = 'MEDIUM'
    kpText.value = ''
  }
  function openAIDialog() {
    resetAIForm()
    ai.visible = true
  }
  async function aiGenerate() {
    ai.loading = true
    try {
      const knowledgePointNames = kpText.value
        .split(/[,，]/)
        .map((s) => s.trim())
        .filter(Boolean)
      await questionApi.generateByAI({
        courseId: courseIdRef.value,
        type: aiForm.type,
        difficulty: aiForm.difficulty,
        knowledgePointNames,
      })
      ElMessage.success('AI 已生成题目')
      ai.visible = false
      load()
    } catch (e) {
      logger.error('AI 生成题目失败', e)
    } finally {
      ai.loading = false
    }
  }

  onMounted(load)
  watch(
    () => courseIdRef.value,
    () => {
      page.value = 1
      load()
    },
  )

  return {
    loading,
    list,
    total,
    page,
    size,
    TYPE_OPTS,
    DIFF_OPTS,
    filters,
    countPending,
    countReviewed,
    dialog,
    form,
    ai,
    aiForm,
    kpText,
    typeLabel,
    diffLabel,
    typeTag,
    diffTag,
    load,
    onFilter,
    onSizeChange,
    resetFilter,
    // 弹窗 @closed 需要它（CourseQuestionBank.vue:100）：曾经只写了函数体没导出，
    // 导致关闭后 dialog.editing 与表单残留、并产生 Vue warn。
    resetForm,
    openCreate,
    openEdit,
    save,
    review,
    copy,
    remove,
    resetAIForm,
    openAIDialog,
    aiGenerate,
  }
}
