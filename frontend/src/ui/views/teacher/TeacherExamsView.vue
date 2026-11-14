<template>
  <section class="module">
    <PageHead
      v-if="!isCourseScope"
      title="试卷库"
      sub="试卷的创建、编辑、题目明细、统计与提交审核；管理员审核通过后学生才可见。"
    >
      <template #actions>
        <button class="btn btn-primary" @click="openCreate">新建试卷</button>
      </template>
    </PageHead>
    <div v-else class="course-tab-bar">
      <div>
        <h2>课程试卷</h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-primary" @click="openCreate">新建试卷</button>
      </div>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>试卷列表</h2>
        </div>
        <span class="chip chip-mute">{{ total }} 条</span>
      </header>
      <div class="table-toolbar">
        <div class="filter-row">
          <select
            v-if="!isCourseScope"
            v-model="filters.courseId"
            class="input filter-course"
            aria-label="按课程筛选试卷"
            @change="applyFilters"
          >
            <option value="">全部课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
          <button class="btn btn-sm" @click="applyFilters">查询</button>
          <button class="btn btn-sm btn-ghost" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>试卷</th>
              <th v-if="!isCourseScope" class="w-140">课程</th>
              <th class="w-80">时长</th>
              <th class="w-80">总分</th>
              <th class="w-80">题量</th>
              <th class="w-96">状态</th>
              <th class="w-260 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in exams" :key="row.id">
              <td>
                <b class="exam-title">{{ row.title || '未命名试卷' }}</b>
                <small class="cell-sub">{{ row.description || '—' }}</small>
              </td>
              <td v-if="!isCourseScope">{{ courseNameOf(row) }}</td>
              <td>{{ row.duration ?? '—' }} 分</td>
              <td>{{ row.totalScore ?? '—' }}</td>
              <td>{{ questionCountOf(row) }}</td>
              <td>
                <span class="chip" :class="statusChipClass(row.status)">{{
                  statusView(row).label
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openQuestions(row)">题目</button>
                  <button class="text-btn" @click="openStats(row)">统计</button>
                  <button class="text-btn" :disabled="copyingId === row.id" @click="copyRow(row)">
                    {{ copyingId === row.id ? '复制中…' : '复制' }}
                  </button>
                  <template v-if="statusView(row).canPublish">
                    <button class="text-btn" @click="openEdit(row)">编辑</button>
                    <button class="text-btn" @click="publishRow(row)">提交审核</button>
                    <button class="text-btn danger" @click="removeRow(row)">删除</button>
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && !exams.length" class="empty-state compact">
        <span class="e-ico"><Icon name="clipboard" /></span>
        <b>暂无试卷</b>
        <p>点击「新建试卷」开始创建；提交审核并通过后学生才可见。</p>
      </div>
      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载试卷…</b>
      </div>

      <footer class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>

    <!-- 新建 / 编辑试卷 -->
    <Modal v-model:open="formOpen" :title="editingId ? '编辑试卷' : '新建试卷'" width="680px">
      <div class="form-grid">
        <label v-if="!isCourseScope" class="field span-2">
          <span>所属课程</span>
          <select v-model="form.courseId" class="input">
            <option value="">请选择课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>试卷标题</span>
          <input v-model="form.title" class="input" placeholder="如：期中测验" />
        </label>
        <label class="field span-2">
          <span>描述</span>
          <textarea
            v-model="form.description"
            class="textarea"
            rows="2"
            placeholder="可选的试卷说明"
          ></textarea>
        </label>
        <label class="field">
          <span>时长（分钟）</span>
          <input v-model.number="form.duration" class="input" type="number" min="1" />
        </label>
        <label class="field">
          <span>总分</span>
          <input v-model.number="form.totalScore" class="input" type="number" min="1" />
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="formOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveForm">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <!-- 题目明细 -->
    <Modal v-model:open="questionsOpen" title="试卷题目" width="760px">
      <p class="modal-text">{{ currentExam?.title || '试卷' }} · {{ examQuestions.length }} 道题</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-60">序号</th>
              <th>题目</th>
              <th class="w-96">题型</th>
              <th class="w-80">分值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(question, index) in examQuestions" :key="question.id ?? index">
              <td>{{ index + 1 }}</td>
              <td>{{ question.content || question.stem || '—' }}</td>
              <td>{{ questionTypeLabel(question.type) }}</td>
              <td>{{ question.score ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="questionsLoading" class="modal-hint">正在加载题目…</div>
      <div v-else-if="!examQuestions.length" class="modal-hint">该试卷暂无题目。</div>
      <template #footer>
        <button class="btn btn-ghost" @click="questionsOpen = false">关闭</button>
      </template>
    </Modal>

    <!-- 统计 -->
    <Modal v-model:open="statsOpen" title="试卷统计" width="620px">
      <p class="modal-text">{{ currentExam?.title || '试卷' }}</p>
      <div v-if="statsLoading" class="modal-hint">正在加载统计…</div>
      <PaperStats v-else-if="examStats" :stats="examStats" />
      <div v-else class="modal-hint">暂无统计数据。</div>
      <template #footer>
        <button class="btn btn-ghost" @click="statsOpen = false">关闭</button>
      </template>
    </Modal>

    <!-- 确认 -->
    <Modal
      v-model:open="confirmState.open"
      :title="confirmState.title"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">{{ confirmState.message }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirmState.open = false">取消</button>
        <button
          class="btn"
          :class="confirmState.danger ? 'btn-danger' : 'btn-primary'"
          :disabled="confirmState.busy"
          @click="runConfirm"
        >
          {{ confirmState.busy ? '处理中…' : confirmState.confirmText }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  copyExam,
  createExamPaper,
  deleteExamPaper,
  getAllExamPapers,
  getExamQuestions,
  getExamStatistics,
  publishExam,
  updateExamPaper,
} from '@/api/exam'
import { getAllMyCourses } from '@/api/course'
import { pickList, unwrap } from '@/utils/response'
import { examPaperStatusView } from '@/constants/examPaperStatus'
import { questionTypeLabel } from '@/constants/questionTypes'
import PageHead from '../../components/PageHead.vue'
import Stat from '../../components/Stat.vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import PaperStats from '../../components/PaperStats.vue'
import { useUi } from '../../composables/ui'
import type { Metric } from '../../nav'

interface ExamRow {
  id: number
  title?: string
  description?: string
  duration?: number
  totalScore?: number
  questionCount?: number
  courseId?: number
  courseName?: string
  status?: string
  questions?: ExamQuestion[]
  [key: string]: unknown
}

interface ExamQuestion {
  id?: number
  content?: string
  stem?: string
  type?: string
  score?: number
}

interface CourseRow {
  id: number
  name?: string
  title?: string
}

const props = defineProps<{ courseId?: number }>()
const { toast } = useUi()

const isCourseScope = computed(() => Number(props.courseId) > 0)
const allExams = ref<ExamRow[]>([])
const courses = ref<CourseRow[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ courseId: '' })

const formOpen = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({ courseId: '', title: '', description: '', duration: 90, totalScore: 100 })

const questionsOpen = ref(false)
const questionsLoading = ref(false)
const currentExam = ref<ExamRow | null>(null)
const examQuestions = ref<ExamQuestion[]>([])

const statsOpen = ref(false)
const statsLoading = ref(false)
const examStats = ref<Record<string, unknown> | null>(null)

const confirmState = reactive({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  danger: true,
  busy: false,
  action: null as null | (() => Promise<void>),
})

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const filteredExams = computed(() => {
  const scopeId = isCourseScope.value ? Number(props.courseId) : 0
  const filterCourseId = Number(filters.courseId) || 0
  return allExams.value.filter((row) => {
    const rowCourseId = Number(row.courseId) || 0
    if (scopeId && rowCourseId && rowCourseId !== scopeId) return false
    if (filterCourseId && rowCourseId !== filterCourseId) return false
    return true
  })
})
const total = computed(() => filteredExams.value.length)
const exams = computed(() =>
  filteredExams.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const publishedCount = computed(
  () =>
    filteredExams.value.filter((row) => String(row.status).toUpperCase() === 'PUBLISHED').length,
)
const draftCount = computed(
  () => filteredExams.value.filter((row) => String(row.status).toUpperCase() === 'DRAFT').length,
)
const pendingReviewCount = computed(
  () =>
    filteredExams.value.filter((row) => String(row.status).toUpperCase() === 'PENDING_REVIEW')
      .length,
)
const archivedCount = computed(
  () => filteredExams.value.filter((row) => String(row.status).toUpperCase() === 'ARCHIVED').length,
)
const metrics = computed<Metric[]>(() => [
  { label: '试卷', value: total.value, unit: '份', note: '当前筛选', icon: 'clipboard' },
  {
    label: '已发布',
    value: publishedCount.value,
    unit: '份',
    note: '当前筛选',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '草稿',
    value: draftCount.value,
    unit: '份',
    note: '当前筛选',
    icon: 'file',
    tone: 'warn',
  },
  {
    label: '待审核',
    value: pendingReviewCount.value,
    unit: '份',
    note: '当前筛选',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '已归档',
    value: archivedCount.value,
    unit: '份',
    note: '当前筛选',
    icon: 'shield',
  },
])

function errMsg(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message || fallback
}

function statusView(row: ExamRow) {
  return examPaperStatusView(row.status)
}

function statusChipClass(status?: string): string {
  const view = examPaperStatusView(status)
  if (view.tagType === 'success') return 'chip-ok'
  if (view.tagType === 'danger') return 'chip-danger'
  if (view.tagType === 'warning') return 'chip-warn'
  return 'chip-mute'
}

function courseNameOf(row: ExamRow): string {
  if (row.courseName) return row.courseName
  const course = courses.value.find((item) => item.id === Number(row.courseId))
  return course?.name || course?.title || '—'
}

function questionCountOf(row: ExamRow): number {
  return Number(row.questionCount ?? (Array.isArray(row.questions) ? row.questions.length : 0))
}

async function loadCourses() {
  try {
    const response = await getAllMyCourses()
    const list = Array.isArray(response) ? response : pickList(unwrap(response))
    courses.value = (list || []) as CourseRow[]
  } catch {
    courses.value = []
  }
}

async function loadExams() {
  loading.value = true
  try {
    const list = await getAllExamPapers(
      isCourseScope.value ? { courseId: Number(props.courseId) } : {},
    )
    const rows = (Array.isArray(list) ? list : []) as ExamRow[]
    const owned = new Set(courses.value.map((course) => Number(course.id)))
    allExams.value = isCourseScope.value
      ? rows
      : rows.filter((row) => !row.courseId || owned.has(Number(row.courseId)))
  } catch (error) {
    allExams.value = []
    toast(errMsg(error, '试卷列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
}

function resetFilters() {
  filters.courseId = isCourseScope.value ? String(props.courseId) : ''
  applyFilters()
}

function goPage(delta: number) {
  const next = page.value + delta
  if (next < 1 || next > pageCount.value) return
  page.value = next
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    courseId: isCourseScope.value ? String(props.courseId) : filters.courseId || '',
    title: '',
    description: '',
    duration: 90,
    totalScore: 100,
  })
  formError.value = ''
  formOpen.value = true
}

function openEdit(row: ExamRow) {
  editingId.value = row.id
  Object.assign(form, {
    courseId: String(row.courseId ?? (isCourseScope.value ? props.courseId : '') ?? ''),
    title: row.title || '',
    description: row.description || '',
    duration: Number(row.duration ?? 90),
    totalScore: Number(row.totalScore ?? 100),
  })
  formError.value = ''
  formOpen.value = true
}

async function saveForm() {
  if (!isCourseScope.value && !form.courseId) {
    formError.value = '请选择所属课程'
    return
  }
  if (!form.title.trim()) {
    formError.value = '请输入试卷标题'
    return
  }
  if (!Number.isFinite(Number(form.duration)) || Number(form.duration) <= 0) {
    formError.value = '考试时长需为正数'
    return
  }
  if (!Number.isFinite(Number(form.totalScore)) || Number(form.totalScore) <= 0) {
    formError.value = '总分需为正数'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = {
      courseId: form.courseId ? Number(form.courseId) : undefined,
      title: form.title.trim(),
      description: form.description.trim(),
      duration: Number(form.duration),
      totalScore: Number(form.totalScore),
    }
    if (editingId.value) await updateExamPaper(editingId.value, payload)
    else await createExamPaper(payload)
    toast(editingId.value ? '试卷已更新' : '试卷已创建', 'ok')
    formOpen.value = false
    await loadExams()
  } catch (error) {
    formError.value = errMsg(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function openQuestions(row: ExamRow) {
  currentExam.value = row
  questionsOpen.value = true
  questionsLoading.value = true
  examQuestions.value = Array.isArray(row.questions) ? row.questions : []
  try {
    if (!examQuestions.value.length) {
      const body = unwrap(await getExamQuestions(row.id))
      examQuestions.value = pickList(body) as ExamQuestion[]
    }
  } catch (error) {
    examQuestions.value = []
    toast(errMsg(error, '试卷题目加载失败'), 'danger')
  } finally {
    questionsLoading.value = false
  }
}

async function openStats(row: ExamRow) {
  currentExam.value = row
  statsOpen.value = true
  statsLoading.value = true
  examStats.value = null
  try {
    const body = unwrap(await getExamStatistics(row.id))
    examStats.value = body && typeof body === 'object' ? (body as Record<string, unknown>) : null
  } catch (error) {
    examStats.value = null
    toast(errMsg(error, '统计加载失败'), 'danger')
  } finally {
    statsLoading.value = false
  }
}

/**
 * 复制试卷（2026-11-07 P2 批次：后端 POST /exam-papers/{id}/copy 此前无前端入口）。
 * 新卷强制 DRAFT 态、复制题目与分值（见 ExamPaperServiceImpl.copyExamPaper）；
 * 命名自动加「副本」后缀，用户可再自行编辑。
 */
const copyingId = ref<number | string | null>(null)

async function copyRow(row: ExamRow) {
  if (copyingId.value != null) return
  copyingId.value = row.id
  try {
    const created = unwrap<Record<string, unknown>>(
      await copyExam(row.id, { newTitle: `${row.title}（副本）` }),
    )
    toast('试卷已复制为新草稿，可直接编辑题目', 'ok')
    await loadExams()
    return created
  } catch (error) {
    toast(errMsg(error, '复制失败，请稍后重试'), 'danger')
    return null
  } finally {
    copyingId.value = null
  }
}

function askConfirm(options: {
  title: string
  message: string
  confirmText?: string
  danger?: boolean
  action: () => Promise<void>
}) {
  Object.assign(confirmState, {
    open: true,
    title: options.title,
    message: options.message,
    confirmText: options.confirmText || '确认',
    danger: options.danger !== false,
    busy: false,
    action: options.action,
  })
}

async function runConfirm() {
  if (!confirmState.action) return
  confirmState.busy = true
  try {
    await confirmState.action()
    confirmState.open = false
  } catch (error) {
    toast(errMsg(error, '操作失败'), 'danger')
  } finally {
    confirmState.busy = false
  }
}

function publishRow(row: ExamRow) {
  askConfirm({
    title: '提交审核',
    message: `提交后管理员会在考试治理中审核「${row.title}」；审核通过前学生看不到该试卷。`,
    confirmText: '提交审核',
    danger: false,
    action: async () => {
      await publishExam(row.id)
      toast('已提交审核', 'ok')
      await loadExams()
    },
  })
}

function removeRow(row: ExamRow) {
  askConfirm({
    title: '删除试卷',
    message: `删除「${row.title}」将一并移除该试卷关联的学生作答记录与成绩明细，且不可恢复。`,
    confirmText: '删除',
    action: async () => {
      await deleteExamPaper(row.id)
      toast('试卷已删除', 'ok')
      await loadExams()
    },
  })
}

watch(
  () => props.courseId,
  (value) => {
    filters.courseId = Number(value) > 0 ? String(value) : ''
    page.value = 1
    loadExams()
  },
)

onMounted(async () => {
  filters.courseId = isCourseScope.value ? String(props.courseId) : ''
  await loadCourses()
  await loadExams()
})
</script>

<style scoped>
.filter-course {
  height: 34px;
  width: auto;
  min-width: 150px;
  padding: 0 8px;
  font-size: 11px;
}
.exam-title {
  display: block;
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.cell-sub {
  display: block;
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 9px;
  color: var(--ink-3);
  margin-top: 3px;
}
@media (max-width: 720px) {
  .exam-title,
  .cell-sub {
    max-width: 200px;
  }
}
</style>
