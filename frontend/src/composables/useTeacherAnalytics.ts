import { computed, onMounted, ref } from 'vue'
import logger from '@/utils/logger'
import analyticsApi from '@/api/analytics'
import teacherApi from '@/api/teacher'
import { pickList, unwrap } from '@/utils/response'
import { getChartTheme, getMonoSeries, monoScore } from '@/utils/chartTheme'

interface TeacherCourseSummary {
  courseId?: number | string | null
  name?: string | null
  studentCount?: number | string | null
  avgProgress?: number | string | null
  completionRate?: number | string | null
  pendingGrading?: number | string | null
}

interface CourseProgressRecord {
  studentId?: number | string | null
  studentName?: string | null
  completedTasks?: number | string | null
  avgAssignmentScore?: number | string | null
}

interface TrendPoint {
  date?: string | null
  label?: string | null
  value?: number | string | null
  count?: number | string | null
}

interface TeacherDashboard {
  courseCount?: number | string | null
  studentCount?: number | string | null
  avgScore?: number | string | null
  pendingGrading?: number | string | null
  courseCards?: TeacherCourseSummary[] | null
  taskPointOverview?: { submissionRate?: number | string | null } | null
  error?: string | null
}

interface TrendChartOption {
  xAxis: { data: string[]; [key: string]: unknown }
  series: Array<{ data: number[]; [key: string]: unknown }>
  [key: string]: unknown
}

interface DistributionChartOption {
  series: Array<{ data: number[]; [key: string]: unknown }>
  [key: string]: unknown
}

interface RadarChartOption {
  legend: { data: string[]; [key: string]: unknown }
  series: Array<{
    data: Array<{ name: string; value: number[] }>
    [key: string]: unknown
  }>
  [key: string]: unknown
}

interface ActivityChartOption {
  yAxis: { data: string[]; [key: string]: unknown }
  series: Array<{ data: number[]; [key: string]: unknown }>
  [key: string]: unknown
}

interface LegacyTeacherDashboard {
  trendData?: TrendPoint[] | null
  error?: string | null
}

function toNullableNumber(value: number | string | null | undefined): number | null {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function emptyOverview() {
  return {
    courseCount: null as number | null,
    studentCount: null as number | null,
    avgScore: null as number | null,
    submissionRate: null as number | null,
    pendingGrading: null as number | null,
  }
}

/** 教师数据分析：只展示后端真实统计，不从学生端提交接口推算或补造图表数值。 */
export function useTeacherAnalytics() {
  let requestSequence = 0
  const loading = ref(false)
  const loadNotice = ref('')
  const overview = ref(emptyOverview())

  function buildTrendOption(): TrendChartOption {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
      },
      legend: {
        data: ['作业提交量'],
        top: 0,
        textStyle: { color: t.subText, fontSize: 12 },
      },
      grid: { left: 50, right: 20, bottom: 30, top: 40 },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.subText, fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
        axisLabel: { color: t.subText, fontSize: 12 },
      },
      series: [
        {
          name: '作业提交量',
          data: [],
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2.5, color: mono[1] },
          itemStyle: { color: mono[1] },
          areaStyle: { opacity: 0.08 },
        },
      ],
    }
  }
  const trendOption = ref(buildTrendOption())

  function buildDistributionOption(): DistributionChartOption {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: '{b}: {c} 人次',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
      },
      grid: { left: 50, right: 20, bottom: 30, top: 20 },
      xAxis: {
        type: 'category',
        data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.subText, fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
        axisLabel: { color: t.subText, fontSize: 12 },
      },
      series: [
        {
          type: 'bar',
          data: [0, 0, 0, 0, 0],
          barWidth: '45%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: (params) =>
              [mono[2], mono[1], mono[3], mono[6], mono[0]][params.dataIndex] || mono[1],
          },
        },
      ],
    }
  }
  const distributionOption = ref(buildDistributionOption())

  function buildRadarOption(): RadarChartOption {
    const t = getChartTheme()
    const mono = getMonoSeries()
    const n = parseInt(mono[1].slice(1), 16)
    const soft = `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, 0.08)`
    return {
      tooltip: {
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
      },
      legend: { data: [], bottom: 0, textStyle: { color: t.subText, fontSize: 11 } },
      radar: {
        indicator: [
          { name: '平均进度', max: 100 },
          { name: '任务完成率', max: 100 },
        ],
        shape: 'circle',
        radius: '65%',
        center: ['50%', '48%'],
        axisName: { color: t.text, fontSize: 11 },
        splitArea: { areaStyle: { color: [soft, 'rgba(0, 0, 0, 0)'] } },
        axisLine: { lineStyle: { color: t.axis } },
        splitLine: { lineStyle: { color: t.axis } },
      },
      series: [{ type: 'radar', data: [] }],
    }
  }
  const radarOption = ref(buildRadarOption())

  function buildActivityRankOption(): ActivityChartOption {
    const t = getChartTheme()
    const mono = getMonoSeries()
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: '{b}: 已完成 {c} 个任务点',
        backgroundColor: t.tooltipBg,
        borderColor: t.tooltipBorder,
        textStyle: { color: t.tooltipText },
      },
      grid: { left: 80, right: 20, bottom: 20, top: 10 },
      xAxis: {
        type: 'value',
        min: 0,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
        axisLabel: { color: t.subText, fontSize: 11 },
      },
      yAxis: {
        type: 'category',
        data: [],
        inverse: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: t.text, fontSize: 12, width: 70, overflow: 'truncate' },
      },
      series: [
        {
          name: '已完成任务点',
          type: 'bar',
          data: [],
          barWidth: 16,
          itemStyle: {
            borderRadius: [0, 8, 8, 0],
            color: (params) => mono[params.dataIndex % mono.length],
          },
          label: { show: true, position: 'right', fontSize: 11, color: t.text },
        },
      ],
    }
  }
  const activityRankOption = ref(buildActivityRankOption())
  const completionTable = ref<TeacherCourseSummary[]>([])

  const hasTrendData = computed(() => trendOption.value.series[0].data.length > 0)
  const hasDistributionData = computed(() =>
    distributionOption.value.series[0].data.some((value) => value > 0),
  )
  const hasCourseComparison = computed(() => radarOption.value.series[0].data.length > 0)
  const hasActivityRank = computed(() => activityRankOption.value.yAxis.data.length > 0)

  function clearCharts() {
    trendOption.value.xAxis.data = []
    trendOption.value.series[0].data = []
    distributionOption.value.series[0].data = [0, 0, 0, 0, 0]
    radarOption.value.legend.data = []
    radarOption.value.series[0].data = []
    activityRankOption.value.yAxis.data = []
    activityRankOption.value.series[0].data = []
    completionTable.value = []
  }

  async function loadAnalyticsData() {
    const requestId = ++requestSequence
    loading.value = true
    loadNotice.value = ''
    overview.value = emptyOverview()
    clearCharts()

    try {
      const [legacyResult, dashboardResult] = await Promise.allSettled([
        analyticsApi.getDashboardStats(),
        teacherApi.getTeacherDashboardV2(),
      ])
      if (requestId !== requestSequence) return

      const legacy =
        legacyResult.status === 'fulfilled'
          ? (unwrap<LegacyTeacherDashboard>(legacyResult.value) ?? {})
          : null
      const dashboard =
        dashboardResult.status === 'fulfilled'
          ? (unwrap<TeacherDashboard>(dashboardResult.value) ?? {})
          : null
      const notices: string[] = []

      if (legacyResult.status === 'rejected') notices.push('提交趋势暂不可用。')
      else if (legacy?.error) notices.push('提交趋势数据加载不完整。')
      if (dashboardResult.status === 'rejected') notices.push('课程汇总暂不可用。')
      else if (dashboard?.error) notices.push('课程汇总数据加载不完整。')

      const courses = dashboard ? pickList<TeacherCourseSummary>(dashboard.courseCards) : []
      overview.value = {
        courseCount: toNullableNumber(dashboard?.courseCount),
        studentCount: toNullableNumber(dashboard?.studentCount),
        avgScore: toNullableNumber(dashboard?.avgScore),
        submissionRate: toNullableNumber(dashboard?.taskPointOverview?.submissionRate),
        pendingGrading: toNullableNumber(dashboard?.pendingGrading),
      }

      const trendRows = legacy ? pickList<TrendPoint>(legacy.trendData) : []
      trendOption.value.xAxis.data = trendRows.map((point) => point.label || point.date || '')
      trendOption.value.series[0].data = trendRows.map(
        (point) => toNullableNumber(point.value ?? point.count) ?? 0,
      )

      const validCourses = courses.filter((course) => course.courseId != null)
      const progressResults = await Promise.allSettled(
        validCourses.map((course) => analyticsApi.getStudentCourseProgress(course.courseId!)),
      )
      if (requestId !== requestSequence) return

      const studentScores: number[] = []
      const studentActivity = new Map<string, { name: string; completedTasks: number }>()
      let failedCourseCount = 0
      progressResults.forEach((result) => {
        if (result.status === 'rejected') {
          failedCourseCount++
          return
        }
        const rows = pickList<CourseProgressRecord>(unwrap<unknown>(result.value))
        rows.forEach((row) => {
          const score = toNullableNumber(row.avgAssignmentScore)
          if (score !== null) studentScores.push(score)
          if (row.studentId == null) return

          const studentKey = String(row.studentId)
          const activity = studentActivity.get(studentKey) ?? {
            name: row.studentName || `学生 #${studentKey}`,
            completedTasks: 0,
          }
          activity.completedTasks += toNullableNumber(row.completedTasks) ?? 0
          studentActivity.set(studentKey, activity)
        })
      })
      if (failedCourseCount > 0) notices.push(`${failedCourseCount} 门课程的学情数据暂不可用。`)

      const scoreBuckets = [0, 0, 0, 0, 0]
      studentScores.forEach((score) => {
        if (score >= 90) scoreBuckets[0]++
        else if (score >= 80) scoreBuckets[1]++
        else if (score >= 70) scoreBuckets[2]++
        else if (score >= 60) scoreBuckets[3]++
        else scoreBuckets[4]++
      })
      distributionOption.value.series[0].data = scoreBuckets

      const comparisonRows = courses
        .map((course) => ({
          name: course.name || `课程 ${course.courseId ?? ''}`,
          avgProgress: toNullableNumber(course.avgProgress),
          completionRate: toNullableNumber(course.completionRate),
        }))
        .filter((course) => course.avgProgress !== null || course.completionRate !== null)
        .slice(0, 8)
      radarOption.value.legend.data = comparisonRows.map((course) => course.name)
      radarOption.value.series[0].data = comparisonRows.map((course) => ({
        name: course.name,
        value: [course.avgProgress ?? 0, course.completionRate ?? 0],
      }))

      const rankedStudents = Array.from(studentActivity.values())
        .sort((a, b) => b.completedTasks - a.completedTasks)
        .slice(0, 10)
        .reverse()
      activityRankOption.value.yAxis.data = rankedStudents.map((student) => student.name)
      activityRankOption.value.series[0].data = rankedStudents.map(
        (student) => student.completedTasks,
      )

      completionTable.value = courses.map((course) => ({
        ...course,
        name: course.name || `课程 ${course.courseId ?? ''}`,
      }))

      loadNotice.value = notices.join('')
    } catch (error) {
      logger.error('加载教师分析数据失败:', error)
      loadNotice.value = '分析数据处理失败，请刷新重试。'
    } finally {
      if (requestId === requestSequence) loading.value = false
    }
  }

  onMounted(loadAnalyticsData)

  return {
    loading,
    loadNotice,
    overview,
    trendOption,
    distributionOption,
    radarOption,
    activityRankOption,
    completionTable,
    hasTrendData,
    hasDistributionData,
    hasCourseComparison,
    hasActivityRank,
    monoScore,
    loadAnalyticsData,
  }
}
