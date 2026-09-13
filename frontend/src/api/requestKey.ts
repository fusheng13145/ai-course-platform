/**
 * 请求键生成
 *
 * 从 request.ts 抽离的纯函数：用于请求去重（取消 / 重试），
 * 与 HTTP 传输逻辑解耦，便于单测与复用。
 */
import type { AxiosRequestConfig } from 'axios'

/** 取消列表键：标识一次未完成请求 */
export function getRequestKey(config: AxiosRequestConfig): string {
  const { method, url, params, data } = config || {}
  return `${method}:${url}:${JSON.stringify(params)}:${JSON.stringify(data)}`
}

/** 重试去重键：标识一次已重试的请求 */
export function generateReqKey(config: AxiosRequestConfig): string {
  const { method, url, params, data } = config || {}
  const paramStr = params ? JSON.stringify(params) : ''
  const dataStr = data ? JSON.stringify(data) : ''
  return `${method}:${url}?${paramStr}|${dataStr}`
}
