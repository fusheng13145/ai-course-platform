import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'

/**
 * 异常成绩的唯一判定口径：后端显式标记，或分数 <45 / ≥98。
 * 未批阅（score 为 null/undefined/NaN）一律不算异常——否则 `null < 45` 在 JS 里
 * 会被强制成 0，把整列待批阅成绩误标为异常。
 */
export function isAbnormalGrade(item) {
  if (item?.isAbnormal) return true
  const score = item?.score
  if (score == null || isNaN(score)) return false
  return score < 45 || score >= 98
}

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

  /** 分页（页码从 1 起，与后端 MyBatis-Plus `Page.of(page,size)` 的 **1 基** 口径一致） */
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

  /** 加载完整课程下拉列表；分页细节由课程 API 统一处理。 */
  async function fetchCourses() {
    try {
      courseList.value = await courseApi.getAllCourses()
    } catch (error) {
      logger.warn('获取课程列表失败:', error)
      courseList.value = []
    }
  }

  /**
   * 加载成绩列表并联动更新统计与图表
   * 兼容多种响应结构（数组 / {records} / {list}，带或不带 total）
   *
   * 缺陷修复（P0-1，2026-10-04）：
   * ① 原实现组装好 `params` 却调用 `examApi.getMyExamSessions()` **不传任何参数**，
   *    导致分页 / 课程 / 学期 / 关键词筛选全部失效（参数被静默丢弃）；
   *    现在 params 真正传给 API（`getMyExamSessions` 已加上可选 params 并透传）。
   * ② 原实现 `pagination.total = data.length`（当前页长度），第 2 页只有 3 条时
   *    总数就变成 3，翻页控件直接算错。现在：后端回传 total 就以后端为准；
   *    后端未分页（返回全量数组）时由本地完成筛选+切片，total 取**筛选后的完整条数**
   *    （真实总数，非当前页长度），翻页控件不会误判。
   */
  async function fetchGrades() {
    loading.value = true
    try {
      // 后端分页口径为 1 基（Page.of(page,size) + defaultValue="1"），故直接发送 pagination.page
      const params: Record<string, unknown> = { page: pagination.page, size: pagination.size }
      if (filters.courseId) params.courseId = filters.courseId
      if (filters.semester) params.semester = filters.semester
      if (filters.keyword) params.keyword = filters.keyword

      const res = await examApi.getMyExamSessions(params)

      // 兼容三种信封：裸数组 / {list,total} / {records,total}
      const body = res?.data ?? res ?? {}
      const serverTotal =
        body && !Array.isArray(body) && typeof body.total === 'number' ? body.total : null
      const rawList = Array.isArray(body)
        ? body
        : (body?.list ?? body?.records ?? body?.grades ?? body?.items ?? [])
      let data = Array.isArray(rawList) ? rawList : []

      if (serverTotal !== null) {
        // 后端已完成分页，直接采信后端 total，绝不二次切片
        pagination.total = serverTotal
      } else {
        // 后端返回全量列表（/exam-sessions/my 现状：接收参数但不分页）
        // → 筛选与分页在本地补齐，保证筛选条件真正生效
        data = applyLocalFilters(data)
        pagination.total = data.length
        const start = (pagination.page - 1) * pagination.size
        data = data.slice(start, start + pagination.size)
      }

      // 仅显示异常：与下方 extractAbnormal 共用同一判定，避免「异常数卡片」与
      // 「仅看异常」列表口径不一致；未批阅（score 为 null）不算异常。
      if (showAbnormalOnly.value) {
        data = data.filter(isAbnormalGrade)
      }

      gradeList.value = data

      // 顺序要求：extractAbnormal 必须先于 updateOverviewStats。
      // 统计卡的「异常数」直接读 abnormalGrades.value.length，若后置则取到上一次
      // 加载的结果——首次加载恒显示 0，与页面列表里的异常标记数量不一致。
      extractAbnormal(data)
      updateOverviewStats(data)
      updateCharts(data)
    } catch (error) {
      logger.error('获取成绩列表失败:', error)
      gradeList.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 本地筛选（仅在后端未按条件过滤时兜底）。
   * 关键词命中学生姓名/课程名，courseId/semester 精确匹配。
   */
  function applyLocalFilters(list) {
    const kw = (filters.keyword || '').trim().toLowerCase()
    return list.filter((item) => {
      if (filters.courseId && String(item.courseId ?? '') !== String(filters.courseId)) return false
      if (filters.semester && item.semester !== filters.semester) return false
      if (kw) {
        const hay = `${item.studentName ?? ''} ${item.fullName ?? ''} ${item.courseName ?? ''}`
        if (!hay.toLowerCase().includes(kw)) return false
      }
      return true
    })
  }

  /** 概览统计：平均分、标准差、及格率、异常数 */
  function updateOverviewStats(data) {
    if (!Array.isArray(data) || data.length === 0) return
    const scores = data
      .map((g) => g.score)
      .filter((s) => s !== null && s !== undefined && !isNaN(s)) as number[]
    if (scores.length === 0) return

    const avg = scores.reduce((a, b) => a + b, 0) / scores.length
    const variance =
      scores.reduce((sum, score) => sum + Math.pow(score - avg, 2), 0) / scores.length
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
    const scores = data
      .map((g) => g.score)
      .filter((s) => s !== null && s !== undefined && !isNaN(s))

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
    abnormalGrades.value = data.filter(isAbnormalGrade)
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

  /** 客户端 XLSX 导出当前列表（xlsx 按需动态加载：323KB 大库只应在点导出时进内存） */
  async function exportGrades() {
    if (gradeList.value.length === 0) {
      ElMessage.warning('暂无可导出的成绩数据')
      return
    }
    try {
      const { downloadXlsx } = await import('@/utils/xlsxExport')
      const exportData = gradeList.value.map((item, idx) => ({
        序号: idx + 1,
        学生姓名: item.studentName || item.fullName || '-',
        课程名称: item.courseName || '-',
        成绩: item.score ?? '-',
        总分: item.totalScore ?? item.total ?? '-',
        提交时间: item.submitTime || item.createdAt || '-',
        状态: item.status || '-',
      }))
      downloadXlsx(
        exportData,
        '成绩报表',
        `成绩报表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
      )
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
