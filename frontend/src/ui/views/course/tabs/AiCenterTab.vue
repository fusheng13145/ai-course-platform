<template>
  <section class="ai-center">
    <div class="course-tab-bar">
      <div>
        <h2>{{ isStudent ? 'AI 学习助手' : 'AI 教学助手' }}</h2>
      </div>
      <div class="course-tab-actions">
        <span class="head-hint">{{
          isStudent ? '回答由模型生成，仅供学习参考' : '回答由模型生成，需人工复核后再用于教学'
        }}</span>
      </div>
    </div>

    <div class="tabs" role="tablist" aria-label="AI 能力">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        :class="{ 'is-active': active === tab.key }"
        role="tab"
        :aria-selected="active === tab.key"
        @click="active = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 智能答疑 -->
    <section v-show="active === 'qa'" class="card">
      <header class="card-head">
        <div>
          <h2>
            智能答疑 <span class="chip chip-mute">{{ messages.length }} 条</span>
          </h2>
        </div>
        <div class="course-tab-actions">
          <button class="btn btn-ghost" :disabled="!sessionId" @click="resetQa">新会话</button>
        </div>
      </header>
      <div v-if="messages.length" class="msg-list">
        <article v-for="(msg, index) in messages" :key="index" :class="['msg', `msg-${msg.role}`]">
          <p class="msg-body">{{ msg.content }}</p>
          <ul v-if="msg.sources?.length" class="src-list">
            <li v-for="(source, sIndex) in msg.sources" :key="sIndex">
              <b>{{ source.documentName || `资料 #${sIndex + 1}` }}</b>
              <span>{{ source.snippet || '' }}</span>
            </li>
          </ul>
        </article>
      </div>
      <div v-else class="empty-state compact">
        <b>还没有提问</b>
        <p>就本课程的学习内容提问，回答会带上检索到的资料出处。</p>
      </div>
      <footer class="qa-foot">
        <textarea
          v-model="qaInput"
          class="textarea"
          rows="2"
          :disabled="qaSending"
          placeholder="例如：子网划分时主机位数怎么算？"
        ></textarea>
        <button class="btn btn-primary" :disabled="qaSending || !qaInput.trim()" @click="askQa">
          {{ qaSending ? '思考中…' : '提问' }}
        </button>
      </footer>
    </section>

    <!-- 薄弱点分析 -->
    <section v-show="active === 'weak'" class="card">
      <header class="card-head">
        <div>
          <h2>
            薄弱点分析 <span class="chip chip-mute">{{ weakPoints.length }} 项</span>
          </h2>
        </div>
        <button class="btn btn-ghost" :disabled="weakLoading" @click="loadWeak">
          {{ weakLoading ? '分析中…' : '重新分析' }}
        </button>
      </header>
      <p v-if="weakError" class="form-error">{{ weakError }}</p>
      <p v-if="weakSummary" class="ai-summary">{{ weakSummary }}</p>
      <ul v-if="weakPoints.length" class="item-list">
        <li v-for="(item, index) in weakPoints" :key="index">
          <span class="nb-name">{{ itemText(item) }}</span>
          <span class="chip" :class="levelChip(itemLevel(item))">{{
            levelText(itemLevel(item))
          }}</span>
        </li>
      </ul>
      <div v-else-if="!weakLoading && !weakError" class="empty-state compact">
        <b>没有可报告的薄弱点</b>
        <p>学习数据积累后重试。</p>
      </div>
      <div v-else-if="weakLoading" class="empty-state compact"><b>正在分析…</b></div>
    </section>

    <!-- 学习建议 -->
    <section v-show="active === 'advice'" class="card">
      <header class="card-head">
        <div>
          <h2>
            学习建议 <span class="chip chip-mute">{{ adviceItems.length }} 条</span>
          </h2>
        </div>
        <button class="btn btn-ghost" :disabled="adviceLoading" @click="loadAdvice">
          {{ adviceLoading ? '生成中…' : '重新生成' }}
        </button>
      </header>
      <p v-if="adviceError" class="form-error">{{ adviceError }}</p>
      <p v-if="adviceSummary" class="ai-summary">{{ adviceSummary }}</p>
      <ol v-if="adviceItems.length" class="advice-list">
        <li v-for="(item, index) in adviceItems" :key="index">
          <div class="advice-head">
            <b>{{ item.title || `建议 ${index + 1}` }}</b>
            <span class="chip" :class="levelChip(item.level)">{{ levelText(item.level) }}</span>
          </div>
          <p v-if="item.detail">{{ item.detail }}</p>
        </li>
      </ol>
      <div v-else-if="!adviceLoading && !adviceError" class="empty-state compact">
        <b>暂无建议</b>
      </div>
      <div v-else-if="adviceLoading" class="empty-state compact"><b>正在生成建议…</b></div>
    </section>

    <!-- 作文批改 -->
    <section v-show="active === 'essay'" class="card">
      <header class="card-head">
        <div>
          <h2>作文批改</h2>
        </div>
      </header>
      <textarea
        v-model="essayText"
        class="textarea"
        rows="6"
        placeholder="粘贴需要批改的文本"
      ></textarea>
      <div class="course-tab-actions essay-actions">
        <button
          class="btn btn-primary"
          :disabled="essayLoading || !essayText.trim()"
          @click="correctEssay"
        >
          {{ essayLoading ? '批改中…' : '批改' }}
        </button>
        <button
          class="btn btn-ghost"
          :disabled="polishLoading || !essayText.trim()"
          @click="polishEssay"
        >
          {{ polishLoading ? '润色中…' : '润色并回填' }}
        </button>
      </div>
      <p v-if="essayError" class="form-error">{{ essayError }}</p>
      <div v-if="essayResult" class="callout">
        <span>RESULT</span>
        <p class="pre-wrap">{{ essayResult }}</p>
      </div>
    </section>

    <!-- 学习路径 -->
    <section v-show="active === 'path'" class="card">
      <header class="card-head">
        <div>
          <h2>学习路径生成</h2>
        </div>
      </header>
      <div class="form-grid">
        <label class="field span-2">
          <span>学习目标 *</span>
          <input
            v-model="pathGoal"
            class="input"
            placeholder="例如：两周内补齐网络层丢包与拥塞控制"
          />
        </label>
      </div>
      <div class="course-tab-actions essay-actions">
        <button
          class="btn btn-primary"
          :disabled="pathLoading || !pathGoal.trim()"
          @click="runPath"
        >
          {{ pathLoading ? '生成中…' : '生成路径' }}
        </button>
      </div>
      <p v-if="pathError" class="form-error">{{ pathError }}</p>
      <div v-if="pathResult" class="callout">
        <span>{{ pathResult.status || 'RESULT' }}</span>
        <p>{{ pathResult.message || '（无摘要）' }}</p>
      </div>
      <details v-if="pathRaw" class="raw">
        <summary>查看模型返回原文</summary>
        <pre>{{ pathRaw }}</pre>
      </details>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useUi } from '../../../composables/ui'
import aiApi from '@/api/ai'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface Source {
  documentName?: string
  snippet?: string
  [key: string]: unknown
}

interface Message {
  role: 'user' | 'ai'
  content: string
  sources?: Source[]
}

const AI_TABS = [
  { key: 'qa', label: '智能答疑' },
  { key: 'weak', label: '薄弱点分析' },
  { key: 'advice', label: '学习建议' },
  { key: 'essay', label: '作文批改', teacherOnly: true },
  { key: 'path', label: '学习路径' },
] as const

// 学生复用同一套真接口，只裁掉教师专属的「作文批改」（其结论由教师采纳）
const props = defineProps<{ courseId: number; mode?: 'teacher' | 'student' }>()
const isStudent = computed(() => props.mode === 'student')
const visibleTabs = computed(() =>
  AI_TABS.filter((tab) => !('teacherOnly' in tab && tab.teacherOnly && isStudent.value)),
)
const { toast } = useUi()

const active = ref<(typeof AI_TABS)[number]['key']>('qa')

const messages = ref<Message[]>([])
const qaInput = ref('')
const qaSending = ref(false)
const sessionId = ref('')

const weakPoints = ref<Record<string, unknown>[]>([])
const weakSummary = ref('')
const weakLoading = ref(false)
const weakError = ref('')

const adviceItems = ref<{ title?: string; detail?: string; level?: string }[]>([])
const adviceSummary = ref('')
const adviceLoading = ref(false)
const adviceError = ref('')

const essayText = ref('')
const essayResult = ref('')
const essayLoading = ref(false)
const polishLoading = ref(false)
const essayError = ref('')

const pathGoal = ref('')
const pathLoading = ref(false)
const pathError = ref('')
const pathResult = ref<{ status?: string; message?: string } | null>(null)
const pathRaw = ref('')

function levelText(level: unknown): string {
  const key = String(level ?? '')
  if (key === 'HIGH' || key === '高') return '高'
  if (key === 'MEDIUM' || key === '中') return '中'
  return '低'
}

function levelChip(level: unknown): string {
  const key = String(level ?? '')
  if (key === 'HIGH' || key === '高') return 'chip-danger'
  if (key === 'MEDIUM' || key === '中') return 'chip-warn'
  return 'chip-mute'
}

function itemText(item: Record<string, unknown>): string {
  return String(
    item.name || item.point || item.title || item.topic || item.description || JSON.stringify(item),
  )
}

function itemLevel(item: Record<string, unknown>): unknown {
  return item.level || item.severity || item.riskLevel
}

/** 这些端点返回双层信封，沿用旧端 unwrap 两次的口径 */
async function callTwice<T>(request: Promise<unknown>): Promise<T> {
  const body = unwrap(await request)
  return (unwrap(body) || {}) as T
}

async function askQa() {
  const text = qaInput.value.trim()
  if (!text || qaSending.value) return
  messages.value.push({ role: 'user', content: text })
  qaInput.value = ''
  qaSending.value = true
  try {
    const data = await callTwice<{
      sessionId?: string
      answer?: string
      sources?: Source[]
    }>(
      sessionId.value
        ? aiApi.qaContinue({ sessionId: sessionId.value, message: text })
        : aiApi.qaAsk({ courseId: props.courseId, question: text }),
    )
    if (data.sessionId) sessionId.value = String(data.sessionId)
    messages.value.push({
      role: 'ai',
      content: data.answer || '（暂无回答）',
      sources: Array.isArray(data.sources) ? data.sources : [],
    })
  } catch (error) {
    logger.error('智能答疑失败', error)
    messages.value.push({ role: 'ai', content: '抱歉，回答失败，请稍后重试。' })
  } finally {
    qaSending.value = false
  }
}

function resetQa() {
  sessionId.value = ''
  messages.value = []
}

async function loadWeak() {
  if (!props.courseId) return
  weakLoading.value = true
  weakError.value = ''
  try {
    const data = await callTwice<{
      weakPoints?: Record<string, unknown>[]
      list?: Record<string, unknown>[]
      points?: Record<string, unknown>[]
      summary?: string
    }>(aiApi.weakPointsAnalysis({ courseId: props.courseId }))
    weakPoints.value = data.weakPoints || data.list || data.points || []
    weakSummary.value = String(data.summary || '')
  } catch (error) {
    logger.error('薄弱点分析失败', error)
    // 该端点会因模型返回坏 JSON 间歇 400：保留上一次成功读数，只补错误提示
    weakError.value = '薄弱点分析加载失败，已保留上一次结果，请稍后重试'
  } finally {
    weakLoading.value = false
  }
}

async function loadAdvice() {
  if (!props.courseId) return
  adviceLoading.value = true
  adviceError.value = ''
  try {
    const data = await callTwice<{
      summary?: string
      items?: { title?: string; detail?: string; level?: string }[]
      list?: { title?: string; detail?: string; level?: string }[]
    }>(aiApi.learningAdvice({ courseId: props.courseId }))
    adviceSummary.value = String(data.summary || '')
    adviceItems.value = data.items || data.list || []
  } catch (error) {
    logger.error('学习建议加载失败', error)
    // 同薄弱点：间歇 400 时不清空上一次成功读数
    adviceError.value = '学习建议加载失败，已保留上一次结果，请稍后重试'
  } finally {
    adviceLoading.value = false
  }
}

async function correctEssay() {
  if (!essayText.value.trim()) return
  essayLoading.value = true
  essayError.value = ''
  try {
    const data = await callTwice<{ result?: string; corrected?: string; feedback?: string }>(
      aiApi.essayCorrect({ content: essayText.value.trim() }),
    )
    essayResult.value = String(data.result || data.corrected || data.feedback || '（暂无批改结果）')
  } catch (error) {
    logger.error('作文批改失败', error)
    essayResult.value = ''
    essayError.value = '作文批改失败，请稍后重试'
  } finally {
    essayLoading.value = false
  }
}

async function polishEssay() {
  if (!essayText.value.trim()) return
  polishLoading.value = true
  essayError.value = ''
  try {
    const data = await callTwice<{ result?: string; polished?: string; content?: string }>(
      aiApi.essayPolish({ content: essayText.value.trim() }),
    )
    const polished = data.result || data.polished || data.content || ''
    if (polished) {
      essayText.value = String(polished)
      toast('已回填润色结果', 'ok')
    } else {
      essayError.value = '没有拿到润色结果'
    }
  } catch (error) {
    logger.error('作文润色失败', error)
    essayError.value = '作文润色失败，请稍后重试'
  } finally {
    polishLoading.value = false
  }
}

async function runPath() {
  if (!pathGoal.value.trim()) return
  pathLoading.value = true
  pathError.value = ''
  pathResult.value = null
  pathRaw.value = ''
  try {
    const data = await callTwice<{ status?: string; message?: string; summary?: string }>(
      aiApi.generateLearningPath({ courseId: props.courseId, goal: pathGoal.value.trim() }),
    )
    pathResult.value = { status: data.status, message: data.message || data.summary }
    pathRaw.value = JSON.stringify(data, null, 2)
    toast('学习路径已生成', 'ok')
  } catch (error) {
    logger.error('学习路径生成失败', error)
    pathError.value = '学习路径生成失败，请稍后重试'
  } finally {
    pathLoading.value = false
  }
}

onMounted(() => {
  void loadAdvice()
  void loadWeak()
})

watch(
  () => props.courseId,
  () => {
    resetQa()
    pathResult.value = null
    pathRaw.value = ''
    void loadAdvice()
    void loadWeak()
  },
)
</script>

<style scoped>
.ai-center {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.head-hint {
  font-size: 10.5px;
  color: var(--ink-3);
}
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}
.msg {
  padding: 10px 12px;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--surface-2);
}
.msg-user {
  background: var(--accent-soft);
  border-color: transparent;
}
.msg-body {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.8;
  white-space: pre-wrap;
}
.src-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 11px;
  color: var(--ink-3);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.qa-foot {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.qa-foot .textarea {
  flex: 1;
}
.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.item-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.nb-name {
  flex: 1;
  min-width: 0;
}
.ai-summary {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-2);
}
.advice-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.advice-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.advice-list p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.75;
  color: var(--ink-2);
}
.essay-actions {
  margin: 10px 0;
}
.pre-wrap {
  white-space: pre-wrap;
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.8;
}
.raw {
  margin-top: 10px;
  font-size: 11px;
}
.raw pre {
  margin: 6px 0 0;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface-2);
  max-height: 260px;
  overflow: auto;
}
</style>
