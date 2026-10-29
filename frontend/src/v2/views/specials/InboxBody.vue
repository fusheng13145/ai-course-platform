<template>
  <section ref="root" class="inbox-body">
    <section class="card table-card" data-block="grading">
      <header class="card-head">
        <div>
          <p class="card-kicker">GRADING / PENDING</p>
          <h2>
            待批阅
            <span class="chip" :class="gradingError ? 'chip-danger' : 'chip-mute'">{{
              gradingChip
            }}</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm" :disabled="busy" @click="loadAll">
            {{ busy ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="grading.loading" class="empty-state compact">
        <b>正在加载待批阅…</b>
      </div>
      <div v-else-if="grading.error" class="callout danger" role="alert" data-state="error">
        <span>ERROR / 待批阅</span>
        <p>{{ grading.error }}</p>
        <button class="btn btn-sm" :disabled="busy" @click="loadAll">重试</button>
      </div>
      <div v-else-if="grading.rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>任务</th>
              <th class="w-220">课程</th>
              <th class="w-96">类型</th>
              <th class="w-200">截止</th>
              <th class="w-96">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in grading.rows" :key="`g-${row.id}-${index}`">
              <td>
                <b class="row-title">{{ row.title }}</b>
              </td>
              <td>{{ courseNameOf(row.courseId) }}</td>
              <td>
                <span class="chip chip-mute">{{ row.type }}</span>
              </td>
              <td>{{ row.dueDate }}</td>
              <td>
                <button
                  v-if="row.courseId !== null"
                  class="text-btn"
                  @click="openGrading(row.courseId)"
                >
                  去批阅
                </button>
                <span v-else class="cell-sub">课程未知</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact" data-state="empty">
        <span class="e-ico"><V2Icon name="check" /></span>
        <b>暂无待批阅任务</b>
        <p>学生提交作业后，有待批提交的任务会出现在这里。</p>
      </div>

      <div class="card-foot">
        <span>数据源 GET /tasks/pending-grading</span>
        <span class="mono">{{ grading.rows.length }} TASKS</span>
      </div>
    </section>

    <section class="card table-card" data-block="publish">
      <header class="card-head">
        <div>
          <p class="card-kicker">PUBLISH / DRAFTS</p>
          <h2>
            待发布
            <span class="chip" :class="publishError ? 'chip-danger' : 'chip-mute'">{{
              publishChip
            }}</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm" :disabled="busy" @click="loadAll">
            {{ busy ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="publish.loading" class="empty-state compact">
        <b>正在加载待发布内容…</b>
      </div>
      <div v-else-if="publish.error" class="callout danger" role="alert" data-state="error">
        <span>ERROR / 待发布</span>
        <p>{{ publish.error }}</p>
        <button class="btn btn-sm" :disabled="busy" @click="loadAll">重试</button>
      </div>
      <template v-else>
        <p v-if="publish.warning" class="hint" data-state="partial">{{ publish.warning }}</p>
        <div v-if="publish.rows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>内容</th>
                <th class="w-80">类型</th>
                <th class="w-220">课程</th>
                <th class="w-200">更新时间</th>
                <th class="w-96">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in publish.rows" :key="`p-${row.key}-${index}`">
                <td>
                  <b class="row-title">{{ row.title }}</b>
                </td>
                <td>
                  <span class="chip" :class="row.kind === '试卷' ? 'chip-info' : 'chip-mute'">{{
                    row.kind
                  }}</span>
                </td>
                <td>{{ courseNameOf(row.courseId) }}</td>
                <td>{{ row.time }}</td>
                <td>
                  <button v-if="row.courseId !== null" class="text-btn" @click="openPublish(row)">
                    去发布
                  </button>
                  <span v-else class="cell-sub">课程未知</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state compact" data-state="empty">
          <span class="e-ico"><V2Icon name="file" /></span>
          <b>暂无待发布的作业或试卷</b>
          <p>草稿状态的作业与试卷会出现在这里。</p>
        </div>
      </template>

      <div class="card-foot">
        <span>数据源 GET /teacher/assignments + /exam-papers/course/{id}</span>
        <span class="mono">{{ publish.rows.length }} DRAFTS</span>
      </div>
    </section>

    <section class="card table-card" data-block="proctor">
      <header class="card-head">
        <div>
          <p class="card-kicker">PROCTOR / LIVE</p>
          <h2>
            监考
            <span class="chip" :class="proctorError ? 'chip-danger' : 'chip-mute'">{{
              proctorChip
            }}</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm" :disabled="busy" @click="loadAll">
            {{ busy ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="proctor.loading" class="empty-state compact">
        <b>正在加载进行中考试…</b>
      </div>
      <div v-else-if="proctor.error" class="callout danger" role="alert" data-state="error">
        <span>ERROR / 监考</span>
        <p>{{ proctor.error }}</p>
        <button class="btn btn-sm" :disabled="busy" @click="loadAll">重试</button>
      </div>
      <template v-else>
        <p v-if="proctor.warning" class="hint" data-state="partial">{{ proctor.warning }}</p>
        <div v-if="proctor.rows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>试卷</th>
                <th class="w-200">课程</th>
                <th class="w-140">学生</th>
                <th class="w-200">开始时间</th>
                <th class="w-96">用时</th>
                <th class="w-96">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in proctor.rows" :key="`s-${row.id}-${index}`">
                <td>
                  <b class="row-title">{{ row.paperTitle }}</b>
                </td>
                <td>{{ courseNameOf(row.courseId) }}</td>
                <td>{{ studentNameOf(row.studentId) }}</td>
                <td>{{ row.startTime }}</td>
                <td>{{ row.timeSpent }}</td>
                <td>
                  <button
                    v-if="row.courseId !== null"
                    class="text-btn"
                    @click="openProctor(row.courseId)"
                  >
                    监考
                  </button>
                  <span v-else class="cell-sub">课程未知</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state compact" data-state="empty">
          <span class="e-ico"><V2Icon name="play" /></span>
          <b>当前没有进行中的考试</b>
          <p>学生开考后，会话会出现在这里。</p>
        </div>
      </template>

      <div class="card-foot">
        <span>数据源 GET /exam-sessions/active（仅本人课程会话）</span>
        <span class="mono">{{ proctor.rows.length }} LIVE</span>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import V2Icon from '../../components/V2Icon.vue'
import teacherApi from '@/api/teacher'
import taskApi from '@/api/task'
import examApi from '@/api/exam'
import userApi from '@/api/user'
import { fetchAllPages, pickList, unwrap } from '@/utils/response'

type Dict = Record<string, unknown>

interface CourseRow {
  id: number
  name: string
}

interface GradingRow {
  id: number
  title: string
  courseId: number | null
  type: string
  dueDate: string
}

interface PublishRow {
  key: string
  title: string
  kind: '作业' | '试卷'
  courseId: number | null
  time: string
  ts: number
}

interface ProctorRow {
  id: number
  paperTitle: string
  courseId: number | null
  studentId: number | null
  startTime: string
  timeSpent: string
}

const router = useRouter()
const root = ref<HTMLElement | null>(null)

const courses = ref<CourseRow[]>([])
const studentNames = ref<Map<number, string>>(new Map())

const grading = reactive({ loading: true, error: '', rows: [] as GradingRow[] })
const publish = reactive({ loading: true, error: '', warning: '', rows: [] as PublishRow[] })
const proctor = reactive({ loading: true, error: '', warning: '', rows: [] as ProctorRow[] })

const busy = computed(() => grading.loading || publish.loading || proctor.loading)
const gradingError = computed(() => Boolean(grading.error))
const publishError = computed(() => Boolean(publish.error))
const proctorError = computed(() => Boolean(proctor.error))
const gradingChip = computed(() =>
  grading.loading ? '加载中' : grading.error ? '暂不可用' : `${grading.rows.length} 项`,
)
const publishChip = computed(() =>
  publish.loading ? '加载中' : publish.error ? '暂不可用' : `${publish.rows.length} 项`,
)
const proctorChip = computed(() =>
  proctor.loading ? '加载中' : proctor.error ? '暂不可用' : `${proctor.rows.length} 场`,
)

let loadSeq = 0
let reminderTab: HTMLElement | null = null

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

const formatDateTime = (value: unknown): string => {
  const s = str(value)
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}

const timestampOf = (row: Dict): number => {
  const parsed = Date.parse(str(row.updatedAt ?? row.createdAt))
  return Number.isNaN(parsed) ? 0 : parsed
}

const formatSeconds = (value: unknown): string => {
  const seconds = num(value)
  if (seconds === null) return '—'
  return seconds < 60 ? `${seconds} 秒` : `${Math.round(seconds / 60)} 分`
}

function taskTypeLabel(value: unknown): string {
  const type = str(value).toUpperCase()
  if (type === 'ASSIGNMENT') return '作业'
  if (type === 'EXAM') return '考试'
  if (type === 'SIGN_IN') return '签到'
  return str(value, '任务')
}

function courseNameOf(courseId: unknown): string {
  const id = num(courseId)
  if (id === null) return '课程未知'
  return courses.value.find((course) => course.id === id)?.name || `课程 #${id}`
}

function studentNameOf(studentId: unknown): string {
  const id = num(studentId)
  if (id === null) return '—'
  return studentNames.value.get(id) || `学生 #${id}`
}

function openGrading(courseId: number) {
  router.push(`/v2/teacher/courses/${courseId}/grading`)
}

function openPublish(row: PublishRow) {
  if (row.courseId === null) return
  router.push(`/v2/teacher/courses/${row.courseId}/${row.kind === '试卷' ? 'exams' : 'tasks'}`)
}

function openProctor(courseId: number) {
  router.push(`/v2/teacher/courses/${courseId}/proctor`)
}

async function loadGrading(seq: number) {
  grading.loading = true
  grading.error = ''
  grading.rows = []
  try {
    const payload = unwrap<unknown>(await taskApi.getPendingGrading())
    const rows = pickList<Dict>(payload).filter(isDict)
    if (seq !== loadSeq) return
    grading.rows = rows.map((row) => ({
      id: num(row.id) ?? 0,
      title: str(row.title, '待批阅任务'),
      courseId: num(row.courseId),
      type: taskTypeLabel(row.taskType),
      dueDate: formatDateTime(row.dueDate),
    }))
  } catch {
    if (seq !== loadSeq) return
    grading.rows = []
    grading.error = '待批阅加载失败，请重试。'
  } finally {
    if (seq === loadSeq) grading.loading = false
  }
}

async function loadShared(seq: number) {
  publish.loading = true
  publish.error = ''
  publish.warning = ''
  publish.rows = []
  proctor.loading = true
  proctor.error = ''
  proctor.warning = ''
  proctor.rows = []
  courses.value = []
  studentNames.value = new Map()

  let courseRows: CourseRow[] = []
  try {
    const payload = unwrap<unknown>(await teacherApi.getMyCourseSummaries())
    const rows = Array.isArray(payload) ? payload : pickList<Dict>(payload)
    courseRows = rows.filter(isDict).map((row) => ({
      id: num(row.courseId ?? row.id) ?? 0,
      name: str(row.name ?? row.courseName, '未命名课程'),
    }))
    courses.value = courseRows
  } catch {
    if (seq !== loadSeq) return
    publish.error = '课程列表加载失败，无法确定待发布范围。'
    proctor.error = '课程列表加载失败，无法按课程过滤进行中会话。'
    publish.loading = false
    proctor.loading = false
    return
  }

  const courseIds = courseRows.map((course) => course.id).filter((id) => id > 0)
  const [assignmentResults, paperResults, sessionResult, studentResult] = await Promise.all([
    Promise.allSettled(
      courseIds.map((courseId) =>
        fetchAllPages<Dict>((page, size) => teacherApi.getAssignments({ courseId, page, size })),
      ),
    ),
    Promise.allSettled(courseIds.map((courseId) => examApi.getAllExamPapersByCourse(courseId))),
    Promise.allSettled([examApi.getActiveExams()]),
    Promise.allSettled([userApi.getTeacherStudents()]),
  ])
  if (seq !== loadSeq) return

  const assignments: Dict[] = []
  let assignmentFailures = 0
  assignmentResults.forEach((result) => {
    if (result.status === 'rejected') {
      assignmentFailures++
      return
    }
    const rows = Array.isArray(result.value) ? result.value : []
    rows.filter(isDict).forEach((row) => assignments.push(row))
  })

  const papers: Dict[] = []
  let paperFailures = 0
  paperResults.forEach((result) => {
    if (result.status === 'rejected') {
      paperFailures++
      return
    }
    const rows = Array.isArray(result.value) ? result.value : []
    rows.filter(isDict).forEach((row) => papers.push(row))
  })

  const paperMap = new Map<number, Dict>()
  papers.forEach((paper) => {
    const id = num(paper.id)
    if (id !== null) paperMap.set(id, paper)
  })

  const draftTasks: PublishRow[] = assignments
    .filter((row) => row.isPublished !== true)
    .map((row) => ({
      key: `task-${str(row.id)}`,
      title: str(row.title, '未命名作业'),
      kind: '作业',
      courseId: num(row.courseId),
      time: formatDateTime(row.updatedAt ?? row.createdAt),
      ts: timestampOf(row),
    }))
  const draftPapers: PublishRow[] = papers
    .filter((row) => str(row.status).toUpperCase() === 'DRAFT')
    .map((row) => ({
      key: `paper-${str(row.id)}`,
      title: str(row.title, '未命名试卷'),
      kind: '试卷',
      courseId: num(row.courseId),
      time: formatDateTime(row.updatedAt ?? row.createdAt),
      ts: timestampOf(row),
    }))

  publish.rows = [...draftTasks, ...draftPapers].sort((a, b) => b.ts - a.ts)
  const failedSources = assignmentFailures + paperFailures
  if (!publish.rows.length && failedSources > 0) {
    publish.error = '待发布内容未能完整加载，请重试。'
  } else if (publish.rows.length && failedSources > 0) {
    publish.warning = '部分课程加载失败，待发布列表可能不完整。'
  }
  publish.loading = false

  const sessionOutcome = sessionResult[0]
  if (sessionOutcome.status === 'rejected') {
    proctor.error = '进行中考试加载失败，请重试。'
    proctor.loading = false
    return
  }
  const sessions = pickList<Dict>(unwrap(sessionOutcome.value)).filter(isDict)
  if (sessions.length && courseIds.length && paperFailures === courseIds.length) {
    proctor.error = '试卷信息未能加载，无法按课程过滤进行中会话。'
    proctor.loading = false
    return
  }

  const students: Dict[] = []
  const studentOutcome = studentResult[0]
  if (studentOutcome.status === 'fulfilled') {
    pickList<Dict>(unwrap(studentOutcome.value))
      .filter(isDict)
      .forEach((row) => students.push(row))
  }
  const names = new Map<number, string>()
  students.forEach((row) => {
    const id = num(row.id)
    if (id !== null) names.set(id, str(row.fullName ?? row.username, `学生 #${id}`))
  })
  studentNames.value = names

  const rows: ProctorRow[] = []
  sessions.forEach((session) => {
    const paperId = num(session.examPaperId)
    const paper = paperId === null ? null : paperMap.get(paperId)
    if (!paper) return
    rows.push({
      id: num(session.id) ?? 0,
      paperTitle: str(paper.title, '未命名试卷'),
      courseId: num(paper.courseId),
      studentId: num(session.studentId),
      startTime: formatDateTime(session.startTime),
      timeSpent: formatSeconds(session.timeSpent),
    })
  })
  proctor.rows = rows

  const warnings: string[] = []
  if (paperFailures > 0 && sessions.length) {
    warnings.push('部分课程试卷未加载，监考列表可能不完整。')
  }
  if (studentOutcome.status === 'rejected' && rows.length) {
    warnings.push('学生姓名暂不可用，已用用户 ID 代替。')
  }
  proctor.warning = warnings.join('')
  if (!rows.length && sessions.length && paperFailures > 0) {
    proctor.error = '试卷信息未能完整加载，无法判断会话归属。'
  }
  proctor.loading = false
}

async function loadAll() {
  const seq = ++loadSeq
  await Promise.all([loadGrading(seq), loadShared(seq)])
}

/**
 * V2GlobalPage 仍为 inbox 硬编码四个页签，其中「提醒」本期明确不实现（任务范围外）。
 * 任务边界禁止修改全局页，因此仅在本 Body 挂载时隐藏该页签，卸载时恢复；
 * 待全局页清理这批硬编码页签后删除本段。
 */
function hideRemindersTab() {
  const container = root.value?.closest('.module')
  const tablist = container?.querySelector('.page-tabs')
  if (!tablist) return
  const tabs = Array.from(tablist.querySelectorAll<HTMLButtonElement>('button'))
  reminderTab = tabs.find((tab) => tab.textContent?.trim() === '提醒') ?? null
  if (reminderTab) {
    reminderTab.style.display = 'none'
    reminderTab.setAttribute('data-inbox-hidden', 'true')
  }
}

onMounted(() => {
  hideRemindersTab()
  loadAll()
})

onBeforeUnmount(() => {
  if (reminderTab) {
    reminderTab.style.display = ''
    reminderTab.removeAttribute('data-inbox-hidden')
    reminderTab = null
  }
})
</script>

<style scoped>
.inbox-body {
  display: grid;
  gap: 14px;
}
.row-title {
  display: block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.card-head .filter-row {
  width: auto;
  justify-content: flex-end;
}
</style>
