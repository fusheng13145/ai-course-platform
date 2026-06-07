<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>{{ stats.courseCount }}</h3><p>我的课程</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><User /></el-icon></div><div class="stat-info"><h3>{{ stats.studentCount }}</h3><p>学生总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>{{ stats.pendingGradeCount }}</h3><p>待批改</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>{{ stats.completionRate }}</h3><p>平均完成率</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">课程学习趋势</div><v-chart :option="lineOption" style="height:280px" autoresize /></div>
      <div class="chart-card"><div class="chart-title">近期动态</div>
        <div v-for="a in activities" :key="a.id" style="padding:8px 0;border-bottom:1px solid #f0f0f0;font-size:13px">
          <span style="color:#606266">{{ a.text }}</span><span style="color:#c0c4cc;float:right;font-size:12px">{{ a.time }}</span>
        </div>
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
      const res = await analyticsApi.getStudentAnalytics(userId)
      const data = res.data || res || {}
      stats.value = {
        courseCount: data.courseCount || 0,
        studentCount: data.studentCount || 0,
        pendingGradeCount: data.pendingGradeCount || 0,
        completionRate: data.completionRate ? data.completionRate + '%' : '0%'
      }
      if (data.trendData) {
        lineOption.value.series[0].data = data.trendData
      }
    }
  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
})
</script>
