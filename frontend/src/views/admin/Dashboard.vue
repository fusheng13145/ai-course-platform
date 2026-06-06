<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><User /></el-icon></div><div class="stat-info"><h3>{{ stats.userCount }}</h3><p>用户总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>{{ stats.courseCount }}</h3><p>课程总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>{{ stats.examCount }}</h3><p>考试总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><TrophyBase /></el-icon></div><div class="stat-info"><h3>{{ stats.submissionCount }}</h3><p>提交总数</p></div></div>
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
        <div v-for="log in recentLogs" :key="log.id" style="padding:8px 0;border-bottom:1px solid #f0f0f0;font-size:13px;">
          <el-tag size="small" :type="log.level === 'ERROR' ? 'danger' : 'info'" style="margin-right:8px">{{ log.level }}</el-tag>
          <span style="color:#606266">{{ log.message }}</span>
          <span style="color:#c0c4cc;float:right;font-size:12px">{{ log.time }}</span>
        </div>
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
  recentLogs.value = [
    { id: 1, level: 'INFO', message: '系统运行正常', time: '10:30' },
    { id: 2, level: 'INFO', message: '用户 admin 登录', time: '10:28' },
    { id: 3, level: 'WARN', message: '磁盘使用率 75%', time: '10:25' }
  ]
})
</script>