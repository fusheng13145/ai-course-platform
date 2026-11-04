import resourceApi from '@/api/resource'

export function approvalLabel(value: unknown): string {
  const raw = String(value || '')
    .trim()
    .toUpperCase()
  if (raw === 'APPROVED') return '已通过'
  if (raw === 'PENDING') return '待审批'
  if (raw === 'REJECTED') return '已驳回'
  return String(value || '—')
}

/**
 * 下载课程资源（教师端章节表与学生端资源区共用同一实现）。
 *
 * 为什么要抽出来：这段逻辑此前只存在于 `ChaptersTab`，学生端要加下载就得抄第二份；
 * 而它有两个非显然的约束——① 走 `resourceApi.downloadResource` 拿 blob，
 * 才能把后端 403（未选课）当成可提示的失败，而不是让 `window.open` 静默打开一个 403 页；
 * ② 环境没有 `URL.createObjectURL` 时（jsdom/老浏览器）退回直链。
 * 两处各写一份必然分叉，所以这里收成唯一实现。
 *
 * @param id 资源 ID
 * @param fileName 落盘文件名（缺省用 `resource-<id>`）
 * @returns 下载已发起时 resolve；网络/权限失败时 reject，由调用方提示
 */
export async function downloadResourceFile(id: number | string, fileName?: string): Promise<void> {
  const payload = await resourceApi.downloadResource(id)
  if (typeof URL.createObjectURL !== 'function') {
    window.open(`/api/resources/download/${id}`, '_blank', 'noopener')
    return
  }
  const body = (payload as { data?: unknown }).data ?? payload
  const blob = body instanceof Blob ? body : new Blob([body as BlobPart])
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || `resource-${id}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
