<template>
  <div>
    <div v-if="loadError" class="callout danger" data-testid="overview-error">
      <span>加载失败</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
    </div>
    <div v-else-if="empty" class="callout" data-testid="overview-empty">
      <span>数据可用性</span>
      <p>暂无数据：{{ emptyHint }}</p>
    </div>
    <div v-else-if="loading" class="empty-state compact" data-testid="overview-loading">
      <b>正在加载总览…</b>
    </div>

    <div v-if="role === 'admin'" class="callout" data-testid="overview-admin-readonly">
      <span>READONLY / 管理员只读</span>
      <p>管理员只读，无数据面：本页仅展示课程档案，不含教学班、选课名单与待批阅读数。</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>
    <div class="course-overview-grid">
      <section v-if="role === 'student'" class="card course-continue">
        <h2>{{ firstChapter || '课程章节' }}</h2>
        <p>{{ continueHint }}</p>
        <div class="course-progress-row">
          <span>已发布章节</span><b>{{ chapterCountText }}</b>
        </div>
        <button class="btn btn-primary" @click="goTab('chapters')">
          继续学习 <Icon name="arrow" />
        </button>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <h2>教学班概览</h2>
          </div>
        </header>
        <div class="empty-state compact">
          <b>教学班数据暂不可用</b>
          <p>{{ classesHint }}</p>
        </div>
      </section>

      <section v-if="role !== 'admin'" class="card">
        <header class="card-head">
          <div>
            <h2>{{ role === 'student' ? '待办与考试' : '待批阅' }}</h2>
          </div>
          <button class="text-btn" @click="goTab(role === 'student' ? 'tasks' : 'grading')">
            全部 <Icon name="arrow" />
          </button>
        </header>
        <div class="empty-state compact">
          <b>{{ queueTitle }}</b>
          <p>{{ queueHint }}</p>
        </div>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <h2>课程信息</h2>
          </div>
        </header>
        <div class="mini-defs">
          <div>
            <span>学期</span><b>{{ course?.semester || '--' }}</b>
          </div>
          <div>
            <span>课程编号</span><b>{{ course?.code || '--' }}</b>
          </div>
          <div>
            <span>学分</span><b>{{ course?.credits ?? '--' }}</b>
          </div>
          <div>
            <span>审批状态</span><b>{{ approvalText }}</b>
          </div>
        </div>
        <button
          v-if="role !== 'student'"
          class="btn btn-ghost btn-block"
          @click="goTab(role === 'teacher' ? 'settings' : 'approval')"
        >
          {{ role === 'teacher' ? '课程设置' : '查看审批记录' }}
        </button>
        <button v-else class="btn btn-ghost btn-block" @click="goTab('tasks')">查看学习任务</button>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <h2>课程内容</h2>
          </div>
        </header>
        <div class="quick-links">
          <button v-for="link in quickLinks" :key="link.tab" @click="goTab(link.tab)">
            {{ link.label }}
          </button>
        </div>
      </section>
    </div>
    <p class="hint">{{ note }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import chapterApi from '@/api/chapter'
import teacherApi from '@/api/teacher'
import { pickList, unwrap } from '@/utils/response'
import Stat from '../../../components/Stat.vue'
import Icon from '../../../components/Icon.vue'
import type { Metric, Role } from '../../../nav'
import { approvalLabel } from '../../../utils'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  status?: string
  approvalStatus?: string
  teacher?: string
}

interface TeacherSummary {
  students: number | null
  pending: number | null
  progress: number | null
}

const props = defineProps<{
  courseId: number
  role: Role
  course: CourseLike | null
}>()

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const empty = ref(false)
const summary = ref<TeacherSummary | null>(null)
const chapterCount = ref<number | null>(null)
const firstChapter = ref('')
/** 课程切换时丢弃先发后至的旧响应，避免把上一门课读数落到当前页 */
let loadToken = 0

function toNumberOrNull(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

/** 教师课程汇总失败必须可见：getCourses 会吞掉异常，这里直接读原接口 */
async function fetchTeacherSummary(): Promise<TeacherSummary | null> {
  const payload = unwrap<unknown>(await teacherApi.getMyCourseSummaries())
  const rows = (Array.isArray(payload) ? payload : pickList(payload)) as Array<
    Record<string, unknown>
  >
  const row = rows.find((item) => Number(item.courseId ?? item.id) === props.courseId)
  if (!row) return null
  return {
    students: toNumberOrNull(row.studentCount ?? row.students),
    pending: toNumberOrNull(row.pendingGrading ?? row.pending),
    progress: toNumberOrNull(row.avgProgress ?? row.progress ?? row.completionRate),
  }
}

async function fetchChapters(): Promise<Array<Record<string, unknown>>> {
  const payload = unwrap(await chapterApi.getChapters(props.courseId))
  return pickList(payload) as Array<Record<string, unknown>>
}

const chapterCountText = computed(() =>
  chapterCount.value == null ? '--' : `${chapterCount.value} 节`,
)

const approvalText = computed(() =>
  props.course?.approvalStatus ? approvalLabel(props.course.approvalStatus) : '--',
)

const classesHint = computed(() =>
  props.role === 'admin' ? '本页暂不汇总班级读数。' : '本页暂不汇总班级、人数与进度。',
)

const continueHint = computed(() => {
  if (loading.value) return '正在读取章节…'
  if (loadError.value) return '章节数据暂不可用，请稍后重试。'
  if (firstChapter.value) return '从「章节与资料」继续学习；学习记录与完成度以章节页为准。'
  return '该课程暂无已发布章节；教师发布后会显示在这里。'
})

const queueTitle = computed(() => {
  if (props.role === 'student') return '明细见作业与考试页签'
  if (summary.value?.pending != null) return `待批阅 ${summary.value.pending} 份`
  return '待批阅读数暂不可用'
})

const queueHint = computed(() => {
  if (props.role === 'student') {
    return '作业、考试与讨论按真实数据在对应页签展示，本页不展示样例队列。'
  }
  if (summary.value?.pending != null) {
    return '明细在「批阅」页签按真实提交展示，本页不展示样例名单。'
  }
  return '待批阅按课程汇总；明细在「批阅」页签查看。'
})

const metrics = computed<Metric[]>(() => {
  if (props.role === 'teacher') {
    return [
      {
        label: '学生',
        value: summary.value?.students ?? '--',
        unit: summary.value?.students == null ? '' : '人',
        note: '按课程汇总',
        icon: 'users',
      },
      {
        label: '章节',
        value: chapterCount.value ?? '--',
        unit: chapterCount.value == null ? '' : '个',
        note: '按章节统计',
        icon: 'book',
      },
      {
        label: '待批阅',
        value: summary.value?.pending ?? '--',
        unit: summary.value?.pending == null ? '' : '份',
        note: '需教师确认',
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '平均进度',
        value: summary.value?.progress ?? '--',
        unit: summary.value?.progress == null ? '' : '%',
        note: '按选课进度平均',
        icon: 'chart',
        tone: 'ok',
      },
    ]
  }
  if (props.role === 'admin') {
    return [
      { label: '教学班', value: '--', note: '管理员无数据面', icon: 'users' },
      { label: '学生', value: '--', note: '管理员无数据面', icon: 'users' },
      { label: '审批', value: approvalText.value, icon: 'check', tone: 'ok' },
      {
        label: '学分',
        value: props.course?.credits ?? '--',
        unit: props.course?.credits == null ? '' : '分',
        icon: 'award',
      },
    ]
  }
  return [
    {
      label: '章节',
      value: chapterCount.value ?? '--',
      unit: chapterCount.value == null ? '' : '个',
      note: '按章节统计',
      icon: 'book',
    },
    {
      label: '学分',
      value: props.course?.credits ?? '--',
      unit: props.course?.credits == null ? '' : '分',
      note: '课程学分',
      icon: 'award',
    },
    { label: '教学班', value: '--', note: '暂无统计', icon: 'users' },
    { label: '待办', value: '--', note: '见作业与考试页签', icon: 'check', tone: 'warn' },
  ]
})

const quickLinks = computed<Array<{ tab: string; label: string }>>(() => {
  if (props.role === 'admin') {
    return [
      { tab: 'content', label: '课程内容' },
      { tab: 'bank', label: '题库' },
      { tab: 'exams', label: '考试' },
      { tab: 'grades', label: '成绩' },
      { tab: 'analytics', label: '学情' },
      { tab: 'graph', label: '知识图谱' },
    ]
  }
  if (props.role === 'student') {
    return [
      { tab: 'chapters', label: '章节与资料' },
      { tab: 'tasks', label: '作业' },
      { tab: 'exams', label: '考试' },
      { tab: 'grades', label: '成绩' },
      { tab: 'discussion', label: '讨论' },
      { tab: 'graph', label: '知识图谱' },
    ]
  }
  return [
    { tab: 'chapters', label: '章节与资源' },
    { tab: 'tasks', label: '作业' },
    { tab: 'exams', label: '考试' },
    { tab: 'analytics', label: '学情' },
    { tab: 'graph', label: '知识图谱' },
    { tab: 'ai', label: 'AI 助手' },
  ]
})

const note = computed(() => {
  if (props.role === 'admin') {
    return '管理员只读，不代教师批阅、不改成绩。'
  }
  if (props.role === 'student') {
    return '本页按课程汇总展示；教学班读数请到教学班页面查看。'
  }
  return '学生数、待批阅与平均进度为课程汇总口径。'
})

const emptyHint = computed(() =>
  props.role === 'student'
    ? '该课程暂无已发布章节，暂无可展示的课程数据。'
    : '该课程暂无课程汇总与章节数据。',
)

function goTab(tab: string) {
  router.push(`/${props.role}/courses/${props.courseId}/${tab}`)
}

async function load() {
  const token = ++loadToken
  loading.value = false
  loadError.value = ''
  empty.value = false
  summary.value = null
  chapterCount.value = null
  firstChapter.value = ''
  // 管理员课程空间没有课程级数据面，只展示课程档案
  if (props.role === 'admin') return
  if (!props.courseId) {
    empty.value = true
    return
  }

  loading.value = true
  const [summaryResult, chapterResult] = await Promise.allSettled([
    props.role === 'teacher' ? fetchTeacherSummary() : Promise.resolve(null),
    fetchChapters(),
  ])
  if (token !== loadToken) return
  loading.value = false

  if (summaryResult.status === 'rejected' || chapterResult.status === 'rejected') {
    loadError.value = '总览暂不可用：课程汇总或章节读数加载失败。'
    return
  }

  summary.value = summaryResult.status === 'fulfilled' ? summaryResult.value : null
  const chapters = chapterResult.status === 'fulfilled' ? chapterResult.value : []
  chapterCount.value = chapters.length
  firstChapter.value = String(chapters[0]?.title || '')
  const summaryMissing = props.role === 'teacher' && !summary.value
  empty.value = summaryMissing && chapters.length === 0
}

watch(() => [props.courseId, props.role] as const, load, { immediate: true })
</script>
