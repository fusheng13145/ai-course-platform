<template>
  <!-- 沉浸式答题页：独立路由，不挂 Shell 侧边栏，全屏专注作答 -->
  <div class="exam-room">
    <header class="room-head">
      <button class="btn btn-ghost btn-sm" @click="exitRoom">返回</button>
      <h1>{{ currentExamPaper?.title || '在线考试' }}</h1>
      <div v-if="viewMode === 'exam'" class="timer" :class="{ warn: remainingMinutes <= 5 }">
        <span class="timer-label">剩余</span>
        <b>{{ countdownDisplay }}</b>
      </div>
    </header>

    <main class="room-body">
      <p v-if="loadingExam" class="hint">正在准备考试…</p>
      <p v-else-if="loadError" class="callout danger">{{ loadError }}</p>

      <template v-else-if="viewMode === 'exam'">
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

          <div v-else-if="currentQuestion.type === 'FILL_BLANK'" class="q-fill">
            <label
              v-for="blankIndex in fillBlankSlots(currentIndex)"
              :key="`fill-${blankIndex}`"
              class="fill-row"
            >
              <span class="fill-label">第 {{ blankIndex + 1 }} 空</span>
              <input
                class="input"
                type="text"
                :value="fillAnswers[currentIndex]?.[blankIndex] ?? ''"
                :disabled="submitting"
                :placeholder="`请填写第 ${blankIndex + 1} 空`"
                @input="setFillAnswer(currentIndex, blankIndex, $event)"
              />
            </label>
            <p v-if="fillBlankSlots(currentIndex).length > 1" class="hint-inline">
              本题共 {{ fillBlankSlots(currentIndex).length }} 空，请逐空填写
            </p>
          </div>

          <textarea
            v-else-if="currentQuestion.type === 'SHORT_ANSWER' || currentQuestion.type === 'ESSAY'"
            v-model="textAnswers[currentIndex]"
            class="input q-text"
            :rows="currentQuestion.type === 'ESSAY' ? 8 : 6"
            :placeholder="
              currentQuestion.type === 'ESSAY' ? '请分点作答，写清论点与理由' : '请输入您的答案'
            "
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
          <button class="btn btn-ghost" :disabled="submitting" @click="exitRoom">退出</button>
          <button class="btn btn-primary" :disabled="submitting" @click="confirmSubmit">
            {{ submitting ? '提交中…' : '交卷' }}
          </button>
        </footer>
      </template>

      <section v-else-if="viewMode === 'result'" class="result">
        <div class="score" :class="getResultClass()">
          <b>{{ scoreText }}</b>
          <small v-if="percentageText">百分制 {{ percentageText }}</small>
        </div>
        <p class="result-message">{{ resultMessage }}</p>
        <div class="room-actions">
          <button class="btn btn-primary" @click="backToCourse">返回考试列表</button>
          <button class="btn btn-ghost" @click="viewDetailResult">成绩详情</button>
        </div>
      </section>
    </main>

    <Modal
      v-model:open="askState.open"
      :title="askState.title"
      width="480px"
      @close="resolveAsk(false)"
    >
      <p class="modal-text">{{ askState.text }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="resolveAsk(false)">{{ askState.cancelText }}</button>
        <button class="btn btn-danger" @click="resolveAsk(true)">{{ askState.confirmText }}</button>
      </template>
    </Modal>

    <!-- 本页在 Shell 之外，toast 渲染器必须自己挂，否则恢复/交卷提示看不到 -->
    <Toasts />
  </div>
</template>

<script setup lang="ts">
// 本页在 Shell 之外，直接引用已收敛的统一入口样式，避免依赖已删除的拆分文件。
import '../../styles/app.css'

import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Modal from '../../components/Modal.vue'
import Toasts from '../../components/Toasts.vue'
import { useUi } from '../../composables/ui'
import { useRoot } from '../../composables/useRoot'
import { useStudentExams } from '@/composables/useStudentExams'
import { buildDangerMessage, type DangerConfirmOptions } from '@/utils/dangerMessage'
import examApi from '@/api/exam'
import { unwrap } from '@/utils/response'

// 本页挂在 Shell 之外，需要自己把主题属性（body[data-app='ui'] 等）挂上，
// 否则 consistency.css 里的全局组件样式全部不生效
const theme = ref<'light' | 'dark'>(localStorage.getItem('theme') === 'dark' ? 'dark' : 'light')
useRoot(theme, 'exam-room')

const route = useRoute()
const router = useRouter()
const { toast } = useUi()

const paperId = Number(route.params.paperId)
const loadingExam = ref(true)
const loadError = ref('')
/** 试卷归属课程，用于交卷/退出后回到课程考试列表 */
let courseId: number | null = null

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

const notify = {
  info: (message: string) => toast(message, 'info'),
  success: (message: string) => toast(message, 'ok'),
  warning: (message: string) => toast(message, 'warn'),
  error: (message: string) => toast(message, 'danger'),
}

const exams = useStudentExams({
  notify,
  // 跳转进入本页即代表学生已确认开考，不再二次弹窗
  confirmStart: () => Promise.resolve(true),
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
  currentExamPaper,
  submitting,
  questions,
  questionsLoading,
  currentIndex,
  answers,
  multiAnswers,
  textAnswers,
  fillAnswers,
  countFillBlanks,
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
  viewDetailResult,
  getResultClass,
} = exams

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

/** 填空题各空的序号数组；空位数由题干占位符推断（学生端拿不到标准答案个数） */
function fillBlankSlots(index: number): number[] {
  const question = questions.value[index]
  const count = countFillBlanks(question?.content ?? question?.questionContent)
  return Array.from({ length: count }, (_, i) => i)
}

function setFillAnswer(index: number, blankIndex: number, event: Event) {
  const target = event.target as HTMLInputElement | null
  const slots = fillBlankSlots(index).length
  // 补齐长度后再写入，避免先填第 2 空时数组只有 1 项
  const current = Array.from({ length: slots }, (_, i) => fillAnswers.value[index]?.[i] ?? '')
  current[blankIndex] = target?.value ?? ''
  fillAnswers.value[index] = current
}

function backToCourse() {
  router.push(courseId ? `/student/courses/${courseId}/exams` : '/student/courses')
}

async function exitRoom() {
  await confirmExitExam()
  // 退出成功会切回 list 并清空本地答题态；保存失败时仍停在答题页，不跳转
  if (viewMode.value === 'list') backToCourse()
}

onMounted(async () => {
  if (!Number.isFinite(paperId) || paperId <= 0) {
    loadError.value = '试卷参数无效，请返回课程页重新进入'
    loadingExam.value = false
    return
  }
  try {
    const paper = unwrap<Record<string, unknown>>(await examApi.getExamPaperById(paperId))
    if (!paper || paper.id == null) throw new Error('试卷不存在或不可访问')
    courseId = paper.courseId == null ? null : Number(paper.courseId)
    await exams.enterExam(paper)
    if (viewMode.value !== 'exam') {
      loadError.value = '未能进入考试，请返回课程页重试'
    }
  } catch (reason) {
    loadError.value = reason instanceof Error ? reason.message : '考试加载失败，请稍后重试'
  } finally {
    loadingExam.value = false
  }
})
</script>

<style scoped>
.exam-room {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}
.room-head {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
.room-head h1 {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.room-body {
  width: min(880px, 100%);
  margin: 0 auto;
  padding: 20px 24px 40px;
  flex: 1;
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
  margin: 4px 0 12px;
  font-size: 14px;
}
.hint-inline,
.q-score {
  color: var(--ink-3);
  font-size: 12px;
}
.qnav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}
.qnav-btn {
  min-width: 32px;
  padding: 5px 8px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-ctl);
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
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  padding: 20px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}
.q-body {
  margin: 0 0 16px;
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 15px;
}
.q-options {
  display: grid;
  gap: 10px;
}
.q-options label {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  cursor: pointer;
}
.q-options label:hover {
  border-color: var(--line-strong);
}
.q-judge {
  display: flex;
  gap: 10px;
}
.q-fill {
  display: grid;
  gap: 10px;
}
.fill-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fill-label {
  flex: none;
  min-width: 52px;
  color: var(--ink-3);
  font-size: 13px;
}
.fill-row .input {
  flex: 1;
}
.q-text {
  width: 100%;
}
.sheet-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
}
.foot-space {
  flex: 1;
}
.result {
  text-align: center;
  padding: 40px 0;
}
.score b {
  display: block;
  font-size: 48px;
  font-variant-numeric: tabular-nums;
}
.result-message {
  color: var(--ink-2);
  margin: 12px 0 24px;
}
.room-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
