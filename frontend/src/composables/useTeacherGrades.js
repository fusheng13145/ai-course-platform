import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import submissionApi from '@/api/submission'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import taskApi from '@/api/task'
import * as XLSX from 'xlsx'
import { getChartTheme, monoScore } from '@/utils/chartTheme'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'

/**
 * 教师成绩管理领域逻辑
 * <p>多源聚合（仪表盘统计/考试会话/作业提交/待批阅任务）与成绩合并、
 * 分布图构建与更新、XLSX 导出、手动批阅与 AI 评分、学生明细构建。</p>
 */
export function useTeacherGrades() {
  const auth = useAuthStore()

  const loading = ref(false)
  const searchText = ref('')
  const selectedCourseId = ref(null)
  const courseList = ref([])
  const selectedCourseName = ref('')

  // 总览统计
  const overview = ref({
    avgScore: null,
    maxScore: null,
    minScore: null,
    pendingCount: 0,
  })

  // 成绩列表
  const gradeList = ref([])

  // 待批阅任务
  const pendingTasks = ref([])

  // 详情弹窗
  const detailDialogVisible = ref(false)
  const currentStudent = ref(null)

  // 批阅弹窗
  const gradeDialogVisible = ref(false)
  const gradingStudent = ref(null)
  const currentTaskName = ref('')
  const gradingLoading = ref(false)
  const gradeForm = ref({
    score: 0,
    comment: '',
    fullScore: 100,
  })

  // 过滤后的成绩列表
  const filteredGradeList = computed(() => {
    if (!searchText.value) return gradeList.value
    const kw = searchText.value.toLowerCase()
    return gradeList.value.filter(
      (s) =>
        (s.studentName || '').toLowerCase().includes(kw) ||
        (s.studentNo || '').toLowerCase().includes(kw),
    )
  })

  onMounted(async () => {
    await loadCourseOptions()
    await loadGradeData()
  })

  // 加载课程下拉
  async function loadCourseOptions() {
    try {
      const res = await courseApi.getMyCourses()
      const data = res.data?.list || res.data || res || []
      courseList.value = Array.isArray(data) ? data : []
    } catch (e) {
      logger.error('加载课程失败:', e)
    }
  }

  // 加载成绩数据（增强：多源聚合 + 待批改 API）
  async function loadGradeData() {
    loading.value = true
    try {
      const params = {}
      if (selectedCourseId.value) params.courseId = selectedCourseId.value

      // 接入 auth store 的当前登录用户ID，作为待批改任务的教师维度过滤参数
      const teacherId = auth.userId

      // 并行加载：统计 + 考试会话 + 作业提交 + 待批改任务列表
      const [statsRes, sessionRes, submitRes, pendingRes] = await Promise.allSettled([
        import('@/api/analytics').then((m) => m.default.getDashboardStats(params)),
        examApi.getMyExamSessions(),
        submissionApi.getMySubmissions({ role: 'teacher', courseId: selectedCourseId.value }),
        taskApi.getPendingGrading(teacherId),
      ])

      // 处理总览统计
      if (statsRes.status === 'fulfilled') {
        const data = statsRes.value.data?.data || statsRes.value.data || {}
        overview.value = {
          avgScore: data.avgScore != null ? Math.round(data.avgScore * 10) / 10 : null,
          maxScore: data.maxScore ?? null,
          minScore: data.minScore ?? null,
          pendingCount: data.pendingCount ?? data.pendingGradeCount ?? 0,
        }
      }

      // 处理考试成绩
      const sessionData =
        sessionRes.status === 'fulfilled'
          ? sessionRes.value.data?.list || sessionRes.value.data || []
          : []
      // 处理作业提交
      const submitData =
        submitRes.status === 'fulfilled'
          ? submitRes.value.data?.list || submitRes.value.data || []
          : []

      // 合并成绩数据
      const studentMap = new Map()

      for (const s of sessionData) {
        const key = s.studentId || s.userId
        if (!studentMap.has(key)) {
          studentMap.set(key, {
            id: key,
            studentNo: s.studentNo || s.userNo || '-',
            studentName: s.studentName || s.userName || '未知',
            homeworkScore: null,
            examScore: s.score ?? null,
            totalScore: s.score ?? null,
            gradeLevel: getGradeLevel(s.score),
            needsGrading: !s.isGraded && s.status !== 'graded',
            submitTime: s.submitTime || s.completedAt || '',
            details: [],
            courseName: s.courseName || '',
          })
        } else {
          const existing = studentMap.get(key)
          existing.examScore = s.score ?? existing.examScore
          existing.totalScore = Math.round(((existing.totalScore + (s.score || 0)) / 2) * 10) / 10
          existing.gradeLevel = getGradeLevel(existing.totalScore)
        }
      }

      for (const sub of submitData) {
        const key = sub.studentId || sub.userId
        if (!studentMap.has(key)) {
          studentMap.set(key, {
            id: key,
            studentNo: sub.studentNo || sub.userNo || '-',
            studentName: sub.studentName || sub.userName || '未知',
            homeworkScore: sub.score ?? null,
            examScore: null,
            totalScore: sub.score ?? null,
            gradeLevel: getGradeLevel(sub.score),
            needsGrading: sub.status !== 'graded' && sub.status !== 'reviewed',
            submitTime: sub.submittedAt || sub.createdAt || '',
            details: [],
            courseName: sub.courseName || '',
          })
        } else {
          const existing = studentMap.get(key)
          existing.homeworkScore = sub.score ?? existing.homeworkScore
          existing.totalScore =
            existing.examScore != null && sub.score != null
              ? Math.round((existing.examScore * 0.5 + sub.score * 0.5) * 10) / 10
              : (existing.examScore ?? sub.score ?? null)
          existing.gradeLevel = getGradeLevel(existing.totalScore)
        }
      }

      gradeList.value = Array.from(studentMap.values())

      // 增强：从 taskApi 获取待批阅任务（优先于从提交记录推导）
      if (pendingRes.status === 'fulfilled') {
        const pData = pendingRes.value.data?.list || pendingRes.value.data || pendingRes.value || []
        const pendingList = Array.isArray(pData) ? pData : []
        if (pendingList.length > 0) {
          pendingTasks.value = pendingList.map((t) => ({
            id: t.id,
            taskName: t.title || t.name || t.taskName || '未命名任务',
            pendingCount: t.pendingCount ?? t.count ?? 1,
          }))
          // 更新待批阅计数
          overview.value.pendingCount = pendingList.length
        }
      }

      // 回退：如果没有待批改任务数据，从提交记录推导
      if (pendingTasks.value.length === 0) {
        pendingTasks.value = (
          submitData.filter((s) => s.status !== 'graded' && s.status !== 'reviewed') || []
        ).map((s) => ({
          id: s.id,
          taskName: s.taskName || s.title || '未命名任务',
          pendingCount: 1,
        }))
      }

      // 更新分布图
      updateDistribution(gradeList.value.map((g) => g.totalScore))

      // 更新选中的课程名
      if (selectedCourseId.value) {
        const found = courseList.value.find((c) => c.id === selectedCourseId.value)
        selectedCourseName.value = found?.name || ''
      }
    } catch (e) {
      logger.error('加载成绩数据失败:', e)
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    // 搜索由 computed 自动处理
  }

  function handleExport() {
    const data = filteredGradeList.value
    if (data.length === 0) {
      ElMessage.warning('暂无可导出的成绩数据')
      return
    }
    try {
      const exportData = data.map((item, idx) => ({
        序号: idx + 1,
        学号: item.studentNo || '-',
        姓名: item.studentName || '-',
        作业成绩: item.homeworkScore ?? '-',
        考试成绩: item.examScore ?? '-',
        总评成绩: item.totalScore ?? '-',
        评级: item.gradeLevel || '-',
        提交时间: item.submitTime || '-',
      }))
      const ws = XLSX.utils.json_to_sheet(exportData)
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '学生成绩')
      XLSX.writeFile(
        wb,
        `学生成绩_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
      )
      ElMessage.success('导出成功')
    } catch (e) {
      logger.error('导出失败:', e)
      ElMessage.error('导出失败')
    }
  }

  // 查看学生详情（增强：从已有成绩数据构建，不再使用模拟数据）
  function viewStudentDetail(row) {
    const details = []
    if (row.homeworkScore != null) {
      details.push({
        taskName: '平时作业',
        type: 'homework',
        score: row.homeworkScore,
        fullScore: 100,
        status: row.needsGrading ? 'pending' : 'graded',
        submitTime: row.submitTime,
      })
    }
    if (row.examScore != null) {
      details.push({
        taskName: '期末考试',
        type: 'exam',
        score: row.examScore,
        fullScore: 100,
        status: 'graded',
        submitTime: row.submitTime,
      })
    }

    currentStudent.value = {
      ...row,
      details,
    }
    detailDialogVisible.value = true
  }

  // 打开批阅对话框
  function openGradeDialog(row) {
    gradingStudent.value = row
    gradeForm.value = { score: 0, comment: '', fullScore: 100 }
    gradeDialogVisible.value = true
  }

  // AI评分（增强：使用 submissionApi.aiGrade）
  async function aiGrade(row) {
    try {
      await ElMessageBox.confirm(`确定使用AI自动评分「${row.studentName}」的作业？`, 'AI评分确认')
      await submissionApi.aiGrade(row.id)
      ElMessage.success('AI评分完成')
      loadGradeData()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.response?.data?.message || 'AI评分失败')
    }
  }

  // 提交评分
  async function submitGrade() {
    if (gradingStudent.value?.id == null) return
    gradingLoading.value = true
    try {
      await submissionApi.gradeSubmission(gradingStudent.value.id, {
        score: gradeForm.value.score,
        comment: gradeForm.value.comment,
      })
      ElMessage.success('评分成功')
      gradeDialogVisible.value = false
      loadGradeData()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '评分失败')
    } finally {
      gradingLoading.value = false
    }
  }

  // 跳转到批阅页面
  function goToGrading(task) {
    ElMessage.info(`跳转到「${task.taskName}」的批阅页面`)
  }

  // 辅助函数：根据分数获取等级
  function getGradeLevel(score) {
    if (score == null) return ''
    if (score >= 90) return 'A'
    if (score >= 80) return 'B'
    if (score >= 70) return 'C'
    if (score >= 60) return 'D'
    return 'F'
  }

  // 等级标签颜色
  function getGradeTagType(level) {
    const map = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
    return map[level] || 'info'
  }

  // 更新成绩分布图
  function buildDistributionOption() {
    const t = getChartTheme()
    const bandScores = [95, 85, 75, 65, 50]
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
      grid: { left: 50, right: 20, bottom: 30, top: 20 },
      xAxis: {
        type: 'category',
        data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
        axisLine: { lineStyle: { color: t.axis } },
        axisLabel: { color: t.text, fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
        axisLabel: { color: t.text, fontSize: 12 },
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
          label: { show: true, position: 'top', fontSize: 11, color: t.text },
        },
      ],
    }
  }

  const distributionOption = ref(buildDistributionOption())

  function updateDistribution(scores) {
    const buckets = [0, 0, 0, 0, 0]
    for (const s of scores) {
      if (s == null) continue
      if (s >= 90) buckets[0]++
      else if (s >= 80) buckets[1]++
      else if (s >= 70) buckets[2]++
      else if (s >= 60) buckets[3]++
      else buckets[4]++
    }
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
    detailDialogVisible,
    currentStudent,
    gradeDialogVisible,
    gradingStudent,
    currentTaskName,
    gradingLoading,
    gradeForm,
    filteredGradeList,
    distributionOption,
    loadGradeData,
    handleSearch,
    handleExport,
    viewStudentDetail,
    openGradeDialog,
    aiGrade,
    submitGrade,
    goToGrading,
    getGradeTagType,
  }
}
