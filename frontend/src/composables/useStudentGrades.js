import { ref, computed } from 'vue'
import examApi from '@/api/exam'
import courseApi from '@/api/course'

/**
 * 学生成绩页数据逻辑（拆分自 student/academic/Grades.vue，2026-08-29）
 */
export function useStudentGrades() {
  /* ---- 数据状态 ---- */
  const loading = ref(false)
  const list = ref([])
  const showTable = ref(false)
  const courseMap = ref({})
  const examMap = ref({})

  /* ---- 计算属性 ---- */
  const avgScore = computed(() => {
    if (list.value.length === 0) return 0
    const sum = list.value.reduce((s, r) => s + (Number(r.score) || 0), 0)
    return Math.round(sum / list.value.length)
  })

  const rankDisplay = computed(() => {
    // 综合排名：取各课程排名中的最佳名次展示（后端注入 courseRank，未参与排名的课程不计入）
    const ranks = Object.values(groupedByCourse.value)
      .map((g) => g.rank)
      .filter((r) => Number.isFinite(r) && r > 0)
    if (ranks.length === 0) return '--'
    return `第 ${Math.min(...ranks)} 名`
  })

  /** 按课程分组 */
  const groupedByCourse = computed(() => {
    const groups = {}
    list.value.forEach((item) => {
      const name = item.courseName || '未知课程'
      if (!groups[name]) {
        groups[name] = { exams: [], scores: [], maxTotal: item.totalScore }
      }
      groups[name].exams.push(item)
      groups[name].scores.push(Number(item.score) || 0)
      if (item.totalScore && (!groups[name].maxTotal || item.totalScore > groups[name].maxTotal)) {
        groups[name].maxTotal = item.totalScore
      }
    })

    // 添加计算字段
    Object.keys(groups).forEach((name) => {
      const g = groups[name]
      g.avgScore = Math.round(g.scores.reduce((a, b) => a + b, 0) / g.scores.length)
      // 课程维度排名：后端在 /exam-sessions/my 注入 courseRank/courseTotalStudents（同课程各记录一致）
      g.rank = g.exams.find((e) => Number.isFinite(e.courseRank))?.courseRank ?? null
      g.totalStudents = g.exams.find((e) => Number.isFinite(e.courseTotalStudents))
        ?.courseTotalStudents ?? null
    })
    return groups
  })

  /* ---- 辅助方法 ---- */
  function getGradeLevelTag(score) {
    if (score >= 90) return 'success'
    if (score >= 75) return '' // primary
    if (score >= 60) return 'warning'
    return 'danger'
  }

  function getGradeLevelType(score) {
    if (score >= 90) return 'success'
    if (score >= 75) return ''
    if (score >= 60) return 'warning'
    return 'danger'
  }

  function getGradeLevelLabel(score) {
    if (score >= 90) return '优秀'
    if (score >= 75) return '良好'
    if (score >= 60) return '及格'
    return '不及格'
  }

  function getGradeClass(score) {
    if (score >= 90) return 'excellent'
    if (score >= 75) return 'good'
    if (score >= 60) return 'pass'
    return 'fail'
  }

  /* ---- 加载数据 ---- */
  async function loadData() {
    loading.value = true
    try {
      const [courseRes, examRes, sessionRes] = await Promise.all([
        courseApi.getCourseList(),
        examApi.getExamPaperList(),
        examApi.getMyExamSessions(),
      ])

      const courses =
        courseRes.data?.records || courseRes.data?.list || courseRes.data || courseRes || []
      courses.forEach((c) => {
        courseMap.value[c.id] = c.name
      })

      const exams = examRes.data?.list || examRes.data || examRes || []
      exams.forEach((e) => {
        examMap.value[e.id] = {
          title: e.title,
          courseId: e.courseId,
          totalScore: e.totalScore || 100,
        }
      })

      const sessions = sessionRes.data || sessionRes || []
      list.value = (Array.isArray(sessions) ? sessions : [])
        .filter((s) => s.status === 'SUBMITTED' || s.status === 'GRADED')
        .map((s) => {
          const exam = examMap.value[s.examPaperId] || {}
          return {
            id: s.id,
            examTitle: exam.title || '考试',
            courseName: courseMap.value[exam.courseId] || '未知课程',
            score: s.totalScore || 0,
            totalScore: exam.totalScore || 100,
            submitTime: s.submitTime || s.createdAt || '',
            courseRank: Number.isFinite(s.courseRank) ? s.courseRank : null,
            courseTotalStudents: Number.isFinite(s.courseTotalStudents)
              ? s.courseTotalStudents
              : null,
          }
        })
        .sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
    } catch (e) {
      console.error('加载成绩失败:', e)
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    list,
    showTable,
    avgScore,
    rankDisplay,
    groupedByCourse,
    getGradeLevelTag,
    getGradeLevelType,
    getGradeLevelLabel,
    getGradeClass,
    loadData,
  }
}
