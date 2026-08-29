import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import analyticsApi from '@/api/analytics'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'

/** hex → rgba 工具（图表渐变用） */
function rgba(hex, a) {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
}

/** 格式化数字显示（与全站 toLocaleString 一致） */
function formatNumber(num) {
  if (typeof num === 'string') return num
  if (!num && num !== 0) return '--'
  return Number(num).toLocaleString('zh-CN')
}

/** 指标卡脚注（涨跌徽标） */
function metricFoot(m) {
  const cls = m.change >= 0 ? 'trend-up' : 'trend-down'
  return `<span class="${cls}">${Math.abs(m.change)}%</span> 较上期`
}

/**
 * 数据分析中心（管理员）数据与图表逻辑
 * <p>承载平台运营分析页全部状态与操作：六类图表配置构建（用户趋势/课程分布/学习热力图/
 * 角色活跃度/成绩分布/热门课程）、并行拉取平台总览数据并回填图表、核心指标卡统计、
 * 日期范围切换与 JSON 报告导出。</p>
 */
export function useAnalytics() {
  const dateRange = ref([])
  const loading = ref(false)

  const hasUserTrend = ref(false)
  const hasCategory = ref(false)
  const hasHeatmap = ref(false)
  const hasRoleActivity = ref(false)
  const hasGradeDist = ref(false)
  const hasTopCourses = ref(false)

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

  /** 核心指标卡 */
  const coreMetrics = ref([
    { label: '总用户数', value: '--', change: 0, icon: 'users', tone: 'var(--accent-soft)', toneColor: 'var(--accent)' },
    { label: '总课程数', value: '--', change: 0, icon: 'book', tone: 'var(--info-soft)', toneColor: 'var(--info)' },
    { label: '日活用户', value: '--', change: 0, icon: 'monitor', tone: 'var(--ok-soft)', toneColor: 'var(--ok)' },
    { label: '完成率', value: '--', change: 0, icon: 'check', tone: 'var(--warn-soft)', toneColor: 'var(--warn)' },
    { label: '增长率', value: '--', change: 0, icon: 'chart', tone: 'var(--violet-soft)', toneColor: 'var(--violet)' },
  ])

  /* ==================== 图表配置构建 ==================== */

  const userTrendOption = ref(buildUserTrendOption())
  function buildUserTrendOption() {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText, fontSize: 12 },
        axisPointer: { type: 'cross' },
      },
      legend: { data: ['新增用户', '活跃用户'], top: 0, textStyle: { color: t.subText, fontSize: 12 } },
      grid: { left: 50, right: 20, bottom: 30, top: 40 },
      xAxis: {
        type: 'category',
        data: [],
        boundaryGap: false,
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.subText, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: t.axis, type: 'dashed' } },
        axisLabel: { color: t.subText },
      },
      series: [
        {
          name: '新增用户',
          data: [],
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { width: 2.5, color: mono[1] },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: rgba(mono[1], 0.2) },
                { offset: 1, color: rgba(mono[1], 0.02) },
              ],
            },
          },
          itemStyle: { color: mono[1] },
        },
        {
          name: '活跃用户',
          data: [],
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { width: 2.5, color: mono[2] },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: rgba(mono[2], 0.18) },
                { offset: 1, color: rgba(mono[2], 0.02) },
              ],
            },
          },
          itemStyle: { color: mono[2] },
        },
      ],
    }
  }

  const categoryPieOption = ref(buildCategoryPieOption())
  function buildCategoryPieOption() {
    const t = getChartTheme()
    return {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} 门 ({d}%)',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
      },
      legend: { orient: 'vertical', right: 15, top: 'center', textStyle: { color: t.subText, fontSize: 12 } },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['38%', '52%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: t.sliceBorder, borderWidth: 2 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
          data: [],
        },
      ],
      color: getMonoSeries(),
    }
  }

  /** 热力图颜色梯度（随主题色板） */
  const heatmapColors = computed(() => {
    const mono = getMonoSeries()
    return [
      rgba(mono[2], 0.08),
      rgba(mono[2], 0.25),
      rgba(mono[4], 0.45),
      rgba(mono[3], 0.65),
      rgba(mono[0], 0.85),
    ]
  })

  const heatmapOption = ref(buildHeatmapOption())
  function buildHeatmapOption() {
    const t = getChartTheme()
    return {
      tooltip: {
        position: 'top',
        formatter: (params) => `${params.data[0]}<br/>学习时长: ${params.data[2]} 小时`,
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText, fontSize: 12 },
      },
      grid: { left: 60, right: 30, bottom: 50, top: 10 },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
        splitArea: { show: true },
        axisLabel: { color: t.subText, fontSize: 11 },
      },
      yAxis: {
        type: 'category',
        data: ['0-6时', '6-12时', '12-18时', '18-24时'],
        splitArea: { show: true },
        axisLabel: { color: t.subText, fontSize: 11 },
      },
      visualMap: {
        min: 0,
        max: 100,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        inRange: {
          color: heatmapColors.value.map((c) => c.replace('rgba', 'rgb').replace(/[\d.]+\)$/, '1)')),
        },
        textStyle: { color: t.subText, fontSize: 11 },
        show: false,
      },
      series: [
        {
          type: 'heatmap',
          data: [],
          label: { show: false },
          emphasis: {
            itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.15)' },
          },
          itemStyle: { borderRadius: 4, borderColor: t.sliceBorder, borderWidth: 2 },
        },
      ],
    }
  }

  const roleActivityOption = ref(buildRoleActivityOption())
  function buildRoleActivityOption() {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
        axisPointer: { type: 'shadow' },
      },
      legend: { data: ['登录次数', '操作频次'], top: 0, textStyle: { color: t.subText, fontSize: 12 } },
      grid: { left: 50, right: 20, bottom: 30, top: 40 },
      xAxis: {
        type: 'category',
        data: ['管理员', '教师', '学生'],
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.subText },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.axis, type: 'dashed' } },
        axisLabel: { color: t.subText },
      },
      series: [
        {
          name: '登录次数',
          type: 'bar',
          barWidth: '28%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: mono[1] },
                { offset: 1, color: rgba(mono[1], 0.4) },
              ],
            },
          },
          data: [],
        },
        {
          name: '操作频次',
          type: 'bar',
          barWidth: '28%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: mono[2] },
                { offset: 1, color: rgba(mono[2], 0.4) },
              ],
            },
          },
          data: [],
        },
      ],
    }
  }

  const gradeDistOption = ref(buildGradeDistOption())
  function buildGradeDistOption() {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
        formatter: '{b}分区间: {c} 人',
      },
      grid: { left: 55, right: 25, bottom: 35, top: 20 },
      xAxis: {
        type: 'category',
        data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
        name: '分数段',
        nameTextStyle: { color: t.subText, padding: [0, 0, 0, 0] },
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.subText },
      },
      yAxis: {
        type: 'value',
        name: '人数',
        nameTextStyle: { color: t.subText },
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.axis, type: 'dashed' } },
        axisLabel: { color: t.subText },
      },
      series: [
        {
          type: 'bar',
          barWidth: '50%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: mono[0] },
                { offset: 1, color: rgba(mono[0], 0.3) },
              ],
            },
          },
          data: [],
        },
      ],
    }
  }

  const topCoursesOption = ref(buildTopCoursesOption())
  function buildTopCoursesOption() {
    const t = getChartTheme()
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
        formatter: (params) => `${params[0].name}<br/>学习人数: ${params[0].value}`,
      },
      grid: { left: 120, right: 40, bottom: 20, top: 10 },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: t.axis, type: 'dashed' } },
        axisLabel: { color: t.subText },
      },
      yAxis: {
        type: 'category',
        data: [],
        inverse: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: t.subText, fontSize: 13, width: 110, overflow: 'truncate' },
      },
      series: [
        {
          type: 'bar',
          barWidth: '60%',
          itemStyle: {
            borderRadius: [0, 6, 6, 0],
            color: (params) => {
              const colors = getMonoSeries()
              return colors[params.dataIndex % colors.length]
            },
          },
          label: { show: true, position: 'right', color: t.subText, fontSize: 12, fontWeight: 500 },
          data: [],
        },
      ],
    }
  }

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
          coreMetrics.value[0].change = statsData.userGrowth ? Math.round(statsData.userGrowth * 100) : 0
        }
        if (statsData.totalCourses !== undefined) {
          coreMetrics.value[1].value = formatNumber(statsData.totalCourses)
          coreMetrics.value[1].change = statsData.courseGrowth || 0
        }
        if (statsData.dailyActiveUsers !== undefined) {
          coreMetrics.value[2].value = formatNumber(statsData.dailyActiveUsers)
        }
        if (statsData.completionRate !== undefined) {
          coreMetrics.value[3].value = (statsData.completionRate * 100).toFixed(1) + '%'
        }
        if (statsData.growthRate !== undefined) {
          coreMetrics.value[4].value = (statsData.growthRate * 100).toFixed(1) + '%'
        }
        if (statsData.categoryDistribution) {
          const entries = Object.entries(statsData.categoryDistribution)
          hasCategory.value = entries.length > 0
          categoryPieOption.value.series[0].data = entries.map(([name, value]) => ({ name, value }))
        }
        if (statsData.gradeDistribution && Array.isArray(statsData.gradeDistribution)) {
          hasGradeDist.value = statsData.gradeDistribution.length > 0
          gradeDistOption.value.series[0].data = statsData.gradeDistribution.map((v) => Number(v) || 0)
        }
        if (statsData.roleActivity && Array.isArray(statsData.roleActivity)) {
          hasRoleActivity.value = statsData.roleActivity.length > 0
          roleActivityOption.value.series[0].data = statsData.roleActivity.map((r) => r.loginCount || 0)
          roleActivityOption.value.series[1].data = statsData.roleActivity.map((r) => r.actionCount || 0)
        }
      }

      if (trendRes.status === 'fulfilled') {
        const trendData = trendRes.value.data || trendRes.value
        if (Array.isArray(trendData) && trendData.length > 0) {
          hasUserTrend.value = true
          userTrendOption.value.xAxis.data = trendData.map((d) => d.date || d.label || '')
          userTrendOption.value.series[0].data = trendData.map((d) => d.newUsers || d.count || 0)
          userTrendOption.value.series[1].data = trendData.map((d) => d.cumulativeUsers || d.activeUsers || 0)
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
        topCoursesOption.value.yAxis.data = courses.map((c) => c.name)
        topCoursesOption.value.series[0].data = courses.map((c) => c.value)
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
            heatmapOption.value.series[0].data = heatmapData
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

  /** 日期范围变化（选择完整区间后重新拉取） */
  function handleDateChange(val) {
    if (val && val.length === 2) {
      fetchAnalyticsData()
    }
  }

  /** 导出分析报告（JSON 原样落盘） */
  async function exportReport() {
    try {
      ElMessage.loading({ message: '正在生成报告...', duration: 0 })
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

  /** 设置空数据占位（API 不可用时的诚实降级，不注入模拟数据） */
  function setEmptyState() {
    coreMetrics.value = [
      { label: '总用户数', value: '--', change: 0, icon: 'users', tone: 'var(--accent-soft)', toneColor: 'var(--accent)' },
      { label: '总课程数', value: '--', change: 0, icon: 'book', tone: 'var(--info-soft)', toneColor: 'var(--info)' },
      { label: '日活用户', value: '--', change: 0, icon: 'monitor', tone: 'var(--ok-soft)', toneColor: 'var(--ok)' },
      { label: '完成率', value: '--', change: 0, icon: 'check', tone: 'var(--warn-soft)', toneColor: 'var(--warn)' },
      { label: '增长率', value: '--', change: 0, icon: 'chart', tone: 'var(--violet-soft)', toneColor: 'var(--violet)' },
    ]
    hasCategory.value = false
    hasGradeDist.value = false
    hasRoleActivity.value = false
    hasUserTrend.value = false
    hasHeatmap.value = false
    hasTopCourses.value = false
    categoryPieOption.value.series[0].data = []
    gradeDistOption.value.series[0].data = []
    roleActivityOption.value.series[0].data = []
    roleActivityOption.value.series[1].data = []
  }

  return {
    dateRange,
    loading,
    hasUserTrend,
    hasCategory,
    hasHeatmap,
    hasRoleActivity,
    hasGradeDist,
    hasTopCourses,
    dateShortcuts,
    coreMetrics,
    metricFoot,
    userTrendOption,
    categoryPieOption,
    heatmapColors,
    heatmapOption,
    roleActivityOption,
    gradeDistOption,
    topCoursesOption,
    fetchAnalyticsData,
    handleDateChange,
    exportReport,
  }
}
