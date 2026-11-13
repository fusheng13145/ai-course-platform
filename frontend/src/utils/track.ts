/**
 * 业务埋点工具（2026-11-07 P2–P3 批次：学习行为埋点分析的地基）。
 *
 * 设计（与 monitoring/index.ts 同一套通道约定）：
 * - 端点复用 VITE_MONITOR_ENDPOINT；为空（默认）时整站埋点为 no-op，零网络副作用；
 * - 事件包络与错误上报共用一个采集端时以 `type: 'event'` 与错误（type 缺省）区分，
 *   采集端（Loki / OTel Collector / 自建）可按 type 分流；
 * - 发送通道优先 sendBeacon（页面卸载不丢包），降级 fetch keepalive；失败静默，绝不反噬业务；
 * - 埋点只记「发生了什么」，不携带任何作答内容等业务敏感数据；用户身份由后端会话解析，
 *   前端不上报 userId（避免可篡改的伪归属）。
 *
 * 已接入事件（后续按同一函数扩展）：
 * - course_view   { courseId }        课程详情页打开（CourseView）
 *
 * 聚合分析（漏斗/留存）属采集端能力，路线图见 docs/后续扩展路线图-2026-11-07.md §E。
 */

interface TrackPayload {
  event: string
  props?: Record<string, unknown>
}

const endpoint = import.meta?.env?.VITE_MONITOR_ENDPOINT || ''

export function track({ event, props = {} }: TrackPayload): void {
  if (!endpoint || typeof window === 'undefined') return
  const body = JSON.stringify({
    type: 'event',
    event,
    props,
    url: window.location.href,
    timestamp: new Date().toISOString(),
  })
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
    } else if (typeof fetch === 'function') {
      void fetch(endpoint, {
        method: 'POST',
        body,
        keepalive: true,
        headers: { 'Content-Type': 'application/json' },
      }).catch(() => {
        /* 埋点失败静默：分析数据绝不反噬学习体验 */
      })
    }
  } catch {
    /* 同上：no-op */
  }
}

export default { track }
