import { ref, computed } from 'vue'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'

/** hex → rgba 工具（图表渐变用） */
function rgba(hex, a) {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
}

/**
 * 数据分析中心（管理员）图表配置域
 * <p>承载六类图表的 ECharts 配置构建（用户趋势/课程分布/学习热力图/角色活跃度/
 * 成绩分布/热门课程），只建配置不做拉取；数据回填由 useAnalyticsOverview 负责。</p>
 */
export function useAnalyticsChartOptions() {
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
      legend: {
        data: ['新增用户', '活跃用户'],
        top: 0,
        textStyle: { color: t.subText, fontSize: 12 },
      },
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
      legend: {
        orient: 'vertical',
        right: 15,
        top: 'center',
        textStyle: { color: t.subText, fontSize: 12 },
      },
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
          color: heatmapColors.value.map((c) =>
            c.replace('rgba', 'rgb').replace(/[\d.]+\)$/, '1)'),
          ),
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
      legend: {
        data: ['登录次数', '操作频次'],
        top: 0,
        textStyle: { color: t.subText, fontSize: 12 },
      },
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

  return {
    userTrendOption,
    categoryPieOption,
    heatmapColors,
    heatmapOption,
    roleActivityOption,
    gradeDistOption,
    topCoursesOption,
  }
}
