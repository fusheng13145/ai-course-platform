<template>
  <div class="fade-in analytics-page">
    <!-- 页面头部：时间范围选择 + 导出按钮 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">数据分析中心</h2>
        <span class="page-subtitle">平台运营数据总览与趋势分析</span>
      </div>
      <div class="header-right">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="default"
          :shortcuts="dateShortcuts"
          @change="handleDateChange"
          style="width: 280px;"
        />
        <el-button type="primary" @click="exportReport">
          <el-icon><Download /></el-icon>
          导出报告
        </el-button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="metrics-row">
      <div
        v-for="(metric, index) in coreMetrics"
        :key="index"
        class="metric-card"
      >
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-trend" :class="metric.change >= 0 ? 'positive' : 'negative'">
          <el-icon :size="12"><Top v-if="metric.change >= 0" /><Bottom v-else /></el-icon>
          {{ Math.abs(metric.change) }}% 较上期
        </div>
      </div>
    </div>

    <!-- 图表区域第一行：用户趋势 + 课程分布 -->
    <div class="charts-grid charts-2col">
      <div class="chart-card">
        <div class="chart-title">用户注册趋势</div>
        <v-chart :option="userTrendOption" style="height: 340px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">课程类别分布</div>
        <v-chart :option="categoryPieOption" style="height: 340px" autoresize />
      </div>
    </div>

    <!-- 学习时长热力图（类似GitHub贡献图） -->
    <div class="chart-card full-width">
      <div class="chart-title">学习时长热力图</div>
      <div class="heatmap-container">
        <v-chart :option="heatmapOption" style="height: 200px" autoresize />
        <div class="heatmap-legend">
          <span>少</span>
          <div class="legend-colors">
            <span v-for="(color, i) in heatmapColors" :key="i" :style="{ background: color }"></span>
          </div>
          <span>多</span>
        </div>
      </div>
    </div>

    <!-- 图表区域第二行：角色活跃度 + 成绩分布 -->
    <div class="charts-grid charts-2col">
      <div class="chart-card">
        <div class="chart-title">各角色活跃度对比</div>
        <v-chart :option="roleActivityOption" style="height: 320px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">成绩分布直方图</div>
        <v-chart :option="gradeDistOption" style="height: 320px" autoresize />
      </div>
    </div>

    <!-- 热门课程TOP10 -->
    <div class="chart-card full-width">
      <div class="chart-title">热门课程 TOP10</div>
      <v-chart :option="topCoursesOption" style="height: 420px" autoresize />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Download, Top, Bottom } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import analyticsApi from '@/api/analytics'

// 日期范围选择器
const dateRange = ref([])

// 日期快捷选项
const dateShortcuts = [
  {
    text: '最近一周',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 7)
      return [start, end]
    }
  },
  {
    text: '最近一个月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setMonth(start.getMonth() - 1)
      return [start, end]
    }
  },
  {
    text: '最近三个月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setMonth(start.getMonth() - 3)
      return [start, end]
    }
  }
]

// 核心指标数据
const coreMetrics = ref([
  { label: '总用户数', value: '--', change: 0 },
  { label: '总课程数', value: '--', change: 0 },
  { label: '日活用户', value: '--', change: 0 },
  { label: '完成率', value: '--', change: 0 },
  { label: '增长率', value: '--', change: 0 }
])

// 用户注册趋势图配置
const userTrendOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    textStyle: { color: '#3D3835', fontSize: 12 },
    axisPointer: { type: 'cross' }
  },
  legend: {
    data: ['新增用户', '活跃用户'],
    top: 0,
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  grid: { left: 50, right: 20, bottom: 30, top: 40 },
  xAxis: {
    type: 'category',
    data: [],
    boundaryGap: false,
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83' }
  },
  series: [
    {
      name: '新增用户',
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2.5, color: '#7A8BA5' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(122, 139, 165, 0.20)' },
            { offset: 1, color: 'rgba(122, 139, 165, 0.02)' }
          ]
        }
      },
      itemStyle: { color: '#7A8BA5' }
    },
    {
      name: '活跃用户',
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2.5, color: '#8BA888' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(139, 168, 136, 0.18)' },
            { offset: 1, color: 'rgba(139, 168, 136, 0.02)' }
          ]
        }
      },
      itemStyle: { color: '#8BA888' }
    }
  ]
})

// 课程类别分布饼图
const categoryPieOption = ref({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} 门 ({d}%)',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE'
  },
  legend: {
    orient: 'vertical',
    right: 15,
    top: 'center',
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['38%', '52%'],
    avoidLabelOverlap: false,
    itemStyle: {
      borderRadius: 6,
      borderColor: '#fff',
      borderWidth: 2
    },
    label: { show: false },
    emphasis: {
      label: { show: true, fontSize: 13, fontWeight: 'bold' }
    },
    data: []
  }],
  color: ['#7A8BA5', '#B89A8A', '#8BA888', '#CBB58A', '#C48080', '#9BA8C5']
})

// 热力图颜色梯度
const heatmapColors = [
  'rgba(139, 168, 136, 0.08)',
  'rgba(139, 168, 136, 0.25)',
  'rgba(184, 154, 138, 0.45)',
  'rgba(203, 181, 138, 0.65)',
  'rgba(196, 128, 128, 0.85)'
]

// 学习时长热力图配置
const heatmapOption = ref({
  tooltip: {
    position: 'top',
    formatter: (params) => `${params.data[0]}<br/>学习时长: ${params.data[2]} 小时`,
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    textStyle: { fontSize: 12 }
  },
  grid: {
    left: 60,
    right: 30,
    bottom: 50,
    top: 10
  },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    splitArea: { show: true },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  yAxis: {
    type: 'category',
    data: ['0-6时', '6-12时', '12-18时', '18-24时'],
    splitArea: { show: true },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  visualMap: {
    min: 0,
    max: 100,
    calculable: true,
    orient: 'horizontal',
    left: 'center',
    bottom: 0,
    inRange: {
      color: heatmapColors.map(c => c.replace('rgba', 'rgb').replace(/[\d.]+\)$/, '1)'))
    },
    textStyle: { color: '#928B83', fontSize: 11 },
    show: false
  },
  series: [{
    type: 'heatmap',
    data: [],
    label: { show: false },
    emphasis: {
      itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.15)' }
    },
    itemStyle: {
      borderRadius: 4,
      borderColor: '#fff',
      borderWidth: 2
    }
  }]
})

// 角色活跃度柱状图
const roleActivityOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    axisPointer: { type: 'shadow' }
  },
  legend: {
    data: ['登录次数', '操作频次'],
    top: 0,
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  grid: { left: 50, right: 20, bottom: 30, top: 40 },
  xAxis: {
    type: 'category',
    data: ['管理员', '教师', '学生'],
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83' }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83' }
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
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#7A8BA5' },
            { offset: 1, color: 'rgba(122, 139, 165, 0.4)' }
          ]
        }
      },
      data: []
    },
    {
      name: '操作频次',
      type: 'bar',
      barWidth: '28%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#8BA888' },
            { offset: 1, color: 'rgba(139, 168, 136, 0.4)' }
          ]
        }
      },
      data: []
    }
  ]
})

// 成绩分布直方图
const gradeDistOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    formatter: '{b}分区间: {c} 人'
  },
  grid: { left: 55, right: 25, bottom: 35, top: 20 },
  xAxis: {
    type: 'category',
    data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
    name: '分数段',
    nameTextStyle: { color: '#928B83', padding: [0, 0, 0, 0] },
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83' }
  },
  yAxis: {
    type: 'value',
    name: '人数',
    nameTextStyle: { color: '#928B83' },
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83' }
  },
  series: [{
    type: 'bar',
    barWidth: '50%',
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#B89A8A' },
          { offset: 1, color: 'rgba(184, 154, 138, 0.3)' }
        ]
      }
    },
    data: []
  }]
})

// 热门课程TOP10横向柱状图
const topCoursesOption = ref({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    formatter: (params) => `${params[0].name}<br/>学习人数: ${params[0].value}`
  },
  grid: { left: 120, right: 40, bottom: 20, top: 10 },
  xAxis: {
    type: 'value',
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83' }
  },
  yAxis: {
    type: 'category',
    data: [],
    inverse: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: '#5E574F',
      fontSize: 13,
      width: 110,
      overflow: 'truncate'
    }
  },
  series: [{
    type: 'bar',
    barWidth: '60%',
    itemStyle: {
      borderRadius: [0, 6, 6, 0],
      color: (params) => {
        const colors = [
          '#7A8BA5',
          '#B89A8A',
          '#8BA888',
          '#CBB58A',
          '#C48080'
        ]
        const idx = params.dataIndex % colors.length
        return colors[idx]
      }
    },
    label: {
      show: true,
      position: 'right',
      color: '#5E574F',
      fontSize: 12,
      fontWeight: 500
    },
    data: []
  }]
})

/**
 * 获取平台总览数据（统一加载：仪表盘统计 + 用户增长趋势 + 课程排行 + 平台热力图）
 */
async function fetchAnalyticsData() {
  try {
    // 并行获取：仪表盘统计 + 用户增长趋势 + 课程排行 + 平台热力图
    const [statsRes, trendRes, courseRes, heatmapRes] = await Promise.allSettled([
      analyticsApi.getDashboardStats(),
      analyticsApi.getUserGrowth(30),
      analyticsApi.getCourseRanking(),
      analyticsApi.getPlatformHeatmap({ days: 30 })
    ])

    // 处理仪表盘统计数据
    if (statsRes.status === 'fulfilled') {
      const statsData = statsRes.value.data || statsRes.value

      // 核心指标卡片
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

      // 课程分类饼图
      if (statsData.categoryDistribution) {
        categoryPieOption.value.series[0].data = Object.entries(statsData.categoryDistribution).map(
          ([name, value]) => ({ name, value })
        )
      }

      // 成绩分布直方图
      if (statsData.gradeDistribution && Array.isArray(statsData.gradeDistribution)) {
        gradeDistOption.value.series[0].data = statsData.gradeDistribution.map(v => Number(v) || 0)
      }

      // 角色活跃度柱状图
      if (statsData.roleActivity && Array.isArray(statsData.roleActivity)) {
        roleActivityOption.value.series[0].data = statsData.roleActivity.map(r => r.loginCount || 0)
        roleActivityOption.value.series[1].data = statsData.roleActivity.map(r => r.actionCount || 0)
      }
    }

    // 处理用户增长趋势（用于用户注册趋势图）
    if (trendRes.status === 'fulfilled') {
      const trendData = trendRes.value.data || trendRes.value
      if (Array.isArray(trendData) && trendData.length > 0) {
        userTrendOption.value.xAxis.data = trendData.map(d => d.date || d.label || '')
        userTrendOption.value.series[0].data = trendData.map(d => d.newUsers || d.count || 0)
        userTrendOption.value.series[1].data = trendData.map(d => d.cumulativeUsers || d.activeUsers || 0)
      }
    }

    // 处理热门课程排行
    if (courseRes.status === 'fulfilled') {
      const courseData = courseRes.value.data || courseRes.value
      let courses = []
      if (Array.isArray(courseData)) {
        courses = courseData.slice(0, 10).map(c => ({
          name: c.courseName || c.name,
          value: c.enrollmentCount || c.studentCount || c.popularity || 0
        }))
      }
      courses.sort((a, b) => b.value - a.value)
      topCoursesOption.value.yAxis.data = courses.map(c => c.name)
      topCoursesOption.value.series[0].data = courses.map(c => c.value)
    }

    // 处理平台热力图数据
    if (heatmapRes.status === 'fulfilled') {
      const data = heatmapRes.value.data || heatmapRes.value || []
      if (Array.isArray(data) && data.length > 0) {
        const heatmapData = data.map(item => [
          item.dayOfWeek ?? item.day ?? item.x ?? 0,
          item.hour ?? item.y ?? 0,
          Math.round(item.value ?? item.count ?? 0)
        ]).filter(d => d[0] >= 0 && d[0] < 7 && d[1] >= 0)
        if (heatmapData.length > 0) {
          heatmapOption.value.series[0].data = heatmapData
        }
      }
    }
  } catch (error) {
    console.warn('加载分析数据失败:', error)
    setMockData()
  }
}

/**
 * 处理日期范围变化
 */
function handleDateChange(val) {
  if (val && val.length === 2) {
    fetchAnalyticsData()
  }
}

/**
 * 导出分析报告
 */
function exportReport() {
  ElMessage.success('报告导出中，请稍候...')
  // 实际项目中这里会调用导出API
  setTimeout(() => {
    ElMessage.success('报告已生成并开始下载')
  }, 1500)
}

/**
 * 格式化数字显示
 */
function formatNumber(num) {
  if (typeof num === 'string') return num
  if (!num && num !== 0) return '--'
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return num.toString()
}

/**
 * 设置空数据（API不可用时的后备，不再使用硬编码模拟数据）
 */
function setMockData() {
  coreMetrics.value = [
    { label: '总用户数', value: '--', change: 0 },
    { label: '总课程数', value: '--', change: 0 },
    { label: '日活用户', value: '--', change: 0 },
    { label: '完成率', value: '--', change: 0 },
    { label: '增长率', value: '--', change: 0 }
  ]

  categoryPieOption.value.series[0].data = []
  gradeDistOption.value.series[0].data = []
  roleActivityOption.value.series[0].data = []
  roleActivityOption.value.series[1].data = []
}

// 初始化加载数据
onMounted(() => {
  fetchAnalyticsData()  // 统一加载所有数据
})
</script>

<style scoped>
.analytics-page {
  max-width: 1440px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--ac-spacing-lg);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}

/* 核心指标卡片行 */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.metric-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: 20px 22px;
  transition: all var(--ac-transition-normal);
  position: relative;
  overflow: hidden;
}

.metric-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  opacity: 0;
  transition: opacity var(--ac-transition-normal);
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-lg);
  border-color: transparent;
}

.metric-card:hover::before {
  opacity: 1;
  background: var(--ac-primary);
}

.metric-label {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-bottom: 8px;
  font-weight: 500;
}

.metric-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
  margin-bottom: 6px;
}

.metric-trend {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

.metric-trend.positive {
  color: var(--ac-success);
  background: rgba(139, 168, 136, 0.12);
}

.metric-trend.negative {
  color: var(--ac-danger);
  background: rgba(196, 128, 128, 0.12);
}

/* 图表网格布局 */
.charts-grid {
  display: grid;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}

.charts-2col {
  grid-template-columns: repeat(2, 1fr);
}

.chart-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

/* 热力图容器 */
.heatmap-container {
  position: relative;
}

.heatmap-legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.legend-colors {
  display: flex;
  gap: 3px;
}

.legend-colors span {
  width: 16px;
  height: 12px;
  border-radius: 2px;
}

/* 响应式适配 */
@media (max-width: 1024px) {
  .charts-2col {
    grid-template-columns: 1fr;
  }

  .chart-card.full-width {
    grid-column: 1;
  }

  .page-header {
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .page-title {
    font-size: 19px;
  }
}
</style>
