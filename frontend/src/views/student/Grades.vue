<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><TrophyBase /></el-icon></div><div class="stat-info"><h3>{{ avgScore }}</h3><p>平均分</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>{{ rank }}</h3><p>班级排名</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>{{ list.length }}</h3><p>已完成考试</p></div></div>
    </div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%" v-loading="loading">
        <el-table-column prop="examTitle" label="考试" min-width="150" />
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="score" label="成绩" width="80"><template #default="{row}"><el-tag :type="row.score >= 60 ? 'success' : 'danger'" size="small">{{ row.score }}</el-tag></template></el-table-column>
        <el-table-column prop="totalScore" label="总分" width="60" />
        <el-table-column prop="submitTime" label="时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button text type="primary" @click="viewDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无考试成绩" />
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import examApi from '@/api/exam'
import courseApi from '@/api/course'

const loading = ref(false)
const list = ref([])
const courseMap = ref({})
const examMap = ref({})

const avgScore = computed(() => {
  if (list.value.length === 0) return 0
  const sum = list.value.reduce((s, r) => s + (Number(r.score) || 0), 0)
  return Math.round(sum / list.value.length)
})
const rank = computed(() => list.value.length > 0 ? '--' : '--')

onMounted(async () => {
  loading.value = true
  try {
    const [courseRes, examRes, sessionRes] = await Promise.all([
      courseApi.getCourseList(),
      examApi.getExamPaperList(),
      examApi.getMyExamSessions()
    ])
    
    // Build course map
    const courses = courseRes.data?.list || courseRes.data || courseRes || []
    courses.forEach(c => { courseMap.value[c.id] = c.name })
    
    // Build exam map
    const exams = examRes.data?.list || examRes.data || examRes || []
    exams.forEach(e => { examMap.value[e.id] = { title: e.title, courseId: e.courseId, totalScore: e.totalScore || 100 } })
    
    // Build grade list from sessions
    const sessions = sessionRes.data || sessionRes || []
    list.value = sessions
      .filter(s => s.status === 'SUBMITTED' || s.status === 'GRADED')
      .map(s => {
        const exam = examMap.value[s.examPaperId] || {}
        return {
          id: s.id,
          examTitle: exam.title || '考试',
          courseName: courseMap.value[exam.courseId] || '未知课程',
          score: s.totalScore || 0,
          totalScore: exam.totalScore || 100,
          submitTime: s.submitTime || s.createdAt || ''
        }
      })
  } catch (e) {
    console.error('Failed to load grades:', e)
  } finally {
    loading.value = false
  }
})

function viewDetail(row) {
  ElMessage.info('成绩详情：' + row.examTitle)
}
</script>