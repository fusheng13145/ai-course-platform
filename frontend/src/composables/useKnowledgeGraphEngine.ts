import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import knowledgeApi from '@/api/knowledge'

/**
 * 知识图谱（教师）引擎域
 * <p>承载图谱引擎（Python 8001）的同步与课程子图拉取：同步模式/说明（ENGINE=
 * 真引擎 / LOCAL=消费侧兜底 / UNAVAILABLE=降级占位）、失败持久提示与
 * 「数据已变更建议重新同步」标记（engineSyncStale，由知识点域在变更后置位）。
 * 课程 id 由编排层注入。</p>
 */
export function useKnowledgeGraphEngine(deps) {
  const { courseId } = deps

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
    return m === 'ENGINE'
      ? 'success'
      : m === 'LOCAL'
        ? 'warning'
        : m === 'UNAVAILABLE'
          ? 'danger'
          : 'info'
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
