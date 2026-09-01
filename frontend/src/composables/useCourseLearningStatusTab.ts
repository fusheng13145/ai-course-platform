import logger from '@/utils/logger'
import { ref, computed, watch, onMounted } from 'vue'
import teacherApi from '@/api/teacher'
import courseApi from '@/api/course'
import { getChartTheme } from '@/utils/chartTheme'

export function useCourseLearningStatusTab(courseIdRef) {
  const loading = ref(false)
  const searchKeyword = ref('')

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
    try {
      const cid = Number(courseIdRef.value)

      const [classRes, studentsRes] = await Promise.allSettled([
        teacherApi.getClassAnalytics(cid),
        courseApi.getStudents(cid),
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
      }

      // 学生数据
      if (studentsRes.status === 'fulfilled') {
        const raw =
          studentsRes.value?.data?.records ||
          studentsRes.value?.data?.list ||
          studentsRes.value?.data ||
          studentsRes.value ||
          []
        const list = Array.isArray(raw) ? raw : []
        students.value = list.map((s) => ({
          ...s,
          studentName: s.studentName || s.name || s.username || '-',
          studentId: s.studentId || s.studentNo || s.id || '-',
          onlineHours: s.onlineHours ?? s.totalOnlineHours ?? null,
          engagement: s.engagement ?? s.engagementScore ?? null,
          completionRate: s.completionRate ?? s.progress ?? null,
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
    searchKeyword,
    overview,
    students,
    activityTrendOption,
    engagementDistOption,
    filteredStudents,
  }
}
