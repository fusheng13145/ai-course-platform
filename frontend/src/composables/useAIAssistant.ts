import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { sanitizeHtml } from '@/utils/sanitize'
import { firstNonEmptyArray, unwrap, decodeHtml } from '@/utils/response'
import {
  isRecognitionSupported,
  isSpeechSupported,
  startRecognition,
  speak,
  stopSpeaking,
} from '@/composables/useSpeech'
import aiApi from '@/api/ai'
import courseApi from '@/api/course'

/**
 * AI 学习助手聊天逻辑
 * <p>承载对话页全部状态与操作：课程加载、对话历史、消息发送（SSE 流式优先 +
 * 非流式逐字降级）、快捷操作（总结课程/生成练习题/解释概念/学习计划）、
 * 重新生成/复制/朗读、Markdown 渲染（经 sanitize 净化）、语音输入/朗读与资源清理。</p>
 */

/** 检索出处条目（与后端 QaSourceVO 对齐，控制器下发前已做 HTML 转义） */
export interface QaSource {
  documentId?: number
  documentName?: string
  relevanceScore?: number
  snippet?: string
}

/**
 * 聊天消息条目。
 * <p>面板按此类型读取 role/content/loading/sources，避免消息数组退化为 unknown[] 后
 * 模板里每处属性访问都成为类型缺口。</p>
 */
export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  time?: string
  /** 该条回答仍在生成（占位气泡据此显示输入中动画） */
  loading?: boolean
  /** 生成失败，content 为错误提示文案 */
  error?: boolean
  /** RAG 检索出处；纯 LLM 直答时为空 */
  sources?: QaSource[]
}

/**
 * 读取 /qa/ask 应答的答案与溯源出处。
 * <p>业务字段在信封的 data 里（answer / sessionId / sources）；历史实现按
 * response / reply / content 取值，全部取不到时用固定文案兜底，真实 RAG 回答
 * 因此被静默丢弃。转义按展示口径还原，渲染仍走 escapeHtml + sanitize。</p>
 */
function readQa(res: unknown): { answer: string; sources: QaSource[] } {
  const payload = (unwrap(res) ?? {}) as { answer?: string; sources?: QaSource[] }
  const sources = Array.isArray(payload.sources)
    ? payload.sources.map((s) => ({
        ...s,
        documentName: decodeHtml(s.documentName),
        snippet: decodeHtml(s.snippet),
      }))
    : []
  return { answer: decodeHtml(payload.answer), sources }
}

export function useAIAssistant() {
  const showSidebar = ref(true)
  const messagesRef = ref(null)
  const inputRef = ref(null)

  const courseList = ref([])
  const selectedCourseId = ref(null)

  const conversations = ref([])
  const currentConvId = ref(Date.now())
  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const loading = ref(false)

  const activeIntervals = ref([])
  const sseController = ref(null)

  /* ---- 课程列表 ---- */
  async function loadCourses() {
    try {
      const res = await courseApi.getCourseList()
      const list = res.data?.list || res.data || res || []
      courseList.value = Array.isArray(list) ? list.slice(0, 10) : []
    } catch (e) {
      logger.error('加载课程失败:', e)
    }
  }

  /* ---- 对话历史 ---- */
  async function loadConversations() {
    try {
      const res = await aiApi.getSessions()
      // 缺陷修复（2026-10-04）：`list || records` 的空数组是 truthy，
      // 后端改用 records（或反之）时历史会话整批被吞、且毫无报错。
      const list = firstNonEmptyArray(res.data?.list, res.data?.records, res.data, res)
      if (list.length > 0) {
        conversations.value = list.map((s) => ({
          id: s.id || s.sessionId,
          title:
            s.title ||
            s.summary ||
            `对话 ${new Date(s.createdAt || Date.now()).toLocaleDateString()}`,
          time: s.updatedAt || s.createdAt || '',
          messages: s.messages || [],
          courseName: s.courseName || '',
        }))
      }
    } catch (e) {
      logger.warn('加载对话历史失败:', e)
    }
  }

  /* ---- 发送消息：SSE 流式优先，非流式降级 ---- */
  async function handleSend() {
    const content = inputMessage.value.trim()
    if (!content || loading.value) return

    messages.value.push({ role: 'user', content, time: formatTimeFull(new Date()) })
    inputMessage.value = ''
    loading.value = true
    await scrollToBottom()

    const aiMsgIdx = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      time: formatTimeFull(new Date()),
      loading: true,
    })

    try {
      await handleSendStream(aiMsgIdx, content)
    } catch {
      try {
        await handleSendFallback(aiMsgIdx, content)
      } catch (e) {
        logger.error('AI 聊天失败:', e)
        messages.value[aiMsgIdx].content = '抱歉，AI 服务暂时不可用，请稍后再试。'
        messages.value[aiMsgIdx].error = true
      }
    } finally {
      messages.value[aiMsgIdx].loading = false
      loading.value = false
      await scrollToBottom()
    }
  }

  /** SSE 流式：逐 token 推送 */
  function handleSendStream(aiMsgIdx, question) {
    return new Promise<void>((resolve, reject) => {
      const params: Record<string, string | number | boolean | undefined> = { question }
      if (selectedCourseId.value) params.courseId = selectedCourseId.value
      const controller = aiApi.qaStream(params, {
        onToken(token) {
          messages.value[aiMsgIdx].content += token
        },
        onError(err) {
          reject(err)
        },
        onDone() {
          sseController.value = null
          resolve()
        },
      })
      sseController.value = controller
    })
  }

  /** 非流式兜底：qaAsk 完整回复后前端逐字渲染 */
  async function handleSendFallback(aiMsgIdx, question) {
    const payload: Record<string, unknown> = { question }
    if (selectedCourseId.value) payload.courseId = selectedCourseId.value
    const { answer, sources } = readQa(await aiApi.qaAsk(payload))
    if (!answer) {
      messages.value[aiMsgIdx].content = 'AI 未返回内容，请稍后再试。'
      messages.value[aiMsgIdx].error = true
      return
    }
    await typewriterEffect(aiMsgIdx, answer)
    messages.value[aiMsgIdx].sources = sources
  }

  /** 打字机效果（非流式兜底用，20ms/字） */
  function typewriterEffect(msgIndex, text) {
    return new Promise<void>((resolve) => {
      let i = 0
      messages.value[msgIndex].content = ''
      const interval = setInterval(() => {
        if (i < text.length) {
          messages.value[msgIndex].content += text[i]
          i++
          if (i % 3 === 0) scrollToBottom()
        } else {
          clearInterval(interval)
          activeIntervals.value = activeIntervals.value.filter((t) => t !== interval)
          resolve()
        }
      }, 20)
      activeIntervals.value.push(interval)
    })
  }

  async function sendQuickMessage(text) {
    inputMessage.value = text
    await nextTick()
    handleSend()
  }

  /** 快捷：总结课程 */
  async function handleGenerateSummary() {
    if (!selectedCourseId.value) {
      ElMessage.warning('请先选择一个课程')
      return
    }
    messages.value.push({
      role: 'user',
      content: '请帮我总结当前课程的核心知识点',
      time: formatTimeFull(new Date()),
    })
    loading.value = true
    await scrollToBottom()
    const aiMsgIdx = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      time: formatTimeFull(new Date()),
      loading: true,
    })
    try {
      const { answer, sources } = readQa(
        await aiApi.qaAsk({
          question:
            '请为当前课程生成一份全面的学习总结，包括课程核心知识点、学习重点、难点分析以及学习建议。',
          courseId: selectedCourseId.value,
        }),
      )
      await typewriterEffect(aiMsgIdx, answer || '课程总结暂时无法生成，请稍后再试。')
      messages.value[aiMsgIdx].sources = sources
    } catch (e) {
      logger.error('生成总结失败:', e)
      messages.value[aiMsgIdx].content = '抱歉，生成课程总结失败，请稍后再试。'
      messages.value[aiMsgIdx].error = true
    } finally {
      messages.value[aiMsgIdx].loading = false
      await scrollToBottom()
    }
  }

  /** 快捷：生成练习题 */
  async function handleGenerateQuestion() {
    if (!selectedCourseId.value) {
      ElMessage.warning('请先选择一个课程')
      return
    }
    messages.value.push({
      role: 'user',
      content: '请根据当前知识点生成几道练习题',
      time: formatTimeFull(new Date()),
    })
    loading.value = true
    await scrollToBottom()
    const aiMsgIdx = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      time: formatTimeFull(new Date()),
      loading: true,
    })
    try {
      // 后端 /recommendations/questions 的业务字段是 recommendations（{content,type,difficulty}[]），
      // 此前按 data.questions 取值取不到，界面把整个 JSON 当回答打字机播了出来。
      const data = unwrap(await aiApi.questionRecommendations({ courseId: selectedCourseId.value }))
      const list = Array.isArray(data?.recommendations) ? data.recommendations : []
      const reply = list.length
        ? list.map((q, i) => `${i + 1}. ${q.content ?? ''}`).join('\n\n')
        : '练习题暂时无法生成，请稍后再试。'
      await typewriterEffect(aiMsgIdx, reply)
    } catch (e) {
      logger.error('生成练习题失败:', e)
      messages.value[aiMsgIdx].content = '抱歉，生成练习题失败，请稍后再试。'
      messages.value[aiMsgIdx].error = true
    } finally {
      messages.value[aiMsgIdx].loading = false
      await scrollToBottom()
    }
  }

  /* ---- 复制/重新生成 ---- */
  function copyMessage(content) {
    navigator.clipboard
      .writeText(stripHtml(content))
      .then(() => ElMessage.success('已复制到剪贴板'))
      .catch(() => ElMessage.error('复制失败'))
  }

  async function regenerateMessage(index) {
    let lastUserMsg = ''
    for (let i = index - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') {
        lastUserMsg = messages.value[i].content
        break
      }
    }
    if (!lastUserMsg) return
    messages.value.splice(index)
    loading.value = true
    const aiMsgIdx = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      time: formatTimeFull(new Date()),
      loading: true,
    })
    try {
      const payload: Record<string, unknown> = { question: lastUserMsg }
      if (selectedCourseId.value) payload.courseId = selectedCourseId.value
      const { answer, sources } = readQa(await aiApi.qaAsk(payload))
      await typewriterEffect(aiMsgIdx, answer || 'AI 未返回内容，请稍后再试。')
      messages.value[aiMsgIdx].sources = sources
    } catch {
      ElMessage.error('重新生成失败')
    } finally {
      loading.value = false
      await scrollToBottom()
    }
  }

  /* ---- Markdown 渲染（XSS 安全） ---- */
  function renderMarkdown(text) {
    if (!text) return ''
    let html = escapeHtml(text)
    html = html.replace(
      /```(\w*)\n?([\s\S]*?)```/g,
      '<pre class="code-block"><code>$2</code></pre>',
    )
    html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    html = html.replace(/\n/g, '<br/>')
    return sanitizeHtml(html)
  }

  function escapeHtml(text) {
    const div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
  }

  function stripHtml(html) {
    const div = document.createElement('div')
    div.innerHTML = html
    return div.textContent || div.innerText || ''
  }

  async function scrollToBottom() {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }

  /* ---- 对话管理 ---- */
  function createNewChat() {
    currentConvId.value = Date.now()
    messages.value = []
  }

  function switchConversation(convId) {
    currentConvId.value = convId
    messages.value = []
  }

  /* ---- 时间格式化 ---- */
  function formatTime(timeStr) {
    if (!timeStr) return ''
    const date = new Date(timeStr)
    const now = new Date()
    const diffMin = Math.floor((now.getTime() - date.getTime()) / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin}分钟前`
    if (diffMin < 1440) return `${Math.floor(diffMin / 60)}小时前`
    return `${date.getMonth() + 1}/${date.getDate()}`
  }

  function formatTimeFull(date) {
    if (!(date instanceof Date)) date = new Date(date)
    const h = date.getHours().toString().padStart(2, '0')
    const m = date.getMinutes().toString().padStart(2, '0')
    return `${h}:${m}`
  }

  /* ---- 语音：STT 输入 + TTS 朗读 ---- */
  const recognitionSupported = isRecognitionSupported()
  const speechSupported = isSpeechSupported()
  const recording = ref(false)
  let recognitionCtrl = null

  function toggleRecording() {
    if (recording.value) {
      recognitionCtrl?.stop()
      recognitionCtrl = null
      recording.value = false
      return
    }
    const ctrl = startRecognition({
      onResult: (text, isFinal) => {
        inputMessage.value = text
        if (isFinal) {
          recording.value = false
          handleSend()
        }
      },
      onEnd: () => {
        recording.value = false
        recognitionCtrl = null
      },
      onError: () => {
        ElMessage.warning('语音识别不可用，请检查浏览器权限或改用文本输入')
      },
    })
    if (!ctrl) {
      ElMessage.warning('当前浏览器不支持语音识别，请使用 Chrome / Edge')
      return
    }
    recognitionCtrl = ctrl
    recording.value = true
    ElMessage.info('正在聆听…请说话（点击按钮停止）')
  }

  function speakMessage(content) {
    if (!content) return
    const text = content.replace(/[#*`>\-|]/g, '').slice(0, 500)
    speak(text, { onError: () => ElMessage.warning('语音朗读不可用') })
  }

  onMounted(() => {
    loadCourses()
    loadConversations()
  })

  onBeforeUnmount(() => {
    if (recognitionCtrl) {
      recognitionCtrl.stop()
      recognitionCtrl = null
    }
    stopSpeaking()
    activeIntervals.value.forEach((t) => clearInterval(t))
    activeIntervals.value = []
    if (sseController.value) {
      sseController.value.abort()
      sseController.value = null
    }
  })

  return {
    showSidebar,
    messagesRef,
    inputRef,
    courseList,
    selectedCourseId,
    conversations,
    currentConvId,
    messages,
    inputMessage,
    loading,
    recognitionSupported,
    speechSupported,
    recording,
    handleSend,
    sendQuickMessage,
    handleGenerateSummary,
    handleGenerateQuestion,
    copyMessage,
    regenerateMessage,
    renderMarkdown,
    createNewChat,
    switchConversation,
    formatTime,
    toggleRecording,
    speakMessage,
  }
}
