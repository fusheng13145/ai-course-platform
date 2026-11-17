<template>
  <div>
    <div v-if="loadError" class="callout danger" data-testid="overview-error">
      <span>加载失败</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
    </div>
    <div v-else-if="empty" class="callout" data-testid="overview-empty">
      <span>暂无数据</span>
    </div>
    <div v-else-if="loading" class="empty-state compact" data-testid="overview-loading">
      <b>正在加载总览…</b>
    </div>

    <div v-if="role === 'admin'" class="callout" data-testid="overview-admin-readonly">
      <span>管理员只读</span>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>
    <div class="course-overview-grid">
      <section v-if="role === 'student'" class="card course-continue">
        <h2>{{ firstChapter || '课程章节' }}</h2>
        <div class="course-progress-row">
          <span>已发布章节</span><b>{{ chapterCountText }}</b>
        </div>
        <button class="btn btn-primary" @click="goTab('chapters')">
          继续学习 <Icon name="arrow" />
        </button>
      </section>

      <section v-if="role === 'teacher'" class="card">
        <header class="card-head">
          <div>
            <h2>教学班概览</h2>
          </div>
        </header>
        <div class="empty-state compact">
          <b>教学班数据暂不可用</b>
        </div>
      </section>

      <section v-if="role === 'teacher'" class="card">
        <header class="card-head">
          <div>
            <h2>待批阅</h2>
          </div>
          <button class="text-btn" @click="goTab('grading')">全部 <Icon name="arrow" /></button>
        </header>
        <div class="empty-state compact">
          <b>{{ queueTitle }}</b>
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
        </div>
      </section>
    </div>
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

const queueTitle = computed(() => {
  if (summary.value?.pending != null) return `待批阅 ${summary.value.pending} 份`
  return '待批阅读数暂不可用'
})

const metrics = computed<Metric[]>(() => {
  if (props.role === 'teacher') {
    return [
      {
        label: '学生',
        value: summary.value?.students ?? '--',
        unit: summary.value?.students == null ? '' : '人',
        icon: 'users',
      },
      {
        label: '章节',
        value: chapterCount.value ?? '--',
        unit: chapterCount.value == null ? '' : '个',
        icon: 'book',
      },
      {
        label: '待批阅',
        value: summary.value?.pending ?? '--',
        unit: summary.value?.pending == null ? '' : '份',
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '平均进度',
        value: summary.value?.progress ?? '--',
        unit: summary.value?.progress == null ? '' : '%',
        icon: 'chart',
        tone: 'ok',
      },
    ]
  }
  if (props.role === 'admin') {
    return [
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
      icon: 'book',
    },
    {
      label: '学分',
      value: props.course?.credits ?? '--',
      unit: props.course?.credits == null ? '' : '分',
      icon: 'award',
    },
  ]
})

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
