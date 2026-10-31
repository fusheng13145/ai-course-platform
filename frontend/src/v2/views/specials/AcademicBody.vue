<template>
  <!-- 学生学业档案：三个页签——成绩（GET /student/grades）、学习中心（/learning-records）、
       错题本（/student/wrong-questions）；首页与导航不变，避免动侧栏基线 -->
  <!-- 原注：只保留成绩（GET /student/grades）；错题本 / 学习路径不在本页提供 -->
  <section class="academic-body">
    <div class="tabs" role="tablist" aria-label="学业档案分区">
      <button
        v-for="tab in ACADEMIC_TABS"
        :key="tab.key"
        type="button"
        role="tab"
        :class="{ 'is-active': active === tab.key }"
        :aria-selected="active === tab.key"
        @click="active = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-show="active === 'grades'">
      <div class="academic-toolbar">
        <p class="academic-note">成绩以教师提交批阅为准；AI 建议分不直接写入成绩。</p>
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>

      <div v-if="error" class="callout danger" role="alert">
        <span>ERROR / 成绩</span>
        <p>{{ error }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>

      <div v-else-if="loading && !hasData" class="empty-state compact">
        <b>正在加载成绩…</b>
      </div>

      <!-- 后端 getGrades 聚合异常时也返回 { courses: [] }，与真·空态不可区分；
         这里按“未能获取”呈现并给重试，不向学生断言“暂无成绩”。 -->
      <div v-else-if="!hasData" class="callout" role="status">
        <span>WAIT / 成绩</span>
        <p>成绩数据未能获取（可能暂无成绩或服务暂不可用）。</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>

      <template v-else>
        <div class="stat-grid kpi-grid">
          <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
        </div>

        <template v-if="courses.length">
          <div class="course-picker" role="tablist" aria-label="按课程查看成绩">
            <button
              v-for="course in courses"
              :key="course.key"
              type="button"
              role="tab"
              class="course-chip"
              :class="{ 'is-active': course.key === activeKey }"
              :aria-selected="course.key === activeKey"
              @click="activeKey = course.key"
            >
              <b>{{ course.courseName }}</b>
              <small>{{ finalScoreText(course.finalScore) }}</small>
            </button>
          </div>

          <section v-if="activeCourse" class="card table-card">
            <header class="card-head">
              <div>
                <p class="card-kicker">COURSE / GRADES</p>
                <h2>{{ activeCourse.courseName }}</h2>
              </div>
              <span class="chip" :class="activeCourse.finalScore == null ? 'chip-warn' : 'chip-ok'">
                总评 {{ finalScoreText(activeCourse.finalScore) }}
              </span>
            </header>

            <div v-if="activeCourse.items.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>成绩项</th>
                    <th class="w-140">类型</th>
                    <th class="w-140">得分</th>
                    <th class="w-140">满分 / 卷面</th>
                    <th class="w-140">状态</th>
                    <th class="w-220">提交时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in activeCourse.items" :key="item.key">
                    <td>
                      <b class="t-title">{{ item.title }}</b>
                    </td>
                    <td>
                      <span class="chip chip-info">{{ item.kind }}</span>
                    </td>
                    <td>{{ scoreText(item) }}</td>
                    <td>{{ item.maxScore == null ? '—' : item.maxScore }}</td>
                    <td>
                      <span class="chip" :class="item.chip">{{ item.statusLabel }}</span>
                    </td>
                    <td>{{ item.submittedAt }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state compact">
              <b>本课程暂无成绩项</b>
              <p>作业批改或考试出分后会显示在这里。</p>
            </div>
          </section>
        </template>

        <section v-else class="card table-card">
          <header class="card-head">
            <div>
              <p class="card-kicker">GRADES / LIST</p>
              <h2>成绩明细</h2>
            </div>
            <span class="chip chip-mute">{{ flatRows.length }} 项</span>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th class="w-220">课程</th>
                  <th>成绩项</th>
                  <th class="w-140">类型</th>
                  <th class="w-140">得分</th>
                  <th class="w-140">满分 / 卷面</th>
                  <th class="w-140">状态</th>
                  <th class="w-220">提交时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in flatRows" :key="row.key">
                  <td class="t-course">{{ row.courseName }}</td>
                  <td>
                    <b class="t-title">{{ row.title }}</b>
                  </td>
                  <td>
                    <span class="chip chip-info">{{ row.kind }}</span>
                  </td>
                  <td>{{ scoreText(row) }}</td>
                  <td>{{ row.maxScore == null ? '—' : row.maxScore }}</td>
                  <td>
                    <span class="chip" :class="row.chip">{{ row.statusLabel }}</span>
                  </td>
                  <td>{{ row.submittedAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <p v-if="loaded && !loading && !error" class="export-note">
        「导出成绩」待扩展：后端学生成绩导出端点尚未落地，本页不提供该动作。
      </p>
    </div>

    <LearningCenterPanel v-if="active === 'learning'" />
    <WrongBookPanel v-if="active === 'wrong'" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import V2Stat from '../../components/V2Stat.vue'
import LearningCenterPanel from './academic/LearningCenterPanel.vue'
import WrongBookPanel from './academic/WrongBookPanel.vue'

const ACADEMIC_TABS = [
  { key: 'grades', label: '成绩' },
  { key: 'learning', label: '学习中心' },
  { key: 'wrong', label: '错题本' },
] as const
const active = ref<(typeof ACADEMIC_TABS)[number]['key']>('grades')
import { useV2Ui } from '../../composables/ui'
import type { V2Metric } from '../../nav'
import studentApi from '@/api/student'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

type RawRow = Record<string, unknown>

interface GradeItem {
  key: string
  title: string
  kind: '作业' | '考试'
  score: number | null
  maxScore: number | null
  percent: boolean
  statusLabel: string
  chip: string
  submittedAt: string
}

interface CourseGrades {
  key: string
  courseId: number | null
  courseName: string
  finalScore: number | null
  items: GradeItem[]
}

interface FlatGradeRow extends GradeItem {
  courseName: string
}

const { role } = useV2Ui()

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const courses = ref<CourseGrades[]>([])
const flatRows = ref<FlatGradeRow[]>([])
const activeKey = ref('')

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

const hasData = computed(() => courses.value.length > 0 || flatRows.value.length > 0)
const activeCourse = computed(
  () => courses.value.find((course) => course.key === activeKey.value) || courses.value[0] || null,
)
const courseCount = computed(() => {
  if (courses.value.length) return courses.value.length
  return new Set(flatRows.value.map((row) => row.courseName)).size
})
const itemCount = computed(() => {
  if (courses.value.length) {
    return courses.value.reduce((sum, course) => sum + course.items.length, 0)
  }
  return flatRows.value.length
})
const scoredCount = computed(() => {
  if (courses.value.length) {
    return courses.value.reduce(
      (sum, course) => sum + course.items.filter((item) => item.score != null).length,
      0,
    )
  }
  return flatRows.value.filter((row) => row.score != null).length
})

const metrics = computed<V2Metric[]>(() => [
  { label: '课程', value: courseCount.value, unit: '门', note: '有成绩记录', icon: 'book' },
  { label: '成绩项', value: itemCount.value, unit: '项', note: '作业与考试', icon: 'award' },
  {
    label: '已出分',
    value: scoredCount.value,
    unit: '项',
    note: '有分值的成绩项',
    icon: 'chart',
    tone: 'ok',
  },
])

function isRecord(value: unknown): value is RawRow {
  return Boolean(value) && typeof value === 'object'
}

function numberOrNull(value: unknown): number | null {
  if (value == null || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function idOrNull(value: unknown): number | null {
  const num = numberOrNull(value)
  return num == null ? null : num
}

function fmtTime(value: unknown): string {
  const text = String(value ?? '')
  return text ? text.replace('T', ' ').slice(0, 16) : '—'
}

function finalScoreText(value: number | null): string {
  return value == null ? '待出分' : String(value)
}

function scoreText(item: GradeItem): string {
  if (item.score == null) return '—'
  return item.percent ? `${item.score}%` : String(item.score)
}

function buildItem(raw: RawRow, kind: '作业' | '考试', key: string, percent: boolean): GradeItem {
  const status = String(raw.status ?? '').toUpperCase()
  return {
    key,
    title: String(raw.title ?? raw.name ?? (kind === '作业' ? '作业' : '考试')),
    kind,
    score: numberOrNull(raw.score),
    maxScore: numberOrNull(percent ? raw.totalScore : raw.maxScore),
    percent,
    statusLabel: STATUS_LABEL[status] || status || '—',
    chip: STATUS_CHIP[status] || 'chip-mute',
    submittedAt: fmtTime(raw.submittedAt),
  }
}

function readCourseRows(payload: unknown): RawRow[] {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return []
  const rows = (payload as { courses?: unknown }).courses
  return Array.isArray(rows) ? rows.filter(isRecord) : []
}

/** 后端只给扁平行时（裸数组 / list / records / items / grades）仍可展示。 */
function readFlatRows(payload: unknown): RawRow[] {
  if (Array.isArray(payload)) return payload.filter(isRecord)
  if (!payload || typeof payload !== 'object') return []
  const holder = payload as {
    list?: unknown
    records?: unknown
    items?: unknown
    grades?: unknown
  }
  for (const candidate of [holder.list, holder.records, holder.items, holder.grades]) {
    if (Array.isArray(candidate)) return candidate.filter(isRecord)
  }
  return []
}

function toCourse(raw: RawRow, index: number): CourseGrades {
  const courseId = idOrNull(raw.courseId ?? raw.id)
  const assignments = Array.isArray(raw.assignments) ? raw.assignments.filter(isRecord) : []
  const exams = Array.isArray(raw.exams) ? raw.exams.filter(isRecord) : []
  const items: GradeItem[] = []
  assignments.forEach((row, itemIndex) => {
    items.push(buildItem(row, '作业', `a-${row.submissionId ?? row.taskId ?? itemIndex}`, false))
  })
  exams.forEach((row, itemIndex) => {
    items.push(buildItem(row, '考试', `e-${row.sessionId ?? itemIndex}`, true))
  })
  return {
    key: String(courseId ?? `course-${index}`),
    courseId,
    courseName: String(
      raw.courseName ?? raw.name ?? (courseId != null ? `课程 #${courseId}` : '未命名课程'),
    ),
    finalScore: numberOrNull(raw.finalScore),
    items,
  }
}

function toFlatRow(raw: RawRow, index: number): FlatGradeRow {
  const kindText = String(raw.kind ?? raw.type ?? '')
  const isExam = /考试|exam/i.test(kindText)
  const base = buildItem(raw, isExam ? '考试' : '作业', `flat-${index}`, isExam)
  return {
    ...base,
    title: String(
      raw.title ?? raw.name ?? raw.assignmentName ?? raw.examName ?? base.title ?? '成绩项',
    ),
    courseName: String(raw.courseName ?? raw.course ?? raw.courseTitle ?? '—'),
  }
}

function applyPayload(payload: unknown) {
  const courseRows = readCourseRows(payload)
  if (courseRows.length) {
    courses.value = courseRows.map(toCourse)
    flatRows.value = []
    activeKey.value = courses.value[0]?.key ?? ''
    return
  }
  courses.value = []
  flatRows.value = readFlatRows(payload).map(toFlatRow)
  activeKey.value = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const payload = unwrap<unknown>(await studentApi.getGrades())
    applyPayload(payload)
  } catch (reason) {
    logger.error('加载学生成绩失败', reason)
    courses.value = []
    flatRows.value = []
    activeKey.value = ''
    error.value = '成绩数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

onMounted(load)
</script>

<style scoped>
.academic-body {
  display: grid;
  gap: 14px;
}
.academic-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.academic-note {
  margin: 0;
  font-size: 10px;
  line-height: 1.7;
  color: var(--ink-3);
}
.callout.danger {
  border-left-color: var(--danger);
}
.callout.danger > span {
  color: var(--danger);
}
.course-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.course-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface);
  color: var(--ink-2);
  text-align: left;
}
.course-chip b {
  font-size: 11px;
  font-weight: 600;
}
.course-chip small {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--ink-3);
}
.course-chip.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent-strong);
}
.course-chip.is-active small {
  color: var(--accent-strong);
}
.t-course {
  color: var(--ink-2);
}
.t-title {
  display: block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.export-note {
  margin: 0;
  font-size: 9.5px;
  color: var(--ink-3);
}
</style>
