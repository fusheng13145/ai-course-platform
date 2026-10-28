<template>
  <section class="module">
    <V2PageHead :kicker="kicker" :title="def.title" :sub="sub" :source="source">
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
            <p class="card-kicker">{{ demo.courses.kicker }}</p>
            <h2>{{ demo.courses.title }}</h2>
          </div>
          <button class="text-btn" @click="goCourses">全部 <V2Icon name="arrow" /></button>
        </header>
        <div class="course-list">
          <button
            v-for="course in courses"
            :key="course.id"
            class="course-row"
            @click="openCourse(course.id, 'overview')"
          >
            <span class="course-cover" :data-tone="course.tone">{{ course.short }}</span>
            <span class="course-info">
              <span class="course-name">{{ course.name }}</span>
              <span class="course-meta">{{
                [course.code, course.teacher].filter(Boolean).join(' · ') || '课程信息'
              }}</span>
              <span class="course-bar"><i :style="{ '--p': `${course.progress}%` }"></i></span>
            </span>
            <span class="course-progress">{{ course.progress }}<small>%</small></span>
          </button>
          <p v-if="!courses.length" class="card-hint">暂无课程数据。</p>
        </div>
      </section>

      <section class="card card-queue">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ queueKicker }}</p>
            <h2>{{ demo.queue.title }}</h2>
          </div>
          <button class="text-btn" @click="goQueue">
            {{ role === 'admin' ? '进入审批' : '查看全部' }}
            <V2Icon name="arrow" />
          </button>
        </header>
        <ul class="queue-list">
          <li v-for="(item, index) in queueItems" :key="index" class="queue-item">
            <label v-if="role === 'student'" class="todo-check">
              <input
                v-model="done[index]"
                type="checkbox"
                :aria-label="`标记完成：${item.title}`"
              />
              <span class="todo-box"></span>
            </label>
            <span v-else-if="role === 'teacher'" class="queue-avatar">{{
              String(item.title).slice(0, 1)
            }}</span>
            <span v-else class="risk-dot" :class="`tone-${item.tone || 'warn'}`"></span>
            <span class="queue-main">
              <b>{{ item.title }}</b>
              <small>{{ item.meta }}</small>
            </span>
            <span class="chip" :class="`chip-${item.tone || 'warn'}`">{{ item.chip }}</span>
          </li>
        </ul>
        <div class="queue-next">
          <span>{{
            role === 'student' ? '下一场' : role === 'teacher' ? '进行中' : '待处理'
          }}</span>
          <b>{{ queueFoot }}</b>
          <span class="chip chip-info">{{ role === 'admin' ? '治理队列' : '示例' }}</span>
        </div>
        <div class="card-foot">
          <span>{{ queueNote }}</span>
          <span class="mono">{{
            role === 'teacher' ? 'TEACHER' : role === 'admin' ? 'ADMIN' : 'STUDENT'
          }}</span>
        </div>
      </section>

      <section class="card card-chart">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ demo.chart.kicker }}</p>
            <h2>{{ demo.chart.title }}</h2>
          </div>
          <span class="chart-unit">{{ demo.chart.unit }}</span>
        </header>
        <p class="card-sub">{{ demo.chart.sub }}</p>
        <div class="bar-chart" role="img" :aria-label="chartLabel">
          <div v-for="(value, index) in demo.chart.values" :key="index" class="bar-col">
            <span class="bar-val">{{ value }}</span>
            <i class="bar" :style="{ '--v': `${Math.round((value / chartMax) * 100)}%` }"></i>
            <span>{{ demo.chart.labels[index] }}</span>
          </div>
        </div>
        <div class="card-foot">
          <span v-for="text in demo.chart.footer" :key="text">{{ text }}</span>
        </div>
      </section>

      <section class="card card-ai">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ demo.ai.kicker }}</p>
            <h2>{{ demo.ai.title }}</h2>
          </div>
          <span class="ai-mini"><V2Icon name="sparkle" /></span>
        </header>
        <div class="ai-panel">
          <p class="ai-quote">“{{ demo.ai.quote }}”</p>
          <div class="ai-tags">
            <span v-for="tag in demo.ai.tags" :key="tag">{{ tag }}</span>
          </div>
          <button class="btn btn-primary btn-block" @click="aiAction">
            {{ demo.ai.action }} <V2Icon name="arrow" />
          </button>
          <p class="ai-caveat">{{ demo.ai.caveat }}</p>
        </div>
      </section>

      <section class="card card-weak">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ demo.weak.kicker }}</p>
            <h2>{{ demo.weak.title }}</h2>
          </div>
          <V2Icon name="chart" />
        </header>
        <ul class="weak-list">
          <li
            v-for="item in demo.weak.items"
            :key="item.label"
            class="weak-item"
            :class="`tone-${item.tone}`"
          >
            <span>{{ item.label }}</span>
            <b>{{ item.value }}</b>
            <span class="weak-bar"><i :style="{ '--p': `${item.pct}%` }"></i></span>
          </li>
        </ul>
        <p class="card-hint">
          {{ role === 'admin' ? '风险等级按治理维度聚合' : '掌握度低于 60% 自动标红' }}
        </p>
      </section>

      <section class="card card-feed">
        <header class="card-head">
          <div>
            <p class="card-kicker">{{ demo.feed.kicker }}</p>
            <h2>{{ demo.feed.title }}</h2>
          </div>
          <span class="live-tag"><i></i>LIVE</span>
        </header>
        <ul class="feed-list">
          <li v-for="item in feed" :key="item.title" class="feed-item">
            <span class="feed-dot" :class="`tone-${item.tone}`"></span>
            <span class="feed-main">
              <b>{{ item.title }}</b>
              <small>{{ item.meta }}</small>
            </span>
            <time>{{ item.time }}</time>
          </li>
        </ul>
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
import { DB } from '../data/demoSource'
import { pageDef, type V2Metric, type V2Role } from '../nav'
import { useV2Ui } from '../composables/ui'
import { getCourses, loadAdminOverview, loadStudentDashboard } from '../data/api'
import type { V2CourseCard } from '../data/api'

const router = useRouter()
const { role, displayName, toast } = useV2Ui()

interface DemoRole {
  stats: V2Metric[]
  dashboard: {
    courses: { kicker: string; title: string }
    queue: {
      kicker: string
      title: string
      items: Array<{ title: string; meta: string; chip: string; tone: string }>
    }
    chart: {
      kicker: string
      title: string
      unit: string
      sub: string
      values: number[]
      labels: string[]
      footer: string[]
    }
    ai: {
      kicker: string
      title: string
      quote: string
      tags: string[]
      action: string
      caveat: string
    }
    weak: {
      kicker: string
      title: string
      items: Array<{ label: string; value: string; pct: number; tone: string }>
    }
    feed: {
      kicker: string
      title: string
      items: Array<{ title: string; meta: string; time: string; tone: string }>
    }
  }
}

const demo = computed(() => {
  const roleDemo = (DB.roles as unknown as Record<V2Role, DemoRole>)[role.value]
  return { ...roleDemo, ...roleDemo.dashboard } as DemoRole & DemoRole['dashboard']
})
const def = computed(() => pageDef(role.value as V2Role, 'home'))

const courses = ref<V2CourseCard[]>([])
const realStats = ref<V2Metric[] | null>(null)
const done = ref<boolean[]>([])
const source = ref<'api' | 'demo' | 'mixed'>('demo')

const roleKicker = computed(
  () =>
    ({
      student: 'STUDENT / 计算机学院 · 2026 秋',
      teacher: 'TEACHER / 计算机学院 · 2026 秋',
      admin: 'ADMIN / 校级治理 · 2026 秋',
    })[role.value],
)
const kicker = computed(() => roleKicker.value)
const sub = computed(() => {
  if (role.value === 'student') {
    const courseCount = courses.value.length || demo.value.stats[0]?.value || 0
    return `${displayName.value}，这里是你今天的课程进度与待办。${courseCount} 门课程。`
  }
  if (role.value === 'teacher') {
    return `${displayName.value}，课程、待批阅与班级风险的单一入口。`
  }
  return '平台规模、审批队列与运行风险；院系作用域需后端支持，本版按校级展示。'
})

const stats = computed<V2Metric[]>(() =>
  realStats.value?.length ? realStats.value : demo.value.stats,
)

const queueItems = computed(() => demo.value.queue.items)
const feed = computed(() => demo.value.feed.items)
const queueKicker = computed(() => {
  const realCount = Number(realStats.value?.[2]?.value)
  if (role.value === 'teacher') {
    return `TO REVIEW / ${Number.isFinite(realCount) ? realCount : '—'}`
  }
  if (role.value === 'student') {
    return `TODAY / ${Number.isFinite(realCount) ? realCount : '—'} ITEMS`
  }
  return demo.value.queue.kicker
})
const queueFoot = computed(() => {
  if (role.value === 'student') return '计算机网络 · 期中测验'
  if (role.value === 'teacher') return '期中测验 · 58 人在线'
  return '审批与风险待处理'
})
const queueNote = computed(() =>
  role.value === 'student'
    ? '示例队列：真实待办已接入统计，列表待接入'
    : role.value === 'teacher'
      ? 'AI 建议需教师确认'
      : '审批与风险待处理',
)
const chartMax = computed(() => Math.max(...demo.value.chart.values, 1))
const chartLabel = computed(
  () => `${demo.value.chart.title}：${demo.value.chart.values.join('，')}`,
)

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

function goCourses() {
  router.push(`/v2/${role.value}/courses`)
}

function openCourse(id: number, tab: string) {
  router.push(`/v2/${role.value}/courses/${id}/${tab}`)
}

function goQueue() {
  if (role.value === 'student') router.push('/v2/student/tasks')
  else if (role.value === 'teacher') router.push('/v2/teacher/inbox')
  else router.push('/v2/admin/approvals')
}

function primaryAction() {
  if (role.value === 'student') {
    const id = courses.value[0]?.id
    if (id) openCourse(id, 'chapters')
    else goCourses()
    return
  }
  if (role.value === 'teacher') {
    const id = courses.value[0]?.id
    if (id) openCourse(id, 'grading')
    else toast('暂无课程可批阅', 'warn')
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
  secondaryAction()
}

onMounted(async () => {
  courses.value = await getCourses(role.value as V2Role)
  if (role.value === 'student') {
    const data = (await loadStudentDashboard()) || {}
    const todos = (data.todos || {}) as { assignments?: unknown[]; exams?: unknown[] }
    const summary = (data.summary || {}) as { courseCount?: number; unreadCount?: number }
    const avg = courses.value.length
      ? Math.round(
          courses.value.reduce((sum, course) => sum + course.progress, 0) / courses.value.length,
        )
      : 0
    realStats.value = [
      {
        label: '在修课程',
        value: summary.courseCount ?? courses.value.length,
        unit: '门',
        note: '本学期',
        icon: 'book',
      },
      {
        label: '平均进度',
        value: avg,
        unit: '%',
        note: '按课程进度平均',
        icon: 'chart',
        tone: 'ok',
      },
      {
        label: '待办任务',
        value: (todos.assignments?.length || 0) + (todos.exams?.length || 0),
        unit: '项',
        note: `${todos.assignments?.length || 0} 作业 · ${todos.exams?.length || 0} 考试`,
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '未读通知',
        value: summary.unreadCount ?? 0,
        unit: '条',
        note: '消息中心',
        icon: 'bell',
      },
    ]
    source.value = 'mixed'
    return
  }
  if (role.value === 'teacher') {
    const totalStudents = courses.value.reduce((sum, course) => sum + course.students, 0)
    const pending = courses.value.reduce((sum, course) => sum + course.pending, 0)
    const avgProgress = courses.value.length
      ? Math.round(
          courses.value.reduce((sum, course) => sum + course.progress, 0) / courses.value.length,
        )
      : 0
    realStats.value = [
      {
        label: '授课',
        value: courses.value.length,
        unit: '门',
        note: '本人负责',
        icon: 'book',
      },
      { label: '学生', value: totalStudents, unit: '人', note: '选课人次', icon: 'users' },
      {
        label: '待批阅',
        value: pending,
        unit: '份',
        note: '需教师确认',
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '平均进度',
        value: avgProgress,
        unit: '%',
        note: '按课程进度平均',
        icon: 'chart',
        tone: 'ok',
      },
    ]
    source.value = 'mixed'
    return
  }
  const { overview } = await loadAdminOverview()
  const userCount = Number(overview.totalUsers ?? 0)
  const courseCount = Number(overview.totalCourses ?? 0)
  if (userCount || courseCount) {
    realStats.value = [
      { label: '用户', value: userCount, unit: '人', note: '平台总额', icon: 'users' },
      {
        label: '课程',
        value: courseCount,
        unit: '门',
        note: `${overview.activeCourses ?? 0} 门活跃`,
        icon: 'book',
      },
      {
        label: '选课',
        value: Number(overview.totalEnrollments ?? 0),
        unit: '人次',
        note: '有效选课',
        icon: 'check',
        tone: 'ok',
      },
      { label: '学院', value: 3, unit: '个', note: '组织层级', icon: 'building', tone: 'info' },
    ]
    source.value = 'mixed'
  }
})
</script>
