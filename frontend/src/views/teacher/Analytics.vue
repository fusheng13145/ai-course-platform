<template>
  <div class="fade-in">
    <!-- 顶部：时间范围选择器 -->
    <div class="analytics-header">
      <h3 class="page-heading">教学数据分析</h3>
      <div class="time-selector">
        <el-radio-group v-model="timeRange" size="default" @change="handleTimeChange">
          <el-radio-button value="week">近7天</el-radio-button>
          <el-radio-button value="month">近30天</el-radio-button>
          <el-radio-button value="semester">本学期</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-primary-bg);color:var(--ac-primary)">
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ overview.studentCount || '--' }}</h3><p>学生总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(139,168,136,0.15);color:var(--ac-success)">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ overview.avgScore != null ? overview.avgScore : '--' }}</h3><p>平均成绩</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(203,181,138,0.18);color:var(--ac-warning)">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ overview.submissionRate != null ? overview.submissionRate + '%' : '--' }}</h3><p>作业提交率</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(196,128,128,0.15);color:var(--ac-danger)">
          <el-icon :size="24"><Calendar /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ overview.attendanceRate != null ? overview.attendanceRate + '%' : '--' }}</h3><p>出勤率</p></div>
      </div>
    </div>

    <!-- 图表区域：成绩趋势 + 成绩分布 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">成绩趋势</div>
        <v-chart :option="trendOption" style="height:300px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">成绩分布</div>
        <v-chart :option="distributionOption" style="height:300px" autoresize />
      </div>
    </div>

    <!-- 图表区域：课程对比雷达图 + 学生活跃度排行 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">各课程对比</div>
        <v-chart :option="radarOption" style="height:320px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">学生活跃度排行</div>
        <v-chart :option="activityRankOption" style="height:320px" autoresize />
      </div>
    </div>

    <!-- 作业完成情况表格 -->
    <div class="table-section">
      <div class="section-header">
        <span class="chart-title" style="margin-bottom:0">作业完成情况</span>
      </div>
      <div class="content-card" style="padding:0;overflow:hidden">
        <el-table :data="completionTable" style="width:100%" v-loading="loading" :header-cell-style="{ background:'var(--ac-bg)', color:'var(--ac-text-secondary)', fontWeight:600 }">
          <el-table-column prop="courseName" label="课程名称" min-width="140" />
          <el-table-column prop="taskName" label="任务/作业" min-width="140" />
          <el-table-column prop="submissionRate" label="提交率" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: getRateColor(row.submissionRate) }">{{ row.submissionRate }}%</span>
            </template>
          </el-table-column>
          <el-table-column prop="avgScore" label="平均分" width="80" align="center" />
          <el-table-column prop="maxScore" label="最高分" width="80" align="center">
            <template #default="{ row }"><span style="color:var(--ac-success)">{{ row.maxScore }}</span></template>
          </el-table-column>
          <el-table-column prop="minScore" label="最低分" width="80" align="center">
            <template #default="{ row }"><span style="color:var(--ac-danger)">{{ row.minScore }}</span></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && completionTable.length === 0" description="暂无数据" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'

const auth = useAuthStore()
const loading = ref(false)
const timeRange = ref('month')

// 总览数据
const overview = ref({
  studentCount: 0,
  avgScore: null,
  submissionRate: null,
  attendanceRate: null
})

// 成绩趋势折线图
const trendOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
  legend: {
    data: ['平均分', '最高分', '最低分'],
    top: 0,
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  grid: { left: 50, right: 20, bottom: 30, top: 40 },
  xAxis: {
    type: 'category',
    data: [],
    axisLine: { lineStyle: { color: '#E2DCD4' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#E2DCD4', type: 'dashed' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  series: [
    {
      name: '平均分',
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2.5, color: '#7A8BA5' },
      itemStyle: { color: '#7A8BA5' }
    },
    {
      name: '最高分',
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2, color: '#8BA888', type: 'dashed' },
      itemStyle: { color: '#8BA888' }
    },
    {
      name: '最低分',
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2, color: '#C48080', type: 'dashed' },
      itemStyle: { color: '#C48080' }
    }
  ]
})

// 成绩分布柱状图
const distributionOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 50, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
    axisLine: { lineStyle: { color: '#E2DCD4' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#E2DCD4', type: 'dashed' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  series: [{
    type: 'bar',
    data: [0, 0, 0, 0, 0],
    barWidth: '45%',
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: (params) => {
        const colors = ['#8BA888', '#7A8BA5', '#CBB58A', '#B89A8A', '#C48080']
        return colors[params.dataIndex] || '#7A8BA5'
      }
    }
  }]
})

// 课程对比雷达图
const radarOption = ref({
  tooltip: {},
  legend: {
    data: [],
    bottom: 0,
    textStyle: { color: '#5E574F', fontSize: 11 }
  },
  radar: {
    indicator: [
      { name: '平均分', max: 100 },
      { name: '提交率', max: 100 },
      { name: '出勤率', max: 100 },
      { name: '参与度', max: 100 },
      { name: '完成率', max: 100 }
    ],
    shape: 'circle',
    radius: '65%',
    center: ['50%', '48%'],
    axisName: { color: '#5E574F', fontSize: 11 },
    splitArea: { areaStyle: { color: ['rgba(122,139,165,0.03)', 'rgba(122,139,165,0.06)'] } },
    axisLine: { lineStyle: { color: '#E2DCD4' } },
    splitLine: { lineStyle: { color: '#E2DCD4' } }
  },
  series: [{
    type: 'radar',
    data: []
  }]
})

// 学生活跃度排行（横向柱状图）
const activityRankOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: 活跃值 {c}' },
  grid: { left: 80, right: 20, bottom: 20, top: 10 },
  xAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#E2DCD4', type: 'dashed' } },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  yAxis: {
    type: 'category',
    data: [],
    inverse: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#5E574F', fontSize: 12, width: 70, overflow: 'truncate' }
  },
  series: [{
    type: 'bar',
    data: [],
    barWidth: 16,
    itemStyle: {
      borderRadius: [0, 8, 8, 0],
      color: (params) => {
        const colors = ['#7A8BA5', '#8BA888', '#CBB58A', '#B89A8A', '#C48080', '#95A5BE', '#D1B5A5', '#9BB898']
        return colors[params.dataIndex % colors.length]
      }
    },
    label: { show: true, position: 'right', fontSize: 11, color: '#5E574F' }
  }]
})

// 作业完成情况表格
const completionTable = ref([])

onMounted(() => {
  loadAnalyticsData()
})

// 切换时间范围
async function handleTimeChange() {
  await loadAnalyticsData()
}

// 加载所有分析数据
async function loadAnalyticsData() {
  loading.value = true
  try {
    const res = await analyticsApi.getDashboardStats({ range: timeRange.value })
    const data = res.data || res || {}

    // 填充总览统计
    overview.value = {
      studentCount: data.studentCount ?? data.totalStudents ?? 0,
      avgScore: data.avgScore != null ? Math.round(data.avgScore * 10) / 10 : null,
      submissionRate: data.submissionRate != null ? Math.round(data.submissionRate) : null,
      attendanceRate: data.attendanceRate != null ? Math.round(data.attendanceRate) : null
    }

    // 填充趋势折线图
    if (data.gradeTrend && Array.isArray(data.gradeTrend)) {
      trendOption.value.xAxis.data = data.gradeTrend.map(d => d.label || d.date || d.name)
      trendOption.value.series[0].data = data.gradeTrend.map(d => d.avg ?? d.average ?? 0)
      trendOption.value.series[1].data = data.gradeTrend.map(d => d.max ?? d.highest ?? 0)
      trendOption.value.series[2].data = data.gradeTrend.map(d => d.min ?? d.lowest ?? 0)
    }

    // 填充成绩分布柱状图
    if (data.gradeDistribution && Array.isArray(data.gradeDistribution)) {
      distributionOption.value.series[0].data = data.gradeDistribution.map(d => d.count ?? d.value ?? 0)
    }

    // 填充雷达图 - 各课程对比
    if (data.courseComparison && Array.isArray(data.courseComparison)) {
      radarOption.value.legend.data = data.courseComparison.map(c => c.name || c.courseName || '')
      radarOption.value.series[0].data = data.courseComparison.map(c => ({
        name: c.name || c.courseName || '',
        value: [
          c.avgScore ?? c.avg ?? 0,
          c.submissionRate ?? c.submitRate ?? 0,
          c.attendanceRate ?? c.attendance ?? 0,
          c.engagement ?? c.participation ?? 0,
          c.completionRate ?? c.completeRate ?? 0
        ]
      }))
    }

    // 填充学生活跃度排行
    if (data.activityRanking && Array.isArray(data.activityRanking)) {
      const ranked = data.activityRanking.slice(0, 10).reverse()
      activityRankOption.value.yAxis.data = ranked.map(s => s.name || s.studentName || s.username || '-')
      activityRankOption.value.series[0].data = ranked.map((s, i) => ({
        value: s.score ?? s.activityValue ?? s.activeValue ?? 0,
        itemStyle: { borderRadius: [0, i === ranked.length - 1 ? 8 : 4, i === ranked.length - 1 ? 8 : 4, 0] }
      }))
    }

    // 填充作业完成表格
    if (data.taskCompletion && Array.isArray(data.taskCompletion)) {
      completionTable.value = data.taskCompletion.map(t => ({
        courseName: t.courseName || t.course || '-',
        taskName: t.taskName || t.name || t.title || '-',
        submissionRate: t.submissionRate != null ? Math.round(t.submissionRate) : '-',
        avgScore: t.avgScore != null ? Math.round(t.avgScore * 10) / 10 : '-',
        maxScore: t.maxScore ?? t.highest ?? '-',
        minScore: t.minScore ?? t.lowest ?? '-'
      }))
    }
  } catch (e) {
    console.error('加载分析数据失败:', e)
  } finally {
    loading.value = false
  }
}

// 根据提交率返回颜色
function getRateColor(rate) {
  if (rate >= 90) return '#8BA888'
  if (rate >= 70) return '#7A8BA5'
  if (rate >= 50) return '#CBB58A'
  return '#C48080'
}
</script>

<style scoped>
.analytics-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-lg);
  flex-wrap: wrap;
  gap: var(--ac-spacing-sm);
}

.page-heading {
  font-size: 18px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.time-selector :deep(.el-radio-button__inner) {
  border-color: var(--ac-border);
  color: var(--ac-text-secondary);
  font-size: 13px;
}

.time-selector :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: var(--ac-primary);
  border-color: var(--ac-primary);
  box-shadow: none;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
  letter-spacing: -0.2px;
}

.table-section {
  margin-top: var(--ac-spacing-md);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}

@media (max-width: 1024px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
