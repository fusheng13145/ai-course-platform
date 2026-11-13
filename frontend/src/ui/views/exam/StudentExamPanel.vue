<template>
  <div class="exam-panel">
    <!-- 列表 -->
    <section v-if="viewMode === 'list'" class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">EXAMS / MINE</p>
          <h2>{{ scopeLabel }}</h2>
        </div>
        <button class="btn btn-sm btn-ghost" :disabled="listLoading" @click="loadExamList">
          重新加载
        </button>
      </header>

      <div class="filter-row">
        <input
          v-model="searchKeyword"
          class="input w-grow"
          type="search"
          placeholder="按试卷名称搜索"
          aria-label="按试卷名称搜索"
        />
        <select v-model="statusFilter" class="input w-sm" aria-label="状态筛选">
          <option value="">全部状态</option>
          <option value="available">可参加</option>
          <option value="completed">已完成</option>
          <option value="expired">已过期</option>
        </select>
      </div>

      <p v-if="listLoading" class="hint">考试列表加载中…</p>
      <p v-else-if="loadError" class="callout danger">{{ loadError }}</p>
      <p v-else-if="!filteredExams.length" class="hint">
        {{ courseId ? '本课程暂无可参加的考试。' : '当前没有可参加的考试。' }}
      </p>

      <ul v-else class="exam-list">
        <li v-for="row in examRows" :key="row.key" class="exam-row">
          <div class="exam-main">
            <b>{{ row.title }}</b>
            <span class="exam-meta">
              {{
                [row.course, `${row.minutes} 分钟`, `${row.questions} 题`]
                  .filter(Boolean)
                  .join(' · ')
              }}
            </span>
          </div>
          <span v-if="row.when" class="chip exam-when" :class="row.when.cls">{{
            row.when.text
          }}</span>
          <span class="chip exam-status" :class="row.statusCls">{{ row.status }}</span>
          <div class="row-actions">
            <button
              class="btn btn-sm"
              :class="row.exam.completed ? 'btn-ghost' : 'btn-primary'"
              :disabled="!row.joinable"
              :title="row.joinable ? '' : row.status"
              @click="onEnter(row.exam)"
            >
              {{ row.exam.completed ? '已完成' : '开始作答' }}
            </button>
          </div>
        </li>
      </ul>
    </section>

    <!-- 答题 -->
    <section v-else-if="viewMode === 'exam'" class="card sheet">
      <header class="card-head">
        <div>
          <p class="card-kicker">SESSION / {{ sessionId ?? '—' }}</p>
          <h2>{{ currentExamPaper?.title || currentExamPaper?.name || '正在作答' }}</h2>
        </div>
        <div class="timer" :class="{ warn: remainingMinutes <= 5 }">
          <span class="timer-label">剩余</span>
          <b>{{ countdownDisplay }}</b>
        </div>
      </header>

      <p class="sheet-progress">
        已作答 {{ answeredCount }} / {{ questions.length }}
        <span class="hint-inline">作答每 30 秒自动保存到服务端</span>
      </p>

      <nav class="qnav" aria-label="题目导航">
        <button
          v-for="(question, index) in questions"
          :key="`nav-${question.id ?? index}`"
          class="qnav-btn"
          :class="{ 'is-active': index === currentIndex, 'is-done': isQuestionAnswered(index) }"
          :aria-current="index === currentIndex ? 'true' : undefined"
          @click="goToQuestion(index)"
        >
          {{ index + 1 }}
        </button>
      </nav>

      <div v-if="questionsLoading" class="hint">题目加载中…</div>
      <div v-else-if="!currentQuestion" class="hint">题目未能加载。</div>
      <article v-else class="question">
        <p class="q-head">
          <span class="chip">{{ getQuestionTypeName(currentQuestion.type) }}</span>
          <b>第 {{ currentIndex + 1 }} 题</b>
          <span class="q-score">{{ currentQuestion.score || defaultScore }} 分</span>
        </p>
        <p class="q-body">{{ currentQuestion.content || currentQuestion.questionContent }}</p>

        <div v-if="currentQuestion.type === 'SINGLE_CHOICE'" class="q-options">
          <label v-for="(opt, i) in parseOptions(currentQuestion.options)" :key="`s-${i}`">
            <input
              type="radio"
              name="single-choice"
              :checked="answers[currentIndex] === letter(i)"
              :disabled="submitting"
              @change="answers[currentIndex] = letter(i)"
            />
            <span>{{ letter(i) }}. {{ opt }}</span>
          </label>
        </div>

        <div v-else-if="currentQuestion.type === 'MULTIPLE_CHOICE'" class="q-options">
          <label v-for="(opt, i) in parseOptions(currentQuestion.options)" :key="`m-${i}`">
            <input
              type="checkbox"
              :checked="isMultiChecked(i)"
              :disabled="submitting"
              @change="toggleMulti(i)"
            />
            <span>{{ letter(i) }}. {{ opt }}</span>
          </label>
        </div>

        <div v-else-if="currentQuestion.type === 'TRUE_FALSE'" class="q-judge">
          <button
            class="btn"
            :class="answers[currentIndex] === true ? 'btn-primary' : 'btn-ghost'"
            :disabled="submitting"
            @click="answers[currentIndex] = true"
          >
            正确
          </button>
          <button
            class="btn"
            :class="answers[currentIndex] === false ? 'btn-danger' : 'btn-ghost'"
            :disabled="submitting"
            @click="answers[currentIndex] = false"
          >
            错误
          </button>
        </div>

        <textarea
          v-else-if="currentQuestion.type === 'SHORT_ANSWER'"
          v-model="textAnswers[currentIndex]"
          class="input q-text"
          rows="6"
          placeholder="请输入您的答案"
          :disabled="submitting"
        />

        <p v-else class="callout warn">
          未识别的题型「{{ currentQuestion.type }}」，本卷不提供作答控件；请交卷后由教师处理。
        </p>
      </article>

      <footer class="sheet-foot">
        <button class="btn btn-ghost" :disabled="currentIndex === 0" @click="prevQuestion">
          上一题
        </button>
        <button
          class="btn btn-ghost"
          :disabled="currentIndex >= questions.length - 1"
          @click="nextQuestion"
        >
          下一题
        </button>
        <span class="foot-space"></span>
        <button class="btn btn-ghost" :disabled="submitting" @click="confirmExitExam">退出</button>
        <button class="btn btn-primary" :disabled="submitting" @click="confirmSubmit">
          {{ submitting ? '提交中…' : '交卷' }}
        </button>
      </footer>
    </section>

    <!-- 结果 -->
    <section v-else class="card result">
      <p class="card-kicker">RESULT / 成绩</p>
      <div class="score" :class="getResultClass()">
        <b>{{ scoreText }}</b>
        <small v-if="percentageText">百分制 {{ percentageText }}</small>
      </div>
      <p class="result-message">{{ resultMessage }}</p>
      <div class="row-actions">
        <button class="btn btn-ghost" @click="backToList">返回考试列表</button>
        <button class="btn btn-sm" @click="viewDetailResult">成绩详情</button>
      </div>
    </section>

    <Modal
      v-model:open="askState.open"
      :title="askState.title"
      kicker="RECORD / CONFIRM"
      width="480px"
      @close="resolveAsk(false)"
    >
      <p class="modal-text">{{ askState.text }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="resolveAsk(false)">{{ askState.cancelText }}</button>
        <button class="btn btn-danger" @click="resolveAsk(true)">{{ askState.confirmText }}</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useStudentExams } from '@/composables/useStudentExams'
import { buildDangerMessage, type DangerConfirmOptions } from '@/utils/dangerMessage'
import { getAllMyCourses } from '@/api/course'

const props = defineProps<{ courseId?: number | null }>()
const { toast } = useUi()

const askState = reactive({
  open: false,
  title: '确认',
  text: '',
  confirmText: '确定',
  cancelText: '取消',
  resolve: null as ((value: boolean) => void) | null,
})

function ask(input: {
  message: string
  title?: string
  confirmText?: string
  cancelText?: string
}): Promise<boolean> {
  if (askState.resolve) askState.resolve(false)
  askState.title = input.title || '确认'
  askState.text = input.message
  askState.confirmText = input.confirmText || '确定'
  askState.cancelText = input.cancelText || '取消'
  askState.open = true
  return new Promise((resolve) => {
    askState.resolve = resolve
  })
}

function resolveAsk(value: boolean) {
  askState.open = false
  const settle = askState.resolve
  askState.resolve = null
  settle?.(value)
}

/** 的 toast 色调与 EP 的四档不同，这里做一次语义映射 */
const notify = {
  info: (message: string) => toast(message, 'info'),
  success: (message: string) => toast(message, 'ok'),
  warning: (message: string) => toast(message, 'warn'),
  error: (message: string) => toast(message, 'danger'),
}

const exams = useStudentExams({
  notify,
  confirmStart: (message, title) => ask({ message, title }),
  dangerConfirm: (options: DangerConfirmOptions) =>
    ask({
      message: buildDangerMessage(options),
      title: `${options.verb}确认`,
      confirmText: options.confirmText || options.verb,
      cancelText: options.cancelText || '取消',
    }),
})

const {
  viewMode,
  searchKeyword,
  statusFilter,
  courseIdFilter,
  listLoading,
  loadError,
  filteredExams,
  loadExamList,
  currentExamPaper,
  sessionId,
  submitting,
  questions,
  questionsLoading,
  currentIndex,
  answers,
  multiAnswers,
  textAnswers,
  defaultScore,
  remainingMinutes,
  countdownDisplay,
  currentQuestion,
  examResult,
  resultMessage,
  goToQuestion,
  prevQuestion,
  nextQuestion,
  isQuestionAnswered,
  getQuestionTypeName,
  parseOptions,
  confirmSubmit,
  confirmExitExam,
  backToList,
  viewDetailResult,
  getResultClass,
  examJoinReason,
  isExamJoinable,
  enterExam,
} = exams

courseIdFilter.value = props.courseId ?? null
watch(
  () => props.courseId,
  (next) => {
    courseIdFilter.value = next ?? null
    loadExamList()
  },
)

const scopeLabel = computed(() =>
  props.courseId ? `本课程考试（${filteredExams.value.length}）` : '我的考试',
)
const answeredCount = computed(
  () => questions.value.filter((_, index) => isQuestionAnswered(index)).length,
)
const scoreText = computed(() => {
  const raw = examResult.value.totalScore
  return raw == null ? '批阅中' : `${Number(raw)} 分`
})
const percentageText = computed(() =>
  examResult.value.percentage == null ? '' : `${Number(examResult.value.percentage)}%`,
)

const letter = (index: number) => (index < 26 ? String.fromCharCode(65 + index) : String(index))
const examTitle = (exam: Record<string, unknown>) =>
  String(exam.title ?? exam.name ?? `试卷 #${exam.id ?? ''}`)
const statusLabel = (exam: Record<string, unknown>) => {
  const reason = examJoinReason(exam)
  if (reason === 'completed') return '已完成'
  if (reason === 'expired') return '已过期'
  if (reason === 'notEnrolled') return '未选课'
  if (reason === 'beforeStart') return '未开始'
  if (reason === 'afterEnd') return '已结束'
  return '可参加'
}
const statusChip = (exam: Record<string, unknown>) =>
  examJoinReason(exam) === 'none' ? 'chip-ok' : 'chip-mute'

// v2：全局考试列表带课程归属 chip——courseName 后端未给，用课程目录做一次 id→名称映射（失败静默降级为不显示）。
const courseNames = ref<Map<number, string>>(new Map())
onMounted(async () => {
  if (props.courseId != null) return
  try {
    const map = new Map<number, string>()
    for (const row of (await getAllMyCourses()) as Record<string, unknown>[]) {
      const id = Number(row?.courseId ?? row?.id)
      const name = String(row?.name ?? row?.courseName ?? '').trim()
      if (Number.isFinite(id) && id > 0 && name) map.set(id, name)
    }
    courseNames.value = map
  } catch {
    /* 名称缺失只是少了 chip，不影响考试列表本身 */
  }
})

const examCourseName = (exam: Record<string, unknown>): string => {
  const direct = String(exam.courseName ?? '').trim()
  if (direct) return direct
  const id = Number(exam.courseId)
  return (Number.isFinite(id) && courseNames.value.get(id)) || ''
}

/** 行内时间提示：把考试时间窗翻译成一句人话（sans 短语，时间数字随 sans）；拿不到日期返回 null。 */
const examWhen = (exam: Record<string, unknown>): { text: string; cls: string } | null => {
  const reason = examJoinReason(exam)
  const start = exam.startTime ? new Date(String(exam.startTime)).getTime() : NaN
  const end = exam.endTime ? new Date(String(exam.endTime)).getTime() : NaN
  const hm = (t: number) => {
    const d = new Date(t)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  if (reason === 'beforeStart' && Number.isFinite(start)) {
    const days = Math.floor((start - Date.now()) / 86400000)
    if (days >= 2) return { text: `${days} 天后开考`, cls: 'chip-mute' }
    if (days === 1) return { text: `明天 ${hm(start)} 开考`, cls: 'chip-warn' }
    return { text: `今天 ${hm(start)} 开考`, cls: 'chip-warn' }
  }
  if (reason === 'none' && Number.isFinite(end)) {
    const left = end - Date.now()
    if (left <= 0) return null
    const days = Math.floor(left / 86400000)
    if (days > 30) return null // 长期开放窗口（如 2099）不算倒计时，避免出现「26395 天后截止」这类噪音
    const hours = Math.floor(left / 3600000)
    if (hours < 24) return { text: `${Math.max(hours, 1)} 小时后截止`, cls: 'chip-warn' }
    return { text: `${days} 天后截止`, cls: 'chip-mute' }
  }
  return null
}

// 行模型：模板只读字段，不再在模板里反复调函数
const examRows = computed(() =>
  filteredExams.value.map((exam) => {
    const minutes = Number(exam.duration ?? exam.timeLimit ?? 60)
    const questions = exam.questionCount ?? exam.totalQuestions
    return {
      exam,
      key: String(exam.id ?? exam.paperId ?? exam.title),
      title: examTitle(exam),
      course: examCourseName(exam),
      minutes: Number.isFinite(minutes) ? minutes : 60,
      questions: questions == null ? '—' : String(questions),
      when: examWhen(exam),
      status: statusLabel(exam),
      statusCls: statusChip(exam),
      joinable: isExamJoinable(exam),
    }
  }),
)

function isMultiChecked(index: number) {
  const key = letter(index)
  const bucket = multiAnswers.value[currentIndex.value]
  return Array.isArray(bucket) && bucket.includes(key)
}

function toggleMulti(index: number) {
  const key = letter(index)
  const at = currentIndex.value
  const current = Array.isArray(multiAnswers.value[at]) ? [...multiAnswers.value[at]] : []
  const position = current.indexOf(key)
  if (position >= 0) current.splice(position, 1)
  else current.push(key)
  current.sort()
  multiAnswers.value[at] = current
}

function onEnter(exam: Record<string, unknown>) {
  enterExam(exam)
}
</script>

<style scoped>
.exam-list {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}
.exam-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-l3, 6px);
  background: var(--surface-2);
}
.exam-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.exam-meta,
.hint-inline,
.q-score {
  color: var(--ink-3);
  font-size: 12px;
}
.timer {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}
.timer b {
  font-size: 20px;
}
.timer.warn b {
  color: var(--danger);
}
.sheet-progress {
  margin: 12px 0 8px;
  font-size: 13px;
}
.qnav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}
.qnav-btn {
  min-width: 30px;
  padding: 4px 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-l3, 6px);
  background: transparent;
  color: var(--ink-2);
  font: inherit;
  cursor: pointer;
}
.qnav-btn.is-done {
  border-color: var(--accent);
  color: var(--accent);
}
.qnav-btn.is-active {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}
.question {
  border-top: 1px solid var(--line);
  padding-top: 14px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
}
.q-body {
  margin: 0 0 12px;
  white-space: pre-wrap;
  line-height: 1.7;
}
.q-options {
  display: grid;
  gap: 8px;
}
.q-options label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-l3, 6px);
  cursor: pointer;
}
.q-judge {
  display: flex;
  gap: 8px;
}
.q-text {
  width: 100%;
}
.sheet-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.foot-space {
  flex: 1;
}
.result {
  text-align: center;
}
.score b {
  display: block;
  font-size: 40px;
  font-variant-numeric: tabular-nums;
}
.result-message {
  color: var(--ink-2);
}
.result .row-actions {
  justify-content: center;
}
</style>
