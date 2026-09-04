import request from './request'

// ==================== v2（当前推荐接口） ====================
/** AI 问答提问 */
export const qaAsk = (data) => request.post('/ai/qa/ask', data)
/** AI 问答追问 */
export const qaContinue = (data) => request.post('/ai/qa/continue', data)
/** 获取问答会话列表 */
export const getSessions = () => request.get('/ai/qa/sessions')
/** AI 作文批改 */
export const essayCorrect = (data) => request.post('/ai/essay/correct', data)
/** AI 作文润色 */
export const essayPolish = (data) => request.post('/ai/essay/polish', data)
/** 获取学习推荐 */
export const learningRecommendations = () => request.get('/ai/recommendations/learning')
/** 获取题目推荐 */
export const questionRecommendations = (params = {}) =>
  request.get('/ai/recommendations/questions', { params })
/** 薄弱点分析 */
export const weakPointsAnalysis = (params = {}) =>
  request.get('/ai/analysis/weak-points', { params })
/** 获取学习建议 */
export const learningAdvice = (params = {}) => request.get('/ai/advice/learning', { params })
/** 生成学习路径 */
export const generateLearningPath = (data) => request.post('/ai/learning-path/generate', data)

/**
 * SSE 流式问答——逐 token 推送，实现打字机效果。
 *
 * 使用原生 fetch + ReadableStream 而非 axios（axios 不支持流式消费），
 * 通过 onToken 回调逐片段传递 delta 文本，前端按需拼装 UI。
 *
 * @param {{ question: string, sessionId?: number, context?: string }} params
 * @param {Object} callbacks
 * @param {(token: string) => void} callbacks.onToken   - 每收到一个 delta 片段时调用
 * @param {(error: Error) => void}  callbacks.onError   - 流中断时调用
 * @param {() => void}              callbacks.onDone    - 流正常结束时调用
 * @returns {AbortController} 可用于中途取消流
 */
export function qaStream(
  params: { question: string; sessionId?: number; context?: string; [key: string]: any },
  {
    onToken,
    onError,
    onDone,
  }: {
    onToken?: (token: string) => void
    onError?: (error: Error) => void
    onDone?: () => void
  } = {},
): AbortController {
  const controller = new AbortController()

  const url = new URL('/api/ai/qa/stream', window.location.origin)
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v))
  })

  fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`SSE 请求失败: HTTP ${response.status}`)
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // 按行解析 SSE 事件（格式：data: xxx\n\n）
        const lines = buffer.split('\n')
        // 最后一个可能不完整，保留到下一个 chunk
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || trimmed.startsWith(':')) continue // 跳过注释和空行
          if (trimmed.startsWith('data:')) {
            const data = trimmed.slice(5).trim()
            if (data === '[DONE]') {
              onDone?.()
              return
            }
            try {
              const parsed = JSON.parse(data)
              if (parsed.token && onToken) {
                onToken(parsed.token)
              }
            } catch {
              // 非 JSON 数据按纯文本传递
              if (onToken) onToken(data)
            }
          }
        }
      }
      onDone?.()
    })
    .catch((err) => {
      console.error('[ai.js] if:', err)
      if (err.name !== 'AbortError') {
        onError?.(err)
      }
    })

  return controller
}

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  qaAsk,
  qaContinue,
  getSessions,
  qaStream,
  essayCorrect,
  essayPolish,
  learningRecommendations,
  questionRecommendations,
  weakPointsAnalysis,
  learningAdvice,
  generateLearningPath,
}
