import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import analyticsApi from '@/api/analytics'
import { useAnalyticsChartOptions } from './useAnalyticsChartOptions'
import { useAnalyticsOverview } from './useAnalyticsOverview'

/** 指标卡脚注（涨跌徽标） */
function metricFoot(m) {
  const cls = m.change >= 0 ? 'trend-up' : 'trend-down'
  return `<span class="${cls}">${Math.abs(m.change)}%</span> 较上期`
}

/**
 * 数据分析中心（管理员）数据与图表逻辑（编排层）
 * <p>承载平台运营分析页的日期范围切换、JSON 报告导出与对外导出面；
 * 图表配置构建拆见 useAnalyticsChartOptions，总览拉取与回填拆见 useAnalyticsOverview。</p>
 */
export function useAnalytics() {
  const charts = useAnalyticsChartOptions()
  const overview = useAnalyticsOverview(charts)

  const dateRange = ref([])

  /** 日期快捷选项 */
  const dateShortcuts = [
    {
      text: '最近一周',
      value: () => {
        const end = new Date()
        const start = new Date()
        start.setDate(start.getDate() - 7)
        return [start, end]
      },
    },
    {
      text: '最近一个月',
      value: () => {
        const end = new Date()
        const start = new Date()
        start.setMonth(start.getMonth() - 1)
        return [start, end]
      },
    },
    {
      text: '最近三个月',
      value: () => {
        const end = new Date()
        const start = new Date()
        start.setMonth(start.getMonth() - 3)
        return [start, end]
      },
    },
  ]

  /** 日期范围变化（选择完整区间后重新拉取） */
  function handleDateChange(val) {
    if (val && val.length === 2) {
      overview.fetchAnalyticsData()
    }
  }

  /** 导出分析报告（JSON 原样落盘） */
  async function exportReport() {
    try {
      ElMessage({ message: '正在生成报告...', duration: 0 })
      const res = await analyticsApi.exportReport()
      ElMessage.closeAll()
      ElMessage.success('报告导出成功')
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `分析报告_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.json`
      link.click()
      window.URL.revokeObjectURL(url)
    } catch (e) {
      ElMessage.closeAll()
      logger.error('导出报告失败:', e)
      ElMessage.error('导出失败，请稍后重试')
    }
  }

  return {
    dateRange,
    loading: overview.loading,
    hasUserTrend: overview.hasUserTrend,
    hasCategory: overview.hasCategory,
    hasHeatmap: overview.hasHeatmap,
    hasRoleActivity: overview.hasRoleActivity,
    hasGradeDist: overview.hasGradeDist,
    hasTopCourses: overview.hasTopCourses,
    dateShortcuts,
    coreMetrics: overview.coreMetrics,
    metricFoot,
    userTrendOption: charts.userTrendOption,
    categoryPieOption: charts.categoryPieOption,
    heatmapColors: charts.heatmapColors,
    heatmapOption: charts.heatmapOption,
    roleActivityOption: charts.roleActivityOption,
    gradeDistOption: charts.gradeDistOption,
    topCoursesOption: charts.topCoursesOption,
    fetchAnalyticsData: overview.fetchAnalyticsData,
    handleDateChange,
    exportReport,
  }
}
