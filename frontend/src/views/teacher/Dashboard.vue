<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-primary)"><el-icon :size="24"><Reading /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.courseCount }}</h3><p>我的课程</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-success)"><el-icon :size="24"><User /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.studentCount }}</h3><p>学生总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-warning)"><el-icon :size="24"><Document /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.pendingGradeCount }}</h3><p>待批改</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-danger)"><el-icon :size="24"><TrendCharts /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.completionRate }}</h3><p>平均完成率</p></div>
      </div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card">
        <div class="chart-title">课程学习趋势</div>
        <v-chart :option="lineOption" style="height:280px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">近期动态</div>
        <div v-for="a in activities" :key="a.id" class="activity-item">
          <span class="activity-text">{{ a.text }}</span>
          <span class="activity-time">{{ a.time }}</span>
        </div>
        <el-empty v-if="activities.length === 0" description="暂无动态" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'

const auth = useAuthStore()
const stats = ref({ courseCount: 0, studentCount: 0, pendingGradeCount: 0, completionRate: '0%' })
const activities = ref([])

const lineOption = ref({
  xAxis: { type: "category", data: ["第1周","第2周","第3周","第4周","第5周","第6周"] },
  yAxis: { type: "value" },
  series: [{ data: [0, 0, 0, 0, 0, 0], type: "line", smooth: true, areaStyle: {} }],
  tooltip: { trigger: "axis" },
  grid: { left: 40, right: 20, bottom: 30, top: 10 }
})

onMounted(async () => {
  try {
    const userId = auth.userId
    if (userId) {
      const res = await analyticsApi.getTeacherAnalytics(userId)
      const data = res.data || res || {}
      stats.value = {
        courseCount: data.courseCount || 0,
        studentCount: data.studentCount || 0,
        pendingGradeCount: data.pendingGradeCount || 0,
        completionRate: data.completionRate ? Math.round(data.completionRate) + '%' : '0%'
      }
      if (data.trendData && Array.isArray(data.trendData)) {
        lineOption.value.series[0].data = data.trendData
      }
      if (data.activities && Array.isArray(data.activities)) {
        activities.value = data.activities
      }
    }
  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
})
</script>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-md);
  border: 1px solid var(--ac-border);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.activity-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--ac-border);
  font-size: 13px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-text {
  color: var(--ac-text-secondary);
}

.activity-time {
  color: var(--ac-text-muted);
  font-size: 12px;
}

@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
