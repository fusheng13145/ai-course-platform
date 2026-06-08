<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-primary)"><el-icon :size="24"><User /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.userCount }}</h3><p>用户总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-success)"><el-icon :size="24"><Reading /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.courseCount }}</h3><p>课程总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-warning)"><el-icon :size="24"><Document /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.examCount }}</h3><p>考试总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-danger)"><el-icon :size="24"><TrophyBase /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.submissionCount }}</h3><p>提交总数</p></div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="chart-card full-width">
        <div class="chart-title">用户增长趋势</div>
        <v-chart :option="trendOption" style="height:300px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">课程分布</div>
        <v-chart :option="pieOption" style="height:300px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">系统动态</div>
        <div v-for="log in recentLogs" :key="log.id" class="log-item">
          <el-tag size="small" :type="log.level === 'ERROR' ? 'danger' : 'info'" class="log-tag">{{ log.level }}</el-tag>
          <span class="log-message">{{ log.message }}</span>
          <span class="log-time">{{ log.time }}</span>
        </div>
        <el-empty v-if="recentLogs.length === 0" description="暂无动态" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import analyticsApi from '@/api/analytics'

const stats = ref({ userCount: 0, courseCount: 0, examCount: 0, submissionCount: 0 })
const recentLogs = ref([])

const trendOption = ref({
  xAxis: { type: 'category', data: ['1月','2月','3月','4月','5月','6月'] },
  yAxis: { type: 'value' },
  series: [{ data: [120, 200, 150, 280, 350, 420], type: 'line', smooth: true, areaStyle: {} }],
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, bottom: 30, top: 10 }
})

const pieOption = ref({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie', radius: ['40%', '70%'],
    data: [
      { name: '编程开发', value: 25 },
      { name: '人工智能', value: 18 },
      { name: '数据分析', value: 15 },
      { name: '设计创意', value: 12 },
      { name: '其他', value: 10 }
    ],
    label: { show: true, formatter: '{b}: {d}%' }
  }]
})

onMounted(async () => {
  try {
    const res = await analyticsApi.getDashboardStats()
    if (res.data) stats.value = res.data
  } catch (e) { /* use defaults */ }
  try {
    const logRes = await import('@/api/system').then(m => m.default.getSystemLogs({ limit: 5 }))
    const logData = logRes.data?.list || logRes.data || logRes || []
    recentLogs.value = Array.isArray(logData) ? logData.slice(0, 5) : []
  } catch (e) {
    recentLogs.value = []
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

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.log-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--ac-border);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-item:last-child {
  border-bottom: none;
}

.log-tag {
  flex-shrink: 0;
}

.log-message {
  flex: 1;
  color: var(--ac-text-secondary);
}

.log-time {
  color: var(--ac-text-muted);
  font-size: 12px;
}

@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-card.full-width {
    grid-column: 1;
  }
}
</style>