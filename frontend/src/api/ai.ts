import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// ==================== （当前推荐接口） ====================
/** AI 问答提问 */
export const qaAsk = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/qa/ask', data) as Promise<ApiEnvelope<ApiData>>
/** AI 问答追问 */
export const qaContinue = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/qa/continue', data) as Promise<ApiEnvelope<ApiData>>
/** 获取问答会话列表 */
export const getSessions = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/qa/sessions') as Promise<ApiEnvelope<ApiData>>
/** AI 作文批改 */
export const essayCorrect = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/essay/correct', data) as Promise<ApiEnvelope<ApiData>>
/** AI 作文润色 */
export const essayPolish = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/essay/polish', data) as Promise<ApiEnvelope<ApiData>>
/** 获取学习推荐 */
export const learningRecommendations = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/recommendations/learning') as Promise<ApiEnvelope<ApiData>>
/** 获取题目推荐 */
export const questionRecommendations = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/recommendations/questions', { params }) as Promise<ApiEnvelope<ApiData>>
/** 薄弱点分析 */
export const weakPointsAnalysis = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/analysis/weak-points', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取学习建议 */
export const learningAdvice = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/ai/advice/learning', { params }) as Promise<ApiEnvelope<ApiData>>
/** 生成学习路径 */
export const generateLearningPath = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/ai/learning-path/generate', data) as Promise<ApiEnvelope<ApiData>>

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
    sessionId?: number
    context?: string
    [key: string]: string | number | boolean | undefined
  },
  {
    onToken,
    onSources,
    onError,
    onDone,
  }: {
    onToken?: (token: string) => void
    onSources?: (sources: QaSourcePayload[]) => void
    onError?: (error: Error) => void
    onDone?: () => void
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
              if (Array.isArray(parsed.sources)) {
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
