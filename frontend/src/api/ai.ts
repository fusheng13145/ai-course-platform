import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

/**
 * AI 端点专用超时：本地 Ollama / 长上下文生成经常超过全局 30s。
 * 全局 30s 会让「还在生成」被前端判成断网（错误体为空，只剩“回答失败”）。
 * 120s 覆盖单次 RAG 生成；后端自身的 60s 读超时仍优先兜底。
 */
const AI_TIMEOUT = 120_000

// ==================== （当前推荐接口） ====================
/** AI 问答提问 */
export const qaAsk = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/qa/ask', data, { timeout: AI_TIMEOUT }) as Promise<ApiEnvelope<ApiData>>
/** 全局 AI 助手提问（不读取课程知识库，不能携带 courseId） */
export const qaAskGlobal = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/qa/global/ask', data, { timeout: AI_TIMEOUT }) as Promise<ApiEnvelope<ApiData>>
/** AI 问答追问 */
export const qaContinue = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/qa/continue', data, { timeout: AI_TIMEOUT }) as Promise<ApiEnvelope<ApiData>>
/** 获取问答会话列表 */
export const getSessions = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/qa/sessions') as Promise<ApiEnvelope<ApiData>>
/** 获取单个会话详情（含持久化的消息正文） */
export const getSessionDetail = (sessionId: string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/ai/qa/sessions/${encodeURIComponent(sessionId)}`) as Promise<ApiEnvelope<ApiData>>
/** AI 作文批改 */
export const essayCorrect = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/essay/correct', data, { timeout: AI_TIMEOUT }) as Promise<ApiEnvelope<ApiData>>
/** AI 作文润色 */
export const essayPolish = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/essay/polish', data, { timeout: AI_TIMEOUT }) as Promise<ApiEnvelope<ApiData>>
/** 获取学习推荐 */
export const learningRecommendations = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/recommendations/learning', {
    timeout: AI_TIMEOUT,
  }) as Promise<ApiEnvelope<ApiData>>
/** 获取题目推荐 */
export const questionRecommendations = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/recommendations/questions', {
    params,
    timeout: AI_TIMEOUT,
  }) as Promise<ApiEnvelope<ApiData>>
/** 薄弱点分析 */
export const weakPointsAnalysis = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/analysis/weak-points', {
    params,
    timeout: AI_TIMEOUT,
  }) as Promise<ApiEnvelope<ApiData>>
/** 获取学习建议 */
export const learningAdvice = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/advice/learning', {
    params,
    timeout: AI_TIMEOUT,
  }) as Promise<ApiEnvelope<ApiData>>
/** 生成学习路径 */
export const generateLearningPath = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/learning-path/generate', data, {
    timeout: AI_TIMEOUT,
  }) as Promise<ApiEnvelope<ApiData>>

/**
 * 检索出处条目（与后端 QaSourceVO 对齐；后端下发前已做 HTML 转义）。
 * <p>SSE 末帧 {@code {"sources":[...]}} 与同步 /qa/ask 的 sources 使用同一形状，
 * 故此处收敛为单一类型，供流式与非流式两条链路共用。</p>
 */
export interface QaSourcePayload {
  documentId?: number
  documentName?: string
  relevanceScore?: number
  snippet?: string
}

/**
 * SSE 流式问答——逐 token 推送，实现打字机效果。
 *
 * 使用原生 fetch + ReadableStream 而非 axios（axios 不支持流式消费），
 * 通过 onToken 回调逐片段传递 delta 文本，前端按需拼装 UI。
 * 后端每帧均为 JSON：增量帧 {"token":"..."}，末帧 {"sources":[...]}。
 *
 * @param {{ question: string, courseId?: number|string, sessionId?: number, context?: string }} params
 * @param {Object} callbacks
 * @param {(token: string) => void} callbacks.onToken   - 每收到一个 delta 片段时调用
 * @param {(sources: QaSourcePayload[]) => void} callbacks.onSources - 收到出处末帧时调用（无出处处为空数组）
 * @param {(error: Error) => void}  callbacks.onError   - 流中断时调用
 * @param {() => void}              callbacks.onDone    - 流正常结束时调用
 * @returns {AbortController} 可用于中途取消流
 */
export function qaStream(
  params: {
    question: string
    courseId?: number | string
    sessionId?: string | number
    context?: string
    [key: string]: string | number | boolean | undefined
  },
  {
    onToken,
    onSources,
    onError,
    onDone,
    onSession,
  }: {
    onToken?: (token: string) => void
    onSources?: (sources: QaSourcePayload[]) => void
    onError?: (error: Error) => void
    onDone?: () => void
    onSession?: (sessionId: string) => void
  } = {},
): AbortController {
  const controller = new AbortController()

  const url = new URL('/api/ai/qa/stream', window.location.origin)
  Object.entries(params).forEach(([k, v]: ApiData) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v))
  })

  fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal: controller.signal,
  })
    .then(async (response: ApiData) => {
      if (!response.ok) {
        // 503 等错误也走 SSE 通道：尽量把后端的业务话术带出来，避免前端只剩裸状态码
        let detail = ''
        try {
          if (typeof response.text === 'function') {
            const text = await response.text()
            const parsed = JSON.parse(text)
            detail = typeof parsed?.message === 'string' ? parsed.message : ''
          }
        } catch {
          // 非 JSON 错误体：保留状态码即可
        }
        throw new Error(`SSE 请求失败: HTTP ${response.status}${detail ? ` - ${detail}` : ''}`)
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
              if (typeof parsed?.sessionId === 'string' && parsed.sessionId) {
                // 首帧：服务端会话ID，供前端建立/续接历史记录
                onSession?.(parsed.sessionId)
              } else if (Array.isArray(parsed.sources)) {
                // 出处末帧：与增量帧互斥，收到即回调 onSources（空数组也算，表示本次无溯源）
                onSources?.(parsed.sources)
              } else if (parsed.token && onToken) {
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
    .catch((err: ApiData) => {
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
  qaAskGlobal,
  qaContinue,
  getSessions,
  getSessionDetail,
  qaStream,
  essayCorrect,
  essayPolish,
  learningRecommendations,
  questionRecommendations,
  weakPointsAnalysis,
  learningAdvice,
  generateLearningPath,
}
