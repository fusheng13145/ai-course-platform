<template>
  <div class="course-overview">
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

    <div class="course-overview-grid" :class="{ 'is-admin': role === 'admin' }">
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
        <div v-if="sections.length" class="section-list">
          <div v-for="row in sections" :key="String(row.id)" class="section-row">
            <b>{{ row.name || `教学班 #${row.id}` }}</b>
            <span class="chip" :class="row.status === 'ACTIVE' ? 'chip-ok' : 'chip-mute'">
              {{ row.status === 'ACTIVE' ? '启用' : '草稿' }}
            </span>
            <span class="section-cap">{{ row.capacity ?? '—' }} 人</span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无教学班</b>
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

      <section
        class="card course-facts-card"
        :class="{
          'overview-span': role === 'teacher' && !descriptionText,
          'is-compact': role === 'teacher' && Boolean(descriptionText),
        }"
      >
        <header class="card-head">
          <div>
            <h2>课程信息</h2>
          </div>
        </header>
        <div class="mini-defs">
          <div v-for="fact in courseFacts" :key="fact.label">
            <span>{{ fact.label }}</span
            ><b>{{ fact.value }}</b>
          </div>
        </div>
      </section>

      <section
        v-if="descriptionText"
        class="card overview-description"
        :class="{ 'overview-span': role !== 'teacher' }"
      >
        <header class="card-head">
          <div>
            <h2>课程简介</h2>
          </div>
        </header>
        <p>{{ descriptionText }}</p>
        <div v-if="role === 'teacher'" class="overview-chapter-inline">
          <div>
            <span>已发布章节</span>
            <b>{{ chapterCountText }}</b>
          </div>
          <div>
            <span>起始章节</span>
            <b>{{ firstChapter || '--' }}</b>
          </div>
        </div>
      </section>

      <section v-if="role === 'teacher' && !descriptionText" class="card overview-span">
        <header class="card-head">
          <div>
            <h2>章节结构</h2>
          </div>
          <button class="text-btn" @click="goTab('chapters')">
            查看章节 <Icon name="arrow" />
          </button>
        </header>
        <div class="overview-chapter-summary">
          <div>
            <span>已发布章节</span>
            <b>{{ chapterCountText }}</b>
          </div>
          <div>
            <span>起始章节</span>
            <b>{{ firstChapter || '--' }}</b>
          </div>
        </div>
      </section>
    </div>

    <section v-if="chapterPreview.length" class="card course-outline">
      <header class="card-head">
        <div>
          <h2>章节导览</h2>
        </div>
        <button class="text-btn" @click="goTab('chapters')">全部章节 <Icon name="arrow" /></button>
      </header>
      <ol class="overview-outline-list">
        <li v-for="(chapter, index) in chapterPreview" :key="chapter.key">
          <NumberBadge :value="index + 1" />
          <span>{{ chapter.title }}</span>
        </li>
      </ol>
      <p v-if="chapterRemaining > 0" class="overview-outline-more">
        另有 {{ chapterRemaining }} 个章节
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import chapterApi from '@/api/chapter'
import teacherApi from '@/api/teacher'
import { getTeachingClasses } from '@/api/teachingClass'
import { pickList, unwrap } from '@/utils/response'
import Stat from '../../../components/Stat.vue'
import Icon from '../../../components/Icon.vue'
import type { Metric, Role } from '../../../nav'
import { approvalLabel } from '../../../utils'
import NumberBadge from '../../../components/NumberBadge.vue'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  status?: string
  approvalStatus?: string
  teacher?: string
  description?: string
}

interface ChapterRow {
  key: string
  title: string
}

interface TeacherSummary {
  students: number | null
  pending: number | null
  progress: number | null
}

interface SectionRow {
  id?: number | string
  name?: string
  status?: string
  capacity?: number | null
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
const chapterRows = ref<ChapterRow[]>([])
const firstChapter = ref('')
const sections = ref<SectionRow[]>([])
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

async function fetchSections(): Promise<void> {
  const payload = unwrap(await getTeachingClasses(props.courseId))
  sections.value = pickList<SectionRow>(payload)
}

const chapterCountText = computed(() =>
  chapterCount.value == null ? '--' : `${chapterCount.value} 节`,
)

const approvalText = computed(() =>
  props.course?.approvalStatus ? approvalLabel(props.course.approvalStatus) : '--',
)

const courseFacts = computed(() => [
  { label: '学期', value: props.course?.semester || '--' },
  { label: '课程编号', value: props.course?.code || '--' },
  { label: '学分', value: props.course?.credits == null ? '--' : String(props.course.credits) },
  { label: '课程性质', value: props.course?.category || '--' },
  { label: '授课教师', value: props.course?.teacher || '--' },
  { label: '状态', value: approvalText.value },
])

const descriptionText = computed(() => String(props.course?.description || '').trim())

const chapterPreview = computed(() => chapterRows.value.slice(0, 8))
const chapterRemaining = computed(() =>
  Math.max(0, chapterRows.value.length - chapterPreview.value.length),
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
      { label: '课程编号', value: props.course?.code || '--', icon: 'book' },
      { label: '学期', value: props.course?.semester || '--', icon: 'calendar' },
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
    { label: '学期', value: props.course?.semester || '--', icon: 'calendar' },
    { label: '课程性质', value: props.course?.category || '--', icon: 'book' },
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
  chapterRows.value = []
  firstChapter.value = ''
  sections.value = []
  // 管理员课程空间没有课程级数据面，只展示课程档案
  if (props.role === 'admin') return
  if (!props.courseId) {
    empty.value = true
    return
  }

  loading.value = true
  const [summaryResult, chapterResult, sectionResult] = await Promise.allSettled([
    props.role === 'teacher' ? fetchTeacherSummary() : Promise.resolve(null),
    fetchChapters(),
    props.role === 'teacher' ? fetchSections() : Promise.resolve(),
  ])
  if (token !== loadToken) return
  loading.value = false

  if (summaryResult.status === 'rejected' || chapterResult.status === 'rejected') {
    loadError.value = '总览暂不可用：课程汇总或章节读数加载失败。'
    return
  }

  summary.value = summaryResult.status === 'fulfilled' ? summaryResult.value : null
  if (sectionResult.status === 'rejected') sections.value = []
  const chapters = chapterResult.status === 'fulfilled' ? chapterResult.value : []
  chapterCount.value = chapters.length
  chapterRows.value = chapters.map((row, index) => {
    const title = String(row.title ?? row.name ?? '').trim()
    return {
      key: String(row.id ?? row.chapterId ?? index),
      title: title || `第 ${index + 1} 章`,
    }
  })
  firstChapter.value = chapterRows.value[0]?.title || ''
  const summaryMissing = props.role === 'teacher' && !summary.value
  empty.value = summaryMissing && chapters.length === 0
}

watch(() => [props.courseId, props.role] as const, load, { immediate: true })
</script>

<style scoped>
.course-overview-grid {
  margin-bottom: 0;
}

.course-overview-grid.is-admin {
  grid-template-columns: minmax(0, 1fr);
}

.course-overview-grid.is-admin .mini-defs {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.overview-span {
  grid-column: 1 / -1;
}

.course-facts-card .mini-defs {
  margin-bottom: 0;
}

.course-facts-card.overview-span .mini-defs {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.course-facts-card.is-compact .mini-defs {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.course-facts-card .mini-defs b {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
  line-height: 1.5;
}

.overview-description p {
  margin: 0;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.75;
}

.overview-chapter-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.overview-chapter-inline {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.overview-chapter-summary > div,
.overview-chapter-inline > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding-top: 8px;
  border-top: 1px solid var(--line-soft);
}

.overview-chapter-inline > div {
  padding-top: 0;
  border-top: 0;
}

.overview-chapter-summary span,
.overview-chapter-inline span {
  color: var(--ink-3);
  font-size: 12px;
}

.overview-chapter-summary b,
.overview-chapter-inline b {
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-outline {
  margin-top: 16px;
}

.overview-outline-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.overview-outline-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 8px 0;
  border-top: 1px solid var(--line-soft);
}

.overview-outline-list li span {
  overflow: hidden;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-outline-more {
  margin: 8px 0 0;
  color: var(--ink-3);
  font-size: 12px;
}

@media (max-width: 768px) {
  .course-overview-grid.is-admin .mini-defs,
  .course-facts-card.overview-span .mini-defs,
  .course-facts-card.is-compact .mini-defs,
  .overview-outline-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

.section-list {
  display: grid;
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  overflow: hidden;
}
.section-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 16px;
  background: var(--surface);
}
.section-row b {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.section-cap {
  font-size: 12px;
  color: var(--ink-3);
}
</style>
