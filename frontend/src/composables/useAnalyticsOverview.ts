import { ref } from 'vue'
import logger from '@/utils/logger'
import analyticsApi from '@/api/analytics'

/** 格式化数字显示（与全站 toLocaleString 一致） */
function formatNumber(num) {
  if (typeof num === 'string') return num
  if (!num && num !== 0) return '--'
  return Number(num).toLocaleString('zh-CN')
}

/** 指标卡初始/空态占位（五张卡同构，两处复用同一工厂保证逐字段一致） */
function createEmptyMetrics() {
  return [
    {
      label: '总用户数',
      value: '--',
      change: 0,
      icon: 'users',
      tone: 'var(--accent-soft)',
      toneColor: 'var(--accent)',
    },
    {
      label: '总课程数',
      value: '--',
      change: 0,
      icon: 'book',
      tone: 'var(--info-soft)',
      toneColor: 'var(--info)',
    },
    {
      label: '日活用户',
      value: '--',
      change: 0,
      icon: 'monitor',
      tone: 'var(--ok-soft)',
      toneColor: 'var(--ok)',
    },
    {
      label: '完成率',
      value: '--',
      change: 0,
      icon: 'check',
      tone: 'var(--warn-soft)',
      toneColor: 'var(--warn)',
    },
    {
      label: '增长率',
      value: '--',
      change: 0,
      icon: 'chart',
      tone: 'var(--violet-soft)',
      toneColor: 'var(--violet)',
    },
  ]
}

/**
 * 数据分析中心（管理员）总览数据域
 * <p>承载核心指标卡、六类图表到位标记与并行拉取回填；图表配置 ref 由编排层注入
 * （useAnalyticsChartOptions 的产物），本域只负责拉取与回填，不反向依赖配置构建。</p>
 */
export function useAnalyticsOverview(charts) {
  const loading = ref(false)

  const hasUserTrend = ref(false)
  const hasCategory = ref(false)
  const hasHeatmap = ref(false)
  const hasRoleActivity = ref(false)
  const hasGradeDist = ref(false)
  const hasTopCourses = ref(false)

  /** 核心指标卡 */
  const coreMetrics = ref(createEmptyMetrics())

  /* ==================== 数据获取与回填 ==================== */

  /** 并行拉取平台总览并回填全部图表 */
  async function fetchAnalyticsData() {
    loading.value = true
    try {
      const [statsRes, trendRes, courseRes, heatmapRes] = await Promise.allSettled([
        analyticsApi.getDashboardStats(),
        analyticsApi.getUserGrowth(30),
        analyticsApi.getCourseRanking(),
        analyticsApi.getPlatformHeatmap({ days: 30 }),
      ])

      if (statsRes.status === 'fulfilled') {
        const statsData = statsRes.value.data || statsRes.value
        if (statsData.totalUsers !== undefined) {
          coreMetrics.value[0].value = formatNumber(statsData.totalUsers)
          // 后端 userGrowth/growthRate 已是百分数（newUsers*100/total，AdminAnalyticsComponent），
          // 直接取整作百分点差值——此前再乘 100 会把 54.1% 显示成 5410.0%（D-10 修复轮发现）
          coreMetrics.value[0].change = statsData.userGrowth ? Math.round(statsData.userGrowth) : 0
        }
        if (statsData.totalCourses !== undefined) {
          coreMetrics.value[1].value = formatNumber(statsData.totalCourses)
          coreMetrics.value[1].change = statsData.courseGrowth || 0
        }
        if (statsData.dailyActiveUsers !== undefined) {
          coreMetrics.value[2].value = formatNumber(statsData.dailyActiveUsers)
        }
        if (statsData.completionRate !== undefined) {
          coreMetrics.value[3].value = Number(statsData.completionRate).toFixed(1) + '%'
        }
        if (statsData.growthRate !== undefined) {
          coreMetrics.value[4].value = Number(statsData.growthRate).toFixed(1) + '%'
        }
        if (statsData.categoryDistribution) {
          const entries = Object.entries(statsData.categoryDistribution)
          hasCategory.value = entries.length > 0
          charts.categoryPieOption.value.series[0].data = entries.map(([name, value]) => ({
            name,
            value,
          }))
        }
        if (statsData.gradeDistribution && Array.isArray(statsData.gradeDistribution)) {
          hasGradeDist.value = statsData.gradeDistribution.length > 0
          charts.gradeDistOption.value.series[0].data = statsData.gradeDistribution.map(
            (v) => Number(v) || 0,
          )
        }
        if (statsData.roleActivity && Array.isArray(statsData.roleActivity)) {
          hasRoleActivity.value = statsData.roleActivity.length > 0
          charts.roleActivityOption.value.series[0].data = statsData.roleActivity.map(
            (r) => r.loginCount || 0,
          )
          charts.roleActivityOption.value.series[1].data = statsData.roleActivity.map(
            (r) => r.actionCount || 0,
          )
        }
      }

      if (trendRes.status === 'fulfilled') {
        const trendData = trendRes.value.data || trendRes.value
        if (Array.isArray(trendData) && trendData.length > 0) {
          hasUserTrend.value = true
          charts.userTrendOption.value.xAxis.data = trendData.map((d) => d.date || d.label || '')
          charts.userTrendOption.value.series[0].data = trendData.map(
            (d) => d.newUsers || d.count || 0,
          )
          charts.userTrendOption.value.series[1].data = trendData.map(
            (d) => d.cumulativeUsers || d.activeUsers || 0,
          )
        }
      }

      if (courseRes.status === 'fulfilled') {
        const courseData = courseRes.value.data || courseRes.value
        let courses = []
        if (Array.isArray(courseData)) {
          courses = courseData.slice(0, 10).map((c) => ({
            name: c.courseName || c.name,
            value: c.enrollmentCount || c.studentCount || c.popularity || 0,
          }))
        }
        courses.sort((a, b) => b.value - a.value)
        hasTopCourses.value = courses.length > 0
        charts.topCoursesOption.value.yAxis.data = courses.map((c) => c.name)
        charts.topCoursesOption.value.series[0].data = courses.map((c) => c.value)
      }

      if (heatmapRes.status === 'fulfilled') {
        const data = heatmapRes.value.data || heatmapRes.value || []
        if (Array.isArray(data) && data.length > 0) {
          const heatmapData = data
            .map((item) => [
              item.dayOfWeek ?? item.day ?? item.x ?? 0,
              item.hour ?? item.y ?? 0,
              Math.round(item.value ?? item.count ?? 0),
            ])
            .filter((d) => d[0] >= 0 && d[0] < 7 && d[1] >= 0)
          if (heatmapData.length > 0) {
            hasHeatmap.value = true
            charts.heatmapOption.value.series[0].data = heatmapData
          }
        }
      }
    } catch (error) {
      logger.warn('加载分析数据失败:', error)
      setEmptyState()
    } finally {
      loading.value = false
    }
  }

  /** 设置空数据占位（API 不可用时的诚实降级，不注入模拟数据） */
  function setEmptyState() {
    coreMetrics.value = createEmptyMetrics()
    hasCategory.value = false
    hasGradeDist.value = false
    hasRoleActivity.value = false
    hasUserTrend.value = false
    hasHeatmap.value = false
    hasTopCourses.value = false
    charts.categoryPieOption.value.series[0].data = []
    charts.gradeDistOption.value.series[0].data = []
    charts.roleActivityOption.value.series[0].data = []
    charts.roleActivityOption.value.series[1].data = []
  }

  return {
    loading,
    hasUserTrend,
    hasCategory,
    hasHeatmap,
    hasRoleActivity,
    hasGradeDist,
    hasTopCourses,
    coreMetrics,
    fetchAnalyticsData,
  }
}
