import logger from '@/utils/logger'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import examApi from '@/api/exam'
import request from '@/api/request'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 全校考试管理领域逻辑
 * <p>试卷列表（筛选/分页/审核通过拒绝）、进行中考试（强制交卷/作弊检测）、试卷统计与成绩分布图。</p>
 */
export function useExamManagement() {
  // 加载状态
  const loading = ref(false)
  const loadingSessions = ref(false)

  // 筛选条件
  const filters = reactive({
    status: '',
    keyword: '',
  })

  // 分页
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0,
  })

  // 数据列表
  const examList = ref([])
  const activeSessions = ref([])

  // 概览数据
  const overview = reactive({
    totalPapers: 0,
    activeExams: 0,
    pendingReview: 0,
    cheatingCases: 0,
  })

  // 对话框状态
  const showActiveSessions = ref(false)
  const showCheatingDialog = ref(false)
  const showStatsDialog = ref(false)

  // 作弊检测日志
  const cheatingLogs = ref([])

  // 试卷统计数据
  const statistics = ref(null)

  // 成绩分布图表配置
  const gradeChartOption = ref({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 252, 248, 0.95)',
      borderColor: '#DDD6CE',
    },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: {
      type: 'category',
      data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
      axisLine: { lineStyle: { color: '#DDD6CE' } },
      axisLabel: { color: '#928B83' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
      axisLabel: { color: '#928B83' },
    },
    series: [
      {
        type: 'bar',
        barWidth: '45%',
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#7A8BA5' },
              { offset: 1, color: 'rgba(122, 139, 165, 0.3)' },
            ],
          },
        },
        data: [],
      },
    ],
  })

  /**
   * 获取试卷列表
   */
  async function fetchExamList() {
    loading.value = true
    try {
      const params: Record<string, unknown> = {
        // 统一为后端实际口径：ExamPaperController `@RequestParam(defaultValue="1") long page`
        // + MyBatis-Plus `Page.of(page, size)`，分页为 **1 基**，不做 -1 转换（P1-12）。
        page: pagination.page,
        size: pagination.size,
        all: true, // 获取全部试卷（管理员视角）
      }

      if (filters.status) params.status = filters.status
      if (filters.keyword) params.keyword = filters.keyword

      const res = await examApi.getExamPaperList(params)
      const payload = unwrap(res)
      examList.value = pickList(payload)
      // 缺陷修复（2026-10-04）：旧写法 `res.data?.records || res.data?.list || …` 中
      // 空数组 truthy 会吞掉真实数据；且总数用 `?? data.length` 回退，裸数组响应时
      // 把「本页条数」当总数，分页只显示 1 页。现改为后端 total 优先。
      pagination.total = readTotal(payload, examList.value.length)

      // 更新概览数据
      overview.totalPapers = pagination.total
    } catch (error) {
      logger.error('加载试卷列表失败:', error)
      ElMessage.error('加载试卷列表失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取进行中的考试
   */
  async function fetchActiveSessions() {
    loadingSessions.value = true
    try {
      const res = await examApi.getActiveExams()
      activeSessions.value = pickList(unwrap(res))
      overview.activeExams = activeSessions.value.length
    } catch (error) {
      logger.warn('获取进行中考试失败:', error)
      activeSessions.value = []
    } finally {
      loadingSessions.value = false
    }
  }

  /**
   * 获取状态对应的标签类型
   */
  function getStatusType(status) {
    const map = {
      DRAFT: 'info',
      PENDING: 'warning',
      PUBLISHED: 'success',
      ENDED: '',
    }
    return map[status] || 'info'
  }

  /**
   * 获取状态文本
   */
  function getStatusText(status) {
    const map = {
      DRAFT: '草稿',
      PENDING: '待审核',
      PUBLISHED: '已发布',
      ENDED: '已结束',
    }
    return map[status] || status
  }

  /**
   * 审核通过
   */
  async function handleApprove(row) {
    try {
      await ElMessageBox.confirm(`确定通过试卷「${row.title}」的审核？`, '确认操作', {
        confirmButtonText: '确定通过',
        cancelButtonText: '取消',
        type: 'info',
      })

      await request.patch(`/exam-papers/${row.id}/approve`)
      ElMessage.success('审核通过')
      fetchExamList()
    } catch (e) {
      if (e !== 'cancel') {
        ElMessage.error('操作失败')
      }
    }
  }

  /**
   * 审核拒绝
   */
  async function handleReject(row) {
    try {
      const { value: reason } = await ElMessageBox.prompt(
        `请输入拒绝试卷「${row.title}」的原因：`,
        '拒绝原因',
        {
          confirmButtonText: '确定拒绝',
          cancelButtonText: '取消',
          inputPattern: /\S+/,
          inputErrorMessage: '请输入拒绝原因',
        },
      )

      await request.post(`/exam-papers/${row.id}/reject`, { reason })
      ElMessage.success('已拒绝该试卷')
      fetchExamList()
    } catch (e) {
      if (e !== 'cancel') {
        ElMessage.error('操作失败')
      }
    }
  }

  /**
   * 强制交卷
   */
  async function forceSubmit(session) {
    try {
      // D2-2：强制交卷影响全体未交卷学生，明确影响范围与不可撤回
      const ok = await confirmDanger({
        verb: '强制交卷',
        target: '考试',
        subject: session.paperTitle,
        impact: '所有未交卷的学生将按当前作答立即提交，无法继续修改',
        irreversible: true,
        recovery: '交卷结果无法撤回，请确认后再执行',
        confirmText: '确定强制交卷',
      })
      if (!ok) return

      await examApi.forceSubmit(session.id)
      ElMessage.success('已强制交卷')
      fetchActiveSessions()
    } catch (e) {
      if (e !== 'cancel') {
        ElMessage.error('操作失败')
      }
    }
  }

  /**
   * 查看作弊检测
   */
  async function viewCheating(session) {
    showCheatingDialog.value = true
    try {
      const res = await examApi.getCheatingDetect(session.id)
      const data = res.data?.list || res.data || res || []
      cheatingLogs.value = Array.isArray(data) ? data : []

      // 更新作弊嫌疑数
      overview.cheatingCases = cheatingLogs.value.length
    } catch (error) {
      logger.warn('获取作弊检测失败:', error)
      cheatingLogs.value = []
    }
  }

  /**
   * 获取风险等级（用于评分组件）
   */
  function getRiskLevel(level) {
    if (!level) return 1
    if (typeof level === 'string') {
      const map = { LOW: 1, MEDIUM: 2, HIGH: 3 }
      return map[level.toUpperCase()] || 1
    }
    return Math.min(Math.max(level, 1), 3)
  }

  /**
   * 查看试卷详情
   */
  function viewDetail(row) {
    // 可以跳转到详情页或打开对话框
    ElMessage.info(`查看试卷: ${row.title}`)
  }

  /**
   * 查看试卷统计
   */
  async function viewStatistics(row) {
    showStatsDialog.value = true
    try {
      const res = await examApi.getExamStatistics(row.id)
      statistics.value = res.data || res

      // 更新成绩分布图
      if (statistics.value?.gradeDistribution) {
        gradeChartOption.value.series[0].data = statistics.value.gradeDistribution
      } else if (statistics.value?.distribution) {
        gradeChartOption.value.series[0].data = statistics.value.distribution
      }
    } catch (error) {
      logger.warn('获取统计数据失败:', error)
      statistics.value = null
      gradeChartOption.value.series[0].data = []
    }
  }

  // 初始化
  onMounted(() => {
    fetchExamList()
    fetchActiveSessions()
  })

  return {
    loading,
    loadingSessions,
    filters,
    pagination,
    examList,
    activeSessions,
    overview,
    showActiveSessions,
    showCheatingDialog,
    showStatsDialog,
    cheatingLogs,
    statistics,
    gradeChartOption,
    fetchExamList,
    getStatusType,
    getStatusText,
    handleApprove,
    handleReject,
    forceSubmit,
    viewCheating,
    getRiskLevel,
    viewDetail,
    viewStatistics,
  }
}
