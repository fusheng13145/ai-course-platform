<template>
  <div class="report-panel">
    <div class="report-toolbar">
      <div class="toolbar-actions">
        <button
          class="btn btn-ghost btn-sm"
          :disabled="!transcriptCourses.length"
          @click="printTranscript"
        >
          打印成绩单
        </button>
        <button
          class="btn btn-ghost btn-sm"
          :disabled="exporting || !hasData"
          @click="exportReport"
        >
          {{ exporting ? '导出中…' : '导出学情报告' }}
        </button>
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </div>

    <div v-if="error" class="callout danger" role="alert">
      <span>学情报告</span>
      <p>{{ error }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
    </div>

    <div v-else-if="loading && !hasData" class="empty-state compact">
      <b>正在生成学情报告…</b>
    </div>

    <div v-else-if="!hasData" class="empty-state compact">
      <b>暂无学习数据</b>
      <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
    </div>

    <template v-else>
      <section class="card report-summary">
        <header class="card-head">
          <div>
            <h2>学情概览</h2>
          </div>
          <span class="chip chip-mute">截至 {{ today }}</span>
        </header>

        <div class="stat-grid kpi-grid">
          <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
        </div>

        <div class="report-meta" aria-label="学生基本信息">
          <div>
            <span>姓名</span><b>{{ profileName }}</b>
          </div>
          <div>
            <span>学号</span><b>{{ profile.studentId || '—' }}</b>
          </div>
          <div>
            <span>学校</span><b>{{ profile.schoolName || '—' }}</b>
          </div>
          <div>
            <span>学院</span><b>{{ collegeText }}</b>
          </div>
          <div>
            <span>专业</span><b>{{ majorText }}</b>
          </div>
          <div>
            <span>年级</span><b>{{ profile.grade || '—' }}</b>
          </div>
        </div>
      </section>

      <section v-if="courseRows.length" class="card table-card">
        <header class="card-head">
          <div>
            <h2>课程成绩</h2>
          </div>
          <span class="chip chip-mute">{{ courseRows.length }} 门</span>
        </header>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th class="w-140">课程代码</th>
                <th>课程名称</th>
                <th class="w-140">学期</th>
                <th class="w-100">学分</th>
                <th class="w-120">总评</th>
                <th class="w-120">进度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in courseRows" :key="row.key">
                <td class="t-code">{{ row.code || '—' }}</td>
                <td>
                  <b class="t-title">{{ row.name }}</b>
                </td>
                <td>{{ row.semester || '—' }}</td>
                <td>{{ creditsText(row.credits) }}</td>
                <td>
                  <span class="chip" :class="row.finalScore == null ? 'chip-warn' : 'chip-ok'">
                    {{ scoreText(row.finalScore) }}
                  </span>
                </td>
                <td>{{ progressText(row.progress) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="report-columns">
        <section class="card">
          <header class="card-head">
            <div>
              <h2>近 14 天学习投入</h2>
            </div>
            <span class="chip chip-mute">{{ studyMinutes }} 分钟 · 活跃 {{ activeDays }} 天</span>
          </header>
          <div v-if="trendBars.length" class="trend-chart" role="img" aria-label="近 14 天学习时长">
            <div v-for="bar in trendBars" :key="bar.key" class="trend-col" :title="bar.label">
              <i :class="{ 'is-empty': !bar.seconds }" :style="{ height: `${bar.height}%` }" />
              <small>{{ bar.date }}</small>
            </div>
          </div>
          <div v-else class="empty-state compact">
            <b>暂无学习记录</b>
          </div>
        </section>

        <section class="card">
          <header class="card-head">
            <div>
              <h2>薄弱课程</h2>
            </div>
            <span class="chip chip-mute">{{ weakCourses.length }} 门</span>
          </header>
          <ul v-if="weakCourses.length" class="weak-list">
            <li v-for="row in weakCourses" :key="row.dimension">
              <b>{{ row.dimension }}</b>
              <span>{{ row.weakRate }}%</span>
              <div class="weak-bar">
                <i :style="{ width: `${row.weakRate}%` }" />
              </div>
            </li>
          </ul>
          <div v-else class="empty-state compact">
            <b>暂无薄弱课程数据</b>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Stat from '../../../components/Stat.vue'
import type { Metric, Role } from '../../../nav'
import authApi from '@/api/auth'
import studentApi from '@/api/student'
import { unwrap } from '@/utils/response'
import { formatDate } from '@/utils/datetime'
import { openTranscript, type TranscriptCourse, type TranscriptProfile } from '@/utils/transcript'
import { useUi } from '../../../composables/ui'
import logger from '@/utils/logger'

interface Profile extends TranscriptProfile {
  role?: string
}

interface MyCourse {
  id: number | null
  name: string
  code: string
  credits: number | null
  semester: string
}

interface GradeCourse {
  courseId: number | null
  courseName: string
  courseCode: string
  credits: number | null
  semester: string
  finalScore: number | null
  progress: number | null
}

interface ReportCourse extends MyCourse {
  key: string
  finalScore: number | null
  progress: number | null
}

interface TrendPoint {
  date: string
  seconds: number
}

interface RadarPoint {
  dimension: string
  weakRate: number
}

type RawRow = Record<string, unknown>

const role: Role = 'student'
const { toast } = useUi()

const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const profile = ref<Profile>({})
const myCourses = ref<MyCourse[]>([])
const gradeCourses = ref<GradeCourse[]>([])
const trend = ref<TrendPoint[]>([])
const radar = ref<RadarPoint[]>([])

const today = computed(() => formatDate(new Date()))
const profileName = computed(() => profile.value.fullName || profile.value.username || '—')
const collegeText = computed(() => profile.value.collegeName || profile.value.department || '—')
const majorText = computed(() => profile.value.majorName || profile.value.major || '—')

const courseRows = computed<ReportCourse[]>(() => {
  const map = new Map<string, ReportCourse>()
  for (const course of myCourses.value) {
    if (course.id == null) continue
    map.set(String(course.id), {
      ...course,
      key: String(course.id),
      finalScore: null,
      progress: null,
    })
  }
  for (const grade of gradeCourses.value) {
    if (grade.courseId == null) continue
    const key = String(grade.courseId)
    const existing = map.get(key)
    if (existing) {
      existing.finalScore = grade.finalScore
      existing.progress = grade.progress
      if (!existing.name) existing.name = grade.courseName
      if (!existing.code) existing.code = grade.courseCode
      if (existing.credits == null) existing.credits = grade.credits
      if (!existing.semester) existing.semester = grade.semester
      continue
    }
    map.set(key, {
      key,
      id: grade.courseId,
      name: grade.courseName,
      code: grade.courseCode,
      credits: grade.credits,
      semester: grade.semester,
      finalScore: grade.finalScore,
      progress: grade.progress,
    })
  }
  return [...map.values()]
})

const totalCredits = computed(() =>
  courseRows.value.reduce(
    (sum, course) => sum + (Number.isFinite(Number(course.credits)) ? Number(course.credits) : 0),
    0,
  ),
)

const averageScore = computed(() => {
  const scored = courseRows.value.filter((course) => Number.isFinite(Number(course.finalScore)))
  if (!scored.length) return null
  return scored.reduce((sum, course) => sum + Number(course.finalScore), 0) / scored.length
})

const studyMinutes = computed(() =>
  Math.round(trend.value.reduce((sum, point) => sum + Math.max(0, point.seconds), 0) / 60),
)
const activeDays = computed(() => trend.value.filter((point) => point.seconds > 0).length)

const trendBars = computed(() => {
  const maxSeconds = Math.max(1, ...trend.value.map((point) => Math.max(0, point.seconds)))
  return trend.value.map((point, index) => {
    const seconds = Math.max(0, point.seconds)
    return {
      key: point.date || String(index),
      date: point.date ? point.date.slice(5) : '—',
      seconds,
      height: seconds > 0 ? Math.max(4, Math.round((seconds / maxSeconds) * 100)) : 2,
      label: `${point.date || '—'} ${Math.round(seconds / 60)} 分钟`,
    }
  })
})

const weakCourses = computed(() =>
  [...radar.value]
    .sort((a, b) => b.weakRate - a.weakRate)
    .slice(0, 5)
    .map((row) => ({
      ...row,
      weakRate: Math.min(100, Math.max(0, Math.round((row.weakRate || 0) * 10) / 10)),
    })),
)

const metrics = computed<Metric[]>(() => [
  { label: '课程', value: courseRows.value.length, unit: '门', note: '已选课程', icon: 'book' },
  {
    label: '总学分',
    value: trimNumber(totalCredits.value),
    note: '课程学分合计',
    icon: 'award',
  },
  {
    label: '平均总评',
    value: averageScore.value == null ? '—' : trimNumber(averageScore.value),
    unit: averageScore.value == null ? '' : '分',
    note: '已出分课程',
    icon: 'chart',
    tone: averageScore.value == null ? undefined : 'ok',
  },
  {
    label: '近 14 天学习',
    value: studyMinutes.value,
    unit: '分钟',
    note: `活跃 ${activeDays.value}/14 天`,
    icon: 'clock',
  },
])

const transcriptCourses = computed<TranscriptCourse[]>(() =>
  courseRows.value.map((course) => ({
    code: course.code || '',
    name: course.name,
    credits: course.credits,
    semester: course.semester,
    score: course.finalScore,
    remark: course.finalScore == null ? '修读中' : '已出分',
  })),
)

const hasData = computed(
  () => courseRows.value.length > 0 || trend.value.length > 0 || weakCourses.value.length > 0,
)

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

function trimNumber(value: number): string {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

function scoreText(value: number | null): string {
  return value == null || !Number.isFinite(Number(value)) ? '待出分' : trimNumber(Number(value))
}

function creditsText(value: number | null): string {
  return value == null || !Number.isFinite(Number(value)) ? '—' : trimNumber(Number(value))
}

function progressText(value: number | null): string {
  return value == null || !Number.isFinite(Number(value)) ? '—' : `${Math.round(Number(value))}%`
}

function readGradeCourses(payload: unknown): GradeCourse[] {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return []
  const rows = (payload as { courses?: unknown }).courses
  if (!Array.isArray(rows)) return []
  return rows.filter(isRecord).map((raw) => ({
    courseId: idOrNull(raw.courseId ?? raw.id),
    courseName: String(raw.courseName ?? raw.name ?? '未命名课程'),
    courseCode: String(raw.courseCode ?? raw.code ?? ''),
    credits: numberOrNull(raw.credits),
    semester: String(raw.semester ?? ''),
    finalScore: numberOrNull(raw.finalScore),
    progress: numberOrNull(raw.progress),
  }))
}

function readMyCourses(payload: unknown): MyCourse[] {
  if (!Array.isArray(payload)) return []
  return payload.filter(isRecord).map((raw) => ({
    id: idOrNull(raw.id ?? raw.courseId),
    name: String(raw.name ?? raw.title ?? '未命名课程'),
    code: String(raw.code ?? ''),
    credits: numberOrNull(raw.credits),
    semester: String(raw.semester ?? ''),
  }))
}

function readTrend(payload: unknown): TrendPoint[] {
  if (!payload || typeof payload !== 'object') return []
  const rows = (payload as { durationTrend?: unknown }).durationTrend
  if (!Array.isArray(rows)) return []
  return rows
    .filter(isRecord)
    .map((raw) => ({
      date: String(raw.date ?? ''),
      seconds: Math.max(0, Number(raw.seconds) || 0),
    }))
    .filter((point) => point.date)
}

function readRadar(payload: unknown): RadarPoint[] {
  if (!payload || typeof payload !== 'object') return []
  const rows = (payload as { radarData?: unknown }).radarData
  if (!Array.isArray(rows)) return []
  return rows
    .filter(isRecord)
    .map((raw) => ({
      dimension: String(raw.dimension ?? '未命名课程'),
      weakRate: Math.max(0, Number(raw.weakRate) || 0),
    }))
    .filter((point) => point.dimension)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, gradesResponse, coursesPayload, visualizationResponse] =
      await Promise.all([
        authApi.getCurrentUser().catch(() => null),
        studentApi.getGrades(),
        studentApi.getAllMyCourses().catch(() => []),
        studentApi.getVisualizationData().catch(() => null),
      ])

    const profileBody = unwrap<Profile>(profileResponse)
    profile.value = profileBody && typeof profileBody === 'object' ? profileBody : {}
    gradeCourses.value = readGradeCourses(unwrap(gradesResponse))
    myCourses.value = readMyCourses(coursesPayload)
    const visualizationBody = unwrap<unknown>(visualizationResponse)
    trend.value = readTrend(visualizationBody)
    radar.value = readRadar(visualizationBody)
  } catch (reason) {
    logger.error('加载学情报告失败', reason)
    error.value = '学情报告加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function printTranscript() {
  if (!transcriptCourses.value.length) {
    toast('暂无可打印的课程成绩', 'danger')
    return
  }
  if (!openTranscript(profile.value, transcriptCourses.value, { issuedAt: new Date() })) {
    toast('打印窗口被浏览器拦截，请允许弹窗后重试', 'danger')
  }
}

async function exportReport() {
  if (exporting.value || !hasData.value) return
  exporting.value = true
  try {
    const { downloadWorkbook } = await import('@/utils/xlsxExport')
    const overviewRows: Record<string, unknown>[] = [
      { 项目: '姓名', 值: profileName.value },
      { 项目: '学号', 值: profile.value.studentId || '—' },
      { 项目: '学校', 值: profile.value.schoolName || '—' },
      { 项目: '学院', 值: collegeText.value },
      { 项目: '专业', 值: majorText.value },
      { 项目: '年级', 值: profile.value.grade || '—' },
      { 项目: '课程数', 值: courseRows.value.length },
      { 项目: '总学分', 值: trimNumber(totalCredits.value) },
      { 项目: '平均总评', 值: averageScore.value == null ? '—' : trimNumber(averageScore.value) },
      { 项目: '近14天学习分钟', 值: studyMinutes.value },
      { 项目: '活跃天数', 值: activeDays.value },
      { 项目: '生成日期', 值: today.value },
    ]
    const courseReportRows = courseRows.value.map((course) => ({
      课程代码: course.code || '—',
      课程名称: course.name,
      学期: course.semester || '—',
      学分: creditsText(course.credits),
      总评: scoreText(course.finalScore),
      学习进度: progressText(course.progress),
    }))
    const weakReportRows = weakCourses.value.map((course) => ({
      课程: course.dimension,
      薄弱率: `${course.weakRate}%`,
    }))

    downloadWorkbook(
      [
        { name: '概览', rows: overviewRows },
        { name: '课程成绩', rows: courseReportRows },
        { name: '薄弱课程', rows: weakReportRows },
      ],
      `学情报告-${formatDate(new Date())}.xlsx`,
    )
    toast('学情报告已导出', 'ok')
  } catch (reason) {
    logger.error('导出学情报告失败', reason)
    toast('导出失败，请稍后重试', 'danger')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.report-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}
.report-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.report-summary {
  display: grid;
  gap: 12px;
}
.report-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px 18px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.report-meta > div {
  display: flex;
  gap: 8px;
  min-width: 0;
  font-size: 11px;
}
.report-meta span {
  flex: none;
  color: var(--ink-3);
}
.report-meta b {
  overflow: hidden;
  color: var(--ink-1);
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 132px;
  padding-top: 10px;
}
.trend-col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  min-width: 0;
}
.trend-col i {
  display: block;
  width: 100%;
  min-height: 2px;
  border-radius: var(--r-bar);
  background: var(--accent);
}
.trend-col i.is-empty {
  background: var(--surface-2);
}
.trend-col small {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 9px;
  white-space: nowrap;
}
.weak-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.weak-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
}
.weak-list b {
  overflow: hidden;
  font-size: 11px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.weak-list span {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 10px;
}
.weak-bar {
  grid-column: 1 / -1;
  height: 6px;
  overflow: hidden;
  border-radius: var(--r-chip);
  background: var(--surface-2);
}
.weak-bar i {
  display: block;
  height: 100%;
  background: var(--danger);
}
.t-code {
  color: var(--ink-3);
  font-family: var(--font-mono);
}
.t-title {
  display: block;
  max-width: 360px;
  overflow: hidden;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 860px) {
  .report-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
