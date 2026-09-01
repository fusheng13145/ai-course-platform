import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import courseApi from '@/api/course'
import knowledgeApi from '@/api/knowledge'

/** SVG 图谱画布尺寸与节点半径 */
const GRAPH_SIZE = 420
const CIRCLE_R = 26

/**
 * 知识图谱（教师）数据与业务逻辑
 * <p>承载课程知识图谱页全部状态与操作：课程选择、概览统计、知识点列表 CRUD、
 * AI 文本抽取知识点/关系、SVG 力导向图谱（环形布局 + 路径高亮）、
 * 学习路径规划（调用后端路径引擎，支持本地 BFS 降级标注）。</p>
 */
export function useKnowledgeGraph() {
  const courseLoading = ref(false)
  const courseOptions = ref([])
  const courseId = ref('')
  const loading = ref(false)

  /** 加载课程选项；默认选中第一门 */
  async function loadCourses() {
    courseLoading.value = true
    try {
      const r = await courseApi.getMyCourses()
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      courseOptions.value = Array.isArray(list) ? list : []
      if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
    } catch (e) {
      logger.error('加载课程列表失败', e)
    } finally {
      courseLoading.value = false
    }
  }

  /** 课程切换：清空并重新加载概览/列表/图谱 */
  function onCourseChange() {
    overview.value = {}
    points.value = []
    graphNodes.value = []
    graphLinks.value = []
    if (courseId.value) {
      loadOverview()
      loadPoints()
      loadGraph()
    }
  }

  /** 覆盖率百分比格式化（0-1 小数转百分数） */
  function pct(v) {
    if (v == null) return '--'
    if (typeof v === 'number' && v <= 1.0001 && v >= 0) return Math.round(v * 100) + '%'
    return v + '%'
  }
  function diffType(d) {
    return d === 'HARD' ? 'danger' : d === 'MEDIUM' ? 'warning' : 'success'
  }
  function diffText(d) {
    return d === 'HARD' ? '困难' : d === 'MEDIUM' ? '中等' : '简单'
  }

  /* ---- 概览 ---- */
  const overview = ref({} as any)
  async function loadOverview() {
    if (!courseId.value) return
    try {
      const r = await knowledgeApi.getKnowledgeOverview({ courseId: courseId.value })
      overview.value = unwrap(unwrap(r)) || {}
    } catch (e) {
      logger.error('加载知识概览失败', e)
    }
  }

  /* ---- 知识点列表 ---- */
  const pointLoading = ref(false)
  const points = ref([])
  async function loadPoints() {
    if (!courseId.value) return
    pointLoading.value = true
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

  function openForm(row) {
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
      const payload: Record<string, any> = {
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
      await ElMessageBox.confirm(`确认删除知识点「${row.name}」？`, '提示', { type: 'warning' })
      await knowledgeApi.deleteKnowledgePoint(row.id)
      ElMessage.success('已删除')
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
      const res = unwrap(body) || {}
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
    } catch (e) {
      logger.error('AI 抽取失败', e)
    } finally {
      extractLoading.value = false
    }
  }

  /* ---- SVG 图谱 ---- */
  const graphLoading = ref(false)
  const graphNodes = ref([])
  const graphLinks = ref([])

  /** 环形布局坐标计算 */
  const nodePos = computed(() => {
    const map = {}
    const n = graphNodes.value.length
    const cx = GRAPH_SIZE / 2
    const cy = GRAPH_SIZE / 2
    const radius = GRAPH_SIZE / 2 - 56
    graphNodes.value.forEach((node, i) => {
      const angle = (Math.PI * 2 * i) / Math.max(n, 1) - Math.PI / 2
      map[node.id] = { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) }
    })
    return map
  })

  async function loadGraph() {
    if (!courseId.value) return
    graphLoading.value = true
    try {
      const r = await knowledgeApi.getRelationGraphData(courseId.value)
      const body = unwrap(r)
      const g = unwrap(body) || {}
      const nodes = g.nodes || []
      const links = g.links || g.relations || []
      graphNodes.value = nodes
      graphLinks.value = links
        .map((l) => ({
          source: l.source ?? l.from,
          target: l.target ?? l.to,
        }))
        .filter((l) => nodePos.value[l.source] && nodePos.value[l.target])
    } catch (e) {
      // 端点不可用则忽略可视化，仅保留列表
      logger.warn('图谱端点不可用，已忽略可视化', e)
      graphNodes.value = []
      graphLinks.value = []
    } finally {
      graphLoading.value = false
    }
  }

  /* ---- 学习路径规划 ---- */
  const tPathSource = ref('')
  const tPathTarget = ref('')
  const tPathHops = ref(null)
  const tPathPlanning = ref(false)
  const tPathPlan = ref(null)
  const tPathNotFound = ref(false)
  const tPathError = ref('')

  async function planPathTeacher() {
    tPathNotFound.value = false
    tPathError.value = ''
    if (!tPathSource.value || !tPathTarget.value) {
      tPathError.value = '请先选择起点与目标知识点'
      return
    }
    if (tPathSource.value === tPathTarget.value) {
      tPathError.value = '起点与目标不能相同'
      return
    }
    tPathPlanning.value = true
    tPathPlan.value = null
    graphNodes.value = graphNodes.value.map((n) => ({ ...n, pathHL: false }))
    try {
      const envelope = await knowledgeApi.planLearningPath(
        tPathSource.value,
        tPathTarget.value,
        tPathHops.value || undefined,
      )
      const vo = envelope?.data || null
      if (vo && vo.found) {
        tPathPlan.value = vo
        const idSet = new Set((vo.path || []).map((n) => n?.id).filter((id) => id != null))
        graphNodes.value = graphNodes.value.map((n) => ({ ...n, pathHL: idSet.has(n.id) }))
      } else if (vo) {
        tPathNotFound.value = true
        tPathError.value = vo.note || '未找到从起点到目标的学习路径'
      }
    } catch (e) {
      const status = e?.response?.status
      const msg = e?.response?.data?.message || ''
      if (status === 404) {
        tPathNotFound.value = true
        tPathError.value = msg || '未找到从起点到目标的学习路径'
      } else {
        tPathError.value = msg || '路径规划失败，请稍后重试'
      }
    } finally {
      tPathPlanning.value = false
    }
  }

  return {
    courseLoading,
    courseOptions,
    courseId,
    loading,
    loadCourses,
    onCourseChange,
    pct,
    diffType,
    diffText,
    overview,
    loadOverview,
    pointLoading,
    points,
    loadPoints,
    formDialog,
    isEdit,
    formSubmitting,
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
    graphLoading,
    graphSize: GRAPH_SIZE,
    circleR: CIRCLE_R,
    graphNodes,
    graphLinks,
    nodePos,
    loadGraph,
    tPathSource,
    tPathTarget,
    tPathHops,
    tPathPlanning,
    tPathPlan,
    tPathNotFound,
    tPathError,
    planPathTeacher,
  }
}
