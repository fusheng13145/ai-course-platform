<template>
  <!-- 学生视图：本人课程成绩（GET /student/grades，按 courseId 过滤） -->
  <div v-if="isStudent" class="student-grades">
    <div class="course-tab-bar">
      <div>
        <h2>
          我的成绩 <span class="chip chip-mute">{{ gradeItems.length }} 项</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="loadStudentGrades">刷新</button>
      </div>
    </div>

    <div v-if="loadError" class="callout danger">
      <span>加载失败</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" @click="loadStudentGrades">重试</button>
    </div>

    <div v-if="loading" class="empty-state compact"><b>正在加载成绩…</b></div>
    <div v-else-if="loadError" class="empty-state compact">
      <b>成绩数据未能加载</b>
      <p>这不是「暂无成绩」：请查看上方提示后重试。</p>
    </div>
    <!--
      课程条目缺失 ≠ 暂无成绩：后端 getGrades 吞异常后会返回 { courses: [] }，
      因此响应里找不到本课程时只能判定为「未能获取」，不能冒充空态。
    -->
    <div v-else-if="!gradeCourse" class="empty-state compact">
      <span class="e-ico"><Icon name="award" /></span>
      <b>成绩数据未能获取（可能暂不可用）</b>
      <p>成绩服务未返回本课程的数据；这不等于「暂无成绩」，请稍后重试。</p>
      <button class="btn btn-sm" @click="loadStudentGrades">重试</button>
    </div>
    <div v-else-if="!gradeItems.length" class="empty-state compact">
      <span class="e-ico"><Icon name="award" /></span>
      <b>本课程暂无成绩记录</b>
      <p>作业批改或考试出分后会显示在这里。</p>
    </div>
    <template v-else>
      <div class="grades-layout">
        <section class="card grade-summary">
          <h2>{{ gradeCourse.courseName || '本课程' }}</h2>
          <div class="grade-total">
            <b>{{ finalScoreText }}</b
            ><span v-if="hasFinalScore">/ 100</span>
          </div>
          <p class="grade-sub">总评由教师提交批阅后确认；未出分时显示「待出分」。</p>
        </section>
        <section class="card">
          <h2>成绩口径</h2>
          <p class="side-body">作业分来自教师批阅；考试分为答卷得分率，卷面总分单列。</p>
          <ul class="side-list">
            <li>成绩以教师提交批阅为准</li>
            <li>AI 建议分不直接写入成绩</li>
            <li>评语为教师批阅时填写的内容</li>
          </ul>
        </section>
      </div>

      <section class="card table-card" style="margin-top: 14px">
        <header class="card-head">
          <div>
            <h2>成绩项</h2>
          </div>
          <span v-if="feedbackNotice" class="chip chip-warn">{{ feedbackNotice }}</span>
        </header>
        <div v-if="gradeItems.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>成绩项</th>
                <th class="w-140">类型</th>
                <th class="w-140">得分</th>
                <th class="w-140">满分 / 卷面</th>
                <th class="w-140">状态</th>
                <th class="w-220">提交时间</th>
                <th>教师评语</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in gradeItems" :key="item.key">
                <td>
                  <b class="t-title">{{ item.title }}</b>
                </td>
                <td>
                  <span class="chip chip-info">{{ item.kind }}</span>
                </td>
                <td>{{ item.score == null ? '—' : `${item.score}${item.percent ? '%' : ''}` }}</td>
                <td>{{ item.maxScore == null ? '—' : item.maxScore }}</td>
                <td>
                  <span class="chip" :class="item.chip">{{ item.statusLabel }}</span>
                </td>
                <td>{{ fmtTime(item.submittedAt) }}</td>
                <td class="t-comment">{{ item.feedback || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <p v-if="!loading" class="export-note">
      「导出成绩」待扩展：后端学生成绩导出端点尚未落地，本页不提供该动作。
    </p>
  </div>

  <!-- 教师 / 管理员：原为硬编码演示视图（batch 24 死示例层清算时整段删除）。
       教师课程空间没有「成绩」页签（批阅走 grading），管理员该页签走只读视图，
       所以这里只留一句明确说明，不再画示例均分、示例表格与示例绩点环。 -->
  <div v-else class="card empty-state">
    <span class="e-ico"><Icon name="award" /></span>
    <b>该角色暂无此页签</b>
    <p>教师在课程空间用「批阅」处理成绩；管理员在课程页签看到的是只读视图。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from '../../../components/Icon.vue'
import type { Role } from '../../../nav'
import studentApi from '@/api/student'
import submissionApi from '@/api/submission'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

const props = withDefaults(defineProps<{ role: Role; courseId?: number }>(), { courseId: 0 })

const isStudent = computed(() => props.role === 'student')

/* ==================== 演示视图（教师 / 管理员） ==================== */

/* ==================== 学生成绩（真实接口） ==================== */

interface GradeAssignment {
  submissionId?: number | null
  taskId?: number | null
  title?: string
  score?: number | null
  maxScore?: number | null
  status?: string
  submittedAt?: string | null
}

interface GradeExam {
  sessionId?: number | null
  title?: string
  score?: number | null
  totalScore?: number | null
  status?: string
  submittedAt?: string | null
}

/** GET /student/grades 的 courses[] 项（StudentDashboardComponent#getGrades） */
interface GradeCoursePayload {
  courseId: number | string
  courseName?: string
  finalScore?: number | null
  progress?: number | null
  assignments?: GradeAssignment[]
  exams?: GradeExam[]
}

interface GradeItem {
  key: string
  title: string
  kind: '作业' | '考试'
  /** 作业为实际得分，考试为得分率（后端 exam.score = ExamSession.percentage） */
  score: number | null
  maxScore: number | null
  percent: boolean
  status: string
  statusLabel: string
  chip: string
  submittedAt: string
  feedback: string
}

const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: '已提交',
  GRADED: '已批改',
  RETURNED: '已退回',
  STARTED: '进行中',
  PENDING: '待批改',
}
const STATUS_CHIP: Record<string, string> = {
  SUBMITTED: 'chip-info',
  GRADED: 'chip-ok',
  RETURNED: 'chip-danger',
  STARTED: 'chip-warn',
  PENDING: 'chip-warn',
}

function attemptTime(row: GradeAssignment): number {
  if (!row.submittedAt) return Number.NEGATIVE_INFINITY
  const time = new Date(String(row.submittedAt).replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : Number.NEGATIVE_INFINITY
}

/**
 * 同一 taskId 重新提交会插入新行，/student/grades 会返回全部提交。
 * 成绩页按 taskId 分组只保留最新一次尝试，避免同一作业出现重复成绩行。
 */
function latestAttemptPerTask(rows: GradeAssignment[]): GradeAssignment[] {
  const latest = new Map<string, GradeAssignment>()
  const order: string[] = []
  for (const row of rows) {
    const key = String(row.taskId ?? `submission-${row.submissionId ?? order.length}`)
    const current = latest.get(key)
    if (!current) {
      latest.set(key, row)
      order.push(key)
      continue
    }
    const timeDelta = attemptTime(row) - attemptTime(current)
    const idDelta = Number(row.submissionId ?? 0) - Number(current.submissionId ?? 0)
    if (timeDelta > 0 || (timeDelta === 0 && idDelta > 0)) latest.set(key, row)
  }
  return order.map((key) => latest.get(key) as GradeAssignment)
}

const loading = ref(false)
const loadError = ref('')
const gradeCourse = ref<GradeCoursePayload | null>(null)
/** submissionId → 教师评语；/student/grades 不下发评语，按我的提交补齐 */
const feedbackBySubmission = ref<Record<string, string>>({})
const feedbackNotice = ref('')

const finalScoreText = computed(() => {
  const value = gradeCourse.value?.finalScore
  return value == null ? '待出分' : String(value)
})
const hasFinalScore = computed(() => gradeCourse.value?.finalScore != null)

const gradeItems = computed<GradeItem[]>(() => {
  const course = gradeCourse.value
  if (!course) return []
  const items: GradeItem[] = []
  for (const row of latestAttemptPerTask(course.assignments || [])) {
    const status = String(row.status || '').toUpperCase()
    items.push({
      key: `a-${row.taskId ?? row.submissionId}`,
      title: row.title || '作业',
      kind: '作业',
      score: row.score == null ? null : Number(row.score),
      maxScore: row.maxScore == null ? null : Number(row.maxScore),
      percent: false,
      status,
      statusLabel: STATUS_LABEL[status] || status || '—',
      chip: STATUS_CHIP[status] || 'chip-mute',
      submittedAt: row.submittedAt || '',
      feedback: feedbackBySubmission.value[String(row.submissionId ?? '')] || '',
    })
  }
  for (const row of course.exams || []) {
    const status = String(row.status || '').toUpperCase()
    items.push({
      key: `e-${row.sessionId ?? row.title}`,
      title: row.title || '考试',
      kind: '考试',
      score: row.score == null ? null : Number(row.score),
      maxScore: row.totalScore == null ? null : Number(row.totalScore),
      percent: true,
      status,
      statusLabel: STATUS_LABEL[status] || status || '—',
      chip: STATUS_CHIP[status] || 'chip-mute',
      submittedAt: row.submittedAt || '',
      feedback: '',
    })
  }
  return items
})

function fmtTime(value?: string): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadFeedback() {
  feedbackNotice.value = ''
  try {
    const rows = (await submissionApi.getAllMySubmissions()) as Record<string, unknown>[]
    const map: Record<string, string> = {}
    for (const row of Array.isArray(rows) ? rows : []) {
      const id = String(row.id ?? '')
      const feedback = String(row.feedback ?? '').trim()
      if (id && feedback) map[id] = feedback
    }
    feedbackBySubmission.value = map
  } catch (error) {
    // 评语是增量信息：失败不冒充「无评语」，显式标注
    logger.error('加载教师评语失败', error)
    feedbackBySubmission.value = {}
    feedbackNotice.value = '教师评语未能加载'
  }
}

async function loadStudentGrades() {
  if (!props.courseId) {
    gradeCourse.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  feedbackNotice.value = ''
  feedbackBySubmission.value = {}
  try {
    const body = unwrap(await studentApi.getGrades()) as {
      courses?: GradeCoursePayload[]
    } | null
    const courses = Array.isArray(body?.courses) ? (body?.courses as GradeCoursePayload[]) : []
    gradeCourse.value =
      courses.find((item) => String(item.courseId) === String(props.courseId)) ?? null
  } catch (error) {
    logger.error('加载学生成绩失败', error)
    gradeCourse.value = null
    loadError.value = '成绩数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
  if (gradeCourse.value) void loadFeedback()
}

watch(
  () => [props.courseId, isStudent.value] as const,
  () => {
    if (isStudent.value) void loadStudentGrades()
  },
  { immediate: true },
)
</script>

<style scoped>
.student-grades {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.t-title {
  display: block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.t-comment {
  max-width: 280px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ink-2);
}
.export-note {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.callout.danger {
  border-left-color: var(--danger);
}
.callout.danger > span {
  color: var(--danger);
}
</style>
