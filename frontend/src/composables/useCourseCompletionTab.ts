import logger from '@/utils/logger'
import { ref, watch, onMounted } from 'vue'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import courseApi from '@/api/course'
import { getChartTheme } from '@/utils/chartTheme'

/**
 * 课程完成度 Tab 数据逻辑（拆分自 components/CourseCompletionTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseCompletionTab(courseIdRef) {
  const loadingAssignments = ref(false)
  const loadingStudents = ref(false)
  const viewMode = ref('assignment')

  const overview = ref({
    totalAssignments: 0,
    avgSubmissionRate: 0,
    avgScore: 0,
    totalSubmissions: 0,
  })

  const assignmentStats = ref([])
  const studentStats = ref([])

  const gradeDistOption = ref({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 45, right: 20, bottom: 25, top: 10 },
    xAxis: {
      type: 'category',
      data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
      axisLine: { lineStyle: { color: getChartTheme().axis } },
      axisLabel: { color: getChartTheme().subText, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: getChartTheme().axis, type: 'dashed' } },
      axisLabel: { color: getChartTheme().subText, fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: [0, 0, 0, 0, 0],
        barWidth: '40%',
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: (p) => {
            const colors = ['#ef4444', '#f59e0b', '#fbbf24', '#34d399', '#10b981']
            return colors[p.dataIndex] || '#3b82f6'
          },
        },
        label: {
          show: true,
          position: 'top',
          fontSize: 11,
          color: getChartTheme().subText,
        },
      },
    ],
  })

  function getRateColor(rate) {
    if (rate >= 90) return '#10b981'
    if (rate >= 60) return '#f59e0b'
    return '#ef4444'
  }

  function formatDate(d) {
    return d ? new Date(d).toLocaleString('zh-CN') : '-'
  }

  async function loadData() {
    loadingAssignments.value = true
    loadingStudents.value = true

    try {
      const cid = Number(courseIdRef.value)

      // 获取作业列表
      const taskRes = await taskApi.getTaskList({ courseId: cid })
      const tasks = taskRes.data?.records || taskRes.data?.list || taskRes.data || taskRes || []
      const taskList = Array.isArray(tasks) ? tasks : []

      // 获取学生列表
      let studentList = []
      try {
        const stuRes = await courseApi.getStudents(cid)
        const raw = stuRes.data?.records || stuRes.data?.list || stuRes.data || stuRes || []
        studentList = Array.isArray(raw) ? raw : []
      } catch (e) {
        logger.warn('[CourseCompletionTab] 获取学生列表失败:', e.message || e)
      }

      const totalStudents = studentList.length || 1

      // 作业级统计
      const taskStats = []
      let totalSubmissions = 0
      const allScores = []
      let totalRates = 0

      for (const t of taskList) {
        let submissions = []
        try {
          const subRes = await submissionApi.getTaskSubmissions(t.id)
          submissions = subRes.data?.records || subRes.data?.list || subRes.data || subRes || []
          submissions = Array.isArray(submissions) ? submissions : []
        } catch (e) {
          // 静默
        }

        const submittedCount = submissions.length
        const gradeSubs = submissions.filter((s) => s.score != null)
        const gradedCount = gradeSubs.length
        const scores = gradeSubs.map((s) => s.score)
        const avgScore =
          scores.length > 0
            ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10
            : 0
        allScores.push(...scores)

        const submissionRate = Math.round((submittedCount / Math.max(totalStudents, 1)) * 100)
        totalRates += submissionRate
        totalSubmissions += submittedCount

        taskStats.push({
          id: t.id,
          title: t.title || '未命名作业',
          status: t.status || 'DRAFT',
          dueDate: t.dueDate,
          totalStudents,
          submittedCount,
          unsubmittedCount: Math.max(0, totalStudents - submittedCount),
          submissionRate,
          gradedCount,
          avgScore,
        })
      }

      assignmentStats.value = taskStats

      // 概览统计
      const avgSubmissionRate = taskStats.length > 0 ? Math.round(totalRates / taskStats.length) : 0
      const totalAvgScore =
        allScores.length > 0
          ? Math.round((allScores.reduce((a, b) => a + b, 0) / allScores.length) * 10) / 10
          : 0

      overview.value = {
        totalAssignments: taskList.length,
        avgSubmissionRate,
        avgScore: totalAvgScore,
        totalSubmissions,
      }

      // 成绩分布直方图
      if (allScores.length > 0) {
        const buckets = [0, 0, 0, 0, 0]
        allScores.forEach((s) => {
          if (s < 60) buckets[0]++
          else if (s < 70) buckets[1]++
          else if (s < 80) buckets[2]++
          else if (s < 90) buckets[3]++
          else buckets[4]++
        })
        gradeDistOption.value.series[0].data = buckets
      }

      // 学生级统计
      const studentMap = new Map()
      studentList.forEach((s) => {
        const key = s.studentId || s.studentNo || s.id
        studentMap.set(String(key), {
          studentId: s.studentId || s.studentNo || String(s.id),
          studentName: s.studentName || s.name || s.username || '-',
          submittedCount: 0,
          totalAssignments: taskList.length,
          scores: [],
        })
      })

      for (const t of taskList) {
        try {
          const subRes = await submissionApi.getTaskSubmissions(t.id)
          const submissions =
            subRes.data?.records || subRes.data?.list || subRes.data || subRes || []
          const subs = Array.isArray(submissions) ? submissions : []
          subs.forEach((sub) => {
            const key = String(sub.studentId || sub.userId || sub.submitterId || '')
            const entry = studentMap.get(key)
            if (entry) {
              entry.submittedCount++
              if (sub.score != null) entry.scores.push(sub.score)
            }
          })
        } catch (e) {
          // 静默
        }
      }

      const stuStats = []
      studentMap.forEach((entry) => {
        const rate =
          entry.totalAssignments > 0
            ? Math.round((entry.submittedCount / entry.totalAssignments) * 100)
            : 0
        const sortedScores = [...entry.scores].sort((a, b) => a - b)
        stuStats.push({
          ...entry,
          submissionRate: rate,
          avgScore:
            entry.scores.length > 0
              ? Math.round((entry.scores.reduce((a, b) => a + b, 0) / entry.scores.length) * 10) /
                10
              : '-',
          maxScore: sortedScores.length > 0 ? sortedScores[sortedScores.length - 1] : '-',
          minScore: sortedScores.length > 0 ? sortedScores[0] : '-',
        })
      })

      studentStats.value = stuStats
    } catch (e) {
      logger.warn('[CourseCompletionTab] loadData 失败:', e.message || e)
    } finally {
      loadingAssignments.value = false
      loadingStudents.value = false
    }
  }

  onMounted(loadData)
  watch(() => courseIdRef.value, loadData)

  return {
    loadingAssignments,
    loadingStudents,
    viewMode,
    overview,
    assignmentStats,
    studentStats,
    gradeDistOption,
    getRateColor,
    formatDate,
    loadData,
  }
}
