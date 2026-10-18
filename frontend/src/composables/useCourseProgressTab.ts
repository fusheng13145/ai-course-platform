import { ref, computed, watch, onMounted } from 'vue'
import teacherApi from '@/api/teacher'
import logger from '@/utils/logger'

/**
 * 课程进度 Tab 数据逻辑（拆分自 components/CourseProgressTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseProgressTab(courseIdRef) {
  const loading = ref(false)
  const loadError = ref('')
  const chapterList = ref([])
  const plannedChapters = ref(0)

  // 环形图常量
  const circumference = 2 * Math.PI * 52

  const totalChapters = computed(() => chapterList.value.length)
  const completedChapters = computed(() => chapterList.value.filter((c) => c.isPublished).length)
  const totalTasks = computed(() =>
    chapterList.value.reduce((sum, c) => sum + (c.taskCount || 0), 0),
  )
  const overallProgress = computed(() => {
    if (totalChapters.value === 0) return 0
    return Math.round((completedChapters.value / totalChapters.value) * 100)
  })
  const dashOffset = computed(() => {
    const pct = overallProgress.value / 100
    return circumference * (1 - pct)
  })

  const avgSubmissionRate = computed(() => {
    const chapters = chapterList.value.filter((c) => c.taskCount > 0)
    if (chapters.length === 0) return 0
    const total = chapters.reduce((s, c) => s + Math.round((c.completionRate ?? 0) * 100), 0)
    return Math.round(total / chapters.length)
  })

  // 计划进度（假设每章均匀分布；数据未加载时回退为全部章节）
  const effectivePlanned = computed(() =>
    plannedChapters.value > 0 ? plannedChapters.value : totalChapters.value,
  )
  const plannedProgress = computed(() => {
    if (totalChapters.value === 0) return 0
    return Math.round((effectivePlanned.value / totalChapters.value) * 100)
  })
  const plannedRemaining = computed(() =>
    Math.max(0, effectivePlanned.value - completedChapters.value),
  )
  const behindCount = computed(() => Math.max(0, effectivePlanned.value - completedChapters.value))

  /** 扁平化章节树（模块内顶层函数，供 loadData 调用） */
  function flattenTree(nodes, flat, level = 0) {
    nodes.forEach((node) => {
      flat.push({
        ...node,
        _level: level,
        title: level > 0 ? '  '.repeat(level) + '└ ' + (node.title || '') : node.title || '',
      })
      if (node.children && node.children.length > 0) {
        flattenTree(node.children, flat, level + 1)
      }
    })
  }

  async function loadData() {
    loading.value = true
    loadError.value = ''
    try {
      const res = await teacherApi.getChapterTree(courseIdRef.value)
      const raw = res.data || res || []
      const list = Array.isArray(raw) ? raw : []

      // 扁平化章节树用于表格展示
      const flat = []
      flattenTree(list, flat)
      chapterList.value = flat
      plannedChapters.value = list.length
    } catch (e) {
      logger.warn('[CourseProgressTab] loadData 失败:', e.message || e)
      chapterList.value = []
      loadError.value = '课程进度数据加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  onMounted(loadData)
  watch(() => courseIdRef.value, loadData)

  return {
    loading,
    loadError,
    chapterList,
    plannedChapters,
    circumference,
    totalChapters,
    completedChapters,
    totalTasks,
    overallProgress,
    dashOffset,
    avgSubmissionRate,
    plannedProgress,
    plannedRemaining,
    behindCount,
    loadData,
  }
}
