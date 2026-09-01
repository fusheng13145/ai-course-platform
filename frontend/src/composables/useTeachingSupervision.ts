import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import adminApi from '@/api/admin'
import analyticsApi from '@/api/analytics'

/**
 * 教学运行监管领域逻辑
 * <p>全景数据并行加载（开课统计/热力/异常监控）、学习预警（全校聚合 + 维度下钻）、
 * 分类分布与热力色阶等展示辅助函数。</p>
 */
export function useTeachingSupervision() {
  const loading = ref(false)
  const overview = reactive({} as any)
  const courseStats = reactive({} as any)
  const heatCells = ref([])
  const aiMonitor = reactive({} as any)

  const timeSlots = ['0–6时', '6–12时', '12–18时', '18–24时']
  const dayOrder = [1, 2, 3, 4, 5, 6, 0] // MON..SAT, SUN
  const dayNames = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六' }

  const categoryDistribution = computed(() => courseStats.categoryDistribution || {})
  const categoryKeys = computed(() => Object.keys(categoryDistribution.value))
  const categoryMax = computed(() =>
    Math.max(1, ...categoryKeys.value.map((k) => categoryDistribution.value[k])),
  )
  const topCourses = computed(() => courseStats.topCourses || [])
  const aiAlerts = computed(() => aiMonitor.alerts || [])

  // 学习预警（全校掉队聚合）
  const riskDim = ref('school')
  const riskLoading = ref(false)
  const riskTotal = ref(0)
  const riskCount = ref(0)
  const riskRate = ref(0)
  const riskGroups = ref([])
  const riskDetails = ref([])

  async function loadRisk() {
    riskLoading.value = true
    try {
      const res = await analyticsApi
        .getAtRiskStudents({ groupBy: riskDim.value, size: 200 })
        .catch(() => ({ data: {} }))
      const d = res?.data || {}
      riskTotal.value = d.totalStudents || 0
      riskCount.value = d.atRiskCount || 0
      riskRate.value = d.atRiskRate || 0
      riskGroups.value = Array.isArray(d.groups) ? d.groups : []
      riskDetails.value = Array.isArray(d.details) ? d.details : []
    } catch (e) {
      logger.error('加载掉队预警失败:', e)
      ElMessage.error('加载掉队预警失败')
    } finally {
      riskLoading.value = false
    }
  }

  function dayLabel(d) {
    return dayNames[d]
  }
  function barWidth(cnt) {
    return Math.round((cnt / categoryMax.value) * 100) + '%'
  }

  const heatPalette = [
    'rgba(127,127,127,0.08)',
    'rgba(59,130,246,0.30)',
    'rgba(59,130,246,0.50)',
    'rgba(59,130,246,0.72)',
    'rgba(37,99,235,0.95)',
  ]
  function heatValue(d, slot) {
    const cell = heatCells.value.find((c) => c.dayOfWeek === d && c.hour === slot)
    return cell ? cell.value || cell.count || 0 : 0
  }
  function heatMax() {
    return Math.max(1, ...heatCells.value.map((c) => c.value || c.count || 0))
  }
  function cellStyle(d, slot) {
    const v = heatValue(d, slot)
    const ratio = v / heatMax()
    const lvl = v === 0 ? 0 : Math.min(4, Math.ceil(ratio * 4))
    return { background: heatPalette[lvl] }
  }
  function legendStyle(n) {
    return { background: heatPalette[n] }
  }
  function alertType(level) {
    return level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'info'
  }

  async function loadAll() {
    loading.value = true
    try {
      const [ov, cs, hm, am] = await Promise.all([
        adminApi.getOverview().catch(() => ({ data: {} })),
        adminApi.getCourseStats().catch(() => ({ data: {} })),
        analyticsApi.getPlatformHeatmap({ days: 30 }).catch(() => ({ data: [] })),
        adminApi.getAIMonitor().catch(() => ({ data: { alerts: [] } })),
      ])
      Object.assign(overview, ov.data || {})
      Object.assign(courseStats, cs.data || {})
      heatCells.value = Array.isArray(hm.data) ? hm.data : []
      Object.assign(aiMonitor, am.data || { alerts: [] })
    } catch (e) {
      logger.error('加载监管数据失败:', e)
      ElMessage.error('加载监管数据失败')
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    loadAll()
    loadRisk()
  })

  return {
    loading,
    overview,
    courseStats,
    heatCells,
    aiMonitor,
    timeSlots,
    dayOrder,
    categoryDistribution,
    categoryKeys,
    topCourses,
    aiAlerts,
    riskDim,
    riskLoading,
    riskTotal,
    riskCount,
    riskRate,
    riskGroups,
    riskDetails,
    loadRisk,
    loadAll,
    dayLabel,
    barWidth,
    cellStyle,
    legendStyle,
    alertType,
  }
}
