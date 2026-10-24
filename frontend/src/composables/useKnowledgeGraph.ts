import { ref, computed } from 'vue'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import courseApi from '@/api/course'
import knowledgeApi from '@/api/knowledge'
import { useKnowledgeGraphPoints } from './useKnowledgeGraphPoints'
import { useKnowledgeGraphViz } from './useKnowledgeGraphViz'
import { useKnowledgeGraphEngine } from './useKnowledgeGraphEngine'

/**
 * 知识图谱（教师）数据与业务逻辑（编排层）
 * <p>承载课程选择、概览统计与两路加载失败的聚合提示（loadError）；
 * 知识点列表/CRUD/AI 抽取拆见 useKnowledgeGraphPoints，力导向图谱与路径规划
 * 拆见 useKnowledgeGraphViz，图谱引擎同步与子图拆见 useKnowledgeGraphEngine。</p>
 */
export function useKnowledgeGraph() {
  const courseLoading = ref(false)
  const courseOptions = ref([])
  // 字符串（统一路由 ?courseId=）与数字（驾驶舱 /teacher/courses/:id/knowledge 直入）两种来源
  const courseId = ref<string | number>('')
  const loading = ref(false)

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

  const viz = useKnowledgeGraphViz({ courseId })
  const engine = useKnowledgeGraphEngine({ courseId })
  const points = useKnowledgeGraphPoints({
    courseId,
    engineSyncStale: engine.engineSyncStale,
    loadOverview,
    loadGraph: viz.loadGraph,
  })

  /**
   * 知识点列表 / 图谱两路加载各自独立记录失败，避免并发加载时被成功方复位而互相串味；
   * 对外以 loadError 聚合暴露，页面据此渲染持久提示（失败可见）。
   * 原实现失败后静默置空 → 页面只剩「暂无知识点 / 暂无图谱数据」，会被误判为确实没有数据。
   */
  const loadError = computed(() => points.pointsError.value || viz.graphError.value)

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

  /** 课程切换：清空并重新加载概览/列表/图谱 */
  function onCourseChange() {
    overview.value = {}
    points.points.value = []
    viz.graphNodes.value = []
    viz.graphLinks.value = []
    points.pointsError.value = ''
    viz.graphError.value = ''
    if (courseId.value) {
      loadOverview()
      points.loadPoints()
      viz.loadGraph()
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
    pointLoading: points.pointLoading,
    points: points.points,
    loadPoints: points.loadPoints,
    formDialog: points.formDialog,
    isEdit: points.isEdit,
    formSubmitting: points.formSubmitting,
    kpForm: points.kpForm,
    openForm: points.openForm,
    submitForm: points.submitForm,
    removePoint: points.removePoint,
    extractDialog: points.extractDialog,
    extractText: points.extractText,
    extractLoading: points.extractLoading,
    extractResult: points.extractResult,
    extractRaw: points.extractRaw,
    extractNodes: points.extractNodes,
    extractRelations: points.extractRelations,
    nodeName: points.nodeName,
    relText: points.relText,
    openExtractDialog: points.openExtractDialog,
    runExtract: points.runExtract,
    graphLoading: viz.graphLoading,
    graphOption: viz.graphOption,
    hasGraph: viz.hasGraph,
    loadGraph: viz.loadGraph,
    tPathSource: viz.tPathSource,
    tPathTarget: viz.tPathTarget,
    tPathHops: viz.tPathHops,
    tPathPlanning: viz.tPathPlanning,
    tPathPlan: viz.tPathPlan,
    tPathNotFound: viz.tPathNotFound,
    tPathError: viz.tPathError,
    planPathTeacher: viz.planPathTeacher,
    engineSyncing: engine.engineSyncing,
    engineSyncError: engine.engineSyncError,
    engineSyncMode: engine.engineSyncMode,
    engineSyncNote: engine.engineSyncNote,
    engineSyncStale: engine.engineSyncStale,
    engineSubGraphLoading: engine.engineSubGraphLoading,
    engineSubGraphError: engine.engineSubGraphError,
    engineSubGraphMode: engine.engineSubGraphMode,
    engineSubGraphNote: engine.engineSubGraphNote,
    engineSubGraph: engine.engineSubGraph,
    engineSubGraphNodeCount: engine.engineSubGraphNodeCount,
    engineSubGraphEdgeCount: engine.engineSubGraphEdgeCount,
    engineModeText: engine.engineModeText,
    engineModeType: engine.engineModeType,
    syncKnowledgeGraphEngine: engine.syncKnowledgeGraphEngine,
    loadEngineSubGraph: engine.loadEngineSubGraph,
  }
}
