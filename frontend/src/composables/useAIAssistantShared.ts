import { sanitizeHtml } from '@/utils/sanitize'
import { unwrap, decodeHtml } from '@/utils/response'
import type { QaSourcePayload } from '@/api/ai'

/** 检索出处条目（与后端 QaSourceVO 对齐，控制器下发前已做 HTML 转义） */
export type QaSource = QaSourcePayload

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

/** 会话列表行的展示字段子集（字段名存在历史分叉，故全部可选） */
export interface ConversationRow {
  id?: number | string
  sessionId?: number | string
  title?: string
  summary?: string
  createdAt?: string
  updatedAt?: string
  messages?: unknown[]
  courseName?: string
}

/**
 * 还原后端展示层转义后的出处条目（documentName/snippet 同 answer 口径做过 HTML 转义）。
 * <p>流式（SSE 末帧 onSources）与非流式（/qa/ask 应答）两条链路共用同一还原，
 * 避免出现「一条链路还原、另一条把 &amp; 直接播给用户」的口径分叉。</p>
 */
export function mapSources(raw: unknown): QaSource[] {
  if (!Array.isArray(raw)) return []
  return (raw as QaSourcePayload[]).map((s) => ({
    ...s,
    documentName: decodeHtml(s.documentName),
    snippet: decodeHtml(s.snippet),
  }))
}

/**
 * 读取 /qa/ask 应答的答案与溯源出处。
 * <p>业务字段在信封的 data 里（answer / sessionId / sources）；历史实现按
 * response / reply / content 取值，全部取不到时用固定文案兜底，真实 RAG 回答
 * 因此被静默丢弃。转义按展示口径还原，渲染仍走 escapeHtml + sanitize。</p>
 */
export function readQa(res: unknown): { answer: string; sources: QaSource[] } {
  const payload = (unwrap(res) ?? {}) as { answer?: string; sources?: QaSourcePayload[] }
  return { answer: decodeHtml(payload.answer), sources: mapSources(payload.sources) }
}

/** Markdown 子集渲染（XSS 安全：先 escape 再 sanitize） */
export function renderMarkdown(text) {
  if (!text) return ''
  let html = escapeHtml(text)
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="code-block"><code>$2</code></pre>')
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\n/g, '<br/>')
  return sanitizeHtml(html)
}

export function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

export function stripHtml(html) {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}

/** 相对时间（会话列表用） */
export function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diffMin = Math.floor((now.getTime() - date.getTime()) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffMin < 1440) return `${Math.floor(diffMin / 60)}小时前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}

/** 消息气泡时间戳（HH:mm） */
export function formatTimeFull(date) {
  if (!(date instanceof Date)) date = new Date(date)
  const h = date.getHours().toString().padStart(2, '0')
  const m = date.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}
