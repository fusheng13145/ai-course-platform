import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import aiApi from '@/api/ai'
import {
  mapSources,
  readQa,
  stripHtml,
  formatTimeFull,
  type ChatMessage,
} from './useAIAssistantShared'

/**
 * AI 学习助手聊天域
 * <p>承载消息发送（SSE 流式优先 + 非流式逐字降级）、快捷操作（总结课程/生成练习题）、
 * 重新生成/复制与打字机效果；课程选择与滚动容器（messagesRef）由编排层注入；
 * 定时器与 SSE 控制器的清理经 releaseResources 交还编排层的 onBeforeUnmount。</p>
 */
export function useAIAssistantChat(deps) {
  const { selectedCourseId, messagesRef } = deps

  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const loading = ref(false)

  const activeIntervals = ref([])
  const sseController = ref(null)

  async function scrollToBottom() {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
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
        const bizCode = (e as { code?: number })?.code
        // 503 时后端的 message 就是可诊断病因（凭据被拒 / 额度用尽 / 未配置 Key），原样呈现；
        // 其它失败仍用通用句——axios 的 "Network Error" 之类不该直接丢给学生。
        messages.value[aiMsgIdx].content =
          bizCode === 503 && e instanceof Error && e.message
            ? e.message
            : '抱歉，AI 服务暂时不可用，请稍后再试。'
        messages.value[aiMsgIdx].error = true
      }
    } finally {
      messages.value[aiMsgIdx].loading = false
      loading.value = false
      await scrollToBottom()
    }
  }

  /** SSE 流式：逐 token 推送；末帧出处写入当前回答气泡供溯源展示 */
  function handleSendStream(aiMsgIdx, question) {
    return new Promise<void>((resolve, reject) => {
      // 与 qaStream 的入参契约对齐：索引签名允许扩展字段，question 必填（原声明缺 question 键保证）。
      const params: { [key: string]: string | number | boolean; question: string } = { question }
      if (selectedCourseId.value) params.courseId = selectedCourseId.value
      const controller = aiApi.qaStream(params, {
        onToken(token) {
          messages.value[aiMsgIdx].content += token
        },
        onSources(sources) {
          // 出处末帧：与 /qa/ask 同口径还原；空数组表示本次无溯源，交由渲染侧按长度隐藏
          messages.value[aiMsgIdx].sources = mapSources(sources)
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

  /** 清理打字机定时器与在途 SSE（编排层 onBeforeUnmount 调用） */
  function releaseResources() {
    activeIntervals.value.forEach((t) => clearInterval(t))
    activeIntervals.value = []
    if (sseController.value) {
      sseController.value.abort()
      sseController.value = null
    }
  }

  return {
    messages,
    inputMessage,
    loading,
    handleSend,
    sendQuickMessage,
    handleGenerateSummary,
    handleGenerateQuestion,
    copyMessage,
    regenerateMessage,
    releaseResources,
  }
}
