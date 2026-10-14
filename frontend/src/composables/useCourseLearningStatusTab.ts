import logger from '@/utils/logger'
import { ref, computed, watch, onMounted } from 'vue'
import teacherApi from '@/api/teacher'
import courseApi from '@/api/course'
import userApi from '@/api/user'
import { getChartTheme } from '@/utils/chartTheme'
import { pickList, unwrap } from '@/utils/response'

/** 选课记录行（后端 Enrollment 展示字段子集；未列字段不做假设） */
type EnrollmentStudentRow = {
  isActive?: boolean
  approvalStatus?: string
  studentId?: number | string
  totalStudyTime?: number | null
  engagement?: number | null
  engagementScore?: number | null
  completionRate?: number | null
  progress?: number | string | null
  submissionCount?: number | null
  submittedAssignments?: number | null
  lastActiveTime?: string | null
  lastLoginTime?: string | null
}

export function useCourseLearningStatusTab(courseIdRef) {
  const loading = ref(false)
  const searchKeyword = ref('')

  // 双路加载各自的独立错误状态：互不复位，防止一路失败清除另一路的提示/数据。
  const classError = ref('')
  const studentsError = ref('')
  const loadError = computed(() => classError.value || studentsError.value)

  const overview = ref({
    activeStudents: 0,
    avgOnlineHours: 0,
    avgEngagement: 0,
    avgCompletion: 0,
  })

  const students = ref([])

  const activityTrendOption = ref({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['活跃学生数', '提交作业数'],
      top: 0,
      textStyle: { color: getChartTheme().subText, fontSize: 12 },
    },
    grid: { left: 45, right: 20, bottom: 25, top: 40 },
    xAxis: {
      type: 'category',
      data: [],
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
        name: '活跃学生数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: [],
        lineStyle: { width: 2.5, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6' },
        areaStyle: { color: 'rgba(59,130,246,0.08)' },
      },
      {
        name: '提交作业数',
        type: 'bar',
        barWidth: 16,
        data: [],
        itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] },
      },
    ],
  })

  const engagementDistOption = ref({
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: getChartTheme().subText, fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '78%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: getChartTheme().sliceBorder, borderWidth: 3 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
        data: [],
      },
    ],
  })

  const filteredStudents = computed(() => {
    if (!searchKeyword.value) return students.value
    const kw = searchKeyword.value.toLowerCase()
    return students.value.filter(
      (s) =>
        (s.studentName && s.studentName.toLowerCase().includes(kw)) ||
        (s.studentId && s.studentId.toLowerCase().includes(kw)),
    )
  })

  async function loadData() {
    loading.value = true
    classError.value = ''
    studentsError.value = ''
    try {
      const cid = Number(courseIdRef.value)

      const [classRes, studentsRes] = await Promise.allSettled([
        teacherApi.getClassAnalytics(cid),
        courseApi.getAllStudents(cid),
      ])

      // 班级级分析数据
      if (classRes.status === 'fulfilled') {
        const d = classRes.value?.data || classRes.value || {}
        overview.value = {
          activeStudents: d.activeStudents ?? d.activeStudentCount ?? 0,
          avgOnlineHours: d.avgOnlineHours != null ? Math.round(d.avgOnlineHours * 10) / 10 : 0,
          avgEngagement: d.avgEngagement != null ? Math.round(d.avgEngagement * 100) : 0,
          avgCompletion: d.avgCompletion != null ? Math.round(d.avgCompletion * 100) : 0,
        }

        // 趋势数据
        if (d.trendDays?.length) {
          activityTrendOption.value.xAxis.data = d.trendDays
          activityTrendOption.value.series[0].data = d.activeStudentTrend || []
          activityTrendOption.value.series[1].data = d.submissionTrend || []
        }

        // 参与度分布
        if (d.engagementBuckets?.length) {
          engagementDistOption.value.series[0].data = d.engagementBuckets.map((b) => ({
            name: b.label,
            value: b.count,
            itemStyle: { color: b.color },
          }))
        }
      } else {
        classError.value = '班级学习数据加载失败，请检查网络后重试'
      }

      // 学生数据
      if (studentsRes.status === 'fulfilled') {
        const rows = studentsRes.value as EnrollmentStudentRow[]
        const list = rows.filter(
          (enrollment) =>
            enrollment?.isActive === true && enrollment?.approvalStatus === 'APPROVED',
        )
        let teacherStudents = []
        try {
          teacherStudents = pickList(unwrap(await userApi.getTeacherStudents()))
        } catch (e) {
          logger.warn('[CourseLearningStatusTab] 获取学生姓名失败:', e.message || e)
        }
        const usersById = new Map(teacherStudents.map((student) => [String(student.id), student]))
        students.value = list.map((s) => ({
          ...s,
          studentName:
            usersById.get(String(s.studentId))?.fullName ||
            usersById.get(String(s.studentId))?.username ||
            `学生 #${s.studentId}`,
          studentNo: usersById.get(String(s.studentId))?.username || '-',
          studentId: usersById.get(String(s.studentId))?.username || String(s.studentId || '-'),
          onlineHours:
            s.totalStudyTime != null ? Math.round((s.totalStudyTime / 60) * 10) / 10 : null,
          engagement: s.engagement ?? s.engagementScore ?? null,
          // Enrollment.progress 使用 0-100 百分制，表格按 0-1 比例显示。
          completionRate: (() => {
            if (s.completionRate != null) return s.completionRate
            const progress = Number(s.progress)
            return Number.isFinite(progress) ? Math.min(100, Math.max(0, progress)) / 100 : null
          })(),
          submissionCount: s.submissionCount ?? s.submittedAssignments ?? null,
          lastActiveTime: s.lastActiveTime || s.lastLoginTime || null,
        }))

        // 参与度分布：班级级接口未提供 engagementBuckets 时，用学生真实参与度兜底聚合。
        // 缺失参与度字段的学生不参与统计，避免以伪造数据渲染业务图表。
        if (engagementDistOption.value.series[0].data.length === 0) {
          const buckets = { '高(>80%)': 0, '中(50-80%)': 0, '低(<50%)': 0 }
          let withEngagement = 0
          students.value.forEach((s) => {
            if (s.engagement == null) return
            withEngagement++
            if (s.engagement >= 80) buckets['高(>80%)']++
            else if (s.engagement >= 50) buckets['中(50-80%)']++
            else buckets['低(<50%)']++
          })
          if (withEngagement > 0) {
            engagementDistOption.value.series[0].data = [
              { name: '高(>80%)', value: buckets['高(>80%)'], itemStyle: { color: '#10b981' } },
              { name: '中(50-80%)', value: buckets['中(50-80%)'], itemStyle: { color: '#f59e0b' } },
              { name: '低(<50%)', value: buckets['低(<50%)'], itemStyle: { color: '#ef4444' } },
            ]
          }
        }
      } else {
        students.value = []
        studentsError.value = '学生名单加载失败，请检查网络后重试'
      }
    } catch (e) {
      logger.warn('[CourseLearningStatusTab] loadData 失败:', e.message || e)
    } finally {
      loading.value = false
    }
  }

  onMounted(loadData)
  watch(courseIdRef, loadData)

  return {
    loading,
    loadError,
    classError,
    studentsError,
    searchKeyword,
    overview,
    students,
    activityTrendOption,
    engagementDistOption,
    filteredStudents,
  }
}
