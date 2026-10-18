import logger from '@/utils/logger'
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import analyticsApi from '@/api/analytics'
import gradeApi from '@/api/grade'
import taskApi from '@/api/task'
import { pickList, unwrap } from '@/utils/response'
import { getChartTheme, getMonoSeries, monoScore } from '@/utils/chartTheme'
import * as XLSX from 'xlsx'

interface CourseProgressRow {
  studentId?: number | string | null
  studentNo?: string | null
  studentName?: string | null
  courseName?: string | null
  avgAssignmentScore?: number | string | null
  avgExamScore?: number | string | null
}

interface GradeComponent {
  key?: string | null
  name?: string | null
  weight?: number | null
  score?: number | string | null
  contribution?: number | string | null
  graded?: boolean
}

interface CourseGradeReport {
  studentId?: number | string | null
  studentName?: string | null
  finalScore?: number | string | null
  components?: GradeComponent[] | null
  warnings?: string[] | null
}

interface GradeRow {
  id: number | string
  studentId: number | string | null
  studentNo: string
  studentName: string
  courseName: string
  homeworkScore: number | null
  examScore: number | null
  totalScore: number | null
  gradeLevel: string
  needsGrading: false
  details: Array<{
    taskName: string
    type: 'component'
    score: number | null
    weight: number | null
    contribution: number | null
    graded: boolean
    status: 'graded' | 'pending'
  }>
  warnings: string[]
}

interface PendingTaskRow {
  id: number | string
  taskName: string
  pendingCount: number | null
}

function toNullableNumber(value: number | string | null | undefined): number | null {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

/**
 * 课程成绩 Tab 数据逻辑（拆分自 components/CourseGradesTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseGradesTab(courseIdRef) {
  let loadSequence = 0
  let lastCourseId: number | null = null
  const loading = ref(false)
  const searchText = ref('')
  const gradeList = ref<GradeRow[]>([])
  const pendingTasks = ref<PendingTaskRow[]>([])
  const pendingUnavailable = ref(false)
  const loadNotice = ref('')
  const overview = ref<{
    avgScore: number | null
    maxScore: number | null
    minScore: number | null
    pendingCount: number | null
  }>({ avgScore: null, maxScore: null, minScore: null, pendingCount: null })

  // 弹窗
  const detailVisible = ref(false)
  const currentStudent = ref<GradeRow | null>(null)

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
    const cid = Number(courseIdRef.value)
    const sequence = ++loadSequence
    if (!Number.isInteger(cid) || cid <= 0) {
      gradeList.value = []
      pendingTasks.value = []
      overview.value = { avgScore: null, maxScore: null, minScore: null, pendingCount: null }
      updateDist([])
      currentStudent.value = null
      detailVisible.value = false
      loadNotice.value = '课程信息无效，无法加载成绩。'
      pendingUnavailable.value = true
      lastCourseId = null
      loading.value = false
      return
    }

    if (lastCourseId !== cid) {
      gradeList.value = []
      pendingTasks.value = []
      overview.value = { avgScore: null, maxScore: null, minScore: null, pendingCount: null }
      updateDist([])
      currentStudent.value = null
      detailVisible.value = false
    }
    lastCourseId = cid
    loadNotice.value = ''
    pendingUnavailable.value = false
    loading.value = true
    try {
      const [progressRes, reportRes, pendingRes] = await Promise.allSettled([
        analyticsApi.getStudentCourseProgress(cid),
        gradeApi.getCourseReport(cid),
        taskApi.getPendingGrading(),
      ])
      if (sequence !== loadSequence || Number(courseIdRef.value) !== cid) return

      const progressRows =
        progressRes.status === 'fulfilled'
          ? pickList<CourseProgressRow>(unwrap<unknown>(progressRes.value))
          : null
      const reportRows =
        reportRes.status === 'fulfilled'
          ? pickList<CourseGradeReport>(unwrap<unknown>(reportRes.value))
          : null

      if (progressRows === null && reportRows === null) {
        gradeList.value = []
      } else {
        const progressByStudent = new Map<string, CourseProgressRow>()
        const reportByStudent = new Map<string, CourseGradeReport>()
        progressRows?.forEach((row) => {
          if (row.studentId != null) progressByStudent.set(String(row.studentId), row)
        })
        reportRows?.forEach((row) => {
          if (row.studentId != null) reportByStudent.set(String(row.studentId), row)
        })

        const studentIds = new Set([...progressByStudent.keys(), ...reportByStudent.keys()])
        gradeList.value = Array.from(studentIds, (studentId) => {
          const progress = progressByStudent.get(studentId)
          const report = reportByStudent.get(studentId)
          const totalScore = toNullableNumber(report?.finalScore)
          const details = Array.isArray(report?.components)
            ? report.components.map((component) => {
                const graded = Boolean(component.graded)
                return {
                  taskName: component.name || component.key || '成绩构成项',
                  type: 'component' as const,
                  score: toNullableNumber(component.score),
                  weight: toNullableNumber(component.weight),
                  contribution: toNullableNumber(component.contribution),
                  graded,
                  status: graded ? ('graded' as const) : ('pending' as const),
                }
              })
            : []

          return {
            id: studentId,
            studentId: report?.studentId ?? progress?.studentId ?? null,
            studentNo: progress?.studentNo || '-',
            studentName: report?.studentName || progress?.studentName || `学生 #${studentId}`,
            courseName: progress?.courseName || '',
            homeworkScore: toNullableNumber(progress?.avgAssignmentScore),
            examScore: toNullableNumber(progress?.avgExamScore),
            totalScore,
            gradeLevel: getGL(totalScore),
            needsGrading: false as const,
            details,
            warnings: Array.isArray(report?.warnings) ? report.warnings : [],
          }
        }).sort((a, b) => (b.totalScore ?? -1) - (a.totalScore ?? -1))
      }

      const pendingRows =
        pendingRes.status === 'fulfilled'
          ? pickList<Record<string, unknown>>(unwrap<unknown>(pendingRes.value))
          : null
      if (pendingRows !== null) {
        pendingTasks.value = pendingRows
          .filter((task) => String(task.courseId ?? '') === String(cid))
          .map((task) => ({
            id: (task.id as number | string) ?? String(task.title ?? 'pending-task'),
            taskName: String(task.title || task.name || '未命名任务'),
            pendingCount: toNullableNumber(task.pendingCount as number | string | null),
          }))
      } else {
        pendingTasks.value = []
      }
      pendingUnavailable.value = pendingRes.status === 'rejected'

      if (currentStudent.value) {
        const refreshedStudent = gradeList.value.find((row) => row.id === currentStudent.value?.id)
        if (refreshedStudent) currentStudent.value = { ...refreshedStudent }
        else {
          currentStudent.value = null
          detailVisible.value = false
        }
      }

      const scores = gradeList.value
        .map((row) => row.totalScore)
        .filter((score): score is number => score !== null)
      overview.value = {
        avgScore:
          scores.length > 0
            ? Math.round((scores.reduce((sum, score) => sum + score, 0) / scores.length) * 10) / 10
            : null,
        maxScore: scores.length > 0 ? Math.max(...scores) : null,
        minScore: scores.length > 0 ? Math.min(...scores) : null,
        pendingCount: pendingUnavailable.value ? null : pendingTasks.value.length,
      }

      updateDist(scores)

      const notices: string[] = []
      if (progressRes.status === 'rejected') notices.push('学生作业/考试均分暂不可用。')
      if (reportRes.status === 'rejected') {
        notices.push('课程总评暂不可用，可能尚未配置成绩方案。')
      } else if (reportRows?.length === 0) {
        notices.push('尚无已录入的课程成绩构成项。')
      }
      if (pendingRes.status === 'rejected') notices.push('待批阅任务暂不可用。')
      loadNotice.value = notices.join('')
    } catch (e) {
      if (sequence === loadSequence) {
        logger.warn('[CourseGradesTab]', e?.message || e)
        gradeList.value = []
        pendingTasks.value = []
        currentStudent.value = null
        detailVisible.value = false
        pendingUnavailable.value = true
        overview.value = { avgScore: null, maxScore: null, minScore: null, pendingCount: null }
        updateDist([])
        loadNotice.value = '成绩数据处理失败，请刷新重试。'
      }
    } finally {
      if (sequence === loadSequence) loading.value = false
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
    currentStudent.value = { ...row }
    detailVisible.value = true
  }

  function handleExport() {
    if (filteredGrades.value.length === 0) {
      ElMessage.warning('暂无可导出的成绩数据')
      return
    }
    try {
      const rows = filteredGrades.value.map((row, index) => ({
        序号: index + 1,
        学号: row.studentNo,
        姓名: row.studentName,
        作业均分: row.homeworkScore ?? '--',
        考试均分: row.examScore ?? '--',
        总评: row.totalScore ?? '--',
        等级: row.gradeLevel || '--',
        成绩构成: row.details
          .map((detail) => `${detail.taskName}：${detail.score ?? '未评分'}`)
          .join('；'),
      }))
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(rows), '课程成绩')
      XLSX.writeFile(
        workbook,
        `课程成绩_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
      )
      ElMessage.success('成绩已导出')
    } catch (e) {
      logger.error('导出课程成绩失败:', e)
      ElMessage.error('导出失败，请稍后重试')
    }
  }

  onMounted(loadData)
  watch(() => courseIdRef.value, loadData)

  return {
    loading,
    searchText,
    gradeList,
    pendingTasks,
    pendingUnavailable,
    loadNotice,
    overview,
    detailVisible,
    currentStudent,
    filteredGrades,
    distOption,
    loadData,
    getGradeTagType,
    getScoreColor,
    viewDetail,
    handleExport,
  }
}
