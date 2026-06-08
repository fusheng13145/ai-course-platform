<template>
  <div class="fade-in">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-primary-bg);color:var(--ac-primary)">
          <el-icon :size="24"><Reading /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.courseCount }}</h3>
          <p>我的课程</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(139,168,136,0.15);color:var(--ac-success)">
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.studentCount }}</h3>
          <p>学生总数</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(203,181,138,0.18);color:var(--ac-warning)">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.pendingGradeCount }}</h3>
          <p>待批作业数</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(196,128,128,0.15);color:var(--ac-danger)">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.avgScore }}</h3>
          <p>平均成绩</p>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="dashboard-grid">
      <div class="chart-card">
        <div class="chart-title">近期教学活动趋势</div>
        <v-chart :option="lineOption" style="height:280px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">课程分布</div>
        <v-chart :option="pieOption" style="height:280px" autoresize />
      </div>
    </div>

    <!-- 近期动态 -->
    <div class="chart-card activity-card">
      <div class="chart-title">近期动态</div>
      <div v-if="activities.length > 0" class="activity-list">
        <div v-for="item in activities" :key="item.id" class="activity-item">
          <div class="activity-left">
            <span class="activity-dot" :class="'dot-' + item.type"></span>
            <span class="activity-text">{{ item.text }}</span>
          </div>
          <span class="activity-time">{{ item.time }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无近期动态" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'

const auth = useAuthStore()

// 统计数据
const stats = ref({
  courseCount: 0,
  studentCount: 0,
  pendingGradeCount: 0,
  avgScore: '--'
})

// 近期动态
const activities = ref([])

// 教学活动折线图配置
const lineOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 40, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: [],
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
    data: [],
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 2.5, color: '#7A8BA5' },
    itemStyle: { color: '#7A8BA5' },
    areaStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(122,139,165,0.25)' },
          { offset: 1, color: 'rgba(122,139,165,0.02)' }
        ]
      }
    }
  }]
})

// 课程分布饼图配置
const pieOption = ref({
  tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
  legend: {
    orient: 'vertical',
    right: 10,
    top: 'center',
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    center: ['40%', '50%'],
    avoidLabelOverlap: true,
    itemStyle: { borderRadius: 6, borderColor: '#FFFCF8', borderWidth: 2 },
    label: { show: false },
    emphasis: {
      label: { show: true, fontSize: 13, fontWeight: 'bold', color: '#3D3835' }
    },
    data: [],
    color: ['#7A8BA5', '#B89A8A', '#8BA888', '#CBB58A', '#C48080']
  }]
})

onMounted(async () => {
  await loadDashboardData()
})

async function loadDashboardData() {
  try {
    const res = await analyticsApi.getDashboardStats()
    const data = res.data || res || {}

    // 填充统计卡片
    stats.value = {
      courseCount: data.courseCount || data.totalCourses || 0,
      studentCount: data.studentCount || data.totalStudents || 0,
      pendingGradeCount: data.pendingGradeCount || data.pendingGrading || 0,
      avgScore: data.avgScore != null ? (Math.round(data.avgScore * 10) / 10) : '--'
    }

    // 填充趋势图
    if (data.trendData && Array.isArray(data.trendData)) {
      const labels = data.trendData.map(d => d.label || d.date || d.name)
      const values = data.trendData.map(d => d.value || d.count || 0)
      lineOption.value.xAxis.data = labels.length > 0 ? labels : []
      lineOption.value.series[0].data = values.length > 0 ? values : [0, 0, 0, 0, 0, 0]
    }

    // 填充课程分布饼图
    if (data.courseDistribution && Array.isArray(data.courseDistribution)) {
      pieOption.value.series[0].data = data.courseDistribution.map(c => ({
        name: c.name || c.courseName || '未知',
        value: c.value || c.studentCount || 0
      }))
    }

    // 填充近期动态
    if (data.activities && Array.isArray(data.activities)) {
      activities.value = data.activities.map(a => ({
        id: a.id || Math.random(),
        text: a.text || a.content || a.description || '',
        time: a.time || a.createdAt || '',
        type: a.type || 'info'
      }))
    }
  } catch (e) {
    console.error('加载仪表盘数据失败:', e)
  }
}
</script>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
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

.activity-card {
  margin-top: 0;
}

.activity-list {
  max-height: 280px;
  overflow-y: auto;
}

.activity-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--ac-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background var(--ac-transition-fast);
  border-radius: var(--ac-radius-sm);
  padding-left: 8px;
  padding-right: 8px;
  margin: 2px 0;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-item:hover {
  background: var(--ac-bg-hover);
}

.activity-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-submit { background: #7A8BA5; }
.dot-question { background: #B89A8A; }
.dot-exam { background: #CBB58A; }
.dot-grade { background: #8BA888; }
.dot-info { background: #928B83; }

.activity-text {
  color: var(--ac-text-secondary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.activity-time {
  color: var(--ac-text-muted);
  font-size: 12px;
  flex-shrink: 0;
  margin-left: 12px;
}

@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
