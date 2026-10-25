import { ref, onMounted, onBeforeUnmount } from 'vue'
import logger from '@/utils/logger'
import { firstNonEmptyArray } from '@/utils/response'
import aiApi from '@/api/ai'
import courseApi from '@/api/course'
import { useAIAssistantChat } from './useAIAssistantChat'
import { useAIAssistantSpeech } from './useAIAssistantSpeech'
import {
  formatTime,
  renderMarkdown,
  type ChatMessage,
  type ConversationRow,
  type QaSource,
} from './useAIAssistantShared'

// 消息/出处类型经编排层重导出，消费方（页面与聊天面板）的导入路径不变
export type { ChatMessage, QaSource }

/**
 * AI 学习助手聊天逻辑（编排层）
 * <p>承载侧栏开关、模板引用、课程选择、对话历史与对话管理（新建/切换）；
 * 消息发送/快捷操作拆见 useAIAssistantChat，语音输入/朗读拆见 useAIAssistantSpeech，
 * Markdown 渲染与时间格式化拆见 useAIAssistantShared。</p>
 */
export function useAIAssistant() {
  const showSidebar = ref(true)
  const messagesRef = ref(null)
  const inputRef = ref(null)

  const courseList = ref([])
  const selectedCourseId = ref(null)

  const conversations = ref([])
  const currentConvId = ref(Date.now())

  /* ---- 课程列表 ---- */
  async function loadCourses() {
    try {
      courseList.value = await courseApi.getAllCourses()
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
      const list = firstNonEmptyArray<ConversationRow>(
        res.data?.list,
        res.data?.records,
        res.data,
        res,
      )
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

  const chat = useAIAssistantChat({ selectedCourseId, messagesRef })
  const speech = useAIAssistantSpeech({
    inputMessage: chat.inputMessage,
    handleSend: chat.handleSend,
  })

  /* ---- 对话管理 ---- */
  function createNewChat() {
    currentConvId.value = Date.now()
    chat.messages.value = []
  }

  function switchConversation(convId) {
    currentConvId.value = convId
    chat.messages.value = []
  }

  onMounted(() => {
    loadCourses()
    loadConversations()
  })

  onBeforeUnmount(() => {
    speech.stopSpeech()
    chat.releaseResources()
  })

  return {
    showSidebar,
    messagesRef,
    inputRef,
    courseList,
    selectedCourseId,
    conversations,
    currentConvId,
    messages: chat.messages,
    inputMessage: chat.inputMessage,
    loading: chat.loading,
    recognitionSupported: speech.recognitionSupported,
    speechSupported: speech.speechSupported,
    recording: speech.recording,
    handleSend: chat.handleSend,
    sendQuickMessage: chat.sendQuickMessage,
    handleGenerateSummary: chat.handleGenerateSummary,
    handleGenerateQuestion: chat.handleGenerateQuestion,
    copyMessage: chat.copyMessage,
    regenerateMessage: chat.regenerateMessage,
    renderMarkdown,
    createNewChat,
    switchConversation,
    formatTime,
    toggleRecording: speech.toggleRecording,
    speakMessage: speech.speakMessage,
  }
}
