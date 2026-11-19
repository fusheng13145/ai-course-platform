<template>
  <section class="practice-tab">
    <div class="course-tab-bar">
      <div>
        <h2>课程刷题</h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-sm btn-ghost" :disabled="loading || !enabled" @click="loadQuestions">
          换一组
        </button>
      </div>
    </div>

    <div v-if="loading" class="empty-state compact">
      <b>正在加载题目…</b>
    </div>

    <div v-else-if="!enabled" class="empty-state compact">
      <span class="e-ico">题</span>
      <b>教师尚未开放本课程题库刷题</b>
    </div>

    <div v-else-if="loadError" class="empty-state compact">
      <b>题目加载失败</b>
      <p>{{ loadError }}</p>
    </div>

    <div v-else-if="!questions.length" class="empty-state compact">
      <b>本课程题库暂无启用题目</b>
    </div>

    <template v-else>
      <div class="practice-list">
        <article
          v-for="(question, index) in questions"
          :key="question.id"
          class="card practice-card"
        >
          <header class="practice-head">
            <div>
              <span class="chip chip-info">{{ typeLabel(question.type) }}</span>
              <span class="chip chip-mute">第 {{ (page - 1) * pageSize + index + 1 }} 题</span>
            </div>
            <span class="practice-difficulty">{{ difficultyLabel(question.difficulty) }}</span>
          </header>

          <p class="practice-content">{{ question.content }}</p>
          <p v-if="question.knowledgePoints?.length" class="practice-kp">
            知识点：{{ question.knowledgePoints.join(' / ') }}
          </p>

          <div v-if="isChoice(question.type)" class="practice-options">
            <button
              v-for="(option, optionIndex) in question.options || []"
              :key="optionIndex"
              type="button"
              class="practice-option"
              :class="{ 'is-selected': choiceSelected(question, optionIndex) }"
              :disabled="Boolean(results[question.id])"
              @click="selectChoice(question, optionIndex)"
            >
              <b>{{ optionLetter(optionIndex) }}</b>
              <span>{{ option }}</span>
            </button>
          </div>

          <div v-else-if="isTrueFalse(question.type)" class="practice-options">
            <button
              type="button"
              class="practice-option"
              :class="{ 'is-selected': answers[question.id] === 'true' }"
              :disabled="Boolean(results[question.id])"
              @click="answers[question.id] = 'true'"
            >
              <b>√</b><span>正确</span>
            </button>
            <button
              type="button"
              class="practice-option"
              :class="{ 'is-selected': answers[question.id] === 'false' }"
              :disabled="Boolean(results[question.id])"
              @click="answers[question.id] = 'false'"
            >
              <b>×</b><span>错误</span>
            </button>
          </div>

          <label v-else class="practice-fill">
            <span>你的答案</span>
            <textarea
              v-model="answers[question.id]"
              class="textarea"
              rows="2"
              :disabled="Boolean(results[question.id])"
              placeholder="请输入答案"
            ></textarea>
          </label>

          <div class="practice-actions">
            <button
              class="btn btn-primary btn-sm"
              :disabled="submitting[question.id] || Boolean(results[question.id])"
              @click="submit(question)"
            >
              {{
                submitting[question.id] ? '判分中…' : results[question.id] ? '已提交' : '提交答案'
              }}
            </button>
            <span v-if="errors[question.id]" class="form-error">{{ errors[question.id] }}</span>
          </div>

          <div
            v-if="results[question.id]"
            class="practice-result"
            :class="{ 'is-correct': results[question.id]?.correct }"
          >
            <b>{{ results[question.id]?.correct ? '回答正确' : '回答错误' }}</b>
            <p>正确答案：{{ results[question.id]?.correctAnswer || '—' }}</p>
            <p v-if="results[question.id]?.explanation">{{ results[question.id]?.explanation }}</p>
          </div>
        </article>
      </div>

      <footer class="practice-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 题</span>
        <Pagination :page="page" :page-count="pageCount" :loading="loading" @turn="goPage" />
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Pagination from '../../../components/Pagination.vue'
import { getCourseById } from '@/api/course'
import { checkPracticeAnswer, getPracticeQuestions } from '@/api/question'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface PracticeQuestion {
  id: number
  courseId: number
  type: string
  content: string
  options?: string[]
  score?: number | null
  difficulty?: string | null
  knowledgePoints?: string[]
}

interface PracticeResult {
  questionId: number
  correct: boolean
  correctAnswer?: string
  explanation?: string
}

const props = defineProps<{ courseId: number }>()

const loading = ref(false)
const loadError = ref('')
const enabled = ref(false)
const questions = ref<PracticeQuestion[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const answers = reactive<Record<string, string>>({})
const results = reactive<Record<string, PracticeResult>>({})
const submitting = reactive<Record<string, boolean>>({})
const errors = reactive<Record<string, string>>({})

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const TYPE_LABELS: Record<string, string> = {
  SINGLE: '单选题',
  SINGLE_CHOICE: '单选题',
  MULTIPLE: '多选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  JUDGE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}

function typeLabel(type: unknown): string {
  return TYPE_LABELS[String(type || '').toUpperCase()] || String(type || '题目')
}

function difficultyLabel(value: unknown): string {
  const text = String(value || '')
  if (text === 'EASY') return '简单'
  if (text === 'MEDIUM') return '中等'
  if (text === 'HARD') return '困难'
  return text || '未标注'
}

function isChoice(type: unknown): boolean {
  const t = String(type || '').toUpperCase()
  return t === 'SINGLE' || t === 'SINGLE_CHOICE' || t === 'MULTIPLE' || t === 'MULTIPLE_CHOICE'
}

function isMulti(type: unknown): boolean {
  const t = String(type || '').toUpperCase()
  return t === 'MULTIPLE' || t === 'MULTIPLE_CHOICE'
}

function isTrueFalse(type: unknown): boolean {
  const t = String(type || '').toUpperCase()
  return t === 'TRUE_FALSE' || t === 'JUDGE'
}

function optionLetter(index: number): string {
  return String.fromCharCode(65 + index)
}

function choiceSelected(question: PracticeQuestion, index: number): boolean {
  const current = String(answers[question.id] || '')
  const letter = optionLetter(index)
  return isMulti(question.type)
    ? current.split(',').filter(Boolean).includes(letter)
    : current === letter
}

function selectChoice(question: PracticeQuestion, index: number): void {
  const letter = optionLetter(index)
  if (isMulti(question.type)) {
    const current = String(answers[question.id] || '')
      .split(',')
      .filter(Boolean)
    answers[question.id] = current.includes(letter)
      ? current
          .filter((item) => item !== letter)
          .sort()
          .join(',')
      : [...current, letter].sort().join(',')
    return
  }
  answers[question.id] = letter
}

async function loadCourseSetting(): Promise<void> {
  try {
    const raw = (unwrap(await getCourseById(props.courseId)) || {}) as Record<string, unknown>
    const flag = raw.questionPracticeEnabled
    enabled.value = flag === true || flag === 1 || flag === '1'
  } catch (error) {
    logger.error('加载课程刷题开关失败', error)
    enabled.value = false
  }
}

async function loadQuestions(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const payload = unwrap<{ list?: PracticeQuestion[]; total?: number }>(
      await getPracticeQuestions({ courseId: props.courseId, page: page.value, size: pageSize }),
    )
    questions.value = Array.isArray(payload?.list) ? payload.list : []
    total.value = Number(payload?.total || questions.value.length)
  } catch (error) {
    logger.error('加载学生题库失败', error)
    questions.value = []
    total.value = 0
    loadError.value = '请确认已选课且教师已开放本课程题库刷题。'
  } finally {
    loading.value = false
  }
}

function goPage(delta: number): void {
  const next = Math.min(pageCount.value, Math.max(1, page.value + delta))
  if (next === page.value) return
  page.value = next
  void loadQuestions()
}

async function submit(question: PracticeQuestion): Promise<void> {
  const answer = String(answers[question.id] || '').trim()
  if (!answer) {
    errors[question.id] = '请先作答后再提交'
    return
  }
  submitting[question.id] = true
  errors[question.id] = ''
  try {
    const result = (unwrap(
      await checkPracticeAnswer({
        questionId: question.id,
        answer,
      }),
    ) || {}) as PracticeResult
    results[question.id] = result
  } catch (error) {
    logger.error('提交练习答案失败', error)
    errors[question.id] = '判分失败，请稍后重试'
  } finally {
    submitting[question.id] = false
  }
}

onMounted(async () => {
  await loadCourseSetting()
  if (enabled.value) {
    await loadQuestions()
  }
})
</script>

<style scoped>
.practice-tab {
  display: grid;
  gap: 16px;
}

.practice-list {
  display: grid;
  gap: 14px;
}

.practice-card {
  display: grid;
  gap: 12px;
  padding: 18px;
}

.practice-head,
.practice-actions,
.practice-result,
.practice-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.practice-head > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.practice-difficulty {
  color: var(--muted, #667085);
  font-size: 12px;
}

.practice-content {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
  white-space: pre-wrap;
}

.practice-kp {
  margin: 0;
  color: var(--muted, #667085);
  font-size: 12px;
}

.practice-options {
  display: grid;
  gap: 8px;
}

.practice-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line, #d0d5dd);
  border-radius: var(--r-card);
  background: var(--surface, #fff);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.practice-option b {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: var(--r-circle);
  background: var(--surface-2, #f2f4f7);
  font-size: 12px;
}

.practice-option.is-selected {
  border-color: var(--primary, #2563eb);
  background: var(--primary-soft, #eff6ff);
}

.practice-option:disabled {
  cursor: default;
  opacity: 0.75;
}

.practice-fill {
  display: grid;
  gap: 6px;
  font-size: 14px;
}

.practice-result {
  display: grid;
  justify-content: stretch;
  padding: 12px;
  border-radius: var(--r-card);
  background: #fff7ed;
  color: #9a3412;
  font-size: 14px;
}

.practice-result.is-correct {
  background: #ecfdf3;
  color: #027a48;
}

.practice-result p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}

.practice-foot {
  padding-top: 4px;
  color: var(--muted, #667085);
  font-size: 14px;
}
</style>
