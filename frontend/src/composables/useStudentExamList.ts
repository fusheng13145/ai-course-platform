import { ref, computed } from 'vue'
import logger from '@/utils/logger'
import examApi from '@/api/exam'
import studentApi from '@/api/student'
import { pickList, unwrap } from '@/utils/response'

/**
 * 计入尝试次数的会话状态：与后端 {@code ExamSessionLifecycleComponent#startExam}
 * 的次数校验口径保持一致（STARTED 也占一次，避免退出未交卷时被无限续考）。
 */
const ATTEMPT_COUNTED_STATUSES = new Set(['STARTED', 'SUBMITTED', 'GRADED', 'TIMEOUT'])

/** 按试卷汇总学生已用考试次数 */
function countAttemptsByPaper(sessions: Record<string, unknown>[]): Map<number, number> {
  const counts = new Map<number, number>()
  for (const s of sessions) {
    if (!ATTEMPT_COUNTED_STATUSES.has(String(s?.status ?? ''))) continue
    const paperId = Number(s?.examPaperId)
    if (!Number.isFinite(paperId)) continue
    counts.set(paperId, (counts.get(paperId) ?? 0) + 1)
  }
  return counts
}

/**
 * 学生考试·列表域（useStudentExams 内部拆分，FE-06）。
 *
 * <p>承载考试列表页的状态与操作：搜索/状态筛选/课程上下文过滤、列表加载
 * （含"已完成"标记回填）、统计卡与状态标签。不持有答题/定时器/提交状态。</p>
 *
 * <p>仅由 useStudentExams 编排消费，不单独对外承诺导出面。</p>
 */
export function useStudentExamList() {
  const searchKeyword = ref('')
  const statusFilter = ref('')
  const listLoading = ref(false)
  /**
   * 加载失败提示文案：非空即表示考试列表数据不可信。
   * 失败时列表被置空并显示"暂无考试"，学生易误判为"当前没有可参加的考试"。
   */
  const loadError = ref('')
  // 考试行尚未 per-endpoint 建模；字段访问点见 examJoinReason/getExamStatus*
  const examList = ref<Record<string, unknown>[]>([])
  /** 课程上下文过滤：非空时仅展示该课程的考试（课程侧边栏/驾驶舱直入） */
  const courseIdFilter = ref<string | number | null>(null)

  /**
   * 学生已选（审批通过）课程 ID 集合，用于全局考试列表的「未选课」预禁用。
   * 仅全局模式（courseIdFilter 为空）生效：课程内考试列表的归属已由课程空间门禁保证，不重复查。
   * 加载失败时退化为空集——此时不前端禁用，交由后端 startExam 的真实拒绝兜底。
   */
  const enrolledCourseIds = ref<Set<number>>(new Set())

  /** 课程上下文子集（统计卡与列表共同基准；统一入口未设置过滤时为全部） */
  const courseScopedExams = computed(() => {
    if (courseIdFilter.value == null) return examList.value
    return examList.value.filter((e) => String(e.courseId) === String(courseIdFilter.value))
  })

  /** 过滤后的考试列表（课程 + 关键字 + 状态） */
  const filteredExams = computed(() => {
    let list = courseScopedExams.value
    if (searchKeyword.value) {
      list = list.filter((e) => String(e.title ?? '').includes(searchKeyword.value))
    }
    if (statusFilter.value) {
      list = list.filter((e) => {
        if (statusFilter.value === 'completed') return e.completed
        if (statusFilter.value === 'available') return !e.completed && e.status !== 'EXPIRED'
        if (statusFilter.value === 'expired') return e.status === 'EXPIRED'
        return true
      })
    }
    return list
  })

  /** 考试总数 */
  const totalExams = computed(() => courseScopedExams.value.length)
  /** 可参加：未完成、未过期、已选课且在时间窗内（M-02b 预校验口径，与后端 startExam 一致） */
  const availableExams = computed(
    () => courseScopedExams.value.filter((e) => isExamJoinable(e)).length,
  )
  /** 已完成 */
  const completedExams = computed(() => courseScopedExams.value.filter((e) => e.completed).length)

  async function loadExamList() {
    listLoading.value = true
    loadError.value = ''
    try {
      // 课程上下文必须走**服务端**按课程过滤，不能只在已加载的第 1 页里做客户端筛选：
      // GET /exam-papers 是分页接口（默认 page=1、size=10，且本页没有分页控件），
      // 实测库存 114 份试卷时第 1 页只有 10 份、按 id 升序排列 → 老师刚发布的课程试卷
      // 大概率压根不在列表里，而 SeExamList/Exams.vue 的承诺是「仅展示本课程考试」。
      // 课程入口取足够大的 size，覆盖单课程规模，避免同一缺陷在课程维度复现。
      const res =
        courseIdFilter.value != null
          ? await examApi.getAllExamPapersByCourse(courseIdFilter.value)
          : await examApi.getAllExamPapers()
      // 两个 getAll* 已由 fetchAllPages 解包为数组（不再有 data 信封）；
      // pickList 对数组原样透传，同时防御信封形态回归。
      examList.value = pickList<Record<string, unknown>>(unwrap(res))

      // 全局模式：拉取已选课程，供「未选课」预禁用（课程内模式由课程空间门禁保证，不重复查）
      if (courseIdFilter.value == null) {
        try {
          const courses = await studentApi.getAllMyCourses()
          enrolledCourseIds.value = new Set(
            (Array.isArray(courses) ? courses : [])
              .map((c: { id?: unknown }) => Number(c?.id))
              .filter((id: number) => !Number.isNaN(id)),
          )
        } catch {
          enrolledCourseIds.value = new Set()
        }
      }

      // 回填尝试次数，并据此判定「已完成」（= 次数用尽），而不是「有过一次提交」。
      // 后端 startExam 按 exam_papers.max_attempts 限制次数并让 attempt_number 递增，
      // 前端若只按「有 SUBMITTED/GRADED 记录」置 completed，会把 max_attempts>1 的试卷
      // 也永久锁死，与「允许重考」的产品能力背离。
      try {
        const sessionRes = await examApi.getMyExamSessions()
        const sessions = sessionRes.data || sessionRes || []
        const usedByPaper = countAttemptsByPaper(Array.isArray(sessions) ? sessions : [])
        examList.value.forEach((e) => {
          const used = usedByPaper.get(Number(e.id)) ?? 0
          // 0 / null = 不限次数（后端 ExamSessionLifecycleComponent 只在 maxAttempts > 0 时限制）；
          // 只有配置了上限且已用满，才可能出现「已完成」锁定态
          const max = Number(e.maxAttempts ?? 0)
          const limited = Number.isFinite(max) && max > 0
          e.maxAttempts = max
          e.attemptsUsed = used
          e.attempted = used > 0
          e.completed = limited && used >= max
        })
      } catch {
        /* 忽略 */
      }
    } catch (e) {
      logger.error('加载考试列表失败', e)
      examList.value = []
      loadError.value = '考试列表加载失败，请检查网络后重试'
    } finally {
      listLoading.value = false
    }
  }

  function getExamStatusType(exam: Record<string, unknown>) {
    if (exam.completed) return 'info'
    if (exam.status === 'EXPIRED') return 'info'
    return 'warning'
  }

  function getExamStatusLabel(exam: Record<string, unknown>) {
    if (exam.completed) return '已完成'
    if (exam.status === 'EXPIRED') return '已过期'
    if (exam.attempted) return '可重考'
    return '进行中'
  }

  /**
   * 全局考试列表「可参加」预校验（M-02b）：镜像后端 startExam 的两类拒绝原因——
   * ① 未选课（全局模式下 exam.courseId 不在已选集合）；② 时间窗（now 落在 [startTime, endTime] 之外）。
   * 课程内模式不做选课校验（归属已由课程空间门禁保证）；两类原因都只在「未完成且未过期」时才参与判定。
   * 返回原因码；'none' 表示可参加。后端 startExam 的真实拒绝仍是最终兜底（保留真实失败提示）。
   */
  function examJoinReason(exam: Record<string, unknown>): string {
    if (exam.completed) return 'completed'
    if (exam.status === 'EXPIRED') return 'expired'
    if (courseIdFilter.value == null && exam.courseId != null) {
      if (!enrolledCourseIds.value.has(Number(exam.courseId))) return 'notEnrolled'
    }
    const now = Date.now()
    if (exam.startTime && now < new Date(String(exam.startTime)).getTime()) return 'beforeStart'
    if (exam.endTime && now > new Date(String(exam.endTime)).getTime()) return 'afterEnd'
    return 'none'
  }
  function isExamJoinable(exam: Record<string, unknown>): boolean {
    return examJoinReason(exam) === 'none'
  }

  return {
    searchKeyword,
    statusFilter,
    courseIdFilter,
    listLoading,
    loadError,
    examList,
    filteredExams,
    totalExams,
    availableExams,
    completedExams,
    loadExamList,
    getExamStatusType,
    getExamStatusLabel,
    examJoinReason,
    isExamJoinable,
  }
}
