<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>{{ stats.courseCount }}</h3><p>在学课程</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><TrophyBase /></el-icon></div><div class="stat-info"><h3>{{ stats.avgScore }}</h3><p>平均分</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Calendar /></el-icon></div><div class="stat-info"><h3>{{ stats.streakDays }}</h3><p>连续学习天数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><WarningFilled /></el-icon></div><div class="stat-info"><h3>{{ stats.todoCount }}</h3><p>待办事项</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">本周学习时长</div><v-chart :option="barOption" style="height:280px" autoresize /></div>
      <div class="chart-card">
        <div class="chart-title">课程进度</div>
        <div v-for="c in courses" :key="c.id" style="margin-bottom:16px">
          <div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:4px"><span>{{ c.name }}</span><span>{{ c.progress }}%</span></div>
          <el-progress :percentage="c.progress" :color="c.color" :stroke-width="8" />
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'
import enrollmentApi from '@/api/enrollment'
import courseApi from '@/api/course'

const auth = useAuthStore()
const stats = ref({ courseCount: 0, avgScore: 0, streakDays: 0, todoCount: 0 })
const courses = ref([])

const barOption = ref({
  xAxis: { type: "category", data: ["周一","周二","周三","周四","周五","周六","周日"] },
  yAxis: { type: "value" },
  series: [{ type: "bar", data: [0, 0, 0, 0, 0, 0, 0], itemStyle: { borderRadius: [4,4,0,0], color: "#409eff" } }],
  tooltip: { trigger: "axis" },
  grid: { left: 40, right: 20, bottom: 30, top: 10 }
})

onMounted(async () => {
  try {
    const userId = auth.userId
    if (userId) {
      const analyticsRes = await analyticsApi.getStudentAnalytics(userId)
      const data = analyticsRes.data || analyticsRes || {}
      stats.value = {
        courseCount: data.enrolledCourses || 0,
        avgScore: data.averageProgress ? Math.round(data.averageProgress) : 0,
        streakDays: data.totalStudyTime ? Math.round(data.totalStudyTime / 60) : 0,
        todoCount: 0
      }
    }
    const [enrollRes, courseRes] = await Promise.all([
      enrollmentApi.getMyEnrollments(),
      courseApi.getCourseList()
    ])
    const enrollData = enrollRes.data?.list || enrollRes.data || enrollRes || []
    const courseList = courseRes.data?.list || courseRes.data || courseRes || []
    const courseMap = {}
    courseList.forEach(c => { courseMap[c.id] = c.name })
    courses.value = enrollData.map(e => ({
      id: e.courseId,
      name: courseMap[e.courseId] || '课程#' + e.courseId,
      progress: e.progress ? Math.round(Number(e.progress)) : 0,
      color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c'][Math.floor(Math.random() * 4)]
    }))
  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
})
</script>
