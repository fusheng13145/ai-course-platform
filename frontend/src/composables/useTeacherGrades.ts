import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import analyticsApi from '@/api/analytics'
import gradeApi from '@/api/grade'
import courseApi from '@/api/course'
import taskApi from '@/api/task'
import * as XLSX from 'xlsx'
import { getChartTheme, monoScore } from '@/utils/chartTheme'
import { pickList, unwrap } from '@/utils/response'
import logger from '@/utils/logger'

/** 取记录的数值分数；null / undefined / 非数值 / NaN 一律视为「未批阅」→ null */
function toScore(value) {
  return typeof value === 'number' && !Number.isNaN(value) ? value : null
}

function studentKeyOf(record) {
  const id = record?.studentId ?? record?.userId
  return id === undefined || id === null || id === '' ? null : String(id)
}

function meanScore(scores) {
  const valid = scores.filter((score) => typeof score === 'number' && !Number.isNaN(score))
  if (valid.length === 0) return null
  return Math.round((valid.reduce((sum, score) => sum + score, 0) / valid.length) * 10) / 10
}

/** 兼容旧数据口径的纯函数回归点；教师成绩页改为使用课程成绩方案报表，不再用此结果代替正式总评。 */
export function mergeGradeRecords(sessionData = [], submitData = []) {
  const map = new Map()
  const standalone = []
  const pickName = (record) => record.studentName || record.userName || '未知'
  const pickNo = (record) => record.studentNo || record.userNo || '-'

  function touch(row, record) {
    if (row.studentName === '未知') row.studentName = pickName(record)
    if (row.studentNo === '-') row.studentNo = pickNo(record)
    if (!row.courseName) row.courseName = record.courseName || ''
    if (!row.submitTime) row.submitTime = record.submitTime || record.completedAt || ''
  }

  function newRow(key, record) {
    return {
      id: key,
      studentNo: pickNo(record),
      studentName: pickName(record),
      examScores: [],
      homeworkScores: [],
      examScore: null,
      homeworkScore: null,
      totalScore: null,
      gradeLevel: '',
      needsGrading: false,
      submitTime: record.submitTime || record.completedAt || record.submittedAt || '',
      details: [],
      courseName: record.courseName || '',
    }
  }

  function finalize(row) {
    row.examScore = meanScore(row.examScores)
    row.homeworkScore = meanScore(row.homeworkScores)
    row.totalScore = meanScore([...row.examScores, ...row.homeworkScores])
    row.gradeLevel = gradeLevelOf(row.totalScore)
    return row
  }

  for (const session of Array.isArray(sessionData) ? sessionData : []) {
    const key = studentKeyOf(session)
    const score = toScore(session.score)
    if (!key) {
      const row = finalize(newRow(null, session))
      row.examScores = score === null ? [] : [score]
      row.examScore = score
      row.totalScore = score
      row.gradeLevel = gradeLevelOf(score)
      row.needsGrading = !session.isGraded && session.status !== 'graded'
      standalone.push(row)
      continue
    }
    if (!map.has(key)) map.set(key, newRow(key, session))
    const row = map.get(key)
    if (score !== null) row.examScores.push(score)
    row.needsGrading = row.needsGrading || (!session.isGraded && session.status !== 'graded')
    touch(row, session)
  }

  for (const submission of Array.isArray(submitData) ? submitData : []) {
    const key = studentKeyOf(submission)
    const score = toScore(submission.score)
    const needsGrading = submission.status !== 'graded' && submission.status !== 'reviewed'
    if (!key) {
      const row = finalize(newRow(null, submission))
      row.homeworkScores = score === null ? [] : [score]
      row.homeworkScore = score
      row.totalScore = score
      row.gradeLevel = gradeLevelOf(score)
      row.needsGrading = needsGrading
      standalone.push(row)
      continue
    }
    if (!map.has(key)) map.set(key, newRow(key, submission))
    const row = map.get(key)
    if (score !== null) row.homeworkScores.push(score)
    row.needsGrading = row.needsGrading || needsGrading
    touch(row, submission)
  }

  return [...Array.from(map.values()).map(finalize), ...standalone]
}

function gradeLevelOf(score) {
  if (score == null) return ''
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

function toNullableNumber(value: number | string | null | undefined): number | null {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

interface TeacherCourse {
  id: number | string
  name?: string | null
}

interface CourseProgressRow {
  studentId?: number | string | null
  studentNo?: string | null
  studentName?: string | null
  avgAssignmentScore?: number | string | null
  avgExamScore?: number | string | null
}

interface GradeComponent {
  key?: string | null
  name?: string | null
  weight?: number | string | null
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

interface TeacherGradeRow {
  id: string
  studentId: number | string
  courseId: number | string
  studentNo: string
  studentName: string
  courseName: string
  homeworkScore: number | null
  examScore: number | null
  totalScore: number | null
  gradeLevel: string
  needsGrading: false
  submitTime: string
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

interface PendingTask {
  id: number | string
  courseId: number | string | null
  taskName: string
  pendingCount: number | null
}

interface RequestDescriptor {
  courseId: string
  courseName: string
  type: 'progress' | 'report'
  request: Promise<unknown>
}

function emptyOverview() {
  return {
    avgScore: null as number | null,
    maxScore: null as number | null,
    minScore: null as number | null,
    pendingCount: null as number | null,
  }
}

/** 教师成绩页：使用课程学情列表和正式加权总评，不把当前教师自身记录当成全班成绩。 */
export function useTeacherGrades() {
  const router = useRouter()
  let loadSequence = 0
  let courseOptionsSequence = 0
  let courseOptionsUnavailable = false

  const loading = ref(false)
  const searchText = ref('')
  const selectedCourseId = ref<number | string | null>(null)
  const courseList = ref<TeacherCourse[]>([])
  const selectedCourseName = ref('')
  const overview = ref(emptyOverview())
  const gradeList = ref<TeacherGradeRow[]>([])
  const pendingTasks = ref<PendingTask[]>([])
  const pendingUnavailable = ref(false)
  const loadNotice = ref('')
  const detailDialogVisible = ref(false)
  const currentStudent = ref<TeacherGradeRow | null>(null)

  const filteredGradeList = computed(() => {
    const keyword = searchText.value.trim().toLowerCase()
    if (!keyword) return gradeList.value
    return gradeList.value.filter(
      (row) =>
        row.studentName.toLowerCase().includes(keyword) ||
        row.studentNo.toLowerCase().includes(keyword) ||
        row.courseName.toLowerCase().includes(keyword),
    )
  })

  const distributionOption =
    ref<ReturnType<typeof buildDistributionOption>>(buildDistributionOption())
  const hasDistribution = computed(() => distributionOption.value.series[0].data.some((n) => n > 0))

  onMounted(async () => {
    loading.value = true
    await loadCourseOptions()
    await loadGradeData()
  })

  async function loadCourseOptions() {
    const sequence = ++courseOptionsSequence
    try {
      const courses = await courseApi.getAllMyCourses()
      if (sequence !== courseOptionsSequence) return
      courseList.value = courses as TeacherCourse[]
      courseOptionsUnavailable = false
    } catch (error) {
      if (sequence !== courseOptionsSequence) return
      logger.error('加载教师课程失败:', error)
      courseList.value = []
      courseOptionsUnavailable = true
    }
  }

  async function loadGradeData() {
    const sequence = ++loadSequence
    loading.value = true
    loadNotice.value = ''
    gradeList.value = []
    pendingTasks.value = []
    pendingUnavailable.value = false
    overview.value = emptyOverview()
    currentStudent.value = null
    detailDialogVisible.value = false
    updateDistribution([])

    const selectedCourse = selectedCourseId.value
      ? courseList.value.find((course) => String(course.id) === String(selectedCourseId.value))
      : null
    selectedCourseName.value = selectedCourse?.name || ''
    const courses = selectedCourse
      ? [selectedCourse]
      : selectedCourseId.value
        ? []
        : courseList.value

    if (courseOptionsUnavailable) {
      loadNotice.value = '课程列表暂不可用，无法加载课程成绩。'
      pendingUnavailable.value = true
      loading.value = false
      return
    }
    if (selectedCourseId.value && !selectedCourse) {
      loadNotice.value = '所选课程不存在或当前账号无权访问。'
      loading.value = false
      return
    }
    if (courses.length === 0 && courseList.value.length === 0) {
      loadNotice.value = '暂无教师课程。'
    }

    const descriptors: RequestDescriptor[] = courses.flatMap((course) => {
      const courseId = String(course.id)
      const courseName = course.name || `课程 ${courseId}`
      return [
        {
          courseId,
          courseName,
          type: 'progress' as const,
          request: analyticsApi.getStudentCourseProgress(course.id),
        },
        {
          courseId,
          courseName,
          type: 'report' as const,
          request: gradeApi.getCourseReport(course.id),
        },
      ]
    })

    try {
      const [dataResults, pendingResults] = await Promise.all([
        Promise.allSettled(descriptors.map((descriptor) => descriptor.request)),
        Promise.allSettled([taskApi.getPendingGrading()]),
      ])
      if (sequence !== loadSequence) return

      const progressByCourse = new Map<string, CourseProgressRow[]>()
      const reportsByCourse = new Map<string, CourseGradeReport[]>()
      let progressFailures = 0
      let reportFailures = 0
      dataResults.forEach((result, index) => {
        const descriptor = descriptors[index]
        if (result.status === 'rejected') {
          if (descriptor.type === 'progress') progressFailures++
          else reportFailures++
          return
        }
        const rows = pickList<CourseProgressRow | CourseGradeReport>(unwrap<unknown>(result.value))
        if (descriptor.type === 'progress') {
          progressByCourse.set(descriptor.courseId, rows as CourseProgressRow[])
        } else {
          reportsByCourse.set(descriptor.courseId, rows as CourseGradeReport[])
        }
      })

      gradeList.value = courses
        .flatMap((course) => {
          const courseId = String(course.id)
          const courseName = course.name || `课程 ${courseId}`
          const progressRows = progressByCourse.get(courseId) ?? []
          const reportRows = reportsByCourse.get(courseId) ?? []
          const progressByStudent = new Map<string, CourseProgressRow>()
          const reportByStudent = new Map<string, CourseGradeReport>()
          progressRows.forEach((row) => {
            if (row.studentId != null) progressByStudent.set(String(row.studentId), row)
          })
          reportRows.forEach((row) => {
            if (row.studentId != null) reportByStudent.set(String(row.studentId), row)
          })

          const studentIds = new Set([...progressByStudent.keys(), ...reportByStudent.keys()])
          return Array.from(studentIds, (studentId): TeacherGradeRow => {
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
              id: `${courseId}:${studentId}`,
              studentId: report?.studentId ?? progress?.studentId ?? studentId,
              courseId: course.id,
              studentNo: progress?.studentNo || '-',
              studentName: report?.studentName || progress?.studentName || `学生 #${studentId}`,
              courseName,
              homeworkScore: toNullableNumber(progress?.avgAssignmentScore),
              examScore: toNullableNumber(progress?.avgExamScore),
              totalScore,
              gradeLevel: gradeLevelOf(totalScore),
              needsGrading: false,
              submitTime: '',
              details,
              warnings: Array.isArray(report?.warnings) ? report.warnings : [],
            }
          })
        })
        .sort((a, b) => (b.totalScore ?? -1) - (a.totalScore ?? -1))

      const pendingResult = pendingResults[0]
      if (pendingResult.status === 'fulfilled') {
        const pendingRows = pickList<Record<string, unknown>>(unwrap<unknown>(pendingResult.value))
        pendingTasks.value = pendingRows
          .filter(
            (task) =>
              !selectedCourseId.value ||
              String(task.courseId ?? '') === String(selectedCourseId.value),
          )
          .map((task) => ({
            id: (task.id as number | string) ?? String(task.title ?? 'pending-task'),
            courseId: (task.courseId as number | string | null) ?? null,
            taskName: String(task.title || task.name || '未命名任务'),
            pendingCount: toNullableNumber(task.pendingCount as number | string | null),
          }))
      } else {
        pendingTasks.value = []
        pendingUnavailable.value = true
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
      updateDistribution(scores)

      const notices: string[] = []
      if (progressFailures > 0) notices.push('部分课程的学生作业/考试均分暂不可用。')
      if (reportFailures > 0) notices.push('部分课程总评暂不可用，可能尚未配置成绩方案。')
      else if (courses.length > 0 && gradeList.value.every((row) => row.details.length === 0)) {
        notices.push('所选课程尚无已录入的成绩构成项。')
      }
      if (pendingUnavailable.value) notices.push('待批阅任务暂不可用。')
      loadNotice.value = notices.join('；')
    } catch (error) {
      if (sequence === loadSequence) {
        logger.error('加载教师成绩失败:', error)
        loadNotice.value = '成绩数据处理失败，请刷新重试。'
        pendingUnavailable.value = true
        overview.value.pendingCount = null
      }
    } finally {
      if (sequence === loadSequence) loading.value = false
    }
  }

  async function refresh() {
    loading.value = true
    await loadCourseOptions()
    await loadGradeData()
  }

  function handleExport() {
    const rows = filteredGradeList.value
    if (rows.length === 0) {
      ElMessage.warning('暂无可导出的成绩数据')
      return
    }
    try {
      const exportData = rows.map((row, index) => ({
        序号: index + 1,
        课程: row.courseName,
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
      XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(exportData), '学生成绩')
      XLSX.writeFile(
        workbook,
        `学生成绩_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
      )
      ElMessage.success('成绩已导出')
    } catch (error) {
      logger.error('导出成绩失败:', error)
      ElMessage.error('导出失败，请稍后重试')
    }
  }

  function viewStudentDetail(row: TeacherGradeRow) {
    currentStudent.value = { ...row }
    detailDialogVisible.value = true
  }

  async function goToGrading(task: PendingTask) {
    if (task.courseId == null || task.id == null) {
      ElMessage.warning('该待批阅任务缺少课程信息，请从课程作业页进入')
      return
    }
    try {
      await router.push({
        path: `/teacher/courses/${task.courseId}/submissions`,
        query: { taskId: String(task.id) },
      })
    } catch (error) {
      logger.error('打开待批阅任务失败:', error)
      ElMessage.error('无法打开该任务，请稍后重试')
    }
  }

  function getGradeTagType(level: string) {
    const map = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
    return map[level] || 'info'
  }

  function buildDistributionOption() {
    const theme = getChartTheme()
    const bandScores = [95, 85, 75, 65, 50]
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
      grid: { left: 50, right: 20, bottom: 30, top: 20 },
      xAxis: {
        type: 'category',
        data: ['90-100', '80-89', '70-79', '60-69', '0-59'],
        axisLine: { lineStyle: { color: theme.axis } },
        axisLabel: { color: theme.text, fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: theme.split, type: 'dashed' } },
        axisLabel: { color: theme.text, fontSize: 12 },
      },
      series: [
        {
          type: 'bar',
          data: [0, 0, 0, 0, 0],
          barWidth: '50%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: (params) => monoScore(bandScores[params.dataIndex] ?? 0),
          },
          label: { show: true, position: 'top', fontSize: 11, color: theme.text },
        },
      ],
    }
  }

  function updateDistribution(scores: number[]) {
    const buckets = [0, 0, 0, 0, 0]
    scores.forEach((score) => {
      if (score >= 90) buckets[0]++
      else if (score >= 80) buckets[1]++
      else if (score >= 70) buckets[2]++
      else if (score >= 60) buckets[3]++
      else buckets[4]++
    })
    distributionOption.value.series[0].data = buckets
  }

  return {
    loading,
    searchText,
    selectedCourseId,
    courseList,
    selectedCourseName,
    overview,
    gradeList,
    pendingTasks,
    pendingUnavailable,
    loadNotice,
    detailDialogVisible,
    currentStudent,
    filteredGradeList,
    distributionOption,
    hasDistribution,
    loadCourseOptions,
    loadGradeData,
    refresh,
    handleExport,
    viewStudentDetail,
    goToGrading,
    getGradeTagType,
  }
}
