<template>
  <main class="chat-main">
    <!-- 聊天头部 -->
    <header class="chat-header">
      <el-button
        text
        class="toggle-sidebar"
        v-bind="buttonA11y('折叠侧边栏')"
        @click="emit('toggle-sidebar')"
      >
        <el-icon :size="18"><Fold /></el-icon>
      </el-button>
      <div class="header-center">
        <el-icon color="var(--ac-primary)" :size="20"><MagicStick /></el-icon>
        <span class="ai-name">AI 学习助手</span>
      </div>
      <div class="header-right">
        <el-select
          :model-value="selectedCourseId"
          placeholder="选择课程"
          size="small"
          style="width: 210px"
          clearable
          @update:model-value="emit('update:course', $event)"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
    </header>

    <!-- 消息列表区域 -->
    <div ref="messagesEl" class="messages-container">
      <!-- 欢迎消息 + 快捷操作 -->
      <div v-if="messages.length === 0 && !loading" class="welcome-area">
        <div class="welcome-icon">
          <el-icon :size="48" color="var(--ac-primary)"><MagicStick /></el-icon>
        </div>
        <h3 class="welcome-title">你好，我是 AI 学习助手</h3>
        <p class="welcome-desc">我可以帮你解答课程问题、总结知识点、生成练习题</p>
        <div class="quick-actions">
          <div class="quick-btn" @click="emit('quick-summary')">
            <el-icon><Document /></el-icon><span>总结课程</span>
          </div>
          <div class="quick-btn" @click="emit('quick-question')">
            <el-icon><EditPen /></el-icon><span>生成练习题</span>
          </div>
          <div class="quick-btn" @click="emit('quick-send', '请解释一下这个概念：')">
            <el-icon><QuestionFilled /></el-icon><span>解释概念</span>
          </div>
          <div class="quick-btn" @click="emit('quick-send', '请为我制定一个学习计划')">
            <el-icon><Calendar /></el-icon><span>学习计划</span>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-else class="message-list">
        <div v-for="(msg, idx) in messages" :key="idx" class="message-row" :class="msg.role">
          <template v-if="msg.role === 'user'">
            <div class="message-bubble user-bubble">
              <p class="message-text">{{ msg.content }}</p>
              <span class="message-time">{{ msg.time }}</span>
            </div>
            <div class="avatar user-avatar">
              <el-icon :size="20"><User /></el-icon>
            </div>
          </template>
          <template v-else>
            <div class="avatar ai-avatar">
              <el-icon :size="20"><MagicStick /></el-icon>
            </div>
            <div class="message-bubble ai-bubble">
              <!-- 占位气泡自身承载输入中动画：此前另起一个 loading 气泡，打字机期间会出现两个气泡 -->
              <div v-if="msg.loading && !msg.content" class="typing-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
              <!-- Markdown 已转义并经 sanitize.js 净化 -->
              <!-- eslint-disable-next-line vue/no-v-html -->
              <div class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
              <!-- RAG 检索出处：后端已按展示口径转义，composable 还原后原样插值渲染 -->
              <div v-if="msg.sources && msg.sources.length" class="message-sources">
                <p class="sources-label">
                  <el-icon :size="14"><Document /></el-icon> 课程资料出处
                </p>
                <ul class="sources-list">
                  <li v-for="(s, si) in msg.sources" :key="si" class="source-item">
                    <span class="source-name">{{ s.documentName || `资料 #${s.documentId}` }}</span>
                    <span v-if="s.relevanceScore != null" class="source-score">
                      相关度 {{ Math.round(s.relevanceScore * 100) }}%
                    </span>
                    <p v-if="s.snippet" class="source-snippet">{{ clipSnippet(s.snippet) }}</p>
                  </li>
                </ul>
              </div>
              <template v-if="!msg.loading">
                <span class="message-time">{{ msg.time }}</span>
                <div class="message-actions">
                  <el-button
                    v-if="speechSupported"
                    text
                    size="small"
                    @click="emit('speak', msg.content)"
                  >
                    <el-icon><Microphone /></el-icon> 朗读
                  </el-button>
                  <el-button text size="small" @click="emit('copy', msg.content)">
                    <el-icon><CopyDocument /></el-icon> 复制
                  </el-button>
                  <el-button text size="small" @click="emit('regenerate', idx)">
                    <el-icon><RefreshRight /></el-icon> 重新生成
                  </el-button>
                </div>
              </template>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <div class="input-wrapper">
        <el-button
          v-if="recognitionSupported"
          circle
          :class="{ 'mic-active': recording }"
          :title="recording ? '点击停止' : '语音输入'"
          v-bind="buttonA11y(recording ? '停止语音输入' : '开始语音输入')"
          @click="emit('toggle-recording')"
        >
          <el-icon :size="17"><Microphone /></el-icon>
        </el-button>
        <el-input
          ref="inputEl"
          :model-value="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入您的问题...（Enter 发送，Shift+Enter 换行）"
          resize="none"
          @update:model-value="emit('update:input', $event)"
          @keydown.enter.exact.prevent="emit('send')"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          circle
          :loading="loading"
          :disabled="!inputMessage.trim()"
          class="send-btn"
          @click="emit('send')"
        />
      </div>
      <p class="input-hint">AI 助手基于课程内容提供学习建议，回答仅供参考</p>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, type PropType } from 'vue'
import type { ChatMessage } from '@/composables/useAIAssistant'
import {
  Fold,
  MagicStick,
  Document,
  EditPen,
  QuestionFilled,
  Calendar,
  User,
  CopyDocument,
  RefreshRight,
  Promotion,
  Microphone,
} from '@element-plus/icons-vue'
import { buttonA11y } from '@/composables/useA11y'

/**
 * AI 助手主聊天区
 * <p>头部（折叠/课程选择）+ 消息列表（欢迎区快捷操作、用户/AI 气泡、输入中动画、
 * 朗读/复制/重新生成操作）+ 输入区（语音/多行输入/发送）；
 * 全部交互经事件上抛（useAIAssistant）；DOM 引用经 register-refs 事件回传父级。</p>
 */
defineProps({
  courseList: { type: Array, default: () => [] },
  selectedCourseId: { type: [Number, String], default: null },
  messages: { type: Array as PropType<ChatMessage[]>, default: () => [] },
  inputMessage: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  recognitionSupported: { type: Boolean, default: false },
  speechSupported: { type: Boolean, default: false },
  recording: { type: Boolean, default: false },
  renderMarkdown: { type: Function, required: true },
})

const emit = defineEmits([
  'toggle-sidebar',
  'update:course',
  'update:input',
  'quick-summary',
  'quick-question',
  'quick-send',
  'send',
  'speak',
  'copy',
  'regenerate',
  'toggle-recording',
  'register-refs',
])

/** 出处片段截断展示（全文仍可在课程资料中查看） */
function clipSnippet(text) {
  if (typeof text !== 'string') return ''
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}

/** 消息滚动容器与输入框的 DOM 引用，挂载后回传给页面壳（滚动到底等逻辑在 useAIAssistant） */
const messagesEl = ref(null)
const inputEl = ref(null)
onMounted(() => emit('register-refs', { messages: messagesEl.value, input: inputEl.value }))
</script>

<style scoped>
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ac-border);
}
.header-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
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
  flex-shrink: 0;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-lg);
  min-height: 0;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}
.welcome-icon {
  margin-bottom: 12px;
}
.welcome-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 8px;
}
.welcome-desc {
  font-size: 14px;
  color: var(--ac-text-muted);
  margin: 0 0 24px;
}
.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}
.quick-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--ac-text-secondary);
  transition: all var(--ac-transition-fast);
}
.quick-btn:hover {
  border-color: var(--ac-primary);
  color: var(--ac-primary);
  background: var(--ac-primary-bg);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.message-row.user {
  justify-content: flex-end;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-avatar {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.ai-avatar {
  background: var(--violet-soft);
  color: var(--violet);
}
.message-bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: var(--ac-radius-md);
  font-size: 14px;
  line-height: 1.7;
}
.user-bubble {
  background: var(--ac-primary);
  color: var(--surface);
  border-top-right-radius: 4px;
}
.ai-bubble {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-top-left-radius: 4px;
}
.message-text {
  margin: 0;
  word-break: break-word;
  white-space: pre-wrap;
}
.message-sources {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px dashed var(--ac-border);
  border-radius: var(--ac-radius-sm);
  background: var(--ac-bg-subtle);
}
.sources-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.sources-list {
  margin: 0;
  padding-left: 16px;
}
.source-item {
  font-size: 12px;
  line-height: 1.6;
  color: var(--ac-text-secondary);
}
.source-score {
  margin-left: 6px;
  color: var(--ac-text-muted);
}
.source-snippet {
  margin: 2px 0 6px;
  color: var(--ac-text-muted);
  line-height: 1.5;
}
.message-time {
  display: block;
  font-size: 11px;
  color: var(--ac-text-muted);
  margin-top: 6px;
  opacity: 0.8;
}
.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}
.typing-indicator {
  display: flex;
  gap: 5px;
  align-items: center;
  /* 气泡自身已有内边距，此处不再叠加 */
  padding: 3px 0;
}
.typing-indicator .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ac-text-muted);
  animation: blink 1.2s infinite ease-in-out;
}
.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}

.input-area {
  padding: 12px 14px;
  border-top: 1px solid var(--ac-border);
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.send-btn {
  flex-shrink: 0;
}
.mic-active {
  color: var(--ac-danger);
  border-color: var(--ac-danger);
}
.input-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ac-text-muted);
  text-align: center;
}
</style>
