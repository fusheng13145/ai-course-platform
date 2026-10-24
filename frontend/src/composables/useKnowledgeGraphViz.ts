import { ref, computed } from 'vue'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import knowledgeApi from '@/api/knowledge'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'

/**
 * 知识图谱（教师）图谱可视化域
 * <p>承载 ECharts 力导向图谱（实时动画、可拖拽缩放、路径高亮）与学习路径规划
 * （调用后端路径引擎，404/未找到按 note 呈现）；路径规划结果直接写入本域的
 * 高亮节点集合并重建图谱配置。课程 id 由编排层注入。</p>
 */
export function useKnowledgeGraphViz(deps) {
  const { courseId } = deps

  /* ---- 力导向图谱 ---- */
  const graphLoading = ref(false)
  const graphNodes = ref([])
  const graphLinks = ref([])
  const graphError = ref('')
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
      const g = (unwrap(body) || {}) as {
        nodes?: {
          id?: number | string
          name?: string
          difficultyLevel?: string
          difficulty?: string
          importance?: number
        }[]
        links?: {
          source?: number | string
          target?: number | string
          from?: number | string
          to?: number | string
        }[]
        relations?: {
          source?: number | string
          target?: number | string
          from?: number | string
          to?: number | string
        }[]
      }
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

  return {
    graphLoading,
    graphNodes,
    graphLinks,
    graphError,
    pathNodeIds,
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
  }
}
