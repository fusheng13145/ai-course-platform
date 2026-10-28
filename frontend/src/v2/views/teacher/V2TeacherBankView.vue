<template>
  <section class="module">
    <V2PageHead
      v-if="!isCourseScope"
      kicker="TEACHER / QUESTION BANK · 2026 秋"
      title="题库管理"
      sub="课程题库的题目筛选、创建、审核、复制与 AI 出题；审核通过后才进入可用题库。"
      source="api"
    >
      <template #actions>
        <button class="btn btn-ghost" @click="openAi">AI 出题</button>
        <button class="btn btn-primary" @click="openCreate">新建题目</button>
      </template>
    </V2PageHead>
    <div v-else class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / QUESTION BANK</p>
        <h2>课程题库</h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" @click="openAi">AI 出题</button>
        <button class="btn btn-primary" @click="openCreate">新建题目</button>
      </div>
    </div>

    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">QUESTIONS / {{ isCourseScope ? 'COURSE' : 'ALL COURSES' }}</p>
          <h2>题目列表</h2>
        </div>
        <span class="chip chip-mute">{{ total }} 条</span>
      </header>
      <div class="table-toolbar">
        <div class="filter-row">
          <select
            v-if="!isCourseScope"
            v-model="filters.courseId"
            class="input filter-course"
            @change="applyFilters"
          >
            <option value="">全部课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
          <select v-model="filters.type" class="input filter-type" @change="applyFilters">
            <option value="">全部题型</option>
            <option
              v-for="option in QUESTION_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
          <select v-model="filters.difficulty" class="input filter-diff" @change="applyFilters">
            <option value="">全部难度</option>
            <option
              v-for="option in QUESTION_DIFFICULTY_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
          <label class="table-search filter-keyword">
            <V2Icon name="search" />
            <input
              v-model="filters.keyword"
              placeholder="搜索题干…"
              aria-label="搜索题干"
              @keyup.enter="applyFilters"
            />
          </label>
          <button class="btn btn-sm" @click="applyFilters">查询</button>
          <button class="btn btn-sm btn-ghost" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>题干</th>
              <th class="w-96">题型</th>
              <th class="w-96">难度</th>
              <th v-if="!isCourseScope" class="w-140">所属课程</th>
              <th class="w-96">审核</th>
              <th class="w-220">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in questions" :key="row.id">
              <td>
                <b class="q-text">{{ questionText(row) }}</b>
              </td>
              <td>
                <span class="chip" :class="typeChipClass(row.type)">{{
                  questionTypeLabel(row.type)
                }}</span>
              </td>
              <td>
                <span class="chip" :class="difficultyChipClass(row.difficulty)">{{
                  questionDifficultyLabel(row.difficulty)
                }}</span>
              </td>
              <td v-if="!isCourseScope">{{ courseNameOf(row) }}</td>
              <td>
                <span class="chip" :class="reviewView(row).cls">{{ reviewView(row).text }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button class="text-btn" @click="openReview(row)">审核</button>
                  <button class="text-btn" @click="copyRow(row)">复制</button>
                  <button class="text-btn danger" @click="removeRow(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && !questions.length" class="empty-state compact">
        <span class="e-ico"><V2Icon name="book" /></span>
        <b>题库暂无题目</b>
        <p>点击「新建题目」或「AI 出题」开始。</p>
      </div>
      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载题目…</b>
      </div>

      <footer class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 条</span>
        <div class="v2-pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>

    <!-- 新建 / 编辑题目 -->
    <V2Modal
      v-model:open="formOpen"
      :title="editingId ? '编辑题目' : '新建题目'"
      kicker="QUESTION / FORM"
      width="760px"
    >
      <div class="form-grid">
        <label class="field">
          <span>题型</span>
          <select v-model="form.type" class="input">
            <option
              v-for="option in QUESTION_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>难度</span>
          <select v-model="form.difficulty" class="input">
            <option
              v-for="option in QUESTION_DIFFICULTY_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
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
          <span>题干</span>
          <textarea
            v-model="form.content"
            class="textarea"
            rows="4"
            placeholder="请输入题干内容"
          ></textarea>
        </label>
        <label class="field span-2">
          <span>参考答案</span>
          <textarea
            v-model="form.answer"
            class="textarea"
            rows="2"
            placeholder="请输入参考答案"
          ></textarea>
        </label>
        <label class="field span-2">
          <span>解析</span>
          <textarea
            v-model="form.explanation"
            class="textarea"
            rows="3"
            placeholder="可选的解析"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="formOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveForm">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </V2Modal>

    <!-- 审核 -->
    <V2Modal v-model:open="reviewOpen" title="审核题目" kicker="QUESTION / REVIEW" width="520px">
      <p class="modal-text">{{ reviewRow ? questionText(reviewRow) : '' }}</p>
      <div class="form-grid">
        <label class="field span-2">
          <span>审核结论</span>
          <select v-model="reviewStatus" class="input">
            <option value="APPROVED">通过</option>
            <option value="REJECTED">驳回</option>
          </select>
        </label>
        <label class="field span-2">
          <span>审核意见</span>
          <textarea
            v-model="reviewComment"
            class="textarea"
            rows="3"
            placeholder="可选的审核意见"
          ></textarea>
        </label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="reviewOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="reviewing" @click="saveReview">
          {{ reviewing ? '提交中…' : '提交审核' }}
        </button>
      </template>
    </V2Modal>

    <!-- AI 出题 -->
    <V2Modal v-model:open="aiOpen" title="AI 出题" kicker="QUESTION / AI" width="560px">
      <div class="form-grid">
        <label v-if="!isCourseScope" class="field span-2">
          <span>课程</span>
          <select v-model="aiForm.courseId" class="input">
            <option value="">请选择课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>题型</span>
          <select v-model="aiForm.type" class="input">
            <option
              v-for="option in QUESTION_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>难度</span>
          <select v-model="aiForm.difficulty" class="input">
            <option
              v-for="option in QUESTION_DIFFICULTY_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>知识点（逗号分隔，可留空）</span>
          <input v-model="aiForm.points" class="input" placeholder="如：拥塞控制，子网划分" />
        </label>
      </div>
      <p class="modal-hint">AI 出题会写入题库，但不改变已发布试卷。生成失败时不会写入任何题目。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="aiOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="aiBusy" @click="generate">
          {{ aiBusy ? '生成中…' : '开始生成' }}
        </button>
      </template>
    </V2Modal>

    <!-- 确认 -->
    <V2Modal
      v-model:open="confirmState.open"
      :title="confirmState.title"
      kicker="CONFIRM / ACTION"
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
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  copyQuestion,
  createQuestion,
  deleteQuestion,
  generateByAI,
  getAllQuestions,
  reviewQuestion,
  updateQuestion,
} from '@/api/question'
import { getAllMyCourses } from '@/api/course'
import { pickList, unwrap } from '@/utils/response'
import { resolveReviewStatus } from '@/composables/useQuestionBank'
import {
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  questionDifficultyLabel,
  questionDifficultyTag,
  questionTypeLabel,
  questionTypeTag,
} from '@/constants/questionTypes'
import V2PageHead from '../../components/V2PageHead.vue'
import V2Stat from '../../components/V2Stat.vue'
import V2Icon from '../../components/V2Icon.vue'
import V2Modal from '../../components/V2Modal.vue'
import { useV2Ui } from '../../composables/ui'
import type { V2Metric } from '../../nav'

interface QuestionRow {
  id: number
  content?: string
  stem?: string
  type?: string
  difficulty?: string
  courseId?: number
  courseName?: string
  answer?: string
  explanation?: string
  analysis?: string
  isActive?: boolean
  reviewStatus?: string
  [key: string]: unknown
}

interface CourseRow {
  id: number
  name?: string
  title?: string
}

const props = defineProps<{ courseId?: number }>()
const { toast } = useV2Ui()

const isCourseScope = computed(() => Number(props.courseId) > 0)
const allQuestions = ref<QuestionRow[]>([])
const courses = ref<CourseRow[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ courseId: '', type: '', difficulty: '', keyword: '' })

const formOpen = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  type: 'SINGLE_CHOICE',
  difficulty: 'MEDIUM',
  courseId: '',
  content: '',
  answer: '',
  explanation: '',
})

const reviewOpen = ref(false)
const reviewRow = ref<QuestionRow | null>(null)
const reviewStatus = ref('APPROVED')
const reviewComment = ref('')
const reviewing = ref(false)

const aiOpen = ref(false)
const aiBusy = ref(false)
const aiForm = reactive({ courseId: '', type: 'SINGLE_CHOICE', difficulty: 'MEDIUM', points: '' })

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
const filteredQuestions = computed(() => {
  const scopeId = isCourseScope.value ? Number(props.courseId) : 0
  const filterCourseId = Number(filters.courseId) || 0
  const keyword = filters.keyword.trim().toLowerCase()
  return allQuestions.value.filter((row) => {
    const rowCourseId = Number(row.courseId) || 0
    if (scopeId && rowCourseId && rowCourseId !== scopeId) return false
    if (filterCourseId && rowCourseId !== filterCourseId) return false
    if (filters.type && row.type !== filters.type) return false
    if (filters.difficulty && row.difficulty !== filters.difficulty) return false
    if (keyword && !questionText(row).toLowerCase().includes(keyword)) return false
    return true
  })
})
const total = computed(() => filteredQuestions.value.length)
const questions = computed(() =>
  filteredQuestions.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const pendingCount = computed(
  () => filteredQuestions.value.filter((row) => resolveReviewStatus(row) === 'PENDING').length,
)
const approvedCount = computed(
  () => filteredQuestions.value.filter((row) => resolveReviewStatus(row) === 'APPROVED').length,
)
const rejectedCount = computed(
  () => filteredQuestions.value.filter((row) => resolveReviewStatus(row) === 'REJECTED').length,
)
const metrics = computed<V2Metric[]>(() => [
  {
    label: '题目',
    value: total.value,
    unit: '道',
    note: '当前筛选',
    icon: 'book',
  },
  {
    label: '待审核',
    value: pendingCount.value,
    unit: '道',
    note: '当前筛选',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '已通过',
    value: approvedCount.value,
    unit: '道',
    note: '当前筛选',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '已驳回',
    value: rejectedCount.value,
    unit: '道',
    note: '当前筛选',
    icon: 'alert',
  },
])

function errMsg(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message || fallback
}

function questionText(row: QuestionRow): string {
  return String(row.content || row.stem || '—')
}

function courseNameOf(row: QuestionRow): string {
  if (row.courseName) return row.courseName
  const course = courses.value.find((item) => item.id === Number(row.courseId))
  return course?.name || course?.title || '—'
}

const CHIP_MAP: Record<string, string> = {
  primary: 'chip-info',
  success: 'chip-ok',
  warning: 'chip-warn',
  danger: 'chip-danger',
  info: 'chip-mute',
}

function typeChipClass(type?: string): string {
  return CHIP_MAP[questionTypeTag(type)] || 'chip-mute'
}
function difficultyChipClass(difficulty?: string): string {
  return CHIP_MAP[questionDifficultyTag(difficulty)] || 'chip-mute'
}
function reviewView(row: QuestionRow): { text: string; cls: string } {
  const status = resolveReviewStatus(row)
  if (status === 'APPROVED') return { text: '已通过', cls: 'chip-ok' }
  if (status === 'REJECTED') return { text: '已驳回', cls: 'chip-danger' }
  return { text: '待审核', cls: 'chip-warn' }
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

async function loadQuestions() {
  loading.value = true
  try {
    const list = await getAllQuestions(
      isCourseScope.value ? { courseId: Number(props.courseId) } : {},
    )
    const rows = (Array.isArray(list) ? list : []) as QuestionRow[]
    const owned = new Set(courses.value.map((course) => Number(course.id)))
    allQuestions.value = isCourseScope.value
      ? rows
      : rows.filter((row) => !row.courseId || owned.has(Number(row.courseId)))
  } catch (error) {
    allQuestions.value = []
    toast(errMsg(error, '题目列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
}

function resetFilters() {
  filters.type = ''
  filters.difficulty = ''
  filters.keyword = ''
  filters.courseId = isCourseScope.value ? String(props.courseId) : ''
  applyFilters()
}

function goPage(delta: number) {
  const next = page.value + delta
  if (next < 1 || next > pageCount.value) return
  page.value = next
}

function resetForm() {
  Object.assign(form, {
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: isCourseScope.value ? String(props.courseId) : '',
    content: '',
    answer: '',
    explanation: '',
  })
  formError.value = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(row: QuestionRow) {
  editingId.value = row.id
  Object.assign(form, {
    type: row.type || 'SINGLE_CHOICE',
    difficulty: row.difficulty || 'MEDIUM',
    courseId: String(row.courseId ?? (isCourseScope.value ? props.courseId : '') ?? ''),
    content: row.content || row.stem || '',
    answer: row.answer || '',
    explanation: row.explanation || row.analysis || '',
  })
  formError.value = ''
  formOpen.value = true
}

async function saveForm() {
  if (!form.content.trim()) {
    formError.value = '请填写题干'
    return
  }
  if (!isCourseScope.value && !form.courseId) {
    formError.value = '请选择所属课程'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = {
      type: form.type,
      difficulty: form.difficulty,
      courseId: form.courseId ? Number(form.courseId) : undefined,
      content: form.content.trim(),
      answer: form.answer.trim(),
      explanation: form.explanation.trim(),
    }
    if (editingId.value) await updateQuestion(editingId.value, payload)
    else await createQuestion(payload)
    toast(editingId.value ? '题目已更新' : '题目已创建', 'ok')
    formOpen.value = false
    await loadQuestions()
  } catch (error) {
    formError.value = errMsg(error, '保存失败')
  } finally {
    saving.value = false
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

function copyRow(row: QuestionRow) {
  askConfirm({
    title: '复制题目',
    message: `将复制「${questionText(row)}」到同一课程题库。`,
    confirmText: '复制',
    danger: false,
    action: async () => {
      await copyQuestion(row.id)
      toast('题目已复制', 'ok')
      await loadQuestions()
    },
  })
}

function removeRow(row: QuestionRow) {
  askConfirm({
    title: '删除题目',
    message: `删除「${questionText(row)}」后不可恢复；已引用该题的试卷可能受到影响。`,
    confirmText: '删除',
    action: async () => {
      await deleteQuestion(row.id)
      toast('题目已删除', 'ok')
      await loadQuestions()
    },
  })
}

function openReview(row: QuestionRow) {
  reviewRow.value = row
  reviewStatus.value = 'APPROVED'
  reviewComment.value = ''
  reviewOpen.value = true
}

async function saveReview() {
  if (!reviewRow.value) return
  reviewing.value = true
  try {
    await reviewQuestion(reviewRow.value.id, {
      status: reviewStatus.value,
      comment: reviewComment.value,
    })
    toast('审核已提交', 'ok')
    reviewOpen.value = false
    await loadQuestions()
  } catch (error) {
    toast(errMsg(error, '审核失败'), 'danger')
  } finally {
    reviewing.value = false
  }
}

function openAi() {
  aiForm.courseId = isCourseScope.value ? String(props.courseId) : filters.courseId
  aiForm.type = filters.type || 'SINGLE_CHOICE'
  aiForm.difficulty = filters.difficulty || 'MEDIUM'
  aiForm.points = ''
  aiOpen.value = true
}

async function generate() {
  if (!isCourseScope.value && !aiForm.courseId) {
    toast('请选择课程', 'warn')
    return
  }
  aiBusy.value = true
  try {
    const names = aiForm.points
      .split(/[,，\s]+/)
      .map((item) => item.trim())
      .filter(Boolean)
    await generateByAI({
      courseId: aiForm.courseId ? Number(aiForm.courseId) : undefined,
      type: aiForm.type,
      difficulty: aiForm.difficulty,
      knowledgePointNames: names,
    })
    toast('AI 出题完成', 'ok')
    aiOpen.value = false
    await loadQuestions()
  } catch (error) {
    toast(errMsg(error, 'AI 生成失败'), 'danger')
  } finally {
    aiBusy.value = false
  }
}

watch(
  () => props.courseId,
  (value) => {
    filters.courseId = Number(value) > 0 ? String(value) : ''
    page.value = 1
    loadQuestions()
  },
)

onMounted(async () => {
  filters.courseId = isCourseScope.value ? String(props.courseId) : ''
  await loadCourses()
  await loadQuestions()
})
</script>

<style scoped>
.filter-course,
.filter-type,
.filter-diff {
  height: 34px;
  width: auto;
  min-width: 116px;
  padding: 0 8px;
  font-size: 11px;
}
.filter-keyword {
  flex: 1;
  min-width: 180px;
}
.q-text {
  display: block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
@media (max-width: 720px) {
  .q-text {
    max-width: 200px;
  }
}
</style>
