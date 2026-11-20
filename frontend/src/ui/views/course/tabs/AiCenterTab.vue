<template>
  <!-- 教师端与学生端共用同一工作区骨架：历史侧栏 + 顶栏 + 内容区 + 输入区 -->
  <section ref="shellRef" class="ai-shell" :style="shellStyle">
    <div class="ai-workspace">
      <aside
        v-if="!isNarrow || sideOpen"
        id="ai-history"
        ref="sideRef"
        class="ai-side"
        :class="{ 'is-collapsed': !sideOpen, 'is-narrow': isNarrow }"
        @keydown.esc.stop="closeSide"
      >
        <div class="side-inner">
          <div class="side-top">
            <button class="side-new" @click="newChat">
              <Icon name="plus" />
              <span>新对话</span>
            </button>
            <button class="side-close" aria-label="收起对话历史" @click="closeSide">
              <Icon name="x" />
            </button>
          </div>

          <div class="side-scroll">
            <p class="side-label">历史对话</p>
            <template v-for="group in sessionGroups" :key="group.key">
              <p class="side-group">{{ group.label }}</p>
              <button
                v-for="item in group.items"
                :key="item.id"
                class="side-item"
                :class="{ 'is-active': item.id === activeId }"
                :aria-current="item.id === activeId ? 'true' : undefined"
                @click="openSession(item)"
              >
                <span class="side-item-title">{{ item.title }}</span>
                <span class="side-item-meta">
                  <span class="side-item-last">{{ item.lastText || '（无消息）' }}</span>
                  <time>{{ shortTime(item.updatedAt) }}</time>
                </span>
              </button>
            </template>
            <p v-if="!sessionGroups.length" class="side-empty">暂无历史对话</p>
          </div>
        </div>
      </aside>

      <div v-if="isNarrow && sideOpen" class="side-scrim" @click="closeSide"></div>

      <div class="ai-main">
        <header class="ai-top">
          <div class="ai-top-row">
            <button
              ref="toggleRef"
              class="top-icon"
              :aria-label="sideOpen ? '收起对话历史' : '展开对话历史'"
              :aria-expanded="sideOpen ? 'true' : 'false'"
              aria-controls="ai-history"
              @click="toggleSide"
            >
              <Icon name="menu" />
            </button>
            <div class="ai-top-title">
              <b>{{ activeTitle }}</b>
              <small v-if="courseName">{{ courseName }}</small>
            </div>
            <!-- 侧栏展开时已有「新对话」，顶栏只在侧栏收起或窄屏时补位，避免出现两个入口 -->
            <button v-if="active === 'qa' && !sideOpen" class="top-new" @click="newChat">
              <Icon name="plus" />
              <span>新对话</span>
            </button>
          </div>

          <!-- 教师端的能力页签；学生端的学习工具是提问模板，不占页签 -->
          <div v-if="showTabs" class="ai-tabs" role="tablist" aria-label="AI 能力">
            <button
              v-for="tab in visibleTabs"
              :key="tab.key"
              class="ai-tab"
              :class="{ 'is-active': active === tab.key }"
              role="tab"
              :aria-selected="active === tab.key"
              @click="active = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </header>

        <!-- 智能答疑：两种角色共用同一对话流 -->
        <div v-if="active === 'qa'" ref="scrollRef" class="ai-stream" @scroll="onChatScroll">
          <p v-if="blockedNotice" class="stream-notice">
            对话历史加载失败，请稍后重试；继续提问仍会接着这条会话。
          </p>

          <div v-if="sessionLoading" class="stream-loading">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
            <span>正在加载对话…</span>
          </div>

          <div v-if="!messages.length && !qaSending && !sessionLoading" class="welcome">
            <div class="welcome-mark"><img :src="aiAvatar" alt="" /></div>
            <h2>{{ isStudent ? '你好，我是你的 AI 学习助手' : '你好，我是你的 AI 助教' }}</h2>
            <p>
              {{
                isStudent
                  ? '可以问我本课程的概念、作业思路或复习方法。'
                  : '可以问我本课程的备课、命题与答疑要点。'
              }}
            </p>
            <div class="suggest">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                class="suggest-item"
                @click="send(suggestion)"
              >
                {{ suggestion }}
              </button>
            </div>
          </div>

          <div v-else class="stream-inner">
            <article
              v-for="(msg, index) in messages"
              :key="index"
              :class="['turn', `turn-${msg.role}`]"
            >
              <div v-if="msg.role === 'ai'" class="turn-avatar ai-avatar">
                <img :src="aiAvatar" alt="" />
              </div>
              <div class="turn-body">
                <p v-if="msg.content" class="turn-text">
                  {{ displayContent(msg) }}<span v-if="msg.streaming" class="stream-caret"></span>
                </p>
                <div v-if="msg.streaming && !msg.content" class="thinking-inline">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="thinking-text">正在思考…</span>
                </div>
                <div v-if="msg.failed" class="turn-fail">
                  <span>{{ msg.failure || '回答失败，请稍后重试。' }}</span>
                  <button class="mini-btn" @click="retry(index)">重试</button>
                </div>
                <details v-if="msg.sources?.length" class="src">
                  <summary>参考了 {{ msg.sources.length }} 份资料</summary>
                  <ul>
                    <li v-for="(source, sIndex) in msg.sources" :key="sIndex">
                      <b>{{ source.documentName || `资料 ${sIndex + 1}` }}</b>
                      <p v-if="source.snippet">{{ source.snippet }}</p>
                    </li>
                  </ul>
                </details>
              </div>
              <div v-if="msg.role === 'user'" class="turn-avatar user-avatar">
                <img
                  v-if="userAvatarSrc"
                  :src="userAvatarSrc"
                  alt=""
                  @error="userAvatarFailed = true"
                />
                <span v-else>{{ userInitial }}</span>
              </div>
            </article>
          </div>
        </div>

        <!-- 教师端其它能力：同一工作区内按页签切换，动作与接口保持原样 -->
        <div v-else ref="panelRef" class="ai-panel">
          <div class="panel-inner">
            <!-- 薄弱点分析 -->
            <template v-if="active === 'weak'">
              <header class="panel-head">
                <h2>
                  薄弱点分析 <span class="chip chip-mute">{{ weakPoints.length }} 项</span>
                </h2>
                <button class="btn btn-ghost" :disabled="weakLoading" @click="loadWeak">
                  {{ weakLoading ? '分析中…' : '重新分析' }}
                </button>
              </header>
              <p v-if="weakError" class="form-error">{{ weakError }}</p>
              <p v-if="weakSummary" class="panel-summary">{{ weakSummary }}</p>
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
            </template>

            <!-- 学习建议 -->
            <template v-else-if="active === 'advice'">
              <header class="panel-head">
                <h2>
                  学习建议 <span class="chip chip-mute">{{ adviceItems.length }} 条</span>
                </h2>
                <button class="btn btn-ghost" :disabled="adviceLoading" @click="loadAdvice">
                  {{ adviceLoading ? '生成中…' : '重新生成' }}
                </button>
              </header>
              <p v-if="adviceError" class="form-error">{{ adviceError }}</p>
              <p v-if="adviceSummary" class="panel-summary">{{ adviceSummary }}</p>
              <ol v-if="adviceItems.length" class="advice-list">
                <li v-for="(item, index) in adviceItems" :key="index">
                  <div class="advice-head">
                    <b>{{ item.title || `建议 ${index + 1}` }}</b>
                    <span class="chip" :class="levelChip(item.level)">{{
                      levelText(item.level)
                    }}</span>
                  </div>
                  <p v-if="item.detail">{{ item.detail }}</p>
                </li>
              </ol>
              <div v-else-if="!adviceLoading && !adviceError" class="empty-state compact">
                <b>暂无建议</b>
              </div>
              <div v-else-if="adviceLoading" class="empty-state compact"><b>正在生成建议…</b></div>
            </template>

            <!-- 作文批改 -->
            <template v-else-if="active === 'essay'">
              <header class="panel-head">
                <h2>作文批改</h2>
              </header>
              <textarea
                v-model="essayText"
                class="textarea"
                rows="8"
                placeholder="粘贴需要批改的文本"
              ></textarea>
              <div class="panel-actions">
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
            </template>

            <!-- 学习路径 -->
            <template v-else-if="active === 'path'">
              <header class="panel-head">
                <h2>学习路径生成</h2>
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
              <div class="panel-actions">
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
            </template>

            <!-- 课程语料（教师专属） -->
            <AiCorpusTab v-else-if="active === 'corpus'" :course-id="courseId" />
          </div>
        </div>

        <div v-if="active === 'qa'" class="composer">
          <!-- 学生端的学习工具改成提问模板：点一下把模板问题填进输入框，仍由同一个会话回答 -->
          <div v-if="isStudent" class="quick-prompts">
            <button
              v-for="prompt in QUICK_PROMPTS"
              :key="prompt.key"
              class="quick-chip"
              :aria-label="`填入${prompt.label}的提问模板`"
              @click="fillPrompt(prompt.key)"
            >
              <Icon :name="prompt.icon" />
              <span>{{ prompt.label }}</span>
            </button>
          </div>
          <div class="composer-box">
            <textarea
              ref="taRef"
              v-model="qaInput"
              class="composer-input"
              rows="1"
              maxlength="2000"
              placeholder="问我任何关于本课程的问题…"
              @input="autosize"
              @keydown="onComposerKey"
              @compositionstart="composing = true"
              @compositionend="composing = false"
            ></textarea>
            <button class="composer-send" :disabled="!canSend" aria-label="发送" @click="send()">
              <Icon name="arrow" />
            </button>
          </div>
          <p class="composer-note">AI 回答仅供学习参考，重要结论请以课程资料为准。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '../../../components/Icon.vue'
import AiCorpusTab from './AiCorpusTab.vue'
import { useUi } from '../../../composables/ui'
import aiApi from '@/api/ai'
import courseApi from '@/api/course'
import { useAuthStore } from '@/stores/auth'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import aiAvatar from '@/assets/ai-assistant.png'

interface Source {
  documentName?: string
  snippet?: string
  [key: string]: unknown
}

interface Message {
  role: 'user' | 'ai'
  content: string
  sources?: Source[]
  /** 正在流式接收：内容可能为空（此时显示思考动效） */
  streaming?: boolean
  /** 请求失败时占位：只渲染错误条与重试，不伪造一条成功回答 */
  failed?: boolean
  retryText?: string
  /** 失败原因：优先后端 message（如配额超限），否则回退通用文案 */
  failure?: string
}

interface ChatSession {
  id: string
  title: string
  messages: Message[]
  updatedAt: number
  lastText: string
  courseId: number
  /** 来自 GET /ai/qa/sessions 的记录；打开时按需拉取消息正文 */
  remote?: boolean
  /** 正文是否已加载（远端历史首次打开为 false） */
  loaded?: boolean
}

type TabKey = 'qa' | 'weak' | 'advice' | 'essay' | 'path' | 'corpus'

const AI_TABS = [
  { key: 'qa', label: '智能答疑' },
  { key: 'weak', label: '薄弱点分析' },
  { key: 'advice', label: '学习建议' },
  { key: 'essay', label: '作文批改', teacherOnly: true },
  { key: 'path', label: '学习路径' },
  { key: 'corpus', label: '课程语料', teacherOnly: true },
] as const

const STUDENT_SUGGESTIONS = [
  '帮我梳理本课程的核心知识点',
  '这个概念可以举例说明吗？',
  '给我出 3 道练习题',
  '帮我制定本周复习计划',
]

const TEACHER_SUGGESTIONS = [
  '帮我梳理本课程的知识点结构',
  '为这一章设计 3 个课堂提问',
  '根据薄弱点给我一份讲评思路',
  '帮我写一段本章小结',
]

/**
 * 学习工具（薄弱点 / 建议 / 路径）从独立页签改为提问模板：
 * 点一下只把问题填进输入框，答案仍由当前会话按普通提问给出，用户可先改再发。
 */
const QUICK_PROMPTS = [
  { key: 'weak', label: '薄弱点分析', icon: 'target' },
  { key: 'advice', label: '学习建议', icon: 'sparkle' },
  { key: 'path', label: '学习路径', icon: 'path' },
] as const

const PROMPT_TEMPLATES: Record<string, (course: string) => string> = {
  weak: (course) => `请帮我分析一下我在《${course}》各章节的薄弱点，并说明判断依据。`,
  advice: (course) => `请根据我目前的学习进度，给我一些《${course}》的学习建议。`,
  path: (course) => `请帮我制定一份《${course}》的学习路径，按先修顺序列出阶段目标。`,
}

const NARROW_QUERY = '(max-width: 900px)'

// 学生复用同一套真接口，只裁掉教师专属的「作文批改」（其结论由教师采纳）
const props = defineProps<{ courseId: number; mode?: 'teacher' | 'student' }>()
const isStudent = computed(() => props.mode === 'student')
const visibleTabs = computed(() =>
  AI_TABS.filter((tab) => !('teacherOnly' in tab && tab.teacherOnly && isStudent.value)),
)
// 教师端六个能力共用同一工作区，页签由顶栏承载；学生端的学习工具是提问模板，不占页签
const showTabs = computed(() => !isStudent.value && visibleTabs.value.length > 1)
const suggestions = computed(() => (isStudent.value ? STUDENT_SUGGESTIONS : TEACHER_SUGGESTIONS))
const { toast } = useUi()
const auth = useAuthStore()
// 用户头像来自登录信息；落库形态是 /uploads/xxx，静态映射挂在 /api/uploads/**
// 下，直接当 src 用会被 Vite 兜底成 index.html，图片解码失败后退化成姓名首字。
const userAvatar = computed(() => {
  const raw = (auth.userAvatar || '').trim()
  if (!raw) return ''
  if (raw.startsWith('/uploads/')) return `/api${raw}`
  if (raw.startsWith('uploads/')) return `/api/${raw}`
  return raw
})
const userInitial = computed(() => (auth.userName || '我').trim().slice(0, 1) || '我')
// 头像 URL 可能指向已删除文件：加载失败就退回姓名首字，避免一个破图占着气泡边缘
const userAvatarFailed = ref(false)
const userAvatarSrc = computed(() => (userAvatarFailed.value ? '' : userAvatar.value))
watch(userAvatar, () => {
  userAvatarFailed.value = false
})

const active = ref<TabKey>('qa')

const messages = ref<Message[]>([])
const qaInput = ref('')
const qaSending = ref(false)
const sessionId = ref('')
const sessionLoading = ref(false)
/** 会话详情请求序号：快速切换会话时只认最后一次响应 */
let sessionLoadSeq = 0
/** 当前 SSE 流：切换会话/新建对话时中止，避免 token 继续写进旧消息 */
let streamController: AbortController | null = null

const weakPoints = ref<Record<string, unknown>[]>([])
const weakSummary = ref('')
const weakLoading = ref(false)
const weakError = ref('')
const weakLoaded = ref(false)

const adviceItems = ref<{ title?: string; detail?: string; level?: string }[]>([])
const adviceSummary = ref('')
const adviceLoading = ref(false)
const adviceError = ref('')
const adviceLoaded = ref(false)

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

/* -------------------------------- 会话与历史 -------------------------------- */

/**
 * 会话缓存只活在本次页面会话的内存里（模块级 Map，按课程分桶）。
 * 不写 localStorage、不冒充云端历史：刷新后侧栏为空是诚实的，而不是"看起来有记录点开是空的"。
 */
const sessionStore = new Map<number, ChatSession[]>()
const sessions = ref<ChatSession[]>([])
const activeId = ref('')
const blockedNotice = ref(false)

const sessionGroups = computed(() => {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const today = start.getTime()
  const day = 86_400_000
  const buckets = [
    { key: 'today', label: '今天', from: today, to: Number.POSITIVE_INFINITY },
    { key: 'yesterday', label: '昨天', from: today - day, to: today },
    { key: 'week', label: '近 7 天', from: today - 6 * day, to: today - day },
    { key: 'older', label: '更早', from: Number.NEGATIVE_INFINITY, to: today - 6 * day },
  ]
  const sorted = [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt)
  return buckets
    .map((bucket) => ({
      key: bucket.key,
      label: bucket.label,
      items: sorted.filter((item) => item.updatedAt >= bucket.from && item.updatedAt < bucket.to),
    }))
    .filter((bucket) => bucket.items.length > 0)
})

const activeTitle = computed(() => {
  const fallback = isStudent.value ? 'AI 学习助手' : 'AI 助教'
  if (active.value !== 'qa') return fallback
  const current = sessions.value.find((item) => item.id === activeId.value)
  return current?.title || fallback
})

function titleFrom(list: Message[]): string {
  const first = list.find((item) => item.role === 'user' && item.content.trim())
  if (!first) return '新对话'
  const text = first.content.trim().replace(/\s+/g, ' ')
  return text.length > 18 ? `${text.slice(0, 18)}…` : text
}

function lastTextFrom(list: Message[]): string {
  const last = [...list].reverse().find((item) => item.content.trim())
  if (!last) return ''
  const text = last.content.trim().replace(/\s+/g, ' ')
  return text.length > 30 ? `${text.slice(0, 30)}…` : text
}

function shortTime(ts: number): string {
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.max(1, Math.floor(diff / 60_000))} 分钟前`
  const date = new Date(ts)
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  if (ts >= start.getTime()) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  }
  if (ts >= start.getTime() - 86_400_000) return '昨天'
  if (ts >= start.getTime() - 6 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return `${date.getMonth() + 1}-${date.getDate()}`
}

function loadSessions() {
  const cached = sessionStore.get(props.courseId)
  if (cached) {
    sessions.value = cached
    return
  }
  sessions.value = []
  sessionStore.set(props.courseId, sessions.value)
}

function syncActiveSession() {
  if (!activeId.value) return
  const current = sessions.value.find((item) => item.id === activeId.value)
  if (!current) return
  current.messages = messages.value.map((item) => ({ ...item }))
  current.updatedAt = Date.now()
  current.lastText = lastTextFrom(messages.value)
  if (!current.title || current.title === '新对话') current.title = titleFrom(messages.value)
}

/** 首次提问拿到后端 sessionId 后才落一条侧栏记录——没有真实会话就不凭空造条目 */
function ensureSession() {
  const id = sessionId.value
  if (!id) return
  let current = sessions.value.find((item) => item.id === id)
  if (!current) {
    current = {
      id,
      title: '',
      messages: [],
      updatedAt: Date.now(),
      lastText: '',
      courseId: props.courseId,
      loaded: true,
    }
    sessions.value.unshift(current)
  }
  activeId.value = id
  syncActiveSession()
}

/**
 * 云端会话列表现在带 courseId，只展示本课程的历史；
 * 正文不随列表下发，点开时再调 /qa/sessions/{id} 拉取，避免一次拉全量长文本。
 */
async function loadRemoteSessions() {
  try {
    const body = unwrap(await aiApi.getSessions()) as unknown
    const payload = unwrap(body as never) as unknown
    const list = Array.isArray(payload)
      ? payload
      : ((payload as { list?: unknown[]; sessions?: unknown[] })?.list ??
        (payload as { sessions?: unknown[] })?.sessions ??
        [])
    for (const raw of list as Record<string, unknown>[]) {
      if (Number(raw?.courseId) !== Number(props.courseId)) continue
      const id = String(raw?.sessionId ?? '')
      if (!id || sessions.value.some((item) => item.id === id)) continue
      const stamp = Date.parse(String(raw?.updatedAt ?? raw?.createdAt ?? '')) || Date.now()
      sessions.value.push({
        id,
        title: String(raw?.title ?? '未命名对话'),
        messages: [],
        updatedAt: stamp,
        lastText: String(raw?.lastMessage ?? ''),
        courseId: props.courseId,
        remote: true,
        loaded: false,
      })
    }
  } catch {
    // 列表接口不可用不该影响对话本身；侧栏退化为只显示本页产生的会话
  }
}

function abortStream() {
  streamController?.abort()
  streamController = null
  qaSending.value = false
}

async function openSession(item: ChatSession) {
  if (item.id === activeId.value && !blockedNotice.value) {
    if (isNarrow.value) closeSide()
    return
  }
  abortStream()
  active.value = 'qa'
  activeId.value = item.id
  sessionId.value = item.id
  blockedNotice.value = false
  if (isNarrow.value) closeSide()

  if (item.remote && !item.loaded) {
    const seq = ++sessionLoadSeq
    sessionLoading.value = true
    messages.value = []
    try {
      const data = await callTwice<{
        title?: string
        messages?: { role?: string; content?: string }[]
      }>(aiApi.getSessionDetail(item.id))
      if (seq !== sessionLoadSeq) return
      const restored: Message[] = (data.messages ?? [])
        .map((msg) => ({
          role: msg.role === 'assistant' ? ('ai' as const) : ('user' as const),
          content: String(msg.content ?? ''),
        }))
        .filter((msg) => msg.content.trim())
      item.messages = restored
      item.loaded = true
      if (data.title) item.title = data.title
      messages.value = restored.map((msg) => ({ ...msg }))
    } catch (error) {
      if (seq !== sessionLoadSeq) return
      logger.error('加载会话历史失败', error)
      // 读不到正文时明确提示，而不是把空对话框伪装成“新会话”
      blockedNotice.value = true
      messages.value = []
    } finally {
      if (seq === sessionLoadSeq) sessionLoading.value = false
    }
  } else {
    sessionLoadSeq += 1
    sessionLoading.value = false
    messages.value = item.messages.map((msg) => ({ ...msg }))
  }
  void scrollToEnd(true)
}

function newChat() {
  abortStream()
  sessionLoadSeq += 1
  active.value = 'qa'
  messages.value = []
  sessionId.value = ''
  activeId.value = ''
  blockedNotice.value = false
  sessionLoading.value = false
  qaInput.value = ''
  atBottom.value = true
  void nextTick(() => {
    taRef.value?.focus()
    autosize()
  })
}

/* -------------------------------- 侧栏开合 -------------------------------- */

const shellRef = ref<HTMLElement | null>(null)
const sideRef = ref<HTMLElement | null>(null)
const toggleRef = ref<HTMLButtonElement | null>(null)
const sideOpen = ref(true)
const isNarrow = ref(false)
const shellHeight = ref(0)

const shellStyle = computed(() =>
  shellHeight.value ? { '--ai-h': `${shellHeight.value}px` } : undefined,
)

function measureShell() {
  const element = shellRef.value
  if (!element) return
  const top = element.getBoundingClientRect().top
  // 减掉 main-inner 的底部内边距，否则页面外层会多出一条滚动条（与消息区滚动叠加成双滚动条）
  const inner = element.closest('.main-inner')
  const padBottom = inner ? Number.parseFloat(getComputedStyle(inner).paddingBottom) || 0 : 24
  // 课程头 + 页头会随断点变化，按实测量而不是猜一个 calc 常数
  shellHeight.value = Math.max(440, Math.round(window.innerHeight - top - padBottom - 4))
  // 课程详情异步到达会垫高课程头，上面的测量于是偏大：按外层真实溢出量再收紧一次即收敛
  requestAnimationFrame(() => {
    const overflow = document.documentElement.scrollHeight - window.innerHeight
    if (overflow > 0) shellHeight.value = Math.max(440, shellHeight.value - overflow)
  })
}

/** jsdom 等无 matchMedia 的环境退化为宽屏，避免挂载期直接抛错 */
function narrowQuery(): MediaQueryList | null {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return null
  return window.matchMedia(NARROW_QUERY)
}

function syncNarrow(event?: MediaQueryList | MediaQueryListEvent) {
  const narrow = event ? event.matches : Boolean(narrowQuery()?.matches)
  const wasNarrow = isNarrow.value
  isNarrow.value = narrow
  if (narrow && !wasNarrow) sideOpen.value = false
  if (!narrow && wasNarrow) sideOpen.value = true
}

function toggleSide() {
  if (sideOpen.value) {
    closeSide()
    return
  }
  sideOpen.value = true
  if (isNarrow.value) {
    void nextTick(() => sideRef.value?.querySelector<HTMLButtonElement>('.side-new')?.focus())
  }
}

function closeSide() {
  if (!sideOpen.value) return
  sideOpen.value = false
  if (isNarrow.value) void nextTick(() => toggleRef.value?.focus())
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !isNarrow.value || !sideOpen.value) return
  event.preventDefault()
  closeSide()
}

/* -------------------------------- 对话交互 -------------------------------- */

const scrollRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const taRef = ref<HTMLTextAreaElement | null>(null)
const atBottom = ref(true)
const composing = ref(false)

// 切回某个能力页签时从顶部开始，避免沿用上一个面板的滚动位置
/**
 * 页签切换：滚动复位，并按需加载该面板数据。
 * 不在挂载时预拉薄弱点/学习建议——两次真实 LLM 调用会把本地 Ollama 的单并发队列占满，
 * 用户紧接着提问只能排队到超时；改为进入对应页签时才请求。
 */
watch(active, (tab) => {
  if (panelRef.value) panelRef.value.scrollTop = 0
  if (tab === 'weak' && !weakLoaded.value) void loadWeak()
  if (tab === 'advice' && !adviceLoaded.value) void loadAdvice()
})

const canSend = computed(
  () => Boolean(qaInput.value.trim()) && !qaSending.value && !sessionLoading.value,
)

function scrollToEnd(force = false) {
  return nextTick().then(() => {
    const element = scrollRef.value
    if (!element) return
    if (!force && !atBottom.value) return
    element.scrollTop = element.scrollHeight
  })
}

function onChatScroll() {
  const element = scrollRef.value
  if (!element) return
  // 用户向上翻阅时不再强行拉回底部：留 90px 容差，贴底才算"跟随"
  atBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 90
}

function autosize() {
  const element = taRef.value
  if (!element) return
  element.style.height = 'auto'
  const max = 22 * 6 + 20
  element.style.height = `${Math.min(element.scrollHeight, max)}px`
  element.style.overflowY = element.scrollHeight > max ? 'auto' : 'hidden'
}

/** 模板只填不发送：用户往往要补一句「哪一章」，直接发反而打断 */
function fillPrompt(key: string) {
  const build = PROMPT_TEMPLATES[key]
  if (!build || qaSending.value) return
  qaInput.value = build(courseName.value || '本课程')
  void nextTick(() => {
    const element = taRef.value
    if (!element) return
    element.focus()
    element.setSelectionRange(element.value.length, element.value.length)
    autosize()
  })
}

function onComposerKey(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) return
  // 中文输入法回车选词时不能发送：isComposing 在部分浏览器 keydown 阶段为 false，另用组合态兜底
  if (event.isComposing || composing.value || event.keyCode === 229) return
  event.preventDefault()
  void send()
}

async function send(preset?: string) {
  const text = (preset ?? qaInput.value).trim()
  if (!text || qaSending.value || sessionLoading.value) return
  blockedNotice.value = false
  messages.value.push({ role: 'user', content: text })
  qaInput.value = ''
  atBottom.value = true
  void nextTick(autosize)
  await scrollToEnd(true)
  await requestAnswer(text)
}

/**
 * 真实流式回答：先插入一条空气泡（显示思考动效），token 到达后逐个追加。
 * 会话ID 来自服务端首帧；用户问题与最终回答由后端按会话落库，刷新/重开课程都能恢复。
 */
async function requestAnswer(text: string) {
  qaSending.value = true
  messages.value.push({
    role: 'ai',
    content: '',
    streaming: true,
    retryText: text,
  })
  // 取数组里的响应式代理再改字段；直接改 push 前的原始对象不会触发视图更新
  const reply = messages.value[messages.value.length - 1]
  await scrollToEnd(true)

  // qaStream 立即返回 AbortController，结果通过回调推进；不能 await 它，否则会误判成流已结束
  streamController = aiApi.qaStream(
    {
      question: text,
      courseId: props.courseId,
      sessionId: sessionId.value || undefined,
    },
    {
      onSession: (id) => {
        sessionId.value = String(id)
        ensureSession()
      },
      onToken: (token) => {
        reply.content += token
        void scrollToEnd()
      },
      onSources: (sources) => {
        reply.sources = sources as Source[]
      },
      onDone: () => {
        reply.streaming = false
        if (!reply.content.trim()) {
          reply.failed = true
          reply.failure = '回答失败，请稍后重试。'
        } else {
          ensureSession()
        }
        qaSending.value = false
        streamController = null
        void scrollToEnd()
      },
      onError: (error) => {
        logger.error('智能答疑失败', error)
        reply.streaming = false
        reply.failed = true
        reply.failure = failureText(error)
        qaSending.value = false
        streamController = null
        void scrollToEnd()
      },
    },
  )
}

function retry(index: number) {
  const target = messages.value[index]
  if (!target?.retryText || qaSending.value) return
  messages.value.splice(index, 1)
  void requestAnswer(target.retryText)
}

/** 模型习惯输出 **加粗**；本项目没有 Markdown 渲染位点（P-12 只保留三处 v-html），去掉标记比露出星号干净。 */
function cleanAiText(content: string): string {
  return content.replace(/\*\*(.+?)\*\*/g, '$1').replace(/\*\*/g, '')
}
function displayContent(message: Message): string {
  return message.role === 'ai' ? cleanAiText(message.content) : message.content
}

/** 失败原因：SSE 错误体/业务 message 优先，比“检查网络”更能指向真实问题 */
function failureText(error: unknown): string {
  const responseMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage.trim()
  }
  // qaStream 把 HTTP 错误体拼进 Error.message；剥掉传输前缀只留后端话术
  const message = (error as { message?: unknown })?.message
  if (typeof message === 'string') {
    const match = message.match(/^SSE 请求失败:\s*HTTP \d+\s*-\s*(.+)$/)
    if (match?.[1]?.trim()) return match[1].trim()
  }
  return '回答失败，请稍后重试。'
}

watch(messages, syncActiveSession, { deep: true })

/* -------------------------------- 课程名 -------------------------------- */

const courseName = ref('')

async function loadCourseName() {
  try {
    const body = unwrap(await courseApi.getCourseById(props.courseId)) as Record<string, unknown>
    const detail = (body?.data ?? body) as Record<string, unknown>
    courseName.value = String(detail?.name ?? '')
  } catch {
    courseName.value = ''
  }
}

/* -------------------------------- 其它能力 -------------------------------- */

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

async function loadWeak() {
  if (!props.courseId || weakLoading.value) return
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
    // 自动加载只做一次：失败后由面板上的「重新分析」显式重试，避免切页签反复烧配额
    weakLoaded.value = true
  }
}

async function loadAdvice() {
  if (!props.courseId || adviceLoading.value) return
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
    adviceLoaded.value = true
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

/* -------------------------------- 生命周期 -------------------------------- */

let narrowMedia: MediaQueryList | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  loadSessions()
  syncNarrow()
  narrowMedia = narrowQuery()
  narrowMedia?.addEventListener('change', syncNarrow)
  document.addEventListener('keydown', onDocumentKeydown)
  window.addEventListener('resize', measureShell)
  measureShell()
  // 只盯课程头：观察自身父容器会被 shell 高度变化反复触发，形成测量抖动
  const courseHead = shellRef.value?.closest('.main-inner')?.querySelector('.course-head')
  if (typeof ResizeObserver !== 'undefined' && courseHead) {
    resizeObserver = new ResizeObserver(() => measureShell())
    resizeObserver.observe(courseHead)
  }
  // 教师端的薄弱点/学习建议改为进入页签时按需加载：
  // 挂载即预拉会连续占用两次真实 LLM 调用，把本地单并发模型队列排满，直接提问要等到超时
  void loadCourseName().then(measureShell)
  void loadRemoteSessions()
  void nextTick(autosize)
})

onBeforeUnmount(() => {
  abortStream()
  narrowMedia?.removeEventListener('change', syncNarrow)
  document.removeEventListener('keydown', onDocumentKeydown)
  window.removeEventListener('resize', measureShell)
  resizeObserver?.disconnect()
})

watch(
  () => props.courseId,
  () => {
    syncActiveSession()
    newChat()
    loadSessions()
    pathResult.value = null
    pathRaw.value = ''
    courseName.value = ''
    // 课程切换：清空上一门课的洞察读数，等用户进入对应页签再拉
    weakLoaded.value = false
    adviceLoaded.value = false
    weakPoints.value = []
    weakSummary.value = ''
    weakError.value = ''
    adviceItems.value = []
    adviceSummary.value = ''
    adviceError.value = ''
    void loadCourseName()
    void loadRemoteSessions()
    void nextTick(measureShell)
  },
)
</script>

<style scoped>
.ai-shell {
  display: flex;
  flex-direction: column;
  height: var(--ai-h, calc(100dvh - 320px));
  min-height: 440px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
  overflow: hidden;
}
.ai-workspace {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 侧栏 */
.ai-side {
  flex: none;
  width: 252px;
  overflow: hidden;
  border-right: 1px solid var(--line);
  transition: width 0.22s var(--ease);
}
.ai-side.is-collapsed {
  width: 0;
  border-right-color: transparent;
}
.side-inner {
  width: 252px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface-2);
}
.side-top {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 10px 8px;
}
.side-new {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.16s var(--ease),
    border-color 0.16s var(--ease);
}
.side-new:hover {
  border-color: var(--line-strong);
  background: var(--surface-3);
}
.side-new .icon {
  width: 14px;
  height: 14px;
}
.side-close {
  display: none;
  width: 32px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
}
.side-close .icon {
  width: 14px;
  height: 14px;
}
.side-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 8px 12px;
  scrollbar-width: thin;
}
.side-label {
  margin: 8px 8px 6px;
  font-size: 10.5px;
  letter-spacing: 0.04em;
  color: var(--ink-3);
}
.side-group {
  margin: 10px 8px 4px;
  font-size: 10px;
  color: var(--ink-4);
}
.side-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border: 0;
  border-radius: var(--r-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease);
}
.side-item:hover {
  background: var(--surface-3);
}
.side-item.is-active {
  background: var(--surface);
  box-shadow: inset 0 0 0 1px var(--line);
}
.side-item-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.side-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 10.5px;
  color: var(--ink-3);
}
.side-item-last {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.side-item-meta time {
  flex: none;
  font-variant-numeric: tabular-nums;
}
.side-empty {
  margin: 18px 10px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* 主区 */
.ai-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.ai-top {
  flex: none;
  padding: 10px 16px 0;
  border-bottom: 1px solid var(--line);
}
.ai-top-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.top-icon {
  flex: none;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  transition:
    background 0.16s var(--ease),
    color 0.16s var(--ease);
}
.top-icon:hover {
  background: var(--surface);
  color: var(--ink);
}
.top-icon .icon {
  width: 16px;
  height: 16px;
}
.ai-top-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.ai-top-title b {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.ai-top-title small {
  font-size: 10.5px;
  color: var(--ink-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.top-new {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: var(--r-chip);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    border-color 0.16s var(--ease),
    color 0.16s var(--ease);
}
.top-new:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}
.top-new .icon {
  width: 12px;
  height: 12px;
}
/* 能力页签：顶栏内的细线页签，不另起大标题 */
.ai-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  overflow-x: auto;
  scrollbar-width: none;
}
.ai-tabs::-webkit-scrollbar {
  display: none;
}
.ai-tab {
  flex: none;
  height: 32px;
  padding: 0 10px;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.16s var(--ease),
    border-color 0.16s var(--ease);
}
.ai-tab:hover {
  color: var(--ink);
}
.ai-tab.is-active {
  color: var(--ink);
  border-bottom-color: var(--accent);
}
/* 对话流 */
.ai-stream {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 18px 20px 8px;
  scrollbar-width: thin;
}
.stream-notice {
  max-width: 760px;
  margin: 0 auto 14px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-2);
}
.stream-inner {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 欢迎态 */
.welcome {
  max-width: 640px;
  margin: 8vh auto 0;
  padding: 0 4px;
  text-align: center;
}
.welcome-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--r-circle);
  background: var(--surface);
  overflow: hidden;
}
.welcome-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.welcome-mark .icon {
  width: 20px;
  height: 20px;
}
.welcome h2 {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ink);
}
.welcome p {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-3);
}
.suggest {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.suggest-item {
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 12.5px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.16s var(--ease),
    background 0.16s var(--ease),
    color 0.16s var(--ease);
}
.suggest-item:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
  color: var(--ink);
}

/* 消息 */
.turn {
  display: flex;
  gap: 9px;
  max-width: 100%;
}
.turn-user {
  justify-content: flex-end;
}
.turn-avatar {
  flex: none;
  width: 32px;
  height: 32px;
  margin-top: 1px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--r-circle);
  background: var(--surface);
  color: var(--accent);
  overflow: hidden;
}
.turn-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.turn-avatar .icon {
  width: 14px;
  height: 14px;
}
.user-avatar {
  background: var(--accent-soft);
  border-color: transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}
.turn-body {
  min-width: 0;
  max-width: 76%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.turn-user .turn-body {
  align-items: flex-end;
}
.turn-text {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.7;
  color: var(--ink);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.turn-user .turn-text {
  padding: 9px 14px;
  border-radius: var(--r-bubble) var(--r-bubble) var(--r-ctl) var(--r-bubble);
  background: var(--accent-soft);
  color: var(--ink);
}
/* 流式回答的光标：只在 token 还在到达时出现，和气泡同色系 */
.stream-caret {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: -0.15em;
  background: var(--accent);
  animation: streamCaret 0.9s step-end infinite;
}
@keyframes streamCaret {
  50% {
    opacity: 0;
  }
}
.turn-fail {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  font-size: 12px;
  color: var(--ink-2);
}
.mini-btn {
  flex: none;
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-chip);
  background: transparent;
  color: var(--ink);
  font-size: 11.5px;
  cursor: pointer;
}
.mini-btn:hover {
  background: var(--surface-2);
}

/* 资料出处 */
.src {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--ink-3);
}
.src summary {
  width: fit-content;
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: var(--r-chip);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
  list-style: none;
}
.src summary::-webkit-details-marker {
  display: none;
}
.src[open] summary {
  margin-bottom: 8px;
}
.src ul {
  margin: 0;
  padding: 0 0 0 2px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.src li {
  padding: 8px 11px;
  border-left: 2px solid var(--line-strong);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  background: var(--surface);
}
.src li b {
  display: block;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-2);
}
.src li p {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.65;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

/* 思考中：气泡内三点，等首个 token 期间占位 */
.thinking-inline {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 24px;
  font-size: 12px;
  color: var(--ink-3);
}
.thinking-inline .dot,
.stream-loading .dot {
  width: 4px;
  height: 4px;
  border-radius: var(--r-circle);
  background: var(--ink-3);
  animation: aiDot 1.1s infinite ease-in-out;
}
.thinking-inline .dot:nth-child(2),
.stream-loading .dot:nth-child(2) {
  animation-delay: 0.16s;
}
.thinking-inline .dot:nth-child(3),
.stream-loading .dot:nth-child(3) {
  animation-delay: 0.32s;
}
.thinking-text {
  margin-left: 4px;
}
.stream-loading {
  max-width: 800px;
  margin: 6vh auto 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--ink-3);
}
.stream-loading span:last-child {
  margin-left: 6px;
}
@keyframes aiDot {
  0%,
  70%,
  100% {
    opacity: 0.28;
    transform: translateY(0);
  }
  35% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

/* 输入区 */
.quick-prompts {
  max-width: 800px;
  margin: 0 auto 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: var(--r-chip);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 11.5px;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 0.16s var(--ease),
    background 0.16s var(--ease),
    color 0.16s var(--ease);
}
.quick-chip:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
  color: var(--ink);
}
.quick-chip .icon {
  width: 13px;
  height: 13px;
  color: var(--accent);
}
.composer {
  flex: none;
  padding: 10px 20px 12px;
  background: linear-gradient(to bottom, transparent, var(--bg) 28%);
}
.composer-box {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--ink) 5%, transparent);
  transition: border-color 0.16s var(--ease);
}
.composer-box:focus-within {
  border-color: var(--accent);
  /* 焦点环跟随胶囊圆角；全局 :focus-visible 的直角 outline 会把输入框框成方角 */
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 16%, transparent);
}
.composer-input {
  flex: 1;
  min-width: 0;
  max-height: 152px;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  resize: none;
  outline: none;
  overflow-y: hidden;
}
.composer-input::placeholder {
  color: var(--ink-4);
}
/* 焦点由外层容器的圆角描边承担，避免直角 outline 破坏胶囊形状 */
.composer-input:focus-visible {
  outline: none;
}
.composer-send {
  flex: none;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--r-circle);
  background: var(--accent);
  color: var(--accent-ink);
  cursor: pointer;
  transition:
    opacity 0.16s var(--ease),
    background 0.16s var(--ease);
}
.composer-send .icon {
  width: 15px;
  height: 15px;
  transform: rotate(-45deg);
}
.composer-send:disabled {
  background: var(--surface-3);
  color: var(--ink-4);
  cursor: not-allowed;
}
.composer-note {
  max-width: 800px;
  margin: 7px auto 0;
  text-align: center;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ink-4);
}

/* 学习工具面板 */
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
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  font-size: 12.5px;
}
.nb-name {
  flex: 1;
  min-width: 0;
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
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--ink-2);
}

/* 窄屏：侧栏改为抽屉 */
@media (max-width: 900px) {
  .ai-side {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    width: 272px;
    border-right: 1px solid var(--line);
    box-shadow: 0 18px 44px color-mix(in srgb, var(--ink) 22%, transparent);
  }
  .ai-side.is-collapsed {
    width: 272px;
    border-right-color: var(--line);
  }
  .side-inner {
    width: 272px;
  }
  .side-close {
    display: grid;
  }
  .side-scrim {
    position: absolute;
    inset: 0;
    z-index: 10;
    background: color-mix(in srgb, var(--ink) 34%, transparent);
  }
  .ai-stream {
    padding: 16px 14px 8px;
  }
  .ai-panel {
    padding: 16px 14px 20px;
  }
  .panel-head {
    flex-wrap: wrap;
  }
  .composer {
    padding: 8px 14px 10px;
  }
  .welcome {
    margin-top: 4vh;
  }
  .suggest {
    grid-template-columns: minmax(0, 1fr);
  }
  .turn-body {
    max-width: 86%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-side,
  .side-item,
  .suggest-item,
  .composer-box,
  .composer-send,
  .ai-tab {
    transition: none;
  }
  .thinking-inline .dot,
  .stream-loading .dot {
    animation: none;
  }
  .stream-caret {
    animation: none;
  }
}

/* 能力面板：与对话流共用同一内容区宽高与留白 */
.ai-panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 24px;
  scrollbar-width: thin;
}
.panel-inner {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-head h2 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 650;
  line-height: 1.3;
  color: var(--ink);
}
.panel-head h2 .chip {
  margin-left: 6px;
  vertical-align: 2px;
}
.panel-summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-2);
}
.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pre-wrap {
  white-space: pre-wrap;
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.8;
}
.raw {
  margin-top: 10px;
  font-size: 12px;
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
