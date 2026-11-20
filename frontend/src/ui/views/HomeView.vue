<template>
  <section class="module">
    <PageHead :title="def.title">
      <template #actions>
        <button class="btn btn-ghost" @click="secondaryAction">{{ secondary.label }}</button>
        <button class="btn btn-primary" @click="primaryAction">
          {{ primary.label }}
          <Icon name="arrow" />
        </button>
      </template>
    </PageHead>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in stats" :key="metric.label" :metric="metric" :role="role" />
    </div>

    <div class="dash-grid">
      <section class="card card-courses">
        <header class="card-head">
          <div>
            <h2>{{ courseTitle }}</h2>
          </div>
          <button class="text-btn" @click="goCourses">全部 <Icon name="arrow" /></button>
        </header>

        <div v-if="courseLoading" class="empty-state compact">
          <b>正在加载课程…</b>
        </div>
        <div v-else-if="courseError" class="callout danger" role="alert">
          <span>课程</span>
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
              {{ course.progress === null ? '--' : `${course.progress}%` }}
            </span>
          </button>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无课程</b>
        </div>
      </section>

      <section class="card card-queue">
        <header class="card-head">
          <div>
            <h2>{{ queueTitle }}</h2>
          </div>
          <button class="text-btn" @click="goQueue">
            {{ role === 'admin' ? '进入审批' : '查看全部' }}
            <Icon name="arrow" />
          </button>
        </header>

        <div v-if="queueLoading" class="empty-state compact">
          <b>正在加载待办…</b>
        </div>
        <div v-else-if="queueError" class="callout danger" role="alert">
          <span>待办</span>
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
        </template>
        <div v-else class="empty-state compact">
          <b>{{ queueEmptyTitle }}</b>
        </div>

        <div class="card-foot">
          <span class="mono">{{ queueCountLabel }}</span>
          <span v-if="queueTruncated">{{ queueTruncated }}</span>
        </div>
      </section>

      <section class="card card-chart">
        <header class="card-head">
          <div>
            <h2>{{ chartTitle }}</h2>
          </div>
          <span class="chart-unit">{{ chartUnit }}</span>
        </header>

        <div v-if="courseLoading" class="empty-state compact"><b>正在加载图表…</b></div>
        <div v-else-if="courseError" class="callout danger" role="alert">
          <span>图表</span>
          <p>{{ courseError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <div v-else-if="chartHasData" class="bar-chart" role="img" :aria-label="chartLabel">
          <div v-for="bar in chartBars" :key="bar.label" class="bar-col">
            <span class="bar-val">{{ bar.display }}</span>
            <span class="bar-track">
              <i class="bar" :style="{ '--v': `${Math.round((bar.value / chartMax) * 100)}%` }"></i>
            </span>
            <span class="bar-axis">{{ bar.label }}</span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无数据</b>
        </div>
      </section>

      <section class="card card-weak">
        <header class="card-head">
          <div>
            <h2>{{ weakTitle }}</h2>
          </div>
          <button
            v-if="role === 'teacher'"
            class="text-btn"
            @click="go('/teacher/advising/alerts')"
          >
            全部预警 <Icon name="arrow" />
          </button>
          <Icon v-else name="chart" />
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
        <div v-else-if="courseError" class="callout danger" role="alert">
          <span>风险学生</span>
          <p>{{ courseError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无记录</b>
        </div>
      </section>

      <section v-if="showAiCard" class="card card-ai">
        <header class="card-head">
          <div>
            <h2>{{ aiTitle }}</h2>
          </div>
          <button class="text-btn" @click="aiAction">
            {{ aiActionLabel }} <Icon name="arrow" />
          </button>
        </header>
        <p class="ai-quote">{{ aiSummary }}</p>
      </section>

      <section class="card card-feed">
        <header class="card-head">
          <div>
            <h2>最近通知</h2>
          </div>
          <span class="chip" :class="feedChipClass">{{ feedChip }}</span>
        </header>

        <div v-if="feedLoading" class="empty-state compact"><b>正在加载通知…</b></div>
        <div v-else-if="feedError" class="callout danger" role="alert">
          <span>通知</span>
          <p>{{ feedError }}</p>
          <button class="btn btn-sm" @click="loadHome">重试</button>
        </div>
        <ul v-else-if="feedRows.length" class="feed-list">
          <li v-for="item in feedRows" :key="item.key" class="feed-item">
            <span class="feed-dot" :class="`tone-${item.tone}`"></span>
            <span class="feed-main">
              <b>{{ item.title }}</b>
            </span>
            <time>{{ item.time }}</time>
          </li>
        </ul>
        <div v-else class="empty-state compact">
          <b>暂无通知</b>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '../components/PageHead.vue'
import Stat from '../components/Stat.vue'
import Icon from '../components/Icon.vue'
import { pageDef, type Metric, type Role } from '../nav'
import { useUi } from '../composables/ui'
import {
  loadAdminOverview,
  loadNotifications,
  loadStudentDashboard,
  type Notification,
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
  time: string
  tone: string
}

const router = useRouter()
const { role } = useUi()
const def = computed(() => pageDef(role.value as Role, 'home'))

const studentData = ref<Dict | null>(null)
const teacherDashboard = ref<Dict | null>(null)
const teacherAiOverview = ref<Dict | null>(null)
const teacherPending = ref<Dict[] | null>(null)
const teacherPapers = ref<Dict[] | null>(null)
const teacherPapersPartial = ref(false)
const adminOverview = ref<Dict | null>(null)
const adminStats = ref<Dict | null>(null)
const adminCourses = ref<Dict[] | null>(null)
const notifications = ref<Notification[]>([])

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

const unavailable = (label: string, icon: string): Metric => ({
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
    return id === null ? '/student/courses' : `/student/courses/${id}/${tab}`
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
        to: courseId === null ? null : `/teacher/courses/${courseId}/grading`,
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
        to: id ? `/admin/courses/${id}/overview` : null,
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

const studentMetrics = computed<Metric[]>(() => {
  const data = studentData.value
  const summary = studentSummary.value
  const lists = studentTodoLists.value
  const courseCount = data ? (num(summary.courseCount) ?? studentCourses.value.length) : null
  const progress = data ? studentProgressAvg.value : null
  const todoCount =
    data && studentTodos.value
      ? lists.assignments.length + lists.exams.length + lists.signIns.length
      : null
  return [
    courseCount === null
      ? unavailable('已选课程', 'book')
      : { label: '已选课程', value: courseCount, unit: '门', icon: 'book' },
    progress === null
      ? unavailable('学习进度', 'chart')
      : {
          label: '学习进度',
          value: progress,
          unit: '%',
          icon: 'chart',
          tone: 'ok',
        },
    todoCount === null
      ? unavailable('待办事项', 'check')
      : {
          label: '待办事项',
          value: todoCount,
          unit: '项',
          icon: 'check',
          tone: 'warn',
        },
  ]
})

const teacherMetrics = computed<Metric[]>(() => {
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
      : { label: '我的课程', value: courseCount, unit: '门', icon: 'book' },
    pending === null
      ? unavailable('待批阅任务', 'check')
      : {
          label: '待批阅任务',
          value: pending,
          unit: '项',
          icon: 'check',
          tone: 'warn',
        },
    upcoming === null
      ? unavailable('近期考试', 'clipboard')
      : {
          label: '近期考试',
          value: upcoming,
          unit: '场',
          note: teacherPapersPartial.value ? '部分课程未加载' : undefined,
          icon: 'clipboard',
          tone: 'info',
        },
    students === null
      ? unavailable('学生', 'users')
      : { label: '学生', value: students, unit: '人', icon: 'users' },
  ]
})

const adminMetrics = computed<Metric[]>(() => {
  const overview = adminOverview.value ?? {}
  const dashboard = adminStats.value ?? {}
  const totalUsers = num(overview.totalUsers) ?? num(dashboard.totalUsers)
  const totalCourses =
    num(overview.totalCourses) ??
    num(dashboard.totalCourses) ??
    (adminCourses.value ? adminCourses.value.length : null)
  const pending = adminCourses.value ? adminPendingCourses.value.length : null
  const activeUsers = num(dashboard.activeUsers)
  return [
    totalUsers === null
      ? unavailable('平台用户', 'users')
      : { label: '平台用户', value: totalUsers, unit: '人', icon: 'users' },
    totalCourses === null
      ? unavailable('课程总数', 'book')
      : {
          label: '课程总数',
          value: totalCourses,
          unit: '门',
          icon: 'book',
        },
    pending === null
      ? unavailable('待审批课程', 'check')
      : {
          label: '待审批课程',
          value: pending,
          unit: '门',
          icon: 'check',
          tone: 'warn',
        },
    activeUsers === null
      ? unavailable('启用用户', 'user')
      : {
          label: '启用用户',
          value: activeUsers,
          unit: '人',
          icon: 'user',
          tone: 'ok',
        },
  ]
})

const stats = computed<Metric[]>(() => {
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
/* 全 0 的柱图等于空白：宁可一行空态，也不摆一排 0% 标签 */
const chartHasData = computed(() => chartBars.value.some((bar) => bar.value > 0))
const chartLabel = computed(
  () =>
    `${chartTitle.value}：${chartBars.value.map((bar) => `${bar.label} ${bar.display}`).join('，')}`,
)

const weakRows = computed<WeakRow[]>(() => {
  if (role.value === 'teacher') {
    const list = teacherDashboard.value?.atRiskStudents
    const rows = Array.isArray(list) ? list.filter(isDict) : []
    // 后端按「学生 × 课程」给记录，同一人同一课可能出现多条；同一对象只留一条
    const seen = new Set<string>()
    return rows
      .filter((row) => {
        const key = `${str(row.studentName)}|${str(row.courseName)}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
      .slice(0, 6)
      .map((row) => {
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
    time: formatDateTime(item.time),
    tone: item.unread ? 'accent' : 'info',
  })),
)

const unreadCount = computed(() => notifications.value.filter((item) => item.unread).length)

const courseTitle = computed(() => (role.value === 'admin' ? '课程目录' : '课程'))

const queueTitle = computed(() => {
  if (role.value === 'teacher') return '待批阅任务'
  if (role.value === 'admin') return '待审批课程'
  return '今日待办'
})
const queueEmptyTitle = computed(() => {
  if (role.value === 'teacher') return '暂无待批阅任务'
  if (role.value === 'admin') return '暂无待审批课程'
  return '暂无待办'
})
const queueCountLabel = computed(() => {
  if (queueLoading.value) return '加载中'
  if (queueError.value) return '暂不可用'
  return `${queueTotal.value} 项`
})

const chartTitle = computed(() => (role.value === 'admin' ? '审批状态分布' : '课程进度'))
const chartUnit = computed(() => (role.value === 'admin' ? '门' : '%'))

/* AI 块只在教师拿到真实总览时出现：没有内容就不占一行，入口在页头次动作 */
const aiTitle = 'AI 教学总览'
const aiSummary = computed(() => {
  const overview = teacherAiOverview.value
  const summary = isDict(overview) ? str(overview.summary).trim() : ''
  // 模型未配置 / 调用失败时后端回的是一句固定说明，不是总览内容：不摆进卡里
  if (summary.startsWith('暂不可用') || summary.startsWith('暂无')) return ''
  return summary
})
const showAiCard = computed(() => role.value === 'teacher' && !!aiSummary.value)
const aiActionLabel = '查看学情分析'
const aiTo = computed(() => {
  const id = courses.value[0]?.id
  return id ? `/teacher/courses/${id}/analytics` : '/teacher/courses'
})

const weakTitle = computed(() => {
  if (role.value === 'teacher') return '风险学生'
  if (role.value === 'admin') return '课程状态'
  return '进度偏低课程'
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
  if (role.value === 'teacher') return { label: '我的课程' }
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
  router.push(`/${role.value}/courses`)
}

function openCourse(id: number) {
  router.push(`/${role.value}/courses/${id}/overview`)
}

function goQueue() {
  if (role.value === 'student') router.push('/student/courses')
  else if (role.value === 'teacher') router.push('/teacher/inbox')
  else router.push('/admin/approvals')
}

function primaryAction() {
  if (role.value === 'student') {
    const id = courses.value[0]?.id
    router.push(id ? `/student/courses/${id}/chapters` : '/student/courses')
    return
  }
  if (role.value === 'teacher') {
    const id = courses.value[0]?.id
    router.push(id ? `/teacher/courses/${id}/grading` : '/teacher/courses')
    return
  }
  router.push('/admin/approvals')
}

function secondaryAction() {
  if (role.value === 'student') {
    const id = courses.value[0]?.id
    router.push(id ? `/student/courses/${id}/ai` : '/student/assistant')
    return
  }
  if (role.value === 'teacher') {
    router.push('/teacher/courses')
    return
  }
  router.push('/admin/teaching-classes')
}

function aiAction() {
  router.push(aiTo.value)
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
  teacherAiOverview.value = null
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
    teacherApi.getTeacherDashboard(),
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
  if (!dashboard) {
    teacherAiOverview.value = null
    courseError.value = '课程数据加载失败，请重试'
  } else {
    // AI 教学总览异步补数：不阻塞主统计首屏
    teacherApi
      .getTeacherDashboardAiOverview()
      .then((res) => {
        if (seq !== loadSeq) return
        const data = unwrap<Dict>(res)
        teacherAiOverview.value = isDict(data) ? data : null
      })
      .catch(() => {
        if (seq === loadSeq) teacherAiOverview.value = null
      })
  }

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
