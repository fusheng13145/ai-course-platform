import logger from '@/utils/logger'
import { ref, watch, onMounted } from 'vue'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import courseApi from '@/api/course'
import userApi from '@/api/user'
import { getChartTheme } from '@/utils/chartTheme'
import { pickList, unwrap } from '@/utils/response'

function submissionStudentKey(submission) {
  const id = submission?.studentId ?? submission?.userId ?? submission?.submitterId
  return id == null || id === '' ? null : String(id)
}

function isNewerAttempt(candidate, current) {
  const candidateAttempt = Number(candidate?.attemptNumber) || 0
  const currentAttempt = Number(current?.attemptNumber) || 0
  if (candidateAttempt !== currentAttempt) return candidateAttempt > currentAttempt

  const candidateTime = Date.parse(candidate?.submittedAt || candidate?.createdAt || '') || 0
  const currentTime = Date.parse(current?.submittedAt || current?.createdAt || '') || 0
  if (candidateTime !== currentTime) return candidateTime > currentTime

  return (Number(candidate?.id) || 0) > (Number(current?.id) || 0)
}

function latestSubmissionPerStudent(submissions) {
  const latestByStudent = new Map()
  for (const submission of submissions) {
    const studentKey = submissionStudentKey(submission)
    if (!studentKey) continue
    const current = latestByStudent.get(studentKey)
    if (!current || isNewerAttempt(submission, current)) latestByStudent.set(studentKey, submission)
  }
  return [...latestByStudent.values()]
}

function numericScore(value) {
  if (value == null || value === '') return null
  const score = Number(value)
  return Number.isFinite(score) ? score : null
}

/**
 * 课程完成度 Tab 数据逻辑（拆分自 components/CourseCompletionTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseCompletionTab(courseIdRef) {
  const loadingAssignments = ref(false)
  const loadingStudents = ref(false)
  const loadNotice = ref('')
  const viewMode = ref('assignment')

  const overview = ref({
    totalAssignments: 0,
    avgSubmissionRate: null,
    avgScore: null,
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
    loadNotice.value = ''
    assignmentStats.value = []
    studentStats.value = []
    overview.value = {
      totalAssignments: 0,
      avgSubmissionRate: null,
      avgScore: null,
      totalSubmissions: 0,
    }
    gradeDistOption.value.series[0].data = [0, 0, 0, 0, 0]

    try {
      const cid = Number(courseIdRef.value)

      // 获取作业列表
      const allTasks = (await taskApi.getAllTasks({ courseId: cid })) as Array<{
        id?: number | string
        title?: string
        status?: string
        taskType?: string
        dueDate?: string
      }>
      const taskList = allTasks.filter((task) => {
        const type = String(task.taskType || '')
          .trim()
          .toUpperCase()
        return !type || ['ASSIGNMENT', 'HOMEWORK', '作业'].includes(type)
      })

      // 获取学生列表
      let studentList = []
      let studentListAvailable = true
      const notices = []
      try {
        const enrollments = (await courseApi.getAllStudents(cid)) as Array<{
          isActive?: boolean
          approvalStatus?: string
          studentId?: number | string
        }>
        studentList = enrollments.filter(
          (enrollment) =>
            enrollment?.isActive === true && enrollment?.approvalStatus === 'APPROVED',
        )
      } catch (e) {
        logger.warn('[CourseCompletionTab] 获取学生列表失败:', e.message || e)
        studentListAvailable = false
        notices.push('学生名单暂不可用，提交率统计不完整。')
      }

      const totalStudents = studentListAvailable ? studentList.length : null

      let teacherStudents = []
      try {
        teacherStudents = pickList(unwrap(await userApi.getTeacherStudents()))
      } catch (e) {
        logger.warn('[CourseCompletionTab] 获取学生姓名失败:', e.message || e)
      }
      const usersById = new Map(teacherStudents.map((student) => [String(student.id), student]))
      const rosterIds = new Set(studentList.map((student) => String(student.studentId)))

      // 作业级统计
      const taskStats = []
      let totalSubmissions = 0
      let submissionFailures = 0
      const allScores = []
      let totalRates = 0

      const submissionsByTask = new Map()
      for (const t of taskList) {
        let submissions = []
        try {
          const allSubmissions = await submissionApi.getAllTaskSubmissions(t.id)
          submissions = latestSubmissionPerStudent(allSubmissions)
          if (studentListAvailable) {
            submissions = submissions.filter((submission) =>
              rosterIds.has(submissionStudentKey(submission)),
            )
          }
        } catch {
          submissionFailures++
        }
        submissionsByTask.set(String(t.id), submissions)

        const submittedCount = submissions.length
        const gradeSubs = submissions.filter((s) => numericScore(s.score) != null)
        const gradedCount = gradeSubs.length
        const scores = gradeSubs.map((s) => numericScore(s.score))
        const avgScore =
          scores.length > 0
            ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10
            : null
        allScores.push(...scores)

        const submissionRate =
          totalStudents == null
            ? null
            : totalStudents > 0
              ? Math.round((submittedCount / totalStudents) * 100)
              : 0
        if (submissionRate != null) totalRates += submissionRate
        totalSubmissions += submittedCount

        taskStats.push({
          id: t.id,
          title: t.title || '未命名作业',
          status: t.status || 'DRAFT',
          dueDate: t.dueDate,
          totalStudents,
          submittedCount,
          unsubmittedCount:
            totalStudents == null ? null : Math.max(0, totalStudents - submittedCount),
          submissionRate,
          gradedCount,
          avgScore,
        })
      }

      assignmentStats.value = taskStats
      if (submissionFailures > 0) {
        notices.push('部分任务提交记录暂不可用，相关统计可能不完整。')
      }
      loadNotice.value = notices.join(' ')

      // 概览统计
      const knownRates = taskStats.filter((task) => task.submissionRate != null)
      const avgSubmissionRate =
        knownRates.length > 0 ? Math.round(totalRates / knownRates.length) : null
      const totalAvgScore =
        allScores.length > 0
          ? Math.round((allScores.reduce((a, b) => a + b, 0) / allScores.length) * 10) / 10
          : null

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
        const key = s.studentId
        const user = usersById.get(String(key))
        studentMap.set(String(key), {
          studentId: key,
          studentName: user?.fullName || user?.username || `学生 #${key}`,
          submittedCount: 0,
          totalAssignments: taskList.length,
          scores: [],
        })
      })

      for (const t of taskList) {
        const submissions = submissionsByTask.get(String(t.id)) || []
        submissions.forEach((sub) => {
          const key = submissionStudentKey(sub)
          if (!key) return
          const entry = studentMap.get(key)
          if (entry) {
            entry.submittedCount++
            const score = numericScore(sub.score)
            if (score != null) entry.scores.push(score)
          }
        })
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
      loadNotice.value = '课程完成度数据加载失败，请刷新页面重试。'
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
    loadNotice,
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
