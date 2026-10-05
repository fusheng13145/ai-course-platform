<template>
  <div class="fade-in chat-layout">
    <!-- 左侧：对话历史 -->
    <AiaSidebar
      :show-sidebar="showSidebar"
      :conversations="conversations"
      :current-conv-id="currentConvId"
      :format-time="formatTime"
      @new-chat="createNewChat"
      @switch="switchConversation"
    />

    <!-- 右侧：主聊天区 -->
    <AiaChatPanel
      :course-list="courseList"
      :selected-course-id="selectedCourseId"
      :messages="messages"
      :input-message="inputMessage"
      :loading="loading"
      :recognition-supported="recognitionSupported"
      :speech-supported="speechSupported"
      :recording="recording"
      :render-markdown="renderMarkdown"
      @toggle-sidebar="showSidebar = !showSidebar"
      @update:course="selectedCourseId = $event"
      @update:input="inputMessage = $event"
      @quick-summary="handleGenerateSummary"
      @quick-question="handleGenerateQuestion"
      @quick-send="sendQuickMessage"
      @send="handleSend"
      @newline="handleNewLine"
      @speak="speakMessage"
      @copy="copyMessage"
      @regenerate="regenerateMessage"
      @toggle-recording="toggleRecording"
      @register-refs="onRegisterRefs"
    />
  </div>
</template>

<script setup lang="ts">
import { useAIAssistant } from '@/composables/useAIAssistant'
import { useRoute } from 'vue-router'
import AiaSidebar from './components/AiaSidebar.vue'
import AiaChatPanel from './components/AiaChatPanel.vue'

/**
 * AI 学习助手页面
 * <p>页面壳：对话历史侧栏 + 主聊天区布局编排；
 * 全部聊天/语音/快捷操作逻辑由 useAIAssistant 承载。</p>
 */
const {
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
} = useAIAssistant()

/** Shift+Enter 换行：依赖 textarea 默认行为，无需额外处理 */
function handleNewLine() {}

/** 课程上下文：/student/courses/:id/ai 或统一路由 ?courseId 预选（setup 同步期完成，先于 onMounted） */
const route = useRoute()
const cid = route.params.id || route.query.courseId
if (cid) selectedCourseId.value = Number(cid)

/** 面板挂载后回传 DOM 引用，供 useAIAssistant 滚动到底等逻辑使用 */
function onRegisterRefs(els: { messages: HTMLElement | null; input: unknown }) {
  messagesRef.value = els.messages
  inputRef.value = els.input
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: var(--ac-spacing-md);
  height: calc(100vh - 180px);
  min-height: 480px;
  max-width: 1440px;
  margin: 0 auto;
}
</style>
