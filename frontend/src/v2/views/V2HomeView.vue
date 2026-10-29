<template>
  <section class="module">
    <V2PageHead :kicker="def.kicker" :title="def.title" :sub="sub" source="api">
      <template #actions>
        <button class="btn btn-ghost" @click="secondaryAction">{{ secondary.label }}</button>
        <button class="btn btn-primary" @click="primaryAction">
          {{ primary.label }}
          <V2Icon name="arrow" />
        </button>
      </template>
    </V2PageHead>

    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in stats" :key="metric.label" :metric="metric" :role="role" />
    </div>

    <div class="dash-grid">
      <section class="card card-courses">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ courseKicker }}</p>
            <h2>{{ courseTitle }}</h2>
          </div>
          <button class="text-btn" @click="goCourses">全部 <V2Icon name="arrow" /></button>
        </header>

        <div v-if="courseLoading" class="empty-state compact">
          <b>正在加载课程…</b>
          <p>读数来自接口，加载完成前不展示占位数据。</p>
        </div>
        <div v-else-if="courseError" class="callout danger" role="alert">
          <span>ERROR / 课程</span>
          <p>{{ courseError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <div v-else-if="courses.length" class="course-list">
          <button
            v-for="course in courses"
            :key="course.id"
            class="course-row"
            @click="openCourse(course.id)"
          >
            <span class="course-cover" :data-tone="course.tone">{{ course.short }}</span>
            <span class="course-info">
              <span class="course-name">{{ course.name }}</span>
              <span class="course-meta">{{ courseMeta(course) }}</span>
              <span v-if="role !== 'admin' && course.progress !== null" class="course-bar">
                <i :style="{ '--p': `${course.progress}%` }"></i>
              </span>
            </span>
            <span v-if="role === 'admin'" class="chip" :class="approvalView(course).cls">
              {{ approvalView(course).text }}
            </span>
            <span v-else class="course-progress">
              {{ course.progress === null ? '--' : course.progress
              }}<small v-if="course.progress !== null">%</small>
            </span>
          </button>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无课程</b>
          <p>{{ courseEmptyHint }}</p>
        </div>

        <div class="card-foot">
          <span>{{ courseFoot }}</span>
          <span class="mono">{{ role.toUpperCase() }}</span>
        </div>
      </section>

      <section class="card card-queue">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ queueKicker }}</p>
            <h2>{{ queueTitle }}</h2>
          </div>
          <button class="text-btn" @click="goQueue">
            {{ role === 'admin' ? '进入审批' : '查看全部' }}
            <V2Icon name="arrow" />
          </button>
        </header>

        <div v-if="queueLoading" class="empty-state compact">
          <b>正在加载待办…</b>
          <p>读数来自接口，加载完成前不展示占位数据。</p>
        </div>
        <div v-else-if="queueError" class="callout danger" role="alert">
          <span>ERROR / 待办</span>
          <p>{{ queueError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <template v-else-if="queueRows.length">
          <ul class="queue-list">
            <li v-for="item in queueRows" :key="item.key" class="queue-item">
              <span v-if="role === 'teacher'" class="queue-avatar">{{ item.avatar }}</span>
              <span v-else class="risk-dot" :class="`tone-${item.tone}`"></span>
              <span class="queue-main">
                <b>{{ item.title }}</b>
                <small>{{ item.meta }}</small>
              </span>
              <span class="chip" :class="`chip-${item.tone}`">{{ item.chip }}</span>
              <button v-if="item.to" class="text-btn" @click="go(item.to)">
                {{ item.action }}
              </button>
            </li>
          </ul>
          <p v-if="queueTruncated" class="card-hint">{{ queueTruncated }}</p>
        </template>
        <div v-else class="empty-state compact">
          <b>{{ queueEmptyTitle }}</b>
          <p>{{ queueEmptyHint }}</p>
        </div>

        <div class="card-foot">
          <span>{{ queueFoot }}</span>
          <span class="mono">{{ queueCountLabel }}</span>
        </div>
      </section>

      <section class="card card-chart">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ chartKicker }}</p>
            <h2>{{ chartTitle }}</h2>
          </div>
          <span class="chart-unit">{{ chartUnit }}</span>
        </header>
        <p class="card-sub">{{ chartSub }}</p>

        <div v-if="courseLoading" class="empty-state compact"><b>正在加载图表…</b></div>
        <div v-else-if="courseError" class="callout danger" role="alert">
          <span>ERROR / 图表</span>
          <p>{{ courseError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <div v-else-if="chartBars.length" class="bar-chart" role="img" :aria-label="chartLabel">
          <div v-for="bar in chartBars" :key="bar.label" class="bar-col">
            <span class="bar-val">{{ bar.display }}</span>
            <i class="bar" :style="{ '--v': `${Math.round((bar.value / chartMax) * 100)}%` }"></i>
            <span>{{ bar.label }}</span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无可视化数据</b>
          <p>{{ chartEmptyHint }}</p>
        </div>

        <div class="card-foot">
          <span>{{ chartFoot }}</span>
          <span>{{ role === 'admin' ? '按审批状态' : '按课程进度' }}</span>
        </div>
      </section>

      <section class="card card-ai">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ aiKicker }}</p>
            <h2>{{ aiTitle }}</h2>
          </div>
          <span class="ai-mini"><V2Icon name="sparkle" /></span>
        </header>
        <div class="ai-panel">
          <p class="ai-quote">“{{ aiSummary }}”</p>
          <div v-if="aiTags.length" class="ai-tags">
            <span v-for="tag in aiTags" :key="tag">{{ tag }}</span>
          </div>
          <button class="btn btn-primary btn-block" @click="aiAction">
            {{ aiActionLabel }} <V2Icon name="arrow" />
          </button>
          <p class="ai-caveat">{{ aiCaveat }}</p>
        </div>
      </section>

      <section class="card card-weak">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ weakKicker }}</p>
            <h2>{{ weakTitle }}</h2>
          </div>
          <V2Icon name="chart" />
        </header>

        <div v-if="courseLoading" class="empty-state compact"><b>正在加载…</b></div>
        <ul v-else-if="weakRows.length" class="weak-list">
          <li
            v-for="item in weakRows"
            :key="item.label"
            class="weak-item"
            :class="`tone-${item.tone}`"
          >
            <span>{{ item.label }}</span>
            <b>{{ item.value }}</b>
            <span class="weak-bar"><i :style="{ '--p': `${item.pct}%` }"></i></span>
          </li>
        </ul>
        <p v-else-if="courseError" class="card-hint">{{ courseError }}</p>
        <p v-else class="card-hint">暂无符合条件的记录。</p>
        <p class="card-hint">{{ weakHint }}</p>
      </section>

      <section class="card card-feed">
        <header class="card-head">
          <div>
            <p class="card-kicker">NOTIFICATIONS / RECENT</p>
            <h2>最近通知</h2>
          </div>
          <span class="chip" :class="feedChipClass">{{ feedChip }}</span>
        </header>

        <div v-if="feedLoading" class="empty-state compact"><b>正在加载通知…</b></div>
        <div v-else-if="feedError" class="callout danger" role="alert">
          <span>ERROR / 通知</span>
          <p>{{ feedError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <ul v-else-if="feedRows.length" class="feed-list">
          <li v-for="item in feedRows" :key="item.key" class="feed-item">
            <span class="feed-dot" :class="`tone-${item.tone}`"></span>
            <span class="feed-main">
              <b>{{ item.title }}</b>
              <small>{{ item.meta }}</small>
            </span>
            <time>{{ item.time }}</time>
          </li>
        </ul>
        <div v-else class="empty-state compact">
          <b>暂无通知</b>
          <p>课程通知、考试安排与系统公告会出现在这里。</p>
        </div>

        <div class="card-foot">
          <span>数据源 GET /notifications · page=1&amp;size=20</span>
          <span class="mono">{{ role.toUpperCase() }}</span>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import V2PageHead from '../components/V2PageHead.vue'
import V2Stat from '../components/V2Stat.vue'
import V2Icon from '../components/V2Icon.vue'
import { pageDef, type V2Metric, type V2Role } from '../nav'
import { useV2Ui } from '../composables/ui'
import {
  loadAdminOverview,
  loadNotifications,
  loadStudentDashboard,
  type V2Notification,
} from '../data/api'
import { pickList, unwrap } from '@/utils/response'
import teacherApi from '@/api/teacher'
import taskApi from '@/api/task'
import examApi from '@/api/exam'
import courseApi from '@/api/course'

type Dict = Record<string, unknown>

interface HomeCourse {
  id: number
  name: string
  code: string
  semester: string
  students: number | null
  progress: number | null
  status: string
  approvalStatus: string
  short: string
  tone: number
}

interface QueueRow {
  key: string
  title: string
  meta: string
  chip: string
  tone: string
  action: string
  to: string | null
  avatar: string
}

interface ChartBar {
  label: string
  value: number
  display: string
}

interface WeakRow {
  label: string
  value: string
  pct: number
  tone: string
}

interface FeedRow {
  key: string
  title: string
  meta: string
  time: string
  tone: string
}

const router = useRouter()
const { role, displayName } = useV2Ui()
const def = computed(() => pageDef(role.value as V2Role, 'home'))

const studentData = ref<Dict | null>(null)
const teacherDashboard = ref<Dict | null>(null)
const teacherPending = ref<Dict[] | null>(null)
const teacherPapers = ref<Dict[] | null>(null)
const teacherPapersPartial = ref(false)
const adminOverview = ref<Dict | null>(null)
const adminStats = ref<Dict | null>(null)
const adminCourses = ref<Dict[] | null>(null)
const notifications = ref<V2Notification[]>([])

const courseLoading = ref(true)
const courseError = ref('')
const queueLoading = ref(true)
const queueError = ref('')
const feedLoading = ref(true)
const feedError = ref('')

let loadSeq = 0

const isDict = (value: unknown): value is Dict =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value)

const num = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

const str = (value: unknown, fallback = ''): string => {
  if (value === null || value === undefined) return fallback
  const s = String(value).trim()
  return s || fallback
}

const clampPct = (value: number): number => Math.min(100, Math.max(0, Math.round(value)))

const formatDateTime = (value: unknown): string => {
  const s = str(value)
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}

const shortOf = (name: string, code: string): string => {
  const ascii = code
    .replace(/[^A-Za-z0-9]/g, '')
    .slice(0, 4)
    .toUpperCase()
  return ascii || name.slice(0, 2)
}

const toneOf = (id: number): number => (Math.abs(Number(id) || 1) % 5) + 1

const unavailable = (label: string, icon: string): V2Metric => ({
  label,
  value: '--',
  note: '暂不可用',
  icon,
})

function toHomeCourse(raw: Dict): HomeCourse {
  const id = num(raw.courseId ?? raw.id) ?? 0
  const name = str(raw.name ?? raw.courseName, `课程 #${id}`)
  const code = str(raw.code)
  return {
    id,
    name,
    code,
    semester: str(raw.semester),
    students: num(raw.studentCount),
    progress: num(raw.progress ?? raw.avgProgress),
    status: str(raw.status),
    approvalStatus: str(raw.approvalStatus),
    short: shortOf(name, code),
    tone: toneOf(id),
  }
}

const studentCourses = computed<Dict[]>(() => {
  const rows = studentData.value?.courses
  return Array.isArray(rows) ? rows.filter(isDict) : []
})

const studentSummary = computed<Dict>(() => {
  const summary = studentData.value?.summary
  return isDict(summary) ? summary : {}
})

const studentTodos = computed<Dict | null>(() => {
  const todos = studentData.value?.todos
  return isDict(todos) ? todos : null
})

const studentTodoLists = computed(() => {
  const assignments = studentTodos.value?.assignments
  const exams = studentTodos.value?.exams
  const signIns = studentTodos.value?.signIns
  return {
    assignments: Array.isArray(assignments) ? assignments.filter(isDict) : [],
    exams: Array.isArray(exams) ? exams.filter(isDict) : [],
    signIns: Array.isArray(signIns) ? signIns.filter(isDict) : [],
  }
})

const studentProgressAvg = computed<number | null>(() => {
  const values = studentCourses.value
    .map((course) => num(course.progress))
    .filter((value): value is number => value !== null)
  if (!values.length) return null
  return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length)
})

const teacherCourseCards = computed<Dict[]>(() => {
  const cards = teacherDashboard.value?.courseCards
  return Array.isArray(cards) ? cards.filter(isDict) : []
})

const teacherUpcomingPapers = computed<Dict[] | null>(() => {
  if (!teacherPapers.value) return null
  const now = Date.now()
  return teacherPapers.value.filter((paper) => {
    if (str(paper.status).toUpperCase() !== 'PUBLISHED') return false
    const start = Date.parse(str(paper.startTime))
    const end = Date.parse(str(paper.endTime))
    const hasStart = !Number.isNaN(start)
    const hasEnd = !Number.isNaN(end)
    // 无起止时间的已发布试卷无法判断是否「近期」，不计入；有开始无结束视为长期开放。
    if (!hasStart && !hasEnd) return false
    return !hasEnd || end >= now
  })
})

const adminPendingCourses = computed<Dict[]>(() => {
  const rows = adminCourses.value ?? []
  return rows.filter((row) => str(row.approvalStatus).toUpperCase() === 'PENDING')
})

const courses = computed<HomeCourse[]>(() => {
  if (role.value === 'student') return studentCourses.value.map(toHomeCourse)
  if (role.value === 'teacher') return teacherCourseCards.value.map(toHomeCourse)
  return (adminCourses.value ?? []).map(toHomeCourse)
})

function courseNameOf(courseId: unknown): string {
  const id = num(courseId)
  if (id === null) return '课程未知'
  return courses.value.find((course) => course.id === id)?.name || `课程 #${id}`
}

const studentQueueRows = computed<QueueRow[]>(() => {
  const lists = studentTodoLists.value
  const coursePath = (courseId: unknown, tab: string) => {
    const id = num(courseId)
    return id === null ? '/v2/student/tasks' : `/v2/student/courses/${id}/${tab}`
  }
  return [
    ...lists.assignments.map((row) => ({
      key: `assignment-${str(row.taskId ?? row.id)}`,
      title: str(row.title, '作业'),
      meta: `${courseNameOf(row.courseId)} · ${
        str(row.dueDate) ? `截止 ${formatDateTime(row.dueDate)}` : '未设置截止'
      }`,
      chip: '作业',
      tone: 'warn',
      action: '查看',
      to: coursePath(row.courseId, 'tasks'),
      avatar: '',
    })),
    ...lists.exams.map((row) => ({
      key: `exam-${str(row.examPaperId ?? row.id)}`,
      title: str(row.title, '考试'),
      meta: `${courseNameOf(row.courseId)} · ${
        str(row.startTime) ? `开考 ${formatDateTime(row.startTime)}` : '时间待定'
      }`,
      chip: '考试',
      tone: 'info',
      action: '查看',
      to: coursePath(row.courseId, 'exams'),
      avatar: '',
    })),
    ...lists.signIns.map((row) => ({
      key: `signin-${str(row.signInId ?? row.id)}`,
      title: str(row.title, '签到'),
      meta: `${courseNameOf(row.courseId)} · ${
        str(row.closedAt) ? `关闭 ${formatDateTime(row.closedAt)}` : '关闭时间待定'
      }`,
      chip: '签到',
      tone: 'ok',
      action: '查看',
      to: coursePath(row.courseId, 'overview'),
      avatar: '',
    })),
  ]
})

const queueRows = computed<QueueRow[]>(() => {
  if (role.value === 'teacher') {
    return (teacherPending.value ?? []).slice(0, 8).map((row) => {
      const courseId = num(row.courseId)
      return {
        key: `grading-${str(row.id)}`,
        title: str(row.title, '待批阅任务'),
        meta: `${courseNameOf(row.courseId)} · ${
          str(row.dueDate) ? `截止 ${formatDateTime(row.dueDate)}` : '未设置截止'
        }`,
        chip: '待批阅',
        tone: 'warn',
        action: '去批阅',
        to: courseId === null ? null : `/v2/teacher/courses/${courseId}/grading`,
        avatar: str(row.title, '任').slice(0, 1),
      }
    })
  }
  if (role.value === 'admin') {
    return adminPendingCourses.value.slice(0, 8).map((row) => {
      const id = num(row.id ?? row.courseId) ?? 0
      return {
        key: `approval-${id}`,
        title: str(row.name ?? row.courseName, `课程 #${id}`),
        meta: [str(row.code), str(row.semester)].filter(Boolean).join(' · ') || `课程 #${id}`,
        chip: '待审批',
        tone: 'warn',
        action: '查看',
        to: id ? `/v2/admin/courses/${id}/overview` : null,
        avatar: '',
      }
    })
  }
  return studentQueueRows.value.slice(0, 8)
})

const queueTotal = computed(() => {
  if (role.value === 'teacher') return teacherPending.value?.length ?? 0
  if (role.value === 'admin') return adminPendingCourses.value.length
  return studentQueueRows.value.length
})

const queueTruncated = computed(() =>
  queueTotal.value > 8 ? `共 ${queueTotal.value} 条，显示前 8 条。` : '',
)

const studentMetrics = computed<V2Metric[]>(() => {
  const data = studentData.value
  const summary = studentSummary.value
  const lists = studentTodoLists.value
  const courseCount = data ? (num(summary.courseCount) ?? studentCourses.value.length) : null
  const progress = data ? studentProgressAvg.value : null
  const todoCount =
    data && studentTodos.value
      ? lists.assignments.length + lists.exams.length + lists.signIns.length
      : null
  const unread = data ? (num(summary.unreadCount) ?? num(data.unreadCount)) : null
  return [
    courseCount === null
      ? unavailable('已选课程', 'book')
      : { label: '已选课程', value: courseCount, unit: '门', note: '本学期', icon: 'book' },
    progress === null
      ? unavailable('学习进度', 'chart')
      : {
          label: '学习进度',
          value: progress,
          unit: '%',
          note: '按课程进度平均',
          icon: 'chart',
          tone: 'ok',
        },
    todoCount === null
      ? unavailable('待办事项', 'check')
      : {
          label: '待办事项',
          value: todoCount,
          unit: '项',
          note: `${lists.assignments.length} 作业 · ${lists.exams.length} 考试 · ${lists.signIns.length} 签到`,
          icon: 'check',
          tone: 'warn',
        },
    unread === null
      ? unavailable('未读通知', 'bell')
      : { label: '未读通知', value: unread, unit: '条', note: '消息中心', icon: 'bell' },
  ]
})

const teacherMetrics = computed<V2Metric[]>(() => {
  const dashboard = teacherDashboard.value
  const courseCount = dashboard
    ? (num(dashboard.courseCount) ?? teacherCourseCards.value.length)
    : null
  const pending = teacherPending.value ? teacherPending.value.length : null
  const upcoming = teacherPapers.value ? (teacherUpcomingPapers.value?.length ?? 0) : null
  const students = dashboard ? num(dashboard.studentCount) : null
  return [
    courseCount === null
      ? unavailable('我的课程', 'book')
      : { label: '我的课程', value: courseCount, unit: '门', note: '本人负责', icon: 'book' },
    pending === null
      ? unavailable('待批阅任务', 'check')
      : {
          label: '待批阅任务',
          value: pending,
          unit: '项',
          note: '有待批提交',
          icon: 'check',
          tone: 'warn',
        },
    upcoming === null
      ? unavailable('近期考试', 'clipboard')
      : {
          label: '近期考试',
          value: upcoming,
          unit: '场',
          note: teacherPapersPartial.value ? '部分课程未加载' : '已发布未结束',
          icon: 'clipboard',
          tone: 'info',
        },
    students === null
      ? unavailable('学生', 'users')
      : { label: '学生', value: students, unit: '人', note: '选课人次', icon: 'users' },
  ]
})

const adminMetrics = computed<V2Metric[]>(() => {
  const overview = adminOverview.value ?? {}
  const dashboard = adminStats.value ?? {}
  const totalUsers = num(overview.totalUsers) ?? num(dashboard.totalUsers)
  const totalCourses =
    num(overview.totalCourses) ??
    num(dashboard.totalCourses) ??
    (adminCourses.value ? adminCourses.value.length : null)
  const activeCourses = num(overview.activeCourses) ?? num(dashboard.activeCourses)
  const pending = adminCourses.value ? adminPendingCourses.value.length : null
  const activeUsers = num(dashboard.activeUsers)
  return [
    totalUsers === null
      ? unavailable('平台用户', 'users')
      : { label: '平台用户', value: totalUsers, unit: '人', note: '全校账号', icon: 'users' },
    totalCourses === null
      ? unavailable('课程总数', 'book')
      : {
          label: '课程总数',
          value: totalCourses,
          unit: '门',
          note: activeCourses === null ? '平台总额' : `${activeCourses} 门活跃`,
          icon: 'book',
        },
    pending === null
      ? unavailable('待审批课程', 'check')
      : {
          label: '待审批课程',
          value: pending,
          unit: '门',
          note: 'approvalStatus=PENDING',
          icon: 'check',
          tone: 'warn',
        },
    activeUsers === null
      ? unavailable('启用用户', 'user')
      : {
          label: '启用用户',
          value: activeUsers,
          unit: '人',
          note: '账号状态',
          icon: 'user',
          tone: 'ok',
        },
  ]
})

const stats = computed<V2Metric[]>(() => {
  if (role.value === 'teacher') return teacherMetrics.value
  if (role.value === 'admin') return adminMetrics.value
  return studentMetrics.value
})

const chartBars = computed<ChartBar[]>(() => {
  if (role.value === 'admin') {
    const rows = adminCourses.value ?? []
    if (!rows.length) return []
    const known = new Set(['PENDING', 'APPROVED', 'REJECTED'])
    const counts = { PENDING: 0, APPROVED: 0, REJECTED: 0, OTHER: 0 }
    rows.forEach((row) => {
      const status = str(row.approvalStatus).toUpperCase()
      if (status === 'PENDING') counts.PENDING++
      else if (status === 'APPROVED') counts.APPROVED++
      else if (status === 'REJECTED') counts.REJECTED++
      else if (!known.has(status)) counts.OTHER++
    })
    return [
      { label: '待审批', value: counts.PENDING, display: `${counts.PENDING}` },
      { label: '已通过', value: counts.APPROVED, display: `${counts.APPROVED}` },
      { label: '已驳回', value: counts.REJECTED, display: `${counts.REJECTED}` },
      { label: '其他', value: counts.OTHER, display: `${counts.OTHER}` },
    ].filter((bar) => bar.value > 0)
  }
  return courses.value
    .filter((course) => course.progress !== null)
    .slice(0, 8)
    .map((course) => ({
      label: course.short,
      value: course.progress as number,
      display: `${course.progress}%`,
    }))
})

const chartMax = computed(() => Math.max(...chartBars.value.map((bar) => bar.value), 1))
const chartLabel = computed(
  () =>
    `${chartTitle.value}：${chartBars.value.map((bar) => `${bar.label} ${bar.display}`).join('，')}`,
)

const weakRows = computed<WeakRow[]>(() => {
  if (role.value === 'teacher') {
    const list = teacherDashboard.value?.atRiskStudents
    const rows = Array.isArray(list) ? list.filter(isDict) : []
    return rows.slice(0, 6).map((row) => {
      const progress = num(row.progress)
      const score = num(row.finalScore)
      return {
        label: `${str(row.studentName, '学生')} · ${str(row.courseName, '课程')}`,
        value: progress !== null ? `${progress}%` : score !== null ? `${score} 分` : '—',
        pct: progress !== null ? clampPct(progress) : score !== null ? clampPct(score) : 0,
        tone: str(row.level) === 'HIGH' ? 'danger' : 'warn',
      }
    })
  }
  if (role.value === 'admin') {
    const rows = adminCourses.value ?? []
    if (!rows.length) return []
    const labels: Record<string, string> = {
      ACTIVE: '在用',
      INACTIVE: '停用',
      DRAFT: '草稿',
      ARCHIVED: '归档',
      UNKNOWN: '未标记',
    }
    const counts = new Map<string, number>()
    rows.forEach((row) => {
      const status = str(row.status).toUpperCase() || 'UNKNOWN'
      counts.set(status, (counts.get(status) ?? 0) + 1)
    })
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4)
      .map(([status, count]) => ({
        label: labels[status] ?? status,
        value: `${count} 门`,
        pct: Math.round((count * 100) / rows.length),
        tone: status === 'ACTIVE' ? 'ok' : status === 'INACTIVE' ? 'danger' : 'warn',
      }))
  }
  return courses.value
    .filter((course) => course.progress !== null)
    .sort((a, b) => (a.progress as number) - (b.progress as number))
    .slice(0, 5)
    .map((course) => {
      const progress = course.progress as number
      return {
        label: course.name,
        value: `${progress}%`,
        pct: clampPct(progress),
        tone: progress < 60 ? 'danger' : 'warn',
      }
    })
})

const feedRows = computed<FeedRow[]>(() =>
  notifications.value.slice(0, 6).map((item) => ({
    key: String(item.id),
    title: item.title,
    meta: item.kind || '通知',
    time: formatDateTime(item.time),
    tone: item.unread ? 'accent' : 'info',
  })),
)

const unreadCount = computed(() => notifications.value.filter((item) => item.unread).length)

const sub = computed(() => {
  if (role.value === 'student') {
    if (!studentData.value) return `${displayName.value}，课程与待办数据暂不可用。`
    return `${displayName.value}，这里是你今天的课程进度与待办。${courses.value.length} 门课程。`
  }
  if (role.value === 'teacher') {
    return `${displayName.value}，课程、待批阅与近期考试的单一入口。`
  }
  return '平台规模、审批队列与运行概览；院系作用域需后端支持，本版按校级展示。'
})

const courseKicker = computed(() => `COURSES / ${courseLoading.value ? '…' : courses.value.length}`)
const courseTitle = computed(() => (role.value === 'admin' ? '课程目录' : '课程'))
const courseEmptyHint = computed(() =>
  role.value === 'admin' ? '课程目录为空。' : '当前账号下暂无课程。',
)
const courseFoot = computed(() => {
  if (role.value === 'teacher') return '数据源 GET /teacher/dashboard/v2 · courseCards'
  if (role.value === 'admin') return '数据源 GET /courses'
  return '数据源 GET /student/dashboard · courses'
})

const queueTitle = computed(() => {
  if (role.value === 'teacher') return '待批阅任务'
  if (role.value === 'admin') return '待审批课程'
  return '今日待办'
})
const queueKicker = computed(() => {
  const count = queueLoading.value || queueError.value ? '—' : queueTotal.value
  if (role.value === 'teacher') return `TO REVIEW / ${count} TASKS`
  if (role.value === 'admin') return `APPROVALS / ${count}`
  return `TODAY / ${count} ITEMS`
})
const queueEmptyTitle = computed(() => {
  if (role.value === 'teacher') return '暂无待批阅任务'
  if (role.value === 'admin') return '暂无待审批课程'
  return '暂无待办'
})
const queueEmptyHint = computed(() => {
  if (role.value === 'teacher') return '学生提交后，有待批提交的任务会出现在这里。'
  if (role.value === 'admin') return '课程提交审批后会出现在这里。'
  return '当前没有未完成的作业、考试或签到。'
})
const queueFoot = computed(() => {
  if (role.value === 'teacher') return '数据源 GET /tasks/pending-grading'
  if (role.value === 'admin') return '数据源 GET /courses · approvalStatus=PENDING'
  return '数据源 GET /student/dashboard · todos'
})
const queueCountLabel = computed(() => {
  if (queueLoading.value) return 'LOADING'
  if (queueError.value) return 'UNAVAILABLE'
  return `${queueTotal.value} ITEMS`
})

const chartTitle = computed(() => (role.value === 'admin' ? '审批状态分布' : '课程进度'))
const chartKicker = computed(() =>
  role.value === 'admin' ? 'APPROVALS / STATUS' : 'PROGRESS / COURSE',
)
const chartUnit = computed(() => (role.value === 'admin' ? '门' : '%'))
const chartSub = computed(() =>
  role.value === 'admin'
    ? '按课程审批状态统计；口径为 GET /courses 全量。'
    : '按课程当前进度展示，无进度的课程不计入。',
)
const chartFoot = computed(() => {
  if (role.value === 'teacher') return '数据源 GET /teacher/dashboard/v2'
  if (role.value === 'admin') return '数据源 GET /courses'
  return '数据源 GET /student/dashboard'
})
const chartEmptyHint = computed(() =>
  role.value === 'admin' ? '暂无课程审批状态可统计。' : '暂无课程进度可统计。',
)

const aiKicker = computed(() => {
  if (role.value === 'teacher') return 'AI / TEACHING'
  if (role.value === 'admin') return 'GOVERNANCE / SHORTCUTS'
  return 'AI / ASSISTANT'
})
const aiTitle = computed(() => {
  if (role.value === 'teacher') return 'AI 教学总览'
  if (role.value === 'admin') return '治理入口'
  return 'AI 助手'
})
const aiSummary = computed(() => {
  if (role.value === 'student') {
    return '基于课程资料与个人学习数据，提供问答与复习建议。'
  }
  if (role.value === 'admin') {
    return '课程审批、考试治理与内容审核集中处理；治理操作会写入审计日志。'
  }
  const overview = teacherDashboard.value?.aiOverview
  const summary = isDict(overview) ? str(overview.summary) : ''
  return summary || '暂不可用'
})
const aiTags = computed<string[]>(() => {
  if (role.value === 'student') return ['课程问答', '复习路径']
  if (role.value === 'admin') return ['课程审批', '考试治理', '内容审核']
  const tags: string[] = []
  if (teacherPending.value) tags.push(`待批阅 ${teacherPending.value.length} 项`)
  if (weakRows.value.length) tags.push(`风险学生 ${weakRows.value.length} 人`)
  return tags
})
const aiCaveat = computed(() => {
  if (role.value === 'student') return '回答以课程资料为准，不替代教师批阅。'
  if (role.value === 'admin') return '治理操作会写入审计日志。'
  return teacherDashboard.value
    ? '由服务端教学数据生成，采纳前需教师确认。'
    : '教学数据加载失败，AI 概览暂不可用。'
})
const aiActionLabel = computed(() => {
  if (role.value === 'teacher') return '查看学情分析'
  if (role.value === 'admin') return '进入考试治理'
  return '打开 AI 助手'
})
const aiTo = computed(() => {
  if (role.value === 'teacher') return '/v2/teacher/analytics'
  if (role.value === 'admin') return '/v2/admin/exam-governance'
  const id = courses.value[0]?.id
  return id ? `/v2/student/courses/${id}/ai` : '/v2/student/assistant'
})

const weakTitle = computed(() => {
  if (role.value === 'teacher') return '风险学生'
  if (role.value === 'admin') return '课程状态'
  return '进度偏低课程'
})
const weakKicker = computed(() => {
  if (role.value === 'teacher') return 'RISK / STUDENTS'
  if (role.value === 'admin') return 'COURSES / STATUS'
  return 'PROGRESS / LOW'
})
const weakHint = computed(() => {
  if (role.value === 'teacher') return '进度 < 40% 或总评 < 60% 自动列为风险，按严重度排序。'
  if (role.value === 'admin') return '按课程启用状态统计，最多展示 4 类。'
  return '掌握度低于 60% 自动标红，按进度升序。'
})

const feedChip = computed(() => {
  if (feedLoading.value) return '加载中'
  if (feedError.value) return '暂不可用'
  return unreadCount.value > 0 ? `${unreadCount.value} 条未读` : '全部已读'
})
const feedChipClass = computed(() => {
  if (feedError.value) return 'chip-danger'
  if (feedLoading.value || unreadCount.value === 0) return 'chip-mute'
  return 'chip-warn'
})

const primary = computed(() => {
  if (role.value === 'student') return { label: '继续学习' }
  if (role.value === 'teacher') return { label: '开始批阅' }
  return { label: '课程审批' }
})
const secondary = computed(() => {
  if (role.value === 'student') return { label: '问 AI 助手' }
  if (role.value === 'teacher') return { label: '教学任务' }
  return { label: '教学班监管' }
})

function approvalView(course: HomeCourse): { text: string; cls: string } {
  const approval = course.approvalStatus.toUpperCase()
  if (approval === 'APPROVED') return { text: '已通过', cls: 'chip-ok' }
  if (approval === 'PENDING') return { text: '待审批', cls: 'chip-warn' }
  if (approval === 'REJECTED') return { text: '已驳回', cls: 'chip-danger' }
  const status = course.status.toUpperCase()
  if (status === 'ACTIVE') return { text: '在用', cls: 'chip-ok' }
  if (status === 'INACTIVE') return { text: '停用', cls: 'chip-danger' }
  if (status === 'DRAFT') return { text: '草稿', cls: 'chip-warn' }
  return { text: course.approvalStatus || course.status || '未标记', cls: 'chip-mute' }
}

function courseMeta(course: HomeCourse): string {
  if (role.value === 'admin') {
    return [course.code, course.semester].filter(Boolean).join(' · ') || `课程 #${course.id}`
  }
  if (role.value === 'teacher') {
    return (
      [course.code, course.students === null ? '' : `${course.students} 名学生`]
        .filter(Boolean)
        .join(' · ') || '本人负责'
    )
  }
  return course.code || '在修课程'
}

function go(path: string | null) {
  if (path) router.push(path)
}

function goCourses() {
  router.push(`/v2/${role.value}/courses`)
}

function openCourse(id: number) {
  router.push(`/v2/${role.value}/courses/${id}/overview`)
}

function goQueue() {
  if (role.value === 'student') router.push('/v2/student/tasks')
  else if (role.value === 'teacher') router.push('/v2/teacher/inbox')
  else router.push('/v2/admin/approvals')
}

function primaryAction() {
  if (role.value === 'student') {
    const id = courses.value[0]?.id
    router.push(id ? `/v2/student/courses/${id}/chapters` : '/v2/student/courses')
    return
  }
  if (role.value === 'teacher') {
    const id = courses.value[0]?.id
    router.push(id ? `/v2/teacher/courses/${id}/grading` : '/v2/teacher/inbox')
    return
  }
  router.push('/v2/admin/approvals')
}

function secondaryAction() {
  if (role.value === 'student') {
    const id = courses.value[0]?.id
    router.push(id ? `/v2/student/courses/${id}/ai` : '/v2/student/assistant')
    return
  }
  if (role.value === 'teacher') {
    router.push('/v2/teacher/inbox')
    return
  }
  router.push('/v2/admin/teaching-classes')
}

function aiAction() {
  go(aiTo.value)
}

async function fetchFeed() {
  try {
    notifications.value = await loadNotifications(true)
    feedError.value = ''
  } catch {
    notifications.value = []
    feedError.value = '通知加载失败，请重试'
  } finally {
    feedLoading.value = false
  }
}

function resetState() {
  studentData.value = null
  teacherDashboard.value = null
  teacherPending.value = null
  teacherPapers.value = null
  teacherPapersPartial.value = false
  adminOverview.value = null
  adminStats.value = null
  adminCourses.value = null
  notifications.value = []
  courseLoading.value = true
  courseError.value = ''
  queueLoading.value = true
  queueError.value = ''
  feedLoading.value = true
  feedError.value = ''
}

async function loadStudentHome(seq: number) {
  const [dashResult] = await Promise.allSettled([loadStudentDashboard(), fetchFeed()])
  if (seq !== loadSeq) return

  const data = dashResult.status === 'fulfilled' ? dashResult.value : null
  studentData.value = isDict(data) ? data : null
  courseLoading.value = false
  queueLoading.value = false
  if (!studentData.value) {
    courseError.value = '课程数据加载失败，请重试'
    queueError.value = '待办数据加载失败，请重试'
  } else if (!studentTodos.value) {
    queueError.value = '待办数据暂不可用，请重试'
  }
}

async function loadTeacherHome(seq: number) {
  const [dashResult, pendingResult] = await Promise.allSettled([
    teacherApi.getTeacherDashboardV2(),
    taskApi.getPendingGrading(),
    fetchFeed(),
  ])
  if (seq !== loadSeq) return

  let dashboard: Dict | null = null
  if (dashResult.status === 'fulfilled') {
    const data = unwrap<Dict>(dashResult.value)
    // 后端在异常时返回 error + 全 0 兜底字段，这类响应不能当作真实读数。
    if (isDict(data) && !data.error) dashboard = data
  }
  teacherDashboard.value = dashboard
  courseLoading.value = false
  if (!dashboard) courseError.value = '课程数据加载失败，请重试'

  if (pendingResult.status === 'fulfilled') {
    teacherPending.value = pickList<Dict>(unwrap(pendingResult.value)).filter(isDict)
  } else {
    teacherPending.value = null
    queueError.value = '待批阅加载失败，请重试'
  }
  queueLoading.value = false

  if (!dashboard) {
    teacherPapers.value = null
    return
  }
  const courseIds = teacherCourseCards.value
    .map((card) => num(card.courseId))
    .filter((id): id is number => id !== null)
  if (!courseIds.length) {
    teacherPapers.value = []
    return
  }
  const results = await Promise.allSettled(
    courseIds.map((courseId) => examApi.getAllExamPapersByCourse(courseId)),
  )
  if (seq !== loadSeq) return
  const papers: Dict[] = []
  let failures = 0
  results.forEach((result) => {
    if (result.status === 'rejected') {
      failures++
      return
    }
    const rows = Array.isArray(result.value) ? result.value : []
    rows.filter(isDict).forEach((row) => papers.push(row))
  })
  teacherPapersPartial.value = failures > 0
  teacherPapers.value = failures === results.length ? null : papers
}

async function loadAdminHome(seq: number) {
  const [overviewResult, coursesResult] = await Promise.allSettled([
    loadAdminOverview(),
    courseApi.getAllCourses(),
    fetchFeed(),
  ])
  if (seq !== loadSeq) return

  if (overviewResult.status === 'fulfilled') {
    const payload = overviewResult.value
    const overview = isDict(payload.overview) ? payload.overview : {}
    const dashboard = isDict(payload.dashboard) ? payload.dashboard : {}
    if (Object.keys(overview).length || Object.keys(dashboard).length) {
      adminOverview.value = overview
      adminStats.value = dashboard
    }
  }

  if (coursesResult.status === 'fulfilled' && Array.isArray(coursesResult.value)) {
    adminCourses.value = coursesResult.value.filter(isDict)
  } else {
    adminCourses.value = null
    courseError.value = '课程列表加载失败，请重试'
    queueError.value = '待审批课程加载失败，请重试'
  }
  courseLoading.value = false
  queueLoading.value = false
}

async function loadHome() {
  const seq = ++loadSeq
  resetState()
  if (role.value === 'teacher') await loadTeacherHome(seq)
  else if (role.value === 'admin') await loadAdminHome(seq)
  else await loadStudentHome(seq)
}

onMounted(loadHome)
</script>
