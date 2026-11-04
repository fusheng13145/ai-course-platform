/**
 * 上传会话的展示语义单一来源：状态译名、语义色调、字节格式化。
 *
 * 旧端（Element Plus 的 tag type）与 （自有 chip 类）各自只做一层薄映射，
 * 判定表本身不复制——两处各写一份必然分叉。
 */

import { isPageEnvelope, pickList, unwrap } from '@/utils/response'

export type UploadTone = 'ok' | 'warn' | 'danger' | 'info'

const STATUS_LABELS: Record<string, string> = {
  UPLOADING: '上传中',
  MERGED: '已合并',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FAILED: '失败',
  PENDING: '待上传',
}

export function uploadStatusLabel(status?: string): string {
  return STATUS_LABELS[status || ''] || status || '未知状态'
}

export function uploadStatusTone(status?: string): UploadTone {
  if (status === 'MERGED' || status === 'COMPLETED') return 'ok'
  if (status === 'UPLOADING') return 'warn'
  if (status === 'CANCELLED' || status === 'FAILED') return 'danger'
  return 'info'
}

/** 旧端 el-tag 的 type 取值（EP 用 warning，本模块的色调名用 warn） */
export function uploadToneToTagType(tone: UploadTone): 'success' | 'warning' | 'danger' | 'info' {
  if (tone === 'ok') return 'success'
  if (tone === 'warn') return 'warning'
  return tone
}

export function formatUploadSize(bytes?: number | null): string {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) return '--'
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

/** 会话列表响应归一：兼容裸数组与分页信封；形状不可信时返回 null（调用方据此进失败态） */
export function pickUploadSessions(payload: unknown): Record<string, unknown>[] | null {
  const data = unwrap(payload)
  if (Array.isArray(data)) return data as Record<string, unknown>[]
  if (isPageEnvelope(data)) return pickList<Record<string, unknown>>(data)
  return null
}

/** 进度响应归一：uploaded/total/missingIndexes 取自服务端磁盘真值，缺失即回 null 由调用方兜住 */
export interface UploadProgressView {
  status: string
  uploaded: number | null
  total: number | null
  missing: number[]
  /** uploaded/total 推导出的百分比；不足以计算、或会话已终态（读数不可信）时为 null */
  percent: number | null
}

function numOr(value: unknown): number | null {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/** 分片序号只认非负整数：与 useChunkedUpload 续传时的同一判定口径 */
function indexOr(value: unknown): number | null {
  const num = Number(value)
  return Number.isInteger(num) && num >= 0 ? num : null
}

/** 后端在 merge 成功与 cancel 时都调用 deleteChunkDir（FileUploadSessionServiceImpl:331/:408），
 *  过期清理也把 COMPLETED/CANCELLED 列为 reclaimable（:463-465）， UPLOADING 与 FAILED 的分片目录刻意保留。
 *  2026-10-29 实测：COMPLETED 会话的 progress 返回 uploaded=0、missingIndexes=[0]。
 *  所以磁盘真值只对未回收目录的状态有意义；终态照抄会把 100% 显示成 0% 并谎报缺片。 */
const RECLAIMED_STATUSES = ['COMPLETED', 'CANCELLED']

export function pickUploadProgress(payload: unknown): UploadProgressView {
  const data = (unwrap(payload) ?? {}) as Record<string, unknown>
  const status = typeof data.status === 'string' ? data.status : ''
  if (RECLAIMED_STATUSES.includes(status)) {
    return { status, uploaded: null, total: null, missing: [], percent: null }
  }
  const uploaded = numOr(data.uploaded)
  const total = numOr(data.total)
  const missing = Array.isArray(data.missingIndexes)
    ? data.missingIndexes.map(indexOr).filter((i): i is number => i !== null)
    : []
  const percent =
    uploaded !== null && total !== null && total > 0
      ? Math.min(100, Math.max(0, Math.round((uploaded / total) * 100)))
      : null
  return {
    status,
    uploaded,
    total,
    missing,
    percent,
  }
}
