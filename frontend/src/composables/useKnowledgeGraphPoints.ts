import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import knowledgeApi from '@/api/knowledge'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 知识图谱（教师）知识点域
 * <p>承载知识点列表加载、新建/编辑/删除（CRUD）与 AI 文本抽取抽取结果展示；
 * 课程 id 与跨域刷新动作（概览/图谱）由编排层注入，CRUD 与抽取成功后置位
 * 引擎再同步标记（engineSyncStale，所有权在引擎域）。</p>
 */
export function useKnowledgeGraphPoints(deps) {
  const { courseId, engineSyncStale, loadOverview, loadGraph } = deps

  /* ---- 知识点列表 ---- */
  const pointLoading = ref(false)
  const points = ref([])
  const pointsError = ref('')
  async function loadPoints() {
    if (!courseId.value) return
    pointLoading.value = true
    pointsError.value = ''
    try {
      const r = await knowledgeApi.getKnowledgePoints(courseId.value)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      points.value = (Array.isArray(list) ? list : []).map((k) => ({
        ...k,
        importance: k.importance ?? k.weight ?? 1,
      }))
    } catch (e) {
      logger.error('加载知识点失败', e)
      points.value = []
      pointsError.value = '知识点列表加载失败，请检查网络后重试'
    } finally {
      pointLoading.value = false
    }
  }

  /* ---- 新建/编辑 ---- */
  const formDialog = ref(false)
  const isEdit = ref(false)
  const formSubmitting = ref(false)
  const editingId = ref(null)
  const kpForm = reactive({ name: '', description: '', difficulty: 'MEDIUM', importance: 2 })

  function openForm(row?: {
    id?: number | string
    name?: string
    description?: string
    difficulty?: string
    importance?: number
  }) {
    if (row) {
      isEdit.value = true
      editingId.value = row.id
      kpForm.name = row.name
      kpForm.description = row.description || ''
      kpForm.difficulty = row.difficulty || 'MEDIUM'
      kpForm.importance = row.importance ?? 2
    } else {
      isEdit.value = false
      editingId.value = null
      kpForm.name = ''
      kpForm.description = ''
      kpForm.difficulty = 'MEDIUM'
      kpForm.importance = 2
    }
    formDialog.value = true
  }

  async function submitForm() {
    if (!kpForm.name.trim()) return ElMessage.warning('请输入名称')
    if (!courseId.value) return ElMessage.warning('请先选择课程')
    formSubmitting.value = true
    try {
      const payload: Record<string, unknown> = {
        name: kpForm.name.trim(),
        description: kpForm.description.trim(),
        difficulty: kpForm.difficulty,
        importance: kpForm.importance,
        courseId: courseId.value,
      }
      if (isEdit.value) await knowledgeApi.updateKnowledgePoint(editingId.value, payload)
      else await knowledgeApi.createKnowledgePoint(payload)
      ElMessage.success(isEdit.value ? '已更新' : '已创建')
      formDialog.value = false
      engineSyncStale.value = true
      loadPoints()
      loadOverview()
    } catch (e) {
      logger.error('保存知识点失败', e)
    } finally {
      formSubmitting.value = false
    }
  }

  async function removePoint(row) {
    try {
      // D2-2：明确连带影响（图谱关联关系一并移除）与不可逆性
      const ok = await confirmDanger({
        verb: '删除',
        target: '知识点',
        subject: row.name,
        impact: '将一并移除该知识点在图谱中的关联关系',
      })
      if (!ok) return
      await knowledgeApi.deleteKnowledgePoint(row.id)
      ElMessage.success('已删除')
      engineSyncStale.value = true
      loadPoints()
      loadOverview()
      loadGraph()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('删除知识点失败', e)
    }
  }

  /* ---- AI 抽取 ---- */
  const extractDialog = ref(false)
  const extractText = ref('')
  const extractLoading = ref(false)
  const extractResult = ref(null)
  const extractRaw = ref('')

  const extractNodes = computed(() => {
    const r = extractResult.value
    return r ? r.nodes || r.points || [] : []
  })
  const extractRelations = computed(() => {
    const r = extractResult.value
    return r ? r.relations || r.links || [] : []
  })
  function nodeName(n) {
    return n?.name || n?.label || n?.text || String(n)
  }
  function relText(r) {
    if (typeof r === 'string') return r
    return `${r.sourceName || r.source || ''} —${r.type || '关联'}→ ${r.targetName || r.target || ''}`
  }

  function openExtractDialog() {
    if (!courseId.value) return ElMessage.warning('请先选择课程')
    extractText.value = ''
    extractResult.value = null
    extractRaw.value = ''
    extractDialog.value = true
  }

  async function runExtract() {
    if (!extractText.value.trim()) return ElMessage.warning('请粘贴课程内容文本')
    if (!courseId.value) return
    extractLoading.value = true
    try {
      const r = await knowledgeApi.extractKnowledgePoints({
        courseId: courseId.value,
        content: extractText.value.trim(),
      })
      const body = unwrap(r)
      const res = (unwrap(body) || {}) as {
        nodeCount?: number
        relationCount?: number
        nodes?: unknown[]
        points?: unknown[]
        relations?: unknown[]
        links?: unknown[]
      }
      extractResult.value = res
      extractRaw.value = JSON.stringify(
        {
          nodeCount: res.nodeCount ?? (res.nodes || res.points || []).length,
          relationCount: res.relationCount ?? (res.relations || res.links || []).length,
        },
        null,
        2,
      )
      ElMessage.success('抽取完成')
      // 抽取结果已真实落库：刷新列表/概览/图谱，教师无需手动刷新即可看到新知识点，
      // 同时置位「建议重新同步引擎」，提示图谱数据已变更。
      engineSyncStale.value = true
      loadPoints()
      loadOverview()
      loadGraph()
    } catch (e) {
      logger.error('AI 抽取失败', e)
    } finally {
      extractLoading.value = false
    }
  }

  return {
    pointLoading,
    points,
    pointsError,
    loadPoints,
    formDialog,
    isEdit,
    formSubmitting,
    editingId,
    kpForm,
    openForm,
    submitForm,
    removePoint,
    extractDialog,
    extractText,
    extractLoading,
    extractResult,
    extractRaw,
    extractNodes,
    extractRelations,
    nodeName,
    relText,
    openExtractDialog,
    runExtract,
  }
}
