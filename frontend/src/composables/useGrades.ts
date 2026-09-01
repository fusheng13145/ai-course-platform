import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import * as XLSX from 'xlsx'
import logger from '@/utils/logger'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'

/**
 * 成绩管理（管理员视角）数据与统计逻辑
 * <p>承载成绩管理页的全部状态与操作：课程/成绩数据加载、概览统计（平均分/标准差/及格率/异常数）、
 * 图表数据更新（成绩分布、各课程平均分对比）、异常成绩提取、重置筛选、
 * 客户端 XLSX 导出与详情对话框状态。</p>
 */
export function useGrades() {
  const loading = ref(false)

  /** 筛选条件（课程/学期/关键字） */
  const filters = reactive({ courseId: '', semester: '', keyword: '' })
  const showAbnormalOnly = ref(false)

  /** 分页（页码从 1 起，接口按 0 基转换） */
  const pagination = reactive({ page: 1, size: 10, total: 0 })

  const gradeList = ref([])
  const courseList = ref([])
  const abnormalGrades = ref([])

  const overviewStats = ref([
    { label: '平均分', value: '--' },
    { label: '标准差', value: '--' },
    { label: '及格率', value: '--' },
    { label: '异常数', value: 0 },
  ])

  /* ---- 详情对话框 ---- */
  const showDetailDialog = ref(false)
  const selectedGrade = ref(null)

  /* ---- 图表配置 ---- */
  const distTheme = getChartTheme()
  const distMono = getMonoSeries()
  const distributionOption = ref({
    tooltip: {
      trigger: 'axis',
      backgroundColor: distTheme.tooltipBg,
      borderColor: distTheme.tooltipBorder,
    },
    grid: { left: 45, right: 15, bottom: 25, top: 15 },
    xAxis: {
      type: 'category',
      data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
      axisLine: { lineStyle: { color: distTheme.axis } },
      axisLabel: { fontSize: 11, color: distTheme.subText },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: distTheme.split, type: 'dashed' } },
      axisLabel: { fontSize: 11, color: distTheme.subText },
    },
    series: [
      {
        type: 'bar',
        barWidth: '55%',
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: (params) => distMono[params.dataIndex % distMono.length],
        },
        data: [],
      },
    ],
  })

  const cmpTheme = getChartTheme()
  const cmpMono = getMonoSeries()
  const courseCompareOption = ref({
    tooltip: {
      trigger: 'axis',
      backgroundColor: cmpTheme.tooltipBg,
      borderColor: cmpTheme.tooltipBorder,
      formatter: '{b}: {c}分',
    },
    grid: { left: 100, right: 20, bottom: 20, top: 10 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: cmpTheme.split, type: 'dashed' } },
      axisLabel: { color: cmpTheme.subText, fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: [],
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: cmpTheme.text, fontSize: 11, width: 90, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        barWidth: '65%',
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: cmpMono[0] },
              { offset: 1, color: cmpMono[6] },
            ],
          },
        },
        label: { show: true, position: 'right', color: cmpTheme.text, fontSize: 11 },
        data: [],
      },
    ],
  })

  /** 加载课程下拉列表（最多 100 条） */
  async function fetchCourses() {
    try {
      const res = await courseApi.getCourseList({ page: 0, size: 100 })
      const data = res.data?.records || res.data?.list || res.data || res || []
      courseList.value = Array.isArray(data) ? data : []
    } catch (error) {
      logger.warn('获取课程列表失败:', error)
      courseList.value = []
    }
  }

  /**
   * 加载成绩列表并联动更新统计与图表
   * 兼容多种响应结构（records/list/grades/items）
   */
  async function fetchGrades() {
    loading.value = true
    try {
      const params: Record<string, any> = { page: pagination.page - 1, size: pagination.size }
      if (filters.courseId) params.courseId = filters.courseId
      if (filters.semester) params.semester = filters.semester
      if (filters.keyword) params.keyword = filters.keyword

      const res = await examApi.getMyExamSessions()
      const rawData = res.data?.records || res.data?.list || res.data || res || []
      let data = Array.isArray(rawData)
        ? rawData
        : (rawData?.records ?? rawData?.grades ?? rawData?.items ?? [])
      if (!Array.isArray(data)) {
        data = data.records || data.grades || data.items || []
      }

      // 仅显示异常：<40 或 =100 或接口已标记 isAbnormal
      if (showAbnormalOnly.value) {
        data = data.filter((item) => item.isAbnormal || item.score < 40 || item.score === 100)
      }

      gradeList.value = data
      pagination.total = Array.isArray(data) ? data.length : 0

      updateOverviewStats(data)
      updateCharts(data)
      extractAbnormal(data)
    } catch (error) {
      logger.error('获取成绩列表失败:', error)
      gradeList.value = []
    } finally {
      loading.value = false
    }
  }

  /** 概览统计：平均分、标准差、及格率、异常数 */
  function updateOverviewStats(data) {
    if (!Array.isArray(data) || data.length === 0) return
    const scores = data.map((g) => g.score).filter((s) => s !== null && s !== undefined && !isNaN(s)) as number[]
    if (scores.length === 0) return

    const avg = scores.reduce((a, b) => a + b, 0) / scores.length
    const variance = scores.reduce((sum, score) => sum + Math.pow(score - avg, 2), 0) / scores.length
    const stdDev = Math.sqrt(variance).toFixed(1)
    const passCount = scores.filter((s) => s >= 60).length
    const passRate = ((passCount / scores.length) * 100).toFixed(1)

    overviewStats.value[0].value = avg
    overviewStats.value[1].value = stdDev
    overviewStats.value[2].value = passRate + '%'
    overviewStats.value[3].value = abnormalGrades.value.length
  }

  /** 更新成绩分布（五档）与各课程平均分对比（升序） */
  function updateCharts(data) {
    if (!Array.isArray(data)) return
    const scores = data.map((g) => g.score).filter((s) => s !== null && s !== undefined && !isNaN(s))

    const dist = [0, 0, 0, 0, 0]
    scores.forEach((s) => {
      if (s < 60) dist[0]++
      else if (s < 70) dist[1]++
      else if (s < 80) dist[2]++
      else if (s < 90) dist[3]++
      else dist[4]++
    })
    distributionOption.value.series[0].data = dist

    const courseMap = new Map()
    data.forEach((item) => {
      const name = item.courseName || '未知课程'
      if (!courseMap.has(name)) courseMap.set(name, [])
      if (item.score != null && !isNaN(item.score)) courseMap.get(name).push(item.score)
    })

    const courseAvgs = Array.from(courseMap.entries())
      .map(([name, scores]) => ({
        name,
        avg: (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1),
      }))
      .sort((a, b) => parseFloat(a.avg) - parseFloat(b.avg))

    courseCompareOption.value.yAxis.data = courseAvgs.map((c) => c.name)
    courseCompareOption.value.series[0].data = courseAvgs.map((c) => parseFloat(c.avg))
  }

  /** 异常成绩：<45 或 ≥98 或接口已标记 */
  function extractAbnormal(data) {
    if (!Array.isArray(data)) {
      abnormalGrades.value = []
      return
    }
    abnormalGrades.value = data.filter(
      (item) => item.isAbnormal || (item.score != null && (item.score < 45 || item.score >= 98)),
    )
  }

  /** 分数段样式类名（表格与详情共用） */
  function getScoreClass(score) {
    if (score == null || isNaN(score)) return ''
    if (score >= 90) return 'excellent'
    if (score >= 80) return 'good'
    if (score >= 70) return 'medium'
    if (score >= 60) return 'pass'
    return 'fail'
  }

  /** 重置筛选并重新加载 */
  function resetFilters() {
    filters.courseId = ''
    filters.semester = ''
    filters.keyword = ''
    showAbnormalOnly.value = false
    fetchGrades()
  }

  /** 客户端 XLSX 导出当前列表 */
  function exportGrades() {
    if (gradeList.value.length === 0) {
      ElMessage.warning('暂无可导出的成绩数据')
      return
    }
    try {
      const exportData = gradeList.value.map((item, idx) => ({
        序号: idx + 1,
        学生姓名: item.studentName || item.fullName || '-',
        课程名称: item.courseName || '-',
        成绩: item.score ?? '-',
        总分: item.totalScore ?? item.total ?? '-',
        提交时间: item.submitTime || item.createdAt || '-',
        状态: item.status || '-',
      }))
      const ws = XLSX.utils.json_to_sheet(exportData)
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '成绩报表')
      XLSX.writeFile(wb, `成绩报表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`)
      ElMessage.success('报表已下载')
    } catch (e) {
      logger.error('导出成绩失败:', e)
      ElMessage.error('导出失败，请稍后重试')
    }
  }

  /** 打开成绩详情对话框 */
  function viewGradeDetail(row) {
    selectedGrade.value = row
    showDetailDialog.value = true
  }

  return {
    loading,
    filters,
    showAbnormalOnly,
    pagination,
    gradeList,
    courseList,
    abnormalGrades,
    overviewStats,
    showDetailDialog,
    selectedGrade,
    distributionOption,
    courseCompareOption,
    fetchCourses,
    fetchGrades,
    getScoreClass,
    resetFilters,
    exportGrades,
    viewGradeDetail,
  }
}
