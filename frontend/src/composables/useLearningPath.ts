import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import learningPathApi from '@/api/learningPath'

/**
 * AI 学习路径领域逻辑
 * <p>路径列表加载与切换、节点/每日任务加载、进度计算与节点样式映射、AI 生成路径。</p>
 */
export function useLearningPath() {
  /* ---- 数据状态 ---- */
  const paths = ref([])
  const pathNodes = ref([])
  const dailyTasks = ref([])
  const selectedPathId = ref(null)
  const currentPath = ref(null)
  const generating = ref(false)
  const loading = ref(false)
  /**
   * 加载失败提示文案：非空即表示学习路径数据不可信。
   * 失败时列表被置空并显示"暂无学习路径"，学生易误判为"我确实没有学习路径"。
   */
  const loadError = ref('')

  /* ---- 计算属性 ---- */
  const completedNodeCount = computed(() => {
    return pathNodes.value.filter((n) => n.status === 'COMPLETED' || n.completed).length
  })

  const overallProgress = computed(() => {
    if (pathNodes.value.length === 0) return 0
    return Math.round((completedNodeCount.value / pathNodes.value.length) * 100)
  })

  const overallProgressText = computed(() => overallProgress.value + '%')

  /* ---- 加载学习路径列表 ---- */
  async function loadPaths() {
    loading.value = true
    loadError.value = ''
    try {
      const res = await learningPathApi.getMyPaths()
      const list = res.data || res || []
      paths.value = Array.isArray(list)
        ? list.map((p) => ({
            id: p.id,
            name: p.pathName || p.name || '学习路径',
            totalStages: p.totalStages || 0,
            status: p.status || 'PENDING',
            progress: p.progress || 0,
          }))
        : []

      // 自动选中第一个路径
      if (paths.value.length > 0) {
        selectPath(paths.value[0].id)
      }
    } catch (e) {
      logger.error('加载学习路径失败', e)
      paths.value = []
      loadError.value = '学习路径加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  /* ---- 选择路径并加载详情 ---- */
  async function selectPath(pathId) {
    selectedPathId.value = pathId
    currentPath.value = paths.value.find((p) => p.id === pathId)

    try {
      // 加载路径节点
      const nodesRes = await learningPathApi.getPathNodes(pathId)
      const nodes = nodesRes.data || nodesRes || []
      pathNodes.value = Array.isArray(nodes)
        ? nodes.map((n) => ({
            ...n,
            title: n.title || n.description || n.knowledgePointName || '学习节点',
            content: n.content || n.detail || '',
            completed: n.status === 'COMPLETED',
            current: n.status === 'IN_PROGRESS',
            progress: n.progress ? Math.round(Number(n.progress)) : undefined,
          }))
        : []

      // 尝试加载每日任务
      loadDailyTasks(pathId)
    } catch (e) {
      logger.error('加载路径节点失败:', e)
    }
  }

  /* ---- 加载每日任务（增强：使用 learningPathApi） ---- */
  async function loadDailyTasks(pathId) {
    try {
      const res = await learningPathApi.getDailyTasks(pathId)
      const data = res.data || res || []
      dailyTasks.value = Array.isArray(data)
        ? data.map((t) => ({
            id: t.id,
            title: t.title || t.name || t.content || '任务',
            completed: t.completed ?? false,
            priority: t.priority || 'MEDIUM',
            knowledgePointName: t.knowledgePointName || '',
          }))
        : []
    } catch {
      // API 可能不存在，忽略错误
      dailyTasks.value = []
    }
  }

  /* ---- 计算路径进度 ---- */
  function calculatePathProgress(path) {
    if (!path.progress) {
      // 根据当前选中的路径节点计算
      if (selectedPathId.value === path.id && pathNodes.value.length > 0) {
        return overallProgress.value
      }
      return 0
    }
    return Math.round(Number(path.progress))
  }

  /* ---- 节点样式辅助方法 ---- */
  function getNodeType(node) {
    if (node.completed) return 'success'
    if (node.current) return 'warning'
    return 'info'
  }

  function getNodeSize(node) {
    if (node.current) return 'large'
    return 'normal'
  }

  /* ---- 操作方法 ---- */
  function handleNodeClick(_node) {
    // 可以展开更多操作
  }

  function startLearningNode(node) {
    ElMessage.info(`开始学习：${node.title}`)
    // 实际项目中跳转到对应课程或知识点页面
  }

  function reviewNode(node) {
    ElMessage.info(`复习：${node.title}`)
  }

  function toggleTask(task) {
    task.completed = !task.completed
    ElMessage.success(task.completed ? '任务已完成' : '任务已取消')
  }

  /* ---- AI 生成学习路径 ---- */
  async function handleGeneratePath() {
    try {
      await ElMessageBox.confirm(
        'AI 将根据你的学习情况生成个性化学习路径，确定要继续吗？',
        'AI 生成路径',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' },
      )

      generating.value = true
      // 调用生成API（需要 courseId 参数）
      await learningPathApi.generatePath(null)
      ElMessage.success('学习路径已生成')
      await loadPaths()
    } catch (e) {
      if (e !== 'cancel') {
        logger.error('生成路径失败:', e)
        ElMessage.error('生成失败，请稍后重试')
      }
    } finally {
      generating.value = false
    }
  }

  /* ---- 初始化 ---- */
  onMounted(() => {
    loadPaths()
  })

  return {
    paths,
    pathNodes,
    dailyTasks,
    selectedPathId,
    currentPath,
    generating,
    loading,
    loadError,
    completedNodeCount,
    overallProgress,
    overallProgressText,
    loadPaths,
    selectPath,
    calculatePathProgress,
    getNodeType,
    getNodeSize,
    handleNodeClick,
    startLearningNode,
    reviewNode,
    toggleTask,
    handleGeneratePath,
  }
}
