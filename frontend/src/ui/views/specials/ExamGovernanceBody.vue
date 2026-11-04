<template>
  <div>
    <div v-if="paperError || sessionsError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ paperError || sessionsError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">SESSIONS / LIVE</p>
          <h2>
            进行中考试 <span class="chip chip-mute">{{ sessions.length }} 场</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm" :disabled="sessionsLoading" @click="loadSessions">
            {{ sessionsLoading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="sessions.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>试卷</th>
              <th class="w-140">学生</th>
              <th class="w-80">次</th>
              <th class="w-220">开始时间</th>
              <th class="w-96">用时</th>
              <th class="w-220">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in sessions" :key="`s-${rowKey(row.id, index)}`">
              <td>{{ paperTitleOf(row.examPaperId) }}</td>
              <td>{{ userNameOf(row.studentId) }}</td>
              <td>{{ row.attemptNumber ?? '—' }}</td>
              <td>{{ formatDateTime(row.startTime) }}</td>
              <td>{{ formatSeconds(row.timeSpent) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openCheating(row)">作弊检测</button>
                  <button class="text-btn danger" @click="askForceSubmit(row)">强制交卷</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="sessionsLoading" class="empty-state compact">
        <b>正在加载进行中考试…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="play" /></span>
        <b>当前没有进行中的考试</b>
        <p>会话进入 STARTED 状态后出现在这里，可强制交卷或做作弊检测。</p>
      </div>
      <p class="hint">
        数据源 GET /exam-sessions/active，一行一名学生的考试会话；强制交卷不可撤回。
      </p>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">PAPERS / SCHOOL-WIDE</p>
          <h2>
            试卷列表 <span class="chip chip-mute">{{ filteredPapers.length }} 份</span>
          </h2>
        </div>
        <div class="filter-row">
          <select v-model="filters.courseId" class="input filter-course" @change="reload">
            <option value="">全部课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
          <select v-model="filters.status" class="input filter-course" @change="reload">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="PUBLISHED">已发布</option>
            <option value="ARCHIVED">已归档</option>
          </select>
          <input
            v-model="filters.keyword"
            class="input filter-keyword"
            type="search"
            placeholder="搜索试卷名/课程…"
            @keyup.enter="reload"
          />
          <button class="btn btn-sm" @click="reload">查询</button>
          <button class="btn btn-sm btn-ghost" @click="resetFilters">重置</button>
        </div>
      </header>

      <div v-if="filteredPapers.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>试卷</th>
              <th class="w-220">课程</th>
              <th class="w-96">创建人</th>
              <th class="w-80">总分</th>
              <th class="w-80">时长</th>
              <th class="w-80">题量</th>
              <th class="w-220">起止</th>
              <th class="w-96">状态</th>
              <th class="w-140">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in pagedPapers" :key="`p-${rowKey(row.id, index)}`">
              <td>
                <b class="paper-title">{{ row.title || '未命名试卷' }}</b>
                <small class="cell-sub">{{ row.description || '—' }}</small>
              </td>
              <td>{{ courseNameOf(row) }}</td>
              <td>{{ userNameOf(row.creatorId) }}</td>
              <td>{{ row.totalScore ?? '—' }}</td>
              <td>{{ row.duration ?? '—' }} 分</td>
              <td>{{ row.questionCount ?? '—' }}</td>
              <td>
                <small class="cell-sub">{{ formatDateTime(row.startTime) }}</small>
                <small class="cell-sub">{{ formatDateTime(row.endTime) }}</small>
              </td>
              <td>
                <span class="chip" :class="statusChip(row.status)">{{
                  examPaperStatusView(row.status).label
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openQuestions(row)">题目</button>
                  <button class="text-btn" @click="openStats(row)">统计</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="papersLoading" class="empty-state compact"><b>正在加载试卷…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="clipboard" /></span>
        <b>{{ paperError ? '试卷列表未能加载' : '没有符合条件的试卷' }}</b>
        <p>{{ paperError ? '请查看上方提示后重试。' : '调整筛选条件后重试。' }}</p>
      </div>

      <footer class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ filteredPapers.length }} 份</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>
      <p class="hint">
        筛选在本页全量数据上进行：GET /exam-papers 只接受 page/size/courseId，
        状态与关键词参数被服务端忽略。试卷审核（通过/拒绝）后端无端点，本页不提供。
      </p>
    </section>

    <!-- 题目明细 -->
    <Modal v-model:open="questions.open" title="试卷题目" kicker="PAPER / QUESTIONS" width="760px">
      <p class="modal-text">
        {{ questions.title }} · {{ questions.rows.length }} 道题（管理员视图含参考答案）
      </p>
      <div v-if="questions.rows.length" class="table-wrap">
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
            <tr v-for="(item, index) in questions.rows" :key="`q-${rowKey(item.id, index)}`">
              <td>{{ index + 1 }}</td>
              <td>{{ item.content || item.stem || '—' }}</td>
              <td>{{ questionTypeLabel(item.type) }}</td>
              <td>{{ item.score ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="questions.loading" class="modal-hint">正在加载题目…</div>
      <div v-else class="modal-hint">{{ questions.error || '该试卷暂无题目。' }}</div>
      <template #footer>
        <button class="btn btn-ghost" @click="questions.open = false">关闭</button>
      </template>
    </Modal>

    <!-- 试卷统计 -->
    <Modal v-model:open="stats.open" title="试卷统计" kicker="PAPER / STATISTICS" width="620px">
      <p class="modal-text">{{ stats.title }}</p>
      <div v-if="stats.loading" class="modal-hint">正在加载统计…</div>
      <PaperStats v-else-if="stats.body" :stats="stats.body" />
      <div v-else class="modal-hint">{{ stats.error || '统计数据不可用。' }}</div>
      <template #footer>
        <button class="btn btn-ghost" @click="stats.open = false">关闭</button>
      </template>
    </Modal>

    <!-- 作弊检测 -->
    <Modal v-model:open="cheating.open" title="作弊检测" kicker="SESSION / CHEATING" width="620px">
      <p class="modal-text">{{ cheating.label }}</p>
      <div v-if="cheating.loading" class="modal-hint">正在检测…</div>
      <ul v-else-if="cheating.rows.length" class="flag-list">
        <li v-for="(item, index) in cheating.rows" :key="`f-${index}`">{{ item }}</li>
      </ul>
      <p v-else-if="cheating.error" class="form-error" role="alert">{{ cheating.error }}</p>
      <p v-else class="modal-hint">服务端未返回可疑项。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="cheating.open = false">关闭</button>
      </template>
    </Modal>

    <!-- 强制交卷确认 -->
    <Modal
      v-model:open="confirm.open"
      title="强制交卷"
      kicker="CONFIRM / FORCE SUBMIT"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">{{ confirm.message }}</p>
      <p class="modal-hint">交卷结果无法撤回，学生不能再修改作答。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runConfirm">
          {{ confirm.busy ? '处理中…' : '确定强制交卷' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Stat from '../../components/Stat.vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import PaperStats from '../../components/PaperStats.vue'
import { useUi } from '../../composables/ui'
import examApi from '@/api/exam'
import { getAllCourses } from '@/api/course'
import userApi from '@/api/user'
import { fetchAllPages, pickList, unwrap } from '@/utils/response'
import { examPaperStatusView } from '@/constants/examPaperStatus'
import { questionTypeLabel } from '@/constants/questionTypes'
import logger from '@/utils/logger'
import type { Metric } from '../../nav'

interface PaperRow {
  id: number
  title?: string
  description?: string
  courseId?: number
  courseName?: string
  creatorId?: number
  status?: string
  totalScore?: number
  duration?: number
  questionCount?: number
  startTime?: string
  endTime?: string
  [key: string]: unknown
}

interface SessionRow {
  id: number
  examPaperId?: number
  studentId?: number
  attemptNumber?: number
  startTime?: string
  timeSpent?: number
  status?: string
  [key: string]: unknown
}

interface CourseOption {
  id: number | string
  name?: string
  title?: string
}

interface PersonRow {
  id?: number | string
  fullName?: string
  username?: string
  [key: string]: unknown
}

interface QuestionCell {
  id?: number | string
  content?: string
  stem?: string
  type?: string
  score?: number
  [key: string]: unknown
}

const { toast } = useUi()

const papers = ref<PaperRow[]>([])
const courses = ref<CourseOption[]>([])
const people = ref<Map<string, string>>(new Map())
const sessions = ref<SessionRow[]>([])

const papersLoading = ref(false)
const paperError = ref('')
const sessionsLoading = ref(false)
const sessionsError = ref('')

const filters = reactive({ courseId: '', status: '', keyword: '' })
const page = ref(1)
const pageSize = 10

const questions = reactive({
  open: false,
  loading: false,
  error: '',
  title: '',
  rows: [] as QuestionCell[],
})

const stats = reactive({
  open: false,
  loading: false,
  error: '',
  title: '',
  body: null as Record<string, unknown> | null,
})

const cheating = reactive({
  open: false,
  loading: false,
  error: '',
  label: '',
  rows: [] as string[],
})

const confirm = reactive({
  open: false,
  busy: false,
  message: '',
  session: null as SessionRow | null,
})

const filteredPapers = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return papers.value.filter((row) => {
    if (filters.courseId && Number(row.courseId) !== Number(filters.courseId)) return false
    if (filters.status && String(row.status).toUpperCase() !== filters.status) return false
    if (keyword) {
      const hay = `${row.title ?? ''} ${row.description ?? ''} ${courseNameOf(row)}`.toLowerCase()
      if (!hay.includes(keyword)) return false
    }
    return true
  })
})

const pageCount = computed(() => Math.max(1, Math.ceil(filteredPapers.value.length / pageSize)))
const pagedPapers = computed(() =>
  filteredPapers.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)

function countByStatus(status: string): number {
  return papers.value.filter((row) => String(row.status).toUpperCase() === status).length
}

const metrics = computed<Metric[]>(() => [
  {
    label: '试卷总数',
    value: papers.value.length,
    unit: '份',
    note: paperError.value ? '加载失败' : '服务端全量',
    icon: 'clipboard',
  },
  {
    label: '已发布',
    value: countByStatus('PUBLISHED'),
    unit: '份',
    note: '全量统计',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '草稿',
    value: countByStatus('DRAFT'),
    unit: '份',
    note: '全量统计',
    icon: 'file',
    tone: 'warn',
  },
  {
    label: '进行中会话',
    value: sessionsLoading.value ? '…' : sessionsError.value ? '—' : sessions.value.length,
    unit: '场',
    note: 'status=STARTED',
    icon: 'play',
    tone: 'info',
  },
])

function rowKey(id: unknown, index: number): string {
  return String(id ?? index)
}

function errMsg(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message || fallback
}

function statusChip(status?: string): string {
  const tag = examPaperStatusView(status).tagType
  if (tag === 'success') return 'chip-ok'
  if (tag === 'danger') return 'chip-danger'
  if (tag === 'warning') return 'chip-warn'
  return 'chip-mute'
}

function formatDateTime(value?: string): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatSeconds(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const seconds = Number(value)
  if (!Number.isFinite(seconds) || seconds < 0) return '—'
  return seconds < 60 ? `${seconds} 秒` : `${Math.round(seconds / 60)} 分`
}

function paperTitleOf(paperId: unknown): string {
  const paper = papers.value.find((row) => Number(row.id) === Number(paperId))
  return paper?.title || (paperId ? `试卷 #${paperId}` : '—')
}

function userNameOf(id: unknown): string {
  if (id === null || id === undefined || id === '') return '—'
  return people.value.get(String(id)) || `用户 #${id}`
}

function courseNameOf(row: PaperRow): string {
  if (row.courseName) return row.courseName
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

/** 创建人与考生只显示姓名，故一次性拉取用户名单；失败时回落为「用户 #id」 */
async function loadPeople() {
  try {
    const rows = await fetchAllPages<PersonRow>((pageNumber, size) =>
      userApi.getUserList({ page: pageNumber, size }),
    )
    people.value = new Map(
      rows
        .filter((row) => row.id !== undefined && row.id !== null)
        .map((row) => [String(row.id), row.fullName || row.username || `用户 #${row.id}`]),
    )
  } catch (error) {
    logger.warn('加载用户名单失败，姓名将降级为 ID', error)
    people.value = new Map()
  }
}

async function loadPapers() {
  papersLoading.value = true
  paperError.value = ''
  try {
    papers.value = (await examApi.getAllExamPapers()) as unknown as PaperRow[]
  } catch (error) {
    logger.error('加载试卷列表失败', error)
    papers.value = []
    paperError.value = '试卷列表加载失败，请检查网络后重试'
  } finally {
    papersLoading.value = false
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    sessions.value = pickList<SessionRow>(unwrap(await examApi.getActiveExams()))
  } catch (error) {
    logger.error('加载进行中考试失败', error)
    sessions.value = []
    sessionsError.value = '进行中考试加载失败，请点击「刷新」重试'
  } finally {
    sessionsLoading.value = false
  }
}

function reload() {
  page.value = 1
}

function resetFilters() {
  filters.courseId = ''
  filters.status = ''
  filters.keyword = ''
  page.value = 1
}

function goPage(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
}

async function openQuestions(row: PaperRow) {
  questions.title = row.title || '试卷'
  questions.rows = []
  questions.error = ''
  questions.open = true
  questions.loading = true
  try {
    questions.rows = pickList<QuestionCell>(unwrap(await examApi.getExamQuestions(row.id)))
  } catch (error) {
    logger.error('加载试卷题目失败', error)
    questions.error = '题目加载失败，请稍后重试'
  } finally {
    questions.loading = false
  }
}

async function openStats(row: PaperRow) {
  stats.title = row.title || '试卷'
  stats.body = null
  stats.error = ''
  stats.open = true
  stats.loading = true
  try {
    const body = unwrap(await examApi.getExamStatistics(row.id))
    stats.body = body && typeof body === 'object' ? (body as Record<string, unknown>) : null
  } catch (error) {
    logger.error('加载试卷统计失败', error)
    stats.error = '统计加载失败，请稍后重试'
  } finally {
    stats.loading = false
  }
}

async function openCheating(session: SessionRow) {
  cheating.label = `${paperTitleOf(session.examPaperId)} · ${userNameOf(session.studentId)}`
  cheating.rows = []
  cheating.error = ''
  cheating.open = true
  cheating.loading = true
  try {
    cheating.rows = pickList<string>(unwrap(await examApi.getCheatingDetect(session.id))).map(
      (item) => String(item),
    )
  } catch (error) {
    logger.error('作弊检测失败', error)
    cheating.error = errMsg(error, '作弊检测失败，请稍后重试')
  } finally {
    cheating.loading = false
  }
}

function askForceSubmit(session: SessionRow) {
  confirm.session = session
  confirm.message = `将对「${paperTitleOf(session.examPaperId)}」的 ${userNameOf(
    session.studentId,
  )} 立即收卷，按其当前作答提交。`
  confirm.open = true
}

async function runConfirm() {
  const session = confirm.session
  if (!session) return
  confirm.busy = true
  try {
    await examApi.forceSubmit(session.id)
    toast('已强制交卷', 'ok')
    confirm.open = false
    confirm.session = null
    await loadSessions()
  } catch (error) {
    toast(errMsg(error, '强制交卷失败'), 'danger')
    logger.error('强制交卷失败', error)
  } finally {
    confirm.busy = false
  }
}

onMounted(async () => {
  await Promise.all([loadPapers(), loadCourses(), loadPeople(), loadSessions()])
})
</script>

<style scoped>
.filter-course {
  height: 34px;
  width: auto;
  min-width: 116px;
  padding: 0 8px;
  font-size: 11px;
}
.filter-keyword {
  flex: 1;
  min-width: 150px;
  max-width: 210px;
}
.paper-title {
  display: block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.flag-list {
  margin: 0;
  padding-left: 18px;
  font-size: 11px;
  line-height: 1.8;
  color: var(--ink-2);
}
</style>
