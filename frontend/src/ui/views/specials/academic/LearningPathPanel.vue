<template>
  <div class="lp-panel">
    <div class="lp-toolbar">
      <select
        v-model.number="courseId"
        class="input lp-course"
        aria-label="选择课程"
        @change="loadPath"
      >
        <option v-if="!courses.length" :value="null">暂无可选课程</option>
        <option v-for="course in courses" :key="course.id" :value="course.id">
          {{ course.name }}
        </option>
      </select>
      <button class="btn btn-ghost btn-sm" :disabled="loading || !courseId" @click="loadPath">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="courseError" class="callout danger" role="alert">
      <span>课程</span>
      <p>{{ courseError }}</p>
      <button class="btn btn-sm" @click="loadCourses">重试</button>
    </div>
    <div v-else-if="!courses.length && !loadingCourses" class="empty-state compact">
      <b>暂无可选课程</b>
    </div>

    <div v-if="loadError" class="callout danger" role="alert">
      <span>学习路径</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadPath">重试</button>
    </div>
    <div v-else-if="loading && !path" class="empty-state compact">
      <b>正在加载学习路径…</b>
    </div>

    <section v-if="path" class="card lp-summary">
      <header class="card-head">
        <div>
          <h2>{{ pathTitle }}</h2>
        </div>
        <div class="course-tab-actions">
          <span class="chip" :class="pathStatusChip">{{ pathStatusLabel }}</span>
          <button
            v-if="pathIsActive"
            class="btn btn-ghost btn-sm"
            :disabled="actionBusy"
            @click="pause"
          >
            {{ actionBusy ? '处理中…' : '暂停' }}
          </button>
          <button
            v-if="pathIsPaused"
            class="btn btn-ghost btn-sm"
            :disabled="actionBusy"
            @click="resume"
          >
            {{ actionBusy ? '处理中…' : '恢复' }}
          </button>
        </div>
      </header>

      <div class="lp-progress">
        <div class="lp-progress-head">
          <span>完成率</span>
          <b>{{ completionPercent }}%</b>
        </div>
        <div
          class="lp-bar"
          role="progressbar"
          :aria-valuenow="completionPercent"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <i :style="{ width: `${completionPercent}%` }" />
        </div>
      </div>

      <div class="stat-grid kpi-grid">
        <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
    </section>

    <section v-if="path && nodeRows.length" class="card">
      <header class="card-head">
        <div>
          <h2>节点</h2>
        </div>
        <div class="course-tab-actions">
          <span class="chip chip-mute">{{ nodeRows.length }} 个</span>
          <button
            v-if="nodeRows.length > NODE_PREVIEW"
            class="btn btn-ghost btn-sm"
            @click="nodeExpanded = !nodeExpanded"
          >
            {{ nodeExpanded ? '收起' : `全部 ${nodeRows.length} 个` }}
          </button>
        </div>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">阶段</th>
              <th>知识点</th>
              <th class="w-120">类型</th>
              <th class="w-120">状态</th>
              <th class="w-120">预计</th>
              <th class="w-120">实际</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in visibleNodeRows" :key="node.id">
              <td>{{ node.stage }}</td>
              <td>
                <b class="t-title">{{ node.title }}</b>
              </td>
              <td>
                <span class="chip chip-info">{{ node.type }}</span>
              </td>
              <td>
                <span class="chip" :class="node.chip">{{ node.status }}</span>
              </td>
              <td>{{ node.estimated }}</td>
              <td>{{ node.actual }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="path && dailyRows.length" class="card">
      <header class="card-head">
        <div>
          <h2>每日任务</h2>
        </div>
        <span class="chip chip-mute">{{ dailyRows.length }} 天</span>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-140">日期</th>
              <th>任务</th>
              <th class="w-140">预计</th>
              <th class="w-120">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in dailyRows" :key="task.key">
              <td>{{ task.date }}</td>
              <td>
                <b class="t-title">{{ task.title }}</b>
              </td>
              <td>{{ task.minutes }}</td>
              <td>
                <span class="chip" :class="task.chip">{{ task.status }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="canGenerate" class="card">
      <header class="card-head">
        <div>
          <h2>生成学习路径</h2>
        </div>
      </header>
      <div class="form-grid">
        <label class="field span-2">
          <span>学习目标</span>
          <input v-model="goal" class="input" placeholder="例如：补齐传输层薄弱点" />
        </label>
      </div>
      <div class="course-tab-actions lp-actions">
        <button class="btn btn-primary" :disabled="generating || !courseId" @click="generate">
          {{ generating ? '生成中…' : '生成学习路径' }}
        </button>
      </div>
      <p v-if="generateError" class="form-error">{{ generateError }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Stat from '../../../components/Stat.vue'
import type { Metric, Role } from '../../../nav'
import { getAllMyCourses } from '@/api/student'
import learningPathApi from '@/api/learningPath'
import aiApi from '@/api/ai'
import { unwrap } from '@/utils/response'
import { formatDate } from '@/utils/datetime'
import logger from '@/utils/logger'

interface CourseRow {
  id?: number | string
  name?: string
  title?: string
}

interface PathRow {
  id?: number | string
  courseId?: number | string
  pathName?: string
  title?: string
  status?: string
  completionRate?: number | string
  currentStage?: number
  totalStages?: number
  estimatedCompletionDate?: string
  actualCompletionDate?: string
  learningGoal?: string
  createdAt?: string
}

interface NodeRow {
  id?: number | string
  title?: string
  knowledgePointName?: string
  nodeType?: string
  status?: string
  orderIndex?: number
  estimatedTime?: number
  actualTime?: number
}

interface StageRow {
  stageNumber?: number
  stageName?: string
  stageDescription?: string
  estimatedTime?: number
  status?: string
  nodeCount?: number
  nodeIds?: Array<number | string>
}

interface DailyTaskRow {
  dayNumber?: number
  date?: string
  taskTitle?: string
  taskDescription?: string
  estimatedMinutes?: number
  status?: string
}

interface DetailBody {
  path?: PathRow
  nodes?: NodeRow[]
  statusStats?: Record<string, number>
  totalNodes?: number
  stagesParsed?: StageRow[]
  stagesRaw?: string
}

const STATUS: Record<string, { label: string; chip: string }> = {
  ACTIVE: { label: '进行中', chip: 'chip-ok' },
  IN_PROGRESS: { label: '进行中', chip: 'chip-ok' },
  PAUSED: { label: '已暂停', chip: 'chip-warn' },
  COMPLETED: { label: '已完成', chip: 'chip-mute' },
  PENDING: { label: '未开始', chip: 'chip-mute' },
  NOT_STARTED: { label: '未开始', chip: 'chip-mute' },
  SKIPPED: { label: '已跳过', chip: 'chip-mute' },
}

const NODE_TYPE: Record<string, string> = {
  STUDY: '学习',
  PRACTICE: '练习',
  TEST: '测试',
  VIDEO: '视频',
}

const TASK_STATUS: Record<string, { label: string; chip: string }> = {
  TODAY: { label: '今日', chip: 'chip-info' },
  UPCOMING: { label: '待开始', chip: 'chip-mute' },
  COMPLETED: { label: '已完成', chip: 'chip-ok' },
}

const role: Role = 'student'
const courses = ref<CourseRow[]>([])
const courseId = ref<number | null>(null)
const loadingCourses = ref(false)
const courseError = ref('')
const path = ref<PathRow | null>(null)
const detail = ref<DetailBody | null>(null)
const dailyTasks = ref<DailyTaskRow[]>([])
const loading = ref(false)
const loadError = ref('')
const goal = ref('')
const generating = ref(false)
const generateError = ref('')
const actionBusy = ref(false)
const actionError = ref('')
const nodeExpanded = ref(false)
const NODE_PREVIEW = 12

const pathId = computed(() => (path.value?.id == null ? null : path.value.id))
const pathTitle = computed(() => path.value?.pathName || path.value?.title || '学习路径')
const pathStatus = computed(() => String(path.value?.status || '').toUpperCase())
const pathIsActive = computed(
  () => pathStatus.value === 'ACTIVE' || pathStatus.value === 'IN_PROGRESS',
)
const pathIsPaused = computed(() => pathStatus.value === 'PAUSED')
const pathStatusLabel = computed(() => STATUS[pathStatus.value]?.label || pathStatus.value || '—')
const pathStatusChip = computed(() => STATUS[pathStatus.value]?.chip || 'chip-mute')
const canGenerate = computed(() => !path.value)

const completionPercent = computed(() => {
  const value = Number(path.value?.completionRate ?? 0)
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, Math.round(value)))
})

const stageByNode = computed(() => {
  const map = new Map<string, number>()
  for (const stage of detail.value?.stagesParsed || []) {
    for (const nodeId of stage.nodeIds || []) {
      map.set(String(nodeId), stage.stageNumber ?? 0)
    }
  }
  return map
})

const nodeRows = computed(() =>
  [...(detail.value?.nodes || [])]
    .sort((a, b) => Number(a.orderIndex ?? 0) - Number(b.orderIndex ?? 0))
    .map((node) => {
      const status = String(node.status || '').toUpperCase()
      const type = String(node.nodeType || '').toUpperCase()
      const id = String(node.id ?? `${node.orderIndex}-${node.title}`)
      return {
        id,
        stage: stageByNode.value.get(id) || '—',
        title: node.title || node.knowledgePointName || '知识点',
        type: NODE_TYPE[type] || node.nodeType || '学习',
        status: STATUS[status]?.label || node.status || '未开始',
        chip: STATUS[status]?.chip || 'chip-mute',
        estimated: formatMinutes(node.estimatedTime),
        actual: formatMinutes(node.actualTime),
      }
    }),
)

const visibleNodeRows = computed(() =>
  nodeExpanded.value ? nodeRows.value : nodeRows.value.slice(0, NODE_PREVIEW),
)

const dailyRows = computed(() =>
  dailyTasks.value.map((task, index) => {
    const status = String(task.status || '').toUpperCase()
    return {
      key: String(task.dayNumber ?? index),
      date: task.date || '—',
      title: task.taskTitle || task.taskDescription || '学习任务',
      minutes: formatMinutes(task.estimatedMinutes),
      status: TASK_STATUS[status]?.label || task.status || '—',
      chip: TASK_STATUS[status]?.chip || 'chip-mute',
    }
  }),
)

const metrics = computed<Metric[]>(() => {
  const stats = detail.value?.statusStats || {}
  const total = detail.value?.totalNodes ?? nodeRows.value.length
  const completed =
    stats.COMPLETED ?? nodeRows.value.filter((row) => row.status === '已完成').length
  return [
    {
      label: '当前阶段',
      value: `${path.value?.currentStage ?? 1}/${path.value?.totalStages ?? detail.value?.stagesParsed?.length ?? '—'}`,
      note: '阶段进度',
      icon: 'chart',
    },
    {
      label: '已完成节点',
      value: `${completed}/${total}`,
      note: '路径节点',
      icon: 'award',
      tone: completed > 0 ? 'ok' : undefined,
    },
    {
      label: '预计完成',
      value: formatDate(path.value?.estimatedCompletionDate),
      note: path.value?.actualCompletionDate
        ? `实际 ${formatDate(path.value.actualCompletionDate)}`
        : '按当前进度',
      icon: 'clock',
    },
  ]
})

function formatMinutes(value?: number): string {
  return value == null ? '—' : `${value} 分钟`
}

function normalizePath(raw: PathRow | null | undefined): PathRow | null {
  if (!raw || raw.id == null) return null
  return raw
}

async function loadCourses() {
  loadingCourses.value = true
  courseError.value = ''
  try {
    const rows = await getAllMyCourses()
    courses.value = Array.isArray(rows) ? (rows as CourseRow[]) : []
    const first = courses.value.find((course) => course.id != null)
    courseId.value = first?.id == null ? null : Number(first.id)
    await loadPath()
  } catch (error) {
    logger.error('加载在修课程失败', error)
    courses.value = []
    courseId.value = null
    courseError.value = '课程列表加载失败，请稍后重试'
  } finally {
    loadingCourses.value = false
  }
}

async function loadPath() {
  if (courseId.value == null) {
    path.value = null
    detail.value = null
    dailyTasks.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  actionError.value = ''
  generateError.value = ''
  try {
    let candidate = normalizePath(
      unwrap<PathRow | null>(await learningPathApi.getActivePath(courseId.value)),
    )
    if (!candidate) {
      const all = unwrap<PathRow[]>(await learningPathApi.getMyPaths()) || []
      candidate =
        [...all]
          .filter((row) => Number(row.courseId) === courseId.value)
          .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))[0] ||
        null
    }
    path.value = candidate
    if (!candidate?.id) {
      detail.value = null
      dailyTasks.value = []
      return
    }
    detail.value = unwrap<DetailBody>(await learningPathApi.getPathDetail(candidate.id)) ?? null
    dailyTasks.value =
      unwrap<DailyTaskRow[]>(await learningPathApi.getDailyTasks(candidate.id)) || []
  } catch (error) {
    logger.error('加载学习路径失败', error)
    path.value = null
    detail.value = null
    dailyTasks.value = []
    loadError.value = '学习路径加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (courseId.value == null || generating.value) return
  generating.value = true
  generateError.value = ''
  try {
    await aiApi.generateLearningPath({
      courseId: String(courseId.value),
      goal: goal.value.trim() || '掌握课程核心知识',
    })
    goal.value = ''
    await loadPath()
  } catch (error) {
    logger.error('生成学习路径失败', error)
    generateError.value = '学习路径生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

async function pause() {
  if (!pathId.value || actionBusy.value) return
  actionBusy.value = true
  actionError.value = ''
  try {
    await learningPathApi.pausePath(pathId.value)
    await loadPath()
  } catch (error) {
    logger.error('暂停学习路径失败', error)
    actionError.value = '暂停失败，请稍后重试'
  } finally {
    actionBusy.value = false
  }
}

async function resume() {
  if (!pathId.value || actionBusy.value) return
  actionBusy.value = true
  actionError.value = ''
  try {
    await learningPathApi.resumePath(pathId.value)
    await loadPath()
  } catch (error) {
    logger.error('恢复学习路径失败', error)
    actionError.value = '恢复失败，请稍后重试'
  } finally {
    actionBusy.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.lp-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}
.lp-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.lp-course {
  width: min(100%, 240px);
}
.lp-summary {
  display: grid;
  gap: 12px;
}
.lp-progress {
  display: grid;
  gap: 6px;
}
.lp-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-2);
  font-size: 12px;
}
.lp-progress-head b {
  font-family: var(--font-mono);
  font-weight: 600;
}
.lp-bar {
  height: 8px;
  overflow: hidden;
  border-radius: var(--r-chip);
  background: var(--surface-2);
}
.lp-bar i {
  display: block;
  height: 100%;
  background: var(--accent);
}
.lp-actions {
  margin-top: 10px;
  justify-content: flex-end;
}
.t-title {
  display: block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
</style>
