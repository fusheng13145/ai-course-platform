<template>
  <div class="fade-in chat-layout">
    <!-- 左侧：对话历史（可选显示） -->
    <aside class="chat-sidebar" :class="{ collapsed: !showSidebar }">
      <div class="sidebar-header">
        <h3 class="sidebar-title">对话历史</h3>
        <el-button text size="small" @click="createNewChat">
          <el-icon><Plus /></el-icon> 新建
        </el-button>
      </div>

      <div class="conversation-list">
        <div
          v-for="(conv, idx) in conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: currentConvId === conv.id }"
          @click="switchConversation(conv.id)"
        >
          <el-icon class="conv-icon"><ChatDotRound /></el-icon>
          <span class="conv-title">{{ conv.title || '新对话' }}</span>
          <span class="conv-time">{{ formatTime(conv.updatedAt) }}</span>
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无历史对话" :image-size="60" />
      </div>
    </aside>

    <!-- 右侧：主聊天区域 -->
    <main class="chat-main">
      <!-- 聊天头部 -->
      <header class="chat-header">
        <el-button text @click="showSidebar = !showSidebar" class="toggle-sidebar">
          <el-icon :size="18"><Fold /></el-icon>
        </el-button>
        <div class="header-center">
          <el-icon color="var(--ac-primary)" :size="20"><MagicStick /></el-icon>
          <span class="ai-name">AI 学习助手</span>
        </div>
        <div class="header-right">
          <el-select v-model="selectedCourseId" placeholder="选择课程" size="small" style="width: 160px" clearable>
            <el-option
              v-for="c in courseList"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </div>
      </header>

      <!-- 消息列表区域 -->
      <div class="messages-container" ref="messagesRef">
        <!-- 欢迎消息 -->
        <div v-if="messages.length === 0 && !loading" class="welcome-area">
          <div class="welcome-icon">
            <el-icon :size="48" color="var(--ac-primary)"><MagicStick /></el-icon>
          </div>
          <h3 class="welcome-title">你好，我是 AI 学习助手</h3>
          <p class="welcome-desc">我可以帮你解答课程问题、总结知识点、生成练习题</p>

          <!-- 快捷操作 -->
          <div class="quick-actions">
            <div class="quick-btn" @click="handleGenerateSummary">
              <el-icon><Document /></el-icon>
              <span>总结课程</span>
            </div>
            <div class="quick-btn" @click="handleGenerateQuestion">
              <el-icon><EditPen /></el-icon>
              <span>生成练习题</span>
            </div>
            <div class="quick-btn" @click="sendQuickMessage('请解释一下这个概念：')">
              <el-icon><QuestionFilled /></el-icon>
              <span>解释概念</span>
            </div>
            <div class="quick-btn" @click="sendQuickMessage('请为我制定一个学习计划')">
              <el-icon><Calendar /></el-icon>
              <span>学习计划</span>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="message-list">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message-row"
            :class="msg.role"
          >
            <!-- 用户消息 -->
            <template v-if="msg.role === 'user'">
              <div class="message-bubble user-bubble">
                <p class="message-text">{{ msg.content }}</p>
                <span class="message-time">{{ msg.time }}</span>
              </div>
              <div class="avatar user-avatar">
                <el-icon :size="20"><User /></el-icon>
              </div>
            </template>

            <!-- AI 回复消息 -->
            <template v-else>
              <div class="avatar ai-avatar">
                <el-icon :size="20"><MagicStick /></el-icon>
              </div>
              <div class="message-bubble ai-bubble">
                <div class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
                <span class="message-time">{{ msg.time }}</span>
                <!-- 操作按钮 -->
                <div class="message-actions">
                  <el-button text size="small" @click="copyMessage(msg.content)">
                    <el-icon><CopyDocument /></el-icon> 复制
                  </el-button>
                  <el-button text size="small" @click="regenerateMessage(idx)">
                    <el-icon><RefreshRight /></el-icon> 重新生成
                  </el-button>
                </div>
              </div>
            </template>
          </div>

          <!-- AI 正在输入提示 -->
          <div v-if="loading" class="message-row assistant">
            <div class="avatar ai-avatar">
              <el-icon :size="20"><MagicStick /></el-icon>
            </div>
            <div class="message-bubble ai-bubble typing-indicator">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            ref="inputRef"
            v-model="inputMessage"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入您的问题...（Enter 发送，Shift+Enter 换行）"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
            @keydown.enter.shift.exact="handleNewLine"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            circle
            :loading="loading"
            :disabled="!inputMessage.trim()"
            @click="handleSend"
            class="send-btn"
          />
        </div>
        <p class="input-hint">AI 助手基于课程内容提供学习建议，回答仅供参考</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, ChatDotRound, Fold, MagicStick,
  Document, EditPen, QuestionFilled, Calendar,
  User, CopyDocument, RefreshRight, Promotion
} from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import aiApi from '@/api/ai'
import courseApi from '@/api/course'

/* ---- 布局状态 ---- */
const showSidebar = ref(true)
const messagesRef = ref(null)
const inputRef = ref(null)

/* ---- 课程选择 ---- */
const courseList = ref([])
const selectedCourseId = ref(null)

/* ---- 对话数据 ---- */
const conversations = ref([])   // 对话历史列表
const currentConvId = ref(Date.now())
const messages = ref([])         // 当前对话的消息
const inputMessage = ref('')
const loading = ref(false)

/* ---- 加载课程列表 ---- */
async function loadCourses() {
  try {
    const res = await courseApi.getCourseList()
    const list = res.data?.list || res.data || res || []
    courseList.value = Array.isArray(list) ? list.slice(0, 10) : []
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

/* ---- 发送消息（支持打字机流式效果）---- */
async function handleSend() {
  const content = inputMessage.value.trim()
  if (!content || loading.value) return

  // 添加用户消息
  const now = new Date()
  messages.value.push({
    role: 'user',
    content: content,
    time: formatTimeFull(now)
  })

  inputMessage.value = ''
  loading.value = true

  await scrollToBottom()

  // 预留 AI 消息位置（用于流式输出）
  const aiMsgIdx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTimeFull(new Date()),
    loading: true
  })

  try {
    // 调用 AI 聊天 API
    const res = await aiApi.aiChat(content, '', selectedCourseId.value)
    const reply = res.data?.response || res?.response ||
                   res.data?.reply || res?.reply ||
                   res.data?.content || res?.content ||
                   '感谢您的提问！我正在学习中，稍后会有更完善的回答。'

    // 使用打字机效果逐字显示 AI 回复
    await typewriterEffect(aiMsgIdx, reply)
  } catch (e) {
    console.error('AI 聊天失败:', e)
    messages.value[aiMsgIdx].content = '抱歉，AI 服务暂时不可用，请稍后再试。'
    messages.value[aiMsgIdx].error = true
  } finally {
    messages.value[aiMsgIdx].loading = false
    await scrollToBottom()
  }
}

/**
 * 打字机效果 - 逐字显示文本，模拟流式输出体验
 * @param {number} msgIndex 消息在列表中的索引
 * @param {string} text 要显示的完整文本
 */
function typewriterEffect(msgIndex, text) {
  return new Promise(resolve => {
    let i = 0
    messages.value[msgIndex].content = ''

    const interval = setInterval(() => {
      if (i < text.length) {
        messages.value[msgIndex].content += text[i]
        i++
        // 每隔几个字符滚动一次到底部，保持阅读体验
        if (i % 3 === 0) {
          scrollToBottom()
        }
      } else {
        clearInterval(interval)
        resolve()
      }
    }, 20) // 每秒约50字，流畅的打字速度
  })
}

/* ---- 快捷操作发送 ---- */
async function sendQuickMessage(text) {
  inputMessage.value = text
  await nextTick()
  handleSend()
}

/* ---- 专用快捷：AI 总结课程 ---- */
async function handleGenerateSummary() {
  if (!selectedCourseId.value) {
    ElMessage.warning('请先选择一个课程')
    return
  }
  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: '请帮我总结当前课程的核心知识点',
    time: formatTimeFull(new Date())
  })
  loading.value = true
  await scrollToBottom()

  const aiMsgIdx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTimeFull(new Date()),
    loading: true
  })

  try {
    const res = await aiApi.generateSummary({
      courseId: selectedCourseId.value,
      type: 'summary'
    })
    const reply = res.data?.content || res.data?.summary ||
                   res.data?.response || res?.response || res?.content ||
                   '正在为您生成课程总结，请稍候...'
    await typewriterEffect(aiMsgIdx, reply)
  } catch (e) {
    console.error('生成总结失败:', e)
    messages.value[aiMsgIdx].content = '抱歉，生成课程总结失败，请稍后再试。'
    messages.value[aiMsgIdx].error = true
  } finally {
    messages.value[aiMsgIdx].loading = false
    await scrollToBottom()
  }
}

/* ---- 专用快捷：AI 生成练习题 ---- */
async function handleGenerateQuestion() {
  if (!selectedCourseId.value) {
    ElMessage.warning('请先选择一个课程')
    return
  }
  messages.value.push({
    role: 'user',
    content: '请根据当前知识点生成几道练习题',
    time: formatTimeFull(new Date())
  })
  loading.value = true
  await scrollToBottom()

  const aiMsgIdx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTimeFull(new Date()),
    loading: true
  })

  try {
    const res = await aiApi.generateQuestion({
      courseId: selectedCourseId.value,
      count: 5
    })
    const data = res.data?.data || res.data || res || {}
    let reply = ''
    if (Array.isArray(data.questions)) {
      reply = data.questions.map((q, i) =>
        `${i + 1}. ${q.content || q.questionText || q}\n${q.answer ? `   答案：${q.answer}` : ''}`
      ).join('\n\n')
    } else if (typeof data === 'string') {
      reply = data
    } else {
      reply = JSON.stringify(data, null, 2)
    }
    if (!reply) reply = '已为您生成练习题，请查看详情。'
    await typewriterEffect(aiMsgIdx, reply)
  } catch (e) {
    console.error('生成练习题失败:', e)
    messages.value[aiMsgIdx].content = '抱歉，生成练习题失败，请稍后再试。'
    messages.value[aiMsgIdx].error = true
  } finally {
    messages.value[aiMsgIdx].loading = false
    await scrollToBottom()
  }
}

/* ---- 处理换行 ---- */
function handleNewLine(e) {
  // Shift+Enter 默认行为就是换行，不需要额外处理
  // 但我们需要阻止默认的发送行为（已在 keydown.enter.exact 中处理）
}

/* ---- 复制消息 ---- */
function copyMessage(content) {
  navigator.clipboard.writeText(stripHtml(content)).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

/* ---- 重新生成最后一条AI回复（支持打字机效果）---- */
async function regenerateMessage(index) {
  // 找到对应的用户消息
  let lastUserMsg = ''
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      lastUserMsg = messages.value[i].content
      break
    }
  }

  if (!lastUserMsg) return

  // 移除当前回复和之后的消息
  messages.value.splice(index)
  loading.value = true

  // 预留新的 AI 消息位置
  const aiMsgIdx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTimeFull(new Date()),
    loading: true
  })

  try {
    const res = await aiApi.aiChat(lastUserMsg, '', selectedCourseId.value)
    const reply = res.data?.response || res?.response || '已重新生成回复。'
    // 使用打字机效果显示重新生成的回复
    await typewriterEffect(aiMsgIdx, reply)
  } catch (e) {
    ElMessage.error('重新生成失败')
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

/* ---- 简单 Markdown 渲染（XSS 安全） ---- */
function renderMarkdown(text) {
  if (!text) return ''
  let html = escapeHtml(text)
  // 代码块 ```code```
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="code-block"><code>$2</code></pre>')
  // 行内代码 `code`
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  // 粗体 **text**
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // 换行
  html = html.replace(/\n/g, '<br/>')
  // DOMPurify 最终清理：防止任何 XSS 攻击
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['pre', 'code', 'strong', 'br', 'p', 'em', 'ul', 'ol', 'li', 'blockquote', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'],
    ALLOWED_ATTR: ['class']
  })
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

/* ---- 滚动到底部 ---- */
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
  // 实际项目中这里应该从后端或本地存储加载对应的消息
  messages.value = []
}

/* ---- 时间格式化 ---- */
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)

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

/* ---- 初始化 ---- */
onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 60px);
  gap: 0;
  border-radius: var(--ac-radius-lg);
  overflow: hidden;
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
}

/* ---- 侧边栏 ---- */
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: var(--ac-bg);
  border-right: 1px solid var(--ac-border);
  display: flex;
  flex-direction: column;
  transition: all var(--ac-transition-normal);
  overflow: hidden;
}

.chat-sidebar.collapsed {
  width: 0;
  padding: 0;
  border-right: none;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-sm);
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  padding: 10px 12px;
  border-radius: var(--ac-radius-sm);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  margin-bottom: 4px;
}

.conversation-item:hover {
  background: var(--ac-bg-hover);
}

.conversation-item.active {
  background: var(--ac-primary-bg);
}

.conv-icon {
  color: var(--ac-text-muted);
  font-size: 16px;
  flex-shrink: 0;
}

.conv-title {
  flex: 1;
  font-size: 13px;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-time {
  font-size: 11px;
  color: var(--ac-text-muted);
  flex-shrink: 0;
}

/* ---- 主聊天区 ---- */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
  flex-shrink: 0;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.toggle-sidebar {
  color: var(--ac-text-muted);
}

.header-right {
  display: flex;
  align-items: center;
}

/* ---- 消息区域 ---- */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-lg);
  scroll-behavior: smooth;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  max-width: 520px;
  margin: 0 auto;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--ac-primary-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--ac-spacing-md);
}

.welcome-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-xs);
}

.welcome-desc {
  font-size: 14px;
  color: var(--ac-text-muted);
  margin: 0 0 var(--ac-spacing-xl);
  line-height: 1.6;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-sm);
  width: 100%;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.quick-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--ac-shadow-sm);
  border-color: var(--ac-primary-light);
  color: var(--ac-primary);
}

.quick-btn .el-icon {
  font-size: 16px;
  color: var(--ac-primary);
}

/* ---- 消息行 ---- */
.message-list {
  max-width: 800px;
  margin: 0 auto;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
  max-width: 85%;
}

.message-row.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4px;
}

.user-avatar {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}

.ai-avatar {
  background: rgba(184,154,138,0.15);
  color: var(--ac-secondary);
}

.message-bubble {
  padding: 12px 16px;
  border-radius: var(--ac-radius-lg);
  position: relative;
  max-width: 100%;
}

.user-bubble {
  background: var(--ac-primary);
  color: #fff;
  border-top-right-radius: 4px;
}

.ai-bubble {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-top-left-radius: 4px;
}

.message-text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  margin: 0;
}

.message-time {
  display: block;
  font-size: 11px;
  color: var(--ac-text-muted);
  margin-top: 6px;
  opacity: 0.7;
}

.user-bubble .message-time {
  color: rgba(255,255,255,0.7);
  text-align: right;
}

.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--ac-border);
}

/* ---- Markdown 样式 ---- */
.markdown-body :deep(pre.code-block) {
  background: var(--ac-bg-hover);
  border-radius: var(--ac-radius-sm);
  padding: 12px 16px;
  margin: 8px 0;
  overflow-x: auto;
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ac-text-primary);
}

.markdown-body :deep(code.inline-code) {
  background: var(--ac-bg-hover);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 13px;
  color: var(--ac-danger);
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: var(--ac-text-primary);
}

/* ---- 打字机动画 ---- */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 16px 20px !important;
  min-width: 60px;
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ac-text-muted);
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ---- 输入区域 ---- */
.input-area {
  padding: var(--ac-spacing-md) var(--ac-spacing-lg) var(--ac-spacing-lg);
  border-top: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: var(--ac-spacing-sm);
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  padding: 6px 10px;
  transition: border-color var(--ac-transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--ac-primary-light);
  box-shadow: 0 0 0 3px var(--ac-primary-bg);
}

.input-wrapper :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 6px 4px;
  font-size: 14px;
  background: transparent;
}

.send-btn {
  flex-shrink: 0;
  margin-bottom: 2px;
}

.input-hint {
  font-size: 11px;
  color: var(--ac-text-muted);
  text-align: center;
  margin: 8px 0 0;
}

@media (max-width: 768px) {
  .chat-sidebar { display: none; }
  .quick-actions { grid-template-columns: 1fr; }
  .message-row { max-width: 95%; }
}
</style>
