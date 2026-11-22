<template>
  <section class="review-page">
    <header class="review-head">
      <button class="btn btn-ghost btn-sm" @click="back">← 试卷库</button>
      <div class="head-main">
        <h1>{{ paperTitle || '考试阅卷' }}</h1>
        <p class="head-sub">
          共 {{ sessions.length }} 份答卷 · 客观题已自动判分，主观题需逐份补分
        </p>
      </div>
      <button class="btn btn-ghost btn-sm" :disabled="loading" @click="reload">刷新</button>
    </header>

    <div v-if="loadError" class="callout danger">
      <span>加载失败</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" @click="reload">重试</button>
    </div>

    <div class="review-body">
      <aside class="session-pane card">
        <h2>学生答卷</h2>
        <p v-if="loadingSessions" class="hint">加载中…</p>
        <p v-else-if="!sessions.length" class="hint">该试卷还没有已交卷的答卷。</p>
        <ul v-else class="session-list">
          <li
            v-for="row in sessions"
            :key="row.sessionId"
            class="session-item"
            :class="{ 'is-active': row.sessionId === activeSessionId }"
            @click="selectSession(row)"
          >
            <div class="session-main">
              <b>{{ row.fullName || row.username || `学生 #${row.studentId}` }}</b>
              <span class="session-meta">
                {{ row.studentNo || '—' }} · 第 {{ row.attemptNumber || 1 }} 次
              </span>
            </div>
            <span class="chip" :class="row.status === 'GRADED' ? 'chip-ok' : 'chip-warn'">
              {{ row.status === 'GRADED' ? `${fmtScore(row.totalScore)} 分` : '待阅卷' }}
            </span>
          </li>
        </ul>
      </aside>

      <main class="sheet-pane">
        <div v-if="!activeSessionId" class="card empty-state">
          <b>选择左侧一份答卷开始阅卷</b>
          <p>客观题由系统自动判分，你只需要给简答题补分。</p>
        </div>

        <template v-else>
          <header class="sheet-head card">
            <div>
              <h2>{{ activeStudent }}</h2>
              <p class="head-sub">
                交卷时间 {{ fmtTime(session?.submitTime) }} · 总分
                <b>{{ fmtScore(session?.totalScore) }}</b>
              </p>
            </div>
            <span class="chip" :class="session?.status === 'GRADED' ? 'chip-ok' : 'chip-warn'">
              {{ statusLabel }}
            </span>
          </header>

          <div v-if="loadingSession" class="card empty-state"><b>正在加载答卷…</b></div>
          <div v-else-if="!questions.length" class="card empty-state">
            <b>本卷没有可阅的题目</b>
          </div>

          <template v-else>
            <article
              v-for="(item, index) in questions"
              :key="item.id ?? index"
              class="card question-card"
            >
            <header class="q-head">
              <span class="chip chip-info">{{ typeLabel(item.type) }}</span>
              <b>第 {{ index + 1 }} 题</b>
              <span class="q-points">满分 {{ item.score ?? '—' }}</span>
              <span class="foot-space"></span>
              <span class="chip" :class="isSubjective(item) ? 'chip-warn' : 'chip-mute'">
                {{ isSubjective(item) ? '需人工评分' : '系统判分' }}
              </span>
            </header>

            <p class="q-content">{{ item.content || '（题干缺失）' }}</p>

            <div class="answer-grid">
              <div class="answer-block">
                <span class="label">学生作答</span>
                <p class="answer-text">{{ answerText(item) }}</p>
              </div>
              <div class="answer-block">
                <span class="label">参考答案</span>
                <p class="answer-text">{{ correctText(item) }}</p>
              </div>
            </div>

            <footer class="q-foot">
              <label class="score-field">
                <span>得分</span>
                <input
                  v-model.number="draftScores[questionKey(item)]"
                  class="input"
                  type="number"
                  min="0"
                  :max="item.score ?? undefined"
                  step="0.5"
                  :disabled="savingQuestion === questionKey(item)"
                />
              </label>
              <label class="comment-field">
                <span>评语（可选）</span>
                <input
                  v-model="draftComments[questionKey(item)]"
                  class="input"
                  type="text"
                  placeholder="给学生的本题反馈"
                  :disabled="savingQuestion === questionKey(item)"
                />
              </label>
              <button
                class="btn btn-primary btn-sm"
                :disabled="savingQuestion === questionKey(item)"
                @click="saveQuestion(item)"
              >
                {{ savingQuestion === questionKey(item) ? '保存中…' : '保存本题' }}
              </button>
            </footer>
            </article>
          </template>
        </template>
      </main>
    </div>

    <Toasts />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Toasts from '../../components/Toasts.vue'
import { useUi } from '../../composables/ui'
import examApi from '@/api/exam'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface SessionRow {
  sessionId: number
  studentId: number
  username: string | null
  fullName: string | null
  studentNo: string | null
  attemptNumber: number | null
  status: string
  totalScore: number | null
  percentage: number | null
  submitTime: string | null
}

/**
 * `GET /exam-papers/{id}/questions` 在 api 层已被 normalizeExamQuestions 扁平化：
 * 题目字段直接挂在顶层，分值从关联行的 points 改名为 score。
 */
interface QuestionItem {
  id: number
  orderIndex?: number
  score: number | null
  type?: string
  content?: string
  options?: unknown
  correctAnswers?: unknown
  explanation?: string
}

const route = useRoute()
const router = useRouter()
const { toast } = useUi()

const paperId = Number(route.params.paperId)
const paperTitle = ref('')
const sessions = ref<SessionRow[]>([])
const questions = ref<QuestionItem[]>([])
const activeSessionId = ref<number | null>(null)
const session = ref<Record<string, unknown> | null>(null)
const loading = ref(true)
const loadingSessions = ref(false)
const loadingSession = ref(false)
const loadError = ref('')
const savingQuestion = ref<string | null>(null)
const draftScores = reactive<Record<string, number>>({})
const draftComments = reactive<Record<string, string>>({})

const activeRow = computed(
  () => sessions.value.find((row) => row.sessionId === activeSessionId.value) || null,
)
const activeStudent = computed(() => {
  const row = activeRow.value
  if (!row) return '答卷'
  return `${row.fullName || row.username || `学生 #${row.studentId}`} · 第 ${row.attemptNumber || 1} 次`
})
const statusLabel = computed(() =>
  session.value?.status === 'GRADED' ? '已阅卷' : '待阅卷',
)

const answers = computed<Record<string, unknown>>(() => parseJson(session.value?.answers))
const savedScores = computed<Record<string, unknown>>(() => parseJson(session.value?.questionScores))
const savedComments = computed<Record<string, string>>(
  () => parseJson(session.value?.questionComments) as Record<string, string>,
)

function parseJson(value: unknown): Record<string, unknown> {
  if (!value) return {}
  if (typeof value === 'object') return value as Record<string, unknown>
  try {
    const parsed = JSON.parse(String(value))
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}

const questionKey = (item: QuestionItem) => String(item.id)
/** 自动阅卷只判客观题，其余题型都要教师补分 */
const SUBJECTIVE_TYPES = new Set(['SHORT_ANSWER', 'ESSAY', 'FILL_BLANK'])
const isSubjective = (item: QuestionItem) =>
  SUBJECTIVE_TYPES.has(String(item.type ?? '').toUpperCase())

function typeLabel(type?: string): string {
  switch (String(type ?? '').toUpperCase()) {
    case 'SINGLE_CHOICE':
      return '单选题'
    case 'MULTIPLE_CHOICE':
      return '多选题'
    case 'TRUE_FALSE':
      return '判断题'
    case 'SHORT_ANSWER':
      return '简答题'
    case 'ESSAY':
      return '论述题'
    case 'FILL_BLANK':
      return '填空题'
    default:
      return type || '题目'
  }
}

function answerText(item: QuestionItem): string {
  const raw = answers.value[questionKey(item)]
  if (raw == null || raw === '') return '（未作答）'
  if (Array.isArray(raw)) return raw.length ? raw.join('、') : '（未作答）'
  if (typeof raw === 'boolean') return raw ? '正确' : '错误'
  return String(raw)
}

function correctText(item: QuestionItem): string {
  const raw = item.correctAnswers
  if (raw == null || raw === '') return '—'
  if (Array.isArray(raw)) return raw.join('、')
  if (typeof raw === 'object') {
    const record = raw as Record<string, unknown>
    return String(record.answer ?? record.value ?? JSON.stringify(raw))
  }
  const text = String(raw)
  // 后端把答案存成 JSON 字面量（如 "\"B\""），展示时去掉多余引号
  if (text.length > 1 && text.startsWith('"') && text.endsWith('"')) {
    try {
      return String(JSON.parse(text))
    } catch {
      return text.slice(1, -1)
    }
  }
  return text
}

function fmtScore(value: unknown): string {
  return value == null ? '—' : String(value)
}

function fmtTime(value: unknown): string {
  const text = String(value ?? '')
  return text ? text.replace('T', ' ').slice(0, 16) : '—'
}

function back() {
  router.push('/teacher/courses')
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = (unwrap<SessionRow[]>(await examApi.getPaperSessions(paperId)) ?? []) as SessionRow[]
  } catch (error) {
    logger.error('加载考试会话失败', error)
    loadError.value = '考试答卷加载失败，请稍后重试'
  } finally {
    loadingSessions.value = false
  }
}

async function loadSession(sessionId: number) {
  loadingSession.value = true
  try {
    let detail = unwrap<Record<string, unknown>>(await examApi.getExamResult(sessionId)) ?? {}
    // 人工阅卷要求会话已自动阅卷（GRADED）；未阅卷的先触发一次客观题判分
    if (String(detail.status ?? '') !== 'GRADED') {
      await examApi.autoGrade(sessionId)
      detail = unwrap<Record<string, unknown>>(await examApi.getExamResult(sessionId)) ?? {}
    }
    session.value = detail
    syncDrafts()
  } catch (error) {
    logger.error('加载答卷详情失败', error)
    loadError.value = '答卷加载失败，请稍后重试'
  } finally {
    loadingSession.value = false
  }
}

function syncDrafts() {
  for (const key of Object.keys(draftScores)) delete draftScores[key]
  for (const key of Object.keys(draftComments)) delete draftComments[key]
  for (const item of questions.value) {
    const key = questionKey(item)
    const savedScore = savedScores.value[key]
    draftScores[key] = savedScore == null ? 0 : Number(savedScore)
    draftComments[key] = String(savedComments.value[key] ?? '')
  }
}

async function selectSession(row: SessionRow) {
  activeSessionId.value = row.sessionId
  await loadSession(row.sessionId)
}

async function saveQuestion(item: QuestionItem) {
  if (!activeSessionId.value) return
  const key = questionKey(item)
  const score = Number(draftScores[key] ?? 0)
  const max = item.score == null ? null : Number(item.score)
  if (!Number.isFinite(score) || score < 0) {
    toast('得分必须是不小于 0 的数字', 'warn')
    return
  }
  if (max != null && score > max) {
    toast(`得分不能超过本题满分 ${max}`, 'warn')
    return
  }
  savingQuestion.value = key
  try {
    await examApi.manualGrade(activeSessionId.value, {
      questionId: Number(item.id),
      score,
      comment: draftComments[key] ?? '',
    })
    toast('本题评分已保存', 'ok')
    await loadSession(activeSessionId.value)
    await loadSessions()
  } catch (error) {
    logger.error('保存评分失败', error)
    toast('评分保存失败，请稍后重试', 'danger')
  } finally {
    savingQuestion.value = null
  }
}

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    const paper = unwrap<Record<string, unknown>>(await examApi.getExamPaperById(paperId)) ?? {}
    paperTitle.value = String(paper.title ?? '')
    const list = unwrap<QuestionItem[]>(await examApi.getExamQuestions(paperId)) ?? []
    questions.value = Array.isArray(list) ? list : []
    await loadSessions()
    if (!activeSessionId.value && sessions.value.length) {
      await selectSession(sessions.value[0])
    }
  } catch (error) {
    logger.error('加载阅卷数据失败', error)
    loadError.value = error instanceof Error ? error.message : '阅卷数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.review-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.head-main {
  flex: 1;
}
.head-main h1 {
  margin: 0;
  font-size: 18px;
}
.head-sub {
  margin: 3px 0 0;
  color: var(--ink-3);
  font-size: 12.5px;
}
.review-body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
.session-pane {
  padding: 14px;
  position: sticky;
  top: 12px;
}
.session-pane h2 {
  margin: 0 0 10px;
  font-size: 14px;
}
.session-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 70vh;
  overflow: auto;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  cursor: pointer;
}
.session-item:hover {
  border-color: var(--line-strong);
}
.session-item.is-active {
  border-color: var(--accent);
  background: var(--surface-2);
}
.session-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.session-main b {
  font-size: 13px;
}
.session-meta {
  color: var(--ink-3);
  font-size: 11.5px;
}
.sheet-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.sheet-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}
.sheet-head h2 {
  margin: 0;
  font-size: 15px;
}
.question-card {
  padding: 16px 18px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.q-points {
  color: var(--ink-3);
  font-size: 12px;
}
.q-content {
  margin: 0 0 12px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.answer-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.answer-block {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  background: var(--surface-2);
}
.label {
  display: block;
  color: var(--ink-3);
  font-size: 11.5px;
  margin-bottom: 4px;
}
.answer-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}
.q-foot {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.score-field,
.comment-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--ink-3);
}
.score-field .input {
  width: 96px;
}
.comment-field {
  flex: 1;
}
.foot-space {
  flex: 1;
}
</style>
