import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import courseApi from '@/api/course'
import knowledgeApi from '@/api/knowledge'

/** SVG 图谱画布尺寸（力导向布局坐标系） */
const GRAPH_SIZE = 640

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
  const overview = ref<Record<string, unknown>>({})
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

  /** 轻量力导向布局：斥力 + 弹簧 + 向心，迭代收敛后输出坐标（确定性初始化） */
  function computeLayout(nodes, links) {
    const n = nodes.length
    if (!n) return {}
    const cx = GRAPH_SIZE / 2
    const cy = GRAPH_SIZE / 2
    const ids = nodes.map((x) => x.id)
    const pos = {}
    // 黄金角螺旋初始布局：确定性且避免节点重合
    nodes.forEach((x, i) => {
      const a = i * 2.399963229728653
      const rr = 24 + 14 * Math.sqrt(i)
      pos[x.id] = { x: cx + rr * Math.cos(a), y: cy + rr * Math.sin(a), vx: 0, vy: 0 }
    })
    const k = Math.sqrt((GRAPH_SIZE * GRAPH_SIZE) / n) * 0.7
    const iterations = 320
    for (let it = 0; it < iterations; it++) {
      const temp = 30 * (1 - it / iterations) + 1.5
      for (let i = 0; i < n; i++) {
        const a = pos[ids[i]]
        for (let j = i + 1; j < n; j++) {
          const b = pos[ids[j]]
          let dx = a.x - b.x
          let dy = a.y - b.y
          let d = Math.hypot(dx, dy)
          if (d < 0.01) {
            dx = 0.01 * (i - j || 1)
            dy = 0.01
            d = Math.hypot(dx, dy)
          }
          const rep = (k * k) / d
          const ux = dx / d
          const uy = dy / d
          a.vx += ux * rep
          a.vy += uy * rep
          b.vx -= ux * rep
          b.vy -= uy * rep
        }
      }
      links.forEach((l) => {
        const a = pos[l.source]
        const b = pos[l.target]
        if (!a || !b) return
        const dx = a.x - b.x
        const dy = a.y - b.y
        const d = Math.max(0.01, Math.hypot(dx, dy))
        const att = (d * d) / k
        const ux = dx / d
        const uy = dy / d
        a.vx -= ux * att
        a.vy -= uy * att
        b.vx += ux * att
        b.vy += uy * att
      })
      ids.forEach((id) => {
        const p = pos[id]
        p.vx += (cx - p.x) * 0.03
        p.vy += (cy - p.y) * 0.03
        const v = Math.hypot(p.vx, p.vy) || 0.001
        const step = Math.min(v, temp)
        p.x += (p.vx / v) * step
        p.y += (p.vy / v) * step
        p.x = Math.max(28, Math.min(GRAPH_SIZE - 28, p.x))
        p.y = Math.max(28, Math.min(GRAPH_SIZE - 28, p.y))
        p.vx *= 0.82
        p.vy *= 0.82
      })
    }
    const out = {}
    ids.forEach((id) => {
      out[id] = { x: pos[id].x, y: pos[id].y }
    })
    return out
  }

  /** 布局坐标（节点或连线变化时重算） */
  const nodePos = computed(() => computeLayout(graphNodes.value, graphLinks.value))

  async function loadGraph() {
    if (!courseId.value) return
    graphLoading.value = true
    try {
      const r = await knowledgeApi.getRelationGraphData(courseId.value)
      const body = unwrap(r)
      const g = unwrap(body) || {}
      const nodes = Array.isArray(g.nodes) ? g.nodes : []
      const links = (g.links || g.relations || []).map((l) => ({
        source: l.source ?? l.from,
        target: l.target ?? l.to,
      }))
      // 依据重要度设定节点半径，令模块枢纽在图谱中更突出
      graphNodes.value = nodes.map((x) => ({
        ...x,
        r: 7 + Math.min(16, (x.importance ?? 3) * 1.3),
      }))
      graphLinks.value = links
    } catch (e) {
      logger.warn('图谱数据不可用，已忽略可视化', e)
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
