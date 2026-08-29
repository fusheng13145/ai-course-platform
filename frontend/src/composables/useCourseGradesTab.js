import logger from '@/utils/logger'
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import submissionApi from '@/api/submission'
import examApi from '@/api/exam'
import taskApi from '@/api/task'
import { getChartTheme, getMonoSeries, monoScore } from '@/utils/chartTheme'

/**
 * 课程成绩 Tab 数据逻辑（拆分自 components/CourseGradesTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseGradesTab(courseIdRef) {
  const loading = ref(false)
  const searchText = ref('')
  const gradeList = ref([])
  const pendingTasks = ref([])
  const overview = ref({ avgScore: null, maxScore: null, minScore: null, pendingCount: 0 })

  // 弹窗
  const detailVisible = ref(false)
  const currentStudent = ref(null)
  const gradeVisible = ref(false)
  const gradingStudent = ref(null)
  const gradingLoading = ref(false)
  const gradeForm = ref({ score: 0, comment: '', fullScore: 100 })

  const filteredGrades = computed(() => {
    if (!searchText.value) return gradeList.value
    const kw = searchText.value.toLowerCase()
    return gradeList.value.filter(
      (s) =>
        (s.studentName || '').toLowerCase().includes(kw) ||
        (s.studentNo || '').toLowerCase().includes(kw),
    )
  })

  const distOption = ref({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: {
      type: 'category',
      data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
      axisLine: { lineStyle: { color: getChartTheme().axis } },
      axisLabel: { color: getChartTheme().subText, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: getChartTheme().axis, type: 'dashed' } },
      axisLabel: { color: getChartTheme().subText, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: [0, 0, 0, 0, 0],
        barWidth: '50%',
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: (p) => getMonoSeries()[p.dataIndex] || getMonoSeries()[0],
        },
        label: { show: true, position: 'top', fontSize: 11, color: getChartTheme().subText },
      },
    ],
  })

  async function loadData() {
    loading.value = true
    try {
      const cid = Number(courseIdRef.value)
      const [statsRes, sessionRes, submitRes, pendingRes] = await Promise.allSettled([
        import('@/api/analytics').then((m) => m.default.getDashboardStats({ courseId: cid })),
        examApi.getMyExamSessions(),
        submissionApi.getMySubmissions({ role: 'teacher', courseId: cid }),
        taskApi.getPendingGrading(undefined),
      ])

      if (statsRes.status === 'fulfilled') {
        const d = statsRes.value?.data?.data || statsRes.value?.data || {}
        overview.value = {
          avgScore: d.avgScore != null ? Math.round(d.avgScore * 10) / 10 : null,
          maxScore: d.maxScore ?? null,
          minScore: d.minScore ?? null,
          pendingCount: d.pendingCount ?? 0,
        }
      }

      const sessionData =
        sessionRes.status === 'fulfilled'
          ? sessionRes.value.data?.list || sessionRes.value.data || []
          : []
      const submitData =
        submitRes.status === 'fulfilled'
          ? submitRes.value.data?.list || submitRes.value.data || []
          : []

      const sm = new Map()
      for (const s of sessionData) {
        const k = s.studentId || s.userId
        if (!sm.has(k))
          sm.set(k, {
            id: k,
            studentNo: s.studentNo || s.userNo || '-',
            studentName: s.studentName || s.userName || '未知',
            homeworkScore: null,
            examScore: s.score ?? null,
            totalScore: s.score ?? null,
            gradeLevel: getGL(s.score),
            needsGrading: !s.isGraded && s.status !== 'graded',
            submitTime: s.submitTime || '',
            details: [],
          })
        else {
          const e = sm.get(k)
          e.examScore = s.score ?? e.examScore
          e.totalScore = Math.round(((e.totalScore + (s.score || 0)) / 2) * 10) / 10
          e.gradeLevel = getGL(e.totalScore)
        }
      }
      for (const s of submitData) {
        const k = s.studentId || s.userId
        if (!sm.has(k))
          sm.set(k, {
            id: k,
            studentNo: s.studentNo || s.userNo || '-',
            studentName: s.studentName || s.userName || '未知',
            homeworkScore: s.score ?? null,
            examScore: null,
            totalScore: s.score ?? null,
            gradeLevel: getGL(s.score),
            needsGrading: s.status !== 'graded' && s.status !== 'reviewed',
            submitTime: s.submittedAt || s.createdAt || '',
            details: [],
          })
        else {
          const e = sm.get(k)
          e.homeworkScore = s.score ?? e.homeworkScore
          e.totalScore =
            e.examScore != null && s.score != null
              ? Math.round((e.examScore * 0.5 + s.score * 0.5) * 10) / 10
              : (e.examScore ?? s.score ?? null)
          e.gradeLevel = getGL(e.totalScore)
        }
      }
      gradeList.value = Array.from(sm.values())

      if (pendingRes.status === 'fulfilled') {
        const pd = pendingRes.value.data?.list || pendingRes.value.data || pendingRes.value || []
        pendingTasks.value = (Array.isArray(pd) ? pd : []).map((t) => ({
          id: t.id,
          taskName: t.title || t.name || t.taskName || '未命名',
          pendingCount: t.pendingCount ?? t.count ?? 1,
        }))
        if (pendingTasks.value.length) overview.value.pendingCount = pendingTasks.value.length
      }
      if (!pendingTasks.value.length) {
        pendingTasks.value = (
          submitData.filter((s) => s.status !== 'graded' && s.status !== 'reviewed') || []
        ).map((s) => ({ id: s.id, taskName: s.taskName || s.title || '未命名', pendingCount: 1 }))
      }

      updateDist(gradeList.value.map((g) => g.totalScore))
    } catch (e) {
      logger.warn('[CourseGradesTab]', e.message || e)
    } finally {
      loading.value = false
    }
  }

  function updateDist(scores) {
    const b = [0, 0, 0, 0, 0]
    for (const s of scores) {
      if (s == null) continue
      if (s >= 90) b[0]++
      else if (s >= 80) b[1]++
      else if (s >= 70) b[2]++
      else if (s >= 60) b[3]++
      else b[4]++
    }
    distOption.value.series[0].data = b
  }

  function getGL(score) {
    if (score == null) return ''
    if (score >= 90) return 'A'
    if (score >= 80) return 'B'
    if (score >= 70) return 'C'
    if (score >= 60) return 'D'
    return 'F'
  }
  function getGradeTagType(l) {
    const m = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
    return m[l] || 'info'
  }
  function getScoreColor(score) {
    return monoScore(score)
  }

  function viewDetail(row) {
    const d = []
    if (row.homeworkScore != null)
      d.push({
        taskName: '平时作业',
        type: 'homework',
        score: row.homeworkScore,
        fullScore: 100,
        status: row.needsGrading ? 'pending' : 'graded',
      })
    if (row.examScore != null)
      d.push({
        taskName: '期末考试',
        type: 'exam',
        score: row.examScore,
        fullScore: 100,
        status: 'graded',
      })
    currentStudent.value = { ...row, details: d }
    detailVisible.value = true
  }

  function openGradeDialog(row) {
    gradingStudent.value = row
    gradeForm.value = { score: 0, comment: '', fullScore: 100 }
    gradeVisible.value = true
  }

  async function submitGrade() {
    if (!gradingStudent.value?.id) return
    gradingLoading.value = true
    try {
      await submissionApi.gradeSubmission(gradingStudent.value.id, {
        score: gradeForm.value.score,
        comment: gradeForm.value.comment,
      })
      ElMessage.success('评分成功')
      gradeVisible.value = false
      await loadData()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '评分失败')
    } finally {
      gradingLoading.value = false
    }
  }

  function handleExport() {
    ElMessage.info('正在导出成绩Excel...')
    logger.log('export:', filteredGrades.value)
  }

  onMounted(loadData)
  watch(() => courseIdRef.value, loadData)

  return {
    loading,
    searchText,
    gradeList,
    pendingTasks,
    overview,
    detailVisible,
    currentStudent,
    gradeVisible,
    gradingStudent,
    gradingLoading,
    gradeForm,
    filteredGrades,
    distOption,
    loadData,
    getGradeTagType,
    getScoreColor,
    viewDetail,
    openGradeDialog,
    submitGrade,
    handleExport,
  }
}
