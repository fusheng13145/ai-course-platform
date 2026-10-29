<template>
  <section class="module">
    <V2PageHead
      kicker="ADMIN / QUESTION BANK"
      title="题库管理"
      sub="全校题目库维护：筛选、编辑、AI 出题、统计与易错题榜单。"
      source="api"
    >
      <template #actions>
        <button class="btn btn-ghost" :disabled="errorLoading" @click="loadErrorProne">
          {{ errorLoading ? '拉取中…' : '易错题榜单' }}
        </button>
        <button class="btn btn-ghost" @click="openAi">AI 出题</button>
        <button class="btn btn-primary" @click="openCreate">新建题目</button>
      </template>
    </V2PageHead>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">QUESTIONS / SCHOOL-WIDE</p>
          <h2>
            题目列表 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row">
          <select v-model="filters.courseId" class="input filter-course" @change="reload">
            <option value="">全部课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
          <select v-model="filters.type" class="input filter-course" @change="reload">
            <option value="">全部题型</option>
            <option
              v-for="option in QUESTION_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
          <select v-model="filters.difficulty" class="input filter-course" @change="reload">
            <option value="">全部难度</option>
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
          <input
            v-model="filters.keyword"
            class="input filter-keyword"
            type="search"
            placeholder="搜索题干…"
            @keyup.enter="reload"
          />
          <button class="btn btn-sm" @click="reload">查询</button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">ID</th>
              <th>题干</th>
              <th class="w-96">题型</th>
              <th class="w-96">难度</th>
              <th class="w-220">所属课程</th>
              <th class="w-96">审核</th>
              <th class="w-220">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="rowKey(row, index)">
              <td>{{ row.id }}</td>
              <td>
                <b class="q-text">{{ rowText(row) }}</b>
              </td>
              <td>
                <span class="chip chip-info">{{ questionTypeLabel(row.type) }}</span>
              </td>
              <td>{{ difficultyLabel(row.difficulty) }}</td>
              <td>{{ courseNameOf(row) }}</td>
              <td>
                <span class="chip" :class="reviewChip(row)">{{ reviewText(row) }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button class="text-btn" @click="openStats(row)">统计</button>
                  <button class="text-btn danger" @click="askRemove(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载题目…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="book" /></span>
        <b>{{ loadError ? '题目列表未能加载' : '没有符合条件的题目' }}</b>
        <p>{{ loadError ? '请查看上方提示后重试。' : '调整筛选条件，或点击「新建题目」。' }}</p>
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

    <!-- 新建 / 编辑 -->
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
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </label>
        <label class="field span-2">
          <span>所属课程 *</span>
          <select v-model="form.courseId" class="input">
            <option value="">请选择课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>题目内容 *</span>
          <textarea
            v-model="form.content"
            class="textarea"
            rows="4"
            placeholder="请输入题干内容"
          ></textarea>
        </label>
        <label v-if="hasOptions" class="field span-2">
          <span>选项设置（每行一个）</span>
          <textarea
            v-model="form.optionsText"
            class="textarea"
            rows="4"
            placeholder="A. 选项一"
          ></textarea>
        </label>
        <label class="field">
          <span>参考答案 *</span>
          <input v-model="form.answer" class="input" placeholder="多选题用逗号分隔" />
        </label>
        <label class="field">
          <span>分值</span>
          <input v-model.number="form.score" class="input" type="number" min="0" step="0.5" />
        </label>
        <label class="field span-2">
          <span>解析说明</span>
          <textarea
            v-model="form.analysis"
            class="textarea"
            rows="2"
            placeholder="可选的解析"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="formOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitForm">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </V2Modal>

    <!-- AI 出题 -->
    <V2Modal v-model:open="aiOpen" title="AI 出题" kicker="QUESTION / AI" width="560px">
      <div class="form-grid">
        <label class="field span-2">
          <span>课程 *</span>
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
          <span>数量</span>
          <input v-model.number="aiForm.count" class="input" type="number" min="1" max="10" />
        </label>
        <label class="field span-2">
          <span>知识点 / 主题</span>
          <input v-model="aiForm.topic" class="input" placeholder="如：拥塞控制，子网划分" />
        </label>
      </div>
      <p v-if="aiError" class="form-error">{{ aiError }}</p>
      <p class="modal-hint">生成结果直接入库，需人工复核后再用于组卷。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="aiOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="aiLoading" @click="runAi">
          {{ aiLoading ? '生成中…' : '开始生成' }}
        </button>
      </template>
    </V2Modal>

    <!-- 题目统计 -->
    <V2Modal v-model:open="statsOpen" title="题目统计分析" kicker="QUESTION / STATS" width="480px">
      <div v-if="statsLoading" class="empty-state compact"><b>正在加载统计…</b></div>
      <template v-else-if="stats">
        <div class="stat-grid kpi-grid">
          <V2Stat v-for="item in statsMetrics" :key="item.label" :metric="item" role="admin" />
        </div>
        <p v-if="!statsHasData" class="modal-hint">该题还没有作答记录。</p>
      </template>
      <div v-else class="empty-state compact"><b>统计不可用</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="statsOpen = false">关闭</button>
      </template>
    </V2Modal>

    <!-- 易错题榜单 -->
    <V2Modal
      v-model:open="errorOpen"
      title="易错题榜单"
      kicker="QUESTION / ERROR-PRONE"
      width="680px"
    >
      <table v-if="errorRows.length" class="table-wrap">
        <thead>
          <tr>
            <th>题干</th>
            <th class="w-96">错误次数</th>
            <th class="w-96">正确率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in errorRows" :key="index">
            <td>{{ rowText(row) }}</td>
            <td>{{ row.errorCount ?? row.errorTimes ?? '—' }}</td>
            <td>{{ row.correctRate ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-state compact"><b>暂无易错题数据</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="errorOpen = false">关闭</button>
      </template>
    </V2Modal>

    <!-- 删除确认 -->
    <V2Modal v-model:open="confirm.open" title="删除题目" kicker="CONFIRM / DELETE" width="460px">
      <p class="modal-text">{{ confirmText }}</p>
      <p class="modal-hint">删除后不可恢复；已组入试卷的题目可能受影响。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runConfirm">
          确定删除
        </button>
      </template>
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import V2PageHead from '../../components/V2PageHead.vue'
import V2Modal from '../../components/V2Modal.vue'
import V2Stat from '../../components/V2Stat.vue'
import V2Icon from '../../components/V2Icon.vue'
import { useV2Ui } from '../../composables/ui'
import questionApi from '@/api/question'
import { getAllCourses } from '@/api/course'
import { QUESTION_TYPE_OPTIONS, questionTypeLabel } from '@/constants/questionTypes'
import { resolveReviewStatus } from '@/composables/useQuestionBank'
import { pickList, unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import type { V2Metric } from '../../nav'

interface CourseOption {
  id: number | string
  name?: string
  title?: string
}

interface QuestionRow {
  id: number
  content?: string
  stem?: string
  type?: string
  difficulty?: string
  courseId?: number
  courseName?: string
  answer?: string
  correctAnswers?: string
  explanation?: string
  analysis?: string
  reviewStatus?: string
  options?: unknown
  [key: string]: unknown
}

const { toast } = useV2Ui()

const courses = ref<CourseOption[]>([])
const rows = ref<QuestionRow[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const loadError = ref('')
const pendingCount = ref<number | null>(null)
const errorRows = ref<Record<string, unknown>[]>([])
const errorLoading = ref(false)
const errorOpen = ref(false)

const filters = reactive({ keyword: '', type: '', difficulty: '', courseId: '' })

const formOpen = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref<number | string | null>(null)
const form = reactive({
  type: 'SINGLE_CHOICE',
  difficulty: 'MEDIUM',
  courseId: '',
  content: '',
  optionsText: '',
  answer: '',
  score: 2,
  analysis: '',
})

const aiOpen = ref(false)
const aiLoading = ref(false)
const aiError = ref('')
const aiForm = reactive({ courseId: '', type: 'SINGLE_CHOICE', count: 3, topic: '' })

const statsOpen = ref(false)
const statsLoading = ref(false)
const stats = ref<Record<string, unknown> | null>(null)

const confirm = reactive({ row: null as QuestionRow | null, open: false, busy: false })

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))
const hasOptions = computed(() => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(form.type))
const confirmText = computed(() => `确定删除题目「${rowText(confirm.row as QuestionRow)}」？`)

const metrics = computed<V2Metric[]>(() => [
  { label: '总题目数', value: total.value, unit: '道', note: '服务端总数', icon: 'book' },
  { label: '今日新增', value: '—', unit: '道', note: '后端未提供该读数', icon: 'plus' },
  {
    label: '待审核',
    value: display(pendingCount.value),
    unit: '道',
    note: '本页统计',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '易错题数',
    value: display(errorRows.value.length || null),
    unit: '道',
    note: '榜单拉取后',
    icon: 'alert',
  },
])

const statsHasData = computed(() => Number(stats.value?.usageCount ?? 0) > 0)
const statsMetrics = computed<V2Metric[]>(() => [
  {
    label: '使用次数',
    value: display(stats.value?.usageCount),
    unit: '次',
    note: '题目统计',
    icon: 'chart',
  },
  {
    label: '错误次数',
    value: display(stats.value?.errorCount),
    unit: '次',
    note: '题目统计',
    icon: 'alert',
    tone: 'warn',
  },
  {
    label: '正确率',
    value:
      stats.value?.correctRate === null || stats.value?.correctRate === undefined
        ? '—'
        : `${stats.value.correctRate}%`,
    note: '题目统计',
    icon: 'check',
    tone: 'ok',
  },
])

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function rowKey(row: QuestionRow, index: number): string {
  return String(row.id ?? index)
}

function rowText(row?: { content?: string; stem?: string }): string {
  if (!row) return '—'
  return String(row.content || row.stem || '—')
}

function difficultyLabel(value: unknown): string {
  const map: Record<string, string> = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[String(value)] || String(value ?? '—')
}

function reviewText(row: QuestionRow): string {
  const map: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
  const status = resolveReviewStatus(row)
  return map[status] || status
}

function reviewChip(row: QuestionRow): string {
  const map: Record<string, string> = {
    PENDING: 'chip-warn',
    APPROVED: 'chip-ok',
    REJECTED: 'chip-danger',
  }
  return map[resolveReviewStatus(row)] || 'chip-mute'
}

function courseNameOf(row: QuestionRow): string {
  if (row.courseName) return String(row.courseName)
  const course = courses.value.find((item) => Number(item.id) === Number(row.courseId))
  return course?.name || course?.title || (row.courseId ? `课程 #${row.courseId}` : '—')
}

async function loadCourses() {
  try {
    courses.value = (await getAllCourses()) as unknown as CourseOption[]
  } catch (error) {
    logger.error('加载课程列表失败', error)
    courses.value = []
  }
}

async function loadRows() {
  loading.value = true
  loadError.value = ''
  try {
    const keyword = filters.keyword.trim()
    const params: Record<string, unknown> = {
      page: page.value,
      size,
      type: filters.type || undefined,
      difficulty: filters.difficulty || undefined,
      courseId: filters.courseId || undefined,
    }
    // GET /questions 只收 courseId/type/difficulty；关键词在 /questions/search 上
    const response = keyword
      ? await questionApi.searchQuestions({ ...params, keyword })
      : await questionApi.getQuestionList(params)
    const body = unwrap(response) as { total?: number; list?: unknown[]; records?: unknown[] }
    rows.value = pickList<QuestionRow>(body)
    total.value = Number(body?.total ?? rows.value.length)
    pendingCount.value = rows.value.filter((row) => resolveReviewStatus(row) === 'PENDING').length
  } catch (error) {
    logger.error('加载题目列表失败', error)
    rows.value = []
    total.value = 0
    pendingCount.value = null
    loadError.value = '题目列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  void loadRows()
}

function goPage(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
  void loadRows()
}

function resetForm() {
  form.type = 'SINGLE_CHOICE'
  form.difficulty = 'MEDIUM'
  form.courseId = filters.courseId || ''
  form.content = ''
  form.optionsText = ''
  form.answer = ''
  form.score = 2
  form.analysis = ''
  editingId.value = null
}

function openCreate() {
  formError.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: QuestionRow) {
  formError.value = ''
  editingId.value = row.id
  form.type = String(row.type || 'SINGLE_CHOICE')
  form.difficulty = String(row.difficulty || 'MEDIUM')
  form.courseId = row.courseId == null ? '' : String(row.courseId)
  form.content = rowText(row)
  form.optionsText = optionsTextOf(row)
  form.answer = String(row.correctAnswers ?? row.answer ?? '')
  form.score = Number(row.score ?? 2)
  form.analysis = String(row.analysis || row.explanation || '')
  formOpen.value = true
}

function buildPayload() {
  const options = form.optionsText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  return {
    type: form.type,
    difficulty: form.difficulty,
    courseId: form.courseId ? Number(form.courseId) : null,
    content: form.content.trim(),
    // 后端 Question.options 是字符串列，必须序列化后下发；答案字段名是 correctAnswers
    options: hasOptions.value ? JSON.stringify(options) : undefined,
    correctAnswers: form.answer.trim(),
    score: form.score,
    explanation: form.analysis,
  }
}

function optionsTextOf(row: QuestionRow): string {
  const raw = row.options
  if (Array.isArray(raw)) return raw.join('\n')
  if (typeof raw !== 'string' || !raw.trim()) return ''
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.join('\n') : raw
  } catch {
    return raw
  }
}

async function submitForm() {
  if (!form.content.trim()) {
    formError.value = '请填写题干'
    return
  }
  if (!form.courseId) {
    formError.value = '请选择所属课程'
    return
  }
  if (!form.answer.trim()) {
    formError.value = '请填写参考答案'
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) await questionApi.updateQuestion(editingId.value, payload)
    else await questionApi.createQuestion(payload)
    toast(editingId.value ? '题目已更新' : '题目已创建', 'ok')
    formOpen.value = false
    await loadRows()
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('保存题目失败', error)
  } finally {
    saving.value = false
  }
}

function openAi() {
  aiError.value = ''
  aiForm.courseId = filters.courseId || String(courses.value[0]?.id ?? '')
  aiForm.type = 'SINGLE_CHOICE'
  aiForm.count = 3
  aiForm.topic = ''
  aiOpen.value = true
}

async function runAi() {
  if (!aiForm.courseId) {
    aiError.value = '请选择课程'
    return
  }
  if (!aiForm.topic.trim()) {
    aiError.value = '请填写知识点或主题'
    return
  }
  aiLoading.value = true
  aiError.value = ''
  try {
    await questionApi.generateByAI({
      courseId: Number(aiForm.courseId),
      type: aiForm.type,
      count: aiForm.count,
      knowledgePoint: aiForm.topic.trim(),
      difficulty: 'MEDIUM',
    })
    toast('AI 出题完成', 'ok')
    aiOpen.value = false
    await loadRows()
  } catch (error) {
    aiError.value = 'AI 出题失败，请稍后重试'
    logger.error('AI 出题失败', error)
  } finally {
    aiLoading.value = false
  }
}

async function openStats(row: QuestionRow) {
  stats.value = null
  statsOpen.value = true
  statsLoading.value = true
  try {
    stats.value = (unwrap(unwrap(await questionApi.getQuestionStats(row.id))) || {}) as Record<
      string,
      unknown
    >
  } catch (error) {
    logger.error('加载题目统计失败', error)
    stats.value = null
    toast('题目统计加载失败', 'danger')
  } finally {
    statsLoading.value = false
  }
}

async function loadErrorProne() {
  errorLoading.value = true
  try {
    const body = unwrap(await questionApi.getErrorProne({ limit: 10 })) as {
      list?: unknown[]
      records?: unknown[]
    }
    const list = pickList<Record<string, unknown>>(body)
    errorRows.value = list
    errorOpen.value = true
    if (!list.length) toast('暂无易错题数据', 'info')
  } catch (error) {
    logger.error('获取易错题失败', error)
    errorRows.value = []
    toast('获取易错题失败，请稍后重试', 'danger')
  } finally {
    errorLoading.value = false
  }
}

function askRemove(row: QuestionRow) {
  confirm.row = row
  confirm.open = true
}

async function runConfirm() {
  if (!confirm.row) return
  confirm.busy = true
  try {
    await questionApi.deleteQuestion(confirm.row.id)
    toast('已删除', 'ok')
    confirm.open = false
    confirm.row = null
    await loadRows()
  } catch (error) {
    toast('删除失败，请稍后重试', 'danger')
    logger.error('删除题目失败', error)
  } finally {
    confirm.busy = false
  }
}

onMounted(async () => {
  await loadCourses()
  await loadRows()
})
</script>

<style scoped>
.filter-row {
  justify-content: flex-end;
}
.filter-course {
  height: 34px;
  width: auto;
  min-width: 116px;
  padding: 0 8px;
  font-size: 11px;
}
.filter-keyword {
  flex: 1;
  min-width: 160px;
  max-width: 220px;
}
.q-text {
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.table-wrap table {
  width: 100%;
}
</style>
