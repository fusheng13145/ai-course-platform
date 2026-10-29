<template>
  <!-- 学生全局任务页：跨课程聚合待办 / 已完成作业（GET /student/assignments/{pending,completed}） -->
  <section class="tasks-body">
    <div class="tasks-toolbar">
      <p class="tasks-note">跨课程聚合待办与已完成作业；点击带课程的任务行进入该课程的任务页。</p>
      <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>

    <div v-if="notices.length" class="callout" role="status">
      <span>INFO / 数据</span>
      <p v-for="notice in notices" :key="notice">{{ notice }}</p>
    </div>

    <section class="card table-card pending-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">TASKS / PENDING</p>
          <h2>
            待完成 <span class="chip chip-warn">{{ pendingRows.length }} 项</span>
          </h2>
        </div>
      </header>

      <div v-if="pendingError" class="callout danger" role="alert">
        <span>ERROR / 待完成</span>
        <p>{{ pendingError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="loading && !loaded" class="empty-state compact">
        <b>正在加载待办任务…</b>
      </div>
      <div v-else-if="!pendingRows.length" class="empty-state compact">
        <span class="e-ico"><V2Icon name="check" /></span>
        <b>暂无待办任务</b>
        <p>教师发布的新作业会出现在这里。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-220">课程</th>
              <th>任务</th>
              <th class="w-220">截止时间</th>
              <th class="w-140">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in pendingRows"
              :key="row.key"
              :class="{ 'row-link': row.courseId != null }"
              :tabindex="row.courseId != null ? 0 : undefined"
              :aria-label="row.courseId != null ? `打开课程任务：${row.title}` : undefined"
              @click="openRow(row)"
              @keydown.enter="openRow(row)"
            >
              <td class="t-course">{{ row.courseName }}</td>
              <td>
                <b class="t-title">{{ row.title }}</b>
              </td>
              <td>{{ row.dueDate }}</td>
              <td>
                <span class="chip" :class="row.chip">{{ row.statusLabel }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card table-card completed-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">TASKS / COMPLETED</p>
          <h2>
            已完成 <span class="chip chip-ok">{{ completedRows.length }} 项</span>
          </h2>
        </div>
      </header>

      <div v-if="completedError" class="callout danger" role="alert">
        <span>ERROR / 已完成</span>
        <p>{{ completedError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="loading && !loaded" class="empty-state compact">
        <b>正在加载已完成作业…</b>
      </div>
      <div v-else-if="!completedRows.length" class="empty-state compact">
        <span class="e-ico"><V2Icon name="award" /></span>
        <b>暂无已完成作业</b>
        <p>提交或批改后的作业会出现在这里。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-220">课程</th>
              <th>任务</th>
              <th class="w-220">截止时间</th>
              <th class="w-140">状态</th>
              <th class="w-220">提交时间</th>
              <th class="w-140">得分</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in completedRows"
              :key="row.key"
              :class="{ 'row-link': row.courseId != null }"
              :tabindex="row.courseId != null ? 0 : undefined"
              :aria-label="row.courseId != null ? `打开课程任务：${row.title}` : undefined"
              @click="openRow(row)"
              @keydown.enter="openRow(row)"
            >
              <td class="t-course">{{ row.courseName }}</td>
              <td>
                <b class="t-title">{{ row.title }}</b>
              </td>
              <td>{{ row.dueDate }}</td>
              <td>
                <span class="chip" :class="row.chip">{{ row.statusLabel }}</span>
              </td>
              <td>{{ row.submittedAt }}</td>
              <td>{{ row.score }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import V2Icon from '../../components/V2Icon.vue'
import V2Stat from '../../components/V2Stat.vue'
import { useV2Ui } from '../../composables/ui'
import type { V2Metric } from '../../nav'
import studentApi from '@/api/student'
import taskApi from '@/api/task'
import { pickList, unwrap } from '@/utils/response'
import logger from '@/utils/logger'

type RawRow = Record<string, unknown>

interface TaskRow {
  key: string
  courseId: number | null
  courseName: string
  title: string
  dueDate: string
  dueRaw: string
  statusLabel: string
  chip: string
  score: string
  submittedAt: string
}

const router = useRouter()
const { role } = useV2Ui()

const pendingRows = ref<TaskRow[]>([])
const completedRows = ref<TaskRow[]>([])
const pendingError = ref('')
const completedError = ref('')
const notices = ref<string[]>([])
const courseNames = ref<Record<string, string>>({})
const loading = ref(false)
const loaded = ref(false)

const COMPLETED_STATUS: Record<string, { label: string; chip: string }> = {
  SUBMITTED: { label: '已提交待批改', chip: 'chip-info' },
  RESUBMITTED: { label: '重新提交待批改', chip: 'chip-info' },
  GRADED: { label: '已批改', chip: 'chip-ok' },
  RETURNED: { label: '已退回待重交', chip: 'chip-danger' },
}

const courseCount = computed(() => {
  const ids = new Set<number>()
  for (const row of [...pendingRows.value, ...completedRows.value]) {
    if (row.courseId != null) ids.add(row.courseId)
  }
  return ids.size
})

const metrics = computed<V2Metric[]>(() => [
  {
    label: '待完成',
    value: pendingRows.value.length,
    unit: '项',
    note: '跨课程待办',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '已完成',
    value: completedRows.value.length,
    unit: '项',
    note: '含待批改与已批改',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '覆盖课程',
    value: courseCount.value,
    unit: '门',
    note: '有任务记录',
    icon: 'book',
  },
])

function isRecord(value: unknown): value is RawRow {
  return Boolean(value) && typeof value === 'object'
}

function asRecords(payload: unknown): RawRow[] {
  const rows = Array.isArray(payload) ? payload : pickList(payload)
  return rows.filter(isRecord)
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

function isOverdue(value: unknown): boolean {
  const text = String(value ?? '')
  if (!text) return false
  const time = new Date(text.replace(' ', 'T')).getTime()
  return Number.isFinite(time) && time < Date.now()
}

function courseNameOf(courseId: number | null): string {
  if (courseId == null) return '—'
  return courseNames.value[String(courseId)] || `课程 #${courseId}`
}

function buildCourseNames(payload: unknown): Record<string, string> {
  const map: Record<string, string> = {}
  for (const row of asRecords(payload)) {
    const id = idOrNull(row.id ?? row.courseId)
    if (id == null) continue
    map[String(id)] = String(row.name ?? row.courseName ?? `课程 #${id}`)
  }
  return map
}

function toPendingRow(raw: RawRow, index: number): TaskRow {
  const taskId = idOrNull(raw.id)
  const courseId = idOrNull(raw.courseId)
  const dueRaw = String(raw.dueDate ?? raw.deadline ?? '')
  const overdue = isOverdue(dueRaw)
  return {
    key: `pending-${taskId ?? index}`,
    courseId,
    courseName: courseNameOf(courseId),
    title: String(raw.title ?? (taskId != null ? `任务 #${taskId}` : '任务')),
    dueDate: fmtTime(dueRaw),
    dueRaw,
    statusLabel: overdue ? '逾期' : '待完成',
    chip: overdue ? 'chip-danger' : 'chip-warn',
    score: '—',
    submittedAt: '—',
  }
}

/** 同一任务多次提交只保留最新一次（优先 attemptNumber，其次 submittedAt）。 */
function isNewerSubmission(candidate: RawRow, current: RawRow): boolean {
  const attempt = Number(candidate.attemptNumber ?? 0) || 0
  const currentAttempt = Number(current.attemptNumber ?? 0) || 0
  if (attempt !== currentAttempt) return attempt > currentAttempt
  return String(candidate.submittedAt ?? '') >= String(current.submittedAt ?? '')
}

function latestSubmissionPerTask(rows: RawRow[]): RawRow[] {
  const byTask = new Map<number, RawRow>()
  for (const row of rows) {
    const taskId = idOrNull(row.taskId)
    if (taskId == null) continue
    const current = byTask.get(taskId)
    if (!current || isNewerSubmission(row, current)) byTask.set(taskId, row)
  }
  return [...byTask.values()]
}

async function loadTaskMap(courseIds: number[]): Promise<Record<string, RawRow>> {
  if (!courseIds.length) return {}
  const results = await Promise.allSettled(
    courseIds.map((courseId) => taskApi.getAllTasks({ courseId })),
  )
  const map: Record<string, RawRow> = {}
  let failed = 0
  for (const result of results) {
    if (result.status === 'rejected') {
      failed += 1
      continue
    }
    for (const task of asRecords(result.value)) {
      const id = idOrNull(task.id)
      if (id != null) map[String(id)] = task
    }
  }
  if (failed) {
    notices.value.push('部分课程的任务详情未能加载，已完成作业可能只显示任务编号。')
  }
  return map
}

function toCompletedRow(sub: RawRow, tasks: Record<string, RawRow>, index: number): TaskRow {
  const taskId = idOrNull(sub.taskId)
  const task = taskId == null ? undefined : tasks[String(taskId)]
  const courseId = task ? idOrNull(task.courseId) : null
  const status = String(sub.status ?? '').toUpperCase()
  const meta = COMPLETED_STATUS[status] || { label: status || '已完成', chip: 'chip-mute' }
  const score = numberOrNull(sub.score)
  const maxScore = numberOrNull(sub.maxScore) ?? numberOrNull(task?.maxScore)
  return {
    key: `completed-${sub.id ?? index}`,
    courseId,
    courseName: courseNameOf(courseId),
    title: String(task?.title ?? (taskId != null ? `任务 #${taskId}` : '任务')),
    dueDate: fmtTime(task?.dueDate ?? task?.deadline),
    dueRaw: String(task?.dueDate ?? task?.deadline ?? ''),
    statusLabel: meta.label,
    chip: meta.chip,
    score: score == null ? '—' : maxScore == null ? String(score) : `${score} / ${maxScore}`,
    submittedAt: fmtTime(sub.submittedAt),
  }
}

function openRow(row: TaskRow) {
  if (row.courseId == null) return
  router.push(`/v2/student/courses/${row.courseId}/tasks`)
}

async function load() {
  loading.value = true
  pendingError.value = ''
  completedError.value = ''
  notices.value = []

  const [pendingRes, completedRes, courseRes] = await Promise.allSettled([
    studentApi.getPendingAssignments(),
    studentApi.getCompletedAssignments(),
    studentApi.getAllMyCourses(),
  ])

  if (courseRes.status === 'fulfilled') {
    courseNames.value = buildCourseNames(unwrap(courseRes.value))
  } else {
    logger.warn('任务页课程名称加载失败')
    courseNames.value = {}
    notices.value.push('课程名称未能加载，暂以「课程 #编号」显示。')
  }

  const courseIds = new Set<number>()
  for (const key of Object.keys(courseNames.value)) courseIds.add(Number(key))

  if (pendingRes.status === 'fulfilled') {
    const rows = asRecords(unwrap(pendingRes.value))
    for (const row of rows) {
      const id = idOrNull(row.courseId)
      if (id != null) courseIds.add(id)
    }
    pendingRows.value = rows
      .map(toPendingRow)
      .sort((a, b) => String(a.dueRaw || '9999').localeCompare(String(b.dueRaw || '9999')))
  } else {
    logger.error('待办任务加载失败', pendingRes.reason)
    pendingRows.value = []
    pendingError.value = '待办任务加载失败，请检查网络后重试'
  }

  if (completedRes.status === 'fulfilled') {
    const submissions = latestSubmissionPerTask(asRecords(unwrap(completedRes.value)))
    if (submissions.length) {
      const tasks = await loadTaskMap([...courseIds])
      completedRows.value = submissions
        .map((sub, index) => toCompletedRow(sub, tasks, index))
        .sort((a, b) => String(b.submittedAt).localeCompare(String(a.submittedAt)))
    } else {
      completedRows.value = []
    }
  } else {
    logger.error('已完成作业加载失败', completedRes.reason)
    completedRows.value = []
    completedError.value = '已完成作业加载失败，请检查网络后重试'
  }

  loading.value = false
  loaded.value = true
}

onMounted(load)
</script>

<style scoped>
.tasks-body {
  display: grid;
  gap: 14px;
}
.tasks-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tasks-note {
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
.row-link {
  cursor: pointer;
}
.row-link:hover {
  background: var(--surface-2);
}
.row-link:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
</style>
