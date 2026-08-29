import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import questionApi from '@/api/question'

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

  const countPending = computed(() => list.value.filter((r) => !r.reviewed).length)
  const countReviewed = computed(() => list.value.filter((r) => r.reviewed).length)

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
  const dialog = reactive({ visible: false, editing: false, saving: false })
  const form = reactive({
    id: null,
    type: 'SINGLE',
    difficulty: 'MEDIUM',
    stem: '',
    answer: '',
    analysis: '',
  })

  function resetForm() {
    form.id = null
    form.type = 'SINGLE'
    form.difficulty = 'MEDIUM'
    form.stem = ''
    form.answer = ''
    form.analysis = ''
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
      stem: row.stem || row.title || '',
      answer: row.answer || '',
      analysis: row.analysis || '',
    })
    dialog.editing = true
    dialog.visible = true
  }
  async function save() {
    if (!form.stem.trim()) {
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
          stem: form.stem,
          answer: form.answer,
          analysis: form.analysis,
        })
        ElMessage.success('题目已更新')
      } else {
        await questionApi.createQuestion({
          courseId: courseIdRef.value,
          type: form.type,
          difficulty: form.difficulty,
          stem: form.stem,
          answer: form.answer,
          analysis: form.analysis,
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
      ElMessage.success(approved ? '已审核通过' : '已驳回')
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
        `确认删除题目「${row.stem || row.title || ''}」？此操作不可恢复。`,
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
      await questionApi.generateAndSaveByAI({
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
