import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import courseApi from '@/api/course'
import knowledgeApi from '@/api/knowledge'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 知识图谱（教师）数据与业务逻辑
 * <p>承载课程知识图谱页全部状态与操作：课程选择、概览统计、知识点列表 CRUD、
 * AI 文本抽取知识点/关系、ECharts 力导向图谱（实时动画、可拖拽缩放、路径高亮）、
 * 学习路径规划（调用后端路径引擎，支持本地 BFS 降级标注）。</p>
 */
export function useKnowledgeGraph() {
  const courseLoading = ref(false)
  const courseOptions = ref([])
  const courseId = ref('')
  const loading = ref(false)
  /**
   * 知识点列表 / 图谱两路加载各自独立记录失败，避免并发加载时被成功方复位而互相串味；
   * 对外以 loadError 聚合暴露，页面据此渲染持久提示（失败可见）。
   * 原实现失败后静默置空 → 页面只剩「暂无知识点 / 暂无图谱数据」，会被误判为确实没有数据。
   */
  const pointsError = ref('')
  const graphError = ref('')
  const loadError = computed(() => pointsError.value || graphError.value)

  /** 加载课程选项；默认选中第一门 */
  async function loadCourses() {
    courseLoading.value = true
    try {
      courseOptions.value = await courseApi.getAllMyCourses()
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
    pointsError.value = ''
    graphError.value = ''
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
      const r = await knowledgeApi.getKnowledgeOverview(courseId.value)
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

  /* ---- 力导向图谱 ---- */
  const graphLoading = ref(false)
  const graphNodes = ref([])
  const graphLinks = ref([])
  /** 已规划学习路径上的节点 id（有序），用于图谱高亮 */
  const pathNodeIds = ref([])
  const graphOption = ref<Record<string, unknown>>({})
  const hasGraph = computed(() => graphNodes.value.length > 0)

  const DIFF_LABEL = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }

  /** 重要度 → 节点直径（px）：收敛在 10~20，避免节点过大糊成一团 */
  function nodeSize(imp) {
    return 10 + Math.min(10, Math.max(0, ((imp ?? 4) - 4) * 1.8))
  }

  /**
   * 构建 ECharts 力导向配置
   * <p>layout: 'force' 让节点在加载/规划后实时运动至平衡态，roam + draggable
   * 提供滚轮缩放、画布平移与单点拖拽；配色按难度分类，尺寸按重要度，路径高亮。</p>
   */
  function buildGraphOption() {
    const t = getChartTheme()
    const st = getMonoStatus()
    const nameById = {}
    graphNodes.value.forEach((n) => (nameById[String(n.id)] = n.name))
    const ids = pathNodeIds.value.map((id) => String(id))
    const onPath = new Set(ids)
    const onPathLink = new Set()
    for (let i = 0; i < ids.length - 1; i++) {
      onPathLink.add(`${ids[i]}>${ids[i + 1]}`)
      onPathLink.add(`${ids[i + 1]}>${ids[i]}`)
    }
    return {
      tooltip: {
        trigger: 'item',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText, fontSize: 12 },
        formatter: (p) => {
          if (p.dataType === 'edge') {
            return `${nameById[String(p.data.source)] || p.data.source} → ${
              nameById[String(p.data.target)] || p.data.target
            }`
          }
          return [
            `<b>${p.data.name}</b>`,
            `难度：${DIFF_LABEL[p.data.difficulty] || '未标注'}`,
            `重要度：${p.data.importance ?? '-'}`,
          ].join('<br/>')
        },
      },
      legend: {
        data: ['简单', '中等', '困难'],
        top: 4,
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        textStyle: { color: t.subText, fontSize: 11 },
      },
      animationDuration: 800,
      animationEasing: 'quinticOut',
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: 'move',
          draggable: true,
          categories: [
            { name: '简单', itemStyle: { color: st.EASY } },
            { name: '中等', itemStyle: { color: st.MEDIUM } },
            { name: '困难', itemStyle: { color: st.HARD } },
          ],
          force: { repulsion: 100, edgeLength: [50, 130], gravity: 0.12, friction: 0.26 },
          emphasis: { focus: 'adjacency', scale: 1.15, label: { show: true } },
          lineStyle: { color: t.subText, width: 1, opacity: 0.5, curveness: 0.12 },
          label: {
            show: true,
            position: 'bottom',
            distance: 4,
            fontSize: 10,
            color: t.subText,
            formatter: '{b}',
          },
          labelLayout: { hideOverlap: true },
          itemStyle: { borderColor: t.sliceBorder, borderWidth: 1 },
          data: graphNodes.value.map((n) => {
            const difficulty = (n.difficultyLevel || n.difficulty || '').toUpperCase()
            const importance = n.importance ?? 4
            const id = String(n.id)
            const hl = onPath.has(id)
            return {
              id,
              name: n.name,
              difficulty,
              importance,
              category: difficulty === 'EASY' ? 0 : difficulty === 'HARD' ? 2 : 1,
              symbolSize: nodeSize(importance) + (hl ? 4 : 0),
              itemStyle: hl ? { color: st.WARNING } : undefined,
              label: hl ? { show: true, fontWeight: 'bold', color: t.text } : undefined,
            }
          }),
          // 端点必须为字符串：ECharts 会把数字端点当作节点下标，导致连线被整体丢弃
          links: graphLinks.value.map((l) => {
            const source = String(l.source)
            const target = String(l.target)
            return {
              source,
              target,
              lineStyle: onPathLink.has(`${source}>${target}`)
                ? { color: st.WARNING, width: 2.2, opacity: 0.95 }
                : undefined,
            }
          }),
        },
      ],
    }
  }

  async function loadGraph() {
    if (!courseId.value) return
    graphLoading.value = true
    graphError.value = ''
    pathNodeIds.value = []
    try {
      const r = await knowledgeApi.getRelationGraphData(courseId.value)
      const body = unwrap(r)
      const g = unwrap(body) || {}
      graphNodes.value = Array.isArray(g.nodes) ? g.nodes : []
      graphLinks.value = (g.links || g.relations || []).map((l) => ({
        source: l.source ?? l.from,
        target: l.target ?? l.to,
      }))
    } catch (e) {
      logger.warn('图谱数据不可用，已忽略可视化', e)
      graphNodes.value = []
      graphLinks.value = []
      graphError.value = '图谱数据加载失败，请检查网络后重试'
    } finally {
      graphOption.value = buildGraphOption()
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
    pathNodeIds.value = []
    graphOption.value = buildGraphOption()
    try {
      const envelope = await knowledgeApi.planLearningPath(
        tPathSource.value,
        tPathTarget.value,
        tPathHops.value || undefined,
      )
      const vo = envelope?.data || null
      if (vo && vo.found) {
        tPathPlan.value = vo
        pathNodeIds.value = (vo.path || []).map((n) => n?.id).filter((id) => id != null)
        graphOption.value = buildGraphOption()
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

  /* ---- 图谱引擎（Python 8001）同步与子图 ---- */
  const engineSyncing = ref(false)
  /** 引擎同步失败持久提示（单路，不自动消失，区别于瞬时 toast） */
  const engineSyncError = ref('')
  /** 引擎同步后的模式与说明（ENGINE=真引擎 / LOCAL=消费侧兜底 / UNAVAILABLE=降级占位） */
  const engineSyncMode = ref('')
  const engineSyncNote = ref('')
  /** 图谱数据已变更、建议重新同步引擎（新建/删除/抽取后置位） */
  const engineSyncStale = ref(false)

  const engineSubGraphLoading = ref(false)
  /** 引擎子图加载失败持久提示（单路，不自动消失） */
  const engineSubGraphError = ref('')
  const engineSubGraphMode = ref('')
  const engineSubGraphNote = ref('')
  const engineSubGraph = ref<{ nodes: unknown[]; edges: unknown[] } | null>(null)
  const engineSubGraphNodeCount = computed(() =>
    Array.isArray(engineSubGraph.value?.nodes) ? engineSubGraph.value.nodes.length : 0,
  )
  const engineSubGraphEdgeCount = computed(() =>
    Array.isArray(engineSubGraph.value?.edges) ? engineSubGraph.value.edges.length : 0,
  )

  /** 引擎模式文案与标签色（ENGINE=引擎计算 / LOCAL=本地兜底 / UNAVAILABLE=引擎不可用） */
  const ENGINE_MODE_TEXT: Record<string, string> = {
    ENGINE: '引擎计算',
    LOCAL: '本地兜底',
    UNAVAILABLE: '引擎不可用',
  }
  function engineModeText(m: string) {
    return ENGINE_MODE_TEXT[m] || '未知'
  }
  function engineModeType(m: string) {
    return m === 'ENGINE' ? 'success' : m === 'LOCAL' ? 'warning' : m === 'UNAVAILABLE' ? 'danger' : 'info'
  }

  /** 将当前课程知识点/关系同步至图谱引擎（引擎未接入时后端返回 mode=LOCAL 消费侧确认） */
  async function syncKnowledgeGraphEngine() {
    if (!courseId.value) return ElMessage.warning('请先选择课程')
    engineSyncing.value = true
    engineSyncError.value = ''
    engineSyncMode.value = ''
    engineSyncNote.value = ''
    try {
      const r = await knowledgeApi.ingestKnowledgeGraph(courseId.value)
      const res = (unwrap(unwrap(r)) || {}) as unknown as { mode?: string; note?: string }
      engineSyncMode.value = res.mode || ''
      engineSyncNote.value =
        res.note || (res.mode === 'ENGINE' ? '已同步至图谱引擎' : '已在消费侧确认数据')
      engineSyncStale.value = false
    } catch (e) {
      logger.error('图谱引擎同步失败', e)
      engineSyncError.value = '图谱引擎同步失败，请检查网络后重试'
    } finally {
      engineSyncing.value = false
    }
  }

  /** 拉取课程知识子图（引擎未接入时后端返回消费侧 MySQL 直读结果，mode=LOCAL） */
  async function loadEngineSubGraph() {
    if (!courseId.value) return ElMessage.warning('请先选择课程')
    engineSubGraphLoading.value = true
    engineSubGraphError.value = ''
    try {
      const r = await knowledgeApi.getKnowledgeSubGraph(courseId.value)
      const res = (unwrap(unwrap(r)) || {}) as unknown as {
        mode?: string
        note?: string
        nodes?: unknown[]
        edges?: unknown[]
      }
      engineSubGraphMode.value = res.mode || ''
      engineSubGraphNote.value = res.note || ''
      engineSubGraph.value = {
        nodes: Array.isArray(res.nodes) ? res.nodes : [],
        edges: Array.isArray(res.edges) ? res.edges : [],
      }
    } catch (e) {
      logger.error('引擎子图加载失败', e)
      engineSubGraph.value = null
      engineSubGraphError.value = '引擎子图加载失败，请检查网络后重试'
    } finally {
      engineSubGraphLoading.value = false
    }
  }

  return {
    courseLoading,
    courseOptions,
    courseId,
    loading,
    loadError,
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
    graphOption,
    hasGraph,
    loadGraph,
    tPathSource,
    tPathTarget,
    tPathHops,
    tPathPlanning,
    tPathPlan,
    tPathNotFound,
    tPathError,
    planPathTeacher,
    engineSyncing,
    engineSyncError,
    engineSyncMode,
    engineSyncNote,
    engineSyncStale,
    engineSubGraphLoading,
    engineSubGraphError,
    engineSubGraphMode,
    engineSubGraphNote,
    engineSubGraph,
    engineSubGraphNodeCount,
    engineSubGraphEdgeCount,
    engineModeText,
    engineModeType,
    syncKnowledgeGraphEngine,
    loadEngineSubGraph,
  }
}
