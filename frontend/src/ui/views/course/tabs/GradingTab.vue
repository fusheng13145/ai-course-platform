<template>
  <div>
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>

    <div class="grading-layout">
      <section class="card queue-panel">
        <header class="card-head">
          <div>
            <p class="card-kicker">TO REVIEW / {{ pendingCount }}</p>
            <h2>待批阅队列</h2>
          </div>
          <select
            v-if="tasks.length"
            class="input task-select"
            :value="selectedTaskId ?? ''"
            aria-label="选择作业"
            @change="onTaskChange(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="task in tasks" :key="task.id" :value="task.id">
              {{ task.title || `作业 #${task.id}` }}
            </option>
          </select>
        </header>

        <div class="tabs grade-filter" role="tablist" aria-label="提交状态筛选">
          <button
            v-for="option in filters"
            :key="option.value"
            :class="{ 'is-active': statusFilter === option.value }"
            @click="statusFilter = option.value"
          >
            {{ option.label }}
          </button>
        </div>

        <div v-if="filteredSubmissions.length" class="grade-list">
          <button
            v-for="row in filteredSubmissions"
            :key="row.id"
            class="grade-row"
            :class="{ 'is-active': selected?.id === row.id }"
            @click="select(row)"
          >
            <span class="grade-avatar">{{ initial(row) }}</span>
            <span class="grade-main">
              <b>{{ studentName(row) }}</b>
              <small>{{ selectedTask?.title || '作业' }} · {{ formatDate(row.submittedAt) }}</small>
            </span>
            <span class="grade-ai">{{ aiScore(row) }}</span>
            <span class="chip" :class="statusChip(row).cls">{{ statusChip(row).text }}</span>
          </button>
        </div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="check" /></span>
          <b>{{
            loading ? '正在加载提交…' : tasks.length ? '当前筛选下没有提交' : '该课程还没有作业'
          }}</b>
          <p>
            {{
              tasks.length
                ? '切换筛选或稍后刷新。'
                : '旧版的作业管理仍可创建任务；新版后续批次接入。'
            }}
          </p>
        </div>

        <footer class="card-foot">
          <span>{{ submissions.length }} 份提交 · {{ pendingCount }} 待批阅</span>
          <button class="text-btn" @click="reload">刷新</button>
        </footer>
      </section>

      <aside class="card peek-panel">
        <template v-if="selected">
          <div class="peek-head">
            <span class="peek-avatar">{{ initial(selected) }}</span>
            <div>
              <h3>{{ studentName(selected) }}</h3>
              <p>{{ selectedTask?.title || '作业' }} · {{ formatDate(selected.submittedAt) }}</p>
            </div>
            <span class="chip" :class="statusChip(selected).cls">{{
              statusChip(selected).text
            }}</span>
          </div>
          <div class="peek-score">
            <span>AI 建议分</span>
            <b>{{ suggestion?.suggestedScore ?? '—' }}</b>
            <small>/ {{ maxScore }} · 待教师确认</small>
          </div>
          <div class="peek-grid">
            <div>
              <span>提交附件</span><b>{{ attachments(selected).length }} 个</b>
            </div>
            <div><span>查重</span><b>未发现异常</b></div>
            <div>
              <span>已交</span><b>{{ submissions.length }} 份</b>
            </div>
          </div>
          <label class="field">
            <span>评分</span>
            <input
              v-model="gradeModal.form.score"
              class="input"
              inputmode="numeric"
              aria-label="评分"
            />
          </label>
          <label class="field">
            <span>评语</span>
            <textarea
              v-model="gradeModal.form.feedback"
              class="textarea"
              rows="3"
              aria-label="评语"
            ></textarea>
          </label>
          <div class="peek-actions">
            <button class="btn btn-ghost" @click="startReturn">退回重做</button>
            <button class="btn btn-ghost" :disabled="gradeModal.aiLoading" @click="runAiPreview">
              {{ gradeModal.aiLoading ? 'AI 预批中…' : 'AI 预批' }}
            </button>
            <button class="btn btn-primary" @click="commitGrade">提交批阅</button>
          </div>

          <div v-if="suggestion" class="ai-suggest">
            <p><b>AI 建议</b> {{ suggestion.suggestedScore ?? '—' }} 分</p>
            <p v-if="suggestion.highlights">亮点：{{ suggestion.highlights }}</p>
            <p v-if="suggestion.suggestions">建议：{{ suggestion.suggestions }}</p>
            <button class="text-btn" @click="applyAiSuggestion">采纳建议</button>
          </div>

          <div v-if="returnModal.row" class="return-box">
            <label class="field">
              <span>退回原因</span>
              <textarea
                v-model="returnModal.form.reason"
                class="textarea"
                rows="2"
                aria-label="退回原因"
              ></textarea>
            </label>
            <div class="peek-actions">
              <button class="btn btn-ghost" @click="cancelReturn">取消</button>
              <button class="btn btn-primary" @click="commitReturn">确认退回</button>
            </div>
          </div>
          <p class="hint">AI 只回填建议分，提交后才写入成绩；退回必须填写理由。</p>
        </template>
        <div v-else class="empty-state">
          <span class="e-ico"><Icon name="file" /></span>
          <b>选择一份提交</b>
          <p>左侧队列选中后，在这里查看 AI 建议、评分与评语。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import { pickList, unwrap } from '@/utils/response'
import {
  useSubmissionGrading,
  parseAiSuggestion,
  submissionStatusView,
  SUBMISSION_FILTER_OPTIONS,
  type Submission,
} from '@/composables/useSubmissionGrading'
import Stat from '../../../components/Stat.vue'
import Icon from '../../../components/Icon.vue'
import type { Metric, Role } from '../../../nav'
import { useUi } from '../../../composables/ui'

interface TaskRow {
  id: number
  title?: string
  [key: string]: unknown
}

const props = defineProps<{ courseId: number; role: Role }>()
const route = useRoute()
const ui = useUi()

const tasks = ref<TaskRow[]>([])
const selectedTaskId = ref<number | null>(null)
const submissions = ref<Submission[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const courseIdRef = ref<number | null>(props.courseId)
const filters = SUBMISSION_FILTER_OPTIONS

const selectedTask = computed(
  () => tasks.value.find((task) => task.id === selectedTaskId.value) || null,
)
const selected = computed(
  () => submissions.value.find((row) => row.id === selectedId.value) || null,
)

const {
  statusFilter,
  filteredSubmissions,
  pendingCount,
  gradeModal,
  returnModal,
  runAiPreview,
  applyAiSuggestion,
  submitGrade,
  openGrade,
  openReturn,
  submitReturn,
} = useSubmissionGrading({
  courseId: courseIdRef,
  submissions,
  onGraded: async () => {
    await loadSubmissions()
  },
})

statusFilter.value = 'PENDING'

const suggestion = computed(() => {
  if (gradeModal.aiResult) return gradeModal.aiResult
  return parseAiSuggestion(selected.value)
})

const maxScore = computed(() => Number(selected.value?.maxScore ?? 100))
const gradedCount = computed(
  () => submissions.value.filter((row) => String(row.status).toUpperCase() === 'GRADED').length,
)

const metrics = computed<Metric[]>(() => [
  {
    label: '待批阅',
    value: pendingCount.value,
    unit: '份',
    note: '需教师确认',
    icon: 'check',
    tone: 'warn',
  },
  {
    label: '已提交',
    value: submissions.value.length,
    unit: '份',
    note: selectedTask.value?.title || '当前作业',
    icon: 'file',
  },
  {
    label: '已批阅',
    value: gradedCount.value,
    unit: '份',
    note: 'AI 不计入成绩',
    icon: 'award',
    tone: 'ok',
  },
])

function studentName(row: Submission): string {
  return row.studentName || row.studentNo || `学生 #${row.studentId ?? row.id}`
}

function initial(row: Submission): string {
  return studentName(row).slice(0, 1)
}

function formatDate(value: unknown): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusChip(row: Submission): { text: string; cls: string } {
  const view = submissionStatusView(row.status)
  const tone =
    view.tagClass === 'green' ? 'chip-ok' : view.tagClass === 'red' ? 'chip-danger' : 'chip-warn'
  return { text: view.label, cls: tone }
}

function aiScore(row: Submission): string {
  const parsed = parseAiSuggestion(row)
  if (parsed?.suggestedScore != null) return String(parsed.suggestedScore)
  if (row.score != null) return String(row.score)
  return '—'
}

function attachments(row: Submission): string[] {
  const value = row.fileUrls
  if (Array.isArray(value))
    return value.filter((item) => typeof item === 'string' && item.trim()) as string[]
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed))
      return parsed.filter((item) => typeof item === 'string' && item.trim())
  } catch {
    // Legacy submissions store comma-separated links rather than a JSON array.
  }
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function select(row: Submission) {
  selectedId.value = row.id
  openGrade(row)
}

async function loadTasks() {
  loading.value = true
  try {
    const response = await taskApi.getAllTasks({ courseId: props.courseId })
    tasks.value = Array.isArray(response) ? (response as TaskRow[]) : []
    if (tasks.value.length) {
      const fromQuery = Number(route.query.taskId)
      const wanted = tasks.value.find((task) => task.id === fromQuery)
      selectedTaskId.value = wanted ? wanted.id : tasks.value[0].id
      await loadSubmissions()
    }
  } finally {
    loading.value = false
  }
}

async function loadSubmissions() {
  const taskId = selectedTaskId.value
  if (taskId == null) return
  loading.value = true
  try {
    const payload = unwrap(await submissionApi.getTaskSubmissions(taskId, { page: 1, size: 100 }))
    submissions.value = pickList(payload).map((row) => ({
      ...(row as Record<string, unknown>),
    })) as Submission[]
    if (selectedId.value && !submissions.value.some((row) => row.id === selectedId.value)) {
      selectedId.value = null
    }
  } catch {
    ui.toast('提交记录加载失败，请稍后重试', 'danger')
    submissions.value = []
  } finally {
    loading.value = false
  }
}

function onTaskChange(value: string) {
  const id = Number(value)
  if (!id) return
  selectedTaskId.value = id
  selectedId.value = null
  returnModal.row = null
  loadSubmissions()
}

function reload() {
  loadSubmissions()
}

function startReturn() {
  if (!selected.value) return
  openReturn(selected.value)
}

function cancelReturn() {
  returnModal.row = null
  returnModal.form.reason = ''
}

async function commitGrade() {
  const ok = await submitGrade()
  if (ok) {
    selectedId.value = null
    ui.toast('批阅已提交，成绩以教师确认为准', 'ok')
  }
}

async function commitReturn() {
  const ok = await submitReturn()
  if (ok) {
    selectedId.value = null
    ui.toast('已退回，学生将收到原因', 'ok')
  }
}

onMounted(loadTasks)
</script>

<style scoped>
.task-select {
  max-width: 210px;
}
.grade-filter {
  margin-bottom: 8px;
}
.ai-suggest {
  margin-top: 10px;
  padding: 9px 11px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-ctl);
  font-size: 10px;
  line-height: 1.7;
  color: var(--ink-2);
}
.ai-suggest p {
  margin: 0 0 4px;
}
.return-box {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
</style>
