import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import knowledgeApi from '@/api/knowledge'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import courseApi from '@/api/course'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'

/** 难度文案/类别映射 */
const DIFF_LABEL = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const DIFF_TAG = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }

/**
 * 学生知识图谱数据与业务逻辑
 * <p>承载学习图谱页全部状态与操作：课程加载（支持路由直入）、图谱数据加载
 * （关系 API 优先 + 基础 API 回退）、难度/关键词过滤、ECharts 力导向图配置与
 * 路径高亮、节点详情（掌握度加载/前置后续节点）、学习路径规划。</p>
 */
export function useStudentKnowledgeGraph() {
  const route = useRoute()
  const router = useRouter()

  const courseList = ref([])
  const selectedCourseId = ref(null)
  const searchKeyword = ref('')
  const difficultyFilter = ref('')
  const graphLoading = ref(false)

  const rawNodes = ref([])
  const rawLinks = ref([])
  const selectedNode = ref(null)

  /* ---- 路径规划状态 ---- */
  const pathSource = ref(null)
  const pathTarget = ref(null)
  const pathMaxHops = ref(null)
  const pathPlanning = ref(false)
  const pathPlan = ref(null)
  const pathNotFound = ref(false)
  const pathError = ref('')
  const pathNodeIds = ref([])
  const pathEdgeKeys = ref([])

  const allPointOptions = computed(() =>
    rawNodes.value.map((n) => ({ value: n.id, label: n.name })),
  )

  const filteredNodeCount = computed(() => rawNodes.value.filter((n) => matchFilter(n)).length)
  const nodeCount = computed(() => rawNodes.value.length)
  const masteredCount = computed(() => rawNodes.value.filter((n) => (n.mastery ?? 0) >= 60).length)
  const avgMastery = computed(() => {
    if (!rawNodes.value.length) return 0
    return Math.round(
      rawNodes.value.reduce((s, n) => s + (n.mastery ?? 0), 0) / rawNodes.value.length,
    )
  })
  const avgMasteryText = computed(() => avgMastery.value + '%')

  /** 前置/后续节点（基于连线方向） */
  const prerequisiteNodes = computed(() => {
    if (!selectedNode.value) return []
    return rawLinks.value
      .filter((l) => l.target === selectedNode.value.id)
      .map((l) => rawNodes.value.find((n) => n.id === l.source))
      .filter(Boolean)
  })
  const subsequentNodes = computed(() => {
    if (!selectedNode.value) return []
    return rawLinks.value
      .filter((l) => l.source === selectedNode.value.id)
      .map((l) => rawNodes.value.find((n) => n.id === l.target))
      .filter(Boolean)
  })

  /** 力导向图配置（按当前主题构建） */
  function createGraphOption() {
    const t = getChartTheme()
    const status = getMonoStatus()
    return {
      tooltip: {
        trigger: 'item',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText, fontSize: 13 },
        formatter: (params) => {
          if (params.dataType === 'node') {
            const d = params.data
            return `<strong>${d.name}</strong><br/>难度：${getDifficultyLabel(d.difficultyLevel)}<br/>掌握度：${d.mastery ?? 0}%`
          }
          const link = params.data
          return `${link.relationType === 'PREREQUISITE' ? '前置依赖' : '关联'}`
        },
      },
      animationDurationUpdate: 500,
      animationEasingUpdate: 'quarticIn',
      series: [
        {
          type: 'graph',
          layout: 'force',
          data: [],
          links: [],
          roam: 'move',
          draggable: true,
          force: { repulsion: 120, edgeLength: [60, 140], gravity: 0.12 },
          emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
          labelLayout: { hideOverlap: true },
          label: {
            show: true,
            position: 'bottom',
            fontSize: 11,
            fontWeight: 500,
            color: t.text,
            formatter: '{b}',
          },
          symbolSize: (val, params) => {
            const importance = params?.data?.importance || val?.[2] || 3
            return Math.max(12, Math.min(26, importance * 2.2))
          },
          itemStyle: {
            borderColor: t.sliceBorder,
            borderWidth: 2,
            shadowBlur: 6,
            shadowColor: 'rgba(24,32,48,0.12)',
          },
          lineStyle: { color: 'source', curveness: 0.15, width: 1.5, opacity: 0.6 },
          categories: [
            { name: '简单', itemStyle: { color: status.EASY } },
            { name: '中等', itemStyle: { color: status.MEDIUM } },
            { name: '困难', itemStyle: { color: status.HARD } },
          ],
        },
      ],
    }
  }
  const graphOption = ref(createGraphOption())

  /** 加载课程列表（支持路由 courseId 直入） */
  async function loadCourseList() {
    try {
      const res = await courseApi.getCourseList()
      const list = res.data?.list || res.data || res || []
      courseList.value = Array.isArray(list) ? list : []
      const routeCourseId = route.query.courseId || route.params.courseId || route.params.id
      if (routeCourseId) {
        selectedCourseId.value = Number(routeCourseId)
        await loadGraphData()
      } else if (courseList.value.length > 0) {
        selectedCourseId.value = courseList.value[0].id
        await loadGraphData()
      }
    } catch (e) {
      logger.error('加载课程列表失败:', e)
    }
  }

  /** 加载图谱数据（关系 API 优先，基础 API 回退） */
  async function loadGraphData() {
    if (!selectedCourseId.value) return
    graphLoading.value = true
    try {
      const res = await knowledgeRelationApi.getGraphData(selectedCourseId.value)
      const data = res.data || res || {}
      const nodes = Array.isArray(data.nodes)
        ? data.nodes
        : Array.isArray(data.points)
          ? data.points
          : []
      rawNodes.value = nodes.map((n) => ({
        id: n.id,
        name: n.name || n.title || '未知',
        description: n.description || '',
        difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
        mastery: n.mastery ?? n.masteredPercent ?? 0,
        importance: n.importance ?? n.weight ?? 3,
        categoryIndex: getCategoryIndex(n.difficultyLevel || n.difficulty || 'MEDIUM'),
      }))
      const links = Array.isArray(data.links)
        ? data.links
        : Array.isArray(data.relations)
          ? data.relations
          : []
      rawLinks.value = links.map((l) => ({
        source: l.source || l.fromId,
        target: l.target || l.toId,
        relationType: l.relationType || l.type || 'RELATED',
        lineStyle: getLineStyle(l.relationType || l.type || 'RELATED'),
      }))
      updateChartOption()
    } catch (e) {
      logger.error('加载知识图谱失败:', e)
      try {
        const fallbackRes = await knowledgeApi.getKnowledgeGraph(selectedCourseId.value)
        const fData = fallbackRes.data || fallbackRes || {}
        if (fData.nodes || fData.points) {
          const fnodes = Array.isArray(fData.nodes)
            ? fData.nodes
            : Array.isArray(fData.points)
              ? fData.points
              : []
          rawNodes.value = fnodes.map((n) => ({
            id: n.id,
            name: n.name || n.title || '未知',
            description: n.description || '',
            difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
            mastery: n.mastery ?? 0,
            importance: 3,
            categoryIndex: getCategoryIndex('MEDIUM'),
          }))
          const flinks = Array.isArray(fData.links) ? fData.links : []
          rawLinks.value = flinks.map((l) => ({
            source: l.source,
            target: l.target,
            relationType: 'RELATED',
            lineStyle: getLineStyle('RELATED'),
          }))
          updateChartOption()
        } else {
          rawNodes.value = []
          rawLinks.value = []
        }
      } catch {
        rawNodes.value = []
        rawLinks.value = []
      }
    } finally {
      graphLoading.value = false
    }
  }

  /** 更新图表数据（过滤 + 路径高亮） */
  function updateChartOption() {
    // ECharts 画在 canvas 上，不解析 CSS 变量，高亮色需取主题实际色值
    const warn = getMonoStatus().WARNING
    const filtered = rawNodes.value.filter((n) => matchFilter(n))
    const nodeIdSet = new Set(filtered.map((n) => n.id))
    const filteredLinks = rawLinks.value.filter(
      (l) => nodeIdSet.has(l.source) && nodeIdSet.has(l.target),
    )
    const pset = new Set(pathNodeIds.value || [])
    const pkeys = pathEdgeKeys.value || []

    graphOption.value.series[0].data = filtered.map((n) => {
      const onPath = pset.has(n.id)
      return {
        ...n,
        // 端点/标识统一转字符串：ECharts 会把数字端点当作节点下标，导致连线被整体丢弃
        id: String(n.id),
        itemStyle: onPath
          ? {
              color: getDifficultyColor(n.difficultyLevel),
              borderColor: warn,
              borderWidth: 4,
              shadowBlur: 14,
              shadowColor: warn,
            }
          : { color: getDifficultyColor(n.difficultyLevel) },
        category: getCategoryIndex(n.difficultyLevel),
      }
    })
    graphOption.value.series[0].links = filteredLinks.map((l) => {
      const onPath =
        pkeys.includes(`${l.source}-${l.target}`) || pkeys.includes(`${l.target}-${l.source}`)
      return onPath
        ? {
            ...l,
            source: String(l.source),
            target: String(l.target),
            lineStyle: {
              color: warn,
              width: 4,
              opacity: 1,
              curveness: 0.15,
              type: 'solid',
            },
          }
        : { ...l, source: String(l.source), target: String(l.target) }
    })
  }

  /** 过滤：难度 + 关键词 */
  function matchFilter(node) {
    if (difficultyFilter.value && node.difficultyLevel !== difficultyFilter.value) return false
    if (searchKeyword.value && !node.name.toLowerCase().includes(searchKeyword.value.toLowerCase()))
      return false
    return true
  }

  function filterNodes() {
    updateChartOption()
  }

  function selectNodeById(nodeId) {
    const node = rawNodes.value.find((n) => n.id === nodeId)
    if (node) selectedNode.value = { ...node }
  }

  function startLearningNode() {
    if (!selectedNode.value || !selectedCourseId.value) return
    router.push(`/student/courses/${selectedCourseId.value}`)
    ElMessage.info(`正在跳转到「${selectedNode.value.name}」的学习页面`)
  }

  /** 节点点击：选中 + 加载真实掌握度 */
  async function handleNodeClick(params) {
    if (params.dataType !== 'node') return
    const clickedNode = rawNodes.value.find((n) => String(n.id) === String(params.data.id))
    if (clickedNode) {
      selectedNode.value = { ...clickedNode }
      try {
        const res = await knowledgeApi.getMastery(clickedNode.id, undefined)
        const mData = res.data || res
        if (mData != null && typeof mData === 'number') {
          selectedNode.value.mastery = Math.round(mData)
        } else if (mData?.masteryPercent != null) {
          selectedNode.value.mastery = Math.round(mData.masteryPercent)
        }
        const origIdx = rawNodes.value.findIndex((n) => n.id === clickedNode.id)
        if (origIdx >= 0) {
          rawNodes.value[origIdx].mastery = selectedNode.value.mastery
        }
      } catch {
        // 掌握度 API 不可用时保留图谱值
      }
    }
  }

  /** 学习路径规划（后端引擎 + 本地 BFS 兜底，结果高亮到图谱） */
  async function planPath() {
    pathNotFound.value = false
    pathError.value = ''
    if (!pathSource.value || !pathTarget.value) {
      pathError.value = '请先选择起点与目标知识点'
      return
    }
    if (pathSource.value === pathTarget.value) {
      pathError.value = '起点与目标不能相同'
      return
    }
    pathPlanning.value = true
    pathPlan.value = null
    try {
      const envelope = await knowledgeApi.planLearningPath(
        pathSource.value,
        pathTarget.value,
        pathMaxHops.value || undefined,
      )
      const vo = envelope?.data || null
      if (vo && vo.found) {
        pathPlan.value = vo
        pathNodeIds.value = (vo.path || []).map((n) => n?.id).filter((id) => id != null)
        pathEdgeKeys.value = (vo.edges || []).map((e) => `${e.sourceId}-${e.targetId}`)
        searchKeyword.value = ''
        difficultyFilter.value = ''
        updateChartOption()
      } else if (vo) {
        pathNotFound.value = true
        pathError.value = vo.note || '未找到从起点到目标的学习路径'
        pathNodeIds.value = []
        pathEdgeKeys.value = []
        updateChartOption()
      }
    } catch (e) {
      const status = e?.response?.status
      const msg = e?.response?.data?.message || ''
      if (status === 404) {
        pathNotFound.value = true
        pathError.value = msg || '未找到从起点到目标的学习路径'
      } else {
        pathError.value = msg || '路径规划失败，请稍后重试'
      }
      pathNodeIds.value = []
      pathEdgeKeys.value = []
      updateChartOption()
    } finally {
      pathPlanning.value = false
    }
  }

  /* ---- 工具 ---- */
  function getDifficultyColor(level) {
    const status = getMonoStatus()
    return status[level] || status.MEDIUM
  }
  function getDifficultyLabel(level) {
    return DIFF_LABEL[level] || '未知'
  }
  function getDifficultyTagType(level) {
    return DIFF_TAG[level] || 'info'
  }
  function getCategoryIndex(level) {
    const map = { EASY: 0, MEDIUM: 1, HARD: 2 }
    return map[level] ?? 1
  }
  function getMasteryColor(mastery) {
    if (mastery >= 80) return 'var(--ac-success)'
    if (mastery >= 50) return 'var(--ac-primary)'
    if (mastery >= 30) return 'var(--ac-warning)'
    return 'var(--ac-danger)'
  }
  function getLineStyle(relationType) {
    const status = getMonoStatus()
    if (relationType === 'PREREQUISITE') {
      return { type: 'dashed', color: status.MEDIUM, opacity: 0.5 }
    }
    return { type: 'solid', color: status.WARNING, opacity: 0.5 }
  }

  return {
    courseList,
    selectedCourseId,
    searchKeyword,
    difficultyFilter,
    graphLoading,
    rawNodes,
    rawLinks,
    selectedNode,
    pathSource,
    pathTarget,
    pathMaxHops,
    pathPlanning,
    pathPlan,
    pathNotFound,
    pathError,
    allPointOptions,
    filteredNodeCount,
    nodeCount,
    masteredCount,
    avgMasteryText,
    prerequisiteNodes,
    subsequentNodes,
    graphOption,
    loadCourseList,
    loadGraphData,
    filterNodes,
    selectNodeById,
    startLearningNode,
    handleNodeClick,
    planPath,
    getDifficultyTagType,
    getDifficultyLabel,
    getMasteryColor,
  }
}
